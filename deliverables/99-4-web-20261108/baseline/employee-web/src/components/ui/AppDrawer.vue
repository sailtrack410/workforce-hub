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
  background: var(--surface-panel);
}
</style>
