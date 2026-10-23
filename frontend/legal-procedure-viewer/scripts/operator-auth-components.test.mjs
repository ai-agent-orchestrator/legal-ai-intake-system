import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { createServer } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';
let vite, Gate, api;
const base = 'http://localhost:8080';
before(async () => {
  vite = await createServer({ configFile: false, root: fileURLToPath(new URL('../', import.meta.url)), plugins: [react()], server: { middlewareMode: true }, appType: 'custom' });
  Gate = (await vite.ssrLoadModule('/src/operator/OperatorAuthGate.jsx')).default;
  api = await vite.ssrLoadModule('/src/operator/operatorApi.js');
});
after(async () => { await vite?.close(); });
const text = node => typeof node === 'string' ? node : (node.children || []).map(text).join('');
const response = (data, status = 200) => new Response(JSON.stringify(data), { status, headers: { 'Content-Type': 'application/json' } });
async function mount(run, { role = 'ROLE_ADMIN', mock = false } = {}) {
  const old = { fetch: globalThis.fetch, location: globalThis.location, window: globalThis.window };
  const calls = [];
  globalThis.location = new URL(`${base}/operator.html${mock ? '?mock=1' : ''}#/observability`);
  globalThis.window = { addEventListener() {}, removeEventListener() {}, history: { replaceState(a, b, url) { globalThis.location = new URL(url); } } };
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init });
    if (url.endsWith('/api/auth/login')) return response({ username: 'qa-admin', role, accessToken: 'qa-test-token' });
    if (url.endsWith('/api/me')) return response({ username: 'qa-admin', role });
    return response([], 401);
  };
  let tree;
  try {
    api.clearOperatorSession();
    await act(async () => { tree = Renderer.create(React.createElement(Gate, { defaultBase: base }, props => React.createElement('section', {},
      React.createElement('p', {}, props.preview ? '모의 자료' : 'PRIVATE_ADMIN_DATA'),
      React.createElement('button', { onClick: props.preview ? props.requireLogin : props.logout }, props.preview ? '실제 자료 연결' : '로그아웃')))); });
    await run(tree, calls);
  } finally {
    await act(async () => tree?.unmount()); api.clearOperatorSession();
    Object.assign(globalThis, old);
  }
}
async function login(tree) {
  for (const [name, value] of [['username', 'qa-admin'], ['password', 'test-only-password']]) {
    await act(async () => tree.root.findByProps({ name }).props.onChange({ target: { value } }));
  }
  await act(async () => tree.root.findByType('form').props.onSubmit({ preventDefault() {} }));
}

test('operator gate mounts no protected data or request before login', async () => {
  await mount(async (tree, calls) => { assert.ok(!text(tree.toJSON()).includes('PRIVATE_ADMIN_DATA')); assert.equal(calls.length, 0); });
});
test('ADMIN login reveals workspace and logout removes its data and JWT', async () => {
  await mount(async tree => {
    await login(tree); assert.match(text(tree.toJSON()), /PRIVATE_ADMIN_DATA/);
    await act(async () => tree.root.findByType('button').props.onClick());
    assert.ok(!text(tree.toJSON()).includes('PRIVATE_ADMIN_DATA')); assert.equal(api.operatorSession(base), null);
  });
});
test('USER login stays outside operator workspace and clears the password', async () => {
  await mount(async tree => {
    await login(tree); assert.match(text(tree.toJSON()), /관리자 계정/);
    assert.ok(!text(tree.toJSON()).includes('PRIVATE_ADMIN_DATA'));
    assert.equal(tree.root.findByProps({ name: 'password' }).props.value, '');
  }, { role: 'ROLE_USER' });
});
test('expired JWT removes previously rendered ADMIN data and shows re-login', async () => {
  await mount(async tree => {
    await login(tree);
    await act(async () => { await assert.rejects(api.getIncidents(base), error => error.status === 401); });
    assert.ok(!text(tree.toJSON()).includes('PRIVATE_ADMIN_DATA')); assert.match(text(tree.toJSON()), /만료/);
  });
});
test('explicit offline preview has no API calls and requires login before real data', async () => {
  await mount(async (tree, calls) => {
    assert.match(text(tree.toJSON()), /모의 자료/); assert.equal(calls.length, 0);
    await act(async () => tree.root.findByType('button').props.onClick());
    assert.match(text(tree.toJSON()), /관리자 로그인/); assert.equal(calls.length, 0);
    assert.equal(globalThis.location.search, '');
  }, { mock: true });
});
