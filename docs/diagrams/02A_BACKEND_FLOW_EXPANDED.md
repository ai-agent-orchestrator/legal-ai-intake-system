# 02-A 부록. 백엔드 세부 처리 흐름

원래의 세부 처리선을 보존한 확대 부록이다. 먼저 [단순 버전·API 번호 지도](02A_BACKEND_FLOW.md)를 읽고 필요한 처리 순서를 이 그림에서 확인한다.

기준: **2026-10-17(Asia/Seoul)**. 번호는 제출 대상 구현 API 36개의 C/R/S/P ID이며, 노드 옆 번호로 README·API 기능표·개별 Postman flow를 연결한다. 번호가 함께 적힌 노드는 업무 묶음이며 API가 자동으로 연속 호출된다는 뜻은 아니다.

읽는 질문: **요청한 API가 어떤 업무를 실행하고, 무엇을 저장·조회해 어떤 응답을 만드는가?** 보안 판단은 02 통합도·02-B 확대도에서, 입력·응답 계약은 개별 API 기능표에서 확인한다.

```mermaid
%%{init: {"themeVariables":{"fontSize":"18px"},"flowchart":{"useMaxWidth":false,"htmlLabels":true}}}%%
flowchart TD
    Request["React / Postman 요청"] --> Kind{"요청 경로"}
    Kind -->|"Actuator"| MetricAPI["지표 조회 C16<br/>ADMIN Basic → Prometheus 텍스트"]
    Kind -->|"application API"| Entry["경로별 인증·CORS<br/>상세 판단은 보안도 참조"]
    Entry --> Controller["Controller → 해당 Request DTO<br/>적용된 입력 제약 검증"]
    Controller --> Route{"요청한 기능 선택"}

    Route --> Login["로그인 C01<br/>계정 확인 → JWT 발급"]
    Route --> Me["내 정보 P01<br/>JWT 사용자·역할 반환"]
    Login --> Response
    Me --> Response
    subgraph Intake["문의 접수·조회·검토"]
        Route --> Create["문의 등록 C03<br/>분류·자료 정리·검색 조건 생성"]
        Create --> IntakeDB[("동일 트랜잭션<br/>legal_intake · intake_document<br/>lawyer_search_condition")]
        IntakeDB --> IntakeDTO["Intake 응답 DTO<br/>자료·조건·메모·파일 메타데이터"]
        Route --> Read["상세 C05 · 목록 C09<br/>문의·자료·조건·메모·파일 조회"]
        Read --> IntakeDTO
        Route --> Review["상태 C10 · 메모 C11<br/>대상 존재·상태 규칙·메모 입력 확인"]
        Review --> ReviewDB[("문의 상태 변경 / consultation_memo")]
        ReviewDB --> Response
        Route --> Preview["분류 미리보기 C02 · 간단 안내 C04<br/>규칙 처리 · DB 저장·LLM 호출 없음"]
        Preview --> Response
        IntakeDTO --> Response
    end
    subgraph Search["검색 요청·결과 기록"]
        Route --> SearchService["생성 C06 · 상세 C07<br/>결과 추가 C08 · 문의별 이력 P02"]
        SearchService --> SearchDB[("ai_search_request<br/>search_result_snapshot")]
        SearchDB --> SearchDTO["검색 요청·조건·정렬된 결과 DTO"]
        SearchDTO --> Response
        SearchService -. "설계중 P03" .-> Execution["외부 검색 실행 P03<br/>도구로 수집 → 결과 저장·설명"]
    end
    Route --> Files["업로드 P04 · 다운로드 P05<br/>JWT·소유자·크기·유형·문의 연결"]
    Files --> FileDB[("파일 메타데이터·BLOB<br/>다운로드 요청 기록")]
    FileDB --> Response
    Route --> Events["행동 이벤트 P06<br/>문의·세션 확인·eventId 중복 방지"]
    Events --> EventDB[("intake_event")]
    EventDB --> Response

    Route --> Reference["절차 목록 R01 · 상세 R02 · 단계 R03<br/>양식 목록 R04 · 상세 R05"]
    Reference --> RefDB[("법률 절차·단계·양식 JPA 조회")]
    RefDB --> Response
    Route --> Checklist["보완수사 체크리스트 R06<br/>고정 prototype · DB 조회 없음"]
    Checklist --> Response
    Route --> AI["동기 Chat S01 · SSE S02<br/>로컬 입력 정책 → Input·LLM·Output<br/>각 유료 단계 비용 예약·정산 → 출력 마스킹"]
    AI --> Response
    Route --> Standalone["입력 단독 검사 S03 · 출력 단독 검사 S04<br/>공유 예산 확인·예약 → 해당 NeMo Client<br/>보고/추정 정산 → 검사 결과 DTO"]
    Standalone --> Response
    Route --> Risk["동기 위험 분석 S05<br/>점수·결정·권장 조치 계산"]
    Risk --> RiskDB[("위험·실행 이력<br/>조건별 Incident 저장")]
    RiskDB --> Response

    Route --> Reporting["운영 요약 C12 · 마케팅 집계 C13<br/>보안 이벤트 C14 · 위험 집계 C15<br/>위험 이력 C17 · Incident 목록 C18"]
    Reporting --> ReadModels[("문의·검색·행동 집계 / Agent 읽기 모델")]
    ReadModels --> Response
    Route --> Incident["Incident 확인 C19 · 종결 C20<br/>대상 조회 → 상태·updatedAt 변경"]
    Incident --> RiskDB

    subgraph Async["기존 보조 Job API · 36개 목록 밖"]
        Route --> Pending["번호 없음 · Job 접수<br/>PENDING 저장 → ID 즉시 응답"]
        Pending --> JobID["HTTP 200 · jobId"]
        Pending --> Executor["가상 스레드 Executor<br/>RUNNING → 동일 위험 분석 Service 사용"]
        Executor --> JobResult[("Job COMPLETED / FAILED<br/>위험·실행 이력 / 조건별 Incident")]
        Route --> Poll["번호 없음 · Job 목록·상태 조회"]
        JobResult --> Poll
        Poll --> Response
        JobID --> Response
    end
    Response["응답<br/>JSON / SSE / 파일 바이트"]
    classDef planned fill:#fff4dc,stroke:#b7791f,stroke-dasharray:5 5
    classDef auxiliary fill:#f4f4f4,stroke:#777
    class Execution planned
    class Pending,JobID,Executor,JobResult,Poll auxiliary
```

