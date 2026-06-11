<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { OrgPositionResponse, OrgPositionSaveRequest } from '@/api/types';
import { errorMessage, statusLabel } from '@/utils/format';
import { usePositionStore } from '@/stores/position';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';

const store = usePositionStore();
const toast = useToast();

const loading = ref(false);
const items = ref<OrgPositionResponse[]>([]);

const createForm = reactive<OrgPositionSaveRequest>({ positionCode: '', positionName: '', status: 'ACTIVE' });
const creating = ref(false);

const selected = computed(() =>
  store.selectedPositionId ? items.value.find((i) => i.id === store.selectedPositionId) ?? null : null,
);

const columns: TableColumn[] = [
  { key: 'positionCode', label: '编码' },
  { key: 'positionName', label: '名称' },
  { key: 'positionCategory', label: '分类' },
  { key: 'status', label: '状态' },
];

const detailRows = computed<DetailRow[]>(() => {
  const r = selected.value;
  if (!r) return [];
  return [
    { label: 'ID', value: String(r.id) },
    { label: '编码', value: r.positionCode },
    { label: '名称', value: r.positionName },
    { label: '分类', value: r.positionCategory ?? '-' },
    { label: '状态', value: statusLabel(r.status) },
  ];
});

async function loadList() {
  loading.value = true;

  try {
    items.value = await adminApi.org.positions();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

async function handleCreate() {
  creating.value = true;
  try {
    await adminApi.org.createPosition(createForm);
    createForm.positionCode = '';
    createForm.positionName = '';
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    creating.value = false;
  }
}

async function handleUpdate() {
  if (!selected.value) return;
  try {
    await adminApi.org.updatePosition(selected.value.id, {
      positionCode: selected.value.positionCode,
      positionName: selected.value.positionName,
      positionCategory: selected.value.positionCategory,
      status: selected.value.status,
    });
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

function onRowClick(row: OrgPositionResponse) {
  store.selectedPositionId = row.id;
}

onMounted(loadList);
</script>

<template>
  <div class="page-grid">
    <AdminPanel :span="7" title="岗位列表">
      <template #actions>
        <button class="icon-button" title="刷新" @click="loadList"><RefreshCcw :size="16" /></button>
      </template>
      <AdminFilterBar>
        <input v-model="createForm.positionCode" placeholder="岗位编码" />
        <input v-model="createForm.positionName" placeholder="岗位名称" />
        <button class="primary-button" :disabled="creating || !createForm.positionCode || !createForm.positionName" @click="handleCreate">
          <Plus :size="16" /> 新建
        </button>
      </AdminFilterBar>
      <AdminTable
        :columns="columns"
        :rows="items"
        :loading="loading"
        :selected-id="store.selectedPositionId"
        row-key="id"
        @row-click="onRowClick"
      />
    </AdminPanel>

    <AdminPanel :span="5" title="岗位详情">
      <template v-if="selected">
        <AdminDetailList :rows="detailRows" />
        <div class="detail-actions">
          <button class="secondary-button" @click="handleUpdate">保存修改</button>
        </div>
      </template>
      <template v-else><div class="empty-state">选择左侧岗位查看详情</div></template>
    </AdminPanel>
  </div>
</template>

<style scoped>
.detail-actions {
  margin-top: 14px;
}
</style>
