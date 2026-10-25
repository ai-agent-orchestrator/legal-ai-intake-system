// Synthetic journeys only. These records never enter C13 or the application DB.
export const marketingDemoPeriod = {
  cohort: '2026.10.01–10.07 접수', asOf: '2026.10.25',
};
export const marketingDemoStages = [
  { key: 'SUBMITTED', label: '문의 접수' },
  { key: 'SEARCH_REQUESTED', label: '변호사 검색' },
  { key: 'CONSULTATION_COMPLETED', label: '상담 완료' },
  { key: 'RETAINED', label: '수임 확정' },
];
const reasonLabels = { COST: '비용 부담', SPECIALTY: '분야 불일치', NO_RESPONSE: '연락 두절' };
const at = (day, hour = 9) => new Date(Date.UTC(2026, 9, day, hour)).toISOString();

export const marketingDemoJourneys = Array.from({ length: 40 }, (_, index) => {
  const day = 1 + index % 7;
  const events = [{ stage: 'SUBMITTED', at: at(day) }];
  if (index < 24) events.push({ stage: 'SEARCH_REQUESTED', at: at(day + 1) });
  if (index < 12) events.push({ stage: 'CONSULTATION_COMPLETED', at: at(day + 2) });
  if (index < 4) events.push({ stage: 'RETAINED', at: at(day + 3) });
  return {
    intakeId: `DEMO-${String(index + 1).padStart(3, '0')}`,
    events,
    outcome: index < 4 ? 'RETAINED' : index < 12 ? 'LOST' : 'PENDING',
    outcomeAt: index < 12 ? at(day + 3) : null,
    lossReason: index >= 4 && index < 8 ? 'COST' : index >= 8 && index < 10 ? 'SPECIALTY' : index >= 10 && index < 12 ? 'NO_RESPONSE' : null,
  };
});

export const marketingPercent = value => value == null ? '—' : `${Number(value.toFixed(1))}%`;
const percent = (numerator, denominator) => denominator ? numerator / denominator * 100 : null;

export function analyzeMarketingJourneys(journeys) {
  const ids = new Set();
  const counts = marketingDemoStages.map(() => 0);
  const reasonCounts = new Map();
  let lost = 0;
  for (const record of journeys) {
    if (!record.intakeId || ids.has(record.intakeId)) throw new Error('문의 ID는 고유해야 합니다.');
    ids.add(record.intakeId);
    const reached = new Set(record.events.map(event => event.stage));
    if (!reached.has('SUBMITTED')) throw new Error('접수 기록이 없는 문의입니다.');
    if (!['RETAINED', 'LOST', 'PENDING'].includes(record.outcome)
        || (record.outcome === 'RETAINED') !== reached.has('RETAINED')) {
      throw new Error('상담 결과와 진행 단계가 다릅니다.');
    }
    let previousReached = true;
    marketingDemoStages.forEach(({ key }, index) => {
      const hasStage = reached.has(key);
      if (hasStage && !previousReached) throw new Error('이전 단계 없는 전환 기록입니다.');
      if (hasStage) counts[index]++;
      previousReached = hasStage;
    });
    if (record.outcome === 'LOST') {
      if (!reached.has('CONSULTATION_COMPLETED') || reached.has('RETAINED')) throw new Error('상담 결과와 진행 단계가 다릅니다.');
      lost++;
      const label = reasonLabels[record.lossReason] || '기타·미기재';
      reasonCounts.set(label, (reasonCounts.get(label) || 0) + 1);
    }
  }
  const stages = marketingDemoStages.map((stage, index) => ({
    ...stage, count: counts[index], share: percent(counts[index], counts[0]),
    conversion: index ? percent(counts[index], counts[index - 1]) : null,
  }));
  const transitions = stages.slice(1).map((stage, index) => ({
    from: stages[index].label, to: stage.label,
    notReached: counts[index] - stage.count,
    decrease: percent(counts[index] - stage.count, counts[index]),
  }));
  const largestDecrease = transitions.filter(item => item.decrease != null)
    .reduce((largest, item) => !largest || item.decrease > largest.decrease ? item : largest, null);
  return {
    stages, transitions, largestDecrease,
    consultationCount: counts[2], retainedCount: counts[3], lostCount: lost,
    pendingCount: counts[2] - counts[3] - lost,
    consultationConversion: percent(counts[3], counts[2]),
    reasons: [...reasonCounts].map(([label, count]) => ({ label, count, share: percent(count, lost) }))
      .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label, 'ko')),
  };
}
