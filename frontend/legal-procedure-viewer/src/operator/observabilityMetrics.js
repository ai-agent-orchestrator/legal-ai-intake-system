import { metricValue } from './operatorApi.js';

// These descriptions follow AgentRiskDecisionService / AgentRiskPatternService.
// Aggregated counters are evidence to inspect, never a replacement for a request decision.
export const behaviorMetrics = {
  policy: { name: 'agent_policy_violation_total', label: '정책 위반', color: '#eb687b' },
  retry: { name: 'agent_retry_count_total', label: '재시도', color: '#8272df' },
  tool: { name: 'agent_tool_calls_total', label: '도구 호출', color: '#367bf5' },
  external: { name: 'agent_external_api_calls_total', label: '외부 접근', color: '#1aab9b' },
  error: { name: 'agent_tool_errors_total', label: '도구 오류', color: '#d88c28' },
  approval: { name: 'agent_approval_required_total', label: '승인 필요', color: '#8794aa' },
};

export const behaviorPatterns = [
  { id: 'policy-retry', title: '차단된 행동의 반복 시도', combination: '정책 위반 + 재시도', keys: ['policy', 'retry'], signals: ['POLICY_VIOLATION', 'RETRY'], decision: 'SUSPICIOUS_RETRY', action: '차단 유지 · 재시도 원인 검토', meaning: '정책 위반과 재시도가 함께 증가하면, 차단된 위험 행동 주변을 AI Agent가 반복 시도하는 패턴을 의심할 수 있습니다.', inspect: '같은 요청의 정책 위반·재시도 신호와 Trace ID를 확인하세요. 더 강한 조합이 있으면 최종 판정이 달라질 수 있습니다.', featured: true },
  { id: 'policy-retry-tool', title: '도구를 통한 위험 행동 반복', combination: '도구 호출 + 정책 위반 + 재시도', keys: ['tool', 'policy', 'retry'], signals: [], decision: null, action: '도구 실행 흐름 · 차단 이후의 재호출 확인', meaning: '도구 호출, 정책 위반, 재시도가 함께 증가하면 단순 실패를 넘어 위험 행동을 도구로 반복하는 패턴의 후보입니다.', inspect: '세 지표의 동반 관측을 위한 조합입니다. 독립된 서버 판정 코드는 없으며, 같은 도구·Trace의 로그로 연결을 확인해야 합니다.', featured: true },
  { id: 'external-policy', title: '외부 전송 위험 후보', combination: '외부 접근 + 정책 위반', keys: ['external', 'policy'], signals: ['EXTERNAL_API_CALL', 'POLICY_VIOLATION'], decision: 'RISKY_EXTERNAL_ACCESS', action: '차단 · 상위 검토', meaning: '위험 요청이 외부 의존성 접근과 결합한 패턴입니다. 데이터 유출 또는 외부 전송 위험의 후보로 검토할 수 있습니다.', inspect: '실제 전송 성공이나 유출 확정을 뜻하지 않습니다. 시뮬레이션 지표도 포함하므로 호출 결과와 Trace를 함께 확인하세요.', featured: true },
  { id: 'error-retry', title: '불안정한 도구의 반복 호출', combination: '도구 오류 + 재시도', keys: ['error', 'retry'], signals: ['TOOL_ERROR', 'RETRY'], decision: 'UNSTABLE_TOOL_LOOP', action: '도구 일시 중지 검토', meaning: '실패하는 도구를 계속 호출하는 흐름입니다. 지연·비용 증가와 반복 실행을 확인해야 합니다.', inspect: '오류 유형과 재시도 한도를 확인하고, 대체 도구 또는 중지 경로를 검토하세요.' },
  { id: 'approval-retry', title: '사람의 승인 경계 반복 접근', combination: '승인 필요 + 재시도', keys: ['approval', 'retry'], signals: ['APPROVAL_REQUIRED', 'RETRY'], decision: 'APPROVAL_BYPASS_RISK', action: '명시적 승인 확인', meaning: '승인이 필요한 행동을 반복 시도하는 패턴입니다. 승인 조건을 우회하려는 흐름인지 확인해야 합니다.', inspect: '승인 기록과 실행 권한을 확인하세요. 지표 관측만으로 승인이나 작업 실행을 자동 수행하지 않습니다.' },
  { id: 'high-risk', title: '외부 접근을 동반한 반복 위험', combination: '정책 위반 + 외부 접근 + 재시도', keys: ['policy', 'external', 'retry'], signals: ['POLICY_VIOLATION', 'EXTERNAL_API_CALL', 'RETRY'], decision: 'HIGH_RISK_AGENT_BEHAVIOR', action: '차단 · 상위 검토', meaning: '정책 경계를 넘는 외부 접근을 재시도하는 조합입니다. 현재 서버에서 다른 조합보다 먼저 검사하는 위험 판정입니다.', inspect: '최종 위험 점수·권고 조치와 연결된 Incident를 화면 12에서 확인하세요.' },
  { id: 'external-policy-tool', title: '도구 활동과 외부 위험의 교차 관측', combination: '외부 접근 + 정책 위반 + 도구 호출', keys: ['external', 'policy', 'tool'], signals: [], decision: null, action: '외부 의존성 · 도구별 로그 검토', meaning: '외부 의존성 접근과 정책 위반이 도구 활동과 함께 나타난 상황을 살펴보는 조합입니다.', inspect: '기존 Observability 실험의 세 지표 조합입니다. 독립된 판정 코드나 실제 유출 여부를 이 누적값만으로 결정하지 않습니다.' },
];

