// Disposable LOCAL browser QA proxy/setup/control. Not imported by production code.
import http from 'node:http';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
const backend = 'http://127.0.0.1:18085', proxy = 'http://127.0.0.1:8087', provider = 'http://127.0.0.1:18181';
const output = new URL('../../../output/intake-05-08-browser/', import.meta.url);
const [command, ...args] = process.argv.slice(2);
async function json(url, body, token, expected = 200) {
  const response = await fetch(url, { method: body === undefined ? 'GET' : 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  assert.equal(response.status, expected, `${url}: ${await response.clone().text()}`); return response.json();
}
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
        assert.ok(['normal', 'slow', 'timeout', 'error', 'forbidden', 'missing', 'validation', 'empty-list', 'empty-detail', 'no-condition', 'malformed', 'lost'].includes(next.kind));
        if (next.kind !== 'normal') assert.ok(next.path?.startsWith('/api/') && ['GET', 'POST', 'PATCH'].includes(next.method));
        scenario = next; return send(scenario);
      }
      if (url.pathname === '/__qa__/traffic') return send({ scenario, traffic });
      if (!url.pathname.startsWith('/api/')) return send({ error: 'QA API paths only' }, 404);
      const selected = scenario;
      const match = selected.path === url.pathname && selected.method === req.method;
      const entry = { name: selected.name, method: req.method, path: url.pathname, fault: match ? selected.kind : null, startedAt: Date.now(), status: null };
      traffic.push(entry);
      if (traffic.length > 2000) traffic.shift();
      if (match) {
        if (['slow', 'timeout'].includes(selected.kind)) await new Promise(resolve => setTimeout(resolve, selected.kind === 'timeout' ? 17000 : 5000));
        const errors = { error: 500, forbidden: 403, missing: 404, validation: 400 };
        if (errors[selected.kind]) {
          entry.status = errors[selected.kind];
          return send({ traceId: 'local-browser-qa', ...(selected.kind === 'validation' ? { fieldErrors: { content: '검수용 입력 오류' } } : {}) }, entry.status);
        }
        if (selected.kind === 'empty-list') { entry.status = 200; return send([]); }
        if (selected.kind === 'malformed') { entry.status = 200; return send({ invalid: true }); }
      }
      if (res.destroyed) { entry.cancelledBeforeForward = true; return; }
      const upstream = await fetch(backend + url.pathname + url.search, { method: req.method,
        headers: { Accept: req.headers.accept || '*/*', ...(req.headers['content-type'] ? { 'Content-Type': req.headers['content-type'] } : {}), ...(req.headers.authorization ? { Authorization: req.headers.authorization } : {}) },
        ...(body.length ? { body } : {}), redirect: 'manual' });
      entry.status = upstream.status; entry.forwarded = true;
      if (match && selected.kind === 'lost' && upstream.ok) { await upstream.arrayBuffer(); entry.responseLost = true; res.destroy(); return; }
      if (match && ['empty-detail', 'no-condition'].includes(selected.kind) && upstream.ok) {
        const value = await upstream.json();
        return send({ ...value, lawyerSearchCondition: null, ...(selected.kind === 'empty-detail' ? { consultationMemos: [], uploadedDocuments: [], selectedDocuments: [], recommendedDocuments: [] } : {}) });
      }
      const headers = {};
      for (const key of ['content-type', 'content-disposition', 'x-content-type-options', 'x-trace-id']) if (upstream.headers.has(key)) headers[key] = upstream.headers.get(key);
      const bytes = Buffer.from(await upstream.arrayBuffer());
      if (!res.destroyed) { res.writeHead(upstream.status, { ...headers, 'Content-Length': bytes.length, 'Cache-Control': 'no-store' }); res.end(bytes); }
    } catch (error) { if (!res.destroyed) send({ error: 'QA proxy failure' }, 502); }
  });
  server.listen(8087, '127.0.0.1', () => console.log('Browser QA proxy :8087 -> isolated Spring :18085 (external providers mocked)'));
  process.on('SIGINT', () => server.close());
} else if (command === 'mode') {
  const [kind, path = '', method = 'GET', name = kind] = args;
  console.log(await json(proxy + '/__qa__/scenario', { kind, path, method, name }));
} else if (command === 'provider') {
  const response = await fetch(provider + '/__qa__/mode?value=' + encodeURIComponent(args[0]), { method: 'POST' });
  assert.equal(response.status, 200); console.log(await response.json());
} else if (command === 'seed') {
  const token = (await json(backend + '/api/auth/login', { username: 'user', password: 'user123' })).accessToken;
  const rows = [];
  for (let index = 0; index < 8; index++) {
    const row = await json(backend + '/api/intakes', { inquiryPurpose: '절차 안내', caseType: ['CIVIL', 'FAMILY', 'CRIMINAL', 'ELECTRONIC_LITIGATION'][index % 4], partyPosition: '원고',
      coreStatement: index === 0 ? '브라우저 검수용 가상 대여금 반환 문의입니다. 계약서와 이체 내역을 확인하고 싶습니다.' : '브라우저 검수용 가상 문의 ' + index,
      desiredNextStep: ['절차 안내', '문서 검토', '자료 확인'][index % 3], preferredRegion: '서울특별시', clientName: '검수인', counterpart: '가상 검수업체',
      incidentDate: '2026-10-01', documents: ['계약서', '이체 내역'], officeType: '법무법인', experienceLevel: '5년 이상' }, token, 201);
    const status = ['CONSULTATION_READY', 'REVIEWED', 'NEED_MORE_INFO', 'SUBMITTED'][index % 4];
    if (status !== 'SUBMITTED') {
      const response = await fetch(backend + `/api/intakes/${row.id}/status`, { method: 'PATCH', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }, body: JSON.stringify({ status }) }); assert.equal(response.status, 200);
    }
    rows.push({ id: row.id, status, caseType: row.caseType });
  }
  const intakeId = rows[0].id;
  const requests = [];
  for (const searchPurpose of ['브라우저 정상 검수', '브라우저 설명 복구 검수', '브라우저 빈 검색 검수']) requests.push(await json(backend + `/api/intakes/${intakeId}/search-requests`, { searchPurpose }, token, 201));
  await json(backend + `/api/intakes/${intakeId}/memo`, { content: '브라우저 검수용 초기 상담 메모입니다.', followUpItems: ['REQUEST_DOCUMENTS'] }, token, 201);
  const form = new FormData(); form.append('file', new Blob(['Synthetic browser QA attachment.\n'], { type: 'text/plain' }), '브라우저-검수.txt'); form.append('documentType', '계약서');
  const upload = await fetch(backend + `/api/intakes/${intakeId}/documents`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form }); assert.equal(upload.status, 201);
  const file = await upload.json();
  const data = { intakeId, requestIds: requests.map(value => value.id), file, rows, origin: 'http://127.0.0.1:5186', synthetic: true };
  await mkdir(output, { recursive: true }); await writeFile(new URL('seed.json', output), JSON.stringify(data, null, 2)); console.log(JSON.stringify(data));
} else if (command === 'evidence') {
  const suffix = args[0] || '';
  assert.ok(!suffix || /^[a-z0-9-]+$/.test(suffix));
  const data = await json(proxy + '/__qa__/traffic'); data.provider = await json(provider + '/__qa__/calls');
  await mkdir(output, { recursive: true }); await writeFile(new URL(suffix ? `traffic-${suffix}.json` : 'traffic.json', output), JSON.stringify(data, null, 2)); console.log({ requests: data.traffic.length, providerCalls: data.provider.calls });
} else throw new Error('Use serve, mode, provider, seed or evidence');
