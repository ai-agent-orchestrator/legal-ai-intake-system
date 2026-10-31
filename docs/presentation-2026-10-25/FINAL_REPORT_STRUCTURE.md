# 최종 보고서 · 편집·출처 구조

원본97쪽 → 개정76쪽. README 순서·실제 화살표 Flow·오늘 회고·확장 계획 통합.

- 01 PM Positioning: 3~3쪽
- 02 PM Multi-Layer Analysis: 4~4쪽
- 03 Problem → Solution: 5~5쪽
- 04 Security Architecture: 6~7쪽
- 05 차단 이유를 읽을 수 있어야 합니다: 8~8쪽
- 06 Product Introduction: 9~11쪽
- 07 전체 시스템·핵심 기능 Flow: 12~13쪽
- 08 핵심 백엔드 Flow: 14~16쪽
- 09 핵심 API·전체 API 지도: 17~23쪽
- 10 쉬운 운영 관리·마케팅: 24~26쪽
- 11 실시간 보안 관측·대응: 27~27쪽
- 12 Current Implementation·수행 방법: 28~30쪽
- 13 Core Message: 31~31쪽
- 14 이번 설계의 의미: 31~31쪽
- A 부록 A · ERD·데이터 제약: 32~35쪽
- B 부록 B · 화면01~13·모바일: 36~49쪽
- C 부록 C · DDD·정책·코드 매핑: 50~55쪽
- D 부록 D · 검증 결과·대표 증거: 56~66쪽
- 15 회고·시연 배포 체크리스트·확장 계획: 67~76쪽

# 편집 가능한 본문·표·흐름 단계

## 팀 프로젝트 결과보고서

Security-Aware Legal AI Intake System · 2026-10-26 통합 개정본

## 목차

README 순서로 읽고, 상세 매핑·화면·실험 근거는 부록에서 확인합니다.

원본 유지: 3쪽. PM 포지셔닝을 한 번만 제시합니다.

원본 유지: 5쪽. 여섯 관점의 분석을 한 번만 제시합니다.

원본 유지: 4쪽. 반복되던 Problem–Solution 설명을 이 페이지로 통합합니다.

원본 유지: 6쪽. 보안 통제의 책임을 요약합니다.

## 보안 Flow · 어디서 멈추는가

인증·입력·객체 권한·도메인 정책의 실패 경계가 서로 다릅니다.

- 경로·메서드 → 공개/JWT/ADMIN Basic → DTO·객체 권한 → 업무·AI 정책 → 응답·관측
- 정책 차단은 HTTP 상태 하나로 판정하지 않습니다.

원본 유지: 7쪽. 차단 사유·세부 사유·보호 결과의 구분을 보존합니다.

원본 유지: 14, 15, 16쪽. 입력 6항목·미니 온톨로지·동일 사건 안내 예시는 고유한 제품 설명입니다.

## 전체 시스템 Flow · 역할 → 서버 → 데이터·공급자

사용자 역할, 공통 보안 경계, 업무 처리와 외부 호출·관측을 한 장으로 연결합니다.

- USER/LAWYER/ADMIN → Spring Security → Controller·DTO → Service·정책
- Service → JPA / 외부 공급자 / 지표·Read Model
- memvid 메모리 계층·Python Action의 추가 확장은 마지막 회고 장의 계획과 구분합니다.

## 핵심 기능 Flow · 고객의 시작에서 운영까지

화살표는 사용자 여정입니다. C03·C06·P03은 자동 연쇄 실행이 아니라 각각 별도 요청입니다.

- C02 · 사건 유형 확인: 선택형 입력·규칙 분류 · 법률 Reference R01~R06
- C03 · 문의 접수: 문의·자료·검색 조건 저장 · intakeId·conditionId 생성
- C04·C05 · 간단 안내: 규칙·템플릿 안내·준비 자료 · 초기 안내는 LLM 미호출
- C06 · 검색 요청 준비: 저장 조건을 연결 · searchRequestId 생성
- P03 · 실행: 검색문 → 외부 검색 · 결과 저장 → 관련성 설명
- C07·P02 · 결과·이력: 결과·출처·실행 상태 조회 · PARTIAL은 설명만 재시도
- C05·C09 · 변호사 검토: 배정 문의·첨부·사건 정보 · C21 배정·회수는 ADMIN
- C10·C11 · 상태·메모: 버전·전이·재전송 확인 · 내부 메모는 USER에서 제외
- C12~C20 · 운영·보안: 마케팅·비용·위험 이력 · 관측 → 근거 확인 → 처리

## 핵심 Flow ① · 문의 저장 → 검색 실행 → 결과

ID 전달과 비동기 실행·수동 저장·부분 복구를 구분합니다.

- C03 저장 → C06 별도 요청 → P03 별도 실행·202 → run·결과 저장 → C07/P02 별도 조회
- C08 수동 저장과 P03 내부 저장은 분리합니다.
- PARTIAL은 저장 결과를 유지하며 미완료 설명만 재시도합니다.

## 핵심 Flow ② · 상태·메모 → 안전한 재조회

같은 문의 행 잠금 안에서 키·작성자·버전·허용 전이를 판정합니다.

- C05/C09 → 수정 권한 → C10 / C11의 독립 트랜잭션 → 결과 → 별도 재조회
- expectedVersion은 호환 정책상 선택값이며, 제공해야 오래된 편집을 검출합니다.
- 기존 메모 재전송 판별은 버전 검사보다 먼저 수행합니다.

## 핵심 Flow ③ · AI·SSE의 비용·출력 생명주기

입력 검사 뒤 첫 유료 단계 전에 전체 예상 비용을 확인하고, 실제 호출은 단계별로 예약·정산합니다.

- JWT·DTO → Dynamic·Fixed·길이 → 요청 전체 예산 사전 확인 → 입력 Guard → 답변 생성 → 출력 Guard → Privacy → 응답
- 각 유료 단계 reserve·usage/추정 정산. 출력 차단에도 이미 생성한 답변 비용은 유지합니다.
- SSE는 전체 출력 검사·마스킹 후 token/done을 전달합니다.

## 전체 API 지도 · 38개 + 보조 Actuator

번호 API는 C21 + R6 + S5 + P6입니다. 아래 책임 그룹과 다음 상세 표를 함께 읽습니다.

