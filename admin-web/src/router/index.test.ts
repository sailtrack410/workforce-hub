import { describe, expect, it } from 'vitest';
import type { RouteRecordRaw } from 'vue-router';

describe('admin router', () => {
  it('contains core route entries', async () => {
    const routes = await loadAdminRoutes();

    expect(findRoute(routes, '/dashboard')?.name).toBe('admin-dashboard');
    expect(findRoute(routes, '/agent-tasks')?.name).toBe('admin-agent-tasks');
    expect(findRoute(routes, '/agent-config')?.name).toBe('admin-agent-config');
    expect(findRoute(routes, '/workflow')?.name).toBe('admin-workflow');
    expect(findRoute(routes, '/leave')?.name).toBe('admin-leave');
    expect(findRoute(routes, '/leave-types')?.name).toBe('admin-leave-types');
    expect(findRoute(routes, '/departments')?.name).toBe('admin-departments');
    expect(findRoute(routes, '/positions')?.name).toBe('admin-positions');
    expect(findRoute(routes, '/users')?.name).toBe('admin-users');
    expect(findRoute(routes, '/files')?.name).toBe('admin-files');
    expect(findRoute(routes, '/plugins')?.name).toBe('admin-plugins');
    expect(findRoute(routes, '/plugins')?.meta?.permission).toBe('ADMIN_PLUGIN_MANAGE');
    expect(findRoute(routes, '/message-delivery')?.name).toBe('admin-message-delivery');
    expect(findRoute(routes, '/permissions')?.name).toBe('admin-permissions');
    expect(findRoute(routes, '/audit-logs')?.name).toBe('admin-audit-logs');
    expect(findRoute(routes, '/ragflow')?.name).toBe('admin-ragflow');
    expect(findRoute(routes, '/ai-models')?.name).toBe('admin-ai-models');
    expect(findRoute(routes, '/system')?.name).toBe('admin-system');
    expect(findRoute(routes, '/settings')?.name).toBe('admin-settings');
    expect(findRoute(routes, '/dicts')?.name).toBe('admin-dicts');
    expect(findRoute(routes, '/roles')?.name).toBe('admin-roles');
    expect(findRoute(routes, '/attendance')?.name).toBe('admin-attendance');
    expect(findRoute(routes, '/agent-config')?.name).toBe('admin-agent-config');
  });

  it('contains top-level DAG editor route', async () => {
    const routes = await loadAdminRoutes();
    const dagRoute = routes.find((r) => r.name === 'admin-agent-dag');
    expect(dagRoute).toBeDefined();
    expect(typeof dagRoute?.component).toBe('function');
  });

  it('lazy-loads shell and page components', async () => {
    const routes = await loadAdminRoutes();
    const shellRoute = routes.find((route) => route.children?.length);
    const pageRoutes = shellRoute?.children?.filter((route) => route.component && route.path !== '') ?? [];

    expect(typeof shellRoute?.component).toBe('function');
    expect(pageRoutes.length).toBeGreaterThanOrEqual(14);
    expect(pageRoutes.every((route) => typeof route.component === 'function')).toBe(true);
  });
});

async function loadAdminRoutes(): Promise<RouteRecordRaw[]> {
  Object.defineProperty(globalThis, 'location', {
    value: {
      host: '127.0.0.1:5173',
      pathname: '/',
      search: '',
      hash: '#/',
    },
    configurable: true,
  });
  Object.defineProperty(globalThis, 'document', {
    value: {
      addEventListener: () => undefined,
      createElement: () => ({}),
      querySelector: () => null,
      removeEventListener: () => undefined,
    },
    configurable: true,
  });
  Object.defineProperty(globalThis, 'window', {
    value: {
      location: globalThis.location,
      document: globalThis.document,
      history: {
        state: null,
        replaceState: () => undefined,
        pushState: () => undefined,
      },
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    },
    configurable: true,
  });
  const module = await import('./index');
  return module.adminRoutes;
}

function findRoute(routes: RouteRecordRaw[], path: string) {
  const normalizedPath = path.replace(/^\//, '');
  return routes.find((route) => route.path === path)
    ?? routes.flatMap((route) => route.children ?? []).find((route) => route.path === normalizedPath);
}
