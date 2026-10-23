# Postman Checklist — Numbered APIs

**Reference catalog — not a mandatory manual run.** 기본 수동 실행은 [매핑 우선·최소 실행 색인](POSTMAN_API_CHECKLIST_INDEX.md)의 4요청, 실제 AI 연결 확인 시 추가 4요청으로 시작한다. 아래 체크박스는 기능·조건을 찾아보는 항목이며 자동 테스트로 검증된 조건을 Postman으로 전부 반복하지 않는다. 현재 매핑/변경 기능과 coverage gap에 필요한 항목만 선택한다.

기준: **2026-10-17**. Code identifiers, features and checks use English. This file covers **36 implemented API entries** plus **P03 (planned)**. It is a checklist, not a record of executed requests.

Start with [the scope and shared-check guide](C:/myLectureWs/legal-ai-intake-security-server/docs/POSTMAN_API_CHECKLIST_INDEX.md). Number-free routes are in [the separate auxiliary checklist](C:/myLectureWs/legal-ai-intake-security-server/docs/POSTMAN_AUXILIARY_API_CHECKLIST.md).

The existing HTTP flows contain **145 representative branches** for the 36 implemented APIs, plus **4 design branches** for P03. The checkboxes below are functional assertions and may be satisfied by the same request; their count is not an additional request total. Existing branch numbers remain in the linked per-API flow.

For every run, attach the shared evidence record: API ID + case, request/response, code branch and relevant before/after state. All checkboxes begin unchecked. “500 expected” below means a current unconverted exception path that needs runtime confirmation, not a desired error contract.

## Authentication

### C01 — Login

- API: `POST /api/auth/login`
- Code: [AuthController.login()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/AuthController.java:26)
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use the configured development account; save userToken/adminToken separately.
- Existing flow: [C01.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C01.mmd) — **3 representative branches**.

- [ ] Valid credentials → HTTP 200; save accessToken and verify username/role.
- [ ] Blank username/password → HTTP 400 with INVALID_REQUEST and fieldErrors; wrong credentials → HTTP 401.
- [ ] Use the returned token in P01; a successful login is not evidence that every later resource is authorized.

### P01 — Current user

- API: `GET /api/me`
- Code: [CurrentUserController.me()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/security/CurrentUserController.java:9)
- Auth: JWT
- Cost / tokens: No token accounting
- Setup: Use the C01 JWT.
- Existing flow: [P01.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/P01.mmd) — **3 representative branches**.

- [ ] HTTP 200 returns username/role from the authenticated principal; compare USER and ADMIN tokens.
- [ ] Missing/invalid/expired token → 401.
- [ ] No DB write, generation call or token-cost update.

## Intake and consultation

### C02 — Classification preview

- API: `POST /api/intakes/classify-preview`
- Code: [LegalIntakeClassificationController.preview()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeClassificationController.java:21) → `LegalIntakeRecordService.classifyPreview()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Send coreStatement; caseType is optional.
- Existing flow: [C02.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C02.mmd) — **3 representative branches**.

- [ ] Valid coreStatement → HTTP 200 classification/guidance fields.
- [ ] Blank or over-300-character coreStatement → HTTP 400; use the DTO's actual limits.
- [ ] Compare DB counts before/after: no new intake, search request or LLM-cost entry.

### C04 — Minimal rule-based guidance

- API: `POST /api/legal-intake/minimal`
- Code: [MinimalLegalIntakeController.analyze()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/legal/MinimalLegalIntakeController.java:19)
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use MinimalLegalIntakeRequest from the existing collection/spec.
- Existing flow: [C04.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C04.mmd) — **3 representative branches**.

- [ ] Valid request → HTTP 200 rule-based guidance; check type/material/next-step fields.
- [ ] Invalid DTO input → HTTP 400 according to the existing branch matrix.
- [ ] Verify no intake persistence, search request creation, remote LLM call or ai.cost.* change.

### C03 — Create intake

- API: `POST /api/intakes`
- Code: [LegalIntakeRecordController.create()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:32) → `LegalIntakeRecordService.create()`
- Auth: Public; JWT optional and recommended for owner fixtures
- Cost / tokens: No token accounting
- Setup: Attach userToken and a unique sessionId when preparing owner-scoped P02/P04/P05 fixtures.
- Existing flow: [C03.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C03.mmd) — **3 representative branches**.

- [ ] Valid request → HTTP 201; save id as intakeId and lawyerSearchCondition.id as conditionId.
- [ ] Map the transaction: legal_intake + selected material records + lawyer_search_condition; read back with C05.
- [ ] Confirm no ai_search_request is created automatically; C06 is a separate call.
- [ ] Invalid required fields/lengths → HTTP 400.
- [ ] Also create an anonymous intake without JWT: HTTP 201 is allowed; later P02/P04/P05 access requires ADMIN for that intake.

### C05 — Intake detail

- API: `GET /api/intakes/{id}`
- Code: [LegalIntakeRecordController.findById()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:43) → `LegalIntakeRecordService.findById()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use intakeId from C03; prepare one memo and one uploaded file for enriched readback.
- Existing flow: [C05.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C05.mmd) — **3 representative branches**.

