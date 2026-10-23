import test from 'node:test';
import assert from 'node:assert/strict';
import { createIntakeApi } from '../src/client/api.js';
import { routeFromHash } from '../src/client/model.js';
import { declaredDocumentCount, EMPTY_LIST_FILTERS, filterIntakes, listCounts, listFilterError, validIntakeTime } from '../src/client/intakeListModel.js';
import { testIntake } from './intake-test-data.mjs';
const rows = [
  testIntake(91, { createdAt: '2026-10-20T23:59:59.999999', selectedDocuments: ['계약서', '이체 내역'], clientNameMasked: '검**', coreStatementMasked: '보호된 대여금 문의', desiredNextStep: '절차 안내' }),
  testIntake(92, { createdAt: '2026-10-21T00:00:00', caseType: 'FAMILY', status: 'NEED_MORE_INFO', counterpartMasked: '가**', desiredNextStep: '문서 검토' }),
  testIntake(93, { createdAt: '2026-10-21T23:59:59', status: 'CONSULTATION_READY', desiredNextStep: '절차 안내' }),
];
const response = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

test('Screen06 C09 sends a parameterless GET, optional bearer and abort signal; accepts empty lists', async t => {
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, init) => { calls.push({ url, init }); return response(calls.length === 1 ? rows : []); });
  assert.deepEqual(await createIntakeApi({ getToken: () => 'qa-jwt' }).intakes(), rows);
  assert.equal(calls[0].url, '/api/intakes');
  assert.equal(calls[0].init.method, 'GET');
  assert.equal(calls[0].init.body, undefined);
  assert.equal(calls[0].init.headers.Authorization, 'Bearer qa-jwt');
  assert.deepEqual(await createIntakeApi().intakes(), []);
  assert.equal(calls[1].init.headers.Authorization, undefined);
  assert.equal(calls[1].init.cache, 'no-store');
});
test('Screen06 C09 rejects malformed rows, duplicate IDs, >50 rows and invalid timestamps', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response([]));
  for (const payload of [{ content: rows }, [testIntake(1, { createdAt: null })], [testIntake(1, { createdAt: '2026-02-30T10:00:00' })],
    [testIntake(1, { selectedDocuments: null })], [testIntake(1), testIntake('1')], Array.from({ length: 51 }, (_, i) => testIntake(i + 1))]) {
    mock.mock.mockImplementation(async () => response(payload));
    await assert.rejects(createIntakeApi().intakes(), /응답 형식/);
  }
});
test('Screen06 C09 preserves HTTP errors and unauthorized handling', async t => {
  let cleared = 0;
  const mock = t.mock.method(globalThis, 'fetch', async () => response({}, 500));
  const api = createIntakeApi({ getToken: () => 'qa-jwt', onUnauthorized: () => cleared++ });
  for (const status of [401, 403, 500]) {
    mock.mock.mockImplementation(async () => response({}, status));
    await assert.rejects(api.intakes(), error => error.status === status);
  }
  assert.equal(cleared, 1);
});
test('Screen06 filters combine case/status/next step and search only supplied protected fields', () => {
  assert.deepEqual(filterIntakes(rows, { ...EMPTY_LIST_FILTERS, query: '검**', caseType: 'CIVIL', desiredNextStep: '절차 안내' }).map(row => row.id), [91]);
  assert.deepEqual(filterIntakes(rows, { ...EMPTY_LIST_FILTERS, query: ' 가** ', status: 'NEED_MORE_INFO' }).map(row => row.id), [92]);
  assert.equal(filterIntakes(rows, { ...EMPTY_LIST_FILTERS, query: '원본 이름' }).length, 0);
  assert.equal(filterIntakes(rows, { ...EMPTY_LIST_FILTERS, query: 'No. 93' })[0].id, 93);
});
test('Screen06 receipt date boundaries include the entire end day; invalid/reversed dates produce errors', () => {
  assert.deepEqual(filterIntakes(rows, { ...EMPTY_LIST_FILTERS, startDate: '2026-10-21', endDate: '2026-10-21' }).map(row => row.id), [93, 92]);
  for (const filters of [{ startDate: '2026-02-30' }, { startDate: '2026-10-22', endDate: '2026-10-21' }, { query: '가'.repeat(101) }]) {
    const actual = { ...EMPTY_LIST_FILTERS, ...filters };
    assert.ok(listFilterError(actual));
    assert.equal(filterIntakes(rows, actual).length, 0);
  }
  assert.equal(validIntakeTime('2024-02-29T23:59:59.123456789'), true);
  assert.equal(validIntakeTime('2026-10-21T24:00:00'), false);
});
test('Screen06 sorting is deterministic and nonmutating; counters use the loaded snapshot and real statuses', () => {
  assert.deepEqual(filterIntakes(rows, EMPTY_LIST_FILTERS).map(row => row.id), [93, 92, 91]);
  assert.deepEqual(filterIntakes(rows, EMPTY_LIST_FILTERS, false).map(row => row.id), [91, 92, 93]);
  assert.deepEqual(rows.map(row => row.id), [91, 92, 93]);
  assert.deepEqual(listCounts(rows), { total: 3, pending: 1, needsInfo: 1, ready: 1 });
  assert.deepEqual(listCounts([testIntake(1, { status: 'REVIEWED' }), testIntake(2, { status: 'CLOSED' })]), { total: 2, pending: 0, needsInfo: 0, ready: 0 });
});
test('Screen06 document count uses declared kinds, excludes none/duplicates, not uploaded file count', () => {
  assert.equal(declaredDocumentCount(testIntake(1, { selectedDocuments: ['없음', '계약서', '계약서', ''], uploadedDocuments: [{}, {}, {}] })), 1);
  assert.equal(declaredDocumentCount(testIntake(1)), 0);
});
test('Screen06 routes keep the actual inquiry ID and reject invalid or appended paths', () => {
  assert.deepEqual(routeFromHash('#/lawyer/intakes'), { name: 'intake-list' });
  assert.deepEqual(routeFromHash('#/lawyer/intakes/987'), { name: 'lawyer-detail', id: '987' });
  for (const path of ['/lawyer/intakes/0', '/lawyer/intakes/-1', '/lawyer/intakes/42/extra']) assert.equal(routeFromHash('#' + path).name, 'not-found');
});
