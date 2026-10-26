import test from 'node:test';
import assert from 'node:assert/strict';
import { createIntakeApi } from '../src/client/api.js';
import { EMPTY_RESULT_FORM, safeSearchUrl, searchRequestCopy, searchResultPayload, searchSourceLabel, searchSourceWarningLabel, sortSearchResults } from '../src/client/searchModel.js';
import { testSearch } from './intake-test-data.mjs';
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const form = overrides => ({ ...EMPTY_RESULT_FORM, sourceType: 'CASE_LAW', title: '검수 자료', url: 'https://example.com/reference', ...overrides });
const result = overrides => ({ id: 101, searchRequestId: 81, sourceType: 'CASE_LAW', title: '검수 자료', url: 'https://example.com/reference', snippet: null, displayOrder: 1, createdAt: '2026-10-21T11:00:00', ...overrides });

test('Screen05 C06 explicitly sends saved policy and optional purpose; old handoff remains compatible', async t => {
  const calls = []; t.mock.method(globalThis, 'fetch', async (url, init) => { calls.push({ url, init }); return json(testSearch(), 201); });
  const api = createIntakeApi({ getToken: () => 'qa-jwt' });
  await api.createSearch(42, '  확인 목적  ', undefined, 'QA_SOURCE_POLICY'); await api.createSearch(42, '');
  assert.equal(calls[0].url, '/api/intakes/42/search-requests'); assert.equal(calls[0].init.method, 'POST');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer qa-jwt');
  assert.deepEqual(JSON.parse(calls[0].init.body), { searchPurpose: '확인 목적', sourcePolicy: 'QA_SOURCE_POLICY' });
  assert.deepEqual(JSON.parse(calls[1].init.body), { searchPurpose: '' });
  assert.throws(() => api.createSearch(42, '가'.repeat(301))); assert.throws(() => api.createSearch(42, '', undefined, ' '));
  assert.equal(calls.length, 2);
});
test('Screen05 P02 requires JWT, uses inquiry ID, validates parent/unique IDs and preserves 403', async t => {
  let calls = 0; const mock = t.mock.method(globalThis, 'fetch', async (url, init) => { calls++; assert.equal(url, '/api/intakes/42/search-requests'); assert.equal(init.headers.Authorization, 'Bearer qa-jwt'); return json([testSearch()]); });
  assert.throws(() => createIntakeApi().searchHistory(42), error => error.status === 401); assert.equal(calls, 0);
  const api = createIntakeApi({ getToken: () => 'qa-jwt' }); assert.equal((await api.searchHistory(42))[0].id, 81);
  for (const bad of [[testSearch(43)], [testSearch(), testSearch()], testSearch()]) { mock.mock.mockImplementation(async () => json(bad)); await assert.rejects(api.searchHistory(42), /응답 형식/); }
  mock.mock.mockImplementation(async () => json({}, 403)); await assert.rejects(api.searchHistory(42), error => error.status === 403);
});
test('Screen05 C08 uses selected request ID and exact fields; accepts complete updated request without inventing COMPLETED', async t => {
  let captured; const data = testSearch(42, 81, { results: [result()] });
  t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, init }; return json(data, 201); });
  const saved = await createIntakeApi().addSearchResult(42, 81, form({ snippet: ' 요약 ', displayOrder: '3' }));
  assert.equal(saved.status, 'PREVIEW_READY'); assert.equal(saved.results.length, 1);
  assert.equal(captured.url, '/api/intakes/search-requests/81/results'); assert.equal(captured.init.method, 'POST'); assert.equal(captured.init.headers.Authorization, undefined);
  assert.deepEqual(JSON.parse(captured.init.body), { sourceType: 'CASE_LAW', title: '검수 자료', url: 'https://example.com/reference', snippet: '요약', displayOrder: 3 });
});
test('Screen05 C07/C08 reject mismatched request/intake/result IDs, duplicates and invalid dates', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json({})); const api = createIntakeApi();
  for (const bad of [testSearch(43), testSearch(42, 82), testSearch(42, 81, { createdAt: 'bad' }),
    testSearch(42, 81, { results: [result({ searchRequestId: 82 })] }), testSearch(42, 81, { results: [result(), result()] }),
    testSearch(42, 81, { results: [result({ createdAt: '2026-02-30T01:00:00' })] })]) {
    mock.mock.mockImplementation(async () => json(bad)); await assert.rejects(api.searchRequest(42, 81), /응답 형식/); await assert.rejects(api.addSearchResult(42, 81, form()), /응답 형식/);
  }
});
test('Screen05 result input rejects blank/length/order/scheme/credentials before fetch and accepts UTF-16 boundaries', t => {
  let calls = 0; t.mock.method(globalThis, 'fetch', async () => { calls++; return json({}); }); const api = createIntakeApi();
  for (const patch of [{ sourceType: ' ' }, { title: '' }, { url: '' }, { sourceType: '가'.repeat(101) }, { title: '가'.repeat(301) }, { url: `https://example.com/${'a'.repeat(1000)}` },
    { snippet: '가'.repeat(1001) }, { displayOrder: '0' }, { displayOrder: '-1' }, { displayOrder: '1.1' }, { displayOrder: '2147483648' },
    { url: 'javascript:alert(1)' }, { url: 'file:///C:/secret' }, { url: 'https://user:pass@example.com/' }, { url: 'https://example.com/a b' }]) assert.throws(() => api.addSearchResult(42, 81, form(patch)), error => !!error.fieldErrors);
  assert.equal(calls, 0);
  const body = searchResultPayload(form({ title: '😀'.repeat(150), snippet: '가'.repeat(1000), displayOrder: '2147483647' }));
  assert.equal(body.title.length, 300); assert.equal(body.snippet.length, 1000); assert.equal(body.displayOrder, 2147483647);
  assert.ok(!('displayOrder' in searchResultPayload(form())));
});
test('Screen05 unsafe legacy URLs remain inert, sort is nonmutating, copy uses real IDs and recorded times', () => {
  for (const url of ['data:text/html,test', 'javascript:alert(1)', '//example.com', 'https://user@example.com', 'https://example.com/\n']) assert.equal(safeSearchUrl(url), '');
  assert.equal(safeSearchUrl('https://example.com/reference'), 'https://example.com/reference');
  const items = [result({ id: 102, displayOrder: 2 }), result({ id: 101, displayOrder: 1, createdAt: '2026-10-21T12:00:00' })];
  assert.deepEqual(sortSearchResults(items, 'order').map(item => item.id), [101, 102]); assert.deepEqual(sortSearchResults(items, 'newest').map(item => item.id), [101, 102]); assert.equal(items[0].id, 102);
  const copy = searchRequestCopy(testSearch(42, 81, { results: items })); assert.ok(copy.includes('문의 #42')); assert.ok(copy.includes('조건 #90')); assert.ok(copy.includes('저장된 결과: 2건'));
});
const execution = overrides => ({ id: '11111111-1111-4111-8111-111111111111', stage: 'QUERY_GENERATION', provider: 'NAVER_WEB', errorCode: null, startedAt: '2026-10-21T10:01:00', finishedAt: null, ...overrides });
test('P03 requires JWT and valid IDs before fetch; sends exact request and accepts asynchronous execution', async t => {
  let captured;
  t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, init }; return json(testSearch(42, 81, { status: 'RUNNING', execution: execution() }), 202); });
  assert.throws(() => createIntakeApi().executeSearch(42, 81), error => error.status === 401);
  assert.equal(captured, undefined);
  const api = createIntakeApi({ getToken: () => 'qa-jwt' });
  assert.throws(() => api.executeSearch(0, 81)); assert.throws(() => api.executeSearch(42, 'bad'));
  assert.throws(() => api.executeSearch(42, 81, 'yes'));
  const result = await api.executeSearch(42, 81);
  assert.equal(result.status, 'RUNNING'); assert.equal(captured.url, '/api/intakes/search-requests/81/execute');
  assert.equal(captured.init.headers.Authorization, 'Bearer qa-jwt'); assert.equal(captured.init.method, 'POST');
  assert.deepEqual(JSON.parse(captured.init.body), { retryExplanation: false });
  await api.executeSearch(42, 81, true); assert.deepEqual(JSON.parse(captured.init.body), { retryExplanation: true });
});
test('P03 validates execution metadata, parent IDs and explanation instead of accepting malformed payloads', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json({}));
  const api = createIntakeApi({ getToken: () => 'qa-jwt' });
  for (const bad of [testSearch(43), testSearch(42, 82), testSearch(42, 81, { status: 'RUNNING' }),
    testSearch(42, 81, { execution: execution({ provider: 'UNTRUSTED' }) }),
    testSearch(42, 81, { execution: execution({ startedAt: 'bad' }) }),
    testSearch(42, 81, { execution: execution({ sourceWarningCode: { secret: 'bad' } }) }),
    testSearch(42, 81, { results: [result({ explanation: { content: 'bad' } })] })]) {
    mock.mock.mockImplementation(async () => json(bad, 202)); await assert.rejects(api.executeSearch(42, 81), /응답 형식/);
  }
});

