<script setup lang="ts">
import { Search } from 'lucide-vue-next';

defineEmits<{
  search: [];
}>();
</script>

<template>
  <form class="filter-row" @submit.prevent="$emit('search')">
    <slot />
    <button class="secondary-button" type="submit">
      <Search :size="16" />
      <span>筛选</span>
    </button>
  </form>
</template>

<style scoped>
.filter-row {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  width: 100%;
}

.filter-row :deep(input),
.filter-row :deep(select) { max-width: 210px; }
</style>
