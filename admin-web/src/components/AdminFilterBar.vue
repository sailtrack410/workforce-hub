<script setup lang="ts">
import { Search } from 'lucide-vue-next';

withDefaults(defineProps<{
  showSearch?: boolean;
}>(), { showSearch: true });

defineEmits<{
  search: [];
}>();
</script>

<template>
  <form class="filter-row" @submit.prevent="$emit('search')">
    <slot />
    <button v-if="showSearch" class="secondary-button" type="submit">
      <Search :size="16" />
      <span>筛选</span>
    </button>
  </form>
</template>

<style scoped>
.filter-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  flex-wrap: wrap;
  margin-bottom: var(--space-base);
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--table-head-bg);
}

.filter-row :deep(input),
.filter-row :deep(select) {
  max-width: 220px;
  min-width: 160px;
}

.filter-row > .secondary-button:last-child {
  margin-left: auto;
}

@media (max-width: 640px) {
  .filter-row :deep(input),
  .filter-row :deep(select) {
    max-width: none;
    min-width: min(100%, 180px);
    flex: 1 1 180px;
  }

  .filter-row > .secondary-button:last-child {
    margin-left: 0;
  }
}
</style>
