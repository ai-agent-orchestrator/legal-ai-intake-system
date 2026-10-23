// Requires a LOCAL test backend. Creates synthetic inquiries, search records, memos and an attachment.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createIntakeApi } from '../src/client/api.js';
import { intakePayload } from '../src/client/model.js';
import { testIntake } from './intake-test-data.mjs';
import { mkdir, writeFile } from 'node:fs/promises';
import { authenticate } from '../src/client/authModel.js';

const base = process.env.INTAKE_TEST_API_BASE || 'http://localhost:8080';
if (!['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname)) throw new Error('Live tests require a local test backend.');
const api = createIntakeApi({ base });

test('Screen01 real C01/P01 confirm USER and ADMIN, reject bad credentials, anonymous and invalid JWT without any invented lawyer identity', async () => {
  const profiles = [];
  for (const [username, password, role] of [['user', 'user123', 'ROLE_USER'], ['admin', 'admin123', 'ROLE_ADMIN']]) {
    const signedIn = await authenticate(api, username, password);
    assert.equal(signedIn.username, username); assert.equal(signedIn.role, role); assert.ok(signedIn.accessToken.length > 0);
    const current = await createIntakeApi({ base, getToken: () => signedIn.accessToken }).currentUser();
    assert.deepEqual(current, { username, role }); profiles.push(current);
  }
  await assert.rejects(authenticate(api, 'user', 'wrong-test-password'), error => error.status === 401);
  const anonymous = await fetch(`${base}/api/me`); assert.equal(anonymous.status, 401);
  const invalid = await fetch(`${base}/api/me`, { headers: { Authorization: 'Bearer invalid-test-token' } }); assert.equal(invalid.status, 401);
  const blank = await fetch(`${base}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: '', password: '' }) }); assert.equal(blank.status, 400);
  const output = new URL('../../../output/intake-01/', import.meta.url); await mkdir(output, { recursive: true });
  await writeFile(new URL('api-evidence.json', output), JSON.stringify({ profiles, incorrectPassword: 401, anonymousMe: 401, invalidJwtMe: 401, blankCredentials: 400,
    loginThenProfileVerified: true, passwordsOrTokensStoredInEvidence: false, lawyerRoleDefined: false, source: 'real local Spring API' }, null, 2));
});

test('Screen04 real saved C05 → protected C04 → R01/R02 and criminal R06 roundtrip keeps the saved inquiry unchanged', async () => {
  const auth = await api.login('user', 'user123'); const owner = createIntakeApi({ base, getToken: () => auth.accessToken });
  const source = { inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고',
    coreStatement: '화면04 개발 검수용 가상 문의입니다. 계약서를 작성하고 돈을 빌려주었지만 아직 돌려받지 못했습니다. 여러 번 변제를 요청했고 이체 내역과 문자 기록이 있습니다. 연락처 010-1234-5678',
    desiredNextStep: '법적 절차가 어떻게 진행되는지 알고 싶어요.', incidentDate: '2024-12-10', counterpart: '가상 검수업체', clientName: '가상 검수인', clientPhone: '010-1234-5678',
    amountOrDamage: '5,000,000원', currentStage: '상대방에게 변제 요청 중', preferredRegion: '서울특별시', documents: ['계약서', '이체 내역', '문자·대화'] };
  const created = await owner.create(source); const saved = await api.receiptIntake(created.id);
  assert.equal(saved.id, created.id); assert.ok(!saved.coreStatementMasked.includes('010-1234-5678')); assert.notEqual(saved.clientNameMasked, source.clientName);
  const guide = await api.minimal(saved); assert.equal(guide.caseType, saved.caseType); assert.equal(guide.partyRole, saved.partyPosition);
  assert.ok(!JSON.stringify(guide).includes('010-1234-5678')); assert.ok(guide.checkedFacts.length); assert.ok(guide.recommendedDocuments.length); assert.equal(guide.id, undefined);
  const procedures = await api.procedures(saved.caseType); assert.ok(procedures.length); const procedure = await api.procedure(procedures[0].code, undefined, procedures[0].domain);
  assert.ok(procedure.steps.length); assert.deepEqual(await api.receiptIntake(created.id), saved);
  const criminal = await owner.create({ ...source, caseType: 'CRIMINAL', partyPosition: '피해자', coreStatement: '화면04 검수용 가상 형사 문의입니다. 사기 피해를 신고했고 수사 기관에 제출할 보완 자료를 준비하려고 합니다.', currentStage: '수사 진행 중' });
  const criminalSaved = await api.receiptIntake(criminal.id); assert.equal((await api.minimal(criminalSaved)).caseType, 'CRIMINAL');
  const checklist = await api.criminalChecklist(); assert.equal(checklist.role, 'COMPLAINANT'); assert.equal(checklist.stageCode, 'SUPPLEMENTARY_INVESTIGATION_AFTER_TRANSFER');
  const output = new URL('../../../output/intake-04/', import.meta.url); await mkdir(output, { recursive: true });
  await writeFile(new URL('api-evidence.json', output), JSON.stringify({ intakeId: created.id, criminalIntakeId: criminal.id, caseType: saved.caseType, status: saved.status, procedureCode: procedure.code,
    procedureSteps: procedure.steps.length, checkedFacts: guide.checkedFacts.length, recommendedDocuments: guide.recommendedDocuments.length, protectedFieldsVerified: true, publicReadsVerified: true, minimalHasNoIntakeId: true, savedRecordUnchanged: true, checklistStage: checklist.stageCode, source: 'real local Spring API; synthetic inquiries' }, null, 2));
});

test('Screen05 real C06/C07/C08/P02 preserve actual IDs, saved policy, manual results, status and owner/admin boundaries; P03 is not called', async () => {
  const auth = await api.login('user', 'user123');
  const owner = createIntakeApi({ base, getToken: () => auth.accessToken });
  const created = await owner.create({ inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고',
    coreStatement: '화면05 개발 검수용 가상 문의입니다. 계약서와 이체 내역을 확인하고 대여금 반환 절차와 관련된 공개 자료를 정리하려고 합니다.',
    desiredNextStep: '법적 절차가 어떻게 진행되는지 알고 싶어요.', counterpart: '가상 검수업체', preferredRegion: '서울특별시',
    officeType: 'LAW_FIRM', experienceLevel: 'EXPERIENCED', lawyerSearchPurpose: '가상 문의 공개 자료 정리', documents: ['계약서', '이체 내역', '문자·대화'] });
  const intake = await api.intake(created.id); const condition = intake.lawyerSearchCondition; assert.ok(condition);
  const first = await owner.createSearch(created.id, '화면05 첫 번째 검수 요청', undefined, condition.sourcePolicy);
  const second = await owner.createSearch(created.id, '화면05 두 번째 검수 요청', undefined, condition.sourcePolicy);
  assert.equal(first.conditionId, condition.id); assert.equal(first.sourcePolicy, condition.sourcePolicy); assert.equal(first.searchQuery, condition.searchQuery);
  const results = [];
  for (const [sourceType, title, url, order] of [['LEGAL_INFORMATION', '검수용 대여금 절차 참고 링크', 'https://example.com/screen05/procedure', '2'],
    ['LAW_FIRM_SITE', '검수용 상담 공개 정보 링크', 'https://example.com/screen05/consultation', '1'],
    ['CASE_LAW', '검수용 판례 자료 참고 링크', 'https://example.com/screen05/case', '']]) {
    const saved = await owner.addSearchResult(created.id, first.id, { sourceType, title, url, snippet: '개발 검수용 가상 링크입니다. 실제 법률 자료나 변호사 추천 결과가 아닙니다.', displayOrder: order });
    assert.equal(saved.status, 'PREVIEW_READY'); results.push(saved.results.find(item => item.url === url).id);
  }
  const detail = await api.searchRequest(created.id, first.id); assert.equal(detail.results.length, 3);
  assert.deepEqual(detail.results.map(item => item.displayOrder), [1, 2, 3]);
  assert.ok(detail.results.every(item => item.searchRequestId === first.id));
  const history = await owner.searchHistory(created.id); assert.deepEqual(history.map(item => item.id), [second.id, first.id]);
  assert.equal(history[1].results.length, 3);
  const anonymous = await fetch(`${base}/api/intakes/${created.id}/search-requests`); assert.equal(anonymous.status, 401);
  const unowned = await api.create({ inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고', coreStatement: '화면05 이력 소유권 검수용 비회원 가상 문의입니다.', desiredNextStep: '절차 안내' });
  await assert.rejects(owner.searchHistory(unowned.id), error => error.status === 403);
  const adminAuth = await api.login('admin', 'admin123'); const admin = createIntakeApi({ base, getToken: () => adminAuth.accessToken });
  assert.equal((await admin.searchHistory(created.id)).length, 2);
  await assert.rejects(api.searchRequest(unowned.id, first.id), /응답 형식/);
  const invalid = await fetch(`${base}/api/intakes/search-requests/${first.id}/results`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sourceType: '', title: '', url: '' }) }); assert.equal(invalid.status, 400);
  assert.equal((await api.searchRequest(created.id, first.id)).results.length, 3);
  const output = new URL('../../../output/intake-05/', import.meta.url); await mkdir(output, { recursive: true });
  await writeFile(new URL('api-evidence.json', output), JSON.stringify({ intakeId: created.id, conditionId: condition.id, requestIds: [first.id, second.id], resultIds: results,
    resultCount: 3, status: detail.status, sourcePolicy: detail.sourcePolicy, anonymousHistory: 401, nonOwnerHistory: 403, ownerHistory: 200, adminHistory: 200, invalidResult: 400, p03Called: false, source: 'real local Spring API; synthetic references' }, null, 2));
});

test('Screen08 real C05 metadata and P05 binary download enforce owner/admin JWT with 401/403/404 boundaries', async () => {
  const auth = await api.login('user', 'user123');
  const owner = createIntakeApi({ base, getToken: () => auth.accessToken });
  const body = { inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고',
    coreStatement: '화면08 개발 검수용 가상 문의입니다. 계약서와 이체 내역을 정리해 대여금 반환 절차를 확인하고 싶습니다.',
    desiredNextStep: '법적 절차가 어떻게 진행되는지 알고 싶어요.', incidentDate: '2024-12-10', counterpart: '가상 검수업체',
    amountOrDamage: '5,000,000원', currentStage: '상대방에게 변제 요청 중', preferredRegion: '서울특별시', documents: ['계약서', '이체 내역', '문자·대화'] };
  const created = await owner.create(body);
  await owner.updateStatus(created.id, 'CONSULTATION_READY');
  const bytes = 'Screen08 synthetic attachment.\n';
  const form = new FormData(); form.append('file', new Blob([bytes], { type: 'text/plain' }), '화면08-검수.txt'); form.append('documentType', '계약서');
  const upload = await fetch(`${base}/api/intakes/${created.id}/documents`, { method: 'POST', headers: { Authorization: `Bearer ${auth.accessToken}` }, body: form });
  assert.equal(upload.status, 201); const uploaded = await upload.json();
  const detail = await api.detailIntake(created.id);
  assert.equal(detail.uploadedDocuments.length, 1); const file = detail.uploadedDocuments[0];
  assert.equal(file.id, uploaded.id); assert.equal(file.intakeId, created.id); assert.equal(file.originalFilename, '화면08-검수.txt');
  assert.equal(await (await owner.downloadDocument(created.id, file)).text(), bytes);
  const anonymous = await fetch(`${base}${file.downloadUrl}`); assert.equal(anonymous.status, 401);
  const inaccessible = await api.create({ ...body, coreStatement: '화면08 다운로드 권한 경계 검수용 비회원 가상 문의입니다.' });
  const forbidden = await fetch(`${base}/api/intakes/${inaccessible.id}/documents/${file.id}/download`, { headers: { Authorization: `Bearer ${auth.accessToken}` } });
  assert.equal(forbidden.status, 403);
  const missing = await fetch(`${base}/api/intakes/${created.id}/documents/9223372036854775807/download`, { headers: { Authorization: `Bearer ${auth.accessToken}` } }); assert.equal(missing.status, 404);
  const adminAuth = await api.login('admin', 'admin123');
  const admin = createIntakeApi({ base, getToken: () => adminAuth.accessToken });
  assert.equal(await (await admin.downloadDocument(created.id, file)).text(), bytes);
  // Check a real non-owned attachment with a matching parent, not only a foreign file ID.
  const otherForm = new FormData(); otherForm.append('file', new Blob([bytes], { type: 'text/plain' }), '비소유자-검수.txt');
  const otherUpload = await fetch(`${base}/api/intakes/${inaccessible.id}/documents`, {
    method: 'POST', headers: { Authorization: `Bearer ${adminAuth.accessToken}` }, body: otherForm,
  });
  assert.equal(otherUpload.status, 201); const otherFile = await otherUpload.json();
  assert.equal(otherFile.intakeId, inaccessible.id);
  const matchingParentForbidden = await fetch(`${base}${otherFile.downloadUrl}`, { headers: { Authorization: `Bearer ${auth.accessToken}` } });
  assert.equal(matchingParentForbidden.status, 403);
  assert.equal(await (await admin.downloadDocument(inaccessible.id, otherFile)).text(), bytes);
  const wrongParent = await fetch(`${base}/api/intakes/${created.id}/documents/${otherFile.id}/download`, { headers: { Authorization: `Bearer ${auth.accessToken}` } });
  assert.equal(wrongParent.status, 404);
  const output = new URL('../../../output/intake-08/', import.meta.url); await mkdir(output, { recursive: true });
  await writeFile(new URL('api-evidence.json', output), JSON.stringify({ intakeId: created.id, inaccessibleIntakeId: inaccessible.id, documentId: file.id, filename: file.originalFilename,
    sizeBytes: file.sizeBytes, status: detail.status, ownerDownload: true, adminDownload: true, anonymousStatus: 401, nonOwnerStatus: 403,
    matchingParentNonOwnerStatus: 403, foreignParentDocumentStatus: 404, otherDocumentId: otherFile.id, missingStatus: 404, source: 'real local Spring API' }, null, 2));
});

test('Screen07 real C05/C10/C11 validate status transitions, protected memo persistence, limits and terminal conflict', async () => {
  const auth = await api.login('user', 'user123');
  const signedIn = createIntakeApi({ base, getToken: () => auth.accessToken });
  const body = { inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고',
    coreStatement: '화면07 개발 검수용 가상 문의입니다. 계약서와 이체 내역을 확인하고 대여금 반환 절차를 준비하려고 합니다.',
    desiredNextStep: '법적 절차가 어떻게 진행되는지 알고 싶어요.', counterpart: '가상 검수업체', clientName: '가상 검수인',
    incidentDate: '2024-12-10', amountOrDamage: '5,000,000원', currentStage: '상대방에게 변제 요청 중', preferredRegion: '서울특별시', documents: ['계약서', '이체 내역', '문자·대화'] };
  const created = await signedIn.create(body);
  const initial = await api.consultationIntake(created.id);
  assert.equal(initial.status, 'SUBMITTED'); assert.deepEqual(initial.consultationMemos, []);
  const changed = await signedIn.updateStatus(created.id, 'REVIEWED');
  assert.deepEqual(changed, { id: created.id, previousStatus: 'SUBMITTED', status: 'REVIEWED', changed: true });
  assert.equal((await api.updateStatus(created.id, 'REVIEWED')).changed, false);
  await assert.rejects(signedIn.updateStatus(created.id, 'SUBMITTED'), error => error.status === 409);
  await signedIn.updateStatus(created.id, 'CONSULTATION_READY');
  const memos = [];
  for (const [content, codes] of [
    ['화면07 검수: 계약서와 이체 내역을 확인했습니다. 추가 대화 기록 확인이 필요합니다. 010-1234-5678', ['REQUEST_DOCUMENTS']],
    ['화면07 검수: 상담 일정을 확인하고 서면 검토 내용을 정리합니다.', ['SCHEDULE_CONSULTATION', 'DOCUMENT_REVIEW']],
    ['화면07 검수: 전자소송 제출 방법을 상담에서 확인할 예정입니다.', ['E_LITIGATION_GUIDE']],
  ]) memos.push(await api.createMemo(created.id, content, codes));
  assert.ok(!memos[0].contentMasked.includes('010-1234-5678'));
  const refreshed = await api.consultationIntake(created.id);
  assert.equal(refreshed.status, 'CONSULTATION_READY');
  assert.deepEqual(refreshed.consultationMemos.map(memo => memo.id), memos.map(memo => memo.id).reverse());
  for (const invalid of [{ content: ' ' }, { content: '가'.repeat(4001) }, { content: '검수', followUpItems: ['UNKNOWN'] }, { content: '검수', followUpItems: Array(5).fill('REQUEST_DOCUMENTS') }]) {
    const result = await fetch(`${base}/api/intakes/${created.id}/memo`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(invalid) });
    assert.equal(result.status, 400);
  }
  assert.equal((await api.consultationIntake(created.id)).consultationMemos.length, 3);
  const terminal = await signedIn.create({ ...body, coreStatement: '화면07 종료 상태 검수 전용 가상 문의입니다.' });
  await signedIn.updateStatus(terminal.id, 'CLOSED');
  await assert.rejects(signedIn.updateStatus(terminal.id, 'REVIEWED'), error => error.status === 409);
  assert.equal((await api.consultationIntake(terminal.id)).status, 'CLOSED');
  const output = new URL('../../../output/intake-07/', import.meta.url);
  await mkdir(output, { recursive: true });
  await writeFile(new URL('api-evidence.json', output), JSON.stringify({ intakeId: created.id, terminalIntakeId: terminal.id, memoIds: memos.map(memo => memo.id), status: refreshed.status, source: 'real local Spring API',
    anonymousReadVerified: true, anonymousSameStatusWriteVerified: true, anonymousMemoWriteVerified: true }, null, 2));
});

test('Screen06 real C09 public/optional JWT list contract, latest order, protected values and C05 ID handoff', async () => {
  const list = await api.intakes();
  assert.ok(list.length <= 50);
  for (let i = 1; i < list.length; i++) assert.ok(list[i - 1].createdAt >= list[i].createdAt);
  const auth = await api.login('user', 'user123');
  const signedIn = createIntakeApi({ base, getToken: () => auth.accessToken });
  const authenticated = await signedIn.intakes();
  assert.deepEqual(authenticated.map(item => item.id), list.map(item => item.id));
  if (list.length) {
    const detail = await api.intake(list[0].id);
    assert.equal(detail.id, list[0].id);
    assert.equal(detail.coreStatementMasked, list[0].coreStatementMasked);
    assert.equal(detail.clientNameMasked, list[0].clientNameMasked);
    assert.deepEqual(detail.selectedDocuments, list[0].selectedDocuments);
  }
});

test('real C02 is public, RULE_BASED, unsaved and creates no intake ID', async () => {
  const before = await fetch(`${base}/api/intakes`).then(response => response.json());
  const result = await api.classify({ caseType: 'UNKNOWN', coreStatement: '개발 검수용: 돈을 빌려줬는데 돌려받지 못하고 있어요.' });
  assert.equal(result.caseType, 'CIVIL');
  assert.equal(result.classificationMethod, 'RULE_BASED');
  assert.equal(result.persisted, false);
  assert.equal(result.id, undefined);
  const after = await fetch(`${base}/api/intakes`).then(response => response.json());
  assert.deepEqual(after.map(item => item.id), before.map(item => item.id));
});
test('real C02 supports all five selected values and UNKNOWN', async () => {
  for (const caseType of ['CIVIL', 'CRIMINAL', 'FAMILY', 'ELECTRONIC_LITIGATION', 'UNKNOWN']) {
    assert.equal((await api.classify({ caseType, coreStatement: '개발 검수용 분류 확인' })).caseType, caseType);
  }
});
test('real server rejects blank and 301 chars with DTO 400 field errors', async () => {
  for (const coreStatement of [' ', '가'.repeat(301)]) {
    const response = await fetch(`${base}/api/intakes/classify-preview`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ caseType: 'CIVIL', coreStatement }) });
    assert.equal(response.status, 400);
    assert.ok((await response.json()).fieldErrors.coreStatement);
  }
});
test('real R01/R02 use returned codes for each supported domain', async () => {
  for (const domain of ['CIVIL', 'CRIMINAL', 'FAMILY', 'ELECTRONIC_LITIGATION']) {
    const list = await api.procedures(domain);
    assert.ok(list.length);
    assert.ok(list.every(item => item.domain === domain));
    const detail = await api.procedure(list[0].code);
    assert.equal(detail.code, list[0].code);
    assert.ok(detail.steps.length);
  }
});
test('real C01 → authenticated C03 → C05 preserves exact generated intake ID', async () => {
  const auth = await api.login('user', 'user123');
  assert.equal(auth.username, 'user');
  assert.equal(auth.role, 'ROLE_USER');
  const signedIn = createIntakeApi({ base, getToken: () => auth.accessToken });
  const body = intakePayload({ inquiryPurpose: '절차 안내', coreStatement: '' }, { caseType: 'CIVIL' }, {
    partyPosition: '원고', coreStatement: '개발 검수용 계약 문의 '.padEnd(100, '가'), desiredNextStep: '개발 검수용 안내 확인',
    incidentDate: '2024-02-29', clientName: '가상 검수인', clientPhone: '010-0000-0000', counterpart: '가상 검수업체', amountOrDamage: '5,000,000원',
    currentStage: '상대방에게 변제 요청 중', preferredRegion: '서울특별시', officeType: '법무법인', experienceLevel: '10년 이상',
    publicCareerPreference: '상관없음', consultationStyle: '전화 상담', lawyerSearchPurpose: '개발 검수용 상담 목적', documents: ['계약서', '이체 내역'],
  });
  const created = await signedIn.create(body);
  assert.ok(created.id > 0);
  assert.deepEqual(created.uploadedDocuments, []);
  const detail = await signedIn.intake(created.id);
  assert.equal(detail.id, created.id);
  assert.equal(detail.inquiryPurpose, '절차 안내');
  assert.equal(detail.caseType, 'CIVIL');
  assert.equal(detail.incidentDate, body.incidentDate);
  assert.equal(detail.partyPosition, body.partyPosition);
  assert.equal(detail.amountOrDamage, body.amountOrDamage);
  assert.equal(detail.currentStage, body.currentStage);
  assert.equal(detail.preferredRegion, body.preferredRegion);
  assert.deepEqual(detail.selectedDocuments, body.documents);
  assert.equal(detail.lawyerSearchCondition.intakeId, created.id);
  for (const field of ['preferredRegion', 'officeType', 'experienceLevel', 'publicCareerPreference', 'consultationStyle']) assert.equal(detail.lawyerSearchCondition[field], body[field]);
  const beforeGuide = await fetch(`${base}/api/intakes`).then(response => response.json());
  const guide = await api.minimal(detail);
  assert.equal(guide.inquiryPurpose, detail.inquiryPurpose);
  assert.equal(guide.caseType, detail.caseType);
  assert.equal(guide.partyRole, detail.partyPosition);
  assert.equal(guide.id, undefined);
  const afterGuide = await fetch(`${base}/api/intakes`).then(response => response.json());
  assert.deepEqual(afterGuide.map(item => item.id), beforeGuide.map(item => item.id));
  const search = await signedIn.createSearch(detail.id, '화면04 개발 검수용 검색 목적');
  assert.equal(search.intakeId, detail.id);
  assert.equal(search.conditionId, detail.lawyerSearchCondition.id);
  assert.equal(search.status, 'PREVIEW_READY');
  const savedSearch = await signedIn.searchRequest(detail.id, search.id);
  assert.equal(savedSearch.id, search.id);
  assert.equal(savedSearch.searchPurpose, '화면04 개발 검수용 검색 목적');
  assert.deepEqual(savedSearch.results, []);
  const ownerHistory = await fetch(`${base}/api/intakes/${created.id}/search-requests`, { headers: { Authorization: `Bearer ${auth.accessToken}` } });
  assert.equal(ownerHistory.status, 200);
  const anonymousHistory = await fetch(`${base}/api/intakes/${created.id}/search-requests`);
  assert.equal(anonymousHistory.status, 401);
});

test('real C03 rejects supplied invalid credentials without creating an anonymous inquiry', async () => {
  const before = await fetch(`${base}/api/intakes`).then(response => response.json());
  const result = await fetch(`${base}/api/intakes`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: 'Bearer invalid-token' },
    body: JSON.stringify({ inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '검수용', coreStatement: '검수용 계약 문의', desiredNextStep: '검수 안내', documents: [] }),
  });
  assert.equal(result.status, 401);
  const after = await fetch(`${base}/api/intakes`).then(response => response.json());
  assert.deepEqual(after.map(item => item.id), before.map(item => item.id));
});

test('real C05 missing ID fails with 404 instead of a blank success screen', async () => {
  await assert.rejects(api.intake('9223372036854775807'), error => error.status === 404);
});

test('real C01 rejects a wrong password without returning a token', async () => {
  await assert.rejects(api.login('user', 'wrong-test-password'), error => error.status === 401 || error.status === 403);
});

test('real Screen03 R02/R03/R04/R05 retain procedure, step checklist and form identity for all domains', async () => {
  for (const domain of ['CIVIL', 'CRIMINAL', 'FAMILY', 'ELECTRONIC_LITIGATION']) {
    const [procedure] = await api.procedures(domain);
    const [detail, steps, forms] = await Promise.all([api.procedure(procedure.code), api.procedureSteps(procedure.code), api.forms(procedure.code)]);
    assert.deepEqual(steps, detail.steps);
    assert.deepEqual(forms, detail.forms);
    for (const form of forms) assert.deepEqual(await api.form(form.formCode, procedure.code), form);
  }
});

test('real Screen04 C04 accepts the five string case types without using the separate ontology enum', async () => {
  for (const caseType of ['CIVIL', 'CRIMINAL', 'FAMILY', 'ELECTRONIC_LITIGATION', 'UNKNOWN']) {
    const guide = await api.minimal(testIntake(42, { caseType, coreStatementMasked: '개발 검수용 상황 정리' }));
    assert.equal(guide.caseType, caseType);
    assert.ok(guide.recommendedDocuments.length);
  }
});

test('real Screen04 R06 is a public criminal complainant supplementary-investigation checklist', async () => {
  const checklist = await api.criminalChecklist();
  assert.equal(checklist.domain, 'CRIMINAL');
  assert.equal(checklist.role, 'COMPLAINANT');
  assert.ok(checklist.checklist.length);
});
