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
  border: 0;
  border-radius: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  border-bottom: 1px solid #eef1f5;
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

.ui-list-item:hover:not(:disabled) {
  background: #eef4ff;
  color: var(--text-primary);
}

.ui-list-item.active {
  background: #e8f2ff;
  color: var(--text-primary);
}

.ui-list-item.disabled {
  opacity: 0.56;
  cursor: not-allowed;
}
</style>
