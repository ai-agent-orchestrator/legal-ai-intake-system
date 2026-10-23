import test from 'node:test';
import assert from 'node:assert/strict';
import { createIntakeApi } from '../src/client/api.js';
import { testGuide, testIntake } from './intake-test-data.mjs';
import { minimalPayload } from '../src/client/model.js';
const json = body => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } });

test('Screen04 receipt C05 uses the real ID and optional JWT, and rejects unknown status, invalid time and foreign inquiry', async t => {
  let captured; const mock = t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, init }; return json(testIntake()); });
  const api = createIntakeApi({ getToken: () => 'qa-jwt' });
  assert.equal((await api.receiptIntake('42')).id, 42); assert.equal(captured.url, '/api/intakes/42');
  assert.equal(captured.init.method, 'GET'); assert.equal(captured.init.headers.Authorization, 'Bearer qa-jwt'); assert.equal(captured.init.body, undefined);
  for (const patch of [{ id: 43 }, { status: 'FAKE_SUCCESS' }, { createdAt: '2026-02-30T09:00:00' }, { createdAt: null }]) {
    mock.mock.mockImplementation(async () => json(testIntake(42, patch))); await assert.rejects(api.receiptIntake(42), /응답 형식/);
  }
  assert.throws(() => api.receiptIntake('0')); assert.throws(() => api.receiptIntake('../42'));
});
test('Screen04 C04 keeps supported saved classification, while UNKNOWN may be inferred by the server', async t => {
  const saved = testIntake(); const body = minimalPayload(saved);
  const mock = t.mock.method(globalThis, 'fetch', async () => json(testGuide(body, { caseType: 'FAMILY' })));
  await assert.rejects(createIntakeApi().minimal(saved), /응답 형식/);
  const unknown = testIntake(42, { caseType: 'UNKNOWN' });
  mock.mock.mockImplementation(async () => json(testGuide(minimalPayload(unknown), { caseType: 'CIVIL' })));
  assert.equal((await createIntakeApi().minimal(unknown)).caseType, 'CIVIL');
});
test('Screen04 R02 validates the domain from the R01 row in addition to the encoded procedure code', async t => {
  let captured;
  const data = { code: 'server-code', domain: 'CIVIL', title: '절차', summary: '안내', userRole: 'party', steps: [], forms: [] };
  t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, init }; return json(data); });
  const api = createIntakeApi({ getToken: () => 'qa-jwt' });
  assert.deepEqual(await api.procedure('server-code', undefined, 'CIVIL'), data);
  assert.equal(captured.url, '/api/legal/procedures/server-code'); assert.equal(captured.init.headers.Authorization, undefined);
  await assert.rejects(api.procedure('server-code', undefined, 'CRIMINAL'), /응답 형식/);
});
