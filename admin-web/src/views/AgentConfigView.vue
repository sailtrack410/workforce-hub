<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { FlaskConical, Pencil, PlayCircle, Plus, PowerOff, RefreshCcw, Save, Search, Send, Trash2 } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import { adminApi } from '@/api/admin';
import type { AgentResponse, EntityId, LlmModelResponse } from '@/api/types';
import { errorMessage, formatDateTime, humanizeText, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';
import AdminIconAvatar from '@/components/AdminIconAvatar.vue';

const router = useRouter();
const toast = useToast();

const loading = ref(false);
const items = ref<AgentResponse[]>([]);
const keyword = ref('');

const createVisible = ref(false);
const createForm = reactive({ agentCode: '', agentName: '', avatarAttachmentRefId: '' });
const creating = ref(false);
const editTarget = ref<AgentResponse | null>(null);
const editVisible = ref(false);
const editForm = reactive({ agentName: '', avatarAttachmentRefId: '' });
const editing = ref(false);
const disableTarget = ref<AgentResponse | null>(null);
const disableVisible = ref(false);
const disabling = ref(false);
const enableTarget = ref<AgentResponse | null>(null);
const enableVisible = ref(false);
const enabling = ref(false);
const modelLoading = ref(false);
const modelSaving = ref(false);
const modelTesting = ref<EntityId | null>(null);
const llmModels = ref<LlmModelResponse[]>([]);
const modelEditId = ref<EntityId | null>(null);
const modelForm = reactive({
  provider: 'openai',
  modelName: '',
  displayName: '',
  endpoint: '',
  apiKey: '',
  status: 'ACTIVE',
});

const columns: TableColumn[] = [
  { key: 'agentName', label: '助手' },
  { key: 'agentCode', label: '编号' },
  { key: 'agentType', label: '类型' },
  { key: 'status', label: '状态' },
  { key: 'publishedVersionId', label: '发布版本' },
  { key: 'createdAt', label: '创建时间' },
  { key: '_actions', label: '操作', width: '230px' },
];

async function loadList() {
  loading.value = true;
  try {
    const res = await adminApi.agentConfig.list(1, 100, keyword.value.trim() || undefined);
    items.value = res.list;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

async function loadModels() {
  modelLoading.value = true;
  try {
    llmModels.value = await adminApi.llmModels.list();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    modelLoading.value = false;
  }
}

function handleSearch() {
  void loadList();
}

function openCreate() {
  createForm.agentCode = '';
  createForm.agentName = '';
  createForm.avatarAttachmentRefId = '';
  createVisible.value = true;
}

function avatarRef(value: string) {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

async function handleCreate() {
  if (!createForm.agentCode.trim() || !createForm.agentName.trim()) return;
  creating.value = true;
  try {
    await adminApi.agentConfig.create({
      agentCode: createForm.agentCode.trim(),
      agentName: createForm.agentName.trim(),
      avatarAttachmentRefId: avatarRef(createForm.avatarAttachmentRefId),
      agentType: 'QA',
      identityMode: 'SYSTEM_IDENTITY',
    });
    createVisible.value = false;
    toast.success('AI 助手已创建');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    creating.value = false;
  }
}

function openEdit(agent: AgentResponse) {
  editTarget.value = agent;
  editForm.agentName = agent.agentName || '';
  editForm.avatarAttachmentRefId = agent.avatarAttachmentRefId == null ? '' : String(agent.avatarAttachmentRefId);
  editVisible.value = true;
}

async function handleEdit() {
  if (!editTarget.value || !editForm.agentName.trim()) return;
  editing.value = true;
  try {
    await adminApi.agentConfig.update(editTarget.value.agentId, {
      agentName: editForm.agentName.trim(),
      avatarAttachmentRefId: avatarRef(editForm.avatarAttachmentRefId),
    });
    editVisible.value = false;
    toast.success('AI 助手已更新');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    editing.value = false;
  }
}

function openDagEditor(agent: AgentResponse) {
  const resolved = router.resolve({ name: 'admin-agent-dag', params: { agentId: agent.agentId } });
  window.open(resolved.href, '_blank');
}

function askDisable(agent: AgentResponse) {
  disableTarget.value = agent;
  disableVisible.value = true;
}

function askEnable(agent: AgentResponse) {
  enableTarget.value = agent;
  enableVisible.value = true;
}

async function handleDisable() {
  if (!disableTarget.value || disabling.value) return;
  disabling.value = true;
  try {
    await adminApi.agentConfig.disable(disableTarget.value.agentId);
    disableVisible.value = false;
    toast.success('已停用');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    disabling.value = false;
  }
}

async function handleEnable() {
  if (!enableTarget.value || enabling.value) return;
  enabling.value = true;
  try {
    await adminApi.agentConfig.enable(enableTarget.value.agentId);
    enableVisible.value = false;
    toast.success('已启用');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    enabling.value = false;
  }
}

function resetModelForm() {
  modelEditId.value = null;
  modelForm.provider = 'openai';
  modelForm.modelName = '';
  modelForm.displayName = '';
  modelForm.endpoint = '';
  modelForm.apiKey = '';
  modelForm.status = 'ACTIVE';
}

function editModel(model: LlmModelResponse) {
  modelEditId.value = model.modelId;
  modelForm.provider = model.provider;
  modelForm.modelName = model.modelName;
  modelForm.displayName = model.displayName;
  modelForm.endpoint = model.endpoint || '';
  modelForm.apiKey = '';
  modelForm.status = model.status || 'ACTIVE';
}

async function saveModel() {
  if (!modelForm.modelName.trim() || !modelForm.displayName.trim()) return;
  modelSaving.value = true;
  try {
    const request = {
      provider: modelForm.provider.trim() || 'openai',
      modelName: modelForm.modelName.trim(),
      displayName: modelForm.displayName.trim(),
      endpoint: modelForm.endpoint.trim() || null,
      apiKey: modelForm.apiKey.trim() || null,
      status: modelForm.status,
    };
    if (modelEditId.value) {
      await adminApi.llmModels.update(modelEditId.value, request);
    } else {
      await adminApi.llmModels.create(request);
    }
    toast.success('模型配置已保存');
    resetModelForm();
    await loadModels();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    modelSaving.value = false;
  }
}

async function testModel(model: LlmModelResponse) {
  modelTesting.value = model.modelId;
  try {
    const res = await adminApi.llmModels.test(model.modelId);
    res.success ? toast.success(res.message) : toast.error(res.message);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    modelTesting.value = null;
  }
}

async function removeModel(model: LlmModelResponse) {
  await adminApi.llmModels.remove(model.modelId);
  if (String(modelEditId.value) === String(model.modelId)) resetModelForm();
  await loadModels();
  toast.success('模型配置已删除');
}

onMounted(() => {
  void loadList();
  void loadModels();
});
</script>

<template>
  <main class="page-grid">
    <AdminPanel :span="12" title="AI 助手">
    <template #actions>
      <form class="agent-search" @submit.prevent="handleSearch">
        <input v-model="keyword" placeholder="搜索名称 / 编码" />
        <button class="secondary-button" title="搜索" type="submit">
          <Search :size="16" />
        </button>
      </form>
      <button class="secondary-button" title="刷新" @click="loadList">
        <RefreshCcw :size="16" />
      </button>
      <button class="primary-button" @click="openCreate">
        <Plus :size="16" />
        <span>新建助手</span>
      </button>
    </template>

    <AdminTable
      :columns="columns"
      :rows="items"
      :loading="loading"
      row-key="agentId"
    >
      <template #cell-agentName="{ row }">
        <div class="agent-name-cell">
          <AdminIconAvatar :label="humanizeText(row.agentName) || row.agentCode || 'AI助手'" kind="agent" size="sm" />
          <div>
            <strong>{{ humanizeText(row.agentName) || row.agentCode }}</strong>
          </div>
        </div>
      </template>
      <template #cell-status="{ row }">
        <span class="status-pill">{{ statusLabel(row.status) }}</span>
      </template>
      <template #cell-publishedVersionId="{ row }">
        {{ row.publishedVersionId ? `V${row.publishedVersionLabel || row.publishedVersionNo}` : '未发布' }}
      </template>
      <template #cell-createdAt="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>
      <template #cell-_actions="{ row }">
        <div class="row-actions">
          <button class="secondary-button action-btn" title="编辑资料" @click.stop="openEdit(row)">
            <Pencil :size="14" />
            <span>编辑</span>
          </button>
          <button class="primary-button action-btn" title="流程编排" @click.stop="openDagEditor(row)">
            <Send :size="14" />
            <span>编排</span>
          </button>
          <button
            v-if="row.status === 'INACTIVE'"
            class="primary-button action-btn"
            title="启用"
            @click.stop="askEnable(row)"
          >
            <PlayCircle :size="14" />
            <span>启用</span>
          </button>
          <button
            v-else
            class="danger-button action-btn"
            title="停用"
            :disabled="row.status !== 'ACTIVE'"
            @click.stop="askDisable(row)"
          >
            <PowerOff :size="14" />
            <span>停用</span>
          </button>
        </div>
      </template>
    </AdminTable>
  </AdminPanel>

  <AdminPanel :span="12" title="模型配置">
    <template #actions>
      <button class="secondary-button" title="刷新" @click="loadModels">
        <RefreshCcw :size="16" />
      </button>
    </template>

    <form class="model-form" @submit.prevent="saveModel">
      <label>
        <span>供应商</span>
        <input v-model="modelForm.provider" placeholder="openai" />
      </label>
      <label>
        <span>模型名称</span>
        <input v-model="modelForm.modelName" placeholder="gpt-4o-mini" />
      </label>
      <label>
        <span>显示名称</span>
        <input v-model="modelForm.displayName" placeholder="默认翻译模型" />
      </label>
      <label>
        <span>API 地址</span>
        <input v-model="modelForm.endpoint" placeholder="https://api.openai.com/v1" />
      </label>
      <label>
        <span>API Key</span>
        <input v-model="modelForm.apiKey" type="password" placeholder="保存时填写" autocomplete="new-password" />
      </label>
      <label>
        <span>状态</span>
        <select v-model="modelForm.status">
          <option value="ACTIVE">启用</option>
          <option value="INACTIVE">停用</option>
        </select>
      </label>
      <div class="model-form-actions">
        <button class="primary-button" type="submit" :disabled="modelSaving">
          <Save :size="16" />
          <span>{{ modelEditId ? '保存模型' : '新增模型' }}</span>
        </button>
        <button class="secondary-button" type="button" @click="resetModelForm">清空</button>
      </div>
    </form>

    <div class="model-list" :aria-busy="modelLoading">
      <div v-for="model in llmModels" :key="model.modelId" class="model-row">
        <div>
          <strong>{{ model.displayName }}</strong>
          <span>{{ model.provider }} · {{ model.modelName }} · {{ statusLabel(model.status) }}</span>
        </div>
        <div class="row-actions">
          <button class="secondary-button action-btn" title="编辑" @click="editModel(model)">
            <Pencil :size="14" />
            <span>编辑</span>
          </button>
          <button class="secondary-button action-btn" title="测试" :disabled="modelTesting === model.modelId" @click="testModel(model)">
            <FlaskConical :size="14" />
            <span>测试</span>
          </button>
          <button class="danger-button action-btn" title="删除" @click="removeModel(model)">
            <Trash2 :size="14" />
            <span>删除</span>
          </button>
        </div>
      </div>
    </div>
  </AdminPanel>
  </main>

  <AdminConfirmDialog
    v-model:visible="createVisible"
    title="新建 AI 助手"
    message=""
    confirm-label="创建"
    :danger="false"
    @confirm="handleCreate"
  >
    <template #default>
      <div class="create-dialog-form">
        <label>
          <span>助手编号</span>
          <input v-model="createForm.agentCode" placeholder="如 LEAVE_HELPER" autofocus />
        </label>
        <label>
          <span>助手名称</span>
          <input v-model="createForm.agentName" placeholder="如 假期助手" />
        </label>
        <label>
          <span>头像附件 ID</span>
          <input v-model="createForm.avatarAttachmentRefId" placeholder="不填则使用默认图标" />
        </label>
      </div>
    </template>
  </AdminConfirmDialog>

  <AdminConfirmDialog
    v-model:visible="editVisible"
    title="编辑 AI 助手"
    message=""
    confirm-label="保存"
    :danger="false"
    @confirm="handleEdit"
  >
    <template #default>
      <div class="create-dialog-form">
        <label>
          <span>助手名称</span>
          <input v-model="editForm.agentName" placeholder="如 假期助手" autofocus />
        </label>
        <label>
          <span>头像附件 ID</span>
          <input v-model="editForm.avatarAttachmentRefId" placeholder="不填则使用默认图标" />
        </label>
        <p class="form-tip">上传头像后填入附件 ID，员工端会优先显示头像，没有头像时显示默认 AI 图标。</p>
      </div>
    </template>
  </AdminConfirmDialog>

  <AdminConfirmDialog
    v-model:visible="disableVisible"
    title="停用 AI 助手"
    :message="disableTarget ? `停用后员工端将不可检索和调用 ${humanizeText(disableTarget.agentName) || disableTarget.agentCode}` : ''"
    confirm-label="停用"
    :danger="true"
    @confirm="handleDisable"
  />

  <AdminConfirmDialog
    v-model:visible="enableVisible"
    title="启用 AI 助手"
    :message="enableTarget ? `启用后员工端可按权限检索和调用 ${humanizeText(enableTarget.agentName) || enableTarget.agentCode}` : ''"
    confirm-label="启用"
    :danger="false"
    @confirm="handleEnable"
  />
</template>

<style scoped>
.agent-search {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.agent-search input {
  width: 220px;
}

.row-actions {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: nowrap;
  align-items: center;
}

.agent-name-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.agent-name-cell div {
  display: grid;
  gap: 2px;
}

.agent-name-cell strong {
  color: var(--ink);
  font-size: 13px;
}

.agent-name-cell span {
  color: var(--muted);
  font-size: 12px;
}

.form-tip {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.6;
}

.action-btn {
  min-height: 32px;
  padding: 0 var(--space-sm);
  font-size: 12px;
  white-space: nowrap;
}

.create-dialog-form {
  display: grid;
  gap: var(--space-lg);
  margin-top: var(--space-md);
}

.create-dialog-form label {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.model-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-md);
}

.model-form label {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.model-form input,
.model-form select {
  min-height: var(--control-h);
}

.model-form-actions {
  display: flex;
  align-items: end;
  gap: var(--space-sm);
}

.model-list {
  display: grid;
  gap: var(--space-sm);
  margin-top: var(--space-base);
}

.model-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
}

.model-row > div:first-child {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.model-row strong,
.model-row span {
  overflow-wrap: anywhere;
}

.model-row span {
  color: var(--muted);
  font-size: 12px;
}

@media (max-width: 900px) {
  .model-form {
    grid-template-columns: 1fr;
  }
}
</style>
