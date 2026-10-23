package com.ohgiraffers.handlermethod.api;

import com.jayway.jsonpath.JsonPath;
import com.ohgiraffers.handlermethod.guardrail.InputGuardrailClient;
import com.ohgiraffers.handlermethod.guardrail.OutputGuardrailClient;
import com.ohgiraffers.handlermethod.llm.LlmClient;
import com.ohgiraffers.handlermethod.llm.StreamingLlmClient;
import com.ohgiraffers.handlermethod.security.JwtTokenProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A deliberately tighter override still blocks before paid clients, without shrinking the DTO. */
@SpringBootTest(properties = {"spring.jpa.show-sql=false",
        "spring.datasource.url=jdbc:h2:mem:input-policy;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "llm.cost.estimated-chars-per-page=50"})
@AutoConfigureMockMvc
class ShortInputPolicyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean InputGuardrailClient input;
    @MockitoBean OutputGuardrailClient output;
    @MockitoBean LlmClient llm;
    @MockitoBean StreamingLlmClient streaming;

    @ParameterizedTest
    @ValueSource(strings = {"/api/ai/chat", "/api/ai/chat/stream"})
    void aTighterConfiguredPolicyStillBlocksAndAccumulatesRiskAcrossTransports(String firstPath) throws Exception {
        String session = UUID.randomUUID().toString();
        String story = "민사" + "가".repeat(49); // 51: within DTO, outside this explicit test policy.
        String first = reply(firstPath, story, session);
        assertThat(JsonPath.<String>read(first, "$.reasonCode")).isEqualTo("COST_LIMIT_BLOCKED");
        assertThat(JsonPath.<String>read(first, "$.detailCode")).isEqualTo("REQUEST_TOO_LONG");
        String second = reply(firstPath.endsWith("/stream") ? "/api/ai/chat" : "/api/ai/chat/stream", story, session);
        assertThat(JsonPath.<String>read(second, "$.reasonCode")).isEqualTo("DYNAMIC_SESSION_BLOCKED");
        assertThat(JsonPath.<String>read(second, "$.detailCode")).isEqualTo("REQUEST_TOO_LONG");
        verifyNoInteractions(input, output, llm, streaming);
    }
    private String reply(String path, String story, String session) throws Exception {
        var result = mvc.perform(post(path).header("Authorization", "Bearer " + tokens.createToken("user", "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("message", story, "sessionId", session))))
                .andExpect(status().isOk()).andReturn();
        if (result.getRequest().isAsyncStarted()) {
            result.getAsyncResult(5000);
            result = mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andReturn();
        }
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return path.endsWith("/stream") ? body.lines().filter(line -> line.startsWith("data:"))
                .reduce((a, b) -> b).orElseThrow().substring(5) : body;
    }
}
