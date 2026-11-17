<script lang="ts">
import { defineComponent, h, ref, watch, shallowRef, onBeforeUnmount, onErrorCaptured, type Component } from 'vue';
import { usePluginsStore } from '@/stores/plugins';
import AppLoadingState from '@/components/ui/AppLoadingState.vue';
import AppErrorState from '@/components/ui/AppErrorState.vue';

export default defineComponent({
  name: 'PluginWorkbenchHost',
  props: {
    pluginId: { type: String, required: true },
  },
  setup(props) {
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

      const host = window.__WORKFORCE_HOST__;
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
      loadComponent(props.pluginId);
    }

    watch(
      () => ({ id: props.pluginId, allowed: plugins.loadedIds.includes(props.pluginId) }),
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

    return () => {
      if (phase.value === 'loading') {
        return h(AppLoadingState);
      }
      if (phase.value === 'error') {
        return h(AppErrorState, {
          title: '插件加载失败',
          description: errorMessage.value,
          actionLabel: '重试',
          onRetry: retry,
        });
      }
      if (pluginComponent.value) {
        return h(pluginComponent.value);
      }
      return null;
    };
  },
});
</script>
