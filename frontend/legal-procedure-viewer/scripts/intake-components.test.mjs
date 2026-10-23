// React rendering/effects tests with a small navigation/DOM adapter.
// These exercise source components, not a browser or the user's blocked tab.
import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { createServer } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
import { testIntake, testGuide, testCriminalChecklist, testSearch, testMemo, testDocument } from './intake-test-data.mjs';

let vite;
let App;
before(async () => {
  vite = await createServer({
    configFile: false,
    root: fileURLToPath(new URL('../', import.meta.url)),
    plugins: [react()],
    server: { middlewareMode: true },
    appType: 'custom',
    define: { 'import.meta.env.VITE_INTAKE_API_BASE': '""' },
  });
  App = (await vite.ssrLoadModule('/src/client/IntakeApp.jsx')).default;
});
after(async () => { await vite?.close(); });

const preview = { caseType: 'CIVIL', caseTypeLabel: '민사 사건', disputeArea: '검수 분쟁', recommendedDocuments: ['검수 자료'], clientGuideMessage: '검수 안내', classificationMethod: 'RULE_BASED', persisted: false };
const procedure = { code: 'qa-civil', domain: 'CIVIL', title: '검수 절차', summary: '검수용 요약', userRole: 'party' };
const intake = id => testIntake(id);
const response = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const text = node => typeof node === 'string' ? node : node.children.map(text).join('');
const button = (tree, name) => tree.root.findAllByType('button').find(node => text(node) === name);
const field = (tree, name) => tree.root.findAll(node => ['input', 'select', 'textarea'].includes(node.type) && node.props.name === name)[0];
const change = async (tree, name, value) => act(async () => { field(tree, name).props.onChange({ target: { name, value } }); });
const nextStep = '법적 절차가 어떻게 진행되는지 알고 싶어요.';
async function fillRequired(tree) {
  await act(async () => tree.root.findAllByType('input').find(node => node.props.value === '원고').props.onChange());
  await change(tree, 'desiredNextStep', nextStep);
}

async function mount(t, handler, { staff = false } = {}) {
  let bootstrap = false;
  const saved = Object.fromEntries(['window', 'document', 'FormData'].map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  const listeners = new Set();
  let hash = '#/intake/type';
  const location = {};
  Object.defineProperty(location, 'hash', {
    get: () => hash,
    set: value => { hash = value.startsWith('#') ? value : `#${value}`; listeners.forEach(listener => listener()); },
  });
  globalThis.window = { location, addEventListener: (name, listener) => listeners.add(listener), removeEventListener: (name, listener) => listeners.delete(listener), scrollTo() {} };
  globalThis.document = { title: '', querySelector: () => ({ focus() {}, setAttribute() {} }) };
  globalThis.FormData = class { constructor(values) { this.values = values; } get(key) { return this.values[key]; } };
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, init = {}) => {
    calls.push({ url, init });
    if (bootstrap && url === '/api/auth/login') return response({ accessToken: 'qa-staff-token', username: 'qa-admin', role: 'ROLE_ADMIN' });
    if (bootstrap && url === '/api/me') return response({ username: 'qa-admin', role: 'ROLE_ADMIN' });
    if (handler) {
      const result = await handler(url, init);
      if (result !== undefined) return result;
    }
    if (url === '/api/intakes/classify-preview') return response(preview);
    if (url === '/api/me') return response({ username: 'qa-user', role: 'ROLE_USER' });
    if (url.startsWith('/api/legal/procedures?') || url === '/api/legal/procedures') return response([procedure]);
    if (url === '/api/legal/procedures/qa-civil') return response({ ...procedure, steps: [], forms: [] });
    if (url === '/api/legal/procedures/qa-civil/steps' || url === '/api/legal/forms?procedure=qa-civil') return response([]);
    if (url === '/api/intakes') return response({ id: 42 }, 201);
    if (url === '/api/legal-intake/minimal') return response(testGuide(JSON.parse(init.body)));
    if (url === '/api/legal/criminal/checklist/supplementary-investigation') return response(testCriminalChecklist);
    if (/^\/api\/intakes\/\d+\/search-requests$/.test(url)) return init.method === 'POST' ? response(testSearch(url.split('/')[3]), 201) : response([]);
    if (/^\/api\/intakes\/search-requests\/\d+$/.test(url)) return response(testSearch(42, url.split('/').at(-1)));
    if (/^\/api\/intakes\/\d+$/.test(url)) return response(intake(Number(url.split('/').at(-1))));
    throw new Error(`Unexpected test request: ${url}`);
  });
  let tree;
  await act(async () => { tree = Renderer.create(React.createElement(App), { createNodeMock: () => ({ focus() {}, showModal() {} }) }); });
  t.after(async () => {
    await act(async () => tree.unmount());
    for (const [key, descriptor] of Object.entries(saved)) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor);
      else delete globalThis[key];
    }
  });
  if (staff) {
    bootstrap = true;
    await act(async () => button(tree, '로그인').props.onClick());
    await act(async () => tree.root.findByType('dialog').findByType('form').props.onSubmit({ preventDefault() {}, currentTarget: { username: 'qa-admin', password: 'fixture' } }));
    bootstrap = false; calls.length = 0;
  }
  return { tree, calls, navigate: async path => act(async () => { location.hash = path; }) };
}

const mountAsStaff = (t, handler) => mount(t, handler, { staff: true });

test('Permissions: anonymous detail is gated; client login sees no internal memo or management controls; logout clears data', async t => {
  const dto = testIntake(42, { consultationMemos: undefined, permissions: { canManage: false, canAssign: false } });
  const { tree, calls, navigate } = await mount(t, url => url === '/api/auth/login' ? response({ accessToken: 'qa-token', username: 'qa-user', role: 'ROLE_USER' }) : url === '/api/intakes/42' ? response(dto) : undefined);
  await navigate('/lawyer/intakes/42');
  assert.ok(text(tree.root).includes('로그인이 필요합니다.'));assert.ok(!calls.some(call => call.url === '/api/intakes/42'));
  await act(async () => button(tree, '로그인하고 계속하기').props.onClick());
  await act(async () => tree.root.findByType('dialog').findByType('form').props.onSubmit({ preventDefault() {}, currentTarget: { username: 'qa-user', password: 'fixture' } }));
  assert.ok(text(tree.root).includes('사건 개요'));
  assert.ok(!text(tree.root).includes('내부 검토 전용'));
  assert.equal(tree.root.findAllByType('a').filter(node => /상담 메모 관리|메모 관리하기/.test(text(node))).length,0);
  await navigate('/lawyer/intakes/42/consultation');assert.ok(text(tree.root).includes('담당 변호사 전용 기능입니다.'));
  await act(async () => button(tree, '로그아웃').props.onClick());assert.ok(text(tree.root).includes('로그인이 필요합니다.'));
});

test('Permissions: verified LAWYER identity routes to assigned list and is accepted by C01/P01 validation', async t => {
  const { tree, navigate }=await mount(t,url=>url==='/api/auth/login'?response({accessToken:'lawyer-jwt',username:'lawyer',role:'ROLE_LAWYER'}):url==='/api/me'?response({username:'lawyer',role:'ROLE_LAWYER'}):url==='/api/intakes'?response([]):undefined);
  await navigate('/login');await change(tree,'username','lawyer');await change(tree,'password','fixture');
  await act(async()=>tree.root.findByType('form').props.onSubmit({preventDefault(){},currentTarget:{username:'lawyer',password:'fixture'}}));
  assert.equal(window.location.hash,'#/lawyer/intakes');assert.ok(text(tree.root).includes('lawyer님'));
});

test('Permissions: only administrator response exposes assignment form and saves the current intake ID', async t => {
  let dto=testIntake(42,{assignedLawyerUsername:null,permissions:{canManage:true,canAssign:true}}),assigned;
  const {tree,navigate,calls}=await mountAsStaff(t,(url,init)=>{
    if(url==='/api/intakes/42/assignment'){assigned=JSON.parse(init.body);dto={...dto,assignedLawyerUsername:assigned.assignedLawyerUsername};return response(dto);}
    if(url==='/api/intakes/42')return response(dto);
  });
  await navigate('/lawyer/intakes/42');
  await act(async()=>tree.root.findByProps({id:'assigned-lawyer'}).props.onChange({target:{value:'lawyer'}}));
  const form=tree.root.findAllByType('form').find(node=>node.findAllByProps({id:'assigned-lawyer'}).length);
  await act(async()=>form.props.onSubmit({preventDefault(){}}));
  assert.deepEqual(assigned,{assignedLawyerUsername:'lawyer'});assert.equal(calls.filter(call=>call.url.endsWith('/assignment')).length,1);
  assert.ok(text(tree.root).includes('담당 변호사를 배정했습니다.'));
});

test('Permissions: a revoked staff write clears previously visible internal notes after 403', async t => {
  const {tree,navigate}=await mountAsStaff(t,(url,init)=>url==='/api/intakes/42'?response(testIntake(42,{consultationMemos:[testMemo()]})):url==='/api/intakes/42/memo'?response({},403):undefined);
  await navigate('/lawyer/intakes/42/consultation');
  assert.ok(text(tree.root).includes('검수용 보호된 상담 메모'));
  await change(tree,'memoContent','권한 확인 중 입력');
  const form=tree.root.findAllByType('form').find(node=>node.findAllByProps({name:'memoContent'}).length);
  await act(async()=>form.props.onSubmit({preventDefault(){}}));
  assert.ok(text(tree.root).includes('접근 권한이 없습니다.'));
  assert.ok(!text(tree.root).includes('검수용 보호된 상담 메모'));
  assert.equal(tree.root.findAllByType('textarea').length,0);
});

async function classify(tree) {
  await act(async () => {
    tree.root.findAllByType('input').find(node => node.props.value === 'CIVIL').props.onChange();
    tree.root.findByType('textarea').props.onChange({ target: { value: '검수용 계약 문의입니다.' } });
  });
  await act(async () => { await tree.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
}

test('React: blank validation, pending state and duplicate-submit lock', async t => {
  let release;
  const { tree, calls } = await mountAsStaff(t, url => url === '/api/intakes/classify-preview' ? new Promise(resolve => { release = resolve; }) : undefined);
  await act(async () => { await tree.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  assert.ok(text(tree.root).includes('현재 상황을 간단히 적어 주세요.'));
  assert.equal(calls.filter(call => call.url === '/api/intakes/classify-preview').length, 0);
  await act(async () => { tree.root.findByType('textarea').props.onChange({ target: { value: '검수 입력' } }); });
  let pending;
  await act(async () => {
    pending = tree.root.findByType('form').props.onSubmit({ preventDefault() {} });
    tree.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  assert.equal(calls.filter(call => call.url === '/api/intakes/classify-preview').length, 1);
  assert.equal(button(tree, '사건 유형 확인 중').props.disabled, true);
  assert.equal(button(tree, '다음: 문의 작성').props.disabled, true);
  await act(async () => { release(response(preview)); await pending; });
  assert.equal(button(tree, '다음: 문의 작성').props.disabled, false);
});

test('React: 100-character story survives classification and writing; 101 fails before fetch', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t);
  const story = '민사' + '가'.repeat(98);
  assert.equal(tree.root.findByType('textarea').props.maxLength, 100);
  await act(async () => tree.root.findByType('textarea').props.onChange({ target: { value: story } }));
  await act(async () => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }));
  const sent = calls.filter(call => call.url === '/api/intakes/classify-preview');
  assert.equal(sent.length, 1); assert.equal(JSON.parse(sent[0].init.body).coreStatement, story);
  assert.ok(text(tree.root).includes('100 / 100'));
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  assert.equal(field(tree, 'coreStatement').props.value, story);
  assert.equal(field(tree, 'coreStatement').props.maxLength, 100);
  await navigate('/intake/type');
  assert.equal(tree.root.findByType('textarea').props.value, story);
  await act(async () => tree.root.findByType('textarea').props.onChange({ target: { value: story + '가' } }));
  await act(async () => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }));
  assert.ok(text(tree.root).includes('100자 이내'));
  assert.equal(calls.filter(call => call.url === '/api/intakes/classify-preview').length, 1);
});

