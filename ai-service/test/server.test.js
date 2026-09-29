import test from 'node:test';
import assert from 'node:assert/strict';
import { createApp } from '../src/server.js';
import { AiError } from '../src/ai.js';

test('HTTP 오류 관측은 요청 ID와 안전한 코드만 포함한다', async t => {
  const logs = [];
  const app = createApp({ process: async () => { throw new AiError('OLLAMA_TIMEOUT', 503); } }, '', e => logs.push(e));
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const requestId = '11111111-1111-1111-1111-111111111111';
  const response = await fetch(`http://127.0.0.1:${server.address().port}/api/ai/process`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Request-ID': requestId },
    body: JSON.stringify({ content: 'private content' })
  });
  assert.equal(response.status, 503);
  assert.equal(response.headers.get('X-Request-ID'), requestId);
  assert.equal((await response.json()).error, 'OLLAMA_TIMEOUT');
  assert.equal(logs[0].requestId, requestId);
  assert.equal(logs[0].code, 'OLLAMA_TIMEOUT');
  assert.equal(JSON.stringify(logs).includes('private content'), false);
});
