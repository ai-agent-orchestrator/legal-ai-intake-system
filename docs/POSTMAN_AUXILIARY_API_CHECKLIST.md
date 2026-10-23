# Postman Checklist — Auxiliary / Unnumbered Routes

**Reference catalog — excluded from the default manual run.** [매핑 우선·최소 실행 색인](POSTMAN_API_CHECKLIST_INDEX.md)을 먼저 본다. 번호 없는 30경로 전체를 실행할 필요는 없다. 해당 모듈을 매핑·수정하거나 검증되지 않은 동작을 완료 처리할 때 필요한 대표 경로/조건만 자동 테스트 또는 HTTP로 확인한다.

기준: **2026-10-17**. This file separates **30 existing Controller routes without C/R/S/P IDs**, **4 additional configured Actuator request patterns**, and **2 external NeMo adapter contracts**. C16 Prometheus remains in the numbered checklist and is not counted again here.

Use [the shared-check guide](C:/myLectureWs/legal-ai-intake-security-server/docs/POSTMAN_API_CHECKLIST_INDEX.md) first. These route names are existing handlers, not newly assigned API IDs. Request/capture names can use `Controller.method.case`; this does not renumber the public API catalog. All boxes are planned checks, not Postman pass results.

The application routes below are currently public **except legacy `POST /api/legal/intake/analyze`, which requires JWT**. Invalid credentials/headers can still be rejected by a filter; “public” means a valid request may omit authentication. Actuator authentication is described separately.

## Common fixtures and metric rules

`AgentPracticeRequest` is optional on practice/risk-pattern/policy-check routes. To check explicit simulated-token overrides:

```json
{
  "userInput": "safe demo",
  "toolName": "search",
  "planSteps": 3,
  "retryCount": 2,
  "promptTokens": 11,
  "completionTokens": 7
}
```

For token-recording practice/pattern routes this produces `totalTokens=18` and prompt/completion metric deltas of +11/+7. `userInput` does not turn a fixed practice scenario into a real tool execution. The mock policy-check route reads `userInput` but does not record these token values.

| Family | Default / zero behavior to map once, then spot-check after changes |
|---|---|
| `AgentPracticeService` | `planSteps<=0` uses a default; `retryCount=0` is accepted but negative uses a default; token values `<=0` use scenario defaults. |
| `AgentRiskPatternService` | `planSteps<=0`, `retryCount<=0` and token values `<=0` use scenario defaults. |
| `AgentRiskDecisionService` (S05 / Job) | Negative/null retry/token values use defaults; **zero tokens are accepted**. Request fields are simulated token input, not provider-reported usage. |
| Actual Guardrail/ANSWER settlement | `usageAvailable=true` with valid zero counts means `REPORTED`; unavailable usage means `ESTIMATED`. This is a different contract from the simulation defaults above. |

- [ ] Capture `agent.cost.tokens` before/after with `type=prompt` and `type=completion`; compare deltas, not the lifetime total.
- [ ] Check that Agent simulations do not change `ai.cost.*.usd`; HTTP request metrics can still change.
- [ ] Record Metric names/tags as Micrometer names; Prometheus names can differ. Use ADMIN Basic on `/actuator/metrics/{metricName}`.
- [ ] For a metric-only practice route, do not use C17/C18 as proof of persistence; S05/Job is the path that writes Agent histories and conditional Incidents.

For S05 or Job analysis, use the separate [AgentRiskAnalyzeRequest](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/dto/AgentRiskAnalyzeRequest.java) contract:

```json
{
  "userInput": "safe demo",
  "toolName": "search",
  "retryCount": 0,
  "promptTokens": 11,
  "completionTokens": 7,
  "policyViolation": false,
  "toolError": false,
  "externalApiCall": false,
  "approvalRequired": false,
  "dbWrite": false
}
```

This safe fixture should add 11 prompt and 7 completion simulated tokens when analysis runs. For a high-risk/Incident fixture, set `policyViolation=true`, `externalApiCall=true` and `retryCount=2`, keeping other risk flags false.

