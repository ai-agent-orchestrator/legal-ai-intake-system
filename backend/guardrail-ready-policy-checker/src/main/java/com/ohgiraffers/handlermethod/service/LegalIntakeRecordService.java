package com.ohgiraffers.handlermethod.service;

import com.ohgiraffers.handlermethod.dto.AiSearchRequestCreateRequest;
import com.ohgiraffers.handlermethod.dto.AiSearchRequestResponse;
import com.ohgiraffers.handlermethod.dto.ConsultationMemoCreateRequest;
import com.ohgiraffers.handlermethod.dto.ConsultationMemoResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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

    public LegalIntakeRecordService(LegalIntakeRepository legalIntakeRepository,
                                    IntakeDocumentRepository intakeDocumentRepository,
                                    LawyerSearchConditionRepository lawyerSearchConditionRepository,
                                    AiSearchRequestRepository aiSearchRequestRepository,
                                    SearchResultSnapshotRepository searchResultSnapshotRepository,
                                    PrivacyOutputGuardService privacyOutputGuardService,
                                    ConsultationMemoRepository consultationMemoRepository, IntakeAccessService access, UserDetailsService users) {
        this.legalIntakeRepository = legalIntakeRepository;
        this.intakeDocumentRepository = intakeDocumentRepository;
        this.lawyerSearchConditionRepository = lawyerSearchConditionRepository;
        this.aiSearchRequestRepository = aiSearchRequestRepository;
        this.searchResultSnapshotRepository = searchResultSnapshotRepository;
        this.privacyOutputGuardService = privacyOutputGuardService;
        this.consultationMemoRepository = consultationMemoRepository;
        this.access = access; this.users = users;
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

    @Transactional
    public LegalIntakeStatusResponse updateStatus(Long id, LegalIntakeStatusRequest request, Authentication authentication) {
        LegalIntake intake = access.requireManager(id, authentication);
        String previousStatus = intake.getStatus();
        try {
            intake.changeStatus(IntakeStatus.valueOf(request.status()));
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        return new LegalIntakeStatusResponse(id, previousStatus, intake.getStatus(),
                !previousStatus.equals(intake.getStatus()));
    }

    @Transactional
    public ConsultationMemoResponse createMemo(Long id, ConsultationMemoCreateRequest request, Authentication authentication) {
        access.requireManager(id, authentication);
        return toMemoResponse(consultationMemoRepository.save(
                ConsultationMemo.create(id, request.content(), request.followUpItems(), authentication.getName())));
    }

    private ConsultationMemoResponse toMemoResponse(ConsultationMemo memo) {
        return new ConsultationMemoResponse(memo.getId(), memo.getIntakeId(),
                privacyOutputGuardService.mask(memo.getContent()).content(),
                memo.getFollowUpItems(), memo.getCreatedAt(), memo.getCreatedBy());
    }

    @Transactional
    public LegalIntakeResponse create(LegalIntakeCreateRequest request, Authentication authentication) {
        String ownerUsername = access.requireUser(authentication);
        String caseType = normalizeCaseType(request.caseType(), request.coreStatement());
        String disputeArea = resolveDisputeArea(caseType, request.coreStatement());
        String clientGuideMessage = clientGuideMessage(caseType, disputeArea);

        LegalIntake intake = legalIntakeRepository.save(LegalIntake.create(
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
        ));

        intake.assignOwner(ownerUsername);
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

        return toIntakeResponse(intake, documents, condition, authentication);
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
                new LegalIntakeResponse.IntakePermissions(internal, access.isAdmin(authentication))
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