**빠진 API 번호**: 구현 36개 중 **없음(36/36 표시)**. 로그인 C01·내 정보 P01·지표 C16·단독 검사 S03/S04도 각각 별도 분기에 표시했다. P03은 설계 위치만 표시한다.

**번호 없는 Job 구간은 무엇인가?**

| 기존 보조 경로 | 역할 | 36개 ID가 없는 이유 |
|---|---|---|
| `POST /api/agent/jobs` | Job 접수·PENDING 저장·ID 반환·Executor 제출 | 제출 대상 36개 밖의 기존 보조 API |
| `GET /api/agent/jobs/{jobId}` | Job 상태·결과 조회 | 같은 보조 API 범위 |
| `GET /api/agent/jobs` | 최근 Job 목록 | 같은 보조 API 범위 |

S05는 `POST /api/agent/risk/analyze`의 **동기** 위험 분석 API다. Job 내부에서 같은 분석 Service를 사용해도 Job 접수·조회 경로를 S05로 바꿔 적지 않는다.

**Service 업무와 적용 규칙**

기능 설명과 API별 업무 규칙은 [본문의 Service 통합 정리](02A_BACKEND_FLOW.md#3-service-업무와-적용-규칙)에서 함께 읽는다. 문의·검색·파일·법률정보·AI·Job·운영·지표의 책임과 현재 구현 범위를 그곳에서 관리하고, 이 확대도에서는 실제 저장·조회·외부 호출 순서를 확인한다.

기본 요청·응답은 JSON이며 S02는 SSE, P05는 파일 바이트, C16은 지표 텍스트다. DB는 기본 H2 또는 별도 mysql 프로필이다. 성공 응답의 실제 원문·마스킹·오류는 API별 계약으로 확인한다.

연결: [API 기능서 flow·기능mapping표](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DRAFT_STATUS_AND_API_SPEC.md), [MySQL 연결 준비서](C:/myLectureWs/legal-ai-intake-security-server/docs/MYSQL_CONNECTION_CHECK.md), [전체 Controller 부록](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_ROUTE_INVENTORY.md).