- 접속·공개 안내: C01 로그인 · C02 규칙 분류 · R01~R06 법률 Reference · P06 행동 보고 · P01 현재 사용자는 JWT 필요
- 문의·검색·파일: C03~C09 · P02~P05 · JWT + 문의 소유/배정/ADMIN · 생성 ID → 후속 요청 ID 전달
- 변호사 업무·배정: C10 상태 · C11 메모: 배정 LAWYER/ADMIN · C21 배정·회수: ADMIN · 버전·키·허용 전이를 함께 확인
- 운영·위험·Incident: C12~C15 · C17~C20: ADMIN JWT · S05 위험 분석 쓰기도 ADMIN JWT · 미등록 /api 경로는 기본 차단
- AI·Guardrail: S01 동기 / S02 SSE / S03 입력 / S04 출력 · JWT → 정책·비용·출력 보호 · 요청·단계·실제 청구액을 구분
- 관측 경로: C16 /actuator/prometheus · 보조 /actuator/guardtimeseries · 업무 JWT와 별도 ADMIN Basic

## 전체 Screen-API-Controller 매핑

S05 권한은 main의 ADMIN JWT로 정정했습니다. 업무 JWT·객체 권한·ADMIN Basic을 구분합니다.

## 전체 Screen-API-Controller 매핑 · 계속

S05 권한은 main의 ADMIN JWT로 정정했습니다. 업무 JWT·객체 권한·ADMIN Basic을 구분합니다.

## 전체 Screen-API-Controller 매핑 · 계속

S05 권한은 main의 ADMIN JWT로 정정했습니다. 업무 JWT·객체 권한·ADMIN Basic을 구분합니다.

## 전체 Screen-API-Controller 매핑 · 계속

S05 권한은 main의 ADMIN JWT로 정정했습니다. 업무 JWT·객체 권한·ADMIN Basic을 구분합니다.

## 전체 Screen-API-Controller 매핑 · 계속

