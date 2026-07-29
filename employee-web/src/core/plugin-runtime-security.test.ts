import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const currentDir = dirname(fileURLToPath(import.meta.url));

describe('plugin runtime security boundaries', () => {
  it('插件加载不阻塞消息工作区核心初始化', () => {
    const source = readFileSync(resolve(currentDir, '../views/MessagesView.vue'), 'utf8');
    const start = source.indexOf('async function loadAuthenticatedWorkspace()');
    const end = source.indexOf('async function hydrateUserPreferences()', start);
    const bootstrap = source.slice(start, end);
    const waitStart = bootstrap.indexOf('await Promise.all([');
    const waitEnd = bootstrap.indexOf(']);', waitStart);

    expect(bootstrap).toContain('void plugins.loadAccessiblePlugins().catch(() => undefined);');
    expect(bootstrap.slice(waitStart, waitEnd)).not.toContain('plugins.loadAccessiblePlugins');
  });

  it('退出登录后整页重载以清除插件顶层副作用', () => {
    const source = readFileSync(resolve(currentDir, '../views/sections/SettingsSection.vue'), 'utf8');
    const start = source.indexOf('async function confirmLogout()');
    const end = source.indexOf('async function updatePresenceStatus', start);
    const logout = source.slice(start, end);

    expect(logout).toContain('window.location.reload();');
    expect(logout).not.toContain('router.replace');
  });

  it('Nginx 对员工端和管理端文档启用最小 CSP', () => {
    const source = readFileSync(resolve(currentDir, '../../../deploy/nginx/default.conf'), 'utf8');
    const defaultServer = source.slice(source.indexOf('listen 80 default_server;'));
    const firstLocation = defaultServer.indexOf('location ');
    const serverHeaders = defaultServer.slice(0, firstLocation);

    expect(serverHeaders).toContain(
      'add_header Content-Security-Policy "script-src \'self\'; object-src \'none\'; base-uri \'none\'; frame-ancestors \'none\'" always;',
    );
    expect(serverHeaders).not.toContain("'unsafe-inline'");
    expect(serverHeaders).not.toContain("'unsafe-eval'");

    for (const marker of ['location /admin/ {', 'location / {']) {
      const start = defaultServer.indexOf(marker);
      const end = defaultServer.indexOf('\n    }', start);
      expect(start).toBeGreaterThan(-1);
      expect(defaultServer.slice(start, end)).not.toContain('add_header');
    }
  });

  it('Nginx 限制插件网关请求体并覆盖最长动作等待时间', () => {
    const source = readFileSync(resolve(currentDir, '../../../deploy/nginx/default.conf'), 'utf8');
    const marker = 'location ~ ^/api/plugin-gateway/[^/]+/actions/[^/]+$ {';
    const start = source.indexOf(marker);
    const end = source.indexOf('\n    }', start);
    const gateway = source.slice(start, end);

    expect(start).toBeGreaterThan(-1);
    expect(gateway).toContain('client_max_body_size 256k;');
    expect(gateway).toContain('proxy_read_timeout 330s;');
  });
});
