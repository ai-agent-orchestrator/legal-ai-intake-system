// Isolated presentation frontend only; no real-provider calls or DB writes.
const fs = require('node:fs'), path = require('node:path'), assert = require('node:assert/strict');
const { createRequire } = require('node:module');
const { chromium } = createRequire(path.join(process.env.USERPROFILE, '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/package.json'))('playwright');
const root = path.resolve(__dirname, '..'), out = path.join(root, 'output/marketing-demo-2026-10-25');
const frontend = 'http://127.0.0.1:5176', provider = 'http://127.0.0.1:18083';
const results = [], errors = [], calls = [];
const counter = () => fetch(provider + '/__demo/status').then(response => response.json());

(async () => {
  fs.mkdirSync(out, { recursive: true });
  const before = await counter();
  const browser = await chromium.launch({ executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe', headless: true });
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  page.on('pageerror', error => errors.push(error.message));
  page.on('request', request => { if (request.url().includes('/api/')) calls.push({ path: new URL(request.url()).pathname, method: request.method() }); });
  try {
    await page.goto(frontend + '/operator.html#/marketing');
    await page.locator('[name=username]').fill('admin');
    await page.locator('[name=password]').fill('admin123');
    await page.getByRole('button', { name: '관리자 로그인', exact: true }).click();
    await page.getByText('단계별 집계 기준이 달라 전환율·이탈률은 아직 측정하지 않습니다.', { exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '실제 집계', exact: true }).getAttribute('aria-pressed'), 'true');
    await page.screenshot({ path: path.join(out, 'real-desktop.png'), fullPage: true });
    results.push({ check: 'Real C13 remains the default and retains its original disclaimer', pass: true });
    const countBefore = calls.length;
    await page.getByRole('button', { name: '시연 분석', exact: true }).click();
    await page.getByText('시연 데이터 기준', { exact: true }).waitFor();
    assert.equal(await page.locator('.marketing-demo-stages > li').count(), 4);
    assert.deepEqual(await page.locator('.marketing-demo-stages > li > strong').allTextContents(), ['40건', '24건', '12건', '4건']);
    assert.match(await page.locator('.marketing-conversion-stat').innerText(), /33\.3%/);
    assert.equal(calls.length, countBefore);
    results.push({ check: 'Demo uses computed 40/24/12/4 funnel and 33.3% conversion without an extra API request', pass: true });
    for (const viewport of [{ width: 1920, height: 1080 }, { width: 1024, height: 768 }, { width: 390, height: 844 }]) {
      await page.setViewportSize(viewport);
      await page.evaluate(() => document.fonts.ready);
      await page.screenshot({ path: path.join(out, `demo-${viewport.width}.png`), fullPage: true });
      const dimensions = await page.evaluate(() => ({ viewport: innerWidth, scroll: document.documentElement.scrollWidth }));
      assert.ok(dimensions.scroll <= dimensions.viewport, `horizontal overflow at ${viewport.width}: ${JSON.stringify(dimensions)}`);
      const mode = page.getByRole('button', { name: '시연 분석', exact: true });
      assert.ok((await mode.boundingBox()).height >= 40);
      results.push({ check: `Demo ${viewport.width}px layout has no page horizontal overflow`, pass: true });
    }
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.getByRole('button', { name: '실제 집계', exact: true }).click();
    await page.getByText('단계별 집계 기준이 달라 전환율·이탈률은 아직 측정하지 않습니다.', { exact: true }).waitFor();
    assert.equal(await page.locator('.marketing-demo').count(), 0);
    results.push({ check: 'Returning to real mode reloads original C13 and removes synthetic conversion', pass: true });
    await page.route('**/api/admin/dashboard/marketing-funnel', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: '시연 검수: 집계 서비스 일시 오류' }) }));
    await page.getByRole('button', { name: '새로고침', exact: true }).click();
    await page.getByRole('alert').waitFor();
    assert.equal(await page.locator('.marketing-demo').count(), 0);
    await page.getByRole('button', { name: '시연 분석', exact: true }).click();
    await page.getByText('시연 데이터 기준', { exact: true }).waitFor();
    await page.unroute('**/api/admin/dashboard/marketing-funnel');
    await page.getByRole('button', { name: '실제 집계', exact: true }).click();
    await page.getByText('단계별 집계 기준이 달라 전환율·이탈률은 아직 측정하지 않습니다.', { exact: true }).waitFor();
    results.push({ check: '503 is visible, never silently replaced by demo, and normal real-mode recovery succeeds', pass: true });
    const writes = calls.filter(call => call.method !== 'GET' && call.method !== 'OPTIONS' && call.path !== '/api/auth/login');
    assert.deepEqual(writes, []);
    const after = await counter();
    assert.deepEqual(after.counts, before.counts);
    assert.deepEqual(errors, []);
    results.push({ check: 'No business writes, provider call count changes, or browser JavaScript errors', pass: true });
    await page.getByRole('button', { name: '시연 분석', exact: true }).click();
    await page.getByText('시연 데이터 기준', { exact: true }).waitFor();
    fs.writeFileSync(path.join(out, 'verification.json'), JSON.stringify({ date: '2026-10-25', frontend, results, browserErrors: errors, businessWrites: writes, providerCallsChanged: false }, null, 2));
    console.log(JSON.stringify({ passed: results.length, browserErrors: errors.length, businessWrites: writes.length, providerCallsChanged: false, output: out }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
