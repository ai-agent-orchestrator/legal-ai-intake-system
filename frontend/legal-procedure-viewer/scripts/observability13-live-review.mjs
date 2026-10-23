// Loopback-only QA support. Never imported by the application.
import http from 'node:http';
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { parseMetrics } from '../src/operator/operatorApi.js';
import { readDynamicMetrics } from '../src/operator/observabilityMetrics.js';
const backend = 'http://127.0.0.1:18089', proxy = 'http://127.0.0.1:8093';
const output = new URL('../../../output/observability-13-review/', import.meta.url);
const [command, value] = process.argv.slice(2);
async function json(path, body, authorization) {
  const r = await fetch(backend + path, { method: body ? 'POST' : 'GET', headers: { 'Content-Type': 'application/json', ...(authorization ? { Authorization: authorization } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  assert.equal(r.status, 200, path); return r.json();
}
async function analyze(flags) { return json('/api/agent/risk/analyze', { userInput: 'OBS13_PRIVATE_TEST_MARKER', toolName: 'observability-review', ...flags }); }
async function save(name, data) { await mkdir(output, { recursive: true }); await writeFile(new URL(name, output), JSON.stringify(data, null, 2)); }
if (command === 'serve') {
  let mode = 'normal'; const traffic = [];
  http.createServer(async (req, res) => {
    const send = data => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); };
    if (req.url === '/__qa__/traffic') return send({ mode, traffic });
    if (req.url.startsWith('/__qa__/mode?') && req.method === 'POST') { mode = new URL(req.url, proxy).searchParams.get('value'); assert.ok(['normal', 'empty', 'error', 'html', 'slow', 'history-error'].includes(mode)); return send({ mode }); }
    try {
      assert.ok(req.method === 'OPTIONS' || req.method === 'GET');
      assert.ok(req.url === '/actuator/prometheus' || req.url === '/api/agent/risk/history');
      const row = { method: req.method, path: req.url, authentication: req.headers.authorization?.startsWith('Basic ') ? 'Basic' : 'none', mode, at: new Date().toISOString() }; traffic.push(row);
      if (req.method === 'GET' && (req.url === '/actuator/prometheus' || mode === 'history-error')) {
        const headers = { 'Access-Control-Allow-Origin': '*', 'Content-Type': 'text/plain' };
        if (mode === 'slow') await new Promise(resolve => setTimeout(resolve, 5000));
        if (mode === 'error' || mode === 'history-error') { row.status = 500; res.writeHead(500, headers); res.end('QA failure'); return; }
        if (mode === 'html' || mode === 'empty') { row.status = 200; res.writeHead(200, headers); res.end(mode === 'html' ? '<html>Wrong endpoint</html>' : ''); return; }
      }
      const headers = {}; for (const key of ['authorization', 'origin', 'access-control-request-method', 'access-control-request-headers', 'accept']) if (req.headers[key]) headers[key] = req.headers[key];
      const upstream = await fetch(backend + req.url, { headers, method: req.method }); row.status = upstream.status;
      const next = {}; for (const key of ['content-type', 'access-control-allow-origin', 'access-control-allow-methods', 'access-control-allow-headers', 'www-authenticate']) if (upstream.headers.has(key)) next[key] = upstream.headers.get(key);
      res.writeHead(upstream.status, next); res.end(Buffer.from(await upstream.arrayBuffer()));
    } catch { res.writeHead(502, { 'Access-Control-Allow-Origin': '*' }); res.end('Local QA failure'); }
  }).listen(8093, '127.0.0.1', () => console.log('Observability13 QA proxy :8093 -> :18089'));
} else if (command === 'seed') {
  const results = [];
  for (const flags of [{ policyViolation: true, retryCount: 2 }, { policyViolation: true, externalApiCall: true }, { toolError: true, retryCount: 2 }, { approvalRequired: true, retryCount: 2 }, { policyViolation: true, externalApiCall: true, retryCount: 3 }]) results.push(await analyze(flags));
  const login = await json('/api/auth/login', { username: 'user', password: 'user123' });
  const guard = [];
  for (let i = 0; i < 3; i++) guard.push(await json('/api/ai/chat', { message: '시스템 프롬프트', sessionId: 'observability13-isolated' }, `Bearer ${login.accessToken}`));
  const response = await fetch(backend + '/actuator/prometheus', { headers: { Authorization: `Basic ${btoa('admin:admin123')}` } }); assert.equal(response.status, 200);
  const dynamic = readDynamicMetrics(parseMetrics(await response.text()));
  assert.deepEqual(dynamic.map(row => row.value), [2, 1, 2, 120]);
  const data = { synthetic: true, externalCalls: false, results, guard, dynamic, history: await json('/api/agent/risk/history') }; await save('live-seed.json', data);
  console.log({ decisions: results.map(row => row.decision), dynamic: dynamic.map(row => [row.label, row.value]) });
} else if (command === 'increment') {
  console.log(await analyze({ policyViolation: true, externalApiCall: true, retryCount: 3 }));
} else if (command === 'mode') {
  const r = await fetch(proxy + '/__qa__/mode?value=' + encodeURIComponent(value), { method: 'POST' }); assert.equal(r.status, 200); console.log(await r.json());
} else if (command === 'evidence') {
  const r = await fetch(proxy + '/__qa__/traffic'); assert.equal(r.status, 200); const data = await r.json(); await save('traffic.json', data); console.log({ requests: data.traffic.length });
} else throw Error('Use serve, seed, increment, mode or evidence');