S05 권한은 main의 ADMIN JWT로 정정했습니다. 업무 JWT·객체 권한·ADMIN Basic을 구분합니다.
| 화면 | API·기능 | HTTP·입력 계약 | 인증·대상 | Controller → Service |
| --- | --- | --- | --- | --- |
| 01 로그인 | C01 로그인 | POST {{baseUrl}}/api/auth/login / raw JSON: username, password | No Auth | AuthController.login() |
| 02 사건 분류·절차 안내 | C02 규칙 기반 사건 분류 | POST {{baseUrl}}/api/intakes/classify-preview / raw JSON: coreStatement(필수·100자), caseType(선택) | No Auth | LegalIntakeClassificationController.preview() |
| 03 문의 등록·자료 준비 | C03 문의 등록 | POST {{baseUrl}}/api/intakes / Header Idempotency-Key={{intakeRequestKey}}; raw JSON: inquiryPurpose, caseType, partyPosition, coreStatement(100자), desiredNextStep 필수; 재전송은 같은 키·본문 | Bearer Token 필수 | LegalIntakeRecordController.create() |
| 04 간단 안내·접수 내용 | C04 규칙 기반 간단 안내 | POST {{baseUrl}}/api/legal-intake/minimal / raw JSON: coreFact 필수100자; caseType은 지원코드 선택; documents 최대20개·각100자 | Bearer Token 필수 | MinimalLegalIntakeController.analyze() |
| 04 간단 안내·접수 내용 | C05 문의 상세 조회 | GET {{baseUrl}}/api/intakes/{{intakeId}} / Path id={{intakeId}}; Body 없음 | Bearer Token + 소유자/배정 LAWYER/ADMIN | LegalIntakeRecordController.findById() |
| 05 검색 요청·결과·이력 | C06 검색 요청 생성 | POST {{baseUrl}}/api/intakes/{{intakeId}}/search-requests / Path intakeId; raw JSON: {} 또는 searchPurpose, sourcePolicy | Bearer Token + 소유자/배정 LAWYER/ADMIN | LegalIntakeRecordController.createSearchRequest() |
| 05 검색 요청·결과·이력 | C07 검색 요청·결과 조회 | GET {{baseUrl}}/api/intakes/search-requests/{{searchRequestId}} / Path searchRequestId; Body 없음 | Bearer Token + 소유자/배정 LAWYER/ADMIN | LegalIntakeRecordController.findSearchRequest() |
| 05 검색 요청·결과·이력 | C08 결과 링크 직접 저장 | POST {{baseUrl}}/api/intakes/search-requests/{{searchRequestId}}/results / Path searchRequestId; raw JSON: sourceType, title, url 필수; snippet, displayOrder 선택 | Bearer Token + 소유자/배정 LAWYER/ADMIN | LegalIntakeRecordController.addSearchResult() |
| 06 문의 목록 | C09 문의 목록 | GET {{baseUrl}}/api/intakes / Params 없음; Body 없음 | Bearer Token + 역할별 목록 범위 | LegalIntakeRecordController.findRecent() |
| 07 상태 변경·상담 메모 | C10 문의 상태 변경 | PATCH {{baseUrl}}/api/intakes/{{intakeId}}/status / Path id={{intakeId}}; raw JSON: {"status":"REVIEWED","expectedVersion":{{intakeVersion}}}; 응답 version 갱신 | Bearer Token + 배정 LAWYER/ADMIN | LegalIntakeStatusController.update() |
| 07 상태 변경·상담 메모 | C11 상담 메모 추가 | POST {{baseUrl}}/api/intakes/{{intakeId}}/memo / Path id={{intakeId}}; Header Idempotency-Key={{memoRequestKey}}; raw JSON: content 필수(4,000자), followUpItems 선택(최대4개), expectedVersion={{intakeVersion}}; 응답 intakeVersion 갱신 | Bearer Token + 배정 LAWYER/ADMIN | ConsultationMemoController.create() |
| 09 운영 요약 | C12 운영 요약 | GET {{baseUrl}}/api/admin/dashboard/summary / Params 없음; Body 없음 | Bearer Token + ADMIN | AdminDashboardController.summary() |
| 10 마케팅 집계 | C13 마케팅 집계 | GET {{baseUrl}}/api/admin/dashboard/marketing-funnel / Params 없음; Body 없음 | Bearer Token + ADMIN | MarketingDashboardController.funnel() |
| 11 보안·위험·비용 | C14 최근 보안 이벤트 | GET {{baseUrl}}/api/admin/dashboard/security-events / Params 없음; Body 없음 | Bearer Token + ADMIN | AdminDashboardController.securityEvents() |
| 11 보안·위험·비용 | C15 위험 집계 | GET {{baseUrl}}/api/agent/risk/summary / Params 없음; Body 없음 | Bearer Token + ADMIN | AgentRiskHistoryController.summary() |
| 11 보안·위험·비용 | C16 Prometheus 지표 | GET {{baseUrl}}/actuator/prometheus / Params 없음; Body 없음; Accept: text/plain | Basic Auth: ADMIN | Spring Boot Actuator (업무 Controller 없음) |
| 12 위험 이력·Incident | C17 위험 이력·점수 | GET {{baseUrl}}/api/agent/risk/history / Body 없음; Params 선택: riskLevel=HIGH 또는 decision=HIGH_RISK_AGENT_BEHAVIOR | Bearer Token + ADMIN | AgentRiskHistoryController.history() |
| 12 위험 이력·Incident | C18 Incident 목록 | GET {{baseUrl}}/api/incidents / Params 없음; Body 없음; 응답의 id에서 incidentId 선택 | Bearer Token + ADMIN | RiskIncidentController.findRecent() |
| 12 위험 이력·Incident | C19 Incident 확인 | PATCH {{baseUrl}}/api/incidents/{{incidentId}}/ack / Path incidentId={{incidentId}}; Params 없음; Body none | Bearer Token + ADMIN | RiskIncidentController.acknowledge() |
| 12 위험 이력·Incident | C20 Incident 종결 | PATCH {{baseUrl}}/api/incidents/{{incidentId}}/resolve / Path incidentId={{incidentId}}; Params 없음; Body none | Bearer Token + ADMIN | RiskIncidentController.resolve() |
| 문의 상세 · 담당자 배정/해제 | C21 | PATCH {{baseUrl}}/api/intakes/{{intakeId}}/assignment / {"assignedLawyerUsername":"lawyer"} 또는 {"assignedLawyerUsername":null} | Bearer Token + ADMIN | IntakeAssignmentController.assign → LegalIntakeRecordService.assignLawyer |
| 02 사건 분류·절차 안내 | R01 절차 목록 | GET {{baseUrl}}/api/legal/procedures / Body 없음; Params domain=CIVIL 선택 | No Auth | LegalProcedureController.procedures() |
| 02 사건 분류·절차 안내 | R02 절차 상세 | GET {{baseUrl}}/api/legal/procedures/{{procedureCode}} / Path code={{procedureCode}}; Body 없음 | No Auth | LegalProcedureController.procedureDetail() |
| 03 문의 등록·자료 준비 | R03 절차 단계 | GET {{baseUrl}}/api/legal/procedures/{{procedureCode}}/steps / Path code={{procedureCode}}; Body 없음 | No Auth | LegalProcedureController.procedureSteps() |
| 03 문의 등록·자료 준비 | R04 양식 목록 | GET {{baseUrl}}/api/legal/forms / Body 없음; Params procedure={{procedureCode}} 선택 | No Auth | LegalProcedureController.forms() |
| 03 문의 등록·자료 준비 | R05 양식 상세 | GET {{baseUrl}}/api/legal/forms/{{formCode}} / Path formCode={{formCode}}; Body 없음 | No Auth | LegalProcedureController.form() |
| 04 간단 안내·접수 내용 | R06 형사 보완수사 체크리스트 | GET {{baseUrl}}/api/legal/criminal/checklist/supplementary-investigation / Params 없음; Body 없음 | No Auth | LegalProcedureController.supplementaryInvestigationChecklist() |
| 공통 AI | S01 동기 Chat | POST /api/ai/chat / message100자, sessionId 선택 | JWT | AiController.chat → AiService.chat |
| 공통 AI | S02 SSE Chat | POST /api/ai/chat/stream / message100자; meta/token/done/blocked/error | JWT | AiController.chatStream → AiStreamingService.stream |
| 화면 불필요 | S03 입력 단독 검사 | POST /api/legal/guardrail/input / message 필수, sessionId 선택 | JWT | LegalGuardrailController.checkInput → CostedGuardrailService |
| 화면 불필요 | S04 출력 단독 검사 | POST /api/legal/guardrail/output / response 필수, sessionId 선택 | JWT | LegalOutputGuardrailController.checkOutput → CostedGuardrailService |
| 화면 불필요 | S05 Agent 위험 분석 | POST /api/agent/risk/analyze / userInput,toolName,retry/token 수,신호 Boolean | ADMIN JWT | AgentRiskDecisionController.analyze → AgentRiskDecisionService |
| 01 로그인 | P01 현재 사용자 | GET {{baseUrl}}/api/me / Body 없음 | Bearer Token | CurrentUserController.me() |
| 05 검색 요청·결과·이력 | P02 문의별 검색 이력 | GET {{baseUrl}}/api/intakes/{{intakeId}}/search-requests / Path intakeId; Body 없음 | Bearer Token + 소유자/배정 LAWYER/ADMIN | IntakeSearchHistoryController.history() |
| 05 검색 요청·결과·이력 | P03 실제 외부 검색 실행 | POST {{baseUrl}}/api/intakes/search-requests/{{searchRequestId}}/execute / Path searchRequestId; Body none 또는 raw JSON: {}; 설명 재시도는 {"retryExplanation":true} | Bearer Token + 소유자/배정 LAWYER/ADMIN | SearchExecutionController.execute() |
| 03·08 공통 | P04 파일 업로드 | POST /api/intakes/{intakeId}/documents / multipart file 필수, documentType 선택 | JWT + 문의 접근 | IntakeDocumentController.upload → IntakeDocumentService |
| 08 공통 | P05 파일 다운로드 | GET /api/intakes/{intakeId}/documents/{documentId}/download / Path intakeId·documentId; Body 없음 | JWT + 문의 접근 | IntakeDocumentController.download → IntakeDocumentService |
| 공통 행동 | P06 행동 보고 | POST /api/intake-events / eventId,eventType,sessionId; SUMMARY_VIEWED는 intakeId 필수 | 현재 공개 · 무결성 검사 | IntakeEventController.record → IntakeEventService |
| 13 | 보조 Actuator | GET /actuator/guardtimeseries / Params/Body 없음 | ADMIN Basic | GuardTimeSeriesEndpoint.read → PrometheusTimeSeriesService.read |

