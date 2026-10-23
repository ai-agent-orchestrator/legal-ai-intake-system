# Controller → Service 분기·공유·응답 조합 전체 목록

2026-10-19 현재 코드 정적 대조. 대상은 이번 프로젝트와 과제 Previewer의 **37개 항목 = Controller API 35개 + Actuator C16 + 설계 P03**이다. **Controller 19개와 직접·간접 도달하는 Service 18개**를 확인했다. 기존 흐름도에 그려졌는지와 무관하게 주입 필드·실제 메서드 호출·공통 helper까지 따라갔다.

이 문서의 분기는 업무 메서드·처리 경로가 나뉘는 관계, 공유는 여러 진입점이 같은 Service·규칙·응답 구성으로 모이는 관계다. 한 Service가 여러 자료를 모아 DTO를 만드는 조합, 두 Service를 순서대로 호출하는 경우도 별도로 표시한다. ‘왜’ 설명은 실제 책임과 재사용 관계를 근거로 해석한 설계 이유다. 문자 공백 보정 같은 세부 if는 관련 메서드의 코드로 이어 읽는다.

같은 앞부분 이름의 Controller → Service는 `→ S.메서드()`로 쓴다. 다른 Service와 단독 인용은 전체 이름을 표시한다. 공유는 모든 기능을 한 번에 실행하거나 서로 다른 HTTP 요청을 자동으로 한 트랜잭션에 묶는다는 뜻이 아니다.

## 먼저 잡을 세 가지 대표 모양

```text
같은 AI 질문·답변 업무 · AiController
├─ S01 chat()       → S.chat() → 완성된 JSON 한 번
└─ S02 chatStream() → AiStreamingService.stream() → SSE 이벤트 전달

문의 업무의 여러 HTTP 진입점
├─ 분류 C02
├─ 등록·조회·검색 기록 C03·C05~C09
├─ 상태 변경 C10
├─ 메모 C11
└─ 문의별 검색 이력 P02
   → 모두 LegalIntakeRecordService의 해당 업무 메서드를 공유

P02 한 요청의 순차 조합
IntakeSearchHistoryController.history()
  → ① IntakeAccessService.requireAccess()
  → ② LegalIntakeRecordService.findSearchRequests()
```

S01·S02는 모두 Chat이다. 응답 전달·콜백·연결 종료 책임이 달라 Service가 갈라진다. 문의의 여러 Controller는 입력과 URL을 나누면서 같은 문의의 규칙을 공유한다. P02의 두 Service는 둘 중 하나를 고르는 분기가 아니라 접근 확인 뒤 이력 조회를 실행하는 순서다.

## 1. Controller 19개 전체 - 각 진입점이 어디로 가는가

각 줄은 해당 API가 호출되었을 때의 연결이다. 같은 코드 블록의 다른 줄까지 자동 실행하는 것은 아니다.

### CONT-01. AiController - 응답 방식 분기

```text
AiController
├─ S01 chat() → S.chat()
└─ S02 chatStream() → AiStreamingService.stream()
```

**왜 이렇게 나누거나 묶었나:** S01과 S02는 같은 AI 질문·답변 업무다. JSON 한 번 반환과 SSE 연결·이벤트·중단 관리가 달라 담당 Service가 나뉜다. 입력·비용·출력·Privacy 규칙은 공통 Service를 재사용한다. 현재 SSE는 전체 답변 검사·마스킹 후 안전한 내용을 나누어 보낸다.

코드: [AiController.chat()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AiController.java:28), [AiController.chatStream()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AiController.java:33).

### CONT-02. LegalIntakeClassificationController - 다른 Controller가 문의 Service 공유

```text
LegalIntakeClassificationController
└─ C02 preview() → LegalIntakeRecordService.classifyPreview()
```

**왜 이렇게 나누거나 묶었나:** C02는 문의 저장 전에 사건 분류만 계산한다. C03 등록과 같은 LegalIntakeRecordService의 사건 유형·분쟁 분야 규칙을 재사용한다. 분류 요청만으로 문의가 저장되거나 LLM이 호출되지는 않는다.

코드: [LegalIntakeClassificationController.preview()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeClassificationController.java:22).

### CONT-03. LegalIntakeRecordController - 문의 업무를 한 Service에 묶음

```text
LegalIntakeRecordController
├─ C03 create() → S.create()
├─ C05 findById() → S.findById()
├─ C06 createSearchRequest() → S.createSearchRequest()
├─ C07 findSearchRequest() → S.findSearchRequest()
├─ C08 addSearchResult() → S.addSearchResult()
└─ C09 findRecent() → S.findRecent()
```

**왜 이렇게 나누거나 묶었나:** C03·C05·C09는 등록·상세·목록이고 C06~C08은 문의를 바탕으로 한 검색 기록이다. 같은 문의·검색 조건·결과 조합을 다루므로 한 업무 Service에서 조회·저장·응답 구성 규칙을 공유한다. C06은 실제 외부 검색 실행이 아니라 PREVIEW_READY 기록 생성이다.

코드: [LegalIntakeRecordController.create()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:34), [LegalIntakeRecordController.findById()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:44), [LegalIntakeRecordController.createSearchRequest()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:50), [LegalIntakeRecordController.findSearchRequest()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:56), [LegalIntakeRecordController.addSearchResult()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:62), [LegalIntakeRecordController.findRecent()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:39).

### CONT-04. LegalIntakeStatusController - 상태 진입점을 나누고 문의 Service 공유

```text
LegalIntakeStatusController
└─ C10 update() → LegalIntakeRecordService.updateStatus()
```

**왜 이렇게 나누거나 묶었나:** C10은 상태 변경 전용 URL·DTO를 갖고, 대상 문의 조회와 변경 조율은 문의 Service를 재사용한다. 전이 가능 여부는 LegalIntake.changeStatus → IntakeStatus.canChangeTo에서 판단한다.

코드: [LegalIntakeStatusController.update()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeStatusController.java:23).

### CONT-05. ConsultationMemoController - 메모 진입점을 나누고 문의 Service 공유

```text
ConsultationMemoController
└─ C11 create() → LegalIntakeRecordService.createMemo()
```

