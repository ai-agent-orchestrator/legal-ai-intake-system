# Spring Boot Observability Practice

This project is a custom observability practice for detecting hidden cost and suspicious behavior behind successful API responses.

The main idea is simple:

```text
API success is not enough.
The system must observe internal cost, agent behavior, security signals, and guardrail-ready risk patterns.
```

## Main Focus: Agent Behavior Detection

AI agent risk is often not visible from the final answer alone.

The important signal is the behavior pattern:

```text
retry + policy violation
-> the agent may be repeatedly trying a risky or blocked action

tool error + retry
-> the agent may be repeatedly calling an unstable tool

policy violation + external API call
-> the agent may be moving from a risky request toward an external dependency

approval required + retry
-> the agent may be repeatedly approaching an action that needs human approval
```

This project treats custom metrics as an early detection layer:

```text
Detect
-> Interpret
-> Alert
-> Guardrail / human approval / handling agent
-> Policy update
```

The goal is not only to draw graphs. The goal is to build the metric foundation for a cost-aware and guardrail-ready AI backend.

Planned October extension:

```text
Learn NVIDIA NeMo Guardrails
-> map each agent behavior pattern to a customized guardrail
-> connect detection metrics to guardrail actions
-> test detect -> block / approve / escalate flows
```

```text
Spring Boot Observability Practice
-> from JPA N+1 metrics
-> to AI Agent behavior metrics
-> to Security/Guardrail metrics
```

Core metric groups:

```text
Internal cost
-> SQL statement count, response time, request count

Agent behavior
-> tool calls, retries, token cost, external API calls, DB writes

Security / guardrail signals
-> policy violations, approval-required events, tool errors, suspicious metric combinations
```

PromQL example for suspicious behavior:

```promql
increase(agent_policy_violation_total[5m]) >= 1
and
increase(agent_retry_count_total[5m]) >= 3
```

The practical question this project asks:

```text
The API succeeded.
But was it cheap, safe, stable, and policy-compliant?
```

## JPA N+1 Practice

This branch is a Postman-based JPA N+1 experiment inside the Spring Boot API observability project.

## Actual Experiment Result

This is the key flow observed in this experiment:

```text
1. ChatLog list is queried first.
   select * from practice_chat_log

2. Five ChatLog objects are loaded into memory.
   But user is LAZY, so PracticeUser is not loaded yet.

3. During DTO conversion, userName is needed.
   chatLog.getUser().getName()

4. At that moment, Hibernate loads each ChatLog's User from DB one by one.
   select * from practice_user where id=?
   select * from practice_user where id=?
   select * from practice_user where id=?
   ...
```

In this experiment:

```text
ChatLog list SELECT 1 time
+ User SELECT N times
= N+1
```

So the problem is not that the Postman response is broken. The response can look perfectly normal.

The problem is hidden in the Hibernate SQL log.

The point is not to repeat generic JPA performance notes. The point is to see the hidden SQL cost behind a normal-looking API response.

## What I Tested

This experiment checks this exact situation:

```text
/bad
→ the response body looks normal
→ Hibernate first loads chat logs
→ then LAZY user access triggers repeated user SELECT queries
→ ChatLog list SELECT 1 time + User SELECT N times

/good
→ the response body looks similar
→ join fetch loads chat logs and users together
→ repeated user SELECT queries are reduced
```

N+1 is dangerous because the API can be functionally correct while SQL quietly explodes.

## Practice APIs

```text
POST /api/n-plus-one-practice/sample-data
GET  /api/n-plus-one-practice/bad
GET  /api/n-plus-one-practice/good
```

## Postman Setup

Run the server:

```powershell
./gradlew bootRun
```

Health check:

```http
GET http://localhost:8080/actuator/health
```

Expected response:

```json
{
  "status": "UP"
}
```

## What Postman Should Show

### 1. Create Sample Data

Request:

```http
POST http://localhost:8080/api/n-plus-one-practice/sample-data
X-Trace-Id: n-plus-one-sample-001
```

