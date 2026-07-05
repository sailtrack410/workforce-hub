<script setup lang="ts">
import AppBadge from '../ui/AppBadge.vue';

defineProps<{
  active?: boolean;
  compact?: boolean;
  badgeCount?: number;
}>();
</script>

<template>
  <button class="shell-nav-button" :class="{ active, compact }" type="button">
    <span class="icon-slot">
      <slot name="icon" />
      <AppBadge v-if="badgeCount" tone="danger" kind="count" class="badge">
        {{ badgeCount > 99 ? '99+' : badgeCount }}
      </AppBadge>
    </span>
    <span class="label-slot">
      <slot />
    </span>
  </button>
</template>

<style scoped>
.shell-nav-button {
  position: relative;
  display: inline-grid;
  justify-items: center;
  align-items: center;
  gap: var(--space-4);
  width: 100%;
  min-height: 3.625rem;
  padding: var(--space-8) var(--space-4);
  border: 0;
  border-radius: var(--radius-10, 0.625rem);
  background: transparent;
  color: var(--text-secondary);
  text-align: center;
  transition: all 200ms ease;
}

.shell-nav-button:hover,
.shell-nav-button:focus-visible {
  background: var(--surface-accent);
  color: var(--text-accent);
  outline: none;
}

.shell-nav-button.active {
  background: var(--primary-500);
  color: var(--text-inverse);
}

.shell-nav-button.active::before {
  position: absolute;
  top: 50%;
  left: -0.5rem;
  width: 0.25rem;
  height: 1.5rem;
  border-radius: 999px;
  background: var(--surface-panel);
  content: '';
  transform: translateY(-50%);
}

.shell-nav-button.compact {
  justify-content: center;
  min-height: 3.5rem;
  padding-inline: 0;
}

.shell-nav-button.compact .label-slot {
  font-size: var(--text-caption-md);
}

.icon-slot {
  position: relative;
  display: inline-grid;
  place-items: center;
}

.badge {
  position: absolute;
  top: -0.375rem;
  right: -0.875rem;
}

.label-slot {
  font-size: var(--text-caption-sm);
  font-weight: 600;
  white-space: nowrap;
}
</style>
