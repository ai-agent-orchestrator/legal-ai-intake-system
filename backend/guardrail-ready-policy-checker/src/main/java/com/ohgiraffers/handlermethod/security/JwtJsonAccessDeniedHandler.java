package com.ohgiraffers.handlermethod.security;

import com.ohgiraffers.handlermethod.dto.ErrorResponse;
import com.ohgiraffers.handlermethod.support.SecurityMetricRecorder;
import com.ohgiraffers.handlermethod.support.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class JwtJsonAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityMetricRecorder securityMetricRecorder;
    private final ObjectMapper objectMapper;

    public JwtJsonAccessDeniedHandler(SecurityMetricRecorder securityMetricRecorder,
                                      ObjectMapper objectMapper) {
        this.securityMetricRecorder = securityMetricRecorder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {
        securityMetricRecorder.recordAccessDenied(request);
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(
                "JWT_FORBIDDEN",
                "Access denied",
                HttpServletResponse.SC_FORBIDDEN,
                request.getRequestURI(),
                TraceContext.currentTraceId(),
                Map.of(),
                Instant.now()
        )));
    }
}
