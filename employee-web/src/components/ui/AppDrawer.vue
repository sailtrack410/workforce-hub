<template>
  <div class="ui-drawer">
    <slot />
  </div>
</template>

<style scoped>
.ui-drawer {
  height: 100%;
  padding: var(--space-24);
  border-left: 1px solid var(--border-subtle);
  background: color-mix(in srgb, var(--surface-panel) 88%, transparent);
  backdrop-filter: blur(18px);
}
</style>
