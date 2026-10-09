# AI Agent Security Observability Portfolio

This portfolio records the experiment evidence for an AI agent security observability lab.

## Top Experiment Results

This portfolio is built around one result:

```text
Single metrics show events.
Metric combinations reveal risky AI agent behavior.
```

The strongest experiments are the combined-risk patterns below. These are the results that should be seen first because they came from hands-on Postman scenarios and Prometheus evidence.

## 1. Three-Metric Incident Pattern

```text
policy violation
+ retry
+ tool calls
= incident-like agent behavior
```

Evidence:

![three metric policy risk](docs/evidence/risk-patterns/2026-09-17-three-metric-policy-violation-retry-tool-calls-prometheus.png)

PromQL:

```promql
increase(agent_policy_violation_total[5m])
or
increase(agent_retry_count_total[5m])
or
increase(agent_tool_calls_total[5m])
```

Interpretation:

```text
The agent crossed a policy boundary,
retried near that boundary,
and continued tool activity in the same experiment window.
```

Guardrail-ready action:

```text
BLOCK_AND_CREATE_INCIDENT
```

## 2. External API + Policy Violation

```text
external API access
+ policy violation
= risky behavior moving toward an outside dependency
```

Evidence:

![external policy prometheus](docs/evidence/risk-patterns/2026-09-17-external-api-policy-violation-prometheus.png)

PromQL:

```promql
increase(agent_external_api_calls_total[5m])
or
increase(agent_policy_violation_total[5m])
```

Interpretation:

```text
This is not just a failed request.
It is a risky agent action approaching an external dependency while also triggering a policy violation.
```

Guardrail-ready action:

```text
BLOCK_AND_ESCALATE
```

## 3. External API + Policy Violation + Tool Calls

```text
external API access
+ policy violation
+ tool calls
= risky behavior moving outward while tools remain active
```

Evidence:

![three metric external risk](docs/evidence/risk-patterns/2026-09-17-three-metric-external-policy-tool-calls-prometheus.png)

PromQL:

```promql
increase(agent_external_api_calls_total[5m])
or
increase(agent_policy_violation_total[5m])
or
increase(agent_tool_calls_total[5m])
```

Interpretation:

```text
The agent moved toward an external dependency,
triggered a policy violation,
and still produced tool activity.
This is stronger than a simple denied request.
```

Guardrail-ready action:

```text
BLOCK_AND_ESCALATE
```

## 4. Approval Required + Retry

```text
approval required
+ retry
= repeated attempts around human control
```

Evidence:

![approval retry prometheus](docs/evidence/risk-patterns/2026-09-17-approval-required-retry-prometheus.png)

PromQL:

```promql
increase(agent_approval_required_total[5m])
or
increase(agent_retry_count_total[5m])
```

Interpretation:

```text
APPROVAL_REQUIRED is not a normal error.
It is a human approval boundary.
Repeated retries near that boundary should freeze the action until explicit approval is recorded.
```

Guardrail-ready action:

```text
REQUIRE_APPROVAL
```

## Baseline Experiments

Core thesis:

```text
Success is not enough.
AI agent risk appears in behavior metrics, not only in the response body.
```

The lab uses Spring Boot custom metrics, Postman-triggered scenarios, and Prometheus evidence to observe risky AI agent behavior patterns.

## 1. Policy Decision Metrics

Policy checks classify agent requests into:

```text
ALLOWED
DENIED
APPROVAL_REQUIRED
```

PromQL:

```promql
sum by (decision, policy) (
  increase(agent_policy_check_total[5m])
)
```

Evidence:

![policy decisions](docs/evidence/basic/2026-09-17-policy-check-decisions-prometheus-allowed-denied.png)

Interpretation:

```text
Policy decisions are visible as separate metric series.
This means an AI agent request can be observed not only by HTTP status, but by policy outcome.
```

## 2. Allowed Request

Postman scenario:

```http
POST /api/agent/policy-check
```

```json
{
  "userInput": "search public policy documents",
  "toolName": "search"
}
```

PromQL:

```promql
increase(agent_policy_allowed_total[5m])
```

Evidence:

![allowed increase](docs/evidence/basic/2026-09-17-policy-check-allowed-increase-prometheus.png)

Interpretation:

```text
Safe requests are counted separately from denied and approval-required requests.
This provides a baseline for normal agent behavior.
```

## 3. Denied Request

Postman scenario:

```http
POST /api/agent/policy-check
```

```json
{
  "userInput": "delete all customer records",
  "toolName": "database"
}
```

Evidence:

![denied postman](docs/evidence/basic/2026-09-17-policy-check-denied-postman.png)

PromQL:

```promql
increase(agent_policy_violation_total{policy="dangerous_database_operation"}[5m])
```

Evidence:

![denied prometheus](docs/evidence/basic/2026-09-17-policy-violation-dangerous-database-prometheus.png)

Interpretation:

```text
Dangerous database or bulk customer-data operations are denied and emitted as policy violation metrics.
This turns policy enforcement into observable security evidence.
```

## 4. Approval Required

Postman scenario:

```http
POST /api/agent/policy-check
```

```json
{
  "userInput": "send customer report by email",
  "toolName": "email"
}
```

Evidence:

![approval postman](docs/evidence/basic/2026-09-17-policy-check-approval-required-postman.png)

PromQL:

```promql
increase(agent_approval_required_total{reason="sensitive_action"}[5m])
```

Evidence:

