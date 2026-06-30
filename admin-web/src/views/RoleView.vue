<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw, Save, Trash2 } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { RbacAdminUserRoleResponse, RbacPermissionResponse, RbacRoleResponse } from '@/api/types';
import { errorMessage, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminSkeleton from '@/components/AdminSkeleton.vue';

const toast = useToast();

const loading = ref(false);
const saving = ref(false);
const roles = ref<RbacRoleResponse[]>([]);
const permissions = ref<RbacPermissionResponse[]>([]);
const adminUsers = ref<RbacAdminUserRoleResponse[]>([]);
const selectedRoleId = ref<string | number | null>(null);
const selectedAdminUserId = ref<string | number | null>(null);
const selectedPermissionIds = ref<Set<string>>(new Set());
const selectedRoleIdsForAdmin = ref<Set<string>>(new Set());
const roleForm = reactive({ roleCode: '', roleName: '', status: 'ACTIVE' });

const roleColumns: TableColumn[] = [
  { key: 'roleCode', label: '编码' },
  { key: 'roleName', label: '名称' },
  { key: 'status', label: '状态' },
];

const adminColumns: TableColumn[] = [
  { key: 'username', label: '账号' },
  { key: 'displayName', label: '姓名' },
  { key: 'roles', label: '角色' },
];

const selectedRole = computed(() => roles.value.find((role) => String(role.roleId) === String(selectedRoleId.value)) ?? null);
const selectedAdminUser = computed(() => adminUsers.value.find((user) => String(user.adminUserId) === String(selectedAdminUserId.value)) ?? null);

async function loadPage() {
  loading.value = true;
  try {
    const [roleResult, permissionResult, userResult] = await Promise.all([
      adminApi.rbac.roles(),
      adminApi.rbac.permissions(),
      adminApi.rbac.adminUsers(),
    ]);
    roles.value = roleResult;
    permissions.value = permissionResult;
    adminUsers.value = userResult;
    if (!selectedRoleId.value && roleResult.length) selectRole(roleResult[0]);
    if (!selectedAdminUserId.value && userResult.length) selectAdminUser(userResult[0]);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function selectRole(role: RbacRoleResponse) {
  selectedRoleId.value = role.roleId;
  roleForm.roleCode = role.roleCode;
  roleForm.roleName = role.roleName;
  roleForm.status = role.status;
  selectedPermissionIds.value = new Set((role.permissions ?? []).map((item) => String(item.permissionId)));
}

function selectAdminUser(user: RbacAdminUserRoleResponse) {
  selectedAdminUserId.value = user.adminUserId;
  selectedRoleIdsForAdmin.value = new Set(user.roles.map((role) => String(role.roleId)));
}

function newRole() {
  selectedRoleId.value = null;
  roleForm.roleCode = '';
  roleForm.roleName = '';
  roleForm.status = 'ACTIVE';
  selectedPermissionIds.value = new Set();
}

async function saveRole() {
  saving.value = true;
  try {
    const request = {
      roleCode: roleForm.roleCode.trim(),
      roleName: roleForm.roleName.trim(),
      status: roleForm.status,
    };
    const saved = selectedRoleId.value
      ? await adminApi.rbac.updateRole(selectedRoleId.value, request)
      : await adminApi.rbac.createRole(request);
    await adminApi.rbac.assignRolePermissions(saved.roleId, Array.from(selectedPermissionIds.value));
    selectedRoleId.value = saved.roleId;
    await loadPage();
    toast.success('已保存');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

async function deleteRole() {
  if (!selectedRoleId.value) return;
  saving.value = true;
  try {
    await adminApi.rbac.deleteRole(selectedRoleId.value);
    newRole();
    await loadPage();
    toast.success('已删除');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

async function saveAdminRoles() {
  if (!selectedAdminUserId.value) return;
  saving.value = true;
  try {
    await adminApi.rbac.assignAdminRoles(selectedAdminUserId.value, Array.from(selectedRoleIdsForAdmin.value));
    await loadPage();
    toast.success('已保存');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

function togglePermission(permissionId: string | number, checked: boolean) {
  const next = new Set(selectedPermissionIds.value);
  if (checked) next.add(String(permissionId));
  else next.delete(String(permissionId));
  selectedPermissionIds.value = next;
}

function toggleAdminRole(roleId: string | number, checked: boolean) {
  const next = new Set(selectedRoleIdsForAdmin.value);
  if (checked) next.add(String(roleId));
  else next.delete(String(roleId));
  selectedRoleIdsForAdmin.value = next;
}

function adminRoleNames(user: RbacAdminUserRoleResponse) {
  return user.roles.map((role) => role.roleName).join('、') || '-';
}

onMounted(loadPage);
</script>

<template>
  <div class="page-grid">
    <AdminPanel :span="7" title="角色">
      <template #actions>
        <button class="icon-button" title="刷新" @click="loadPage"><RefreshCcw :size="16" /></button>
      </template>

      <AdminTable
        :columns="roleColumns"
        :rows="roles"
        :loading="loading"
        :selected-id="selectedRoleId"
        row-key="roleId"
        @row-click="selectRole"
      >
        <template #cell-status="{ row }">{{ statusLabel(row.status) }}</template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="5" title="角色维护">
      <template #actions>
        <button class="icon-button" title="新建" @click="newRole"><Plus :size="16" /></button>
      </template>
      <template v-if="loading"><AdminSkeleton :lines="5" /></template>
      <div v-else class="stack-form">
        <label>编码 <input v-model="roleForm.roleCode" /></label>
        <label>名称 <input v-model="roleForm.roleName" /></label>
        <label>状态
          <select v-model="roleForm.status">
            <option value="ACTIVE">启用</option>
            <option value="INACTIVE">停用</option>
          </select>
        </label>
        <div class="check-grid">
          <label v-for="permission in permissions" :key="permission.permissionId" class="check-row">
            <input
              type="checkbox"
              :checked="selectedPermissionIds.has(String(permission.permissionId))"
              @change="togglePermission(permission.permissionId, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ permission.permName }}</span>
          </label>
        </div>
        <div class="button-row">
          <button class="primary-button" :disabled="saving || !roleForm.roleCode || !roleForm.roleName" @click="saveRole">
            <Save :size="16" /> 保存
          </button>
          <button class="ghost-button danger" :disabled="saving || !selectedRole" @click="deleteRole">
            <Trash2 :size="16" /> 删除
          </button>
        </div>
      </div>
    </AdminPanel>

    <AdminPanel :span="7" title="管理员">
      <AdminTable
        :columns="adminColumns"
        :rows="adminUsers"
        :loading="loading"
        :selected-id="selectedAdminUserId"
        row-key="adminUserId"
        @row-click="selectAdminUser"
      >
        <template #cell-roles="{ row }">
          {{ adminRoleNames(row) }}
        </template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="5" title="管理员角色">
      <template v-if="loading"><AdminSkeleton :lines="5" /></template>
      <div v-else class="stack-form">
        <div class="field-readonly">
          <span>账号</span>
          <strong>{{ selectedAdminUser?.username ?? '-' }}</strong>
        </div>
        <div class="check-grid">
          <label v-for="role in roles" :key="role.roleId" class="check-row">
            <input
              type="checkbox"
              :checked="selectedRoleIdsForAdmin.has(String(role.roleId))"
              @change="toggleAdminRole(role.roleId, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ role.roleName }}</span>
          </label>
        </div>
        <button class="primary-button" :disabled="saving || !selectedAdminUser" @click="saveAdminRoles">
          <Save :size="16" /> 保存
        </button>
      </div>
    </AdminPanel>
  </div>
</template>

<style scoped>
.check-grid {
  display: grid;
  gap: var(--space-sm);
}

.check-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: 36px;
  padding: 0 var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  color: var(--ink);
}

.check-row input {
  width: auto;
  min-height: auto;
  padding: 0;
}

.button-row {
  display: flex;
  gap: var(--space-sm);
}

.field-readonly {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
}

.field-readonly strong {
  color: var(--ink);
  font-size: var(--text-base);
}

.danger {
  color: var(--red);
}
</style>
