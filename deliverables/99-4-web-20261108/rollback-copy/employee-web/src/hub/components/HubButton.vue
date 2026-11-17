<script setup lang="ts">
/**
 * HubButton：按钮，画布规范（pill/圆角 12、primary 蓝、chip 灰底、danger 红描边）。
 * 全新实现，不依赖旧 AppButton。
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
  <button
    class="hub-btn"
    :class="[`hub-btn-${variant}`, `hub-btn-${size}`]"
    :type="type"
    :disabled="disabled"
  >
    <slot />
  </button>
</template>

<style scoped>
.hub-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  cursor: pointer;
  font-family: "Noto Sans SC", sans-serif;
  font-weight: 500;
  white-space: nowrap;
  transition: background 0.2s cubic-bezier(0.33, 1, 0.68, 1), opacity 0.2s;
}
.hub-btn:disabled {
  opacity: 0.5;
  cursor: default;
}
.hub-btn-sm { height: 32px; padding: 0 12px; border-radius: 999px; font-size: 12px; }
.hub-btn-md { height: 40px; padding: 0 18px; border-radius: 12px; font-size: 13px; }
.hub-btn-primary {
  background: var(--hub-primary, #1677ff);
  color: #fff;
}
.hub-btn-primary:hover { background: var(--hub-primary-deep, #124da3); }
.hub-btn-secondary {
  background: var(--hub-chip, #f2f5f9);
  color: var(--hub-secondary, #424b56);
}
.hub-btn-secondary:hover { background: var(--hub-track, #eaeff5); }
.hub-btn-ghost {
  background: transparent;
  color: var(--hub-secondary, #424b56);
}
.hub-btn-ghost:hover { background: var(--hub-chip, #f2f5f9); }
.hub-btn-danger {
  background: var(--hub-card, #fff);
  color: var(--hub-red, #ff5a4e);
  border: 1px solid var(--hub-red, #ff5a4e);
}
.hub-btn-danger:hover { background: var(--hub-chip, #f2f5f9); }
</style>
