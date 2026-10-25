# 요청 단위 예산 사전 확인 (2026-10-25)

작성: Claude (브랜치 `claude/request-budget-precheck`). 비용 통제 실험 A7에서 재현한 문제를 고쳤다. 이 작업공간에서는 Gradle 다운로드가 막혀 **테스트를 직접 실행하지 못했으며**, 로컬에서 아래 검증을 실행해야 완료로 본다.

## 문제 (실험 A7)

S01 `/api/ai/chat`, S02 `/api/ai/chat/stream`은 유료 단계(입력 Guard → 답변 생성 → 출력 Guard)마다 **그 단계 금액만** 예산과 비교했다. 남은 예산이 애매하면 입력 Guard와 답변 생성은 통과하고 출력 Guard 예약에서 `SESSION_BUDGET_EXCEEDED`가 나며, **답변 생성 비용은 이미 쓰였는데 사용자는 답을 받지 못했다.**

모의 실험 근거 (세션 예산 $0.0004, 같은 세션 2회, 출력 상한 64):

| 지표 | 값 | 의미 |
|---|---|---|
| `ai_cost_blocked_total{stage="output_guardrail",reason="SESSION_BUDGET_EXCEEDED"}` | 1 | 답변 생성 **뒤** 차단 |
| `ai_cost_stage_actual_usd{stage="answer"}` | 3.06E-5 (= 2회분) | 2번째 요청도 답변 비용 발생 |
| `ai_cost_stage_actual_usd{stage="output_guardrail"}` | 1.95E-6 (1회분) | 2번째 출력 검사는 미실행 |
| 총 actual / reserved | 3.645E-5 / 0 | 사전 계산값과 일치, 예약은 정상 해제 |

출력 상한이 클수록 버려지는 금액이 커진다(gpt-4o-mini 기준 최대 64토큰 ≈ $0.00004, 300토큰 ≈ $0.0002, 기본 2048토큰 ≈ $0.0012).

## 수정

- `AiCostControlService.precheckRequestBudget(plan, model, sessionId)`: 요청의 **모든 유료 단계 예상액 합**이 월·일·세션 남은 예산(사용·추정·예약 포함)에 들어가는지 먼저 확인한다. 들어가지 않으면 기존과 같은 `COST_LIMIT_BLOCKED` + `*_BUDGET_EXCEEDED`로 차단하고, 지표 `ai.cost.blocked`의 `stage`는 `request_precheck`다. **예약은 하지 않는다.** 단계별 예약·정산·한도 검사는 그대로 유지되는 추가 관문이다.
- `CostedGuardrailService.chatPlan(systemPrompt, message)`: 입력 Guard(기존 예약과 같은 계산) + 답변(`answerEstimate`, 기존 예약과 같은 계산) + 출력 Guard. 출력 Guard는 답변이 생기기 전이므로 **출력 상한 × 6바이트/토큰 + 256**으로 크기를 잡는다(`ANSWER_BYTES_PER_TOKEN = 6`, 한국어 평균 3~5바이트/토큰을 넉넉히 덮는 값).
- `AiService`, `AiStreamingService`: 무료 검사(Dynamic·Fixed Guard, 입력 길이) 직후, 첫 유료 단계(입력 Guard) **전에** 호출한다.
- 리팩토링: 월·일·세션 비교를 `budgetExceededReason()` 하나로 모아 단계 예약과 사전 확인이 같은 규칙을 쓴다.

## 동작 변화

- 요청을 시작하려면 세 단계 예상액 전체가 남아 있어야 한다. 기본 설정(출력 2,048토큰)에서 짧은 질문 1건의 사전 확인액은 약 $0.0038, 시험 서버 64토큰은 약 $0.00082다. 세션 예산 $0.12에서는 체감 차이가 없다.
- 남은 위험: 공급자가 보고한 실제 사용량이 추정을 크게 넘으면(예: 모델이 상한보다 긴 입력 토큰을 보고) 여전히 출력 단계에서 막힐 수 있다. 이 경우는 기존 테스트 `outputBudgetDenialDoesNotDiscardAlreadyGeneratedCost`가 계속 검증한다(생성 비용 보존).
- 범위 밖: P03 검색(`SearchLlmService`)은 검색문·설명 단계별 검사 그대로다. 결과 설명을 템플릿으로 바꾸는 작업(네이버 약관 대응)과 함께 정리한다.

## 테스트

- 신규 `RequestBudgetPrecheckIntegrationTest`(동기·SSE): 세션 예산 $0.0012에서 1번째 요청 성공, 2번째는 **유료 클라이언트 호출 없이** 차단, LLM·입력·출력 Guard 각 1회, `request_precheck` 차단 1건, 예약 0. 수정 전 코드에서는 LLM이 2번 호출되어 실패한다.
- 신규 `AiCostRequestPrecheckTest`: 합계 기준 판정, 예약 없음, 기존 예약 반영, 월→일→세션 우선순위, 음수 거부.
- 수정 `AiSecurityIntegrationTest.outputBudgetDenialDoesNotDiscardAlreadyGeneratedCost`: 사전 확인을 통과할 여유($0.0040)를 남기고 초과 보고를 30,000토큰으로 키워 같은 상황(초과 보고 → 출력 Guard 예산 없음 → 생성 비용 보존)을 유지했다.

## 로컬 검증 (미실행 · 적용 후 필수)

```powershell
cd backend\guardrail-ready-policy-checker
.\gradlew.bat --offline test --rerun-tasks
```

실험 A7 재실행(모의 서버, 기본 질문 73바이트, 출력 64):
- 사전 확인액 ≈ $0.00084315(입력 Guard 0.00035655 + 답변 0.0000834 + 출력 Guard 0.0004032). 모의 서버는 Guard 사용량(5/2토큰)을 보고하므로 1회 실제 사용액은 ≈ $0.0000192다.
- 세션 예산 **$0.0004**: 사전 확인액보다 작아 **1번째 요청부터** `stage="request_precheck"`로 차단, `stage="answer"` actual 0.
- 세션 예산 **$0.00085**: 1번째 통과(0.00084315 ≤ 0.00085), 2번째는 0.0000192 + 0.00084315 = 0.00086235 > 0.00085로 **유료 단계 전에** 차단, `stage="answer"` actual은 1회분(1.53E-5)에 머문다.
