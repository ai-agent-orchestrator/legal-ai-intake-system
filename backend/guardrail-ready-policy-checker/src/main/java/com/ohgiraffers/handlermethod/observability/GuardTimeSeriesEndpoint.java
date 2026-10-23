package com.ohgiraffers.handlermethod.observability;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Covered by the existing ADMIN Basic Actuator security chain. */
@Component
@Endpoint(id = "guardtimeseries")
public class GuardTimeSeriesEndpoint {
    private final PrometheusTimeSeriesService service;

    public GuardTimeSeriesEndpoint(PrometheusTimeSeriesService service) { this.service = service; }

    @ReadOperation
    public Map<String, Object> read() { return service.read(); }
}
