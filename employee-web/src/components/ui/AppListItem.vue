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
  gap: var(--space-16);
  padding: var(--space-16);
  border: 0;
  border-radius: var(--radius-12);
  background: transparent;
  color: inherit;
  text-align: left;
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

.ui-list-item:hover:not(:disabled) {
  background: color-mix(in srgb, var(--surface-panel-muted) 62%, transparent);
  color: var(--text-primary);
}

.ui-list-item.active {
  background: color-mix(in srgb, var(--surface-panel-strong) 72%, var(--surface-panel));
  color: var(--text-primary);
}

.ui-list-item.disabled {
  opacity: 0.56;
  cursor: not-allowed;
}
</style>
