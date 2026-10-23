// Loopback-only disposable browser QA. Not imported by production code.
import http from 'node:http';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
const backend = 'http://127.0.0.1:18086', proxy = 'http://127.0.0.1:8088';
const output = new URL('../../../output/intake-01-04-browser/', import.meta.url);
const [command, ...args] = process.argv.slice(2);
if (command === 'serve') {
  let scenario = { kind: 'normal', name: 'normal' };
  const traffic = [];
  const server = http.createServer(async (req, res) => {
    const send = (data, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(data)); };
    try {
      const url = new URL(req.url, proxy);
      const chunks = []; for await (const chunk of req) chunks.push(chunk);
      const body = Buffer.concat(chunks);
      if (url.pathname === '/__qa__/scenario' && req.method === 'POST') {
        const next = JSON.parse(body.toString());
        assert.ok(['normal', 'slow', 'timeout', 'error', 'forbidden', 'missing', 'empty-array', 'empty-guide', 'malformed'].includes(next.kind));
        if (next.kind !== 'normal') assert.ok(next.path?.startsWith('/api/') && ['GET', 'POST'].includes(next.method));
        scenario = next; return send(scenario);
      }
      if (url.pathname === '/__qa__/traffic') return send({ scenario, traffic });
      if (!url.pathname.startsWith('/api/')) return send({ error: 'QA API paths only' }, 404);
      const selected = scenario, match = selected.path === url.pathname && selected.method === req.method;
      const entry = { name: selected.name, method: req.method, path: url.pathname, query: url.search, fault: match ? selected.kind : null, startedAt: Date.now(), status: null };
      traffic.push(entry);
      if (match) {
        if (['slow', 'timeout'].includes(selected.kind)) await new Promise(resolve => setTimeout(resolve, selected.kind === 'timeout' ? 17000 : 5000));
        const errors = { error: 500, forbidden: 403, missing: 404 };
        if (errors[selected.kind]) { entry.status = errors[selected.kind]; return send({ traceId: 'local-browser-0104-qa' }, entry.status); }
        if (selected.kind === 'empty-array') { entry.status = 200; return send([]); }
        if (selected.kind === 'malformed') { entry.status = 200; return send({ invalid: true }); }
      }
      if (res.destroyed) { entry.cancelledBeforeForward = true; return; }
      const upstream = await fetch(backend + url.pathname + url.search, { method: req.method,
        headers: { Accept: req.headers.accept || '*/*', ...(req.headers['content-type'] ? { 'Content-Type': req.headers['content-type'] } : {}), ...(req.headers.authorization ? { Authorization: req.headers.authorization } : {}) },
        ...(body.length ? { body } : {}), redirect: 'manual' });
      entry.status = upstream.status; entry.forwarded = true;
      if (match && selected.kind === 'empty-guide' && upstream.ok) {
        const value = await upstream.json(); return send({ ...value, checkedFacts: [], recommendedDocuments: [] });
      }
      const bytes = Buffer.from(await upstream.arrayBuffer());
      if (!res.destroyed) { res.writeHead(upstream.status, { 'Content-Type': upstream.headers.get('content-type') || 'application/json', 'Content-Length': bytes.length, 'Cache-Control': 'no-store' }); res.end(bytes); }
    } catch { if (!res.destroyed) send({ error: 'QA proxy failure' }, 502); }
  });
  server.listen(8088, '127.0.0.1', () => console.log('Browser QA :8088 -> isolated Spring :18086'));
  process.on('SIGINT', () => server.close());
} else if (command === 'mode') {
  const [kind, path = '', method = 'GET', name = kind] = args;
  const response = await fetch(proxy + '/__qa__/scenario', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ kind, path, method, name }) });
  assert.equal(response.status, 200); console.log(await response.json());
} else if (command === 'seed') {
  const response = await fetch(backend + '/api/intakes', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ inquiryPurpose: '절차 안내', caseType: 'CRIMINAL', partyPosition: '피해자', coreStatement: '형사 참고 안내 브라우저 검수용 가상 문의입니다.', desiredNextStep: '절차 안내' }) });
  assert.equal(response.status, 201); const value = await response.json();
  await mkdir(output, { recursive: true }); await writeFile(new URL('seed.json', output), JSON.stringify({ criminalId: value.id, synthetic: true }, null, 2)); console.log({ criminalId: value.id });
} else if (command === 'evidence') {
  const response = await fetch(proxy + '/__qa__/traffic'); assert.equal(response.status, 200); const value = await response.json();
  await mkdir(output, { recursive: true }); await writeFile(new URL('traffic.json', output), JSON.stringify(value, null, 2)); console.log({ requests: value.traffic.length });
} else throw new Error('Use serve, mode, seed or evidence');
