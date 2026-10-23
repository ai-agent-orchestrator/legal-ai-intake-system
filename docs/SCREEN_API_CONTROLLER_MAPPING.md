# Screen - API - Controller Mapping · Postman 입력 기준

2026-10-23 · 화면01~13 전체 · 번호 API **38개 구현**(기존37개+C21 담당자 배정), 보조 Actuator 경로1개 별도. 운영자 화면09~13은 React 주소 대신 Postman 요청값을 표기한다. 최신 문의 권한 계약을 반영했다. 마지막 권한 검증은 서버·React·H2 실제 요청과 브라우저14개 검사이며 외부 유료 호출은0회다.

## 1. Postman 공통 설정

| 변수 | 입력값·받는 위치 |
|---|---|
| `baseUrl` | `http://127.0.0.1:8080` — 기본 백엔드 주소. 실제 서버 포트 설정이 다르면 변경. `5175`는 React 개발 서버 주소 |
| `accessToken` | C01 응답의 `accessToken`; Bearer Token 입력란에는 토큰 값만 입력 |
| `intakeId` | C03 응답 또는 C09 항목의 `id` |
| `searchRequestId` | C06 응답 또는 P02 항목의 `id` |
| `procedureCode` | R01 항목의 `code` |
| `formCode` | R04 항목의 `formCode` 값. 실제 응답을 확인해 설정 |
| `documentId` | P04 응답 또는 C05 `uploadedDocuments` 항목의 `id` |
| `incidentId` | **C18 목록에서 실험할 Incident의 `id`를 직접 선택**. `riskHistoryId`와 다름 |
| `adminUsername` / `adminPassword` | C16 Basic Auth용 관리자 계정. 현재 개발 기본값은 `admin` / `admin123` (SecurityConfig 기준) |

JSON 요청은 **Body → raw → JSON**, `Content-Type: application/json`을 사용한다. 조회 GET과 C19·C20 PATCH는 **Body → none**이다. C16은 **Authorization → Basic Auth**, Username=`{{adminUsername}}`, Password=`{{adminPassword}}`, `Accept: text/plain`으로 설정한다. 나머지 운영자 C12~C15·C17~C20은 현재 **No Auth**다. 컬렉션 상위 Bearer/Basic 설정을 상속하지 않는다.

표의 API는 모두 백엔드에 구현돼 있다. 화면 연결·브라우저 검수와 실제 외부 공급자 호출 검수는 별도 기록으로 구분한다. C03은 Bearer 필수이며 소유자를 JWT로 기록한다. 저장 문의는 소유자·배정 LAWYER·ADMIN만 접근하고 내부 상태/메모는 담당자·ADMIN만 관리한다. 기존 무소유 자료는 ADMIN만 접근하고 배정은 불가하다. [권한·응답 변경 상세](SCREEN04_08_ACCESS_CONTROL_REVIEW.md).

## 2. 화면01~08 전체 매핑

화면 번호는 기존 기능표 기준으로 **07=문의 상세, 08=상태·상담 메모**다. 이전 첨부 이미지의 두 번호는 반대였으므로 기능명으로 구분한다. R01은 화면03·04의 실제 절차 코드 조회 보조이며, C05는 화면05·08의 공통 문의 조회로 함께 표기했다.

