import test from 'node:test';
import assert from 'node:assert/strict';
import { testIntake, testGuide, testCriminalChecklist, testSearch } from './intake-test-data.mjs';
import { createIntakeApi, normalizeBase } from '../src/client/api.js';
import { classificationPayload, INITIAL_DRAFT, intakePayload, routeFromHash, selectProcedureCode, validateClassification } from '../src/client/model.js';

const draft = { ...INITIAL_DRAFT, caseType: 'CIVIL', coreStatement: '  개발 검수용 계약 문의입니다.  ' };
const preview = { caseType: 'CIVIL', caseTypeLabel: '민사 사건', disputeArea: '계약 관련 분쟁', recommendedDocuments: ['계약서'], clientGuideMessage: '검수용 안내', classificationMethod: 'RULE_BASED', persisted: false };
const procedure = { code: 'civil/from-list', domain: 'CIVIL', title: '검수 절차', summary: '검수용 요약', userRole: 'party' };
const intake = testIntake(42, { inquiryPurpose: '절차 안내', coreStatementMasked: '검수 내용' });
const response = (data, status = 200, headers = {}) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json', ...headers } });

test('C02 sends only caseType/coreStatement; public API never sends account credentials', async t => {
  let captured;
  t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, ...init }; return response(preview); });
  assert.deepEqual(await createIntakeApi({ base: 'http://localhost:8080/', getToken: () => 'private-token' }).classify(draft), preview);
  assert.equal(captured.url, 'http://localhost:8080/api/intakes/classify-preview');
  assert.deepEqual(JSON.parse(captured.body), { caseType: 'CIVIL', coreStatement: draft.coreStatement.trim() });
  assert.equal(captured.method, 'POST');
  assert.equal(captured.headers.Authorization, undefined);
  assert.equal(captured.credentials, 'omit');
});
test('blank / whitespace / 101 units rejected; 100 units including UTF-16 emoji accepted', () => {
  for (const coreStatement of ['', '   ', 'a'.repeat(101), '😀'.repeat(51)]) assert.ok(validateClassification({ ...draft, coreStatement }));
  assert.equal(validateClassification({ ...draft, coreStatement: 'a'.repeat(100) }), '');
  assert.equal(validateClassification({ ...draft, coreStatement: '😀'.repeat(50) }), '');
  assert.throws(() => classificationPayload({ ...draft, caseType: 'CIVIL_LITIGATION' }));
});
test('invalid input never reaches fetch', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response(preview));
  assert.throws(() => createIntakeApi().classify({ ...draft, coreStatement: ' ' }));
  assert.equal(mock.mock.callCount(), 0);
});
test('preview rejects empty / malformed / saved response instead of rendering false success', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response(null));
  for (const value of [null, {}, [], { ...preview, persisted: true }, { ...preview, recommendedDocuments: null }, { ...preview, caseType: 'UNSUPPORTED' }]) {
    mock.mock.mockImplementation(async () => response(value));
    await assert.rejects(createIntakeApi().classify(draft), /응답 형식/);
  }
});
test('400 preserves server field errors and trace reference', async t => {
  t.mock.method(globalThis, 'fetch', async () => response({ fieldErrors: { coreStatement: 'too long' } }, 400, { 'X-Trace-Id': 'test-trace' }));
  await assert.rejects(createIntakeApi().classify(draft), error => error.status === 400 && error.fieldErrors.coreStatement === 'too long' && error.traceId === 'test-trace');
});
test('401 / 403 / 404 / 429 / 500 yield actionable errors', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response({}, 500));
  for (const status of [401, 403, 404, 429, 500]) {
    mock.mock.mockImplementation(async () => response({}, status));
    await assert.rejects(createIntakeApi().classify(draft), error => error.status === status && error.message.length > 10);
  }
});
test('network and HTML responses are not accepted as API results', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => { throw new TypeError('offline'); });
  await assert.rejects(createIntakeApi().classify(draft), /서버에 연결/);
  mock.mock.mockImplementation(async () => new Response('<!doctype html><html>SPA fallback</html>'));
  await assert.rejects(createIntakeApi().classify(draft), /응답 형식/);
});
test('R01 handles empty lists and sends exact supported domain', async t => {
  let url;
  t.mock.method(globalThis, 'fetch', async value => { url = value; return response([]); });
  assert.deepEqual(await createIntakeApi().procedures('UNKNOWN'), []);
  assert.equal(url, '/api/legal/procedures');
  await createIntakeApi().procedures('CRIMINAL');
  assert.equal(url, '/api/legal/procedures?domain=CRIMINAL');
});
test('R02 encodes server-provided code, validates detail, rejects missing code', async t => {
  let url;
  t.mock.method(globalThis, 'fetch', async value => { url = value; return response({ ...procedure, steps: [], forms: [] }); });
  await createIntakeApi().procedure(procedure.code);
  assert.equal(url, '/api/legal/procedures/civil%2Ffrom-list');
  assert.throws(() => createIntakeApi().procedure(''));
});
test('C01 uses exact credentials and validates accessToken/username/role', async t => {
  let body;
  t.mock.method(globalThis, 'fetch', async (url, init) => { assert.equal(url, '/api/auth/login'); body = JSON.parse(init.body); return response({ accessToken: 'test-token', username: 'test-user', role: 'ROLE_USER' }); });
  await createIntakeApi().login('test-user', 'test-password');
  assert.deepEqual(body, { username: 'test-user', password: 'test-password' });
});
test('C03 uses optional bearer token; C05 receives returned ID; no invented ID', async t => {
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, init) => { calls.push({ url, init }); return response(intake); });
  const api = createIntakeApi({ getToken: () => 'test-token' });
  const created = await api.create({ ...draft, partyPosition: '검수', desiredNextStep: '검수' });
  await api.intake(created.id);
  assert.equal(calls[0].init.headers.Authorization, 'Bearer test-token');
  assert.equal(calls[1].url, '/api/intakes/42');
  assert.throws(() => api.intake('42/../1'));
  assert.throws(() => api.intake('0'));
});
test('authenticated 401 clears identity, public 401 does not trigger false logout', async t => {
  let cleared = 0;
  t.mock.method(globalThis, 'fetch', async () => response({}, 401));
  const api = createIntakeApi({ getToken: () => 'expired', onUnauthorized: () => { cleared++; } });
  await assert.rejects(api.intake(1));
  assert.equal(cleared, 1);
  await assert.rejects(api.classify(draft));
  assert.equal(cleared, 1);
});
test('unmount/input-change abort propagates to fetch', async t => {
  t.mock.method(globalThis, 'fetch', (url, init) => new Promise((resolve, reject) => {
    const abort = () => reject(new DOMException('cancelled', 'AbortError'));
    if (init.signal.aborted) abort(); else init.signal.addEventListener('abort', abort, { once: true });
  }));
  const controller = new AbortController();
  const pending = createIntakeApi().classify(draft, controller.signal);
  controller.abort();
  await assert.rejects(pending, error => error.name === 'AbortError');
});
test('request timeout ends loading with a retryable message', async t => {
  t.mock.method(globalThis, 'fetch', (url, init) => new Promise((resolve, reject) => init.signal.addEventListener('abort', () => reject(new DOMException('timeout', 'AbortError')), { once: true })));
  await assert.rejects(createIntakeApi({ timeoutMs: 5 }).classify(draft), /대기 시간/);
});
test('base URL rejects credential-bearing / unsafe URLs', () => {
  assert.equal(normalizeBase(''), '');
  assert.equal(normalizeBase('https://api.example.com/'), 'https://api.example.com');
  for (const url of ['javascript:alert(1)', 'https://user:secret@example.com', 'https://example.com/?token=secret', 'https://example.com/#secret']) assert.throws(() => normalizeBase(url));
});
test('routes preserve the server ID and resolve unknown routes explicitly', () => {
  assert.deepEqual(routeFromHash(''), { name: 'login' });
  assert.deepEqual(routeFromHash('#/intake/new'), { name: 'write' });
  assert.deepEqual(routeFromHash('#/intake/42'), { name: 'receipt', id: '42' });
  assert.deepEqual(routeFromHash('#/intake/0'), { name: 'not-found' });
});

