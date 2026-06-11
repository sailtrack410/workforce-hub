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
  }>(),
  { loading: false, rowKey: 'id', selectedId: null },
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
            :class="{ sortable: col.sortable, sorted: sortKey === col.key }"
            :style="col.width ? { width: col.width } : undefined"
            @click="toggleSort(col)"
          >
            <span class="th-content">
              {{ col.label }}
              <template v-if="col.sortable && sortKey === col.key">
                <ArrowUp v-if="sortDir === 'asc'" :size="12" />
                <ArrowDown v-else :size="12" />
              </template>
            </span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="row in sortedRows"
          :key="getKey(row)"
          :class="{ selected: isSelected(row) }"
          @click="handleRowClick(row)"
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
.data-table { overflow: auto; }

table { width: 100%; border-collapse: collapse; }

th, td {
  border-bottom: 1px solid var(--line);
  padding: 11px var(--space-md);
  text-align: left;
  vertical-align: top;
  font-size: var(--text-sm);
}

th { color: var(--muted); font-size: var(--text-xs); }

td strong, td span { display: block; }

td span { color: var(--muted); margin-top: 3px; }

tbody tr { cursor: pointer; }

tbody tr:hover,
tbody tr.selected {
  background: var(--hover-bg);
}

.sortable {
  cursor: pointer;
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

.sorted {
  color: var(--green-dark);
}
</style>
