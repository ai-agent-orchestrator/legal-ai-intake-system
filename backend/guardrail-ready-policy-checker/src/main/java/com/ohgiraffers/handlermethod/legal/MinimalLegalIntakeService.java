package com.ohgiraffers.handlermethod.legal;

import com.ohgiraffers.handlermethod.riskengine.PrivacyOutputGuardService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class MinimalLegalIntakeService {

    private final PrivacyOutputGuardService privacyOutputGuardService;

    public MinimalLegalIntakeService(PrivacyOutputGuardService privacyOutputGuardService) {
        this.privacyOutputGuardService = privacyOutputGuardService;
    }

    public MinimalLegalIntakeResponse analyze(MinimalLegalIntakeRequest request) {
        String caseType = normalizeCaseType(request.caseType(), request.coreFact());
        String disputeArea = resolveDisputeArea(caseType, request.coreFact());
        List<String> documents = recommendDocuments(caseType, request.documents());
        List<String> checkedFacts = checkedFacts(request);

        return new MinimalLegalIntakeResponse(
                request.sessionId(),
                blankToDefault(request.inquiryPurpose(), "UNSPECIFIED"),
                caseType,
                caseTypeLabel(caseType),
                blankToDefault(request.partyRole(), "UNKNOWN"),
                disputeArea,
                checkedFacts,
                documents,
                blankToDefault(request.desiredNextStep(), "UNSPECIFIED"),
                clientGuideMessage(caseType, disputeArea),
                "개인정보 보호를 위해 이름, 전화번호, 주민등록번호, 계좌번호 등은 답변에 그대로 표시하지 않습니다.",
                "위험 요청은 Fixed Guard에서, 반복 위험 행동은 Dynamic Guard에서, 긴 입력은 Cost Control에서 LLM 호출 전에 통제됩니다."
        );
    }

    private String normalizeCaseType(String selectedCaseType, String coreFact) {
        String selected = normalize(selectedCaseType);
        if (isSupportedCaseType(selected) && !"UNKNOWN".equals(selected)) {
            return selected;
        }

        String text = normalize(coreFact);
        if (containsAny(text, "고소", "경찰", "검찰", "피해자", "피고소", "사기", "폭행", "수사")) {
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

    private String resolveDisputeArea(String caseType, String coreFact) {
        String text = normalize(coreFact);
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

    private List<String> recommendDocuments(String caseType, List<String> selectedDocuments) {
        Set<String> documents = new LinkedHashSet<>();
        if (selectedDocuments != null) {
            selectedDocuments.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(documents::add);
        }

        if ("CIVIL".equals(caseType)) {
            documents.add("계약서 또는 약정서");
            documents.add("입금 내역");
            documents.add("카카오톡/문자 대화");
            documents.add("상대방에게 요청한 기록");
        } else if ("CRIMINAL".equals(caseType)) {
            documents.add("사건 경위 메모");
            documents.add("사진/영상 자료");
            documents.add("카카오톡/문자 대화");
            documents.add("경찰/검찰에서 받은 서류");
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

    private List<String> checkedFacts(MinimalLegalIntakeRequest request) {
        List<String> facts = new ArrayList<>();
        addIfPresent(facts, "사건 발생일", request.incidentDate());
        addIfPresent(facts, "상대방", privacyOutputGuardService.maskDisplayName(request.counterpart()));
        addIfPresent(facts, "금액/피해 규모", request.amountOrDamage());
        addIfPresent(facts, "핵심 내용", privacyOutputGuardService.mask(request.coreFact()).content());
        return facts;
    }

    private void addIfPresent(List<String> facts, String label, String value) {
        if (value != null && !value.isBlank()) {
            facts.add(label + ": " + value);
        }
    }

    private String clientGuideMessage(String caseType, String disputeArea) {
        if ("UNKNOWN".equals(caseType)) {
            return "현재 문의는 아직 사건 유형이 명확하지 않습니다. 선택형 항목과 핵심 내용을 조금 더 채우면 분류 정확도가 올라갑니다.";
        }
        return "현재 문의는 " + caseTypeLabel(caseType) + "에 가까워 보이며, " + disputeArea + " 가능성이 있습니다. 준비 자료를 날짜순으로 정리해 주세요.";
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

    private String blankToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
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

}