test('Mixed search keeps source provenance and optional source warnings in existing C07 responses', async t => {
  t.mock.method(globalThis, 'fetch', async () => json(testSearch(42, 81, { status: 'COMPLETED',
    execution: execution({ stage: 'FINISHED', sourceWarningCode: 'GENERAL_SEARCH_UNAVAILABLE' }),
    results: [result({ sourceType: 'KBA_PUBLIC_WEB', url: 'https://www.klaw.or.kr/fixture?id=1' })] })));
  const data = await createIntakeApi().searchRequest(42, 81);
  assert.equal(data.results[0].sourceType, 'KBA_PUBLIC_WEB');
  assert.equal(data.execution.sourceWarningCode, 'GENERAL_SEARCH_UNAVAILABLE');
  assert.match(searchSourceLabel('KBA_PUBLIC_WEB', data.results[0].url), /대한변협.*웹 검색 수집/);
  for (const url of ['https://klaw.or.kr.evil.example/a', 'https://evil.example/klaw.or.kr', 'https://klaw.or.kr@evil.example/a'])
    assert.equal(searchSourceLabel('KBA_PUBLIC_WEB', url), '공개 웹 · 출처 확인 필요');
  assert.match(searchSourceWarningLabel('GENERAL_SEARCH_UNAVAILABLE'), /일반 웹 검색에 연결하지 못했습니다/);
  assert.match(searchSourceWarningLabel('KBA_SEARCH_UNAVAILABLE'), /변협 대상 검색에 연결하지 못했습니다/);
});
test('P03 missing configuration and ownership errors remain explicit without retrying POST', async t => {
  let calls = 0;
  const mock = t.mock.method(globalThis, 'fetch', async () => { calls++; return json({}, 503); });
  const api = createIntakeApi({ getToken: () => 'qa-jwt' });
  await assert.rejects(api.executeSearch(42, 81), error => error.status === 503 && error.message.includes('LLM·외부 검색'));
  mock.mock.mockImplementation(async () => { calls++; return json({}, 403); });
  await assert.rejects(api.executeSearch(42, 81), error => error.status === 403); assert.equal(calls, 2);
});
