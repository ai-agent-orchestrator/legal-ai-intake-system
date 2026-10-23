// Local-only React review support. Never imported by production code.
import http from 'node:http';
import assert from 'node:assert/strict';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
const backend = 'http://127.0.0.1:18088', proxy = 'http://127.0.0.1:8089';
const output = new URL('../../../output/operator-09-12-react-review/', import.meta.url);
const [command, value, faultPath = '', faultMethod = 'GET'] = process.argv.slice(2);
async function request(path, body, method = body === undefined ? 'GET' : 'POST', expected = 200) {
  const r = await fetch(backend + path, { method, headers: { 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  assert.equal(r.status, expected, path); return r.json();
}
if (command === 'serve') {
  let mode = 'normal', scenario = { kind:'normal' };
  const traffic = await readFile(new URL('traffic.json',output),'utf8').then(text=>JSON.parse(text).traffic).catch(()=>[]);
  const server = http.createServer(async (req, res) => {
    const send = data => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(data)); };
    try {
      if (req.url === '/__qa__/traffic') return send({ mode, traffic });
      if (req.url.startsWith('/__qa__/mode?') && req.method === 'POST') { mode = new URL(req.url, proxy).searchParams.get('value'); assert.ok(['normal','slow-write'].includes(mode)); scenario={kind:'normal'}; return send({ mode }); }
      if (req.url.startsWith('/__qa__/scenario?') && req.method === 'POST') { const q=new URL(req.url,proxy).searchParams; scenario={kind:q.get('kind'),path:q.get('path'),method:q.get('method')}; assert.ok(['slow','timeout','empty','error','forbidden','malformed','bad-metrics','lost'].includes(scenario.kind)); assert.ok(scenario.path.startsWith('/api/')||scenario.path==='/actuator/prometheus'); assert.ok(['GET','PATCH'].includes(scenario.method));mode=scenario.kind;return send(scenario); }
      assert.ok(req.url.startsWith('/api/') || req.url.startsWith('/actuator/'));
      const chunks = []; for await (const chunk of req) chunks.push(chunk); const body = Buffer.concat(chunks);
      const row = { method: req.method, path: req.url, authentication: req.headers.authorization ? req.headers.authorization.startsWith('Basic ') ? 'Basic' : 'Bearer' : 'none', bodyBytes: body.length, mode, startedAt: Date.now() }; traffic.push(row);
      const selected=scenario, currentPath=new URL(req.url,proxy).pathname, match=selected.method===req.method&&(selected.path?.endsWith('/*')?currentPath.startsWith(selected.path.slice(0,-1)):selected.path===currentPath);
      row.fault=match?selected.kind:null;
      if(match&&['slow','timeout'].includes(selected.kind)) await new Promise(resolve=>setTimeout(resolve,selected.kind==='timeout'?17000:5000));
      const corsHeaders={'Access-Control-Allow-Origin':req.headers.origin||'*','Content-Type':'application/json'};
      if(match&&['error','forbidden','malformed','bad-metrics'].includes(selected.kind)) { row.status=selected.kind==='error'?500:selected.kind==='forbidden'?403:200;res.writeHead(row.status,corsHeaders);res.end(selected.kind==='bad-metrics'?'<html>Not Prometheus</html>':JSON.stringify(selected.kind==='malformed'?{invalid:true}:{error:'Synthetic QA failure'}));return; }
      if (mode === 'slow-write' && req.method === 'PATCH') await new Promise(resolve => setTimeout(resolve, 5000));
      const headers = {}; for (const name of ['authorization','origin','access-control-request-method','access-control-request-headers','content-type','accept']) if (req.headers[name]) headers[name] = req.headers[name];
      const upstream = await fetch(backend + req.url, { method: req.method, headers, ...(body.length ? { body } : {}), redirect: 'manual' }); row.status = upstream.status;
      const resultHeaders = {}; for (const name of ['content-type','x-trace-id','www-authenticate','access-control-allow-origin','access-control-allow-methods','access-control-allow-headers','access-control-expose-headers','vary']) if (upstream.headers.has(name)) resultHeaders[name] = upstream.headers.get(name);
      let bytes = Buffer.from(await upstream.arrayBuffer());
      if(match&&selected.kind==='lost'&&upstream.ok){row.responseLost=true;res.destroy();return;}
      if(match&&selected.kind==='empty'&&upstream.ok){if(req.url==='/actuator/prometheus')bytes=Buffer.from('');else{const data=JSON.parse(bytes.toString());const emptyRisk={total:0,high:0,medium:0,low:0,decisionCounts:{},recommendedActionCounts:{}};const next=Array.isArray(data)?[]:req.url.includes('/marketing-funnel')?{...data,submittedIntakeCount:0,searchRequestCount:0,searchRequestedIntakeCount:0,consultationReadyIntakeCount:0,stages:data.stages.map(s=>({...s,count:0})),caseTypeDistribution:{},desiredNextStepDistribution:{},regionDistribution:{},eventCounts:{VISIT:0,INTAKE_STARTED:0,SUMMARY_VIEWED:0}}:req.url.includes('/dashboard/summary')?{agentRunCount:0,riskHistoryCount:0,securityEventCount:0,openIncidentCount:0,riskSummary:emptyRisk}:emptyRisk;bytes=Buffer.from(JSON.stringify(next));}}
      res.writeHead(upstream.status, { ...resultHeaders, 'Cache-Control':'no-store' }); res.end(bytes);
    } catch { if (!res.destroyed) { res.writeHead(502); res.end('Local QA proxy error'); } }
  });
  server.listen(8089,'127.0.0.1',()=>console.log('Operator React QA proxy :8089 -> isolated Spring :18088'));
  process.on('SIGINT',()=>server.close());
} else if (command === 'seed') {
  const intake = await request('/api/intakes', { sessionId:'operator-react-qa', inquiryPurpose:'절차 안내',caseType:'CIVIL',partyPosition:'원고',coreStatement:'운영자 React 검수용 가상 대여금 문의입니다.',desiredNextStep:'절차 안내',preferredRegion:'서울특별시' },'POST',201);
  for(let i=0;i<2;i++) await request(`/api/intakes/${intake.id}/search-requests`,{},'POST',201);
  await request(`/api/intakes/${intake.id}/status`,{status:'CONSULTATION_READY'},'PATCH');
  for(const eventType of ['VISIT','INTAKE_STARTED','SUMMARY_VIEWED']) await request('/api/intake-events',{eventId:'operator-react-'+eventType,eventType,sessionId:'operator-react-qa',...(eventType==='SUMMARY_VIEWED'?{intakeId:intake.id}:{})},'POST',201);
  const history=[];
  for(const [userInput,extra] of [['SYNTHETIC_REACT_LOW',{}],['SYNTHETIC_REACT_HIGH_1',{policyViolation:true,externalApiCall:true,retryCount:3}],['SYNTHETIC_REACT_MEDIUM',{policyViolation:true}],['SYNTHETIC_REACT_HIGH_2',{policyViolation:true,externalApiCall:true,retryCount:3}]]) history.push(await request('/api/agent/risk/analyze',{userInput,toolName:'qa_local',...extra}));
  // Authentication failures create real security records without external calls.
  const denied = await fetch(backend+'/actuator/prometheus'); assert.equal(denied.status,401); await denied.arrayBuffer();
  const data={synthetic:true,intakeId:intake.id,history,incidents:await request('/api/incidents'),summary:await request('/api/admin/dashboard/summary'),marketing:await request('/api/admin/dashboard/marketing-funnel')};
  await mkdir(output,{recursive:true});await writeFile(new URL('seed.json',output),JSON.stringify(data,null,2));console.log({history:history.length,incidents:data.incidents.map(i=>({id:i.id,riskHistoryId:i.riskHistoryId})),summary:data.summary});
} else if (command === 'mode') {
  const r=await fetch(proxy+'/__qa__/mode?value='+encodeURIComponent(value),{method:'POST'});assert.equal(r.status,200);console.log(await r.json());
} else if (command === 'fault') {
  const query=new URLSearchParams({kind:value,path:faultPath,method:faultMethod});const r=await fetch(proxy+'/__qa__/scenario?'+query,{method:'POST'});assert.equal(r.status,200);console.log(await r.json());
} else if (command === 'evidence') {
  const r=await fetch(proxy+'/__qa__/traffic');assert.equal(r.status,200);const data=await r.json();data.summary=await request('/api/admin/dashboard/summary');data.incidents=await request('/api/incidents');
  await mkdir(output,{recursive:true});await writeFile(new URL('traffic.json',output),JSON.stringify(data,null,2));console.log({requests:data.traffic.length,summary:data.summary});
} else throw Error('Use serve, seed, mode, fault or evidence');
