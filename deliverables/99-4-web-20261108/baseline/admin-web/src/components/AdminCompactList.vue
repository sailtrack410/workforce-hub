<script setup lang="ts">
withDefaults(
  defineProps<{
    loading?: boolean;
    empty?: boolean;
  }>(),
  { loading: false, empty: false },
);
</script>

<template>
  <div class="compact-list">
    <slot />
    <div v-if="!loading && empty" class="empty-state">无数据</div>
  </div>
</template>

<style scoped>
.compact-list { display: grid; gap: var(--space-sm); }

.compact-list :slotted(article) {
  display: grid;
  gap: var(--space-xs);
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
  transition: border-color 140ms ease, background-color 140ms ease;
}

.compact-list :slotted(article.clickable) { cursor: pointer; }

.compact-list :slotted(article.clickable:hover) {
  border-color: var(--green-light);
  background: var(--hover-bg);
}

.compact-list :slotted(article.selected) {
  background: var(--hover-bg);
  box-shadow: inset 3px 0 0 var(--green);
}

.compact-list :slotted(span) { color: var(--muted); font-size: var(--text-xs); }
</style>
