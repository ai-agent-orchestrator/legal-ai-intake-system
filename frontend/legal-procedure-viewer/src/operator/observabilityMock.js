// Explicit opt-in preview only. Never saved, sent to Actuator or used for billing.
export function createObservabilityMock(now = Date.now()) {
  const end = Math.floor(now / 15000) * 15, start = end - 3600;
  const shapes = {
    policy: n => n >= 55 && n < 83 ? 4 : n >= 150 && n < 172 ? 2 : 0,
    retry: n => n >= 55 && n < 83 ? 6 : n >= 108 && n < 133 ? 3 : n >= 150 && n < 172 ? 4 : 0,
    tool: n => n >= 55 && n < 83 ? 7 : n >= 108 && n < 133 ? 4 : n >= 150 && n < 172 ? 5 : 0,
    error: n => n >= 108 && n < 133 ? 2 : 0,
    external: n => n >= 150 && n < 172 ? 3 : 0,
  };
  const series = Object.entries(shapes).map(([signal, shape]) => ({
    metric: { signal, job: 'preview-only' },
    values: Array.from({ length: 241 }, (_, i) => [start + i * 15, String(shape(i))]),
  }));
  series.push({ metric: { signal: 'scrape_up' }, values: [[end, '1']] });
  const row = (id, signals, riskScore, behaviorPoints, retryPoints, recommendedAction) => ({
    id, signals, riskScore, scoreBreakdown: { behaviorPoints, retryPoints, total: riskScore },
    recommendedAction, createdAt: new Date(now).toISOString(),
  });
  return {
    timeline: { status: 'ok', at: new Date(now), data: { start, end, stepSeconds: 15, windowSeconds: 300, query: 'MOCK_ONLY', warnings: [], series } },
    history: { status: 'ok', data: [
      row(1, ['POLICY_VIOLATION', 'EXTERNAL_API_CALL', 'RETRY'], 81, 65, 16, 'BLOCK_AND_ESCALATE'),
      row(2, ['TOOL_ERROR', 'RETRY'], 31, 15, 16, 'DISABLE_TOOL_TEMPORARILY'),
    ] },
    metrics: [
      { name: 'legal_ai_session_max_risk_score', value: 120, labels: {} },
      { name: 'legal_ai_dynamic_risk_event_total', value: 3, labels: {} },
      { name: 'legal_ai_dynamic_block_total', value: 1, labels: {} },
    ],
  };
}
