<template>
  <span class="ui-skeleton" />
</template>

<style scoped>
.ui-skeleton {
  display: block;
  width: 100%;
  height: 1rem;
  border-radius: var(--radius-6);
  background: var(--surface-panel-strong);
  animation: shimmer 1.2s ease infinite;
}

@keyframes shimmer {
  50% {
    opacity: 0.42;
  }
  to {
    opacity: 1;
  }
}
</style>
