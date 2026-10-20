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
  gap: 6px;
  border: 0;
  cursor: pointer;
  font-weight: 500;
  white-space: nowrap;
  transition: background 0.2s var(--hw-ease), opacity 0.2s;
}
.hw-btn:disabled { opacity: 0.5; cursor: default; }
.hw-btn-sm { height: 32px; padding: 0 12px; border-radius: 999px; font-size: 12px; }
.hw-btn-md { height: 40px; padding: 0 18px; border-radius: 12px; font-size: 13px; }
.hw-btn-primary { background: var(--hw-primary); color: #fff; }
.hw-btn-primary:hover { background: var(--hw-primary-deep); }
.hw-btn-secondary { background: var(--hw-chip); color: var(--hw-secondary); }
.hw-btn-secondary:hover { background: var(--hw-track); }
.hw-btn-ghost { background: transparent; color: var(--hw-secondary); }
.hw-btn-ghost:hover { background: var(--hw-chip); }
.hw-btn-danger {
  background: var(--hw-card);
  color: var(--hw-red);
  border: 1px solid var(--hw-red);
}
.hw-btn-danger:hover { background: var(--hw-chip); }
</style>
