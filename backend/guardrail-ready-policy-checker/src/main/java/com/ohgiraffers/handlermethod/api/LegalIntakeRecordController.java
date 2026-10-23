package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AiSearchRequestCreateRequest;
import com.ohgiraffers.handlermethod.dto.AiSearchRequestResponse;
import com.ohgiraffers.handlermethod.dto.IdempotentResult;
import com.ohgiraffers.handlermethod.dto.LegalIntakeCreateRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeResponse;
import com.ohgiraffers.handlermethod.dto.SearchResultSnapshotRequest;
import com.ohgiraffers.handlermethod.entity.LegalIntake;
import com.ohgiraffers.handlermethod.service.LegalIntakeRecordService;
import com.ohgiraffers.handlermethod.support.RequestFingerprint;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/intakes")
public class LegalIntakeRecordController {

    private static final Logger log = LoggerFactory.getLogger(LegalIntakeRecordController.class);
    private final LegalIntakeRecordService legalIntakeRecordService;

    public LegalIntakeRecordController(LegalIntakeRecordService legalIntakeRecordService) {
        this.legalIntakeRecordService = legalIntakeRecordService;
    }

    /*
     * 화면03 문의 접수. 재전송(같은 사용자 + 같은 Idempotency-Key + 같은 본문)이면 새로 저장하지 않고
     * 처음 저장된 같은 문의(같은 id)의 현재 상세를 Idempotent-Replayed: true 헤더와 함께 돌려준다.
     * 최초 응답 스냅샷이 아니므로 그 사이 바뀐 상태는 반영된다. 화면은 성공 처리만 하면 되므로 상태 코드는 201로 같다.
     * 같은 키 + 다른 본문은 409 IDEMPOTENCY_KEY_REUSED, 키 형식 오류는 400.
     */
    @PostMapping
    public ResponseEntity<LegalIntakeResponse> create(@Valid @RequestBody LegalIntakeCreateRequest request,
                                                      Authentication authentication,
                                                      @RequestHeader(value = RequestFingerprint.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {
        IdempotentResult<LegalIntakeResponse> result;
        try {
            result = legalIntakeRecordService.create(request, idempotencyKey, authentication);
        } catch (DataIntegrityViolationException exception) {
            // 같은 키의 요청이 동시에 들어와 다른 요청이 먼저 저장(커밋)한 경우에만 그 건을 돌려준다.
            // 여기 도달했을 때 진 쪽 트랜잭션은 이미 롤백됐고, 조회는 새 트랜잭션에서 한다.
            // 다른 무결성 오류는 재전송 성공으로 숨기지 않고 그대로 실패시킨다.
            if (!isIdempotencyKeyCollision(exception)) {
                throw exception;
            }
            result = legalIntakeRecordService.resolveConcurrentSubmission(request, idempotencyKey, authentication);
            if (result == null) {
                throw exception;
            }
            log.info("Idempotent intake submission resolved after unique-key collision intakeId={}", result.body().id());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(RequestFingerprint.REPLAYED_HEADER, String.valueOf(result.replayed()))
                .body(result.body());
    }

    /** 원인 체인에서 (owner_username, idempotency_key) 유니크 제약 위반인지 확인한다. 값은 로그/응답에 쓰지 않는다. */
    static boolean isIdempotencyKeyCollision(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String name = cause instanceof ConstraintViolationException violation ? violation.getConstraintName() : null;
            String text = name != null ? name : cause.getMessage();
            if (text != null && text.toLowerCase(Locale.ROOT).contains(LegalIntake.IDEMPOTENCY_CONSTRAINT)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
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
