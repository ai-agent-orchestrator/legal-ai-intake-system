package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailClient;
import com.ohgiraffers.handlermethod.llm.LlmClient;
import com.ohgiraffers.handlermethod.llm.StreamingLlmClient;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureMockMvc
@ActiveProfiles("cost-block-demo")
class SmallBudgetIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired MeterRegistry registry;
    @MockitoBean InputGuardrailClient input;
    @MockitoBean OutputGuardrailClient output;
    @MockitoBean LlmClient llm;
    @MockitoBean StreamingLlmClient stream;

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void tinySessionBudgetBlocksBeforeAnyPaidClientOrReservationLeak(String path) throws Exception {
        var result = mvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.createToken("user", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"민사\",\"sessionId\":\"demo-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andReturn();
        if (path.endsWith("/stream")) {
            assertThat(result.getRequest().isAsyncStarted()).isTrue();
            result.getAsyncResult(5000);
            result = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
        }
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (path.endsWith("/stream")) {
            body = body.lines().filter(line -> line.startsWith("data:"))
                    .reduce((previous, next) -> next).orElseThrow().substring(5);
        }
        var response = JsonPath.parse(body);
        assertThat(response.read("$.blocked", Boolean.class)).isTrue();
        assertThat(response.read("$.reasonCode", String.class)).isEqualTo("COST_LIMIT_BLOCKED");
        assertThat(response.read("$.detailCode", String.class)).isEqualTo("SESSION_BUDGET_EXCEEDED");
        assertThat(registry.get("ai.cost.session.budget.usd").gauge().value()).isEqualTo(0.00001);
        assertThat(registry.get("ai.cost.reserved.usd").gauge().value()).isZero();
        assertThat(registry.get("ai.cost.accounted.usd").gauge().value()).isZero();
        verifyNoInteractions(input, output, llm, stream);
    }
}
