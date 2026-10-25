// Three finite local waves. Synthetic risk input, zero LLM/token billing calls.
const base='http://127.0.0.1:18080';
const login=await fetch(base+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'admin',password:'admin123'})}).then(r=>r.json());
if(login.role!=='ROLE_ADMIN')throw new Error('Local demo admin login failed');
for(let i=0;i<3;i++){
  for(let j=0;j<=i;j++){
    const response=await fetch(base+'/api/agent/risk/analyze',{method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer '+login.accessToken},body:JSON.stringify({userInput:'[시연] 시계열용 모의 위험 신호',toolName:'presentation-wave-'+i,retryCount:i+2,policyViolation:true,externalApiCall:i===2,toolError:i===1,approvalRequired:false,dbWrite:false,promptTokens:0,completionTokens:0})});
    if(!response.ok)throw new Error('Synthetic risk wave failed: '+response.status);
  }
  console.log(JSON.stringify({wave:i+1,synthetic:true,realProviderCalls:0}));
  if(i<2)await new Promise(resolve=>setTimeout(resolve,15000));
}
