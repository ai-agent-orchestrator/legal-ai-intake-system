export function normalizeApiBase(value) {
  const url = new URL(value.trim());
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
    throw new Error('사용자 정보·쿼리가 없는 HTTP(S) 서버 주소를 입력하세요.');
  }
  return url.href.replace(/\/+$/, '');
}

async function request(base, path, { signal, method = 'GET', authorization, text = false, list = false } = {}) {
  const controller = new AbortController();
  const abort = () => controller.abort();
  signal?.addEventListener('abort', abort, { once: true });
  if (signal?.aborted) controller.abort();
  const timeout = setTimeout(abort, 15000);
  try {
    const response = await fetch(`${base}${path}`, {
      method, signal: controller.signal, cache: 'no-store',
      headers: { Accept: text ? 'text/plain' : 'application/json', ...(authorization ? { Authorization: authorization } : {}) },
    });
    if (!response.ok) {
      const hint = response.status === 401 ? '인증 정보를 확인하세요.' : response.status === 403 ? '접근 권한을 확인하세요.' : '서버 응답을 확인하세요.';
      const trace = response.headers.get('X-Trace-Id');
      const error = new Error(`HTTP ${response.status} · ${hint}${trace ? ` (Trace: ${trace})` : ''}`);
      error.status = response.status;
      throw error;
    }
    if (text) {
      const body = await response.text();
      if (/^\s*</.test(body)) throw new Error('Prometheus 텍스트 대신 HTML이 반환되었습니다. 서버 주소를 확인하세요.');
      return body;
    }
    const body = await response.json();
    if (!body || (list ? !Array.isArray(body) || body.some(row => !isRecord(row)) : !isRecord(body))) {
      throw new Error('예상한 API 응답 형식과 다릅니다. 서버 주소와 응답을 확인하세요.');
    }
    return body;
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('요청이 취소되었거나 15초 대기 시간을 넘었습니다.');
    if (error instanceof TypeError) throw new Error('서버 연결 실패 · 서버 실행 상태와 주소, CORS 설정을 확인하세요.');
    throw error;
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener('abort', abort);
  }
}

const isRecord = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const isCount = value => Number.isSafeInteger(value) && value >= 0;
const countMap = value => isRecord(value) && Object.values(value).every(isCount);
const counts = (value, keys) => isRecord(value) && keys.every(key => isCount(value[key]));
const riskSummary = value => counts(value, ['total', 'high', 'medium', 'low']) && countMap(value.decisionCounts) && countMap(value.recommendedActionCounts);
function checked(value, valid) {
  if (!valid) throw new Error('API 응답 필드가 누락되었거나 형식이 다릅니다. 다시 조회해 주세요.');
  return value;
}
export const getAdminDashboardSummary = async (base, signal) => {
  const value = await request(base, '/api/admin/dashboard/summary', { signal });
  return checked(value, counts(value, ['agentRunCount', 'riskHistoryCount', 'securityEventCount', 'openIncidentCount']) && riskSummary(value.riskSummary));
};
export const getMarketingFunnel = async (base, signal) => {
  const value = await request(base, '/api/admin/dashboard/marketing-funnel', { signal });
  return checked(value, counts(value, ['submittedIntakeCount', 'searchRequestCount', 'searchRequestedIntakeCount', 'consultationReadyIntakeCount']) && Array.isArray(value.stages)
    && value.stages.every(stage => isRecord(stage) && typeof stage.stage === 'string' && isCount(stage.count) && typeof stage.measurement === 'string')
    && ['caseTypeDistribution', 'desiredNextStepDistribution', 'regionDistribution', 'eventCounts'].every(key => countMap(value[key]))
    && typeof value.measurementBasis === 'string' && typeof value.dropOffMeasured === 'boolean');
};
export const getSecurityEvents = (base, signal) => request(base, '/api/admin/dashboard/security-events', { signal, list: true });
export const getRiskSummary = async (base, signal) => {
  const value = await request(base, '/api/agent/risk/summary', { signal });
  return checked(value, riskSummary(value));
};
// query uses one server filter at a time because riskLevel takes precedence over decision.
export const getRiskHistory = (base, signal, query = '') => request(base, `/api/agent/risk/history${query ? `?${query}` : ''}`, { signal, list: true });
export const getIncidents = (base, signal) => request(base, '/api/incidents', { signal, list: true });
function incidentPath(id, action) {
  if (!/^[1-9]\d*$/.test(String(id))) throw new Error('목록에서 유효한 Incident를 선택하세요.');
  return `/api/incidents/${encodeURIComponent(id)}/${action}`;
}
export const ackIncident = (base, id, signal) => request(base, incidentPath(id, 'ack'), { method: 'PATCH', signal });
export const resolveIncident = (base, id, signal) => request(base, incidentPath(id, 'resolve'), { method: 'PATCH', signal });
const metricsAuthorization = (username, password) => {
  const bytes = new TextEncoder().encode(`${username}:${password}`);
  return `Basic ${btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(''))}`;
};
export const getPrometheusMetrics = (base, username, password, signal) => {
  return request(base, '/actuator/prometheus', { authorization: metricsAuthorization(username, password), text: true, signal });
};
export const getGuardTimeSeries = async (base, username, password, signal) => {
  const value = await request(base, '/actuator/guardtimeseries', { authorization: metricsAuthorization(username, password), signal });
  return checked(value, Number.isFinite(value.start) && Number.isFinite(value.end) && value.end > value.start
    && value.stepSeconds === 15 && value.windowSeconds === 300 && typeof value.query === 'string'
    && Array.isArray(value.warnings) && Array.isArray(value.series) && value.series.length <= 128
    && value.series.every(row => isRecord(row.metric) && typeof row.metric.signal === 'string'
      && Array.isArray(row.values) && row.values.length <= 241 && row.values.every(point => Array.isArray(point)
        && point.length === 2 && Number.isFinite(point[0]) && typeof point[1] === 'string')));
};

// Prometheus samples only; comments and non-finite values are not dashboard values.
export function parseMetrics(source) {
  return source.split('\n').flatMap(line => {
    const match = line.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(.*)\})?\s+([^\s]+)(?:\s+.*)?$/);
    if (!match || !Number.isFinite(Number(match[3]))) return [];
    const labels = Object.fromEntries(Array.from((match[2] || '').matchAll(/(\w+)="((?:\\.|[^"\\])*)"/g), value => [value[1], value[2]]));
    return [{ name: match[1], labels, value: Number(match[3]) }];
  });
}

export function metricValue(samples, name, labels = {}) {
  const matched = samples.filter(sample => sample.name === name && Object.entries(labels).every(([key, value]) => sample.labels[key] === value));
  return matched.length ? matched.reduce((sum, sample) => sum + sample.value, 0) : null;
}
