const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');
let handler;
vm.runInNewContext(readFileSync(join(__dirname, 'index.ts'), 'utf8'), {
  Deno: { serve: fn => { handler = fn; }, env: { get: key => key === 'SUPABASE_URL' ? 'https://example.supabase.co' : 'test-key' } },
  Response,
  fetch: async (_url, options) => options.headers.Authorization === 'Bearer valid'
    ? Response.json({ app_metadata: { ogrenci_id: 'test-student' } })
    : new Response(null, { status: 401 }),
});
const request = (body, token = 'valid') => new Request('https://example.test', {
  method: 'POST', body,
  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/octet-stream' },
});
test('rejects missing/invalid authentication', async () => {
  assert.equal((await handler(new Request('https://example.test'))).status, 401);
  assert.equal((await handler(request(new Uint8Array(1), 'invalid'))).status, 401);
});
test('accepts warmup and consumes exactly 512 KiB', async () => {
  assert.equal((await handler(new Request('https://example.test', { headers: { Authorization: 'Bearer valid' } }))).status, 200);
  const response = await handler(request(new Uint8Array(512 * 1024)));
  assert.equal(response.status, 200);
  assert.equal((await response.json()).bytes, 512 * 1024);
});
test('rejects oversized streaming payloads even without Content-Length', async () => {
  assert.equal((await handler(request(new Uint8Array(1024 * 1024 + 1)))).status, 413);
});
test('rejects empty or wrong content type', async () => {
  assert.equal((await handler(request(new Uint8Array()))).status, 400);
  const r = request('text');
  r.headers.set('Content-Type', 'text/plain');
  assert.equal((await handler(r)).status, 415);
});
