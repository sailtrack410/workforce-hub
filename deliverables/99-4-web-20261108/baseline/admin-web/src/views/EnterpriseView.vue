<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Pencil, Plus, RefreshCcw, Trash2 } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { DictItemResponse, DictItemSaveRequest } from '@/api/types';
import { errorMessage, formatDateTime } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDialog from '@/components/AdminDialog.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const toast = useToast();

// 品牌标语落库在 sys_dict，固定字典类型；条目编码对用户无意义，新增时自动分配
const TAGLINE_DICT_TYPE = 'brand_login_tagline';
const TAGLINE_REMARK = '登录页轮播标语';

const loading = ref(false);
const items = ref<DictItemResponse[]>([]);

const dialogVisible = ref(false);
const editId = ref<DictItemResponse['id'] | null>(null);
const saving = ref(false);
const deleteTarget = ref<DictItemResponse | null>(null);
const deleteVisible = ref(false);
const deleting = ref(false);

const form = reactive({ itemLabel: '', sortNo: 0, status: true });

const columns: TableColumn[] = [
  { key: 'itemLabel', label: '标语内容' },
  { key: 'sortNo', label: '轮播顺序', width: '100px' },
  { key: 'status', label: '状态', width: '110px' },
  { key: 'updatedAt', label: '更新时间', width: '150px' },
  { key: '_actions', label: '操作', width: '150px' },
];

const canSubmit = computed(() => Boolean(form.itemLabel.trim()));

const enabledCount = computed(() => items.value.filter((item) => Number(item.status) === 1).length);

function isEnabled(row: DictItemResponse) {
  return Number(row.status) === 1;
}

async function loadItems() {
  loading.value = true;
  try {
    items.value = await adminApi.dicts.items(TAGLINE_DICT_TYPE);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function nextItemCode() {
  const maxCode = items.value.reduce((max, item) => {
    const matched = /^T(\d+)$/.exec(item.itemCode);
    return matched ? Math.max(max, Number(matched[1])) : max;
  }, 0);
  return `T${maxCode + 1}`;
}

function openCreate() {
  editId.value = null;
  form.itemLabel = '';
  form.sortNo = items.value.length ? Math.max(...items.value.map((item) => item.sortNo ?? 0)) + 1 : 1;
  form.status = true;
  dialogVisible.value = true;
}

function openEdit(row: DictItemResponse) {
  editId.value = row.id;
  form.itemLabel = row.itemLabel;
  form.sortNo = row.sortNo ?? 0;
  form.status = isEnabled(row);
  dialogVisible.value = true;
}

async function save() {
  if (!canSubmit.value || saving.value) return;
  const editing = editId.value ? items.value.find((item) => item.id === editId.value) : null;
  const request: DictItemSaveRequest = {
    dictType: TAGLINE_DICT_TYPE,
    itemCode: editing?.itemCode ?? nextItemCode(),
    itemLabel: form.itemLabel.trim(),
    sortNo: Number(form.sortNo) || 0,
    status: form.status ? 1 : 0,
    remark: TAGLINE_REMARK,
  };
  saving.value = true;
  try {
    if (editId.value) {
      await adminApi.dicts.update(editId.value, request);
    } else {
      await adminApi.dicts.create(request);
    }
    dialogVisible.value = false;
    toast.success('已保存');
    await loadItems();
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
    await loadItems();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    deleting.value = false;
    deleteTarget.value = null;
  }
}

onMounted(loadItems);
</script>

<template>
  <div class="page-grid">
    <AdminPanel
      :span="12"
      title="企业设置"
      subtitle="面向全员的品牌化配置，当前开放登录页轮播标语，启用中的标语会在员工端登录页轮流展示"
    >
      <template #actions>
        <button class="secondary-button" title="刷新" :disabled="loading" @click="loadItems">
          <RefreshCcw :size="16" />刷新
        </button>
        <button class="primary-button" @click="openCreate">
          <Plus :size="16" />新增标语
        </button>
      </template>
      <p class="panel-hint">共 {{ items.length }} 条标语，{{ enabledCount }} 条启用中；员工端登录页每 4.5 秒轮播一条。</p>
    </AdminPanel>

    <AdminPanel :span="12" title="登录页品牌标语" subtitle="按轮播顺序展示，停用后不再出现在登录页">
      <AdminTable :columns="columns" :rows="items" :loading="loading" row-key="id">
        <template #cell-status="{ row }">
          <span class="status-pill" :class="{ 'risk-status': !isEnabled(row) }">{{ isEnabled(row) ? '启用' : '停用' }}</span>
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
      :title="editId ? '编辑标语' : '新增标语'"
      width="min(480px, 92vw)"
    >
      <form class="stack-form" @submit.prevent="save">
        <label>
          <span>标语内容 *</span>
          <input v-model="form.itemLabel" maxlength="128" placeholder="例如：把重复交给系统，把判断留给专业。" />
        </label>
        <label>
          <span>轮播顺序</span>
          <input v-model.number="form.sortNo" type="number" min="0" step="1" />
        </label>
        <label class="check-row">
          <input v-model="form.status" type="checkbox" />
          <span>启用</span>
        </label>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" @click="dialogVisible = false">取消</button>
          <button class="primary-button" type="submit" :disabled="saving || !canSubmit">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </form>
    </AdminDialog>

    <AdminConfirmDialog
      v-model:visible="deleteVisible"
      title="删除标语"
      :message="`确定删除「${deleteTarget?.itemLabel || ''}」吗？删除后登录页不再展示。`"
      confirm-label="删除"
      :danger="true"
      @confirm="confirmDelete"
    />
  </div>
</template>

<style scoped>
.panel-hint {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
}
</style>
