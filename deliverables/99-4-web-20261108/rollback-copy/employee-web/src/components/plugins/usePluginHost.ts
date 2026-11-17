import { ref, watch, shallowRef, onBeforeUnmount, onErrorCaptured, type Component } from 'vue';
import { usePluginsStore } from '@/stores/plugins';
import type { WorkforceHostSdk } from '@/core/host-sdk';

export function usePluginHost(pluginId: () => string) {
  const plugins = usePluginsStore();
  const pluginComponent = shallowRef<Component | null>(null);
  const phase = ref<'loading' | 'ready' | 'error'>('loading');
  const errorMessage = ref('');
  let loadSeq = 0;

  onErrorCaptured(() => {
    loadSeq++;
    pluginComponent.value = null;
    phase.value = 'error';
    errorMessage.value = '插件运行失败';
    return false;
  });

  onBeforeUnmount(() => {
    loadSeq++;
    pluginComponent.value = null;
  });

  async function loadComponent(id: string) {
    const seq = ++loadSeq;
    pluginComponent.value = null;
    phase.value = 'loading';
    errorMessage.value = '';

    if (!plugins.loadedIds.includes(id)) {
      phase.value = 'error';
      errorMessage.value = '插件不可用';
      return;
    }

    if (typeof window === 'undefined') {
      phase.value = 'error';
      errorMessage.value = '插件不可用';
      return;
    }

    const host: WorkforceHostSdk | undefined = window.__WORKFORCE_HOST__;
    const reg = host?.getPlugin(id);
    if (!reg) {
      phase.value = 'error';
      errorMessage.value = '插件不可用';
      return;
    }

    try {
      const comp = await reg.component();
      if (seq !== loadSeq) return;
      if (!comp) {
        phase.value = 'error';
        errorMessage.value = '插件加载失败';
        return;
      }
      pluginComponent.value = comp;
      phase.value = 'ready';
    } catch {
      if (seq !== loadSeq) return;
      phase.value = 'error';
      errorMessage.value = '插件加载失败';
    }
  }

  function retry() {
    loadComponent(pluginId());
  }

  watch(
    () => ({ id: pluginId(), allowed: plugins.loadedIds.includes(pluginId()) }),
    ({ id, allowed }) => {
      if (!allowed) {
        loadSeq++;
        pluginComponent.value = null;
        phase.value = 'error';
        errorMessage.value = '插件不可用';
      } else {
        loadComponent(id);
      }
    },
    { immediate: true },
  );

  return { pluginComponent, phase, errorMessage, retry };
}