export const dynamicMetrics = [
  { name: 'legal_ai_dynamic_risk_event_total', label: '누적 위험 이벤트', unit: '회', note: '세션 위험에 반영한 차단 사유' },
  { name: 'legal_ai_dynamic_block_total', label: '세션 차단 전이', unit: '회', note: '허용에서 차단으로 바뀐 횟수' },
  { name: 'legal_ai_dynamic_block_response_total', label: '차단 응답', unit: '회', note: '차단 전이와 이후 차단 응답 포함' },
  { name: 'legal_ai_session_max_risk_score', label: '최대 세션 위험 점수', unit: '점', note: '서버 메모리 내 세션 중 최댓값' },
];

export const dynamicScoreRules = [
  ['법률 위해', 'BLOCKED_LEGAL_HARM', 70],
  ['탈옥 · 프롬프트 주입', 'BLOCKED_JAILBREAK / BLOCKED_PROMPT_INJECTION', 60],
  ['비밀 · 개인정보 · 인증 우회 · 입력 Guard 차단', 'BLOCKED_SECRET_REQUEST / BLOCKED_PRIVACY_RISK / BLOCKED_AUTH_BYPASS / NEMO_INPUT_GUARDRAIL', 50],
  ['긴 입력 · 비용 제한', 'REQUEST_TOO_LONG / COST_LIMIT_BLOCKED', 35],
  ['범위 밖 주제', 'OUT_OF_SCOPE_TOPIC', 20],
  ['그 외 사유', '기본값', 25],
];

const counter = (samples, name) => {
  const value = metricValue(samples || [], name);
  return value != null && value >= 0 ? value : null;
};
const identity = sample => JSON.stringify(Object.entries(sample.labels).sort(([a], [b]) => a.localeCompare(b)));

export function counterDelta(current, previous, name) {
  if (!previous) return null;
  const start = metricValue(current, 'process_start_time_seconds');
  const previousStart = metricValue(previous, 'process_start_time_seconds');
  if (start !== previousStart) return null;
  const now = current.filter(sample => sample.name === name), before = previous.filter(sample => sample.name === name);
  if (!now.length || now.length !== before.length) return null;
  const old = new Map(before.map(sample => [identity(sample), sample.value]));
  let delta = 0;
  for (const sample of now) {
    const value = old.get(identity(sample));
    if (value == null || sample.value < value || sample.value < 0) return null;
    delta += sample.value - value;
  }
  return delta;
}

export function readBehaviorPattern(pattern, samples, previous) {
  const metrics = pattern.keys.map(key => ({ ...behaviorMetrics[key], value: counter(samples, behaviorMetrics[key].name), delta: counterDelta(samples || [], previous, behaviorMetrics[key].name) }));
  const complete = metrics.every(metric => metric.value != null);
  const increased = complete && metrics.every(metric => metric.delta != null && metric.delta > 0);
  const observed = complete && metrics.every(metric => metric.value > 0);
  return { metrics, increased, status: !complete ? '지표 미관측' : increased ? '수집 구간 동반 증가' : observed ? '누적 신호 존재' : '동반 신호 없음' };
}

