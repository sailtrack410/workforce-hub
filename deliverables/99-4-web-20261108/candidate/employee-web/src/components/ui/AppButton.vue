<script setup lang="ts">
const props = withDefaults(defineProps<{
  variant?: 'primary' | 'secondary' | 'ghost' | 'destructive' | 'link';
  size?: 'sm' | 'md' | 'lg';
  block?: boolean;
  selected?: boolean;
  disabled?: boolean;
  type?: 'button' | 'submit' | 'reset';
}>(), {
  variant: 'secondary',
  size: 'md',
  block: false,
  selected: false,
  disabled: false,
  type: 'button',
});
</script>

<template>
  <button
    class="ui-button"
    :class="[
      `variant-${props.variant}`,
      `size-${props.size}`,
      { block: props.block, selected: props.selected },
    ]"
    :type="props.type"
    :disabled="props.disabled"
  >
    <slot />
  </button>
</template>

<style scoped>
.ui-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-8);
  min-height: var(--control-height-md);
  padding: var(--space-8) var(--space-16);
  border: 1px solid transparent;
  border-radius: var(--radius-8);
  font-family: var(--font-family-body);
  font-size: var(--text-body-sm);
  font-weight: 600;
  line-height: 1;
  transition: background var(--transition-fast), border-color var(--transition-fast), color var(--transition-fast), box-shadow var(--transition-fast), transform var(--transition-fast);
}

.ui-button:active:not(:disabled) {
  transform: scale(0.98);
}

.ui-button:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--surface-panel), 0 0 0 4px var(--focus-ring);
}

.ui-button:disabled {
  cursor: not-allowed;
  background: var(--surface-panel-muted);
  color: var(--text-tertiary);
  border-color: var(--border-subtle);
  transform: none;
}

.ui-button.block {
  width: 100%;
}

.ui-button.size-sm {
  min-height: var(--control-height-sm);
  padding-inline: var(--space-12);
  font-size: var(--text-caption-md);
}

.ui-button.size-lg {
  min-height: var(--control-height-lg);
  padding-inline: var(--space-20);
  font-size: var(--text-body-md);
}

.ui-button.variant-primary {
  background: var(--primary-500);
  color: var(--text-inverse);
}

.ui-button.variant-primary:hover:not(:disabled),
.ui-button.variant-primary:focus-visible {
  background: var(--primary-600);
}

.ui-button.variant-secondary {
  background: var(--surface-panel);
  border-color: var(--border-default);
  color: var(--text-primary);
}

.ui-button.variant-secondary:hover:not(:disabled),
.ui-button.variant-secondary.selected {
  border-color: var(--border-accent);
  background: var(--surface-accent);
  color: var(--text-accent);
}

.ui-button.variant-ghost {
  background: transparent;
  color: var(--text-secondary);
}

.ui-button.variant-ghost:hover:not(:disabled),
.ui-button.variant-ghost.selected {
  background: color-mix(in srgb, var(--primary-100) 50%, transparent);
  color: var(--text-accent);
}

.ui-button.variant-destructive {
  background: var(--danger-500);
  color: var(--text-inverse);
}

.ui-button.variant-destructive:hover:not(:disabled) {
  background: var(--danger-600);
}

.ui-button.variant-link {
  min-height: auto;
  padding: 0;
  background: transparent;
  color: var(--text-accent);
}

.ui-button.variant-link:hover:not(:disabled) {
  color: var(--primary-600);
}
</style>