test('React: navigation preserves purpose, extra fields and documents; create uses returned ID', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t);
  await act(async () => { tree.root.findAllByType('input').find(node => node.props.value === '문서 검토').props.onChange(); });
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  assert.ok(text(tree.root).includes('문서 검토'));
  await fillRequired(tree);
  await change(tree, 'clientName', '검수 이름');
  await act(async () => tree.root.findAllByType('input').find(node => node.props.type === 'checkbox').props.onChange({ target: { checked: true } }));
  await navigate('/intake/type');
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  await navigate('/help');
  await navigate('/intake/new');
  assert.equal(tree.root.findAllByType('input').find(node => node.props.value === '원고').props.checked, true);
  assert.equal(field(tree, 'desiredNextStep').props.value, nextStep);
  assert.equal(field(tree, 'clientName').props.value, '검수 이름');
  assert.equal(tree.root.findAllByType('input').find(node => node.props.name === 'documents').props.checked, true);
  await act(async () => { await tree.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  const created = calls.find(call => call.url === '/api/intakes');
  assert.deepEqual(JSON.parse(created.init.body).documents, ['계약서']);
  assert.equal(JSON.parse(created.init.body).inquiryPurpose, '문서 검토');
  assert.equal(window.location.hash, '#/intake/42');
  assert.ok(calls.some(call => call.url === '/api/intakes/42'));
  assert.ok(text(tree.root).includes('문의가 접수되었습니다.'));
});

