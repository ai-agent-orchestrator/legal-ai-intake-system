import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import fs from 'node:fs/promises';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { compareCosts, costStages, inspectSearch, loopbackBase, positiveId, snapshotCosts } from './lib/experimentReview.mjs';
const root = fileURLToPath(new URL('../', import.meta.url));
const beforeAt = '2026-10-25T01:00:00Z', afterAt = '2026-10-25T01:01:00Z';
const metrics = ({ actual = 0, estimated = 0, reserved = 0, process = 1000 } = {}) => [
  `process_start_time_seconds ${process}`, `ai_cost_actual_usd ${actual}`, `ai_cost_estimated_usage_usd ${estimated}`,
  `ai_cost_accounted_usd ${actual + estimated}`, `ai_cost_reserved_usd ${reserved}`,
  'ai_cost_budget_usd 2', 'ai_cost_daily_budget_usd 0.5', 'ai_cost_session_budget_usd 0.12',
  ...costStages.map(stage => `ai_cost_stage_accounted_usd{stage="${stage}"} ${stage === 'search_query' ? actual + estimated : 0}`),
  'ai_llm_tokens_total{type="prompt"} 100', 'ai_llm_tokens_total{type="completion"} 20',
].join('\n');
const pair = (overrides = {}) => [snapshotCosts(metrics(), { base: 'http://localhost:18080', at: beforeAt }), snapshotCosts(metrics(overrides), { base: 'http://localhost:18080', at: afterAt })];
const receipt = { model: 'fixture-model', usageAvailable: true, promptTokens: 100, completionTokens: 20, costSource: 'ACTUAL_USAGE', providerMode: 'actual', guardMode: 'mock', answer: 'DO_NOT_EXPORT_STORY' };
const invoice = { kind: 'provider_usage_export', amountUsd: 0.001, exclusiveOperation: true, windowStart: beforeAt, windowEnd: afterAt, model: 'fixture-model' };
const saved = (status = 'COMPLETED') => ({ id: 81, intakeId: 11, conditionId: 44, sourcePolicy: 'KBA_NAVER_PUBLIC_WEB', status,
  results: status === 'PREVIEW_READY' ? [] : [{ id: 101, searchRequestId: 81, title: 'DO_NOT_EXPORT_TITLE', sourceType: 'KBA_PUBLIC_WEB', url: 'https://www.klaw.or.kr/profile?id=4&token=DO_NOT_EXPORT_QUERY', explanation: 'DO_NOT_EXPORT_EXPLANATION' }] });

