package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AiSearchRequestCreateRequest;
import com.ohgiraffers.handlermethod.dto.AiSearchRequestResponse;
import com.ohgiraffers.handlermethod.dto.ConsultationMemoCreateRequest;
import com.ohgiraffers.handlermethod.dto.ConsultationMemoResponse;
import com.ohgiraffers.handlermethod.dto.IdempotentResult;
import com.ohgiraffers.handlermethod.dto.LegalIntakeClassificationRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeClassificationResponse;
import com.ohgiraffers.handlermethod.dto.LegalIntakeStatusRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeStatusResponse;
import com.ohgiraffers.handlermethod.dto.LawyerSearchConditionResponse;
import com.ohgiraffers.handlermethod.dto.LegalIntakeCreateRequest;
import com.ohgiraffers.handlermethod.dto.LegalIntakeResponse;
import com.ohgiraffers.handlermethod.dto.SearchResultSnapshotRequest;
import com.ohgiraffers.handlermethod.dto.SearchResultSnapshotResponse;
import com.ohgiraffers.handlermethod.entity.AiSearchRequest;
import com.ohgiraffers.handlermethod.entity.ConsultationMemo;
import com.ohgiraffers.handlermethod.legal.IntakeStatus;
import com.ohgiraffers.handlermethod.repository.ConsultationMemoRepository;
import com.ohgiraffers.handlermethod.entity.IntakeDocument;
import com.ohgiraffers.handlermethod.entity.LawyerSearchCondition;
import com.ohgiraffers.handlermethod.entity.LegalIntake;
import com.ohgiraffers.handlermethod.entity.SearchResultSnapshot;
import com.ohgiraffers.handlermethod.repository.AiSearchRequestRepository;
import com.ohgiraffers.handlermethod.repository.IntakeDocumentRepository;
import com.ohgiraffers.handlermethod.repository.LawyerSearchConditionRepository;
import com.ohgiraffers.handlermethod.repository.LegalIntakeRepository;
import com.ohgiraffers.handlermethod.repository.SearchResultSnapshotRepository;
import com.ohgiraffers.handlermethod.riskengine.PrivacyOutputGuardService;
import com.ohgiraffers.handlermethod.support.ApiConflictException;
import com.ohgiraffers.handlermethod.support.RequestFingerprint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LegalIntakeRecordService {

    private static final String DEFAULT_SOURCE_POLICY = "KBA_NAVER_PUBLIC_WEB";

    private final LegalIntakeRepository legalIntakeRepository;
    private final IntakeDocumentRepository intakeDocumentRepository;
    private final LawyerSearchConditionRepository lawyerSearchConditionRepository;
    private final AiSearchRequestRepository aiSearchRequestRepository;
    private final SearchResultSnapshotRepository searchResultSnapshotRepository;
    private final PrivacyOutputGuardService privacyOutputGuardService;
    private final ConsultationMemoRepository consultationMemoRepository;
    private final IntakeAccessService access;
    private final UserDetailsService users;
    /* 메모: Idempotency-Key 없이 같은 작성자가 같은 내용을 이 시간 안에 다시 보내면 재전송으로 본다. */
    private final long resubmitWindowSeconds;

    public LegalIntakeRecordService(LegalIntakeRepository legalIntakeRepository,
                                    IntakeDocumentRepository intakeDocumentRepository,
                                    LawyerSearchConditionRepository lawyerSearchConditionRepository,
                                    AiSearchRequestRepository aiSearchRequestRepository,
                                    SearchResultSnapshotRepository searchResultSnapshotRepository,
                                    PrivacyOutputGuardService privacyOutputGuardService,
                                    ConsultationMemoRepository consultationMemoRepository, IntakeAccessService access, UserDetailsService users,
                                    @Value("${intake.memo.resubmit-window-seconds:120}") long resubmitWindowSeconds) {
        this.legalIntakeRepository = legalIntakeRepository;
        this.intakeDocumentRepository = intakeDocumentRepository;
        this.lawyerSearchConditionRepository = lawyerSearchConditionRepository;
        this.aiSearchRequestRepository = aiSearchRequestRepository;
        this.searchResultSnapshotRepository = searchResultSnapshotRepository;
        this.privacyOutputGuardService = privacyOutputGuardService;
        this.consultationMemoRepository = consultationMemoRepository;
        this.access = access; this.users = users;
        this.resubmitWindowSeconds = Math.max(1, resubmitWindowSeconds);
    }

    public LegalIntakeClassificationResponse classifyPreview(LegalIntakeClassificationRequest request) {
        String caseType = normalizeCaseType(request.caseType(), request.coreStatement());
        String disputeArea = resolveDisputeArea(caseType, request.coreStatement());
        return new LegalIntakeClassificationResponse(
                caseType, caseTypeLabel(caseType), disputeArea,
                recommendDocuments(caseType, List.of()), clientGuideMessage(caseType, disputeArea),
                "RULE_BASED", false
        );
    }

    /*
     * 화면07 상태 변경.
     * 접수 행을 비관적 잠금으로 잡은 뒤(requireManager) 담당자 권한 -> 현재 상태 -> expectedVersion 순으로 확인하고 저장한다.
     * - 이미 요청한 상태면 버전과 무관하게 200 changed=false, version은 올리지 않는다(통신 오류 후 재전송이 409가 되지 않도록).
     * - expectedVersion이 현재 버전과 다르면 다른 상담자가 먼저 수정한 것이므로 409 INTAKE_VERSION_CONFLICT.
     *   expectedVersion을 생략하면 버전 검사 없이 처리한다(기존 호출 호환, 마지막 요청이 반영됨).
     * - 허용되지 않는 전이(CLOSED 재오픈, SUBMITTED 복귀)는 409 INTAKE_STATUS_TRANSITION_NOT_ALLOWED.
     * - 변경되면 version이 1 올라가고 응답 version은 커밋될 값과 같다(flush 후 읽음).
     */
    @Transactional
    public LegalIntakeStatusResponse updateStatus(Long id, LegalIntakeStatusRequest request, Authentication authentication) {
        LegalIntake intake = access.requireManager(id, authentication);
        IntakeStatus next = IntakeStatus.valueOf(request.status());
        String previousStatus = intake.getStatus();
        if (previousStatus.equals(next.name())) {
            return new LegalIntakeStatusResponse(id, previousStatus, previousStatus, false, intake.getVersion());
        }
        requireExpectedVersion(intake, request.expectedVersion());
        if (!IntakeStatus.valueOf(previousStatus).canChangeTo(next)) {
            throw new ApiConflictException("INTAKE_STATUS_TRANSITION_NOT_ALLOWED",
                    "Cannot change intake status from " + previousStatus + " to " + next,
                    intakeState(intake, Map.of("requestedStatus", next.name())));
        }
        intake.changeStatus(next);
        legalIntakeRepository.flush();
        return new LegalIntakeStatusResponse(id, previousStatus, intake.getStatus(), true, intake.getVersion());
    }

    @Transactional
    public ConsultationMemoResponse createMemo(Long id, ConsultationMemoCreateRequest request, Authentication authentication) {
        return createMemo(id, request, null, authentication).body();
    }

    /*
     * 화면07 상담 메모.
     * - Idempotency-Key 범위는 (문의, 키)이며 그 키를 처음 쓴 작성자의 것이다.
     *   같은 키 + 같은 작성자 + 같은 내용 -> 새로 저장하지 않고 처음 저장한 메모(같은 memoId)를 돌려준다.
     *   같은 키 + 다른 내용 또는 다른 작성자 -> 409 IDEMPOTENCY_KEY_REUSED (남의 메모를 돌려주지 않는다).
     *   서로 다른 키 + 같은 내용 -> 별도 메모로 저장한다.
     * - 키가 없으면 기존 호출 호환용 보조 정책: 같은 작성자·같은 내용이 resubmitWindowSeconds(기본 120초) 안에
     *   다시 오면 재전송으로 본다. 이 시간이 지나면 같은 내용도 새로 저장되므로 영구 멱등 보장이 아니다.
     * - 재전송 판별은 버전 검사보다 먼저 한다(이미 저장된 요청의 재전송이 409가 되지 않도록).
     * - expectedVersion이 다르면 409 INTAKE_VERSION_CONFLICT. 생략하면 버전 검사 없이 저장한다(기존 호출 호환).
     * - 저장하면 접수 version이 1 올라가고 응답 intakeVersion이 그 값이다. 재전송 응답의 intakeVersion은 현재 값이다.
     * 접수 행을 비관적 잠금(SELECT ... FOR UPDATE)으로 잡은 뒤 권한·재전송·버전을 확인하므로
     * 동시에 들어온 재전송·상태 변경·재배정과 직렬화된다. (intake_id, request_key) 유니크 제약은 최후 방어선이다.
     */
    @Transactional
    public IdempotentResult<ConsultationMemoResponse> createMemo(Long id, ConsultationMemoCreateRequest request,
                                                                  String idempotencyKey, Authentication authentication) {
        LegalIntake intake = access.requireManager(id, authentication);
        String key = normalizeIdempotencyKey(idempotencyKey);
        String author = authentication.getName();
        List<String> followUps = request.followUpItems() == null ? List.of() : request.followUpItems().stream().distinct().toList();
        String contentHash = RequestFingerprint.sha256("memo-v1", request.content().trim(), followUps);

        Optional<ConsultationMemo> previous = key != null
                ? consultationMemoRepository.findByIntakeIdAndRequestKey(id, key)
                : consultationMemoRepository.findFirstByIntakeIdAndCreatedByAndContentHashAndCreatedAtAfterOrderByIdDesc(
                        id, author, contentHash, resubmitWindowStart());
        if (previous.isPresent()) {
            ConsultationMemo memo = previous.get();
            if (key != null && (!contentHash.equals(memo.getContentHash()) || !author.equals(memo.getCreatedBy()))) {
                throw idempotencyKeyReused();
            }
            return IdempotentResult.replayed(toMemoResponse(memo, intake.getVersion()));
        }

        requireExpectedVersion(intake, request.expectedVersion());
        ConsultationMemo memo = consultationMemoRepository.save(
                ConsultationMemo.create(id, request.content(), followUps, author, key, contentHash));
        intake.touch();
        legalIntakeRepository.flush();
        return IdempotentResult.created(toMemoResponse(memo, intake.getVersion()));
    }

    private ConsultationMemoResponse toMemoResponse(ConsultationMemo memo) {
        return toMemoResponse(memo, null);
    }

    private ConsultationMemoResponse toMemoResponse(ConsultationMemo memo, Long intakeVersion) {
        return new ConsultationMemoResponse(memo.getId(), memo.getIntakeId(),
                privacyOutputGuardService.mask(memo.getContent()).content(),
                memo.getFollowUpItems(), memo.getCreatedAt(), memo.getCreatedBy(), intakeVersion);
    }

    private void requireExpectedVersion(LegalIntake intake, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != intake.getVersion()) {
            throw new ApiConflictException("INTAKE_VERSION_CONFLICT",
                    "The intake was changed by another user. Reload it and try again.",
                    intakeState(intake, Map.of("expectedVersion", expectedVersion)));
        }
    }

    private Map<String, Object> intakeState(LegalIntake intake, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>(extra);
        details.put("intakeId", intake.getId());
        details.put("currentStatus", intake.getStatus());
        details.put("currentVersion", intake.getVersion());
        return details;
    }

    @Transactional
    public LegalIntakeResponse create(LegalIntakeCreateRequest request, Authentication authentication) {
        return create(request, null, authentication).body();
    }

    /*
     * 화면03 문의 접수.
     * - 화면은 "접수" 버튼을 누를 때 Idempotency-Key를 한 번 만들고, 재시도할 때 같은 키를 다시 보낸다.
     * - 키 범위는 사용자별이다: (owner_username, idempotency_key)당 문의 1건과 그 관련 자료·검색조건 1벌만 저장한다.
     * - 같은 키의 요청 지문(요청 DTO의 모든 필드, intake-v2)이 다르면 409 IDEMPOTENCY_KEY_REUSED, 아무것도 저장하지 않는다.
     * - 재전송 응답 계약: 201 + Idempotent-Replayed: true + 처음 저장된 같은 문의(같은 id)의 "현재" 상세.
     *   최초 응답 스냅샷을 따로 저장하지 않으므로, 그 사이 상담자가 상태를 바꿨다면 바뀐 상태가 보인다.
     *   필수 보장은 문의ID 동일성과 저장 부작용(문의·관련 자료·검색조건) 중복 없음이다.
     * - 키가 없으면 기존처럼 매번 새로 저장한다(같은 내용의 별도 문의를 막지 않기 위해).
     * - 동시에 들어온 재전송은 (owner_username, idempotency_key) 유니크 제약이 막는다. 진 쪽 트랜잭션은 롤백되고,
     *   컨트롤러가 롤백이 끝난 뒤 resolveConcurrentSubmission(새 읽기 트랜잭션)으로 먼저 커밋된 건을 돌려준다.
     */
    @Transactional
    public IdempotentResult<LegalIntakeResponse> create(LegalIntakeCreateRequest request, String idempotencyKey,
                                                        Authentication authentication) {
        String ownerUsername = access.requireUser(authentication);
        String key = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = intakeRequestHash(request);
        if (key != null) {
            Optional<LegalIntake> previous = legalIntakeRepository.findByOwnerUsernameAndIdempotencyKey(ownerUsername, key);
            if (previous.isPresent()) {
                return IdempotentResult.replayed(replayOf(previous.get(), key, request, authentication));
            }
        }

        String caseType = normalizeCaseType(request.caseType(), request.coreStatement());
        String disputeArea = resolveDisputeArea(caseType, request.coreStatement());
        String clientGuideMessage = clientGuideMessage(caseType, disputeArea);

        LegalIntake newIntake = LegalIntake.create(
                defaultValue(request.sessionId(), "anonymous"),
                blankToNull(request.clientName()),
                blankToNull(request.clientPhone()),
                request.inquiryPurpose(),
                caseType,
                request.partyPosition(),
                blankToNull(request.incidentDate()),
                blankToNull(request.counterpart()),
                blankToNull(request.amountOrDamage()),
                blankToNull(request.currentStage()),
                request.coreStatement(),
                request.desiredNextStep(),
                blankToNull(request.preferredRegion()),
                blankToNull(request.lawyerSearchPurpose()),
                disputeArea,
                clientGuideMessage
        );
        newIntake.assignOwner(ownerUsername);
        newIntake.recordSubmission(key, requestHash);
        LegalIntake intake = legalIntakeRepository.saveAndFlush(newIntake);
        List<IntakeDocument> documents = selectedDocuments(request.documents()).stream()
                .map(document -> IntakeDocument.create(intake.getId(), document))
                .toList();
        intakeDocumentRepository.saveAll(documents);

        LawyerSearchCondition condition = lawyerSearchConditionRepository.save(
                LawyerSearchCondition.create(
                        intake.getId(),
                        legalField(caseType),
                        disputeArea,
                        defaultValue(request.preferredRegion(), "지역 미정"),
                        defaultValue(request.officeType(), "사무소 유형 미정"),
                        defaultValue(request.experienceLevel(), "경력 조건 미정"),
                        defaultValue(request.publicCareerPreference(), "공직/수사기관 경력 선호 미정"),
                        defaultValue(request.consultationStyle(), "상담 방식 미정"),
                        DEFAULT_SOURCE_POLICY,
                        buildSearchQuery(caseType, disputeArea, request.preferredRegion())
                )
        );

        return IdempotentResult.created(toIntakeResponse(intake, documents, condition, authentication));
    }

    /** 유니크 제약 충돌(동시 재전송) 뒤 새 트랜잭션에서 먼저 저장된 건을 찾는다. 없으면 null. */
    @Transactional(readOnly = true)
    public IdempotentResult<LegalIntakeResponse> resolveConcurrentSubmission(LegalIntakeCreateRequest request,
                                                                             String idempotencyKey,
                                                                             Authentication authentication) {
        String ownerUsername = access.requireUser(authentication);
        String key = normalizeIdempotencyKey(idempotencyKey);
        if (key == null) {
            return null;
        }
        return legalIntakeRepository.findByOwnerUsernameAndIdempotencyKey(ownerUsername, key)
                .map(intake -> IdempotentResult.replayed(replayOf(intake, key, request, authentication)))
                .orElse(null);
    }

    private LegalIntakeResponse replayOf(LegalIntake intake, String key, LegalIntakeCreateRequest request,
                                         Authentication authentication) {
        if (key != null && !sameIntakeRequest(intake.getRequestHash(), request)) {
            throw idempotencyKeyReused();
        }
        // 계약: 같은 문의(같은 id)의 현재 상세. 최초 응답 스냅샷이 아니다.
        return findById(intake.getId(), authentication);
    }

    /** 요청 DTO의 모든 필드 지문. 해시만 저장하고 원문은 남기지 않는다. */
    public static String intakeRequestHash(LegalIntakeCreateRequest request) {
        return RequestFingerprint.ofRecord("intake-v2", request);
    }

    private static boolean sameIntakeRequest(String storedHash, LegalIntakeCreateRequest request) {
        if (storedHash == null) {
            return false;
        }
        // intake-v1(record toString 기반)은 이 변경 이전에 저장된 행과의 호환용이다.
        return storedHash.equals(intakeRequestHash(request))
                || storedHash.equals(RequestFingerprint.sha256("intake-v1", request));
    }

    private LocalDateTime resubmitWindowStart() {
        return LocalDateTime.now().minusSeconds(resubmitWindowSeconds);
    }

    private String normalizeIdempotencyKey(String key) {
        try {
            return RequestFingerprint.normalizeKey(key);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private ApiConflictException idempotencyKeyReused() {
        return new ApiConflictException("IDEMPOTENCY_KEY_REUSED",
                "This Idempotency-Key was already used for a different request. Generate a new key for new content.",
                Map.of());
    }

    @Transactional(readOnly = true)
    public LegalIntakeResponse findById(Long id, Authentication authentication) {
        LegalIntake intake = access.requireAccess(id, authentication);
        List<IntakeDocument> documents = intakeDocumentRepository.findByIntakeIdOrderById(id);
        LawyerSearchCondition condition = lawyerSearchConditionRepository.findFirstByIntakeIdOrderByIdDesc(id)
                .orElse(null);
        return toIntakeResponse(intake, documents, condition, authentication);
    }

    @Transactional(readOnly = true)
    public List<LegalIntakeResponse> findRecent(Authentication authentication) {
        String username = access.requireUser(authentication);
        List<LegalIntake> visible = access.isAdmin(authentication) ? legalIntakeRepository.findTop50ByOrderByCreatedAtDesc()
                : authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LAWYER"))
                ? legalIntakeRepository.findTop50ByAssignedLawyerUsernameAndOwnerUsernameIsNotNullOrderByCreatedAtDesc(username)
                : legalIntakeRepository.findTop50ByOwnerUsernameOrderByCreatedAtDesc(username);
        return visible.stream()
                .map(intake -> toIntakeResponse(
                        intake,
                        intakeDocumentRepository.findByIntakeIdOrderById(intake.getId()),
                        lawyerSearchConditionRepository.findFirstByIntakeIdOrderByIdDesc(intake.getId()).orElse(null), authentication
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiSearchRequestResponse> findSearchRequests(Long intakeId, Authentication authentication) {
        access.requireAccess(intakeId, authentication);
        return aiSearchRequestRepository.findByIntakeIdOrderByCreatedAtDescIdDesc(intakeId).stream()
                .map(request -> findSearchRequest(request.getId(), authentication)).toList();
    }

    @Transactional
    public AiSearchRequestResponse createSearchRequest(Long intakeId, AiSearchRequestCreateRequest request, Authentication authentication) {
        LegalIntake intake = access.requireAccess(intakeId, authentication);
        LawyerSearchCondition condition = lawyerSearchConditionRepository.findFirstByIntakeIdOrderByIdDesc(intakeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lawyer search condition not found for intake id " + intakeId
                ));

        AiSearchRequest searchRequest = aiSearchRequestRepository.save(AiSearchRequest.create(
                intake.getId(),
                condition.getId(),
                defaultValue(request.searchPurpose(), defaultValue(intake.getLawyerSearchPurpose(), "LAWYER_SEARCH_ROUTING")),
                defaultValue(request.sourcePolicy(), condition.getSourcePolicy()),
                "PREVIEW_READY"
        ));

        return toSearchRequestResponse(searchRequest, condition, List.of());
    }

    @Transactional(readOnly = true)
    public AiSearchRequestResponse findSearchRequest(Long searchRequestId, Authentication authentication) {
        access.requireUser(authentication);
        AiSearchRequest searchRequest = findSearchRequestEntity(searchRequestId);
        access.requireAccess(searchRequest.getIntakeId(), authentication);
        LawyerSearchCondition condition = lawyerSearchConditionRepository.findById(searchRequest.getConditionId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lawyer search condition not found for search request id " + searchRequestId
                ));
        List<SearchResultSnapshot> results = searchResultSnapshotRepository
                .findBySearchRequestIdOrderByDisplayOrder(searchRequestId);
        return toSearchRequestResponse(searchRequest, condition, results);
    }

    @Transactional
    public AiSearchRequestResponse addSearchResult(Long searchRequestId, SearchResultSnapshotRequest request, Authentication authentication) {
        AiSearchRequest searchRequest = aiSearchRequestRepository.findForExecution(searchRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search request not found"));
        access.requireAccess(searchRequest.getIntakeId(), authentication);
        if (searchRequest.isExecuting()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Search execution is running");
        LawyerSearchCondition condition = lawyerSearchConditionRepository.findById(searchRequest.getConditionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search condition not found"));

        int displayOrder = request.displayOrder() == null ? nextDisplayOrder(searchRequestId) : request.displayOrder();
        searchResultSnapshotRepository.save(SearchResultSnapshot.create(
                searchRequestId,
                request.sourceType(),
                request.title(),
                request.url(),
                blankToNull(request.snippet()),
                displayOrder
        ));

        return toSearchRequestResponse(
                searchRequest,
                condition,
                searchResultSnapshotRepository.findBySearchRequestIdOrderByDisplayOrder(searchRequestId)
        );
    }

    /*
     * 화면07 재배정(ADMIN). 상태 변경·메모와 같은 접수 행 잠금을 잡으므로, 이전 담당 LAWYER의 저장 요청과는
     * 잠금 순서대로 직렬화된다: 재배정이 먼저면 이전 담당자의 요청은 잠금 획득 후 권한 확인에서 403, 나중이면 정상 저장.
     * expectedVersion은 받지 않는다(기존 계약 유지). 담당자가 바뀌면 version이 1 오른다.
     */
    @Transactional
    public LegalIntakeResponse assignLawyer(Long id, String lawyerUsername, Authentication authentication) {
        access.requireAdmin(authentication);
        LegalIntake intake = access.requireManager(id, authentication);
        if (intake.getOwnerUsername() == null && lawyerUsername != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Legacy anonymous intake is administrator-only");
        }
        if (lawyerUsername != null) {
            try {
                var lawyer = users.loadUserByUsername(lawyerUsername);
                if (!lawyer.isEnabled() || !lawyer.isAccountNonExpired() || !lawyer.isAccountNonLocked()
                        || lawyer.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_LAWYER"))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active lawyer account");
                }
            } catch (UsernameNotFoundException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select an active lawyer account");
            }
        }
        intake.assignLawyer(lawyerUsername);
        // 응답 version이 커밋될 값과 같도록 @Version 증가를 먼저 반영한다(같은 담당자 재배정은 변경 없음 -> 증가 없음).
        legalIntakeRepository.flush();
        return findById(id, authentication);
    }

    private AiSearchRequest findSearchRequestEntity(Long searchRequestId) {
        return aiSearchRequestRepository.findById(searchRequestId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "AI search request not found: " + searchRequestId
                ));
    }

    private LegalIntakeResponse toIntakeResponse(LegalIntake intake,
                                                 List<IntakeDocument> documents,
                                                 LawyerSearchCondition condition, Authentication authentication) {
        boolean internal = access.canManage(intake, authentication);
        return new LegalIntakeResponse(
                intake.getId(),
                intake.getSessionId(),
                privacyOutputGuardService.maskDisplayName(intake.getClientName()),
                privacyOutputGuardService.mask(intake.getClientPhone()).content(),
                intake.getInquiryPurpose(),
                intake.getCaseType(),
                caseTypeLabel(intake.getCaseType()),
                intake.getPartyPosition(),
                intake.getIncidentDate(),
                privacyOutputGuardService.maskDisplayName(intake.getCounterpart()),
                intake.getAmountOrDamage(),
                intake.getCurrentStage(),
                privacyOutputGuardService.mask(intake.getCoreStatement()).content(),
                intake.getDesiredNextStep(),
                intake.getPreferredRegion(),
                intake.getDisputeArea(),
                documents.stream().map(IntakeDocument::getDocumentType).distinct().toList(),
                recommendDocuments(intake.getCaseType(), documents.stream().map(IntakeDocument::getDocumentType).toList()),
                condition == null ? null : toConditionResponse(condition),
                intake.getClientGuideMessage(),
                "개인정보 입력은 업무 처리를 위해 허용하되, 응답/로그/대시보드 출력에서는 마스킹합니다.",
                intake.getStatus(),
                intake.getCreatedAt(),
                internal ? consultationMemoRepository.findByIntakeIdOrderByCreatedAtDescIdDesc(intake.getId())
                        .stream().map(this::toMemoResponse).toList() : null,
                documents.stream().filter(IntakeDocument::hasFile)
                        .map(com.ohgiraffers.handlermethod.dto.IntakeDocumentResponse::from).toList(),
                internal ? intake.getAssignedLawyerUsername() : null,
                new LegalIntakeResponse.IntakePermissions(internal, access.isAdmin(authentication)),
                intake.getVersion()
        );
    }

    private AiSearchRequestResponse toSearchRequestResponse(AiSearchRequest searchRequest,
                                                            LawyerSearchCondition condition,
                                                            List<SearchResultSnapshot> results) {
        return new AiSearchRequestResponse(
                searchRequest.getId(),
                searchRequest.getIntakeId(),
                searchRequest.getConditionId(),
                searchRequest.getSearchPurpose(),
                searchRequest.getSourcePolicy(),
                searchRequest.getStatus(),
                searchRequest.getGeneratedQuery() == null ? condition.getSearchQuery() : searchRequest.getGeneratedQuery(),
                results.stream().map(this::toSnapshotResponse).toList(),
                searchRequest.getCreatedAt(),
                com.ohgiraffers.handlermethod.dto.SearchExecutionResponse.from(searchRequest)
        );
    }

    private LawyerSearchConditionResponse toConditionResponse(LawyerSearchCondition condition) {
        return new LawyerSearchConditionResponse(
                condition.getId(),
                condition.getIntakeId(),
                condition.getLegalField(),
                condition.getDisputeType(),
                condition.getPreferredRegion(),
                condition.getOfficeType(),
                condition.getExperienceLevel(),
                condition.getPublicCareerPreference(),
                condition.getConsultationStyle(),
                condition.getSourcePolicy(),
                condition.getSearchQuery(),
                condition.getCreatedAt()
        );
    }

    private SearchResultSnapshotResponse toSnapshotResponse(SearchResultSnapshot snapshot) {
        return new SearchResultSnapshotResponse(
                snapshot.getId(),
                snapshot.getSearchRequestId(),
                snapshot.getSourceType(),
                snapshot.getTitle(),
                snapshot.getUrl(),
                snapshot.getSnippet(),
                snapshot.getDisplayOrder(),
                snapshot.getCreatedAt(),
                snapshot.getExplanation()
        );
    }

    private int nextDisplayOrder(Long searchRequestId) {
        return searchResultSnapshotRepository.findBySearchRequestIdOrderByDisplayOrder(searchRequestId).size() + 1;
    }

    private List<String> selectedDocuments(List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of("선택 자료 없음");
        }
        Set<String> cleaned = new LinkedHashSet<>();
        documents.stream()
                .filter(document -> document != null && !document.isBlank())
                .map(String::trim)
                .forEach(cleaned::add);
        if (cleaned.isEmpty()) {
            return List.of("선택 자료 없음");
        }
        return new ArrayList<>(cleaned);
    }

    private List<String> recommendDocuments(String caseType, List<String> selectedDocuments) {
        Set<String> documents = new LinkedHashSet<>(selectedDocuments);
        if ("CIVIL".equals(caseType)) {
            documents.add("계약서 또는 약정서");
            documents.add("입금 내역");
            documents.add("카카오톡/문자 대화");
            documents.add("상대방에게 반환을 요청한 기록");
        } else if ("CRIMINAL".equals(caseType)) {
            documents.add("사건 경위 메모");
            documents.add("사진/영상 자료");
            documents.add("카카오톡/문자 대화");
            documents.add("경찰/공소청 등 수사기관에서 받은 서류");
        } else if ("FAMILY".equals(caseType)) {
            documents.add("가족관계증명서");
            documents.add("혼인관계증명서");
            documents.add("재산 관련 자료");
            documents.add("대화 기록");
        } else if ("ELECTRONIC_LITIGATION".equals(caseType)) {
            documents.add("소장/답변서/보정명령 등 법원 서류");
            documents.add("제출하려는 증거자료");
            documents.add("송달 또는 사건 진행 내역");
        } else {
            documents.add("사건 경위 메모");
            documents.add("관련 계약서 또는 통지서");
            documents.add("입금 내역 또는 대화 기록");
        }
        return new ArrayList<>(documents);
    }

    private String normalizeCaseType(String selectedCaseType, String coreStatement) {
        String selected = normalize(selectedCaseType);
        if (isSupportedCaseType(selected) && !"UNKNOWN".equals(selected)) {
            return selected;
        }

        String text = normalize(coreStatement);
        if (containsAny(text, "고소", "경찰", "공소청", "검찰", "피해자", "피고소", "사기", "폭행", "수사")) {
            return "CRIMINAL";
        }
        if (containsAny(text, "이혼", "양육", "상속", "친권", "가사", "재산분할")) {
            return "FAMILY";
        }
        if (containsAny(text, "전자소송", "소장", "답변서", "보정명령", "제출", "송달")) {
            return "ELECTRONIC_LITIGATION";
        }
        if (containsAny(text, "돈", "대여금", "계약", "임대차", "손해배상", "계약금", "못 받")) {
            return "CIVIL";
        }
        return "UNKNOWN";
    }

    private boolean isSupportedCaseType(String caseType) {
        return "CIVIL".equals(caseType)
                || "CRIMINAL".equals(caseType)
                || "FAMILY".equals(caseType)
                || "ELECTRONIC_LITIGATION".equals(caseType)
                || "UNKNOWN".equals(caseType);
    }

    private String resolveDisputeArea(String caseType, String coreStatement) {
        String text = normalize(coreStatement);
        if ("CIVIL".equals(caseType)) {
            if (containsAny(text, "대여금", "빌려", "못 받", "돈")) {
                return "대여금/금전 반환 분쟁";
            }
            if (containsAny(text, "계약", "계약금", "해지")) {
                return "계약 관련 분쟁";
            }
            if (containsAny(text, "임대차", "보증금", "월세", "전세")) {
                return "임대차 분쟁";
            }
            return "민사 분쟁";
        }
        if ("CRIMINAL".equals(caseType)) {
            if (containsAny(text, "사기")) {
                return "사기 관련 형사 사건";
            }
            if (containsAny(text, "폭행", "상해")) {
                return "폭행/상해 관련 형사 사건";
            }
            return "형사 사건";
        }
        if ("FAMILY".equals(caseType)) {
            if (containsAny(text, "이혼")) {
                return "이혼 관련 가사 사건";
            }
            if (containsAny(text, "양육", "친권")) {
                return "양육/친권 관련 가사 사건";
            }
            return "가사 사건";
        }
        if ("ELECTRONIC_LITIGATION".equals(caseType)) {
            return "전자소송 제출/대응";
        }
        return "분류 대기";
    }

    private String legalField(String caseType) {
        return switch (caseType) {
            case "CIVIL" -> "민사";
            case "CRIMINAL" -> "형사";
            case "FAMILY" -> "가사";
            case "ELECTRONIC_LITIGATION" -> "전자소송";
            default -> "미분류";
        };
    }

    private String caseTypeLabel(String caseType) {
        return switch (caseType) {
            case "CIVIL" -> "민사 사건";
            case "CRIMINAL" -> "형사 사건";
            case "FAMILY" -> "가사 사건";
            case "ELECTRONIC_LITIGATION" -> "전자소송";
            default -> "미분류 사건";
        };
    }

    private String clientGuideMessage(String caseType, String disputeArea) {
        if ("UNKNOWN".equals(caseType)) {
            return "현재 문의는 아직 사건 유형이 명확하지 않습니다. 선택형 항목과 핵심 내용을 조금 더 채우면 분류 정확도가 올라갑니다.";
        }
        return "현재 문의는 " + caseTypeLabel(caseType) + "에 가까워 보이며, "
                + disputeArea + " 가능성이 있습니다. 상담 전 자료를 날짜순으로 정리해 주세요.";
    }

    private String buildSearchQuery(String caseType, String disputeArea, String preferredRegion) {
        return String.join(" ",
                legalField(caseType),
                disputeArea,
                defaultValue(preferredRegion, "서울"),
                "변호사",
                "대한변협",
                "네이버 공개 검색"
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String defaultValue(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