This creates:

```text
5 PracticeUser rows
5 PracticeChatLog rows
Each chat log points to one user with @ManyToOne(fetch = LAZY)
```

Expected response shape:

```json
{
  "mode": "sample-data",
  "message": "created 5 users and 5 chat logs",
  "count": 5,
  "traceId": "n-plus-one-sample-001",
  "chatLogs": [
    {
      "chatLogId": 1,
      "message": "message from user-1",
      "userId": 1,
      "userName": "user-1"
    }
  ]
}
```

### 2. Bad Query

Request:

```http
GET http://localhost:8080/api/n-plus-one-practice/bad
X-Trace-Id: n-plus-one-bad-001
```

What this does:

```text
1. practiceChatLogRepository.findAll()
2. Response DTO calls chatLog.getUser().getName()
3. LAZY user is accessed one by one
4. Hibernate can run repeated SELECT queries
```

Expected Postman result:

```text
The response body looks normal.
It returns chat logs with userName.
```

Expected Hibernate SQL log:

```sql
select ... from practice_chat_log ...
select ... from practice_user where id=?
select ... from practice_user where id=?
select ... from practice_user where id=?
select ... from practice_user where id=?
select ... from practice_user where id=?
```

Key evidence from `/bad`:

```text
/bad
→ API response looks correct
→ repeated SELECT queries appear in Hibernate SQL logs
→ the problem is not the response body
→ the problem is hidden DB query cost
```

### 3. Good Query

Request:

```http
GET http://localhost:8080/api/n-plus-one-practice/good
X-Trace-Id: n-plus-one-good-001
```

What this does:

```text
1. practiceChatLogRepository.findAllWithUser()
2. JPQL join fetch loads PracticeChatLog and PracticeUser together
3. Response DTO can read userName without repeated user SELECT queries
```

Expected Postman result:

```text
The response body looks similar to /bad.
It still returns chat logs with userName.
```

Expected Hibernate SQL log:

```sql
select ...
from practice_chat_log ...
join practice_user ...
```

Key evidence from `/good`:

```text
/good
→ API response looks similar to /bad
→ Hibernate SQL log shows a join query
→ repeated user SELECT queries are reduced
→ fetch join fixed the hidden query cost
```

## Result

```text
Postman confirms the API response.
Hibernate SQL logs reveal the hidden DB query cost.
traceId connects the Postman request to the server log.
```

The practical lesson:

```text
A correct API response does not guarantee an efficient API.
N+1 must be checked through Hibernate SQL logs, not only through Postman response bodies.
```

## Why This Matters

This is part of the backend feedback loop:

```text
Postman response
→ traceId logs
→ Hibernate SQL logs
→ repeated SELECT detection
→ fetch join comparison
```

The goal is to understand JPA performance by observing real request/response behavior and actual SQL execution.

## Prometheus and Grafana Check

This branch also exposes SQL statement count as application metrics.

```text
practice.api.sql.statements
practice.api.sql.statements.per.request
```

Prometheus converts dots to underscores:

```promql
practice_api_sql_statements_total
practice_api_sql_statements_per_request_count
practice_api_sql_statements_per_request_sum
```

Compare `/bad` and `/good` with these queries:

```promql
increase(practice_api_sql_statements_total{uri="/api/n-plus-one-practice/bad"}[5m])
```

```promql
increase(practice_api_sql_statements_total{uri="/api/n-plus-one-practice/good"}[5m])
```

Average SQL statements per request:

```promql
rate(practice_api_sql_statements_per_request_sum{uri="/api/n-plus-one-practice/bad"}[5m])
/
rate(practice_api_sql_statements_per_request_count{uri="/api/n-plus-one-practice/bad"}[5m])
```

```promql
rate(practice_api_sql_statements_per_request_sum{uri="/api/n-plus-one-practice/good"}[5m])
/
rate(practice_api_sql_statements_per_request_count{uri="/api/n-plus-one-practice/good"}[5m])
```

