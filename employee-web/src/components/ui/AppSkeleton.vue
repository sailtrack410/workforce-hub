<template>
  <span class="ui-skeleton" />
</template>

<style scoped>
.ui-skeleton {
  display: block;
  width: 100%;
  height: 1rem;
  border-radius: var(--radius-6);
  background:
    linear-gradient(
      90deg,
      color-mix(in srgb, var(--surface-panel-strong) 80%, transparent) 0%,
      color-mix(in srgb, var(--surface-panel) 95%, transparent) 50%,
      color-mix(in srgb, var(--surface-panel-strong) 80%, transparent) 100%
    );
  background-size: 220% 100%;
  animation: shimmer 1.2s ease infinite;
}

@keyframes shimmer {
  to {
    background-position: -220% 0;
  }
}
</style>
