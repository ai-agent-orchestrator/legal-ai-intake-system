import test from 'node:test';
import assert from 'node:assert/strict';
import { patternSeries, linePath, scrapeState } from '../src/operator/prometheusChart.js';
import { operatorRiskPatterns } from '../src/operator/observabilityMetrics.js';
import { getGuardTimeSeries, getRiskHistory } from '../src/operator/operatorApi.js';
import { createServer } from 'node:http';
import { createObservabilityMock } from '../src/operator/observabilityMock.js';
import { readPatternRisk } from '../src/operator/observabilityMetrics.js';

test('explicit preview has reproducible curves and scores and is labelled mock', () => {
  const preview = createObservabilityMock(Date.parse('2026-10-22T08:00:00Z'));
  assert.equal(preview.timeline.data.query,'MOCK_ONLY');
  assert.equal(preview.timeline.data.series.length,6);
  assert.equal(readPatternRisk(operatorRiskPatterns[0],preview.history.data).total,81);
  assert.equal(readPatternRisk(operatorRiskPatterns[1],preview.history.data).retryPoints,16);
  assert.equal(preview.timeline.data.series[0].values[60][1],'4');
});

test('real matrix keeps fractional increase, observed zero, labels and gaps without fabricating points', () => {
  const data = { start: 100, end: 190, stepSeconds: 15, series: [
    {metric:{signal:'policy',tool:'db'},values:[[100,'0'],[115,'1.25'],[130,'NaN'],[145,'2'],[190,'1']]},
    {metric:{signal:'retry'},values:[[115,'0.5']]},
  ]};
  const rows = patternSeries(operatorRiskPatterns[0],data);
  assert.equal(rows[0].points[0].value,0); assert.equal(rows[0].points[1].value,1.25);
  assert.equal(rows[0].points[2].value,null); assert.equal(rows[0].labels,'tool=db');
  const path = linePath(rows[0].points,data,2);
  assert.equal((path.match(/M/g)||[]).length,3); // NaN and missing scrape steps both split the line.
  assert.equal(patternSeries(operatorRiskPatterns[0],null).length,0);
  assert.equal(scrapeState(data),'수집 대상 미확인');
  assert.equal(scrapeState({...data,series:[{metric:{signal:'scrape_up'},values:[[190,'0']]}]}),'수집 대상 연결 오류');
});

test('range endpoint sends Basic only to Actuator, validates its response and keeps an empty matrix empty', async () => {
  const calls=[];
  const data={start:100,end:190,stepSeconds:15,windowSeconds:300,query:'fixed query',series:[],warnings:[]};
  const server=createServer((req,res)=>{calls.push({path:req.url,auth:req.headers.authorization});res.setHeader('Content-Type','application/json');res.end(JSON.stringify(req.url.includes('guardtimeseries')?data:[]));});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try{
    const base=`http://127.0.0.1:${server.address().port}`;
    assert.deepEqual((await getGuardTimeSeries(base,'test','only-local')).series,[]);
    await getRiskHistory(base);
    assert.equal(calls[0].path,'/actuator/guardtimeseries');assert.match(calls[0].auth,/^Basic /);assert.equal(calls[1].auth,undefined);
    data.series=[{metric:{signal:'policy'},values:[[100,{}]]}];
    await assert.rejects(getGuardTimeSeries(base,'test','only-local'),/응답 필드/);
  }finally{server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}
});
