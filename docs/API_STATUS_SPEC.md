# API 정리서 — 2026-10-21 갱신

기준: 2026-10-17(Asia/Seoul), 현재 로컬 코드·화면 매핑·자동 테스트 결과. 3일간의 학습에서 **API 매핑 → Postman 기초 실험 → 코드 추적 → 필요한 수정·재실험**을 진행하기 위한 문서다. 2026-10-22 후속 변경으로 사건 본문은 분류·등록·Chat/SSE 모두 100자로 통일했다.

Postman의 진행 지도는 [오늘 버전 Backend Draft 문서의 3번 흐름도](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DRAFT_STATUS_AND_API_SPEC.md)를 사용한다. 이 문서는 상세 계약·메서드와 실제 실험 기록을 확인하는 기준으로 함께 본다.

**현재 상태 요약**

| 구분 | 오늘 기준 |
|---|---|
| 현재 버전 API | **37개 구현**: C01~C20 20개 + R01~R06 6개 + S01~S05 5개 + P01~P06 6개 |
| Security·Dynamic·Privacy | 확정한 경로의 연결 구현·자동 검증 완료. 동기·SSE의 처리 차이는 유지 |
| 비용 처리 | S01~S04의 입력 Guardrail·답변 생성·출력 Guardrail별 예산 예약·보고/추정 정산, C16 비용 지표 조회. 호출 후 오류·SSE 중단은 미확인 비용 추정 정산. P03 검색문·결과 설명도 SEARCH_QUERY/SEARCH_EXPLANATION으로 같은 장부에 정산. S05·기존 Agent 실험의 agent.cost.tokens는 별도 모의 토큰 지표이며 USD 장부에 합산하지 않음. [전체 비용 관련 API 기능표](diagrams/03_COST_CONTROL_FLOW.md) 참조 |
| 자동 검증 | 최신 SSE·P03 포함 백엔드 전체 통과. 수치·개별 근거는 [P03 검수](P03_EXTERNAL_SEARCH_REVIEW.md)와 [검증 JSON](../output/p03-search/verification.json) 참조. 아래 날짜별 검증 수치는 당시 기록 |
| 수동 Postman | **1차 실험 예정**. 자동 테스트 통과를 수동 통과로 표시하지 않음 |
| React·브라우저 | 화면01~08 공통 React 검증 135개 통과. P03은 실제 React·Spring + 모의 제공자로 브라우저 확인. 실제 유료 제공자 호출은 미실행 |
| P03 | **실행 API·LLM 검색문/설명·네이버 어댑터·React 연결 구현**. 키 설정 후 실제 유료 제공자 검증 필요 |
| 보류한 보완 | 실제 청구 대사·추정액 보정·영속 장부·분산 실행기. SSE 중단·오류·timeout·날짜 경계·중복 종료 검증은 [SSE 보완](SSE_COST_LIFECYCLE_REVIEW.md)에 기록 |

| 문서 상태명 | 이 문서에서의 의미 |
|---|---|
| **개발중** | 현재 버전의 호출 경로·코드가 구현됨. Postman·실서비스·화면 연결 검증은 별도로 기록한다. 사용자가 말하는 ‘우리 버전 구현 완료’에 해당한다. |
| **설계중** | 아직 호출 가능한 구현이 없는 후속 API. 후속 확장 중 미구현 경로에만 적용한다. P03은 2026-10-21 구현되었다. |

37개 API를 모두 같은 보안·저장 흐름으로 취급하지 않는다. 화면 행동은 [화면/API/Controller 매핑표](C:/myLectureWs/legal-ai-intake-security-server/docs/SCREEN_API_CONTROLLER_MAPPING.md), 실제 클래스·메서드 연결은 아래 **8절**, 검사 구분은 **9절**, 실험 순서·기록은 **10~11절**을 함께 본다. 동일 API를 여러 화면에서 사용해도 한 번만 센다. 기존 학습·실험 경로는 [전체 경로 부록](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_ROUTE_INVENTORY.md)으로 구분한다.

**1. 개발중 — 접수·검색·상담 검토**

| ID / 화면 | 기능 | HTTP API | 입력 | 성공 응답 / 동작 | Controller |
|---|---|---|---|---|---|
| C01 / 01 | 로그인 | `POST /api/auth/login` | `username`, `password` 필수 | 200: `accessToken`, `username`, `role` | `AuthController` |
| C02 / 02 | 사건 분류 미리보기 | `POST /api/intakes/classify-preview` | `coreStatement` 필수·최대 100자, `caseType` 선택 | 200: 분류·권장 자료·안내, 무저장 | `LegalIntakeClassificationController` |
| C03 / 03 | 문의 등록 | `POST /api/intakes` | Intake JSON, JWT 선택(전달 시 소유자 기록) | 201: `LegalIntakeResponse`, 자료 종류·검색 조건 동시 저장, uploadedDocuments는 초기 빈 배열 | `LegalIntakeRecordController` |
| C04 / 04 | 간단 안내 생성 | `POST /api/legal-intake/minimal` | `MinimalLegalIntakeRequest` | 200: 규칙 기반 유형·권장 자료·안내. 저장·LLM 없음. | `MinimalLegalIntakeController` |
| C05 / 04·07 | 문의 상세 | `GET /api/intakes/{id}` | Path `id` | 200: 문의·자료·조건·상담 메모·uploadedDocuments | `LegalIntakeRecordController` |
| C06 / 05 | 검색 요청 생성 | `POST /api/intakes/{intakeId}/search-requests` | Path ID, JSON `{}` 또는 목적·출처 | 201: `AiSearchRequestResponse`, `PREVIEW_READY` | `LegalIntakeRecordController` |
| C07 / 05 | 검색 요청 상세 | `GET /api/intakes/search-requests/{searchRequestId}` | Path ID | 200: 요청·검색문·정렬된 결과 | `LegalIntakeRecordController` |
| C08 / 05 | 결과 스냅샷 저장 | `POST /api/intakes/search-requests/{searchRequestId}/results` | 출처·제목·URL, 요약·순서 | 201: 결과 포함 검색 요청 DTO | `LegalIntakeRecordController` |
| C09 / 06 | 문의 목록 | `GET /api/intakes` | 없음 | 200: 최신순 최대 50개 `LegalIntakeResponse[]` | `LegalIntakeRecordController` |
| C10 / 08 | 상태 변경 | `PATCH /api/intakes/{id}/status` | Path ID, `status` 필수 | 200: `id`, `previousStatus`, `status`, `changed` | `LegalIntakeStatusController` |
| C11 / 08 | 상담 메모 추가 | `POST /api/intakes/{id}/memo` | Path ID, `content` 필수, `followUpItems` 선택 | 201: ID·마스킹 본문·후속 조치·생성 시각 | `ConsultationMemoController` |

위 행은 모두 **개발중**이다. C02/C10/C11은 2026-10-15에 추가했으며, 통합 테스트로 정상·실패 동작을 확인했다. Intake 상세/목록 응답에는 `consultationMemos` 배열을 추가했고 각 항목의 본문은 패턴 마스킹한다. 이 표의 기존 문의 등록은 자료 종류를 저장한다. 실제 파일 첨부는 아래 추가 API로 제공하며, 외부 검색 실행·프런트 연결 완료를 의미하지 않는다.

**2. 개발중 — 운영·마케팅·감사**