## Controller routes — 30 existing APIs

### Agent practice

#### AgentPracticeController.run — Simulated run

- API: `POST /api/agent/practice/run`
- Code: [AgentPracticeController.run()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentPracticeController.java:21) → `AgentPracticeService.run()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; omit the body or use {} for defaults.

- [ ] HTTP 200; mode=run; decision=ALLOWED; traceId present. These are business fields, not HTTP status codes.
- [ ] Defaults: toolName=search, planSteps=3, retryCount=0, totalTokens=200.
- [ ] Verify before/after metrics: agent.tool.calls outcome=success; agent.plan.steps; agent.retry.count; agent.cost.tokens type=prompt +120, type=completion +80.
- [ ] For explicit override coverage, send positive promptTokens/completionTokens and compare totalTokens and metric deltas. No real LLM/tool action or shared USD-budget deduction.

#### AgentPracticeController.toolError — Simulated tool-error

- API: `POST /api/agent/practice/tool-error`
- Code: [AgentPracticeController.toolError()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentPracticeController.java:26) → `AgentPracticeService.toolError()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; omit the body or use {} for defaults.

- [ ] HTTP 200; mode=tool-error; decision=FAILED; traceId present. These are business fields, not HTTP status codes.
- [ ] Defaults: toolName=search, planSteps=2, retryCount=1, totalTokens=100.
- [ ] Verify before/after metrics: agent.tool.calls outcome=error; agent.tool.errors error=simulated_tool_failure; agent.cost.tokens type=prompt +80, type=completion +20.
- [ ] For explicit override coverage, send positive promptTokens/completionTokens and compare totalTokens and metric deltas. No real LLM/tool action or shared USD-budget deduction.

#### AgentPracticeController.retry — Simulated retry

- API: `POST /api/agent/practice/retry`
- Code: [AgentPracticeController.retry()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentPracticeController.java:31) → `AgentPracticeService.retry()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; omit the body or use {} for defaults.

- [ ] HTTP 200; mode=retry; decision=RETRIED; traceId present. These are business fields, not HTTP status codes.
- [ ] Defaults: toolName=search, planSteps=5, retryCount=3, totalTokens=230.
- [ ] Verify before/after metrics: agent.tool.calls outcome=retried; agent.retry.count; agent.cost.tokens type=prompt +140, type=completion +90.
- [ ] For explicit override coverage, send positive promptTokens/completionTokens and compare totalTokens and metric deltas. No real LLM/tool action or shared USD-budget deduction.

#### AgentPracticeController.approval — Simulated approval

- API: `POST /api/agent/practice/approval`
- Code: [AgentPracticeController.approval()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentPracticeController.java:36) → `AgentPracticeService.approval()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; omit the body or use {} for defaults.

- [ ] HTTP 200; mode=approval; decision=APPROVAL_REQUIRED; traceId present. These are business fields, not HTTP status codes.
- [ ] Defaults: toolName=search, planSteps=4, retryCount=0, totalTokens=220.
- [ ] Verify before/after metrics: agent.tool.calls outcome=approval_required; agent.approval.required reason=sensitive_action; agent.cost.tokens type=prompt +160, type=completion +60.
- [ ] For explicit override coverage, send positive promptTokens/completionTokens and compare totalTokens and metric deltas. No real LLM/tool action or shared USD-budget deduction.

#### AgentPracticeController.policyViolation — Simulated policy-violation

- API: `POST /api/agent/practice/policy-violation`
- Code: [AgentPracticeController.policyViolation()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentPracticeController.java:41) → `AgentPracticeService.policyViolation()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; omit the body or use {} for defaults.

- [ ] HTTP 200; mode=policy-violation; decision=DENIED; traceId present. These are business fields, not HTTP status codes.
- [ ] Defaults: toolName=search, planSteps=1, retryCount=0, totalTokens=70.
- [ ] Verify before/after metrics: agent.tool.calls outcome=denied; agent.policy.violation policy=mock_guardrail_policy; agent.cost.tokens type=prompt +60, type=completion +10.
- [ ] For explicit override coverage, send positive promptTokens/completionTokens and compare totalTokens and metric deltas. No real LLM/tool action or shared USD-budget deduction.

#### AgentPracticeController.externalApi — Simulated external-api

- API: `POST /api/agent/practice/external-api`
- Code: [AgentPracticeController.externalApi()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentPracticeController.java:46) → `AgentPracticeService.externalApi()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; omit the body or use {} for defaults.