test('cost snapshots distinguish missing, zero, labels and non-finite observations', () => {
  const value = snapshotCosts('ai_cost_actual_usd 0\nai_cost_reserved_usd NaN\nai_llm_tokens_total{type="prompt",stage="a"} 2\nai_llm_tokens_total{stage="b",type="prompt"} 3');
  assert.equal(value.costs.actual, 0); assert.equal(value.costs.reserved, null); assert.equal(value.costs.estimated, null);
  assert.equal(value.tokens.prompt, 5); assert.equal(value.tokens.completion, null);
});
test('a valid isolated invoice, known Guard mode and closed ledger can reconcile', () => {
  const result = compareCosts(...pair({ actual: 0.001 }), receipt, invoice);
  assert.equal(result.ledgerChecksPassed, true); assert.equal(result.billingReconciled, true);
  assert.equal(result.stageDelta.search_query, 0.001); assert.equal(result.stageConserved, true);
  assert.equal(result.receipt.promptTokens, 100); assert.equal(result.receipt.completionTokens, 20);
  assert.ok(!JSON.stringify(result).includes('DO_NOT_EXPORT_STORY'));
});
test('unknown Guard coverage or missing usage/export cannot be reported as billing completion', () => {
  for (const [call, exportValue] of [[{ ...receipt, guardMode: undefined }, invoice], [{ ...receipt, usageAvailable: false }, invoice], [receipt, null], [receipt, { ...invoice, exclusiveOperation: false }]]) {
    assert.equal(compareCosts(...pair({ actual: 0.001 }), call, exportValue).billingReconciled, false);
  }
});
test('actual Guard billing requires stage evidence and an explicitly Guard-inclusive provider export', () => {
  const calls = { ...receipt, guardMode: 'actual' };
  assert.equal(compareCosts(...pair({ actual: 0.001 }), calls, invoice).billingReconciled, false);
  assert.equal(compareCosts(...pair({ actual: 0.001 }), calls, { ...invoice, guardIncluded: true }).billingReconciled, true);
});
test('estimated usage remains visible and cannot be passed as reported provider usage', () => {
  const result = compareCosts(...pair({ actual: 0.001, estimated: 0.0002 }), receipt, invoice);
  assert.equal(result.conserved, true); assert.equal(result.delta.estimated, 0.0002); assert.equal(result.billingReconciled, false);
  assert.equal(compareCosts(...pair({ actual: 0.001, estimated: 1e-12 }), receipt, invoice).billingReconciled, false);
});
test('mock Guard bookkeeping is separated from the real provider amount; fully mocked calls cannot reconcile a paid invoice', () => {
  const [before, after] = pair({ actual: 0.0013 });
  after.stages = { ...after.stages, search_query: 0.001, input_guardrail: 0.0001, output_guardrail: 0.0002 };
  const result = compareCosts(before, after, receipt, invoice);
  assert.equal(result.billingReconciled, true);
  assert.ok(Math.abs(result.mockGuardAccountingDelta - 0.0003) < 1e-12);
  assert.ok(Math.abs(result.providerComparison.comparableActualDeltaUsd - 0.001) < 1e-12);
  assert.equal(compareCosts(before, after, { ...receipt, providerMode: 'mock' }, invoice).billingReconciled, false);
});
test('server restart, counter reset and other reservations invalidate attribution', () => {
  const [before, after] = pair({ actual: 0.001 });
  assert.equal(compareCosts(before, { ...after, processStarted: 2000 }).ledgerChecksPassed, false);
  assert.equal(compareCosts({ ...before, costs: { ...before.costs, actual: 2 } }, after).ledgerChecksPassed, false);
  assert.equal(compareCosts({ ...before, costs: { ...before.costs, reserved: 0.1 } }, after).ledgerChecksPassed, false);
  assert.equal(compareCosts(before, { ...after, costs: { ...after.costs, reserved: 0.1 } }).reservationClosed, false);
  assert.equal(compareCosts(before, { ...after, costs: { ...after.costs, reserved: 1e-12 } }).reservationClosed, false);
  assert.equal(compareCosts(before, { ...after, base: 'http://localhost:8080' }).ledgerChecksPassed, false);
});
test('inconsistent accounting, missing stages, wrong provider window/model and 1000-fold amount differences stay unconfirmed', () => {
  const [before, after] = pair({ actual: 0.001 });
  assert.equal(compareCosts(before, { ...after, costs: { ...after.costs, accounted: 0.002 } }).ledgerChecksPassed, false);
  assert.equal(compareCosts(before, { ...after, stages: { ...after.stages, answer: 2 } }).stageConserved, false);
  assert.equal(compareCosts(before, { ...after, stages: { ...after.stages, answer: null } }, receipt, invoice).billingReconciled, false);
  for (const extra of [{ windowStart: afterAt }, { model: 'other-model' }, { amountUsd: 1 }]) {
    assert.equal(compareCosts(before, after, receipt, { ...invoice, ...extra }).billingReconciled, false);
  }
});
test('server origin and IDs reject credentials, non-loopback hosts and invalid paths/IDs', () => {
  assert.equal(loopbackBase('http://127.0.0.1:18080'), 'http://127.0.0.1:18080');
  for (const value of ['https://example.com', 'http://user:pass@localhost', 'http://localhost/api', 'http://localhost/?key=secret']) assert.throws(() => loopbackBase(value));
  for (const value of [0, -1, '1x', 1.5, '99999999999999999999']) assert.throws(() => positiveId(value));
});
test('search review checks real parent/result IDs and excludes content and URL secrets', () => {
  const report = inspectSearch(saved(), 11, 81, 44);
  assert.equal(report.technicalChecksPassed, true); assert.equal(report.officialKbaResultCount, 1);
  assert.equal(report.results[0].sourceUrl, 'https://www.klaw.or.kr/profile');
  assert.ok(!JSON.stringify(report).includes('DO_NOT_EXPORT'));
  for (const patch of [{ id: 82 }, { intakeId: 12 }, { conditionId: 45 }, { results: [{ ...saved().results[0], searchRequestId: 82 }] }]) assert.throws(() => inspectSearch({ ...saved(), ...patch }, 11, 81, 44));
});
test('unsafe/duplicate/mislabeled sources and missing explanations are detected without fetching links', () => {
  const record = saved(); record.results[0].url = 'https://klaw.or.kr.evil.example/a'; record.results[0].explanation = '';
  let result = inspectSearch(record, 11, 81, 44);
  assert.ok(result.issues.includes('KBA_LABEL_DOMAIN_MISMATCH')); assert.ok(result.issues.includes('MISSING_RESULT_EXPLANATION'));
  record.results.push({ ...record.results[0], id: 102 });
  assert.ok(inspectSearch(record, 11, 81, 44).issues.includes('DUPLICATE_SOURCE_URL'));
  for (const value of ['javascript:alert(1)', 'http://127.0.0.1/private', 'http://[::ffff:7f00:1]/private', 'http://foo.localhost/private', 'https://user:password@example.com/a']) {
    record.results[0].url = value; assert.ok(inspectSearch(record, 11, 81, 44).issues.includes('UNSAFE_OR_INVALID_SOURCE_URL'));
  }
});
test('PARTIAL and empty outcomes remain different and semantic relevance is never automatically approved', () => {
  assert.equal(inspectSearch(saved('PARTIAL'), 11, 81, 44).technicalChecksPassed, false);
  const empty = inspectSearch({ ...saved(), status: 'NO_RESULTS', results: [] }, 11, 81, 44);
  assert.equal(empty.technicalChecksPassed, true); assert.match(empty.qualityReview, /pending/);
});