원본 유지: 35쪽. ID 전달 표는 상세 계약의 보조 자료로 유지합니다.

원본 유지: 8쪽. 역할별 보드 설명을 한 번만 제시합니다.

## 마케팅 집계 · 숫자의 의미를 먼저 구분

가상 여정의 API·DB 검증과 상담 후 합성 전환 분석을 혼합하지 않습니다.
| 대상 | 관측한 결과 | 해석 기준 |
| --- | --- | --- |
| 방문·작성·안내 | P06 시드로 VISIT 40 / INTAKE_STARTED 28 / SUMMARY_VIEWED 16 | React 자동 수집 미연결. 실제 고객 성과가 아니라 집계 로직 검증 |
| 문의·검색 | 신규 문의20 / 검색 요청13회 / 검색한 문의10건 | 요청 횟수를 신규 고객 수로 합산하지 않음 |
| 현재 상담 준비 | 6건 READY → 1건 CLOSED → 현재5건 | 현재 상태이며 누적 상담·계약 건수와 다름 |
| 분포·무결성 | 신규20건 분포 대조 / 조작·권한 위반10건 거절 / 기존20/20 통과 | 중복 이벤트 제외·키/내용 충돌 검증 |
| 상담 후 시연 분석 | 가상40→24→12→4 및 수임·이탈 사유 | 프런트 합성 자료. API 시드40→28→20→16과 별도 |

원본 유지: 90쪽. 가상 여정과 실제 API 집계의 대표 캡처 한 장으로 통합합니다.

원본 유지: 9쪽. 실시간 관측·근거 확인·운영자 대응의 책임을 보존합니다.

## 현재 구현·검증 상태

main 반영과 과거 실공급자 실험, 앞으로의 운영 배포를 서로 다른 근거로 관리합니다.
| 영역 | 현재 기준 | 검증·남은 범위 |
| --- | --- | --- |
| 코드·문서 기준 | main / 64f7f62 · 베타 자료 + 리팩터링 + 승인 README·회고 | beta/2026-10-23은 이전 버전으로 보존 |
| 회귀 검사 | 백엔드35개 suite · 276개 테스트 · 실패0 / 프런트 운영자·문의·Q02·실험 검수 도구 검사 및 빌드 통과 | 직전 게시 작업에서 실행한 결과. 이번 PDF 편집에서 새 유료 호출·서버 실험 없음 |
| 오늘의 코드 보완 | 운영 쓰기 경로 ADMIN · 전체 요청 예산 사전 확인 · 검색/Incident 연결 · LAN UUID 대체 생성 | 회고의 코드 버그와 실행 환경 문제를 구분 |
| 제품·데이터 | 화면01~13 · 소유/배정·메모 비공개·키·해시·버전·전이 | 시연 서버의 메모리 DB·예산 장부와 영속 운영 환경 구분 |
| 실제 LLM 기록 | 과거 OpenAI 호출과 usage·예약 종료·공급자 화면 대조 | 모의 Guard 장부를 실공급자 청구액에 합산하지 않음 |
| 운영·확장 | 업무 DB 집계·지표·위험 이력·Incident | HTTPS·운영 계정·영속 예산·실측 여정 / Python Action·memvid 메모리 계층은 계획 |

## 수행 방법·일정 · 14일 흐름

AI 초안 → 정상·실패·차단 검증 → 정책·코드 매핑 → 수정·재검증 → 문서·시연으로 진행합니다.
| 단계 | 핵심 작업 | 확인할 산출물 |
| --- | --- | --- |
| Day1~2 | 시장 문제·MVP·화면·DB·API 설계 | Ground Rule·화면 정의·ERD·API 매핑 |
| Day2~5 | Backend Draft·보안·비용·DB 연결 | Controller·Service·Entity·reasonCode·실패 경계 |
| Day6 | Mapping Check + Refactoring | 화면→API→코드→응답/ID/DB 대조 |
| Day7 | DDD·Event Storming | Actor→Command→Event→Policy→Code / Top–Middle–Bottom |
| Day8~10 | React·Dashboard·LLM Search Routing | 화면/API·검색문·외부 검색·결과·설명 |
| Day11~13 | PowerShell 통합 검증·Refactoring·시연 | 정상/실패/차단 요청·로그·지표·영상·회고 |
| Day14 | 발표·제출 품질 강화 | README·흐름도·캡처·보고서·발표 문장 최종 정리 |

## 시연 영상 · 핵심 흐름과 검증 구간

기존9분·30장면 영상의 핵심 구간을 한 장으로 통합합니다. 세부 장면별 대본은 별도 편집 원본에 있습니다.
| 구간 | 보여주는 흐름 | 근거·구분 |
| --- | --- | --- |
| 00:00–01:40 | 선택형 접수 → 안내·검색 → 변호사 검토·상태·메모 | 규칙 안내·모의 검색·역할별 업무 |
| 01:40–02:25 | 세션·일일·월 예산 차단 | 모의 공급자 예산 실험 |
| 02:25–03:20 | 실제 OpenAI 응답·토큰·정산·출력 상한 | 실제 LLM 기록 / 공급자 사용량 대조 |
| 03:20–03:45 | 입력400·저장 충돌409 | 입력 보존·오래된 편집 처리 |
| 03:45–07:45 | 인증·Fixed·Dynamic·Privacy·객체 권한·중복·상태·부분 복구 | 기존 모의 공급자·DB·후속 중단 검증 |
| 07:45–08:45 | 가상 고객 여정의 실제 API 집계·중복 방지 | 마케팅 기존20/20 기록 / 실제 매출 성과와 구분 |
| 08:45–09:00 | 모바일 입력·마무리 | 기존 작은 화면 뷰포트 / 실제 기기 전체 검증과 구분 |

## Core Message · 이번 설계의 의미

모델 연결을 넘어 정책·비용·운영 결과를 설명하고 검증하는 구조를 만들었습니다.

원본 유지: 26, 27, 28, 29쪽. 전체 관계·실제 필드·제약을 보존하고, 저해상도 전체 ERD 중복 사진은 제거했습니다.

