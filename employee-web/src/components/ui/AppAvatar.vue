<script setup lang="ts">
const props = withDefaults(defineProps<{
  label: string;
  image?: string;
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
  size?: 'sm' | 'md' | 'lg' | 'xl';
  status?: 'online' | 'busy' | 'away' | 'offline' | '';
}>(), {
  image: '',
  tone: 'primary',
  size: 'md',
  status: '',
});
</script>

<template>
  <span class="ui-avatar-wrap">
    <span class="ui-avatar" :class="[props.tone, props.size, { 'has-image': !!props.image }]">
      <img v-if="props.image" :src="props.image" :alt="props.label" />
      <slot v-else>{{ props.label.slice(0, 2) }}</slot>
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
  border-radius: 999px;
  font-size: var(--text-caption-md);
  font-weight: 750;
  overflow: hidden;
}

.ui-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ui-avatar.sm {
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 999px;
}

.ui-avatar.md {
  width: 2.25rem;
  height: 2.25rem;
}

.ui-avatar.lg {
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 999px;
}

.ui-avatar.xl {
  width: 4.75rem;
  height: 4.75rem;
  border-radius: 999px;
}

.ui-avatar.primary {
  background: linear-gradient(135deg, #4fa2ff, #1677ff);
  color: #fff;
}

.ui-avatar.success {
  background: linear-gradient(135deg, #53d18a, #16a36a);
  color: #fff;
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
  background: #e6eaf0;
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
