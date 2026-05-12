<template>
  <div class="ui-loading-state" role="status" aria-live="polite">
    <span class="loading-orbit" />
    <span>正在同步内容…</span>
  </div>
</template>

<style scoped>
.ui-loading-state {
  display: inline-flex;
  align-items: center;
  gap: var(--space-12);
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
}

.loading-orbit {
  width: 1rem;
  height: 1rem;
  border: 2px solid color-mix(in srgb, var(--primary-200) 65%, transparent);
  border-top-color: var(--primary-600);
  border-radius: 999px;
  animation: orbit 0.8s linear infinite;
}

@keyframes orbit {
  to {
    transform: rotate(360deg);
  }
}
</style>
