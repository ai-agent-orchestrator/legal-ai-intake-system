package com.ohgiraffers.handlermethod.security;

import com.ohgiraffers.handlermethod.dto.ErrorResponse;
import com.ohgiraffers.handlermethod.support.SecurityMetricRecorder;
import com.ohgiraffers.handlermethod.support.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class JwtJsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityMetricRecorder securityMetricRecorder;
    private final ObjectMapper objectMapper;

    public JwtJsonAuthenticationEntryPoint(SecurityMetricRecorder securityMetricRecorder,
                                           ObjectMapper objectMapper) {
        this.securityMetricRecorder = securityMetricRecorder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        securityMetricRecorder.recordAuthFailure(request);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse(
                "JWT_REQUIRED",
                "Authentication required",
                HttpServletResponse.SC_UNAUTHORIZED,
                request.getRequestURI(),
                TraceContext.currentTraceId(),
                Map.of(),
                Instant.now()
        )));
    }
}
