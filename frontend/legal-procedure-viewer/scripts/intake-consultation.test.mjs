import test from 'node:test';
import assert from 'node:assert/strict';
import { createIntakeApi } from '../src/client/api.js';
import { allowedStatuses, consultationMemoPayload, FOLLOW_UP_ITEMS, recentMemos } from '../src/client/consultationModel.js';
import { routeFromHash } from '../src/client/model.js';
import { testIntake, testMemo } from './intake-test-data.mjs';
const response = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

test('Screen07 status choices match server transitions, including same status and CLOSED terminal policy', () => {
  assert.deepEqual(allowedStatuses('SUBMITTED'), ['SUBMITTED', 'REVIEWED', 'NEED_MORE_INFO', 'CONSULTATION_READY', 'CLOSED']);
  for (const current of ['REVIEWED', 'NEED_MORE_INFO', 'CONSULTATION_READY']) assert.deepEqual(allowedStatuses(current), ['REVIEWED', 'NEED_MORE_INFO', 'CONSULTATION_READY', 'CLOSED']);
  assert.deepEqual(allowedStatuses('CLOSED'), ['CLOSED']);
  assert.deepEqual(allowedStatuses('UNKNOWN_STATUS'), []);
});
test('Screen07 C10 uses inquiry ID, PATCH, status only and optional JWT; validates changed and unchanged acknowledgements', async t => {
  let captured;
  const mock = t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, init }; return response({ id: 42, previousStatus: 'SUBMITTED', status: 'REVIEWED', changed: true }); });
  const api = createIntakeApi({ getToken: () => 'qa-jwt' });
  await api.updateStatus('42', 'REVIEWED');
  assert.equal(captured.url, '/api/intakes/42/status'); assert.equal(captured.init.method, 'PATCH');
  assert.deepEqual(JSON.parse(captured.init.body), { status: 'REVIEWED' }); assert.equal(captured.init.headers.Authorization, 'Bearer qa-jwt');
  mock.mock.mockImplementation(async () => response({ id: 42, previousStatus: 'REVIEWED', status: 'REVIEWED', changed: false }));
  assert.equal((await api.updateStatus(42, 'REVIEWED')).changed, false);
  assert.throws(() => api.updateStatus(0, 'REVIEWED')); assert.throws(() => api.updateStatus(42, 'INVALID'));
});
test('Screen07 C10 rejects wrong inquiry, wrong target and inconsistent changed flags; preserves 409', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response({}));
  for (const data of [{ id: 43, previousStatus: 'SUBMITTED', status: 'REVIEWED', changed: true }, { id: 42, previousStatus: 'SUBMITTED', status: 'CLOSED', changed: true }, { id: 42, previousStatus: 'REVIEWED', status: 'REVIEWED', changed: true }]) {
    mock.mock.mockImplementation(async () => response(data)); await assert.rejects(createIntakeApi().updateStatus(42, 'REVIEWED'), /응답 형식/);
  }
  mock.mock.mockImplementation(async () => response({}, 409)); await assert.rejects(createIntakeApi().updateStatus(42, 'REVIEWED'), error => error.status === 409);
});
test('Screen07 memo validation enforces blank/4000 UTF-16 units and four supported follow-ups before fetching', () => {
  const items = FOLLOW_UP_ITEMS.map(item => item.value);
  assert.deepEqual(consultationMemoPayload('  검수 메모  ', items), { content: '검수 메모', followUpItems: items });
  assert.equal(consultationMemoPayload('😀'.repeat(2000), []).content.length, 4000);
  for (const [content, selected] of [[' ', []], ['가'.repeat(4001), []], ['메모', [...items, items[0]]], ['메모', ['UNSUPPORTED']]]) assert.throws(() => consultationMemoPayload(content, selected));
});
test('Screen07 C11 sends content/codes only with inquiry ID and accepts protected memo ID from response', async t => {
  let captured;
  t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, init }; return response(testMemo(42, 301), 201); });
  const result = await createIntakeApi({ getToken: () => 'qa-jwt' }).createMemo(42, '  메모 원문  ', ['REQUEST_DOCUMENTS']);
  assert.equal(captured.url, '/api/intakes/42/memo'); assert.equal(captured.init.method, 'POST');
  assert.deepEqual(JSON.parse(captured.init.body), { content: '메모 원문', followUpItems: ['REQUEST_DOCUMENTS'] });
  assert.equal(captured.init.headers.Authorization, 'Bearer qa-jwt'); assert.equal(result.id, 301); assert.equal(result.intakeId, 42);
  assert.equal(result.author, undefined); assert.equal(result.contentMasked, '검수용 보호된 상담 메모');
});
test('Screen07 C05/C11 reject mismatched memo parents, bad dates, duplicate IDs and unsupported checklist fields', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response({}));
  for (const memo of [testMemo(43), testMemo(42, 301, { contentMasked: null }), testMemo(42, 301, { createdAt: 'bad' }), testMemo(42, 301, { followUpItems: ['UNKNOWN'] })]) {
    mock.mock.mockImplementation(async () => response(memo, 201)); await assert.rejects(createIntakeApi().createMemo(42, '메모', []), /응답 형식/);
    mock.mock.mockImplementation(async () => response(testIntake(42, { consultationMemos: [memo] }))); await assert.rejects(createIntakeApi().consultationIntake(42), /응답 형식/);
  }
  mock.mock.mockImplementation(async () => response(testIntake(42, { consultationMemos: [testMemo(), testMemo()] })));
  await assert.rejects(createIntakeApi().consultationIntake(42), /응답 형식/);
  mock.mock.mockImplementation(async () => response(testIntake(42)));
  assert.deepEqual((await createIntakeApi().consultationIntake(42)).consultationMemos, []);
});
test('Screen07 saved memos show recent five by server date/id without mutating server data; route preserves inquiry ID', () => {
  const memos = Array.from({ length: 7 }, (_, index) => testMemo(42, index + 1));
  assert.deepEqual(recentMemos(memos).map(memo => memo.id), [7, 6, 5, 4, 3]); assert.equal(memos[0].id, 1);
  assert.deepEqual(routeFromHash('#/lawyer/intakes/42/consultation'), { name: 'consultation', id: '42' });
  assert.equal(routeFromHash('#/lawyer/intakes/0/consultation').name, 'not-found');
});
