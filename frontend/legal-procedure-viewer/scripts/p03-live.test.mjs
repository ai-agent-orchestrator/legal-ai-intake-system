// Requires the isolated Spring server :18083 and p03-mock-providers.mjs :18181. Never targets a paid provider.
import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
const base = 'http://127.0.0.1:18083', provider = 'http://127.0.0.1:18181';
const output = new URL('../../../output/p03-search/', import.meta.url);
let token, intakeId;
const evidence = { kind: 'real Spring HTTP + local provider fixtures; no paid external calls', cases: [] };
async function api(path, body, expected = 200, auth = true) {
  const response = await fetch(base + path, { method: body === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: `Bearer ${token}` } : {}) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  assert.equal(response.status, expected, `${path}: ${await response.clone().text()}`); return response.json();
}
async function mode(value) { const response = await fetch(`${provider}/__qa__/mode?value=${value}`, { method: 'POST' }); assert.equal(response.status, 200); }
const create = purpose => api(`/api/intakes/${intakeId}/search-requests`, { searchPurpose: purpose, sourcePolicy: 'KBA_NAVER_PUBLIC_WEB' }, 201);
const execute = (id, retryExplanation = false) => api(`/api/intakes/search-requests/${id}/execute`, { retryExplanation }, 202);
async function terminal(id) {
  const until = Date.now() + 10000;
  while (Date.now() < until) { const data = await api(`/api/intakes/search-requests/${id}`); if (!['QUEUED', 'RUNNING'].includes(data.status)) return data; await new Promise(resolve => setTimeout(resolve, 100)); }
  throw new Error('P03 local verification timeout');
}
test('P03 local HTTP: query → provider → saved sources → explanations; duplicate execution is idempotent', async () => {
  token = (await api('/api/auth/login', { username: 'user', password: 'user123' }, 200, false)).accessToken;
  intakeId = (await api('/api/intakes', { inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고', coreStatement: '모의 검수용 대여금 반환 문의입니다.', desiredNextStep: '절차 안내', preferredRegion: '서울특별시' }, 201)).id;
  await mode('normal'); const request = await create('모의 검수 정상 검색'); await execute(request.id);
  const result = await terminal(request.id); assert.equal(result.status, 'COMPLETED'); assert.equal(result.results.length, 2);
  assert.equal(result.intakeId, intakeId); assert.equal(result.conditionId, request.conditionId);
  assert.ok(result.results.every(item => item.searchRequestId === request.id && item.explanation));
  await execute(request.id); const calls = (await (await fetch(`${provider}/__qa__/calls`)).json()).calls;
  assert.deepEqual(calls, ['query', 'search', 'explanation', 'explanation']);
  evidence.cases.push({ scenario: 'completed/idempotent', requestId: request.id, status: result.status, calls });
});
test('P03 local HTTP: empty and external error stay distinct', async () => {
  for (const [fixtureMode, expected] of [['empty', 'NO_RESULTS'], ['search-error', 'FAILED']]) {
    await mode(fixtureMode); const request = await create(`모의 검수 ${fixtureMode}`); await execute(request.id);
    const result = await terminal(request.id); assert.equal(result.status, expected); assert.deepEqual(result.results, []);
    evidence.cases.push({ scenario: fixtureMode, requestId: request.id, status: result.status, errorCode: result.execution.errorCode });
  }
});
test('P03 local HTTP: explanation-only retry preserves IDs and does not repeat search', async () => {
  await mode('explanation-error'); const request = await create('모의 검수 설명 오류 복구'); await execute(request.id);
  const partial = await terminal(request.id); assert.equal(partial.status, 'PARTIAL'); assert.equal(partial.results.length, 2);
  await mode('normal'); await execute(request.id, true); const completed = await terminal(request.id);
  assert.equal(completed.status, 'COMPLETED'); assert.deepEqual(completed.results.map(item => item.id), partial.results.map(item => item.id));
  assert.deepEqual((await (await fetch(`${provider}/__qa__/calls`)).json()).calls, ['explanation', 'explanation']);
  const browser = await create('브라우저 모의 검수 · 실행 대기');
  evidence.browser = { intakeId, searchRequestId: browser.id, url: `http://127.0.0.1:5185/intake.html#/intake/${intakeId}/search/${browser.id}` };
  evidence.cases.push({ scenario: 'partial/explanation retry', requestId: request.id, status: completed.status });
  await mkdir(output, { recursive: true }); await writeFile(new URL('local-http.json', output), JSON.stringify(evidence, null, 2));
  console.log('Browser QA:', evidence.browser.url);
});
