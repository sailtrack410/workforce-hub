<script setup lang="ts">
import type { AdminAgentTaskResponse } from '@/api/types';
import type { EntityId } from '@/api/types';
import { formatDateTime, shortId, statusLabel } from '@/utils/format';
import { useAgentTasksStore } from '@/stores/agentTasks';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminPagination from '@/components/AdminPagination.vue';

defineProps<{
  items: AdminAgentTaskResponse[];
  loading: boolean;
}>();

const emit = defineEmits<{
  select: [task: AdminAgentTaskResponse];
  search: [];
}>();

const store = useAgentTasksStore();

const columns: TableColumn[] = [
  { key: 'taskId', label: '任务' },
  { key: 'agentName', label: 'Agent' },
  { key: 'status', label: '状态' },
  { key: 'createdAt', label: '创建时间' },
];
</script>

<template>
  <AdminPanel :span="7" title="任务列表">
    <AdminFilterBar @search="$emit('search')">
      <select v-model="store.filters.status">
        <option value="">全部状态</option>
        <option value="PENDING">待处理</option>
        <option value="RUNNING">运行中</option>
        <option value="WAITING_HUMAN_CONFIRM">待确认</option>
        <option value="SUCCEEDED">成功</option>
        <option value="FAILED">失败</option>
        <option value="CANCELLED">已取消</option>
      </select>
      <input v-model="store.filters.contextType" placeholder="contextType" />
      <input v-model="store.filters.triggeredByType" placeholder="triggeredByType" />
    </AdminFilterBar>
    <AdminTable
      :columns="columns"
      :rows="items"
      :loading="loading"
      row-key="taskId"
      :selected-id="store.selectedTaskId"
      @select="(row) => $emit('select', row)"
    >
      <template #cell-taskId="{ row }">
        #{{ shortId(row.taskId) }}
      </template>
      <template #cell-agentName="{ row }">
        {{ row.agentName || row.agentCode || shortId(row.agentId) }}
      </template>
      <template #cell-status="{ row }">
        <span class="status-pill">{{ statusLabel(row.status) }}</span>
      </template>
      <template #cell-createdAt="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>
    </AdminTable>
    <AdminPagination
      :page-no="store.pagination.pageNo"
      :page-size="store.pagination.pageSize"
      :total="store.pagination.total"
      @update:page-no="(v) => { store.pagination.pageNo = v; $emit('search'); }"
      @update:page-size="(v) => { store.pagination.pageSize = v; store.pagination.pageNo = 1; $emit('search'); }"
    />
  </AdminPanel>
</template>
