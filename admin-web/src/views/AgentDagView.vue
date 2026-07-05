<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeft, CheckCircle2, Plus, Save, Send, ShieldCheck } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type {
  AgentAclResponse,
  AgentResponse,
  AgentVersionResponse,
  AgentToolResponse,
  LlmModelResponse,
  WorkflowValidationResponse,
} from '@/api/types';
import { errorMessage, humanizeText, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AgentDagEditor from '@/components/AgentDagEditor.vue';

const route = useRoute();
const toast = useToast();
const agentId = route.params.agentId as string;

const loading = ref(true);
const saving = ref(false);
const validating = ref(false);
const publishing = ref(false);
const aclSaving = ref(false);

const agent = ref<AgentResponse | null>(null);
const versions = ref<AgentVersionResponse[]>([]);
const tools = ref<AgentToolResponse[]>([]);
const models = ref<LlmModelResponse[]>([]);
const aclList = ref<AgentAclResponse[]>([]);
const selectedVersionId = ref('');
const newVersionLabel = ref('');

const systemPrompt = ref('');
const modelConfigJson = ref('');
const workflowConfigJson = ref('');
const validationResult = ref<WorkflowValidationResponse | null>(null);
const lastWorkflowTestPassed = ref(false);
const lastWorkflowTestSignature = ref('');
const aclForm = ref({
  subjectId: '1',
  permCode: 'use',
});

const roleOptions = [
  { id: '1', label: '普通用户' },
  { id: '2', label: '领导' },
  { id: '3', label: '管理员' },
];

const selectedVersion = computed(() =>
  versions.value.find((v) => String(v.versionId) === selectedVersionId.value) ?? null,
);

const isReadonly = computed(() => {
  const s = selectedVersion.value?.publishStatus;
  return s === 'PUBLISHED' || s === 'ARCHIVED';
});

const versionStateText = computed(() => {
  const v = selectedVersion.value;
  if (!v) return '';
  if (v.publishStatus === 'PUBLISHED') return '当前线上版本';
  if (v.publishStatus === 'ARCHIVED') return '历史归档版本';
  return '草稿版本';
});

const activeAclList = computed(() =>
  aclList.value.filter((acl) => acl.status === 'ACTIVE' && ['use', 'manage'].includes(acl.permCode)),
);

const hasActiveAcl = computed(() => activeAclList.value.length > 0);

const workflowSignature = computed(() =>
  JSON.stringify({
    workflowConfigJson: workflowConfigJson.value,
    systemPrompt: systemPrompt.value,
    modelConfigJson: modelConfigJson.value,
  }),
);

onMounted(async () => {
  try {
    const [agentData, versionList, toolList, modelList, aclData] = await Promise.all([
      adminApi.agentConfig.detail(agentId),
      adminApi.agentConfig.versions(agentId),
      adminApi.agentConfig.tools(),
      adminApi.llmModels.list(),
      adminApi.agentConfig.acl(agentId),
    ]);
    agent.value = agentData;
    versions.value = versionList;
    tools.value = toolList;
    models.value = modelList;
    aclList.value = aclData;

    let draft = versionList.find((v) => v.publishStatus === 'DRAFT')
      ?? (agentData.publishedVersionId
        ? versionList.find((v) => String(v.versionId) === String(agentData.publishedVersionId)) ?? null
        : versionList[0] ?? null);

    if (!draft) {
      draft = await adminApi.agentConfig.createVersion(agentId, {
        versionNo: 1,
        systemPrompt: '你是一个企业协同助手。',
        modelConfigJson: '{}',
        workflowConfigJson: defaultDag(),
      });
      versions.value = [draft];
    }

    selectVersion(draft);
    resetNewVersionNo();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
});

function selectVersion(version: AgentVersionResponse) {
  selectedVersionId.value = String(version.versionId);
  systemPrompt.value = version.systemPrompt ?? '';
  modelConfigJson.value = version.modelConfigJson ?? '{}';
  workflowConfigJson.value = ensureDag(version.workflowConfigJson);
  validationResult.value = null;
  if (version.workflowTestedAt) {
    lastWorkflowTestPassed.value = true;
    lastWorkflowTestSignature.value = workflowSignature.value;
  } else {
    resetWorkflowTestGate();
  }
}

function resetNewVersionNo() {
  const maxNo = versions.value.reduce((max, ver) => Math.max(max, ver.versionNo), 0);
  newVersionLabel.value = String(maxNo + 1);
}

function versionDisplay(v: AgentVersionResponse | null | undefined) {
  if (!v) return '';
  return v.versionLabel || String(v.versionNo);
}

function ensureDag(value?: string | null) {
  if (!value) return defaultDag();
  try {
    const p = JSON.parse(value);
    if (Array.isArray(p.nodes)) {
      if (isOldDefault(p)) return defaultDag();
      return JSON.stringify(p, null, 2);
    }
  } catch { /* ignore */ }
  return defaultDag();
}

function isOldDefault(parsed: { nodes: { id: string; type: string }[] }): boolean {
  const ids = parsed.nodes.map((n) => n.id).sort().join(',');
  return ids === 'answer,end,start';
}

function defaultDag() {
  return JSON.stringify({
    nodes: [
      {
        id: 'start', type: 'START', position: { x: 80, y: 320 },
        outputFields: [
          { key: 'query', label: '用户请求' },
        ],
      },
      {
        id: 'answer', type: 'LLM', position: { x: 360, y: 320 },
        outputKey: 'answer',
        config: {
          prompt: '请根据用户请求直接回复：{{query}}',
          modelId: defaultModelId(),
        },
        inputFields: [
          { key: 'query', label: '用户输入', ref: '{{start.query}}' },
        ],
        outputFields: [
          { key: 'reply', label: '回复内容' },
        ],
      },
      {
        id: 'end', type: 'END', position: { x: 660, y: 320 },
        config: { reply: '{{answer}}' },
        inputFields: [
          { key: 'reply', label: '回复', ref: '{{answer}}' },
        ],
        outputFields: [],
      },
    ],
    edges: [
      { from: 'start', to: 'answer' },
      { from: 'answer', to: 'end' },
    ],
  }, null, 2);
}

function defaultModelId() {
  return String(models.value.find((m) => m.status === 'ACTIVE')?.modelId ?? models.value[0]?.modelId ?? '');
}

async function handleSave() {
  const v = selectedVersion.value;
  if (!v) return;
  if (v.publishStatus !== 'DRAFT') {
    toast.error('已发布版本不可直接修改，请先创建新草稿');
    return;
  }
  saving.value = true;
  try {
    const updated = await adminApi.agentConfig.updateVersion(v.versionId, {
      systemPrompt: systemPrompt.value,
      modelConfigJson: modelConfigJson.value,
      workflowConfigJson: workflowConfigJson.value,
    });
    const idx = versions.value.findIndex((ver) => ver.versionId === v.versionId);
    if (idx >= 0) versions.value[idx] = updated;
    toast.success('已保存');
    resetWorkflowTestGate();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

async function handleCreateDraft() {
  const versionLabel = newVersionLabel.value.trim();
  if (!versionLabel) {
    toast.error('请填写显示版本号');
    return;
  }
  if (versions.value.some((ver) => versionDisplay(ver) === versionLabel)) {
    toast.error('显示版本号已存在');
    return;
  }
  const versionNo = Math.max(0, ...versions.value.map((ver) => ver.versionNo)) + 1;
  saving.value = true;
  try {
    const newVersion = await adminApi.agentConfig.createVersion(agentId, {
      versionNo,
      versionLabel,
      systemPrompt: systemPrompt.value,
      modelConfigJson: modelConfigJson.value,
      workflowConfigJson: workflowConfigJson.value,
    });
    versions.value = [newVersion, ...versions.value].sort((a, b) => b.versionNo - a.versionNo);
    selectVersion(newVersion);
    resetNewVersionNo();
    toast.success(`已创建草稿 V${versionDisplay(newVersion)}`);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

async function handleValidate() {
  validating.value = true;
  validationResult.value = null;
  try {
    validationResult.value = await adminApi.agentConfig.validateWorkflow({
      workflowConfigJson: workflowConfigJson.value,
    });
    if (validationResult.value.valid) toast.success('校验通过');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    validating.value = false;
  }
}

async function handlePublish() {
  const v = selectedVersion.value;
  if (!v) return;
  if (v.publishStatus !== 'DRAFT') {
    toast.error('只有草稿版本可以发布');
    return;
  }
  if (!hasActiveAcl.value) {
    toast.error('请先配置 AI 助手可用范围');
    return;
  }
  if (!lastWorkflowTestPassed.value || lastWorkflowTestSignature.value !== workflowSignature.value) {
    toast.error('请先完成一次流程测试');
    return;
  }
  publishing.value = true;
  try {
    await adminApi.agentConfig.publishVersion(v.versionId);
    toast.success('已发布');
    const [agentData, list] = await Promise.all([
      adminApi.agentConfig.detail(agentId),
      adminApi.agentConfig.versions(agentId),
    ]);
    agent.value = agentData;
    versions.value = list;
    const updated = list.find((ver) => String(ver.versionId) === selectedVersionId.value);
    if (updated) selectVersion(updated);
    resetNewVersionNo();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    publishing.value = false;
  }
}

async function handleGrantAcl() {
  if (!aclForm.value.subjectId.trim()) {
    toast.error('请选择可用身份');
    return;
  }
  aclSaving.value = true;
  try {
    await adminApi.agentConfig.grantAcl(agentId, {
      subjectType: 'ROLE',
      subjectId: aclForm.value.subjectId.trim(),
      permCode: aclForm.value.permCode,
      status: 'ACTIVE',
    });
    aclList.value = await adminApi.agentConfig.acl(agentId);
    toast.success('已配置可用范围');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    aclSaving.value = false;
  }
}

function handleWorkflowTestResult(result: { success: boolean }) {
  lastWorkflowTestPassed.value = result.success;
  lastWorkflowTestSignature.value = result.success ? workflowSignature.value : '';
}

function resetWorkflowTestGate() {
  lastWorkflowTestPassed.value = false;
  lastWorkflowTestSignature.value = '';
}

function aclSubjectLabel(type: string) {
  return {
    USER: '用户',
    ROLE: '身份',
    DEPARTMENT: '部门',
    GROUP: '群会话',
    ADMIN_USER: '管理员',
  }[type] ?? type;
}

function aclSubjectName(acl: AgentAclResponse) {
  if (acl.subjectType !== 'ROLE') return String(acl.subjectId);
  return roleOptions.find((role) => role.id === String(acl.subjectId))?.label ?? String(acl.subjectId);
}

function aclPermissionLabel(code: string) {
  return {
    visible: '可见',
    use: '可用',
    manage: '管理',
  }[code] ?? code;
}

function goBack() {
  if (window.history.length > 1) window.close();
  else window.location.hash = '#/agent-config';
}

watch([workflowConfigJson, systemPrompt, modelConfigJson], () => {
  if (lastWorkflowTestSignature.value && lastWorkflowTestSignature.value !== workflowSignature.value) {
    resetWorkflowTestGate();
  }
});
</script>

<template>
  <div v-if="loading" class="dag-fullscreen">
    <div class="empty-state">加载中…</div>
  </div>
  <div v-else-if="agent" class="dag-fullscreen">
    <header class="dag-commandbar">
      <div class="dag-commandbar-main">
        <button class="icon-button dag-back" title="返回列表" @click="goBack">
          <ArrowLeft :size="16" />
        </button>
        <div class="dag-title-group">
          <span class="bar-title">{{ humanizeText(agent.agentName) || agent.agentCode }}</span>
          <span class="dag-agent-code">{{ agent.agentCode }}</span>
        </div>
        <span :class="['version-state', selectedVersion?.publishStatus?.toLowerCase()]">{{ versionStateText }}</span>
        <select class="bar-select" :value="selectedVersionId" @change="selectVersion(versions.find((v) => String(v.versionId) === ($event.target as HTMLSelectElement).value)!)">
          <option v-for="v in versions" :key="v.versionId" :value="String(v.versionId)">
            V{{ versionDisplay(v) }} · {{ statusLabel(v.publishStatus) }}
          </option>
        </select>
        <div class="version-create">
          <input v-model="newVersionLabel" title="新草稿显示版本号" />
          <button class="bar-btn" :disabled="saving" @click="handleCreateDraft">
            <Plus :size="14" /> 草稿
          </button>
        </div>
      </div>
      <div class="dag-commandbar-actions">
        <span v-if="isReadonly" class="bar-readonly">只读</span>
        <span v-if="validationResult && !validationResult.valid" class="bar-error">{{ validationResult.messages.join('；') }}</span>
        <span :class="['acl-state', hasActiveAcl ? 'ok' : 'warn']">{{ hasActiveAcl ? '范围已配置' : '范围未配置' }}</span>
        <span :class="['acl-state', lastWorkflowTestPassed ? 'ok' : 'warn']">{{ lastWorkflowTestPassed ? '流程已测' : '流程待测' }}</span>
        <button class="bar-btn" :disabled="validating || isReadonly" @click="handleValidate">
          <CheckCircle2 :size="14" /> 校验
        </button>
        <button class="bar-btn" :disabled="saving || isReadonly" @click="handleSave">
          <Save :size="14" /> 保存
        </button>
        <button class="bar-btn primary" :disabled="publishing || selectedVersion?.publishStatus !== 'DRAFT'" @click="handlePublish">
          <Send :size="14" /> 发布
        </button>
      </div>
    </header>

    <div class="dag-workspace">
      <aside class="dag-acl-panel">
        <div class="acl-title">
          <ShieldCheck :size="15" />
          <span>可用范围</span>
        </div>
        <div class="acl-form">
          <select v-model="aclForm.subjectId" :disabled="aclSaving">
            <option v-for="role in roleOptions" :key="role.id" :value="role.id">{{ role.label }}</option>
          </select>
          <select v-model="aclForm.permCode" :disabled="aclSaving">
            <option value="use">可用</option>
            <option value="visible">可见</option>
            <option value="manage">管理</option>
          </select>
          <button type="button" :disabled="aclSaving || isReadonly" @click="handleGrantAcl">添加</button>
        </div>
        <div class="acl-list">
          <span v-for="acl in aclList" :key="acl.aclId">
            {{ aclSubjectLabel(acl.subjectType) }} {{ aclSubjectName(acl) }} · {{ aclPermissionLabel(acl.permCode) }} · {{ statusLabel(acl.status) }}
          </span>
        </div>
      </aside>

      <AgentDagEditor
        v-model="workflowConfigJson"
        :tools="tools"
        :models="models.map(m => ({ modelId: String(m.modelId), displayName: m.displayName, provider: m.provider }))"
        :readonly="isReadonly"
        :version-id="selectedVersion?.versionId"
        :system-prompt="systemPrompt"
        :model-config-json="modelConfigJson"
        @update:system-prompt="(v) => systemPrompt = v"
        @update:model-config-json="(v) => modelConfigJson = v"
        @workflow-test-result="handleWorkflowTestResult"
      />
    </div>
  </div>
  <div v-else class="dag-fullscreen">
    <div class="empty-state">AI 助手不存在</div>
  </div>
</template>

<style scoped>
.dag-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--surface);
}

.dag-commandbar {
  position: relative;
  z-index: 12;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-base);
  min-height: 58px;
  padding: var(--space-sm) var(--space-base);
  border-bottom: 1px solid var(--line);
  background: var(--panel);
  color: var(--ink);
  box-shadow: var(--shadow-sm);
}

.dag-commandbar-main,
.dag-commandbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.dag-commandbar-actions {
  justify-content: flex-end;
  flex-wrap: wrap;
}

.dag-back {
  color: var(--ink);
  background: var(--panel);
  border-color: var(--line);
}

.dag-title-group {
  display: grid;
  gap: var(--space-xs);
  min-width: 0;
}

.bar-title {
  font-size: 15px;
  font-weight: 900;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dag-agent-code {
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-state {
  border-radius: var(--radius-pill);
  padding: 5px var(--space-sm);
  background: var(--pill-bg);
  color: var(--green-dark);
  font-size: 11px;
  font-weight: 900;
  white-space: nowrap;
}

.version-state.archived {
  background: var(--surface);
  color: var(--muted);
}

.version-state.published {
  background: var(--success-bg);
  color: var(--success);
}

.bar-select {
  width: auto;
  min-width: 154px;
  min-height: 34px;
  padding: 4px var(--space-sm);
  font-size: 12px;
  font-weight: 700;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--ink);
}

.bar-select option {
  color: var(--ink);
}

.bar-error {
  color: var(--red-dark);
  font-size: 11px;
  font-weight: 900;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bar-readonly {
  color: var(--amber);
  font-size: 11px;
  font-weight: 900;
  white-space: nowrap;
}

.bar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-xs);
  min-height: 34px;
  padding: 0 var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: all 200ms ease;
}

.bar-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.bar-btn:hover:not(:disabled) {
  border-color: var(--green-light);
  color: var(--green-dark);
}

.bar-btn.primary {
  background: var(--green);
  border-color: var(--green);
  color: var(--panel-strong);
}

.bar-btn.primary:hover:not(:disabled) {
  background: var(--green-dark);
}

.dag-workspace {
  display: flex;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.dag-acl-panel {
  width: 286px;
  flex-shrink: 0;
  padding: var(--space-base);
  border-right: 1px solid var(--line);
  background: var(--panel);
  box-shadow: var(--shadow-sm);
  overflow-y: auto;
}

.version-create {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
}

.version-create input {
  width: 58px;
  min-height: 34px;
  padding: 4px var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
}

.acl-title {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin-bottom: var(--space-md);
  color: var(--ink);
  font-size: 13px;
  font-weight: 900;
}

.acl-state {
  padding: 4px var(--space-sm);
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 900;
  white-space: nowrap;
}

.acl-state.ok {
  color: var(--green-dark);
  background: var(--success-bg);
}

.acl-state.warn {
  color: var(--red-dark);
  background: var(--error-bg);
}

.acl-form {
  display: grid;
  grid-template-columns: 1fr;
  gap: var(--space-sm);
}

.acl-form select,
.acl-form input {
  min-width: 0;
  height: 34px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
}

.acl-form input {
  padding: 0 var(--space-sm);
}

.acl-form button {
  height: 34px;
  border: 1px solid var(--green);
  border-radius: var(--radius-sm);
  background: var(--green);
  color: var(--panel-strong);
  font-size: 12px;
  font-weight: 700;
  transition: all 200ms ease;
}

.acl-form button:hover:not(:disabled) {
  border-color: var(--green-dark);
  background: var(--green-dark);
}

.acl-form button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.acl-list {
  display: grid;
  gap: var(--space-sm);
  margin-top: var(--space-md);
  overflow: auto;
}

.acl-list span {
  max-width: 100%;
  padding: 7px var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--muted);
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 1180px) {
  .dag-commandbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .dag-commandbar-main,
  .dag-commandbar-actions {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;
  }
}

@media (max-width: 980px) {
  .dag-workspace {
    flex-direction: column;
  }

  .dag-acl-panel {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid rgba(15, 23, 42, 0.1);
  }

  .acl-form {
    grid-template-columns: minmax(0, 1fr) 110px 70px;
  }

  .acl-list {
    display: flex;
    flex-wrap: wrap;
  }
}
</style>