원본 유지: 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53쪽. 화면01~13과 모바일의 서로 다른 실제 캡처를 모두 유지합니다. 중복 화면 목록표는 제거했습니다.

## DDD · 책임 경계와 Read Model

단일 Spring Boot 내부의 책임 경계 후보이며 마이크로서비스 분리 완료를 뜻하지 않습니다.
| Bounded Context 초안 | 담당 업무 | 현재 코드 / 데이터 |
| --- | --- | --- |
| Intake 접수·검토 | 문의 접수, 자료 종류, 분류, 검토 상태, 상담 메모 | LegalIntakeRecordService, LegalIntake, IntakeDocument, LawyerSearchCondition, ConsultationMemo |
| Lawyer Search Routing | 문의 조건→검색문→네이버 두 출처 검색→결과 저장→설명 | AiSearchRequest, SearchResultSnapshot, LegalIntakeRecordService, SearchExecutionService/Store, SearchLlmService, ExternalSearchClient |
| Legal Reference | 법률 절차·단계·양식 안내 | LegalProcedureService, 관련 Entity/Repository |
| AI Safety & Cost | 세션 위험·고정 정책·토큰 예산·입출력 가드·출력 마스킹 | AiService, Guard/Cost/LLM Client, PrivacyOutputGuardService |
| Risk & Operations | Agent 위험 분석, 이력, Incident, 비동기 Job | AgentRiskDecisionService, RiskIncidentService, AgentJobService |
| Reporting | 접수·검색·현재 상담 상태 및 Agent 보안 현황 조회, 화면13 지표/위험 조합 | MarketingDashboardService, AdminDashboardService, C16 Actuator, C17 위험 이력. 화면10 시연 분석은 프런트 합성 자료로 분리 |

## Actor → Command → Event → Policy → Code

Event는 학습용 업무 사실입니다. Event Bus·Outbox·Event Sourcing 구현을 뜻하지 않습니다.

## Actor → Command → Event → Policy → Code · 계속

Event는 학습용 업무 사실입니다. Event Bus·Outbox·Event Sourcing 구현을 뜻하지 않습니다.

## Actor → Command → Event → Policy → Code · 계속

Event는 학습용 업무 사실입니다. Event Bus·Outbox·Event Sourcing 구현을 뜻하지 않습니다.

## Actor → Command → Event → Policy → Code · 계속

