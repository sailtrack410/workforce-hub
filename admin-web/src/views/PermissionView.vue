<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { RefreshCcw, ShieldCheck } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import { adminApi } from '@/api/admin';
import type { RbacPermissionResponse, RbacRoleResponse } from '@/api/types';
import { errorMessage, permissionActionLabel, permissionDisplayName, permissionModuleLabel, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';

const router = useRouter();
const toast = useToast();
const loading = ref(false);
const permissions = ref<RbacPermissionResponse[]>([]);
const roles = ref<RbacRoleResponse[]>([]);
const filters = reactive({ keyword: '', moduleCode: '', actionCode: '', status: '' });

const columns: TableColumn[] = [
  { key: 'permName', label: '权限点' },
  { key: 'moduleCode', label: '模块' },
  { key: 'actionCode', label: '动作' },
  { key: 'roles', label: '关联角色' },
  { key: 'status', label: '状态' },
];

const moduleOptions = computed(() => [...new Set(permissions.value.map((item) => item.moduleCode).filter((item): item is string => Boolean(item)))].sort());
const actionOptions = computed(() => [...new Set(permissions.value.map((item) => item.actionCode).filter((item): item is string => Boolean(item)))].sort());
const rows = computed(() => permissions.value.filter((item) => {
  const keyword = filters.keyword.trim().toLowerCase();
  return (!keyword || `${item.permName} ${item.permCode}`.toLowerCase().includes(keyword))
    && (!filters.moduleCode || item.moduleCode === filters.moduleCode)
    && (!filters.actionCode || item.actionCode === filters.actionCode)
    && (!filters.status || item.status === filters.status);
}));

function roleNames(permissionId: string | number) {
  const names = roles.value
    .filter((role) => role.permissions?.some((item) => String(item.permissionId) === String(permissionId)))
    .map((role) => role.roleName);
  return names.length ? names.join('、') : '未分配';
}

async function loadPage() {
  loading.value = true;
  try {
    const [permissionRows, roleRows] = await Promise.all([adminApi.rbac.permissions(), adminApi.rbac.roles()]);
    permissions.value = permissionRows;
    roles.value = roleRows;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  filters.keyword = '';
  filters.moduleCode = '';
  filters.actionCode = '';
  filters.status = '';
}

onMounted(loadPage);
</script>

<template>
  <main class="page-grid">
    <AdminPanel :span="12" :title="`权限点 (${rows.length}/${permissions.length})`" subtitle="权限点由系统预置，角色授权请进入角色配置">
      <template #actions>
        <button class="secondary-button" :disabled="loading" @click="loadPage"><RefreshCcw :size="16" />刷新</button>
        <button class="primary-button" @click="router.push('/roles')"><ShieldCheck :size="16" />角色配置</button>
      </template>
      <div class="permission-filters" aria-label="权限点筛选">
        <input v-model.trim="filters.keyword" placeholder="权限名称或编码" />
        <select v-model="filters.moduleCode"><option value="">全部模块</option><option v-for="item in moduleOptions" :key="item" :value="item">{{ permissionModuleLabel(item) }}</option></select>
        <select v-model="filters.actionCode"><option value="">全部动作</option><option v-for="item in actionOptions" :key="item" :value="item">{{ permissionActionLabel(item) }}</option></select>
        <select v-model="filters.status"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select>
        <button class="secondary-button" type="button" @click="resetFilters">重置</button>
      </div>
      <AdminTable :columns="columns" :rows="rows" :loading="loading" row-key="permissionId">
        <template #cell-permName="{ row }"><strong>{{ permissionDisplayName(row.permName) }}</strong><span>{{ row.permCode }}</span></template>
        <template #cell-moduleCode="{ row }">{{ permissionModuleLabel(row.moduleCode) }}</template>
        <template #cell-actionCode="{ row }">{{ permissionActionLabel(row.actionCode) }}</template>
        <template #cell-roles="{ row }">{{ roleNames(row.permissionId) }}</template>
        <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
      </AdminTable>
    </AdminPanel>
  </main>
</template>

<style scoped>
.permission-filters { display: flex; align-items: center; gap: var(--space-sm); flex-wrap: wrap; margin-bottom: var(--space-base); }
.permission-filters input, .permission-filters select { width: min(100%, 220px); }
@media (max-width: 640px) { .permission-filters input, .permission-filters select, .permission-filters button { width: 100%; } }
</style>
