# 01. 전체 서비스 구성 및 사용자 흐름

**2026-10-22 현행 반영:** P03 실행·화면05 연결 구현, 운영자 React 화면09~13 구현. 화면13은 기존 C16/C17 재사용이며 새 API 번호·DB 테이블을 추가하지 않았다. 구현 API는 37개다. 외부 LLM·네이버 실제 키 호출·청구 검증은 아직 별도다.

기준: **2026-10-17(Asia/Seoul)**. 번호는 제출 대상 구현 API 37개의 C/R/S/P ID이며, 노드 옆 번호로 README·API 기능표·개별 Postman flow를 연결한다. 번호가 함께 적힌 노드는 업무 묶음이며 API가 자동으로 연속 호출된다는 뜻은 아니다.

읽는 질문: **누가 어떤 기능을 사용하고, 각 API가 백엔드·DB·외부 서비스의 어디에 위치하는가?** 파란 노드는 구현된 화면·API 경로, 주황 점선은 RAG 등 향후 확장이다. 화면 API 연결 완료나 외부 NeMo/LLM 실서비스 검증을 뜻하지 않는다.

백엔드 기능은 **3열 × 3단**으로 묶었다. DB·외부 서비스는 아래 연결 영역, **C16은 맨 아래 Metric 조회 영역**에서 확인한다. 박스의 배치는 기능 분류용이며 API 자동 호출 순서가 아니다.

```mermaid
%%{init: {"theme":"base","themeVariables":{"fontSize":"20px","fontFamily":"Malgun Gothic, sans-serif"},"flowchart":{"useMaxWidth":false,"htmlLabels":true,"nodeSpacing":24,"rankSpacing":30,"padding":14}}}%%
flowchart TD
    subgraph Users["사용자 · React 화면"]
        direction LR
        Client["의뢰인 · 화면01~05<br/>로그인·분류·접수·안내·검색"]
        Attorney["변호사 · 화면06~08<br/>문의 목록·상세·상태·메모"]
        Operator["운영자 · 화면09~13<br/>운영·마케팅·보안·감사·AI 위험 요약"]
        Client ~~~ Attorney ~~~ Operator
    end
    Users -->|"React / Postman 개별 요청"| Api["Spring Boot API<br/>경로별 인증 · CORS · DTO · Trace"]
    Api --> Backend
    subgraph Backend["백엔드 기능과 API 위치 · 구현 37개"]
        direction TB
        subgraph Row1["인증 · 문의 · 검색"]
            direction LR
            Account["인증·사용자<br/>로그인 C01 · 내 정보 P01"]
            Intake["규칙 기반 분류·접수·검토<br/>분류 C02 · 안내 C04<br/>등록 C03 · 상세 C05 · 목록 C09<br/>상태 C10 · 메모 C11"]
            Search["검색 기록·외부 실행<br/>생성 C06 · 상세 C07 · 직접 결과 C08<br/>문의별 이력 P02 · 실제 실행 P03<br/>조건 → LLM 검색문 → 검색 → 저장 → LLM 설명"]
            Account ~~~ Intake ~~~ Search
        end
        subgraph Row2["자료 · 법률정보 · AI 질문"]
            direction LR
            Materials["파일·행동 기록<br/>업로드 P04 · 다운로드 P05<br/>행동 이벤트 P06"]
            Reference["법률 Reference<br/>목록 R01 · 상세 R02 · 단계 R03<br/>양식 목록 R04 · 상세 R05<br/>고정 체크리스트 R06"]
            Chat["AI 질문 S01 · SSE S02<br/>입력·생성·출력 Guard별 예약·정산<br/>최종 Privacy 마스킹"]
            Materials ~~~ Reference ~~~ Chat
        end
        subgraph Row3["단독 검사 · 위험 분석 · 운영"]
            direction LR
            Standalone["단독 Guardrail 검사<br/>입력 S03 · 출력 S04<br/>검사 비용도 공통 장부에 합산"]
            RiskAnalyze["Agent 위험 분석 S05<br/>요청별 점수·판정·이력<br/>조건별 Incident 생성"]
            Reporting["운영자 화면09~13<br/>09 C12 · 10 C13 · 11 C14·C15·C16<br/>12 C17·C18·C19·C20<br/>13 C16 지표 + C17 기존 점수·신호"]
            Standalone ~~~ RiskAnalyze ~~~ Reporting
        end
        Row1 ~~~ Row2 ~~~ Row3
    end
    Backend --> Dependencies
    subgraph Dependencies["업무별 저장 · 외부 서비스"]
        direction LR
        Store[("JPA 데이터<br/>문의·자료·조건·검색 결과·설명<br/>파일·행동·Reference·Agent·Incident")]
        External["NeMo / LLM / 네이버 검색<br/>P03: 변협 대상 + 일반 공개 웹<br/>각 LLM 생성 전후 Guard·예산 통제<br/>실제 유료 공급자 검증은 별도"]
        Store ~~~ External
    end
    Backend --> Metrics["Metric · Trace 관측<br/>DB 이력과 구분 · MeterRegistry"]
    Metrics --> Monitor["C16 Prometheus · ADMIN Basic<br/>화면11·13에서 재사용"]
    Monitor --> Observability["화면13 AI 위험 요약<br/>C16 지표 + C17 저장된 요청 점수<br/>요청별 위험 / 대화 누적 위험의 두 기준"]
    Backend -. "향후 확장" .-> Future["RAG / Vector DB / 기간·코호트 분석"]
    classDef implemented fill:#eaf2ff,stroke:#3465a4,color:#102544
    classDef planned fill:#fff4dc,stroke:#b7791f,stroke-dasharray:5 5,color:#5b3b12
    classDef data fill:#edf8ed,stroke:#38803c,color:#173c20
    class Client,Attorney,Operator,Api,Account,Intake,Search,Materials,Reference,Chat,Standalone,RiskAnalyze,Reporting,Metrics,Monitor,Observability implemented
    class Future planned
    class Store,External data
    style Row1 fill:#ffffff,stroke:#c8d6e5
    style Row2 fill:#ffffff,stroke:#c8d6e5
    style Row3 fill:#ffffff,stroke:#c8d6e5
```