- [ ] HTTP 200; mode=external-api; decision=ALLOWED; traceId present. These are business fields, not HTTP status codes.
- [ ] Defaults: toolName=search, planSteps=3, retryCount=0, totalTokens=320.
- [ ] Verify before/after metrics: agent.external.api.calls provider=mock-llm-provider, outcome=success; agent.cost.tokens type=prompt +200, type=completion +120.
- [ ] For explicit override coverage, send positive promptTokens/completionTokens and compare totalTokens and metric deltas. No real LLM/tool action or shared USD-budget deduction.

#### AgentPracticeController.dbWrite — Simulated db-write

- API: `POST /api/agent/practice/db-write`
- Code: [AgentPracticeController.dbWrite()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentPracticeController.java:51) → `AgentPracticeService.dbWrite()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; omit the body or use {} for defaults.

- [ ] HTTP 200; mode=db-write; decision=ALLOWED; traceId present. These are business fields, not HTTP status codes.
- [ ] Defaults: toolName=search, planSteps=2, retryCount=0, totalTokens=140.
- [ ] Verify before/after metrics: agent.db.write target=agent_memory, outcome=success; agent.cost.tokens type=prompt +100, type=completion +40.
- [ ] For explicit override coverage, send positive promptTokens/completionTokens and compare totalTokens and metric deltas. No real LLM/tool action or shared USD-budget deduction.

### Risk-pattern simulations

#### AgentRiskPatternController.policyViolationRetry — Simulated policy-violation-retry

- API: `POST /api/agent/risk-patterns/policy-violation-retry`
- Code: [AgentRiskPatternController.policyViolationRetry()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskPatternController.java:21) → `AgentRiskPatternService.policyViolationRetry()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; empty body uses the scenario defaults.

- [ ] HTTP 200; mode=policy-violation-retry; decision=SUSPICIOUS; traceId present.
- [ ] Defaults: toolName=database, planSteps=4, retryCount=3, totalTokens=160.
- [ ] Verify combined metrics: agent.policy.violation policy=blocked_sensitive_operation + agent.retry.count; simulated prompt/completion deltas are +120/+40.
- [ ] Confirm this handler records a fixed simulation; it does not call AgentRiskDecisionService, persist S05 risk history or automatically create an Incident.

#### AgentRiskPatternController.toolErrorRetry — Simulated tool-error-retry

- API: `POST /api/agent/risk-patterns/tool-error-retry`
- Code: [AgentRiskPatternController.toolErrorRetry()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskPatternController.java:26) → `AgentRiskPatternService.toolErrorRetry()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; empty body uses the scenario defaults.

- [ ] HTTP 200; mode=tool-error-retry; decision=UNSTABLE; traceId present.
- [ ] Defaults: toolName=search, planSteps=5, retryCount=3, totalTokens=150.
- [ ] Verify combined metrics: agent.tool.errors error=repeated_tool_failure + agent.retry.count; simulated prompt/completion deltas are +100/+50.
- [ ] Confirm this handler records a fixed simulation; it does not call AgentRiskDecisionService, persist S05 risk history or automatically create an Incident.

#### AgentRiskPatternController.externalApiPolicyViolation — Simulated external-api-policy-violation

- API: `POST /api/agent/risk-patterns/external-api-policy-violation`
- Code: [AgentRiskPatternController.externalApiPolicyViolation()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskPatternController.java:31) → `AgentRiskPatternService.externalApiPolicyViolation()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; empty body uses the scenario defaults.

