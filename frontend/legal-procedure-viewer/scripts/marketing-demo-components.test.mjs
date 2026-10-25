import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import React from 'react';
import Renderer, { act } from 'react-test-renderer';
import { createServer } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

let vite, Page, Demo, api;
const base = 'http://localhost:18080';
const real = { submittedIntakeCount: 13, searchRequestCount: 6, searchRequestedIntakeCount: 6, consultationReadyIntakeCount: 5, stages: [], caseTypeDistribution: {}, desiredNextStepDistribution: {}, regionDistribution: {}, measurementBasis: 'ALL_RECORDS_IN_CURRENT_DATABASE', dropOffMeasured: false, eventCounts: {} };
const content = node => typeof node === 'string' ? node : (node?.children || []).map(content).join('');
before(async () => {
  vite = await createServer({ configFile: false, root: fileURLToPath(new URL('../', import.meta.url)), plugins: [react()], server: { middlewareMode: true, hmr: false }, appType: 'custom' });
  Page = (await vite.ssrLoadModule('/src/operator/MarketingPage.jsx')).default;
  Demo = (await vite.ssrLoadModule('/src/operator/MarketingDemoPanel.jsx')).default;
  api = await vite.ssrLoadModule('/src/operator/operatorApi.js');
});
after(async () => { await vite?.close(); });

async function withPage(run, status = 200) {
  const originalFetch = globalThis.fetch;
  const calls = [];
  let tree;
  globalThis.fetch = async (url, init = {}) => {
    calls.push({ url, method: init.method || 'GET' });
    const login = url.endsWith('/api/auth/login'), me = url.endsWith('/api/me');
    return new Response(JSON.stringify(login || me ? { username: 'test-admin', role: 'ROLE_ADMIN', ...(login ? { accessToken: 'synthetic-unit-test-token' } : {}) } : status === 200 ? real : { message: '마케팅 조회 실패' }), { status: login || me ? 200 : status, headers: { 'Content-Type': 'application/json' } });
  };
  try {
    api.clearOperatorSession();
    await api.loginOperator(base, 'test-admin', 'synthetic-test-password');
    calls.length = 0;
    await act(async () => { tree = Renderer.create(React.createElement(Page, { base })); });
    await run(tree, calls);
  } finally {
    await act(async () => tree?.unmount());
    api.clearOperatorSession(); globalThis.fetch = originalFetch;
  }
}
async function choose(tree, name) {
  const button = tree.root.findAllByType('button').find(node => content(node.toJSON ? node.toJSON() : { children: node.children }) === name);
  assert.ok(button, name);
  await act(async () => button.props.onClick());
}
test('real data is default; demo never changes C13 or writes data; switching back reloads real data', async () => {
  await withPage(async (tree, calls) => {
    assert.match(content(tree.toJSON()), /접수된 문의13건/);
    assert.doesNotMatch(content(tree.toJSON()), /상담 후 수임률/);
    assert.equal(calls.length, 1);
    await choose(tree, '시연 분석');
    assert.match(content(tree.toJSON()), /시연 데이터 기준/);
    assert.match(content(tree.toJSON()), /33\.3%/);
    assert.doesNotMatch(content(tree.toJSON()), /접수된 문의13건/);
    assert.equal(calls.length, 1);
    await choose(tree, '실제 집계');
    assert.match(content(tree.toJSON()), /접수된 문의13건/);
    assert.doesNotMatch(content(tree.toJSON()), /상담 후 수임률/);
    assert.equal(calls.length, 2);
    assert.ok(calls.every(call => call.method === 'GET' && call.url.endsWith('/api/admin/dashboard/marketing-funnel')));
  });
});
test('real query failures stay visible and do not silently fall back to demo', async () => {
  await withPage(async (tree, calls) => {
    assert.ok(tree.root.findAllByProps({ role: 'alert' }).length);
    assert.doesNotMatch(content(tree.toJSON()), /시연 데이터 기준/);
    await choose(tree, '시연 분석');
    assert.match(content(tree.toJSON()), /시연 데이터 기준/);
    assert.equal(calls.length, 1);
    await choose(tree, '실제 집계');
    assert.ok(tree.root.findAllByProps({ role: 'alert' }).length);
    assert.doesNotMatch(content(tree.toJSON()), /시연 데이터 기준/);
  }, 503);
});
test('an empty demo has no invented percentages or recommendations', async () => {
  let tree;
  try {
    await act(async () => { tree = Renderer.create(React.createElement(Demo, { journeys: [] })); });
    const value = content(tree.toJSON());
    assert.match(value, /상담 후 수임률—/);
    assert.match(value, /기록된 미수임 사유가 없습니다/);
    assert.doesNotMatch(value, /33\.3%|NaN|Infinity|미도달 비율 최대/);
  } finally { await act(async () => tree?.unmount()); }
});