- [ ] HTTP 200 returns the same intake, selected materials, search condition and current status.
- [ ] After C11/P04, verify memo and uploadedDocuments appear in the correct fields.
- [ ] Unknown numeric id → HTTP 404; this route is currently public, so do not record a missing JWT as an expected 401.

### C09 — Recent intakes

- API: `GET /api/intakes`
- Code: [LegalIntakeRecordController.findRecent()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:38) → `LegalIntakeRecordService.findRecent()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Create multiple distinct C03 fixtures.
- Existing flow: [C09.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C09.mmd) — **2 representative branches**.

- [ ] HTTP 200 returns recent-first results, at most 50 records.
- [ ] Empty database → HTTP 200 with []; compare IDs with C03/C05.
- [ ] Read-only: no intake creation or token-ledger increment.

### C10 — Intake status transition

- API: `PATCH /api/intakes/{id}/status`
- Code: [LegalIntakeStatusController.update()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeStatusController.java:22) → `LegalIntakeRecordService.updateStatus()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use separate intakes for transition, no-op and CLOSED cases.
- Existing flow: [C10.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C10.mmd) — **5 representative branches**.

- [ ] Allowed transition → HTTP 200 with correct previousStatus/status and changed=true; verify C05.
- [ ] Same-state request → HTTP 200 with changed=false.
- [ ] CLOSED → another state → HTTP 409; also map IntakeStatus.canChangeTo() before adding other forbidden-transition assertions.
- [ ] Missing/unsupported status → HTTP 400; unknown intake → HTTP 404.

### C11 — Create consultation memo

- API: `POST /api/intakes/{id}/memo`
- Code: [ConsultationMemoController.create()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/ConsultationMemoController.java:24) → `LegalIntakeRecordService.createMemo()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use an intakeId and content containing controlled sample PII; valid followUpItems only.
- Existing flow: [C11.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C11.mmd) — **3 representative branches**.

- [ ] HTTP 201; save memo id; verify intakeId/contentMasked/followUpItems/createdAt.
- [ ] Check masking in the response and C05 readback; inspect the service's actual persistence/protection path.
- [ ] Blank/too-long content or unsupported followUpItems → HTTP 400; unknown intake → HTTP 404.

## Search-request records

### C06 — Create search-request record

- API: `POST /api/intakes/{intakeId}/search-requests`
- Code: [LegalIntakeRecordController.createSearchRequest()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:48) → `LegalIntakeRecordService.createSearchRequest()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use an existing intakeId; send a JSON object, including {} for defaults.
- Existing flow: [C06.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C06.mmd) — **3 representative branches**.

- [ ] HTTP 201; save searchRequestId; status=PREVIEW_READY and results initially empty.
- [ ] Verify association with the intake/search condition and read back using C07/P02.
- [ ] Unknown intake → HTTP 404; missing body or over-300-character searchPurpose → HTTP 400.
- [ ] Confirm this creates a preparation record, not an external search execution.

### C07 — Search-request detail