**왜 이렇게 나누거나 묶었나:** C11의 HTTP 입력·201 응답은 전용 Controller가 맡는다. 문의 존재 확인·메모 저장·응답 마스킹은 문의 Service에 모여 있다. 상태 변경과 메모 등록은 서로를 자동 호출하지 않는다.

코드: [ConsultationMemoController.create()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/ConsultationMemoController.java:26).

### CONT-06. IntakeSearchHistoryController - 한 요청에서 두 Service를 순차 조합

```text
IntakeSearchHistoryController
└─ P02 history() → IntakeAccessService.requireAccess() → LegalIntakeRecordService.findSearchRequests()
```

**왜 이렇게 나누거나 묶었나:** P02는 Controller가 먼저 IntakeAccessService.requireAccess를 호출하고, 통과하면 LegalIntakeRecordService.findSearchRequests를 호출한다. 이는 둘 중 하나를 고르는 분기가 아니라 접근 확인 → 조회의 순서다. 접근 Service가 조회 Service를 호출하는 구조도 아니다.

코드: [IntakeSearchHistoryController.history()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeSearchHistoryController.java:24).

### CONT-07. MinimalLegalIntakeController - 같은 문의 계열이지만 독립된 계산 Service

```text
MinimalLegalIntakeController
└─ C04 analyze() → S.analyze()
```

**왜 이렇게 나누거나 묶었나:** C04는 저장 없는 간단 안내 DTO를 만드는 별도 MinimalLegalIntakeService를 사용한다. C02와 비슷한 분류 개념이 있지만 현재 분류 구현을 서로 호출하지 않는다. 두 Service가 실제로 공유하는 업무 모듈은 PrivacyOutputGuardService다. 이름에 Intake가 있다고 모든 기능이 하나의 Service에 모인 것은 아니다.

코드: [MinimalLegalIntakeController.analyze()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/MinimalLegalIntakeController.java:20).

### CONT-08. LegalProcedureController - 같은 Reference를 여러 조회 단위로 나눔

```text
LegalProcedureController
├─ R01 procedures() → S.findProcedures()
├─ R02 procedureDetail() → S.findProcedureDetail()
├─ R03 procedureSteps() → S.findProcedureSteps()
├─ R04 forms() → S.findForms()
├─ R05 form() → S.findForm()
└─ R06 supplementaryInvestigationChecklist() → S.supplementaryInvestigationChecklist()
```

**왜 이렇게 나누거나 묶었나:** R01~R06은 절차·단계·양식·체크리스트를 화면이 필요한 크기로 읽는 기능이다. Service 하나에서 Reference 읽기 규칙을 유지한다. R02는 기본 절차·단계·양식을 조합하고 R03~R05는 일부를 따로 조회한다. R06은 고정값이므로 Repository를 거치지 않는다.

코드: [LegalProcedureController.procedures()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:40), [LegalProcedureController.procedureDetail()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:58), [LegalProcedureController.procedureSteps()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:74), [LegalProcedureController.forms()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:86), [LegalProcedureController.form()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:100), [LegalProcedureController.supplementaryInvestigationChecklist()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:111).

### CONT-09. IntakeDocumentController - 같은 파일 업무의 입력·출력 분기

```text
IntakeDocumentController
├─ P04 upload() → S.upload()
└─ P05 download() → S.download()
```

**왜 이렇게 나누거나 묶었나:** P04는 파일 검증·본문 저장, P05는 파일 조회·다운로드 감사 저장·bytes 반환이다. 같은 문의 파일과 접근 규칙을 공유하므로 IntakeDocumentService에 묶는다. 두 메서드는 각 요청에서 IntakeAccessService를 재사용한다.

코드: [IntakeDocumentController.upload()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeDocumentController.java:25), [IntakeDocumentController.download()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeDocumentController.java:32).

### CONT-10. IntakeEventController - 단일 진입점 안의 신규·중복·충돌 분기

```text
IntakeEventController
└─ P06 record() → S.record()
```

**왜 이렇게 나누거나 묶었나:** P06은 eventId 기준 행동 보고 업무다. 새 ID는 저장, 같은 내용 재전송은 기존 결과, 같은 ID의 다른 내용은 충돌로 나뉜다. 신규·재시도를 같은 Service에서 처리해 중복 보고가 집계를 부풀리지 않게 한다.

코드: [IntakeEventController.record()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeEventController.java:18).

### CONT-11. LegalGuardrailController - 공통 검사 Service의 입력 쪽 진입점

```text
LegalGuardrailController
└─ S03 checkInput() → CostedGuardrailService.checkInput()
```

**왜 이렇게 나누거나 묶었나:** S03은 입력 검사 전용 Controller이며 CostedGuardrailService.checkInput을 호출한다. S01·S02도 같은 Service 메서드를 내부 호출한다. 단독 검사와 Chat 내부 검사에서 예약·정산 규칙을 공유한다.

코드: [LegalGuardrailController.checkInput()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalGuardrailController.java:26).

### CONT-12. LegalOutputGuardrailController - 공통 검사 Service의 출력 쪽 진입점

```text
LegalOutputGuardrailController
└─ S04 checkOutput() → CostedGuardrailService.checkOutput()
```

**왜 이렇게 나누거나 묶었나:** S04는 출력 검사 전용 Controller이며 CostedGuardrailService.checkOutput을 호출한다. 입력과 출력은 검사 대상·Client가 다르지만 비용 생애는 공통화했다. 로컬 출력 정책으로 즉시 차단되는 경우 외부 검사·비용 예약을 건너뛴다. 전체 Chat의 Privacy 마스킹과는 별도다.

코드: [LegalOutputGuardrailController.checkOutput()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalOutputGuardrailController.java:26).

### CONT-13. AgentRiskDecisionController - 분석·쓰기 책임의 독립 Service

```text
AgentRiskDecisionController
└─ S05 analyze() → S.analyze()
```

