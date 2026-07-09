<script setup lang="ts">
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

function closeDialog() {
  if (props.closeDisabled) return;
  emit('update:visible', false);
  emit('close');
}
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="admin-dialog-overlay" @click.self="closeDialog">
      <section class="admin-dialog" role="dialog" aria-modal="true" :aria-labelledby="labelledBy" :style="{ width }">
        <header v-if="title || showClose" class="admin-dialog-head">
          <h3 v-if="title" :id="labelledBy">{{ title }}</h3>
          <button v-if="showClose" class="icon-button" type="button" title="关闭" :disabled="closeDisabled" @click="closeDialog">
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
}

.admin-dialog {
  max-height: min(720px, 86vh);
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  box-shadow: var(--shadow);
  padding: var(--space-xl);
}

.admin-dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-base);
}

.admin-dialog-head h3 {
  margin: 0;
  font-size: var(--text-md);
}
</style>
