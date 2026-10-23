// QA-only API double. No production UI switch or fake-success fallback.
// Run with node scripts/intake-fixture.mjs, then point a separate Vite process at port 8086.
import http from 'node:http';
import { testIntake, testGuide, testCriminalChecklist, testSearch, testMemo } from './intake-test-data.mjs';
import { allowedStatuses } from '../src/client/consultationModel.js';

let mode = 'success';
let classificationRequests = 0;
let createRequests = 0;
let statusRequests = 0;
let memoRequests = 0;
let nextMemoId = 304;
const procedure = { code: 'qa-civil', domain: 'CIVIL', title: '검수용 참고 절차', summary: '실제 데이터와 구분된 검수 전용 응답입니다.', userRole: 'party' };
const preview = { caseType: 'CIVIL', caseTypeLabel: '민사 사건', disputeArea: '검수용 분쟁', recommendedDocuments: ['검수용 자료'], clientGuideMessage: '검수용 응답입니다.', classificationMethod: 'RULE_BASED', persisted: false };
const step = { stepOrder: 1, stepCode: 'qa-step', title: '검수 단계', description: '검수 설명', checklist: ['검수 준비 항목'] };
const form = { procedureCode: 'qa-civil', formCode: 'qa-form', title: '검수용 양식', fileType: 'pdf', usageNote: '실제 양식과 구분된 검수 전용 안내입니다.' };
const intake = testIntake(777, { inquiryPurpose: '절차 안내', coreStatementMasked: '검수용 가상 문의', clientGuideMessage: '검수 서버의 등록 완료 응답입니다.', privacyNotice: '검수 전용 데이터입니다.',
  status: 'CONSULTATION_READY', counterpartMasked: '가**', disputeArea: '대여금 · 계약', consultationMemos: [testMemo(777, 303), testMemo(777, 302), testMemo(777, 301)] });
