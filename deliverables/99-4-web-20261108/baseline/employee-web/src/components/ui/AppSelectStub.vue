<script setup lang="ts">
interface SelectOption {
  label: string;
  value: string | number;
  disabled?: boolean;
}

withDefaults(defineProps<{
  label: string;
  description?: string;
  modelValue?: string | number;
  options?: SelectOption[];
  placeholder?: string;
  disabled?: boolean;
  error?: string;
}>(), {
  description: '',
  modelValue: '',
  options: () => [],
  placeholder: '请选择',
  disabled: false,
  error: '',
});

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

function handleChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLSelectElement).value);
}
</script>

<template>
  <label class="app-select" :class="{ disabled, invalid: error }">
    <span class="select-label">{{ label }}</span>
    <select
      class="select-control"
      :value="modelValue"
      :disabled="disabled"
      @change="handleChange"
    >
      <option value="" disabled>{{ placeholder }}</option>
      <option
        v-for="option in options"
        :key="option.value"
        :value="option.value"
        :disabled="option.disabled"
      >
        {{ option.label }}
      </option>
    </select>
    <span v-if="error" class="select-error">{{ error }}</span>
    <span v-else-if="description" class="select-description">{{ description }}</span>
  </label>
</template>

<style scoped>
.app-select {
  display: grid;
  gap: var(--space-6);
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
}

.select-label {
  font-weight: 700;
  color: var(--text-primary);
}

.select-control {
  width: 100%;
  min-height: 40px;
  padding: 0 var(--space-12);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-8);
  background: var(--surface-panel);
  color: var(--text-primary);
  font: inherit;
}

.select-control:focus {
  outline: 2px solid var(--accent-signal-soft);
  border-color: var(--accent-signal);
}

.select-control:disabled {
  cursor: not-allowed;
  opacity: 0.64;
}

.select-description,
.select-error {
  min-height: 18px;
}

.select-error {
  color: var(--text-danger);
}
</style>
