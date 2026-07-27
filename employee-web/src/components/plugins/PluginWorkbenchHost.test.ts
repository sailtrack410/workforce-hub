import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/components/ui/AppLoadingState.vue', async () => {
  const { defineComponent, h } = await import('vue');
  return {
    default: defineComponent({
      setup: () => () => h('span', '正在同步内容…'),
    }),
  };
});

vi.mock('@/components/ui/AppErrorState.vue', async () => {
  const { defineComponent, h } = await import('vue');
  return {
    default: defineComponent({
      props: {
        title: String,
        description: String,
        actionLabel: String,
      },
      emits: ['retry'],
      setup(props, { emit }) {
        return () =>
          h('div', [
            h('span', props.description ?? ''),
            h('button', { onClick: () => emit('retry') }, props.actionLabel ?? '重试'),
          ]);
      },
    }),
  };
});

import { createRenderer, defineComponent, h, nextTick, ref, type Component } from 'vue';
import { createPinia, setActivePinia, type Pinia } from 'pinia';
import { usePluginsStore } from '@/stores/plugins';
import PluginWorkbenchHost from './PluginWorkbenchHost.vue';
import type { WorkforceHostSdk, PluginRegistration } from '@/core/host-sdk';

interface TestNode {
  type: string;
  text: string;
  parent: TestNode | null;
  children: TestNode[];
}

function createTestNode(type: string): TestNode {
  return { type, text: '', parent: null, children: [] };
}

function createTestRenderer() {
  return createRenderer<TestNode, TestNode>({
    patchProp: () => undefined,
    insert(child, parent, anchor) {
      if (anchor) {
        const idx = parent.children.indexOf(anchor);
        if (idx >= 0) {
          parent.children.splice(idx, 0, child);
        } else {
          parent.children.push(child);
        }
      } else {
        parent.children.push(child);
      }
      child.parent = parent;
    },
    remove(child) {
      if (child.parent) {
        const idx = child.parent.children.indexOf(child);
        if (idx >= 0) child.parent.children.splice(idx, 1);
        child.parent = null;
      }
    },
    createElement: (type) => createTestNode(type),
    createText: (text) => ({ type: '#text', text, parent: null, children: [] }),
    createComment: () => ({ type: '#comment', text: '', parent: null, children: [] }),
    setText(node, text) {
      node.text = text;
    },
    setElementText(node, text) {
      node.text = text;
    },
    parentNode: (node) => node.parent,
    nextSibling: (node) => {
      if (!node.parent) return null;
      const idx = node.parent.children.indexOf(node);
      return idx >= 0 && idx < node.parent.children.length - 1
        ? node.parent.children[idx + 1]
        : null;
    },
  });
}

const renderer = createTestRenderer();

function collectText(node: TestNode): string {
  let result = '';
  if (node.text) result += node.text;
  for (const child of node.children) {
    result += collectText(child);
  }
  return result;
}

function setupHost(sdk: Partial<WorkforceHostSdk>) {
  Object.defineProperty(globalThis, 'window', { value: {}, configurable: true });
  window.__WORKFORCE_HOST__ = sdk as WorkforceHostSdk;
}

function mountHost(pluginId: string, pinia: Pinia) {
  setActivePinia(pinia);
  const container = createTestNode('#root');
  const Root = defineComponent({
    setup: () => () => h(PluginWorkbenchHost, { pluginId }),
  });
  const app = renderer.createApp(Root);
  app.use(pinia);
  app.provide(Symbol.for('v-scx'), { modules: new Set<string>() });
  app.mount(container);
  return { container, app };
}

function mountHostWithRef(pluginIdRef: ReturnType<typeof ref<string>>, pinia: Pinia) {
  setActivePinia(pinia);
  const container = createTestNode('#root');
  const Root = defineComponent({
    setup: () => () => h(PluginWorkbenchHost, { pluginId: pluginIdRef.value as string }),
  });
  const app = renderer.createApp(Root);
  app.use(pinia);
  app.provide(Symbol.for('v-scx'), { modules: new Set<string>() });
  app.mount(container);
  return { container, app };
}