**왜 이렇게 나누거나 묶었나:** S05는 입력 신호로 점수·판정·권장 조치를 계산하고 위험·실행 이력을 저장한다. 조회·집계는 AgentRiskHistoryQueryService, 조건별 Incident 생성·상태는 RiskIncidentService로 나눈다. 분석 결과의 권장 조치가 외부 도구의 실제 실행을 자동 차단한다는 뜻은 아니다.

코드: [AgentRiskDecisionController.analyze()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskDecisionController.java:22).

### CONT-14. AgentRiskHistoryController - 같은 위험 이력의 목록·집계 분기

```text
AgentRiskHistoryController
├─ C15 summary() → AgentRiskHistoryQueryService.summarize()
└─ C17 history() → AgentRiskHistoryQueryService.findRecent()
```

**왜 이렇게 나누거나 묶었나:** C17은 조건에 맞는 이력 목록, C15는 전체 이력의 등급·판정·권장 조치 집계다. 같은 저장 이력을 읽으므로 QueryService에 묶고 S05 분석·쓰기와 책임을 나눈다. C17에 riskLevel과 decision을 함께 주면 riskLevel이 우선한다.

코드: [AgentRiskHistoryController.summary()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java:30), [AgentRiskHistoryController.history()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java:24).

### CONT-15. RiskIncidentController - 한 Incident의 생애주기를 묶음

```text
RiskIncidentController
├─ C18 findRecent() → S.findRecent()
├─ C19 acknowledge() → S.acknowledge()
└─ C20 resolve() → S.resolve()
```

**왜 이렇게 나누거나 묶었나:** C18 목록·C19 확인·C20 종결은 같은 RiskIncident를 읽고 바꾸는 업무다. 같은 Service가 S05의 조건별 생성과 C12의 열린 건수 조회도 담당한다. 문의 C10의 상태 전이 규칙이 Incident에도 자동 적용되는 것은 아니다.

코드: [RiskIncidentController.findRecent()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:24), [RiskIncidentController.acknowledge()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:29), [RiskIncidentController.resolve()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:34).

### CONT-16. AdminDashboardController - 운영 화면의 요약·상세 조회 묶음

```text
AdminDashboardController
├─ C12 summary() → S.summary()
└─ C14 securityEvents() → S.recentSecurityEvents()
```

**왜 이렇게 나누거나 묶었나:** C12는 실행·위험·보안 이력 수, 열린 Incident 수, 위험 집계를 한 응답으로 조합한다. C14는 보안 이벤트 상세 목록이다. 운영 화면용 조합은 AdminDashboardService에 두고, 위험 집계·Incident 건수는 각 담당 Service를 재사용한다.

코드: [AdminDashboardController.summary()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java:23), [AdminDashboardController.securityEvents()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java:28).

### CONT-17. MarketingDashboardController - 마케팅 읽기 모델로 데이터가 모임

```text
MarketingDashboardController
└─ C13 funnel() → S.funnel()
```

**왜 이렇게 나누거나 묶었나:** C13은 문의·검색 요청·행동 이벤트를 하나의 MarketingFunnelResponse로 조합한다. 운영·보안 C12와 화면 이름은 비슷해도 집계 대상과 해석이 달라 별도 Service다. 저장 행 수·DISTINCT 문의 수·현재 상태·보고 횟수를 구분한다.

코드: [MarketingDashboardController.funnel()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/MarketingDashboardController.java:19).

### CONT-18. AuthController - 업무 Service 없이 인증·토큰을 순차 조합

```text
AuthController
└─ C01 login() → AuthenticationManager.authenticate() → JwtTokenProvider.createToken()
```

**왜 이렇게 나누거나 묶었나:** C01은 AuthenticationManager.authenticate → JwtTokenProvider.createToken 순서다. 사용자 인증과 토큰 발급의 서로 다른 책임을 조합하며 AuthService라는 클래스는 없다.

코드: [AuthController.login()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/AuthController.java:27).

### CONT-19. CurrentUserController - 업무 Service 없이 인증 결과를 읽음

```text
CurrentUserController
└─ P01 me() → 별도 Service 없이 Authentication → 응답 DTO
```

**왜 이렇게 나누거나 묶었나:** P01은 이미 검증된 Authentication의 사용자·역할을 응답으로 만든다. 별도 조회 업무·DB 읽기가 없어 CurrentUserService를 만들지 않았다.

코드: [CurrentUserController.me()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/CurrentUserController.java:10).

## 2. 여러 호출자가 한곳으로 모이는 Service 9개

Controller에서 직접 모이는 경우와 Service 내부에서 모이는 경우를 모두 포함한다. 아래 부모 줄의 API는 그 Service에 도달할 수 있는 상위 진입점을 뜻하며, 모든 분기에서 반드시 실행된다는 뜻은 아니다.

### SHARE-01. LegalIntakeRecordService

관련 API: C02, C03, C05, C06, C07, C08, C09, C10, C11, P02.

```text
LegalIntakeRecordService ← 공통 처리로 모이는 곳
├─ LegalIntakeClassificationController → classifyPreview()
├─ LegalIntakeRecordController → addSearchResult(), create(), createSearchRequest(), findById(), findRecent(), findSearchRequest()
├─ LegalIntakeStatusController → updateStatus()
├─ ConsultationMemoController → createMemo()
└─ IntakeSearchHistoryController → findSearchRequests()
```

**왜 공통으로 묶었나:** 같은 문의와 그 문의의 조건·검색 기록을 다룬다. Controller는 분류·등록·상태·메모·이력이라는 HTTP 진입점을 나누고 업무 규칙과 응답 구성은 공유한다.

코드: [LegalIntakeRecordService.classifyPreview()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:70).

### SHARE-02. CostedGuardrailService

관련 API: S01, S02, S03, S04.

```text
CostedGuardrailService ← 공통 처리로 모이는 곳
├─ AiService → checkInput(), checkOutput()
├─ AiStreamingService → checkInput(), checkOutput()
├─ LegalGuardrailController → checkInput()
└─ LegalOutputGuardrailController → checkOutput()
```

**왜 공통으로 묶었나:** 독립 입력·출력 검사와 Chat 내부 검사에서 비용 예약 → 외부 검사 → 보고/추정 정산이 같아 네 호출자가 재사용한다. S01·S02가 S03·S04 URL을 다시 HTTP 호출하는 것은 아니다.