The point is:

```text
/bad  -> one HTTP request, many SQL statements
/good -> one HTTP request, fewer SQL statements
```

## Agent Custom Metrics Practice

This branch also starts a guardrail-ready agent observability practice.

NeMo Guardrails is not integrated yet. The goal is to define a replaceable boundary first:

```text
Agent API
-> AgentPracticeService
-> AgentMetricRecorder
-> Prometheus / Grafana
```

Later, a mock policy decision can be replaced with a real NeMo Guardrails adapter.

Practice APIs:

```http
POST /api/agent/practice/run
POST /api/agent/practice/tool-error
POST /api/agent/practice/retry
POST /api/agent/practice/approval
POST /api/agent/practice/policy-violation
POST /api/agent/practice/external-api
POST /api/agent/practice/db-write
```

Sample body:

```json
{
  "userInput": "search recent policy documents",
  "toolName": "search",
  "planSteps": 3,
  "retryCount": 1,
  "promptTokens": 120,
  "completionTokens": 80
}
```

Custom metrics:

```text
agent.tool.calls
agent.tool.errors
agent.plan.steps
agent.plan.steps.per.request
agent.retry.count
agent.cost.tokens
agent.external.api.calls
agent.db.write
agent.approval.required
agent.policy.violation
```

Prometheus metric names:

```promql
agent_tool_calls_total
agent_tool_errors_total
agent_plan_steps_total
agent_plan_steps_per_request_sum
agent_retry_count_total
agent_cost_tokens_total
agent_external_api_calls_total
agent_db_write_total
agent_approval_required_total
agent_policy_violation_total
```

PromQL examples:

```promql
increase(agent_tool_calls_total[5m])
```

```promql
increase(agent_tool_errors_total[5m])
```

```promql
increase(agent_retry_count_total[5m])
```

```promql
increase(agent_external_api_calls_total[5m])
```

```promql
increase(agent_db_write_total[5m])
```

```promql
increase(agent_approval_required_total[5m])
```

```promql
increase(agent_policy_violation_total[5m])
```

The point is:

```text
API success is not enough.
Agent behavior, retries, tool failures, token cost, approvals, and policy violations must be observable.
```

## Suspicious Agent Behavior Signals

Single metrics are useful, but agent security becomes more meaningful when related signals are interpreted together.

```text
retry
-> the agent is repeating a failed or blocked behavior

policy violation
-> the agent attempted an action that violates a policy

tool error
-> a tool call failed during agent execution
```

The important pattern is not one failure. The important pattern is repeated behavior around a risky action.

```text
retry only
-> unstable tool, external API failure, or temporary failure

policy violation only
-> dangerous or disallowed request appeared

retry + policy violation
-> the agent may be repeatedly trying a risky or blocked action

retry + tool error
-> the agent may be repeatedly calling an unstable tool

tool error + retry + external API call
-> the agent may be repeatedly trying to recover through an external dependency
```

PromQL examples:

```promql
increase(agent_retry_count_total[5m])
```

```promql
increase(agent_policy_violation_total[5m])
```

```promql
increase(agent_tool_errors_total[5m])
```

Grafana panel idea:

```text
Panel title: Suspicious Agent Behavior
Query A: increase(agent_retry_count_total[5m])
Query B: increase(agent_policy_violation_total[5m])
Query C: increase(agent_tool_errors_total[5m])
```

Alert candidate:

```promql
increase(agent_policy_violation_total[5m]) >= 1
and
increase(agent_retry_count_total[5m]) >= 3
```

The point is:

```text
AI agent security is not only about the final answer.
It is also about what the agent repeatedly tried to do when it was blocked.
```

## Agent Risk Pattern Experiment APIs

This branch provides scenario APIs that intentionally emit multiple related metrics together.

```http
POST /api/agent/risk-patterns/policy-violation-retry
POST /api/agent/risk-patterns/tool-error-retry
POST /api/agent/risk-patterns/external-api-policy-violation
POST /api/agent/risk-patterns/approval-required-retry
```

