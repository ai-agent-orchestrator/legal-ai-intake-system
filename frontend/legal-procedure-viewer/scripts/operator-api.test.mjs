import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import * as api from '../src/operator/operatorApi.js';
const emptyRisk = { total: 0, high: 0, medium: 0, low: 0, decisionCounts: {}, recommendedActionCounts: {} };
const emptySummary = { agentRunCount: 0, riskHistoryCount: 0, securityEventCount: 0, openIncidentCount: 0, riskSummary: emptyRisk };

async function withServer(handler, run) {
  const server = createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}`); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}

test('incident writes preserve the selected ID, use PATCH and omit a JSON body', async () => {
  const calls = [];
  await withServer(async (req, res) => {
    let body = ''; for await (const part of req) body += part;
    calls.push({ url: req.url, method: req.method, body, auth: req.headers.authorization });
    res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ id: 42, status: req.url.endsWith('ack') ? 'ACKNOWLEDGED' : 'RESOLVED' }));
  }, async base => {
    assert.equal((await api.ackIncident(base, 42)).status, 'ACKNOWLEDGED');
    assert.equal((await api.resolveIncident(base, 42)).status, 'RESOLVED');
    assert.deepEqual(calls, [
      { url: '/api/incidents/42/ack', method: 'PATCH', body: '', auth: undefined },
      { url: '/api/incidents/42/resolve', method: 'PATCH', body: '', auth: undefined },
    ]);
    assert.throws(() => api.ackIncident(base, '../wrong'), /유효한/);
  });
});

test('C16 sends Basic only for the metrics request; normal API calls do not inherit it', async () => {
  const calls = [];
  await withServer((req, res) => {
    calls.push([req.url, req.headers.authorization]);
    if (req.url === '/actuator/prometheus') res.end('ai_cost_actual_usd 0.012\n');
    else { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(emptySummary)); }
  }, async base => {
    assert.match(await api.getPrometheusMetrics(base, 'test-admin', 'test-password'), /0.012/);
    await api.getAdminDashboardSummary(base);
    assert.deepEqual(calls, [['/actuator/prometheus', `Basic ${btoa('test-admin:test-password')}`], ['/api/admin/dashboard/summary', undefined]]);
  });
});

test('filtered history uses a query and validates array responses', async () => {
  await withServer((req, res) => {
    assert.equal(req.url, '/api/agent/risk/history?riskLevel=HIGH');
    res.setHeader('Content-Type', 'application/json'); res.end('{}');
  }, async base => {
    await assert.rejects(api.getRiskHistory(base, undefined, 'riskLevel=HIGH'), /응답 형식/);
  });
});

test('401 and 403 remain distinct and expose a trace reference without a response dump', async () => {
  await withServer((req, res) => {
    res.statusCode = req.url.includes('incidents') ? 403 : 401;
    res.setHeader('X-Trace-Id', 'test-trace'); res.end('sensitive internal response');
  }, async base => {
    await assert.rejects(api.getRiskSummary(base), error => error.status === 401 && /인증/.test(error.message) && /test-trace/.test(error.message) && !/sensitive/.test(error.message));
    await assert.rejects(api.getIncidents(base), error => error.status === 403 && /접근 권한/.test(error.message));
  });
});

test('cancelled requests do not proceed as successful reads', async () => {
  await withServer(() => {}, async base => {
    const controller = new AbortController(); controller.abort();
    await assert.rejects(api.getRiskSummary(base, controller.signal), /취소/);
  });
});

test('Prometheus zero, absent, tagged samples and timer counts remain distinguishable', () => {
  const samples = api.parseMetrics('# HELP example\nai_cost_actual_usd 0\nai_chat_stage_duration_seconds_count{stage="llm"} 2\nai_chat_stage_duration_seconds_sum{stage="llm"} 1.5\nx NaN\nx +Inf\n');
  assert.equal(api.metricValue(samples, 'ai_cost_actual_usd'), 0);
  assert.equal(api.metricValue(samples, 'ai_cost_estimated_usage_usd'), null);
  assert.equal(api.metricValue(samples, 'ai_chat_stage_duration_seconds_sum', { stage: 'llm' }) / api.metricValue(samples, 'ai_chat_stage_duration_seconds_count', { stage: 'llm' }), 0.75);
  assert.equal(samples.length, 3);
  assert.throws(() => api.normalizeApiBase('https://user:secret@example.test'), /서버 주소/);
});

test('malformed successful C12/C13/C15 DTOs are recoverable API errors, while real zero totals remain valid', async () => {
  await withServer((req, res) => { res.setHeader('Content-Type', 'application/json'); res.end('{}'); }, async base => {
    for (const read of [api.getAdminDashboardSummary, api.getMarketingFunnel, api.getRiskSummary]) await assert.rejects(read(base), /응답 필드/);
  });
  await withServer((req, res) => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(emptySummary)); }, async base => {
    assert.deepEqual(await api.getAdminDashboardSummary(base), emptySummary);
  });
});

test('null list rows are rejected before security and audit components dereference them', async () => {
  await withServer((req, res) => { res.setHeader('Content-Type', 'application/json'); res.end('[null]'); }, async base => {
    for (const read of [api.getSecurityEvents, api.getRiskHistory, api.getIncidents]) await assert.rejects(read(base), /응답 형식/);
  });
});
