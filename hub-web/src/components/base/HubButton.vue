<script setup lang="ts">
/**
 * HubButton：按钮（画布规范：primary 蓝 / secondary 灰底 / ghost / danger 红描边）。
 */
withDefaults(defineProps<{
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  size?: 'sm' | 'md';
  disabled?: boolean;
  type?: 'button' | 'submit';
}>(), {
  variant: 'secondary',
  size: 'md',
  disabled: false,
  type: 'button',
});
</script>

<template>
  <button class="hw-btn" :class="[`hw-btn-${variant}`, `hw-btn-${size}`]" :type="type" :disabled="disabled">
    <slot />
  </button>
</template>

<style scoped>
.hw-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--hw-space-2);
  border: 1px solid transparent;
  border-radius: var(--hw-radius-control);
  cursor: pointer;
  font-weight: 500;
  white-space: nowrap;
  line-height: var(--hw-line-body);
  transition: background var(--hw-duration-fast) var(--hw-ease), border-color var(--hw-duration-fast), color var(--hw-duration-fast);
}
.hw-btn-sm { height: var(--hw-control-sm); padding: 0 var(--hw-space-3); font-size: 12px; }
.hw-btn-md { height: var(--hw-control-md); padding: 0 var(--hw-space-4); font-size: var(--hw-font-body); }
.hw-btn-primary { background: var(--hw-primary-fill); color: var(--hw-on-primary); }
.hw-btn-primary:hover:not(:disabled) { background: var(--hw-primary-fill-hover); }
.hw-btn-primary:active:not(:disabled) { background: var(--hw-primary-fill-active); }
.hw-btn-secondary { background: var(--hw-card); border-color: var(--hw-control-border); color: var(--hw-ink); }
.hw-btn-secondary:hover:not(:disabled) { background: var(--hw-inner); }
.hw-btn-secondary:active:not(:disabled) { background: var(--hw-track); }
.hw-btn-ghost { background: transparent; color: var(--hw-secondary); }
.hw-btn-ghost:hover:not(:disabled) { background: var(--hw-chip); color: var(--hw-ink); }
.hw-btn-ghost:active:not(:disabled) { background: var(--hw-track); }
.hw-btn-danger {
  background: var(--hw-card);
  color: var(--hw-red);
  border: 1px solid var(--hw-red);
}
.hw-btn-danger:hover:not(:disabled), .hw-btn-danger:active:not(:disabled) { background: var(--hw-red-bg); }
.hw-btn:disabled { background: var(--hw-track); border-color: transparent; color: var(--hw-disabled-text); cursor: not-allowed; }
.hw-btn-ghost:disabled { background: transparent; }
</style>
