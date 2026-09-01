<script setup lang="ts">
import AppButton from './AppButton.vue';

defineProps<{
  options: string[];
  modelValue: string;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();
</script>

<template>
  <div class="ui-segmented" role="tablist">
    <AppButton
      v-for="option in options"
      :key="option"
      variant="ghost"
      size="sm"
      :selected="modelValue === option"
      @click="emit('update:modelValue', option)"
    >
      {{ option }}
    </AppButton>
  </div>
</template>

<style scoped>
.ui-segmented {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-12);
  background: var(--surface-sunken);
}
</style>