http.createServer(async (request, response) => {
  const send = (body, status = 200) => { response.writeHead(status, { 'Content-Type': 'application/json' }); response.end(JSON.stringify(body)); };
  const url = new URL(request.url, 'http://localhost:8086');
  if (url.pathname === '/__qa__/mode' && request.method === 'POST') { mode = url.searchParams.get('value') || 'success'; classificationRequests = 0; createRequests = 0; statusRequests = 0; memoRequests = 0; return send({ mode }); }
  if (url.pathname === '/__qa__/status') return send({ mode, classificationRequests, createRequests, statusRequests, memoRequests });
  if (url.pathname === '/api/intakes' && request.method === 'GET') {
    const requestMode = mode;
    if (requestMode === 'list-slow') await new Promise(resolve => setTimeout(resolve, 5000));
    if (requestMode === 'list-error') return send({ traceId: 'qa-list-error' }, 500);
    if (requestMode === 'list-forbidden') return send({}, 403);
    if (requestMode === 'list-empty') return send([]);
    if (requestMode === 'list-malformed') return send({ content: [] });
    return send(Array.from({ length: 8 }, (_, index) => testIntake(777 - index, {
      createdAt: `2026-10-${String(21 - index).padStart(2, '0')}T14:20:00`,
      caseType: ['CIVIL', 'FAMILY', 'CRIMINAL', 'CIVIL', 'ELECTRONIC_LITIGATION'][index % 5],
      status: ['CONSULTATION_READY', 'REVIEWED', 'NEED_MORE_INFO', 'SUBMITTED'][index % 4],
      coreStatementMasked: ['검수 전용: 대여금 반환 절차를 확인하고 싶습니다.', '검수 전용: 양육비 관련 자료를 정리하려고 합니다.', '검수 전용: 경찰 조사 준비 내용을 확인하고 싶습니다.'][index % 3],
      clientNameMasked: '검**', counterpartMasked: index % 2 ? null : '가**',
      disputeArea: ['대여금 · 계약', '이혼 · 양육비', '사기 · 경찰 조사', '손해배상', '전자소송 절차'][index % 5],
      desiredNextStep: ['절차 안내', '문서 검토', '변호사 상담', '자료 확인'][index % 4],
      selectedDocuments: ['계약서', '이체 내역', '문자·대화'].slice(0, index % 3 + 1),
    })));
  }
  if (url.pathname === '/api/intakes/classify-preview') {
    classificationRequests++;
    const requestMode = mode;
    if (requestMode === 'slow') await new Promise(resolve => setTimeout(resolve, 2500));
    if (requestMode === 'error') return send({ traceId: 'qa-error-reference' }, 500);
    if (requestMode === 'validation') return send({ fieldErrors: { coreStatement: 'required' } }, 400);
    if (requestMode === 'malformed') return send({ wrongField: true });
    return send(preview);
  }
  if (url.pathname === '/api/legal/procedures') {
    if (mode === 'empty') return send([]);
    if (mode === 'reference-error') return send({}, 503);
    return send([procedure]);
  }
  if (url.pathname === '/api/legal/procedures/qa-civil') return send({ ...procedure, steps: mode === 'empty-reference' ? [] : [step], forms: [form] });
  if (url.pathname === '/api/legal/procedures/qa-civil/steps') return send(mode === 'empty-reference' ? [] : [step]);
  if (url.pathname === '/api/legal/forms') {
    if (mode === 'forms-error') return send({}, 503);
    return send(mode === 'empty-reference' ? [] : [form]);
  }
  if (url.pathname === '/api/legal/forms/qa-form') return send(form);
  if (url.pathname === '/api/intakes' && request.method === 'POST') {
    createRequests++;
    const requestMode = mode;
    if (requestMode === 'create-slow') await new Promise(resolve => setTimeout(resolve, 5000));
    if (requestMode === 'create-error') return send({ traceId: 'qa-create-error' }, 500);
    if (requestMode === 'create-validation') return send({ fieldErrors: { counterpart: 'invalid' } }, 400);
    return send(intake, 201);
  }
  if (url.pathname === '/api/intakes/777') {
    if (mode === 'consultation-slow') await new Promise(resolve => setTimeout(resolve, 5000));
    if (mode === 'consultation-error') return send({}, 500);
    if (mode === 'consultation-missing') return send({}, 404);
    if (mode === 'consultation-empty') return send({ ...intake, consultationMemos: [] });
    return send(intake);
  }
  if (url.pathname === '/api/intakes/777/status' && request.method === 'PATCH') {
    statusRequests++;
    const chunks = []; for await (const chunk of request) chunks.push(chunk);
    const body = JSON.parse(Buffer.concat(chunks).toString());
    if (mode === 'status-slow') await new Promise(resolve => setTimeout(resolve, 5000));
    if (mode === 'status-error') return send({}, 500);
    if (mode === 'status-conflict' || !allowedStatuses(intake.status).includes(body.status)) return send({}, 409);
    const previousStatus = intake.status; intake.status = body.status;
    return send({ id: 777, previousStatus, status: intake.status, changed: previousStatus !== intake.status });
  }
  if (url.pathname === '/api/intakes/777/memo' && request.method === 'POST') {
    memoRequests++;
    const chunks = []; for await (const chunk of request) chunks.push(chunk);
    const body = JSON.parse(Buffer.concat(chunks).toString());
    if (mode === 'memo-slow') await new Promise(resolve => setTimeout(resolve, 5000));
    if (mode === 'memo-error') return send({}, 500);
    if (mode === 'memo-validation') return send({ fieldErrors: { content: '검수용 메모 입력 오류' } }, 400);
    const memo = testMemo(777, nextMemoId++, { contentMasked: '별도 가상 API에서 보호 처리한 메모', followUpItems: body.followUpItems || [], createdAt: '2026-10-21T12:00:00' });
    intake.consultationMemos.unshift(memo);
    if (mode === 'memo-lost') { response.destroy(); return; }
    return send(memo, 201);
  }
  if (url.pathname === '/api/legal-intake/minimal') {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const body = JSON.parse(Buffer.concat(chunks).toString());
    if (mode === 'guide-slow') await new Promise(resolve => setTimeout(resolve, 5000));
    if (mode === 'guide-error') return send({}, 500);
    if (mode === 'guide-malformed') return send({ unexpected: true });
    return send(testGuide(body, mode === 'guide-empty' ? { checkedFacts: [], recommendedDocuments: [] } : {}));
  }
  if (url.pathname === '/api/legal/criminal/checklist/supplementary-investigation') return send(testCriminalChecklist);
  if (url.pathname === '/api/intakes/777/search-requests') return send(testSearch(777), 201);
  if (url.pathname === '/api/intakes/search-requests/81') return send(testSearch(777));
  send({}, 404);
}).listen(8086, '127.0.0.1', () => console.log('Intake QA fixture: http://127.0.0.1:8086'));
