<script setup lang="ts">
const props = withDefaults(defineProps<{
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
  kind?: 'label' | 'count';
}>(), {
  tone: 'neutral',
  kind: 'label',
});
</script>

<template>
  <span class="ui-badge" :class="[props.tone, props.kind]">
    <slot />
  </span>
</template>

<style scoped>
.ui-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 1.375rem;
  padding: 0 var(--space-8);
  border-radius: 999px;
  font-size: var(--text-caption-md);
  font-weight: 700;
  white-space: nowrap;
}

.ui-badge.count {
  min-width: 1.375rem;
  padding-inline: var(--space-4);
}

.ui-badge.primary {
  background: var(--primary-100);
  color: var(--primary-700);
}

.ui-badge.success {
  background: var(--success-100);
  color: var(--success-700);
}

.ui-badge.warning {
  background: var(--warning-100);
  color: var(--warning-700);
}

.ui-badge.danger {
  background: var(--danger-500);
  color: var(--text-inverse);
}

.ui-badge.neutral {
  background: var(--surface-panel-strong);
  color: var(--text-secondary);
}
</style>
