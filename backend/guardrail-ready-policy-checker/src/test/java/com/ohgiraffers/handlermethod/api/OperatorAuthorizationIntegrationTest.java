package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.entity.AgentRiskHistory;
import com.ohgiraffers.handlermethod.entity.RiskIncident;
import com.ohgiraffers.handlermethod.repository.AgentRiskHistoryRepository;
import com.ohgiraffers.handlermethod.repository.RiskIncidentRepository;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@Transactional
class OperatorAuthorizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired AgentRiskHistoryRepository histories;
    @Autowired RiskIncidentRepository incidents;
    @Value("${jwt.secret}") String signingSecret;
    private static final List<String> READS = List.of("/api/admin/dashboard/summary", "/api/admin/dashboard/marketing-funnel",
            "/api/admin/dashboard/security-events", "/api/agent/risk/summary", "/api/agent/risk/history", "/api/incidents");
    private String bearer(String role) { return "Bearer " + tokens.createToken(role.toLowerCase(), "ROLE_" + role); }
    private long fixture() {
        var history = histories.saveAndFlush(AgentRiskHistory.create("가상 자료", "qa-tool", "QA", 90, "HIGH",
                "POLICY_VIOLATION", "BLOCK_AND_ESCALATE", true, "operator-authorization-test"));
        return incidents.saveAndFlush(RiskIncident.open("QA", "HIGH", "QA", 90, "BLOCK_AND_ESCALATE",
                history.getId(), "operator-authorization-test", "가상 자료")).getId();
    }
    private void rejectAll(String authorization, int expected, String code) throws Exception {
        long id = fixture();
        for (String path : READS) {
            MockHttpServletRequestBuilder request = get(path);
            if (authorization != null) request.header("Authorization", authorization);
            mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code));
        }
        for (String action : List.of("ack", "resolve")) {
            var request = patch("/api/incidents/{id}/" + action, id);
            if (authorization != null) request.header("Authorization", authorization);
            mvc.perform(request).andExpect(status().is(expected)).andExpect(jsonPath("$.code").value(code));
        }
        assertThat(incidents.findById(id).orElseThrow().getStatus().name()).isEqualTo("OPEN");
    }
    @Test void anonymousCannotReadOrWriteAnyOfTheEightApis() throws Exception { rejectAll(null, 401, "JWT_REQUIRED"); }
    @Test void userCannotReadOrWriteAnyOfTheEightApis() throws Exception { rejectAll(bearer("USER"), 403, "JWT_FORBIDDEN"); }
    @Test void lawyerCannotReadOrWriteAnyOfTheEightApis() throws Exception { rejectAll(bearer("LAWYER"), 403, "JWT_FORBIDDEN"); }
    @Test void invalidAndExpiredTokensAreRejected() throws Exception {
        rejectAll("Bearer invalid", 401, "JWT_REQUIRED");
        rejectAll("Bearer " + new JwtTokenProvider(signingSecret, -60000).createToken("admin", "ROLE_ADMIN"), 401, "JWT_REQUIRED");
    }
    @Test void adminCanReadAndPerformBothIncidentActions() throws Exception {
        String auth = bearer("ADMIN"); long id = fixture();
        for (String path : READS) mvc.perform(get(path).header("Authorization", auth)).andExpect(status().isOk());
        mvc.perform(patch("/api/incidents/{id}/ack", id).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        mvc.perform(patch("/api/incidents/{id}/resolve", id).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
    @Test void corsPreflightWorksWithoutGrantingAnonymousAccess() throws Exception {
        for (String path : READS) {
            mvc.perform(options(path).header("Origin", "http://127.0.0.1:5175")
                    .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization"))
                    .andExpect(status().isOk()).andExpect(header().exists("Access-Control-Allow-Origin"));
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(options("/api/incidents/1/ack").header("Origin", "http://127.0.0.1:5175")
                .header("Access-Control-Request-Method", "PATCH").header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk());
    }
    @Test void actuatorBasicAndBusinessJwtRemainSeparate() throws Exception {
        String basic = "Basic " + Base64.getEncoder().encodeToString("admin:admin123".getBytes(StandardCharsets.UTF_8));
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header("Authorization", bearer("ADMIN"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").header("Authorization", basic)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/dashboard/summary").header("Authorization", basic)).andExpect(status().isUnauthorized());
    }
}