test('React: failed login keeps dialog; successful login sends bearer; C03 401 clears identity without losing form', async t => {
  let loginCalls = 0;
  const { tree, calls } = await mount(t, url => {
    if (url === '/api/auth/login') return response(++loginCalls === 1 ? {} : { accessToken: 'qa-token', username: 'qa-user', role: 'ROLE_USER' }, loginCalls === 1 ? 401 : 200);
    if (url === '/api/intakes') return response({}, 401);
  });
  await act(async () => button(tree, '로그인').props.onClick());
  const event = { preventDefault() {}, currentTarget: { username: 'qa-user', password: 'qa-password' } };
  await act(async () => { await tree.root.findByType('dialog').findByType('form').props.onSubmit(event); });
  assert.ok(text(tree.root).includes('아이디 또는 비밀번호가 올바르지 않습니다.'));
  await act(async () => { await tree.root.findByType('dialog').findByType('form').props.onSubmit(event); });
  assert.equal(tree.root.findAllByType('dialog').length, 0);
  assert.ok(text(tree.root).includes('qa-user님'));
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  await fillRequired(tree);
  await change(tree, 'clientName', '보존할 이름');
  await act(async () => { await tree.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  assert.equal(calls.find(call => call.url === '/api/intakes').init.headers.Authorization, 'Bearer qa-token');
  assert.ok(text(tree.root).includes('비회원'));
  assert.equal(field(tree, 'clientName').props.value, '보존할 이름');
  assert.equal(calls.filter(call => call.url === '/api/intakes').length, 1);
});

test('React: direct-write guard, unknown route and receipt-ID switch never show old record', async t => {
  let release;
  const { tree, navigate } = await mountAsStaff(t, url => url === '/api/intakes/43' ? new Promise(resolve => { release = resolve; }) : undefined);
  await navigate('/intake/new');
  assert.ok(text(tree.root).includes('사건 유형부터 확인해 주세요.'));
  await navigate('/unimplemented');
  assert.ok(text(tree.root).includes('페이지를 찾을 수 없습니다.'));
  await navigate('/intake/42');
  assert.ok(text(tree.root).includes('No. 42'));
  await navigate('/intake/43');
  assert.ok(!text(tree.root).includes('No. 42'));
  assert.ok(text(tree.root).includes('등록된 문의를 확인하고 있습니다.'));
  await act(async () => { release(response(intake(43))); });
  assert.ok(text(tree.root).includes('No. 43'));
});

test('Screen03 React: required/phone/101-char errors prevent POST; optional fields and edited 100 chars survive', async t => {
  const { tree, calls } = await mountAsStaff(t);
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  const submit = () => tree.root.findByType('form').props.onSubmit({ preventDefault() {} });
  await act(submit);
  assert.ok(text(tree.root).includes('당사자 위치를 선택해 주세요.'));
  assert.equal(calls.filter(call => call.url === '/api/intakes').length, 0);
  await fillRequired(tree);
  await change(tree, 'clientPhone', 'not-a-phone');
  await change(tree, 'coreStatement', '가'.repeat(101));
  await act(submit);
  assert.equal(field(tree, 'clientPhone').props['aria-invalid'], true);
  assert.equal(field(tree, 'coreStatement').props['aria-invalid'], true);
  assert.equal(calls.filter(call => call.url === '/api/intakes').length, 0);
  await change(tree, 'clientPhone', '010-0000-0000');
  await change(tree, 'coreStatement', '가'.repeat(100));
  await change(tree, 'clientName', '가상 검수인');
  await change(tree, 'incidentDate', '2024-02-29');
  await change(tree, 'counterpart', '가상 회사');
  await change(tree, 'preferredRegion', '서울');
  await act(async () => button(tree, '변호사 검색 선호 조건 (선택)').props.onClick());
  await change(tree, 'officeType', '법무법인');
  await change(tree, 'consultationStyle', '전화 상담');
  await change(tree, 'lawyerSearchPurpose', '개발 검수용 상담 목적');
  await act(submit);
  const body = JSON.parse(calls.find(call => call.url === '/api/intakes').init.body);
  assert.equal(body.coreStatement.length, 100);
  assert.equal(body.clientPhone, '010-0000-0000');
  assert.equal(body.incidentDate, '2024-02-29');
  assert.equal(body.officeType, '법무법인');
  assert.equal(body.lawyerSearchPurpose, '개발 검수용 상담 목적');
  assert.ok(!('searchOpen' in body));
});

test('Screen03 React: pending registration locks all fields and previous navigation; only one POST', async t => {
  let release;
  const { tree, calls } = await mountAsStaff(t, url => url === '/api/intakes' ? new Promise(resolve => { release = resolve; }) : undefined);
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  await fillRequired(tree);
  let pending;
  await act(async () => {
    pending = tree.root.findByType('form').props.onSubmit({ preventDefault() {} });
    tree.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  assert.equal(button(tree, '문의 접수 중').props.disabled, true);
  assert.equal(button(tree, '이전').props.disabled, true);
  assert.equal(tree.root.findByType('form').props['aria-busy'], true);
  assert.equal(tree.root.findAllByType('fieldset').find(node => node.props.className === 'write-controls').props.disabled, true);
  assert.equal(calls.filter(call => call.url === '/api/intakes').length, 1);
  await act(async () => { release(response({ id: '314' }, 201)); await pending; });
  assert.equal(window.location.hash, '#/intake/314');
  assert.ok(calls.some(call => call.url === '/api/intakes/314'));
});

test('Screen03 React: C03 400/500 preserves fields, maps field errors and recovers on explicit resubmit', async t => {
  let count = 0;
  const { tree, calls } = await mountAsStaff(t, url => url === '/api/intakes' ? (++count === 1 ? response({ fieldErrors: { counterpart: 'invalid' } }, 400) : count === 2 ? response({}, 500) : response({ id: 42 }, 201)) : undefined);
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  await fillRequired(tree);
  await change(tree, 'counterpart', '보존할 가상 회사');
  const submit = () => tree.root.findByType('form').props.onSubmit({ preventDefault() {} });
  await act(submit);
  assert.equal(field(tree, 'counterpart').props['aria-invalid'], true);
  assert.equal(field(tree, 'counterpart').props.value, '보존할 가상 회사');
  await act(submit);
  assert.ok(text(tree.root).includes('서버에서 요청을 처리하지 못했습니다.'));
  assert.equal(field(tree, 'counterpart').props.value, '보존할 가상 회사');
  assert.equal(calls.filter(call => call.url === '/api/intakes').length, 2);
  await act(submit);
  assert.ok(text(tree.root).includes('문의가 접수되었습니다.'));
});

const referenceStep = { stepOrder: 1, stepCode: 'qa-step', title: '첫 검수 단계', description: '검수 단계 설명', checklist: ['검수 준비 항목'] };
const referenceForm = { procedureCode: 'qa-civil', formCode: 'qa-form', title: '검수 양식 안내', fileType: 'pdf', usageNote: '검수 양식 사용 설명' };

test('Screen02/03 React: R02 rejects a detail from a different domain even with the correct procedure code', async t => {
  const { tree } = await mountAsStaff(t, url => url === '/api/legal/procedures/qa-civil'
    ? response({ ...procedure, domain: 'CRIMINAL', title: '잘못 연결된 형사 절차', steps: [], forms: [] }) : undefined);
  await classify(tree);
  assert.ok(text(tree.root).includes('서버 응답 형식을 확인할 수 없습니다.'));
  assert.ok(!text(tree.root).includes('잘못 연결된 형사 절차'));
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  assert.ok(text(tree.root).includes('서버 응답 형식을 확인할 수 없습니다.'));
  assert.ok(!text(tree.root).includes('잘못 연결된 형사 절차'));
  await fillRequired(tree);
  assert.equal(button(tree, '문의 접수하기').props.disabled, false);
});

test('Screen02/03 React: UNKNOWN labels all-domain references and verifies detail against the selected R01 row', async t => {
  const { tree, calls } = await mountAsStaff(t, url => url === '/api/intakes/classify-preview'
    ? response({ ...preview, caseType: 'UNKNOWN', caseTypeLabel: '미분류' }) : undefined);
  assert.ok(text(tree.root).includes('전체 분야의 참고 절차를 표시합니다.'));
  await act(async () => tree.root.findByType('textarea').props.onChange({ target: { value: '검수 상황입니다.' } }));
  await act(async () => { await tree.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  assert.ok(text(tree.root).includes('전체 분야의 참고 절차를 표시합니다.'));
  assert.ok(text(tree.root).includes(procedure.title));
  assert.ok(calls.filter(call => call.url.startsWith('/api/legal/procedures')).every(call => !call.url.includes('domain=UNKNOWN')));
});

test('Screen03 React: R02/R03/R04/R05 use returned codes; checklist and form fold keep state', async t => {
  const { tree, calls } = await mountAsStaff(t, url => {
    if (url === '/api/legal/procedures/qa-civil') return response({ ...procedure, steps: [referenceStep], forms: [referenceForm] });
    if (url.endsWith('/steps')) return response([referenceStep]);
    if (url === '/api/legal/forms?procedure=qa-civil') return response([referenceForm]);
    if (url === '/api/legal/forms/qa-form') return response(referenceForm);
  });
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  assert.ok(text(tree.root).includes(referenceForm.usageNote));
  await act(async () => button(tree, '단계별 체크리스트').props.onClick());
  const check = () => tree.root.findAllByType('input').find(node => node.props.type === 'checkbox' && !node.props.name);
  await act(async () => check().props.onChange({ target: { checked: true } }));
  await act(async () => button(tree, '단계별 체크리스트').props.onClick());
  await act(async () => button(tree, '단계별 체크리스트').props.onClick());
  assert.equal(check().props.checked, true);
  assert.equal(calls.filter(call => call.url.endsWith('/steps')).length, 1);
  await act(async () => button(tree, '관련 양식 안내').props.onClick());
  await act(async () => button(tree, '관련 양식 안내').props.onClick());
  assert.equal(calls.filter(call => call.url === '/api/legal/forms/qa-form').length, 1);
  assert.ok(calls.some(call => call.url === '/api/legal/forms?procedure=qa-civil'));
});

test('Screen03 React: empty reference lists show messages and do not block registration', async t => {
  const { tree } = await mountAsStaff(t);
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  assert.ok(text(tree.root).includes('등록된 절차 단계가 없습니다.'));
  assert.ok(text(tree.root).includes('이 절차에 등록된 관련 양식이 없습니다.'));
  await act(async () => button(tree, '단계별 체크리스트').props.onClick());
  assert.ok(text(tree.root).includes('등록된 단계별 체크리스트가 없습니다.'));
  await fillRequired(tree);
  assert.equal(button(tree, '문의 접수하기').props.disabled, false);
});

test('Screen03 React: changing case type cancels outdated detail/forms; no invented procedure for empty domain', async t => {
  let release;
  let write = false;
  const { tree, calls } = await mountAsStaff(t, url => {
    if (write && url === '/api/legal/forms?procedure=qa-civil') return new Promise(resolve => { release = resolve; });
    if (url === '/api/legal/procedures?domain=FAMILY') return response([]);
  });
  await classify(tree);
  write = true;
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  assert.ok(text(tree.root).includes('관련 양식을 불러오는 중입니다.'));
  await change(tree, 'caseType', 'FAMILY');
  assert.ok(text(tree.root).includes('등록된 참고 절차와 양식이 없습니다.'));
  await act(async () => release(response([referenceForm])));
  assert.ok(!text(tree.root).includes(referenceForm.title));
  assert.ok(!calls.some(call => call.url === '/api/legal/forms/qa-form'));
  assert.equal(field(tree, 'coreStatement').props.value, '검수용 계약 문의입니다.');
});

test('Screen03 React: reference fetch failure retries independently without losing typed form', async t => {
  let attempts = 0;
  let write = false;
  const { tree } = await mountAsStaff(t, url => write && url === '/api/legal/forms?procedure=qa-civil' ? (++attempts === 1 ? response({}, 503) : response([referenceForm])) : url === '/api/legal/forms/qa-form' ? response(referenceForm) : undefined);
  await classify(tree);
  write = true;
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  await change(tree, 'clientName', '검수값 유지');
  assert.ok(text(tree.root).includes('서버에서 요청을 처리하지 못했습니다.'));
  await act(async () => button(tree, '다시 시도').props.onClick());
  assert.ok(text(tree.root).includes(referenceForm.title));
  assert.equal(field(tree, 'clientName').props.value, '검수값 유지');
});

test('Screen03 React: no-documents choice is exclusive and never claims a file upload', async t => {
  const { tree, calls } = await mountAsStaff(t);
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  const documents = () => tree.root.findAllByType('input').filter(node => node.props.name === 'documents');
  await act(async () => documents()[0].props.onChange({ target: { checked: true } }));
  await act(async () => documents().at(-1).props.onChange({ target: { checked: true } }));
  assert.equal(documents().filter(node => node.props.checked).length, 1);
  assert.equal(documents().at(-1).props.checked, true);
  await fillRequired(tree);
  await act(async () => { await tree.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  assert.deepEqual(JSON.parse(calls.find(call => call.url === '/api/intakes').init.body).documents, []);
  assert.ok(!calls.some(call => call.url.includes('/documents')));
});

test('shared navigation: repeating the same classification preserves edited Screen03 required fields', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t);
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  await fillRequired(tree);
  await change(tree, 'inquiryPurpose', '문서 검토');
  await change(tree, 'caseType', 'FAMILY');
  await change(tree, 'coreStatement', '화면03에서 보완한 상세 설명 '.repeat(3));
  await change(tree, 'clientName', '보존할 가상 이름');
  const edited = field(tree, 'coreStatement').props.value;
  await navigate('/intake/type');
  await act(async () => { await tree.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  assert.equal(field(tree, 'coreStatement').props.value, edited);
  assert.equal(field(tree, 'caseType').props.value, 'FAMILY');
  assert.equal(field(tree, 'inquiryPurpose').props.value, '문서 검토');
  assert.equal(field(tree, 'clientName').props.value, '보존할 가상 이름');
  await act(async () => { await tree.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  const body = JSON.parse(calls.find(call => call.url === '/api/intakes').init.body);
  assert.equal(body.coreStatement, edited.trim());
  assert.equal(body.caseType, 'FAMILY');
});

test('shared routing: header stays active for receipt and unknown route has the correct title', async t => {
  const { tree, navigate } = await mountAsStaff(t);
  await navigate('/intake/42');
  const entry = tree.root.findAllByType('a').find(node => text(node) === '사건 접수');
  assert.equal(entry.props['aria-current'], 'page');
  await navigate('/unimplemented');
  assert.equal(document.title, '페이지를 찾을 수 없습니다 | Legal Intake');
});

test('shared navigation: explicitly changed Screen02 content replaces only that field after reclassification', async t => {
  const { tree, navigate } = await mountAsStaff(t);
  await classify(tree);
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  await change(tree, 'coreStatement', '화면03 보완 내용');
  await change(tree, 'inquiryPurpose', '문서 검토');
  await change(tree, 'clientName', '선택 정보 유지');
  await navigate('/intake/type');
  await act(async () => tree.root.findByType('textarea').props.onChange({ target: { value: '화면02에서 새로 바꾼 계약 문의' } }));
  await act(async () => { await tree.root.findByType('form').props.onSubmit({ preventDefault() {} }); });
  await act(async () => button(tree, '다음: 문의 작성').props.onClick());
  assert.equal(field(tree, 'coreStatement').props.value, '화면02에서 새로 바꾼 계약 문의');
  assert.equal(field(tree, 'inquiryPurpose').props.value, '문서 검토');
  assert.equal(field(tree, 'clientName').props.value, '선택 정보 유지');
});

test('Screen04 React: C05 protected fields and real status render; expand never requests raw data', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t, url => url === '/api/intakes/42' ? response(testIntake(42, { status: 'NEED_MORE_INFO', clientNameMasked: '검**', clientPhoneMasked: '010-0***-****', coreStatementMasked: '보호된 핵심 내용 '.repeat(40) })) : undefined);
  await navigate('/intake/42');
  assert.ok(text(tree.root).includes('추가 자료 필요'));
  assert.ok(text(tree.root).includes('검**'));
  assert.ok(text(tree.root).includes('010-0***-****'));
  assert.ok(text(tree.root).includes('간단 안내'));
  const before = calls.length;
  await act(async () => button(tree, '더 보기').props.onClick());
  assert.equal(button(tree, '접기').props['aria-expanded'], true);
  assert.equal(calls.length, before);
  assert.ok(!calls.some(call => /raw|unmask|decrypt/.test(call.url)));
  assert.equal(calls.filter(call => call.url === '/api/legal-intake/minimal').length, 1);
});

test('Screen04 React: guide loading/error/retry is independent of the saved inquiry', async t => {
  let release;
  let attempts = 0;
  let guideBody;
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => {
    if (url === '/api/legal-intake/minimal') {
      guideBody = JSON.parse(init.body);
      return ++attempts === 1 ? new Promise(resolve => { release = resolve; }) : response(testGuide(guideBody));
    }
  });
  await navigate('/intake/42');
  assert.ok(text(tree.root).includes('No. 42'));
  assert.ok(text(tree.root).includes('간단 안내를 준비하고 있습니다.'));
  await act(async () => release(response({}, 500)));
  assert.ok(text(tree.root).includes('서버에서 요청을 처리하지 못했습니다.'));
  assert.ok(text(tree.root).includes('접수된 문의 내용'));
  await act(async () => button(tree, '다시 시도').props.onClick());
  assert.ok(text(tree.root).includes('확인된 내용'));
  assert.equal(calls.filter(call => call.url === '/api/intakes/42').length, 1);
  assert.equal(attempts, 2);
});

test('Screen04 React: empty guide/reference lists render meaningful empty states without invented procedures', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => url === '/api/legal-intake/minimal' ? response(testGuide(JSON.parse(init.body), { checkedFacts: [], recommendedDocuments: [] })) : url.startsWith('/api/legal/procedures') ? response([]) : undefined);
  await navigate('/intake/42');
  assert.ok(text(tree.root).includes('정리할 내용이 없습니다.'));
  assert.ok(text(tree.root).includes('추천 준비 자료가 없습니다.'));
  assert.ok(text(tree.root).includes('등록된 참고 절차가 없습니다.'));
  assert.ok(!calls.some(call => call.url === '/api/legal/procedures/qa-civil'));
  const prepare = tree.root.findAllByType('a').find(node => text(node) === '검색 요청 준비하기');
  assert.equal(prepare.props.href, '#/intake/42/search');
});

test('Screen04 React: R02 opens the server procedure code and R06 is limited to criminal context', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t, url => url === '/api/intakes/43' ? response(testIntake(43, { caseType: 'CRIMINAL' })) : url === '/api/legal/procedures?domain=CRIMINAL' ? response([]) : undefined);
  await navigate('/intake/42');
  await act(async () => button(tree, '검수 절차').props.onClick());
  assert.ok(calls.some(call => call.url === '/api/legal/procedures/qa-civil'));
  assert.ok(!calls.some(call => call.url.includes('/criminal/checklist')));
  await navigate('/intake/43');
  assert.ok(calls.some(call => call.url.endsWith('/criminal/checklist/supplementary-investigation')));
  assert.ok(text(tree.root).includes('검찰 송치 후 보완수사 단계 · 고소인 참고용'));
});

test('Screen04 React: previous is read-only C05 review; new inquiry clears draft and does not repeat C03', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t);
  await classify(tree);
  await navigate('/intake/42');
  const previous = tree.root.findAllByType('a').find(node => node.props['aria-label'] === '이전: 접수 내용 확인');
  assert.equal(previous.props.href, '#/intake/42/review');
  await navigate('/intake/42/review');
  assert.ok(text(tree.root).includes('접수된 문의 내용을 확인해 주세요.'));
  assert.equal(tree.root.findAllByType('form').length, 0);
  await act(async () => button(tree, '새 문의 작성').props.onClick());
  assert.equal(window.location.hash, '#/intake/type');
  assert.equal(tree.root.findByType('textarea').props.value, '');
  assert.equal(button(tree, '다음: 문의 작성').props.disabled, true);
  assert.ok(!calls.some(call => call.url === '/api/intakes'));
});

test('Screen04 React: malformed receipt is rejected before guide/reference calls and retry keeps returned inquiry ID', async t => {
  let valid = false;
  const { tree, calls, navigate } = await mountAsStaff(t, url => url === '/api/intakes/42' ? response(testIntake(42, valid ? { status: 'CLOSED' } : { status: 'UNKNOWN_STATUS' })) : undefined);
  await navigate('/intake/42'); assert.ok(text(tree.root).includes('응답 형식')); assert.ok(!text(tree.root).includes('문의가 접수되었습니다.'));
  assert.ok(!calls.some(call => call.url === '/api/legal-intake/minimal')); valid = true;
  await act(async () => button(tree, '다시 시도').props.onClick());
  assert.ok(text(tree.root).includes('종료')); assert.ok(tree.root.findAllByType('span').some(node => node.props.className === 'receipt-status status-closed'));
  assert.equal(tree.root.findAllByType('a').find(node => text(node) === '검색 요청 준비하기').props.href, '#/intake/42/search');
});
test('Screen04 React: UNKNOWN explicitly labels all-domain references; switching receipt cancels stale guide and procedure reads', async t => {
  let guideSignal; let detailSignal;
  const { tree, navigate } = await mountAsStaff(t, (url, init) => url === '/api/legal-intake/minimal' && JSON.parse(init.body).sessionId === 'pending-session' ? new Promise((resolve, reject) => { guideSignal = init.signal; init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))); }) : url === '/api/intakes/42' ? response(testIntake(42, { sessionId: 'pending-session', caseType: 'UNKNOWN' })) : url === '/api/legal/procedures/qa-civil' ? new Promise((resolve, reject) => { detailSignal = init.signal; init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))); }) : undefined);
  await navigate('/intake/42'); assert.ok(text(tree.root).includes('전체 참고 절차를 표시합니다.'));
  await act(async () => button(tree, '검수 절차').props.onClick()); assert.equal(guideSignal.aborted, false); assert.equal(detailSignal.aborted, false);
  await navigate('/intake/43'); assert.equal(guideSignal.aborted, true); assert.equal(detailSignal.aborted, true); assert.ok(text(tree.root).includes('No. 43')); assert.ok(!text(tree.root).includes('No. 42'));
});

