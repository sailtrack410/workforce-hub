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
.compact-list { display: grid; gap: 9px; }

.compact-list :slotted(article) {
  display: grid;
  gap: 5px;
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--panel-strong);
}

.compact-list :slotted(article.clickable) { cursor: pointer; }

.compact-list :slotted(article.selected) {
  background: var(--hover-bg);
}

.compact-list :slotted(span) { color: var(--muted); font-size: var(--text-xs); }
</style>
