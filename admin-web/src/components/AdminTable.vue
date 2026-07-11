<script setup lang="ts">
import { computed, ref } from 'vue';
import { ArrowUp, ArrowDown } from 'lucide-vue-next';
import type { EntityId } from '@/api/types';
import AdminSkeleton from '@/components/AdminSkeleton.vue';

export interface TableColumn {
  key: string;
  label: string;
  width?: string;
  sortable?: boolean;
}

const props = withDefaults(
  defineProps<{
    columns: TableColumn[];
    rows: any[];
    loading?: boolean;
    rowKey?: string;
    selectedId?: EntityId | null;
    interactive?: boolean;
  }>(),
  { loading: false, rowKey: 'id', selectedId: null, interactive: false },
);

const emit = defineEmits<{
  select: [row: any];
  rowClick: [row: any];
  sort: [key: string, dir: 'asc' | 'desc'];
}>();

const sortKey = ref('');
const sortDir = ref<'asc' | 'desc'>('asc');

const sortedRows = computed(() => {
  if (!sortKey.value) return props.rows;
  const key = sortKey.value;
  const dir = sortDir.value === 'asc' ? 1 : -1;
  return [...props.rows].sort((a, b) => {
    const va = a[key];
    const vb = b[key];
    if (va == null && vb == null) return 0;
    if (va == null) return 1;
    if (vb == null) return -1;
    if (typeof va === 'number' && typeof vb === 'number') return (va - vb) * dir;
    return String(va).localeCompare(String(vb)) * dir;
  });
});

function getKey(row: any): string {
  const val = row[props.rowKey];
  return val != null ? String(val) : JSON.stringify(row);
}

function isSelected(row: any): boolean {
  if (props.selectedId == null) return false;
  return String(row[props.rowKey]) === String(props.selectedId);
}

function handleRowClick(row: any) {
  emit('select', row);
  emit('rowClick', row);
}

function toggleSort(col: TableColumn) {
  if (!col.sortable) return;
  if (sortKey.value === col.key) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc';
  } else {
    sortKey.value = col.key;
    sortDir.value = 'asc';
  }
  emit('sort', sortKey.value, sortDir.value);
}
</script>

<template>
  <div class="data-table">
    <table>
      <thead>
        <tr>
          <th
            v-for="col in columns"
            :key="col.key"
            scope="col"
            :class="{ sortable: col.sortable, sorted: sortKey === col.key }"
            :style="col.width ? { width: col.width } : undefined"
          >
            <button v-if="col.sortable" class="sort-button" type="button" @click="toggleSort(col)">
              {{ col.label }}
              <template v-if="sortKey === col.key">
                <ArrowUp v-if="sortDir === 'asc'" :size="12" />
                <ArrowDown v-else :size="12" />
              </template>
            </button>
            <span v-else class="th-content">{{ col.label }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in sortedRows"
          :key="getKey(row)"
          :class="{ selected: isSelected(row), interactive }"
          :tabindex="interactive ? 0 : undefined"
          :aria-selected="interactive ? isSelected(row) : undefined"
          @click="interactive && handleRowClick(row)"
          @keydown.enter.prevent="interactive && handleRowClick(row)"
          @keydown.space.prevent="interactive && handleRowClick(row)"
        >
          <td v-for="col in columns" :key="col.key">
            <slot :name="`cell-${col.key}`" :row="row">
              {{ row[col.key] }}
            </slot>
          </td>
        </tr>
        <tr v-if="loading && rows.length === 0">
          <td :colspan="columns.length">
            <AdminSkeleton :lines="5" />
          </td>
        </tr>
      </tbody>
    </table>
    <div v-if="!loading && rows.length === 0" class="empty-state">无数据</div>
  </div>
</template>

<style scoped>
.data-table {
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--panel-strong);
}

table { width: 100%; border-collapse: collapse; }

th, td {
  border-bottom: 1px solid var(--line);
  padding: var(--space-md);
  text-align: left;
  vertical-align: middle;
  font-size: var(--text-sm);
}

th {
  background: var(--table-head-bg);
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

tbody tr:last-child td { border-bottom: 0; }

td strong, td span { display: block; }

td span { color: var(--muted); margin-top: 3px; }

tbody tr.interactive { cursor: pointer; }

tbody tr.interactive:hover,
tbody tr.selected {
  background: var(--hover-bg);
}

tbody tr.interactive:focus-visible {
  outline: 2px solid var(--green);
  outline-offset: -2px;
}

.sortable {
  user-select: none;
}

.sortable:hover {
  color: var(--ink);
}

.th-content {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}

.sort-button {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  min-height: 28px;
  margin: calc(var(--space-xs) * -1);
  padding: var(--space-xs);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: inherit;
  font: inherit;
  font-weight: inherit;
}

.sorted {
  color: var(--green-dark);
}
</style>
