import { describe, expect, it } from 'vitest';
import type { RouteRecordRaw } from 'vue-router';

describe('employee router', () => {
  it('redirects the employee root to the message section', async () => {
    const employeeRoutes = await loadEmployeeRoutes();
    const root = employeeRoutes.find((route) => route.path === '/');

    expect(root?.redirect).toBe('/messages');
  });

  it.each([
    ['/messages', 'employee-messages', 'messages'],
    ['/messages/conv-1001', 'employee-message-detail', 'messages'],
    ['/todos', 'employee-todos', 'todos'],
    ['/todos/todo-1001', 'employee-todo-detail', 'todos'],
    ['/workbench', 'employee-workbench', 'workbench'],
    ['/workbench/leave', 'employee-workbench-detail', 'workbench'],
    ['/files', 'employee-files', 'files'],
    ['/contacts', 'employee-contacts', 'contacts'],
    ['/settings', 'employee-settings', 'settings'],
    ['/settings/security', 'employee-settings-detail', 'settings'],
  ])('resolves %s to a real section route', async (path, name, section) => {
    const employeeRoutes = await loadEmployeeRoutes();
    const route = findRoute(employeeRoutes, path);

    expect(route?.name).toBe(name);
    expect(route?.meta?.section).toBe(section);
  });

  it('lazy-loads the shell and section components', async () => {
    const employeeRoutes = await loadEmployeeRoutes();
    const shellRoute = employeeRoutes.find((route) => route.children?.length);
    const sectionRoutes = shellRoute?.children?.filter((route) => route.component) ?? [];

    expect(typeof shellRoute?.component).toBe('function');
    expect(sectionRoutes.length).toBeGreaterThan(0);
    expect(sectionRoutes.every((route) => typeof route.component === 'function')).toBe(true);
  });
});

async function loadEmployeeRoutes(): Promise<RouteRecordRaw[]> {
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
  return module.employeeRoutes;
}

function findRoute(employeeRoutes: RouteRecordRaw[], path: string) {
  const normalizedPath = path.replace(/^\//, '');
  return employeeRoutes.find((route) => route.path === path)
    ?? employeeRoutes.flatMap((route) => route.children ?? []).find((route) => {
      const routePath = route.path.replace(/\/:[^/]+/g, '/[^/]+');
      return new RegExp(`^${routePath}$`).test(normalizedPath);
    });
}
