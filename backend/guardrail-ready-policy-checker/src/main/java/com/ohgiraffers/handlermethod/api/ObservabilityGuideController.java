package com.ohgiraffers.handlermethod.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityGuideController {

    @GetMapping("/guide")
    public Map<String, Object> guide() {
        return Map.of(
                "goal", "Connect metrics, logs, and traceId so one request can be diagnosed.",
                "metrics", List.of(
                        "GET /actuator/metrics/practice.api.requests",
                        "GET /actuator/metrics/practice.api.request.duration",
                        "GET /actuator/metrics/practice.api.errors",
                        "GET /actuator/prometheus"
                ),
                "logs", List.of(
                        "request start traceId=...",
                        "request end traceId=... status=... outcome=... elapsedMs=..."
                ),
                "traces", List.of(
                        "Send X-Trace-Id header",
                        "Find the same traceId in response header, response body, and console logs"
                ),
                "testRequests", List.of(
                        "POST /api/chat with X-Trace-Id",
                        "POST /api/chat with invalid body and X-Trace-Id"
                )
        );
    }
}
