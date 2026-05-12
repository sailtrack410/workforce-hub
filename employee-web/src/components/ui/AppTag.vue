<script setup lang="ts">
import { X } from 'lucide-vue-next';

const emit = defineEmits<{
  close: [];
}>();

const props = withDefaults(defineProps<{
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
  selected?: boolean;
  closable?: boolean;
}>(), {
  tone: 'neutral',
  selected: false,
  closable: false,
});
</script>

<template>
  <span class="ui-tag" :class="[props.tone, { selected: props.selected }]">
    <slot />
    <button v-if="props.closable" class="close-button" type="button" @click="emit('close')">
      <X :size="12" />
    </button>
  </span>
</template>

<style scoped>
.ui-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--space-8);
  min-height: 1.75rem;
  padding: 0 var(--space-12);
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: var(--text-caption-md);
  font-weight: 600;
}

.ui-tag.primary {
  background: var(--primary-50);
  color: var(--primary-700);
}

.ui-tag.success {
  background: var(--success-50);
  color: var(--success-700);
}

.ui-tag.warning {
  background: var(--warning-50);
  color: var(--warning-700);
}

.ui-tag.danger {
  background: var(--danger-50);
  color: var(--danger-700);
}

.ui-tag.neutral {
  background: var(--surface-panel-muted);
  color: var(--text-secondary);
}

.ui-tag.selected {
  border-color: currentColor;
}

.close-button {
  display: inline-grid;
  width: 1rem;
  height: 1rem;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
}
</style>