| ID / 화면 | 기능 | HTTP API | 입력 | 성공 응답 / 동작 | Controller |
|---|---|---|---|---|---|
| C12 / 09 | 운영 요약 | `GET /api/admin/dashboard/summary` | 없음 | 200: Agent 실행·위험 이력·보안 이벤트·열린 Incident 수, `riskSummary` | `AdminDashboardController` |
| C13 / 10 | 마케팅 기본 집계 | `GET /api/admin/dashboard/marketing-funnel` | 없음 | 200: 접수·검색·현재 상담 준비 수, 분포, 보고된 행동 이벤트 수 | `MarketingDashboardController` |
| C14 / 11 | 최근 보안 이벤트 | `GET /api/admin/dashboard/security-events` | 없음 | 200: 최근 최대 50개 보안 이벤트 배열 | `AdminDashboardController` |
| C15 / 11 | 위험 집계 | `GET /api/agent/risk/summary` | 없음 | 200: 전체·등급별·결정별·조치별 수 | `AgentRiskHistoryController` |
| C16 / 11 | Prometheus 지표 | `GET /actuator/prometheus` | ADMIN Basic 인증 | 200: Prometheus 텍스트. 입력 검사·답변 생성·출력 검사별 예약·보고·추정·합계 비용과 전체 합계. | Spring Boot Actuator |
| C17 / 12 | 위험 이력 | `GET /api/agent/risk/history` | Query `riskLevel`, `decision` 선택(riskLevel 우선) | 200: 위험 이력 배열 | `AgentRiskHistoryController` |
| C18 / 12 | Incident 목록 | `GET /api/incidents` | 없음 | 200: 최근 Incident 배열 | `RiskIncidentController` |
| C19 / 12 | Incident 확인 | `PATCH /api/incidents/{incidentId}/ack` | Path ID | 200: 변경된 Incident DTO | `RiskIncidentController` |
| C20 / 12 | Incident 종결 | `PATCH /api/incidents/{incidentId}/resolve` | Path ID | 200: 변경된 Incident DTO | `RiskIncidentController` |

위 행은 모두 **개발중**이다. C13의 기본 집계는 2026-10-15에, 행동 이벤트 연계는 2026-10-16에 추가·자동 검증했다. C12는 화면 설계에 있는 모든 문의 KPI를 제공하는 API가 아니라 현재 Agent/보안 요약이다. C16은 실제 Prometheus 수집 성공을 이번 작업에서 검증하지 않았다.

**3. 개발중 — 법률 Reference API**

| ID | 기능 | HTTP API | 입력 | 200 응답 | Controller |
|---|---|---|---|---|---|
| R01 | 절차 목록 | `GET /api/legal/procedures` | Query `domain` 선택 | `code`, `domain`, `title`, `summary`, `userRole` 배열 | `LegalProcedureController` |
| R02 | 절차 상세 | `GET /api/legal/procedures/{code}` | Path `code` | 기본 정보·`steps`·`forms`·`knowledgeGraphEdges` | 동일 |
| R03 | 절차 단계 | `GET /api/legal/procedures/{code}/steps` | Path `code` | 순서·코드·제목·설명·체크리스트 배열 | 동일 |
| R04 | 양식 목록 | `GET /api/legal/forms` | Query `procedure` 선택 | 양식 코드·절차 코드·제목·파일 유형·사용 안내 배열 | 동일 |
| R05 | 양식 상세 | `GET /api/legal/forms/{formCode}` | Path `formCode` | 양식 DTO | 동일 |
| R06 | 형사 보완수사 체크리스트 | `GET /api/legal/criminal/checklist/supplementary-investigation` | 없음 | `domain`, `role`, `stageCode`, `stageLabel`, `checklist`, `recommendedAction` | 동일 |

위 행은 모두 **개발중**이다. 이전 Spring Boot–React 연동 과제에서 구현한 API 6개를 이번 프로젝트의 Legal Reference 모듈로 재사용한다. 기존 React에서는 R01·R02·R06이 연결되어 있다. 화면 2의 사건 유형 안내, 화면 3의 문의 입력·자료 정리, 화면 4의 결과·준비자료 안내에 활용하며 새 핵심 화면의 프런트 연결은 이후 단계다.

현재 구현 37개에는 이 기존 6개도 포함된다. 절차·단계·양식은 JPA 조회이며 보완수사 체크리스트는 현재 고정 prototype 데이터다. Reference는 C02 규칙 분류·C03 문의 저장·P03 실제 외부 검색과 구분한다.

**4. 개발중 — AI·Guardrail 보조 API**

| ID | 기능 | HTTP API | 입력 | 성공 응답 / 동작 | Controller |
|---|---|---|---|---|---|
| S01 | 동기 AI Chat | `POST /api/ai/chat` | `message` 필수·DTO/Cost 기본 최대 100자, `sessionId` 선택 | 200: `answer`, `blocked`, `fallback`, `traceId`, `model`, 호환용 `reason`, `reasonCode`, `detailCode`, `usageAvailable`, `costSource` | `AiController` |
| S02 | SSE AI Chat | `POST /api/ai/chat/stream` | 동일 | `text/event-stream`; meta 후 전체 답변 검사·마스킹 완료 시 token/done. done에 usageAvailable·costSource, 미확인 토큰 수는 null | `AiController` |
| S03 | Input Guardrail 단독 검사 | `POST /api/legal/guardrail/input` | `message` 필수, `sessionId` 선택 | 200: 기존 검사 필드 + `reasonCode`, `detailCode`, `usageAvailable`, `costSource` | `LegalGuardrailController` |
| S04 | Output Guardrail 단독 검사 | `POST /api/legal/guardrail/output` | `response` 필수, `sessionId` 선택 | 200: 기존 검사 필드 + `reasonCode`, `detailCode`, `usageAvailable`, `costSource` | `LegalOutputGuardrailController` |
| S05 | Agent 위험 분석 | `POST /api/agent/risk/analyze` | `userInput`, `toolName`, 재시도·토큰 수, 위험 신호 Boolean 등 | 200: `decision`, `riskScore`, `riskLevel`, `signals`, `recommendedAction`, `guardrailReady`, `historyId`, `traceId` | `AgentRiskDecisionController` |

위 행은 모두 **개발중**이다. 동기·SSE 모두 공통 입력 보안·Privacy를 연결했다. SSE는 생성 비용을 먼저 정산하고 전체 답변 검사·마스킹 후 본문을 전송한다. Guardrail 비용도 공통 장부에 합산하며 오류·timeout·연결 종료 시 미확인 생성 비용을 추정 정산한다. C16에 세 단계별 비용 지표를 추가했다. 정상 완료의 usage 부재·불완전 값은 ESTIMATED, 유효한 보고값(0 포함)은 REPORTED로 집계한다. [전체 흐름도](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_FULL_FLOW.md)를 참조한다. 이번 테스트는 외부 NeMo/LLM 실서비스를 호출하지 않는다. AI 정책 차단은 HTTP 200 안의 `blocked`, `fallback`, `reasonCode`, `detailCode`로 구분하며 기존 `reason` 값도 유지한다.

현재 입력 기준: 동기·SSE 모두 DTO/Cost 기본 최대 100자이며 101자부터 DTO Validation HTTP 400이다. 비용 길이 차단은 별도 정책을 50자로 낮춘 시험에서 51~100자로 확인한다. 생성 출력 상한은 2,048토큰, Guard 출력 예약 추정은 512토큰(원격 상한 아님)이다. 소스 기본 누적·하루·세션 예산은 $2·$0.50·$0.12다. Dynamic·Cost의 sessionId는 앞뒤 공백을 제거하며 누락 시 anonymous를 사용한다.

**5. 주요 API 상세 계약**

**C02 — 분류 미리보기**

```json
{"caseType":"UNKNOWN","coreStatement":"돈을 빌려줬는데 못 받고 있습니다."}
```

| 필드 | 제약 / 의미 |
|---|---|
| 요청 `caseType` | 선택, 최대 50자. CIVIL/CRIMINAL/FAMILY/ELECTRONIC_LITIGATION/UNKNOWN 지원. |
| 요청 `coreStatement` | 필수·공백 불가·최대 100자 |
| 응답 `caseType`, `caseTypeLabel`, `disputeArea` | 정규화된 코드·표시명·분쟁 영역 |
| 응답 `recommendedDocuments`, `clientGuideMessage` | 권장 자료 배열·안내 문구 |
| 응답 `classificationMethod`, `persisted` | `RULE_BASED`, `false` |

지원 코드가 선택되면 우선 사용한다. UNKNOWN 또는 미지원 코드라면 본문 키워드로 분류한다. 이 요청은 Intake나 검색 조건을 저장하지 않고 LLM도 호출하지 않는다. 초기 사건 요약은 100자까지 허용하며, 공백·누락·101자 이상은 DTO 검증에서 400·INVALID_REQUEST와 fieldErrors.coreStatement를 반환한다. 문자 수 기준의 입력 정책이며 LLM 비용 차단 코드와 구분한다. 2026-10-22에 C02와 문의 등록의 상한을 100자로 통일했다.

