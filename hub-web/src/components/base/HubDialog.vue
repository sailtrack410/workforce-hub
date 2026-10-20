<script setup lang="ts">
/**
 * HubDialog：居中模态弹窗（画布：白底 r20 + 遮罩）。Esc/遮罩点击关闭（可锁）。
 */
import { onBeforeUnmount, onMounted } from 'vue';

const props = withDefaults(defineProps<{ lock?: boolean }>(), { lock: false });
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
  <div class="hw-dialog-overlay" role="presentation" @click="onOverlayClick">
    <div class="hw-dialog" role="dialog" aria-modal="true">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.hw-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 32px;
  background: rgba(15, 30, 55, 0.42);
  animation: hw-dialog-fade 0.18s ease-out;
}
.hw-dialog {
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 64px);
  max-height: calc(100vh - 96px);
  padding: 24px;
  border-radius: 20px;
  background: var(--hw-card);
  box-shadow: 0 24px 64px rgba(15, 30, 55, 0.22);
  overflow-y: auto;
  animation: hw-dialog-rise 0.22s var(--hw-spring);
}
@keyframes hw-dialog-fade { from { opacity: 0; } to { opacity: 1; } }
@keyframes hw-dialog-rise {
  from { opacity: 0; transform: translateY(12px) scale(0.97); }
  to { opacity: 1; transform: none; }
}
</style>