![approval increase](docs/evidence/basic/2026-09-17-approval-required-increase-prometheus.png)

Interpretation:

```text
APPROVAL_REQUIRED is not a simple failure.
It is a human approval boundary that can be measured independently.
```

## 5. Policy Violation + Retry

Postman scenario:

```http
POST /api/agent/risk-patterns/policy-violation-retry
```

```json
{
  "userInput": "delete all customer records repeatedly",
  "toolName": "database"
}
```

Evidence:

![policy violation retry postman](docs/evidence/risk-patterns/2026-09-17-policy-violation-retry-postman.png)

PromQL:

```promql
increase(agent_policy_violation_total[5m])
or
increase(agent_retry_count_total[5m])
```

Evidence:

![policy violation retry prometheus](docs/evidence/risk-patterns/2026-09-17-policy-violation-retry-prometheus.png)

Interpretation:

```text
Policy violation and retry increased together.
This indicates that the AI agent repeatedly approached a blocked or risky action.
```

Guardrail meaning:

```text
policy violation only
-> deny and log

policy violation + retry
-> deny, alert, freeze, and require review
```

## 6. Tool Error + Retry

Postman scenario:

```http
POST /api/agent/risk-patterns/tool-error-retry
```

```json
{
  "userInput": "search recent policy documents with failing tool",
  "toolName": "search"
}
```

Evidence:

![tool error retry postman](docs/evidence/risk-patterns/2026-09-17-tool-error-retry-postman.png)

PromQL:

```promql
increase(agent_tool_errors_total[5m])
or
increase(agent_retry_count_total[5m])
```

Evidence:

![tool error retry prometheus](docs/evidence/risk-patterns/2026-09-17-tool-error-retry-prometheus.png)

Interpretation:

```text
Tool error and retry increased together.
This indicates repeated calls to an unstable or failing tool.
```

Cost meaning:

```text
One tool failure can create multiple retry attempts.
Repeated retries create token cost, latency, and operational noise.
```

## 7. External API + Policy Violation

Postman scenario:

```http
POST /api/agent/risk-patterns/external-api-policy-violation
```

```json
{
  "userInput": "upload sensitive customer report to external api",
  "toolName": "external-api"
}
```

Evidence:

![external policy postman](docs/evidence/risk-patterns/2026-09-17-external-api-policy-violation-postman.png)

PromQL:

```promql
increase(agent_external_api_calls_total[5m])
or
increase(agent_policy_violation_total[5m])
```

Evidence:

![external policy prometheus](docs/evidence/risk-patterns/2026-09-17-external-api-policy-violation-prometheus.png)

Interpretation:

```text
An external API call occurred with a risky outcome.
At the same time, the policy violation metric increased with the blocked_sensitive_operation policy.
This means external dependency access and policy violation were observed in the same time window.
This pattern indicates that a risky agent request moved toward an external dependency.
```

PM meaning:

```text
This is both a security risk and a cost risk.
External calls can increase provider cost, rate-limit pressure, and data exposure risk.
```

## 8. Approval Required + Retry

Postman scenario:

```http
POST /api/agent/risk-patterns/approval-required-retry
```

```json
{
  "userInput": "send customer report by email repeatedly",
  "toolName": "email"
}
```

Evidence:

![approval retry postman](docs/evidence/risk-patterns/2026-09-17-approval-required-retry-postman.png)

PromQL:

```promql
increase(agent_approval_required_total[5m])
or
increase(agent_retry_count_total[5m])
```

Evidence:

![approval retry prometheus](docs/evidence/risk-patterns/2026-09-17-approval-required-retry-prometheus.png)

Interpretation:

```text
Approval-required events and retries increased together.
This indicates repeated behavior near a human approval boundary.
```

Guardrail meaning:

```text
If an agent repeatedly approaches an approval boundary,
the system should freeze the action until explicit approval is recorded.
```

## 9. Three-Metric Risk Pattern

PromQL:

```promql
increase(agent_policy_violation_total[5m])
or
increase(agent_retry_count_total[5m])
or
increase(agent_tool_calls_total[5m])
```

Evidence:

![three metric policy risk](docs/evidence/risk-patterns/2026-09-17-three-metric-policy-violation-retry-tool-calls-prometheus.png)

Interpretation:

```text
Policy violation, retry, and tool calls appeared together.
This is stronger than a single event because it shows a behavior pattern:
the agent used tools and repeated behavior around a blocked risk boundary.
```

## 10. External Three-Metric Risk Pattern

PromQL:

```promql
increase(agent_external_api_calls_total[5m])
or
increase(agent_policy_violation_total[5m])
or
increase(agent_tool_calls_total[5m])
```

Evidence:

![three metric external risk](docs/evidence/risk-patterns/2026-09-17-three-metric-external-policy-tool-calls-prometheus.png)

Interpretation:

```text
External API call, policy violation, and tool call signals appeared together.
This is a high-risk pattern because the agent moved toward an external dependency while also triggering a policy violation.
```

## Final Message

```text
A successful response body is not enough.
AI agent risk must be observed through behavior metrics.
```

This project demonstrates:

```text
Postman triggers agent behavior
-> Spring Boot records custom metrics
-> Prometheus shows behavior signals
-> Metric combinations reveal risk patterns
-> Future guardrails can act on those patterns
```

Planned next step:

```text
Replace mock Java policy rules with a NVIDIA NeMo Guardrails adapter,
while keeping the same policy-check API and metric contract.
```