- API: `GET /api/intakes/search-requests/{searchRequestId}`
- Code: [LegalIntakeRecordController.findSearchRequest()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:55) → `LegalIntakeRecordService.findSearchRequest()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use searchRequestId from C06; add fixtures with C08.
- Existing flow: [C07.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C07.mmd) — **3 representative branches**.

- [ ] HTTP 200 returns the request/condition and result snapshots in configured order.
- [ ] Compare results before and after C08; verify IDs and relationship to the same request.
- [ ] Unknown searchRequestId → HTTP 404; GET must not collect new external results or add token usage.

### C08 — Append result snapshot

- API: `POST /api/intakes/search-requests/{searchRequestId}/results`
- Code: [LegalIntakeRecordController.addSearchResult()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeRecordController.java:60) → `LegalIntakeRecordService.addSearchResult()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use an existing searchRequestId; provide sourceType, title, url and optional summary/order.
- Existing flow: [C08.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C08.mmd) — **3 representative branches**.

- [ ] HTTP 201 returns results containing the submitted snapshot; read back with C07/P02.
- [ ] Verify association and ordering; PREVIEW_READY remains the preparation status.
- [ ] Missing required snapshot fields/invalid lengths → HTTP 400; unknown request → HTTP 404.
- [ ] Confirm supplied snapshots are stored without performing an external crawl/search.

### P02 — Intake search history

- API: `GET /api/intakes/{intakeId}/search-requests`
- Code: [IntakeSearchHistoryController.history()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeSearchHistoryController.java:23) → `LegalIntakeRecordService.findSearchRequests()`
- Auth: JWT + owner/ADMIN
- Cost / tokens: No token accounting
- Setup: JWT-owned intake and C06/C08 records; also prepare another user's/anonymous intake.
- Existing flow: [P02.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/P02.mmd) — **5 representative branches**.

- [ ] Owner or ADMIN → HTTP 200 matching search-request history; existing accessible intake with no requests → [].
- [ ] Missing/invalid JWT → 401; non-owner → 403; authenticated unknown intake → 404.
- [ ] Anonymous intake is ADMIN-only; verify the ownership check rather than relying only on authentication.
- [ ] GET must not create another search request, execute external search or increment a token ledger.

## Files and behavior events

### P04 — Upload actual file

- API: `POST /api/intakes/{intakeId}/documents`
- Code: [IntakeDocumentController.upload()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeDocumentController.java:23) → `IntakeDocumentService.upload()`
- Auth: JWT + owner/ADMIN
- Cost / tokens: No token accounting
- Setup: JWT-owned intake; multipart/form-data field file; use controlled PDF/PNG/JPEG/UTF-8 TXT fixtures.
- Existing flow: [P04.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/P04.mmd) — **7 representative branches**.

- [ ] Allowed upload up to configured 5 MiB default → HTTP 201; save documentId/downloadUrl; verify C05 metadata and P05 bytes.
- [ ] Missing/invalid JWT → 401; non-owner → 403; unknown intake → 404.
- [ ] Missing/empty file → 400; oversized file → 413; unsupported content or MIME/content mismatch → 415.
- [ ] Confirm BLOB/metadata belongs to the intake; a C03 selected material ID is not an uploaded-file ID.

### P05 — Download uploaded file

- API: `GET /api/intakes/{intakeId}/documents/{documentId}/download`
- Code: [IntakeDocumentController.download()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeDocumentController.java:31) → `IntakeDocumentService.download()`
- Auth: JWT + owner/ADMIN
- Cost / tokens: No token accounting
- Setup: Use an actual P04 documentId associated with the accessible intake.
- Existing flow: [P05.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/P05.mmd) — **4 representative branches**.

- [ ] HTTP 200 byte content matches upload; verify Content-Type and attachment Content-Disposition.
- [ ] Missing/invalid JWT → 401; non-owner → 403.
- [ ] Missing document, selected-material-only ID, or mismatched accessible intake/document → 404.
- [ ] Check the download-request record; no new upload or AI token usage.

### P06 — Record behavior event