[갱신된 기본 보안 흐름·운영자 화면09~13](02B_SECURITY_FLOW_API_MAP.md)

[갱신된 기본 보안 흐름·운영자 화면09~13](02B_SECURITY_FLOW_API_MAP.md)

**API 위치 대조표**

| 그림의 기능 항목 | 표시한 API ID |
|---|---|
| 인증·사용자 | C01, P01 |
| 분류·문의·상담 검토 | C02, C03, C04, C05, C09, C10, C11 |
| 검색 기록·실행 | C06, C07, C08, P02, P03 |
| 실제 파일 | P04, P05 |
| 법률 Reference | R01, R02, R03, R04, R05, R06 |
| AI 질문·단독 검사·위험 분석 | S01, S02, S03, S04, S05 |
| 운영·감사·Incident | C12, C14, C15, C17, C18, C19, C20 |
| 마케팅·행동 | C13, P06 |
| Metric 조회 | C16 |

**빠진 API 번호**: 구현 37개 중 **없음(37/37 표시)**. P03도 검색 실행 구현 수에 포함한다. 화면13은 C16/C17 재사용이므로 새 API 번호를 추가하지 않는다. RAG·코호트는 확장 개념이라 새 API 번호를 만들지 않았다.

**사용자 흐름을 읽는 순서**

1. C01으로 로그인하고 P01로 사용자·역할을 확인한다. 현재 공개 문의 등록은 로그인 없이도 가능하다.
2. C02/C04로 규칙 안내를 확인하거나 C03으로 문의·자료 종류·검색 조건을 저장한다. 안내 호출은 등록의 필수 선행 단계가 아니다.
3. C06으로 검색 요청을 만들고 P03을 명시적으로 실행한다. LLM 검색문→네이버 두 검색→결과 저장→LLM 설명 후 C07/P02로 조회한다. C08 직접 기록도 유지한다.
4. C09/C05 조회에서 C10/C11 검토·메모로 이어지고, 실제 파일은 P04/P05를 사용한다.
5. 운영자는 화면09~13에서 C12~C20의 집계·이력·지표·Incident를 확인한다. 화면13은 C16 지표와 C17 기존 점수·신호를 재사용한다. 문의·마케팅 집계와 Agent·보안 읽기 모델은 다르다.

DB 그림은 물리 DB 여러 개가 아니라 논리 데이터 묶음이다. 기본 H2 메모리와 별도 mysql 프로필의 MySQL을 구분한다. R01~R05는 JPA 조회이고 R06은 고정 prototype이다. Chat Metric이 모든 DB 보안 이벤트에 자동 저장되는 것은 아니다.

근거: [API 기능서 flow·기능mapping표](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DRAFT_STATUS_AND_API_SPEC.md), [화면·API·Controller 매핑](C:/myLectureWs/legal-ai-intake-security-server/docs/SCREEN_API_CONTROLLER_MAPPING.md), [MySQL 연결 준비서](C:/myLectureWs/legal-ai-intake-security-server/docs/MYSQL_CONNECTION_CHECK.md).
