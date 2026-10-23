import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import * as api from '../src/operator/operatorApi.js';
import { canChangeIncident, changeIncidentAndReload, filterIncidents, incidentId, riskId, riskQuery } from '../src/operator/auditModel.js';

async function withServer(handler, run) {
  const server = createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}`); }
  finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}
const json = (res, data) => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(data)); };
const summary = openIncidentCount => ({ agentRunCount: 0, riskHistoryCount: 0, securityEventCount: 0, openIncidentCount,
  riskSummary: { total: 0, high: 0, medium: 0, low: 0, decisionCounts: {}, recommendedActionCounts: {} } });

test('risk query sends exactly one supported server condition; Incident filters combine status and severity', () => {
  assert.equal(riskQuery('riskLevel', 'ALL'), '');
  assert.equal(riskQuery('riskLevel', 'HIGH'), 'riskLevel=HIGH');
  assert.equal(riskQuery('decision', 'SAFE'), 'decision=SAFE');
  assert.throws(() => riskQuery('decision', 'BLOCK'), /조회 조건/);
  const rows = [{ id: 1, status: 'OPEN', severity: 'HIGH' }, { id: 2, status: 'RESOLVED', severity: 'HIGH' }, { id: 3, status: 'OPEN', severity: 'MEDIUM' }];
  assert.deepEqual(filterIncidents(rows, { status: 'OPEN', severity: 'HIGH' }).map(row => row.id), [1]);
  assert.equal(filterIncidents(rows, { status: 'ALL', severity: 'LOW' }).length, 0);
});

test('closed incidents cannot be acknowledged again; ID prefixes are presentation only', () => {
  assert.equal(canChangeIncident({ id: 42, status: 'OPEN' }, 'ack'), true);
  assert.equal(canChangeIncident({ id: 42, status: 'ACKNOWLEDGED' }, 'ack'), false);
  assert.equal(canChangeIncident({ id: 42, status: 'ACKNOWLEDGED' }, 'resolve'), true);
  assert.equal(canChangeIncident({ id: 42, status: 'RESOLVED' }, 'ack'), false);
  assert.equal(canChangeIncident({ id: 42, status: 'RESOLVED' }, 'resolve'), false);
  assert.equal(canChangeIncident({ id: 'INC-00042', status: 'OPEN' }, 'ack'), false);
  assert.equal(incidentId(42), 'INC-00042'); assert.equal(riskId(99), 'RH-00099');
});

test('ack and resolve use Incident ID, not riskHistoryId, then reread C18 and C12', async () => {
  const calls = []; let incident = { id: 42, riskHistoryId: 99, status: 'OPEN' };
  await withServer(async (req, res) => {
    let body = ''; for await (const chunk of req) body += chunk;
    calls.push({ url: req.url, method: req.method, body });
    if (req.method === 'PATCH') { incident = { ...incident, status: req.url.endsWith('/ack') ? 'ACKNOWLEDGED' : 'RESOLVED' }; json(res, incident); }
    else json(res, req.url === '/api/incidents' ? [incident] : summary(incident.status === 'OPEN' ? 1 : 0));
  }, async base => {
    const refreshIncidents = async () => ({ ok: true, data: await api.getIncidents(base) });
    const refreshSummary = async () => ({ ok: true, data: await api.getAdminDashboardSummary(base) });
    for (const action of ['ack', 'resolve']) assert.deepEqual(await changeIncidentAndReload({ base, incident, action, refreshIncidents, refreshSummary }), { saved: true, error: null, refreshed: true });
    assert.deepEqual(calls.map(call => `${call.method} ${call.url}`), [
      'PATCH /api/incidents/42/ack', 'GET /api/incidents', 'GET /api/admin/dashboard/summary',
      'PATCH /api/incidents/42/resolve', 'GET /api/incidents', 'GET /api/admin/dashboard/summary',
    ]);
    assert.ok(calls.every(call => call.body === ''));
    assert.equal(incident.status, 'RESOLVED'); assert.equal(incident.riskHistoryId, 99);
  });
});

test('lost PATCH response triggers reads without retrying the write', async () => {
  const calls = []; let status = 'OPEN';
  await withServer((req, res) => {
    calls.push(`${req.method} ${req.url}`);
    if (req.method === 'PATCH') { status = 'ACKNOWLEDGED'; res.destroy(); }
    else json(res, req.url === '/api/incidents' ? [{ id: 42, status }] : summary(0));
  }, async base => {
    const result = await changeIncidentAndReload({ base, incident: { id: 42, status: 'OPEN' }, action: 'ack',
      refreshIncidents: async () => ({ ok: true, data: await api.getIncidents(base) }),
      refreshSummary: async () => ({ ok: true, data: await api.getAdminDashboardSummary(base) }),
    });
    assert.equal(result.saved, false); assert.equal(result.refreshed, true); assert.ok(result.error);
    assert.deepEqual(calls, ['PATCH /api/incidents/42/ack', 'GET /api/incidents', 'GET /api/admin/dashboard/summary']);
  });
});

test('a saved PATCH and a failed summary refresh are reported separately', async () => {
  await withServer((req, res) => json(res, { id: 42, status: 'RESOLVED' }), async base => {
    const result = await changeIncidentAndReload({ base, incident: { id: 42, status: 'ACKNOWLEDGED' }, action: 'resolve',
      refreshIncidents: async () => ({ ok: true }), refreshSummary: async () => ({ ok: false }),
    });
    assert.deepEqual(result, { saved: true, error: null, refreshed: false });
  });
});