- API: `POST /api/intake-events`
- Code: [IntakeEventController.record()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/IntakeEventController.java:17) → `IntakeEventService.record()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Unique eventId/sessionId; use an existing intake for intake-linked events.
- Existing flow: [P06.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/P06.mmd) — **5 representative branches**.

- [ ] New valid event → HTTP 201, created=true; save receivedAt; check C13.
- [ ] Same eventId and same payload → HTTP 200, created=false, original receivedAt retained.
- [ ] Same eventId with different valid payload → 409 and no duplicate aggregation.
- [ ] Invalid event type/required fields, missing intakeId for SUMMARY_VIEWED, or intake/session mismatch → 400; unknown linked intake → 404.
- [ ] These are reported behavior events, not measured browser abandonment; no LLM/cost reservation.

## AI, Guardrails and risk analysis

### S01 — Synchronous AI Chat

- API: `POST /api/ai/chat`
- Code: [AiController.chat()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AiController.java:27) → `AiService.chat()`
- Auth: JWT
- Cost / tokens: ai.cost.* USD: input_guardrail + answer + output_guardrail
- Setup: JWT; fresh sessionId; message within the current cost length limit; controllable NeMo/LLM adapters for branch tests.
- Existing flow: [S01.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/S01.mmd) — **11 representative branches**.

- [ ] Allowed pipeline → HTTP 200, blocked=false/fallback=false; verify masked answer and generation-stage usageAvailable/costSource.
- [ ] Trace Dynamic preCheck → Fixed → length check → input reservation/check/settlement → ANSWER reservation/call/settlement → output reservation/check/settlement → Privacy.
- [ ] Check local security/length blocks and distinct budget blocks; later stage denial must retain earlier settled costs.
- [ ] Reported usage, missing usage and provider failure: compare REPORTED/ESTIMATED and reserved/actual/estimated stage deltas with C16.
- [ ] Output policy block must not expose the original generated text; use isolated adapters for missing usage/failure cases, not invented request flags.

### S02 — Buffered SSE AI Chat

- API: `POST /api/ai/chat/stream`
- Code: [AiController.chatStream()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AiController.java:32) → `AiStreamingService.stream()`
- Auth: JWT
- Cost / tokens: ai.cost.* USD: input_guardrail + answer + output_guardrail
- Setup: Same preparation as S01; use Postman's SSE display or raw stream capture.
- Existing flow: [S02.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/S02.mmd) — **11 representative branches**.

- [ ] meta appears before token/done; original generation chunks are buffered until output checks and Privacy masking finish.
- [ ] Normal completion exposes generation-stage usageAvailable/costSource in done; usage fields are null when unavailable.
- [ ] blocked/error terminates the response appropriately; output-blocked original text must not appear in token events.
- [ ] Generation cost settles before output inspection; output block/budget failure retains already generated cost.
- [ ] Timeout/disconnect/provider error settles unknown dispatched cost once; pre-dispatch cancellation releases only the unstarted reservation. Forced callback races/late completion require an automated fixture.

### S03 — Standalone input Guardrail

- API: `POST /api/legal/guardrail/input`
- Code: [LegalGuardrailController.checkInput()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalGuardrailController.java:25) → `CostedGuardrailService.checkInput()`
- Auth: JWT
- Cost / tokens: ai.cost.* USD: input_guardrail
- Setup: JWT; message and optional sessionId; controllable NeMo input adapter for response/usage variants.
- Existing flow: [S03.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/S03.mmd) — **6 representative branches**.

- [ ] PASSED/MODIFIED → HTTP 200 allowed=true; blocked policy result → HTTP 200 allowed=false; inspect status/content/rail.
- [ ] Blank/missing message → 400; missing/invalid JWT → 401.
- [ ] Budget denial → HTTP 200 with status=BLOCKED, allowed=false, reasonCode=COST_LIMIT_BLOCKED and the appropriate detailCode; no remote call.
- [ ] Reported valid usage, including zero, → REPORTED; missing/invalid usage or remote failure → ESTIMATED; verify input_guardrail metrics.
- [ ] Remote technical exceptions are not converted to Chat fallback: current 500 expected, confirm runtime. No ANSWER or output-Guardrail execution.

### S04 — Standalone output Guardrail

- API: `POST /api/legal/guardrail/output`
- Code: [LegalOutputGuardrailController.checkOutput()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalOutputGuardrailController.java:25) → `CostedGuardrailService.checkOutput()`
- Auth: JWT
- Cost / tokens: ai.cost.* USD: output_guardrail
- Setup: JWT; response and optional sessionId; controllable output adapter.
- Existing flow: [S04.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/S04.mmd) — **7 representative branches**.

- [ ] Allowed/modified remote result and blocked policy result use HTTP 200; check allowed/status/content/rail.
- [ ] Blank/missing response → DTO 400; test a valid-body local policy rejection separately (e.g. over the local output limit) → known-zero result, no reservation/remote call.
- [ ] Budget denial → HTTP 200 BLOCKED/allowed=false with COST_LIMIT_BLOCKED/detailCode.
- [ ] Reported/estimated usage affects output_guardrail stage and shared budgets; remote technical exception remains an exception response (500 expected).
- [ ] Do not assert that this standalone path applies the full Chat Dynamic/Fixed chain or separate Privacy masking.

### S05 — Synchronous Agent risk analysis

- API: `POST /api/agent/risk/analyze`
- Code: [AgentRiskDecisionController.analyze()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskDecisionController.java:21) → `AgentRiskDecisionService.analyze()`
- Auth: Public in current applicationSecurity
- Cost / tokens: agent.cost.tokens: simulated; no USD deduction
- Setup: Use AgentRiskAnalyzeRequest; choose explicit flags rather than ambiguous keywords.
- Existing flow: [S05.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/S05.mmd) — **6 representative branches**.

- [ ] Safe request → SAFE/LOW/ALLOW with historyId; {} uses defaults; missing body/malformed types → 400.
- [ ] policyViolation=true + externalApiCall=true + retryCount=2 → HIGH_RISK_AGENT_BEHAVIOR/HIGH/BLOCK_AND_ESCALATE; verify C17/C18/C12.
- [ ] approvalRequired + retry → APPROVAL_BYPASS_RISK; toolError + retry → UNSTABLE_TOOL_LOOP; check rule ordering.
- [ ] Verify AgentRiskHistory and AgentRunHistory persistence and conditional Incident creation.
- [ ] Compare agent.cost.tokens prompt/completion deltas with explicit values (default 120/80); no remote LLM call or ai.cost.* USD deduction.

## Reference data

### R01 — Procedure list

- API: `GET /api/legal/procedures`
- Code: [LegalProcedureController.procedures()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:39) → `LegalProcedureService.findProcedures()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Load the configured Reference seed data; use a supported domain enum from code.
- Existing flow: [R01.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/R01.mmd) — **4 representative branches**.

