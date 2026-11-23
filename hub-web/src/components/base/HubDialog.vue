<script setup lang="ts">
/**
 * HubDialog：居中模态弹窗（画布：白底 r20 + 遮罩）。Esc/遮罩点击关闭（可锁）。
 */
import { onBeforeUnmount, onMounted } from 'vue';

const props = withDefaults(defineProps<{ lock?: boolean; titleId?: string }>(), { lock: false });
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
    <div class="hw-dialog" role="dialog" aria-modal="true" :aria-labelledby="titleId">
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
  padding: var(--hw-space-4);
  background: var(--hw-overlay);
  animation: hw-dialog-fade 0.18s ease-out;
}
.hw-dialog {
  display: flex;
  flex-direction: column;
  max-width: calc(100vw - 32px);
  max-height: calc(100dvh - 32px);
  padding: var(--hw-space-6);
  border: 1px solid var(--hw-border);
  border-radius: var(--hw-radius-panel);
  background: var(--hw-card);
  box-shadow: var(--hw-shadow-dialog);
  overflow-y: auto;
  animation: hw-dialog-rise var(--hw-duration-panel) var(--hw-motion-enter);
}
@keyframes hw-dialog-fade { from { opacity: 0; } to { opacity: 1; } }
@keyframes hw-dialog-rise {
  from { opacity: 0; transform: translateY(12px) scale(0.97); }
  to { opacity: 1; transform: none; }
}
</style>
