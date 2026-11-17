import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { initHostSdk, type WorkforceHostSdk, type PluginRegistration } from './host-sdk';
import { loadPlugin } from './plugin-loader';
import * as pluginLoader from './plugin-loader';
import { defineComponent, h } from 'vue';

const VALID_SRI = 'sha384-abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789';
const VALID_SRI_2 = 'sha384-0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';

function makePlugin(id: string): PluginRegistration {
  return { id, name: id, component: () => Promise.resolve(defineComponent({ render: () => h('div') })) };
}

type FakeScript = HTMLScriptElement & { remove(): void };

function createFakeDocument(appendChildFn?: (el: FakeScript) => void) {
  const scripts: FakeScript[] = [];
  const doc = {
    head: {
      appendChild(el: FakeScript) {
        if (appendChildFn) appendChildFn(el);
        scripts.push(el);
        return el;
      },
    },
    createElement(_tag: string) {
      const el = { remove: vi.fn() } as unknown as FakeScript;
      return el;
    },
  };
  return { doc: doc as unknown as Document, scripts };
}

function resetPluginRuntime(): void {
  const reset = (pluginLoader as unknown as { resetPluginRuntime?: () => void }).resetPluginRuntime;
  if (!reset) throw new Error('resetPluginRuntime 未实现');
  reset();
}

function registerFromScript(sdk: WorkforceHostSdk, id: string, script: FakeScript): void {
  Object.defineProperty(document, 'currentScript', { value: script, configurable: true });
  sdk.register(id, makePlugin(id));
  Object.defineProperty(document, 'currentScript', { value: null, configurable: true });
}