- [ ] HTTP 200 procedure list; domain filter returns matching procedures; save procedureCode.
- [ ] No rows/no match → []; unsupported domain currently has an unconverted enum exception (500 expected, runtime confirmation).
- [ ] Public GET/JPA read; no LLM or token-ledger increment.

### R02 — Procedure detail

- API: `GET /api/legal/procedures/{code}`
- Code: [LegalProcedureController.procedureDetail()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:57) → `LegalProcedureService.findProcedureDetail()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use a procedureCode returned by R01.
- Existing flow: [R02.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/R02.mmd) — **2 representative branches**.

- [ ] HTTP 200 basic details, steps, forms and knowledgeGraphEdges; match relationships to the selected procedure.
- [ ] Unknown procedureCode currently raises an unconverted exception (500 expected, runtime confirmation).
- [ ] Read-only Reference data; no remote generation.

### R03 — Procedure steps

- API: `GET /api/legal/procedures/{code}/steps`
- Code: [LegalProcedureController.procedureSteps()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:73) → `LegalProcedureService.findProcedureSteps()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use procedureCode; an existing procedure with no steps requires a controlled fixture.
- Existing flow: [R03.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/R03.mmd) — **3 representative branches**.

- [ ] HTTP 200 steps in stepOrder; existing procedure with no steps → [].
- [ ] Unknown procedureCode currently raises an unconverted exception (500 expected, runtime confirmation).
- [ ] Compare steps with R02 and the procedure-step relationship.

### R04 — Form list

- API: `GET /api/legal/forms`
- Code: [LegalProcedureController.forms()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:85) → `LegalProcedureService.findForms()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use a procedure from R01 when testing the procedure query parameter.
- Existing flow: [R04.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/R04.mmd) — **4 representative branches**.

- [ ] HTTP 200 all forms or forms for the selected procedure.
- [ ] No forms for an existing procedure → []; an unknown procedure currently raises an unconverted exception (500 expected).
- [ ] Save formCode and compare with R05; no new records or token usage.

### R05 — Form detail

- API: `GET /api/legal/forms/{formCode}`
- Code: [LegalProcedureController.form()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:99) → `LegalProcedureService.findForm()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use formCode from R04.
- Existing flow: [R05.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/R05.mmd) — **2 representative branches**.

