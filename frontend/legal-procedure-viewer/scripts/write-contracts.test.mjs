import test from 'node:test';
import assert from 'node:assert/strict';
import { createIntakeApi } from '../src/client/api.js';
import { prepareWriteAttempt, serverProblem } from '../src/shared/writeContracts.js';
import { testIntake, testMemo } from './intake-test-data.mjs';
const json = (data, status = 200, headers = {}) => new Response(JSON.stringify(data), {status,headers:{'Content-Type':'application/json',...headers}});

test('logical write survives retry; new identity or confirmed new intent uses a different key', () => {
  const first=prepareWriteAttempt(null,'user',{story:'가상 문의'});
  assert.equal(prepareWriteAttempt(first,'user',{story:'가상 문의'}),first);
  assert.notEqual(prepareWriteAttempt(first,'other',{story:'가상 문의'}).key,first.key);
  first.uncertain=true;
  assert.throws(()=>prepareWriteAttempt(first,'user',{story:'변경 내용'}),e=>e.code==='UNCERTAIN_WRITE_CHANGED');
  assert.notEqual(prepareWriteAttempt(null,'user',{story:'가상 문의'}).key,first.key);
});

test('C03 sends request key only on that write and reads the actual replay header', async t => {
  const calls=[];
  t.mock.method(globalThis,'fetch',async(url,init)=>{calls.push({url,init});return url==='/api/intakes'?json({id:42},201,{'Idempotent-Replayed':'true'}):json(testIntake());});
  const api=createIntakeApi({getToken:()=>'test-token'});
  const result=await api.create({story:'가상'},undefined,{idempotencyKey:'retry-key-0001'});
  assert.equal(result.id,42);assert.equal(result.replayed,true);
  await api.intake(42);
  assert.equal(calls[0].init.headers['Idempotency-Key'],'retry-key-0001');
  assert.equal(calls[1].init.headers['Idempotency-Key'],undefined);
});

test('C10 and C11 send version zero, preserve real IDs and accept returned versions', async t=>{
  const calls=[];
  t.mock.method(globalThis,'fetch',async(url,init)=>{calls.push({url,init});return url.endsWith('/status')?json({id:42,previousStatus:'SUBMITTED',status:'REVIEWED',changed:true,version:1}):json(testMemo(42,301,{intakeVersion:2}),201,{'Idempotent-Replayed':'false'});});
  const api=createIntakeApi();
  assert.equal((await api.updateStatus(42,'REVIEWED',undefined,{expectedVersion:0})).version,1);
  const memo=await api.createMemo(42,'가상 메모',[],undefined,{idempotencyKey:'memo-key-0001',expectedVersion:1});
  assert.deepEqual(JSON.parse(calls[0].init.body),{status:'REVIEWED',expectedVersion:0});
  assert.deepEqual(JSON.parse(calls[1].init.body),{content:'가상 메모',followUpItems:[],expectedVersion:1});
  assert.equal(calls[1].init.headers['Idempotency-Key'],'memo-key-0001');assert.equal(memo.intakeVersion,2);assert.equal(memo.replayed,false);
});

test('invalid keys and versions fail before HTTP; requested concurrency cannot accept missing response version', async t=>{
  let calls=0;
  const mocked=t.mock.method(globalThis,'fetch',async()=>{calls++;return json({id:42,previousStatus:'SUBMITTED',status:'REVIEWED',changed:true});});
  const api=createIntakeApi();
  for(const value of [-1,NaN,Infinity,'0',null]) assert.throws(()=>api.updateStatus(42,'REVIEWED',undefined,{expectedVersion:value}));
  for(const key of ['', 'short', 'bad key-0001', 'x'.repeat(101)]) assert.throws(()=>api.create({},undefined,{idempotencyKey:key}));
  assert.equal(calls,0);
  await assert.rejects(api.updateStatus(42,'REVIEWED',undefined,{expectedVersion:0}),/응답 형식/);
  mocked.mock.mockImplementation(async()=>json(testMemo(42,301,{intakeVersion:null}),201));
  await assert.rejects(api.createMemo(42,'메모',[],undefined,{expectedVersion:0,idempotencyKey:'memo-key-0002'}),/응답 형식/);
});

test('structured 409 keeps safe state/version and never displays provider or user-input dumps', async t=>{
  t.mock.method(globalThis,'fetch',async()=>json({code:'INTAKE_VERSION_CONFLICT',message:'SECRET RAW TEXT',details:{intakeId:42,currentStatus:'REVIEWED',currentVersion:0,expectedVersion:0,userInput:'SECRET RAW TEXT',accessToken:'SECRET TOKEN'}},409));
  await assert.rejects(createIntakeApi().updateStatus(42,'CLOSED',undefined,{expectedVersion:0}),error=>{
    assert.equal(error.code,'INTAKE_VERSION_CONFLICT');assert.match(error.message,/다른 담당자/);
    assert.deepEqual(error.details,{intakeId:42,currentVersion:0,expectedVersion:0,currentStatus:'REVIEWED'});
    assert(!JSON.stringify(error).includes('SECRET'));return true;
  });
  assert.deepEqual(serverProblem({code:'<script>',details:{currentVersion:-1,currentStatus:{bad:true},incidentId:'INC-1'}},409),{code:'',details:{},message:undefined});
});

test('invalid replay acknowledgement and malformed present version remain uncertain responses', async t=>{
  const mock=t.mock.method(globalThis,'fetch',async()=>json({id:42},201,{'Idempotent-Replayed':'yes'}));
  const api=createIntakeApi();
  await assert.rejects(api.create({},undefined,{idempotencyKey:'retry-key-0002'}),/재전송/);
  for(const version of [-1,'0',null]){mock.mock.mockImplementation(async()=>json(testIntake(42,{version})));await assert.rejects(api.consultationIntake(42),/응답 형식/);}
});
