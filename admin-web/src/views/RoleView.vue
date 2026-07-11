<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw, Save, ShieldCheck, Trash2, UserCog } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { RbacAdminUserRoleResponse, RbacPermissionResponse, RbacRoleResponse } from '@/api/types';
import { errorMessage, permissionDisplayName, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';
import AdminDialog from '@/components/AdminDialog.vue';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminSkeleton from '@/components/AdminSkeleton.vue';

const toast = useToast();

const activeTab = ref<'roles' | 'admins'>('roles');
const loading = ref(false);
const saving = ref(false);
const roles = ref<RbacRoleResponse[]>([]);
const permissions = ref<RbacPermissionResponse[]>([]);
const adminUsers = ref<RbacAdminUserRoleResponse[]>([]);
const selectedRoleId = ref<string | number | null>(null);
const selectedAdminUserId = ref<string | number | null>(null);
const selectedPermissionIds = ref<Set<string>>(new Set());
const selectedRoleIdsForAdmin = ref<Set<string>>(new Set());
const roleDialogVisible = ref(false);
const adminRoleDialogVisible = ref(false);
const deleteConfirmVisible = ref(false);
const assignConfirmVisible = ref(false);
const roleForm = reactive({ roleCode: '', roleName: '', status: 'ACTIVE' });

const roleCodeOptions = [
  { value: 'ADMIN', label: '系统管理员' },
  { value: 'HR_MANAGER', label: '人事管理员' },
  { value: 'DEPT_MANAGER', label: '部门主管' },
  { value: 'EMPLOYEE', label: '普通员工' },
];

const roleColumns: TableColumn[] = [
  { key: 'roleName', label: '名称' },
  { key: 'roleCode', label: '类型' },
  { key: 'adminCount', label: '管理员' },
  { key: 'permissionCount', label: '权限' },
  { key: 'status', label: '状态' },
];

const adminColumns: TableColumn[] = [
  { key: 'username', label: '账号' },
  { key: 'displayName', label: '姓名' },
  { key: 'roles', label: '角色' },
];

const selectedRole = computed(() => roles.value.find((role) => String(role.roleId) === String(selectedRoleId.value)) ?? null);
const selectedAdminUser = computed(() => adminUsers.value.find((user) => String(user.adminUserId) === String(selectedAdminUserId.value)) ?? null);
const isBuiltInRole = computed(() => selectedRole.value?.roleCode === 'SUPER_ADMIN');
const affectedAdminUsers = computed(() => selectedRole.value
  ? adminUsers.value.filter((user) => user.roles.some((role) => String(role.roleId) === String(selectedRole.value?.roleId)))
  : []);
const currentAdminRoleIds = computed(() => new Set((selectedAdminUser.value?.roles ?? []).map((role) => String(role.roleId))));
const addedAdminRoles = computed(() => roles.value.filter((role) => (
  selectedRoleIdsForAdmin.value.has(String(role.roleId)) && !currentAdminRoleIds.value.has(String(role.roleId))
)));
const removedAdminRoles = computed(() => roles.value.filter((role) => (
  !selectedRoleIdsForAdmin.value.has(String(role.roleId)) && currentAdminRoleIds.value.has(String(role.roleId))
)));

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
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function fillRoleForm(role: RbacRoleResponse) {
  selectedRoleId.value = role.roleId;
  roleForm.roleCode = role.roleCode;
  roleForm.roleName = role.roleName;
  roleForm.status = role.status;
  selectedPermissionIds.value = new Set((role.permissions ?? []).map((item) => String(item.permissionId)));
}

function openRole(role: RbacRoleResponse) {
  fillRoleForm(role);
  roleDialogVisible.value = true;
}

function openAdminRoles(user: RbacAdminUserRoleResponse) {
  selectedAdminUserId.value = user.adminUserId;
  selectedRoleIdsForAdmin.value = new Set(user.roles.map((role) => String(role.roleId)));
  adminRoleDialogVisible.value = true;
}

function newRole() {
  selectedRoleId.value = null;
  roleForm.roleCode = 'EMPLOYEE';
  roleForm.roleName = '普通员工';
  roleForm.status = 'ACTIVE';
  selectedPermissionIds.value = new Set();
  roleDialogVisible.value = true;
}

async function saveRole() {
  if (isBuiltInRole.value || saving.value) return;
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
    roleDialogVisible.value = false;
    await loadPage();
    toast.success('角色已保存');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

function requestDeleteRole() {
  if (!selectedRole.value || isBuiltInRole.value) return;
  deleteConfirmVisible.value = true;
}

async function deleteRole() {
  if (!selectedRoleId.value || isBuiltInRole.value || saving.value) return;
  saving.value = true;
  try {
    await adminApi.rbac.deleteRole(selectedRoleId.value);
    deleteConfirmVisible.value = false;
    roleDialogVisible.value = false;
    selectedRoleId.value = null;
    await loadPage();
    toast.success('角色已删除');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

function requestSaveAdminRoles() {
  if (!selectedAdminUser.value || saving.value) return;
  if (!addedAdminRoles.value.length && !removedAdminRoles.value.length) {
    toast.success('角色未发生变化');
    return;
  }
  assignConfirmVisible.value = true;
}

async function saveAdminRoles() {
  if (!selectedAdminUserId.value || saving.value) return;
  saving.value = true;
  try {
    await adminApi.rbac.assignAdminRoles(selectedAdminUserId.value, Array.from(selectedRoleIdsForAdmin.value));
    assignConfirmVisible.value = false;
    adminRoleDialogVisible.value = false;
    await loadPage();
    toast.success('管理员角色已保存');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

function togglePermission(permissionId: string | number, checked: boolean) {
  if (isBuiltInRole.value) return;
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

function roleAdminCount(role: RbacRoleResponse) {
  return adminUsers.value.filter((user) => user.roles.some((item) => String(item.roleId) === String(role.roleId))).length;
}

function roleTypeLabel(role: RbacRoleResponse) {
  if (role.roleCode === 'SUPER_ADMIN') return '系统内置';
  return roleCodeOptions.find((item) => item.value === role.roleCode)?.label ?? role.roleCode;
}

onMounted(loadPage);
</script>

<template>
  <main class="role-page">
    <div class="role-tabs" role="tablist" aria-label="角色权限任务">
      <button type="button" role="tab" :aria-selected="activeTab === 'roles'" :class="{ active: activeTab === 'roles' }" @click="activeTab = 'roles'">
        <ShieldCheck :size="16" />角色配置
      </button>
      <button type="button" role="tab" :aria-selected="activeTab === 'admins'" :class="{ active: activeTab === 'admins' }" @click="activeTab = 'admins'">
        <UserCog :size="16" />人员授权
      </button>
    </div>

    <AdminPanel v-if="activeTab === 'roles'" :span="12" :title="`角色配置 (${roles.length})`">
      <template #actions>
        <button class="secondary-button" title="刷新" :disabled="loading" @click="loadPage"><RefreshCcw :size="16" />刷新</button>
        <button class="primary-button" @click="newRole"><Plus :size="16" />新建角色</button>
      </template>
      <AdminTable
        interactive
        :columns="roleColumns"
        :rows="roles"
        :loading="loading"
        row-key="roleId"
        @row-click="openRole"
      >
        <template #cell-roleName="{ row }"><strong>{{ row.roleName }}</strong></template>
        <template #cell-roleCode="{ row }">{{ roleTypeLabel(row) }}</template>
        <template #cell-adminCount="{ row }">{{ roleAdminCount(row) }}</template>
        <template #cell-permissionCount="{ row }">{{ row.permissions?.length ?? 0 }}</template>
        <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel v-else :span="12" :title="`人员授权 (${adminUsers.length})`">
      <template #actions>
        <button class="secondary-button" title="刷新" :disabled="loading" @click="loadPage"><RefreshCcw :size="16" />刷新</button>
      </template>
      <AdminTable
        interactive
        :columns="adminColumns"
        :rows="adminUsers"
        :loading="loading"
        row-key="adminUserId"
        @row-click="openAdminRoles"
      >
        <template #cell-username="{ row }"><strong>{{ row.username }}</strong></template>
        <template #cell-displayName="{ row }">{{ row.displayName || '-' }}</template>
        <template #cell-roles="{ row }">{{ adminRoleNames(row) }}</template>
      </AdminTable>
    </AdminPanel>

    <AdminDialog
      v-model:visible="roleDialogVisible"
      :title="selectedRole ? '编辑角色' : '新建角色'"
      labelled-by="role-editor-title"
      width="min(720px, 92vw)"
      :close-disabled="saving"
    >
      <template v-if="loading"><AdminSkeleton :lines="5" /></template>
      <div v-else class="stack-form">
        <div v-if="isBuiltInRole" class="protected-notice">
          <ShieldCheck :size="18" />
          <span>超级管理员是系统内置角色，始终拥有全部有效权限，不允许停用、改权或删除。</span>
        </div>
        <label>角色类型
          <select
            v-model="roleForm.roleCode"
            :disabled="Boolean(selectedRole)"
            @change="roleForm.roleName = roleCodeOptions.find((item) => item.value === roleForm.roleCode)?.label ?? roleForm.roleName"
          >
            <option v-if="roleForm.roleCode && !roleCodeOptions.some((item) => item.value === roleForm.roleCode)" :value="roleForm.roleCode">
              {{ roleForm.roleCode === 'SUPER_ADMIN' ? '超级管理员' : roleForm.roleCode }}
            </option>
            <option v-for="item in roleCodeOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </label>
        <label>名称 <input v-model="roleForm.roleName" :disabled="isBuiltInRole" maxlength="128" /></label>
        <label>状态
          <select v-model="roleForm.status" :disabled="isBuiltInRole">
            <option value="ACTIVE">启用</option>
            <option value="INACTIVE">停用</option>
          </select>
        </label>
        <div class="permission-heading">
          <strong>权限范围</strong>
          <span>{{ selectedPermissionIds.size }} / {{ permissions.length }}</span>
        </div>
        <div class="check-grid">
          <label v-for="permission in permissions" :key="permission.permissionId" class="check-row">
            <input
              type="checkbox"
              :checked="selectedPermissionIds.has(String(permission.permissionId))"
              :disabled="isBuiltInRole"
              @change="togglePermission(permission.permissionId, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ permissionDisplayName(permission.permName) }}</span>
          </label>
        </div>
        <div class="dialog-button-row">
          <button v-if="selectedRole && !isBuiltInRole" class="ghost-button danger" :disabled="saving" @click="requestDeleteRole">
            <Trash2 :size="16" />删除角色
          </button>
          <span />
          <button class="secondary-button" :disabled="saving" @click="roleDialogVisible = false">取消</button>
          <button v-if="!isBuiltInRole" class="primary-button" :disabled="saving || !roleForm.roleCode || !roleForm.roleName.trim()" @click="saveRole">
            <Save :size="16" />{{ saving ? '保存中' : '保存' }}
          </button>
        </div>
      </div>
    </AdminDialog>

    <AdminDialog
      v-model:visible="adminRoleDialogVisible"
      title="管理员角色"
      labelled-by="admin-role-editor-title"
      width="min(620px, 92vw)"
      :close-disabled="saving"
    >
      <div class="stack-form">
        <div class="field-readonly">
          <span>管理员</span>
          <strong>{{ selectedAdminUser?.displayName || selectedAdminUser?.username || '-' }}</strong>
          <small>{{ selectedAdminUser?.username }}</small>
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
        <div class="dialog-button-row align-end">
          <button class="secondary-button" :disabled="saving" @click="adminRoleDialogVisible = false">取消</button>
          <button class="primary-button" :disabled="saving || !selectedAdminUser" @click="requestSaveAdminRoles">
            <Save :size="16" />保存角色
          </button>
        </div>
      </div>
    </AdminDialog>

    <AdminConfirmDialog
      v-model:visible="deleteConfirmVisible"
      title="删除角色"
      confirm-label="删除角色"
      :danger="true"
      :close-on-confirm="false"
      :confirm-disabled="saving"
      :cancel-disabled="saving"
      @confirm="deleteRole"
    >
      <div class="impact-summary">
        <p>删除“{{ selectedRole?.roleName }}”后不可恢复，角色权限绑定将同时清除。</p>
        <strong>{{ affectedAdminUsers.length }} 名管理员将失去该角色</strong>
        <span v-if="affectedAdminUsers.length">{{ affectedAdminUsers.map((user) => user.displayName || user.username).join('、') }}</span>
      </div>
    </AdminConfirmDialog>

    <AdminConfirmDialog
      v-model:visible="assignConfirmVisible"
      title="确认调整管理员角色"
      confirm-label="确认保存"
      :close-on-confirm="false"
      :confirm-disabled="saving"
      :cancel-disabled="saving"
      @confirm="saveAdminRoles"
    >
      <div class="impact-summary">
        <p>将更新管理员“{{ selectedAdminUser?.displayName || selectedAdminUser?.username }}”的访问权限。</p>
        <strong v-if="addedAdminRoles.length">新增：{{ addedAdminRoles.map((role) => role.roleName).join('、') }}</strong>
        <strong v-if="removedAdminRoles.length" class="danger-text">移除：{{ removedAdminRoles.map((role) => role.roleName).join('、') }}</strong>
      </div>
    </AdminConfirmDialog>
  </main>
</template>

<style scoped>
.role-page {
  display: grid;
  gap: var(--space-base);
}

.role-tabs {
  display: inline-flex;
  width: max-content;
  gap: var(--space-xs);
  padding: var(--space-xs);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--panel);
}

.role-tabs button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: var(--control-h);
  padding: 0 var(--space-base);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--muted);
  font-weight: 700;
}

.role-tabs button.active {
  background: var(--primary-soft);
  color: var(--green-dark);
}

.check-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-sm);
}

.check-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: 40px;
  padding: 0 var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  color: var(--ink);
  transition: all 200ms ease;
}

.check-row:hover {
  border-color: var(--green-light);
  background: var(--hover-bg);
}

.check-row input {
  width: auto;
  min-height: auto;
  padding: 0;
}

.permission-heading,
.dialog-button-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.permission-heading span {
  color: var(--muted);
  font-size: var(--text-sm);
}

.dialog-button-row span {
  flex: 1;
}

.dialog-button-row.align-end {
  justify-content: flex-end;
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

.protected-notice {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  padding: var(--space-md);
  border: 1px solid var(--success-border);
  border-radius: var(--radius-sm);
  background: var(--success-bg);
  color: var(--success);
  font-size: var(--text-sm);
  line-height: 1.5;
}

.protected-notice svg {
  flex: 0 0 auto;
}

.impact-summary {
  display: grid;
  gap: var(--space-sm);
  color: var(--muted);
  line-height: 1.5;
}

.impact-summary p {
  margin: 0;
}

.impact-summary strong {
  color: var(--ink);
}

.danger,
.danger-text {
  color: var(--red) !important;
}

@media (max-width: 640px) {
  .role-tabs,
  .role-tabs button {
    width: 100%;
  }

  .role-tabs button {
    justify-content: center;
  }

  .check-grid {
    grid-template-columns: 1fr;
  }

  .dialog-button-row {
    flex-wrap: wrap;
  }
}
</style>
