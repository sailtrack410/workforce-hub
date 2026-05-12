import { computed, createRenderer, defineComponent, h, isRef, nextTick, ref } from 'vue';
import { describe, expect, it } from 'vitest';
import { provideEmployeeShellContext, useEmployeeShellContext } from './employee-shell-context';

describe('employee shell context', () => {
  it('keeps injected refs reactive after section components destructure them', async () => {
    const selectedProfile = ref('center');
    let injectedSelectedProfile: unknown;

    const Child = defineComponent({
      setup() {
        const { selectedProfileId } = useEmployeeShellContext();
        injectedSelectedProfile = selectedProfileId;
        return () => h('span');
      },
    });

    const Root = defineComponent({
      setup() {
        provideEmployeeShellContext({
          selectedProfileId: computed(() => selectedProfile.value),
        });
        return () => h(Child);
      },
    });

    createTestRenderer().createApp(Root).mount({});

    expect(isRef(injectedSelectedProfile)).toBe(true);
    expect((injectedSelectedProfile as { value: string }).value).toBe('center');

    selectedProfile.value = 'security';
    await nextTick();

    expect((injectedSelectedProfile as { value: string }).value).toBe('security');
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
