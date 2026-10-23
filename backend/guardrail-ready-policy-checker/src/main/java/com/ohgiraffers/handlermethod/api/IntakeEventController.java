package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.IntakeEventRequest;
import com.ohgiraffers.handlermethod.dto.IntakeEventResponse;
import com.ohgiraffers.handlermethod.service.IntakeEventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class IntakeEventController {
    private final IntakeEventService service;

    public IntakeEventController(IntakeEventService service) { this.service = service; }

    @PostMapping("/api/intake-events")
    public ResponseEntity<IntakeEventResponse> record(@Valid @RequestBody IntakeEventRequest request) {
        var response = service.record(request);
        return ResponseEntity.status(response.created() ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }
}