코드: [CostedGuardrailService.checkInput()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/cost/CostedGuardrailService.java:48).

### SHARE-03. AiCostControlService

관련 API: S01, S02, S03, S04.

```text
AiCostControlService ← 공통 처리로 모이는 곳
├─ AiService → checkInputPageLimit(), recordUsage(), reserveWithBlock()
├─ CostedGuardrailService → recordUsage(), reserveStageWithBlock()
└─ AiStreamingService → checkInputPageLimit(), recordUsage(), release(), reserveWithBlock()
```

**왜 공통으로 묶었나:** 입력 검사·답변·출력 검사 비용을 같은 예산과 예약·정산 규칙으로 판단한다. 경로마다 별도 계산기를 두어 예산이 어긋나지 않도록 공통화한 구조다. 실제 사용 상태는 메모리이며 영속 청구 원장은 아니다.

코드: [AiCostControlService.checkInputPageLimit()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/cost/AiCostControlService.java:108).

### SHARE-04. PrivacyOutputGuardService

관련 API: C03, C04, C05, C09, C11, S01, S02.

```text
PrivacyOutputGuardService ← 공통 처리로 모이는 곳
├─ LegalIntakeRecordService → mask(), maskDisplayName()
├─ MinimalLegalIntakeService → mask(), maskDisplayName()
├─ AiService → mask()
└─ AiStreamingService → mask()
```

**왜 공통으로 묶었나:** 동기·SSE AI 출력, 문의 응답, 간단 안내 응답의 개인정보 보호 규칙을 재사용한다. 지정된 응답 필드를 마스킹하며 저장된 원문 전체를 자동 수정하는 처리는 아니다.

코드: [PrivacyOutputGuardService.mask()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/riskengine/PrivacyOutputGuardService.java:26).

### SHARE-05. IntakeAccessService

관련 API: P02, P04, P05.

```text
IntakeAccessService ← 공통 처리로 모이는 곳
├─ IntakeSearchHistoryController → requireAccess()
└─ IntakeDocumentService → requireAccess()
```

**왜 공통으로 묶었나:** P02·P04·P05에서 문의 존재·소유자/관리자 판단을 일관되게 적용한다. P02는 Controller에서, P04·P05는 파일 Service에서 호출한다. 공유 사실만으로 C05·C09 등에 같은 소유권 검사가 적용되지는 않는다.

코드: [IntakeAccessService.requireAccess()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeAccessService.java:16).

### SHARE-06. LegalFixedGuardService

관련 API: S01, S02.

```text
LegalFixedGuardService ← 공통 처리로 모이는 곳
├─ AiService → check()
└─ AiStreamingService → check()
```

**왜 공통으로 묶었나:** S01·S02가 같은 현재 입력의 고정 위험 규칙을 적용한다. JSON과 SSE의 응답 통로가 달라도 같은 입력 판단을 재사용한다.

코드: [LegalFixedGuardService.check()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/riskengine/LegalFixedGuardService.java:99).

### SHARE-07. LegalDynamicGuardService

관련 API: S01, S02.

```text
LegalDynamicGuardService ← 공통 처리로 모이는 곳
├─ AiService → preCheck(), recordBlockedEvent()
└─ AiStreamingService → preCheck(), recordBlockedEvent()
```

**왜 공통으로 묶었나:** S01·S02가 정규화된 sessionId를 기준으로 동일한 세션 위험 상태와 누적 규칙을 사용한다. 한 경로에서 누적된 위험을 다른 경로에서도 확인할 수 있다. S03·S04의 단독 검사에는 자동으로 이 누적 처리가 붙지 않는다.

코드: [LegalDynamicGuardService.preCheck()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/riskengine/LegalDynamicGuardService.java:33).

### SHARE-08. AgentRiskHistoryQueryService

관련 API: C12, C15, C17.

```text
AgentRiskHistoryQueryService ← 공통 처리로 모이는 곳
├─ AdminDashboardService → summarize()
└─ AgentRiskHistoryController → findRecent(), summarize()
```

**왜 공통으로 묶었나:** C15 위험 집계와 C12 운영 요약의 위험 집계가 같은 summarize를 사용한다. C17 목록도 같은 위험 이력을 읽는다. 집계 기준의 중복 구현을 줄이고 읽기 책임을 분석·쓰기에서 나눈다.

코드: [AgentRiskHistoryQueryService.summarize()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskHistoryQueryService.java:41).

### SHARE-09. RiskIncidentService

관련 API: C12, C18, C19, C20, S05.

```text
RiskIncidentService ← 공통 처리로 모이는 곳
├─ AdminDashboardService → countOpen()
├─ RiskIncidentController → acknowledge(), findRecent(), resolve()
└─ AgentRiskDecisionService → createIfNeeded()
```

**왜 공통으로 묶었나:** S05 조건별 생성, C18~C20 목록·상태 변경, C12 열린 건수 조회가 모두 같은 Incident의 생애와 상태를 다룬다. 생성·조회·변경의 규칙과 저장 대상을 한곳에서 유지한다.

코드: [RiskIncidentService.countOpen()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java:69).

## 3. Service 내부에서 나뉘거나 다시 모이는 업무 흐름

### INNER-01. 사건 분류를 미리보기와 실제 등록이 공유

```text
C02 분류 미리보기 ─┐
                  ├→ normalizeCaseType / resolveDisputeArea / 추천 자료·안내
C03 실제 문의 등록 ─┘
                  ├─ C02: 분류 응답만 반환
                  └─ C03: 문의·자료 종류·검색 조건 저장
```

**왜:** 같은 입력을 미리 본 결과와 실제 등록의 사건 분류 기준을 맞춘다. C04 MinimalLegalIntakeService에도 유사한 분류 코드가 있지만 현재 이 분류 helper를 공유하는 호출은 아니다. 같은 개념·같은 저장 테이블·이름 유사성만으로 공통 Service라고 판단하지 않는다. 근거: [LegalIntakeRecordService.classifyPreview()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:70), [LegalIntakeRecordService.create()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:107), [MinimalLegalIntakeService.analyze()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/MinimalLegalIntakeService.java:21).

