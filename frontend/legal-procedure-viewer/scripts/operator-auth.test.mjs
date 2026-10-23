import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import * as api from '../src/operator/operatorApi.js';

const json = (res, data, status = 200) => { res.statusCode = status; res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(data)); };
async function fixture(run, overrides = {}) {
  const calls = [];
  const server = createServer((req, res) => {
    calls.push({ path: req.url, authorization: req.headers.authorization });
    if (overrides.handle?.(req, res)) return;
    if (req.url === '/api/auth/login') json(res, { accessToken: 'qa-admin-token', username: 'qa-admin', role: overrides.loginRole || 'ROLE_ADMIN' });
    else if (req.url === '/api/me') json(res, { username: overrides.profileName || 'qa-admin', role: 'ROLE_ADMIN' });
    else json(res, []);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(`http://127.0.0.1:${server.address().port}`, calls); }
  finally { api.clearOperatorSession(); server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
}

test('operator rejects missing login locally without sending an anonymous business request', async () => {
  await fixture(async (base, calls) => {
    await assert.rejects(api.getIncidents(base), error => error.status === 401);
    assert.equal(calls.length, 0);
  });
});

test('ADMIN login checks P01 then sends Bearer only to the matching server', async () => {
  await fixture(async (base, calls) => {
    await api.loginOperator(base, 'qa-admin', 'local-password');
    await api.getIncidents(base);
    assert.deepEqual(calls.map(row => row.authorization), [undefined, 'Bearer qa-admin-token', 'Bearer qa-admin-token']);
    await assert.rejects(api.getIncidents(base + '/other-server'), error => error.status === 401);
    assert.equal(calls.length, 3);
  });
});

test('non-ADMIN login and mismatched P01 cannot establish an operator session', async () => {
  for (const overrides of [{ loginRole: 'ROLE_USER' }, { loginRole: 'ROLE_LAWYER' }, { profileName: 'someone-else' }]) {
    await fixture(async base => {
      await assert.rejects(api.loginOperator(base, 'qa-admin', 'local-password'));
      assert.equal(api.operatorSession(base), null);
    }, overrides);
  }
});

test('business 401/403 clear the session, while Actuator 401 keeps ADMIN JWT alive', async () => {
  for (const code of [401, 403]) await fixture(async base => {
    await api.loginOperator(base, 'qa-admin', 'local-password');
    await assert.rejects(api.getPrometheusMetrics(base, 'bad-basic', 'local-password'), error => error.status === 401);
    assert.ok(api.operatorSession(base));
    await assert.rejects(api.getIncidents(base), error => error.status === code);
    assert.equal(api.operatorSession(base), null);
  }, { handle(req, res) {
    if (req.url.includes('actuator')) { json(res, {}, 401); return true; }
    if (req.url === '/api/incidents') { json(res, {}, code); return true; }
  } });
});

test('logout cancels pending business reads and does not retry or retain their result', async () => {
  let pending;
  await fixture(async base => {
    await api.loginOperator(base, 'qa-admin', 'local-password');
    const read = api.getIncidents(base);
    const rejected = assert.rejects(read, /취소/);
    while (!pending) await new Promise(resolve => setTimeout(resolve, 1));
    api.clearOperatorSession();
    await rejected; assert.equal(api.operatorSession(base), null);
  }, { handle(req, res) { if (req.url === '/api/incidents') { pending = res; return true; } } });
});

test('a late rejection from an older session cannot invalidate a newer login', async () => {
  let pending;
  await fixture(async base => {
    await api.loginOperator(base, 'qa-admin', 'local-password');
    const read = api.getIncidents(base); const rejected = assert.rejects(read, error => error.status === 401);
    while (!pending) await new Promise(resolve => setTimeout(resolve, 1));
    const next = await api.loginOperator(base, 'qa-admin', 'local-password');
    json(pending, {}, 401); await rejected;
    assert.equal(api.operatorSession(base), next);
  }, { handle(req, res) { if (req.url === '/api/incidents') { pending = res; return true; } } });
});
