// Presentation server only. Requests to paid-generation paths are intercepted.
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const { chromium } = createRequire(path.join(process.env.USERPROFILE, '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/package.json'))('playwright');
const root = path.resolve(__dirname, '..'), out = path.join(root, 'output/q-final-2026-10-25');
const base = 'http://127.0.0.1:18080', front = 'http://127.0.0.1:5176', provider = 'http://127.0.0.1:18083';
const checks = [], errors = [], requests = [];
const mark = name => { checks.push({ name, pass: true }); console.log(name); };
async function read(url, token, method = 'GET', body) {
  const response = await fetch(base + url, { method, headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  assert.ok(response.ok, `${method} ${url}: ${response.status}`); return response.json();
}
async function login(page, who, operator) {
  await page.goto(front + (operator ? '/operator.html#/overview' : '/intake.html#/login'));
  await page.locator('[name=username]').fill(who);
  await page.locator('[name=password]').fill(who === 'admin' ? 'admin123' : 'user123');
  await page.getByRole('button', { name: operator ? '관리자 로그인' : '로그인', exact: true }).click();
  await page.locator('[name=password]').waitFor({ state: 'detached' });
}
async function connectMetrics(page, password = 'admin123', buttonName = '지표 연결') {
  await page.getByRole('button', { name: buttonName, exact: true }).click();
  await page.locator('#metrics-user').fill('admin');
  await page.locator('#metrics-password').fill(password);
  await page.getByRole('button', { name: '연결하고 지표 조회', exact: true }).click();
}
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const before = await fetch(provider + '/__demo/status').then(r => r.json());
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  const context = await browser.newContext({ viewport: { width: 1672, height: 1000 } });
  const page = await context.newPage();
  page.setDefaultTimeout(10000);
  page.on('pageerror', error => errors.push(error.message));
  page.on('request', request => {
    const url = new URL(request.url());
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) requests.push({ path: url.pathname, method: request.method(), authentication: request.headers().authorization?.split(' ')[0] || 'none' });
  });
  try {
    const userToken = (await read('/api/auth/login', null, 'POST', { username: 'user', password: 'user123' })).accessToken;
    const intake = await read('/api/intakes', userToken, 'POST', { sessionId: 'q02-local-' + Date.now(), inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고', coreStatement: '가상 대여금 문의의 검색 복구 검수입니다.', desiredNextStep: '절차 안내', preferredRegion: '서울특별시' });
    await login(page, 'user', false);
    await page.evaluate(hash => { location.hash = hash; }, '/intake/' + intake.id + '/search');
    await page.locator('[name=searchPurpose]').fill('Q02 가상 검색 요청');
    let created, creates = 0;
    const createPattern = '**/api/intakes/' + intake.id + '/search-requests';
    await page.route(createPattern, async route => {
      if (route.request().method() !== 'POST') return route.continue();
      creates++; const response = await route.fetch(); created = await response.json(); await route.abort('failed');
    });
    await page.getByRole('button', { name: '검색 요청 기록하기', exact: true }).click();
    await page.getByText('서버의 저장 여부를 확인할 수 없습니다.', { exact: false }).waitFor();
    assert.equal(creates, 1);
    await page.unroute(createPattern);
    let failRead = true;
    await page.route(createPattern, route => failRead ? route.fulfill({ status: 500, json: { message: 'Q02 synthetic read failure' } }) : route.continue());
    await page.getByRole('button', { name: '최신 저장 정보 조회', exact: true }).click();
    await page.getByRole('button', { name: '최신 저장 정보 조회', exact: true }).waitFor({ state: 'visible' });
    assert.equal(await page.getByRole('button', { name: '중복 가능성 확인 후 편집 계속', exact: true }).count(), 0);
    assert.ok(await page.getByRole('button', { name: '검색 요청 기록하기', exact: true }).isDisabled());
    mark('Q02 C06 committed response loss: failed verification cannot unlock a second POST');
    failRead = false;
    await page.getByRole('button', { name: '최신 저장 정보 조회', exact: true }).click();
    await page.getByRole('button', { name: '중복 가능성 확인 후 편집 계속', exact: true }).click();
    await page.getByRole('radio', { name: `검색 요청 ${created.id} 선택`, exact: true }).click();
    await page.waitForURL('**#/intake/' + intake.id + '/search/' + created.id);
    await page.getByText('SR-' + created.id, { exact: true }).first().waitFor();
    assert.equal((await read('/api/intakes/' + intake.id + '/search-requests', userToken)).length, 1);
    assert.equal(created.conditionId, intake.lawyerSearchCondition.id);
    mark('Q02 history recovery selects the saved intake/condition/search IDs without creating a duplicate');
    const form = page.getByRole('form', { name: `검색 요청 ${created.id} 결과 링크 기록`, exact: true });
    await form.locator('[name=sourceType]').selectOption('CASE_LAW');
    await form.locator('[name=title]').fill('Q02 가상 결과'); await form.locator('[name=url]').fill('https://www.klaw.or.kr/');
    const resultPattern = '**/api/intakes/search-requests/' + created.id + '/results';
    let resultPosts = 0;
    await page.route(resultPattern, async route => { resultPosts++; await route.fetch(); await route.abort('failed'); });
    await form.getByRole('button', { name: '이 결과 추가하기', exact: true }).click();
    await page.getByRole('button', { name: '최신 저장 정보 조회', exact: true }).click();
    await page.getByRole('button', { name: '중복 가능성 확인 후 편집 계속', exact: true }).click();
    assert.equal(resultPosts, 1);
    const saved = await read('/api/intakes/search-requests/' + created.id, userToken);
    assert.equal(saved.results.length, 1); assert.equal(saved.results[0].searchRequestId, created.id);
    assert.equal(await form.locator('[name=title]').inputValue(), 'Q02 가상 결과');
    mark('Q02 C08 committed response loss keeps input and saved result, no automatic re-POST');
    await page.screenshot({ path: path.join(out, 'q02-recovery.png'), fullPage: true });

    // P03 is fully intercepted: no provider execution is allowed by this browser test.
    let executionPosts = 0, selected = saved;
    const execution = { id: '11111111-1111-4111-8111-111111111111', stage: 'EXPLANATION', provider: 'NAVER_WEB', errorCode: 'SEARCH_LLM_ERROR', startedAt: '2026-10-25T10:00:00', finishedAt: '2026-10-25T10:01:00', sourceWarningCode: 'GENERAL_SEARCH_UNAVAILABLE' };
    await page.route('**/api/intakes/search-requests/' + created.id, route => route.fulfill({ json: selected }));
    await page.route('**/api/intakes/search-requests/' + created.id + '/execute', async route => {
      executionPosts++;
      const body = route.request().postDataJSON();
      assert.equal(body.retryExplanation, executionPosts > 1);
      selected = executionPosts === 1 ? { ...saved, status: 'PARTIAL', execution } : { ...saved, status: 'COMPLETED', execution: { ...execution, stage: 'FINISHED', errorCode: null }, results: saved.results.map(result => ({ ...result, explanation: '가상 자료의 관련성 설명입니다.' })) };
      await route.fulfill({ status: 202, json: selected });
    });
    await page.getByRole('button', { name: '외부 검색 실행', exact: true }).click();
    await page.getByRole('button', { name: '저장된 결과 설명 다시 생성', exact: true }).waitFor();
    await page.getByText('일반 웹 검색에 연결하지 못했습니다.', { exact: false }).waitFor();
    assert.equal(selected.results.length, 1);
    await page.getByRole('button', { name: '저장된 결과 설명 다시 생성', exact: true }).click();
    await page.getByText('가상 자료의 관련성 설명입니다.', { exact: true }).waitFor();
    assert.equal(executionPosts, 2);
    mark('Q02 PARTIAL preserves sources, shows partial-source warning and retries explanations only');
    await login(page, 'user', false);
    await page.evaluate(hash => { location.hash = hash; }, `/intake/${intake.id}/search/${created.id}`);
    await page.getByText('가상 자료의 관련성 설명입니다.', { exact: true }).waitFor();
    assert.equal(executionPosts, 2);
    assert.ok(await page.getByRole('button', { name: '외부 검색 실행', exact: true }).isDisabled());
    mark('Q02 page reload and re-login restore the same completed request fixture without executing again');
    await page.unrouteAll({ behavior: 'wait' });

    await login(page, 'admin', true);
    await page.getByRole('heading', { name: '운영 현황을 한눈에 확인하세요.', exact: true }).waitFor();
    mark('Q03 ADMIN screen09 uses authenticated C12');
    const adminToken = (await read('/api/auth/login', null, 'POST', { username: 'admin', password: 'admin123' })).accessToken;
    const lawyerToken = (await read('/api/auth/login', null, 'POST', { username: 'lawyer', password: 'lawyer123' })).accessToken;
    const protectedPaths = ['/api/admin/dashboard/summary', '/api/admin/dashboard/marketing-funnel', '/api/admin/dashboard/security-events', '/api/agent/risk/summary', '/api/agent/risk/history', '/api/incidents', '/api/incidents/999999999/ack', '/api/incidents/999999999/resolve'];
    for (const endpoint of protectedPaths) {
      for (const [token, expected] of [[null, 401], [userToken, 403], [lawyerToken, 403]]) {
        const response = await fetch(base + endpoint, { method: endpoint.includes('/999999999/') ? 'PATCH' : 'GET', headers: token ? { Authorization: 'Bearer ' + token } : {} });
        assert.equal(response.status, expected, endpoint);
      }
    }
    mark('Q03 all eight operator business endpoints reject anonymous, USER and LAWYER requests');
    await page.getByRole('link', { name: '마케팅 집계', exact: true }).click();
    await page.getByRole('button', { name: '시연 분석', exact: true }).click();
    await page.getByText('시연 데이터 기준', { exact: true }).waitFor();
    await page.getByRole('button', { name: '실제 집계', exact: true }).click();
    await page.getByText('현재 DB 전체 기록 기준', { exact: false }).first().waitFor();
    mark('Q03 screen10 separates synthetic conversion from C13 real counts');
    await page.getByRole('link', { name: 'AI 위험 요약', exact: true }).click();
    await connectMetrics(page, 'synthetic-wrong-password');
    await page.getByRole('alert').first().waitFor();
    assert.equal(await page.locator('[name=password]').count(), 0);
    await page.getByRole('dialog').getByRole('button', { name: '닫기', exact: true }).click();
    await page.getByRole('link', { name: '운영 요약', exact: true }).click();
    await page.getByRole('heading', { name: '운영 현황을 한눈에 확인하세요.', exact: true }).waitFor();
    mark('Q03 failed C16 Basic authentication does not clear ADMIN JWT');
    await page.getByRole('link', { name: '보안·비용', exact: true }).click();
    await page.route('**/actuator/prometheus', route => route.fulfill({ status: 200, contentType: 'text/plain', body: 'ai_cost_actual_usd 0\n' }));
    await connectMetrics(page, 'admin123', '연결 설정');
    await page.locator('dialog').waitFor({ state: 'hidden' });
    const actual = page.locator('.security-metric-cards > div').filter({ hasText: '실사용 비용' }).locator('strong');
    const reserved = page.locator('.security-metric-cards > div').filter({ hasText: '진행 중 예약 금액' }).locator('strong');
    assert.match(await actual.innerText(), /0/); assert.equal(await reserved.innerText(), '—');
    mark('Q03 screen11 keeps observed zero distinct from missing Prometheus samples');
    await page.unroute('**/actuator/prometheus');
    await page.getByRole('link', { name: 'AI 위험 요약', exact: true }).click();
    await connectMetrics(page);
    await page.locator('dialog').waitFor({ state: 'hidden' });
    await page.screenshot({ path: path.join(out, 'q03-observability.png'), fullPage: true });
    const end = Math.floor(Date.now() / 1000);
    await page.route('**/actuator/guardtimeseries*', route => route.fulfill({ json: { start: end - 3600, end, stepSeconds: 15, windowSeconds: 300, query: 'increase over 5m', warnings: [], series: [] } }));
    await page.getByRole('button', { name: '새로고침', exact: true }).click();
    await page.locator('.obs-graph-empty').filter({ hasText: '관측 없음' }).first().waitFor();
    assert.equal(await page.locator('.obs-series-line').count(), 0);
    mark('Q03 empty series displays no observations rather than fabricated chart curves');
    await page.unroute('**/actuator/guardtimeseries*');
    await page.route('**/actuator/guardtimeseries*', route => route.fulfill({ status: 503, json: { message: 'Q03 synthetic Prometheus unavailable' } }));
    await page.getByRole('button', { name: '새로고침', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: '시계열 연결 실패' }).first().waitFor();
    mark('Q03 unavailable Prometheus produces a time-series error without clearing risk history or JWT');
    await page.unroute('**/actuator/guardtimeseries*');
    await page.getByRole('button', { name: '새로고침', exact: true }).click();
    const auditLinks = page.locator('a[href*="riskId="]');
    await auditLinks.first().waitFor();
    assert.ok(await auditLinks.count());
    const href = await auditLinks.first().getAttribute('href');
    await auditLinks.first().click();
    assert.ok(page.url().endsWith(href));
    await page.getByRole('heading', { name: '위험 판단을 추적하고 대응하세요.', exact: true }).waitFor();
    mark('Q03 screen13 links to screen12 with the exact selected risk ID');
    const incidentRows = await read('/api/incidents', adminToken);
    assert.ok(incidentRows.length, 'Presentation incidents are required for the UI-only state fixture');
    let incident = { ...incidentRows[0], status: 'OPEN' };
    const summary = await read('/api/admin/dashboard/summary', adminToken);
    await page.route('**/api/incidents', route => route.fulfill({ json: [incident] }));
    await page.route('**/api/admin/dashboard/summary', route => route.fulfill({ json: { ...summary, openIncidentCount: incident.status === 'OPEN' ? 1 : 0 } }));
    await page.route('**/api/incidents/' + incident.id + '/*', route => {
      assert.equal(route.request().method(), 'PATCH');
      incident = { ...incident, status: route.request().url().endsWith('/ack') ? 'ACKNOWLEDGED' : 'RESOLVED' };
      return route.fulfill({ json: incident });
    });
    await page.getByRole('button', { name: '새로고침', exact: true }).click();
    await page.getByRole('button', { name: `Incident ${incident.id} 상세`, exact: true }).click();
    const summaryReadsBefore = requests.filter(request => request.path === '/api/admin/dashboard/summary').length;
    await page.getByRole('button', { name: '확인 처리', exact: true }).click();
    await page.getByRole('table', { name: '보안 Incident 목록', exact: true }).getByText('확인됨', { exact: true }).waitFor();
    assert.ok(requests.filter(request => request.path === '/api/admin/dashboard/summary').length > summaryReadsBefore);
    await page.getByRole('button', { name: '해결 처리', exact: true }).click();
    await page.getByRole('table', { name: '보안 Incident 목록', exact: true }).getByText('해결됨', { exact: true }).waitFor();
    assert.ok(await page.getByRole('button', { name: '확인 처리', exact: true }).isDisabled());
    await page.getByRole('link', { name: '운영 요약', exact: true }).click();
    assert.match(await page.locator('.stat').filter({ hasText: '미확인 Incident' }).locator('strong').innerText(), /^0/);
    mark('Q03 screen12 changes reread C18/C12, prevent re-ack after resolve, and update screen09 (UI-only write fixture)');
    const badHeaders = requests.filter(request => request.path.startsWith('/actuator/') ? request.authentication !== 'Basic' : request.path !== '/api/auth/login' && !request.path.startsWith('/api/legal/') && request.authentication !== 'Bearer');
    assert.deepEqual(badHeaders, []);
    mark('Q03 all observed business requests use Bearer; Actuator uses Basic only');
    const mockPage = await context.newPage();
    const mockCalls = [];
    mockPage.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/') || new URL(request.url()).pathname.startsWith('/actuator/')) mockCalls.push(request.url()); });
    await mockPage.goto(front + '/operator.html?mock=1#/observability');
    await mockPage.getByText('서버 저장·LLM 호출·과금은 없습니다.', { exact: false }).waitFor();
    assert.deepEqual(mockCalls, []);
    await mockPage.screenshot({ path: path.join(out, 'q03-explicit-preview.png'), fullPage: true });
    await mockPage.close();
    mark('Q03 explicit preview is labelled synthetic and sends zero business/Actuator requests');
    assert.deepEqual(errors, []);
    const after = await fetch(provider + '/__demo/status').then(r => r.json());
    assert.deepEqual(after.counts, before.counts);
    const evidence = { date: '2026-10-25', kind: 'isolated synthetic demo browser regression', checks, browserErrors: errors, providerCallsChanged: false, intakeId: intake.id, searchRequestId: created.id, requests };
    fs.writeFileSync(path.join(out, 'q02-q03-browser.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify({ passed: checks.length, browserErrors: errors.length, providerCallsChanged: false }));
  } catch (error) {
    await page.screenshot({ path: path.join(out, 'q02-q03-failed-' + Date.now() + '.png'), fullPage: true });
    fs.writeFileSync(path.join(out, 'q02-q03-last-failure.json'), JSON.stringify({ error: error.message, checks }, null, 2));
    throw error;
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