These APIs are designed for Prometheus and Grafana experiments.

```text
policy-violation-retry
-> agent_policy_violation_total
-> agent_retry_count_total
-> agent_tool_calls_total

tool-error-retry
-> agent_tool_errors_total
-> agent_retry_count_total
-> agent_tool_calls_total

external-api-policy-violation
-> agent_external_api_calls_total
-> agent_policy_violation_total
-> agent_tool_calls_total

approval-required-retry
-> agent_approval_required_total
-> agent_retry_count_total
-> agent_tool_calls_total
```

Recommended Prometheus queries:

```promql
increase(agent_retry_count_total[5m])
```

```promql
increase(agent_policy_violation_total[5m])
```

```promql
increase(agent_tool_errors_total[5m])
```

```promql
increase(agent_external_api_calls_total[5m])
```

```promql
increase(agent_approval_required_total[5m])
```

Grafana panel idea:

```text
Panel title: Agent Risk Pattern Signals
Query A: increase(agent_retry_count_total[5m])
Query B: increase(agent_policy_violation_total[5m])
Query C: increase(agent_tool_errors_total[5m])
Query D: increase(agent_external_api_calls_total[5m])
Query E: increase(agent_approval_required_total[5m])
```

The point is:

```text
Suspicious agent behavior is not a single number.
It is a pattern made from metric combinations.
```

## Planned NeMo Guardrails Extension

This project does not integrate NVIDIA NeMo Guardrails yet.

The current goal is to prepare the detection layer first:

```text
agent behavior
-> custom metric
-> Prometheus query
-> Grafana panel / alert candidate
-> future customized guardrail
```

Planned October work:

```text
policy-violation-retry
-> detect repeated blocked behavior
-> custom guardrail: stop repeated risky attempts and require human review

tool-error-retry
-> detect repeated failed tool calls
-> custom guardrail: limit retries and route to fallback handling

external-api-policy-violation
-> detect risky behavior moving toward an external dependency
-> custom guardrail: block external call or require approval before outbound access

approval-required-retry
-> detect repeated attempts near a human approval boundary
-> custom guardrail: freeze action until explicit approval is recorded
```

The long-term direction:

```text
Detect suspicious agent behavior early
-> apply customized guardrails
-> escalate to human or handling AI
-> record the incident
-> update policy and metrics
```

## Guardrail-Ready Policy Checker

This branch also adds a mock policy checker that can later be replaced with a NVIDIA NeMo Guardrails adapter.

Current flow:

```text
POST /api/agent/policy-check
-> AgentPolicyCheckService
-> AgentMetricRecorder
-> Prometheus / Grafana
```

Practice API:

```http
POST /api/agent/policy-check
```

Safe request:

```json
{
  "userInput": "search public policy documents",
  "toolName": "search"
}
```

Expected decision:

```text
ALLOWED
```

Dangerous request:

```json
{
  "userInput": "delete all customer records",
  "toolName": "database"
}
```

Expected decision:

```text
DENIED
```

Sensitive action:

```json
{
  "userInput": "send customer report by email",
  "toolName": "email"
}
```

Expected decision:

```text
APPROVAL_REQUIRED
```

Policy-check metrics:

```text
agent.policy.check
agent.policy.allowed
agent.policy.violation
agent.approval.required
```

Prometheus names:

```promql
agent_policy_check_total
agent_policy_allowed_total
agent_policy_violation_total
agent_approval_required_total
```

PromQL examples:

```promql
sum by (decision, policy) (
  increase(agent_policy_check_total[5m])
)
```

```promql
increase(agent_policy_violation_total{policy="dangerous_database_operation"}[5m])
```

```promql
increase(agent_approval_required_total{reason="sensitive_action"}[5m])
```

The replacement point for October:

```text
Current:
AgentPolicyCheckService -> mock if-based policy rules

Future:
AgentPolicyCheckService -> NeMo Guardrails adapter
```