| 화면 | API·동작 | Postman 메서드·URL | Params·Body 입력 | Authorization | Controller.메서드 |
|---|---|---|---|---|---|
| 01 로그인 | **C01** 로그인 | `POST {{baseUrl}}/api/auth/login` | raw JSON: username, password | No Auth | [AuthController.login()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/AuthController.java#L27) |
| 01 로그인 | **P01** 현재 사용자 | `GET {{baseUrl}}/api/me` | Body 없음 | Bearer Token | [CurrentUserController.me()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/CurrentUserController.java#L10) |
| 02 사건 분류·절차 안내 | **C02** 규칙 기반 사건 분류 | `POST {{baseUrl}}/api/intakes/classify-preview` | raw JSON: coreStatement(필수·100자), caseType(선택) | No Auth | [LegalIntakeClassificationController.preview()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeClassificationController.java#L22) |
| 02 사건 분류·절차 안내 | **R01** 절차 목록 | `GET {{baseUrl}}/api/legal/procedures` | Body 없음; Params domain=CIVIL 선택 | No Auth | [LegalProcedureController.procedures()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L40) |
| 02 사건 분류·절차 안내 | **R02** 절차 상세 | `GET {{baseUrl}}/api/legal/procedures/{{procedureCode}}` | Path code={{procedureCode}}; Body 없음 | No Auth | [LegalProcedureController.procedureDetail()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L58) |
| 03 문의 등록·자료 준비 | **C03** 문의 등록 | `POST {{baseUrl}}/api/intakes` | raw JSON: inquiryPurpose, caseType, partyPosition, coreStatement(100자), desiredNextStep 필수 | Bearer Token 필수 | [LegalIntakeRecordController.create()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java#L38) |
| 03 문의 등록·자료 준비 | **R01** 절차 목록 | `GET {{baseUrl}}/api/legal/procedures` | Body 없음; Params domain=CIVIL 선택 | No Auth | [LegalProcedureController.procedures()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L40) |
| 03 문의 등록·자료 준비 | **R02** 절차 상세 | `GET {{baseUrl}}/api/legal/procedures/{{procedureCode}}` | Path code={{procedureCode}}; Body 없음 | No Auth | [LegalProcedureController.procedureDetail()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L58) |
| 03 문의 등록·자료 준비 | **R03** 절차 단계 | `GET {{baseUrl}}/api/legal/procedures/{{procedureCode}}/steps` | Path code={{procedureCode}}; Body 없음 | No Auth | [LegalProcedureController.procedureSteps()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L74) |
| 03 문의 등록·자료 준비 | **R04** 양식 목록 | `GET {{baseUrl}}/api/legal/forms` | Body 없음; Params procedure={{procedureCode}} 선택 | No Auth | [LegalProcedureController.forms()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L86) |
| 03 문의 등록·자료 준비 | **R05** 양식 상세 | `GET {{baseUrl}}/api/legal/forms/{{formCode}}` | Path formCode={{formCode}}; Body 없음 | No Auth | [LegalProcedureController.form()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L100) |
| 04 간단 안내·접수 내용 | **C04** 규칙 기반 간단 안내 | `POST {{baseUrl}}/api/legal-intake/minimal` | raw JSON: coreFact, caseType, inquiryPurpose, partyRole, documents 등 | Bearer Token 필수 | [MinimalLegalIntakeController.analyze()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/MinimalLegalIntakeController.java#L20) |
| 04 간단 안내·접수 내용 | **C05** 문의 상세 조회 | `GET {{baseUrl}}/api/intakes/{{intakeId}}` | Path id={{intakeId}}; Body 없음 | Bearer Token + 소유자/배정 LAWYER/ADMIN | [LegalIntakeRecordController.findById()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java#L56) |
| 04 간단 안내·접수 내용 | **R01** 절차 목록 | `GET {{baseUrl}}/api/legal/procedures` | Body 없음; Params domain=CIVIL 선택 | No Auth | [LegalProcedureController.procedures()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L40) |
| 04 간단 안내·접수 내용 | **R02** 절차 상세 | `GET {{baseUrl}}/api/legal/procedures/{{procedureCode}}` | Path code={{procedureCode}}; Body 없음 | No Auth | [LegalProcedureController.procedureDetail()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L58) |
| 04 간단 안내·접수 내용 | **R06** 형사 보완수사 체크리스트 | `GET {{baseUrl}}/api/legal/criminal/checklist/supplementary-investigation` | Params 없음; Body 없음 | No Auth | [LegalProcedureController.supplementaryInvestigationChecklist()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java#L111) |
| 05 검색 요청·결과·이력 | **C05** 문의 상세 조회 | `GET {{baseUrl}}/api/intakes/{{intakeId}}` | Path id={{intakeId}}; Body 없음 | Bearer Token + 소유자/배정 LAWYER/ADMIN | [LegalIntakeRecordController.findById()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java#L56) |
| 05 검색 요청·결과·이력 | **C06** 검색 요청 생성 | `POST {{baseUrl}}/api/intakes/{{intakeId}}/search-requests` | Path intakeId; raw JSON: {} 또는 searchPurpose, sourcePolicy | Bearer Token + 소유자/배정 LAWYER/ADMIN | [LegalIntakeRecordController.createSearchRequest()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java#L62) |
| 05 검색 요청·결과·이력 | **C07** 검색 요청·결과 조회 | `GET {{baseUrl}}/api/intakes/search-requests/{{searchRequestId}}` | Path searchRequestId; Body 없음 | Bearer Token + 소유자/배정 LAWYER/ADMIN | [LegalIntakeRecordController.findSearchRequest()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java#L68) |
| 05 검색 요청·결과·이력 | **C08** 결과 링크 직접 저장 | `POST {{baseUrl}}/api/intakes/search-requests/{{searchRequestId}}/results` | Path searchRequestId; raw JSON: sourceType, title, url 필수; snippet, displayOrder 선택 | Bearer Token + 소유자/배정 LAWYER/ADMIN | [LegalIntakeRecordController.addSearchResult()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java#L74) |
| 05 검색 요청·결과·이력 | **P02** 문의별 검색 이력 | `GET {{baseUrl}}/api/intakes/{{intakeId}}/search-requests` | Path intakeId; Body 없음 | Bearer Token + 소유자/배정 LAWYER/ADMIN | [IntakeSearchHistoryController.history()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeSearchHistoryController.java#L24) |
| 05 검색 요청·결과·이력 | **P03** 실제 외부 검색 실행 | `POST {{baseUrl}}/api/intakes/search-requests/{{searchRequestId}}/execute` | Path searchRequestId; Body none 또는 raw JSON: {}; 설명 재시도는 {"retryExplanation":true} | Bearer Token + 소유자/배정 LAWYER/ADMIN | [SearchExecutionController.execute()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/SearchExecutionController.java#L20) |
| 06 문의 목록 | **C09** 문의 목록 | `GET {{baseUrl}}/api/intakes` | Params 없음; Body 없음 | Bearer Token + 역할별 목록 범위 | [LegalIntakeRecordController.findRecent()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java#L51) |
| 07 문의 상세 | **C05** 문의 상세 조회 | `GET {{baseUrl}}/api/intakes/{{intakeId}}` | Path id={{intakeId}}; Body 없음 | Bearer Token + 소유자/배정 LAWYER/ADMIN | [LegalIntakeRecordController.findById()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java#L56) |
| 08 상태 변경·상담 메모 | **C05** 문의 상세 조회 | `GET {{baseUrl}}/api/intakes/{{intakeId}}` | Path id={{intakeId}}; Body 없음 | Bearer Token + 소유자/배정 LAWYER/ADMIN | [LegalIntakeRecordController.findById()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java#L56) |
| 08 상태 변경·상담 메모 | **C10** 문의 상태 변경 | `PATCH {{baseUrl}}/api/intakes/{{intakeId}}/status` | Path id={{intakeId}}; raw JSON: {"status":"REVIEWED"} | Bearer Token + 배정 LAWYER/ADMIN | [LegalIntakeStatusController.update()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeStatusController.java#L23) |
| 08 상태 변경·상담 메모 | **C11** 상담 메모 추가 | `POST {{baseUrl}}/api/intakes/{{intakeId}}/memo` | Path id={{intakeId}}; raw JSON: content 필수(4,000자), followUpItems 선택(최대4개) | Bearer Token + 배정 LAWYER/ADMIN | [ConsultationMemoController.create()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/ConsultationMemoController.java#L26) |

담당자 배정은 화면08 상세의 관리자 카드에서 실행한다(아래 기존 기능표 번호07/08 차이는 기능명으로 구분).

| 추가 화면·동작 | API | Postman 요청 | Body | Authorization | Controller → Service |
|---|---|---|---|---|---|
| 문의 상세 · 담당자 배정/해제 | **C21** | `PATCH {{baseUrl}}/api/intakes/{{intakeId}}/assignment` | `{"assignedLawyerUsername":"lawyer"}` 또는 `{"assignedLawyerUsername":null}` | Bearer Token + ADMIN | IntakeAssignmentController.assign → LegalIntakeRecordService.assignLawyer |

## 3. 운영자 화면09~13 매핑 · Postman 실험 입력

| 화면 | API·동작 | Postman 메서드·URL | Params·Body 입력 | Authorization | Controller.메서드 |
|---|---|---|---|---|---|
| 09 운영 요약 | **C12** 운영 요약 | `GET {{baseUrl}}/api/admin/dashboard/summary` | Params 없음; Body 없음 | No Auth | [AdminDashboardController.summary()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java#L23) |
| 10 마케팅 집계 | **C13** 마케팅 집계 | `GET {{baseUrl}}/api/admin/dashboard/marketing-funnel` | Params 없음; Body 없음 | No Auth | [MarketingDashboardController.funnel()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/MarketingDashboardController.java#L19) |
| 11 보안·위험·비용 | **C14** 최근 보안 이벤트 | `GET {{baseUrl}}/api/admin/dashboard/security-events` | Params 없음; Body 없음 | No Auth | [AdminDashboardController.securityEvents()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java#L28) |
| 11 보안·위험·비용 | **C15** 위험 집계 | `GET {{baseUrl}}/api/agent/risk/summary` | Params 없음; Body 없음 | No Auth | [AgentRiskHistoryController.summary()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java#L30) |
| 11 보안·위험·비용 | **C16** Prometheus 지표 | `GET {{baseUrl}}/actuator/prometheus` | Params 없음; Body 없음; Accept: text/plain | Basic Auth: ADMIN | [Spring Boot Actuator (업무 Controller 없음)](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java) |
| 12 위험 이력·Incident | **C17** 위험 이력·점수 | `GET {{baseUrl}}/api/agent/risk/history` | Body 없음; Params 선택: riskLevel=HIGH 또는 decision=HIGH_RISK_AGENT_BEHAVIOR | No Auth | [AgentRiskHistoryController.history()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java#L24) |
| 12 위험 이력·Incident | **C18** Incident 목록 | `GET {{baseUrl}}/api/incidents` | Params 없음; Body 없음; 응답의 id에서 incidentId 선택 | No Auth | [RiskIncidentController.findRecent()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java#L24) |
| 12 위험 이력·Incident | **C19** Incident 확인 | `PATCH {{baseUrl}}/api/incidents/{{incidentId}}/ack` | Path incidentId={{incidentId}}; Params 없음; Body none | No Auth | [RiskIncidentController.acknowledge()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java#L29) |
| 12 위험 이력·Incident | **C20** Incident 종결 | `PATCH {{baseUrl}}/api/incidents/{{incidentId}}/resolve` | Path incidentId={{incidentId}}; Params 없음; Body none | No Auth | [RiskIncidentController.resolve()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java#L34) |
| 13 AI 위험 요약 | **C16** Prometheus 지표 | `GET {{baseUrl}}/actuator/prometheus` | Params 없음; Body 없음; Accept: text/plain | Basic Auth: ADMIN | [Spring Boot Actuator (업무 Controller 없음)](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java) |
| 13 AI 위험 요약 | **C17** 위험 이력·점수 | `GET {{baseUrl}}/api/agent/risk/history` | Body 없음; Params 없음 · 최신 최대50건에서 같은 기록의 신호 조합 건수·점수 집계 | No Auth | [AgentRiskHistoryController.history()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java#L24) |

| 13 AI 위험 요약 | **보조 Actuator 신규** 실제 시계열 | `GET {{baseUrl}}/actuator/guardtimeseries` | Params/Body 없음; Accept application/json | Basic Auth: ADMIN | [GuardTimeSeriesEndpoint.read()](../backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/observability/GuardTimeSeriesEndpoint.java) → PrometheusTimeSeriesService.read() |

운영 설명은 화면13에 통합했으며 화면14는 폐지했다. 화면13은 C16·C17과 신규 보조 Actuator API를 사용한다. C16은 현재 누적 값, C17은 건수·점수·ID, guardtimeseries는 실제 시간 이력이다. 현재 번호 API38개 외 보조 경로1개다. 화면13 그래프 자체의 DB 변경은 없으며, 후속 문의 권한 작업에서 기존 테이블에 nullable 컬럼2개를 추가했다.
