package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AiSearchRequestCreateRequest;
import com.ohgiraffers.handlermethod.dto.AiSearchRequestResponse;
import com.ohgiraffers.handlermethod.dto.LegalIntakeCreateRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeResponse;
import com.ohgiraffers.handlermethod.dto.SearchResultSnapshotRequest;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/intakes")
public class LegalIntakeRecordController {

    private final LegalIntakeRecordService legalIntakeRecordService;

    public LegalIntakeRecordController(LegalIntakeRecordService legalIntakeRecordService) {
        this.legalIntakeRecordService = legalIntakeRecordService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LegalIntakeResponse create(@Valid @RequestBody LegalIntakeCreateRequest request,
                                     Authentication authentication,
                                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return legalIntakeRecordService.create(request, authentication);
    }

    @GetMapping
    public List<LegalIntakeResponse> findRecent(Authentication authentication) {
        return legalIntakeRecordService.findRecent(authentication);
    }

    @GetMapping("/{id}")
    public LegalIntakeResponse findById(@PathVariable Long id, Authentication authentication) {
        return legalIntakeRecordService.findById(id, authentication);
    }

    @PostMapping("/{intakeId}/search-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public AiSearchRequestResponse createSearchRequest(@PathVariable Long intakeId,
                                                       @Valid @RequestBody AiSearchRequestCreateRequest request, Authentication authentication) {
        return legalIntakeRecordService.createSearchRequest(intakeId, request, authentication);
    }

    @GetMapping("/search-requests/{searchRequestId}")
    public AiSearchRequestResponse findSearchRequest(@PathVariable Long searchRequestId, Authentication authentication) {
        return legalIntakeRecordService.findSearchRequest(searchRequestId, authentication);
    }

    @PostMapping("/search-requests/{searchRequestId}/results")
    @ResponseStatus(HttpStatus.CREATED)
    public AiSearchRequestResponse addSearchResult(@PathVariable Long searchRequestId,
                                                   @Valid @RequestBody SearchResultSnapshotRequest request, Authentication authentication) {
        return legalIntakeRecordService.addSearchResult(searchRequestId, request, authentication);
    }
}