- [ ] HTTP 200; mode=external-api-policy-violation; decision=RISKY_EXTERNAL_ACCESS; traceId present.
- [ ] Defaults: toolName=external-api, planSteps=4, retryCount=1, totalTokens=250.
- [ ] Verify combined metrics: agent.external.api.calls outcome=risky + agent.policy.violation policy=risky_external_dependency; simulated prompt/completion deltas are +180/+70.
- [ ] Confirm this handler records a fixed simulation; it does not call AgentRiskDecisionService, persist S05 risk history or automatically create an Incident.

#### AgentRiskPatternController.approvalRequiredRetry — Simulated approval-required-retry

- API: `POST /api/agent/risk-patterns/approval-required-retry`
- Code: [AgentRiskPatternController.approvalRequiredRetry()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentRiskPatternController.java:36) → `AgentRiskPatternService.approvalRequiredRetry()`
- Cost / tokens: agent.cost.tokens (simulated)
- Setup: Optional AgentPracticeRequest; empty body uses the scenario defaults.

- [ ] HTTP 200; mode=approval-required-retry; decision=APPROVAL_RISK; traceId present.
- [ ] Defaults: toolName=email, planSteps=4, retryCount=2, totalTokens=210.
- [ ] Verify combined metrics: agent.approval.required reason=sensitive_action_retry + agent.retry.count; simulated prompt/completion deltas are +150/+60.
- [ ] Confirm this handler records a fixed simulation; it does not call AgentRiskDecisionService, persist S05 risk history or automatically create an Incident.

### Policy decisions

#### AgentPolicyCheckController.policyCheck — Mock policy decision

- API: `POST /api/agent/policy-check`
- Code: [AgentPolicyCheckController.policyCheck()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentPolicyCheckController.java:23) → `AgentPolicyCheckService.check()`
- Cost / tokens: No token accounting
- Setup: Optional AgentPracticeRequest; use userInput to select keyword rules.

- [ ] Safe input / {} / omitted body → HTTP 200, decision=ALLOWED, allowed=true, policyName=safe_request.
- [ ] userInput="drop table" → DENIED, allowed=false, policyName=dangerous_database_operation.
- [ ] userInput="email report" → APPROVAL_REQUIRED/sensitive_action; userInput="external api" → APPROVAL_REQUIRED/external_dependency_risk.
- [ ] Test rule precedence and case normalization; check agent.policy.check and the allowed/approval/violation metric as applicable.
- [ ] guardrailReady=true is a mock-policy response field; no NeMo call, agent.cost.tokens increment or ai.cost.* reservation occurs.

### Agent Jobs

#### AgentJobController.start — Start asynchronous Agent risk job

- API: `POST /api/agent/jobs`
- Code: [AgentJobController.start()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentJobController.java:26) → `AgentJobService.start()`
- Cost / tokens: agent.cost.tokens during background analyze()
- Setup: AgentRiskAnalyzeRequest body required; {} is accepted. Keep a fresh job fixture and baseline counts.

- [ ] HTTP 200 includes jobId/status/message/traceId; save jobId and poll the separate GET. Do not require analysis to be complete in the start response.
- [ ] Observe eventual COMPLETED with decision/riskScore/riskLevel/recommendedAction via GET; intermediate PENDING/RUNNING can be too brief to capture.
- [ ] Verify agent.jobs.started and eventual agent.jobs.completed, AgentRunHistory/AgentRiskHistory and conditional Incident links; follow the same risk rules as S05.
- [ ] Explicit promptTokens/completionTokens affect agent.cost.tokens during background analysis once, not on each poll; no USD reservation.
- [ ] Missing/malformed body → 400. FAILED/errorMessage requires a controlled failing service/DB fixture; there is no fail=true request option and no implemented restart recovery.

#### AgentJobController.findById — Job status and result

- API: `GET /api/agent/jobs/{jobId}`
- Code: [AgentJobController.findById()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentJobController.java:31) → `AgentJobService.findById()`
- Cost / tokens: Read-only; no token increment
- Setup: Use jobId from POST /api/agent/jobs.

