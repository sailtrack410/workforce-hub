<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { OrgPositionResponse, OrgPositionSaveRequest } from '@/api/types';
import { errorMessage, positionCategoryLabel, statusLabel } from '@/utils/format';
import { usePositionStore } from '@/stores/position';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import { maxLength, requiredText } from '@/utils/validation';

const store = usePositionStore();
const toast = useToast();

const loading = ref(false);
const items = ref<OrgPositionResponse[]>([]);

const createForm = reactive<OrgPositionSaveRequest>({ positionCode: '', positionName: '', status: 'ACTIVE' });
const creating = ref(false);
const createNameTouched = ref(false);
const createNameError = computed(() => requiredText(createForm.positionName, '岗位名称') || maxLength(createForm.positionName, 128, '岗位名称'));

const selected = computed(() =>
  store.selectedPositionId ? items.value.find((i) => i.id === store.selectedPositionId) ?? null : null,
);

const columns: TableColumn[] = [
  { key: 'positionName', label: '名称' },
  { key: 'positionCategory', label: '分类' },
  { key: 'status', label: '状态' },
];

const detailRows = computed<DetailRow[]>(() => {
  const r = selected.value;
  if (!r) return [];
  return [
    { label: '名称', value: r.positionName },
    { label: '分类', value: positionCategoryLabel(r.positionCategory) },
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
  if (createNameError.value) return;
  creating.value = true;
  try {
    await adminApi.org.createPosition(createForm);
    createForm.positionCode = '';
    createForm.positionName = '';
    createNameTouched.value = false;
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
    <AdminPanel :span="selected ? 7 : 12" title="岗位列表">
      <template #actions>
        <button class="secondary-button" title="刷新" @click="loadList"><RefreshCcw :size="16" />刷新</button>
      </template>
      <AdminFilterBar :show-search="false">
        <label class="filter-field">
          <input v-model="createForm.positionName" :aria-invalid="createNameTouched && Boolean(createNameError)" maxlength="128" placeholder="岗位名称" @blur="createNameTouched = true" />
          <small v-if="createNameTouched && createNameError" class="field-error">{{ createNameError }}</small>
        </label>
        <button class="primary-button" :disabled="creating || Boolean(createNameError)" @click="handleCreate">
          <Plus :size="16" /> 新建
        </button>
      </AdminFilterBar>
      <AdminTable
        interactive
        :columns="columns"
        :rows="items"
        :loading="loading"
        :selected-id="store.selectedPositionId"
        row-key="id"
        @row-click="onRowClick"
      >
        <template #cell-positionCategory="{ row }">{{ positionCategoryLabel(row.positionCategory) }}</template>
        <template #cell-status="{ row }">{{ statusLabel(row.status) }}</template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel v-if="selected" :span="5" title="岗位详情">
      <AdminDetailList :rows="detailRows" />
      <div class="detail-actions">
        <button class="secondary-button" @click="handleUpdate">保存修改</button>
      </div>
    </AdminPanel>
  </div>
</template>

<style scoped>
.detail-actions {
  display: flex;
  margin-top: var(--space-base);
}

.filter-field { display: grid; gap: var(--space-xs); }
</style>
