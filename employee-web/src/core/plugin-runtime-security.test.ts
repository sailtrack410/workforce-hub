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
});
