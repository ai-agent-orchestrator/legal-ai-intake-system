# 백엔드 구체적 흐름 3개

2026-10-25 현재 소스 대조. 실행 서버 재검수나 새 유료 호출을 수행한 문서가 아닙니다. 원본 그림에 P03 실행·현재 권한·재전송·버전 정책을 반영했습니다.

## 1. 문의 등록 → 검색 요청 → 실행·결과 → 이력

```mermaid
flowchart TD
    A["C03 POST /api/intakes<br/>LegalIntakeRecordController.create<br/>DTO 검증 · JWT · Idempotency-Key"] --> B["LegalIntakeRecordService.create · 트랜잭션<br/>LegalIntake + 자료 종류 + LawyerSearchCondition 저장<br/>응답 id = intakeId · lawyerSearchCondition.id = conditionId"]
    B --> C["C06 POST /api/intakes/{intakeId}/search-requests<br/>createSearchRequest · 문의 접근 권한·최신 조건 확인<br/>AiSearchRequest 저장 · PREVIEW_READY · id = searchRequestId"]
    C --> D["P03 POST /api/intakes/search-requests/{searchRequestId}/execute<br/>SearchExecutionController → SearchExecutionService.execute<br/>실행권 선점 · 작업 큐 · 202 응답"]
    D --> E["run · SearchLlmService.query → 외부 검색<br/>네이버 변협 도메인 대상 + 일반 웹<br/>결과 저장 → 결과별 LLM 관련성 설명"]
    C --> F["C08 POST /api/intakes/search-requests/{searchRequestId}/results<br/>LegalIntakeRecordService.addSearchResult<br/>호출자가 직접 보낸 결과 저장 · 실행 중이면409"]
    E --> G["SearchResultSnapshot · searchRequestId로 연결<br/>P03 COMPLETED / NO_RESULTS / PARTIAL / FAILED"]
    F --> G
    G --> H["C07 요청 상세 · P02 문의별 요청 이력<br/>조건 + 정렬된 results를 DTO로 조합<br/>화면이 별도로 조회·갱신"]
    B -. "문의 조회" .-> I["C05 상세 / C09 역할별 목록"]
    classDef api fill:#183348,stroke:#68a8ff,color:#e8eff7
    classDef store fill:#173a32,stroke:#70dfb2,color:#e8eff7
    classDef control fill:#3e3521,stroke:#d5af55,color:#e8eff7
    class A,C,D,F,H,I api
    class B,G store
    class E control
```

- **자동 연쇄 실행이 아닙니다.** C03 저장, C06 요청 생성, P03 실행은 각각 별도 요청입니다. C08 수동 저장과 P03 내부 저장도 별도 경로입니다.
- `conditionId`는 서버가 연결하므로 C06 본문으로 다시 보내지 않습니다. 검색 실행·상세·결과 추가에는 `searchRequestId`, 문의별 이력에는 `intakeId`를 사용합니다.
- 조회·검색 접근은 문의 소유자·배정 변호사·ADMIN 범위입니다. P03의 PARTIAL 상태에서 `retryExplanation:true`를 보내면 저장 결과를 유지하며 미완료 설명만 재시도합니다.

## 2. 상태 변경·상담 메모 → 문의 재조회

```mermaid
flowchart TD
    A["C05 상세 / C09 목록<br/>intakeId · 현재 version 확인"] --> B["C10 PATCH /api/intakes/{id}/status<br/>LegalIntakeStatusController.update<br/>status · expectedVersion 검증"]
    A --> C["C11 POST /api/intakes/{id}/memo<br/>ConsultationMemoController.create<br/>content · followUpItems · expectedVersion<br/>헤더 Idempotency-Key"]
    B --> D["LegalIntakeRecordService.updateStatus<br/>requireManager · 문의 행 잠금<br/>배정 LAWYER / ADMIN 권한 확인"]
    D --> E["같은 상태 → 200 · changed=false · 버전 유지<br/>다른 상태 → expectedVersion 대조<br/>IntakeStatus.canChangeTo → changeStatus"]
    E --> F["허용: flush → 200<br/>previousStatus · status · changed · version"]
    E --> X["금지 전이 / 오래된 버전 → 409<br/>CLOSED 재개·SUBMITTED 복귀 금지"]
    C --> G["LegalIntakeRecordService.createMemo<br/>requireManager · 같은 문의 행 잠금<br/>작성자·키·내용 해시로 재전송 판별"]
    G --> H["같은 키·작성자·내용 → 기존 memoId 반환<br/>키 재사용 충돌 / 버전 불일치 → 409<br/>새 요청: memo save · intake.touch · flush"]
    H --> I["PrivacyOutputGuardService.mask → 201<br/>id = memoId · intakeId · intakeVersion<br/>Idempotent-Replayed 응답 헤더"]
    F --> J["화면의 별도 C05 / C09 재조회<br/>최신 상태·버전·허용된 메모 확인<br/>USER DTO에는 내부 메모 제외"]
    I --> J
    classDef api fill:#183348,stroke:#68a8ff,color:#e8eff7
    classDef store fill:#173a32,stroke:#70dfb2,color:#e8eff7
    classDef control fill:#3e3521,stroke:#d5af55,color:#e8eff7
    classDef error fill:#422630,stroke:#ff9eaa,color:#e8eff7
    class A,B,C,F,I,J api
    class D,G store
    class E,H control
    class X error
```