- [ ] HTTP 200 returns the requested jobId/status/progress; after completion, compare result fields to the job input and S05 rule mapping.
- [ ] Poll twice: reads must not rerun analysis, add Agent history rows or increment agent.cost.tokens.
- [ ] Unknown numeric jobId currently throws IllegalArgumentException (500 expected, verify runtime); nonnumeric jobId → 400. Do not label the missing-ID branch as implemented 404.

#### AgentJobController.findRecent — Recent Job list

- API: `GET /api/agent/jobs`
- Code: [AgentJobController.findRecent()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/AgentJobController.java:36) → `AgentJobService.findRecent()`
- Cost / tokens: Read-only; no token increment
- Setup: Create several Job fixtures; allow analysis to finish.

- [ ] HTTP 200 recent-first list, at most 50; match jobId/status/result with the single-job GET.
- [ ] With no Job rows, [] is valid; repeated reads must not increment job-started/completed/token counters.
- [ ] Compare PENDING/RUNNING/COMPLETED/FAILED only for fixtures that actually reached those states.

### Legacy Intake

#### LegalIntakeController.analyze — Legacy rule-based readiness analysis

- API: `POST /api/legal/intake/analyze`
- Code: [LegalIntakeController.analyze()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeController.java:31) → `LegalIntakeDecisionService.analyze()`
- Cost / tokens: No token accounting
- Setup: JWT required; LegalIntakeAnalyzeRequest body; {} exercises defaults.

- [ ] HTTP 200 returns readinessScore/readinessLevel/decision/signals/recommendedNextStep/historyId and matched template fields.
- [ ] {} → low/default readiness; controlled complete inputs exercise HIGH readiness; urgent=true + hasDeadline=true exercises URGENT_REVIEW_REQUIRED priority.
- [ ] Verify the saved legal_intake_history row and query it through the legacy history/summary routes.
- [ ] Missing JWT → 401; missing body/malformed JSON → 400. No @Valid field rejection is declared on this handler; map null/blank/default behavior to toDraft().
- [ ] No core C03 intake creation and no LLM/ai.cost.* or agent.cost.tokens call.

#### LegalIntakeController.history — Legacy readiness history

- API: `GET /api/legal/intake/history`
- Code: [LegalIntakeController.history()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeController.java:36) → `LegalIntakeQueryService.findRecent()`
- Cost / tokens: Read-only; no token increment
- Setup: Create records through the legacy analyze route; this GET is currently public.

- [ ] No filter → recent-first, at most 50; readinessLevel filter and decision filter return matching legacy records.
- [ ] When both filters are supplied, readinessLevel takes precedence; no match / no records → [].
- [ ] Do not confuse these LegalIntakeHistory records with C05/C09 core intake DTOs or C17 Agent risk history; GET has no token increment.

#### LegalIntakeController.summary — Legacy readiness summary

- API: `GET /api/legal/intake/summary`
- Code: [LegalIntakeController.summary()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/LegalIntakeController.java:42) → `LegalIntakeQueryService.summarize()`
- Cost / tokens: Read-only; no token increment
- Setup: Capture baseline and create one legacy analysis fixture.

- [ ] HTTP 200 returns total/level counts and decision/next-step maps; reconcile deltas with the legacy history.
- [ ] Empty data → zeros/empty maps; this is separate from C12 Agent operations and C13 marketing.
- [ ] Repeated reads do not create history or add token cost.

### Mock DTO APIs

#### ChatApiController.chat — Mock chat response

- API: `POST /api/chat`
- Code: [ChatApiController.chat()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/ChatApiController.java:22) → `ChatService.chat()`
- Cost / tokens: No token accounting
- Setup: ChatRequest: userId, message, model are required strings.

- [ ] HTTP 200 echoes userId/model and constructs answer from request values; thread/traceId are present.
- [ ] Blank required fields → HTTP 400 INVALID_REQUEST/fieldErrors; capture X-Trace-Id and the matching error/log evidence.
- [ ] Confirm ChatService.chat() builds a string without a provider client; this is not S01/S02 and does not consume ai.cost.* or agent.cost.tokens.

#### MenuApiController.createMenu — Mock menu response

