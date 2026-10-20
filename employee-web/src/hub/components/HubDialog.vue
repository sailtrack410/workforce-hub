<script setup lang="ts">
/**
 * HubDialog：居中模态弹窗，画布规范（白底、圆角 20、遮罩）。
 * 全新实现，不依赖旧 AppDialog。Esc / 点遮罩关闭（可禁用）。
 */
import { onBeforeUnmount, onMounted } from 'vue';

const props = withDefaults(defineProps<{
  lock?: boolean;
}>(), {
  lock: false,
});

const emit = defineEmits<{ (e: 'close'): void }>();

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && !props.lock) emit('close');
}
function onOverlayClick(event: MouseEvent) {
  if (event.target === event.currentTarget && !props.lock) emit('close');
}

onMounted(() => window.addEventListener('keydown', onKeydown));
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown));
</script>

<template>
  <div class="hub-dialog-overlay" role="presentation" @click="onOverlayClick">
    <div class="hub-dialog" role="dialog" aria-modal="true">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.hub-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 32px;
  background: rgba(15, 30, 55, 0.42);
  box-sizing: border-box;
  animation: hub-dialog-fade 0.18s ease-out;
}
.hub-dialog {
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 64px);
  max-height: calc(100vh - 96px);
  padding: 24px;
  border-radius: 20px;
  background: var(--hub-card, #fff);
  box-shadow: 0 24px 64px rgba(15, 30, 55, 0.22);
  overflow-y: auto;
  box-sizing: border-box;
  animation: hub-dialog-rise 0.22s cubic-bezier(0.34, 1.3, 0.64, 1);
}
@keyframes hub-dialog-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes hub-dialog-rise {
  from { opacity: 0; transform: translateY(12px) scale(0.97); }
  to { opacity: 1; transform: none; }
}
</style>