**C10 — 상태 변경**

```json
{"status":"REVIEWED"}
```

허용 상태는 `SUBMITTED`, `REVIEWED`, `NEED_MORE_INFO`, `CONSULTATION_READY`, `CLOSED`이다. 화면 초안의 NEW는 SUBMITTED에 해당한다. 같은 상태 재요청은 200, `changed=false`. 종료 후 재개와 처리 후 SUBMITTED 복귀는 409. 없는 문의는 404, 미지원 상태는 400이다.

```json
{"id":1,"previousStatus":"SUBMITTED","status":"REVIEWED","changed":true}
```

**C11 — 상담 메모**

```json
{"content":"자료를 확인하고 상담 일정을 정리합니다.","followUpItems":["REQUEST_DOCUMENTS","SCHEDULE_CONSULTATION"]}
```

| 필드 | 제약 / 의미 |
|---|---|
| 요청 `content` | 필수·공백 불가·최대 4,000자 |
| 요청 `followUpItems` | 선택·최대 4개. REQUEST_DOCUMENTS / SCHEDULE_CONSULTATION / DOCUMENT_REVIEW / E_LITIGATION_GUIDE |
| 응답 `id`, `intakeId` | 메모·문의 ID |
| 응답 `contentMasked` | 개인정보 패턴 마스킹 본문. 자유문 속 모든 이름을 자동 식별한다는 뜻은 아님. |
| 응답 `followUpItems`, `createdAt` | 중복 제거한 조치 코드, 생성 시각 |

없는 문의는 404, 본문·조치 검증 실패는 400. Intake 상세와 목록의 `consultationMemos`에서도 메모를 읽을 수 있다. DB 원문 보관과 API 출력 마스킹은 구분한다.

**C13 — 마케팅 기본 집계**

| 응답 필드 | 의미 |
|---|---|
| `submittedIntakeCount` | 현재 DB의 전체 Intake 수. 상태가 바뀐 문의도 포함. |
| `searchRequestCount` | 전체 Search Request 수 |
| `searchRequestedIntakeCount` | Search Request의 `intakeId`를 중복 제거한 문의 수 |
| `consultationReadyIntakeCount` | 현재 상태가 CONSULTATION_READY인 문의 수 |
| `stages` | 단계별 `stage`, `count`, `measurement` |
| `caseTypeDistribution` | 사건 유형별 문의 수 |
| `desiredNextStepDistribution` | 원하는 다음 단계별 문의 수 |
| `regionDistribution` | 선호 지역별 문의 수. 미입력은 `지역 미정`. |
| `measurementBasis` | `ALL_RECORDS_IN_CURRENT_DATABASE`; 현재 DB 전체 기준 |
| `dropOffMeasured` | 현재 `false` |
| `eventCounts` | VISIT / INTAKE_STARTED / SUMMARY_VIEWED별 보고된 기록 수 |

방문·시작·요약조회 단계는 행동 수집 API의 기록 수를 `count`, `measurement=REPORTED_EVENT`로 반환한다. 기록이 없으면 0이며, `eventCounts`에도 유형별 수를 제공한다. 동일 eventId 재전송은 중복 집계하지 않지만 서로 다른 ID는 별도 이벤트다. 순방문자 수나 자동 수집을 의미하지 않는다. 검색 단계는 DISTINCT_INTAKE, 상담 준비 단계는 CURRENT_STATUS다. 서로 다른 측정 기준을 한 줄의 엄밀한 전환 퍼널이나 누적 도달 수로 해석하지 않는다. 기간 필터·코호트·이탈률은 설계중이다.

**기존 Intake 요청·응답 핵심**

Intake 생성 필수 필드는 `inquiryPurpose`(100자), `caseType`(50자), `partyPosition`(50자), `coreStatement`(100자), `desiredNextStep`(100자)이다. 선택 필드는 세션·이름·전화·사건일·상대방·금액·현재 단계·지역·검색 선호·자료 종류다. 상세 필드 목록은 [오늘 버전 Draft 실험 가이드](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DRAFT_STATUS_AND_API_SPEC.md)에 있으며 다음 변경을 적용한다.

- `LegalIntakeResponse`에 `consultationMemos`·`uploadedDocuments` 배열 포함.
- `AiSearchRequestCreateRequest.searchPurpose`와 DB 컬럼을 최대 **300자**로 통일.
- 문의 상태는 새 상태 변경 API로 변경 가능.
- 분류 preview·메모·마케팅 기본 집계는 코드·자동 검증이 추가됨.

검색 요청 생성 본문은 필수이며 기본값만 사용하려면 `{}`를 보낸다. 생략한 `searchPurpose`는 Intake의 목적, 없으면 LAWYER_SEARCH_ROUTING을 사용한다. 출처 기본값은 KBA_NAVER_PUBLIC_WEB. 결과 생성 필수값은 `sourceType`(100자), `title`(300자), `url`(1,000자), 선택값은 `snippet`(1,000자), `displayOrder`이다. 결과 추가만으로 검색 요청 상태가 완료로 바뀌지 않는다.

**6. 추가 구현 6개**

기존 제안 P01·P02·P04·P05·P06은 2026-10-16에 구현·자동 검증했다. 원래 제안 ID는 추적을 위해 유지하며 현재 상태는 **개발중**이다. 기존 API 31개와 내부 보완 8개, 이번 신규 API 5개는 서로 다른 집계다.

| ID | 구현 API | 요청 / 정책 | 성공 응답 | Controller |
|---|---|---|---|---|
| P01 | `GET /api/me` | JWT 필수 | 200: username, role | CurrentUserController |
| P02 | `GET /api/intakes/{intakeId}/search-requests` | JWT·소유자 또는 관리자 | 200: 최신순 검색 요청·결과 배열, 없으면 [] | IntakeSearchHistoryController |
| P04 | `POST /api/intakes/{intakeId}/documents` | JWT·소유자/관리자, multipart file 필수, documentType 선택 | 201: id·intakeId·documentType·originalFilename·contentType·sizeBytes·createdAt·downloadUrl | IntakeDocumentController |
| P05 | `GET /api/intakes/{intakeId}/documents/{documentId}/download` | JWT·소유자/관리자, 문의·파일 연결 확인 | 200: 파일 바이트, attachment·nosniff·no-store, 다운로드 요청 기록 | IntakeDocumentController |
| P06 | `POST /api/intake-events` | eventId·eventType·sessionId 필수, intakeId 선택. SUMMARY_VIEWED는 intakeId 필수 | 최초 201/created=true, 동일 재전송 200/false, 다른 내용으로 ID 재사용 409 | IntakeEventController |

상세 계약·설정·실험 방법: [5개 API 구현 결과](C:/myLectureWs/legal-ai-intake-security-server/docs/DAY4_FIVE_APIS_2026-10-16.md). [추가 Postman Collection](C:/myLectureWs/legal-ai-intake-security-server/docs/ADDITIONAL_FIVE_APIS.postman_collection.json).

- 로그인 상태로 문의 생성 시 서버가 JWT username을 ownerUsername에 저장한다. 기존 익명 문의의 신규 파일·검색 이력 접근은 관리자만 가능하다. 기존 전체 API에 소유권 검사를 확장한 것은 아니다.
- 파일 기본 5 MiB, multipart 요청 6 MiB. PDF·PNG·JPEG의 확장자/시그니처, TXT의 UTF-8을 검사한다. 원본은 현재 DB의 BLOB이며 H2 재시작 시 삭제된다. 빈 파일 400, 크기 초과 413, 유형 불일치 415, 대상 없음 404다.
- uploadedDocuments는 문의 목록·상세의 실제 업로드 메타데이터다. 자료 종류만 저장한 레코드의 다운로드는 404다.
- 이벤트는 VISIT/INTAKE_STARTED/SUMMARY_VIEWED를 지원한다. intakeId 전달 시 존재·sessionId 일치를 확인한다. 서버 UTC receivedAt을 저장하고 eventId 기본키로 동시 재전송 중복을 방지한다. 클라이언트가 보고하는 공개 수집 경로이며 자동 행동 추적·기간별 전환율은 별도다.

