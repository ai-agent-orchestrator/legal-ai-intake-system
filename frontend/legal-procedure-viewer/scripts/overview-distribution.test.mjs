import test from 'node:test';
import assert from 'node:assert/strict';
import { buildOverviewDistribution } from '../src/operator/overviewDistribution.js';

test('empty successful maps retain four decision and five action rows at zero', () => {
  const decisions = buildOverviewDistribution({}, 'decision');
  const actions = buildOverviewDistribution({}, 'action');
  assert.deepEqual(decisions.map(row => row.label), ['위험 감지', '주의 필요', '정상', '추가 검토']);
  assert.deepEqual(actions.map(row => row.label), ['추가 검토', '모니터링', '차단 검토', '정상 처리', '기타']);
  assert.ok([...decisions, ...actions].every(row => row.count === 0));
});

test('all decision codes and unknown codes retain their counts without duplication', () => {
  const counts = { HIGH_RISK_AGENT_BEHAVIOR: 4, RISKY_EXTERNAL_ACCESS: 3, APPROVAL_BYPASS_RISK: 2, UNSTABLE_TOOL_LOOP: 5, SUSPICIOUS_RETRY: 6, SAFE: 7, SUSPICIOUS: 8, FUTURE_DECISION: 9 };
  const rows = buildOverviewDistribution(counts, 'decision');
  assert.deepEqual(rows.map(row => row.count), [9, 11, 7, 17]);
  assert.equal(rows.reduce((sum, row) => sum + row.count, 0), Object.values(counts).reduce((sum, count) => sum + count, 0));
});

test('blocking, tool disablement and Incident creation remain represented; monitoring is not fabricated', () => {
  const counts = { REQUIRE_APPROVAL: 2, BLOCK: 3, BLOCK_AND_ESCALATE: 4, ALLOW: 5, DISABLE_TOOL_TEMPORARILY: 6, CREATE_INCIDENT: 7, FUTURE_ACTION: 8 };
  const rows = buildOverviewDistribution(counts, 'action');
  assert.deepEqual(rows.map(row => row.count), [2, 0, 7, 5, 21]);
  assert.match(rows[1].title, /별도 모니터링 권고 코드가 없어/);
  assert.match(rows[4].title, /DISABLE_TOOL_TEMPORARILY/);
  assert.equal(rows.reduce((sum, row) => sum + row.count, 0), Object.values(counts).reduce((sum, count) => sum + count, 0));
});

test('an unavailable or invalid map is distinct from a successful empty map', () => {
  for (const value of [null, undefined, [], { SAFE: -1 }, { SAFE: '2' }]) {
    assert.ok(buildOverviewDistribution(value, 'decision').every(row => row.count === null));
  }
});