Event는 학습용 업무 사실입니다. Event Bus·Outbox·Event Sourcing 구현을 뜻하지 않습니다.
| Actor | Command → Event | Policy | 저장 / Read Model | 코드 진입점 |
| --- | --- | --- | --- | --- |
| 의뢰인·비로그인 허용 / 02 | PreviewCaseClassification / → CaseClassificationPresented | 본문 필수·공백 불가·최대100자(UTF-16), 비저장 규칙 분류 | 저장 없음 / Read: LegalIntakeClassificationResponse | C02 POST /api/intakes/classify-preview → LegalIntakeClassificationController.preview() → LegalIntakeRecordService.classifyPreview() |
| 의뢰인 업무 / 03, USER·LAWYER·ADMIN JWT | SubmitLegalIntake / → LegalIntakeSubmitted | 필수/길이·유형·자료 정리, 로그인 주체를 owner로 저장. 같은 소유자/키/내용은 같은 ID, 다른 내용409 | LegalIntake / legal_intake 및 자료·조건 / Read: LegalIntakeResponse | C03 POST /api/intakes → LegalIntakeRecordController.create() → LegalIntakeRecordService.create() |
| 접수 내부 정책 | RecordSelectedDocuments / → IntakeDocumentsRecorded | C03 트랜잭션 내부, null·공백·중복 제거·기본 자료 적용 | intake_document / Read: selectedDocuments | LegalIntakeRecordService.create() → intakeDocumentRepository.saveAll() |
| 접수 내부 정책 | PrepareLawyerSearchCondition / → LawyerSearchConditionPrepared | 유형·분쟁·지역으로 조건·규칙 검색문 준비. 이 단계는 LLM 미호출 | lawyer_search_condition / Read: lawyerSearchCondition | LegalIntakeRecordService.create() → LawyerSearchCondition.create()·Repository 저장 |
| USER 소유자·배정 LAWYER·ADMIN / 05 | CreateSearchRequest / → LawyerSearchRequestCreated | requireAccess, 문의/최신 조건 존재, 목적 최대300자·출처 정책, PREVIEW_READY 생성 | AiSearchRequest / ai_search_request / Read: AiSearchRequestResponse | C06 POST /api/intakes/{intakeId}/search-requests → LegalIntakeRecordController.createSearchRequest() → RecordService 동명 메서드 |
| USER 소유자·배정 LAWYER·ADMIN / 05 | AppendSearchResultSnapshot / → SearchResultSnapshotRecorded | 접근 권한·요청/조건 존재, 실행 중 수동 추가409, 제목/URL 필수·안전 URL·양수 순서 | search_result_snapshot / Read: 같은 검색 ID의 정렬된 results | C08 POST /api/intakes/search-requests/{searchRequestId}/results → LegalIntakeRecordController.addSearchResult() → RecordService 동명 메서드 |
| USER 소유자·배정 LAWYER·ADMIN / 05 | ExecuteLawyerSearch / → LawyerSearchExecutionStarted / SearchResultSnapshotsSaved / LawyerSearchCompleted | 문의 접근, 조건 소속·출처 정책, 실행 claim/lease로 중복 실행 방지. 검색문→네이버 두 출처→최대3건 저장→설명. 예산·Guard·시간 초과 처리 | ai_search_request 실행 필드·search_result_snapshot / Read: AiSearchRequestResponse.execution/results | P03 POST /api/intakes/search-requests/{searchRequestId}/execute → SearchExecutionController.execute() → SearchExecutionService.execute()/run() → SearchExecutionStore |
| 같은 권한 / 05 | RetrySearchExplanation / → SearchExplanationRegenerated / LawyerSearchCompleted | PARTIAL + retryExplanation=true일 때 저장 결과 유지·미완성 설명만 생성. 외부 검색을 자동 재호출하지 않음 | 같은 검색 요청·저장 결과 / Read: 기존 result ID·설명·실행 상태 | 같은 P03 → SearchExecutionStore.claim() → SearchExecutionService.run()의 explanationOnly 분기 |
| ADMIN / 08 | AssignIntakeLawyer / UnassignIntakeLawyer / → IntakeLawyerAssigned / IntakeLawyerUnassigned | ADMIN·활성 LAWYER 검증·행 잠금. null로 회수, 변경 시 version 증가·같은 배정 no-op. 현재 배정 API는 expectedVersion 미사용 | legal_intake.assigned_lawyer_username/version / Read: 권한에 따른 LegalIntakeResponse | C21 PATCH /api/intakes/{id}/assignment → IntakeAssignmentController.assign() → LegalIntakeRecordService.assignLawyer() |
| 배정 LAWYER·ADMIN / 07 | ChangeIntakeStatus / → IntakeStatusChanged | requireManager 행 잠금·허용 전이·expectedVersion 불일치409. 같은 상태는 no-op200, CLOSED 재개/SUBMITTED 복귀409 | LegalIntake / legal_intake.status/version / Read: LegalIntakeStatusResponse | C10 PATCH /api/intakes/{id}/status → LegalIntakeStatusController.update() → LegalIntakeRecordService.updateStatus() → LegalIntake.changeStatus() |
| 배정 LAWYER·ADMIN / 07 | RecordConsultationMemo / → ConsultationMemoRecorded | 행 잠금·작성자·4,000자·후속코드 최대4개·버전. 같은 문의/키/작성자/내용은 같은 메모, 키 충돌409. 키 없으면120초 중복 추정 | consultation_memo, 문의 version / Read: ConsultationMemoResponse.intakeVersion, 내부 권한 C05의 consultationMemos | C11 POST /api/intakes/{id}/memo → ConsultationMemoController.create() → LegalIntakeRecordService.createMemo() |
| USER·LAWYER·ADMIN / 01 | AuthenticateUser / → AccessTokenIssued (설명용) | 계정 인증 후 서버의 role로 JWT 발급. 화면에서 ADMIN을 선택했다고 관리자 권한을 부여하지 않음 | 현재 InMemoryUserDetailsManager·JWT, 새 업무 행 저장 없음 / Read: LoginResponse, P01의 username·role | C01 POST /api/auth/login → security.AuthController.login() → AuthenticationManager·JwtTokenProvider; P01 GET /api/me |
| USER·LAWYER·ADMIN / 04·06·08 | ViewIntakeDetail / ListIntakes (Query) / → 새 도메인 이벤트 없음 | JWT + 역할별 범위. 메모는 canManage일 때만 JSON에 포함 | legal_intake와 자료·조건·내부 메모 읽기 / Read: LegalIntakeResponse / 목록 | C05/C09 GET /api/intakes/{id}, GET /api/intakes → LegalIntakeRecordController.findById()/findRecent() → RecordService 동명 메서드 |
| USER·LAWYER·ADMIN / 04 | PreviewLegalGuide / → LegalGuidePresented (설명용·비저장) | JWT·필수/길이/유형·단계/목적 코드 검사, 규칙·템플릿 안내. 사건 정리 LLM 미호출 | 현재 C04 서비스는 저장/DB 조회 없음. 화면의 별도 R02/R06 조회와 구분 / Read: MinimalLegalIntakeResponse | C04 POST /api/legal-intake/minimal → MinimalLegalIntakeController.analyze() → MinimalLegalIntakeService.analyze() |
| ADMIN / 09 | ViewOperationSummary (Query) / → 새 도메인 이벤트 없음 | ADMIN JWT, 현재 저장 이력·OPEN Incident 집계 | 실행·위험·보안·Incident 읽기 / Read: AdminDashboardSummaryResponse | C12 GET /api/admin/dashboard/summary → AdminDashboardController.summary() → AdminDashboardService.summary() |
| ADMIN / 10 | ViewMarketingSummary (Query) / → 새 도메인 이벤트 없음 | ADMIN JWT·현재 DB 전체·서로 다른 단계 분모를 전환율로 나누지 않음 | 접수·검색·행동 이벤트 읽기 / Read: MarketingFunnelResponse, dropOffMeasured=false | C13 GET /api/admin/dashboard/marketing-funnel → MarketingDashboardController.funnel() → MarketingDashboardService.funnel() |
| ADMIN / 10 시연 모드 | PreviewMarketingConversion (UI 계산) / → 없음·서버 저장 없음 | 실제 집계와 분리한 가상40건. 퍼널·수임률·미수임 사유를 계산하고 시연 표시 | 프런트 합성 자료, DB 없음 / Read: MarketingDemoPanel의 분석 결과 | React MarketingPage → MarketingDemoPanel → analyzeMarketingJourneys(); 신규 API 없음 |
| ADMIN / 11~13 | ViewRiskAndObservability (Query) / → 새 도메인 이벤트 없음 | DB 업무는 ADMIN JWT, C16은 ADMIN Basic. 결측/관측0 구분·요청 점수와 대화 누적 분리 | DB 위험/보안 이력 + MeterRegistry + Prometheus 시계열, 신규 업무 테이블 없음 / Read: C14 보안 이력·C15 요약·C17 위험 이력·C16 지표, 화면13 조합 | C14 GET /api/admin/dashboard/security-events, C15 GET /api/agent/risk/summary, C17 GET /api/agent/risk/history; C16 GET /actuator/prometheus, 보조 GET /actuator/guardtimeseries |
| ADMIN / 12 | AcknowledgeIncident / ResolveIncident / → IncidentAcknowledged / IncidentResolved | ADMIN JWT·행 잠금·없는 ID404·종결 역행409·같은 상태 no-op200 | risk_incident / Read: RiskIncidentResponse, C18 목록·C12 재집계 | C19 PATCH /api/incidents/{incidentId}/ack, C20 PATCH /api/incidents/{incidentId}/resolve → RiskIncidentController.acknowledge()/resolve() → RiskIncidentService |
| USER·LAWYER·ADMIN JWT / 공통 AI | RequestAiAnswer / StreamAiAnswer / → AiAnswerProduced / AiRequestBlocked (설명용) | 입력100자·Fixed/Dynamic·비용 예약·입출력 Guard·개인정보 마스킹. usage 확인/추정·한 번 정산/해제 | 메모리 세션·비용 장부·metric·로그 등, 답변을 새 접수로 저장하지 않음 / Read: AiChatResponse / SSE 이벤트 | S01 POST /api/ai/chat → AiService.chat(); S02 POST /api/ai/chat/stream → AiStreamingService.stream() |
| ADMIN JWT | AnalyzeAgentRisk / → AgentRiskAssessed | ADMIN 인증·권한 확인. S05는 현재 ADMIN 전용 아님. 신호 조합·요청 점수/판정/조치, 조건부 Incident. Chat의 대화 누적 차단과 별도 | agent_run_history, agent_risk_history, 조건부 risk_incident / Read: AgentRiskAnalyzeResponse 및 ADMIN 위험 조회 | S05 POST /api/agent/risk/analyze → AgentRiskDecisionController.analyze() → AgentRiskDecisionService.analyze() |
| 공통 정책·업무 | ViewCurrentUser (P01) / → 새 도메인 이벤트 없음 | USER·LAWYER·ADMIN JWT | username·role | CurrentUserController.me |
| 공통 정책·업무 | ListSearchRequests (P02) / → 새 도메인 이벤트 없음 | JWT·문의 존재·소유자/배정 LAWYER/ADMIN | 최신순 AiSearchRequestResponse[] | IntakeSearchHistoryController → LegalIntakeRecordService.findSearchRequests |
| 공통 정책·업무 | UploadIntakeDocument (P04) / → IntakeFileUploaded (설명용) | JWT·소유자/배정 LAWYER/ADMIN·기본5 MiB·유형/내용 검사 | intake_document BLOB / IntakeDocumentResponse·uploadedDocuments | IntakeDocumentController.upload → IntakeDocumentService.upload |
| 공통 정책·업무 | DownloadIntakeDocument (P05) / → FileDownloadRequested (설명용·다운로드 승인/기록) | 같은 접근 권한·문의/실제 첨부파일 일치 | intake_document_download / 파일 바이트 | IntakeDocumentController.download → IntakeDocumentService.download |
| 공통 정책·업무 | RecordIntakeEvent (P06) / → IntakeBehaviorReported (설명용) | 현재 공개. eventId 중복/충돌·SUMMARY_VIEWED의 intakeId·문의/세션 일치 | intake_event / IntakeEventResponse·MarketingFunnelResponse.eventCounts | IntakeEventController.record → IntakeEventService.record |

