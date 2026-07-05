<script setup lang="ts">
import { reactive, provide } from 'vue';
import { toastKey, type ToastApi } from '@/composables/useToast';

interface ToastItem {
  id: number;
  type: 'error' | 'success';
  message: string;
}

const toasts = reactive<ToastItem[]>([]);
let nextId = 0;

function push(type: ToastItem['type'], message: string) {
  const id = nextId++;
  toasts.push({ id, type, message });
  setTimeout(() => remove(id), 3500);
}

function remove(id: number) {
  const idx = toasts.findIndex((t) => t.id === id);
  if (idx >= 0) toasts.splice(idx, 1);
}

const api: ToastApi = {
  error: (msg) => push('error', msg),
  success: (msg) => push('success', msg),
};

provide(toastKey, api);
</script>

<template>
  <Teleport to="body">
    <div class="toast-container" aria-live="polite">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        :class="['toast-item', `toast-${toast.type}`]"
        @click="remove(toast.id)"
      >
        {{ toast.message }}
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: var(--space-base);
  right: var(--space-base);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  max-width: 380px;
  pointer-events: none;
}

.toast-item {
  padding: var(--space-sm) var(--space-base);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  font-weight: 700;
  cursor: pointer;
  pointer-events: auto;
  animation: toast-in 0.2s ease-out;
  box-shadow: var(--shadow-sm);
}

.toast-error {
  background: var(--error-bg);
  color: var(--red-dark);
  border: 1px solid rgba(179, 49, 49, 0.2);
}

.toast-success {
  background: var(--success-bg);
  color: var(--success);
  border: 1px solid rgba(22, 163, 74, 0.2);
}

@keyframes toast-in {
  from { opacity: 0; transform: translateX(20px); }
  to { opacity: 1; transform: translateX(0); }
}
</style>
