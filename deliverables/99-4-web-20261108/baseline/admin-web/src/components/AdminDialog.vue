<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { X } from 'lucide-vue-next';

const props = withDefaults(
  defineProps<{
    visible: boolean;
    title?: string;
    labelledBy?: string;
    width?: string;
    showClose?: boolean;
    closeDisabled?: boolean;
  }>(),
  { title: '', labelledBy: undefined, width: 'min(520px, 92vw)', showClose: true, closeDisabled: false },
);

const emit = defineEmits<{
  close: [];
  'update:visible': [value: boolean];
}>();

const dialogRef = ref<HTMLElement | null>(null);
let previousFocus: HTMLElement | null = null;

function focusableElements() {
  return Array.from(dialogRef.value?.querySelectorAll<HTMLElement>(
    'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [href], [tabindex]:not([tabindex="-1"])',
  ) ?? []).filter((element) => !element.hasAttribute('hidden'));
}

function handleKeydown(event: KeyboardEvent) {
  if (!props.visible) return;
  const dialogs = Array.from(document.querySelectorAll<HTMLElement>('.admin-dialog'));
  if (dialogs[dialogs.length - 1] !== dialogRef.value) return;
  if (event.key === 'Escape') {
    event.preventDefault();
    closeDialog();
    return;
  }
  if (event.key !== 'Tab') return;
  const elements = focusableElements();
  if (!elements.length) {
    event.preventDefault();
    dialogRef.value?.focus();
    return;
  }
  const first = elements[0];
  const last = elements[elements.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      document.addEventListener('keydown', handleKeydown);
      await nextTick();
      const preferred = dialogRef.value?.querySelector<HTMLElement>('[autofocus]');
      (preferred ?? focusableElements()[0] ?? dialogRef.value)?.focus();
      return;
    }
    document.removeEventListener('keydown', handleKeydown);
    previousFocus?.focus();
    previousFocus = null;
  },
  { immediate: true },
);

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown);
  previousFocus?.focus();
});

function closeDialog() {
  if (props.closeDisabled) return;
  emit('update:visible', false);
  emit('close');
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="admin-dialog-overlay" @click.self="closeDialog">
      <section ref="dialogRef" class="admin-dialog" role="dialog" aria-modal="true" :aria-labelledby="labelledBy" :aria-label="labelledBy ? undefined : title || '对话框'" tabindex="-1" :style="{ width }">
        <header v-if="title || showClose" class="admin-dialog-head">
          <h3 v-if="title" :id="labelledBy">{{ title }}</h3>
          <button v-if="showClose" class="icon-button" type="button" title="关闭" aria-label="关闭" :disabled="closeDisabled" @click="closeDialog">
            <X :size="16" />
          </button>
        </header>
        <slot />
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.admin-dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--dialog-z);
  display: grid;
  place-items: center;
  padding: var(--space-xl);
  background: var(--dialog-overlay-bg);
  backdrop-filter: blur(2px);
}

.admin-dialog {
  max-height: min(720px, 86vh);
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  box-shadow: var(--shadow);
  padding: var(--space-lg);
  animation: admin-dialog-enter 160ms ease-out;
}

.admin-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin: calc(var(--space-lg) * -1) calc(var(--space-lg) * -1) var(--space-lg);
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--line);
  background: var(--surface-subtle);
}

.admin-dialog-head h3 {
  margin: 0;
  font-size: var(--text-md);
}

.admin-dialog-head .icon-button {
  width: 32px;
  min-height: 32px;
}

@keyframes admin-dialog-enter {
  from { opacity: 0; transform: translateY(8px) scale(0.99); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@media (max-width: 640px) {
  .admin-dialog-overlay {
    align-items: end;
    padding: var(--space-md);
  }

  .admin-dialog {
    max-height: calc(100vh - var(--space-xl));
  }
}
</style>
