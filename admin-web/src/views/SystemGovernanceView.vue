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
import { auditActionLabel, auditOperatorLabel, auditTargetLabel, errorMessage, formatDateTime, permissionActionLabel, permissionDisplayName, permissionModuleLabel, statusLabel } from '@/utils/format';
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

const auditActionOptions = [
  { value: '', label: '全部动作' },
  { value: ['FILE', 'UPLOAD', 'TICKET', 'CREATED'].join('_'), label: '创建上传凭证' },
  { value: ['FILE', 'ADMIN', 'UPLOAD', 'TICKET', 'CREATED'].join('_'), label: '管理员创建上传凭证' },
  { value: ['FILE', 'UPLOAD', 'CONFIRMED'].join('_'), label: '确认文件上传' },
  { value: ['FILE', 'ADMIN', 'UPLOAD', 'CONFIRMED'].join('_'), label: '管理员确认文件上传' },
  { value: ['FILE', 'DOWNLOAD', 'TICKET', 'CREATED'].join('_'), label: '创建下载凭证' },
  { value: ['FILE', 'ADMIN', 'DOWNLOAD', 'TICKET', 'CREATED'].join('_'), label: '管理员创建下载凭证' },
  { value: ['WORKFLOW', 'ATTACHMENT', 'DOWNLOAD', 'TICKET', 'CREATED'].join('_'), label: '创建审批附件下载凭证' },
  { value: ['FILE', 'DOWNLOAD', 'EXECUTED'].join('_'), label: '下载文件' },
  { value: ['FILE', 'ADMIN', 'DOWNLOAD', 'EXECUTED'].join('_'), label: '管理员下载文件' },
  { value: ['WORKFLOW', 'ATTACHMENT', 'DOWNLOAD', 'EXECUTED'].join('_'), label: '下载审批附件' },
  { value: ['FILE', 'CLEANUP', 'JOB', 'RUN'].join('_'), label: '执行文件清理' },
  { value: ['NOTIFY', 'CHANNEL', 'UPDATED'].join('_'), label: '修改通知渠道' },
  { value: ['NOTIFY', 'CHANNEL', 'TEST'].join('_'), label: '测试通知渠道' },
  { value: ['TASK', 'CREATED'].join('_'), label: '创建 AI 任务' },
  { value: ['TASK', 'SUCCEEDED'].join('_'), label: 'AI 任务完成' },
  { value: ['TASK', 'FAILED'].join('_'), label: 'AI 任务失败' },
];

const auditTargetOptions = [
  { value: '', label: '全部对象' },
  { value: ['DOWNLOAD', 'TICKET'].join('_'), label: '下载凭证' },
  { value: ['UPLOAD', 'TICKET'].join('_'), label: '上传凭证' },
  { value: ['ATTACHMENT', 'REF'].join('_'), label: '文件' },
  { value: ['FILE', 'FOLDER'].join('_'), label: '文件夹' },
  { value: ['WORKFLOW', 'REQUEST'].join('_'), label: '审批申请' },
  { value: ['WORKFLOW', 'ATTACHMENT'].join('_'), label: '审批附件' },
  { value: ['AGENT', 'TASK'].join('_'), label: 'AI 任务' },
  { value: 'AGENT', label: 'AI 助手' },
  { value: ['NOTIFY', 'CHANNEL'].join('_'), label: '通知渠道' },
  { value: ['OPS', 'ALERT'].join('_'), label: '系统告警' },
  { value: ['CLEANUP', 'JOB'].join('_'), label: '清理任务' },
  { value: ['STORAGE', 'QUOTA', 'POLICY'].join('_'), label: '存储配额' },
];

const roleColumns: TableColumn[] = [
  { key: 'roleName', label: '名称' },
  { key: 'status', label: '状态' },
];

const permissionColumns: TableColumn[] = [
  { key: 'permName', label: '名称' },
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
  operator: row.operatorName || row.operatorTypeName || auditOperatorLabel(row.operatorType, row.operatorId),
  actionType: row.actionTypeName || auditActionLabel(row.actionType),
  targetName: row.targetDisplayName || row.targetTypeName || auditTargetLabel(row.targetType),
  result: row.resultName || statusLabel(row.result ?? ''),
})));

const permissionRows = computed(() => permissions.value.map((row) => ({
  ...row,
  permName: permissionDisplayName(row.permName),
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
        <button class="secondary-button" title="刷新" @click="loadAll"><RefreshCcw :size="16" />刷新</button>
      </template>
      <AdminTable :columns="roleColumns" :rows="roles" :loading="loading" row-key="roleId">
        <template #cell-status="{ row }">{{ row.statusName || statusLabel(row.status) }}</template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="7" title="权限">
      <AdminTable :columns="permissionColumns" :rows="permissionRows" :loading="loading" row-key="permissionId">
        <template #cell-moduleCode="{ row }">{{ row.moduleName || permissionModuleLabel(row.moduleCode) }}</template>
        <template #cell-actionCode="{ row }">{{ row.actionName || permissionActionLabel(row.actionCode) }}</template>
        <template #cell-status="{ row }">{{ row.statusName || statusLabel(row.status) }}</template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="12" title="审计日志">
      <AdminFilterBar @search="loadAll">
        <select v-model="filters.actionType">
          <option v-for="option in auditActionOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
        <select v-model="filters.targetType">
          <option v-for="option in auditTargetOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadAll">
          <RefreshCcw :size="16" /> 查询
        </button>
      </AdminFilterBar>
      <AdminTable :columns="auditColumns" :rows="auditRows" :loading="loading" row-key="auditLogId">
        <template #cell-target="{ row }">
          <strong>{{ row.targetName }}</strong>
        </template>
      </AdminTable>
    </AdminPanel>
  </main>
</template>
