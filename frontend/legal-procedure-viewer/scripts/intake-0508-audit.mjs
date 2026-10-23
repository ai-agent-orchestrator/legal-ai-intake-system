// Contract audit of an isolated, disposable LOCAL backend. Records existing security/validation gaps.
// Does not execute P03 or contact any external search/LLM provider.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
const base = process.env.INTAKE_AUDIT_API_BASE;
if (!base || !['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname)) throw new Error('An explicit local audit backend is required.');
const observations = [];
async function call(method, path, body, token, expected) {
  const response = await fetch(base + path, { method, headers: { Accept: 'application/json', ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body !== undefined ? { body: JSON.stringify(body) } : {}) });
  observations.push({ method, path, authenticated: !!token, status: response.status });
  assert.equal(response.status, expected, `${method} ${path}`);
  return response.json().catch(() => null);
}
const ownerToken = (await call('POST', '/api/auth/login', { username: 'user', password: 'user123' }, '', 200)).accessToken;
const adminToken = (await call('POST', '/api/auth/login', { username: 'admin', password: 'admin123' }, '', 200)).accessToken;
const payload = { inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고', coreStatement: '화면05~08 권한 검수용 가상 대여금 문의입니다.', desiredNextStep: '절차 안내', preferredRegion: '서울특별시' };
const owned = await call('POST', '/api/intakes', payload, ownerToken, 201);
const unowned = await call('POST', '/api/intakes', payload, '', 201);
assert.equal((await call('GET', `/api/intakes/${owned.id}`, undefined, '', 200)).id, owned.id);
assert.ok((await call('GET', '/api/intakes', undefined, '', 200)).some(item => item.id === owned.id));
const status = await call('PATCH', `/api/intakes/${owned.id}/status`, { status: 'REVIEWED' }, '', 200);
assert.equal(status.id, owned.id); assert.equal(status.changed, true);
const memo = await call('POST', `/api/intakes/${owned.id}/memo`, { content: '가상 검수 연락처 010-1234-5678', followUpItems: ['REQUEST_DOCUMENTS'] }, '', 201);
assert.equal(memo.intakeId, owned.id); assert.ok(!memo.contentMasked.includes('010-1234-5678'));
const request = await call('POST', `/api/intakes/${owned.id}/search-requests`, { searchPurpose: '직접 호출 검수', sourcePolicy: 'KBA_NAVER_PUBLIC_WEB' }, '', 201);
assert.equal(request.intakeId, owned.id);
const detail = await call('GET', `/api/intakes/search-requests/${request.id}`, undefined, '', 200);
assert.equal(detail.conditionId, owned.lawyerSearchCondition.id);
// These intentionally invalid values establish a server validation gap. They are stored only in this disposable DB.
const unsafe = await call('POST', `/api/intakes/search-requests/${request.id}/results`, { sourceType: 'PUBLIC_WEB', title: '검수용 위험 URL · 실행하지 않음', url: 'javascript:void(0)', displayOrder: -1 }, '', 201);
assert.ok(unsafe.results.some(item => item.url === 'javascript:void(0)' && item.displayOrder === -1));
await call('GET', `/api/intakes/${owned.id}/search-requests`, undefined, '', 401);
await call('GET', `/api/intakes/${owned.id}/search-requests`, undefined, ownerToken, 200);
await call('GET', `/api/intakes/${unowned.id}/search-requests`, undefined, ownerToken, 403);
await call('GET', `/api/intakes/${unowned.id}/search-requests`, undefined, adminToken, 200);
const otherRequest = await call('POST', `/api/intakes/${unowned.id}/search-requests`, {}, '', 201);
await call('POST', `/api/intakes/search-requests/${request.id}/execute`, {}, '', 401);
await call('POST', `/api/intakes/search-requests/${otherRequest.id}/execute`, {}, ownerToken, 403);
// The audit server is explicitly started with search.enabled=false.
await call('POST', `/api/intakes/search-requests/${request.id}/execute`, {}, ownerToken, 503);
const unchanged = await call('GET', `/api/intakes/search-requests/${request.id}`, undefined, ownerToken, 200);
assert.equal(unchanged.status, 'PREVIEW_READY'); assert.equal(unchanged.execution, null);
const evidence = { verifiedAt: new Date().toISOString(), backend: base, externalProvidersCalled: false, ids: { intakeId: owned.id, requestId: request.id, conditionId: detail.conditionId, memoId: memo.id }, observations,
  findings: [
    { priority: 'P1', code: 'DEVELOPMENT_PUBLIC_ENDPOINTS', details: 'C05/C06/C07/C08/C09/C10/C11 accept anonymous requests; lawyer routes do not enforce a lawyer role.' },
    { priority: 'P2', code: 'C08_SERVER_VALIDATION_GAP', details: 'Direct C08 accepts javascript URL and displayOrder=-1. React prevents these inputs and renders unsafe links inert.' },
  ] };
const output = new URL('../../../output/intake-05-08-recheck/', import.meta.url);
await mkdir(output, { recursive: true }); await writeFile(new URL('auth-and-validation.json', output), JSON.stringify(evidence, null, 2));
console.log(JSON.stringify({ verified: observations.length, findings: evidence.findings.map(item => item.code), output: 'output/intake-05-08-recheck/auth-and-validation.json' }));
