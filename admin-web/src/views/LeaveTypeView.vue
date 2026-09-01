<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { Plus, RefreshCcw, Save, Trash2 } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { LeaveTypeResponse } from '@/api/types';
import { errorMessage, formatDateTime, leaveBalanceModeLabel, statusLabel } from '@/utils/format';
import { useLeaveTypeStore } from '@/stores/leaveType';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';
import AdminMetricStrip from '@/components/AdminMetricStrip.vue';

const store = useLeaveTypeStore();
const toast = useToast();

const loading = ref(false);
const items = ref<LeaveTypeResponse[]>([]);

const leaveTypeOptions = [
  { leaveCode: 'ANNUAL', leaveName: '年假' },
  { leaveCode: 'SICK', leaveName: '病假' },
  { leaveCode: 'PERSONAL', leaveName: '事假' },
  { leaveCode: 'MARRIAGE', leaveName: '婚假' },
  { leaveCode: 'MATERNITY', leaveName: '产假' },
  { leaveCode: 'PATERNITY', leaveName: '陪产假' },
  { leaveCode: 'BEREAVEMENT', leaveName: '丧假' },
  { leaveCode: 'TIME_OFF', leaveName: '调休' },
];

const createForm = reactive({ leaveCode: 'ANNUAL', balanceMode: 'DEDUCT', status: 'ACTIVE' });
const creating = ref(false);
const editForm = reactive({ leaveCode: '', balanceMode: 'DEDUCT', status: 'ACTIVE' });
const saving = ref(false);
const deleting = ref(false);
const deleteVisible = ref(false);
const createError = computed(() => items.value.some((item) => item.leaveCode === createForm.leaveCode) ? '该假种已存在，请直接编辑现有假种' : '');
const policyMetrics = computed(() => [
  { label: '政策总数', value: items.value.length },
  { label: '已启用', value: items.value.filter((item) => item.status === 'ACTIVE').length },
  { label: '扣减余额', value: items.value.filter((item) => item.balanceMode === 'DEDUCT').length },
  { label: '不扣余额', value: items.value.filter((item) => item.balanceMode === 'NO_DEDUCT').length },
]);

const selected = computed(() =>
  store.selectedLeaveTypeId ? items.value.find((i) => i.leaveTypeId === store.selectedLeaveTypeId) ?? null : null,
);

const columns: TableColumn[] = [
  { key: 'leaveName', label: '名称' },
  { key: 'balanceMode', label: '余额模式' },
  { key: 'status', label: '状态' },
];

const detailRows = computed<DetailRow[]>(() => {
  const r = selected.value;
  if (!r) return [];
  return [
    { label: '名称', value: r.leaveName },
    { label: '余额模式', value: leaveBalanceModeLabel(r.balanceMode) },
    { label: '状态', value: statusLabel(r.status) },
    { label: '创建时间', value: formatDateTime(r.createdAt) },
    { label: '更新时间', value: formatDateTime(r.updatedAt) },
  ];
});

