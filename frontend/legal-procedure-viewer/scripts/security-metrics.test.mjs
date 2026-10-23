import test from 'node:test';
import assert from 'node:assert/strict';
import { parseMetrics } from '../src/operator/operatorApi.js';
import { formatUsd, readSecurityMetrics } from '../src/operator/securityMetrics.js';

test('C16 distinguishes zero values from missing metrics and preserves small costs', () => {
  const data = readSecurityMetrics(parseMetrics('ai_cost_actual_usd 0\nai_cost_session_budget_usd 0.3\n'));
  assert.equal(data.cost[0].value, 0);
  assert.equal(data.cost[1].value, null);
  assert.equal(data.budgets[2].value, 0.3);
  assert.equal(formatUsd(0), '$0.00');
  assert.equal(formatUsd(null), '—');
  assert.equal(formatUsd(0.00012), '$0.00012');
});

test('stage costs use answer while LLM timings use llm; token types are aggregated separately', () => {
  const data = readSecurityMetrics(parseMetrics([
    'ai_cost_stage_accounted_usd{stage="input_guardrail"} 0.01',
    'ai_cost_stage_accounted_usd{stage="answer"} 0.04',
    'ai_cost_stage_accounted_usd{stage="output_guardrail"} 0.02',
    'ai_cost_stage_accounted_usd{stage="search_query"} 0.005',
    'ai_cost_stage_accounted_usd{stage="search_explanation"} 0.012',
    'ai_chat_stage_duration_seconds_sum{stage="llm"} 5',
    'ai_chat_stage_duration_seconds_count{stage="llm"} 2',
    'ai_llm_tokens_total{type="prompt",model="one"} 100',
    'ai_llm_tokens_total{type="prompt",model="two"} 150',
    'ai_llm_tokens_total{type="completion",model="one"} 40',
  ].join('\n')));
  assert.deepEqual(data.stages.map(item => item.value), [0.01, 0.04, 0.02, 0.005, 0.012]);
  assert.equal(data.timings[1].value, 2.5);
  assert.deepEqual(data.tokens.map(item => item.value), [250, 40]);
});

test('Timer average is combined sum/count and no observations remain unavailable', () => {
  const data = readSecurityMetrics(parseMetrics([
    'ai_chat_duration_seconds_sum{instance="one"} 2',
    'ai_chat_duration_seconds_count{instance="one"} 1',
    'ai_chat_duration_seconds_sum{instance="two"} 6',
    'ai_chat_duration_seconds_count{instance="two"} 3',
    'ai_chat_stage_duration_seconds_sum{stage="input_guardrail"} 0',
    'ai_chat_stage_duration_seconds_count{stage="input_guardrail"} 0',
  ].join('\n')));
  assert.equal(data.timings[0].value, 2);
  assert.equal(data.timings[2].value, null);
  assert.ok(readSecurityMetrics([]).timings.every(item => item.value === null));
});
