import { metricValue } from './operatorApi.js';

export function readSecurityMetrics(samples) {
  const value = (name, labels) => metricValue(samples || [], name, labels);
  const average = stage => {
    const prefix = stage ? 'ai_chat_stage_duration_seconds' : 'ai_chat_duration_seconds';
    const labels = stage ? { stage } : {};
    const count = value(`${prefix}_count`, labels), sum = value(`${prefix}_sum`, labels);
    return count > 0 && sum != null ? sum / count : null;
  };
  return {
    cost: [
      { label: '실사용 비용', key: 'ai.cost.actual.usd', value: value('ai_cost_actual_usd') },
      { label: '추정 사용 정산', key: 'ai.cost.estimated.usage.usd', value: value('ai_cost_estimated_usage_usd') },
      { label: '정산 합계', key: 'ai.cost.accounted.usd', value: value('ai_cost_accounted_usd') },
      { label: '진행 중 예약 금액', key: 'ai.cost.reserved.usd', value: value('ai_cost_reserved_usd') },
    ],
    budgets: [
      { label: '월간 예산', key: 'ai.cost.budget.usd', value: value('ai_cost_budget_usd') },
      { label: '일일 예산', key: 'ai.cost.daily.budget.usd', value: value('ai_cost_daily_budget_usd') },
      { label: '세션 예산', key: 'ai.cost.session.budget.usd', value: value('ai_cost_session_budget_usd') },
    ],
    // CostStage uses answer; the synchronous Timer uses llm. They are separate labels.
    stages: [
      { label: '입력 가드레일', stage: 'input_guardrail' },
      { label: 'LLM', stage: 'answer' },
      { label: '출력 가드레일', stage: 'output_guardrail' },
      { label: '검색문 생성', stage: 'search_query' },
      { label: '검색 결과 설명', stage: 'search_explanation' },
    ].map(stage => ({ ...stage, value: value('ai_cost_stage_accounted_usd', { stage: stage.stage }) })),
    tokens: [
      { label: '프롬프트 토큰', value: value('ai_llm_tokens_total', { type: 'prompt' }) },
      { label: '완료 토큰', value: value('ai_llm_tokens_total', { type: 'completion' }) },
    ],
    timings: [
      { label: '전체 처리 시간', value: average() },
      { label: 'LLM 처리 시간', value: average('llm') },
      { label: '입력 검사 시간', value: average('input_guardrail') },
      { label: '출력 검사 시간', value: average('output_guardrail') },
    ],
  };
}

export const formatUsd = value => value == null ? '—' : `$${value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 6 })}`;
export const formatSeconds = value => value == null ? '—' : value.toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 3 });
