import { createRenderer, defineComponent, h, nextTick, reactive } from 'vue';
import { describe, expect, it } from 'vitest';
import { provideSettingsSectionContext, useSettingsSectionContext } from './employee-shell-context';
import type { SettingsSectionContext } from './employee-shell-context';

describe('employee shell context', () => {
  it('keeps injected store objects live after section components destructure them', async () => {
    const shell = reactive({ theme: 'light' });
    let injectedShell: unknown;

    const Child = defineComponent({
      setup() {
        const { shell } = useSettingsSectionContext();
        injectedShell = shell;
        return () => h('span');
      },
    });

    const Root = defineComponent({
      setup() {
        provideSettingsSectionContext({
          shell,
          session: {},
          showToast: () => undefined,
        } as unknown as SettingsSectionContext);
        return () => h(Child);
      },
    });

    createTestRenderer().createApp(Root).mount({});

    expect((injectedShell as { theme: string }).theme).toBe('light');

    shell.theme = 'dark';
    await nextTick();

    expect((injectedShell as { theme: string }).theme).toBe('dark');
  });
});

function createTestRenderer() {
  return createRenderer({
    patchProp: () => undefined,
    insert: () => undefined,
    remove: () => undefined,
    createElement: () => ({}),
    createText: () => ({}),
    createComment: () => ({}),
    setText: () => undefined,
    setElementText: () => undefined,
    parentNode: () => null,
    nextSibling: () => null,
  });
}
