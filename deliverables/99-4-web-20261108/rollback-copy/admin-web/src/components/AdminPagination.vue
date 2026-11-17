<script setup lang="ts">
import { computed } from 'vue';
import { ChevronLeft, ChevronRight } from 'lucide-vue-next';

const props = withDefaults(
  defineProps<{
    pageNo: number;
    pageSize: number;
    total: number;
    pageSizeOptions?: number[];
  }>(),
  { pageSizeOptions: () => [10, 20, 50] },
);

const emit = defineEmits<{
  'update:pageNo': [value: number];
  'update:pageSize': [value: number];
}>();

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)));

const visible = computed(() => props.total > props.pageSize);

const pages = computed(() => {
  const current = props.pageNo;
  const last = totalPages.value;
  if (last <= 7) return Array.from({ length: last }, (_, i) => i + 1);
  const result: (number | '...')[] = [1];
  if (current > 3) result.push('...');
  for (let i = Math.max(2, current - 1); i <= Math.min(last - 1, current + 1); i++) {
    result.push(i);
  }
  if (current < last - 2) result.push('...');
  result.push(last);
  return result;
});

function goTo(page: number) {
  if (page < 1 || page > totalPages.value) return;
  emit('update:pageNo', page);
}

function onPageSizeChange(e: Event) {
  const size = Number((e.target as HTMLSelectElement).value);
  emit('update:pageSize', size);
  emit('update:pageNo', 1);
}
</script>

<template>
  <nav v-if="visible" class="pagination">
    <button
      class="icon-button"
      :disabled="pageNo <= 1"
      title="上一页"
      @click="goTo(pageNo - 1)"
    >
      <ChevronLeft :size="16" />
    </button>

    <template v-for="(p, i) in pages" :key="i">
      <span v-if="p === '...'" class="ellipsis">...</span>
      <button
        v-else
        :class="['page-btn', { active: p === pageNo }]"
        @click="goTo(p)"
      >
        {{ p }}
      </button>
    </template>

    <button
      class="icon-button"
      :disabled="pageNo >= totalPages"
      title="下一页"
      @click="goTo(pageNo + 1)"
    >
      <ChevronRight :size="16" />
    </button>

    <span class="page-info">{{ pageNo }} / {{ totalPages }} 页，共 {{ total }} 条</span>

    <select class="page-size-select" :value="pageSize" @change="onPageSizeChange">
      <option v-for="size in pageSizeOptions" :key="size" :value="size">
        {{ size }} 条/页
      </option>
    </select>
  </nav>
</template>

<style scoped>
.pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
  margin-top: var(--space-base);
  padding-top: var(--space-md);
  border-top: 1px solid var(--line);
  flex-wrap: wrap;
}

.pagination > .icon-button {
  width: 32px;
  min-height: 32px;
}

.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  color: var(--ink);
  font-size: var(--text-sm);
  font-weight: 700;
  cursor: pointer;
  padding: 0 6px;
}

.page-btn:hover {
  border-color: var(--green);
  color: var(--green);
}

.page-btn.active {
  background: var(--green);
  border-color: var(--green);
  color: var(--panel-strong);
}

.ellipsis {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  color: var(--muted);
  font-size: var(--text-sm);
}

.page-info {
  margin-left: var(--space-sm);
  color: var(--muted);
  font-size: var(--text-xs);
  white-space: nowrap;
}

.page-size-select {
  width: auto;
  min-height: 32px;
  margin-left: var(--space-sm);
  padding: 2px var(--space-sm);
  font-size: var(--text-xs);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
}

@media (max-width: 640px) {
  .pagination { justify-content: flex-start; }
  .page-info { margin-left: 0; }
  .page-size-select { margin-left: 0; }
}
</style>
