package com.ohgiraffers.handlermethod.interceptor;

import com.ohgiraffers.handlermethod.support.TraceContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TraceIdInterceptor.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String START_TIME_ATTRIBUTE = "startTime";
    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final MeterRegistry meterRegistry;

    public TraceIdInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = resolveTraceId(request);
        long startTime = System.currentTimeMillis();

        TraceContext.setTraceId(traceId);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);
        response.setHeader(TRACE_ID_HEADER, traceId);

        log.info("request start traceId={} method={} uri={} thread={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                Thread.currentThread());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long elapsedMs = elapsedMs(request);
        String traceId = TraceContext.currentTraceId();
        String status = String.valueOf(response.getStatus());
        String outcome = response.getStatus() >= 400 || ex != null ? "ERROR" : "SUCCESS";
        int sqlStatementCount = TraceContext.currentSqlStatementCount();

        recordMetrics(request, status, outcome, elapsedMs, sqlStatementCount);

        log.info("request end traceId={} method={} uri={} status={} outcome={} elapsedMs={} sqlStatements={} thread={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                status,
                outcome,
                elapsedMs,
                sqlStatementCount,
                Thread.currentThread());

        if (ex != null) {
            log.warn("request exception traceId={} method={} uri={} exception={}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    ex.getClass().getSimpleName());
        }

        TraceContext.clear();
        MDC.remove(TRACE_ID_MDC_KEY);
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return traceId;
    }

    private long elapsedMs(HttpServletRequest request) {
        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);

        if (startTime instanceof Long value) {
            return System.currentTimeMillis() - value;
        }

        return -1;
    }

    private void recordMetrics(HttpServletRequest request, String status, String outcome, long elapsedMs, int sqlStatementCount) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        Counter.builder("practice.api.requests")
                .description("Total number of traced API requests")
                .tag("method", method)
                .tag("uri", uri)
                .tag("status", status)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();

        Timer.builder("practice.api.request.duration")
                .description("Traced API request duration")
                .tag("method", method)
                .tag("uri", uri)
                .tag("status", status)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Math.max(elapsedMs, 0), TimeUnit.MILLISECONDS);

        Counter.builder("practice.api.sql.statements")
                .description("Total number of SQL statements executed during traced API requests")
                .tag("method", method)
                .tag("uri", uri)
                .tag("status", status)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment(sqlStatementCount);

        DistributionSummary.builder("practice.api.sql.statements.per.request")
                .description("SQL statement count per traced API request")
                .tag("method", method)
                .tag("uri", uri)
                .tag("status", status)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(sqlStatementCount);

        if ("ERROR".equals(outcome)) {
            Counter.builder("practice.api.errors")
                    .description("Total number of traced API error responses")
                    .tag("method", method)
                    .tag("uri", uri)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
        }
    }
}
