const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const { startAppServer } = require('./app-server.cjs');

test('后端不可用时仍从包内加载页面，拒绝跨源访问和路径穿越', async () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'workforce-app-'));
  fs.writeFileSync(path.join(root, 'index.html'), '<title>workforce</title>');
  const app = await startAppServer({ root, backend: 'http://127.0.0.1:1' });
  try {
    assert.equal(await (await fetch(app.origin)).text(), '<title>workforce</title>');
    assert.equal((await fetch(app.origin + '/api/employee/auth/me')).status, 503);
    assert.equal((await fetch(app.origin, { headers: { Origin: 'https://untrusted.example' } })).status, 403);
    assert.equal((await fetch(app.origin + '/%2e%2e%2fprivate.txt')).status, 403);
    assert.equal((await fetch(app.origin + '/assets/missing.js')).status, 404);
  } finally { app.close(); fs.rmSync(root, { recursive: true, force: true }); }
});

test('同源代理保留请求与认证 Cookie，透传错误状态并报告认证结果', async () => {
  const remote = http.createServer((request, response) => {
    assert.equal(request.headers['x-workforce-client'], 'desktop');
    assert.equal(request.headers.cookie, 'SESSION=fixture');
    response.writeHead(200, { 'Content-Type': 'application/json', 'Set-Cookie': 'SESSION=fixture; Domain=backend.invalid; Secure; HttpOnly; SameSite=Lax' });
    response.end('{"code":0,"data":{"userId":"123"}}');
  });
  await new Promise((resolve) => remote.listen(0, '127.0.0.1', resolve));
  const observed = [];
  const app = await startAppServer({ root: os.tmpdir(), backend: `http://127.0.0.1:${remote.address().port}`, onResponse: (...args) => observed.push(args) });
  try {
    const result = await fetch(app.origin + '/api/employee/auth/me', { headers: { Cookie: 'SESSION=fixture' } });
    assert.equal((await result.json()).data.userId, '123');
    assert.equal(result.headers.get('set-cookie'), 'SESSION=fixture; HttpOnly; SameSite=Lax');
    assert.equal(observed[0][0], '/api/employee/auth/me');
  } finally { app.close(); remote.closeAllConnections(); remote.close(); }
});
