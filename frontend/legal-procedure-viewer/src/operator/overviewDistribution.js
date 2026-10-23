const decisionGroups = [
  { id: 'risk', label: '위험 감지', codes: ['HIGH_RISK_AGENT_BEHAVIOR', 'RISKY_EXTERNAL_ACCESS', 'APPROVAL_BYPASS_RISK'] },
  { id: 'attention', label: '주의 필요', codes: ['UNSTABLE_TOOL_LOOP', 'SUSPICIOUS_RETRY'] },
  { id: 'normal', label: '정상', codes: ['SAFE'] },
  { id: 'review', label: '추가 검토', codes: ['SUSPICIOUS'], fallback: true },
];

const actionGroups = [
  { id: 'review', label: '추가 검토', codes: ['REQUIRE_APPROVAL'] },
  // The draft includes this row. No current RecommendedAction code represents monitoring.
  { id: 'monitoring', label: '모니터링', codes: [], note: '현재 서버에 별도 모니터링 권고 코드가 없어 0건으로 표시합니다.' },
  { id: 'block', label: '차단 검토', codes: ['BLOCK', 'BLOCK_AND_ESCALATE'] },
  { id: 'normal', label: '정상 처리', codes: ['ALLOW'] },
  { id: 'other', label: '기타', codes: ['DISABLE_TOOL_TEMPORARILY', 'CREATE_INCIDENT'], fallback: true },
];

// Display groups only: preserve every server count exactly once and keep empty rows visible.
export function buildOverviewDistribution(values, kind) {
  const groups = kind === 'decision' ? decisionGroups : actionGroups;
  const available = values != null && typeof values === 'object' && !Array.isArray(values)
    && Object.values(values).every(count => Number.isFinite(count) && count >= 0);
  const rows = groups.map(group => ({ ...group, count: available ? 0 : null, details: [] }));
  if (available) {
    for (const [code, count] of Object.entries(values)) {
      const row = rows.find(group => group.codes.includes(code)) || rows.find(group => group.fallback);
      row.count += count;
      row.details.push(`${code}: ${count}건`);
    }
  }
  return rows.map(({ id, label, count, codes, details, note }) => ({
    id, label, count,
    title: !available ? '집계값을 불러오지 못했습니다.' : note || (details.length ? details.join(' / ') : codes.join(' / ')),
  }));
}
