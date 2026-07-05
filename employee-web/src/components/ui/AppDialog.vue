<script setup lang="ts">
const props = defineProps<{
  flush?: boolean;
  wide?: boolean;
}>();
</script>

<template>
  <Teleport to="body">
    <div class="ui-overlay">
      <div class="ui-dialog" :class="{ flush: props.flush, wide: props.wide }">
        <slot />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.ui-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: grid;
  place-items: center;
  padding: var(--space-24);
  background: rgba(15, 20, 18, 0.42);
  overflow: auto;
}

.ui-dialog {
  width: min(36rem, 100%);
  max-height: calc(100vh - var(--space-56));
  overflow: auto;
  padding: var(--space-24);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-12);
  background: var(--surface-panel);
  box-shadow: var(--shadow-md);
}

.ui-dialog.wide {
  width: min(56rem, 100%);
}

.ui-dialog.flush {
  width: auto;
  max-height: none;
  padding: 0;
  border: 0;
  background: transparent;
  overflow: hidden;
}
</style>
