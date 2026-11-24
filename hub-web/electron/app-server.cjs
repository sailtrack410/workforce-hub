const http = require('node:http');
const https = require('node:https');
const fs = require('node:fs');
const path = require('node:path');

async function startAppServer({ root, backend, onResponse = () => {} }) {
  const upstream = new URL(backend);
  if (!['http:', 'https:'].includes(upstream.protocol) || upstream.username || upstream.password) throw new Error('服务地址无效');
  const transport = upstream.protocol === 'https:' ? https : http;
  const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.json': 'application/json' };
  let origin;
  function allowed(request) {
    return request.headers.host === new URL(origin).host && (!request.headers.origin || request.headers.origin === origin);
  }
  function options(request) {
    const headers = { ...request.headers, host: upstream.host, origin: upstream.origin, 'X-Workforce-Client': 'desktop' };
    delete headers['accept-encoding'];
    return { protocol: upstream.protocol, hostname: upstream.hostname, port: upstream.port, path: request.url, method: request.method, headers };
  }
  const server = http.createServer((request, response) => {
    if (!allowed(request)) { response.writeHead(403); response.end(); return; }
    if (request.url.startsWith('/api/') || request.url.startsWith('/__employee-local-upload')) {
      const remote = transport.request(options(request), (incoming) => {
        const headers = { ...incoming.headers };
        // Cookie 只属于应用的回环 HTTP 源；认证仍由后端校验。
        if (headers['set-cookie']) headers['set-cookie'] = headers['set-cookie'].map((cookie) => cookie.replace(/;\s*Domain=[^;]+/ig, '').replace(/;\s*Secure\b/ig, ''));
        response.writeHead(incoming.statusCode, headers);
        const chunks = [];
        const inspect = /\/api\/employee\/auth\/(me|login|logout)(?:\?|$)/.test(request.url);
        incoming.on('data', (chunk) => { if (inspect && chunks.reduce((n, b) => n + b.length, 0) < 65536) chunks.push(chunk); });
        incoming.on('end', () => { if (inspect) onResponse(request.url, incoming.statusCode, Buffer.concat(chunks).toString()); });
        incoming.pipe(response);
      });
      remote.setTimeout(15000, () => remote.destroy());
      remote.on('error', () => {
        if (!response.headersSent) response.writeHead(503, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify({ code: 503, message: '服务暂不可用', data: null }));
      });
      request.pipe(remote); return;
    }
    if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405); response.end(); return; }
    let relative;
    try { relative = decodeURIComponent(new URL(request.url, origin).pathname); }
    catch { response.writeHead(400); response.end(); return; }
    const filename = path.resolve(root, '.' + (relative === '/' ? '/index.html' : relative));
    if (!filename.startsWith(path.resolve(root) + path.sep) || relative.includes('\0')) { response.writeHead(403); response.end(); return; }
    fs.readFile(filename, (error, data) => {
      if (error) { response.writeHead(404); response.end(); return; }
      response.writeHead(200, { 'Content-Type': types[path.extname(filename)] || 'application/octet-stream', 'Cache-Control': 'no-cache', 'X-Content-Type-Options': 'nosniff' });
      response.end(request.method === 'HEAD' ? undefined : data);
    });
  });
  server.on('upgrade', (request, socket, head) => {
    if (!allowed(request) || !request.url.startsWith('/ws/employee/')) { socket.destroy(); return; }
    const remote = transport.request(options(request));
    remote.on('upgrade', (response, peer, remoteHead) => {
      socket.write(`HTTP/1.1 101 Switching Protocols\r\n${Object.entries(response.headers).map(([k, v]) => `${k}: ${v}`).join('\r\n')}\r\n\r\n`);
      if (head.length) peer.write(head);
      if (remoteHead.length) socket.write(remoteHead);
      peer.pipe(socket); socket.pipe(peer);
      socket.on('error', () => peer.destroy()); peer.on('error', () => socket.destroy());
      socket.on('close', () => peer.destroy()); peer.on('close', () => socket.destroy());
    });
    remote.on('response', () => socket.destroy());
    remote.on('error', () => socket.destroy()); remote.end();
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  origin = `http://127.0.0.1:${server.address().port}`;
  return { origin, close: () => { server.closeAllConnections(); server.close(); } };
}

module.exports = { startAppServer };
