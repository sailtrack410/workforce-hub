<script setup lang="ts">
defineProps<{
  label: string;
  description?: string;
}>();
</script>

<template>
  <div class="stub-card">
    <strong>{{ label }}</strong>
    <span v-if="description">{{ description }}</span>
  </div>
</template>

<style scoped>
.stub-card {
  display: grid;
  gap: var(--space-8);
  padding: var(--space-16);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-12);
  background: var(--surface-panel);
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
}

.stub-card strong {
  color: var(--text-primary);
}
</style>