### INNER-02. 문의 상세·목록·등록 응답이 같은 조합으로 모임

```text
C03 등록 결과 ─┐
C05 상세 조회 ─┼→ toIntakeResponse()
C09 목록 조회 ─┘  ├─ 문의 정보 + 자료 종류 + 최신 검색 조건
                 ├─ 상담 메모 + 업로드 파일 메타데이터
                 └─ 지정 개인정보 필드를 마스킹한 응답
```

**왜:** 같은 문의를 어느 화면에서 읽어도 응답 구조와 보호 규칙을 맞춘다. 메모 C11의 단독 응답과 상세·목록에 포함된 메모도 toMemoResponse를 공유한다. 저장된 원문을 일괄 수정하는 처리가 아니다. 근거: [LegalIntakeRecordService.toIntakeResponse()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:256), [LegalIntakeRecordService.toMemoResponse()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:100).

### INNER-03. 검색 기록의 여러 진입점이 같은 검색 응답으로 모임

```text
C06 요청 생성 ─────────────┐
C07 요청 상세 ─────────────┼→ toSearchRequestResponse()
C08 결과 스냅샷 저장 ──────┘  → 요청 + 검색 조건 + 정렬된 결과
P02 문의별 이력 → findSearchRequests() → 각 findSearchRequest() 재사용
```

**왜:** 요청 생성·조회·결과 추가가 같은 검색 기록 모델을 반환한다. P02에서 findSearchRequest를 호출하는 것은 내부 Java 메서드 호출이며 C07 URL을 다시 요청하는 것이 아니다. C08의 displayOrder가 없으면 현재 결과 수 + 1로 채운다. C06·C08은 실제 외부 검색을 실행하거나 검색 완료 상태로 자동 전환하지 않는다. 근거: [LegalIntakeRecordService.toSearchRequestResponse()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:290), [LegalIntakeRecordService.findSearchRequests()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:176), [LegalIntakeRecordService.addSearchResult()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:215), [LegalIntakeRecordService.nextDisplayOrder()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:336).

### INNER-04. C10은 조회·정책·변경 결과로 나뉨

```text
문의 조회 → Service.updateStatus → Entity.changeStatus → IntakeStatus.canChangeTo
├─ 같은 상태: 허용 · changed=false
├─ 허용된 다른 상태: 변경 · changed=true
└─ CLOSED 이탈 / SUBMITTED 복귀: 금지 · 409
```

**왜:** Service는 대상 조회·업무 조율·HTTP 오류 변환을 맡고, 상태 자체의 전이 규칙은 Entity·Enum에 둔다. 문의 없음은 조회 단계의 404다. Incident의 상태 변경에 이 규칙이 자동 공유되지는 않는다. 근거: [LegalIntakeRecordService.updateStatus()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:81), [LegalIntake.changeStatus()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/entity/LegalIntake.java:238), [IntakeStatus.canChangeTo()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/IntakeStatus.java:10).

### INNER-05. Reference는 전체·부분 조회로 갈라지고 상세에서 다시 조합

```text
R01: domain 없음 → 전체 / 있음 → 분야별 조회
R02: 절차 + 단계 + 양식 → 상세 DTO 하나
R03: 절차 존재 확인 → 단계만
R04: procedure 없음 → 전체 양식 / 있음 → 그 절차의 양식
R05: formCode로 양식 한 개
R06: 고정 안내 → DB 조회 없이 반환
```

**왜:** 동일 Reference를 화면이 필요한 크기로 제공한다. R02·R03·R04의 해당 경로는 findProcedure를 공유하며, 없는 code의 예외도 같은 helper에서 발생한다. R06이 같은 Service에 있어도 모든 요청에서 Repository를 쓰는 것은 아니다. 근거: [LegalProcedureService.findProcedureDetail()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:85), [LegalProcedureService.findProcedureSteps()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:112), [LegalProcedureService.findForms()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:128), [LegalProcedureService.findProcedure()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:194).

### INNER-06. P04·P05는 파일 생애를 묶고 접근 검사를 공유

```text
P04 upload → 공통 접근 검사 → 파일 크기·이름·종류·내용 검사 → BLOB·메타데이터 저장
P05 download → 공통 접근 검사 → 문의에 속한 실제 파일 확인 → 다운로드 감사 저장 → bytes·헤더
```

**왜:** 같은 문의 파일의 입력·출력을 한곳에서 다루면서 권한 규칙은 IntakeAccessService로 분리한다. 입력 검사 실패는 400·413·415 등의 해당 경로로 끝나며 잘못된 파일을 저장하지 않는다. C03의 자료 종류 선택과 실제 파일 업로드는 서로 다른 처리다. 근거: [IntakeDocumentService.upload()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeDocumentService.java:41), [IntakeDocumentService.download()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeDocumentService.java:79), [IntakeAccessService.requireAccess()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeAccessService.java:16).

### INNER-07. P06 신규·재시도·충돌이 한 중복 처리로 모임

```text
eventId 조회
├─ 새 ID → saveAndFlush → 201 · created=true
├─ 기존 ID + 같은 내용 → duplicate() → 200 · created=false
└─ 기존 ID + 다른 내용 → duplicate() → 409
동시 저장의 유일키 충돌 → 재조회 → 같은 duplicate()로 합류
```

**왜:** 같은 행동 보고가 재전송돼도 같은 규칙으로 판정하고 중복 집계를 막는다. SUMMARY_VIEWED는 intakeId가 필요하고, 문의 ID가 있으면 문의 존재·sessionId 일치를 먼저 확인한다. sessionId 일치는 로그인 사용자 소유권 검사와 같은 기능이 아니다. 근거: [IntakeEventService.record()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeEventService.java:26), [IntakeEventService.duplicate()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeEventService.java:51).

### INNER-08. S01·S02의 성공 경로와 조기 종료