test('Screen04 React: search preparation saves once and routes with the returned request ID to C07', async t => {
  let release;
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => url === '/api/intakes/42/search-requests' && init.method === 'POST' ? new Promise(resolve => { release = resolve; }) : undefined);
  await navigate('/intake/42/search');
  await act(async () => tree.root.findByType('textarea').props.onChange({ target: { value: '검수할 검색 목적' } }));
  let pending;
  await act(async () => {
    pending = tree.root.findByType('form').props.onSubmit({ preventDefault() {} });
    tree.root.findByType('form').props.onSubmit({ preventDefault() {} });
  });
  assert.equal(button(tree, '검색 요청 저장 중').props.disabled, true);
  assert.equal(calls.filter(call => call.url === '/api/intakes/42/search-requests' && call.init.method === 'POST').length, 1);
  await act(async () => { release(response(testSearch(42, 81), 201)); await pending; });
  assert.equal(window.location.hash, '#/intake/42/search/81');
  assert.ok(calls.some(call => call.url === '/api/intakes/search-requests/81'));
  assert.ok(text(tree.root).includes('검색 요청을 기록했습니다. 외부 검색은 실행되지 않았습니다.'));
  assert.equal(button(tree, '외부 검색 실행').props.disabled, false);
});

const searchResult = overrides => ({ id: 101, searchRequestId: 81, sourceType: 'CASE_LAW', title: '검수 결과 링크', url: 'https://example.com/qa', snippet: '가상 자료 요약', displayOrder: 1, createdAt: '2026-10-21T11:00:00', ...overrides });
const searchForm = (tree, name) => tree.root.findAllByType('form').find(node => node.props['aria-label'] === name);
const submitSearch = (tree, name) => act(async () => { await searchForm(tree, name).props.onSubmit({ preventDefault() {} }); });
async function loginSearch(tree) {
  const trigger = button(tree, '로그인하여 이력 보기') || button(tree, '로그인하고 계속하기');
  if (!trigger) return;
  await act(async () => trigger.props.onClick());
  await act(async () => tree.root.findByType('dialog').findByType('form').props.onSubmit({ preventDefault() {}, currentTarget: { username: 'qa-user', password: 'qa-password' } }));
}
const searchLogin = url => url === '/api/auth/login' ? response({ accessToken: 'qa-token', username: 'qa-user', role: 'ROLE_USER' }) : undefined;

test('Screen05 React: P02 is gated by login, history selection uses actual C07 ID, old condition snapshots remain readable', async t => {
  const { tree, navigate, calls } = await mount(t, (url, init) => searchLogin(url) || (url === '/api/intakes/42/search-requests' && init.method === 'GET' ? response([testSearch(42, 82)]) : url === '/api/intakes/search-requests/82' ? response(testSearch(42, 82, { conditionId: 89 })) : undefined));
  await navigate('/intake/42/search'); assert.ok(text(tree.root).includes('로그인이 필요합니다.'));
  assert.ok(!calls.some(call => call.url === '/api/intakes/42/search-requests'));
  await loginSearch(tree);
  assert.equal(calls.find(call => call.url === '/api/intakes/42/search-requests').init.headers.Authorization, 'Bearer qa-token');
  await act(async () => tree.root.findAllByType('input').find(node => node.props['aria-label'] === '검색 요청 82 선택').props.onChange());
  assert.equal(window.location.hash, '#/intake/42/search/82'); assert.ok(text(tree.root).includes('이 요청은 이전에 저장된 조건 #89'));
  assert.equal(button(tree, '외부 검색 실행').props.disabled, false); assert.ok(!calls.some(call => call.url.includes('/execute')));
});
test('Screen05 React: P02 403 is isolated from selected C07 and request creation; selected ID change hides stale results', async t => {
  let release;
  const { tree, navigate } = await mountAsStaff(t, (url, init) => searchLogin(url) || (url === '/api/intakes/42/search-requests' && init.method === 'GET' ? response({}, 403) : url.endsWith('/82') ? new Promise(resolve => { release = resolve; }) : undefined));
  await navigate('/intake/42/search/81'); await loginSearch(tree);
  assert.ok(text(tree.root).includes('접근 권한이 없습니다.')); assert.ok(text(tree.root).includes('SR-81')); assert.equal(button(tree, '검색 요청 기록하기').props.disabled, false);
  await navigate('/intake/42/search/82'); assert.ok(!text(tree.root).includes('SR-81')); assert.ok(text(tree.root).includes('선택한 검색 요청을 불러오는 중'));
  await act(async () => release(response(testSearch(42, 82)))); assert.ok(text(tree.root).includes('SR-82'));
});
test('Screen05 React: blank/unsafe result validation prevents POST; C08 locks duplicate submissions, refreshes and clears only after ack', async t => {
  let saved = testSearch(); let release;
  const { tree, navigate, calls } = await mountAsStaff(t, (url, init) => url.endsWith('/81/results') ? new Promise(resolve => { release = resolve; }) : url === '/api/intakes/search-requests/81' ? response(saved) : undefined);
  await navigate('/intake/42/search/81'); await submitSearch(tree, '검색 요청 81 결과 링크 기록');
  assert.ok(text(tree.root).includes('출처 유형 항목을 입력해 주세요.')); assert.ok(!calls.some(call => call.init.method === 'POST'));
  await change(tree, 'sourceType', 'CASE_LAW'); await change(tree, 'title', '검수 결과 링크'); await change(tree, 'url', 'javascript:alert(1)');
  await submitSearch(tree, '검색 요청 81 결과 링크 기록'); assert.ok(text(tree.root).includes('올바른 http://'));
  await change(tree, 'url', 'https://example.com/qa'); let pending;
  await act(async () => { pending = searchForm(tree, '검색 요청 81 결과 링크 기록').props.onSubmit({ preventDefault() {} }); searchForm(tree, '검색 요청 81 결과 링크 기록').props.onSubmit({ preventDefault() {} }); });
  assert.equal(calls.filter(call => call.url.endsWith('/results')).length, 1); assert.equal(button(tree, '검색 요청 기록하기').props.disabled, true);
  saved = testSearch(42, 81, { results: [searchResult()] }); await act(async () => { release(response(saved, 201)); await pending; });
  assert.equal(field(tree, 'title').props.value, ''); assert.ok(text(tree.root).includes('결과 링크를 저장했습니다.')); assert.ok(text(tree.root).includes('검색 준비 완료'));
  const link = tree.root.findAllByType('a').find(node => node.props.href === 'https://example.com/qa'); assert.equal(link.props.target, '_blank'); assert.equal(link.props.rel, 'noopener noreferrer');
});
test('Screen05 React: C08 ack remains success when reread fails; explicit GET retry never repeats POST', async t => {
  let reads = 0; const saved = testSearch(42, 81, { results: [searchResult()] });
  const { tree, navigate, calls } = await mountAsStaff(t, url => url.endsWith('/81/results') ? response(saved, 201) : url === '/api/intakes/search-requests/81' ? ++reads === 1 ? response(testSearch()) : response({}, 500) : undefined);
  await navigate('/intake/42/search/81'); await change(tree, 'sourceType', 'CASE_LAW'); await change(tree, 'title', '검수'); await change(tree, 'url', 'https://example.com/qa');
  await submitSearch(tree, '검색 요청 81 결과 링크 기록'); assert.ok(text(tree.root).includes('결과 링크를 저장했습니다.')); assert.ok(text(tree.root).includes('검수 결과 링크')); assert.equal(field(tree, 'title').props.value, '');
  await act(async () => button(tree, '다시 시도').props.onClick()); assert.equal(calls.filter(call => call.url.endsWith('/results')).length, 1); assert.ok(reads >= 3);
});
test('Screen05 React: failed C08 preserves draft; uncertain save requires latest GET and explicit continuation, no auto resend', async t => {
  let writes = 0;
  const { tree, navigate } = await mountAsStaff(t, url => url.endsWith('/81/results') ? response({}, ++writes === 1 ? 400 : 500) : undefined);
  await navigate('/intake/42/search/81'); await change(tree, 'sourceType', 'CASE_LAW'); await change(tree, 'title', '보존할 제목'); await change(tree, 'url', 'https://example.com/qa');
  await submitSearch(tree, '검색 요청 81 결과 링크 기록'); assert.equal(field(tree, 'title').props.value, '보존할 제목');
  await submitSearch(tree, '검색 요청 81 결과 링크 기록'); assert.equal(writes, 2); assert.ok(text(tree.root).includes('서버의 저장 여부를 확인할 수 없습니다.'));
  await submitSearch(tree, '검색 요청 81 결과 링크 기록'); assert.equal(writes, 2);
  await act(async () => button(tree, '최신 저장 정보 조회').props.onClick()); assert.equal(writes, 2);
  await act(async () => button(tree, '중복 가능성 확인 후 편집 계속').props.onClick()); assert.equal(field(tree, 'title').props.value, '보존할 제목'); assert.equal(button(tree, '이 결과 추가하기').props.disabled, false);
});
test('Screen05 React: result sorting/copy fallback and unsafe legacy link rendering use stored data only', async t => {
  const saved = testSearch(42, 81, { results: [searchResult({ id: 102, title: '이전 기록', displayOrder: 1 }), searchResult({ id: 103, title: '새 기록', displayOrder: 2, createdAt: '2026-10-21T12:00:00', url: 'javascript:alert(1)' })] });
  const { tree, navigate } = await mountAsStaff(t, url => url === '/api/intakes/search-requests/81' ? response(saved) : undefined);
  await navigate('/intake/42/search/81');
  assert.ok(!tree.root.findAllByType('a').some(node => node.props.href?.startsWith('javascript:')));
  const sort = tree.root.findAllByType('select').find(node => node.props['aria-label'] === '결과 정렬'); await act(async () => sort.props.onChange({ target: { value: 'newest' } }));
  const ordered = tree.root.findAllByType('ol').find(node => node.props.className === 'search05-results'); assert.ok(text(ordered).indexOf('새 기록') < text(ordered).indexOf('이전 기록'));
  await act(async () => button(tree, '요청 내용 복사').props.onClick());
  assert.ok(tree.root.findAllByType('textarea').some(node => node.props.readOnly && node.props.value.includes('검색 요청 #81')));
});