describe('PluginWorkbenchHost', () => {
  afterEach(() => {
    delete (globalThis as Record<string, unknown>).window;
  });

  it('loads and renders plugin component when allowed and registered', async () => {
    const pluginComp = defineComponent({
      setup: () => () => h('div', 'plugin-content'),
    });
    const factory = vi.fn<() => Promise<Component>>().mockResolvedValue(pluginComp);
    setupHost({
      getPlugin: (id: string) =>
        id === 'my-plugin'
          ? ({ id: 'my-plugin', name: 'My', component: factory } satisfies PluginRegistration)
          : undefined,
    });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['my-plugin'];

    const { container } = mountHost('my-plugin', pinia);
    await nextTick();
    await nextTick();

    expect(factory).toHaveBeenCalledTimes(1);
    expect(collectText(container)).toContain('plugin-content');
  });

  it('does not call factory when id is not in loadedIds', async () => {
    const factory = vi.fn<() => Promise<Component>>().mockResolvedValue(
      defineComponent({ setup: () => () => h('div', 'should-not-render') }),
    );
    setupHost({
      getPlugin: () => ({ id: 'x', name: 'X', component: factory } satisfies PluginRegistration),
    });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['other-plugin'];

    const { container } = mountHost('my-plugin', pinia);
    await nextTick();
    await nextTick();

    expect(factory).not.toHaveBeenCalled();
    expect(collectText(container)).toContain('插件不可用');
  });

  it('shows error when registration is missing from host SDK', async () => {
    setupHost({ getPlugin: () => undefined });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['my-plugin'];

    const { container } = mountHost('my-plugin', pinia);
    await nextTick();
    await nextTick();

    expect(collectText(container)).toContain('插件不可用');
  });

  it('catches factory rejection and shows error', async () => {
    const factory = vi.fn<() => Promise<Component>>().mockRejectedValue(new Error('load failed'));
    setupHost({
      getPlugin: (id: string) =>
        id === 'my-plugin'
          ? ({ id: 'my-plugin', name: 'My', component: factory } satisfies PluginRegistration)
          : undefined,
    });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['my-plugin'];

    const { container } = mountHost('my-plugin', pinia);
    await nextTick();
    await nextTick();

    expect(factory).toHaveBeenCalledTimes(1);
    expect(collectText(container)).toContain('插件加载失败');
  });

  it('catches factory returning null and shows error', async () => {
    const factory = vi.fn<() => Promise<Component>>().mockResolvedValue(null as unknown as Component);
    setupHost({
      getPlugin: (id: string) =>
        id === 'my-plugin'
          ? ({ id: 'my-plugin', name: 'My', component: factory } satisfies PluginRegistration)
          : undefined,
    });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['my-plugin'];

    const { container } = mountHost('my-plugin', pinia);
    await nextTick();
    await nextTick();

    expect(factory).toHaveBeenCalledTimes(1);
    expect(collectText(container)).toContain('插件加载失败');
  });

  it('isolates setup/render errors via onErrorCaptured', async () => {
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const badComp = defineComponent({
      setup() {
        throw new Error('render boom');
      },
    });
    const factory = vi.fn<() => Promise<Component>>().mockResolvedValue(badComp);
    setupHost({
      getPlugin: (id: string) =>
        id === 'bad-plugin'
          ? ({ id: 'bad-plugin', name: 'Bad', component: factory } satisfies PluginRegistration)
          : undefined,
    });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['bad-plugin'];

    const { container } = mountHost('bad-plugin', pinia);
    await nextTick();
    await nextTick();

    expect(collectText(container)).toContain('插件运行失败');
    errorSpy.mockRestore();
  });

  it('switches from A to B and stale A resolve does not overwrite', async () => {
    let resolveFirst!: (v: Component) => void;
    const firstFactory = vi.fn<() => Promise<Component>>().mockReturnValue(
      new Promise((r) => { resolveFirst = r; }),
    );
    const secondComp = defineComponent({ setup: () => () => h('div', 'second') });
    const secondFactory = vi.fn<() => Promise<Component>>().mockResolvedValue(secondComp);

    setupHost({
      getPlugin: (id: string) => {
        if (id === 'plugin-a') return { id: 'plugin-a', name: 'A', component: firstFactory } satisfies PluginRegistration;
        if (id === 'plugin-b') return { id: 'plugin-b', name: 'B', component: secondFactory } satisfies PluginRegistration;
        return undefined;
      },
    });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['plugin-a', 'plugin-b'];

    const pluginId = ref('plugin-a');
    const { container } = mountHostWithRef(pluginId, pinia);

    await nextTick();
    expect(firstFactory).toHaveBeenCalledTimes(1);

    pluginId.value = 'plugin-b';
    await nextTick();
    await nextTick();
    expect(secondFactory).toHaveBeenCalledTimes(1);
    expect(collectText(container)).toContain('second');

    const staleComp = defineComponent({ setup: () => () => h('div', 'stale') });
    resolveFirst(staleComp);
    await nextTick();

    expect(collectText(container)).toContain('second');
    expect(collectText(container)).not.toContain('stale');
  });

  it('clears content when loadedIds removes the plugin id', async () => {
    const pluginComp = defineComponent({ setup: () => () => h('div', 'content-visible') });
    const factory = vi.fn<() => Promise<Component>>().mockResolvedValue(pluginComp);
    setupHost({
      getPlugin: (id: string) =>
        id === 'my-plugin'
          ? ({ id: 'my-plugin', name: 'My', component: factory } satisfies PluginRegistration)
          : undefined,
    });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['my-plugin'];

    const { container } = mountHost('my-plugin', pinia);
    await nextTick();
    await nextTick();

    expect(collectText(container)).toContain('content-visible');

    store.loadedIds = [];
    await nextTick();

    expect(collectText(container)).not.toContain('content-visible');
    expect(collectText(container)).toContain('插件不可用');
  });

  it('pending factory with permission removed before resolve does not mount', async () => {
    let resolveFactory!: (v: Component) => void;
    const factory = vi.fn<() => Promise<Component>>().mockReturnValue(
      new Promise((r) => { resolveFactory = r; }),
    );
    setupHost({
      getPlugin: (id: string) =>
        id === 'my-plugin'
          ? ({ id: 'my-plugin', name: 'My', component: factory } satisfies PluginRegistration)
          : undefined,
    });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['my-plugin'];

    const { container } = mountHost('my-plugin', pinia);
    await nextTick();
    expect(factory).toHaveBeenCalledTimes(1);

    store.loadedIds = [];
    await nextTick();

    const lateComp = defineComponent({ setup: () => () => h('div', 'late-content') });
    resolveFactory(lateComp);
    await nextTick();

    expect(collectText(container)).not.toContain('late-content');
    expect(collectText(container)).toContain('插件不可用');
  });

  it('unmount during pending factory prevents late update', async () => {
    let resolveFactory!: (v: Component) => void;
    const factory = vi.fn<() => Promise<Component>>().mockReturnValue(
      new Promise((r) => { resolveFactory = r; }),
    );
    setupHost({
      getPlugin: (id: string) =>
        id === 'my-plugin'
          ? ({ id: 'my-plugin', name: 'My', component: factory } satisfies PluginRegistration)
          : undefined,
    });

    const pinia = createPinia();
    const store = usePluginsStore(pinia);
    store.loadedIds = ['my-plugin'];

    const { app, container } = mountHost('my-plugin', pinia);
    await nextTick();
    expect(factory).toHaveBeenCalledTimes(1);

    app.unmount();

    const lateComp = defineComponent({ setup: () => () => h('div', 'late') });
    resolveFactory(lateComp);
    await nextTick();

    expect(collectText(container)).not.toContain('late');
  });
});