```text
세션 Dynamic → 현재 입력 Fixed → 길이 검사 → 외부 입력 검사 → 답변 예약·LLM → 출력 검사 → Privacy
├─ 통과: S01 JSON / S02 안전한 SSE 조각과 done
├─ 정책·예산 차단: 사유를 담은 안전 응답 또는 blocked
└─ 외부·연결 오류: fallback / error · 필요한 비용 정산
```

**왜:** 검사들은 순서와 선행 결과가 필요한 단계다. 모든 Service를 동시에 실행하는 분기라고 읽지 않는다. 두 Chat Service는 전달 방식을 나누면서 같은 Guard·Cost·Privacy를 공유한다. 외부 출력 검사를 통과한 뒤 개인정보 마스킹을 수행한다. S02의 원문 조각은 pending에 모이고 전체 검사 후에 전송된다. 근거: [AiService.chat()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AiService.java:67), [AiStreamingService.stream()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AiStreamingService.java:64), [AiStreamingService.flushCompletedResponse()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AiStreamingService.java:243).

### INNER-09. 입력·출력 검사는 다른 Client, 비용 생애는 공통

```text
CostedGuardrailService
├─ checkInput → 공통 check(INPUT_GUARDRAIL, InputGuardrailClient)
└─ checkOutput
   ├─ 로컬 출력 정책 차단 → 즉시 결과 · 외부 예약·호출 생략
   └─ 그 외 → 공통 check(OUTPUT_GUARDRAIL, OutputGuardrailClient)

공통 check → 예약 → 외부 검사 → 보고/추정 정산 → 검사 결과
```

**왜:** 입력과 출력의 대상·검사 API는 다르지만 비용과 오류 정산의 생애는 같다. 검사 Client의 차이는 유지하고 같은 비용 처리를 generic check로 모았다. 근거: [CostedGuardrailService.checkInput()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/cost/CostedGuardrailService.java:48), [CostedGuardrailService.checkOutput()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/cost/CostedGuardrailService.java:53), [CostedGuardrailService.check()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/cost/CostedGuardrailService.java:62).

### INNER-10. 비용은 세 단계에서 한 예약·정산 규칙으로 모임

```text
입력 검사 · 답변 · 출력 검사
  → reserveStageWithBlock
  ├─ 예산 부족 → 해당 단계 외부 호출 전 차단
  └─ 예약 허용 → 외부 호출
     ├─ usage 확인 → REPORTED 정산
     └─ usage 미확인 → ESTIMATED 정산
S02 중단: 외부 호출 전 → 예약 해제 / 외부 호출 후 usage 미확인 → 추정 정산
```

**왜:** 여러 호출 경로의 사용액·예약액을 같은 전체·하루·세션 예산으로 판단한다. recordUsage의 예약 종료 플래그가 중복 정산을 막는다. 앞 단계의 발생 비용은 뒤 단계 차단으로 자동 취소되지 않는다. 근거: [AiCostControlService.reserveStageWithBlock()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/cost/AiCostControlService.java:155), [AiCostControlService.recordUsage()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/cost/AiCostControlService.java:194), [AiStreamingService.settleInterrupted()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AiStreamingService.java:208).

### INNER-11. S05는 신호·점수·판정·권장 조치가 단계적으로 갈라짐

```text
요청의 플래그 + 입력·도구 이름 규칙
  → 위험 신호 모음 + 점수 합산(최대 100)
  ├─ 등급: 80 이상 HIGH / 40 이상 MEDIUM / 그 외 LOW
  ├─ 판정: 복합 신호의 우선순위 → SUSPICIOUS / SAFE 등
  └─ 권장 조치: 판정 우선, 이어 등급 → BLOCK / REQUIRE_APPROVAL / CREATE_INCIDENT / ALLOW 등
  → 위험 이력 + 실행 이력 저장 → 조건별 Incident 생성
```

**왜:** 신호·위험 등급·판정·권장 조치는 같은 값이 아니므로 계산 책임을 메서드로 나눴다. policy violation·external call·retry 등의 조합이 decision의 우선 분기에 영향을 준다. 계산 결과로 실제 외부 API나 DB 작업을 실행하는 Agent는 아니다. 근거: [AgentRiskDecisionService.analyze()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskDecisionService.java:42), [AgentRiskDecisionService.decision()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskDecisionService.java:157), [AgentRiskDecisionService.riskLevel()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskDecisionService.java:191), [AgentRiskDecisionService.recommendedAction()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskDecisionService.java:203).

### INNER-12. 위험 이력은 분석·목록·집계로 분리하고 집계를 재사용

```text
S05: AgentRiskDecisionService → 분석·저장
C17: AgentRiskHistoryQueryService.findRecent
     ├─ riskLevel 있음 → 등급 필터
     ├─ 그 외 decision 있음 → 판정 필터
     └─ 둘 다 없음 → 최근 50개
C15와 C12: AgentRiskHistoryQueryService.summarize → 같은 전체 위험 집계
```

**왜:** 같은 위험 데이터라도 쓰기·판정과 읽기·통계의 변경 이유가 다르다. 목록·집계는 QueryService에 두고 운영 대시보드가 같은 집계를 재사용한다. C17에 두 필터를 함께 전달해도 AND 조건으로 합쳐지지 않는다. 근거: [AgentRiskHistoryQueryService.findRecent()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskHistoryQueryService.java:24), [AgentRiskHistoryQueryService.summarize()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskHistoryQueryService.java:41), [AdminDashboardService.summary()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AdminDashboardService.java:35).

### INNER-13. Incident는 생성 조건에서 나뉘고 생애주기로 모임

```text
S05 분석 결과 → RiskIncidentService.createIfNeeded
├─ 점수 80 이상 또는 BLOCK_AND_ESCALATE / CREATE_INCIDENT → Incident OPEN 저장
└─ 조건 미충족 → Incident 추가 없음

같은 RiskIncidentService
├─ C18: 최근 Incident 목록
├─ C19: acknowledge → 확인 상태 저장
├─ C20: resolve → 종결 상태 저장
└─ C12 내부: countOpen → 열린 건수만 재사용
```