P03은 2026-10-21 구현했다. [상세 요청·응답·단계·검수](P03_EXTERNAL_SEARCH_REVIEW.md).

| ID | 구현 API | 목적 / 정책 | 상태 |
|---|---|---|---|
| P03 | `POST /api/intakes/search-requests/{searchRequestId}/execute` | JWT·소유자/ADMIN. 본문 `{retryExplanation?:boolean}`. 202 현재 요청 DTO + 실행 단계. LLM 검색문·네이버 수집·저장·결과별 설명. PARTIAL은 설명만 재시도 | 개발중: 구현·모의 검증 완료, 실제 유료 제공자 미검증 |

SSE 중단·오류·timeout 추정 정산과 중복 콜백 방지는 기존 S02 내부에 구현했으며 신규 API 개수는 늘지 않는다. 날짜 경계 귀속은 SSE 보완에서 검증했으며 실제 청구 대사는 후속 범위다. 기존 전체 API의 역할·소유권 확대, 운영 DB 영속화, 목록 페이지·필터, 메모 작성자·수정 이력, 상태 이력, Marketing 기간·코호트 분석은 후속 확장이다.

**7. 공통 통신·오류·검증 상태**

- JSON 요청은 `Content-Type: application/json`. 성공 응답은 대부분 DTO 또는 배열을 직접 반환한다. C16은 텍스트, S02는 SSE.
- 현재 `/api/me`, `/api/ai/**`, GET 이외 `/api/legal/**`, 신규 파일 API, GET 문의별 검색 이력, P03 검색 실행은 JWT 인증 대상. 나머지 기존 `/api/intakes/**`, `/api/admin/**`, `/api/agent/**`, `/api/incidents/**`, 행동 수집 `/api/intake-events`는 현재 개발용 공개 설정이다.
- Actuator는 별도 Security 체인이다. health/info는 공개, 그 밖의 Endpoint는 ADMIN Basic 인증이다.
- CORS는 application·Actuator Security 체인에서 공통 source를 사용한다. preflight를 처리한 뒤 실제 요청의 JWT·ADMIN Basic 인증을 유지하며, SSE 완료 비동기 dispatch에서도 JWT 인증을 복원한다.
- Validation 오류는 400, `ErrorResponse(code=INVALID_REQUEST, message, status, path, traceId, fieldErrors, timestamp)`이다.
- 존재하지 않는 Intake·검색 요청·파일은 해당 신규 업무 경로에서 404, 금지 상태 전이는 409. 기존 다른 모듈의 예외·오류 본문은 동일하게 통일되어 있지 않다.
- traceId는 `/api/**`의 Interceptor·로그에서 추적한다. 모든 성공 DTO가 traceId 필드를 포함하는 것은 아니다.
- 기본 실행은 H2 메모리 DB·`create-drop`이다. 별도 `mysql` 프로필은 사용자의 root 계정과 프로젝트 DB `legal_intake_lab`·`update`를 사용한다. 2026-10-16 실제 연결·테이블 20개·Reference 조회 200·종료 및 재시작 후 기본 데이터 유지를 확인했다. 문의 등록 등 전체 수동 실험과 구분하며, 근거는 [MySQL 연결 준비서](MYSQL_CONNECTION_CHECK.md)를 따른다.

자동 테스트: `gradlew.bat test --no-daemon --console=plain`, **124개 통과 / 실패 0 / 오류 0**. 기존 93개에 비용 API·SSE·C16 통합 검증 14개, Guardrail 비용 단위 검증 7개, Guardrail HTTP usage 검증 10개를 추가했다. 외부 NeMo·LLM은 mock·로컬 HTTP/SSE 대역으로 검증하며, 모든 36개 API의 실서비스·프런트 연결을 전수 검증한 결과는 아니다.

수동 검증용 [Postman Collection](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DRAFT_TODAY.postman_collection.json)을 제공한다. 추가 5개용 Collection도 함께 사용한다. 수동 실험 기록은 아래 11절에 남긴다.

저장 테이블·참조 ID·실제 FK는 [현재 ERD](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_ERD.md)에서 확인한다.

**8. Middle 진입 — 36개 API의 실제 코드 매핑**

Top에서는 화면·API·모듈의 연결을 확인한다. Middle에서는 **입력 DTO → Controller 메서드 → Service 또는 Client → 적용 정책 → 저장·외부 호출 → 응답 DTO**를 따라간다. 아래 링크는 오늘 코드의 실제 파일·메서드 위치다. 메서드 존재를 확인한 표이며 학습 완료·Postman 통과 표시는 아니다.

**8-1. 접수·검색·상담 검토**

| ID | Controller 진입점 | 주 처리 메서드 | 요청 → 응답 | 저장·조회·처리 책임 |
|---|---|---|---|---|
| C01 | [AuthController.login](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/AuthController.java:27) | [AuthController.login](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/AuthController.java:27) | [AuthController.LoginRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/AuthController.java:47) → [AuthController.LoginResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/AuthController.java:40) | AuthenticationManager가 개발 계정 확인 → JwtTokenProvider로 JWT 발급. 업무 DB 저장 없음 |
| C02 | [LegalIntakeClassificationController.preview](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeClassificationController.java:22) | [LegalIntakeRecordService.classifyPreview](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:70) | [LegalIntakeClassificationRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalIntakeClassificationRequest.java) → [LegalIntakeClassificationResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalIntakeClassificationResponse.java) | 규칙 분류·추천 자료. DB 저장·LLM 호출 없음 |
| C03 | [LegalIntakeRecordController.create](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:34) | [LegalIntakeRecordService.create](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:97) | [LegalIntakeCreateRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalIntakeCreateRequest.java) → [LegalIntakeResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalIntakeResponse.java) | LegalIntake + 자료 종류 IntakeDocument + LawyerSearchCondition을 한 트랜잭션으로 저장. JWT가 있으면 소유자 기록 |
| C04 | [MinimalLegalIntakeController.analyze](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/MinimalLegalIntakeController.java:20) | [MinimalLegalIntakeService.analyze](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/MinimalLegalIntakeService.java:21) | [MinimalLegalIntakeRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/MinimalLegalIntakeRequest.java) → [MinimalLegalIntakeResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/MinimalLegalIntakeResponse.java) | 규칙 안내·지정 필드 마스킹. DB 저장·LLM 호출 없음 |
| C05 | [LegalIntakeRecordController.findById](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:44) | [LegalIntakeRecordService.findById](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:156) | Path·Query·인증 등(상단 계약 참조) → [LegalIntakeResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalIntakeResponse.java) | 문의·자료·검색 조건·메모 조회, 지정 필드 마스킹, uploadedDocuments 메타데이터 |
| C06 | [LegalIntakeRecordController.createSearchRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:50) | [LegalIntakeRecordService.createSearchRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:182) | [AiSearchRequestCreateRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AiSearchRequestCreateRequest.java) → [AiSearchRequestResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AiSearchRequestResponse.java) | 문의·최신 검색 조건 확인 → AiSearchRequest PREVIEW_READY 저장 |
| C07 | [LegalIntakeRecordController.findSearchRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:56) | [LegalIntakeRecordService.findSearchRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:178) | Path·Query·인증 등(상단 계약 참조) → [AiSearchRequestResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AiSearchRequestResponse.java) | AiSearchRequest + LawyerSearchCondition + 정렬된 SearchResultSnapshot 조회 |
| C08 | [LegalIntakeRecordController.addSearchResult](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:62) | [LegalIntakeRecordService.addSearchResult](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:215) | [SearchResultSnapshotRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/SearchResultSnapshotRequest.java) → [AiSearchRequestResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AiSearchRequestResponse.java) | 전달한 제목·URL·요약을 SearchResultSnapshot으로 저장. 실제 검색 호출 없음 |
| C09 | [LegalIntakeRecordController.findRecent](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:39) | [LegalIntakeRecordService.findRecent](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:165) | Path·Query·인증 등(상단 계약 참조) → [LegalIntakeResponse[]](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalIntakeResponse.java) | 최신 최대 50개 문의 + 자료·조건·메모 조회, 지정 필드 마스킹 |
| C10 | [LegalIntakeStatusController.update](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeStatusController.java:23) | [LegalIntakeRecordService.updateStatus](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:81) | [LegalIntakeStatusRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalIntakeStatusRequest.java) → [LegalIntakeStatusResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalIntakeStatusResponse.java) | LegalIntake.changeStatus → IntakeStatus 정책 → JPA 변경 반영 |
| C11 | [ConsultationMemoController.create](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/ConsultationMemoController.java:26) | [LegalIntakeRecordService.createMemo](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:94) | [ConsultationMemoCreateRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/ConsultationMemoCreateRequest.java) → [ConsultationMemoResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/ConsultationMemoResponse.java) | 문의 존재 확인 → ConsultationMemo 저장 → 출력 본문 마스킹 |

