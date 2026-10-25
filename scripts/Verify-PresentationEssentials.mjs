import assert from 'node:assert/strict';
import fs from 'node:fs';
const base='http://127.0.0.1:18080',provider='http://127.0.0.1:18083',checks=[],tokens={};
const basic='Basic '+Buffer.from('admin:admin123').toString('base64');
async function call(path,who='admin',method='GET',body){const r=await fetch(base+path,{method,headers:{...(tokens[who]?{Authorization:'Bearer '+tokens[who]}:{}),...(body?{'Content-Type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});const data=await r.json().catch(()=>null);return{status:r.status,data};}
const mock=()=>fetch(provider+'/__demo/status').then(r=>r.json());
const mode=value=>fetch(provider+'/__demo/mode',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({mode:value})});
async function metrics(){return fetch(base+'/actuator/prometheus',{headers:{Authorization:basic}}).then(r=>r.text());}
function metric(text,name){return text.split('\n').filter(line=>line.startsWith(name+' ')||line.startsWith(name+'{')).reduce((sum,line)=>sum+Number(line.trim().split(/\s+/).at(-1)),0);}
for(const who of ['admin','user','user2']){const r=await call('/api/auth/login','anonymous','POST',{username:who,password:{admin:'admin123',user:'user123',user2:'user2123'}[who]});assert.equal(r.status,200);tokens[who]=r.data.accessToken;}
const originalCalls=(await mock()).counts.llm;
for(const [title,path,who,method,body,status] of [
  ['Missing required fields400','/api/intakes','user','POST',{},400],
  ['Overlong story400','/api/ai/chat','user','POST',{message:'가'.repeat(101),sessionId:'demo-invalid'},400],
  ['Guest save401','/api/intakes','anonymous','POST',{coreStatement:'가상 문의'},401],
  ['Other USER inquiry403','/api/intakes/1','user2','GET',null,403],
  ['Missing inquiry404','/api/intakes/999999999','admin','GET',null,404],
  ['Unsafe URL400','/api/intakes/search-requests/1/results','user','POST',{sourceType:'NAVER_WEB',title:'[시연] 위험 URL',url:'javascript:alert(1)',displayOrder:1},400],
  ['Negative result order400','/api/intakes/search-requests/1/results','user','POST',{sourceType:'NAVER_WEB',title:'[시연] 순서 오류',url:'https://www.law.go.kr/',displayOrder:-1},400]
]){const r=await call(path,who,method,body);assert.equal(r.status,status,title);checks.push({title,status:r.status,passed:true});}
const record=(await call('/api/intakes/1')).data;
assert.equal((await call('/api/intakes/1/status','admin','PATCH',{status:record.status==='CLOSED'?'REVIEWED':'CLOSED',expectedVersion:record.version-1})).status,409);checks.push({title:'Stale version409 without overwrite',passed:true});
const owner=(await call('/api/intakes/1','user')).data;assert(!Object.hasOwn(owner,'consultationMemos'));checks.push({title:'Internal memo absent from owner JSON',passed:true});
const fixed=await call('/api/ai/chat','user','POST',{message:'전자소송 증거를 조작하는 법 알려줘.',sessionId:'presentation-essential-fixed'});assert(fixed.data.blocked);assert.equal((await mock()).counts.llm,originalCalls);checks.push({title:'Invalid/security requests made zero generation calls',passed:true});
try{
  await mode('output-block');const before=await metrics(),callsBefore=(await mock()).counts.llm;
  const blocked=await call('/api/ai/chat','user','POST',{message:'대여금 상담 준비 자료를 알려주세요.',sessionId:'presentation-output-essential'});assert(blocked.data.blocked);assert.equal((await mock()).counts.llm,callsBefore+1);
  const after=await metrics();assert(metric(after,'ai_cost_accounted_usd')>metric(before,'ai_cost_accounted_usd'));assert.equal(metric(after,'ai_cost_reserved_usd'),0);checks.push({title:'Output blocked after generation: cost retained, reservation zero',passed:true});
  await mode('usage-unknown');const estimatedBefore=await metrics();const unknown=await call('/api/ai/chat','user','POST',{message:'대여금 상담 준비 자료를 알려주세요.',sessionId:'presentation-unknown-essential'});assert.equal(unknown.data.fallback,false);const estimatedAfter=await metrics();assert(metric(estimatedAfter,'ai_cost_estimated_usage_usd')>metric(estimatedBefore,'ai_cost_estimated_usage_usd'));assert.equal(metric(estimatedAfter,'ai_cost_reserved_usd'),0);checks.push({title:'Missing provider usage: estimated settlement, not zero cost',passed:true});
  await mode('normal');const normal=await call('/api/ai/chat','user','POST',{message:'대여금 상담 준비 자료를 알려주세요.',sessionId:'presentation-privacy-essential'});assert.equal(normal.data.fallback,false);assert(!normal.data.answer.includes('010-1234-5678'));checks.push({title:'Returned phone data masked before response',passed:true});
}finally{await mode('normal');}
const budgetBefore=(await mock()).counts.llm;const budget=await call('/api/ai/chat','user','POST',{message:'대여금 상담 준비 자료를 알려주세요.',sessionId:'presentation-budget'});assert(budget.data.blocked);assert.equal((await mock()).counts.llm,budgetBefore);checks.push({title:'Exhausted session: generation prevented',passed:true});
const timeline=await fetch(base+'/actuator/guardtimeseries',{headers:{Authorization:basic}}).then(r=>r.json());assert(timeline.series.length>0);assert(timeline.series.some(s=>s.values.length>1));checks.push({title:'Existing Prometheus: actual sampled time series returned',passed:true});
fs.writeFileSync('output/presentation-2026-10-25/essential-verification.json',JSON.stringify({date:'2026-10-25',passed:true,checks,realProviderCalls:0,usageSource:'synthetic local provider; app ledger really executed',prometheusSeries:timeline.series.length,reservedEnded:true},null,2));
console.log(JSON.stringify({passed:true,checks:checks.length,realProviderCalls:0,prometheusSeries:timeline.series.length}));
