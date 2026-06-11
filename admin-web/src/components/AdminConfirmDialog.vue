<script setup lang="ts">
import { ref, watch } from 'vue';
import { AlertTriangle } from 'lucide-vue-next';

const props = withDefaults(
  defineProps<{
    visible: boolean;
    title?: string;
    message?: string;
    confirmLabel?: string;
    danger?: boolean;
  }>(),
  { title: '确认操作', message: '', confirmLabel: '确认', danger: false },
);

const emit = defineEmits<{
  confirm: [];
  cancel: [];
  'update:visible': [value: boolean];
}>();

const opened = ref(false);
watch(
  () => props.visible,
  (val) => { opened.value = val; },
);

function handleConfirm() {
  opened.value = false;
  emit('update:visible', false);
  emit('confirm');
}

function handleCancel() {
  opened.value = false;
  emit('update:visible', false);
  emit('cancel');
}
</script>

<template>
  <Teleport to="body">
    <div v-if="opened" class="confirm-overlay" @click.self="handleCancel">
      <div :class="['confirm-dialog', $slots.default ? 'confirm-dialog-slotted' : '']">
        <div class="confirm-header">
          <AlertTriangle v-if="danger" :size="20" class="confirm-icon-danger" />
          <h3>{{ title }}</h3>
        </div>
        <slot>
          <p v-if="message">{{ message }}</p>
        </slot>
        <div class="confirm-actions">
          <button class="secondary-button" @click="handleCancel">取消</button>
          <button :class="danger ? 'danger-button' : 'primary-button'" @click="handleConfirm">
            {{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: grid;
  place-items: center;
  background: rgba(0, 20, 60, 0.4);
}

.confirm-dialog {
  width: min(400px, 90vw);
  padding: 24px;
  border-radius: 10px;
  background: var(--panel-strong);
  box-shadow: var(--shadow);
}

.confirm-dialog-slotted {
  width: min(860px, 92vw);
}

.confirm-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.confirm-header h3 {
  margin: 0;
  font-size: 17px;
}

.confirm-icon-danger {
  color: var(--red);
}

.confirm-dialog p {
  margin: 0 0 20px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.5;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
}
</style>
