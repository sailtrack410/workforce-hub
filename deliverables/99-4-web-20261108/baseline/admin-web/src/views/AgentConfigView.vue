<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
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
import { codeFormat, maxLength, requiredText } from '@/utils/validation';

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

const createErrors = computed(() => ({
  agentCode: requiredText(createForm.agentCode, '助手用途') || codeFormat(createForm.agentCode, '助手编码') || maxLength(createForm.agentCode, 64, '助手编码'),
  agentName: requiredText(createForm.agentName, '助手名称') || maxLength(createForm.agentName, 128, '助手名称'),
}));
const editNameError = computed(() => requiredText(editForm.agentName, '助手名称') || maxLength(editForm.agentName, 128, '助手名称'));
const activeAgentCount = computed(() => items.value.filter((item) => item.status === 'ACTIVE').length);
const publishedAgentCount = computed(() => items.value.filter((item) => item.status !== 'DEPRECATED' && item.publishedVersionId).length);
const unpublishedAgentCount = computed(() => items.value.filter((item) => item.status !== 'DEPRECATED' && !item.publishedVersionId).length);
const knowledgeRiskCount = computed(() => items.value.filter((item) => Number(item.knowledgeIssueCount ?? 0) > 0).length);
const inactiveAgentCount = computed(() => items.value.filter((item) => item.status === 'INACTIVE' || item.status === 'DEPRECATED').length);

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
  if (createErrors.value.agentCode || createErrors.value.agentName) return;
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
  if (!editTarget.value || editNameError.value) return;
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
    <AdminPanel :span="12" title="助手发布态" subtitle="识别未发布与知识库异常，确保启用的助手具备可用版本">
      <template #actions>
        <button class="secondary-button" type="button" @click="router.push('/ai-models')">
          <Settings :size="16" />
          <span>模型基座</span>
        </button>
        <button class="primary-button" type="button" @click="openCreate">
          <Plus :size="16" />
          <span>新建助手</span>
        </button>
      </template>
      <div class="agent-governance-grid">
        <article :class="['release-card', { attention: unpublishedAgentCount || knowledgeRiskCount }]">
          <span>当前发布状态</span>
          <strong>{{ unpublishedAgentCount || knowledgeRiskCount ? '需处置' : '发布就绪' }}</strong>
          <p>
            {{ unpublishedAgentCount ? `${unpublishedAgentCount} 个助手尚未发布` : '所有可用助手均已发布' }}
            <template v-if="knowledgeRiskCount"> · {{ knowledgeRiskCount }} 个知识配置异常</template>
          </p>
        </article>
        <div class="agent-metric-grid">
          <article><span>已启用</span><strong>{{ activeAgentCount }}</strong></article>
          <article><span>已发布</span><strong>{{ publishedAgentCount }}</strong></article>
          <article :class="{ attention: knowledgeRiskCount }"><span>知识异常</span><strong>{{ knowledgeRiskCount }}</strong></article>
          <article><span>停用 / 弃用</span><strong>{{ inactiveAgentCount }}</strong></article>
        </div>
      </div>
    </AdminPanel>

    <AdminPanel :span="12" title="助手发布清单" subtitle="编辑基本资料、进入流程编排并管理运行状态">
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
      <button class="secondary-button" title="模型基座" @click="router.push('/ai-models')">
        <Settings :size="16" />
        <span>模型基座</span>
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

  </main>

  <AgentConfigDialogs
    v-model:create-visible="createVisible"
    v-model:disable-visible="disableVisible"
    v-model:delete-visible="deleteVisible"
    v-model:edit-visible="editVisible"
    v-model:enable-visible="enableVisible"
    :agent-options="agentOptions"
    :create-form="createForm"
    :create-errors="createErrors"
    :creating="creating"
    :disable-target="disableTarget"
    :delete-target="deleteTarget"
    :edit-form="editForm"
    :edit-name-error="editNameError"
    :editing="editing"
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

.agent-governance-grid {
  display: grid;
  grid-template-columns: minmax(260px, 1.1fr) minmax(0, 2fr);
  gap: var(--space-md);
}

.release-card,
.agent-metric-grid article {
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  letter-spacing: 0;
}

.release-card {
  display: grid;
  align-content: center;
  gap: var(--space-sm);
  min-height: 132px;
  padding: var(--space-lg);
}

.release-card.attention,
.agent-metric-grid article.attention {
  border-color: var(--error-border);
  background: var(--error-bg);
}

.release-card > span,
.agent-metric-grid span {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.release-card strong {
  font-size: var(--text-xl);
}

.release-card.attention strong,
.agent-metric-grid article.attention strong {
  color: var(--red-dark);
}

.release-card p {
  margin: 0;
  color: var(--muted);
  font-size: var(--text-sm);
  line-height: 1.5;
}

.agent-metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-md);
}

.agent-metric-grid article {
  display: grid;
  align-content: center;
  gap: var(--space-sm);
  min-height: 132px;
  padding: var(--space-base);
}

.agent-metric-grid strong {
  font-size: 26px;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 1050px) {
  .agent-governance-grid { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .agent-metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .release-card,
  .agent-metric-grid article { min-height: 104px; }
}

</style>
