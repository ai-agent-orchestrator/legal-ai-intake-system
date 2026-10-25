import test from 'node:test';
import assert from 'node:assert/strict';
import { analyzeMarketingJourneys, marketingDemoJourneys, marketingPercent } from '../src/operator/marketingDemo.js';

test('linked inquiry journeys produce the demonstrated funnel and consultation conversion', () => {
  const result = analyzeMarketingJourneys(marketingDemoJourneys);
  assert.deepEqual(result.stages.map(stage => stage.count), [40, 24, 12, 4]);
  assert.deepEqual(result.stages.map(stage => marketingPercent(stage.conversion)), ['—', '60%', '50%', '33.3%']);
  assert.equal(marketingPercent(result.consultationConversion), '33.3%');
  assert.deepEqual(result.reasons.map(({ label, count, share }) => [label, count, share]), [['비용 부담', 4, 50], ['분야 불일치', 2, 25], ['연락 두절', 2, 25]]);
  assert.equal(result.largestDecrease.from, '상담 완료');
  assert.equal(result.largestDecrease.notReached, 8);
  assert.equal(result.lostCount, 8);
  assert.equal(result.pendingCount, 0);
});
test('changing a consultation outcome changes conversion, reasons and recommendations input', () => {
  const records = structuredClone(marketingDemoJourneys);
  const item = records[4];
  item.outcome = 'RETAINED'; item.lossReason = null;
  item.events.push({ stage: 'RETAINED', at: item.outcomeAt });
  const result = analyzeMarketingJourneys(records);
  assert.equal(result.retainedCount, 5);
  assert.equal(marketingPercent(result.consultationConversion), '41.7%');
  assert.equal(result.lostCount, 7);
  assert.equal(result.reasons[0].count, 3);
});
test('empty denominators are unavailable, while observed zero conversion is zero', () => {
  const empty = analyzeMarketingJourneys([]);
  assert.equal(marketingPercent(empty.consultationConversion), '—');
  assert.equal(empty.largestDecrease, null);
  assert.deepEqual(empty.reasons, []);
  const submittedOnly = analyzeMarketingJourneys([marketingDemoJourneys[39]]);
  assert.equal(marketingPercent(submittedOnly.stages[1].conversion), '0%');
  assert.equal(marketingPercent(submittedOnly.stages[2].conversion), '—');
  assert.equal(submittedOnly.lostCount, 0);
});
test('duplicate stage events cannot inflate unique inquiries; duplicate inquiry IDs are rejected', () => {
  const record = structuredClone(marketingDemoJourneys[0]);
  record.events.push(record.events[1]);
  assert.deepEqual(analyzeMarketingJourneys([record]).stages.map(stage => stage.count), [1, 1, 1, 1]);
  assert.throws(() => analyzeMarketingJourneys([record, record]), /고유/);
});
test('pending consultation results are not counted as confirmed losses', () => {
  const record = structuredClone(marketingDemoJourneys[4]);
  record.outcome = 'PENDING'; record.lossReason = null; record.outcomeAt = null;
  const result = analyzeMarketingJourneys([record]);
  assert.equal(result.pendingCount, 1);
  assert.equal(result.lostCount, 0);
  assert.equal(result.transitions[2].notReached, 1);
  assert.deepEqual(result.reasons, []);
});
test('impossible progress and contradictory consultation outcomes are rejected', () => {
  const gap = structuredClone(marketingDemoJourneys[0]);
  gap.events = gap.events.filter(item => item.stage !== 'SEARCH_REQUESTED');
  assert.throws(() => analyzeMarketingJourneys([gap]), /이전 단계/);
  const mismatch = structuredClone(marketingDemoJourneys[0]);
  mismatch.outcome = 'LOST';
  assert.throws(() => analyzeMarketingJourneys([mismatch]), /결과와 진행/);
});