describe('loadPlugin', () => {
  let sdk: WorkforceHostSdk;
  let scripts: FakeScript[];
  let originalWindow: typeof globalThis.window | undefined;
  let originalDocument: typeof globalThis.document | undefined;

  beforeEach(() => {
    originalWindow = (globalThis as Record<string, unknown>).window as typeof globalThis.window | undefined;
    originalDocument = (globalThis as Record<string, unknown>).document as typeof globalThis.document | undefined;

    const fakeDoc = createFakeDocument();
    scripts = fakeDoc.scripts;

    Object.defineProperty(globalThis, 'window', { value: {}, configurable: true });
    Object.defineProperty(globalThis, 'document', { value: fakeDoc.doc, configurable: true });

    sdk = initHostSdk();
  });

  afterEach(() => {
    (pluginLoader as unknown as { resetPluginRuntime?: () => void }).resetPluginRuntime?.();
    if (originalWindow === undefined) {
      delete (globalThis as Record<string, unknown>).window;
    } else {
      Object.defineProperty(globalThis, 'window', { value: originalWindow, configurable: true });
    }
    if (originalDocument === undefined) {
      delete (globalThis as Record<string, unknown>).document;
    } else {
      Object.defineProperty(globalThis, 'document', { value: originalDocument, configurable: true });
    }
  });

  it('成功加载并注册插件，integrity 属性已设置', async () => {
    const p = loadPlugin('my-plugin', VALID_SRI);
    expect(scripts.length).toBe(1);
    expect(scripts[0].src).toBe('/plugins/my-plugin/index.js');
    expect(scripts[0].async).toBe(true);
    expect(scripts[0].integrity).toBe(VALID_SRI);

    registerFromScript(sdk, 'my-plugin', scripts[0]);
    scripts[0].onload!(new Event('load'));
    await p;
    expect(scripts[0].remove).not.toHaveBeenCalled();

    await loadPlugin('my-plugin', VALID_SRI);
    expect(scripts.length).toBe(1);
  });

  it('SDK 外部预注册插件拒绝复用且不插入 script', async () => {
    const externalScript = {} as FakeScript;
    Object.defineProperty(document, 'currentScript', { value: externalScript, configurable: true });
    sdk.register('already', makePlugin('already'));
    Object.defineProperty(document, 'currentScript', { value: null, configurable: true });
    await expect(loadPlugin('already', VALID_SRI)).rejects.toThrow('未经 loader 验证');
    expect(scripts.length).toBe(0);
  });

  it('目标 script 已注册但尚未 onload 时，同摘要复用在途 pending', async () => {
    const first = loadPlugin('register-before-load', VALID_SRI);
    registerFromScript(sdk, 'register-before-load', scripts[0]);

    const second = loadPlugin('register-before-load', VALID_SRI);
    expect(scripts.length).toBe(1);
    let secondSettled = false;
    void second.then(
      () => { secondSettled = true; },
      () => { secondSettled = true; },
    );
    await Promise.resolve();
    expect(secondSettled).toBe(false);

    scripts[0].onload!(new Event('load'));
    await Promise.all([first, second]);
  });

  it('loader 已验证插件使用不同 integrity 时拒绝且不插入新 script', async () => {
    const p = loadPlugin('verified-conflict', VALID_SRI);
    registerFromScript(sdk, 'verified-conflict', scripts[0]);
    scripts[0].onload!(new Event('load'));
    await p;

    await expect(loadPlugin('verified-conflict', VALID_SRI_2)).rejects.toThrow('integrity 不匹配');
    expect(scripts.length).toBe(1);
  });

  it('pending 期间的外部同 ID 注册不会被目标 script 标记为已验证', async () => {
    const p = loadPlugin('source-mismatch', VALID_SRI);
    const externalScript = {} as FakeScript;
    Object.defineProperty(document, 'currentScript', { value: externalScript, configurable: true });
    sdk.register('source-mismatch', makePlugin('source-mismatch'));
    Object.defineProperty(document, 'currentScript', { value: null, configurable: true });

    scripts[0].onload!(new Event('load'));

    await expect(p).rejects.toThrow('未由当前 script 注册');
    expect(scripts[0].remove).toHaveBeenCalledOnce();
    await expect(loadPlugin('source-mismatch', VALID_SRI)).rejects.toThrow('未经 loader 验证');
    expect(scripts.length).toBe(1);
  });

  it('脚本加载后未注册目标插件则失败并移除 script', async () => {
    const p = loadPlugin('no-register', VALID_SRI);
    scripts[0].onload!(new Event('load'));
    await expect(p).rejects.toThrow('插件 no-register 加载后未注册');
    expect(scripts[0].remove).toHaveBeenCalledOnce();
  });

  it('脚本加载失败则 reject 并移除 script', async () => {
    const p = loadPlugin('fail-plugin', VALID_SRI);
    scripts[0].onerror!(new Event('error'));
    await expect(p).rejects.toThrow('插件 fail-plugin 加载失败');
    expect(scripts[0].remove).toHaveBeenCalledOnce();
  });

  it('脚本加载超时则 reject 并移除 script', async () => {
    vi.useFakeTimers();
    try {
      let rejection = '';
      const p = loadPlugin('slow-plugin', VALID_SRI);
      void p.catch((error: Error) => { rejection = error.message; });

      await vi.advanceTimersByTimeAsync(10_000);

      expect(rejection).toBe('插件 slow-plugin 加载超时');
      expect(scripts[0].remove).toHaveBeenCalledOnce();
    } finally {
      vi.useRealTimers();
    }
  });

  it('非法 id 不插入 script', async () => {
    const invalidIds = ['', '../escape', 'has/slash', 'has space', 'UPPER', '%20encoded', 'p'.repeat(65)];
    for (const id of invalidIds) {
      await expect(loadPlugin(id, VALID_SRI)).rejects.toThrow('非法插件 id');
    }
    expect(scripts.length).toBe(0);
  });

  it('合法 id 含连字符可进入加载', async () => {
    const p = loadPlugin('my-cool-plugin', VALID_SRI);
    expect(scripts.length).toBe(1);
    expect(scripts[0].src).toBe('/plugins/my-cool-plugin/index.js');
    expect(scripts[0].integrity).toBe(VALID_SRI);
    registerFromScript(sdk, 'my-cool-plugin', scripts[0]);
    scripts[0].onload!(new Event('load'));
    await p;
  });

  it('并发调用复用同一个 Promise，只插入一个 script', async () => {
    const p1 = loadPlugin('concurrent', VALID_SRI);
    const p2 = loadPlugin('concurrent', VALID_SRI);
    const p3 = loadPlugin('concurrent', VALID_SRI);
    expect(scripts.length).toBe(1);

    registerFromScript(sdk, 'concurrent', scripts[0]);
    scripts[0].onload!(new Event('load'));
    await Promise.all([p1, p2, p3]);
  });

  it('失败后清理 pending 状态，允许重试', async () => {
    const p1 = loadPlugin('retry-me', VALID_SRI);
    scripts[0].onerror!(new Event('error'));
    await expect(p1).rejects.toThrow('插件 retry-me 加载失败');

    const p2 = loadPlugin('retry-me', VALID_SRI);
    expect(scripts.length).toBe(2);

    registerFromScript(sdk, 'retry-me', scripts[1]);
    scripts[1].onload!(new Event('load'));
    await p2;
  });

  it('appendChild 同步抛错后清理 pending 状态，允许重试', async () => {
    const failingDoc = createFakeDocument(() => {
      throw new Error('DOM not ready');
    });
    Object.defineProperty(globalThis, 'document', { value: failingDoc.doc, configurable: true });

    await expect(loadPlugin('dom-fail', VALID_SRI)).rejects.toThrow('DOM not ready');
    expect(failingDoc.scripts.length).toBe(0);

    const okDoc = createFakeDocument();
    Object.defineProperty(globalThis, 'document', { value: okDoc.doc, configurable: true });

    const p = loadPlugin('dom-fail', VALID_SRI);
    expect(okDoc.scripts.length).toBe(1);
    registerFromScript(sdk, 'dom-fail', okDoc.scripts[0]);
    okDoc.scripts[0].onload!(new Event('load'));
    await p;
  });

  it('window 未初始化时抛错', async () => {
    delete (globalThis as Record<string, unknown>).window;
    await expect(loadPlugin('any', VALID_SRI)).rejects.toThrow('Host SDK 未初始化');
  });

  it('非法 integrity 均拒绝且不插入 script', async () => {
    const invalid = [
      '',
      'abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789',
      'sha256-abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
      'sha512-abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef',
      'sha384-abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456 789',
      `${VALID_SRI} ${VALID_SRI_2}`,
      'sha384-abcdef0123456789',
    ];
    for (const sri of invalid) {
      await expect(loadPlugin('bad-sri', sri)).rejects.toThrow('非法 integrity');
    }
    expect(scripts.length).toBe(0);
  });

  it('并发同 pluginId 不同 integrity 拒绝且只插入一个 script', async () => {
    const p1 = loadPlugin('conflict-sri', VALID_SRI);
    expect(scripts.length).toBe(1);

    await expect(loadPlugin('conflict-sri', VALID_SRI_2)).rejects.toThrow('integrity 不匹配');
    expect(scripts.length).toBe(1);

    registerFromScript(sdk, 'conflict-sri', scripts[0]);
    scripts[0].onload!(new Event('load'));
    await p1;
  });

  it('reset 清除已加载注册、已验证摘要与 script', async () => {
    const p = loadPlugin('loaded-reset', VALID_SRI);
    registerFromScript(sdk, 'loaded-reset', scripts[0]);
    scripts[0].onload!(new Event('load'));
    await p;

    resetPluginRuntime();

    expect(sdk.getPlugin('loaded-reset')).toBeUndefined();
    expect(scripts[0].remove).toHaveBeenCalledOnce();

    const reloaded = loadPlugin('loaded-reset', VALID_SRI_2);
    expect(scripts.length).toBe(2);
    registerFromScript(sdk, 'loaded-reset', scripts[1]);
    scripts[1].onload!(new Event('load'));
    await reloaded;
  });

  it('reset 结束 pending Promise，并拒绝旧脚本的延迟注册', async () => {
    const p = loadPlugin('pending-reset', VALID_SRI);
    const oldScript = scripts[0];
    const oldOnload = oldScript.onload!;

    resetPluginRuntime();

    await expect(p).rejects.toThrow('加载已取消');
    expect(oldScript.remove).toHaveBeenCalledOnce();

    Object.defineProperty(document, 'currentScript', { value: oldScript, configurable: true });
    expect(() => sdk.register('pending-reset', makePlugin('pending-reset'))).toThrow('插件 script 已失效');
    Object.defineProperty(document, 'currentScript', { value: null, configurable: true });
    oldOnload.call(oldScript, new Event('load'));

    expect(sdk.getPlugin('pending-reset')).toBeUndefined();
  });

  it('reset 后失效旧 script 不能抢占同 ID 的新会话注册', async () => {
    const oldPromise = loadPlugin('stale-register-race', VALID_SRI);
    const oldScript = scripts[0];
    const oldOnload = oldScript.onload!;
    const oldOnerror = oldScript.onerror!;

    resetPluginRuntime();
    const newPromise = loadPlugin('stale-register-race', VALID_SRI_2);
    void newPromise.catch(() => undefined);
    const newScript = scripts[1];
    await expect(oldPromise).rejects.toThrow('加载已取消');

    Object.defineProperty(document, 'currentScript', { value: oldScript, configurable: true });
    expect(() => sdk.register('stale-register-race', makePlugin('stale-register-race'))).toThrow('插件 script 已失效');
    Object.defineProperty(document, 'currentScript', { value: null, configurable: true });
    expect(sdk.getPlugin('stale-register-race')).toBeUndefined();

    registerFromScript(sdk, 'stale-register-race', newScript);
    newScript.onload!(new Event('load'));
    await newPromise;

    oldOnload.call(oldScript, new Event('load'));
    oldOnerror.call(oldScript, new Event('error'));

    expect(sdk.getPlugin('stale-register-race')).toBeDefined();
    await loadPlugin('stale-register-race', VALID_SRI_2);
    await expect(loadPlugin('stale-register-race', VALID_SRI)).rejects.toThrow('integrity 不匹配');
  });

  it('reset 后旧脚本异步回调在 currentScript 为空时也不能注册', async () => {
    const oldPromise = loadPlugin('stale-async-register', VALID_SRI);
    const oldScript = scripts[0];

    resetPluginRuntime();
    await expect(oldPromise).rejects.toThrow('加载已取消');

    Object.defineProperty(document, 'currentScript', { value: null, configurable: true });
    expect(() => sdk.register('stale-async-register', makePlugin('stale-async-register')))
      .toThrow('插件必须在当前脚本执行期间注册');
    expect(sdk.getPlugin('stale-async-register')).toBeUndefined();
    expect(oldScript.remove).toHaveBeenCalledOnce();
  });

  it('reset 后同 ID 新摘要加载不受旧 finally、onload 或 onerror 污染', async () => {
    const oldPromise = loadPlugin('session-race', VALID_SRI);
    const oldScript = scripts[0];
    const oldOnload = oldScript.onload!;
    const oldOnerror = oldScript.onerror!;

    resetPluginRuntime();
    const newPromise = loadPlugin('session-race', VALID_SRI_2);
    const newScript = scripts[1];
    await expect(oldPromise).rejects.toThrow('加载已取消');

    registerFromScript(sdk, 'session-race', newScript);
    newScript.onload!(new Event('load'));
    await newPromise;

    oldOnload.call(oldScript, new Event('load'));
    oldOnerror.call(oldScript, new Event('error'));

    expect(sdk.getPlugin('session-race')).toBeDefined();
    await loadPlugin('session-race', VALID_SRI_2);
    await expect(loadPlugin('session-race', VALID_SRI)).rejects.toThrow('integrity 不匹配');
    expect(scripts.length).toBe(2);
  });
});