- 상태와 메모는 **각각 별도 트랜잭션**이며 서로를 자동 호출하지 않습니다. 동일 문의 행 잠금으로 상태 변경·메모·재배정을 직렬화합니다.
- `expectedVersion`은 현행 호환 정책상 선택값입니다. 보내야 오래된 편집을409로 검출합니다. 같은 상태와 기존 메모 재전송 판별은 버전 검사보다 먼저 수행합니다.
- 메모 키가 없으면 같은 작성자·같은 내용의 기본120초 재전송 추정만 적용하며 영구 멱등 보장이 아닙니다. C11의201은 새 저장과 기존 응답 재전송 모두에 사용하므로 헤더·ID로 구분합니다.

## 3. AI 동기·SSE → 사유·비용·마스킹·지표

```mermaid
flowchart TD
    A["JWT · DTO 통과 → AiController<br/>S01 /api/ai/chat → AiService.chat<br/>S02 /api/ai/chat/stream → AiStreamingService.stream"] --> B["LegalDynamicGuardService.preCheck<br/>→ LegalFixedGuardService.check<br/>→ AiCostControlService.checkInputPageLimit"]
    B --> C["CostedGuardrailService.checkInput<br/>입력 Guard 비용 예약 → 검사 → 정산"]
    C --> D["AiCostControlService.reserveWithBlock<br/>답변 예산 예약 → LLM 호출 → recordUsage<br/>usage 확인 여부로 실사용·추정 정산 구분"]
    D --> E["CostedGuardrailService.checkOutput<br/>출력 Guard 비용 예약·검사·정산<br/>허용 출력 → PrivacyOutputGuardService.mask"]
    E --> F["S01 JSON / S02 마스킹된 token → done<br/>traceId · reasonCode/detailCode<br/>usageAvailable · costSource는 경로에 따라 제공"]
    B -. "차단 · 후속 단계 중단" .-> X["안전 응답 / blocked / error<br/>발생 사유 반환 · 기술 오류 fallback<br/>입력 차단은 반복 위험에 반영"]
    C -. "정책·예산 차단 / 오류" .-> X
    D -. "예산 차단 / 공급자 오류" .-> X
    E -. "출력 차단 · 생성 비용은 정산" .-> X
    D -. "SSE 종료·timeout·오류" .-> Y["LlmStreamControl.cancel · 중복 종결 방지<br/>usage 있음: 해당 사용량 정산<br/>호출됨·usage 없음: 추정 정산<br/>미호출: 예약 해제"]
    F --> M["AiMetricRecorder · 비용/위험 메모리 상태<br/>C16 GET /actuator/prometheus · ADMIN Basic<br/>Counter/Gauge/Timer → 운영 화면 관측"]
    X --> M
    Y --> M
    classDef api fill:#183348,stroke:#68a8ff,color:#e8eff7
    classDef store fill:#173a32,stroke:#70dfb2,color:#e8eff7
    classDef control fill:#3e3521,stroke:#d5af55,color:#e8eff7
    classDef error fill:#422630,stroke:#ff9eaa,color:#e8eff7
    class A,F,M api
    class D store
    class B,C,E,Y control
    class X error
```

- S02는 공급자 원본 조각을 내부 버퍼에 모은 뒤 **전체 출력 검사·패턴 마스킹 후** 전송합니다. 원본 토큰을 실시간으로 그대로 중계하지 않습니다.
- 출력이 차단돼도 이미 생성한 답변 비용은 남습니다. 입력 Guard·답변·출력 Guard는 각각 비용 처리 단계입니다. C16 조회는 LLM을 호출하지 않습니다.
- `reason`은 기존 호환 필드이며 공통 사유·세부 사유가 항상 같은 의미는 아닙니다. `usageAvailable`·`costSource`가 없는 분기에서 임의의0·실측으로 해석하지 않습니다.
- 초기 화면02~04의 규칙·템플릿 안내와 이 S01·S02 AI 파이프라인은 구분합니다. Prometheus는 지표이고, DB 위험 이력·Incident는 별도 저장·조회 경로입니다.

## 코드 대조 진입점

| 흐름 | 확인한 코드 |
|---|---|
| 등록·검색 | `LegalIntakeRecordController`, `LegalIntakeRecordService.create/createSearchRequest/addSearchResult/findSearchRequest/findSearchRequests`, `SearchExecutionController`, `SearchExecutionService.execute/run` |
| 상태·메모 | `LegalIntakeStatusController`, `ConsultationMemoController`, `IntakeAccessService.requireManager`, `LegalIntakeRecordService.updateStatus/createMemo/toMemoResponse`, `IntakeStatus.canChangeTo` |
| AI·SSE | `AiService.chat`, `AiStreamingService.stream/settleInterrupted/flushCompletedResponse`, `CostedGuardrailService`, `AiCostControlService`, `PrivacyOutputGuardService`, `AiMetricRecorder` |