test('C05 rejects missing fields / wrong IDs / unsafe numeric IDs', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response({ id: 42 }));
  const api = createIntakeApi();
  for (const body of [{ id: 42 }, { ...intake, id: 43 }, { ...intake, coreStatementMasked: null }]) {
    mock.mock.mockImplementation(async () => response(body));
    await assert.rejects(api.intake(42), /응답 형식/);
  }
  mock.mock.mockImplementation(async () => response({ id: 9007199254740992 }));
  await assert.rejects(api.create(draft), /응답 형식/);
  assert.throws(() => api.intake(9007199254740992));
});

test('R01/R02 reject wrong domain and code instead of displaying another record', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response([procedure]));
  await assert.rejects(createIntakeApi().procedures('CRIMINAL'), /응답 형식/);
  mock.mock.mockImplementation(async () => response({ ...procedure, steps: [], forms: [] }));
  await assert.rejects(createIntakeApi().procedure('different-code'), /응답 형식/);
});

test('selected procedure falls back when the server list no longer contains it', () => {
  assert.equal(selectProcedureCode([procedure], 'old-code'), procedure.code);
  assert.equal(selectProcedureCode([procedure], procedure.code), procedure.code);
  assert.equal(selectProcedureCode([], procedure.code), undefined);
});

test('C03 payload preserves purpose and filters UI fields; validates all required limits', () => {
  const form = { partyPosition: '  검수 의뢰인  ', desiredNextStep: '검수 안내', documents: ['계약서', '계약서'] };
  const body = intakePayload({ ...draft, uiOnly: 'must-not-leak' }, preview, form);
  assert.deepEqual(Object.keys(body).sort(), ['caseType', 'coreStatement', 'desiredNextStep', 'documents', 'inquiryPurpose', 'partyPosition']);
  assert.equal(body.inquiryPurpose, draft.inquiryPurpose);
  assert.equal(body.partyPosition, '검수 의뢰인');
  assert.deepEqual(body.documents, ['계약서']);
  for (const invalidForm of [{ ...form, partyPosition: ' ' }, { ...form, partyPosition: '가'.repeat(51) }, { ...form, desiredNextStep: '가'.repeat(101) }, { ...form, documents: ['가'.repeat(101)] }]) {
    assert.throws(() => intakePayload(draft, preview, invalidForm));
  }
  assert.throws(() => intakePayload({ ...draft, coreStatement: '가'.repeat(101) }, preview, form));
});