test('Screen05 React: C05 loading/error/retry and missing condition cannot create a request; C07 can still display an existing snapshot', async t => {
  let release; let reads = 0;
  const { tree, navigate, calls } = await mountAsStaff(t, url => url === '/api/intakes/42' ? ++reads === 1 ? new Promise(resolve => { release = resolve; }) : response(testIntake(42, { lawyerSearchCondition: null })) : undefined);
  await navigate('/intake/42/search/81'); assert.ok(text(tree.root).includes('접수된 문의와 검색 조건을 불러오는 중'));
  await act(async () => release(response({}, 404))); assert.ok(text(tree.root).includes('요청한 정보를 찾을 수 없습니다.')); assert.ok(text(tree.root).includes('SR-81'));
  await act(async () => button(tree, '다시 시도').props.onClick()); assert.ok(text(tree.root).includes('저장된 검색 조건이 없어'));
  assert.equal(button(tree, '검색 요청 기록하기'), undefined); assert.ok(!calls.some(call => call.init.method === 'POST'));
});
test('Screen05 React: uncertain C06 keeps purpose and requires protected history review; unmount aborts an in-flight C07', async t => {
  let hangingSignal;
  const { tree, navigate, calls } = await mountAsStaff(t, (url, init) => url === '/api/intakes/42/search-requests' && init.method === 'POST' ? response({}, 500) : url === '/api/intakes/search-requests/82' ? new Promise((resolve, reject) => { hangingSignal = init.signal; init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))); }) : undefined);
  await navigate('/intake/42/search'); await change(tree, 'searchPurpose', '보존할 검색 목적'); await submitSearch(tree, '검색 요청 추가');
  assert.equal(field(tree, 'searchPurpose').props.value, '보존할 검색 목적'); assert.equal(button(tree, '검색 요청 기록하기').props.disabled, true);
  await act(async () => button(tree, '최신 저장 정보 조회').props.onClick()); assert.ok(calls.some(call => call.url === '/api/intakes/42/search-requests' && call.init.method === 'GET'));
  assert.equal(calls.filter(call => call.init.method === 'POST' && call.url.endsWith('/search-requests')).length, 1);
  await navigate('/intake/42/search/82'); assert.equal(hangingSignal.aborted, false);
  await navigate('/help'); assert.equal(hangingSignal.aborted, true);
});

const pageLoginEvent = tree => ({ preventDefault() {}, currentTarget: { username: field(tree, 'username').props.value, password: field(tree, 'password').props.value } });
const submitPageLogin = tree => act(async () => { await tree.root.findByType('form').props.onSubmit(pageLoginEvent(tree)); });
test('Screen01 React: login page is real UI with required validation and no fabricated initial failure', async t => {
  const { tree, navigate, calls } = await mount(t); await navigate('/login');
  assert.equal(document.title, '로그인 | Legal Intake'); assert.ok(text(tree.root).includes('또는 데모로 체험하기'));
  assert.equal(tree.root.findAllByProps({ role: 'alert' }).length, 0); await submitPageLogin(tree);
  assert.ok(text(tree.root).includes('이메일 또는 아이디를 입력해 주세요.')); assert.equal(field(tree, 'username').props['aria-invalid'], true);
  assert.ok(!calls.some(call => call.url === '/api/auth/login' || call.url === '/api/me'));
});
test('Screen01 React: C01 is single-flight and identity/navigation wait for successful P01', async t => {
  let loginRelease; let profileRelease;
  const { tree, navigate, calls } = await mount(t, url => url === '/api/auth/login' ? new Promise(resolve => { loginRelease = resolve; }) : url === '/api/me' ? new Promise(resolve => { profileRelease = resolve; }) : undefined);
  await navigate('/login'); await change(tree, 'username', 'qa-user'); await change(tree, 'password', 'qa-password'); let pending;
  await act(async () => { pending = tree.root.findByType('form').props.onSubmit(pageLoginEvent(tree)); tree.root.findByType('form').props.onSubmit(pageLoginEvent(tree)); });
  assert.equal(button(tree, '로그인 중').props.disabled, true); assert.equal(calls.filter(call => call.url === '/api/auth/login').length, 1);
  await act(async () => loginRelease(response({ accessToken: 'qa-token', username: 'qa-user', role: 'ROLE_USER' })));
  assert.equal(window.location.hash, '#/login'); assert.equal(button(tree, '계정 정보 확인 중').props.disabled, true); assert.equal(field(tree, 'password').props.value, '');
  assert.equal(calls.find(call => call.url === '/api/me').init.headers.Authorization, 'Bearer qa-token');
  await act(async () => { profileRelease(response({ username: 'qa-user', role: 'ROLE_USER' })); await pending; });
  assert.equal(window.location.hash, '#/intake/type'); assert.ok(text(tree.root).includes('qa-user님'));
});
test('Screen01 React: failed password preserves username, clears secret and supports explicit visibility toggle', async t => {
  const { tree, navigate } = await mount(t, url => url === '/api/auth/login' ? response({}, 401) : undefined); await navigate('/login');
  await change(tree, 'username', 'qa-user'); await change(tree, 'password', 'wrong-password');
  assert.equal(field(tree, 'password').props.type, 'password');
  await act(async () => tree.root.findByProps({ 'aria-label': '비밀번호 표시' }).props.onClick()); assert.equal(field(tree, 'password').props.type, 'text');
  await submitPageLogin(tree); assert.ok(text(tree.root).includes('아이디 또는 비밀번호가 올바르지 않습니다.')); assert.equal(field(tree, 'username').props.value, 'qa-user'); assert.equal(field(tree, 'password').props.value, '');
  assert.equal(window.location.hash, '#/login');
});
test('Screen01 React: P01 mismatch prevents authentication even when C01 has returned a token', async t => {
  const { tree, navigate } = await mount(t, url => url === '/api/auth/login' ? response({ accessToken: 'qa-token', username: 'qa-user', role: 'ROLE_USER' }) : url === '/api/me' ? response({ username: 'different-user', role: 'ROLE_USER' }) : undefined);
  await navigate('/login'); await change(tree, 'username', 'qa-user'); await change(tree, 'password', 'qa-password'); await submitPageLogin(tree);
  assert.equal(window.location.hash, '#/login'); assert.ok(text(tree.root).includes('일치하지 않습니다.')); assert.equal(field(tree, 'password').props.value, '');
  await navigate('/account'); assert.ok(text(tree.root).includes('로그인이 필요합니다.'));
});
test('Screen01 React: verified admin goes to account, P01 GET retry does not repeat C01, and logout removes access', async t => {
  let profileReads = 0;
  const { tree, navigate, calls } = await mount(t, url => url === '/api/auth/login' ? response({ accessToken: 'qa-admin-token', username: 'admin', role: 'ROLE_ADMIN' }) : url === '/api/me' ? ++profileReads === 2 ? response({}, 500) : response({ username: 'admin', role: 'ROLE_ADMIN' }) : undefined);
  await navigate('/login'); await change(tree, 'username', 'admin'); await change(tree, 'password', 'qa-password'); await submitPageLogin(tree);
  assert.equal(window.location.hash, '#/account'); assert.ok(text(tree.root).includes('admin님')); assert.ok(text(tree.root).includes('서버에서 요청을 처리하지 못했습니다.'));
  await act(async () => button(tree, '다시 시도').props.onClick()); assert.ok(text(tree.root).includes('ROLE_ADMIN'));
  assert.equal(calls.filter(call => call.url === '/api/auth/login').length, 1);
  const operator = tree.root.findAllByType('a').find(node => text(node) === '운영 대시보드 열기'); assert.equal(operator.props.target, '_blank'); assert.equal(operator.props.href, 'operator.html#/overview');
  await act(async () => button(tree, '로그아웃').props.onClick()); assert.ok(text(tree.root).includes('로그인이 필요합니다.'));
  const reads = profileReads; await navigate('/account'); assert.equal(profileReads, reads);
});
test('Screen01 React: leaving a pending login aborts it and a late success cannot redirect or publish identity', async t => {
  let release; let signal;
  const { tree, navigate, calls } = await mount(t, (url, init) => url === '/api/auth/login' ? new Promise(resolve => { release = resolve; signal = init.signal; }) : undefined);
  await navigate('/login'); await change(tree, 'username', 'qa-user'); await change(tree, 'password', 'qa-password'); let pending;
  await act(async () => { pending = tree.root.findByType('form').props.onSubmit(pageLoginEvent(tree)); }); await navigate('/help'); assert.equal(signal.aborted, true);
  await act(async () => { release(response({ accessToken: 'qa-token', username: 'qa-user', role: 'ROLE_USER' })); await pending; });
  assert.equal(window.location.hash, '#/help'); assert.ok(text(tree.root).includes('비회원')); assert.ok(!calls.some(call => call.url === '/api/me'));
});
test('Screen01 React: demos are public routes and never manufacture a token, role or privileged session', async t => {
  const { tree, navigate, calls } = await mount(t, (url, init) => url === '/api/intakes' && init.method === 'GET' ? response([]) : undefined);
  await navigate('/login'); const links = tree.root.findAllByType('a');
  assert.equal(links.find(node => text(node) === '의뢰인 데모').props.href, '#/intake/type');
  assert.equal(links.find(node => text(node) === '변호사 데모').props.href, '#/lawyer/intakes');
  const operator = links.find(node => text(node) === '관리자 데모'); assert.equal(operator.props.href, 'operator.html#/overview'); assert.equal(operator.props.target, '_blank');
  await navigate('/lawyer/intakes'); assert.ok(text(tree.root).includes('비회원')); assert.ok(!calls.some(call => call.url === '/api/auth/login' || call.url === '/api/me'));
});
test('Screen01 React: shared modal waits for P01 and preserves the inquiry draft on failed profile confirmation', async t => {
  const { tree, navigate } = await mount(t, url => url === '/api/auth/login' ? response({ accessToken: 'qa-token', username: 'qa-user', role: 'ROLE_USER' }) : url === '/api/me' ? response({}, 401) : undefined);
  await classify(tree); await navigate('/intake/new'); await change(tree, 'clientName', '보존할 작성 중 정보');
  await act(async () => button(tree, '로그인').props.onClick());
  await act(async () => tree.root.findByType('dialog').findByType('form').props.onSubmit({ preventDefault() {}, currentTarget: { username: 'qa-user', password: 'qa-password' } }));
  assert.equal(tree.root.findAllByType('dialog').length, 1); assert.ok(text(tree.root).includes('비회원'));
  await act(async () => button(tree, '닫기').props.onClick()); assert.equal(field(tree, 'clientName').props.value, '보존할 작성 중 정보');
});

