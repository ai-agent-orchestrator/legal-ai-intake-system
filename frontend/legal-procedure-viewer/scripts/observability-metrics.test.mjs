import test from 'node:test';
import assert from 'node:assert/strict';
import { parseMetrics } from '../src/operator/operatorApi.js';
import { behaviorPatterns, counterDelta, readBehaviorPattern, readDynamicMetrics, relatedHistory, operatorRiskPatterns, readPatternRisk, readPatternOccurrences } from '../src/operator/observabilityMetrics.js';

const pattern = behaviorPatterns.find(row => row.id === 'policy-retry');
const samples = text => parseMetrics(text);

test('unregistered counters are unavailable, registered zero is a valid observed value', () => {
  const absent = readBehaviorPattern(pattern, [], null);
  assert.equal(absent.status, '지표 미관측');
  assert.deepEqual(absent.metrics.map(row => row.value), [null, null]);
  const zero = readBehaviorPattern(pattern, samples('agent_policy_violation_total 0\nagent_retry_count_total 0'), null);
  assert.deepEqual(zero.metrics.map(row => row.value), [0, 0]);
  assert.equal(zero.status, '동반 신호 없음');
});

test('label series are summed and increases require both counters in the same collection interval', () => {
  const before = samples('process_start_time_seconds 100\nagent_policy_violation_total{policy="a"} 2\nagent_policy_violation_total{policy="b"} 1\nagent_retry_count_total 4');
  const current = samples('process_start_time_seconds 100\nagent_policy_violation_total{policy="b"} 2\nagent_policy_violation_total{policy="a"} 4\nagent_retry_count_total 6');
  const result = readBehaviorPattern(pattern, current, before);
  assert.deepEqual(result.metrics.map(row => [row.value, row.delta]), [[6, 3], [6, 2]]);
  assert.equal(result.status, '수집 구간 동반 증가');
  const singleIncrease = readBehaviorPattern(pattern, samples('process_start_time_seconds 100\nagent_policy_violation_total{policy="b"} 2\nagent_policy_violation_total{policy="a"} 4\nagent_retry_count_total 4'), before);
  assert.equal(singleIncrease.increased, false);
  assert.equal(singleIncrease.status, '누적 신호 존재');
});

test('a restart, counter reset, appearing or disappearing label series requires a new baseline', () => {
  const before = samples('process_start_time_seconds 100\nagent_retry_count_total{tool="a"} 8');
  for (const text of [
    'process_start_time_seconds 200\nagent_retry_count_total{tool="a"} 20',
    'process_start_time_seconds 100\nagent_retry_count_total{tool="a"} 1',
    'process_start_time_seconds 100\nagent_retry_count_total{tool="a"} 8\nagent_retry_count_total{tool="b"} 3',
    'process_start_time_seconds 100\nagent_retry_count_total{tool="b"} 10',
  ]) assert.equal(counterDelta(samples(text), before, 'agent_retry_count_total'), null);
});

test('label ordering cannot manufacture a reset or duplicate increase', () => {
  const before = samples('agent_tool_calls_total{tool="a",outcome="denied"} 2');
  const current = samples('agent_tool_calls_total{outcome="denied",tool="a"} 3');
  assert.equal(counterDelta(current, before, 'agent_tool_calls_total'), 1);
});

test('coincident aggregate counters do not join signals from unrelated request histories', () => {
  const rows = [
    { id: 1, signals: ['POLICY_VIOLATION'] },
    { id: 2, signals: ['RETRY'] },
    { id: 3, signals: ['POLICY_VIOLATION', 'EXTERNAL_API_CALL', 'RETRY'], decision: 'HIGH_RISK_AGENT_BEHAVIOR' },
    { id: 4, signals: null },
  ];
  assert.deepEqual(relatedHistory(pattern, rows).map(row => row.id), [3]);
  assert.deepEqual(relatedHistory(behaviorPatterns.find(row => row.id === 'policy-retry-tool'), rows), []);
});

