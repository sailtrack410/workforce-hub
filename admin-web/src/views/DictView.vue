<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Pencil, Plus, RefreshCcw, Trash2 } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { DictItemResponse, DictItemSaveRequest, DictTypeResponse } from '@/api/types';
import { errorMessage, formatDateTime } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminDialog from '@/components/AdminDialog.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const toast = useToast();

const loading = ref(false);
const types = ref<DictTypeResponse[]>([]);
const items = ref<DictItemResponse[]>([]);
const activeDictType = ref('');

const dialogVisible = ref(false);
const editId = ref<DictItemResponse['id'] | null>(null);
const saving = ref(false);
const deleteTarget = ref<DictItemResponse | null>(null);
const deleteVisible = ref(false);
const deleting = ref(false);

// status 契约是 1/0，表单里用布尔开关，提交时再换算
const form = reactive({ itemCode: '', itemLabel: '', sortNo: 0, status: true, remark: '' });

const columns: TableColumn[] = [
  { key: 'itemCode', label: '编码' },
  { key: 'itemLabel', label: '显示文案' },
  { key: 'sortNo', label: '排序', width: '90px' },
  { key: 'status', label: '状态', width: '110px' },
  { key: 'updatedAt', label: '更新时间', width: '150px' },
  { key: '_actions', label: '操作', width: '150px' },
];

const canSubmit = computed(() => Boolean(form.itemCode.trim() && form.itemLabel.trim()));
// 新增时避免同一字典类型下出现重复编码，后端校验之外的轻量提示
const formError = computed(() => (
  !editId.value && form.itemCode.trim() && items.value.some((item) => item.itemCode === form.itemCode.trim())
    ? '该编码在当前字典类型下已存在'
    : ''
));

function isEnabled(row: DictItemResponse) {
  return Number(row.status) === 1;
}

function statusText(row: DictItemResponse) {
  return row.statusName || (isEnabled(row) ? '启用' : '停用');
}

