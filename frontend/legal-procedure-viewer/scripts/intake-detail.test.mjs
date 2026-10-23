import test from 'node:test';
import assert from 'node:assert/strict';
import { createIntakeApi } from '../src/client/api.js';
import { documentTypeLabel, fileSize, safeDownloadName } from '../src/client/intakeDetailModel.js';
import { testDocument, testIntake, testMemo } from './intake-test-data.mjs';
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

test('Screen08 C05 validates saved condition, protected memos and real uploaded file IDs without another reference API', async t => {
  let captured;
  const data = testIntake(42, { consultationMemos: [testMemo()], uploadedDocuments: [testDocument()] });
  t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, init }; return json(data); });
  assert.deepEqual(await createIntakeApi({ getToken: () => 'qa-jwt' }).detailIntake(42), data);
  assert.equal(captured.url, '/api/intakes/42'); assert.equal(captured.init.headers.Authorization, 'Bearer qa-jwt');
  assert.equal(captured.init.body, undefined);
});
test('Screen08 C05 rejects foreign document IDs, remote/changed download URLs, invalid MIME and file metadata', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json({}));
  for (const file of [testDocument(43), testDocument(42, 401, { downloadUrl: 'https://other.invalid/file' }), testDocument(42, 401, { downloadUrl: '/api/intakes/42/documents/402/download' }),
    testDocument(42, 401, { contentType: 'text/html' }), testDocument(42, 401, { sizeBytes: -1 }), testDocument(42, 401, { originalFilename: '../file.txt' }), testDocument(42, 401, { originalFilename: 'run.exe' })]) {
    mock.mock.mockImplementation(async () => json(testIntake(42, { uploadedDocuments: [file] })));
    await assert.rejects(createIntakeApi().detailIntake(42), /응답 형식/);
  }
  mock.mock.mockImplementation(async () => json(testIntake(42, { uploadedDocuments: [testDocument(), testDocument()] })));
  await assert.rejects(createIntakeApi().detailIntake(42), /응답 형식/);
});
test('Screen08 download uses inquiry ID plus document ID and Bearer in headers; returns verified binary, never a token URL', async t => {
  let captured;
  t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, init }; return new Response('test', { headers: { 'Content-Type': 'text/plain;charset=UTF-8' } }); });
  const blob = await createIntakeApi({ getToken: () => 'qa-jwt' }).downloadDocument(42, testDocument());
  assert.equal(await blob.text(), 'test'); assert.equal(captured.url, '/api/intakes/42/documents/401/download');
  assert.equal(captured.init.headers.Authorization, 'Bearer qa-jwt'); assert.equal(captured.init.headers.Accept, '*/*');
  assert.equal(captured.init.method, 'GET'); assert.equal(captured.init.body, undefined); assert.equal(captured.init.credentials, 'omit');
});
test('Screen08 anonymous downloads fail before fetch; foreign metadata is never followed with credentials', async t => {
  let calls = 0; t.mock.method(globalThis, 'fetch', async () => { calls++; return json({}); });
  assert.throws(() => createIntakeApi().downloadDocument(42, testDocument()), error => error.status === 401);
  assert.throws(() => createIntakeApi({ getToken: () => 'qa-jwt' }).downloadDocument(42, testDocument(43)));
  assert.equal(calls, 0);
});
test('Screen08 downloads reject HTML or wrong byte lengths and preserve 401/403/404 response errors', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => new Response('test', { headers: { 'Content-Type': 'text/html' } }));
  const api = createIntakeApi({ getToken: () => 'qa-jwt' });
  await assert.rejects(api.downloadDocument(42, testDocument()), /응답 형식/);
  mock.mock.mockImplementation(async () => new Response('short', { headers: { 'Content-Type': 'text/plain' } }));
  await assert.rejects(api.downloadDocument(42, testDocument()), /응답 형식/);
  for (const status of [401, 403, 404]) { mock.mock.mockImplementation(async () => json({}, status)); await assert.rejects(api.downloadDocument(42, testDocument()), error => error.status === status); }
});
test('Screen08 download cancellation propagates without turning an abort into a success', async t => {
  t.mock.method(globalThis, 'fetch', async (_, init) => new Promise((resolve, reject) => init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))));
  const controller = new AbortController();
  const request = createIntakeApi({ getToken: () => 'qa-jwt' }).downloadDocument(42, testDocument(), controller.signal);
  controller.abort(); await assert.rejects(request, error => error.name === 'AbortError');
});
test('Screen08 filename/size/type formatting does not claim fabricated sizes or permit Windows path characters', () => {
  assert.equal(safeDownloadName('CON.txt'), 'file-CON.txt'); assert.equal(safeDownloadName('검수:파일?.txt'), '검수_파일_.txt');
  assert.equal(fileSize(4), '4 B'); assert.equal(fileSize(1024), '1.0 KB'); assert.equal(fileSize(1048576), '1.0 MB');
  assert.equal(documentTypeLabel('text/plain;charset=UTF-8'), 'TXT');
});
