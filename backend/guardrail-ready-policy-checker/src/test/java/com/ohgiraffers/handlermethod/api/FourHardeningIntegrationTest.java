package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.entity.AgentRiskHistory;
import com.ohgiraffers.handlermethod.entity.RiskIncident;
import com.ohgiraffers.handlermethod.repository.*;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@Transactional
class FourHardeningIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    @Autowired AgentRiskHistoryRepository histories;
    @Autowired RiskIncidentRepository incidents;
    @Autowired SearchResultSnapshotRepository results;
    @Autowired LegalIntakeRepository intakes;

    private String auth() { return "Bearer " + tokens.createToken("user", "ROLE_USER"); }
    private String body(Object value) { return json.writeValueAsString(value); }

    @Test
    void historyOmitsInputOnAllQueriesButKeepsScoresAndStoredEvidence() throws Exception {
        String raw = "PRIVATE_MARKER_010-1234-5678";
        var row = histories.saveAndFlush(AgentRiskHistory.create(raw, "qa-tool", "QA_DECISION", 81,
                "HIGH", "POLICY_VIOLATION,EXTERNAL_API_CALL", "BLOCK_AND_ESCALATE", true, "qa-private-trace"));
        for (String query : List.of("", "?riskLevel=HIGH", "?decision=QA_DECISION")) {
            String response = mvc.perform(get("/api/agent/risk/history" + query).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("userInput", raw);
            List<Map<String, Object>> rows = JsonPath.read(response, "$");
            var saved = rows.stream().filter(item -> row.getId().toString().equals(item.get("id").toString()))
                    .findFirst().orElseThrow();
            assertThat(saved).containsEntry("riskScore", 81).containsEntry("traceId", "qa-private-trace");
            assertThat(saved.get("signals")).isEqualTo(List.of("POLICY_VIOLATION", "EXTERNAL_API_CALL"));
        }
        assertThat(histories.findById(row.getId()).orElseThrow().getUserInput()).isEqualTo(raw);
    }

    @Test
    void minimalAcceptsBoundariesAndOptionalFieldsWithoutCreatingAnIntake() throws Exception {
        long before = intakes.count();
        for (String type : List.of("CIVIL", "CRIMINAL", "FAMILY", "ELECTRONIC_LITIGATION", "UNKNOWN", "civil")) {
            mvc.perform(post("/api/legal-intake/minimal").header("Authorization", auth())
                    .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("coreFact", "가".repeat(100),
                            "caseType", type, "documents", Collections.nCopies(20, "가".repeat(100))))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.caseType").value(type.toUpperCase(Locale.ROOT)));
        }
        mvc.perform(post("/api/legal-intake/minimal").header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("coreFact", "빌려준 돈을 못 받았습니다."))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.caseType").value("CIVIL"));
        assertThat(intakes.count()).isEqualTo(before);
    }

    @Test
    void minimalRejectsInvalidFieldsAndReturnsActionableErrors() throws Exception {
        var badFields = new ArrayList<Map<String, Object>>();
        badFields.add(Map.of("coreFact", " "));
        badFields.add(Map.of("coreFact", "가".repeat(101)));
        badFields.add(Map.of("caseType", "UNSUPPORTED"));
        badFields.add(Map.of("caseType", ""));
        for (String field : List.of("sessionId", "inquiryPurpose", "counterpart", "amountOrDamage", "desiredNextStep")) {
            badFields.add(Map.of(field, "가".repeat(101)));
        }
        badFields.add(Map.of("partyRole", "가".repeat(51)));
        badFields.add(Map.of("incidentDate", "가".repeat(31)));
        badFields.add(Map.of("documents", Collections.nCopies(21, "계약서")));
        badFields.add(Map.of("documents", List.of("가".repeat(101))));
        badFields.add(Map.of("documents", List.of(" ")));
        badFields.add(Map.of("documents", Arrays.asList((String) null)));
        for (var fields : badFields) {
            var request = new HashMap<String, Object>();
            request.put("coreFact", "가상 사건"); request.putAll(fields);
            mvc.perform(post("/api/legal-intake/minimal").header("Authorization", auth())
                    .contentType(MediaType.APPLICATION_JSON).content(body(request)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        }
        mvc.perform(post("/api/legal-intake/minimal").header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.coreFact").exists());
    }

    @Test
    void minimalStillRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/legal-intake/minimal").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("coreFact", "가상 사건")))).andExpect(status().isUnauthorized());
    }

    private long search() throws Exception {
        var intake = mvc.perform(post("/api/intakes").header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("inquiryPurpose", "절차 안내",
                        "caseType", "CIVIL", "partyPosition", "원고", "coreStatement", "가상 문의",
                        "desiredNextStep", "절차 안내"))))
                .andExpect(status().isCreated()).andReturn();
        Number intakeId = JsonPath.read(intake.getResponse().getContentAsString(), "$.id");
        var search = mvc.perform(post("/api/intakes/{id}/search-requests", intakeId).header("Authorization", auth())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(search.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void unsafeUrlsAreRejectedBeforePersistence() throws Exception {
        long searchId = search(); long before = results.count();
        for (String url : List.of("javascript:alert(1)", "data:text/html,test", "file:///C:/secret", "//example.com/a",
                "https:///missing-host", "https://user:pass@example.com/", "https://user@example.com/",
                "https://example.com/a b", "https://example.com/\n", "https://example.com/\u0000",
                "https://example.com/%0a", "https://example.com/%0D", "https://example.com/%7f",
                "https://example.com:99999/", "https://example.com/\\bad")) {
            mvc.perform(post("/api/intakes/search-requests/{id}/results", searchId).header("Authorization", auth())
                    .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("sourceType", "PUBLIC_WEB",
                            "title", "가상 자료", "url", url))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.fieldErrors.url").exists());
        }
        assertThat(results.count()).isEqualTo(before);
    }

    @Test
    void nonPositiveOrdersAreRejectedBeforePersistence() throws Exception {
        long searchId = search(); long before = results.count();
        for (int order : List.of(0, -1, Integer.MIN_VALUE)) {
            mvc.perform(post("/api/intakes/search-requests/{id}/results", searchId).header("Authorization", auth())
                    .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("sourceType", "PUBLIC_WEB",
                            "title", "가상 자료", "url", "https://example.com/", "displayOrder", order))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.displayOrder").exists());
        }
        assertThat(results.count()).isEqualTo(before);
    }

    @Test
    void validUrlsAndAutomaticOrderStillWork() throws Exception {
        long searchId = search();
        for (String url : List.of("https://example.com/profile?id=123&name=a%20b#details", "http://example.com:8080/path")) {
            mvc.perform(post("/api/intakes/search-requests/{id}/results", searchId).header("Authorization", auth())
                    .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("sourceType", "PUBLIC_WEB",
                            "title", "가상 자료", "url", url))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/api/intakes/search-requests/{id}", searchId).header("Authorization", auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].displayOrder").value(1))
                .andExpect(jsonPath("$.results[1].displayOrder").value(2));
    }

    @Test
    void missingAndMalformedIncidentIdsDoNotChangeExistingIncidents() throws Exception {
        var history = histories.saveAndFlush(AgentRiskHistory.create("가상 사건", "qa-tool", "QA_DECISION", 81,
                "HIGH", "POLICY_VIOLATION", "BLOCK_AND_ESCALATE", true, "qa-404-trace"));
        var existing = incidents.saveAndFlush(RiskIncident.open("QA", "HIGH", "QA_DECISION", 81,
                "BLOCK_AND_ESCALATE", history.getId(), "qa-404-trace", "가상 자료"));
        long before = incidents.count();
        for (String action : List.of("ack", "resolve")) {
            mvc.perform(patch("/api/incidents/{id}/" + action, Long.MAX_VALUE).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"))
                    .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.traceId").isNotEmpty());
            mvc.perform(patch("/api/incidents/not-a-number/" + action).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))).andExpect(status().isBadRequest());
        }
        assertThat(incidents.count()).isEqualTo(before);
        assertThat(incidents.findById(existing.getId()).orElseThrow().getStatus().name()).isEqualTo("OPEN");
        mvc.perform(patch("/api/incidents/{id}/ack", existing.getId()).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
        mvc.perform(patch("/api/incidents/{id}/resolve", existing.getId()).header("Authorization", "Bearer " + tokens.createToken("admin", "ROLE_ADMIN"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
