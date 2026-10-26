package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.repository.AgentRiskHistoryRepository;
import com.ohgiraffers.handlermethod.repository.RiskIncidentRepository;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route policy regressions found in the operator 09-13 cross review:
 * anonymous callers could create risk history/incidents shown on the operator screens,
 * and every user's legal-intake summaries were readable without login.
 */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@Transactional
class SecurityRouteAccessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired AgentRiskHistoryRepository histories;
    @Autowired RiskIncidentRepository incidents;

    /** Every write that feeds operator data (risk history, incidents, agent runs/jobs). */
    private static final List<String> AGENT_WRITES = List.of(
            "/api/agent/risk/analyze",
            "/api/agent/risk-patterns/policy-violation-retry",
            "/api/agent/risk-patterns/external-api-policy-violation",
            "/api/agent/practice/run",
            "/api/agent/practice/db-write",
            "/api/agent/policy-check",
            "/api/agent/jobs");

    private static final String HIGH_RISK_BODY = """
            {"userInput":"경로 권한 검사용 가상 요청","toolName":"route-check","retryCount":3,
             "policyViolation":true,"externalApiCall":true,"toolError":false,"approvalRequired":false,
             "dbWrite":false,"promptTokens":0,"completionTokens":0}
            """;

    private String bearer(String role) { return "Bearer " + tokens.createToken(role.toLowerCase(), "ROLE_" + role); }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String authorization) {
        return authorization == null ? request : request.header("Authorization", authorization);
    }

    private void assertAgentWritesRejected(String authorization, int expectedStatus, String expectedCode) throws Exception {
        long historiesBefore = histories.count();
        long incidentsBefore = incidents.count();
        for (String path : AGENT_WRITES) {
            mvc.perform(as(post(path), authorization).contentType(MediaType.APPLICATION_JSON).content(HIGH_RISK_BODY))
                    .andExpect(status().is(expectedStatus))
                    .andExpect(jsonPath("$.code").value(expectedCode));
        }
        assertThat(histories.count()).isEqualTo(historiesBefore);
        assertThat(incidents.count()).isEqualTo(incidentsBefore);
    }

    @Test
    void anonymousCannotCreateRiskHistoryOrIncidents() throws Exception {
        assertAgentWritesRejected(null, 401, "JWT_REQUIRED");
    }

    @Test
    void userAndLawyerCannotCreateRiskHistoryOrIncidents() throws Exception {
        assertAgentWritesRejected(bearer("USER"), 403, "JWT_FORBIDDEN");
        assertAgentWritesRejected(bearer("LAWYER"), 403, "JWT_FORBIDDEN");
    }

    @Test
    void adminCanStillRecordAnAnalysis() throws Exception {
        long before = histories.count();
        mvc.perform(post("/api/agent/risk/analyze").header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(HIGH_RISK_BODY))
                .andExpect(status().isOk());
        assertThat(histories.count()).isEqualTo(before + 1);
    }

    @Test
    void legalIntakeAggregatesAreAdminOnly() throws Exception {
        for (String path : List.of("/api/legal/intake/history", "/api/legal/intake/summary")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("JWT_REQUIRED"));
            mvc.perform(get(path).header("Authorization", bearer("USER")))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("JWT_FORBIDDEN"));
            mvc.perform(get(path).header("Authorization", bearer("LAWYER")))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("JWT_FORBIDDEN"));
            mvc.perform(get(path).header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
        }
    }

    @Test
    void labAndGuideApisAreAdminOnly() throws Exception {
        for (String path : List.of("/api/status", "/api/observability/guide", "/api/security-observability/guide",
                "/api/n-plus-one-practice/good")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", bearer("USER"))).andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization", bearer("ADMIN"))).andExpect(status().isOk());
        }
    }

    @Test
    void publicReferenceCatalogAndGuestFlowsStayAnonymous() throws Exception {
        mvc.perform(get("/api/legal/procedures")).andExpect(status().isOk());
        mvc.perform(get("/api/legal/forms")).andExpect(status().isOk());
        int preview = mvc.perform(post("/api/intakes/classify-preview").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        assertThat(preview).isNotIn(401, 403);
        mvc.perform(post("/api/intake-events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"route-check-visit\",\"eventType\":\"VISIT\",\"sessionId\":\"route-check\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void unlistedApiRoutesAreDeniedByDefault() throws Exception {
        mvc.perform(get("/api/not-a-route")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("JWT_REQUIRED"));
        mvc.perform(get("/api/not-a-route").header("Authorization", bearer("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/not-a-route").header("Authorization", bearer("ADMIN"))).andExpect(status().isForbidden());
    }

    @Test
    void corsAllowsLocalOriginsOnlyAndNeverCredentials() throws Exception {
        for (String origin : List.of("http://127.0.0.1:5176", "http://localhost:5175")) {
            mvc.perform(options("/api/legal/procedures").header("Origin", origin)
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }
        mvc.perform(options("/api/legal/procedures").header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
