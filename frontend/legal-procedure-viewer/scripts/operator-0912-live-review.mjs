// Disposable LOCAL API review, with synthetic DB records. No external LLM/search calls.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import * as api from '../src/operator/operatorApi.js';
import { canChangeIncident, changeIncidentAndReload, riskQuery } from '../src/operator/auditModel.js';
import { readSecurityMetrics } from '../src/operator/securityMetrics.js';
const base = 'http://127.0.0.1:18087';
const output = new URL('../../../output/operator-09-12-api-review/', import.meta.url);
const calls = [], checks = [], snapshots = {};
async function raw(name, path, { method = 'GET', body, authorization, expected = 200, headers = {} } = {}) {
  const response = await fetch(base + path, { method, headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(authorization ? { Authorization: authorization } : {}), ...headers }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  const text = await response.text();
  calls.push({ name, method, path, status: response.status, bodyFieldNames: body === undefined ? [] : Object.keys(body), authentication: !authorization ? 'none' : authorization.startsWith('Basic ') ? 'Basic' : 'Bearer' });
  assert.equal(response.status, expected, name);
  return { response, data: text && response.headers.get('content-type')?.includes('json') ? JSON.parse(text) : text };
}
async function check(name, run) { await run(); checks.push({ name, pass: true }); console.log('PASS', name); }
const fields = (value, names) => { for (const name of names) assert.ok(Object.hasOwn(value, name), 'Missing field: ' + name); };
const riskFields = ['total','high','medium','low','decisionCounts','recommendedActionCounts'];
const incidentFields = ['id','incidentType','severity','status','decision','riskScore','recommendedAction','riskHistoryId','traceId','message','createdAt','updatedAt'];
const originalFetch = globalThis.fetch;
// Observe the actual React request layer without writing credentials or request bodies to evidence.
globalThis.fetch = async (url, init) => {
  const response = await originalFetch(url, init);
  if (init?.cache === 'no-store') calls.push({ name: 'React API function', method: init.method, path: new URL(url).pathname + new URL(url).search, status: response.status, bodyPresent: init.body != null, authentication: init.headers?.Authorization ? 'Basic' : 'none' });
  return response;
};
let ownerToken, adminToken, incident, high, before;
await check('C12-C15/C17/C18 accept anonymous reads and complete empty DTOs', async () => {
  before = await api.getAdminDashboardSummary(base); fields(before, ['agentRunCount','riskHistoryCount','securityEventCount','openIncidentCount','riskSummary']); fields(before.riskSummary, riskFields);
  assert.equal(before.agentRunCount, 0); assert.equal(before.openIncidentCount, 0);
  const funnel = await api.getMarketingFunnel(base); fields(funnel, ['submittedIntakeCount','searchRequestCount','searchRequestedIntakeCount','consultationReadyIntakeCount','stages','caseTypeDistribution','desiredNextStepDistribution','regionDistribution','measurementBasis','dropOffMeasured','eventCounts']); assert.equal(funnel.stages.length, 6);
  assert.deepEqual(await api.getSecurityEvents(base), []); assert.deepEqual(await api.getRiskHistory(base), []); assert.deepEqual(await api.getIncidents(base), []); fields(await api.getRiskSummary(base), riskFields);
  snapshots.empty = { before, funnel };
});
await check('C16 rejects anonymous, USER Basic and ADMIN JWT; accepts ADMIN Basic text', async () => {
  ownerToken = (await raw('login USER', '/api/auth/login', { method: 'POST', body: { username: 'user', password: 'user123' } })).data.accessToken;
  adminToken = (await raw('login ADMIN', '/api/auth/login', { method: 'POST', body: { username: 'admin', password: 'admin123' } })).data.accessToken;
  await raw('C16 anonymous', '/actuator/prometheus', { expected: 401 });
  await raw('C16 USER Basic', '/actuator/prometheus', { authorization: 'Basic ' + btoa('user:user123'), expected: 403 });
  await raw('C16 ADMIN JWT', '/actuator/prometheus', { authorization: 'Bearer ' + adminToken, expected: 401 });
  const metrics = await api.getPrometheusMetrics(base, 'admin', 'admin123'); const samples = api.parseMetrics(metrics);
  assert.ok(samples.length > 0); const read = readSecurityMetrics(samples); assert.equal(read.cost[0].value, 0); assert.ok(read.budgets.every(item => item.value > 0));
  snapshots.metrics = { contentType: 'Prometheus text', sampleCount: samples.length, dashboardValues: read };
});
await check('C14 stores auth events and C12 matches the actual security count', async () => {
  const events = await api.getSecurityEvents(base); assert.equal(events.length, 3);
  for (const event of events) fields(event, ['id','eventType','method','uri','status','username','createdAt']);
  assert.equal(events.filter(e => e.eventType === 'AUTH_FAILURE').length, 2); assert.equal(events.filter(e => e.eventType === 'ACCESS_DENIED').length, 1);
  assert.equal((await api.getAdminDashboardSummary(base)).securityEventCount, before.securityEventCount + 3); snapshots.securityEvents = events;
});
await check('C13 counts stored inquiries, requests, unique inquiries, current status and deduplicated events', async () => {
  const intake = (await raw('seed inquiry', '/api/intakes', { method: 'POST', body: { sessionId: 'synthetic-review', inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고', coreStatement: '운영자 API 검수용 가상 대여금 문의입니다.', desiredNextStep: '절차 안내', preferredRegion: '서울특별시' }, expected: 201 })).data;
  for (let i = 0; i < 2; i++) await raw('seed search request', `/api/intakes/${intake.id}/search-requests`, { method: 'POST', body: {}, expected: 201 });
  await raw('seed consultation status', `/api/intakes/${intake.id}/status`, { method: 'PATCH', body: { status: 'CONSULTATION_READY' } });
  for (const eventType of ['VISIT','INTAKE_STARTED','SUMMARY_VIEWED']) {
    const body = { eventId: 'operator-api-review-' + eventType, eventType, sessionId: 'synthetic-review', ...(eventType === 'SUMMARY_VIEWED' ? { intakeId: intake.id } : {}) };
    await raw('seed event', '/api/intake-events', { method: 'POST', body, expected: 201 });
    const duplicate = await raw('deduplicate event', '/api/intake-events', { method: 'POST', body }); assert.equal(duplicate.data.created, false);
  }
  const funnel = await api.getMarketingFunnel(base);
  assert.equal(funnel.submittedIntakeCount, 1); assert.equal(funnel.searchRequestCount, 2); assert.equal(funnel.searchRequestedIntakeCount, 1); assert.equal(funnel.consultationReadyIntakeCount, 1);
  assert.equal(funnel.measurementBasis, 'ALL_RECORDS_IN_CURRENT_DATABASE'); assert.equal(funnel.dropOffMeasured, false);
  for (const eventType of ['VISIT','INTAKE_STARTED','SUMMARY_VIEWED']) assert.equal(funnel.eventCounts[eventType], 1);
  for (const stage of funnel.stages) fields(stage, ['stage','count','measurement']);
  assert.equal(funnel.caseTypeDistribution.CIVIL, 1); assert.equal(funnel.regionDistribution['서울특별시'], 1); snapshots.marketing = funnel;
});
await check('C12/C15 risk counts and C18 link an actual Incident ID distinct from historyId', async () => {
  await raw('seed low analysis', '/api/agent/risk/analyze', { method: 'POST', body: { userInput: 'SYNTHETIC_LOW_INPUT', toolName: 'qa_local' } });
  high = (await raw('seed high analysis', '/api/agent/risk/analyze', { method: 'POST', body: { userInput: 'SYNTHETIC_PRIVATE_MARKER_010-1234-5678', toolName: 'qa_local', policyViolation: true, externalApiCall: true, retryCount: 3 } })).data;
  await raw('seed medium analysis', '/api/agent/risk/analyze', { method: 'POST', body: { userInput: 'SYNTHETIC_MEDIUM_INPUT', toolName: 'qa_local', policyViolation: true } });
  const risk = await api.getRiskSummary(base); assert.equal(risk.total, 3); assert.equal(risk.high, 1); assert.equal(risk.medium, 1); assert.equal(risk.low, 1);
  const summary = await api.getAdminDashboardSummary(base); assert.equal(summary.agentRunCount, 3); assert.equal(summary.riskHistoryCount, 3); assert.equal(summary.openIncidentCount, 1); assert.deepEqual(summary.riskSummary, risk);
  const list = await api.getIncidents(base); assert.equal(list.length, 1); incident = list[0]; fields(incident, incidentFields); assert.equal(incident.riskHistoryId, high.historyId); assert.notEqual(incident.id, high.historyId); assert.equal(incident.traceId, high.traceId);
  snapshots.riskSummary = risk; snapshots.summaryBefore = summary; snapshots.incidentBefore = incident;
});
await check('C17 filters use actual enum values, riskLevel takes precedence, raw userInput is public', async () => {
  const all = await api.getRiskHistory(base); assert.equal(all.length, 3);
  for (const row of all) fields(row, ['id','userInput','toolName','decision','riskScore','riskLevel','signals','recommendedAction','guardrailReady','traceId','createdAt']);
  const filtered = await api.getRiskHistory(base, undefined, riskQuery('riskLevel','HIGH')); assert.equal(filtered.length, 1); assert.equal(filtered[0].id, high.historyId); assert.equal(filtered[0].userInput, 'SYNTHETIC_PRIVATE_MARKER_010-1234-5678');
  const decision = await api.getRiskHistory(base, undefined, riskQuery('decision',high.decision)); assert.ok(decision.every(row => row.decision === high.decision));
  const precedence = await raw('riskLevel precedence', '/api/agent/risk/history?riskLevel=LOW&decision=' + encodeURIComponent(high.decision)); assert.ok(precedence.data.length > 0 && precedence.data.every(row => row.riskLevel === 'LOW'));
  const invalid = await raw('unsupported server filter', '/api/agent/risk/history?riskLevel=NOT_A_LEVEL'); assert.deepEqual(invalid.data, []);
  const user = await raw('C12 USER JWT allowed', '/api/admin/dashboard/summary', { authorization: 'Bearer ' + ownerToken }); assert.equal(user.data.agentRunCount, 3);
  snapshots.publicRawHistory = filtered; snapshots.filterPrecedence = precedence.data;
});
await check('C19/C20 React workflow sends real Incident ID, no body, then rereads C18/C12', async () => {
  const refreshIncidents = async () => ({ ok: true, data: await api.getIncidents(base) });
  const refreshSummary = async () => ({ ok: true, data: await api.getAdminDashboardSummary(base) });
  const from = calls.length;
  for (const action of ['ack','resolve']) {
    const result = await changeIncidentAndReload({ base, incident, action, refreshIncidents, refreshSummary }); assert.deepEqual(result, { saved: true, error: null, refreshed: true });
    incident = (await api.getIncidents(base)).find(row => row.id === incident.id); assert.equal(incident.riskHistoryId, high.historyId); assert.equal(incident.traceId, high.traceId); fields(incident, incidentFields);
  }
  assert.equal(incident.status, 'RESOLVED'); assert.equal(canChangeIncident(incident,'ack'), false); assert.equal(canChangeIncident(incident,'resolve'), false); assert.equal((await api.getAdminDashboardSummary(base)).openIncidentCount, 0);
  const writes = calls.slice(from).filter(call => call.method === 'PATCH'); assert.deepEqual(writes.map(call => call.path), [`/api/incidents/${incident.id}/ack`,`/api/incidents/${incident.id}/resolve`]); assert.ok(writes.every(call => call.bodyPresent === false && call.authentication === 'none'));
  snapshots.incidentResolved = incident; snapshots.writeFlow = calls.slice(from);
});
await check('finding: direct anonymous C19 changes RESOLVED back to ACKNOWLEDGED', async () => {
  const result = await raw('resolved state regression', `/api/incidents/${incident.id}/ack`, { method: 'PATCH' }); assert.equal(result.data.status, 'ACKNOWLEDGED'); assert.equal(result.data.id, incident.id); snapshots.resolvedRegression = result.data;
  await raw('restore synthetic resolved state', `/api/incidents/${incident.id}/resolve`, { method: 'PATCH' });
});
await check('unknown Incident ID has an error response; malformed path ID is rejected', async () => {
  const missing = await raw('missing Incident', '/api/incidents/999999/ack', { method: 'PATCH', expected: 500 }); snapshots.missingIncident = { status: missing.response.status };
  await raw('malformed Incident ID', '/api/incidents/not-an-id/resolve', { method: 'PATCH', expected: 400 });
});
await check('cross-origin preflight permits API PATCH and Actuator Authorization headers', async () => {
  const apiPreflight = await raw('API preflight', `/api/incidents/${incident.id}/ack`, { method: 'OPTIONS', headers: { Origin: 'http://127.0.0.1:5187', 'Access-Control-Request-Method':'PATCH', 'Access-Control-Request-Headers':'authorization' } });
  const metricsPreflight = await raw('Actuator preflight', '/actuator/prometheus', { method: 'OPTIONS', headers: { Origin: 'http://127.0.0.1:5187', 'Access-Control-Request-Method':'GET', 'Access-Control-Request-Headers':'authorization' } });
  assert.ok(apiPreflight.response.headers.get('access-control-allow-methods').includes('PATCH')); assert.ok(metricsPreflight.response.headers.get('access-control-allow-headers').includes('authorization'));
  snapshots.cors = { apiOrigin: apiPreflight.response.headers.get('access-control-allow-origin'), metricsOrigin: metricsPreflight.response.headers.get('access-control-allow-origin') };
});
globalThis.fetch = originalFetch;
await mkdir(output, { recursive: true });
await writeFile(new URL('verification.json', output), JSON.stringify({ verifiedAt: new Date().toISOString(), base, database: 'isolated disposable H2 operator0912', checks, passed: checks.length, failed: 0, calls, snapshots, productionCodeChanged: false, externalProvidersCalled: false, note: 'Findings reproduce current behavior; passing review assertions do not mean production security is complete.' }, null, 2));
console.log(JSON.stringify({ passed: checks.length, requests: calls.length, incidentId: incident.id, riskHistoryId: high.historyId, output: 'output/operator-09-12-api-review/verification.json' }));
