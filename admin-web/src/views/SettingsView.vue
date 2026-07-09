<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { CheckCircle2, FlaskConical, MoreHorizontal, Pencil, Plus, RefreshCcw, Save, Send, Trash2 } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { AiProviderResponse, CreateLlmModelRequest, EntityId, LlmModelResponse, NotificationChannelResponse, NotificationChannelSaveRequest, ProviderModelOptionResponse, SaveAiProviderRequest, TestLlmModelRequest } from '@/api/types';
import { errorMessage, formatDateTime, safeBusinessMessage, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const toast = useToast();
const route = useRoute();

type AiTab = 'config' | 'models' | 'defaults' | 'test';
type SettingsTab = 'models' | 'notifications';

const activeTab = computed<SettingsTab>(() => route.path.startsWith('/ai-models') ? 'models' : 'notifications');
const aiTab = ref<AiTab>('config');
const loading = ref(false);
const providers = ref<AiProviderResponse[]>([]);
const models = ref<LlmModelResponse[]>([]);
const selectedProviderId = ref<EntityId | null>(null);
const selectedModel = ref<LlmModelResponse | null>(null);
const providerDialogVisible = ref(false);
const providerEditId = ref<EntityId | null>(null);
const providerSaving = ref(false);
const providerTesting = ref(false);
const modelDialogVisible = ref(false);
const modelEditId = ref<EntityId | null>(null);
const modelSaving = ref(false);
const modelTesting = ref(false);
const providerModelLoading = ref(false);
const providerModelOptions = ref<ProviderModelOptionResponse[]>([]);
const providerModelOptionsProviderId = ref<EntityId | null>(null);
const testResult = ref<{ success: boolean; message: string; durationMs?: number } | null>(null);
const deleteTarget = ref<LlmModelResponse | null>(null);
const deleteVisible = ref(false);
const providerDeleteTarget = ref<AiProviderResponse | null>(null);
const providerDeleteVisible = ref(false);
const openModelMenuId = ref<EntityId | null>(null);

const providerForm = reactive({
  providerType: 'openai',
  displayName: 'OpenAI',
  baseUrl: '',
  apiKey: '',
  enabled: true,
});

const modelForm = reactive({
  provider: 'openai',
  modelType: 'TEXT',
  modelName: 'gpt-4o-mini',
  displayName: 'GPT-4o mini',
  endpoint: '',
  apiKey: '',
  defaultFlag: false,
  streamOutputFlag: false,
  visionFlag: false,
  toolCallFlag: false,
  structuredOutputFlag: false,
  reasoningFlag: false,
  status: 'ACTIVE',
});

const providerOptions = [
  { value: 'openai', label: 'OpenAI', endpoint: 'https://api.openai.com/v1' },
  { value: 'deepseek', label: 'DeepSeek', endpoint: 'https://api.deepseek.com/v1' },
  { value: 'anthropic', label: 'Claude', endpoint: 'https://api.anthropic.com/v1' },
  { value: 'qwen', label: '通义千问', endpoint: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { value: 'doubao', label: '豆包', endpoint: 'https://ark.cn-beijing.volces.com/api/v3' },
  { value: 'siliconflow', label: '硅基流动', endpoint: 'https://api.siliconflow.cn/v1' },
  { value: 'mimo', label: 'MiMo', endpoint: 'https://api.xiaomimimo.com/v1' },
  { value: 'openai-compatible', label: '兼容接入', endpoint: '' },
];

const modelTypeOptions = [
  { value: 'TEXT', label: '语言模型' },
  { value: 'EMBEDDING', label: 'Embedding' },
  { value: 'RERANK', label: 'Rerank' },
  { value: 'TTS', label: 'TTS' },
  { value: 'VISION', label: '视觉' },
  { value: 'OCR', label: 'OCR' },
];

const aiTabs = [
  { value: 'config', label: '模型配置' },
  { value: 'models', label: '模型列表' },
  { value: 'defaults', label: '默认模型' },
  { value: 'test', label: '测试' },
] as const;

const modelColumns: TableColumn[] = [
  { key: 'displayName', label: '模型' },
  { key: 'modelType', label: '类型', width: '120px' },
  { key: 'status', label: '启用', width: '110px' },
  { key: 'lastTestedAt', label: '模型连通性', width: '170px' },
  { key: '_actions', label: '操作', width: '220px' },
];

const emailLoading = ref(false);
const emailSaving = ref(false);
const emailTesting = ref(false);
const emailChannel = ref<NotificationChannelResponse | null>(null);
const emailStatus = ref('未配置');
const emailForm = reactive({
  enabled: false,
  host: '',
  port: 465,
  username: '',
  secretValue: '',
  fromAddress: '',
  fromName: '',
  sslEnabled: true,
  starttlsEnabled: false,
  timeoutMillis: 5000,
  recipient: '',
});

const selectedProvider = computed(() => providers.value.find((item) => String(item.providerId) === String(selectedProviderId.value)) ?? providers.value[0] ?? null);
const selectedProviderType = computed(() => selectedProvider.value?.providerType ?? '');
const providerModels = computed(() => models.value.filter((item) => item.provider === selectedProviderType.value));
const defaultModels = computed(() => models.value.filter((item) => item.defaultFlag));
const providerBaseUrl = computed(() => selectedProvider.value?.baseUrl || providerOptions.find((item) => item.value === selectedProviderType.value)?.endpoint || '');
const providerStatus = computed(() => {
  if (!selectedProvider.value) return '未配置';
  if (!selectedProvider.value.enabled) return '未启用';
  if (selectedProvider.value.lastTestSuccess === true) return '正常';
  if (selectedProvider.value.lastTestSuccess === false) return '异常';
  return '未测试';
});

function providerTypeLabel(value?: string | null) {
  return providerOptions.find((item) => item.value === value)?.label || '供应商';
}

function modelTypeLabel(value?: string | null) {
  return modelTypeOptions.find((item) => item.value === value)?.label || '模型';
}

function testState(item?: LlmModelResponse | null) {
  if (!item) return '未测试';
  if (item.lastTestSuccess === true) return '模型可用';
  if (item.lastTestSuccess === false) return '模型不可用';
  return '未测试';
}

function testStateClass(item?: LlmModelResponse | null) {
  if (item?.lastTestSuccess === true) return 'health-ok';
  if (item?.lastTestSuccess === false) return 'health-fail';
  return 'health-unknown';
}

function testStateDetail(item?: LlmModelResponse | null) {
  if (!item) return '等待检测';
  if (item.lastTestedAt && item.lastTestMessage) return `${safeBusinessMessage(item.lastTestMessage, '已检测')} · ${formatDateTime(item.lastTestedAt)}`;
  if (item.lastTestedAt) return `上次 ${formatDateTime(item.lastTestedAt)}`;
  return safeBusinessMessage(item.lastTestMessage, '等待检测');
}

function providerDotClass(provider?: AiProviderResponse | null) {
  if (!provider || provider.enabled === false || provider.lastTestSuccess == null) return 'dot idle';
  return provider.lastTestSuccess === false ? 'dot bad' : 'dot';
}

function modelEnabledLabel(item: LlmModelResponse) {
  return item.status === 'ACTIVE' ? '启用' : statusLabel(item.status);
}

function modelFeatures(model: LlmModelResponse) {
  const list = [model.modelTypeName || modelTypeLabel(model.modelType)];
  if (model.streamOutputFlag) list.push('流式输出');
  if (model.visionFlag) list.push('视觉');
  if (model.toolCallFlag) list.push('工具调用');
  if (model.structuredOutputFlag) list.push('结构化输出');
  if (model.reasoningFlag) list.push('推理');
  return list;
}

function providerTypeChanged() {
  const option = providerOptions.find((item) => item.value === providerForm.providerType);
  providerForm.displayName = option?.label || providerForm.displayName;
  providerForm.baseUrl = '';
}

function selectedProviderChanged(providerId: EntityId) {
  selectedProviderId.value = providerId;
  selectedModel.value = providerModels.value[0] || null;
  if (String(providerModelOptionsProviderId.value) !== String(providerId)) {
    providerModelOptions.value = [];
    providerModelOptionsProviderId.value = null;
  }
}

async function loadAiBase() {
  loading.value = true;
  try {
    const providerRows = await adminApi.aiProviders.list();
    providers.value = providerRows;
    if (!selectedProviderId.value || !providers.value.some((item) => String(item.providerId) === String(selectedProviderId.value))) {
      selectedProviderId.value = providers.value[0]?.providerId ?? null;
    }
    try {
      models.value = await adminApi.llmModels.list();
    } catch {
      models.value = [];
    }
    selectedModel.value = selectedModel.value
      ? models.value.find((item) => String(item.modelId) === String(selectedModel.value?.modelId)) || null
      : providerModels.value[0] || null;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function openProviderCreate() {
  providerEditId.value = null;
  providerForm.providerType = 'openai';
  providerForm.displayName = 'OpenAI';
  providerForm.baseUrl = '';
  providerForm.apiKey = '';
  providerForm.enabled = true;
  providerDialogVisible.value = true;
}

function openProviderEdit(provider: AiProviderResponse) {
  providerEditId.value = provider.providerId;
  providerForm.providerType = provider.providerType;
  providerForm.displayName = provider.displayName;
  providerForm.baseUrl = provider.baseUrl || '';
  providerForm.apiKey = '';
  providerForm.enabled = provider.enabled !== false;
  providerDialogVisible.value = true;
}

function buildProviderRequest(): SaveAiProviderRequest {
  return {
    providerType: providerForm.providerType,
    displayName: providerForm.displayName.trim(),
    baseUrl: providerForm.baseUrl.trim() || null,
    apiKey: providerForm.apiKey.trim() || null,
    enabled: providerForm.enabled,
  };
}

async function saveProvider() {
  if (!providerForm.providerType || !providerForm.displayName.trim() || providerSaving.value) return;
  providerSaving.value = true;
  try {
    const row = providerEditId.value
      ? await adminApi.aiProviders.update(providerEditId.value, buildProviderRequest())
      : await adminApi.aiProviders.create(buildProviderRequest());
    selectedProviderId.value = row.providerId;
    providerDialogVisible.value = false;
    toast.success('已保存');
    await loadAiBase();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    providerSaving.value = false;
  }
}

async function testProvider(provider: AiProviderResponse) {
  providerTesting.value = true;
  testResult.value = null;
  try {
    const result = await adminApi.aiProviders.test(provider.providerId);
    testResult.value = {
      success: result.success,
      message: safeBusinessMessage(result.message, result.success ? '测试成功' : '测试失败'),
      durationMs: result.durationMs,
    };
    await loadAiBase();
  } catch (e) {
    testResult.value = { success: false, message: errorMessage(e) };
  } finally {
    providerTesting.value = false;
  }
}

function askProviderDelete(provider: AiProviderResponse) {
  providerDeleteTarget.value = provider;
  providerDeleteVisible.value = true;
}

async function deleteProvider() {
  if (!providerDeleteTarget.value) return;
  try {
    await adminApi.aiProviders.remove(providerDeleteTarget.value.providerId);
    selectedProviderId.value = null;
    selectedModel.value = null;
    toast.success('已删除');
    await loadAiBase();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    providerDeleteVisible.value = false;
  }
}

function openCreateModel() {
  if (!selectedProvider.value) {
    aiTab.value = 'config';
    toast.error('请先添加供应商');
    return;
  }
  modelEditId.value = null;
  modelForm.provider = selectedProviderType.value;
  modelForm.endpoint = '';
  modelForm.modelType = 'TEXT';
  modelForm.modelName = '';
  modelForm.displayName = '';
  modelForm.apiKey = '';
  modelForm.defaultFlag = false;
  modelForm.streamOutputFlag = false;
  modelForm.visionFlag = false;
  modelForm.toolCallFlag = false;
  modelForm.structuredOutputFlag = false;
  modelForm.reasoningFlag = false;
  modelForm.status = 'ACTIVE';
  testResult.value = null;
  modelDialogVisible.value = true;
  if (String(providerModelOptionsProviderId.value) !== String(selectedProvider.value.providerId)) {
    providerModelOptions.value = [];
    providerModelOptionsProviderId.value = null;
  }
}

function openEditModel(model: LlmModelResponse) {
  modelEditId.value = model.modelId;
  modelForm.provider = model.provider;
  modelForm.endpoint = '';
  modelForm.modelType = model.modelType || 'TEXT';
  modelForm.modelName = model.modelName;
  modelForm.displayName = model.displayName;
  modelForm.apiKey = '';
  modelForm.defaultFlag = Boolean(model.defaultFlag);
  modelForm.streamOutputFlag = Boolean(model.streamOutputFlag);
  modelForm.visionFlag = Boolean(model.visionFlag);
  modelForm.toolCallFlag = Boolean(model.toolCallFlag);
  modelForm.structuredOutputFlag = Boolean(model.structuredOutputFlag);
  modelForm.reasoningFlag = Boolean(model.reasoningFlag);
  modelForm.status = model.status || 'ACTIVE';
  testResult.value = null;
  modelDialogVisible.value = true;
}

async function fetchProviderModelOptions() {
  if (!selectedProvider.value || providerModelLoading.value) return;
  providerModelLoading.value = true;
  try {
    const rows = await adminApi.aiProviders.models(selectedProvider.value.providerId);
    providerModelOptions.value = rows;
    providerModelOptionsProviderId.value = selectedProvider.value.providerId;
    if (rows.length === 0) {
      toast.error('没有获取到模型');
    } else {
      const currentModelName = modelForm.modelName.trim();
      const hasCurrentModel = rows.some((item) => item.modelName === currentModelName);
      if (!currentModelName || !hasCurrentModel) {
        applyProviderModelOption(rows[0].modelName);
      }
      toast.success(`已获取 ${rows.length} 个模型`);
    }
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    providerModelLoading.value = false;
  }
}

function applyProviderModelOption(modelName: string) {
  const option = providerModelOptions.value.find((item) => item.modelName === modelName);
  if (!option) return;
  modelForm.modelName = option.modelName;
  modelForm.displayName = option.displayName || option.modelName;
  if (option.modelType && modelTypeOptions.some((item) => item.value === option.modelType)) {
    modelForm.modelType = option.modelType;
    if (option.modelType === 'VISION' || option.modelType === 'OCR') {
      modelForm.visionFlag = true;
    }
  }
}

function buildModelRequest(): CreateLlmModelRequest {
  return {
    provider: modelForm.provider,
    modelType: modelForm.modelType,
    modelName: modelForm.modelName.trim(),
    displayName: modelForm.displayName.trim(),
    endpoint: modelForm.endpoint.trim() || null,
    apiKey: modelForm.apiKey.trim() || null,
    defaultFlag: modelForm.defaultFlag,
    streamOutputFlag: modelForm.streamOutputFlag,
    visionFlag: modelForm.visionFlag,
    toolCallFlag: modelForm.toolCallFlag,
    structuredOutputFlag: modelForm.structuredOutputFlag,
    reasoningFlag: modelForm.reasoningFlag,
    status: modelForm.status,
  };
}

function buildModelTestRequest(): TestLlmModelRequest {
  return {
    provider: modelForm.provider,
    modelType: modelForm.modelType,
    modelName: modelForm.modelName.trim(),
    endpoint: modelForm.endpoint.trim() || null,
    apiKey: modelForm.apiKey.trim() || null,
    visionFlag: modelForm.visionFlag,
  };
}

async function saveModel() {
  if (!modelForm.modelName.trim() || !modelForm.displayName.trim() || modelSaving.value) return;
  modelSaving.value = true;
  try {
    const row = modelEditId.value
      ? await adminApi.llmModels.update(modelEditId.value, buildModelRequest())
      : await adminApi.llmModels.create(buildModelRequest());
    selectedModel.value = row;
    selectedProviderId.value = providers.value.find((item) => item.providerType === row.provider)?.providerId ?? selectedProviderId.value;
    modelDialogVisible.value = false;
    toast.success('已保存');
    await loadAiBase();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    modelSaving.value = false;
  }
}

async function testDraftModel() {
  if (!modelForm.modelName.trim() || modelTesting.value) return;
  modelTesting.value = true;
  testResult.value = null;
  try {
    const result = await adminApi.llmModels.testDraft(buildModelTestRequest());
    testResult.value = {
      success: result.success,
      message: safeBusinessMessage(result.message, result.success ? '测试成功' : '测试失败'),
      durationMs: result.durationMs,
    };
  } catch (e) {
    testResult.value = { success: false, message: errorMessage(e) };
  } finally {
    modelTesting.value = false;
  }
}

async function testSavedModel(model: LlmModelResponse) {
  openModelMenuId.value = null;
  selectedModel.value = model;
  modelTesting.value = true;
  testResult.value = null;
  try {
    const result = await adminApi.llmModels.test(model.modelId);
    testResult.value = {
      success: result.success,
      message: safeBusinessMessage(result.message, result.success ? '测试成功' : '测试失败'),
      durationMs: result.durationMs,
    };
    await loadAiBase();
  } catch (e) {
    testResult.value = { success: false, message: errorMessage(e) };
  } finally {
    modelTesting.value = false;
  }
}

function askDelete(model: LlmModelResponse) {
  openModelMenuId.value = null;
  deleteTarget.value = model;
  deleteVisible.value = true;
}

async function doDelete() {
  if (!deleteTarget.value) return;
  try {
    await adminApi.llmModels.remove(deleteTarget.value.modelId);
    if (String(selectedModel.value?.modelId) === String(deleteTarget.value.modelId)) selectedModel.value = null;
    toast.success('已删除');
    await loadAiBase();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    deleteVisible.value = false;
  }
}

async function setDefaultModel(model: LlmModelResponse) {
  openModelMenuId.value = null;
  const request: CreateLlmModelRequest = {
    provider: model.provider,
    modelType: model.modelType || 'TEXT',
    modelName: model.modelName,
    displayName: model.displayName,
    endpoint: model.endpoint || null,
    apiKey: null,
    defaultFlag: true,
    streamOutputFlag: model.streamOutputFlag,
    visionFlag: model.visionFlag,
    toolCallFlag: model.toolCallFlag,
    structuredOutputFlag: model.structuredOutputFlag,
    reasoningFlag: model.reasoningFlag,
    status: model.status,
  };
  try {
    await adminApi.llmModels.update(model.modelId, request);
    toast.success('已保存');
    await loadAiBase();
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

function handleDefaultChange(value: string) {
  const model = models.value.find((item) => String(item.modelId) === value);
  if (model) void setDefaultModel(model);
}

function selectModel(model: LlmModelResponse) {
  selectedModel.value = model;
  openModelMenuId.value = null;
}

function isModelMenuOpen(model: LlmModelResponse) {
  return String(openModelMenuId.value) === String(model.modelId);
}

function toggleModelMenu(model: LlmModelResponse) {
  selectedModel.value = model;
  openModelMenuId.value = isModelMenuOpen(model) ? null : model.modelId;
}

function openModelEditFromMenu(model: LlmModelResponse) {
  openModelMenuId.value = null;
  openEditModel(model);
}

async function loadEmailChannel() {
  emailLoading.value = true;
  try {
    const channel = await adminApi.notificationChannels.detail('EMAIL_DEFAULT');
    emailChannel.value = channel;
    emailForm.enabled = channel.enabled;
    emailForm.host = channel.host || '';
    emailForm.port = channel.port || 465;
    emailForm.username = channel.username || '';
    emailForm.secretValue = '';
    emailForm.fromAddress = channel.fromAddress || '';
    emailForm.fromName = channel.fromName || '';
    emailForm.sslEnabled = channel.sslEnabled;
    emailForm.starttlsEnabled = channel.starttlsEnabled;
    emailForm.timeoutMillis = channel.timeoutMillis || 5000;
    emailStatus.value = resolveEmailStatus(channel);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    emailLoading.value = false;
  }
}

function buildEmailRequest(): NotificationChannelSaveRequest {
  return {
    enabled: emailForm.enabled,
    host: emailForm.host.trim(),
    port: Number(emailForm.port),
    username: emailForm.username.trim() || null,
    secretValue: emailForm.secretValue.trim() || null,
    fromAddress: emailForm.fromAddress.trim(),
    fromName: emailForm.fromName.trim() || null,
    sslEnabled: emailForm.sslEnabled,
    starttlsEnabled: emailForm.starttlsEnabled,
    timeoutMillis: Number(emailForm.timeoutMillis),
  };
}

async function saveEmailChannel() {
  if (!canSubmitEmail() || emailSaving.value) return;
  emailSaving.value = true;
  emailStatus.value = '保存中';
  try {
    const channel = await adminApi.notificationChannels.update('EMAIL_DEFAULT', buildEmailRequest());
    emailChannel.value = channel;
    emailForm.secretValue = '';
    emailStatus.value = resolveEmailStatus(channel);
    toast.success('已保存');
  } catch (e) {
    emailStatus.value = '保存失败';
    toast.error(errorMessage(e));
  } finally {
    emailSaving.value = false;
  }
}

async function testEmailChannel() {
  if (!canSubmitEmail() || !emailForm.recipient.trim() || emailTesting.value) return;
  emailTesting.value = true;
  emailStatus.value = '测试中';
  try {
    const result = await adminApi.notificationChannels.test('EMAIL_DEFAULT', {
      ...buildEmailRequest(),
      recipient: emailForm.recipient.trim(),
    });
    emailStatus.value = result.success ? '测试成功' : '测试失败';
    await loadEmailChannel();
  } catch (e) {
    emailStatus.value = '测试失败';
    toast.error(errorMessage(e));
  } finally {
    emailTesting.value = false;
  }
}

function canSubmitEmail() {
  return Boolean(emailForm.host.trim() && emailForm.port && emailForm.fromAddress.trim() && emailForm.timeoutMillis);
}

function resolveEmailStatus(channel: NotificationChannelResponse) {
  if (!channel.host || !channel.port || !channel.fromAddress) return '未配置';
  if (!channel.enabled) return '未启用';
  if (channel.lastTestSuccess === true) return '测试成功';
  if (channel.lastTestSuccess === false) return '测试失败';
  return '未测试';
}

onMounted(async () => {
  if (activeTab.value === 'models') {
    await loadAiBase();
  } else {
    await loadEmailChannel();
  }
});
</script>

<template>
  <main v-if="activeTab === 'models'" class="ai-base-page">
    <div class="ai-page-tabs">
      <button
        v-for="tab in aiTabs"
        :key="tab.value"
        :class="{ active: aiTab === tab.value }"
        type="button"
        @click="aiTab = tab.value"
      >
        {{ tab.label }}
      </button>
    </div>

    <section v-if="aiTab === 'config'" class="ai-base-layout">
      <aside class="provider-rail">
        <div class="rail-head">
          <div>
            <strong>供应商</strong>
            <span>已接入 {{ providers.length }} 个</span>
          </div>
          <button class="icon-button" title="添加供应商" @click="openProviderCreate"><Plus :size="15" /></button>
        </div>
        <button
          v-for="provider in providers"
          :key="provider.providerId"
          :class="['provider-item', { active: String(selectedProvider?.providerId) === String(provider.providerId) }]"
          type="button"
          @click="selectedProviderChanged(provider.providerId)"
        >
          <div>
            <strong>{{ provider.displayName }}</strong>
            <span>{{ providerTypeLabel(provider.providerType) }}</span>
          </div>
          <i :class="providerDotClass(provider)"></i>
        </button>
      </aside>

      <section v-if="selectedProvider" class="model-workspace">
        <AdminPanel title="供应商状态" :subtitle="`${selectedProvider.displayName} / ${providerTypeLabel(selectedProvider.providerType)}`">
          <div class="provider-state compact">
            <div class="state-line state-summary">
              <span>状态</span>
              <div class="state-main">
                <div class="state-pair">
                  <strong><i :class="providerDotClass(selectedProvider)"></i>{{ providerStatus }}</strong>
                  <span class="state-meta">上次 {{ formatDateTime(selectedProvider.lastTestedAt) || '未测试' }}</span>
                  <span class="state-meta">凭据 {{ selectedProvider.apiKeyConfigured ? '已配置' : '未配置' }}</span>
                </div>
                <div class="provider-actions">
                  <label class="inline-switch">
                    <input :checked="selectedProvider.enabled !== false" type="checkbox" disabled />
                    <span></span>
                  </label>
                  <button class="secondary-button" :disabled="providerTesting" @click="testProvider(selectedProvider)">
                    <FlaskConical :size="14" />检查
                  </button>
                  <button class="secondary-button" @click="openProviderEdit(selectedProvider)">
                    <Pencil :size="14" />编辑
                  </button>
                  <button class="danger-button" @click="askProviderDelete(selectedProvider)">
                    <Trash2 :size="14" />删除
                  </button>
                </div>
              </div>
            </div>
            <div class="state-line">
              <span>Base URL</span>
              <strong>{{ providerBaseUrl || '未配置' }}</strong>
            </div>
            <div class="state-line">
              <span>自检</span>
              <strong>每 30 分钟检查供应商端点，{{ safeBusinessMessage(selectedProvider.lastTestMessage, providerStatus) }}</strong>
            </div>
          </div>
        </AdminPanel>

        <AdminPanel title="模型列表" :subtitle="`当前供应商下共 ${providerModels.length} 个模型`">
          <template #actions>
            <button class="primary-button" :disabled="!selectedProvider" @click="openCreateModel">
              <Plus :size="16" />添加模型
            </button>
          </template>
          <div class="model-list">
            <div
              v-for="model in providerModels"
              :key="model.modelId"
              :class="['model-row', { active: String(selectedModel?.modelId) === String(model.modelId) }]"
              role="button"
              tabindex="0"
              @click="selectModel(model)"
              @keydown.enter.prevent="selectModel(model)"
              @keydown.space.prevent="selectModel(model)"
            >
              <div>
                <strong>{{ model.displayName }}</strong>
                <span>供应商模型 ID: {{ model.modelName }}</span>
              </div>
              <div class="model-tags">
                <span :class="['status-pill', testStateClass(model)]">{{ testState(model) }}</span>
                <span class="model-test-time">{{ testStateDetail(model) }}</span>
                <span v-for="feature in modelFeatures(model)" :key="feature" class="status-pill">{{ feature }}</span>
                <label class="inline-switch small">
                  <input :checked="model.status === 'ACTIVE'" type="checkbox" disabled />
                  <span></span>
                </label>
                <div class="model-action-menu">
                  <button class="icon-button model-action-trigger" title="更多操作" @click.stop="toggleModelMenu(model)">
                    <MoreHorizontal :size="18" />
                  </button>
                  <div v-if="isModelMenuOpen(model)" class="model-action-popover" @click.stop>
                    <button type="button" :disabled="modelTesting" @click="testSavedModel(model)"><FlaskConical :size="14" />测试连通性</button>
                    <button type="button" @click="openModelEditFromMenu(model)"><Pencil :size="14" />编辑模型</button>
                    <button type="button" @click="setDefaultModel(model)"><CheckCircle2 :size="14" />设为默认</button>
                    <button type="button" class="danger-menu-item" @click="askDelete(model)"><Trash2 :size="14" />删除模型</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </AdminPanel>
      </section>
      <section v-else></section>
    </section>

    <section v-else-if="aiTab === 'models'" class="model-workspace">
      <AdminPanel title="模型列表">
        <template #actions>
          <button class="secondary-button" :disabled="loading" @click="loadAiBase"><RefreshCcw :size="16" />刷新</button>
          <button class="primary-button" :disabled="!selectedProvider" @click="openCreateModel"><Plus :size="16" />添加模型</button>
        </template>
        <AdminTable :columns="modelColumns" :rows="models" :loading="loading" :selected-id="selectedModel?.modelId" row-key="modelId" @row-click="selectedModel = $event">
          <template #cell-displayName="{ row }">
            <strong>{{ row.displayName }}</strong>
            <span>{{ providerTypeLabel(row.provider) }} / {{ row.modelName }}</span>
          </template>
          <template #cell-modelType="{ row }"><span class="status-pill">{{ row.modelTypeName || modelTypeLabel(row.modelType) }}</span></template>
          <template #cell-status="{ row }"><span class="status-pill">{{ modelEnabledLabel(row) }}</span></template>
          <template #cell-lastTestedAt="{ row }">
            <strong :class="['status-pill', testStateClass(row)]">{{ testState(row) }}</strong>
            <span>{{ testStateDetail(row) }}</span>
          </template>
          <template #cell-_actions="{ row }">
            <div class="row-actions">
              <button class="secondary-button action-btn" :disabled="modelTesting" @click.stop="testSavedModel(row)"><FlaskConical :size="14" />测试</button>
              <button class="secondary-button action-btn" @click.stop="openEditModel(row)"><Pencil :size="14" />编辑</button>
              <button class="danger-button action-btn" @click.stop="askDelete(row)"><Trash2 :size="14" />删除</button>
            </div>
          </template>
        </AdminTable>
      </AdminPanel>
    </section>

    <section v-else-if="aiTab === 'defaults'" class="model-workspace">
      <AdminPanel title="默认模型">
        <div class="default-grid">
          <div v-for="type in modelTypeOptions" :key="type.value" class="default-item">
            <span>{{ type.label }}</span>
            <strong>{{ defaultModels.find((item) => item.modelType === type.value)?.displayName || '未设置' }}</strong>
            <select :value="defaultModels.find((item) => item.modelType === type.value)?.modelId || ''" @change="handleDefaultChange(($event.target as HTMLSelectElement).value)">
              <option value="">选择模型</option>
              <option v-for="model in models.filter((item) => item.modelType === type.value)" :key="model.modelId" :value="model.modelId">{{ model.displayName }}</option>
            </select>
          </div>
        </div>
      </AdminPanel>
    </section>

    <section v-else class="model-workspace">
      <AdminPanel title="测试">
        <div class="test-grid">
          <label>
            <span>供应商</span>
            <select :value="selectedProviderId || ''" @change="selectedProviderChanged(($event.target as HTMLSelectElement).value)">
              <option v-for="provider in providers" :key="provider.providerId" :value="provider.providerId">{{ provider.displayName }}</option>
            </select>
          </label>
          <label>
            <span>模型</span>
            <select :value="selectedModel?.modelId || ''" @change="selectedModel = providerModels.find((model) => String(model.modelId) === String(($event.target as HTMLSelectElement).value)) || null">
              <option value="">选择模型</option>
              <option v-for="model in providerModels" :key="model.modelId" :value="model.modelId">{{ model.displayName }}</option>
            </select>
          </label>
          <button class="primary-button" :disabled="!selectedModel || modelTesting" @click="selectedModel && testSavedModel(selectedModel)">
            <FlaskConical :size="14" />{{ modelTesting ? '测试中' : '执行测试' }}
          </button>
        </div>
        <div v-if="testResult" :class="['test-result', testResult.success ? 'test-ok' : 'test-fail']">
          <strong>{{ testResult.success ? '测试成功' : '测试失败' }}</strong>
          <span>{{ testResult.message }}{{ testResult.durationMs ? ` · ${testResult.durationMs}ms` : '' }}</span>
        </div>
      </AdminPanel>
    </section>
  </main>

  <div v-else class="page-grid">
    <AdminPanel :span="12" title="通知渠道">
      <template #actions>
        <button class="secondary-button" title="刷新" :disabled="emailLoading" @click="loadEmailChannel"><RefreshCcw :size="16" />刷新</button>
      </template>
      <form class="mail-form" @submit.prevent="saveEmailChannel">
        <label class="check-row">
          <input v-model="emailForm.enabled" type="checkbox" />
          <span>启用</span>
        </label>
        <label><span>邮件服务器</span><input v-model="emailForm.host" autocomplete="off" /></label>
        <label><span>端口</span><input v-model.number="emailForm.port" type="number" min="1" max="65535" /></label>
        <label><span>用户名</span><input v-model="emailForm.username" autocomplete="username" /></label>
        <label><span>发件地址</span><input v-model="emailForm.fromAddress" type="email" autocomplete="email" /></label>
        <label><span>发件名称</span><input v-model="emailForm.fromName" autocomplete="off" /></label>
        <label><span>超时毫秒</span><input v-model.number="emailForm.timeoutMillis" type="number" min="100" max="60000" step="100" /></label>
        <label class="check-row">
          <input v-model="emailForm.sslEnabled" type="checkbox" />
          <span>启用安全加密（SSL）</span>
        </label>
        <label class="check-row">
          <input v-model="emailForm.starttlsEnabled" type="checkbox" />
          <span>启用传输加密（STARTTLS）</span>
        </label>
        <label class="wide">
          <span>密码</span>
          <input v-model="emailForm.secretValue" type="password" autocomplete="new-password" :placeholder="emailChannel?.hasSecret ? '已配置，留空保持不变' : '未配置'" />
        </label>
        <label class="wide"><span>测试收件人</span><input v-model="emailForm.recipient" type="email" autocomplete="email" /></label>
        <div class="mail-actions wide">
          <span class="status-pill">{{ emailStatus }}</span>
          <span v-if="emailChannel?.lastTestedAt" class="mail-time">{{ formatDateTime(emailChannel.lastTestedAt) }}</span>
          <button class="primary-button" type="submit" :disabled="emailSaving || !canSubmitEmail()"><Save :size="14" />{{ emailSaving ? '保存中...' : '保存' }}</button>
          <button class="secondary-button" type="button" :disabled="emailTesting || !canSubmitEmail() || !emailForm.recipient.trim()" @click="testEmailChannel"><Send :size="14" />{{ emailTesting ? '测试中...' : '发送测试' }}</button>
        </div>
        <div v-if="emailChannel?.lastTestMessage" class="test-result wide" :class="emailChannel.lastTestSuccess ? 'test-ok' : 'test-fail'">
          <strong>{{ emailChannel.lastTestSuccess ? '测试成功' : '测试失败' }}</strong>
          <span>{{ safeBusinessMessage(emailChannel.lastTestMessage, emailChannel.lastTestSuccess ? '测试成功' : '测试失败') }}</span>
        </div>
      </form>
    </AdminPanel>
  </div>

  <AdminConfirmDialog v-model:visible="providerDialogVisible" :title="providerEditId ? '编辑供应商' : '添加供应商'" message="" confirm-label="保存" @confirm="saveProvider">
    <div class="provider-dialog">
      <label>
        <span>供应商类型</span>
        <select v-model="providerForm.providerType" @change="providerTypeChanged">
          <option v-for="provider in providerOptions" :key="provider.value" :value="provider.value">{{ provider.label }}</option>
        </select>
      </label>
      <label><span>显示名称</span><input v-model="providerForm.displayName" /></label>
      <label><span>Base URL</span><input v-model="providerForm.baseUrl" :placeholder="providerOptions.find((item) => item.value === providerForm.providerType)?.endpoint || '自定义端点'" /></label>
      <label><span>API Key</span><input v-model="providerForm.apiKey" type="password" autocomplete="new-password" :placeholder="providerEditId ? '留空保持不变' : ''" /></label>
      <label class="check-row">
        <input v-model="providerForm.enabled" type="checkbox" />
        <span>启用</span>
      </label>
    </div>
  </AdminConfirmDialog>

  <AdminConfirmDialog v-model:visible="modelDialogVisible" :title="modelEditId ? '编辑模型' : '创建模型'" message="" confirm-label="保存" @confirm="saveModel">
    <div class="model-dialog">
      <div class="model-discovery wide">
        <button class="secondary-button" type="button" :disabled="providerModelLoading || !selectedProvider" @click="fetchProviderModelOptions">
          <RefreshCcw :size="14" />{{ providerModelLoading ? '获取中...' : '获取模型列表' }}
        </button>
        <select :value="modelForm.modelName" :disabled="providerModelLoading || providerModelOptions.length === 0" @change="applyProviderModelOption(($event.target as HTMLSelectElement).value)">
          <option value="">{{ providerModelOptions.length ? '选择供应商模型' : '尚未获取模型' }}</option>
          <option v-for="option in providerModelOptions" :key="option.modelName" :value="option.modelName">
            {{ option.displayName || option.modelName }}
          </option>
        </select>
        <span v-if="providerModelOptions.length" class="discovery-count">已获取 {{ providerModelOptions.length }} 个</span>
      </div>
      <label><span>供应商模型 ID *</span><input v-model="modelForm.modelName" placeholder="例如: gpt-4o" /></label>
      <label><span>显示名称 *</span><input v-model="modelForm.displayName" placeholder="例如: GPT-4o" /></label>
      <label>
        <span>模型类型 *</span>
        <select v-model="modelForm.modelType">
          <option v-for="type in modelTypeOptions" :key="type.value" :value="type.value">{{ type.label }}</option>
        </select>
      </label>
      <fieldset>
        <legend>高级特性</legend>
        <label class="check-row"><input v-model="modelForm.streamOutputFlag" type="checkbox" /><span>流式输出</span></label>
        <label class="check-row"><input v-model="modelForm.visionFlag" type="checkbox" /><span>视觉</span></label>
        <label class="check-row"><input v-model="modelForm.toolCallFlag" type="checkbox" /><span>工具调用</span></label>
        <label class="check-row"><input v-model="modelForm.structuredOutputFlag" type="checkbox" /><span>结构化输出</span></label>
        <label class="check-row"><input v-model="modelForm.reasoningFlag" type="checkbox" /><span>推理</span></label>
      </fieldset>
      <label class="check-row">
        <input v-model="modelForm.status" true-value="ACTIVE" false-value="INACTIVE" type="checkbox" />
        <span>启用</span>
      </label>
      <label class="check-row">
        <input v-model="modelForm.defaultFlag" type="checkbox" />
        <span>设为默认</span>
      </label>
      <div class="dialog-test">
        <button class="secondary-button" type="button" :disabled="modelTesting || !modelForm.modelName.trim()" @click="testDraftModel">
          <FlaskConical :size="14" />{{ modelTesting ? '测试中' : '新增前测试' }}
        </button>
        <span v-if="testResult" :class="testResult.success ? 'inline-ok' : 'inline-fail'">
          <CheckCircle2 v-if="testResult.success" :size="14" />{{ testResult.message }}
        </span>
      </div>
    </div>
  </AdminConfirmDialog>

  <AdminConfirmDialog v-model:visible="deleteVisible" title="删除模型" :message="`确定删除「${deleteTarget?.displayName || '模型'}」吗？`" confirm-label="删除" :danger="true" @confirm="doDelete" />
  <AdminConfirmDialog v-model:visible="providerDeleteVisible" title="删除供应商" :message="`确定删除「${providerDeleteTarget?.displayName || '供应商'}」吗？`" confirm-label="删除" :danger="true" @confirm="deleteProvider" />
</template>

<style scoped>
.ai-base-page,
.model-workspace {
  display: grid;
  align-content: start;
  gap: var(--space-base);
}

.ai-base-layout :deep(.model-workspace > .panel:first-child .panel-title) {
  align-items: flex-start;
}

.ai-base-layout :deep(.model-workspace > .panel:first-child .panel-title > div) {
  min-width: 0;
  flex: 1 1 auto;
}

.ai-page-tabs {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  border-bottom: 1px solid var(--line);
}

.ai-page-tabs button {
  min-height: 38px;
  border: 0;
  border-bottom: 2px solid transparent;
  padding: 0 var(--space-base);
  background: transparent;
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.ai-page-tabs button.active {
  border-color: var(--green);
  color: var(--ink);
}

.ai-base-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: var(--space-base);
}

.provider-rail {
  min-height: calc(100vh - 220px);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
}

.rail-head,
.provider-item,
.row-actions,
.mail-actions,
.dialog-test,
.inline-switch,
.provider-actions,
.model-row,
.model-tags {
  display: flex;
  align-items: center;
}

.rail-head {
  justify-content: space-between;
  min-height: 66px;
  padding: 0 var(--space-base);
  border-bottom: 1px solid var(--line);
}

.rail-head div,
.provider-item div {
  display: grid;
  gap: 3px;
}

.rail-head span,
.provider-item span {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.provider-item {
  width: 100%;
  justify-content: space-between;
  min-height: 58px;
  border: 0;
  border-bottom: 1px solid var(--line);
  padding: 0 var(--space-base);
  background: transparent;
  color: var(--ink);
  text-align: left;
}

.provider-item.active {
  background: var(--primary-soft);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
  display: inline-block;
  flex: 0 0 auto;
}

.dot.bad {
  background: var(--red);
}

.dot.idle {
  background: var(--muted);
}

.status-pill.health-ok {
  background: var(--success-bg);
  color: var(--success);
}

.status-pill.health-fail {
  background: var(--error-bg);
  color: var(--red-dark);
}

.status-pill.health-unknown {
  background: var(--pill-bg);
  color: var(--muted);
}

.provider-state {
  display: grid;
  gap: 0;
  border-top: 1px solid var(--line);
}

.state-line {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  column-gap: var(--space-sm);
  min-height: 34px;
  align-items: center;
  padding: 5px var(--space-base);
  border-bottom: 1px solid var(--line);
}

.state-summary {
  grid-template-columns: 64px minmax(0, 1fr);
}

.state-main {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
}

.state-pair {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 0;
  flex-wrap: nowrap;
}

.provider-state span,
.state-meta {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.provider-state strong {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  min-width: 0;
  color: var(--ink);
  font-size: var(--text-sm);
  overflow-wrap: anywhere;
}

.state-meta {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 600;
  white-space: nowrap;
}

.provider-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
  margin-left: auto;
  flex-wrap: nowrap;
  flex: 0 0 auto;
}

.provider-actions .secondary-button,
.provider-actions .danger-button {
  min-height: 26px;
  gap: var(--space-xs);
  padding: 0 10px;
  font-size: var(--text-xs);
}

.provider-actions .inline-switch {
  width: 40px;
  height: 22px;
}

.provider-actions .inline-switch span::after {
  width: 16px;
  height: 16px;
}

.provider-actions .inline-switch input:checked + span::after {
  transform: translateX(18px);
}

.model-list {
  display: grid;
  border-top: 1px solid var(--line);
}

.model-row {
  justify-content: space-between;
  gap: var(--space-base);
  min-height: 70px;
  border: 0;
  border-bottom: 1px solid var(--line);
  padding: 0 var(--space-base);
  background: transparent;
  color: var(--ink);
  cursor: pointer;
  text-align: left;
}

.model-row > div:first-child {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.model-row > div:first-child span,
.model-test-time {
  color: var(--muted);
  font-size: var(--text-xs);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-tags {
  gap: var(--space-xs);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.model-action-menu {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.model-action-trigger {
  color: var(--muted);
}

.model-action-popover {
  position: absolute;
  top: calc(100% + var(--space-xs));
  right: 0;
  z-index: 20;
  display: grid;
  min-width: 148px;
  padding: var(--space-xs);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  box-shadow: var(--shadow);
}

.model-action-popover button {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  min-height: 32px;
  border: 0;
  border-radius: var(--radius-sm);
  padding: 0 var(--space-sm);
  background: transparent;
  color: var(--ink);
  font-size: var(--text-sm);
  font-weight: 700;
  text-align: left;
}

.model-action-popover button:hover {
  background: var(--primary-soft);
}

.model-action-popover button:disabled {
  color: var(--muted-light);
  cursor: not-allowed;
}

.model-action-popover .danger-menu-item {
  color: var(--red);
}

.model-test-time {
  max-width: 140px;
  font-weight: 700;
}

.danger-icon {
  color: var(--red);
}

.inline-switch {
  width: 48px;
  height: 26px;
}

.inline-switch.small {
  width: 42px;
  height: 24px;
}

.inline-switch input {
  display: none;
}

.inline-switch span {
  position: relative;
  width: 100%;
  height: 100%;
  border-radius: 999px;
  background: var(--line);
}

.inline-switch span::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--panel-strong);
  transition: transform 160ms ease;
}

.inline-switch input:checked + span {
  background: var(--green);
}

.inline-switch input:checked + span::after {
  transform: translateX(22px);
}

.inline-switch.small span::after {
  width: 18px;
  height: 18px;
}

.inline-switch.small input:checked + span::after {
  transform: translateX(18px);
}

.row-actions {
  gap: var(--space-xs);
  flex-wrap: nowrap;
}

.action-btn {
  min-height: 30px;
  padding: 0 var(--space-sm);
  font-size: 12px;
}

.provider-dialog,
.model-dialog,
.mail-form,
.test-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-lg);
}

.provider-dialog label,
.model-dialog label,
.mail-form label,
.test-grid label {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.model-dialog fieldset {
  grid-column: 1 / -1;
  display: grid;
  gap: var(--space-sm);
  margin: 0;
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}

.model-dialog legend {
  padding: 0 var(--space-xs);
  color: var(--ink);
  font-weight: 800;
}

.check-row {
  display: flex !important;
  align-items: center;
  gap: var(--space-sm);
  color: var(--ink) !important;
}

.check-row input {
  width: 18px;
  min-height: 18px;
}

.dialog-test,
.model-dialog .wide,
.mail-form .wide {
  grid-column: 1 / -1;
}

.dialog-test,
.model-discovery,
.mail-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.model-discovery select {
  min-width: 260px;
  flex: 1 1 260px;
}

.discovery-count {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.default-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-base);
}

.default-item {
  display: grid;
  gap: var(--space-sm);
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
}

.default-item span {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.inline-ok {
  color: var(--success);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--text-sm);
  font-weight: 700;
}

.inline-fail {
  color: var(--red);
  font-size: var(--text-sm);
  font-weight: 700;
}

.mail-time {
  color: var(--muted);
  font-size: var(--text-sm);
  margin-right: auto;
}

.test-result {
  margin-top: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border-radius: var(--radius-sm);
  font-size: var(--text-sm);
  display: grid;
  gap: 4px;
}

.test-ok {
  background: var(--success-bg);
  color: var(--success);
  border: 1px solid var(--success-border);
}

.test-fail {
  background: var(--error-bg);
  color: var(--red-dark);
  border: 1px solid var(--error-border);
}

@media (max-width: 900px) {
  .ai-base-layout,
  .provider-dialog,
  .model-dialog,
  .mail-form,
  .test-grid,
  .default-grid,
  .provider-state,
  .state-line {
    grid-template-columns: 1fr;
  }

  .state-summary {
    grid-template-columns: 1fr;
  }

  .state-main {
    align-items: flex-start;
    flex-direction: column;
  }

  .state-pair {
    gap: var(--space-sm);
    flex-wrap: wrap;
  }

  .provider-rail {
    min-height: auto;
  }

  .provider-actions {
    justify-content: flex-end;
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
