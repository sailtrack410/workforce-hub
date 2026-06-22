<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type {
  AdminAuditLogResponse,
  AdminPermissionResponse,
  AdminRoleResponse,
  PageResult,
} from '@/api/types';
import { errorMessage, formatDateTime, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';

const toast = useToast();
const loading = ref(false);
const roles = ref<AdminRoleResponse[]>([]);
const permissions = ref<AdminPermissionResponse[]>([]);
const auditPage = ref<PageResult<AdminAuditLogResponse>>({ pageNo: 1, pageSize: 20, total: 0, list: [] });
const filters = reactive({ actionType: '', targetType: '' });

const roleColumns: TableColumn[] = [
  { key: 'roleCode', label: '编码' },
  { key: 'roleName', label: '名称' },
  { key: 'status', label: '状态' },
];

const permissionColumns: TableColumn[] = [
  { key: 'permCode', label: '编码' },
  { key: 'permName', label: '名称' },
  { key: 'moduleCode', label: '模块' },
  { key: 'actionCode', label: '动作' },
  { key: 'status', label: '状态' },
];

const auditColumns: TableColumn[] = [
  { key: 'occurredAtText', label: '时间' },
  { key: 'operator', label: '操作人' },
  { key: 'actionType', label: '动作' },
  { key: 'target', label: '对象' },
  { key: 'result', label: '结果' },
];

const auditRows = computed(() => auditPage.value.list.map((row) => ({
  ...row,
  occurredAtText: formatDateTime(row.occurredAt),
  operator: `${row.operatorType}${row.operatorId ? ` #${row.operatorId}` : ''}`,
  target: `${row.targetType ?? '-'}${row.targetId ? ` #${row.targetId}` : ''}`,
  result: statusLabel(row.result ?? ''),
})));

onMounted(loadAll);

async function loadAll() {
  loading.value = true;
  try {
    const [roleRows, permissionRows, logs] = await Promise.all([
      adminApi.system.roles(),
      adminApi.system.permissions(),
      adminApi.system.auditLogs({
        pageNo: 1,
        pageSize: 20,
        actionType: filters.actionType.trim() || undefined,
        targetType: filters.targetType.trim() || undefined,
      }),
    ]);
    roles.value = roleRows;
    permissions.value = permissionRows;
    auditPage.value = logs;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="page-grid">
    <AdminPanel :span="5" title="角色">
      <template #actions>
        <button class="icon-button" title="刷新" @click="loadAll"><RefreshCcw :size="16" /></button>
      </template>
      <AdminTable :columns="roleColumns" :rows="roles" :loading="loading" row-key="roleId">
        <template #cell-status="{ row }">{{ statusLabel(row.status) }}</template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="7" title="权限">
      <AdminTable :columns="permissionColumns" :rows="permissions" :loading="loading" row-key="permissionId">
        <template #cell-status="{ row }">{{ statusLabel(row.status) }}</template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="12" title="审计日志">
      <AdminFilterBar>
        <input v-model="filters.actionType" placeholder="动作类型" @keyup.enter="loadAll" />
        <input v-model="filters.targetType" placeholder="对象类型" @keyup.enter="loadAll" />
        <button class="secondary-button" :disabled="loading" @click="loadAll">
          <RefreshCcw :size="16" /> 查询
        </button>
      </AdminFilterBar>
      <AdminTable :columns="auditColumns" :rows="auditRows" :loading="loading" row-key="auditLogId" />
    </AdminPanel>
  </main>
</template>
