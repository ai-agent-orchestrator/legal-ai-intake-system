package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AiSearchRequestResponse;
import com.ohgiraffers.handlermethod.dto.SearchExecuteRequest;
import com.ohgiraffers.handlermethod.search.SearchExecutionService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/intakes/search-requests")
public class SearchExecutionController {
    private final SearchExecutionService service;
    public SearchExecutionController(SearchExecutionService service) { this.service = service; }
    @PostMapping("/{searchRequestId}/execute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiSearchRequestResponse execute(@PathVariable @Positive Long searchRequestId,
            @RequestBody(required = false) SearchExecuteRequest request, Authentication authentication) {
        return service.execute(searchRequestId, request != null && Boolean.TRUE.equals(request.retryExplanation()), authentication);
    }
}