- API: `POST /api/menus`
- Code: [MenuApiController.createMenu()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/MenuApiController.java:22) → `MenuService.createMenu()`
- Cost / tokens: No token accounting
- Setup: MenuCreateRequest: name, price>=1, categoryCode>=1, nonblank orderableStatus.

- [ ] HTTP 200 returns submitted fields/message/traceId; this handler does not create a database Menu row.
- [ ] Blank name/status or nonpositive price/categoryCode → HTTP 400 with fieldErrors.
- [ ] Use this to map DTO validation and request metrics; do not confuse it with the persistent transaction-practice menu routes.

### JPA transaction practice

#### JpaTransactionPracticeController.create — Create persistent practice menu

- API: `POST /api/transaction-practice/menus`
- Code: [JpaTransactionPracticeController.create()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/JpaTransactionPracticeController.java:26) → `JpaTransactionPracticeService.create()`
- Cost / tokens: No token accounting
- Setup: PracticeMenuCreateRequest, e.g. {"name":"before","price":1000}.

- [ ] HTTP 200 returns id/name/price; save id as practiceMenuId and verify with the practice GET.
- [ ] Blank name or price<1 → HTTP 400; compare repository.save() with the mock /api/menus route.
- [ ] Use a separate practice record for each good/bad update comparison.

#### JpaTransactionPracticeController.find — Read persistent practice menu

- API: `GET /api/transaction-practice/menus/{id}`
- Code: [JpaTransactionPracticeController.find()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/JpaTransactionPracticeController.java:31) → `JpaTransactionPracticeService.find()`
- Cost / tokens: No token accounting
- Setup: Use practiceMenuId from the practice POST.

- [ ] HTTP 200 returns the current persisted name/price; compare before/after each PATCH.
- [ ] Unknown numeric id → HTTP 404; nonnumeric id → HTTP 400.
- [ ] The GET result, not the PATCH response alone, is the persistence evidence.

#### JpaTransactionPracticeController.goodUpdate — Transactional dirty-checking update

- API: `PATCH /api/transaction-practice/menus/{id}/good`
- Code: [JpaTransactionPracticeController.goodUpdate()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/JpaTransactionPracticeController.java:36) → `JpaTransactionPracticeService.goodUpdate()`
- Cost / tokens: No token accounting
- Setup: Existing practiceMenuId; body {"name":"after-good"}.

- [ ] HTTP 200 displays the new name; a separate GET must also return after-good.
- [ ] Map @Transactional → managed entity change → commit/UPDATE SQL; check database evidence.
- [ ] Blank name → HTTP 400; unknown numeric id → HTTP 404.

#### JpaTransactionPracticeController.badUpdate — Nontransactional update demonstration

- API: `PATCH /api/transaction-practice/menus/{id}/bad`
- Code: [JpaTransactionPracticeController.badUpdate()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/JpaTransactionPracticeController.java:42) → `JpaTransactionPracticeService.badUpdate()`
- Cost / tokens: No token accounting
- Setup: Existing separate practiceMenuId; record the old name; body {"name":"after-bad"}.

- [ ] HTTP 200 can display after-bad, while a separate GET should still return the old persisted name for this demonstration.
- [ ] Map absence of the service transaction/no save() to SQL/DB evidence; record actual behavior if runtime persistence settings change.
- [ ] Blank name → HTTP 400; unknown numeric id → HTTP 404.

### JPA N+1 practice

#### NPlusOnePracticeController.createSampleData — Reset N+1 practice fixtures

- API: `POST /api/n-plus-one-practice/sample-data`
- Code: [NPlusOnePracticeController.createSampleData()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/NPlusOnePracticeController.java:20) → `NPlusOnePracticeService.createSampleData()`
- Cost / tokens: No token accounting
- Setup: Destructive fixture endpoint: use only the intended practice database/tables.

- [ ] Review createSampleData(): deletes practice chat logs and users, then creates five users and five logs.
- [ ] HTTP 200: mode=sample-data, count=5, chatLogs and traceId; verify the fixture rows.
- [ ] Repeated calls reset those practice tables rather than append; do not include this in a general shared-data smoke run.