- [ ] HTTP 200 with procedureCode/formCode/title/fileType/usageNote; verify matching Reference data.
- [ ] Unknown formCode currently raises an unconverted exception (500 expected, runtime confirmation).
- [ ] Do not treat metadata lookup as downloading an uploaded P05 file.

### R06 — Fixed supplementary-investigation checklist

- API: `GET /api/legal/criminal/checklist/supplementary-investigation`
- Code: [LegalProcedureController.supplementaryInvestigationChecklist()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalProcedureController.java:110) → `LegalProcedureService.supplementaryInvestigationChecklist()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: No body or database fixture required.
- Existing flow: [R06.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/R06.mmd) — **1 representative branches**.

- [ ] HTTP 200 fixed prototype/checklist fields; compare with LegalProcedureService.supplementaryInvestigationChecklist().
- [ ] Public GET; no DB lookup or remote LLM/cost reservation in this handler.

## Operations, incidents and metrics

### C12 — Agent operations dashboard

- API: `GET /api/admin/dashboard/summary`
- Code: [AdminDashboardController.summary()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java:22) → `AdminDashboardService.summary()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Capture baseline; run S05 or an Agent Job and create/update an Incident.
- Existing flow: [C12.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C12.mmd) — **3 representative branches**.

- [ ] HTTP 200 exposes agentRunCount/riskHistoryCount/securityEventCount/openIncidentCount/riskSummary.
- [ ] Re-read after S05 and C19/C20; reconcile changes with the corresponding persisted records.
- [ ] With no source data, zeros/empty maps are valid; this is distinct from the C13 marketing model.
- [ ] Current route is public despite /admin in the path; record this actual boundary, not a planned ADMIN policy.

### C13 — Marketing funnel read model

- API: `GET /api/admin/dashboard/marketing-funnel`
- Code: [MarketingDashboardController.funnel()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/MarketingDashboardController.java:18) → `MarketingDashboardService.funnel()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Prepare C03/C06/C10 and P06 fixtures with distinct eventId values.
- Existing flow: [C13.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C13.mmd) — **3 representative branches**.

- [ ] HTTP 200 returns intake/search/current-status counts and eventCounts.
- [ ] No events → zero event counts and dropOffMeasured=false; retain the REPORTED_EVENT interpretation.
- [ ] Retry the same P06 eventId: event totals must not increase twice.
- [ ] Trace the data source to MarketingDashboardService; do not substitute Agent security totals from C12.

### C14 — Recent security events

- API: `GET /api/admin/dashboard/security-events`
- Code: [AdminDashboardController.securityEvents()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AdminDashboardController.java:27) → `AdminDashboardService.recentSecurityEvents()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Prepare an application authentication/authorization failure using an existing protected route.
- Existing flow: [C14.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C14.mmd) — **2 representative branches**.

- [ ] HTTP 200 returns recent security events, at most 50; [] is valid with no events.
- [ ] Match event category/path/timestamp/trace where present to the triggering request and SecurityMetricRecorder.
- [ ] Do not assume Agent practice simulations write the same security-event history.

### C15 — Agent risk summary

- API: `GET /api/agent/risk/summary`
- Code: [AgentRiskHistoryController.summary()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java:29) → `AgentRiskHistoryQueryService.summarize()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Create known S05 SAFE and high-risk fixtures; note baseline counts.
- Existing flow: [C15.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C15.mmd) — **3 representative branches**.

- [ ] HTTP 200 returns totals and riskLevel/decision/recommendedAction distributions.
- [ ] Reconcile the delta with S05/Job AgentRiskHistory records; with no data, zeros/empty maps are valid.
- [ ] GET must not run analyze() again or increment agent.cost.tokens/ai.cost.*.

### C16 — Prometheus metrics

- API: `GET /actuator/prometheus`
- Code: Spring Boot Actuator → SecurityConfig.actuatorSecurity()
- Auth: ADMIN Basic
- Cost / tokens: Read-only observation of both metric families
- Setup: Use ADMIN Basic credentials, not the application Bearer token. Capture values before/after the target request.
- Existing flow: [C16.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C16.mmd) — **4 representative branches**.

- [ ] ADMIN Basic → HTTP 200 Prometheus text; no credentials → 401; USER Basic → 403.
- [ ] JWT-only access is not a substitute for ADMIN Basic; check the actual Actuator chain.
- [ ] Check input_guardrail/answer/output_guardrail stage series and the overall reserved/reported/estimated/accounted values.
- [ ] After all calls settle: reserved cost returns to baseline; stage accounted costs sum to the total; estimates still consume budget.
- [ ] Keep agent.cost.tokens (simulated tokens) separate from ai.cost.*.usd; metric names are lazily registered, so trigger the relevant path first.

### C17 — Agent risk history

- API: `GET /api/agent/risk/history`
- Code: [AgentRiskHistoryController.history()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskHistoryController.java:23) → `AgentRiskHistoryQueryService.findRecent()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use S05/Job records with different decisions and risk levels.
- Existing flow: [C17.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C17.mmd) — **5 representative branches**.