test('abort while reading JSON remains a cancellation', async t => {
  t.mock.method(globalThis, 'fetch', async () => ({ ok: true, json: async () => { throw new DOMException('cancelled', 'AbortError'); } }));
  await assert.rejects(createIntakeApi().classify(draft), error => error.name === 'AbortError');
});

test('Screen03 R03/R04/R05 encode returned codes; reference requests stay public', async t => {
  const calls = [];
  const form = { procedureCode: procedure.code, formCode: 'server/form-code', title: '검수 양식', fileType: 'pdf', usageNote: '검수 용도' };
  const step = { stepOrder: 1, stepCode: 'server-step', title: '검수 단계', description: '검수 설명', checklist: ['검수 항목'] };
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    calls.push({ url, init });
    return response(url.endsWith('/steps') ? [step] : url.includes('?procedure=') ? [form] : form);
  });
  const api = createIntakeApi({ getToken: () => 'must-not-send' });
  assert.deepEqual(await api.procedureSteps(procedure.code), [step]);
  assert.deepEqual(await api.forms(procedure.code), [form]);
  assert.deepEqual(await api.form(form.formCode, procedure.code), form);
  assert.deepEqual(calls.map(call => call.url), ['/api/legal/procedures/civil%2Ffrom-list/steps', '/api/legal/forms?procedure=civil%2Ffrom-list', '/api/legal/forms/server%2Fform-code']);
  assert.ok(calls.every(call => call.init.headers.Authorization === undefined));
  assert.throws(() => api.forms(''));
  assert.throws(() => api.form('', procedure.code));
});

