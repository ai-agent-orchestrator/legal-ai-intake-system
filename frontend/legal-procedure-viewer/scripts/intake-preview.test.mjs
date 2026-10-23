// Run after npm run build, with a local Spring Boot backend on port 8080.
import test from 'node:test';
import assert from 'node:assert/strict';
import { preview } from 'vite';

test('built intake entry and real API work through the local build-preview proxy', async () => {
  const server = await preview({ preview: { host: '127.0.0.1', port: 0, strictPort: true, open: false } });
  try {
    const base = `http://127.0.0.1:${server.httpServer.address().port}`;
    const page = await fetch(`${base}/intake.html`);
    assert.equal(page.status, 200);
    const html = await page.text();
    assert.match(html, /<html lang="ko">/);
    const script = html.match(/<script[^>]+src="([^"]+)"/);
    assert.ok(script);
    const bundle = await fetch(new URL(script[1], base));
    assert.equal(bundle.status, 200);
    assert.match(bundle.headers.get('Content-Type'), /javascript/);
    const classification = await fetch(`${base}/api/intakes/classify-preview`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ caseType: 'CIVIL', coreStatement: '개발 검수용 계약 문의입니다.' }),
    });
    assert.equal(classification.status, 200);
    const data = await classification.json();
    assert.equal(data.caseType, 'CIVIL');
    assert.equal(data.persisted, false);
    const listResponse = await fetch(`${base}/api/intakes`);
    assert.equal(listResponse.status, 200);
    const items = await listResponse.json();
    assert.ok(Array.isArray(items) && items.length <= 50);
    if (items.length) {
      const detailResponse = await fetch(`${base}/api/intakes/${items[0].id}`);
      assert.equal(detailResponse.status, 200);
      assert.equal((await detailResponse.json()).id, items[0].id);
    }
  } finally {
    await new Promise((resolve, reject) => server.httpServer.close(error => error ? reject(error) : resolve()));
  }
});
