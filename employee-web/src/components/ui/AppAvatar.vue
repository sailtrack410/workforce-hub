<script setup lang="ts">
const props = withDefaults(defineProps<{
  label: string;
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
  size?: 'sm' | 'md' | 'lg';
  status?: 'online' | 'busy' | 'away' | 'offline' | '';
}>(), {
  tone: 'primary',
  size: 'md',
  status: '',
});
</script>

<template>
  <span class="ui-avatar-wrap">
    <span class="ui-avatar" :class="[props.tone, props.size]">
      <slot>{{ props.label.slice(0, 2) }}</slot>
    </span>
    <span v-if="props.status" class="ui-avatar-status" :class="props.status" />
  </span>
</template>

<style scoped>
.ui-avatar-wrap {
  position: relative;
  display: inline-grid;
}

.ui-avatar {
  display: grid;
  place-items: center;
  border-radius: var(--radius-8);
  font-size: var(--text-caption-md);
  font-weight: 750;
}

.ui-avatar.sm {
  width: 1.75rem;
  height: 1.75rem;
  border-radius: var(--radius-6);
}

.ui-avatar.md {
  width: 2.25rem;
  height: 2.25rem;
}

.ui-avatar.lg {
  width: 2.75rem;
  height: 2.75rem;
  border-radius: var(--radius-12);
}

.ui-avatar.primary {
  background: var(--primary-100);
  color: var(--primary-700);
}

.ui-avatar.success {
  background: var(--success-100);
  color: var(--success-700);
}

.ui-avatar.warning {
  background: var(--warning-100);
  color: var(--warning-700);
}

.ui-avatar.danger {
  background: var(--danger-100);
  color: var(--danger-700);
}

.ui-avatar.neutral {
  background: var(--surface-panel-strong);
  color: var(--text-secondary);
}

.ui-avatar-status {
  position: absolute;
  right: -0.0625rem;
  bottom: -0.0625rem;
  width: 0.625rem;
  height: 0.625rem;
  border: 2px solid var(--surface-panel);
  border-radius: 999px;
}

.ui-avatar-status.online {
  background: var(--success-500);
}

.ui-avatar-status.busy {
  background: var(--danger-500);
}

.ui-avatar-status.away {
  background: var(--warning-500);
}

.ui-avatar-status.offline {
  background: var(--neutral-400);
}
</style>
