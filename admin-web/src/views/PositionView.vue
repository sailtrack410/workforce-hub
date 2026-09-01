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
import AdminMetricStrip from '@/components/AdminMetricStrip.vue';
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
const positionMetrics = computed(() => [
  { label: '岗位总数', value: items.value.length },
  { label: '已启用', value: items.value.filter((item) => item.status === 'ACTIVE').length },
  { label: '已停用', value: items.value.filter((item) => item.status !== 'ACTIVE').length },
  { label: '未分类', value: items.value.filter((item) => !item.positionCategory).length },
]);

const columns: TableColumn[] = [
  { key: 'positionName', label: '名称' },
  { key: 'positionCategory', label: '分类' },
  { key: 'status', label: '状态' },
];

const detailRows = computed<DetailRow[]>(() => {
  const r = selected.value;
  if (!r) return [];
  return [
    { label: '编码', value: r.positionCode || '-' },
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
  <div class="page-grid position-page">
    <AdminPanel :span="12" title="岗位主数据概览" subtitle="岗位启停与分类完整度决定员工归属和后续权限映射质量">
      <AdminMetricStrip :cards="positionMetrics" />
    </AdminPanel>

    <AdminPanel :span="7" :title="`岗位目录 (${items.length})`" subtitle="选择岗位后在右侧核对编码、分类与启停状态">
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
        <template #cell-positionName="{ row }">
          <strong>{{ row.positionName }}</strong>
          <span>{{ row.positionCode || '未设置编码' }}</span>
        </template>
        <template #cell-positionCategory="{ row }"><span :class="{ 'risk-text': !row.positionCategory }">{{ positionCategoryLabel(row.positionCategory) }}</span></template>
        <template #cell-status="{ row }"><span class="status-pill" :class="{ 'risk-status': row.status !== 'ACTIVE' }">{{ statusLabel(row.status) }}</span></template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="5" title="岗位详情" :subtitle="selected ? `当前岗位：${selected.positionName}` : '从左侧选择岗位'">
      <template v-if="selected">
        <div class="position-governance" :class="{ risk: selected.status !== 'ACTIVE' || !selected.positionCategory }">
          <span>主数据状态</span>
          <strong>{{ selected.status === 'ACTIVE' && selected.positionCategory ? '配置完整' : '需要补充或复核' }}</strong>
        </div>
        <AdminDetailList :rows="detailRows" />
        <div class="detail-actions">
          <button class="primary-button" @click="handleUpdate">保存修改</button>
        </div>
      </template>
      <div v-else class="empty-state">选择岗位后查看完整配置</div>
    </AdminPanel>
  </div>
</template>

<style scoped>
.position-page { letter-spacing: 0; }

.position-governance {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-base);
  padding: var(--space-md);
  border-left: 3px solid var(--success);
  background: var(--success-bg);
  color: var(--success);
  font-size: var(--text-xs);
}

.position-governance.risk {
  border-left-color: var(--amber);
  background: var(--surface-subtle);
  color: var(--red-dark);
}

.detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-base);
}

.filter-field { display: grid; gap: var(--space-xs); }

.risk-status,
.risk-text { color: var(--red-dark) !important; }
</style>
