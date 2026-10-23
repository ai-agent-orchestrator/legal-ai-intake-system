import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, join, resolve } from 'node:path';
import configuration from '../vite.config.js';

test('.env API_PROXY_TARGET is used by both local dev and build preview', async () => {
  const originalDirectory = process.cwd();
  const originalTarget = process.env.API_PROXY_TARGET;
  const directory = await mkdtemp(join(tmpdir(), 'intake-proxy-test-'));
  try {
    delete process.env.API_PROXY_TARGET;
    await writeFile(join(directory, '.env.test'), 'API_PROXY_TARGET=http://127.0.0.1:8086\n');
    process.chdir(directory);
    const result = configuration({ mode: 'test' });
    assert.equal(result.server.proxy['/api'].target, 'http://127.0.0.1:8086');
    assert.equal(result.preview.proxy['/api'].target, 'http://127.0.0.1:8086');
    process.env.API_PROXY_TARGET = 'http://127.0.0.1:8087';
    assert.equal(configuration({ mode: 'test' }).server.proxy['/api'].target, 'http://127.0.0.1:8087');
  } finally {
    process.chdir(originalDirectory);
    if (originalTarget === undefined) delete process.env.API_PROXY_TARGET;
    else process.env.API_PROXY_TARGET = originalTarget;
    const target = resolve(directory);
    assert.equal(dirname(target), resolve(tmpdir()));
    assert.ok(basename(target).startsWith('intake-proxy-test-'));
    await rm(target, { recursive: true, force: true });
  }
});