export function relatedHistory(pattern, rows) {
  if (!pattern.signals.length) return [];
  return (rows || []).filter(row => Array.isArray(row.signals) && pattern.signals.every(signal => row.signals.includes(signal)));
}

export function patternPromql(pattern) {
  return pattern.keys.map(key => `increase(${behaviorMetrics[key].name}[5m])`).join('\nor\n');
}

export const readDynamicMetrics = samples => dynamicMetrics.map(metric => ({ ...metric, value: counter(samples, metric.name) }));

export const REQUEST_BLOCK_SCORE = 80;
export const operatorRiskPatterns = [
  { id: 'blocked-retry', title: '차단 행동 반복 시도', tone: 'blue', keys: ['policy', 'retry'], signals: ['POLICY_VIOLATION', 'RETRY'] },
  { id: 'dangerous-retry', title: '위험 행동 반복', tone: 'green', keys: ['tool', 'error', 'retry'], signals: ['TOOL_ERROR', 'RETRY'] },
  { id: 'external-retry', title: '외부 전송 위험 재시도', tone: 'red', keys: ['external', 'policy', 'tool', 'retry'], signals: ['EXTERNAL_API_CALL', 'RETRY'] },
];

// Count matched history records, never add independent Prometheus counters or
// join signals across rows. The API returns a recent cohort, not lifetime totals.
export function readPatternOccurrences(pattern, rows) {
  if (!Array.isArray(rows)) return { count: null, sourceCount: null, bins: [], start: null, end: null, undated: 0 };
  const unique = new Map();
  for (const row of rows) {
    if (row && /^[1-9]\d*$/.test(String(row.id)) && Number.isSafeInteger(Number(row.id)) && !unique.has(String(row.id))) unique.set(String(row.id), row);
  }
  const cohort = [...unique.values()];
  const matched = relatedHistory(pattern, cohort);
  const time = row => {
    const text = row.createdAt;
    if (typeof text !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})?$/.test(text)) return null;
    // LocalDateTime from the server is Korean local time, not the viewer's zone.
    const value = Date.parse(/[Zz]$|[+-]\d{2}:\d{2}$/.test(text) ? text : text + '+09:00');
    return Number.isFinite(value) ? value : null;
  };
  const times = cohort.map(time).filter(value => value != null);
  const datedMatches = matched.map(time).filter(value => value != null);
  if (!times.length || (matched.length && !datedMatches.length)) return { count: matched.length, sourceCount: cohort.length, bins: [], start: null, end: null, undated: matched.length };
  const end = Math.max(...times), start = Math.min(Math.min(...times), end - 60000);
  const bins = Array(6).fill(0);
  for (const value of datedMatches) bins[Math.min(5, Math.floor((value - start) / (end - start) * 6))]++;
  return { count: matched.length, sourceCount: cohort.length, bins, start, end, undated: matched.length - datedMatches.length };
}

// Only a single matching history supplies a score; counters and unrelated histories are never added.
export function readPatternRisk(pattern, rows) {
  const row = relatedHistory(pattern, rows).find(row => Number.isInteger(row.riskScore) && row.riskScore >= 0 && row.riskScore <= 100);
  if (!row) return { row: null, total: null, behaviorPoints: null, retryPoints: null, block: false };
  const detail = row.scoreBreakdown;
  const complete = detail && Number.isInteger(detail.behaviorPoints) && detail.behaviorPoints >= 0
    && Number.isInteger(detail.retryPoints) && detail.retryPoints >= 0 && detail.retryPoints <= 25
    && detail.total === row.riskScore && Math.min(detail.behaviorPoints + detail.retryPoints, 100) === row.riskScore;
  return { row, total: row.riskScore, behaviorPoints: complete ? detail.behaviorPoints : null,
    retryPoints: complete ? detail.retryPoints : null, block: row.riskScore >= REQUEST_BLOCK_SCORE };
}