const listRows = () => [
  testIntake(501, { clientNameMasked: '검**', coreStatementMasked: '보호된 계약 문의', selectedDocuments: ['계약서', '이체 내역'], createdAt: '2026-10-20T14:20:00' }),
  testIntake(502, { caseType: 'FAMILY', status: 'NEED_MORE_INFO', desiredNextStep: '자료 확인', createdAt: '2026-10-21T10:00:00' }),
  testIntake(503, { status: 'CONSULTATION_READY', createdAt: '2026-10-21T11:00:00' }),
];
const setListFilter = async (tree, name, value) => act(async () => field(tree, name).props.onChange({ target: { name, value } }));
const detailLinks = tree => tree.root.findAllByType('a').filter(node => /^문의 \d+ 상세 보기$/.test(node.props['aria-label'] || ''));

test('Screen03 retry keeps its request key across navigation and lands on the original saved ID', async t => {
  const keys=[];let attempts=0;
  const {tree,navigate}=await mountAsStaff(t,(url,init)=>{
    if(url==='/api/intakes'&&init.method==='POST'){keys.push(init.headers['Idempotency-Key']);if(++attempts===1)throw new TypeError('response lost after commit');return response({id:42},201);}
  });
  await classify(tree);await act(async()=>button(tree,'다음: 문의 작성').props.onClick());await fillRequired(tree);
  await act(async()=>tree.root.findByType('form').props.onSubmit({preventDefault(){}}));
  assert.match(text(tree.root),/저장 여부가 불확실/);assert.equal(attempts,1);
  await navigate('/intake/type');await act(async()=>button(tree,'다음: 문의 작성').props.onClick());
  await act(async()=>tree.root.findByType('form').props.onSubmit({preventDefault(){}}));
  assert.equal(keys.length,2);assert.equal(keys[0],keys[1]);assert.ok(keys[0]);assert.equal(window.location.hash,'#/intake/42');
});

test('Screen03 uncertain edited payload is not silently sent under a new key',async t=>{
  const keys=[];const {tree}=await mountAsStaff(t,(url,init)=>{if(url==='/api/intakes'&&init.method==='POST'){keys.push(init.headers['Idempotency-Key']);throw new TypeError('response lost');}});
  await classify(tree);await act(async()=>button(tree,'다음: 문의 작성').props.onClick());await fillRequired(tree);
  const submit=()=>act(async()=>tree.root.findByType('form').props.onSubmit({preventDefault(){}}));
  await submit();await change(tree,'clientName','새로 수정한 가상 이름');await submit();
  assert.equal(keys.length,1);assert.match(text(tree.root),/기존 문의를 확인/);
  await act(async()=>button(tree,'기존 문의 확인 후 새 접수로 진행').props.onClick());await submit();
  assert.equal(keys.length,2);assert.notEqual(keys[0],keys[1]);
});

function consultationStore() {
  const state = { status: 'SUBMITTED', memos: [], version: 0 };
  return { state, handler(url, init) {
    if (url === '/api/intakes/42') return response(testIntake(42, { status: state.status, version: state.version, consultationMemos: state.memos }));
    if (url === '/api/intakes/42/status') { const previousStatus = state.status; state.status = JSON.parse(init.body).status; if (previousStatus !== state.status) state.version++; return response({ id: 42, previousStatus, status: state.status, changed: previousStatus !== state.status, version: state.version }); }
    if (url === '/api/intakes/42/memo') { const body = JSON.parse(init.body); const memo = testMemo(42, 300 + state.memos.length, { followUpItems: body.followUpItems, intakeVersion: ++state.version }); state.memos.unshift(memo); return response(memo, 201); }
  } };
}
const consultationForm = (tree, kind) => tree.root.findAllByType('form').find(form => form.findAllByProps({ name: kind === 'status' ? 'nextStatus' : 'memoContent' }).length);
const submitConsultation = (tree, kind) => act(async () => { await consultationForm(tree, kind).props.onSubmit({ preventDefault() {} }); });

test('Screen07 conflict preserves memo/checklist, locks writes until confirmation, then sends the refreshed version',async t=>{
  const store=consultationStore();let writes=0;
  const {tree,calls,navigate}=await mountAsStaff(t,(url,init)=>{
    if(url.endsWith('/status')&&++writes===1){store.state.version=3;store.state.status='REVIEWED';return response({code:'INTAKE_VERSION_CONFLICT',details:{currentVersion:3,currentStatus:'REVIEWED'}},409);}
    return store.handler(url,init);
  });
  await navigate('/lawyer/intakes/42/consultation');await change(tree,'memoContent','충돌 후 보존할 메모');
  await act(async()=>tree.root.findAllByType('input').find(node=>node.props.value==='REQUEST_DOCUMENTS').props.onChange());
  await change(tree,'nextStatus','NEED_MORE_INFO');await submitConsultation(tree,'status');
  assert.equal(writes,1);assert.match(text(tree.root),/다른 담당자/);assert.equal(field(tree,'memoContent').props.value,'충돌 후 보존할 메모');
  assert.equal(button(tree,'메모 추가 저장').props.disabled,true);assert.equal(tree.root.findAllByType('input').find(node=>node.props.value==='REQUEST_DOCUMENTS').props.checked,true);
  await act(async()=>button(tree,'확인 후 편집 계속').props.onClick());await submitConsultation(tree,'status');
  const requests=calls.filter(call=>call.url.endsWith('/status')).map(call=>JSON.parse(call.init.body));
  assert.deepEqual(requests.map(body=>body.expectedVersion),[0,3]);assert.equal(writes,2);
});

test('Screen07 lost memo replay uses the same key; a new identical memo has a new key',async t=>{
  const store=consultationStore(),keys=[];let first=true;
  const {tree,navigate}=await mountAsStaff(t,(url,init)=>{
    if(url.endsWith('/memo')){
      const key=init.headers['Idempotency-Key'];keys.push(key);
      if(first){first=false;store.handler(url,init);throw new TypeError('lost after commit');}
      if(keys[0]===key)return new Response(JSON.stringify({...store.state.memos[0],intakeVersion:store.state.version}),{status:201,headers:{'Content-Type':'application/json','Idempotent-Replayed':'true'}});
    }
    return store.handler(url,init);
  });
  await navigate('/lawyer/intakes/42/consultation');await change(tree,'memoContent','동일 내용의 메모');await submitConsultation(tree,'memo');
  await act(async()=>button(tree,'저장 결과 조회').props.onClick());await act(async()=>button(tree,'확인 후 편집 계속').props.onClick());
  await submitConsultation(tree,'memo');assert.equal(keys[0],keys[1]);assert.equal(store.state.memos.length,1);assert.match(text(tree.root),/중복으로 추가하지 않았습니다/);
  await change(tree,'memoContent','동일 내용의 메모');await submitConsultation(tree,'memo');assert.notEqual(keys[1],keys[2]);assert.equal(store.state.memos.length,2);
});

test('Screen07 successful write keeps its returned version even if follow-up GET fails',async t=>{
  const store=consultationStore();let reads=0;
  const {tree,calls,navigate}=await mountAsStaff(t,(url,init)=>url==='/api/intakes/42'&&++reads===2?response({},500):store.handler(url,init));
  await navigate('/lawyer/intakes/42/consultation');await change(tree,'nextStatus','REVIEWED');await submitConsultation(tree,'status');
  assert.match(text(tree.root),/저장은 완료됐지만/);await change(tree,'memoContent','후속 조회 실패 후 메모');await submitConsultation(tree,'memo');
  assert.equal(JSON.parse(calls.find(call=>call.url.endsWith('/memo')).init.body).expectedVersion,1);
});

test('Screen07 missing server version locks editing instead of inventing version zero',async t=>{
  const {tree,calls,navigate}=await mountAsStaff(t,url=>url==='/api/intakes/42'?response(testIntake(42,{version:undefined})):undefined);
  await navigate('/lawyer/intakes/42/consultation');assert.match(text(tree.root),/버전 정보를 확인하지 못해/);
  assert.equal(button(tree,'상태 변경 저장').props.disabled,true);assert.equal(button(tree,'메모 추가 저장').props.disabled,true);
  await submitConsultation(tree,'memo');assert.ok(!calls.some(call=>call.url.endsWith('/memo')));
});

test('Screen07 React: detail links real ID to consultation; status and protected memo writes refresh C05 independently', async t => {
  const store = consultationStore();
  const { tree, calls, navigate } = await mountAsStaff(t, store.handler);
  await navigate('/lawyer/intakes/42');
  assert.ok(tree.root.findAllByType('a').some(link => link.props.href === '#/lawyer/intakes/42/consultation'));
  await navigate('/lawyer/intakes/42/consultation');
  assert.equal(document.title, '상태 변경 · 상담 메모 | Legal Intake');
  await change(tree, 'nextStatus', 'CONSULTATION_READY');
  await submitConsultation(tree, 'status');
  assert.equal(store.state.status, 'CONSULTATION_READY'); assert.ok(text(tree.root).includes('상태 변경을 저장했습니다.'));
  assert.ok(text(tree.root).includes('접수됨 → 상담 준비'));
  await change(tree, 'memoContent', '원문 검수 메모');
  await act(async () => tree.root.findAllByType('input').find(input => input.props.type === 'checkbox' && input.props.value === 'REQUEST_DOCUMENTS').props.onChange());
  await submitConsultation(tree, 'memo');
  assert.equal(field(tree, 'memoContent').props.value, '');
  assert.ok(text(tree.root).includes('검수용 보호된 상담 메모'));
  assert.ok(!text(tree.root).includes('원문 검수 메모'));
  assert.equal(calls.filter(call => call.url === '/api/intakes/42').length, 4); // detail + initial + two acknowledged-write refreshes
  assert.equal(calls.filter(call => call.url.endsWith('/memo')).length, 1);
  assert.equal(calls.filter(call => call.url.endsWith('/status')).length, 1);
});

