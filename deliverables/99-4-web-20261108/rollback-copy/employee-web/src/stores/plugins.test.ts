import { describe, it, expect, vi, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { usePluginsStore } from './plugins';
import type { EmployeePluginResponse } from '../api';

vi.mock('../api', () => ({
  employeeApi: {
    plugins: {
      mine: vi.fn(),
    },
  },
}));

vi.mock('../core/plugin-loader', () => ({
  loadPlugin: vi.fn(),
  resetPluginRuntime: vi.fn(),
}));

import { employeeApi } from '../api';
import { loadPlugin, resetPluginRuntime } from '../core/plugin-loader';

const mineMock = vi.mocked(employeeApi.plugins.mine);
const loadPluginMock = vi.mocked(loadPlugin);
const resetPluginRuntimeMock = vi.mocked(resetPluginRuntime);

describe('usePluginsStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    mineMock.mockReset();
    loadPluginMock.mockReset();
    resetPluginRuntimeMock.mockReset();
  });

  it('loads manifest and loads all plugins successfully', async () => {
    mineMock.mockResolvedValue([
      { id: 'p-a', name: 'A', frontendEntry: '/plugins/p-a/index.js', integrity: 'sha384-aaa' },
      { id: 'p-b', name: 'B', frontendEntry: '/plugins/p-b/index.js', integrity: 'sha384-bbb' },
    ]);
    loadPluginMock.mockResolvedValue(undefined);

    const store = usePluginsStore();
    expect(store.resolved).toBe(false);

    await store.loadAccessiblePlugins();

    expect(store.manifest).toHaveLength(2);
    expect(store.loadedIds).toEqual(['p-a', 'p-b']);
    expect(store.errors).toEqual({});
    expect(store.manifestError).toBe('');
    expect(store.loading).toBe(false);
    expect(store.resolved).toBe(true);
    expect(loadPluginMock).toHaveBeenCalledWith('p-a', 'sha384-aaa');
    expect(loadPluginMock).toHaveBeenCalledWith('p-b', 'sha384-bbb');
  });

  it('records per-plugin failure by index even when error has no plugin id', async () => {
    mineMock.mockResolvedValue([
      { id: 'p-ok', name: 'OK', frontendEntry: '/plugins/p-ok/index.js', integrity: 'sha384-okk' },
      { id: 'p-fail', name: 'Fail', frontendEntry: '/plugins/p-fail/index.js', integrity: 'sha384-fal' },
      { id: 'p-ok2', name: 'OK2', frontendEntry: '/plugins/p-ok2/index.js', integrity: 'sha384-ok2' },
    ]);
    loadPluginMock.mockImplementation(async (id: string) => {
      if (id === 'p-fail') throw new Error('非法 integrity');
    });

    const store = usePluginsStore();
    await store.loadAccessiblePlugins();

    expect(store.loadedIds).toEqual(['p-ok', 'p-ok2']);
    expect(store.errors['p-fail']).toBe('非法 integrity');
    expect(Object.keys(store.errors)).toEqual(['p-fail']);
    expect(store.manifestError).toBe('');
    expect(store.loading).toBe(false);
  });

  it('records manifest error without rejecting', async () => {
    mineMock.mockRejectedValue(new Error('网络异常'));

    const store = usePluginsStore();
    await store.loadAccessiblePlugins();

    expect(store.manifest).toEqual([]);
    expect(store.loadedIds).toEqual([]);
    expect(store.manifestError).toBe('网络异常');
    expect(store.loading).toBe(false);
    expect(store.resolved).toBe(true);
    expect(loadPluginMock).not.toHaveBeenCalled();
  });

  it('reset clears all state', async () => {
    mineMock.mockResolvedValue([
      { id: 'p-x', name: 'X', frontendEntry: '/plugins/p-x/index.js', integrity: 'sha384-xxx' },
    ]);
    loadPluginMock.mockResolvedValue(undefined);

    const store = usePluginsStore();
    await store.loadAccessiblePlugins();
    expect(store.manifest).toHaveLength(1);
    expect(store.resolved).toBe(true);

    store.reset();
    expect(store.manifest).toEqual([]);
    expect(store.loadedIds).toEqual([]);
    expect(store.errors).toEqual({});
    expect(store.manifestError).toBe('');
    expect(store.loading).toBe(false);
    expect(store.resolved).toBe(false);
  });

  it('reset calls plugin runtime cleanup', () => {
    const store = usePluginsStore();

    store.reset();

    expect(resetPluginRuntimeMock).toHaveBeenCalledOnce();
  });

  it('reset after manifest but during loadPlugin pending prevents stale writeback', async () => {
    let resolveLoader!: () => void;
    loadPluginMock.mockReturnValue(new Promise<void>((r) => { resolveLoader = r; }));
    mineMock.mockResolvedValue([
      { id: 'stale', name: 'Stale', frontendEntry: '/plugins/stale/index.js', integrity: 'sha384-stl' },
    ]);

    const store = usePluginsStore();
    const loadPromise = store.loadAccessiblePlugins();

    await vi.waitFor(() => expect(loadPluginMock).toHaveBeenCalled());
    store.reset();
    expect(store.resolved).toBe(false);
    resolveLoader();
    await loadPromise;

    expect(store.manifest).toEqual([]);
    expect(store.loadedIds).toEqual([]);
    expect(store.errors).toEqual({});
    expect(store.manifestError).toBe('');
    expect(store.loading).toBe(false);
    expect(store.resolved).toBe(false);
  });

  it('passes exact id and integrity from manifest to loadPlugin', async () => {
    mineMock.mockResolvedValue([
      { id: 'my-plugin', name: 'Mine', frontendEntry: '/plugins/my-plugin/index.js', integrity: 'sha384-abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789' },
    ]);
    loadPluginMock.mockResolvedValue(undefined);

    const store = usePluginsStore();
    await store.loadAccessiblePlugins();

    expect(loadPluginMock).toHaveBeenCalledTimes(1);
    expect(loadPluginMock).toHaveBeenCalledWith('my-plugin', 'sha384-abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789');
  });

  it('first request slow, second request finishes first — only second state persists', async () => {
    let resolveFirstManifest!: (v: EmployeePluginResponse[]) => void;
    const firstManifest = new Promise<EmployeePluginResponse[]>((r) => { resolveFirstManifest = r; });
    let resolveSecondManifest!: (v: EmployeePluginResponse[]) => void;
    const secondManifest = new Promise<EmployeePluginResponse[]>((r) => { resolveSecondManifest = r; });

    let callCount = 0;
    mineMock.mockImplementation(() => {
      callCount++;
      return (callCount === 1 ? firstManifest : secondManifest);
    });
    loadPluginMock.mockResolvedValue(undefined);

    const store = usePluginsStore();
    const first = store.loadAccessiblePlugins();

    // second call bumps generation, first is now stale
    const second = store.loadAccessiblePlugins();

    // resolve second first
    resolveSecondManifest([
      { id: 'fast', name: 'Fast', frontendEntry: '/plugins/fast/index.js', integrity: 'sha384-abc' },
    ]);
    await second;

    expect(store.manifest).toHaveLength(1);
    expect(store.manifest[0].id).toBe('fast');
    expect(store.loadedIds).toEqual(['fast']);
    expect(store.resolved).toBe(true);

    // now resolve first (stale) — must not overwrite
    resolveFirstManifest([
      { id: 'slow', name: 'Slow', frontendEntry: '/plugins/slow/index.js', integrity: 'sha384-def' },
    ]);
    await first;

    expect(store.manifest).toHaveLength(1);
    expect(store.manifest[0].id).toBe('fast');
    expect(store.loadedIds).toEqual(['fast']);
    expect(store.resolved).toBe(true);
  });

  it('新一代加载开始时清理旧运行时，并允许同 ID 新摘要重新加载', async () => {
    let resolveFirstManifest!: (v: EmployeePluginResponse[]) => void;
    const firstManifest = new Promise<EmployeePluginResponse[]>((resolve) => {
      resolveFirstManifest = resolve;
    });
    let resolveSecondManifest!: (v: EmployeePluginResponse[]) => void;
    const secondManifest = new Promise<EmployeePluginResponse[]>((resolve) => {
      resolveSecondManifest = resolve;
    });

    let resetCalls = 0;
    let secondReset = false;
    let oldPendingCount = 0;
    let oldPendingSettled = false;
    const oldPending = new Set<() => void>();
    resetPluginRuntimeMock.mockImplementation(() => {
      resetCalls++;
      if (resetCalls === 2) {
        secondReset = true;
        for (const reject of oldPending) reject();
        oldPending.clear();
      }
    });
    mineMock
      .mockReturnValueOnce(firstManifest)
      .mockReturnValueOnce(secondManifest);
    loadPluginMock.mockImplementation((id: string, integrity: string) => {
      if (!secondReset && oldPendingCount < 2) {
        oldPendingCount++;
        return new Promise<void>((_resolve, reject) => {
          oldPending.add(() => {
            reject(new Error('旧运行时已清理'));
            oldPendingSettled = true;
          });
        });
      }
      if (secondReset && id === 'same-plugin' && integrity === 'sha384-new') return Promise.resolve();
      throw new Error('旧插件不应在新一代写回');
    });

    const store = usePluginsStore();
    const first = store.loadAccessiblePlugins();
    void first.catch(() => undefined);
    resolveFirstManifest([
      { id: 'same-plugin', name: 'Same old', frontendEntry: '/plugins/same-plugin/index.js', integrity: 'sha384-old' },
      { id: 'removed-plugin', name: 'Removed', frontendEntry: '/plugins/removed-plugin/index.js', integrity: 'sha384-removed' },
    ]);
    await vi.waitFor(() => expect(loadPluginMock).toHaveBeenCalledTimes(2));
    expect(oldPendingCount).toBe(2);
    resetPluginRuntimeMock.mockClear();

    const second = store.loadAccessiblePlugins();
    void second.catch(() => undefined);
    expect(resetPluginRuntimeMock).toHaveBeenCalledOnce();
    await Promise.resolve();
    expect(oldPendingSettled).toBe(true);

    resolveSecondManifest([
      { id: 'same-plugin', name: 'Same new', frontendEntry: '/plugins/same-plugin/index.js', integrity: 'sha384-new' },
    ]);
    await second;
    await first;

    expect(loadPluginMock).toHaveBeenCalledWith('same-plugin', 'sha384-new');
    expect(store.manifest).toEqual([
      { id: 'same-plugin', name: 'Same new', frontendEntry: '/plugins/same-plugin/index.js', integrity: 'sha384-new' },
    ]);
    expect(store.loadedIds).toEqual(['same-plugin']);
    expect(store.loadedIds).not.toContain('removed-plugin');
    expect(store.errors).toEqual({});
    expect(store.resolved).toBe(true);
  });

  it('loadAccessiblePlugins clears old state immediately before fetching', async () => {
    mineMock.mockResolvedValue([
      { id: 'p-x', name: 'X', frontendEntry: '/plugins/p-x/index.js', integrity: 'sha384-xxx' },
    ]);
    loadPluginMock.mockResolvedValue(undefined);

    const store = usePluginsStore();
    await store.loadAccessiblePlugins();
    expect(store.manifest).toHaveLength(1);
    expect(store.loadedIds).toEqual(['p-x']);

    // next call — old state must be gone before manifest resolves
    let resolveManifest!: (v: EmployeePluginResponse[]) => void;
    mineMock.mockReturnValueOnce(new Promise<EmployeePluginResponse[]>((r) => { resolveManifest = r; }));

    const loadPromise = store.loadAccessiblePlugins();
    expect(store.manifest).toEqual([]);
    expect(store.loadedIds).toEqual([]);
    expect(store.loading).toBe(true);
    expect(store.resolved).toBe(false);

    resolveManifest([
      { id: 'p-y', name: 'Y', frontendEntry: '/plugins/p-y/index.js', integrity: 'sha384-yyy' },
    ]);
    loadPluginMock.mockResolvedValue(undefined);
    await loadPromise;

    expect(store.manifest).toHaveLength(1);
    expect(store.manifest[0].id).toBe('p-y');
  });
});