**8-2. 운영·마케팅·감사**

| ID | Controller 진입점 | 주 처리 메서드 | 요청 → 응답 | 저장·조회·처리 책임 |
|---|---|---|---|---|
| C12 | [AdminDashboardController.summary](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java:23) | [AdminDashboardService.summary](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AdminDashboardService.java:35) | Path·Query·인증 등(상단 계약 참조) → [AdminDashboardSummaryResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AdminDashboardSummaryResponse.java) | Agent 실행·위험·보안 이벤트·열린 Incident 집계. 마케팅·Chat 비용 집계와 구분 |
| C13 | [MarketingDashboardController.funnel](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/MarketingDashboardController.java:19) | [MarketingDashboardService.funnel](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/MarketingDashboardService.java:32) | Path·Query·인증 등(상단 계약 참조) → [MarketingFunnelResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/MarketingFunnelResponse.java) | LegalIntake·AiSearchRequest·IntakeEvent에서 서로 다른 기준의 수를 집계 |
| C14 | [AdminDashboardController.securityEvents](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java:28) | [AdminDashboardService.recentSecurityEvents](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AdminDashboardService.java:46) | Path·Query·인증 등(상단 계약 참조) → [SecurityEventHistoryResponse[]](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/SecurityEventHistoryResponse.java) | SecurityEventHistoryRepository에서 최근 최대 50개 조회 |
| C15 | [AgentRiskHistoryController.summary](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java:30) | [AgentRiskHistoryQueryService.summarize](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskHistoryQueryService.java:41) | Path·Query·인증 등(상단 계약 참조) → [AgentRiskSummaryResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AgentRiskSummaryResponse.java) | AgentRiskHistory의 등급·결정·권장 조치 집계. Chat Dynamic 세션 Map 조회 API가 아님 |
| C16 | 프레임워크 Actuator | [SecurityConfig.actuatorSecurity](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java:42) | Path·Query·인증 등(상단 계약 참조) → Prometheus 텍스트 | Spring Boot Actuator + Prometheus registry의 텍스트 노출. 사용자 작성 Controller/DTO 없음 |
| C17 | [AgentRiskHistoryController.history](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java:24) | [AgentRiskHistoryQueryService.findRecent](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskHistoryQueryService.java:24) | Path·Query·인증 등(상단 계약 참조) → [AgentRiskHistoryResponse[]](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AgentRiskHistoryResponse.java) | AgentRiskHistory 조회. riskLevel 우선, 없으면 decision, 둘 다 없으면 최근 최대 50개 |
| C18 | [RiskIncidentController.findRecent](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:24) | [RiskIncidentService.findRecent](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java:47) | Path·Query·인증 등(상단 계약 참조) → [RiskIncidentResponse[]](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/RiskIncidentResponse.java) | RiskIncidentRepository의 최근 최대 50개 |
| C19 | [RiskIncidentController.acknowledge](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:29) | [RiskIncidentService.acknowledge](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java:55) | Path·Query·인증 등(상단 계약 참조) → [RiskIncidentResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/RiskIncidentResponse.java) | RiskIncident.acknowledge → 상태 저장 |
| C20 | [RiskIncidentController.resolve](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:34) | [RiskIncidentService.resolve](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/RiskIncidentService.java:62) | Path·Query·인증 등(상단 계약 참조) → [RiskIncidentResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/RiskIncidentResponse.java) | RiskIncident.resolve → 상태 저장 |

**8-3. 법률 Reference**

| ID | Controller 진입점 | 주 처리 메서드 | 요청 → 응답 | 저장·조회·처리 책임 |
|---|---|---|---|---|
| R01 | [LegalProcedureController.procedures](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:28) | [LegalProcedureService.findProcedures](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:49) | Path·Query·인증 등(상단 계약 참조) → [LegalProcedureListResponse[]](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalProcedureListResponse.java) | LegalProcedureRepository 목록·분야 필터 조회 |
| R02 | [LegalProcedureController.procedureDetail](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:58) | [LegalProcedureService.findProcedureDetail](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:85) | Path·Query·인증 등(상단 계약 참조) → [LegalProcedureDetailResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalProcedureDetailResponse.java) | LegalProcedure·단계·양식 JPA 조회와 응답 구성 |
| R03 | [LegalProcedureController.procedureSteps](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:74) | [LegalProcedureService.findProcedureSteps](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:112) | Path·Query·인증 등(상단 계약 참조) → [LegalProcedureStepResponse[]](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalProcedureStepResponse.java) | LegalProcedureStepRepository에서 절차별 단계 조회 |
| R04 | [LegalProcedureController.forms](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:86) | [LegalProcedureService.findForms](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:128) | Path·Query·인증 등(상단 계약 참조) → [LegalFormTemplateResponse[]](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalFormTemplateResponse.java) | LegalFormTemplateRepository에서 양식 조회·절차 필터 |
| R05 | [LegalProcedureController.form](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:100) | [LegalProcedureService.findForm](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:152) | Path·Query·인증 등(상단 계약 참조) → [LegalFormTemplateResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalFormTemplateResponse.java) | LegalFormTemplateRepository에서 코드로 양식 조회 |
| R06 | [LegalProcedureController.supplementaryInvestigationChecklist](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:111) | [LegalProcedureService.supplementaryInvestigationChecklist](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalProcedureService.java:165) | Path·Query·인증 등(상단 계약 참조) → [LegalChecklistResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/LegalChecklistResponse.java) | 현재 고정 prototype 체크리스트. 새 DB 쓰기 없음 |

**8-4. AI·Guardrail**

| ID | Controller 진입점 | 주 처리 메서드 | 요청 → 응답 | 저장·조회·처리 책임 |
|---|---|---|---|---|
| S01 | [AiController.chat](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AiController.java:28) | [AiService.chat](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AiService.java) | [AiChatRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AiChatRequest.java) → [AiChatResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AiChatResponse.java) | Dynamic → Fixed → 길이 → 입력 검사 예약·정산 → 생성 예약·정산 → 출력 검사 예약·정산 → Privacy. 세션·예산은 메모리, 지표는 Metric |
| S02 | [AiController.chatStream](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AiController.java:33) | [AiStreamingService.stream](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AiStreamingService.java) | [AiChatRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AiChatRequest.java) → [AiStreamEvent](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AiStreamEvent.java) | 공통 입력 검사·검사 비용 정산 → 생성 예약·전체 버퍼·정산 → 출력 검사 예약·정산 → Privacy → token/done. 중단·오류는 추정 정산 |
| S03 | [LegalGuardrailController.checkInput](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalGuardrailController.java) | [CostedGuardrailService.checkInput](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/cost/CostedGuardrailService.java) | [LegalGuardrailController.InputCheckRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalGuardrailController.java) → [LegalGuardrailController.InputCheckResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalGuardrailController.java) | Controller → CostedGuardrailService → NemoInputGuardrailClient. 검사 전 예약·보고/추정 정산, 업무 DB 쓰기 없음 |
| S04 | [LegalOutputGuardrailController.checkOutput](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalOutputGuardrailController.java) | [CostedGuardrailService.checkOutput](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/cost/CostedGuardrailService.java) | [LegalOutputGuardrailController.OutputCheckRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalOutputGuardrailController.java) → [LegalOutputGuardrailController.OutputCheckResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalOutputGuardrailController.java) | Controller → CostedGuardrailService → 로컬 정책·NemoOutputGuardrailClient. 외부 검사 전 예약·정산. 단독 API는 별도 Privacy 마스킹을 호출하지 않음 |
| S05 | [AgentRiskDecisionController.analyze](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskDecisionController.java:22) | [AgentRiskDecisionService.analyze](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskDecisionService.java:42) | [AgentRiskAnalyzeRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AgentRiskAnalyzeRequest.java) → [AgentRiskAnalyzeResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AgentRiskAnalyzeResponse.java) | 신호 점수·판정 → AgentRiskHistory·AgentRunHistory 저장 → 조건에 따라 Incident 생성, Metric 기록. 실제 검색 실행 아님 |

