// Q02 화면05 회귀: 기존 intake-components 테스트에 없는 경우만 추가한다(유료 호출 없음, fetch는 모두 가짜 응답).
// 하네스(mount)는 intake-components.test.mjs와 같은 코드를 그대로 옮겨 왔다. 원본 파일은 수정하지 않는다.
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
const p03Execution = overrides => ({ id: '11111111-1111-4111-8111-111111111111', stage: 'EXTERNAL_SEARCH', provider: 'NAVER_WEB', errorCode: null, startedAt: '2026-10-21T10:01:00', finishedAt: null, ...overrides });

// ---------- Q02 추가 검사 ----------
import { searchSourceLabel, searchSourceWarningLabel } from '../src/client/searchModel.js';

const wait = ms => act(async () => { await new Promise(resolve => setTimeout(resolve, ms)); });
const POLL = 1650; // 화면 폴링 간격(1500ms)보다 조금 길게
const executes = calls => calls.filter(call => call.url.endsWith('/execute')).length;
const posts = calls => calls.filter(call => call.init.method === 'POST').length;
const c07Reads = (calls, id) => calls.filter(call => call.url === `/api/intakes/search-requests/${id}` && call.init.method !== 'POST').length;
const running = () => testSearch(42, 81, { status: 'RUNNING', execution: p03Execution() });
const finished = (status, overrides = {}) => testSearch(42, 81, { status, execution: p03Execution({ stage: 'FINISHED', finishedAt: '2026-10-21T10:02:00' }), ...overrides });

test('Q02 P03: execute response lost after the server claimed the job → review lock, no resend; explicit C07 check resumes polling', async t => {
  let saved = testSearch(42, 81);
  const { tree, calls, navigate } = await mountAsStaff(t, url => {
    if (url.endsWith('/81/execute')) { saved = running(); throw new TypeError('response lost after commit'); }
    if (url === '/api/intakes/search-requests/81') return response(saved);
  });
  await navigate('/intake/42/search/81');
  await act(async () => button(tree, '외부 검색 실행').props.onClick());
  assert.ok(text(tree.root).includes('서버의 저장 여부를 확인할 수 없습니다.'));
  assert.equal(button(tree, '외부 검색 실행').props.disabled, true);

  // 연타·대기 중에도 POST는 다시 나가지 않는다.
  await act(async () => { button(tree, '외부 검색 실행').props.onClick(); button(tree, '외부 검색 실행').props.onClick(); });
  await wait(POLL);
  assert.equal(executes(calls), 1);

  const before = c07Reads(calls, 81);
  await act(async () => button(tree, '최신 저장 정보 조회').props.onClick());
  assert.equal(c07Reads(calls, 81), before + 1);
  assert.ok(text(tree.root).includes('외부 자료 검색 중입니다.'));

  saved = finished('COMPLETED', { results: [searchResult({ sourceType: 'NAVER_WEB', explanation: '조건 관련 설명입니다. 원문 확인 필요.' })] });
  await wait(POLL);
  assert.ok(text(tree.root).includes('검색 완료'));
  assert.ok(text(tree.root).includes('공개 웹 검색'));
  assert.equal(executes(calls), 1);
});

test('Q02 P03: reopening a RUNNING request (refresh) polls C07 to the end, never POSTs, then stops polling', async t => {
  let reads = 0;
  const { tree, calls, navigate } = await mountAsStaff(t, url => url === '/api/intakes/search-requests/81' ? response(++reads < 3 ? running() : finished('NO_RESULTS')) : undefined);
  await navigate('/intake/42/search/81');
  assert.ok(text(tree.root).includes('외부 자료 검색 중입니다.'));
  await wait(POLL); await wait(POLL); await wait(POLL);
  assert.ok(text(tree.root).includes('조건에 맞는 공개 자료를 찾지 못했습니다.'));
  const settled = reads;
  await wait(POLL);
  assert.equal(reads, settled, 'terminal state must stop polling');
  assert.equal(posts(calls), 0);
});