test('Screen07 React: required status/blank/4001-char memo validation never sends writes; CLOSED allows same status only', async t => {
  const store = consultationStore(); store.state.status = 'CLOSED';
  const { tree, calls, navigate } = await mountAsStaff(t, store.handler);
  await navigate('/lawyer/intakes/42/consultation');
  await submitConsultation(tree, 'status');
  assert.ok(text(tree.root).includes('변경할 상태를 선택해 주세요.'));
  await submitConsultation(tree, 'memo');
  assert.ok(text(tree.root).includes('상담 준비 메모를 입력해 주세요.'));
  await change(tree, 'memoContent', '가'.repeat(4001)); await submitConsultation(tree, 'memo');
  assert.ok(text(tree.root).includes('4,000자 이내'));
  assert.ok(!calls.some(call => ['POST', 'PATCH'].includes(call.init.method)));
  assert.deepEqual(field(tree, 'nextStatus').findAllByType('option').map(option => option.props.value), ['', 'CLOSED']);
  await change(tree, 'nextStatus', 'CLOSED'); await submitConsultation(tree, 'status');
  assert.ok(text(tree.root).includes('상태는 변경되지 않았습니다.'));
  assert.ok(text(tree.root).includes('아직 이 화면에서 확인한 상태 변경이 없습니다.'));
});

test('Screen07 React: pending status save locks both forms and duplicate submits use a single PATCH', async t => {
  const store = consultationStore(); let release;
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => url.endsWith('/status') ? new Promise(resolve => { release = () => resolve(store.handler(url, init)); }) : store.handler(url, init));
  await navigate('/lawyer/intakes/42/consultation'); await change(tree, 'nextStatus', 'REVIEWED');
  let pending;
  await act(async () => { pending = consultationForm(tree, 'status').props.onSubmit({ preventDefault() {} }); consultationForm(tree, 'status').props.onSubmit({ preventDefault() {} }); });
  assert.equal(button(tree, '상태 변경 저장').props.disabled, true); assert.equal(button(tree, '메모 추가 저장').props.disabled, true);
  assert.equal(calls.filter(call => call.url.endsWith('/status')).length, 1);
  await act(async () => { release(); await pending; });
  assert.equal(button(tree, '메모 추가 저장').props.disabled, false);
});

test('Screen07 React: known 400 field errors preserve memo/checklist and permit explicit correction', async t => {
  const store = consultationStore(); let attempt = 0;
  const { tree, navigate } = await mountAsStaff(t, (url, init) => url.endsWith('/memo') && ++attempt === 1 ? response({ fieldErrors: { content: '메모 검수 오류' } }, 400) : store.handler(url, init));
  await navigate('/lawyer/intakes/42/consultation'); await change(tree, 'memoContent', '보존할 메모');
  await submitConsultation(tree, 'memo');
  assert.equal(field(tree, 'memoContent').props.value, '보존할 메모'); assert.equal(field(tree, 'memoContent').props['aria-invalid'], true);
  assert.equal(button(tree, '메모 추가 저장').props.disabled, false);
  await change(tree, 'memoContent', '수정한 메모'); await submitConsultation(tree, 'memo');
  assert.equal(attempt, 2); assert.equal(field(tree, 'memoContent').props.value, '');
});

test('Screen07 React: lost memo response blocks automatic retry, rereads saved memo and requires review before editing', async t => {
  const store = consultationStore();
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => {
    if (url.endsWith('/memo')) { store.handler(url, init); throw new TypeError('lost response'); }
    return store.handler(url, init);
  });
  await navigate('/lawyer/intakes/42/consultation'); await change(tree, 'memoContent', '응답 유실 검수');
  await submitConsultation(tree, 'memo');
  assert.equal(field(tree, 'memoContent').props.value, '응답 유실 검수'); assert.equal(button(tree, '메모 추가 저장').props.disabled, true);
  await act(async () => button(tree, '저장 결과 조회').props.onClick());
  assert.ok(text(tree.root).includes('상담 메모 #300')); assert.equal(calls.filter(call => call.url.endsWith('/memo')).length, 1);
  await act(async () => button(tree, '확인 후 편집 계속').props.onClick());
  assert.equal(button(tree, '메모 추가 저장').props.disabled, false); assert.equal(calls.filter(call => call.url.endsWith('/memo')).length, 1);
});

test('Screen07 React: confirmed memo save plus failed read stays a success and never resends the POST', async t => {
  const store = consultationStore(); let reads = 0;
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => url === '/api/intakes/42' && ++reads === 2 ? response({}, 500) : store.handler(url, init));
  await navigate('/lawyer/intakes/42/consultation'); await change(tree, 'memoContent', '저장 성공 조회 실패');
  await submitConsultation(tree, 'memo');
  assert.ok(text(tree.root).includes('메모를 추가 저장했습니다.'));
  assert.ok(text(tree.root).includes('저장은 완료됐지만 최신 내용을 다시 조회하지 못했습니다.'));
  assert.ok(text(tree.root).includes('상담 메모 #300')); assert.equal(field(tree, 'memoContent').props.value, '');
  await act(async () => button(tree, '다시 시도').props.onClick());
  assert.equal(calls.filter(call => call.url.endsWith('/memo')).length, 1); assert.equal(reads, 3);
});

test('Screen07 React: 409 reloads authoritative status without erasing memo; route change cancels an older record', async t => {
  const store = consultationStore(); let release;
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => {
    if (url === '/api/intakes/43') return new Promise(resolve => { release = resolve; });
    if (url.endsWith('/status')) { store.state.status = 'CLOSED'; return response({}, 409); }
    return store.handler(url, init);
  });
  await navigate('/lawyer/intakes/42/consultation'); await change(tree, 'memoContent', '보존할 별도 메모'); await change(tree, 'nextStatus', 'REVIEWED');
  await submitConsultation(tree, 'status');
  assert.equal(field(tree, 'memoContent').props.value, '보존할 별도 메모');
  assert.deepEqual(field(tree, 'nextStatus').findAllByType('option').map(option => option.props.value), ['', 'CLOSED']);
  await navigate('/lawyer/intakes/43/consultation'); const old = calls.find(call => call.url === '/api/intakes/43');
  await navigate('/lawyer/intakes/42/consultation');
  await act(async () => release(response(testIntake(43))));
  assert.equal(old.init.signal.aborted, true); assert.ok(text(tree.root).includes('문의 #42 상태 변경'));
});

test('Screen07 React: initial C05 failure has no editable forms and explicit retry loads an empty memo list', async t => {
  let attempt = 0;
  const { tree, navigate } = await mountAsStaff(t, url => url === '/api/intakes/42' ? ++attempt === 1 ? response({}, 403) : response(testIntake(42)) : undefined);
  await navigate('/lawyer/intakes/42/consultation');
  assert.equal(tree.root.findAllByType('form').length, 0);
  assert.ok(text(tree.root).includes('접근 권한이 없습니다.'));
  await act(async () => button(tree, '다시 시도').props.onClick());
  assert.equal(tree.root.findAllByType('form').length, 2);
  assert.ok(text(tree.root).includes('저장된 상담 메모가 없습니다.'));
});

test('Screen08 React: saved C05 facts/condition/guide/protected memo and real attachment count render with the same management ID', async t => {
  const data = testIntake(42, { coreStatementMasked: '보호 처리된 사실관계', counterpartMasked: '검**', amountOrDamage: '5,000,000원', incidentDate: '2024-12-10',
    clientGuideMessage: '저장된 의뢰인용 안내', selectedDocuments: ['계약서', '이체 내역'], consultationMemos: [testMemo()], uploadedDocuments: [testDocument()] });
  const { tree, calls, navigate } = await mountAsStaff(t, url => url === '/api/intakes/42' ? response(data) : undefined);
  await navigate('/lawyer/intakes/42');
  const visible = text(tree.root);
  for (const value of ['사건 개요', '사실관계', '보호 처리된 사실관계', '검**', '5,000,000원', '저장된 의뢰인용 안내', '검수 검색어', '검수.txt', '1개', '상담 메모 #301']) assert.ok(visible.includes(value), value);
  assert.ok(!visible.includes('김지훈')); assert.ok(!visible.includes('최근 수정'));
  const links = tree.root.findAllByType('a').filter(link => link.props.href === '#/lawyer/intakes/42/consultation');
  assert.equal(links.length, 2);
  assert.equal(calls.filter(call => call.url === '/api/intakes/42').length, 1);
});

test('Screen08 React: loading and 404 do not show stale sections; retry shows distinct empty attachments, condition and memo', async t => {
  let release; let attempt = 0;
  const { tree, navigate } = await mountAsStaff(t, url => url === '/api/intakes/42' ? ++attempt === 1 ? new Promise(resolve => { release = resolve; }) : response(testIntake(42, { lawyerSearchCondition: null, recommendedDocuments: [] })) : undefined);
  await navigate('/lawyer/intakes/42'); assert.ok(text(tree.root).includes('문의 내용을 불러오는 중입니다.'));
  await act(async () => release(response({}, 404))); assert.ok(text(tree.root).includes('요청한 정보를 찾을 수 없습니다.'));
  assert.ok(!text(tree.root).includes('사건 개요'));
  await act(async () => button(tree, '다시 시도').props.onClick());
  for (const value of ['저장된 변호사 탐색 조건이 없습니다.', '업로드된 첨부파일이 없습니다.', '아직 작성된 상담 메모가 없습니다.', '추천 준비 자료가 없습니다.']) assert.ok(text(tree.root).includes(value));
});

test('Screen08 React: authenticated download is single-flight and binary is saved with a safe name', async t => {
  let release; let capturedDownload = null;
  const file = testDocument();
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => {
    if (url === '/api/intakes/42') return response(testIntake(42, { uploadedDocuments: [file] }));
    if (url === '/api/auth/login') return response({ accessToken: 'qa-token', username: 'qa-user', role: 'ROLE_USER' });
    if (url === file.downloadUrl) return new Promise(resolve => { release = () => resolve(new Response('test', { headers: { 'Content-Type': 'text/plain' } })); });
  });
  t.mock.method(URL, 'createObjectURL', () => 'blob:qa-document'); t.mock.method(URL, 'revokeObjectURL', () => {});
  document.body = { append() {} }; document.createElement = () => ({ click() { capturedDownload = { href: this.href, name: this.download }; }, remove() {} });
  await navigate('/lawyer/intakes/42');
  assert.ok(!calls.some(call => call.url === file.downloadUrl));
  let pending;
  await act(async () => { pending = button(tree, '다운로드').props.onClick(); button(tree, '다운로드').props.onClick(); });
  assert.equal(button(tree, '다운로드').props.disabled, true); assert.equal(calls.filter(call => call.url === file.downloadUrl).length, 1);
  await act(async () => { release(); await pending; });
  assert.deepEqual(capturedDownload, { href: 'blob:qa-document', name: '검수.txt' });
  assert.ok(text(tree.root).includes('다운로드를 시작했습니다.'));
});

test('Screen08 React: attachment 403 stays local and does not remove the saved inquiry or imply a download', async t => {
  const file = testDocument();
  const { tree, navigate } = await mountAsStaff(t, url => url === '/api/intakes/42' ? response(testIntake(42, { uploadedDocuments: [file] })) : url === '/api/auth/login' ? response({ accessToken: 'qa-token', username: 'qa-user', role: 'ROLE_USER' }) : url === file.downloadUrl ? response({}, 403) : undefined);
  await navigate('/lawyer/intakes/42');
  await act(async () => button(tree, '다운로드').props.onClick());
  assert.ok(text(tree.root).includes('접근 권한이 없습니다.')); assert.ok(text(tree.root).includes('사건 개요')); assert.ok(!text(tree.root).includes('다운로드를 시작했습니다.'));
});

