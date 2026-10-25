/* Q01: disposable local Spring + production React + real Chrome. No provider calls. */
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { spawn, execFileSync } = require('node:child_process');
const { randomBytes, createHmac, createHash } = require('node:crypto');
const assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const runtimeRequire = createRequire(path.join(process.env.USERPROFILE, '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/package.json'));
const { chromium } = runtimeRequire('playwright');
const root = path.resolve(__dirname, '..');
const out = path.join(root, 'output/q01-cross-role-2026-10-25');
const backendPath = path.join(root, 'backend/guardrail-ready-policy-checker');
const dist = path.join(root, 'frontend/legal-procedure-viewer/dist');
const runtime = JSON.parse(fs.readFileSync(path.join(backendPath, '.local/runtime-logs/server-8080.json'), 'utf8').replace(/^\uFEFF/, ''));
const base = 'http://127.0.0.1:18101';
const runId = Date.now().toString();
const database = path.join(backendPath, '.local', `q01-${runId}`, 'qa-db');
const java = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot/bin/java.exe';
const signingKey = randomBytes(32);
const tokens = {}, checks = [], failures = [], wire = [], browserWire = [], browserErrors = [], layouts = [];
const passwords = { user: 'user123', user2: 'user2123', lawyer: 'lawyer123', lawyer2: 'lawyer2123', admin: 'admin123' };
fs.mkdirSync(out, { recursive: true });
fs.mkdirSync(path.dirname(database), { recursive: true });
const stdout = fs.openSync(path.join(out, 'isolated-server.log'), 'w');
const serverProcess = spawn(java, ['-jar', runtime.jar, '--server.address=127.0.0.1', '--server.port=18101', '--spring.profiles.active=local-persistent', '--spring.jpa.hibernate.ddl-auto=update', '--llm.api-key=', '--llm.base-url=http://127.0.0.1:1', '--guardrails.base-url=http://127.0.0.1:1', '--search.enabled=false'], { cwd: backendPath, env: { ...process.env, LOCAL_H2_DB_PATH: database.replaceAll('\\', '/'), JWT_SECRET: signingKey.toString('base64') }, windowsHide: true, stdio: ['ignore', stdout, stdout] });
const story = 'Q01 가상 검수: 돈을 빌려줬는데 돌려받지 못했습니다.';
const memoText = 'Q01 변호사 내부 전용 메모입니다.';
let server, browser, context, front, intakeId, otherId, documentId, searchId;
async function call(endpoint, who = 'admin', method = 'GET', body, headers = {}) {
  const isForm = body instanceof FormData;
  const response = await fetch(base + endpoint, { method, signal: AbortSignal.timeout(15000), headers: { ...(tokens[who] ? { Authorization: `Bearer ${tokens[who]}` } : {}), ...(body && !isForm ? { 'Content-Type': 'application/json' } : {}), ...headers }, ...(body ? { body: isForm ? body : JSON.stringify(body) } : {}) });
  const type = response.headers.get('content-type') || '';
  const data = type.includes('json') ? await response.json() : await response.text();
  wire.push({ endpoint, identity: who, method, status: response.status });
  return { status: response.status, data };
}
async function run(id, title, action) {
  try { await action(); checks.push({ id, title, passed: true }); console.log(`${id} PASS ${title}`); }
  catch (error) { failures.push({ id, title, message: error.message }); console.log(`${id} FAIL ${error.message.slice(0, 250)}`); }
}
async function hash(page, value) { await page.evaluate(value => { location.hash = value; }, value); }
async function login(page, who, operator = false, navigate = true) {
  if (navigate) await page.goto(front + (operator ? '/operator.html#/overview' : '/intake.html#/login'));
  if (operator) { await page.getByText('서버 연결 설정', { exact: true }).click(); await page.getByRole('textbox', { name: 'API 서버', exact: true }).fill(base); }
  await page.locator('input[name=username]').fill(who);
  await page.locator('input[name=password]').fill(passwords[who]);
  await page.locator(operator ? 'form[aria-label="관리자 로그인"]' : 'form[aria-label="로그인"]').getByRole('button', { name: operator ? '관리자 로그인' : '로그인', exact: true }).click();
  await page.waitForFunction(() => !document.querySelector('input[name=password]'));
}
async function layout(page, label) {
  await page.screenshot({ path: path.join(out, `${label}-pc.png`), fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: path.join(out, `${label}-390.png`), fullPage: true });
  const sizes = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth }));
  layouts.push({ label, ...sizes });
  assert(sizes.document <= sizes.viewport, `${label}: document overflows ${sizes.document}/${sizes.viewport}`);
  await page.setViewportSize({ width: 1672, height: 941 });
}
function expiredToken() {
  const b64 = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const unsigned = b64({ alg: 'HS256', typ: 'JWT' }) + '.' + b64({ sub: 'user', role: 'ROLE_USER', iat: Math.floor(Date.now() / 1000) - 120, exp: Math.floor(Date.now() / 1000) - 60 });
  return unsigned + '.' + createHmac('sha256', signingKey).update(unsigned).digest('base64url');
}
async function main() {
  const deadline = Date.now() + 60000;
  while (true) {
    try { if ((await fetch(base + '/actuator/health')).ok) break; } catch {}
    if (Date.now() > deadline || serverProcess.exitCode !== null) throw new Error('Disposable QA server did not become healthy.');
    await new Promise(resolve => setTimeout(resolve, 300));
  }
  for (const who of Object.keys(passwords)) { const result = await call('/api/auth/login', 'anonymous', 'POST', { username: who, password: passwords[who] }); assert.equal(result.status, 200); tokens[who] = result.data.accessToken; }
  server = http.createServer((request, response) => {
    if (request.url.startsWith('/api/')) {
      const upstream = http.request(base + request.url, { method: request.method, headers: { ...request.headers, host: new URL(base).host } }, result => { response.writeHead(result.statusCode, result.headers); result.pipe(response); });
      upstream.on('error', () => { response.writeHead(502); response.end(); }); request.pipe(upstream); return;
    }
    const file = path.resolve(dist, '.' + decodeURIComponent(new URL(request.url, 'http://localhost').pathname));
    if (!file.startsWith(dist + path.sep) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) { response.writeHead(404); response.end(); return; }
    response.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream'); fs.createReadStream(file).pipe(response);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); front = 'http://127.0.0.1:' + server.address().port;
  browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  context = await browser.newContext({ viewport: { width: 1672, height: 941 }, acceptDownloads: true });
  context.on('page', page => { page.setDefaultTimeout(10000); page.on('pageerror', error => browserErrors.push(error.message)); page.on('response', response => { const url = new URL(response.url()); if (url.pathname.startsWith('/api/')) browserWire.push({ path: url.pathname, method: response.request().method(), status: response.status() }); }); });
  const user = await context.newPage();
  await run('Q01-01', 'Guest classification/write allowed; save really returns401 and preserves fields', async () => {
    await user.goto(front + '/intake.html#/login'); await layout(user, 'screen01');
    await user.getByRole('link', { name: /의뢰인 데모/ }).click();
    await user.locator('#core-statement').fill(story);
    await user.getByRole('button', { name: '사건 유형 확인하기', exact: true }).click();
    await user.getByRole('button', { name: '다음: 문의 작성', exact: true }).waitFor(); await layout(user, 'screen02');
    await user.getByRole('button', { name: '다음: 문의 작성', exact: true }).click();
    await user.locator('input[name=partyPosition][value="원고"]').check({ force: true }); await user.locator('select[name=desiredNextStep]').selectOption({ index: 1 });
    await layout(user, 'screen03');
    await Promise.all([user.waitForResponse(r => new URL(r.url()).pathname === '/api/intakes' && r.request().method() === 'POST' && r.status() === 401), user.getByRole('button', { name: '문의 접수하기', exact: true }).click()]);
    assert.equal(await user.locator('[name=coreStatement]').inputValue(), story);
    assert.equal((await call('/api/intakes')).data.length, 0);
  });
  await run('Q01-02', 'Login from draft; C03→C05/C04→C06/P02/C07 preserve actual intake/condition/request IDs', async () => {
    await user.locator('.intake-account').getByRole('button', { name: '로그인', exact: true }).click();
    await login(user, 'user', false, false);
    const created = user.waitForResponse(r => new URL(r.url()).pathname === '/api/intakes' && r.request().method() === 'POST' && r.status() === 201);
    await user.getByRole('button', { name: '문의 접수하기', exact: true }).click(); intakeId = (await (await created).json()).id;
    await user.waitForURL(`**#/intake/${intakeId}`); await user.getByRole('link', { name: /검색 요청 준비하기/ }).waitFor(); await layout(user, 'screen04');
    const saved = (await call('/api/intakes/' + intakeId, 'user')).data;
    assert(saved.lawyerSearchCondition.id); assert(!Object.hasOwn(saved, 'consultationMemos'));
    await user.getByRole('link', { name: /검색 요청 준비하기/ }).click(); await user.getByRole('button', { name: '검색 요청 기록하기', exact: true }).waitFor();
    const searchResponse = user.waitForResponse(r => new URL(r.url()).pathname === `/api/intakes/${intakeId}/search-requests` && r.request().method() === 'POST' && r.status() === 201);
    await user.getByRole('button', { name: '검색 요청 기록하기', exact: true }).click(); const search = await (await searchResponse).json(); searchId = search.id;
    assert.equal(search.intakeId, intakeId); assert.equal(search.conditionId, saved.lawyerSearchCondition.id);
    await user.waitForURL(`**#/intake/${intakeId}/search/${searchId}`); await user.getByRole('heading', { name: /선택한 검색 요청 상세/ }).waitFor();
    const history = (await call(`/api/intakes/${intakeId}/search-requests`, 'user')).data; assert(history.some(row => row.id === searchId));
    await layout(user, 'screen05');
  });
  if (!intakeId) throw new Error('Primary synthetic inquiry was not created; dependent checks cannot proceed.');
  const other = await call('/api/intakes', 'user2', 'POST', { sessionId: 'q01-other', inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고', coreStatement: 'Q01 다른 의뢰인의 가상 사건입니다.', desiredNextStep: '절차 안내', documents: ['계약서'] }, { 'Idempotency-Key': 'q01-other-' + runId }); assert.equal(other.status, 201); otherId = other.data.id;
  const upload = new FormData(); upload.set('file', new Blob(['Q01 synthetic document. No real personal data.'], { type: 'text/plain' }), 'q01-document.txt'); upload.set('documentType', 'CONTRACT');
  const uploaded = await call(`/api/intakes/${intakeId}/documents`, 'user', 'POST', upload); assert.equal(uploaded.status, 201); documentId = uploaded.data.id;
  await run('Q01-03', 'Other USER cannot list/read/search/upload/download another owner’s inquiry; mismatched document404', async () => {
    const list = (await call('/api/intakes', 'user2')).data; assert.deepEqual(list.map(row => row.id), [otherId]);
    for (const endpoint of [`/api/intakes/${intakeId}`, `/api/intakes/${intakeId}/documents/${documentId}/download`, `/api/intakes/${intakeId}/search-requests`]) assert.equal((await call(endpoint, 'user2')).status, 403);
    assert.equal((await call(`/api/intakes/${otherId}/documents/${documentId}/download`, 'user2')).status, 404);
    assert.equal((await call(`/api/intakes/${intakeId}/documents/${documentId}/download`, 'anonymous')).status, 401);
    const rejectedUpload = new FormData(); rejectedUpload.set('file', new Blob(['synthetic'], { type: 'text/plain' }), 'denied.txt'); assert.equal((await call(`/api/intakes/${intakeId}/documents`, 'user2', 'POST', rejectedUpload)).status, 403);
    assert.equal((await call(`/api/intakes/${intakeId}/search-requests`, 'user2', 'POST', { searchPurpose: '가상 검수' })).status, 403);
    await hash(user, `#/lawyer/intakes/${intakeId}`); await user.getByRole('button', { name: /다운로드/ }).waitFor();
    const downloadEvent = user.waitForEvent('download'); await user.getByRole('button', { name: /다운로드/ }).click(); assert.equal((await downloadEvent).suggestedFilename(), 'q01-document.txt');
    const denied = await context.newPage(); await login(denied, 'user2'); await hash(denied, `#/lawyer/intakes/${intakeId}`); await denied.getByText('이 요청에 대한 접근 권한이 없습니다.', { exact: true }).waitFor(); assert(!(await denied.locator('main').innerText()).includes(story));
  });
  const admin = await context.newPage(), a = await context.newPage(), b = await context.newPage();
  await run('Q01-04', 'ADMIN sees all inquiries and assigns lawyer; unassigned lawyers get403', async () => {
    for (const who of ['lawyer', 'lawyer2']) assert.equal((await call('/api/intakes/' + intakeId, who)).status, 403);
    await login(admin, 'admin'); await hash(admin, '#/lawyer/intakes');
    for (const id of [intakeId, otherId]) await admin.getByRole('link', { name: `문의 ${id} 상세 보기`, exact: true }).waitFor();
    await admin.getByRole('link', { name: `문의 ${intakeId} 상세 보기`, exact: true }).click();
    await admin.locator('#assigned-lawyer').fill('lawyer'); await admin.getByRole('button', { name: '담당 배정 저장', exact: true }).click(); await admin.getByText('현재 담당:', { exact: false }).filter({ hasText: 'lawyer' }).waitFor();
    assert.equal((await call('/api/intakes/' + intakeId, 'lawyer')).status, 200);
    assert.equal((await call('/api/intakes/' + intakeId, 'lawyer2')).status, 403);
  });
  await run('Q01-05', 'LAWYER assigned list→detail→status and internal memo save; USER JSON omits memo entirely', async () => {
    await login(a, 'lawyer'); await a.getByRole('link', { name: `문의 ${intakeId} 상세 보기`, exact: true }).waitFor(); await layout(a, 'screen06');
    assert.equal(await a.getByRole('link', { name: `문의 ${otherId} 상세 보기`, exact: true }).count(), 0);
    await a.getByRole('link', { name: `문의 ${intakeId} 상세 보기`, exact: true }).click(); await a.getByRole('link', { name: /상태 · 상담 메모 관리/ }).waitFor(); await layout(a, 'screen08');
    await a.getByRole('link', { name: /상태 · 상담 메모 관리/ }).click(); await a.locator('select[name=nextStatus]').waitFor(); await layout(a, 'screen07');
    await a.locator('select[name=nextStatus]').selectOption('REVIEWED'); await a.getByRole('button', { name: '상태 변경 저장', exact: true }).click(); await a.locator('.consultation-success').waitFor();
    await a.locator('[name=memoContent]').fill(memoText); await a.getByRole('button', { name: '메모 추가 저장', exact: true }).click(); await a.getByText('메모를 추가 저장했습니다.', { exact: true }).waitFor();
    const own = (await call('/api/intakes/' + intakeId, 'user')).data; assert(!Object.hasOwn(own, 'consultationMemos')); assert(!JSON.stringify(own).includes(memoText));
    const staff = (await call('/api/intakes/' + intakeId, 'lawyer')).data; assert.equal(staff.consultationMemos.length, 1);
    assert.equal((await call(`/api/intakes/${intakeId}/status`, 'user', 'PATCH', { status: 'CLOSED', expectedVersion: own.version })).status, 403);
    assert.equal((await call(`/api/intakes/${intakeId}/memo`, 'user', 'POST', { content: 'denied' })).status, 403);
  });
  await run('Q01-06', 'Two LAWYER tabs409 retain memo/checklist/selection; explicit refresh confirmation recovers', async () => {
    await login(b, 'lawyer'); await hash(b, `#/lawyer/intakes/${intakeId}/consultation`); await b.locator('select[name=nextStatus]').waitFor();
    await a.locator('select[name=nextStatus]').selectOption('NEED_MORE_INFO'); await a.getByRole('button', { name: '상태 변경 저장', exact: true }).click(); await a.locator('.consultation-success').waitFor();
    await b.locator('[name=memoContent]').fill('Q01 충돌 후 유지할 메모'); await b.locator('input[value=REQUEST_DOCUMENTS]').check(); await b.locator('select[name=nextStatus]').selectOption('CONSULTATION_READY');
    await Promise.all([b.waitForResponse(r => r.url().endsWith('/status') && r.status() === 409), b.getByRole('button', { name: '상태 변경 저장', exact: true }).click()]);
    await b.getByRole('button', { name: '확인 후 편집 계속', exact: true }).waitFor(); assert.equal(await b.locator('[name=memoContent]').inputValue(), 'Q01 충돌 후 유지할 메모'); assert(await b.locator('input[value=REQUEST_DOCUMENTS]').isChecked()); assert.equal(await b.locator('select[name=nextStatus]').inputValue(), 'CONSULTATION_READY'); assert(await b.getByRole('button', { name: '메모 추가 저장', exact: true }).isDisabled());
    await layout(b, 'screen07-conflict');
    await b.getByRole('button', { name: '확인 후 편집 계속', exact: true }).click(); await Promise.all([b.waitForResponse(r => r.url().endsWith('/status') && r.status() === 200), b.getByRole('button', { name: '상태 변경 저장', exact: true }).click()]);
  });
  await run('Q01-07', 'ADMIN stale edit409 recovers; reassignment/revocation removes old lawyer data and write access', async () => {
    await hash(admin, `#/lawyer/intakes/${intakeId}/consultation`); await admin.locator('select[name=nextStatus]').waitFor();
    const latest = (await call('/api/intakes/' + intakeId)).data;
    assert.equal((await call(`/api/intakes/${intakeId}/status`, 'lawyer', 'PATCH', { status: 'REVIEWED', expectedVersion: latest.version })).status, 200);
    await admin.locator('[name=memoContent]').fill('Q01 관리자 충돌 메모'); await admin.locator('select[name=nextStatus]').selectOption('NEED_MORE_INFO');
    await Promise.all([admin.waitForResponse(r => r.url().endsWith('/status') && r.status() === 409), admin.getByRole('button', { name: '상태 변경 저장', exact: true }).click()]); await admin.getByRole('button', { name: '확인 후 편집 계속', exact: true }).waitFor(); assert.equal(await admin.locator('[name=memoContent]').inputValue(), 'Q01 관리자 충돌 메모'); await admin.getByRole('button', { name: '확인 후 편집 계속', exact: true }).click(); await admin.getByRole('button', { name: '상태 변경 저장', exact: true }).click(); await admin.locator('.consultation-success').waitFor();
    await hash(admin, `#/lawyer/intakes/${intakeId}`); await admin.locator('#assigned-lawyer').fill('lawyer2'); await admin.getByRole('button', { name: '담당 배정 저장', exact: true }).click(); await admin.getByText('현재 담당:', { exact: false }).filter({ hasText: 'lawyer2' }).waitFor();
    await b.locator('[name=memoContent]').fill('Q01 회수 후 저장 금지'); await Promise.all([b.waitForResponse(r => r.url().endsWith('/memo') && r.status() === 403), b.getByRole('button', { name: '메모 추가 저장', exact: true }).click()]); await b.getByText('이 요청에 대한 접근 권한이 없습니다.', { exact: true }).waitFor(); assert.equal(await b.locator('[name=memoContent]').count(), 0); assert(!(await b.locator('main').innerText()).includes(memoText));
    assert.equal((await call(`/api/intakes/${intakeId}/documents/${documentId}/download`, 'lawyer')).status, 403); assert.equal((await call('/api/intakes/' + intakeId, 'lawyer2')).status, 200);
    await admin.locator('#assigned-lawyer').fill(''); await admin.getByRole('button', { name: '담당 배정 저장', exact: true }).click(); await admin.getByText('현재 담당:', { exact: false }).filter({ hasText: '미배정' }).waitFor(); assert.equal((await call('/api/intakes/' + intakeId, 'lawyer2')).status, 403);
  });
  await run('Q01-08', 'Operator login rejects USER/LAWYER; actual admin API403 and admin success', async () => {
    for (const who of ['user', 'lawyer']) {
      const page = await context.newPage(); await page.goto(front + '/operator.html#/overview'); await page.getByText('서버 연결 설정', { exact: true }).click(); await page.getByRole('textbox', { name: 'API 서버', exact: true }).fill(base); await page.locator('input[name=username]').fill(who); await page.locator('input[name=password]').fill(passwords[who]); await page.getByRole('button', { name: '관리자 로그인', exact: true }).click(); await page.getByText('관리자 계정으로 로그인해 주세요.', { exact: true }).waitFor(); assert.equal(await page.locator('input[name=password]').inputValue(), ''); assert.equal((await call('/api/admin/dashboard/summary', who)).status, 403);
    }
    const operator = await context.newPage(); await login(operator, 'admin', true); await operator.getByRole('heading', { name: '운영 현황을 한눈에 확인하세요.', exact: true }).waitFor(); assert.equal((await call('/api/admin/dashboard/summary')).status, 200); await operator.locator('.operator-menu summary').click(); await operator.getByRole('button', { name: '로그아웃', exact: true }).click(); await operator.locator('input[name=password]').waitFor();
  });
  await run('Q01-09', 'Wrong password401 clears password and retains no authenticated view', async () => {
    const page = await context.newPage(); await page.goto(front + '/intake.html#/login'); await page.locator('input[name=username]').fill('user'); await page.locator('input[name=password]').fill('q01-wrong-password'); await Promise.all([page.waitForResponse(r => r.url().endsWith('/api/auth/login') && r.status() === 401), page.getByRole('button', { name: '로그인', exact: true }).click()]); await page.getByText('아이디 또는 비밀번호가 올바르지 않습니다. 다시 확인해 주세요.', { exact: true }).waitFor(); assert.equal(await page.locator('input[name=password]').inputValue(), ''); assert.equal(await page.locator('.intake-account a').count(), 0);
  });
  await run('Q01-10', 'Cryptographically valid expired JWT gets actual401; old protected data unmounts; no browser token storage', async () => {
    const page = await context.newPage(); await login(page, 'user'); await hash(page, `#/lawyer/intakes/${intakeId}`); await page.getByRole('button', { name: /다운로드/ }).waitFor();
    const expired = expiredToken(); assert.equal((await call('/api/me', 'anonymous', 'GET', null, { Authorization: 'Bearer ' + expired })).status, 401);
    await page.route('**/api/intakes', route => route.continue({ headers: { ...route.request().headers(), authorization: 'Bearer ' + expired } })); await hash(page, '#/lawyer/intakes'); await page.getByRole('heading', { name: '로그인이 필요합니다.', exact: true }).waitFor(); assert(!(await page.locator('main').innerText()).includes(story)); assert.equal(await page.locator('.intake-account').innerText().then(text => text.includes('비회원')), true);
    const storage = await page.evaluate(() => [...Object.values(localStorage), ...Object.values(sessionStorage)].join(' ')); assert(!/eyJ[A-Za-z0-9_-]+\./.test(storage));
  });
  await run('Q01-11', 'Full reload ends memory session and explicitly explains re-login', async () => {
    const page = await context.newPage(); await login(page, 'user'); await hash(page, `#/intake/${intakeId}`); await page.getByRole('link', { name: /검색 요청 준비하기/ }).waitFor(); await page.reload(); await page.getByRole('heading', { name: '로그인이 필요합니다.', exact: true }).waitFor(); assert((await page.locator('main').innerText()).includes('새로고침'), 'Protected page must explain that refresh ends the in-memory session'); await layout(page, 'session-reload');
  });
  await run('Q01-12', 'Logout clears inquiry drafts, old list and search filter before another account', async () => {
    const page = await context.newPage(); await login(page, 'user'); await page.locator('#core-statement').fill('Q01 로그아웃 후 지워야 할 내용'); await hash(page, '#/lawyer/intakes'); await page.locator('input[name=query]').fill('Q01 이전 계정 검색어'); await page.getByRole('button', { name: '로그아웃', exact: true }).click(); await page.getByRole('heading', { name: '로그인이 필요합니다.', exact: true }).waitFor(); assert.equal(await page.locator('.inquiry-table').count(), 0); await hash(page, '#/login'); await login(page, 'user2', false, false); assert.equal(await page.locator('#core-statement').inputValue(), ''); await hash(page, '#/lawyer/intakes'); await page.locator('input[name=query]').waitFor(); assert.equal(await page.locator('input[name=query]').inputValue(), '', 'Previous account search filter must be cleared'); await page.getByRole('link', { name: `문의 ${otherId} 상세 보기`, exact: true }).waitFor(); assert.equal(await page.getByRole('link', { name: `문의 ${intakeId} 상세 보기`, exact: true }).count(), 0);
  });
  await run('Q01-13', 'Changing identity in same tab clears prior draft/filter and protected components', async () => {
    const page = await context.newPage(); await login(page, 'user'); await page.locator('#core-statement').fill('Q01 계정 변경 전 입력'); await hash(page, '#/lawyer/intakes'); await page.locator('input[name=query]').fill('Q01 계정 변경 전 검색어'); await hash(page, '#/login'); await login(page, 'user2', false, false); assert.equal(await page.locator('#core-statement').inputValue(), ''); await hash(page, '#/lawyer/intakes'); await page.locator('input[name=query]').waitFor(); assert.equal(await page.locator('input[name=query]').inputValue(), '', 'Identity change must reset prior account filters');
  });
  await run('Q01-14', 'No JavaScript errors; screens01–08 and conflict390px layouts checked; no execute/LLM calls', async () => {
    assert.deepEqual(browserErrors, []); assert.equal(layouts.filter(item => /^screen0[1-8]$/.test(item.label)).length, 8);
    assert(![...wire.map(row => row.endpoint), ...browserWire.map(row => row.path)].some(value => /\/execute$|\/api\/ai\/chat|\/guardrail\//.test(value)));
  });
}
(async () => {
  try { await main(); }
  catch (error) { failures.push({ id: 'HARNESS', message: error.message }); console.log('HARNESS FAIL ' + error.message.slice(0, 250)); }
  finally {
    if (browser) await browser.close();
    if (server) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
    if (serverProcess.exitCode === null) {
      try { const tool = path.join(root, 'tmp/local-stop-tools'); execFileSync(java, ['--add-modules', 'jdk.attach', '-cp', tool, 'AttachSnapshot', String(serverProcess.pid), path.join(tool, 'exit-agent.jar'), 'stop'], { windowsHide: true, stdio: 'pipe', timeout: 10000 }); await new Promise(resolve => { if (serverProcess.exitCode !== null) resolve(); else { serverProcess.once('exit', resolve); setTimeout(resolve, 10000); } }); }
      catch { if (serverProcess.exitCode === null) serverProcess.kill(); }
    }
    fs.closeSync(stdout);
    const result = { passed: failures.length === 0, checkedAt: new Date().toISOString(), checks, failures, layouts, wire, browserWire, browserErrors, qa: { intakeId, otherId, documentId, searchId }, backendPort: 18101, backendPid: serverProcess.pid, qaServerStopped: serverProcess.exitCode !== null, jarSha256: createHash('sha256').update(fs.readFileSync(runtime.jar)).digest('hex'), userServer8080Changed: false, paidProviderCalls: 0, expiryFixture: 'Valid signature, past expiry, disposable QA signing key only; never reads production secrets.' };
    fs.writeFileSync(path.join(out, 'verification.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify({ passed: result.passed, checks: checks.length, failures: failures.length, httpRequests: wire.length, browserResponses: browserWire.length, layouts: layouts.length, qaServerStopped: result.qaServerStopped, paidProviderCalls: 0 }));
    if (!result.passed) process.exitCode = 1;
  }
})();
