// Local evidence only: never substitute missing observations with zero.
export const costNames = {
  actual: 'ai_cost_actual_usd', estimated: 'ai_cost_estimated_usage_usd',
  accounted: 'ai_cost_accounted_usd', reserved: 'ai_cost_reserved_usd',
  totalBudget: 'ai_cost_budget_usd', dailyBudget: 'ai_cost_daily_budget_usd', sessionBudget: 'ai_cost_session_budget_usd',
};
export const costStages = ['input_guardrail', 'answer', 'output_guardrail', 'search_query', 'search_explanation'];
const finite = value => typeof value === 'number' && Number.isFinite(value) && value >= 0;
const near = (a, b) => Math.abs(a - b) <= Math.max(1e-8, Math.abs(a) * 1e-6, Math.abs(b) * 1e-6);
export function snapshotCosts(text, { base, at = new Date().toISOString() } = {}) {
  const samples = text.split('\n').flatMap(line => {
    const match = /^([a-zA-Z_:][\w:]*)(?:\{(.*)\})?\s+([^\s]+)/.exec(line);
    if (!match || !finite(Number(match[3]))) return [];
    const labels = Object.fromEntries([...String(match[2] || '').matchAll(/(\w+)="((?:\\.|[^"\\])*)"/g)].map(item => [item[1], item[2]]));
    return [{ name: match[1], labels, value: Number(match[3]) }];
  });
  const sum = (name, predicate = () => true) => {
    const rows = samples.filter(row => row.name === name && predicate(row.labels));
    return rows.length ? rows.reduce((total, row) => total + row.value, 0) : null;
  };
  return {
    schema: 'cost-snapshot-v1', base, at,
    processStarted: sum('process_start_time_seconds'),
    costs: Object.fromEntries(Object.entries(costNames).map(([key, name]) => [key, sum(name)])),
    stages: Object.fromEntries(costStages.map(stage => [stage, sum('ai_cost_stage_accounted_usd', labels => labels.stage === stage)])),
    tokens: { prompt: sum('ai_llm_tokens_total', labels => labels.type === 'prompt'), completion: sum('ai_llm_tokens_total', labels => labels.type === 'completion') },
  };
}
export function compareCosts(before, after, receipt = {}, providerUsage = null) {
  if (before?.schema !== 'cost-snapshot-v1' || after?.schema !== 'cost-snapshot-v1' || !before.costs || !after.costs || !before.stages || !after.stages) throw new Error('Cost snapshot schema mismatch');
  const issues = [];
  const sameBase = before.base === after.base;
  const ordered = Number.isFinite(Date.parse(before.at)) && Date.parse(after.at) >= Date.parse(before.at);
  const sameProcess = finite(before.processStarted) && before.processStarted === after.processStarted;
  if (!sameBase || !ordered || !sameProcess) issues.push('SAME_PROCESS_AND_ORDERED_BASELINE_NOT_CONFIRMED');
  const difference = (a, b) => finite(a) && finite(b) && b >= a - 1e-10 ? Math.max(0, b - a) : null;
  const delta = Object.fromEntries(['actual', 'estimated', 'accounted'].map(key => [key, sameBase && ordered && sameProcess ? difference(before.costs[key], after.costs[key]) : null]));
  if (Object.values(delta).some(value => value == null)) issues.push('MISSING_OR_RESET_COST_COUNTER');
  const stageDelta = Object.fromEntries(costStages.map(stage => [stage, sameBase && ordered && sameProcess ? difference(before.stages[stage], after.stages[stage]) : null]));
  const conserved = Object.values(delta).every(finite) ? near(delta.accounted, delta.actual + delta.estimated) : null;
  if (conserved === false) issues.push('ACCOUNTED_DOES_NOT_MATCH_ACTUAL_PLUS_ESTIMATED');
  const stageTotal = Object.values(stageDelta).every(finite) ? Object.values(stageDelta).reduce((sum, value) => sum + value, 0) : null;
  const stageConserved = stageTotal != null && finite(delta.accounted) ? near(stageTotal, delta.accounted) : null;
  if (stageConserved === false) issues.push('STAGES_DO_NOT_MATCH_ACCOUNTED');
  const reservationClosed = finite(after.costs.reserved) ? after.costs.reserved === 0 : null;
  if (reservationClosed !== true) issues.push('RESERVATION_NOT_CONFIRMED_CLOSED');
  const cleanStart = finite(before.costs.reserved) && before.costs.reserved === 0;
  if (!cleanStart) issues.push('OTHER_RESERVATIONS_AT_BASELINE');
  const sanitizedReceipt = {
    model: typeof receipt.model === 'string' ? receipt.model.slice(0, 120) : null,
    usageAvailable: typeof receipt.usageAvailable === 'boolean' ? receipt.usageAvailable : null,
    costSource: typeof receipt.costSource === 'string' ? receipt.costSource.slice(0, 60) : null,
    blocked: typeof receipt.blocked === 'boolean' ? receipt.blocked : null,
    fallback: typeof receipt.fallback === 'boolean' ? receipt.fallback : null,
    providerMode: ['actual', 'mock'].includes(receipt.providerMode) ? receipt.providerMode : 'unknown',
    promptTokens: Number.isSafeInteger(receipt.promptTokens) && receipt.promptTokens >= 0 ? receipt.promptTokens : null,
    completionTokens: Number.isSafeInteger(receipt.completionTokens) && receipt.completionTokens >= 0 ? receipt.completionTokens : null,
  };
  const tokenDelta = Object.fromEntries(['prompt', 'completion'].map(key => [key, sameBase && ordered && sameProcess ? difference(before.tokens?.[key], after.tokens?.[key]) : null]));
  // Mock Guard usage can still produce bookkeeping cost in the app. It is not an external invoice item.
  const mockGuardAccountingDelta = receipt.guardMode === 'mock' && finite(stageDelta.input_guardrail) && finite(stageDelta.output_guardrail)
    ? stageDelta.input_guardrail + stageDelta.output_guardrail : null;
  const providerComparableActualDelta = sanitizedReceipt.providerMode !== 'actual' ? null
    : receipt.guardMode === 'mock' ? finite(mockGuardAccountingDelta) && finite(delta.actual) && delta.estimated === 0
      && delta.actual >= mockGuardAccountingDelta - 1e-10 ? Math.max(0, delta.actual - mockGuardAccountingDelta) : null : delta.actual;
  const providerScopeValid = providerUsage?.kind === 'provider_usage_export' && finite(providerUsage.amountUsd)
    && providerUsage.exclusiveOperation === true
    && Number.isFinite(Date.parse(providerUsage.windowStart)) && Number.isFinite(Date.parse(providerUsage.windowEnd))
    && Date.parse(providerUsage.windowStart) <= Date.parse(before.at) && Date.parse(providerUsage.windowEnd) >= Date.parse(after.at)
    && (!sanitizedReceipt.model || !providerUsage.model || providerUsage.model === sanitizedReceipt.model);
  const providerComparison = providerScopeValid && finite(providerComparableActualDelta) ? {
    amountUsd: providerUsage.amountUsd, appActualDeltaUsd: delta.actual, comparableActualDeltaUsd: providerComparableActualDelta,
    matches: near(providerUsage.amountUsd, providerComparableActualDelta), evidence: 'user-supplied isolated-window export; not independently fetched',
  } : null;
  if (sanitizedReceipt.providerMode !== 'actual') issues.push('ACTUAL_PROVIDER_MODE_NOT_CONFIRMED');
  if (!providerScopeValid) issues.push('PROVIDER_USAGE_EXPORT_OR_EXCLUSIVE_WINDOW_MISSING');
  if (providerComparison?.matches === false) issues.push('PROVIDER_USAGE_DIFFERENCE');
  const guardCoverage = receipt.guardMode === 'mock' ? 'mock-no-provider-billing'
    : receipt.guardMode === 'actual' && finite(stageDelta.input_guardrail) && finite(stageDelta.output_guardrail) && providerScopeValid && providerUsage.guardIncluded === true ? 'included-in-provided-export' : 'unconfirmed';
  if (guardCoverage === 'unconfirmed') issues.push('GUARD_BILLING_SCOPE_UNCONFIRMED');
  return {
    schema: 'cost-review-v1', beforeAt: before.at, afterAt: after.at,
    delta, stageDelta, tokenDelta, stageConserved, conserved, reservationClosed, cleanStart, receipt: sanitizedReceipt,
    providerComparison, mockGuardAccountingDelta, providerComparableActualDelta, guardCoverage, issues,
    ledgerChecksPassed: sameBase && ordered && sameProcess && cleanStart && conserved === true && stageConserved !== false && reservationClosed === true,
    billingReconciled: sameBase && ordered && sameProcess && cleanStart && conserved === true && reservationClosed === true
      && stageConserved === true && !!sanitizedReceipt.model && sanitizedReceipt.usageAvailable === true && delta.estimated === 0 && providerComparison?.matches === true && guardCoverage !== 'unconfirmed',
    attribution: 'Global server counters. Valid attribution requires no other experiment in the interval. Token counters do not prove Guard token usage.',
  };
}
export function loopbackBase(value) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol) || !['127.0.0.1', 'localhost', '[::1]'].includes(url.hostname)
      || url.username || url.password || url.search || url.hash || url.pathname !== '/') throw new Error('Use a loopback server origin without credentials or a path');
  return url.origin;
}
export function positiveId(value) {
  if (!/^[1-9]\d*$/.test(String(value)) || !Number.isSafeInteger(Number(value))) throw new Error('A positive safe-integer ID is required');
  return Number(value);
}
function sourceUrl(value) {
  try {
    if (typeof value !== 'string' || /[\x00-\x20\x7f]/.test(value)) return null;
    const url = new URL(value);
    if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password
        || ['localhost', '[::1]', '0.0.0.0'].includes(url.hostname) || url.hostname.endsWith('.localhost')
        || (url.hostname.startsWith('[') && !/^\[[23][0-9a-f]{3}:/.test(url.hostname))
        || /^(127\.|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.|169\.254\.)/.test(url.hostname)) return null;
    return url;
  } catch { return null; }
}
export function inspectSearch(request, intakeId, requestId, conditionId) {
  const issues = [];
  if (request?.id !== requestId || request.intakeId !== intakeId || request.conditionId !== conditionId) throw new Error('Search/intake/condition ID mismatch');
  if (!Array.isArray(request.results)) throw new Error('Missing saved results');
  const ids = new Set(), urls = new Set();
  const rows = request.results.map(row => {
    if (!row || row.searchRequestId !== requestId || !Number.isSafeInteger(row.id) || row.id <= 0 || ids.has(row.id)) throw new Error('Saved result ID mismatch or duplicate');
    ids.add(row.id);
    const url = sourceUrl(row.url), key = url ? url.origin + url.pathname.replace(/\/$/, '') + url.search : '';
    const official = url ? ['klaw.or.kr', 'koreanbar.or.kr'].some(host => url.hostname === host || url.hostname.endsWith('.' + host)) : false;
    if (!url) issues.push('UNSAFE_OR_INVALID_SOURCE_URL');
    if (key && urls.has(key)) issues.push('DUPLICATE_SOURCE_URL');
    if (row.sourceType === 'KBA_PUBLIC_WEB' && !official) issues.push('KBA_LABEL_DOMAIN_MISMATCH');
    if (key) urls.add(key);
    if (typeof row.title !== 'string' || !row.title.trim()) issues.push('MISSING_RESULT_TITLE');
    if (request.status === 'COMPLETED' && (typeof row.explanation !== 'string' || !row.explanation.trim())) issues.push('MISSING_RESULT_EXPLANATION');
    return { id: row.id, searchRequestId: row.searchRequestId, sourceType: String(row.sourceType || '').slice(0, 60),
      sourceUrl: url ? url.origin + url.pathname : null, // No URL query, fragment, title, story or generated text in exported evidence.
      officialKbaDomain: official, hasTitle: typeof row.title === 'string' && !!row.title.trim(), hasExplanation: typeof row.explanation === 'string' && !!row.explanation.trim() };
  });
  if (rows.length > 3) issues.push('MORE_THAN_THREE_RESULTS');
  if (request.status === 'COMPLETED' && !rows.length) issues.push('COMPLETED_WITH_NO_RESULTS');
  if (!['COMPLETED', 'NO_RESULTS'].includes(request.status)) issues.push('EXECUTION_NOT_SUCCESSFULLY_FINISHED');
  return {
    schema: 'search-review-v1', intakeId, searchRequestId: requestId, conditionId, status: request.status,
    sourcePolicy: request.sourcePolicy, resultCount: rows.length,
    officialKbaResultCount: rows.filter(row => row.officialKbaDomain).length,
    generalWebResultCount: rows.filter(row => row.sourceType === 'NAVER_WEB').length,
    warningCode: request.execution?.sourceWarningCode || null, errorCode: request.execution?.errorCode || null,
    results: rows, issues: [...new Set(issues)], technicalChecksPassed: issues.length === 0,
    qualityReview: 'pending: human must check region, legal field, actual profile, and explanation against the original source',
    providerCallCount: 'not exposed by existing API; two-source policy alone is not proof of two successful external calls',
  };
}