async function loadList() {
  loading.value = true;

  try {
    items.value = await adminApi.leave.types();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function selectedLeaveType(leaveCode: string) {
  return leaveTypeOptions.find((item) => item.leaveCode === leaveCode) ?? null;
}

async function handleCreate() {
  if (createError.value) return;
  const option = selectedLeaveType(createForm.leaveCode);
  if (!option) {
    toast.error('请选择假种');
    return;
  }
  creating.value = true;
  try {
    await adminApi.leave.createType({
      leaveCode: option.leaveCode,
      leaveName: option.leaveName,
      balanceMode: createForm.balanceMode,
      status: createForm.status,
    });
    createForm.leaveCode = 'ANNUAL';
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    creating.value = false;
  }
}

async function handleUpdate() {
  const row = selected.value;
  if (!row) return;
  const option = selectedLeaveType(editForm.leaveCode);
  if (!option) {
    toast.error('请选择假种');
    return;
  }
  saving.value = true;
  try {
    await adminApi.leave.updateType(row.leaveTypeId, {
      leaveCode: option.leaveCode,
      leaveName: option.leaveName,
      balanceMode: editForm.balanceMode,
      status: editForm.status,
    });
    await loadList();
    toast.success('已保存');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

function handleDelete() {
  const row = selected.value;
  if (!row) return;
  deleteVisible.value = true;
}

async function confirmDelete() {
  const row = selected.value;
  if (!row) return;
  deleting.value = true;
  try {
    await adminApi.leave.deleteType(row.leaveTypeId);
    store.selectedLeaveTypeId = null;
    await loadList();
    toast.success('已删除');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    deleting.value = false;
  }
}

function onRowClick(row: LeaveTypeResponse) {
  store.selectedLeaveTypeId = row.leaveTypeId;
}

watch(selected, (row) => {
  if (!row) return;
  editForm.leaveCode = selectedLeaveType(row.leaveCode)?.leaveCode ?? '';
  editForm.balanceMode = row.balanceMode;
  editForm.status = row.status;
}, { immediate: true });

onMounted(loadList);
</script>

<template>
  <div class="page-grid leave-type-page">
    <AdminPanel :span="12" title="假期政策目录" subtitle="政策状态直接影响员工可申请范围，余额模式决定账户是否扣减">
      <template #actions>
        <button class="secondary-button" title="刷新" @click="loadList"><RefreshCcw :size="16" />刷新</button>
      </template>
      <AdminMetricStrip :cards="policyMetrics" />
    </AdminPanel>

    <AdminPanel :span="7" :title="`假种清单 (${items.length})`" subtitle="选择一行后在右侧维护规则">
      <AdminTable
        interactive
        :columns="columns"
        :rows="items"
        :loading="loading"
        :selected-id="store.selectedLeaveTypeId"
        row-key="leaveTypeId"
        @row-click="onRowClick"
      >
        <template #cell-leaveName="{ row }">
          <strong>{{ row.leaveName }}</strong>
          <span>{{ row.leaveCode }}</span>
        </template>
        <template #cell-balanceMode="{ row }">
          <span class="status-pill">{{ leaveBalanceModeLabel(row.balanceMode) }}</span>
        </template>
        <template #cell-status="{ row }">
          <span class="status-pill" :class="{ 'risk-status': row.status !== 'ACTIVE' }">{{ statusLabel(row.status) }}</span>
        </template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="5" :title="selected ? '政策维护' : '新增政策'" :subtitle="selected ? `正在维护 ${selected.leaveName}` : '选择标准假种并配置余额规则'">
      <section class="policy-operation">
        <div class="operation-heading">
          <strong>新增假种</strong>
          <span>标准政策模板</span>
        </div>
        <AdminFilterBar :show-search="false">
        <select v-model="createForm.leaveCode">
          <option v-for="type in leaveTypeOptions" :key="type.leaveCode" :value="type.leaveCode">
            {{ type.leaveName }}
          </option>
        </select>
        <select v-model="createForm.balanceMode">
          <option value="DEDUCT">扣减余额</option>
          <option value="NO_DEDUCT">不扣减余额</option>
        </select>
        <button class="primary-button" :disabled="creating || !createForm.leaveCode || Boolean(createError)" @click="handleCreate">
          <Plus :size="16" /> 新建假种
        </button>
        </AdminFilterBar>
        <p v-if="createError" class="field-error create-error">{{ createError }}</p>
      </section>

      <section v-if="selected" class="policy-operation selected-policy">
        <div class="operation-heading">
          <strong>当前政策</strong>
          <span :class="{ 'risk-text': selected.status !== 'ACTIVE' }">{{ statusLabel(selected.status) }}</span>
        </div>
        <AdminDetailList :rows="detailRows" />
        <div class="detail-edit-form">
        <select v-model="editForm.leaveCode">
          <option value="">请选择假种</option>
          <option v-for="type in leaveTypeOptions" :key="type.leaveCode" :value="type.leaveCode">
            {{ type.leaveName }}
          </option>
        </select>
        <select v-model="editForm.balanceMode">
          <option value="DEDUCT">扣减余额</option>
          <option value="NO_DEDUCT">不扣减余额</option>
        </select>
        <select v-model="editForm.status">
          <option value="ACTIVE">启用</option>
          <option value="INACTIVE">停用</option>
        </select>
        <div class="detail-actions">
          <button class="primary-button" :disabled="saving || !editForm.leaveCode" @click="handleUpdate">
            <Save :size="16" /> 保存
          </button>
          <button class="ghost-button danger" :disabled="deleting" @click="handleDelete">
            <Trash2 :size="16" /> 删除
          </button>
        </div>
        </div>
      </section>
      <div v-else class="policy-empty">从左侧选择假种后可编辑、停用或删除</div>
    </AdminPanel>

    <AdminConfirmDialog
      v-model:visible="deleteVisible"
      title="删除假种"
      :message="`删除假种 ${selected?.leaveName || ''}？`"
      confirm-label="删除"
      :danger="true"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.leave-type-page { letter-spacing: 0; }

.policy-operation {
  display: grid;
  gap: var(--space-md);
}

.selected-policy {
  margin-top: var(--space-base);
  padding-top: var(--space-base);
  border-top: 1px solid var(--line);
}

.operation-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
}

.operation-heading span,
.policy-empty {
  color: var(--muted);
  font-size: var(--text-xs);
}

.policy-empty {
  margin-top: var(--space-base);
  padding: var(--space-base);
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
  text-align: center;
}

.policy-operation :deep(.filter-row) {
  display: grid;
  grid-template-columns: 1fr;
  margin-bottom: 0;
}

.policy-operation :deep(.filter-row select),
.policy-operation :deep(.filter-row button) {
  width: 100%;
  max-width: none;
}

.detail-edit-form {
  display: grid;
  gap: var(--space-md);
  margin-top: var(--space-base);
}

.detail-actions {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.create-error { margin: calc(var(--space-sm) * -1) 0 var(--space-md); }

.risk-status,
.risk-text { color: var(--red-dark) !important; }

</style>