#### NPlusOnePracticeController.badFindAll — Lazy-loading query demonstration

- API: `GET /api/n-plus-one-practice/bad`
- Code: [NPlusOnePracticeController.badFindAll()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/NPlusOnePracticeController.java:25) → `NPlusOnePracticeService.badFindAll()`
- Cost / tokens: No token accounting
- Setup: Run the sample-data reset intentionally; capture request-scoped SQL/DB query evidence.

- [ ] HTTP 200: mode=bad, expected fixture count and user/log data.
- [ ] Map findAll() followed by lazy user access; inspect repeated user SELECTs under controlled cache conditions.
- [ ] A 200 response or slower duration alone does not prove N+1; compare query patterns/counts with the good route.

#### NPlusOnePracticeController.goodFindAll — Join-fetch query demonstration

- API: `GET /api/n-plus-one-practice/good`
- Code: [NPlusOnePracticeController.goodFindAll()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/NPlusOnePracticeController.java:30) → `NPlusOnePracticeService.goodFindAll()`
- Cost / tokens: No token accounting
- Setup: Use the same fixture dataset as the bad route; isolate/clear cache when comparing.

- [ ] HTTP 200: mode=good; the returned log/user dataset should match the bad route.
- [ ] Map findAllWithUser() to join-fetch SQL and compare query patterns/counts with the bad path.
- [ ] Read-only; no fixture reset, token ledger update or inference based only on HTTP latency.

### Status and guide APIs

#### ApiStatusController.status — Application status DTO

- API: `GET /api/status`
- Code: [ApiStatusController.status()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/ApiStatusController.java:12)
- Cost / tokens: No token accounting
- Setup: No body; public application route.

- [ ] HTTP 200 exposes status=UP, application identity, Java/thread details and the trace/interceptor note.
- [ ] This static DTO does not establish that the database, NeMo or an LLM provider is healthy; check the appropriate route separately.
- [ ] No domain DB write or token-accounting action.

#### ObservabilityGuideController.guide — Static observability guide

- API: `GET /api/observability/guide`
- Code: [ObservabilityGuideController.guide()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/ObservabilityGuideController.java:14)
- Cost / tokens: No token accounting
- Setup: No body; prepare X-Trace-Id for a separate /api/chat request.

- [ ] HTTP 200 exposes goal/metrics/logs/traces/testRequests and references the existing metric endpoints.
- [ ] Follow one referenced valid/invalid /api/chat request; correlate response trace header/body with request logs and metrics.
- [ ] Reading the guide itself does not execute its listed requests or validate the referenced metrics.

#### SecurityObservabilityGuideController.guide — Static Agent security guide

- API: `GET /api/security-observability/guide`
- Code: [SecurityObservabilityGuideController.guide()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/api/SecurityObservabilityGuideController.java:14)
- Cost / tokens: No token accounting
- Setup: No body; use the listed mock policy/risk-pattern endpoints as separate tests.

- [ ] HTTP 200 exposes policyModel/practiceEndpoints/metricCombinations/prometheus/futureGuardrail.
- [ ] Follow one referenced simulation and inspect the specified metric combination.
- [ ] futureGuardrail describes replacing the mock AgentPolicyCheckService; it does not mean the guide invokes NeMo or that S03/S04 use this mock policy service.

## Additional Actuator request patterns — 4, no API IDs

Configured exposure: `management.endpoints.web.exposure.include=health,info,metrics,prometheus`. C16 covers Prometheus. Security comes from [SecurityConfig.actuatorSecurity()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/config/SecurityConfig.java:44); no application business Controller is involved.

### Health

- API: `GET /actuator/health`
- [ ] Public request returns the actual health status; use the status/body to diagnose DB/health contributors, not `ApiStatusController`'s static UP field.
- [ ] Check the configured health-detail exposure in the current environment; do not assume DOWN must return HTTP 200.
- [ ] Read-only; no token reservation or simulated-token increment.

### Info