test('Screen03 rejects missing checklist, mismatched form/procedure and malformed form metadata', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response([{ stepOrder: 1, title: 'bad', description: '' }]));
  const api = createIntakeApi();
  await assert.rejects(api.procedureSteps('civil'), /응답 형식/);
  const form = { procedureCode: 'civil', formCode: 'form1', title: '양식', fileType: 'pdf', usageNote: '용도' };
  mock.mock.mockImplementation(async () => response([form]));
  await assert.rejects(api.forms('criminal'), /응답 형식/);
  mock.mock.mockImplementation(async () => response(form));
  await assert.rejects(api.form('another-form', 'civil'), /응답 형식/);
  await assert.rejects(api.form('form1', 'criminal'), /응답 형식/);
  mock.mock.mockImplementation(async () => response({ ...form, usageNote: null }));
  await assert.rejects(api.form('form1', 'civil'), /응답 형식/);
});

test('Screen03 C03 payload uses edited required fields and all optional DTO fields without UI extras', () => {
  const form = { inquiryPurpose: '문서 검토', caseType: 'FAMILY', partyPosition: '신청인', coreStatement: '가'.repeat(100), desiredNextStep: '상담', documents: ['이체 내역'], incidentDate: '2024-02-29', clientName: '검수 이름', clientPhone: '010-0000-0000', counterpart: '검수 상대방', amountOrDamage: '500만원', currentStage: '준비', preferredRegion: '서울', officeType: '법무법인', experienceLevel: '10년 이상', publicCareerPreference: '상관없음', consultationStyle: '전화 상담', lawyerSearchPurpose: '검수 목적', expanded: true, fakeId: 999 };
  const body = intakePayload(draft, preview, form);
  assert.equal(body.caseType, 'FAMILY');
  assert.equal(body.coreStatement.length, 100);
  assert.equal(body.inquiryPurpose, '문서 검토');
  assert.equal(body.expanded, undefined);
  assert.equal(body.fakeId, undefined);
  assert.equal(body.clientPhone, form.clientPhone);
  assert.equal(body.consultationStyle, form.consultationStyle);
  assert.deepEqual(Object.keys(body).sort(), Object.keys(form).filter(key => !['expanded', 'fakeId'].includes(key)).sort());
});

test('Screen03 optional date/phone/length validation and mutually exclusive no-documents choice', () => {
  const form = { partyPosition: '원고', desiredNextStep: '상담', documents: [] };
  for (const invalid of [{ incidentDate: '2025-02-29' }, { incidentDate: '2024-13-01' }, { clientPhone: 'abc01012345678' }, { clientPhone: '123' }, { counterpart: '가'.repeat(101) }, { lawyerSearchPurpose: '가'.repeat(301) }, { documents: ['없음', '계약서'] }, { coreStatement: ' ' }, { coreStatement: '가'.repeat(101) }]) {
    assert.throws(() => intakePayload(draft, preview, { ...form, ...invalid }), error => Object.keys(error.fieldErrors).length > 0);
  }
  assert.deepEqual(intakePayload(draft, preview, { ...form, documents: ['없음'] }).documents, []);
  assert.equal(intakePayload(draft, preview, { ...form, clientPhone: '+82 10-1234-5678' }).clientPhone, '+82 10-1234-5678');
});

test('a late 401 from a previous token never clears a newer login', async t => {
  let token = 'old-token';
  let release;
  let cleared = 0;
  t.mock.method(globalThis, 'fetch', () => new Promise(resolve => { release = resolve; }));
  const api = createIntakeApi({ getToken: () => token, onUnauthorized: () => { token = ''; cleared++; } });
  const pending = api.intake(42);
  token = 'new-token';
  release(response({}, 401));
  await assert.rejects(pending, error => error.status === 401);
  assert.equal(token, 'new-token');
  assert.equal(cleared, 0);
});

test('Screen04 C04 sends exactly the protected C05 fields with coreFact/partyRole and sends JWT', async t => {
  let captured;
  const protectedIntake = testIntake(42, { counterpartMasked: '검**', coreStatementMasked: '문의 010-0***-****', selectedDocuments: ['계약서', '선택 자료 없음'], clientName: 'must-not-leak', coreStatement: 'must-not-leak' });
  t.mock.method(globalThis, 'fetch', async (url, init) => {
    captured = { url, init, body: JSON.parse(init.body) };
    return response(testGuide(captured.body));
  });
  const guide = await createIntakeApi({ getToken: () => 'private-token' }).minimal(protectedIntake);
  assert.equal(captured.url, '/api/legal-intake/minimal');
  assert.equal(captured.init.method, 'POST');
  assert.equal(captured.init.headers.Authorization, 'Bearer private-token');
  assert.deepEqual(Object.keys(captured.body).sort(), ['sessionId', 'inquiryPurpose', 'caseType', 'partyRole', 'incidentDate', 'counterpart', 'amountOrDamage', 'documents', 'desiredNextStep', 'coreFact'].sort());
  assert.equal(captured.body.partyRole, '원고');
  assert.equal(captured.body.coreFact, '문의 010-0***-****');
  assert.equal(captured.body.counterpart, '검**');
  assert.deepEqual(captured.body.documents, ['계약서']);
  assert.ok(!JSON.stringify(captured.body).includes('must-not-leak'));
  assert.equal(guide.id, undefined);
});

