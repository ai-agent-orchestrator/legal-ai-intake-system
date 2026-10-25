// Defaults to GET/offline review. Existing APIs only; OpenAI/Naver keys are never read here.
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { compareCosts, inspectSearch, loopbackBase, positiveId, snapshotCosts } from './lib/experimentReview.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const options = {};
for (let i = 2; i < process.argv.length; i++) {
  const name = process.argv[i];
  if (!name.startsWith('--') || options[name.slice(2)] !== undefined) throw new Error('Use unique named arguments');
  options[name.slice(2)] = name === '--execute' ? true : process.argv[++i];
}
const allowed = new Set(['action', 'base', 'intake-id', 'search-request-id', 'output', 'execute', 'confirm', 'provider-mode', 'guard-mode', 'before', 'after', 'receipt', 'provider-usage', 'watch-seconds']);
if (Object.keys(options).some(key => !allowed.has(key))) throw new Error('Unknown argument');
const action = options.action || 'snapshot';
const jsonFile = async file => JSON.parse((await fs.readFile(path.resolve(file), 'utf8')).replace(/^\uFEFF/, ''));
const output = path.resolve(options.output || path.join(root, 'output/paid-experiment-review', new Date().toISOString().replace(/[:.]/g, '-')));
const save = async (name, value) => { await fs.mkdir(output, { recursive: true }); await fs.writeFile(path.join(output, name), JSON.stringify(value, null, 2)); };
let base, bearer, basic;
let ownedLock = null, releaseOwnedLock = true;
async function request(endpoint, { method = 'GET', body, metrics = false } = {}) {
  const auth = metrics ? basic : bearer;
  if (!auth) throw new Error(metrics ? 'Missing local Actuator credentials' : 'Missing local application JWT');
  let response;
  try {
    response = await fetch(base + endpoint, { method, redirect: 'error', signal: AbortSignal.timeout(15000), headers: { Authorization: auth, ...(body ? { 'Content-Type': 'application/json' } : {}) }, ...(body ? { body: JSON.stringify(body) } : {}) });
  } catch { throw new Error(`${method} ${endpoint}: transport outcome unknown; no automatic retry`); }
  if (!response.ok) throw new Error(`${method} ${endpoint}: HTTP ${response.status}; no automatic retry`);
  if (metrics) return response.text();
  try { return await response.json(); } catch { throw new Error(`${method} ${endpoint}: invalid JSON response`); }
}
const snapshot = async () => snapshotCosts(await request('/actuator/prometheus', { metrics: true }), { base });
async function main() {
  if (action === 'cost') {
    if (options.execute || options.base || !options.before || !options.after) throw new Error('Cost review is offline: supply --before and --after');
    const report = compareCosts(await jsonFile(options.before), await jsonFile(options.after), options.receipt ? await jsonFile(options.receipt) : {}, options['provider-usage'] ? await jsonFile(options['provider-usage']) : null);
    await save('cost-review.json', report);
    console.log(JSON.stringify({ action, ledgerChecksPassed: report.ledgerChecksPassed, billingReconciled: report.billingReconciled, issues: report.issues, output }));
    return;
  }
  if (!['snapshot', 'search'].includes(action)) throw new Error('Supported actions: snapshot, cost, search');
  base = loopbackBase(options.base || '');
  bearer = process.env.EXPERIMENT_APP_JWT ? 'Bearer ' + process.env.EXPERIMENT_APP_JWT : null;
  basic = process.env.EXPERIMENT_METRICS_USER && process.env.EXPERIMENT_METRICS_PASSWORD
    ? 'Basic ' + Buffer.from(process.env.EXPERIMENT_METRICS_USER + ':' + process.env.EXPERIMENT_METRICS_PASSWORD).toString('base64') : null;
  if (process.env.EXPERIMENT_METRICS_USER?.includes(':')) throw new Error('Invalid Basic username');
  if (action === 'snapshot') {
    if (options.execute) throw new Error('Snapshot never executes a search');
    await save('snapshot.json', await snapshot());
    console.log(JSON.stringify({ action, generationRequests: 0, output })); return;
  }
  const intakeId = positiveId(options['intake-id']), searchId = positiveId(options['search-request-id']);
  const seconds = Number(options['watch-seconds'] || 180);
  if (!Number.isInteger(seconds) || seconds < 1 || seconds > 600) throw new Error('Watch seconds must be 1–600');
  const intake = await request('/api/intakes/' + intakeId);
  const conditionId = positiveId(intake.lawyerSearchCondition?.id);
  let selected = await request('/api/intakes/search-requests/' + searchId);
  inspectSearch(selected, intakeId, searchId, conditionId);
  let before = await snapshot(); await save('before.json', before);
  const journalKey = createHash('sha256').update(`${base}|${intakeId}|${searchId}`).digest('hex').slice(0, 24);
  const journalDir = path.join(root, 'backend/guardrail-ready-policy-checker/.local/experiment-review');
  await fs.mkdir(journalDir, { recursive: true });
  const journalFile = path.join(journalDir, journalKey + '.json');
  let executeAttempted = false, executeAcknowledged = false, executionError = null;
  if (options.execute) {
    const mode = options['provider-mode'], expected = mode === 'actual' ? 'ONE_SEARCH_MAY_BILL' : mode === 'mock' ? 'MOCK_ONLY' : null;
    if (!expected || options.confirm !== expected || !['mock', 'actual'].includes(options['guard-mode'])) throw new Error('Explicit provider/Guard modes and the matching one-search confirmation are required');
    if (selected.status !== 'PREVIEW_READY' || selected.execution) throw new Error('Execute only a new PREVIEW_READY request. Existing/partial/finished executions are read-only here');
    if (selected.sourcePolicy !== 'KBA_NAVER_PUBLIC_WEB') throw new Error('Saved source policy is not the mixed KBA/Naver policy');
    const lockPath = path.join(journalDir, 'server-' + createHash('sha256').update(base).digest('hex').slice(0, 24) + '.lock');
    const lock = await fs.open(lockPath, 'wx').catch(() => { throw new Error('A server execution lock exists: review the previous attempt; no parallel execution allowed'); });
    ownedLock = { path: lockPath, token: randomUUID() };
    try { await lock.writeFile(JSON.stringify({ token: ownedLock.token, base, intakeId, searchId, pid: process.pid, at: new Date().toISOString() })); }
    finally { await lock.close(); }
    // Re-sample after taking the server-wide tool lock, so a preceding tool run cannot enter this baseline.
    before = await snapshot(); await save('before.json', before);
    if (before.costs.reserved !== 0 || !Number.isFinite(before.processStarted)
      || !['totalBudget', 'dailyBudget', 'sessionBudget'].every(key => Number.isFinite(before.costs[key]) && before.costs[key] > 0)
      || !Number.isFinite(before.costs.accounted) || before.costs.accounted >= before.costs.totalBudget) throw new Error('Cannot confirm idle ledger and positive server budgets. No search submitted');
    // Persistent intent precedes POST. A crash or lost response cannot silently permit another attempt.
    const journal = await fs.open(journalFile, 'wx').catch(() => { throw new Error('An attempt journal already exists: use read-only review; never automatically resubmit'); });
    try { await journal.writeFile(JSON.stringify({ base, intakeId, searchId, at: new Date().toISOString(), state: 'POST_INTENT_RECORDED', providerMode: mode, guardMode: options['guard-mode'] })); }
    finally { await journal.close(); }
    executeAttempted = true;
    releaseOwnedLock = false;
    try { await request('/api/intakes/search-requests/' + searchId + '/execute', { method: 'POST', body: { retryExplanation: false } }); executeAcknowledged = true; }
    catch (error) { executionError = error.message; }
    selected = await request('/api/intakes/search-requests/' + searchId);
    const deadline = Date.now() + seconds * 1000;
    while (['QUEUED', 'RUNNING'].includes(selected.status) && Date.now() < deadline) {
      await new Promise(resolve => setTimeout(resolve, 1500));
      selected = await request('/api/intakes/search-requests/' + searchId);
    }
  }
  const analysis = inspectSearch(selected, intakeId, searchId, conditionId);
  const reread = inspectSearch(await request('/api/intakes/search-requests/' + searchId), intakeId, searchId, conditionId);
  if (analysis.status !== reread.status || JSON.stringify(analysis.results.map(row => row.id)) !== JSON.stringify(reread.results.map(row => row.id))) analysis.issues.push('STORED_RESULT_CHANGED_DURING_REVIEW');
  analysis.technicalChecksPassed = analysis.issues.length === 0;
  const after = await snapshot(); await save('after.json', after);
  const cost = compareCosts(before, after, { providerMode: options['provider-mode'], guardMode: options['guard-mode'] });
  if (executeAttempted && ['COMPLETED', 'NO_RESULTS', 'PARTIAL', 'FAILED'].includes(analysis.status) && cost.reservationClosed === true) releaseOwnedLock = true;
  const report = { ...analysis, executeAttempted, executeAcknowledged, executionError, providerMode: options['provider-mode'] || 'not-attested', guardMode: options['guard-mode'] || 'unknown', cost,
    costScope: executeAttempted ? 'this verification window; requires no other client activity' : 'read-only sampling window; not the historical cost of the saved execution',
    attemptJournal: executeAttempted ? path.relative(root, journalFile) : null, executionLockRetained: executeAttempted ? !releaseOwnedLock : null,
    warning: 'One P03 POST can generate a query plus explanations and Guard calls. This tool does not impose a provider billing cap. Existing server budgets remain authoritative.',
    storedReadback: 'same IDs/status observed twice; source content and semantic relevance still require human review' };
  await save('search-review.json', report);
  if (executeAttempted) await fs.writeFile(journalFile, JSON.stringify({ base, intakeId, searchId, state: 'ATTEMPT_REVIEWED', executeAcknowledged, status: analysis.status, report: path.join(output, 'search-review.json') }, null, 2));
  console.log(JSON.stringify({ action, executeAttempted, executeAcknowledged, status: analysis.status, results: analysis.resultCount, technicalChecksPassed: analysis.technicalChecksPassed, billingReconciled: false, output }));
  if (executeAttempted && !analysis.technicalChecksPassed) process.exitCode = 2;
}
main().catch(error => { console.error(error.message); process.exitCode = 1; }).finally(async () => {
  if (!ownedLock || !releaseOwnedLock) return;
  const owner = await jsonFile(ownedLock.path).catch(() => null);
  if (owner?.token === ownedLock.token) await fs.unlink(ownedLock.path);
});
