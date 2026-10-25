// End-to-end proof against the existing isolated mock demo, never a paid endpoint.
import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const base = 'http://127.0.0.1:18080', provider = 'http://127.0.0.1:18083';
const output = path.join(root, 'output/paid-experiment-review-demo-2026-10-25');
const metadata = JSON.parse(await fs.readFile(path.join(root, 'backend/guardrail-ready-policy-checker/.local/presentation-2026-10-25/process.json'), 'utf8'));
assert.equal(metadata.mode, 'synthetic-only'); assert.equal(metadata.backendBase, base); assert.equal(metadata.provider, provider);
const before = await fetch(provider + '/__demo/status').then(r => r.json());
async function post(endpoint, body, token) {
  const response = await fetch(base + endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: JSON.stringify(body) });
  assert.ok(response.ok, `${endpoint}: ${response.status}`); return response.json();
}
const login = await post('/api/auth/login', { username: 'user', password: 'user123' });
const intake = await post('/api/intakes', { sessionId: 'experiment-review-' + Date.now(), inquiryPurpose: '절차 안내', caseType: 'CIVIL', partyPosition: '원고', coreStatement: '검증 도구를 위한 가상 대여금 사건입니다.', desiredNextStep: '절차 안내', preferredRegion: '서울특별시' }, login.accessToken);
const search = await post(`/api/intakes/${intake.id}/search-requests`, { searchPurpose: '모의 변협·네이버 검색 검증', sourcePolicy: 'KBA_NAVER_PUBLIC_WEB' }, login.accessToken);
const child = spawn(process.execPath, [path.join(root, 'scripts/Review-PaidExperiment.mjs'), '--action', 'search', '--base', base, '--intake-id', String(intake.id), '--search-request-id', String(search.id), '--provider-mode', 'mock', '--guard-mode', 'mock', '--execute', '--confirm', 'MOCK_ONLY', '--watch-seconds', '30', '--output', output], {
  cwd: root, windowsHide: true, env: { ...process.env, EXPERIMENT_APP_JWT: login.accessToken, EXPERIMENT_METRICS_USER: 'admin', EXPERIMENT_METRICS_PASSWORD: 'admin123', LLM_API_KEY: '', OPENAI_API_KEY: '', NAVER_SEARCH_CLIENT_ID: '', NAVER_SEARCH_CLIENT_SECRET: '' },
});
let stdout = '', stderr = ''; child.stdout.on('data', value => { stdout += value; }); child.stderr.on('data', value => { stderr += value; });
const exit = await new Promise(resolve => child.on('close', resolve)); assert.equal(exit, 0, stderr);
const report = JSON.parse(await fs.readFile(path.join(output, 'search-review.json'), 'utf8'));
assert.equal(report.status, 'COMPLETED'); assert.equal(report.executeAttempted, true); assert.equal(report.executeAcknowledged, true);
assert.equal(report.technicalChecksPassed, true); assert.ok(report.resultCount > 0); assert.equal(report.cost.reservationClosed, true);
assert.equal(report.cost.ledgerChecksPassed, true); assert.equal(report.cost.billingReconciled, false);
const after = await fetch(provider + '/__demo/status').then(r => r.json());
assert.ok(after.counts.llm > before.counts.llm);
await fs.writeFile(path.join(output, 'verification.json'), JSON.stringify({ date: '2026-10-25', kind: 'real local Spring + synthetic LLM/Guard/Naver', intakeId: intake.id, searchRequestId: search.id, status: report.status, results: report.resultCount, singleExecuteAcknowledged: true, ledgerChecksPassed: report.cost.ledgerChecksPassed, reservationClosed: true, realPaidCalls: 0, providerInvoiceReconciled: false }, null, 2));
console.log(JSON.stringify({ passed: true, status: report.status, results: report.resultCount, reservationClosed: true, realPaidCalls: 0, output }));