- [ ] Unfiltered HTTP 200 list is recent-first, at most 50.
- [ ] riskLevel filter returns the selected level; decision works when riskLevel is absent.
- [ ] When both filters are supplied, riskLevel takes precedence; no match → [].
- [ ] GET does not create another analysis record or token increment.

### C18 — Recent Incidents

- API: `GET /api/incidents`
- Code: [RiskIncidentController.findRecent()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:23) → `RiskIncidentService.findRecent()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: S05 with policyViolation=true, externalApiCall=true, retryCount=2 creates a suitable high-risk fixture.
- Existing flow: [C18.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C18.mmd) — **2 representative branches**.

- [ ] HTTP 200 returns recent Incidents; save incidentId; [] is a valid empty result.
- [ ] Check riskHistoryId/traceId/decision/riskLevel linkage to the triggering analysis where exposed.
- [ ] Read back after C19/C20; do not assume every SAFE or low-risk analysis creates an Incident.

### C19 — Acknowledge Incident

- API: `PATCH /api/incidents/{incidentId}/ack`
- Code: [RiskIncidentController.acknowledge()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:28) → `RiskIncidentService.acknowledge()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use incidentId from C18; prepare OPEN, ACKNOWLEDGED and RESOLVED fixtures.
- Existing flow: [C19.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C19.mmd) — **4 representative branches**.

- [ ] Existing Incident → HTTP 200, status=ACKNOWLEDGED; read back with C18.
- [ ] Current implementation also acknowledges already ACKNOWLEDGED or RESOLVED records; it does not reuse the C10 CLOSED-transition rule.
- [ ] Unknown numeric id: current unconverted exception → 500 expected, verify runtime; nonnumeric id → 400.
- [ ] Check updatedAt and C12 openIncidentCount against actual state changes.

### C20 — Resolve Incident

- API: `PATCH /api/incidents/{incidentId}/resolve`
- Code: [RiskIncidentController.resolve()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/RiskIncidentController.java:33) → `RiskIncidentService.resolve()`
- Auth: Public in current applicationSecurity
- Cost / tokens: No token accounting
- Setup: Use OPEN/ACKNOWLEDGED/RESOLVED fixtures from C18.
- Existing flow: [C20.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/C20.mmd) — **4 representative branches**.

- [ ] HTTP 200 with status=RESOLVED; verify C18 and C12 open count.
- [ ] Repeated resolve remains RESOLVED; do not assert a C10-style 409.
- [ ] Unknown numeric id: current unconverted exception → 500 expected, verify runtime; nonnumeric id → 400.

## Planned API — not executable

### P03 — Execute external search — planned

- API: **Proposed only:** `POST /api/intakes/search-requests/{searchRequestId}/execute`
- Code: No implemented handler
- Auth: Design pending
- Cost / tokens: Future search-provider billing/usage integration
- Setup: No implemented handler; use the design flow only.
- Existing flow: [P03.mmd](C:/myLectureWs/legal-ai-intake-security-server/docs/postman-http/P03.mmd) — **4 design branches**.

- [ ] Review proposed authentication, allowed sources, request state, duplicate execution and provider-failure rules before implementation.
- [ ] Define provider billing/usage accounting and how it joins the current ledger; do not claim C06/C08 already execute or charge search.
- [ ] Keep the four existing design branches separate from current executable Postman cases.
