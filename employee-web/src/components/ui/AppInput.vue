<script setup lang="ts">
const model = defineModel<string>({ default: '' });

const props = withDefaults(defineProps<{
  type?: string;
  placeholder?: string;
  disabled?: boolean;
}>(), {
  type: 'text',
  placeholder: '',
  disabled: false,
});
</script>

<template>
  <input
    v-model="model"
    class="ui-input"
    :type="props.type"
    :placeholder="props.placeholder"
    :disabled="props.disabled"
  />
</template>

<style scoped>
.ui-input {
  width: 100%;
  min-height: var(--control-height-md);
  padding: var(--space-8) var(--space-12);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-8);
  background: var(--surface-panel);
  color: var(--text-primary);
  font-family: var(--font-family-body);
  font-size: var(--text-body-md);
  transition: background var(--transition-fast), border-color var(--transition-fast), box-shadow var(--transition-fast);
}

.ui-input::placeholder {
  color: var(--text-tertiary);
}

.ui-input:hover:not(:disabled) {
  border-color: var(--border-default);
}

.ui-input:focus-visible {
  outline: none;
  border-color: var(--border-accent);
  background: var(--surface-panel);
  box-shadow: 0 0 0 2px var(--surface-panel), 0 0 0 4px var(--focus-ring);
}

.ui-input:disabled {
  cursor: not-allowed;
  color: var(--text-tertiary);
}
</style>
