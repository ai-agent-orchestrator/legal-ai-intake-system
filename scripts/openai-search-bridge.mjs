// OpenAI web search bridge for the P03 lawyer search (local demo use only, loopback).
// The Spring server keeps calling its NAVER-shaped endpoint (GET /v1/search/webkr.json); this bridge answers it
// with ONE OpenAI web search (Responses API + web_search tool) and returns NAVER-shaped items.
//   - Key: env OPENAI_SEARCH_BRIDGE_KEY (set only for this process by Start-OpenAiSearchBridge.ps1, removed from env at start)
//   - Hard cap: BRIDGE_MAX_SEARCHES paid searches per process, then 429. Min 2s between paid searches.
//   - The two P03 legs ("<q> site:klaw.or.kr" then "<q>") share one paid search through a 5-minute cache.
//   - Logs counts/tokens/cost only (no key, no query text) to BRIDGE_LOG.
import http from 'node:http';
import { appendFileSync } from 'node:fs';

const key = process.env.OPENAI_SEARCH_BRIDGE_KEY || '';
delete process.env.OPENAI_SEARCH_BRIDGE_KEY;
if (!/^[A-Za-z0-9_-]{20,}$/.test(key)) { console.error('STOP: no usable key in this process environment.'); process.exit(1); }
const port = Number(process.env.BRIDGE_PORT || 18282);
const maxSearches = Number(process.env.BRIDGE_MAX_SEARCHES || 20);
const logFile = process.env.BRIDGE_LOG || '';
const MODEL = 'gpt-4o-mini', FEE = 0.01, BLOCK = 8000, IN = 0.15 / 1e6, OUT = 0.60 / 1e6;
const KBA = ' site:klaw.or.kr';
const cache = new Map();
const stats = { searches: 0, cacheHits: 0, failures: 0, estimatedUsd: 0, max: maxSearches };
let lastPaidAt = 0, busy = Promise.resolve();

const log = entry => {
  const line = JSON.stringify({ at: new Date().toISOString(), ...entry });
  console.log(line);
  if (logFile) { try { appendFileSync(logFile, line + '\n'); } catch { /* logging never breaks the demo */ } }
};
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

function itemsFrom(payload) {
  const message = (payload.output || []).find(item => item.type === 'message');
  const parts = (message?.content || []).filter(part => part.type === 'output_text');
  const items = [], seen = new Set();
  for (const part of parts) {
    const text = part.text || '';
    for (const a of part.annotations || []) {
      if (a.type !== 'url_citation' || !a.url || seen.has(a.url)) continue;
      seen.add(a.url);
      // Description = the line of the answer that carries this citation, without markdown links.
      const lineStart = text.lastIndexOf('\n', Math.max(0, a.start_index - 1)) + 1;
      const lineEnd = text.indexOf('\n', a.end_index);
      const line = text.slice(lineStart, lineEnd < 0 ? undefined : lineEnd)
        .replace(/\(\[[^\]]*\]\([^)]*\)\)/g, '').replace(/\[([^\]]*)\]\([^)]*\)/g, '$1').replace(/[*#>`_-]{1,3}\s?/g, '').trim();
      items.push({ title: (a.title || a.url).slice(0, 300), link: a.url, description: line.slice(0, 900) });
    }
  }
  return items;
}

async function paidSearch(query) {
  if (stats.searches >= maxSearches) { const e = new Error('cap'); e.status = 429; throw e; }
  const wait = lastPaidAt + 2000 - Date.now(); if (wait > 0) await sleep(wait);
  stats.searches++; lastPaidAt = Date.now();
  const body = {
    model: MODEL, store: false, max_output_tokens: 600, tool_choice: 'required', max_tool_calls: 1,
    tools: [{ type: 'web_search', user_location: { type: 'approximate', country: 'KR' } }],
    instructions: '한국어로 답하라. 웹 검색 결과에 근거한 내용만, 3~5개 항목으로 한 줄씩 쓰고 각 줄에 출처 링크를 달아라. '
      + '대한변호사협회(klaw.or.kr, koreanbar.or.kr)의 공식 변호사 검색·안내 페이지가 있으면 먼저 포함하라. '
      + '특정 변호사를 추천하거나 평가하지 말고, 공개된 검색 경로와 절차 정보만 다뤄라.',
    input: query,
  };
  const started = Date.now();
  const response = await fetch('https://api.openai.com/v1/responses', {
    method: 'POST', headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body), signal: AbortSignal.timeout(40000), redirect: 'error',
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    stats.failures++;
    log({ event: 'search_failed', status: response.status, code: payload?.error?.code || '', type: payload?.error?.type || '', param: payload?.error?.param || '' });
    const e = new Error('provider'); e.status = 502; throw e;
  }
  const calls = (payload.output || []).filter(item => item.type === 'web_search_call').length;
  const inTok = payload.usage?.input_tokens || 0, outTok = payload.usage?.output_tokens || 0;
  const cost = calls * (FEE + BLOCK * IN) + inTok * IN + outTok * OUT;
  stats.estimatedUsd += cost;
  const items = itemsFrom(payload);
  log({ event: 'search', webSearchCalls: calls, inputTokens: inTok, outputTokens: outTok, citations: items.length,
    estimatedUsd: Number(cost.toFixed(6)), totalUsd: Number(stats.estimatedUsd.toFixed(6)), used: `${stats.searches}/${maxSearches}`, ms: Date.now() - started });
  return items;
}

async function search(rawQuery, display) {
  const base = rawQuery.endsWith(KBA) ? rawQuery.slice(0, -KBA.length).trim() : rawQuery.trim();
  const hit = cache.get(base);
  if (hit && Date.now() - hit.at < 300000) { stats.cacheHits++; return hit.items.slice(0, display); }
  // Serialize paid searches so two legs of one P03 run never pay twice.
  const run = busy.then(async () => {
    const again = cache.get(base);
    if (again && Date.now() - again.at < 300000) { stats.cacheHits++; return again.items; }
    const items = await paidSearch(base);
    cache.set(base, { at: Date.now(), items });
    return items;
  });
  busy = run.catch(() => {});
  return (await run).slice(0, display);
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${port}`);
  const send = (body, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(body)); };
  if (url.pathname === '/__bridge__/stats') return send({ ...stats, estimatedUsd: Number(stats.estimatedUsd.toFixed(6)) });
  if (req.method !== 'GET' || url.pathname !== '/v1/search/webkr.json') return send({ error: 'not found' }, 404);
  const query = url.searchParams.get('query') || '';
  const display = Math.min(Math.max(Number(url.searchParams.get('display') || 3), 1), 5);
  if (!query.trim() || query.length > 320) return send({ error: 'invalid query' }, 400);
  try { send({ items: await search(query, display) }); }
  catch (error) {
    if (error.status === 429) log({ event: 'cap_reached', used: `${stats.searches}/${maxSearches}` });
    send({ error: error.status === 429 ? 'search cap reached' : 'search failed' }, error.status || 502);
  }
});
server.listen(port, '127.0.0.1', () => console.log(`OpenAI web search bridge: http://127.0.0.1:${port} · model ${MODEL} · cap ${maxSearches} paid searches · ~$${(FEE + BLOCK * IN).toFixed(4)} each`));
process.on('SIGINT', () => { log({ event: 'stopped', ...stats }); server.close(); process.exit(0); });
