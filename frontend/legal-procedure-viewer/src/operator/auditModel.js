import { ackIncident, resolveIncident } from './operatorApi.js';

export const riskLevels = { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' };
export const decisions = {
  SAFE: '정상', UNSTABLE_TOOL_LOOP: '도구 반복 불안정', SUSPICIOUS_RETRY: '의심스러운 재시도',
  RISKY_EXTERNAL_ACCESS: '위험한 외부 접근', APPROVAL_BYPASS_RISK: '승인 우회 위험',
  HIGH_RISK_AGENT_BEHAVIOR: '고위험 Agent 행동', SUSPICIOUS: '추가 검토 필요',
};
export const recommendedActions = {
  ALLOW: '정상 처리', BLOCK: '차단', REQUIRE_APPROVAL: '승인 필요',
  DISABLE_TOOL_TEMPORARILY: '도구 일시 중지', BLOCK_AND_ESCALATE: '차단·상위 검토', CREATE_INCIDENT: 'Incident 생성',
};
export const incidentStatuses = { OPEN: '미확인', ACKNOWLEDGED: '확인됨', RESOLVED: '해결됨' };
export const riskSignals = {
  POLICY_VIOLATION: '정책 위반', RETRY: '재시도 발생', EXTERNAL_API_CALL: '외부 API 호출',
  TOOL_ERROR: '도구 오류', APPROVAL_REQUIRED: '승인 필요', DB_WRITE: 'DB 쓰기 시도', SAFE_REQUEST: '정상 요청',
};
export const displayCode = (names, value) => names[value] || value || '—';
export const riskId = id => id == null ? '—' : `RH-${String(id).padStart(5, '0')}`;
export const incidentId = id => id == null ? '—' : `INC-${String(id).padStart(5, '0')}`;
export function riskQuery(mode, value) {
  if (value === 'ALL') return '';
  const options = mode === 'riskLevel' ? riskLevels : mode === 'decision' ? decisions : null;
  if (!options || !Object.hasOwn(options, value)) throw new Error('유효한 위험 이력 조회 조건을 선택하세요.');
  // The server prioritizes riskLevel, so deliberately send only one condition.
  return new URLSearchParams({ [mode]: value }).toString();
}
export function filterIncidents(rows, { status = 'ALL', severity = 'ALL' }) {
  return rows.filter(row => (status === 'ALL' || row.status === status) && (severity === 'ALL' || row.severity === severity));
}
export function canChangeIncident(incident, action) {
  if (!incident || !/^[1-9]\d*$/.test(String(incident.id))) return false;
  return action === 'ack' ? incident.status === 'OPEN' : action === 'resolve' && ['OPEN', 'ACKNOWLEDGED'].includes(incident.status);
}
export async function changeIncidentAndReload({ base, incident, action, signal, refreshIncidents, refreshSummary }) {
  if (!canChangeIncident(incident, action)) throw new Error('최신 Incident 상태를 확인한 후 처리하세요.');
  let saved = false, error = null, failure = null;
  try {
    const result = await (action === 'ack' ? ackIncident : resolveIncident)(base, incident.id, signal);
    if (String(result.id) !== String(incident.id) || result.status !== (action === 'ack' ? 'ACKNOWLEDGED' : 'RESOLVED')) {
      throw new Error('상태 변경 응답의 ID 또는 상태가 요청과 다릅니다.');
    }
    saved = true;
  } catch (cause) { error = cause.message; failure = cause; }
  if (signal?.aborted) return { cancelled: true };
  // A lost PATCH response may follow a committed write. Read both views, never replay PATCH.
  const reads = await Promise.allSettled([refreshIncidents(), refreshSummary()]);
  if (signal?.aborted) return { cancelled: true };
  const result = { saved, error, refreshed: reads.every(read => read.status === 'fulfilled' && read.value?.ok),
    incidentsRefreshed: reads[0].status === 'fulfilled' && reads[0].value?.ok === true,
    summaryRefreshed: reads[1].status === 'fulfilled' && reads[1].value?.ok === true };
  if (failure?.status) result.status = failure.status;
  if (failure?.code) result.code = failure.code;
  return result;
}