**8-5. 추가 구현**

| ID | Controller 진입점 | 주 처리 메서드 | 요청 → 응답 | 저장·조회·처리 책임 |
|---|---|---|---|---|
| P01 | [CurrentUserController.me](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/CurrentUserController.java:10) | [CurrentUserController.me](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/CurrentUserController.java:10) | Path·Query·인증 등(상단 계약 참조) → [CurrentUserController.CurrentUserResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/CurrentUserController.java:11) | Security Authentication에서 username·role 조회. 별도 Service/업무 DB 없음 |
| P02 | [IntakeSearchHistoryController.history](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeSearchHistoryController.java:24) | [LegalIntakeRecordService.findSearchRequests](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/LegalIntakeRecordService.java:176) | Path·Query·인증 등(상단 계약 참조) → [AiSearchRequestResponse[]](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AiSearchRequestResponse.java) | IntakeAccessService.requireAccess 후 문의별 최신순 요청·결과 조회 |
| P04 | [IntakeDocumentController.upload](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeDocumentController.java:25) | [IntakeDocumentService.upload](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeDocumentService.java:41) | multipart file + documentType → [IntakeDocumentResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/IntakeDocumentResponse.java) | IntakeAccessService → 파일 크기·유형 → IntakeDocument 메타데이터·BLOB 저장. 입력은 MultipartFile + documentType |
| P05 | [IntakeDocumentController.download](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeDocumentController.java:32) | [IntakeDocumentService.download](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeDocumentService.java:79) | Path·Query·인증 등(상단 계약 참조) → 파일 byte[] | 소유자·문의/파일 연결 확인 → 원본 byte[] 반환 → IntakeDocumentDownload 요청 기록 저장 |
| P06 | [IntakeEventController.record](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeEventController.java:18) | [IntakeEventService.record](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeEventService.java:26) | [IntakeEventRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/IntakeEventRequest.java) → [IntakeEventResponse](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/IntakeEventResponse.java) | 문의·세션 연결 확인 → eventId PK 중복 방지 → IntakeEvent 저장. 동일 재전송 200, 내용 충돌 409 |

P02·P04·P05는 [IntakeAccessService.requireAccess](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/IntakeAccessService.java:16)를 함께 읽는다. P02는 Controller에서 접근을 확인한 뒤 조회 Service를 호출하고, 파일은 해당 Service 안에서 접근을 확인한다.

모든 API에 Service·Repository·Entity가 반드시 있는 것은 아니다. 내 정보는 Security Authentication, Guardrail 단독 API는 비용 Service와 Client, Prometheus는 프레임워크로 이어진다. Read Model은 응답을 구성하는 조회 결과이며 별도의 물리 테이블을 뜻하지 않는다.

**9. API별 검사·정책 매핑**

| 대상 | 실제 적용 검사 | 저장·출력 / 실패 해석 |
|---|---|---|
| C01 로그인 / P01 내 정보 | C01 필수 입력·계정 인증, P01 JWT | 사용자·역할·JWT. P01 인증 누락/잘못된 토큰은 401 |
| C02 preview | @Valid: 본문 필수·길이, 규칙 분류 | 무저장·LLM 없음. 필드 위반 400 |
| C04 minimal | 현재 DTO에 @Valid 제약 없음. Service의 규칙·기본값 처리 | 무저장·LLM 없음, 지정 출력 마스킹. preview와 같은 Validation이라고 표시하지 않음 |
| C03/C05/C09 저장·조회 | C03 @Valid, 조회 대상 확인, JWT가 있으면 생성 시 소유자 기록 | 자료 종류·조건·메모·파일 메타데이터, 지정 필드 마스킹. 기존 조회 전체에 JWT가 강제되지는 않음 |
| C06/C07/C08 검색 기록 | 생성/결과의 @Valid, 문의·요청·조건 존재 확인 | PREVIEW_READY 기록·스냅샷 저장. 외부 검색·LLM 실행 없음 |
| C10/C11 상태·메모 | @Valid·문의 존재, 상태는 Entity/Enum 전이 정책 | 같은 상태는 200/changed=false, 금지 전이 409, 메모 본문 마스킹 |
| P02/P04/P05 이력·파일 | JWT·소유자/관리자, 파일은 크기·확장자/내용 유형·문의 연결 | 401/403/404, 빈 파일 400·크기 413·유형 415. 익명 문의의 신규 API는 관리자 접근 |
| P06 행동 이벤트 | @Valid, 타입, SUMMARY_VIEWED의 intakeId, 문의·세션 일치, eventId 중복 | 201 최초 / 200 동일 재전송 / 409 다른 내용으로 ID 재사용. 외부 호출 없음 |
| C12~C15/C17~C20 운영 조회·Incident | 현재 개발용 공개 경로, 각 조회·상태 메서드 규칙 | Agent/보안 DB와 마케팅 읽기 모델 구분. 기존 Incident의 없는 ID 등 오류 본문·상태는 실제 실험으로 기록 |
| C16 Prometheus | ADMIN Basic. 사용자 JWT와 구분 | 401 인증 없음, 403 USER Basic, 200 ADMIN Basic. 노출 성공과 Prometheus의 실제 수집 성공은 구분 |
| R01~R06 Reference | 현재 GET 공개, Path·Query를 Service에서 처리 | JPA 조회 또는 고정 체크리스트. 없는 코드·잘못된 필터는 실제 응답 기록 |
| S01/S02 동기·SSE | JWT·@Valid → Dynamic preCheck → Fixed → 입력 길이 → 입력 검사 비용 예약·정산 → 생성 비용 예약 | 동기·SSE 모두 생성 정산 후 출력 검사 예약·정산·Privacy. SSE는 전체 버퍼 보호 후 token, 오류·중단은 미확인 비용 추정 정산 |
| S03/S04 Guardrail 단독 | JWT·@Valid → 비용 Service → 해당 NeMo Client → 보고/추정 정산 | Chat의 Fixed·Dynamic·답변 생성 전체를 실행하지 않으며 검사 비용은 같은 예산·장부 사용. Output 단독 API도 Privacy와 구분 |
| S05 Agent 위험 분석 | 현재 @Valid 제약 없음. 신호·기본값·점수·결정 규칙 | 이력·Metric·필요 시 Incident. Chat Dynamic 세션 누적과 별도 경로 |

CORS·preflight 서버 처리는 Security 체인에 연결되어 있다. **이번 1차는 Postman으로 HTTP 작동·오류를 확인하고, React 연결 시 실제 브라우저 CORS를 확인한다.** 모든 API에 동일 Guard를 추가하거나 브라우저 확인을 이번 학습의 선행 조건으로 삼지 않는다.

실험·코드 매핑을 두 번 진행한 뒤 이 표의 적용 결과를 확인할 구체적인 방법은 [API별 검사·동기/SSE 후속 점검표](DAY5_AFTER_MAPPING_SECURITY_CHECK.md)를 따른다. 기존 요청과 실험 기록을 재사용한다.

**10. Day 6~7 매핑·Postman·DDD 학습 순서**

단계 구분은 [개정 12일 개발 계획](PROJECT_12_DAY_PLAN.md)을 따른다. 실제 실행일과 결과는 아래 수동 기록표에 따로 남긴다.