**왜:** 위험 분석은 Incident가 필요한지 결과를 만들고, Incident Service는 Incident 자체의 생성·조회·상태를 맡는다. S05가 C18~C20 HTTP API를 자동 호출하는 것은 아니다. 근거: [RiskIncidentService.createIfNeeded()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java:23), [RiskIncidentService.acknowledge()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java:55), [RiskIncidentService.resolve()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java:62), [RiskIncidentService.countOpen()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java:69).

### INNER-14. 여러 자료가 하나의 화면용 결과로 모이는 조합

| 조합 위치 | 모으는 자료 | 묶는 이유 |
|---|---|---|
| C03 등록 | 문의 + 선택한 자료 종류 + 검색 조건 | 하나의 등록 업무에서 함께 저장할 데이터. 실제 파일 본문 업로드는 P04 별도 |
| C05·C09 문의 보기 | 문의 + 자료 + 최신 조건 + 메모 + 업로드 메타데이터 | 화면이 같은 문의를 일관된 DTO로 읽음 |
| C06~C08·P02 검색 보기 | 요청 + 조건 + 결과 스냅샷 | 같은 검색 기록을 생성·갱신·조회 후 같은 형태로 읽음 |
| R02 절차 상세 | 절차 + 순서 있는 단계 + 양식 | 상세 화면이 필요한 Reference를 한 번에 받음 |
| S05 위험 분석 | 신호·점수·판정·조치 → 위험 이력 + 실행 이력 + 조건별 Incident | 하나의 위험 평가 결과를 기록하고 후속 Incident 책임에 전달 |
| C12 운영 요약 | 실행·위험·보안 이력 수 + 열린 Incident 수 + 위험 집계 | 운영 화면의 한 요약 DTO. 일부 값은 담당 Service를 재사용 |
| C13 마케팅 | 문의·검색 행 수 + DISTINCT 문의 수 + 현재 준비 상태 + 행동 보고 횟수 | 마케팅 읽기 모델 하나로 제공하되 집계 단위는 구분 |

집계를 묶는다고 여러 API를 다시 HTTP 호출하거나 병렬 조회를 자동 수행하는 것은 아니다. C12와 C13은 목적·자료·집계 의미가 달라 별도 Service를 유지한다. 근거: [AdminDashboardService.summary()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AdminDashboardService.java:35), [MarketingDashboardService.funnel()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/MarketingDashboardService.java:32), [LegalProcedureService.findProcedureDetail()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:85).

### INNER-15. 보안·관측은 공통 경계이고 업무 Service와 역할이 다름

여러 업무 요청은 SecurityConfig의 경로별 정책을 먼저 적용받고, 해당 /api 경로는 TraceIdInterceptor를 사용한다. S01·S02는 AiMetricRecorder를 공유하고 Dynamic·Cost도 MeterRegistry에 각각 관측값을 등록한다. C16은 같은 Registry를 노출한다. 인증 실패·접근 거절의 SecurityEventHistory는 별도 기록 경로이며, Chat 차단 Counter가 같은 DB 감사 행으로 자동 합쳐지는 것은 아니다. S02에는 S01과 같은 전체·단계별 Timer 호출이 붙어 있지 않다.

이는 Controller들이 공통 업무 Service를 호출하는 관계와 구분되는 HTTP 경계·관측 모듈의 재사용이다. 근거: [SecurityConfig.applicationSecurity()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java:66), [SecurityConfig.actuatorSecurity()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java:42), [WebMvcConfig.addInterceptors()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/WebMvcConfig.java:23), [AiMetricRecorder.recordRequest()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/support/AiMetricRecorder.java:34), [AiMetricRecorder.stopStage()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/support/AiMetricRecorder.java:27).

## 4. 실제 업무 Service가 없거나 아직 연결되지 않은 항목

| API | 실제 연결 | 읽을 의미 |
|---|---|---|
| C01 | AuthController → AuthenticationManager → JwtTokenProvider | 인증 성공 뒤 토큰 발급의 순서. AuthService를 가정하지 않음 |
| P01 | CurrentUserController → Authentication → CurrentUserResponse | 이미 검증된 사용자·역할을 읽으므로 별도 업무 Service 없음 |
| C16 | SecurityConfig의 ADMIN Basic → Spring Boot Actuator → MeterRegistry | 직접 작성한 업무 Controller·Service 없이 프레임워크가 지표 노출 |
| P03 | 설계 상태 | 실제 외부 검색 실행 Controller·Service가 없으므로 현재 분기·공유 구현으로 세지 않음 |

## 5. Service 18개 전체 연결 인덱스

공유 Service만 보다가 빠지는 전용 Service가 없도록 전체를 함께 싣는다. 다른 Service 호출은 도달 가능한 해당 Service 메서드들의 실제 필드 호출을 합친 목록이다. Repository는 저장·조회 연결이며 업무 Service와 구분한다.

| Service | 연결되는 API | 이 Service가 호출하는 다른 Service | Repository 연결 |
|---|---|---|---|
| AdminDashboardService | C12, C14 | AgentRiskHistoryQueryService, RiskIncidentService | AgentRiskHistoryRepository, AgentRunHistoryRepository, SecurityEventHistoryRepository |
| AgentRiskDecisionService | S05 | RiskIncidentService | AgentRiskHistoryRepository, AgentRunHistoryRepository |
| AgentRiskHistoryQueryService | C12, C15, C17 | 없음 | AgentRiskHistoryRepository |
| AiCostControlService | S01, S02, S03, S04 | 없음 | 없음 |
| AiService | S01 | AiCostControlService, CostedGuardrailService, LegalDynamicGuardService, LegalFixedGuardService, PrivacyOutputGuardService | 없음 |
| AiStreamingService | S02 | AiCostControlService, CostedGuardrailService, LegalDynamicGuardService, LegalFixedGuardService, PrivacyOutputGuardService | 없음 |
| CostedGuardrailService | S01, S02, S03, S04 | AiCostControlService | 없음 |
| IntakeAccessService | P02, P04, P05 | 없음 | LegalIntakeRepository |
| IntakeDocumentService | P04, P05 | IntakeAccessService | IntakeDocumentDownloadRepository, IntakeDocumentRepository |
| IntakeEventService | P06 | 없음 | IntakeEventRepository, LegalIntakeRepository |
| LegalDynamicGuardService | S01, S02 | 없음 | 없음 |
| LegalFixedGuardService | S01, S02 | 없음 | 없음 |
| LegalIntakeRecordService | C02, C03, C05, C06, C07, C08, C09, C10, C11, P02 | PrivacyOutputGuardService | AiSearchRequestRepository, ConsultationMemoRepository, IntakeDocumentRepository, LawyerSearchConditionRepository, LegalIntakeRepository, SearchResultSnapshotRepository |
| LegalProcedureService | R01, R02, R03, R04, R05, R06 | 없음 | LegalFormTemplateRepository, LegalProcedureRepository, LegalProcedureStepRepository |
| MarketingDashboardService | C13 | 없음 | AiSearchRequestRepository, IntakeEventRepository, LegalIntakeRepository |
| MinimalLegalIntakeService | C04 | PrivacyOutputGuardService | 없음 |
| PrivacyOutputGuardService | C03, C04, C05, C09, C11, S01, S02 | 없음 | 없음 |
| RiskIncidentService | C12, C18, C19, C20, S05 | 없음 | RiskIncidentRepository |

