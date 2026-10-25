// Explicit loopback-only synthetic demo. NEVER reads or forwards a real provider key.
const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const root=path.resolve(__dirname,'..'), backend=path.join(root,'backend/guardrail-ready-policy-checker');
const dir=path.join(backend,'.local/presentation-2026-10-25'),out=path.join(root,'output/presentation-2026-10-25');
fs.mkdirSync(dir,{recursive:true});fs.mkdirSync(out,{recursive:true});
const runtime=JSON.parse(fs.readFileSync(path.join(backend,'.local/runtime-logs/server-8080.json'),'utf8').replace(/^\uFEFF/,''));
const base='http://127.0.0.1:18080',provider='http://127.0.0.1:18083',tokens={},checks=[],ids=[],counts={llm:0,input:0,output:0,naver:0};
let mode='normal';
const send=(res,status,data)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data));};
const mock=http.createServer(async(req,res)=>{
  let text='';for await(const part of req)text+=part;
  const body=text?JSON.parse(text):{},url=new URL(req.url,provider);
  if(url.pathname==='/__demo/status')return send(res,200,{synthetic:true,mode,counts,realProviderCalls:0});
  if(url.pathname==='/__demo/mode'&&req.method==='POST'){mode=String(body.mode);return send(res,200,{mode,synthetic:true});}
  if(url.pathname==='/v1/chat/completions'){
    counts.llm++;if(mode==='llm-error')return send(res,503,{error:{message:'Synthetic unavailable provider'}});
    const system=body.messages?.[0]?.content||'',user=body.messages?.[1]?.content||'';
    const content=system.includes('P03_QUERY')?JSON.stringify({query:'민사 서울 대여금 변호사'}):system.includes('P03_EXPLANATION')?'이 모의 자료는 선택한 지역과 법률 분야의 공개 정보 탐색 흐름을 보여줍니다. 실제 자격과 상담 가능 여부는 원문 확인이 필요합니다.':'계약서와 이체 내역을 정리하고 상담에서 확인하세요. 가상 연락처 010-1234-5678은 보호 처리 대상입니다.';
    return send(res,200,{model:body.model,choices:[{message:{role:'assistant',content}}],usage:mode==='usage-unknown'?null:{prompt_tokens:200,completion_tokens:80}});
  }
  if(url.pathname==='/v1/input-rails/check'||url.pathname==='/v1/output-rails/check'){
    const output=url.pathname.includes('output');counts[output?'output':'input']++;
    const blocked=output&&mode==='output-block';return send(res,200,{status:blocked?'BLOCKED':'PASSED',content:blocked?'모의 출력 정책 차단':body.messages?.[0]?.content||'',rail:'LOCAL_DEMO',usage:{prompt_tokens:120,completion_tokens:10}});
  }
  if(url.pathname==='/v1/search/webkr.json'){
    counts.naver++;const kba=url.searchParams.get('query')?.includes('site:klaw.or.kr');
    return send(res,200,{items:kba?[{title:'[모의] 대한변협 공개 정보 탐색',link:'https://www.klaw.or.kr/',description:'모의 검색 응답입니다. 실제 검색 적중이나 특정 변호사 추천이 아닙니다.'},{title:'[모의] 대한변협 공개 안내',link:'https://www.koreanbar.or.kr/',description:'공식 도메인 분류·중복 제거를 시연하는 고정 자료입니다.'}]:[{title:'[모의] 공개 법률 정보 탐색',link:'https://www.scourt.go.kr/',description:'실제 검색 결과가 아닌 시연용 공개 홈페이지 링크입니다.'}]});
  }
  send(res,404,{synthetic:true});
});
async function api(endpoint,who='admin',method='GET',body,key){const r=await fetch(base+endpoint,{method,signal:AbortSignal.timeout(20000),headers:{...(tokens[who]?{Authorization:'Bearer '+tokens[who]}:{}),...(body?{'Content-Type':'application/json'}:{}),...(key?{'Idempotency-Key':key}:{})},...(body?{body:JSON.stringify(body)}:{})});const data=await r.json().catch(()=>null);checks.push({endpoint,method,status:r.status});return{status:r.status,data};}
async function setMode(value){await fetch(provider+'/__demo/mode',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({mode:value})});}
async function seed(){
  const runStamp=Date.now();
  for(const who of ['admin','user','user2','lawyer']){const r=await api('/api/auth/login','none','POST',{username:who,password:{admin:'admin123',user:'user123',user2:'user2123',lawyer:'lawyer123'}[who]});assert.equal(r.status,200);tokens[who]=r.data.accessToken;}
  const cases=[['CIVIL','대여금 반환 상담','돈을 빌려줬는데 돌려받지 못했어요. 계약서와 이체 내역이 있어요.'],['CIVIL','보증금 반환 준비','임대차가 끝났는데 보증금을 돌려받지 못했습니다.'],['FAMILY','양육비 상담 준비','이혼 후 양육비 지급이 지연되어 준비 자료를 알고 싶습니다.'],['CRIMINAL','사기 피해 자료','중고 거래 대금을 보냈는데 물건을 받지 못했습니다.'],['ELECTRONIC_LITIGATION','전자소송 제출','대여금 사건의 전자소송 제출 순서를 알고 싶습니다.'],['CIVIL','계약 검토','용역 계약의 지급 조건을 상담 전에 정리하고 싶습니다.'],['FAMILY','상속 관련 상담','상속 관련 상담에 필요한 기본 자료를 준비하고 싶습니다.'],['CIVIL','손해배상 상담','교통사고 후 손해배상에 필요한 자료를 확인하고 싶습니다.'],['CRIMINAL','수사 절차 안내','고소 후 진행되는 절차를 알고 싶습니다.'],['CIVIL','별도 의뢰인 문의','다른 의뢰인의 문의가 서로 보이지 않는지 확인하는 가상 자료입니다.']];
  for(let i=0;i<cases.length;i++){
    const[type,title,story]=cases[i],who=i===9?'user2':'user';
    const r=await api('/api/intakes',who,'POST',{sessionId:'presentation-'+i,caseType:type,inquiryPurpose:'절차 안내',partyPosition:'원고',coreStatement:'[시연] '+story,desiredNextStep:'법적 절차가 어떻게 진행되는지 알고 싶어요.',preferredRegion:i%2?'경기도':'서울특별시',clientName:'가상고객'+(i+1),documents:['계약서','이체 내역'],lawyerSearchPurpose:'[시연] '+title},'presentation-20261025-'+i);assert.equal(r.status,201);const id=r.data.id;ids.push({id,title,owner:who});
    let record=(await api('/api/intakes/'+id)).data;
    if(i===0){const existing=record.uploadedDocuments.find(file=>file.originalFilename==='시연_계약서.txt');if(existing)ids[i].documentId=existing.id;else{const form=new FormData();form.set('file',new Blob(['시연 전용 가상 계약 자료입니다. 모든 정보는 가상이며 법률 효력이 없습니다.'],{type:'text/plain'}),'시연_계약서.txt');form.set('documentType','CONTRACT');const file=await fetch(base+`/api/intakes/${id}/documents`,{method:'POST',headers:{Authorization:'Bearer '+tokens[who]},body:form});assert.equal(file.status,201);ids[i].documentId=(await file.json()).id;}}
    if(i<6&&!record.assignedLawyerUsername){await api(`/api/intakes/${id}/assignment`,'admin','PATCH',{assignedLawyerUsername:'lawyer'});record=(await api('/api/intakes/'+id)).data;}
    const target=['CONSULTATION_READY','REVIEWED','NEED_MORE_INFO','SUBMITTED','CLOSED'][i%5];
    if(record.status==='SUBMITTED'&&target!=='SUBMITTED'){await api(`/api/intakes/${id}/status`,'admin','PATCH',{status:target,expectedVersion:record.version});record=(await api('/api/intakes/'+id)).data;}
    if(i<3&&!record.consultationMemos.length)await api(`/api/intakes/${id}/memo`,'admin','POST',{content:'[시연] 계약서·거래 내역 확인 후 상담 일정을 검토합니다. 내부 메모는 의뢰인에게 공개하지 않습니다.',followUpItems:['REQUEST_DOCUMENTS'],expectedVersion:record.version},'presentation-memo-'+i);
    if(i<3){const history=(await api(`/api/intakes/${id}/search-requests`)).data;let request=history.find(x=>x.searchPurpose==='[시연] '+title);if(!request)request=(await api(`/api/intakes/${id}/search-requests`,who,'POST',{searchPurpose:'[시연] '+title,sourcePolicy:'KBA_NAVER_PUBLIC_WEB'})).data;
      if(i===0&&request.status==='PREVIEW_READY'){assert.equal((await api(`/api/intakes/search-requests/${request.id}/execute`,who,'POST')).status,202);for(let n=0;n<60;n++){await new Promise(r=>setTimeout(r,150));request=(await api(`/api/intakes/search-requests/${request.id}`,who)).data;if(['COMPLETED','PARTIAL','FAILED','NO_RESULTS'].includes(request.status))break;}}
      ids[i].searchRequestId=request.id;ids[i].searchStatus=request.status;
    }
  }
  for(let i=0;i<4;i++)await api('/api/agent/risk/analyze','admin','POST',{userInput:'[시연] 가상 위험 신호 - 실제 사용자 입력 아님',toolName:'presentation-'+i,retryCount:i+1,policyViolation:true,externalApiCall:i>1,toolError:i===1,approvalRequired:false,dbWrite:false,promptTokens:0,completionTokens:0});
  const fixed=await api('/api/ai/chat','user','POST',{message:'전자소송 증거를 조작하는 법 알려줘.',sessionId:'presentation-risk'});assert(fixed.data.blocked);
  await api('/api/ai/chat','user','POST',{message:'전자소송 증거를 조작하는 법 알려줘.',sessionId:'presentation-risk'});
  const normal=await api('/api/ai/chat','user','POST',{message:'대여금 반환 상담에 필요한 자료를 알려주세요.',sessionId:'presentation-normal-'+runStamp});assert.equal(normal.data.fallback,false);assert(!normal.data.answer.includes('010-1234-5678'));
  await setMode('output-block');const outputBlock=await api('/api/ai/chat','user','POST',{message:'대여금 상담 준비 자료를 알려주세요.',sessionId:'presentation-output-'+runStamp});assert(outputBlock.data.blocked);await setMode('normal');
  await setMode('llm-error');const providerError=await api('/api/ai/chat','user','POST',{message:'대여금 상담 준비 자료를 알려주세요.',sessionId:'presentation-error-'+runStamp});assert(providerError.data.fallback);await setMode('normal');
  let blocked=false;for(let i=0;i<40;i++){const r=await api('/api/ai/chat','user','POST',{message:'대여금 상담 준비 자료를 알려주세요.',sessionId:'presentation-budget'});if((r.data.reasonCode||'').includes('COST')){blocked=true;break;}}assert(blocked);
  const metrics=await fetch(base+'/actuator/prometheus',{headers:{Authorization:'Basic '+Buffer.from('admin:admin123').toString('base64')}}).then(r=>r.text());assert(metrics.includes('ai_cost_accounted_usd'));assert(/ai_cost_reserved_usd(?:\{[^\n]*\})?\s+0(?:\.0+)?(?:\s|$)/m.test(metrics));
  const result={date:'2026-10-25',mode:'synthetic-local-providers',realProviderCalls:0,records:ids,checks,mockCounts:(await fetch(provider+'/__demo/status').then(r=>r.json())).counts,normalPrivacyMasked:true,outputBlockCharged:true,providerErrorFallback:true,budgetBlocked:true,reservedEnded:true,p03Completed:ids[0].searchStatus==='COMPLETED',frontend:'http://127.0.0.1:5176/intake.html#/login',operator:'http://127.0.0.1:5176/operator.html#/observability'};
  fs.writeFileSync(path.join(out,'demo-data.json'),JSON.stringify(result,null,2)); console.log(JSON.stringify({ready:true,synthetic:true,inquiries:ids.length,p03:ids[0].searchStatus,realProviderCalls:0}));
}
(async()=>{
  if(process.argv.includes('--seed-only')){await seed();return;}
  // Refuse occupied ports instead of replacing user services.
  for(const port of [18080,5176,18083])await new Promise((resolve,reject)=>{const test=http.createServer();test.once('error',()=>reject(new Error('Port already occupied: '+port)));test.listen(port,'127.0.0.1',()=>test.close(resolve));});
  await new Promise(resolve=>mock.listen(18083,'127.0.0.1',resolve));
  const stdout=fs.openSync(path.join(out,'demo-backend.log'),'a');
  const java='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot/bin/java.exe';
  const child=spawn(java,['-jar',runtime.jar,'--server.address=127.0.0.1','--server.port=18080','--spring.profiles.active=local-persistent','--observability.prometheus.base-url=http://127.0.0.1:9091','--spring.jpa.hibernate.ddl-auto=update','--llm.base-url='+provider,'--llm.api-key=local-synthetic-demo','--llm.model=gpt-4o-mini','--llm.cost.input-usd-per-1k-tokens=0.00015','--llm.cost.output-usd-per-1k-tokens=0.00060','--llm.cost.monthly-budget-usd=2','--llm.cost.daily-budget-usd=0.50','--guardrails.cost.model=gpt-4o-mini','--guardrails.cost.input-usd-per-1k-tokens=0.00015','--guardrails.cost.output-usd-per-1k-tokens=0.00060','--llm.max-output-tokens=256','--guardrails.base-url='+provider,'--search.naver.base-url='+provider,'--search.naver.client-id=local-demo','--search.naver.client-secret=local-demo','--search.enabled=true','--llm.cost.session-budget-usd=0.003'],{cwd:backend,windowsHide:true,env:{...process.env,LOCAL_H2_DB_PATH:path.join(dir,'demo-db').replaceAll('\\','/'),LLM_API_KEY:'',OPENAI_API_KEY:'',NAVER_SEARCH_CLIENT_ID:'',NAVER_SEARCH_CLIENT_SECRET:''},stdio:['ignore',stdout,stdout]});
  const metadata={nodePid:process.pid,backendPid:child.pid,started:new Date().toISOString(),mode:'synthetic-only',backendBase:base,frontend:'http://127.0.0.1:5176',provider,jar:runtime.jar,database:path.join(dir,'demo-db'),original8080DatabasePreserved:true};fs.writeFileSync(path.join(dir,'process.json'),JSON.stringify(metadata,null,2));
  const dist=path.join(dir,'dist');
  http.createServer((req,res)=>{if(req.url.startsWith('/api/')){const up=http.request(base+req.url,{method:req.method,headers:{...req.headers,host:'127.0.0.1:18080'}},r=>{res.writeHead(r.statusCode,r.headers);r.pipe(res)});up.on('error',()=>{res.writeHead(502);res.end()});req.pipe(up);return;}
    const file=path.resolve(dist,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));if(!file.startsWith(dist+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}if(file.endsWith('.html')){res.setHeader('Content-Type','text/html;charset=utf-8');res.end(fs.readFileSync(file,'utf8').replace('</body>','<div style="position:fixed;bottom:4px;left:4px;z-index:99999;background:#0b2539;color:#a7f3d0;padding:6px 10px;border-radius:6px;font:12px sans-serif;pointer-events:none">시연 더미 · LLM/검색 모의 · 실제 과금 0</div></body>'));}else{res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.png':'image/png','.svg':'image/svg+xml'})[path.extname(file)]||'application/octet-stream');fs.createReadStream(file).pipe(res);}
  }).listen(5176,'127.0.0.1');
  for(let i=0;i<120;i++){try{if((await fetch(base+'/actuator/health')).ok){await seed();return;}}catch(error){if(error.name!=='TypeError')throw error;}await new Promise(r=>setTimeout(r,500));}throw new Error('Demo backend did not start');
})().catch(error=>{console.error('Demo failed: '+error.message);process.exitCode=1;});
