package com.ohgiraffers.handlermethod.legal;

import java.util.List;
import java.util.Locale;

public final class LegalIntakeTemplateCatalog {

    private static final List<LegalIntakeTemplate> TEMPLATES = List.of(
            new LegalIntakeTemplate(
                    LegalIntakeCaseType.CIVIL_LITIGATION,
                    LegalProcedureDomain.CIVIL,
                    "civil-litigation",
                    "민사소송 절차",
                    List.of("민사", "소장", "피고", "청구취지", "청구원인", "답변서", "변론", "판결", "금전", "계약", "손해배상"),
                    List.of("당사자 인적사항", "청구취지", "청구원인", "계약서 또는 거래자료", "입금내역", "문자/카카오톡 등 대화자료", "핵심 증거 목록"),
                    List.of("상대방 인적사항을 알고 있나요?", "청구 금액이나 요구사항이 정리되어 있나요?", "계약서나 거래내역 같은 근거자료가 있나요?", "상대방 주장에 반박할 자료가 있나요?"),
                    List.of("소장 작성 전 청구취지와 청구원인을 정리합니다.", "증거를 시간순으로 묶습니다.", "피고 정보와 송달 가능 주소를 확인합니다.")
            ),
            new LegalIntakeTemplate(
                    LegalIntakeCaseType.CRIMINAL_SUPPLEMENTARY_INVESTIGATION,
                    LegalProcedureDomain.CRIMINAL,
                    "criminal-supplementary-investigation",
                    "형사 보완수사 대응",
                    List.of("형사", "고소", "고소인", "보완수사", "검찰", "송치", "추가 증거", "수사기관", "녹취", "계좌자료"),
                    List.of("고소장 또는 진술서", "보완수사 요구 취지", "시간순 사건표", "메시지/녹취/계좌자료", "쟁점별 증거표", "수사기관 연락 기록"),
                    List.of("보완수사 요구 취지를 확인했나요?", "추가로 제출할 객관 증거가 있나요?", "사건 경과를 시간순으로 정리했나요?", "쟁점별로 증거를 구분했나요?"),
                    List.of("보완수사 취지를 먼저 요약합니다.", "추가 증거 패킷을 만듭니다.", "제출자료 목록과 핵심 주장을 분리해 정리합니다.")
            ),
            new LegalIntakeTemplate(
                    LegalIntakeCaseType.FAMILY_PROCEDURE_AID,
                    LegalProcedureDomain.FAMILY,
                    "family-procedure-aid",
                    "가사 절차구조 제도",
                    List.of("가사", "가정법원", "절차구조", "가사비송", "가족관계", "한부모", "기초연금", "장애인연금", "비용", "재산관계"),
                    List.of("절차구조신청서", "재산관계진술서", "가족관계증명서", "주민등록등본", "수급자/차상위/한부모 등 소명자료", "예금/자동차/부동산 자료"),
                    List.of("절차비용 부담이 어려운 사정이 있나요?", "가족관계 또는 주민등록 자료를 준비할 수 있나요?", "재산관계 자료를 정리했나요?", "지원대상 증명자료가 있나요?"),
                    List.of("무자력 요건을 먼저 확인합니다.", "재산관계진술서와 가족관계 자료를 준비합니다.", "절차구조신청서 작성 가능성을 검토합니다.")
            ),
            new LegalIntakeTemplate(
                    LegalIntakeCaseType.ELECTRONIC_LITIGATION,
                    LegalProcedureDomain.ELECTRONIC_LITIGATION,
                    "electronic-litigation",
                    "전자소송 이용 절차",
                    List.of("전자소송", "전자소송포털", "인증서", "사용자 등록", "소 제기", "전자서명", "사건기록", "송달", "기록열람"),
                    List.of("실명확인 가능한 인증수단", "사건 유형 정보", "제출할 소장 또는 신청서", "증거자료 파일", "사건번호 또는 송달문서"),
                    List.of("전자소송 사용자 등록을 했나요?", "제출할 사건 유형을 알고 있나요?", "증거자료 파일을 준비했나요?", "사건번호나 송달문서를 확인했나요?"),
                    List.of("사용자 등록과 인증수단을 먼저 확인합니다.", "사건 유형에 맞는 문서를 준비합니다.", "전자서명 후 제출하고 사건기록을 확인합니다.")
            )
    );

    private LegalIntakeTemplateCatalog() {
    }

    public static LegalIntakeTemplate match(String caseType, String summary, String claimPurpose, String currentStage) {
        String text = normalize(String.join(" ",
                safe(caseType),
                safe(summary),
                safe(claimPurpose),
                safe(currentStage)
        ));

        return TEMPLATES.stream()
                .map(template -> new TemplateMatch(template, score(template, text, caseType)))
                .filter(match -> match.score() > 0)
                .max((left, right) -> Integer.compare(left.score(), right.score()))
                .map(TemplateMatch::template)
                .orElse(unknown());
    }

    private static int score(LegalIntakeTemplate template, String text, String caseType) {
        int score = 0;

        if (template.caseType().name().equalsIgnoreCase(safe(caseType))) {
            score += 10;
        }

        if (template.domain().name().equalsIgnoreCase(safe(caseType))) {
            score += 6;
        }

        if (template.procedureCode().equalsIgnoreCase(safe(caseType))) {
            score += 10;
        }

        for (String keyword : template.keywords()) {
            if (text.contains(normalize(keyword))) {
                score += 2;
            }
        }

        return score;
    }

    private static LegalIntakeTemplate unknown() {
        return new LegalIntakeTemplate(
                LegalIntakeCaseType.UNKNOWN,
                null,
                "unknown",
                "분류 필요",
                List.of(),
                List.of("사건 분야", "상대방 정보", "요구사항", "증거자료", "현재 진행 단계"),
                List.of("어떤 사건 분야인지에 가까운지 알려주세요.", "상대방이 누구인지 확인되어 있나요?", "원하는 결과나 청구 목적이 무엇인가요?", "증거자료가 있나요?"),
                List.of("기본 사실관계를 먼저 정리합니다.", "사건 분야가 확인되면 관련 절차와 체크리스트를 안내합니다.")
        );
    }

    private static String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private record TemplateMatch(LegalIntakeTemplate template, int score) {
    }
}
