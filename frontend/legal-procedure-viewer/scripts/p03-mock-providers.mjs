// Local QA only: deterministic HTTP fixtures for the REAL Spring adapters. Never use as a production provider.
import http from 'node:http';
const port = Number(process.env.P03_MOCK_PORT || 18181);
let mode = 'normal';
const calls = [];
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://127.0.0.1:${port}`);
  const send = (body, status = 200) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' }); res.end(JSON.stringify(body)); };
  if (url.pathname === '/__qa__/mode' && req.method === 'POST') { mode = url.searchParams.get('value') || 'normal'; calls.length = 0; return send({ mode }); }
  if (url.pathname === '/__qa__/calls') return send({ mode, calls });
  let body = ''; for await (const chunk of req) body += chunk;
  const data = body ? JSON.parse(body) : {};
  if (['/v1/input-rails/check', '/v1/output-rails/check'].includes(url.pathname)) return send({ status: 'PASSED', content: data.messages?.[0]?.content || '', rail: 'QA_LOCAL', usage: { prompt_tokens: 5, completion_tokens: 2 } });
  if (url.pathname === '/v1/chat/completions') {
    const query = data.messages?.[0]?.content.includes('P03_QUERY');
    calls.push(query ? 'query' : 'explanation');
    await new Promise(resolve => setTimeout(resolve, mode === 'slow' ? 3000 : 400));
    if (!query && mode === 'explanation-error') return send({ error: 'local fixture failure' }, 500);
    const content = query ? JSON.stringify({ query: '서울 민사 대여금 변호사 공개 법률 자료' }) : '민사 대여금과 서울 지역 검색 조건에 관련된 공개 자료입니다. 요약에 없는 경력·자격 정보는 원문 확인이 필요합니다.';
    return send({ model: 'p03-local-fixture', choices: [{ message: { role: 'assistant', content } }], usage: { prompt_tokens: 30, completion_tokens: 18 } });
  }
  if (url.pathname === '/v1/search/webkr.json') {
    calls.push('search');
    if (mode === 'search-error') return send({ error: 'local fixture failure' }, 500);
    return send({ items: mode === 'empty' ? [] : [
      { title: '<b>모의 검수</b> · 민사 대여금 공개 자료', link: 'https://example.com/p03-civil', description: '로컬 검수용 가상 자료입니다. 민사 대여금 절차를 설명하는 제공자 요약입니다.' },
      { title: '모의 검수 · 서울 지역 법률 안내', link: 'https://example.org/p03-region', description: '로컬 검수용 가상 자료입니다. 서울 지역 법률 상담 정보를 담은 제공자 요약입니다.' },
    ] });
  }
  send({ error: 'fixture route not found' }, 404);
});
server.listen(port, '127.0.0.1', () => console.log(`P03 LOCAL MOCK provider: http://127.0.0.1:${port}`));
process.on('SIGINT', () => server.close());