- API: `GET /actuator/info`
- [ ] Public request returns configured contributor data; an empty object can be valid.
- [ ] Confirm it uses the Actuator chain and does not require the application JWT.
- [ ] Read-only; no token-accounting action.

### Metric-name catalog

- API: `GET /actuator/metrics`
- [ ] ADMIN Basic → metric-name list; missing credentials → 401; USER Basic → 403.
- [ ] Trigger the target path before expecting lazily registered `ai.cost.*` or `agent.*` meters.
- [ ] Listing meters does not execute the underlying Agent/LLM action.

### Metric details

- API: `GET /actuator/metrics/{metricName}`
- [ ] Use `ai.cost.accounted.usd`, `ai.cost.stage.accounted.usd` or `agent.cost.tokens`; inspect measurements and availableTags.
- [ ] Use the actual tags to isolate a stage/token type, e.g. `?tag=stage:answer` or `?tag=type:prompt`; record a baseline and a post-call value.
- [ ] Check ADMIN Basic / 401 / 403 as above; an unregistered metric may return 404.
- [ ] Reading the measurement does not reserve cost or increment the underlying token Counter.

Optional framework discovery `GET /actuator` and health component/group subpaths depend on framework registration/configuration. Inspect the running links/config before adding them to a run; they are not new Controller APIs or included in the four patterns above. UI/servlet paths such as `/h2-console` and implicit HEAD/OPTIONS are outside the JSON API count; CORS OPTIONS is a shared transport check.

## External adapter contracts — 2, separate server

Base URL: `guardrails.base-url` / `NEMO_GUARDRAILS_URL`, default `http://localhost:8000`. These are not extra application S-IDs. Remote server authentication is deployment-specific; application JWT/Actuator Basic rules do not define that server's access policy.

### Input adapter

- API: `POST /v1/input-rails/check` on the NeMo base URL.
- Code consumer: [NemoInputGuardrailClient.check()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/guardrail/NemoInputGuardrailClient.java).
- [ ] Send `{"messages":[{"role":"user","content":"민사"}]}`; verify status/content/rail and optional usage.prompt_tokens/completion_tokens.
- [ ] Compare PASSED/MODIFIED/blocked responses with S03; test valid reported zero versus unavailable/invalid usage using a controlled adapter.
- [ ] Verify timeout/error/empty-body behavior through S03 and `CostedGuardrailService`; direct remote calls bypass the application's reservation wrapper.

### Output adapter

- API: `POST /v1/output-rails/check` on the NeMo base URL.
- Code consumer: [NemoOutputGuardrailClient.check()](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/guardrail/NemoOutputGuardrailClient.java).
- [ ] Send `{"messages":[{"role":"assistant","content":"안내"}]}`; verify status/content/rail and optional aggregate usage.
- [ ] Compare allowed/modified/blocked results with S04; check that local output rejection in the application avoids this remote call.
- [ ] The direct adapter request can still incur provider cost but does not run the application's `AiCostControlService`. Verify application budget accounting by calling S04, not by counting a direct adapter call as a ledger transaction.

## Failure cases that need controlled fixtures

A stock Postman body cannot force a provider to omit usage, guarantee an SSE timeout race, make a Job fail in the DB layer, or prove synchronized concurrency. Use a controllable adapter / isolated configuration / automated fixture and record that run mode. Current missing-ID and validation behavior must be measured as implemented; do not replace an unconverted 500 with a desired 404 in the evidence.

Source families: [AgentPracticeService](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentPracticeService.java) · [AgentRiskPatternService](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskPatternService.java) · [AgentRiskDecisionService](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentRiskDecisionService.java) · [AgentJobService](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/AgentJobService.java) · [AgentMetricRecorder](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/support/AgentMetricRecorder.java) · [JpaTransactionPracticeService](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/JpaTransactionPracticeService.java) · [NPlusOnePracticeService](C:/myLectureWs/legal-ai-intake-security-server/backend/guardrail-ready-policy-checker/src/main/java/com/ohgiraffers/handlermethod/service/NPlusOnePracticeService.java).