test('Q02 P03: a failed C07 poll stops automatic polling until the user retries explicitly', async t => {
  let reads = 0;
  const { tree, calls, navigate } = await mountAsStaff(t, url => {
    if (url !== '/api/intakes/search-requests/81') return;
    reads += 1;
    return reads === 1 ? response(running()) : reads === 2 ? response({}, 500) : response(finished('COMPLETED', { results: [searchResult()] }));
  });
  await navigate('/intake/42/search/81');
  await wait(POLL);
  assert.equal(reads, 2);
  assert.ok(text(tree.root).includes('마지막으로 확인한 저장 결과입니다.'));
  await wait(POLL); await wait(POLL);
  assert.equal(reads, 2, 'no automatic retry loop after a failed poll');
  await act(async () => button(tree, '다시 시도').props.onClick());
  assert.equal(reads, 3);
  assert.ok(text(tree.root).includes('검색 완료'));
  assert.equal(posts(calls), 0);
});

test('Q02 C06: create response lost after commit keeps the purpose, sends one POST, and the history check shows the saved request', async t => {
  let committed = false;
  const { tree, calls, navigate } = await mountAsStaff(t, (url, init) => {
    if (url !== '/api/intakes/42/search-requests') return;
    if (init.method === 'POST') { committed = true; throw new TypeError('response lost after commit'); }
    return response(committed ? [testSearch(42, 82, { searchPurpose: '보존할 검색 목적' })] : []);
  });
  await navigate('/intake/42/search');
  await change(tree, 'searchPurpose', '보존할 검색 목적');
  await submitSearch(tree, '검색 요청 추가');
  assert.ok(text(tree.root).includes('서버의 저장 여부를 확인할 수 없습니다.'));
  assert.equal(field(tree, 'searchPurpose').props.value, '보존할 검색 목적');
  await submitSearch(tree, '검색 요청 추가');
  assert.equal(posts(calls), 1, 'locked form must not resend');

  await act(async () => button(tree, '최신 저장 정보 조회').props.onClick());
  assert.ok(text(tree.root).includes('SR-82'), 'committed request is visible before the user decides to resend');
  assert.equal(posts(calls), 1);
  await act(async () => button(tree, '중복 가능성 확인 후 편집 계속').props.onClick());
  assert.equal(field(tree, 'searchPurpose').props.value, '보존할 검색 목적');
  assert.equal(button(tree, '검색 요청 기록하기').props.disabled, false);
  assert.equal(posts(calls), 1);
});

test('Q02 sources: KBA badge uses the same host rule as the server (SearchText.kbaPublicUrl); warning text matches server codes', () => {
  const official = '대한변협 공개 페이지 · 웹 검색 수집', unverified = '공개 웹 · 출처 확인 필요';
  for (const url of ['https://www.klaw.or.kr/fixture?id=1', 'https://klaw.or.kr/', 'https://sub.koreanbar.or.kr/a', 'https://WWW.KLAW.OR.KR/x'])
    assert.equal(searchSourceLabel('KBA_PUBLIC_WEB', url), official, url);
  for (const url of ['https://klaw.or.kr.example.com/', 'https://evilklaw.or.kr/', 'https://user@klaw.or.kr/', 'javascript:alert(1)', 'https://example.com/p03-civil'])
    assert.equal(searchSourceLabel('KBA_PUBLIC_WEB', url), unverified, url);
  assert.equal(searchSourceLabel('NAVER_WEB', 'https://www.klaw.or.kr/'), '공개 웹 검색');
  assert.equal(searchSourceWarningLabel('KBA_SEARCH_UNAVAILABLE'), '변협 대상 검색에 연결하지 못했습니다. 일반 웹 검색에서 수집한 결과를 표시합니다.');
  assert.equal(searchSourceWarningLabel('GENERAL_SEARCH_UNAVAILABLE'), '일반 웹 검색에 연결하지 못했습니다. 변협 대상 검색에서 수집한 결과를 표시합니다.');
  assert.equal(searchSourceWarningLabel('SOMETHING_NEW'), '일부 출처를 조회하지 못했습니다. 수집된 결과를 표시합니다.');
});
