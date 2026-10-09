package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.ApiStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class ApiStatusController {

    @GetMapping
    public ApiStatusResponse status() {
        return new ApiStatusResponse(
                "UP",
                "spring-boot-api-observability-practice",
                System.getProperty("java.version"),
                Thread.currentThread().toString(),
                "traceId/interceptor enabled"
        );
    }
}
