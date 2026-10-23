package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@Transactional
class AgentRiskThresholdIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired com.ohgiraffers.handlermethod.security.JwtTokenProvider tokens;

    private String body(boolean policy, boolean external, boolean error, boolean approval, boolean db, int retries) {
        return """
                {"userInput":"점수 확인용 가상 요청","toolName":"local-check", "retryCount":%d,
                 "policyViolation":%s,"externalApiCall":%s,"toolError":%s,"approvalRequired":%s,"dbWrite":%s,
                 "promptTokens":0,"completionTokens":0}
                """.formatted(retries, policy, external, error, approval, db);
    }

    private Map<String, Object> analyze(String body, String trace) throws Exception {
        var result = mvc.perform(post("/api/agent/risk/analyze").header("X-Trace-Id", trace)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$");
    }

    @Test
    void scoreBelow80NeverGetsBlockRecommendationAnd80IsInclusive() throws Exception {
        var requests = List.of(
                body(true, false, false, false, false, 1),
                body(true, true, false, false, false, 0),
                body(true, false, true, false, false, 3),
                body(true, true, true, false, false, 0),
                body(true, true, false, false, false, 2));
        int[] expected = {48, 65, 79, 80, 81};
        for (int i = 0; i < requests.size(); i++) {
            var response = analyze(requests.get(i), UUID.randomUUID().toString());
            assertThat(response.get("riskScore")).isEqualTo(expected[i]);
            assertThat(response.get("recommendedAction")).isEqualTo(expected[i] >= 80 ? "BLOCK_AND_ESCALATE" : "REQUIRE_APPROVAL");
            Map<?, ?> breakdown = (Map<?, ?>) response.get("scoreBreakdown");
            assertThat(breakdown.get("threshold")).isEqualTo(80);
            assertThat(breakdown.get("total")).isEqualTo(expected[i]);
        }
    }

    @Test
    void historyReturnsTheActualPartsEvenWhenTheTotalWasCapped() throws Exception {
        var response = analyze(body(true, true, true, true, true, 4), UUID.randomUUID().toString());
        assertThat(response.get("riskScore")).isEqualTo(100);
        var result = mvc.perform(get("/api/agent/risk/history").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))).andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> rows = JsonPath.read(result.getResponse().getContentAsString(), "$");
        var row = rows.stream().filter(r -> r.get("id").toString().equals(response.get("historyId").toString())).findFirst().orElseThrow();
        Map<?,?> breakdown = (Map<?,?>) row.get("scoreBreakdown");
        assertThat(breakdown.get("behaviorPoints")).isEqualTo(115);
        assertThat(breakdown.get("retryPoints")).isEqualTo(25);
        assertThat(breakdown.get("total")).isEqualTo(100);
    }

    @Test
    void reusedTraceWithAmbiguousRunsDoesNotInventHistoryParts() throws Exception {
        String trace = UUID.randomUUID().toString();
        var first = analyze(body(true, true, true, true, true, 1), trace);
        var second = analyze(body(true, true, true, true, true, 2), trace);
        var result = mvc.perform(get("/api/agent/risk/history").header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))).andExpect(status().isOk()).andReturn();
        List<Map<String,Object>> rows = JsonPath.read(result.getResponse().getContentAsString(), "$");
        var targetIds = List.of(first.get("historyId").toString(), second.get("historyId").toString());
        assertThat(rows.stream().filter(row -> targetIds.contains(row.get("id").toString())).toList())
                .hasSize(2).allSatisfy(row -> assertThat(row.get("scoreBreakdown")).isNull());
    }

    @Test
    void veryLargeRetryCountCannotOverflowIntoANegativeScore() throws Exception {
        var response = analyze(body(false, false, false, false, false, Integer.MAX_VALUE), UUID.randomUUID().toString());
        assertThat(response.get("riskScore")).isEqualTo(25);
    }
}
