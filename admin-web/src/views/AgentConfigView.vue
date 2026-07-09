<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { AlertCircle, Pencil, PlayCircle, Plus, PowerOff, RefreshCcw, Search, Send, Settings, Trash2 } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import { adminApi } from '@/api/admin';
import type { AgentResponse } from '@/api/types';
import { errorMessage, formatDateTime, humanizeText, safeBusinessMessage, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminIconAvatar from '@/components/AdminIconAvatar.vue';
import AgentConfigDialogs from '@/components/AgentConfigDialogs.vue';

const router = useRouter();
const toast = useToast();

const loading = ref(false);
const items = ref<AgentResponse[]>([]);
const keyword = ref('');

const createVisible = ref(false);
const createForm = reactive({ agentCode: 'LEAVE_HELPER', agentName: '假期助手', avatarAttachmentRefId: '' });
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
const deleteTarget = ref<AgentResponse | null>(null);
const deleteVisible = ref(false);
const deleting = ref(false);

const agentOptions = [
  { value: 'LEAVE_HELPER', label: '假期助手' },
  { value: 'ATTENDANCE_HELPER', label: '考勤助手' },
  { value: 'POLICY_HELPER', label: '制度问答助手' },
];

const columns: TableColumn[] = [
  { key: 'agentName', label: '助手' },
  { key: 'agentType', label: '类型' },
  { key: 'status', label: '状态' },
  { key: 'publishedVersionId', label: '发布版本' },
  { key: 'createdAt', label: '创建时间' },
  { key: '_actions', label: '操作', width: '310px' },
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

function handleSearch() {
  void loadList();
}

function agentTypeLabel(value?: string | null) {
  if (value === 'QA') return '问答助手';
  return value ? '其他助手' : '-';
}

function isDeprecated(agent: AgentResponse) {
  return agent.status === 'DEPRECATED';
}

function openCreate() {
  createForm.agentCode = 'LEAVE_HELPER';
  createForm.agentName = '假期助手';
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

function askDelete(agent: AgentResponse) {
  deleteTarget.value = agent;
  deleteVisible.value = true;
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

async function handleDelete() {
  if (!deleteTarget.value || deleting.value) return;
  deleting.value = true;
  try {
    await adminApi.agentConfig.remove(deleteTarget.value.agentId);
    deleteVisible.value = false;
    toast.success('AI 助手已弃用');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    deleting.value = false;
  }
}

onMounted(() => {
  void loadList();
});
</script>

<template>
  <main class="page-grid">
    <AdminPanel :span="12" title="AI 助手">
    <template #actions>
      <form class="agent-search" @submit.prevent="handleSearch">
        <input v-model="keyword" placeholder="搜索名称" />
        <button class="secondary-button" title="搜索" type="submit">
          <Search :size="16" />
        </button>
      </form>
      <button class="secondary-button" title="刷新" @click="loadList">
        <RefreshCcw :size="16" />
        <span>刷新</span>
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
          <AdminIconAvatar :label="humanizeText(row.agentName) || 'AI助手'" kind="agent" size="sm" />
          <div>
            <strong>{{ humanizeText(row.agentName) || 'AI 助手' }}</strong>
            <span v-if="row.knowledgeIssueCount" class="agent-risk-text">
              <AlertCircle :size="13" />
              <span>{{ safeBusinessMessage(row.knowledgeIssueMessage, '知识库配置异常') }}</span>
            </span>
          </div>
        </div>
      </template>
      <template #cell-status="{ row }">
        <div class="agent-status-cell">
          <span class="status-pill">{{ statusLabel(row.status) }}</span>
          <span v-if="row.knowledgeIssueCount" class="status-pill status-danger">知识库异常</span>
        </div>
      </template>
      <template #cell-agentType="{ row }">
        {{ row.agentTypeName || agentTypeLabel(row.agentType) }}
      </template>
      <template #cell-publishedVersionId="{ row }">
        {{ row.publishedVersionId ? `V${row.publishedVersionLabel || row.publishedVersionNo}` : '未发布' }}
      </template>
      <template #cell-createdAt="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>
      <template #cell-_actions="{ row }">
        <div class="row-actions">
          <button class="secondary-button action-btn" title="编辑资料" :disabled="isDeprecated(row)" @click.stop="openEdit(row)">
            <Pencil :size="14" />
            <span>编辑</span>
          </button>
          <button class="primary-button action-btn" title="流程编排" @click.stop="openDagEditor(row)">
            <Send :size="14" />
            <span>{{ isDeprecated(row) ? '查看' : '编排' }}</span>
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
            v-else-if="!isDeprecated(row)"
            class="danger-button action-btn"
            title="停用"
            :disabled="row.status !== 'ACTIVE'"
            @click.stop="askDisable(row)"
          >
            <PowerOff :size="14" />
            <span>停用</span>
          </button>
          <button
            class="danger-button action-btn"
            title="删除"
            :disabled="isDeprecated(row) || deleting"
            @click.stop="askDelete(row)"
          >
            <Trash2 :size="14" />
            <span>删除</span>
          </button>
        </div>
      </template>
    </AdminTable>
  </AdminPanel>

  <AdminPanel :span="12" title="AI 模型基座">
    <template #actions>
      <button class="primary-button" title="进入基座" @click="router.push('/ai-models')">
        <Settings :size="16" />
        <span>进入基座</span>
      </button>
    </template>
    <div class="foundation-entry">
      <strong>统一配置入口</strong>
      <span>Agent 工作流只选择基座中的模型。</span>
    </div>
  </AdminPanel>
  </main>

  <AgentConfigDialogs
    v-model:create-visible="createVisible"
    v-model:disable-visible="disableVisible"
    v-model:delete-visible="deleteVisible"
    v-model:edit-visible="editVisible"
    v-model:enable-visible="enableVisible"
    :agent-options="agentOptions"
    :create-form="createForm"
    :disable-target="disableTarget"
    :delete-target="deleteTarget"
    :edit-form="editForm"
    :enable-target="enableTarget"
    @create="handleCreate"
    @disable="handleDisable"
    @delete="handleDelete"
    @edit="handleEdit"
    @enable="handleEnable"
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

.agent-risk-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  color: var(--red-dark);
  font-size: 12px;
  font-weight: 700;
}

.agent-risk-text span {
  color: var(--red-dark);
}

.agent-status-cell {
  display: grid;
  gap: var(--space-xs);
  justify-items: start;
}

.status-danger {
  background: var(--error-bg);
  color: var(--red-dark);
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

.foundation-entry {
  display: grid;
  gap: var(--space-sm);
  padding: var(--space-lg);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
}

.foundation-entry strong {
  color: var(--ink);
  font-size: var(--text-md);
}

.foundation-entry span {
  color: var(--muted);
  font-size: var(--text-sm);
}
</style>