| 시점 / 회차 | 진행 | 남길 내용 |
|---|---|---|
| Day 6 / 1회차 | 문의·검색·메모·파일·이벤트·조회 등 API별 요청·응답을 실행하고 DB·반환 ID 확인. AI는 준비된 의존 서비스 범위에서 실행 | HTTP·업무 결과·저장 행·필요한 ID, 아직 실행하지 못한 항목 |
| Day 6~7 / 2회차 | 같은 요청을 다시 보며 8절의 Controller·Service·저장/응답과 연결. 해당 API의 정상·실패·차단 분기를 추적 | 실제 처리 메서드, 검사가 실행·종료되는 위치, 재조회·DB·로그 근거 |
| Day 7 / 도메인·정책 연결 | DDD / Event Storming 초안 정리 후 [API별 검사 적용·동기/SSE 비교](DAY5_AFTER_MAPPING_SECURITY_CHECK.md). 기존 요청·기록 재사용 | 적용 검사 표, 두 응답 경로 비교, 확인된 불일치의 수정·재실험 |

순서는 학습용 권장안이며 날짜별로 전부 끝내야 하는 고정 시간표가 아니다. 1회차에 전 코드를 이해할 필요는 없다. 요청 결과를 확인한 뒤 해당 메서드·분기를 읽고, 같은 흐름을 2~3회 반복한다.

**실행 준비와 ID 전달**

- 기본 baseUrl은 `http://localhost:8080`. 서버를 실행한 뒤 Postman에서 현재 주소를 사용한다.
- MySQL 저장 실험은 [root 연결 설정·SQL 확인 순서](MYSQL_CONNECTION_CHECK.md)에 따라 `mysql` 프로필을 사용한다. MySQL DB 계정과 API 로그인 JWT 계정은 서로 다른 용도다.
- [기존 업무 Collection: 요청 17개](C:/myLectureWs/legal-ai-intake-security-server/docs/BACKEND_DRAFT_TODAY.postman_collection.json)과 [추가 API Collection: 요청 15개](C:/myLectureWs/legal-ai-intake-security-server/docs/ADDITIONAL_FIVE_APIS.postman_collection.json)을 Import한다. **요청 개수와 API 개수는 다르다.** 같은 API의 정상·실패 재호출이 포함되어 있어 두 파일만으로 36개 전수 실험이 끝나지 않는다. Reference·운영·AI의 부족한 요청은 이 문서와 [보안 실험 시나리오](C:/myLectureWs/legal-ai-intake-security-server/POSTMAN_SECURITY_SCENARIOS.md)를 기준으로 추가한다.
- 각 Collection은 자체 intakeId·sessionId를 만든다. 다른 Collection의 ID를 무조건 재사용하지 않는다. 추가 Collection은 01 로그인에서 토큰·세션·이벤트 ID를 준비한다.
- 로그인: `POST /api/auth/login`, JSON `{"username":"user","password":"user123"}`. 반환 accessToken을 `Authorization: Bearer {{accessToken}}`으로 전달한다. 관리자 계정은 현재 개발 설정의 admin/admin123, Actuator는 Authorization → Basic Auth에서 사용한다.
- 소유자 실험은 로그인 토큰을 붙여 **새 문의를 생성**한 후 수행한다. JWT 없이 생성한 기존 문의는 신규 파일·검색 이력 API에서 관리자만 접근한다.
- 문의 응답 `id` → intakeId, 검색 요청 응답 `id` → searchRequestId, 파일 업로드 응답 `id` → documentId, Incident 목록 항목 `id` → incidentId. 서로 바꿔 쓰지 않는다.
- GET 요청은 보통 Body가 없고, JSON은 Body → raw → JSON이다. 파일은 Body → form-data의 `file`을 File로 선택한다. multipart Content-Type/boundary는 Postman이 생성하게 둔다. [실험 TXT](C:/myLectureWs/legal-ai-intake-security-server/docs/fixtures/sample-intake.txt)를 사용할 수 있다.
- 조회 시 빈 배열·0이 반환돼도 저장된 데이터가 없다면 정상일 수 있다. 생성 API → 조회 API 순서로 데이터를 준비한다. SSE는 일반 JSON 응답과 달리 meta/token/done/blocked/error 이벤트를 읽는다.
- 기본 H2의 DB 데이터와 메모리의 위험 세션·예산은 서버 수명에 영향을 받는다. MySQL 프로필의 DB 데이터는 재시작 후 유지하고, 위험 세션·예산은 계속 메모리에서 관리한다. 사용 프로필과 재시작 여부를 기록한다. JWT는 별도로 만료 시각·서명 키에 따라 유효성을 확인한다.

**첫 요청 예시 — 문의·검색 기록**

`C03 POST /api/intakes` (소유자 실험에는 JWT 전달):

```json
{
  "sessionId": "study-20261016-01",
  "inquiryPurpose": "상담 전 필요한 자료 확인",
  "caseType": "CIVIL",
  "partyPosition": "원고",
  "coreStatement": "빌려준 돈을 돌려받지 못하고 있습니다.",
  "desiredNextStep": "상담",
  "preferredRegion": "서울",
  "documents": ["입금 내역"]
}
```

`C06 POST /api/intakes/{{intakeId}}/search-requests`의 Body는 `{}`로 시작할 수 있다. `C08 POST /api/intakes/search-requests/{{searchRequestId}}/results`는 아래 테스트 결과를 직접 저장한다. 실제 검색 성공으로 기록하지 않는다.

```json
{"sourceType":"TEST","title":"검색 결과 저장 실험","url":"https://example.com/lawyer","snippet":"조회 연결 확인용 데이터","displayOrder":1}
```

**minimal·AI·이벤트 요청을 구분한다**

`C04 minimal`의 핵심 입력은 coreFact, 저장형 C03은 coreStatement, AI Chat은 message다.

```json
{"caseType":"CIVIL","partyRole":"원고","coreFact":"빌려준 돈을 받지 못함","desiredNextStep":"상담"}
```

`S01/S02`의 정상 경로 실험은 JWT와 준비된 NeMo/LLM을 사용한다. 아래 message는 10자다.

```json
{"message":"민사소송절차를알려줘","sessionId":"study-ai-normal-01"}
```

100자 허용·101자 거절을 확인한다. 비용 길이 차단만 따로 확인할 때는 별도 50자 정책을 사용한다. 각 독립 실험은 새 세션, Dynamic 누적 실험만 같은 세션을 사용한다. 외부 서비스 미준비 fallback은 정상 LLM 성공으로 기록하지 않는다. blocked/fallback/reasonCode/detailCode 및 usageAvailable/costSource를 확인한다.

`P06`은 아래처럼 실제 문의의 sessionId와 intakeId를 연결한다. 같은 eventId·같은 내용 재요청은 200/created=false다.

```json
{"eventId":"study-summary-01","eventType":"SUMMARY_VIEWED","sessionId":"study-20261016-01","intakeId":1}
```

위 intakeId=1은 예시이므로 실제 생성 ID로 바꾼다. VISIT·INTAKE_STARTED는 intakeId 없이도 기록할 수 있다. 조회 API만 호출해서 행동 이벤트가 자동 생성되는 것은 아니다.

**운영 읽기 모델용 데이터 준비**

`S05 POST /api/agent/risk/analyze`에 다음 신호를 전달하면 위험 이력과 조건에 따른 Incident를 확인할 수 있다. 실제 외부 API를 실행하는 요청은 아니다.

```json
{"userInput":"운영 위험 실험","toolName":"postman-test","retryCount":1,"policyViolation":true,"externalApiCall":true,"approvalRequired":true}
```

이후 C17 위험 이력 → C15 위험 요약 → C12 운영 요약 → C18 Incident 목록 → 목록의 실제 ID로 C19 확인/C20 종결을 추적한다. C14 보안 이벤트는 별도 저장 경로의 데이터이므로 Chat 차단이나 S05 실행만으로 반드시 늘어난다고 기대하지 않는다.

**11. Postman 1차·Middle 이해 기록표**

