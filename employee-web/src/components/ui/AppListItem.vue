<script setup lang="ts">
defineProps<{
  active?: boolean;
  disabled?: boolean;
}>()

const emit = defineEmits<{
  click: [event: MouseEvent];
}>();
</script>

<template>
  <button class="ui-list-item" :class="{ active, disabled }" type="button" :disabled="disabled" @click="emit('click', $event)">
    <slot />
  </button>
</template>

<style scoped>
.ui-list-item {
  display: grid;
  width: 100%;
  gap: var(--space-12);
  padding: var(--space-12) var(--space-16);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-panel);
  color: inherit;
  text-align: left;
  transition: background var(--transition-fast), border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.ui-list-item:hover:not(:disabled) {
  border-color: var(--border-accent);
  background: var(--surface-accent);
  color: var(--text-primary);
}

.ui-list-item.active {
  border-color: var(--primary-300);
  background: var(--surface-accent);
  color: var(--text-primary);
}

.ui-list-item.disabled {
  opacity: 0.56;
  cursor: not-allowed;
}
</style>
