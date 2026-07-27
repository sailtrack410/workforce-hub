import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { defineComponent, h } from 'vue';
import { initHostSdk, type WorkforceHostSdk, type PluginRegistration } from './host-sdk';
import * as hostSdkModule from './host-sdk';

function makePlugin(id: string, name?: string): PluginRegistration {
  return { id, name: name ?? id, component: () => Promise.resolve(defineComponent({ render: () => h('div') })) };
}

describe('initHostSdk', () => {
  beforeEach(() => {
    Object.defineProperty(globalThis, 'window', { value: {}, configurable: true });
  });
  afterEach(() => {
    delete (globalThis as Record<string, unknown>).window;
  });

  it('首次初始化返回 SDK 并挂载到 window', () => {
    const sdk = initHostSdk();
    expect(sdk).toBeDefined();
    expect(sdk.Vue).toBeDefined();
    expect(window.__WORKFORCE_HOST__).toBe(sdk);
  });

  it('幂等：重复调用返回同一实例，不清空插件', () => {
    const sdk1 = initHostSdk();
    sdk1.register('test-keep', makePlugin('test-keep'));

    const sdk2 = initHostSdk();
    expect(sdk2).toBe(sdk1);
    const found = sdk2.getPlugin('test-keep');
    expect(found).toBeDefined();
    if (found) {
      expect(found.id).toBe('test-keep');
    }
  });

  it('Reflect.set 不能替换全局 SDK', () => {
    const sdk = initHostSdk();
    const fake = { Vue: {}, register: () => {}, getPlugin: () => undefined, getAllPlugins: () => new Map() };
    const result = Reflect.set(window, '__WORKFORCE_HOST__', fake);
    expect(result).toBe(false);
    expect(window.__WORKFORCE_HOST__).toBe(sdk);
  });

  it('不能替换 SDK.register', () => {
    const sdk = initHostSdk();
    expect(() => {
      (sdk as { register: unknown }).register = () => {};
    }).toThrow();
  });

  it('宿主模块可清空注册表，但全局 SDK 不暴露清理能力', () => {
    const sdk = initHostSdk();
    sdk.register('clear-me', makePlugin('clear-me'));

    const clear = (hostSdkModule as unknown as { clearHostSdkPlugins?: () => void }).clearHostSdkPlugins;
    if (!clear) throw new Error('clearHostSdkPlugins 未实现');
    clear();

    expect(sdk.getPlugin('clear-me')).toBeUndefined();
    expect(sdk).not.toHaveProperty('clearHostSdkPlugins');
    expect(sdk).not.toHaveProperty('unregister');
    expect(sdk).not.toHaveProperty('reset');
  });
});

describe('register', () => {
  let sdk: WorkforceHostSdk;
  beforeEach(() => {
    Object.defineProperty(globalThis, 'window', { value: {}, configurable: true });
    sdk = initHostSdk();
  });
  afterEach(() => {
    delete (globalThis as Record<string, unknown>).window;
  });

  it('正常注册并查询', () => {
    const reg = makePlugin('hello', 'Hello Plugin');
    sdk.register('hello', reg);

    const found = sdk.getPlugin('hello');
    expect(found).toBeDefined();
    if (found) {
      expect(found.id).toBe('hello');
      expect(found.name).toBe('Hello Plugin');
    }
  });

  it('拒绝空 id', () => {
    expect(() => sdk.register('', makePlugin(''))).toThrow('插件 id 不能为空');
    expect(() => sdk.register('  ', makePlugin('  '))).toThrow('插件 id 不能为空');
  });

  it('拒绝不符合资源路径规则的 id', () => {
    for (const id of ['../escape', 'has/slash', 'has space', 'UPPER', '%20encoded', 'p'.repeat(65)]) {
      expect(() => sdk.register(id, makePlugin(id))).toThrow('非法插件 id');
    }
  });

  it('拒绝 id 与 registration.id 不一致', () => {
    expect(() => sdk.register('expected', makePlugin('actual'))).toThrow('注册 id 与 registration.id 不一致');
  });

  it('拒绝重复 id，不覆盖', () => {
    sdk.register('dup', makePlugin('dup', 'First'));
    expect(() => sdk.register('dup', makePlugin('dup', 'Second'))).toThrow('插件已注册');

    const found = sdk.getPlugin('dup');
    expect(found).toBeDefined();
    if (found) {
      expect(found.name).toBe('First');
    }
  });

  it('注册对象修改不影响已注册数据', () => {
    const reg = makePlugin('immutable', 'Original');
    sdk.register('immutable', reg);

    reg.name = 'Mutated';
    reg.id = 'changed';

    const found = sdk.getPlugin('immutable');
    expect(found).toBeDefined();
    if (found) {
      expect(found.name).toBe('Original');
    }
  });

  it('拒绝空白 name', () => {
    const invalid = { id: 'blank-name', name: '   ', component: () => Promise.resolve(defineComponent({ render: () => h('div') })) };
    expect(() => sdk.register('blank-name', invalid)).toThrow('插件 name 不能为空');
  });

  it('拒绝非函数 component', () => {
    const invalid = { id: 'bad-comp', name: 'Bad', component: 'not-a-function' } as unknown as PluginRegistration;
    expect(() => sdk.register('bad-comp', invalid)).toThrow('插件 component 必须是函数');
  });
});

describe('getPlugin', () => {
  let sdk: WorkforceHostSdk;
  beforeEach(() => {
    Object.defineProperty(globalThis, 'window', { value: {}, configurable: true });
    sdk = initHostSdk();
  });
  afterEach(() => {
    delete (globalThis as Record<string, unknown>).window;
  });

  it('未注册返回 undefined', () => {
    expect(sdk.getPlugin('nonexistent')).toBeUndefined();
  });

  it('返回的是副本，修改不影响注册表', () => {
    sdk.register('copy-test', makePlugin('copy-test', 'Original'));
    const copy = sdk.getPlugin('copy-test');
    expect(copy).toBeDefined();
    if (copy) {
      copy.name = 'Mutated';
    }

    const again = sdk.getPlugin('copy-test');
    expect(again).toBeDefined();
    if (again) {
      expect(again.name).toBe('Original');
    }
  });
});

describe('getAllPlugins', () => {
  let sdk: WorkforceHostSdk;
  beforeEach(() => {
    Object.defineProperty(globalThis, 'window', { value: {}, configurable: true });
    sdk = initHostSdk();
  });
  afterEach(() => {
    delete (globalThis as Record<string, unknown>).window;
  });

  it('返回只读快照', () => {
    sdk.register('a', makePlugin('a'));
    sdk.register('b', makePlugin('b'));

    const all = sdk.getAllPlugins();
    expect(all.size).toBe(2);
  });

  it('修改快照不影响内部注册表', () => {
    sdk.register('snap', makePlugin('snap'));

    const all = sdk.getAllPlugins();
    (all as Map<string, PluginRegistration>).set('injected', makePlugin('injected'));

    expect(sdk.getAllPlugins().size).toBe(1);
  });

  it('快照元素是副本', () => {
    sdk.register('deep', makePlugin('deep', 'Original'));

    const all = sdk.getAllPlugins();
    const entry = all.get('deep');
    expect(entry).toBeDefined();
    if (entry) {
      entry.name = 'Mutated';
    }

    const fresh = sdk.getPlugin('deep');
    expect(fresh).toBeDefined();
    if (fresh) {
      expect(fresh.name).toBe('Original');
    }
  });
});