기존 flowchart를 학습 기준으로 유지하고, 같은 흐름을 두 번 실험·매핑한 결과를 이 표에 모은다. 필수 기록은 **실제 HTTP·reason/reasonCode/detailCode, 해당 DB·Metric 근거, 수정·재실행 결과**다. DB는 SQL·ID·조회 결과, Metric은 이름·라벨·전후 값으로 남길 수 있으며 API 성격상 해당하지 않으면 그 이유를 적는다. 실험마다 화면을 전부 캡처할 필요는 없다. 확인한 내용을 API 정리서·코드 매핑·필요한 흐름도 수정에 반영한다.

아래는 **학습·수동 실험용 빈 기록표**다. ‘매핑 확인’은 본인이 입력·주 메서드·저장/응답을 설명한 뒤 표시한다. 정상·실패 요청은 실제 실행 후 `통과 / 실패 / 의존 서비스 미준비 / 해당 없음`과 근거를 기록한다. 체크하지 않은 항목을 자동 검증 결과만으로 채우지 않는다. 실패 실험은 API에 맞게 선택하며 모든 GET에 DTO 실패를 강제로 만들지 않는다.

| API ID | HTTP API | 매핑 확인 | 정상 요청 | 실패·차단 요청 | 근거·수정·재실험 메모 |
|---|---|---|---|---|---|
| C01 | `POST /api/auth/login` | [ ] | 미실행 | 미실행 | — |
| C02 | `POST /api/intakes/classify-preview` | [ ] | 미실행 | 미실행 | — |
| C03 | `POST /api/intakes` | [ ] | 미실행 | 미실행 | — |
| C04 | `POST /api/legal-intake/minimal` | [ ] | 미실행 | 미실행 | — |
| C05 | `GET /api/intakes/{id}` | [ ] | 미실행 | 미실행 | — |
| C06 | `POST /api/intakes/{intakeId}/search-requests` | [ ] | 미실행 | 미실행 | — |
| C07 | `GET /api/intakes/search-requests/{searchRequestId}` | [ ] | 미실행 | 미실행 | — |
| C08 | `POST /api/intakes/search-requests/{searchRequestId}/results` | [ ] | 미실행 | 미실행 | — |
| C09 | `GET /api/intakes` | [ ] | 미실행 | 미실행 | — |
| C10 | `PATCH /api/intakes/{id}/status` | [ ] | 미실행 | 미실행 | — |
| C11 | `POST /api/intakes/{id}/memo` | [ ] | 미실행 | 미실행 | — |
| C12 | `GET /api/admin/dashboard/summary` | [ ] | 미실행 | 미실행 | — |
| C13 | `GET /api/admin/dashboard/marketing-funnel` | [ ] | 미실행 | 미실행 | — |
| C14 | `GET /api/admin/dashboard/security-events` | [ ] | 미실행 | 미실행 | — |
| C15 | `GET /api/agent/risk/summary` | [ ] | 미실행 | 미실행 | — |
| C16 | `GET /actuator/prometheus` | [ ] | 미실행 | 미실행 | — |
| C17 | `GET /api/agent/risk/history` | [ ] | 미실행 | 미실행 | — |
| C18 | `GET /api/incidents` | [ ] | 미실행 | 미실행 | — |
| C19 | `PATCH /api/incidents/{incidentId}/ack` | [ ] | 미실행 | 미실행 | — |
| C20 | `PATCH /api/incidents/{incidentId}/resolve` | [ ] | 미실행 | 미실행 | — |
| R01 | `GET /api/legal/procedures` | [ ] | 미실행 | 미실행 | — |
| R02 | `GET /api/legal/procedures/{code}` | [ ] | 미실행 | 미실행 | — |
| R03 | `GET /api/legal/procedures/{code}/steps` | [ ] | 미실행 | 미실행 | — |
| R04 | `GET /api/legal/forms` | [ ] | 미실행 | 미실행 | — |
| R05 | `GET /api/legal/forms/{formCode}` | [ ] | 미실행 | 미실행 | — |
| R06 | `GET /api/legal/criminal/checklist/supplementary-investigation` | [ ] | 미실행 | 미실행 | — |
| S01 | `POST /api/ai/chat` | [ ] | 미실행 | 미실행 | — |
| S02 | `POST /api/ai/chat/stream` | [ ] | 미실행 | 미실행 | — |
| S03 | `POST /api/legal/guardrail/input` | [ ] | 미실행 | 미실행 | — |
| S04 | `POST /api/legal/guardrail/output` | [ ] | 미실행 | 미실행 | — |
| S05 | `POST /api/agent/risk/analyze` | [ ] | 미실행 | 미실행 | — |
| P01 | `GET /api/me` | [ ] | 미실행 | 미실행 | — |
| P02 | `GET /api/intakes/{intakeId}/search-requests` | [ ] | 미실행 | 미실행 | — |
| P04 | `POST /api/intakes/{intakeId}/documents` | [ ] | 미실행 | 미실행 | — |
| P05 | `GET /api/intakes/{intakeId}/documents/{documentId}/download` | [ ] | 미실행 | 미실행 | — |
| P06 | `POST /api/intake-events` | [ ] | 미실행 | 미실행 | — |

한 API에서 여러 조건을 실험하면 아래 형식으로 행을 추가한다.

| 실행일·API·시나리오 | 설정·세션·대상 ID | 예상 HTTP·업무 결과 | 실제 HTTP·업무 결과 | 재조회·DB·로그·Metric 근거 | 수정·재실험 |
|---|---|---|---|---|---|
| 예: C10 종료 후 재개 | 실제 intakeId, 현재 CLOSED | 409 | 미실행 | 상태 재조회·traceId 기록 예정 | — |

현재 구현 범위에서 정상·실패를 설명할 수 있고, 발견된 오류를 수정·재실험하면 해당 모듈의 1차 확인으로 기록한다. **P03은 구현·로컬 모의 검증 완료**, **실제 외부 제공자 호출·청구 대사·추정액 보정은 후속 범위**다. 브라우저 CORS·핵심 React 화면 연결은 이후 화면 연결 단계의 검증이다.

**12. 오늘 버전의 코드·검증 근거**

| 근거 | 확인 범위 |
|---|---|
| [SecurityConfig.applicationSecurity](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java:66) / [SecurityConfig.actuatorSecurity](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java:42) | 경로별 JWT·ADMIN Basic·개발용 공개 규칙 |
| [WebMvcConfig.corsConfigurationSource](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/WebMvcConfig.java:29) | application·Actuator 공통 CORS source |
| [BackendDraftApiIntegrationTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/api/BackendDraftApiIntegrationTest.java) | preview 300/301자 경계·상태·메모·기본 집계·검색 기록 등의 통합 검증 10개 |
| [AdditionalFiveApiIntegrationTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/api/AdditionalFiveApiIntegrationTest.java) | 신규 5개 API·인증/소유자·파일·이벤트 통합 검증 21개 |
| [AiSecurityIntegrationTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/api/AiSecurityIntegrationTest.java) | 동기/SSE·단독 검사 예산·출력 차단 비용 유지·중단 중복 콜백·C16·Privacy 등 통합 검증 44개 |
| [GuardrailCostTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/cost/GuardrailCostTest.java) | 검사 불허·오류의 추정 정산·검사 단가·보고 0·로컬 정책·중복 정산 7개 |
| [GuardrailUsageClientTest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/test/java/com/ohgiraffers/handlermethod/guardrail/GuardrailUsageClientTest.java) | usage 누락·부분·음수·소수·초과 값·유효 0·로컬 무호출 등 10개 |
| 나머지 단위·Client·기본 테스트 | Fixed 5, Dynamic 5, Cost session 2, Cost usage 5, LLM Client 14, 기본 1 — 전체 합계 124개 |

과거 구현 경과는 [6개 보완 결과](C:/myLectureWs/legal-ai-intake-security-server/docs/DAY4_SIX_IMPLEMENTED_2026-10-16.md), [Privacy·usage 보완](C:/myLectureWs/legal-ai-intake-security-server/docs/DAY4_PRIVACY_USAGE_2026-10-16.md), [추가 5개 API 결과](C:/myLectureWs/legal-ai-intake-security-server/docs/DAY4_FIVE_APIS_2026-10-16.md)를 참고한다. 과거 문서의 테스트 수와 잔여 개수는 해당 시점 기록이며 오늘의 합계는 이 문서 앞부분을 기준으로 한다.