async function fixture(run, { lost = false, holdPost = false } = {}) {
  let posts = 0, status = 'PREVIEW_READY'; const auth = [];
  let releasePost, startPost;
  const postGate = new Promise(resolve => { releasePost = resolve; });
  const postStarted = new Promise(resolve => { startPost = resolve; });
  const server = http.createServer(async (req, res) => {
    auth.push({ path: req.url, auth: req.headers.authorization });
    const send = data => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(data)); };
    if (req.url === '/actuator/prometheus') { res.setHeader('Content-Type', 'text/plain'); res.end(metrics({ actual: posts ? 0.001 : 0 })); return; }
    if (req.url === '/api/intakes/11') return send({ id: 11, lawyerSearchCondition: { id: 44 }, coreStatement: 'DO_NOT_EXPORT_STORY' });
    if (req.url === '/api/intakes/search-requests/81') return send(saved(status));
    if (req.url === '/api/intakes/search-requests/81/execute') {
      posts++; for await (const chunk of req) { assert.equal(JSON.parse(chunk).retryExplanation, false); }
      startPost(); if (holdPost) await postGate;
      if (lost) { req.socket.destroy(); return; }
      status = 'COMPLETED'; res.statusCode = 202; return send(saved(status));
    }
    res.statusCode = 404; res.end();
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  const parent = path.join(root, 'output/paid-experiment-review-tests'); await fs.mkdir(parent, { recursive: true });
  const output = await fs.mkdtemp(path.join(parent, 'case-'));
  async function cli(extra = []) {
    const args = [path.join(root, 'scripts/Review-PaidExperiment.mjs'), '--action', 'search', '--base', base, '--intake-id', '11', '--search-request-id', '81', '--output', output, ...extra];
    const child = spawn(process.execPath, args, { cwd: root, windowsHide: true, env: { ...process.env, EXPERIMENT_APP_JWT: 'FAKE_TEST_JWT_DO_NOT_EXPORT', EXPERIMENT_METRICS_USER: 'test-admin', EXPERIMENT_METRICS_PASSWORD: 'FAKE_TEST_PASSWORD_DO_NOT_EXPORT' } });
    let stdout = '', stderr = ''; child.stdout.on('data', value => { stdout += value; }); child.stderr.on('data', value => { stderr += value; });
    const code = await new Promise(resolve => child.on('close', resolve)); return { code, stdout, stderr };
  }
  try { await run({ cli, output, posts: () => posts, reset: () => { status = 'PREVIEW_READY'; }, auth, releasePost, postStarted }); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}
test('CLI defaults to read-only, preserves JWT/Basic separation and exports no credentials', async () => {
  await fixture(async ({ cli, posts, auth, output }) => {
    const result = await cli(); assert.equal(result.code, 0, result.stderr); assert.equal(posts(), 0);
    assert.ok(auth.every(row => row.path.startsWith('/actuator/') ? row.auth.startsWith('Basic ') : row.auth.startsWith('Bearer ')));
    const report = await fs.readFile(path.join(output, 'search-review.json'), 'utf8');
    assert.ok(!report.includes('DO_NOT_EXPORT')); assert.equal(JSON.parse(report).executeAttempted, false);
  });
});
const executeArgs = ['--execute', '--provider-mode', 'mock', '--guard-mode', 'mock', '--confirm', 'MOCK_ONLY'];
test('CLI submits one P03 request, records before/after and verifies saved results', async () => {
  await fixture(async ({ cli, posts, output }) => {
    const result = await cli(executeArgs); assert.equal(result.code, 0, result.stderr); assert.equal(posts(), 1);
    const report = JSON.parse(await fs.readFile(path.join(output, 'search-review.json'), 'utf8'));
    assert.equal(report.executeAcknowledged, true); assert.equal(report.status, 'COMPLETED'); assert.equal(report.cost.delta.actual, 0.001);
    assert.equal(report.cost.billingReconciled, false);
  });
});
test('CLI cannot submit actual-provider execution without the explicit matching confirmation', async () => {
  await fixture(async ({ cli, posts }) => {
    const result = await cli(['--execute', '--provider-mode', 'actual', '--guard-mode', 'actual']);
    assert.equal(result.code, 1); assert.equal(posts(), 0);
  });
});
test('invalid watch settings are rejected before any paid-capable POST', async () => {
  await fixture(async ({ cli, posts }) => { assert.equal((await cli([...executeArgs, '--watch-seconds', '0'])).code, 1); assert.equal(posts(), 0); });
});
test('lost POST response is never retried and persistent intent blocks a second invocation', async () => {
  await fixture(async ({ cli, posts, output }) => {
    const first = await cli(executeArgs); assert.equal(first.code, 2, first.stderr); assert.equal(posts(), 1);
    const report = JSON.parse(await fs.readFile(path.join(output, 'search-review.json'), 'utf8'));
    assert.equal(report.executeAcknowledged, false); assert.match(report.executionError, /unknown/);
    assert.equal(report.executionLockRetained, true);
    const second = await cli(executeArgs); assert.equal(second.code, 1); assert.match(second.stderr, /journal already exists|execution lock/); assert.equal(posts(), 1);
    assert.equal((await cli()).code, 0); assert.equal(posts(), 1);
  }, { lost: true });
});
test('server-wide tool lock blocks concurrent execution and normal completion releases only the lock, preserving the intent journal', async () => {
  await fixture(async ({ cli, postStarted, releasePost, posts, reset }) => {
    const first = cli(executeArgs);
    await postStarted;
    const second = await cli(executeArgs);
    assert.equal(second.code, 1); assert.match(second.stderr, /server execution lock/); assert.equal(posts(), 1);
    releasePost(); assert.equal((await first).code, 0);
    reset();
    const replay = await cli(executeArgs);
    assert.equal(replay.code, 1); assert.match(replay.stderr, /journal already exists/); assert.equal(posts(), 1);
  }, { holdPost: true });
});
test('offline cost CLI needs no credentials or external request and saves the sanitized comparison', async () => {
  const parent = path.join(root, 'output/paid-experiment-review-tests'); await fs.mkdir(parent, { recursive: true });
  const output = await fs.mkdtemp(path.join(parent, 'offline-'));
  const [before, after] = pair({ actual: 0.001 });
  for (const [name, data] of [['before', before], ['after', after], ['receipt', receipt], ['provider', invoice]]) await fs.writeFile(path.join(output, name + '.json'), JSON.stringify(data));
  const args = [path.join(root, 'scripts/Review-PaidExperiment.mjs'), '--action', 'cost', '--before', path.join(output, 'before.json'), '--after', path.join(output, 'after.json'), '--receipt', path.join(output, 'receipt.json'), '--provider-usage', path.join(output, 'provider.json'), '--output', output];
  const child = spawn(process.execPath, args, { cwd: root, windowsHide: true, env: { ...process.env, EXPERIMENT_APP_JWT: '', EXPERIMENT_METRICS_USER: '', EXPERIMENT_METRICS_PASSWORD: '' } });
  const code = await new Promise(resolve => child.on('close', resolve)); assert.equal(code, 0);
  const report = JSON.parse(await fs.readFile(path.join(output, 'cost-review.json'), 'utf8'));
  assert.equal(report.billingReconciled, true); assert.ok(!JSON.stringify(report).includes('DO_NOT_EXPORT_STORY'));
});
