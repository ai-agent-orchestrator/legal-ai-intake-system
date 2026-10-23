// Explicit disposable local backend only. No LLM/search/Guard execution.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
const base = process.env.HARDENING_API_BASE;
if (!base || !['127.0.0.1', 'localhost', '[::1]'].includes(new URL(base).hostname)) {
  throw new Error('Set HARDENING_API_BASE to an isolated disposable local server.');
}
const checks = [];
let token;
async function call(method, path, body, expected = 200) {
  const response = await fetch(base + path, { method, signal: AbortSignal.timeout(15000), headers: {
    Accept: 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
  }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  assert.equal(response.status, expected, `${method} ${path}`);
  checks.push({ method, path, status: response.status });
  return response.json().catch(() => null);
}
token = (await call('POST', '/api/auth/login', { username: 'user', password: 'user123' })).accessToken;
const guide = await call('POST', '/api/legal-intake/minimal', { coreFact: '돈을 빌려줬는데 못 받았어요.', caseType: 'CIVIL' });
assert.equal(guide.caseType, 'CIVIL');
for (const patch of [{ coreFact: '' }, { coreFact: '가'.repeat(101) }, { caseType: 'INVALID' }, { documents: Array(21).fill('계약서') }]) {
  const result = await call('POST', '/api/legal-intake/minimal', { coreFact: '가상 사건', ...patch }, 400);
  assert.equal(result.code, 'INVALID_REQUEST'); assert.ok(Object.keys(result.fieldErrors).length);
}
const intake = await call('POST', '/api/intakes', { inquiryPurpose: '절차 안내', caseType: 'CIVIL',
  partyPosition: '원고', coreStatement: '가상 검수 사건', desiredNextStep: '절차 안내' }, 201);
const saved = await call('GET', `/api/intakes/${intake.id}`);
await call('POST', '/api/legal-intake/minimal', { sessionId: saved.sessionId || '', inquiryPurpose: saved.inquiryPurpose,
  caseType: saved.caseType, partyRole: saved.partyPosition, coreFact: saved.coreStatementMasked,
  documents: saved.selectedDocuments.filter(value => !['선택 자료 없음', '없음'].includes(value)),
  desiredNextStep: saved.desiredNextStep });
const search = await call('POST', `/api/intakes/${intake.id}/search-requests`, {}, 201);
const result = { sourceType: 'PUBLIC_WEB', title: '가상 검수 링크', url: 'https://example.com/reference' };
for (const patch of [{ url: 'javascript:alert(1)' }, { url: 'https://user:pass@example.com/' },
  { url: 'https://example.com/%0a' }, { displayOrder: -1 }, { displayOrder: 0 }]) {
  const rejected = await call('POST', `/api/intakes/search-requests/${search.id}/results`, { ...result, ...patch }, 400);
  assert.equal(rejected.code, 'INVALID_REQUEST');
}
assert.equal((await call('GET', `/api/intakes/search-requests/${search.id}`)).results.length, 0);
const added = await call('POST', `/api/intakes/search-requests/${search.id}/results`, result, 201);
assert.equal(added.results[0].displayOrder, 1);
// C17/C18/C19/C20 now require the office administrator JWT.
token = (await call('POST', '/api/auth/login', { username: 'admin', password: 'admin123' })).accessToken;
const raw = 'PRIVATE_LIVE_MARKER_010-1234-5678';
const risk = await call('POST', '/api/agent/risk/analyze', { userInput: raw, toolName: 'qa-tool',
  retryCount: 2, policyViolation: true, externalApiCall: true, toolError: false,
  approvalRequired: false, dbWrite: false, promptTokens: 0, completionTokens: 0 });
for (const query of ['', '?riskLevel=HIGH', `?decision=${encodeURIComponent(risk.decision)}`]) {
  const histories = await call('GET', `/api/agent/risk/history${query}`);
  const row = histories.find(item => item.id === risk.historyId);
  assert.ok(row); assert.equal(row.riskScore, risk.riskScore);
  assert.ok(!Object.hasOwn(row, 'userInput')); assert.ok(!JSON.stringify(histories).includes(raw));
}
const incidentsBefore = await call('GET', '/api/incidents');
for (const action of ['ack', 'resolve']) {
  const missing = await call('PATCH', `/api/incidents/9223372036854775807/${action}`, undefined, 404);
  assert.equal(missing.code, 'INCIDENT_NOT_FOUND'); assert.ok(missing.traceId);
  await call('PATCH', `/api/incidents/invalid/${action}`, undefined, 400);
}
assert.deepEqual(await call('GET', '/api/incidents'), incidentsBefore);
const output = new URL('../output/hardening-four/', import.meta.url);
await mkdir(output, { recursive: true });
await writeFile(new URL('live-api-verification.json', output), JSON.stringify({ date: new Date().toISOString(),
  passed: true, checks, externalProviderCalls: 0, fixture: 'isolated H2, virtual data only' }, null, 2));
console.log(JSON.stringify({ passed: true, checks: checks.length, externalProviderCalls: 0 }));
