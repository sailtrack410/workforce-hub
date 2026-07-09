<script setup lang="ts">
import type { AdminAgentTaskResponse } from '@/api/types';
import type { EntityId } from '@/api/types';
import { formatDateTime, humanizeText, statusLabel } from '@/utils/format';
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
  { key: 'agentName', label: 'AI 助手' },
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
      <select v-model="store.filters.contextType">
        <option value="">全部场景</option>
        <option value="USER_QUERY">员工咨询</option>
        <option value="WF_REQUEST">审批申请</option>
        <option value="IM_GROUP">群聊消息</option>
      </select>
      <select v-model="store.filters.triggeredByType">
        <option value="">全部来源</option>
        <option value="USER">员工</option>
        <option value="ADMIN">管理员</option>
        <option value="SYSTEM">系统</option>
      </select>
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
        {{ row.contextDisplayName || row.contextTypeName || 'AI 任务' }}
      </template>
      <template #cell-agentName="{ row }">
        {{ humanizeText(row.agentName) || 'AI 助手' }}
      </template>
      <template #cell-status="{ row }">
        <span class="status-pill">{{ row.statusName || statusLabel(row.status) }}</span>
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