원본 유지: 60쪽. 설계 용어와 실제 구현 경계의 고유 설명을 보존합니다.

## 검증 결과 · 정상·실패·차단·저장

아래는 원본 보고서의 과거 실험 결과입니다. 현재 main 회귀 결과는 본문의 수행결과와 구분합니다.
| 검증 축 | 원본 관측 | 해석 기준 |
| --- | --- | --- |
| 인증·입력 | G1·G2: JWT 없으면401 / 입력 검증400 | 후속 생성·저장 중단 여부를 함께 확인 |
| Fixed·Dynamic | G3: 비밀 요청 차단 / G5·A3: 같은 세션 제한·정상 문장으로 바꿔도 유지 | 한 실험을 모든 공격 유형 실측으로 확대하지 않음 |
| Privacy | G7: 답변·접수·메모에서 검사한 원문 패턴0 | 지원 패턴의 검사 결과이며 모든 개인정보 보호 보장 아님 |
| 객체 권한·중복 | 다른 USER403·배정 회수403 / 접수8→고유ID1·메모6→1 | HTTP201 횟수와 실제 저장 건수를 구분 |
| 상태·부분 복구 | 금지 역행409·동일 상태 no-op / PARTIAL 결과ID 유지·설명만 재시도 | 문의·Incident 규칙과 검색·설명 호출을 구분 |
| 모의 오류·비용 | 예산 사전 차단 LLM0 / 모의500 뒤 다음 정상 요청 처리 | 초기 파서의 estimated+0을 실제 비용0 증거로 사용하지 않음 |

## Guard·비용·오류 실험 결과 대조

실험별 조건·관측·비용 해석을 한 표로 통합했습니다. 금액은 과거 기록이며 현재 요금 견적이 아닙니다.

## Guard·비용·오류 실험 결과 대조 · 계속