async function loadTypes() {
  try {
    types.value = await adminApi.dicts.types();
    if (!types.value.some((type) => type.dictType === activeDictType.value)) {
      activeDictType.value = types.value[0]?.dictType ?? '';
    }
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

async function loadItems() {
  if (!activeDictType.value) {
    items.value = [];
    return;
  }
  loading.value = true;
  try {
    items.value = await adminApi.dicts.items(activeDictType.value);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

async function refreshAll() {
  await loadTypes();
  await loadItems();
}

function openCreate() {
  if (!activeDictType.value) {
    toast.error('请先选择字典类型');
    return;
  }
  editId.value = null;
  form.itemCode = '';
  form.itemLabel = '';
  form.sortNo = 0;
  form.status = true;
  form.remark = '';
  dialogVisible.value = true;
}

function openEdit(row: DictItemResponse) {
  editId.value = row.id;
  form.itemCode = row.itemCode;
  form.itemLabel = row.itemLabel;
  form.sortNo = row.sortNo ?? 0;
  form.status = isEnabled(row);
  form.remark = row.remark ?? '';
  dialogVisible.value = true;
}

async function save() {
  if (!canSubmit.value || formError.value || saving.value) return;
  saving.value = true;
  const request: DictItemSaveRequest = {
    dictType: activeDictType.value,
    itemCode: form.itemCode.trim(),
    itemLabel: form.itemLabel.trim(),
    sortNo: Number(form.sortNo) || 0,
    status: form.status ? 1 : 0,
    remark: form.remark.trim() || null,
  };
  try {
    if (editId.value) {
      await adminApi.dicts.update(editId.value, request);
    } else {
      await adminApi.dicts.create(request);
    }
    dialogVisible.value = false;
    toast.success('已保存');
    await refreshAll();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

function askDelete(row: DictItemResponse) {
  deleteTarget.value = row;
  deleteVisible.value = true;
}

async function confirmDelete() {
  const row = deleteTarget.value;
  if (!row || deleting.value) return;
  deleting.value = true;
  try {
    await adminApi.dicts.remove(row.id);
    toast.success('已删除');
    await refreshAll();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    deleting.value = false;
    deleteTarget.value = null;
  }
}

onMounted(refreshAll);
</script>

<template>
  <div class="page-grid dict-page">
    <AdminPanel :span="12" title="数据字典" subtitle="维护业务枚举字典，字典类型由系统预设，条目编码在类型内唯一">
      <template #actions>
        <button class="secondary-button" title="刷新" :disabled="loading" @click="refreshAll">
          <RefreshCcw :size="16" />刷新
        </button>
        <button class="primary-button" :disabled="!activeDictType" @click="openCreate">
          <Plus :size="16" />新增条目
        </button>
      </template>
      <AdminFilterBar :show-search="false">
        <label class="dict-type-field">
          <span>字典类型</span>
          <select v-model="activeDictType" :disabled="!types.length" @change="loadItems">
            <option v-if="!types.length" value="">暂无字典类型</option>
            <option v-for="type in types" :key="type.dictType" :value="type.dictType">
              {{ type.dictType }}（{{ type.itemCount }} 项）
            </option>
          </select>
        </label>
      </AdminFilterBar>
    </AdminPanel>

    <AdminPanel
      :span="12"
      :title="`条目列表${activeDictType ? ` · ${activeDictType}` : ''}`"
      subtitle="按排序号展示，停用条目不再供业务侧选择"
    >
      <AdminTable :columns="columns" :rows="items" :loading="loading" row-key="id">
        <template #cell-itemCode="{ row }">
          <strong>{{ row.itemCode }}</strong>
          <span v-if="row.remark">{{ row.remark }}</span>
        </template>
        <template #cell-status="{ row }">
          <span class="status-pill" :class="{ 'risk-status': !isEnabled(row) }">{{ statusText(row) }}</span>
        </template>
        <template #cell-updatedAt="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        <template #cell-_actions="{ row }">
          <div class="row-actions">
            <button class="secondary-button action-btn" @click="openEdit(row)">
              <Pencil :size="14" />编辑
            </button>
            <button class="danger-button action-btn" @click="askDelete(row)">
              <Trash2 :size="14" />删除
            </button>
          </div>
        </template>
      </AdminTable>
    </AdminPanel>

    <AdminDialog
      v-model:visible="dialogVisible"
      :title="editId ? '编辑字典条目' : '新增字典条目'"
      width="min(480px, 92vw)"
    >
      <form class="stack-form" @submit.prevent="save">
        <label>
          <span>字典类型</span>
          <input :value="activeDictType" disabled />
        </label>
        <label>
          <span>编码 *</span>
          <input v-model="form.itemCode" placeholder="例如：LEAVE" />
        </label>
        <label>
          <span>显示文案 *</span>
          <input v-model="form.itemLabel" placeholder="例如：请假" />
        </label>
        <label>
          <span>排序</span>
          <input v-model.number="form.sortNo" type="number" min="0" step="1" />
        </label>
        <label class="check-row">
          <input v-model="form.status" type="checkbox" />
          <span>启用</span>
        </label>
        <label>
          <span>备注</span>
          <input v-model="form.remark" placeholder="选填" />
        </label>
        <p v-if="formError" class="field-error">{{ formError }}</p>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" @click="dialogVisible = false">取消</button>
          <button class="primary-button" type="submit" :disabled="saving || !canSubmit || Boolean(formError)">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </form>
    </AdminDialog>

    <AdminConfirmDialog
      v-model:visible="deleteVisible"
      title="删除字典条目"
      :message="`确定删除「${deleteTarget?.itemLabel || deleteTarget?.itemCode || ''}」吗？`"
      confirm-label="删除"
      :danger="true"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.dict-type-field {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.risk-status {
  color: var(--red-dark) !important;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  flex-wrap: nowrap;
}

.action-btn {
  min-height: 30px;
  padding: 0 var(--space-sm);
  font-size: var(--text-xs);
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  margin-top: var(--space-md);
}
</style>