## 6. 37개 항목 누락 확인표

| API | 기능 | Controller 메서드 | 직접 연결·예외 |
|---|---|---|---|
| C01 | 로그인 | [AuthController.login()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/AuthController.java:27) | AuthenticationManager.authenticate() → JwtTokenProvider.createToken() |
| C02 | 사건 분류 미리보기 | [LegalIntakeClassificationController.preview()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeClassificationController.java:22) | LegalIntakeRecordService.classifyPreview() |
| C03 | 문의 등록 | [LegalIntakeRecordController.create()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:34) | S.create() |
| C04 | 간단 안내 생성 | [MinimalLegalIntakeController.analyze()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/MinimalLegalIntakeController.java:20) | S.analyze() |
| C05 | 문의 상세 | [LegalIntakeRecordController.findById()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:44) | S.findById() |
| C06 | 검색 요청 생성 | [LegalIntakeRecordController.createSearchRequest()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:50) | S.createSearchRequest() |
| C07 | 검색 요청 상세 | [LegalIntakeRecordController.findSearchRequest()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:56) | S.findSearchRequest() |
| C08 | 결과 스냅샷 저장 | [LegalIntakeRecordController.addSearchResult()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:62) | S.addSearchResult() |
| C09 | 문의 목록 | [LegalIntakeRecordController.findRecent()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:39) | S.findRecent() |
| C10 | 상태 변경 | [LegalIntakeStatusController.update()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeStatusController.java:23) | LegalIntakeRecordService.updateStatus() |
| C11 | 상담 메모 추가 | [ConsultationMemoController.create()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/ConsultationMemoController.java:26) | LegalIntakeRecordService.createMemo() |
| C12 | 운영 요약 | [AdminDashboardController.summary()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java:23) | S.summary() |
| C13 | 마케팅 기본 집계 | [MarketingDashboardController.funnel()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/MarketingDashboardController.java:19) | S.funnel() |
| C14 | 최근 보안 이벤트 | [AdminDashboardController.securityEvents()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java:28) | S.recentSecurityEvents() |
| C15 | 위험 집계 | [AgentRiskHistoryController.summary()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java:30) | AgentRiskHistoryQueryService.summarize() |
| C16 | Prometheus 지표 | Spring Boot Actuator | ADMIN Basic → MeterRegistry |
| C17 | 위험 이력 | [AgentRiskHistoryController.history()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java:24) | AgentRiskHistoryQueryService.findRecent() |
| C18 | Incident 목록 | [RiskIncidentController.findRecent()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:24) | S.findRecent() |
| C19 | Incident 확인 | [RiskIncidentController.acknowledge()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:29) | S.acknowledge() |
| C20 | Incident 종결 | [RiskIncidentController.resolve()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:34) | S.resolve() |
| R01 | 절차 목록 | [LegalProcedureController.procedures()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:40) | S.findProcedures() |
| R02 | 절차 상세 | [LegalProcedureController.procedureDetail()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:58) | S.findProcedureDetail() |
| R03 | 절차 단계 | [LegalProcedureController.procedureSteps()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:74) | S.findProcedureSteps() |
| R04 | 양식 목록 | [LegalProcedureController.forms()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:86) | S.findForms() |
| R05 | 양식 상세 | [LegalProcedureController.form()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:100) | S.findForm() |
| R06 | 형사 보완수사 체크리스트 | [LegalProcedureController.supplementaryInvestigationChecklist()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:111) | S.supplementaryInvestigationChecklist() |
| S01 | 동기 AI Chat | [AiController.chat()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AiController.java:28) | S.chat() |
| S02 | SSE AI Chat | [AiController.chatStream()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AiController.java:33) | AiStreamingService.stream() |
| S03 | Input Guardrail 단독 검사 | [LegalGuardrailController.checkInput()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalGuardrailController.java:26) | CostedGuardrailService.checkInput() |
| S04 | Output Guardrail 단독 검사 | [LegalOutputGuardrailController.checkOutput()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalOutputGuardrailController.java:26) | CostedGuardrailService.checkOutput() |
| S05 | Agent 위험 분석 | [AgentRiskDecisionController.analyze()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskDecisionController.java:22) | S.analyze() |
| P01 | 내 정보 | [CurrentUserController.me()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/CurrentUserController.java:10) | Authentication에서 직접 응답 |
| P02 | 문의별 검색 이력 | [IntakeSearchHistoryController.history()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeSearchHistoryController.java:24) | IntakeAccessService.requireAccess() → LegalIntakeRecordService.findSearchRequests() |
| P03 | 실제 외부 검색 실행 · 설계안 | 미구현 | 실제 외부 검색 실행 설계 |
| P04 | 파일 업로드 | [IntakeDocumentController.upload()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeDocumentController.java:25) | S.upload() |
| P05 | 파일 다운로드 | [IntakeDocumentController.download()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeDocumentController.java:32) | S.download() |
| P06 | 행동 이벤트 기록 | [IntakeEventController.record()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeEventController.java:18) | S.record() |