test('session transitions, blocked responses and an unclamped max score remain separate metrics', () => {
  const result = readDynamicMetrics(samples('legal_ai_dynamic_risk_event_total{reason="a"} 2\nlegal_ai_dynamic_block_total{reason="REPEATED_RISK",trigger="a"} 1\nlegal_ai_dynamic_block_response_total{reason="DYNAMIC_SESSION_BLOCKED"} 5\nlegal_ai_session_max_risk_score 140'));
  assert.deepEqual(result.map(row => row.value), [2, 1, 5, 140]);
  assert.deepEqual(readDynamicMetrics([]).map(row => row.value), [null, null, null, null]);
});

test('screen13 uses a matching single request and applies the inclusive 80-point threshold', () => {
  const pattern = operatorRiskPatterns[0];
  const splitRows = [{ id: 1, signals: ['POLICY_VIOLATION'], riskScore: 40 }, { id: 2, signals: ['RETRY'], riskScore: 16 }];
  assert.equal(readPatternRisk(pattern, splitRows).total, null);
  for (const [total, block] of [[79, false], [80, true], [100, true]]) {
    const result = readPatternRisk(pattern, [{ id: 3, signals: ['POLICY_VIOLATION', 'RETRY'], riskScore: total }]);
    assert.equal(result.total, total);
    assert.equal(result.block, block);
    assert.equal(result.retryPoints, null);
  }
});

test('request score parts come from the server, with capped totals and unknown legacy details preserved', () => {
  const pattern = operatorRiskPatterns[2];
  const row = { id: 8, signals: ['POLICY_VIOLATION', 'EXTERNAL_API_CALL', 'RETRY'], riskScore: 81,
    scoreBreakdown: { behaviorPoints: 65, retryPoints: 16, total: 81, threshold: 80 } };
  assert.deepEqual([readPatternRisk(pattern,[row]).behaviorPoints,readPatternRisk(pattern,[row]).retryPoints],[65,16]);
  const capped = {...row,riskScore:100,scoreBreakdown:{behaviorPoints:115,retryPoints:25,total:100,threshold:80}};
  assert.equal(readPatternRisk(pattern,[capped]).behaviorPoints,115);
  assert.equal(readPatternRisk(pattern,[{...row,scoreBreakdown:{behaviorPoints:1,retryPoints:1,total:81}}]).behaviorPoints,null);
  assert.equal(readPatternRisk(pattern,[{...row,riskScore:'81'}]).total,null);
});

test('combined pattern count joins signals only within a unique history ID', () => {
  const p = operatorRiskPatterns[0];
  const rows = [{id:1, signals:['POLICY_VIOLATION']}, {id:2, signals:['RETRY']}, {id:3, signals:['POLICY_VIOLATION','RETRY'],createdAt:'2026-10-22T12:00:00'}, {id:3, signals:['POLICY_VIOLATION','RETRY'],createdAt:'2026-10-22T12:00:00'}];
  const result = readPatternOccurrences(p, rows);
  assert.equal(result.count,1); assert.equal(result.sourceCount,3);
  assert.equal(result.bins.reduce((a,b)=>a+b),1);
  assert.equal(readPatternOccurrences(p,null).count,null);
  assert.equal(readPatternOccurrences(p,[]).count,0);
});

test('pattern histogram uses Korean server time and preserves undated counts', () => {
  const p = operatorRiskPatterns[2];
  const rows = [
    {id:1,signals:p.signals,createdAt:'2026-10-22T12:00:00'},
    {id:2,signals:p.signals,createdAt:'2026-10-22T12:06:00+09:00'},
    {id:3,signals:p.signals,createdAt:'unknown'},
    {id:4,signals:['EXTERNAL_API_CALL'],createdAt:'2026-10-22T12:03:00'},
  ];
  const result = readPatternOccurrences(p,rows);
  assert.equal(result.count,3); assert.equal(result.undated,1);
  assert.equal(result.start,Date.parse('2026-10-22T03:00:00Z'));
  assert.deepEqual(result.bins,[1,0,0,0,0,1]);
  assert.equal(readPatternOccurrences(p,[rows[2]]).bins.length,0);
});