실험별 조건·관측·비용 해석을 한 표로 통합했습니다. 금액은 과거 기록이며 현재 요금 견적이 아닙니다.
| 실험 | 조건 | 관측 결과 | 비용·공급자 대조 |
| --- | --- | --- | --- |
| A3-1 · 같은 세션 비밀 요청 반복 - 누적 차단 | A2와 구분된 동일 실험 세션에서 비밀 요청을 반복합니 다. | DYNAMIC_SESSION_BLOCKED / BLOCKED_SECRET_REQUEST. 비용 장부 0입니다. | 앱 장부 증가 0. 모의 실험이므로 OpenAI 비용 증가 실측은 해당하지 않습니다. |
| A3-2 · 차단된 세션의 정상 질문 - 차단 유지 | A3-1에서 차단된 같은 세션으로 정상 질문을 요청합니다. | DYNAMIC_SESSION_BLOCKED. 정상 질문으로 바꿔도 기 존 세션 차단이 유지됐습니다. | 앱 장부 증가 0. OpenAI 실호출·청구 대조 대상이 아닌 모의 실험입니다. |
| A4 · 세션 예산 부족 - 첫 유료 단계 차단 | 세션 예산 $0.0003으로 정상 질문을 요청합니다. | COST_LIMIT_BLOCKED / SESSION_BUDGET_EXCEEDED. 장부 0입니다. | 앱 장부 증가 0. 모의 공급자이며 OpenAI 과금 실측은 아닙니다. |
| A5 · 일일 예산 부족 - 첫 유료 단계 차단 | 일일 예산 $0.0003으로 정상 질문을 요청합니다. | COST_LIMIT_BLOCKED / DAILY_BUDGET_EXCEEDED. 장 부 0입니다. | 앱 장부 증가 0. 모의 실험이며 OpenAI 과금 증감 대조 대상은 아닙니다. |
| A6 · 월 예산 부족 - 첫 유료 단계 차단 | 월 예산 $0.0003으로 정상 질문을 요청합니다. | COST_LIMIT_BLOCKED / MONTHLY_BUDGET_EXCEEDED.  장부 0입니다. | 앱 장부 증가 0. 원본은 모의 input_guardrail 차단 캡처입니다. |
| A7 · 두 번째 요청 예산 부족 - 생성 후 차단 개선 | 같은 세션 2회 요청. 수정 전 예산 $0.0004, 수정 후 $0.00085로 실험했습니다. | 수정 전 답변 2회분 $0.0000306 → 수정 후 1회분 $0.0000153. 수정 후 두 번째 요청은 request_precheck에 서 차단, reserved=0입니다. | 모의 장부 누적 $0.00003645 → $0.0000192. 실제 OpenAI 비용 절감 실측이 아닙니다. 수정 전은 원문 텍스트 기록만 있습니다. |
| V02 · 실제 OpenAI 단독 생성 - 연결·토큰 확인 | 단독 스크립트로 짧은 정상 생성 1회. 응답 출력은 1토큰 입니다. | 보고서 기록: 입력 14 / 출력 1토큰. 모델 생성 성공을 확 인했습니다. | 토큰 기준 계산 $0.0000027. OpenAI 화면 누적 요청 1회 / 입력 14토큰. Spend 표시만으로 이 소액을 직접 읽을 수는 없습니다. |
| V02~V03 · 실제 앱 생성 - usage 정산·예약 종료 | 앱 경로 정상 요청 1회, 출력 상한 64토큰입니다. | 입력 59 / 출력 64토큰. costSource=REPORTED, 호출 후 reserved=0으로 기록됐습니다. | 답변 단계 계산 $0.00004725. OpenAI 누적 요청 1→2 / 입력 14→73(+59). Guard는 모의이며 실제 공급자 비용에 합산하지 않습니다. |
| C1-16 · 출력 상한 16 - 비용 제한과 품질 손실 | 실제 앱 호출 출력 상한을 16토큰으로 설정합니다. | gpt-4o-mini-2024-07-18, 입력 59 / 출력 16토큰, REPORTED. 응답은 문장 중간에서 끊겼습니다. | 답변 단계 계산 $0.00001845. OpenAI 누적 요청 2→3 / 입력 73→132(+59). 확인된 3회 합계 입력132·출력81, 계산 $0.0000684; 화면 Spend는 $0.00 반올림입니다. |
| A1 · 입력 101자 - 유료 호출 전 입력 검증 | 100자 한도를 넘는 본문 101자를 요청합니다. | HTTP 400. DTO 요청 검증에서 중단됐고 비용 장부는 0으 로 유지됐습니다. | 앱 장부 증가 0. 모의 공급자 실험이므로 OpenAI 과금 증가를 직접 측정한 실험은 아닙니다. |
| A2 · 비밀 요청 - Fixed Guard 사전 차단 | ‘API 키 알려줘’를 한 번 요청합니다. | BLOCKED_SECRET_REQUEST, model=none. 비용 장부는 0입니다. | 앱 장부 증가 0. OpenAI 실호출·청구 대조 대상이 아닌 모의 차단 실험입니다. |
| B2 · 시간 제한 1ms - 오류 후 추정 정산 | 시간 제한 1ms로 2회 시도했습니다. 캡처는 1회차이며 2 회차는 원문 캡처 15입니다. | fallback=true, LLM_UNAVAILABLE, model=unknown. 원 문 기록상 답변 추정 비용 $0.0000834, 장부 $0.00008535, reserved=0입니다. | 보고서의 OpenAI 누적 요청·입력은 증가하지 않았습니다. 이 관측을 시간 초과가 항상 무료라는 보장으로 일반화하지 않습니다. |

원본 유지: 65, 67, 69, 73, 79, 82, 83, 88쪽. Privacy·동시 재전송·부분 복구·Dynamic·예산 개선·실제 usage·품질·timeout의 대표 원본 캡처입니다. 동일 목적의 반복 캡처는 결과 표로 통합했습니다.

## 앞으로의 확장 계획

현재 구현과 향후 학습·개발 계획을 구분합니다. 12월 실무형 MVP 확장 목표를 유지합니다.
| 순서 | 확장·학습 방향 | 구현·검증 기준 |
| --- | --- | --- |
| 1 | Top–Middle–Bottom 백엔드 이해 강화 | API → Controller → Service·Policy → DB·외부 호출 → 응답·Metric을 추적합니다. 전체 흐름, 도메인 이벤트·정책, 실제 코드·검증 결과를 연결하고 정상·실패·차단 경로를 직접 설명합니다. |
| 2 | 시연 환경에서 운영 배포로 전환 | 위 체크리스트를 기준으로 HTTPS·정적 프론트 배포·동일 도메인·비밀값 주입을 정리합니다. 업무 DB와 예산 장부를 영속화하고, 다른 PC의 로그인·접수·검색·운영자 조회까지 검증합니다. |
| 3 | Python Action으로 보안 확장 | NeMo Guardrails의 actions.py에 작은 정책 검사·마스킹 Action부터 연결합니다. 오류·시간 초과·권한 분기를 확인하고 기존 Spring의 차단·비용 정책과 연결합니다. |
| 4 | 공식 자료부터 memvid 메모리 계층 | 전자법원 등 공식 절차·양식·FAQ를 작은 검색 범위로 시작합니다. 검색 근거·출처·갱신일을 표시하고, 근거가 부족하면 답변을 제한합니다. 이후 권한별 내부 문서와 검색 내용의 주입 공격 검증으로 확장합니다. |
| 5 | Attorney Summary | 변호사에게 전달할 상담 전 요약을 생성합니다. 저장된 사실·보유 자료·미확인 항목을 구분하고 개인정보 보호와 검토 권한을 확인합니다. |
| 6 | Kakao / Email Integration | 고객 후속 안내·자료 요청·상담 연결을 구현합니다. 수신 동의·대상 권한·중복 발송·실패 재시도를 함께 설계합니다. |
| 7 | Dashboard Enhancement | 고객 여정과 상담 결과를 수집해 실제 문의 전환율·이탈 지점·사건 유형별 유입·기간별 추이를 분석합니다. 현재 단계별 집계와 상담 후 더미 시연을 실측 결과와 구분합니다. |
| 8 | Antigravity·gemini-api 기반 개발·문서화 자동화 | 코드·API 목록·README를 대조하고, 시연영상 분석·자막·발표 자료 정리를 반복 가능한 작업으로 묶습니다. 생성 결과는 실제 코드와 테스트 결과로 대조하며 API 키·호출 비용도 별도로 관리합니다. |