test('Screen04 C04 rejects missing guide fields and a response for different source data', async t => {
  const body = { sessionId: intake.sessionId, inquiryPurpose: intake.inquiryPurpose, partyRole: intake.partyPosition, caseType: intake.caseType, desiredNextStep: intake.desiredNextStep };
  const mock = t.mock.method(globalThis, 'fetch', async () => response({}));
  for (const data of [{}, testGuide(body, { checkedFacts: null }), testGuide(body, { inquiryPurpose: '다른 문의 목적' }), testGuide(body, { caseType: 'UNSUPPORTED' })]) {
    mock.mock.mockImplementation(async () => response(data));
    await assert.rejects(createIntakeApi().minimal(intake), /응답 형식/);
  }
  assert.throws(() => createIntakeApi().minimal({ id: 42 }));
});

test('Screen04 C05 protects display rendering against missing type/arrays and wrong condition linkage', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response({}));
  for (const data of [{ ...intake, caseType: undefined }, { ...intake, selectedDocuments: null }, { ...intake, clientPhoneMasked: {} }, { ...intake, lawyerSearchCondition: { ...intake.lawyerSearchCondition, intakeId: 77 } }]) {
    mock.mock.mockImplementation(async () => response(data));
    await assert.rejects(createIntakeApi().intake(42), /응답 형식/);
  }
});

test('Screen04 R06 validates the specific criminal supplementary-investigation reference contract', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async (url, init) => {
    assert.equal(url, '/api/legal/criminal/checklist/supplementary-investigation');
    assert.equal(init.headers.Authorization, undefined);
    return response(testCriminalChecklist);
  });
  assert.deepEqual(await createIntakeApi().criminalChecklist(), testCriminalChecklist);
  mock.mock.mockImplementation(async () => response({ ...testCriminalChecklist, domain: 'CIVIL' }));
  await assert.rejects(createIntakeApi().criminalChecklist(), /응답 형식/);
});

test('Screen04 search handoff passes intake ID to C06 and returned request ID to C07', async t => {
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, init) => { calls.push({ url, init }); return response(testSearch(42, 81), init.method === 'POST' ? 201 : 200); });
  const api = createIntakeApi({ getToken: () => 'test-token' });
  const created = await api.createSearch('42', '  검수 목적  ');
  await api.searchRequest('42', created.id);
  assert.equal(calls[0].url, '/api/intakes/42/search-requests');
  assert.deepEqual(JSON.parse(calls[0].init.body), { searchPurpose: '검수 목적' });
  assert.equal(calls[0].init.headers.Authorization, 'Bearer test-token');
  assert.equal(calls[1].url, '/api/intakes/search-requests/81');
  assert.throws(() => api.createSearch('0', ''));
  assert.throws(() => api.createSearch(42, '가'.repeat(301)));
});

test('Screen04 search handoff rejects mismatched inquiry/request IDs instead of showing another search', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => response(testSearch(43, 81)));
  const api = createIntakeApi();
  await assert.rejects(api.createSearch(42, ''), /응답 형식/);
  await assert.rejects(api.searchRequest(42, 81), /응답 형식/);
  mock.mock.mockImplementation(async () => response(testSearch(42, 82)));
  await assert.rejects(api.searchRequest(42, 81), /응답 형식/);
  assert.deepEqual(routeFromHash('#/intake/42/review'), { name: 'review', id: '42' });
  assert.deepEqual(routeFromHash('#/intake/42/search/81'), { name: 'search', id: '42', searchRequestId: '81' });
});
