import test from 'node:test';
import assert from 'node:assert/strict';
import { createIntakeApi } from '../src/client/api.js';
import { authenticate, loginCredentials, loginDestination } from '../src/client/authModel.js';
import { routeFromHash } from '../src/client/model.js';
const json = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
const login = { accessToken: 'candidate-token', username: 'user', role: 'ROLE_USER' };
const profile = { username: 'user', role: 'ROLE_USER' };

test('Screen01 C01 rejects blank credentials before fetch, trims username only, preserves password and sends no old JWT', async t => {
  let captured; let calls = 0;
  t.mock.method(globalThis, 'fetch', async (url, init) => { calls++; captured = { url, init }; return json(login); });
  const api = createIntakeApi({ getToken: () => 'old-token' });
  for (const [name, password] of [['', 'x'], ['  ', 'x'], ['user', ''], ['user', '\t ']]) assert.throws(() => api.login(name, password), error => !!error.fieldErrors);
  assert.equal(calls, 0); await api.login('  user  ', ' password ');
  assert.equal(captured.url, '/api/auth/login'); assert.equal(captured.init.method, 'POST'); assert.equal(captured.init.headers.Authorization, undefined);
  assert.deepEqual(JSON.parse(captured.init.body), { username: 'user', password: ' password ' }); assert.equal(captured.init.credentials, 'omit');
  assert.deepEqual(loginCredentials(' user ', 'x'), { username: 'user', password: 'x' });
});
test('Screen01 P01 validates a candidate token before publishing it, sends Bearer in a header and no body/query', async t => {
  let captured; let unauthorized = 0;
  t.mock.method(globalThis, 'fetch', async (url, init) => { captured = { url, init }; return json(profile); });
  const api = createIntakeApi({ getToken: () => 'old-token', onUnauthorized: () => unauthorized++ });
  assert.deepEqual(await api.currentUser('candidate-token'), profile);
  assert.equal(captured.url, '/api/me'); assert.equal(captured.init.method, 'GET'); assert.equal(captured.init.body, undefined);
  assert.equal(captured.init.headers.Authorization, 'Bearer candidate-token'); assert.equal(unauthorized, 0);
  await api.currentUser(); assert.equal(captured.init.headers.Authorization, 'Bearer old-token');
});
test('Screen01 C01 → P01 accepts only matching server username/role; failed credentials never call P01', async t => {
  const calls = []; const mock = t.mock.method(globalThis, 'fetch', async (url, init) => { calls.push({ url, init }); return json(url.endsWith('/login') ? login : profile); });
  assert.deepEqual(await authenticate(createIntakeApi(), 'user', 'password'), login);
  assert.deepEqual(calls.map(call => call.url), ['/api/auth/login', '/api/me']);
  for (const data of [{ ...profile, username: 'another-user' }, { ...profile, role: 'ROLE_ADMIN' }]) {
    mock.mock.mockImplementation(async url => json(url.endsWith('/login') ? login : data)); await assert.rejects(authenticate(createIntakeApi(), 'user', 'password'), /일치하지 않습니다/);
  }
  let profileCalls = 0; mock.mock.mockImplementation(async url => { if (url === '/api/me') profileCalls++; return json({}, 401); });
  await assert.rejects(authenticate(createIntakeApi(), 'user', 'wrong'), /아이디 또는 비밀번호/); assert.equal(profileCalls, 0);
});
test('Screen01 malformed or unsupported C01/P01 responses cannot become authenticated identities', async t => {
  const mock = t.mock.method(globalThis, 'fetch', async () => json({})); const api = createIntakeApi();
  for (const patch of [{ accessToken: '' }, { accessToken: 'token with spaces' }, { username: ' ' }, { role: 'ROLE_UNKNOWN' }, { role: '__proto__' }]) {
    mock.mock.mockImplementation(async () => json({ ...login, ...patch })); await assert.rejects(api.login('user', 'password'), /응답 형식/);
  }
  for (const data of [[], {}, { username: ' ', role: 'ROLE_USER' }, { username: 'user', role: 'ROLE_UNKNOWN' }]) {
    mock.mock.mockImplementation(async () => json(data)); await assert.rejects(api.currentUser('qa-token'), /응답 형식/);
  }
});
test('Screen01 P01 anonymous preflight, 401/403/500 and late candidate 401 preserve authentication boundaries', async t => {
  let calls = 0; let cleared = 0;
  const mock = t.mock.method(globalThis, 'fetch', async () => { calls++; return json({}, 401); });
  assert.throws(() => createIntakeApi().currentUser(), error => error.status === 401); assert.equal(calls, 0);
  const api = createIntakeApi({ getToken: () => 'current-token', onUnauthorized: () => cleared++ });
  await assert.rejects(api.currentUser('candidate-token'), error => error.status === 401); assert.equal(cleared, 0);
  await assert.rejects(api.currentUser(), error => error.status === 401); assert.equal(cleared, 1);
  for (const status of [403, 500]) { mock.mock.mockImplementation(async () => json({}, status)); await assert.rejects(api.currentUser('candidate-token'), error => error.status === status); }
});
test('Screen01 cancellation during P01 propagates and cannot produce a successful sign-in', async t => {
  let signal;
  t.mock.method(globalThis, 'fetch', async (url, init) => url.endsWith('/login') ? json(login) : new Promise((resolve, reject) => { signal = init.signal; init.signal.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError'))); }));
  const controller = new AbortController(); let ready; const reached = new Promise(resolve => { ready = resolve; });
  const result = authenticate(createIntakeApi(), 'user', 'password', controller.signal, ready);
  await reached; await Promise.resolve(); controller.abort(); await assert.rejects(result, error => error.name === 'AbortError'); assert.equal(signal?.aborted, true);
});
test('Screen01 entry/account routes and role destinations preserve existing inquiry IDs without creating a lawyer role', () => {
  assert.deepEqual(routeFromHash(''), { name: 'login' }); assert.deepEqual(routeFromHash('#/'), { name: 'login' });
  assert.deepEqual(routeFromHash('#/login'), { name: 'login' }); assert.deepEqual(routeFromHash('#/account'), { name: 'account' });
  assert.equal(loginDestination('ROLE_USER'), '/intake/type'); assert.equal(loginDestination('ROLE_ADMIN'), '/account');
  assert.deepEqual(routeFromHash('#/intake/42/search/81'), { name: 'search', id: '42', searchRequestId: '81' });
});
