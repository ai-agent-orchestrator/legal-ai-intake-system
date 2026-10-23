package com.ohgiraffers.handlermethod.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GuardTimeSeriesSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean PrometheusTimeSeriesService service;

    @Test
    void adminBasicOnlyAndReadOnlyWithBrowserPreflight() throws Exception {
        mvc.perform(get("/actuator/guardtimeseries")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/guardtimeseries").header(HttpHeaders.AUTHORIZATION, basic("user", "user123"))).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/guardtimeseries").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-basic-credential")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
        mvc.perform(options("/actuator/guardtimeseries").header(HttpHeaders.ORIGIN, "http://127.0.0.1:5175")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET").header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://127.0.0.1:5175"));
        when(service.read()).thenReturn(Map.of("series", List.of(), "stepSeconds", 15));
        mvc.perform(get("/actuator/guardtimeseries").header(HttpHeaders.AUTHORIZATION, basic("admin", "admin123")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.series").isEmpty());
        verify(service, times(1)).read();
        mvc.perform(post("/actuator/guardtimeseries").header(HttpHeaders.AUTHORIZATION, basic("admin", "admin123")))
                .andExpect(status().is4xxClientError());
        verifyNoMoreInteractions(service);
    }
    private String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