test('Screen06 React: C09 rows, real status/counts, compound filters and ID detail preserve filters on return', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => url === '/api/intakes' && init.method === 'GET' ? response(listRows()) : undefined);
  await navigate('/lawyer/intakes');
  assert.equal(document.title, '접수된 문의 | Legal Intake');
  assert.equal(detailLinks(tree).length, 3);
  assert.ok(text(tree.root).includes('조회된 문의3건'));
  assert.ok(text(tree.root).includes('검**'));
  await setListFilter(tree, 'caseType', 'CIVIL');
  await setListFilter(tree, 'query', '계약');
  assert.deepEqual(detailLinks(tree).map(link => link.props.href), ['#/lawyer/intakes/501']);
  assert.equal(calls.filter(call => call.url === '/api/intakes').length, 1);
  await navigate('/lawyer/intakes/501');
  assert.ok(calls.some(call => call.url === '/api/intakes/501'));
  assert.ok(text(tree.root).includes('문의 #501 상세'));
  assert.equal(calls.filter(call => call.url === '/api/legal-intake/minimal').length, 0);
  await navigate('/lawyer/intakes');
  assert.equal(field(tree, 'query').props.value, '계약');
  assert.equal(field(tree, 'caseType').props.value, 'CIVIL');
  assert.equal(detailLinks(tree).length, 1);
  await act(async () => button(tree, '필터 초기화').props.onClick());
  assert.equal(detailLinks(tree).length, 3);
  await act(async () => button(tree, '상담 준비').props.onClick());
  assert.equal(field(tree, 'status').props.value, 'CONSULTATION_READY');
  assert.equal(detailLinks(tree)[0].props.href, '#/lawyer/intakes/503');
});

test('Screen06 React: loading disables refresh, error does not display fake zero counts, retry recovers an empty list', async t => {
  let release;
  let attempt = 0;
  const { tree, navigate } = await mountAsStaff(t, (url, init) => url === '/api/intakes' && init.method === 'GET'
    ? ++attempt === 1 ? new Promise(resolve => { release = resolve; }) : response([]) : undefined);
  await navigate('/lawyer/intakes');
  assert.equal(button(tree, '새로고침').props.disabled, true);
  assert.ok(text(tree.root).includes('접수된 문의를 확인하고 있습니다.'));
  assert.ok(!text(tree.root).includes('조회된 문의0건'));
  await act(async () => release(response({}, 500)));
  assert.ok(text(tree.root).includes('서버에서 요청을 처리하지 못했습니다.'));
  assert.ok(!text(tree.root).includes('조회된 문의0건'));
  await act(async () => button(tree, '다시 시도').props.onClick());
  assert.ok(text(tree.root).includes('아직 접수된 문의가 없습니다.'));
  assert.ok(text(tree.root).includes('조회된 문의0건'));
  assert.equal(attempt, 2);
});

test('Screen06 React: sorting, no matches, date errors and refresh keep criteria without another filtered API', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => url === '/api/intakes' && init.method === 'GET' ? response(listRows()) : undefined);
  await navigate('/lawyer/intakes');
  assert.equal(detailLinks(tree)[0].props.href, '#/lawyer/intakes/503');
  await act(async () => button(tree, '접수 시각 오름차순으로 정렬').props.onClick());
  assert.equal(detailLinks(tree)[0].props.href, '#/lawyer/intakes/501');
  await setListFilter(tree, 'startDate', '2026-10-22');
  await setListFilter(tree, 'endDate', '2026-10-21');
  assert.ok(text(tree.root).includes('시작일은 종료일보다 늦을 수 없습니다.'));
  assert.equal(detailLinks(tree).length, 0);
  await act(async () => button(tree, '필터 초기화').props.onClick());
  await setListFilter(tree, 'query', '없는 문의');
  assert.ok(text(tree.root).includes('조건에 맞는 문의가 없습니다.'));
  await act(async () => button(tree, '새로고침').props.onClick());
  assert.equal(field(tree, 'query').props.value, '없는 문의');
  assert.equal(calls.filter(call => call.url === '/api/intakes').length, 2);
});

test('Screen06 React: leaving an in-flight list aborts it and cannot replace the detail; missing detail offers retry', async t => {
  let release;
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => url === '/api/intakes' && init.method === 'GET'
    ? new Promise(resolve => { release = resolve; }) : url === '/api/intakes/404' ? response({}, 404) : undefined);
  await navigate('/lawyer/intakes');
  const pending = calls.find(call => call.url === '/api/intakes');
  await navigate('/lawyer/intakes/404');
  assert.equal(pending.init.signal.aborted, true);
  await act(async () => release(response(listRows())));
  assert.ok(text(tree.root).includes('요청한 정보를 찾을 수 없습니다.'));
  assert.equal(detailLinks(tree).length, 0);
  assert.ok(button(tree, '다시 시도'));
  assert.ok(tree.root.findAllByType('a').some(link => link.props.href === '#/lawyer/intakes'));
});
const p03Execution = overrides => ({ id: '11111111-1111-4111-8111-111111111111', stage: 'EXTERNAL_SEARCH', provider: 'NAVER_WEB', errorCode: null, startedAt: '2026-10-21T10:01:00', finishedAt: null, ...overrides });
test('P03 React: official source badge, partial-source notice and direct KBA link survive a C07 reread', async t => {
  const saved = testSearch(42, 81, { sourcePolicy: 'KBA_NAVER_PUBLIC_WEB', status: 'COMPLETED',
    execution: p03Execution({ stage: 'FINISHED', sourceWarningCode: 'GENERAL_SEARCH_UNAVAILABLE' }),
    results: [searchResult({ sourceType: 'KBA_PUBLIC_WEB', url: 'https://www.klaw.or.kr/fixture?id=1', explanation: '조건과 관련된 공개 자료입니다. 원문 확인 필요.' })] });
  const { tree, calls, navigate } = await mountAsStaff(t, url => url === '/api/intakes/search-requests/81' ? response(saved) : undefined);
  await navigate('/intake/42/search/81');
  assert.ok(text(tree.root).includes('대한변협 공개 페이지 · 네이버 수집'));
  assert.ok(text(tree.root).includes('일반 웹 검색에 연결하지 못했습니다.'));
  assert.ok(text(tree.root).includes('조건과 관련된 공개 자료입니다.'));
  assert.ok(tree.root.findAllByType('a').some(link => link.props.href === 'https://www.klaw.or.kr/' && link.props.rel === 'noopener noreferrer'));
  assert.equal(calls.filter(call => call.init.method === 'POST').length, 0);
});
test('P03 React: requires login, submits once, polls C07 and displays source and separate AI explanation', async t => {
  let release; let saved = testSearch();
  const { tree, calls, navigate } = await mount(t, (url, init) => searchLogin(url) || (url.endsWith('/81/execute') ? new Promise(resolve => { release = resolve; }) : url === '/api/intakes/search-requests/81' ? response(saved) : undefined));
  await navigate('/intake/42/search/81');
  await act(async () => button(tree, '로그인하고 계속하기').props.onClick());
  assert.equal(tree.root.findAllByType('dialog').length, 1); assert.ok(!calls.some(call => call.url.endsWith('/execute')));
  await act(async () => tree.root.findByType('dialog').findByType('form').props.onSubmit({ preventDefault() {}, currentTarget: { username: 'qa-user', password: 'qa-password' } }));
  let pending;
  await act(async () => { pending = button(tree, '외부 검색 실행').props.onClick(); button(tree, '외부 검색 실행').props.onClick(); });
  assert.equal(calls.filter(call => call.url.endsWith('/execute')).length, 1); assert.equal(button(tree, '외부 검색 진행 중').props.disabled, true);
  saved = testSearch(42, 81, { status: 'RUNNING', execution: p03Execution() });
  await act(async () => { release(response(saved, 202)); await pending; });
  assert.ok(text(tree.root).includes('외부 자료 검색 중입니다.'));
  assert.equal(searchForm(tree, '검색 요청 81 결과 링크 기록').findByType('fieldset').props.disabled, true);
  saved = testSearch(42, 81, { status: 'COMPLETED', execution: p03Execution({ stage: 'FINISHED', finishedAt: '2026-10-21T10:02:00' }), results: [searchResult({ sourceType: 'NAVER_WEB', explanation: '조건 관련 설명입니다. 원문 확인 필요.' })] });
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 1650)); });
  assert.ok(text(tree.root).includes('AI 관련성 설명 · 원문 확인 필요')); assert.ok(text(tree.root).includes('조건 관련 설명입니다.'));
  assert.ok(text(tree.root).includes('네이버 공개 웹 검색')); assert.equal(button(tree, '외부 검색 실행').props.disabled, true);
  assert.equal(calls.find(call => call.url.endsWith('/execute')).init.headers.Authorization, 'Bearer qa-token');
});
test('P03 React: missing config preserves selected request and shows actionable error without auto retry', async t => {
  const { tree, calls, navigate } = await mountAsStaff(t, url => searchLogin(url) || (url.endsWith('/81/execute') ? response({}, 503) : undefined));
  await navigate('/intake/42/search/81'); await loginSearch(tree);
  await act(async () => button(tree, '외부 검색 실행').props.onClick());
  assert.ok(text(tree.root).includes('LLM·네이버 검색 설정이 필요합니다.')); assert.ok(text(tree.root).includes('SR-81'));
  assert.equal(button(tree, '외부 검색 실행').props.disabled, false); assert.equal(calls.filter(call => call.url.endsWith('/execute')).length, 1);
});
test('P03 React: PARTIAL keeps source results and retries explanations only', async t => {
  const saved = testSearch(42, 81, { status: 'PARTIAL', execution: p03Execution({ stage: 'EXPLANATION', errorCode: 'SEARCH_LLM_ERROR', finishedAt: '2026-10-21T10:02:00' }), results: [searchResult()] });
  const { tree, calls, navigate } = await mountAsStaff(t, url => searchLogin(url) || (url === '/api/intakes/search-requests/81' ? response(saved) : url.endsWith('/81/execute') ? response(saved, 202) : undefined));
  await navigate('/intake/42/search/81'); await loginSearch(tree);
  assert.ok(text(tree.root).includes('저장된 결과는 유지됩니다.')); assert.ok(text(tree.root).includes('검수 결과 링크'));
  await act(async () => button(tree, '저장된 결과 설명 다시 생성').props.onClick());
  assert.deepEqual(JSON.parse(calls.find(call => call.url.endsWith('/execute')).init.body), { retryExplanation: true });
});
test('P03 React: route change aborts polling and hides previous execution', async t => {
  let signal; let reads = 0;
  const { tree, navigate } = await mountAsStaff(t, (url, init) => {
    if (url !== '/api/intakes/search-requests/81') return;
    if (++reads === 1) return response(testSearch(42, 81, { status: 'RUNNING', execution: p03Execution() }));
    return new Promise((resolve, reject) => { signal = init.signal; signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))); });
  });
  await navigate('/intake/42/search/81');
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 1650)); });
  assert.equal(signal.aborted, false); await navigate('/intake/43/search'); assert.equal(signal.aborted, true);
  assert.ok(!text(tree.root).includes('SR-81')); assert.ok(!text(tree.root).includes('외부 자료 검색 중입니다.'));
});
