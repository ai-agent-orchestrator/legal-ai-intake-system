import test from 'node:test';
import assert from 'node:assert/strict';
import { readOperatorRoute } from '../src/operator/operatorRoute.js';

test('risk history handoff preserves the actual positive ID only on the audit route', () => {
  assert.deepEqual(readOperatorRoute('#/audit?riskId=42'), { route: 'audit', riskId: 42, redirect: false });
  for (const value of ['0', '-1', 'RH-42', '1.5', '9007199254740992']) assert.equal(readOperatorRoute(`#/audit?riskId=${value}`).riskId, null);
  assert.equal(readOperatorRoute('#/observability?riskId=42').riskId, null);
});

test('removed guide bookmarks redirect to screen13, while unknown routes remain unknown', () => {
  assert.deepEqual(readOperatorRoute('#/observability-guide'), { route: 'observability', riskId: null, redirect: true });
  assert.equal(readOperatorRoute('#/no-such-page').route, 'no-such-page');
});
