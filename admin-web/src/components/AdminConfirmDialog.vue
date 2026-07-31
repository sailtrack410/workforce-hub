<script setup lang="ts">
import { ref, useId, watch } from 'vue';
import { AlertTriangle } from 'lucide-vue-next';
import AdminDialog from '@/components/AdminDialog.vue';

const props = withDefaults(
  defineProps<{
    visible: boolean;
    title?: string;
    message?: string;
    confirmLabel?: string;
    danger?: boolean;
    closeOnConfirm?: boolean;
    confirmDisabled?: boolean;
    cancelDisabled?: boolean;
  }>(),
  { title: '确认操作', message: '', confirmLabel: '确认', danger: false, closeOnConfirm: true, confirmDisabled: false, cancelDisabled: false },
);

const emit = defineEmits<{
  confirm: [];
  cancel: [];
  'update:visible': [value: boolean];
}>();

const opened = ref(false);
const titleId = useId();
watch(
  () => props.visible,
  (val) => { opened.value = val; },
);

function handleConfirm() {
  if (props.confirmDisabled) return;
  if (props.closeOnConfirm) {
    opened.value = false;
    emit('update:visible', false);
  }
  emit('confirm');
}

function handleCancel() {
  if (props.cancelDisabled) return;
  opened.value = false;
  emit('update:visible', false);
  emit('cancel');
}
</script>

<template>
  <AdminDialog
    v-model:visible="opened"
    :width="$slots.default ? 'min(860px, 92vw)' : 'min(400px, 90vw)'"
    :labelled-by="titleId"
    :show-close="false"
    :close-disabled="cancelDisabled"
    @close="handleCancel"
  >
    <div class="confirm-header">
      <AlertTriangle v-if="danger" :size="20" class="confirm-icon-danger" />
      <h3 :id="titleId">{{ title }}</h3>
    </div>
    <slot>
      <p v-if="message">{{ message }}</p>
    </slot>
    <div class="confirm-actions">
      <button class="secondary-button" type="button" :disabled="cancelDisabled" @click="handleCancel">取消</button>
      <button :class="danger ? 'danger-button' : 'primary-button'" type="button" :disabled="confirmDisabled" @click="handleConfirm">
        {{ confirmLabel }}
      </button>
    </div>
  </AdminDialog>
</template>

<style scoped>
.confirm-header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.confirm-header h3 {
  margin: 0;
  font-size: var(--text-md);
}

.confirm-icon-danger {
  color: var(--red);
}

.confirm-header + p {
  margin: 0 0 var(--space-lg);
  color: var(--muted);
  font-size: var(--text-base);
  line-height: 1.5;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-lg);
}
</style>
