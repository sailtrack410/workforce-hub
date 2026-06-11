<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { LeaveTypeResponse } from '@/api/types';
import { errorMessage, formatDateTime, statusLabel } from '@/utils/format';
import { useLeaveTypeStore } from '@/stores/leaveType';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';

const store = useLeaveTypeStore();
const toast = useToast();

const loading = ref(false);
const items = ref<LeaveTypeResponse[]>([]);

const createForm = reactive({ leaveCode: '', leaveName: '', balanceMode: 'DEDUCT', status: 'ACTIVE' });
const creating = ref(false);

const selected = computed(() =>
  store.selectedLeaveTypeId ? items.value.find((i) => i.leaveTypeId === store.selectedLeaveTypeId) ?? null : null,
);

const columns: TableColumn[] = [
  { key: 'leaveCode', label: '编码' },
  { key: 'leaveName', label: '名称' },
  { key: 'balanceMode', label: '余额模式' },
  { key: 'status', label: '状态' },
];

const detailRows = computed<DetailRow[]>(() => {
  const r = selected.value;
  if (!r) return [];
  return [
    { label: 'ID', value: String(r.leaveTypeId) },
    { label: '编码', value: r.leaveCode },
    { label: '名称', value: r.leaveName },
    { label: '余额模式', value: r.balanceMode },
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

async function handleCreate() {
  creating.value = true;
  try {
    await adminApi.leave.createType(createForm);
    createForm.leaveCode = '';
    createForm.leaveName = '';
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    creating.value = false;
  }
}

function onRowClick(row: LeaveTypeResponse) {
  store.selectedLeaveTypeId = row.leaveTypeId;
}

onMounted(loadList);
</script>

<template>
  <div class="page-grid">
    <AdminPanel :span="7" title="假种列表">
      <template #actions>
        <button class="icon-button" title="刷新" @click="loadList"><RefreshCcw :size="16" /></button>
      </template>
      <AdminFilterBar>
        <input v-model="createForm.leaveCode" placeholder="编码 (ANNUAL_LEAVE)" />
        <input v-model="createForm.leaveName" placeholder="名称 (年假)" />
        <select v-model="createForm.balanceMode">
          <option value="DEDUCT">扣减模式</option>
          <option value="GRANT">授予模式</option>
          <option value="UNLIMITED">不限</option>
        </select>
        <button class="primary-button" :disabled="creating || !createForm.leaveCode || !createForm.leaveName" @click="handleCreate">
          <Plus :size="16" /> 新建假种
        </button>
      </AdminFilterBar>
      <AdminTable
        :columns="columns"
        :rows="items"
        :loading="loading"
        :selected-id="store.selectedLeaveTypeId"
        row-key="leaveTypeId"
        @row-click="onRowClick"
      />
    </AdminPanel>

    <AdminPanel :span="5" title="假种详情">
      <template v-if="selected">
        <AdminDetailList :rows="detailRows" />
      </template>
      <template v-else><div class="empty-state">选择左侧假种查看详情</div></template>
    </AdminPanel>
  </div>
</template>
