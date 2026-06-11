<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Play, Plus, RefreshCcw, Settings, Trash2 } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { LlmModelResponse } from '@/api/types';
import { errorMessage, formatDateTime, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const toast = useToast();

const loading = ref(false);
const items = ref<LlmModelResponse[]>([]);
const selected = ref<LlmModelResponse | null>(null);

const createVisible = ref(false);
const createForm = reactive({
  provider: '',
  modelName: '',
  displayName: '',
  endpoint: '',
  apiKey: '',
});
const creating = ref(false);

const deleteTarget = ref<LlmModelResponse | null>(null);
const deleteVisible = ref(false);

const testing = ref(false);
const testResult = ref<{ success: boolean; message: string; sampleReply?: string } | null>(null);

const editVisible = ref(false);
const editForm = reactive({
  provider: '',
  modelName: '',
  displayName: '',
  endpoint: '',
  apiKey: '',
});
const editing = ref(false);

async function handleTest() {
  if (!selected.value || testing.value) return;
  testing.value = true;
  testResult.value = null;
  try {
    testResult.value = await adminApi.llmModels.test(selected.value.modelId);
  } catch (e) {
    testResult.value = { success: false, message: errorMessage(e) };
  } finally {
    testing.value = false;
  }
}

const columns: TableColumn[] = [
  { key: 'displayName', label: '名称' },
  { key: 'provider', label: '供应商' },
  { key: 'modelName', label: '模型 ID' },
  { key: 'status', label: '状态' },
  { key: 'createdAt', label: '创建时间' },
];

async function loadList() {
  loading.value = true;
  try {
    items.value = await adminApi.llmModels.list();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function getDetailRows(item: LlmModelResponse): DetailRow[] {
  return [
    { label: 'ID', value: String(item.modelId) },
    { label: '供应商', value: item.provider },
    { label: '模型 ID', value: item.modelName },
    { label: '显示名称', value: item.displayName },
    { label: '端点', value: item.endpoint || '-' },
    { label: 'API Key', value: item.apiKey ? '已配置' : '未配置' },
    { label: '状态', value: statusLabel(item.status) },
    { label: '创建时间', value: formatDateTime(item.createdAt) },
  ];
}

function openCreate() {
  createForm.provider = '';
  createForm.modelName = '';
  createForm.displayName = '';
  createForm.endpoint = '';
  createForm.apiKey = '';
  createVisible.value = true;
}

async function handleCreate() {
  if (!createForm.provider.trim() || !createForm.modelName.trim() || !createForm.displayName.trim()) return;
  creating.value = true;
  try {
    await adminApi.llmModels.create({
      provider: createForm.provider.trim(),
      modelName: createForm.modelName.trim(),
      displayName: createForm.displayName.trim(),
      endpoint: createForm.endpoint.trim() || null,
      apiKey: createForm.apiKey.trim() || null,
      status: 'ACTIVE',
    });
    createVisible.value = false;
    toast.success('模型已添加');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    creating.value = false;
  }
}

function openEdit() {
  if (!selected.value) return;
  editForm.provider = selected.value.provider;
  editForm.modelName = selected.value.modelName;
  editForm.displayName = selected.value.displayName;
  editForm.endpoint = selected.value.endpoint || '';
  editForm.apiKey = selected.value.apiKey || '';
  editVisible.value = true;
}

async function handleEdit() {
  if (!selected.value || !editForm.provider.trim() || !editForm.modelName.trim() || !editForm.displayName.trim()) return;
  editing.value = true;
  try {
    const updated = await adminApi.llmModels.update(selected.value.modelId, {
      provider: editForm.provider.trim(),
      modelName: editForm.modelName.trim(),
      displayName: editForm.displayName.trim(),
      endpoint: editForm.endpoint.trim() || null,
      apiKey: editForm.apiKey.trim() || null,
      status: selected.value.status,
    });
    editVisible.value = false;
    toast.success('模型已更新');
    selected.value = updated;
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    editing.value = false;
  }
}

function askDelete(item: LlmModelResponse) {
  deleteTarget.value = item;
  deleteVisible.value = true;
}

async function doDelete() {
  if (!deleteTarget.value) return;
  try {
    await adminApi.llmModels.remove(deleteTarget.value.modelId);
    toast.success('模型已删除');
    if (selected.value?.modelId === deleteTarget.value.modelId) selected.value = null;
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    deleteVisible.value = false;
  }
}

function onRowClick(row: LlmModelResponse) {
  selected.value = row;
}

onMounted(loadList);
</script>

<template>
  <div class="page-grid">
    <AdminPanel :span="7" title="大模型管理">
      <template #actions>
        <button class="icon-button" title="刷新" @click="loadList"><RefreshCcw :size="16" /></button>
        <button class="primary-button" @click="openCreate">
          <Plus :size="16" /> 添加模型
        </button>
      </template>
      <AdminTable
        :columns="columns"
        :rows="items"
        :loading="loading"
        :selected-id="selected?.modelId"
        row-key="modelId"
        @row-click="onRowClick"
      >
        <template #cell-status="{ row }">
          <span class="status-pill">{{ statusLabel(row.status) }}</span>
        </template>
        <template #cell-createdAt="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="5" title="模型详情">
      <template v-if="selected">
        <AdminDetailList :rows="getDetailRows(selected)" />
        <div class="detail-actions">
          <button class="secondary-button" @click="openEdit">
            <Settings :size="14" /> 编辑
          </button>
          <button class="secondary-button" :disabled="testing" @click="handleTest">
            <Play :size="14" /> {{ testing ? '测试中...' : '测试连接' }}
          </button>
          <button class="danger-button" @click="askDelete(selected!)">
            <Trash2 :size="14" /> 删除
          </button>
        </div>
        <div v-if="testResult" :class="['test-result', testResult.success ? 'test-ok' : 'test-fail']">
          <strong>{{ testResult.success ? '成功' : '失败' }}</strong>
          <span>{{ testResult.message }}</span>
          <span v-if="testResult.sampleReply" class="test-reply">回复：{{ testResult.sampleReply }}</span>
        </div>
      </template>
      <template v-else><div class="empty-state">选择左侧模型查看详情</div></template>
    </AdminPanel>
  </div>

  <AdminConfirmDialog
    v-model:visible="createVisible"
    title="添加大模型"
    message=""
    confirm-label="添加"
    @confirm="handleCreate"
  >
    <div class="create-form">
      <label>
        <span>供应商</span>
        <input v-model="createForm.provider" placeholder="如 OpenAI、Anthropic、DeepSeek" />
      </label>
      <label>
        <span>模型 ID</span>
        <input v-model="createForm.modelName" placeholder="如 gpt-4o、claude-sonnet-4-20250514" />
      </label>
      <label>
        <span>显示名称</span>
        <input v-model="createForm.displayName" placeholder="如 GPT-4o、Claude Sonnet" />
      </label>
      <label>
        <span>端点 (可选)</span>
        <input v-model="createForm.endpoint" placeholder="自定义 API 端点" />
      </label>
      <label>
        <span>API Key (可选)</span>
        <input v-model="createForm.apiKey" type="password" placeholder="API Key" />
      </label>
    </div>
  </AdminConfirmDialog>

  <AdminConfirmDialog
    v-model:visible="deleteVisible"
    title="删除模型"
    :message="`确定要删除「${deleteTarget?.displayName}」吗？`"
    confirm-label="删除"
    :danger="true"
    @confirm="doDelete"
  />

  <AdminConfirmDialog
    v-model:visible="editVisible"
    title="编辑模型"
    message=""
    confirm-label="保存"
    @confirm="handleEdit"
  >
    <div class="create-form">
      <label>
        <span>供应商</span>
        <input v-model="editForm.provider" placeholder="如 OpenAI、DeepSeek" />
      </label>
      <label>
        <span>模型 ID</span>
        <input v-model="editForm.modelName" placeholder="如 deepseek-chat" />
      </label>
      <label>
        <span>显示名称</span>
        <input v-model="editForm.displayName" placeholder="如 DeepSeek Chat" />
      </label>
      <label>
        <span>端点</span>
        <input v-model="editForm.endpoint" placeholder="API 端点" />
      </label>
      <label>
        <span>API Key</span>
        <input v-model="editForm.apiKey" type="password" placeholder="留空则保持不变" />
      </label>
    </div>
  </AdminConfirmDialog>
</template>

<style scoped>
.create-form {
  display: grid;
  gap: var(--space-lg);
  margin-top: var(--space-md);
}

.create-form label {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.detail-actions {
  display: flex;
  gap: var(--space-sm);
  margin-top: 14px;
}

.test-result {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  display: grid;
  gap: 4px;
}

.test-ok {
  background: var(--success-bg);
  color: #16a34a;
  border: 1px solid rgba(22, 163, 74, 0.2);
}

.test-fail {
  background: var(--error-bg);
  color: var(--red-dark);
  border: 1px solid rgba(179, 49, 49, 0.2);
}

.test-reply {
  font-family: ui-monospace, monospace;
  font-size: 12px;
  margin-top: 4px;
  padding: 6px 8px;
  background: var(--panel-strong);
  border-radius: 4px;
  white-space: pre-wrap;
}
</style>
