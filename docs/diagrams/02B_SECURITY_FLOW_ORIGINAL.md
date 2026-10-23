# 02-B 참고. 기존 통합 보안 상세도

복잡한 처리선을 한 그림에 모은 원본이다. 먼저 [간단 지도·API 기능표·분리 상세도](02B_SECURITY_FLOW.md)를 읽고 필요한 경우 이 원본을 확인한다.

```mermaid
flowchart TD
    Request["요청"] --> Cors["Security CORS 처리"]
    Cors -->|"preflight"| CorsResponse["CORS 허용 여부 응답"]
    Cors -->|"실제 요청"| Access{"경로별 인증 규칙 통과?"}
    Access -->|"아니요"| AuthError["401 / 403<br/>업무 처리 이전 중단"]
    Access -->|"공개 경로 또는 인증 통과"| Format{"해당 DTO 입력 검증 통과?"}
    Format -->|"아니요"| Invalid["400 INVALID_REQUEST"]
    Format -->|"예"| Route{"호출 경로"}

    Route -->|"신규 파일·검색 이력"| OwnerCheck{"JWT·소유자 또는 관리자?"}
    OwnerCheck -->|"아니요"| AuthError
    OwnerCheck -->|"파일 업로드"| FileCheck{"크기·허용 유형 통과?"}
    FileCheck -->|"실패"| FileError["400 / 413 / 415"]
    FileCheck -->|"허용"| Business
    OwnerCheck -->|"다운로드·검색 이력"| Business
    Route -->|"일반 업무 API"| Business["해당 백엔드 업무 처리<br/>상세는 02-A 참조"]
    Business --> NonAI["응답 종류에 맞는 출력 처리<br/>Intake·메모·minimal의 지정 필드 마스킹"]
    NonAI --> NonAIResponse["업무 응답"]

    Route -->|"동기 AI Chat / SSE"| Session{"Dynamic preCheck<br/>차단된 세션인가?"}
    Session -->|"예"| Block["정책 차단 응답<br/>blocked=true / fallback=true"]
    Session -->|"아니요"| Fixed{"Fixed Guard 통과?"}
    Fixed -->|"아니요"| Risk["위험 이유·점수 누적<br/>반복 조건에 따라 세션 차단"]
    Fixed -->|"예"| Length{"입력 페이지 제한 통과?"}
    Length -->|"아니요"| Risk
    Length -->|"예"| Input{"NeMo Input 허용?"}
    Input -->|"아니요"| Risk
    Input -->|"기술 오류"| Technical["기술 fallback<br/>blocked=false / fallback=true"]
    Input -->|"예"| Cost{"Cost Control 허용?<br/>세부 흐름은 03 참조"}
    Cost -->|"아니요"| Block
    Cost -->|"동기 Chat 허용"| Model["LLM 처리<br/>성공 시 사용량 정산"]
    Cost -->|"SSE 허용"| StreamModel["LLM 전체 답변 버퍼<br/>Output Guard → Privacy → token 전송<br/>정상 완료 보고/추정 비용 집계"]
    StreamModel -->|"정상"| StreamResponse["SSE 이벤트 응답"]
    StreamModel -->|"출력 정책 차단"| Block
    StreamModel -->|"기술 오류"| Technical
    Model -->|"실패 / 예약 해제"| Technical
    Model -->|"성공"| Output{"NeMo Output 허용?"}
    Output -->|"아니요"| Block
    Output -->|"기술 오류"| Technical
    Output -->|"예"| Privacy["Privacy Output Guard<br/>개인정보 패턴 마스킹"]
    Privacy --> Allowed["정상 AI 응답<br/>blocked=false / fallback=false"]
    Risk --> Block

    Risk --> Observe["관련 reason·차단 metric·trace 로그"]
    Block --> Observe
    Technical --> Observe
    Allowed --> Observe
    Observe --> Inspect["차단 원인·호출 방지·실패 단계 추적"]

    Business -. "설계중" .-> Ownership["역할·문의 소유권 인가 확대"]
    StreamModel -. "보류" .-> SSE["SSE 중단·오류·날짜 변경 정산 보완"]
    classDef planned fill:#fff4dc,stroke:#b7791f,stroke-dasharray:5 5
    class Ownership,SSE planned
```

**현재 코드의 적용 범위**

| 경계 | 현재 적용 |
|---|---|
| JWT | `/api/me`, `/api/ai/**`, GET 이외 `/api/legal/**`, 신규 파일 업로드/다운로드·문의별 검색 이력 |
| 신규 소유자 인가 | 검색 이력·파일 API는 문의 소유자 또는 ROLE_ADMIN. 익명 문의는 관리자만 접근 |
| 공개 API | 로그인, 법률 GET, 신규 파일·이력 경로를 제외한 개발용 `/api/intakes/**`, `/api/admin/**`, `/api/agent/**`, `/api/incidents/**`, 행동 수집 `/api/intake-events` |
| Actuator | 별도 체인. health/info 공개, 그 밖의 Endpoint는 ADMIN Basic |
| DTO 검증 | `@Valid`와 제약이 선언된 API에 적용 |
| Fixed·Dynamic·입력 길이·NeMo Input | 동기·SSE 공통 순서 적용. 차단 이력·Metric·세션 상태 공유 |
| Intake 출력 | 이름·전화·상대방·본문, 메모 본문 등 지정 필드 처리. 모든 DTO·DB·로그에 일괄 적용되는 것은 아님. |
| SSE | 공통 입력 검사 → 비용 예약 → LLM 전체 답변 버퍼 → Output Guard → Privacy → token 전송·정상 완료 집계. 중단 정산은 보류. |
| CORS | application·Actuator 체인에서 공통 source 적용. preflight와 실제 요청의 JWT·ADMIN Basic 유지 자동 검증. |

일반 업무·SSE·동기 AI Chat을 각각의 현재 경로로 구분했다. SSE의 출력 정책 적용 차이는 표를 함께 읽는다. AI 정책·기술 대체 응답은 HTTP 200 안의 플래그·reasonCode·detailCode로 구분하며 기존 reason도 유지한다. 400·404·409 등 업무/입력 오류와 혼동하지 않는다.

‘요청이 어떤 테이블에 저장되는가’는 02-A의 질문이고, ‘이 요청을 허용해야 하는가’는 이 도표의 질문이다. 같은 Service 안에서 연속 실행되어도 설명 목적과 책임은 구분할 수 있다.

코드 근거: [SecurityConfig](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java), [동기 AI 정책 흐름](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AiService.java), [SSE의 별도 구현](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AiStreamingService.java).
