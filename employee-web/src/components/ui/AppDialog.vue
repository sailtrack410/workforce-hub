<template>
  <div class="ui-overlay">
    <div class="ui-dialog">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.ui-overlay {
  position: fixed;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgba(15, 20, 18, 0.42);
  backdrop-filter: blur(10px);
}

.ui-dialog {
  width: min(36rem, calc(100vw - 2rem));
  padding: var(--space-24);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-12);
  background: var(--surface-panel);
  box-shadow: var(--shadow-lg);
}
</style>
