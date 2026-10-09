package com.ohgiraffers.handlermethod.riskengine;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class LegalFixedGuardService {

    private static final String LEGAL_HARM_RESPONSE = """
            해당 요청은 허위 자료, 증거 조작, 또는 법률 절차 악용을 도울 수 있어 처리할 수 없습니다.
            대신 전자소송 제출 전 사실관계, 증거 원본, 제출 목적을 정리하는 방법은 안내할 수 있습니다.
            """;

    private static final String PRIVACY_RESPONSE = """
            상대방의 주민번호, 인증정보, 계정정보 등 개인정보를 알아내거나 우회적으로 조회하는 방법은 안내할 수 없습니다.
            사건번호, 송달문서, 본인이 보유한 자료처럼 적법하게 확인 가능한 정보 중심으로 정리해 주세요.
            """;

    private static final String PROMPT_INJECTION_RESPONSE = """
            시스템 프롬프트, 숨은 지시문, 개발자 메시지, 내부 정책은 제공할 수 없습니다.
            전자소송 절차나 제출 전 체크리스트처럼 공개 가능한 법률 안내 범위의 질문으로 다시 작성해 주세요.
            """;

    private static final String AUTH_BYPASS_RESPONSE = """
            전자서명, 인증, 계정 접근을 우회하는 방법은 안내할 수 없습니다.
            본인 인증과 적법한 권한 확인 절차를 거친 뒤 전자소송 문서 제출 과정을 진행해 주세요.
            """;

    private static final List<String> LEGAL_HARM_PATTERNS = List.of(
            "증거조작", "증거 조작", "증거를조작", "증거를 조작", "증거를유리하게", "증거를 유리하게",
            "증거없애", "증거 없애", "증거삭제", "증거 삭제", "증거은폐", "증거 은폐",
            "허위자료", "허위 자료", "허위문서", "허위 문서", "거짓진술", "거짓 진술",
            "가짜자료", "가짜 자료", "가짜문서", "가짜 문서", "조작하는법", "조작하는 법"
    );

    private static final List<String> PRIVACY_PATTERNS = List.of(
            "주민번호", "주민등록번호", "상대방주민", "상대방 주민", "개인정보 알아내",
            "개인정보를 알아", "주소 알아내", "계정정보", "비밀번호", "인증번호", "otp"
    );

    private static final List<String> PROMPT_INJECTION_PATTERNS = List.of(
            "시스템프롬프트", "시스템 프롬프트", "systemprompt", "system prompt",
            "developer message", "개발자메시지", "개발자 메시지", "숨은지시", "숨은 지시",
            "hidden instruction", "내부정책", "내부 정책", "프롬프트 보여", "instruction 보여"
    );

    private static final List<String> AUTH_BYPASS_PATTERNS = List.of(
            "전자서명 우회", "전자서명우회", "인증 우회", "인증우회", "본인인증 우회", "본인 인증 우회",
            "공동인증서 없이", "인증서 없이", "대리로그인", "대리 로그인", "계정 우회", "계정우회"
    );

    public FixedGuardResult check(String message) {
        String normalized = normalize(message);

        if (containsAny(normalized, PROMPT_INJECTION_PATTERNS)) {
            return FixedGuardResult.block("BLOCKED_PROMPT_INJECTION", PROMPT_INJECTION_RESPONSE);
        }

        if (containsAny(normalized, PRIVACY_PATTERNS)) {
            return FixedGuardResult.block("BLOCKED_PRIVACY_RISK", PRIVACY_RESPONSE);
        }

        if (containsAny(normalized, AUTH_BYPASS_PATTERNS)) {
            return FixedGuardResult.block("BLOCKED_AUTH_BYPASS", AUTH_BYPASS_RESPONSE);
        }

        if (containsAny(normalized, LEGAL_HARM_PATTERNS)) {
            return FixedGuardResult.block("BLOCKED_LEGAL_HARM", LEGAL_HARM_RESPONSE);
        }

        return FixedGuardResult.allow();
    }

    private boolean containsAny(String normalized, List<String> patterns) {
        return patterns.stream()
                .map(this::normalize)
                .anyMatch(normalized::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
