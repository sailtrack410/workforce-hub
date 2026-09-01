<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  AlertTriangle,
  Box,
  CircleStop,
  KeyRound,
  LoaderCircle,
  Play,
  Plus,
  RefreshCcw,
  Save,
  Settings2,
  ShieldCheck,
  Trash2,
  Upload,
} from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import { ApiClientError } from '@/api/http';
import type {
  AdminPluginDependencyResponse,
  AdminPluginResponse,
  PluginConfigResponse,
  PluginConfigSaveRequest,
} from '@/api/types';
import { useToast } from '@/composables/useToast';
import { errorMessage, formatDateTime, statusLabel } from '@/utils/format';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';
import AdminDialog from '@/components/AdminDialog.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminMetricStrip from '@/components/AdminMetricStrip.vue';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';

type DetailTab = 'overview' | 'config';
type LifecycleAction = 'enable' | 'disable' | 'uninstall';

const MAX_JAR_BYTES = 32 * 1024 * 1024;
const MAX_SIGNATURE_BYTES = 65_535;
const MAX_CONFIG_VALUE_BYTES = 16 * 1024;
const CONFIG_KEY_PATTERN = /^[a-z0-9][a-z0-9._-]{0,127}$/;

const toast = useToast();
const loading = ref(false);
const pluginLoadError = ref('');
const plugins = ref<AdminPluginResponse[]>([]);
const selectedPluginId = ref<string | null>(null);
const keyword = ref('');
const statusFilter = ref('');
const typeFilter = ref('');
const detailTab = ref<DetailTab>('overview');

const uploadVisible = ref(false);
const uploading = ref(false);
const uploadJar = ref<File | null>(null);
const uploadSignature = ref<File | null>(null);
const uploadInputKey = ref(0);
const uploadForm = reactive({ signerId: '', name: '', description: '' });

const lifecycleVisible = ref(false);
const lifecycleAction = ref<LifecycleAction>('enable');
const lifecycleTarget = ref<AdminPluginResponse | null>(null);
const lifecycleSubmitting = ref(false);

const configs = ref<PluginConfigResponse[]>([]);
const configTombstoneVersions = ref(new Map<string, string | number>());
const configNamespaceCount = ref(0);
const configLoading = ref(false);
const configLoadError = ref('');
const configLoadedPluginId = ref<string | null>(null);
const configDialogVisible = ref(false);
const configSaving = ref(false);
const configEditing = ref<PluginConfigResponse | null>(null);
const configForm = reactive({
  key: '',
  valueType: 'STRING',
  value: '',
  useValue: true,
  defaultValue: '',
  useDefault: false,
  description: '',
  requiresRestart: false,
});
const configDeleteVisible = ref(false);
const configDeleteTarget = ref<PluginConfigResponse | null>(null);
const configDeleting = ref(false);
let pluginLoadSequence = 0;
let configLoadSequence = 0;

const columns: TableColumn[] = [
  { key: 'name', label: '插件', sortable: true },
  { key: 'version', label: '版本', width: '110px', sortable: true },
  { key: 'type', label: '类型', width: '110px' },
  { key: 'status', label: '状态', width: '112px', sortable: true },
  { key: 'dependencies', label: '依赖' },
  { key: 'updatedAt', label: '最近更新', width: '150px', sortable: true },
  { key: '_actions', label: '操作', width: '240px' },
];

const configColumns: TableColumn[] = [
  { key: 'key', label: '配置键', sortable: true },
  { key: 'valueType', label: '类型', width: '100px' },
  { key: 'value', label: '当前值' },
  { key: 'requiresRestart', label: '重启', width: '88px' },
  { key: 'version', label: '版本', width: '76px' },
  { key: '_actions', label: '操作', width: '150px' },
];

const selectedPlugin = computed(() => (
  selectedPluginId.value
    ? plugins.value.find((plugin) => plugin.id === selectedPluginId.value) ?? null
    : null
));

const typeOptions = computed(() => [...new Set(plugins.value.map((plugin) => plugin.type))].sort());
const filteredPlugins = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return plugins.value.filter((plugin) => {
    if (statusFilter.value && plugin.status !== statusFilter.value) return false;
    if (typeFilter.value && plugin.type !== typeFilter.value) return false;
    if (!query) return true;
    return [
      plugin.id,
      plugin.name,
      plugin.version,
      plugin.provider ?? '',
      ...plugin.dependencies.map((dependency) => dependency.pluginId),
    ].some((value) => value.toLowerCase().includes(query));
  });
});

const metricCards = computed(() => [
  { label: '已登记', value: plugins.value.length },
  { label: '运行中', value: plugins.value.filter((plugin) => plugin.status === 'ACTIVE').length },
  { label: '待处理', value: plugins.value.filter((plugin) => ['ERROR', 'DISABLING'].includes(plugin.status)).length },
  { label: '依赖关系', value: plugins.value.reduce((sum, plugin) => sum + plugin.dependencies.length, 0) },
]);

const uploadError = computed(() => {
  if (!uploadJar.value) return '请选择插件 JAR';
  if (!uploadJar.value.name.toLowerCase().endsWith('.jar')) return '插件包必须是 .jar 文件';
  if (uploadJar.value.size === 0) return '插件 JAR 不能为空';
  if (uploadJar.value.size > MAX_JAR_BYTES) return '插件 JAR 不能超过 32 MiB';
  if (!uploadSignature.value) return '请选择签名文件';
  if (uploadSignature.value.size === 0) return '签名文件不能为空';
  if (uploadSignature.value.size > MAX_SIGNATURE_BYTES) return '签名文件格式不正确';
  if (!uploadForm.signerId.trim()) return '请输入签名者 ID';
  if (uploadForm.signerId.trim().length > 128) return '签名者 ID 不能超过 128 个字符';
  if (uploadForm.name.trim().length > 128) return '插件名称不能超过 128 个字符';
  if (uploadForm.description.trim().length > 512) return '插件描述不能超过 512 个字符';
  return '';
});

const canSaveSelectedConfig = computed(() => (
  selectedPlugin.value != null
  && !['DISABLING', 'UNINSTALLED'].includes(selectedPlugin.value.status)
));

const canDeleteSelectedConfig = computed(() => (
  selectedPlugin.value != null && selectedPlugin.value.status !== 'DISABLING'
));

const configError = computed(() => {
  const key = configForm.key.trim();
  if (!CONFIG_KEY_PATTERN.test(key)) return '配置键仅支持小写字母、数字、点、下划线和短横线';
  if (configForm.description.trim().length > 256) return '配置说明不能超过 256 个字符';
  if (configForm.valueType === 'SECRET') {
    if (configForm.value && !configForm.value.trim()) return '敏感配置值不能全为空白';
    if (!configEditing.value && !configForm.value) return '新建敏感配置必须提供值';
    if (configEditing.value && !configEditing.value.hasValue && !configForm.value) return '敏感配置需要重新设置';
    if (byteLength(configForm.value) > MAX_CONFIG_VALUE_BYTES) return '敏感配置值不能超过 16 KiB';
    return '';
  }
  if (!configForm.useValue && !configForm.useDefault) return '配置值和默认值不能同时为空';
  if (configForm.useValue) {
    const valueError = validateTypedValue(configForm.valueType, configForm.value, '配置值');
    if (valueError) return valueError;
  }
  if (configForm.useDefault) {
    const defaultError = validateTypedValue(configForm.valueType, configForm.defaultValue, '默认值');
    if (defaultError) return defaultError;
  }
  return '';
});

const lifecycleTitle = computed(() => ({
  enable: '启用插件',
  disable: '停用插件',
  uninstall: '卸载插件',
})[lifecycleAction.value]);

const lifecycleConfirmLabel = computed(() => ({
  enable: '启用',
  disable: '停用',
  uninstall: '卸载',
})[lifecycleAction.value]);

const lifecycleMessage = computed(() => {
  const name = lifecycleTarget.value?.name || lifecycleTarget.value?.id || '';
  if (lifecycleAction.value === 'enable') return `启用 ${name} 后，插件将开始接收请求。`;
  if (lifecycleAction.value === 'disable') return `停用 ${name} 前会等待在途请求排空。`;
  return `卸载 ${name} 后将保留注册记录、配置和业务数据。`;
});

async function loadPlugins(preferredPluginId?: string) {
  const sequence = ++pluginLoadSequence;
  loading.value = true;
  pluginLoadError.value = '';
  try {
    const rows = await adminApi.plugins.list();
    if (sequence !== pluginLoadSequence) return;
    plugins.value = rows;
    const preferred = preferredPluginId ?? selectedPluginId.value;
    selectedPluginId.value = preferred && plugins.value.some((plugin) => plugin.id === preferred)
      ? preferred
      : plugins.value[0]?.id ?? null;
  } catch (error) {
    if (sequence !== pluginLoadSequence) return;
    pluginLoadError.value = errorMessage(error, '插件列表加载失败');
    toast.error(pluginLoadError.value);
  } finally {
    if (sequence === pluginLoadSequence) loading.value = false;
  }
}

function selectPlugin(plugin: AdminPluginResponse) {
  selectedPluginId.value = plugin.id;
}

function resetUploadForm() {
  uploadJar.value = null;
  uploadSignature.value = null;
  uploadForm.signerId = '';
  uploadForm.name = '';
  uploadForm.description = '';
  uploadInputKey.value += 1;
}

function openUpload() {
  resetUploadForm();
  uploadVisible.value = true;
}

function readUploadFile(event: Event, target: 'jar' | 'signature') {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null;
  if (target === 'jar') uploadJar.value = file;
  else uploadSignature.value = file;
}

async function submitUpload() {
  if (uploadError.value || !uploadJar.value || !uploadSignature.value || uploading.value) return;
  const request = new FormData();
  request.append('jar', uploadJar.value, uploadJar.value.name);
  request.append('signature', uploadSignature.value, uploadSignature.value.name);
  request.append('signerId', uploadForm.signerId.trim());
  if (uploadForm.name.trim()) request.append('name', uploadForm.name.trim());
  if (uploadForm.description.trim()) request.append('description', uploadForm.description.trim());

  uploading.value = true;
  try {
    const installed = await adminApi.plugins.upload(request);
    uploadVisible.value = false;
    toast.success('插件已安装');
    await loadPlugins(installed.id);
  } catch (error) {
    toast.error(errorMessage(error));
  } finally {
    uploading.value = false;
  }
}

function requestLifecycle(action: LifecycleAction, plugin: AdminPluginResponse) {
  if (loading.value || lifecycleSubmitting.value) return;
  lifecycleAction.value = action;
  lifecycleTarget.value = plugin;
  lifecycleVisible.value = true;
}

async function confirmLifecycle() {
  const plugin = lifecycleTarget.value;
  if (!plugin || lifecycleSubmitting.value) return;
  lifecycleSubmitting.value = true;
  try {
    if (lifecycleAction.value === 'enable') await adminApi.plugins.enable(plugin.id);
    else if (lifecycleAction.value === 'disable') await adminApi.plugins.disable(plugin.id);
    else await adminApi.plugins.uninstall(plugin.id);
    lifecycleVisible.value = false;
    toast.success(`${lifecycleConfirmLabel.value}完成`);
    await loadPlugins(plugin.id);
  } catch (error) {
    toast.error(errorMessage(error));
    await loadPlugins(plugin.id);
  } finally {
    lifecycleSubmitting.value = false;
  }
}

async function loadConfigs(force = false) {
  const plugin = selectedPlugin.value;
  if (!plugin || (!force && configLoadedPluginId.value === plugin.id)) return;
  const sequence = ++configLoadSequence;
  configLoading.value = true;
  configLoadError.value = '';
  try {
    const rows = await adminApi.plugins.configs(plugin.id, true);
    if (sequence !== configLoadSequence || selectedPluginId.value !== plugin.id) return;
    configs.value = rows.filter((row) => !row.deleted);
    configTombstoneVersions.value = new Map(
      rows.filter((row) => row.deleted).map((row) => [row.key, row.version]),
    );
    configNamespaceCount.value = rows.length;
    configLoadedPluginId.value = plugin.id;
  } catch (error) {
    if (sequence !== configLoadSequence || selectedPluginId.value !== plugin.id) return;
    configLoadError.value = errorMessage(error, '插件配置加载失败');
    toast.error(configLoadError.value);
  } finally {
    if (sequence === configLoadSequence) configLoading.value = false;
  }
}

function resetConfigForm() {
  configEditing.value = null;
  configForm.key = '';
  configForm.valueType = 'STRING';
  configForm.value = '';
  configForm.useValue = true;
  configForm.defaultValue = '';
  configForm.useDefault = false;
  configForm.description = '';
  configForm.requiresRestart = false;
}

function openConfigCreate() {
  if (!canSaveSelectedConfig.value || configLoading.value) return;
  resetConfigForm();
  configDialogVisible.value = true;
}

function openConfigEdit(row: PluginConfigResponse) {
  if (!canSaveSelectedConfig.value || configLoading.value) return;
  configEditing.value = row;
  configForm.key = row.key;
  configForm.valueType = row.valueType;
  configForm.value = row.secret ? '' : row.value ?? '';
  configForm.useValue = row.secret ? false : row.value != null;
  configForm.defaultValue = row.secret ? '' : row.defaultValue ?? '';
  configForm.useDefault = row.secret ? false : row.defaultValue != null;
  configForm.description = row.description ?? '';
  configForm.requiresRestart = row.requiresRestart;
  configDialogVisible.value = true;
}

function handleConfigTypeChange() {
  if (configEditing.value) return;
  configForm.value = configForm.valueType === 'BOOL' ? 'true' : '';
  configForm.useValue = true;
  configForm.defaultValue = '';
  configForm.useDefault = false;
}

async function saveConfig() {
  const plugin = selectedPlugin.value;
  if (!plugin || configError.value || configSaving.value || !canSaveSelectedConfig.value) return;
  const editing = configEditing.value;
  const key = configForm.key.trim();
  const request: PluginConfigSaveRequest = {
    expectedVersion: editing?.version ?? configTombstoneVersions.value.get(key) ?? 0,
    valueType: configForm.valueType,
    value: configForm.valueType === 'SECRET'
      ? (configForm.value || null)
      : (configForm.useValue ? configForm.value : null),
    defaultValue: configForm.valueType === 'SECRET'
      ? null
      : (configForm.useDefault ? configForm.defaultValue : null),
    description: configForm.description.trim() || null,
    requiresRestart: configForm.requiresRestart,
  };
  configSaving.value = true;
  try {
    await adminApi.plugins.saveConfig(plugin.id, key, request);
    if (!editing) configTombstoneVersions.value.delete(key);
    configDialogVisible.value = false;
    toast.success('配置已保存');
    await loadConfigs(true);
  } catch (error) {
    const conflict = error instanceof ApiClientError && error.status === 409;
    const message = errorMessage(error);
    const staleWrite = conflict && (message.includes('版本冲突') || message.includes('其他请求修改'));
    toast.error(staleWrite ? '配置已被其他管理员修改，请从最新列表重新打开' : message);
    if (conflict) await loadPlugins(plugin.id);
    await loadConfigs(true);
    if (conflict) {
      configDialogVisible.value = false;
      configEditing.value = null;
    }
  } finally {
    configSaving.value = false;
  }
}

function requestConfigDelete(row: PluginConfigResponse) {
  if (!canDeleteSelectedConfig.value || configLoading.value) return;
  configDeleteTarget.value = row;
  configDeleteVisible.value = true;
}

async function confirmConfigDelete() {
  const plugin = selectedPlugin.value;
  const row = configDeleteTarget.value;
  if (!plugin || !row || configDeleting.value || !canDeleteSelectedConfig.value) return;
  configDeleting.value = true;
  try {
    const deletedVersion = await adminApi.plugins.deleteConfig(plugin.id, row.key, row.version);
    configTombstoneVersions.value.set(row.key, deletedVersion);
    configs.value = configs.value.filter((config) => config.key !== row.key);
    configDeleteVisible.value = false;
    toast.success('配置已删除');
    await loadConfigs(true);
  } catch (error) {
    const conflict = error instanceof ApiClientError && error.status === 409;
    const message = errorMessage(error);
    const staleWrite = conflict && (message.includes('版本冲突') || message.includes('其他请求修改'));
    toast.error(staleWrite ? '配置已被其他管理员修改，请从最新列表重新操作' : message);
    if (conflict) await loadPlugins(plugin.id);
    await loadConfigs(true);
    if (conflict) {
      configDeleteVisible.value = false;
      configDeleteTarget.value = null;
    }
  } finally {
    configDeleting.value = false;
  }
}

function setDetailTab(tab: DetailTab) {
  detailTab.value = tab;
}

function handleTabKeydown(event: KeyboardEvent) {
  const tabs: DetailTab[] = ['overview', 'config'];
  const current = tabs.indexOf(detailTab.value);
  let next = current;
  if (event.key === 'ArrowLeft') next = (current - 1 + tabs.length) % tabs.length;
  else if (event.key === 'ArrowRight') next = (current + 1) % tabs.length;
  else if (event.key === 'Home') next = 0;
  else if (event.key === 'End') next = tabs.length - 1;
  else return;
  event.preventDefault();
  setDetailTab(tabs[next]);
  document.querySelector<HTMLElement>(`[data-plugin-tab="${tabs[next]}"]`)?.focus();
}

function pluginStatusLabel(status: string) {
  return ({
    ACTIVE: '运行中',
    INSTALLED: '已安装',
    STOPPED: '已停用',
    DISABLING: '停用中',
    ERROR: '异常',
    UNINSTALLED: '已卸载',
  } as Record<string, string>)[status] ?? statusLabel(status);
}

function pluginTypeLabel(type: string) {
  return ({ UI: '界面', FUNCTIONAL: '功能' } as Record<string, string>)[type] ?? type;
}

function statusClass(status: string) {
  if (status === 'ACTIVE') return 'status-active';
  if (status === 'ERROR') return 'status-error';
  if (['INSTALLED', 'DISABLING'].includes(status)) return 'status-warning';
  return 'status-neutral';
}

function dependencyRange(dependency: AdminPluginDependencyResponse) {
  if (dependency.minVersion && dependency.maxVersion) return `${dependency.minVersion} - ${dependency.maxVersion}`;
  if (dependency.minVersion) return `>= ${dependency.minVersion}`;
  if (dependency.maxVersion) return `<= ${dependency.maxVersion}`;
  return '任意版本';
}

function canEnable(plugin: AdminPluginResponse) {
  return ['INSTALLED', 'STOPPED'].includes(plugin.status);
}

function canDisable(plugin: AdminPluginResponse) {
  return plugin.status === 'ACTIVE';
}

function canUninstall(plugin: AdminPluginResponse) {
  return plugin.status !== 'UNINSTALLED';
}

function safeDiagnostic(plugin: AdminPluginResponse) {
  if (plugin.status === 'ERROR') return '插件运行异常，内部异常明细已隐藏。请结合最近更新时间处理。';
  if (plugin.status === 'DISABLING') return '插件正在关闭入口并等待在途请求排空。';
  if (plugin.status === 'INSTALLED') return '插件已完成安装，尚未开始接收请求。';
  if (plugin.status === 'STOPPED') return '插件入口已关闭，配置和业务数据仍保留。';
  if (plugin.status === 'UNINSTALLED') return '插件已卸载，注册记录、配置和业务数据仍保留。';
  return '插件运行正常。';
}

function configDisplayValue(row: PluginConfigResponse) {
  if (row.secret) return row.hasValue ? '已设置' : '未设置';
  if (row.value != null) return row.value || '空字符串';
  if (row.defaultValue != null) return `默认：${row.defaultValue || '空字符串'}`;
  return '-';
}

function byteLength(value: string) {
  return new TextEncoder().encode(value).length;
}

function validateTypedValue(valueType: string, value: string, label: string) {
  if (byteLength(value) > MAX_CONFIG_VALUE_BYTES) return `${label}不能超过 16 KiB`;
  if (valueType === 'INT') {
    if (!/^-?\d+$/.test(value)) return `${label}必须是 64 位整数`;
    try {
      const parsed = BigInt(value);
      if (parsed < -(2n ** 63n) || parsed > (2n ** 63n) - 1n) return `${label}必须是 64 位整数`;
    } catch {
      return `${label}必须是 64 位整数`;
    }
  }
  if (valueType === 'BOOL' && !['true', 'false'].includes(value)) return `${label}必须是 true 或 false`;
  return '';
}

watch(selectedPluginId, () => {
  configLoadSequence += 1;
  detailTab.value = 'overview';
  configLoadedPluginId.value = null;
  configLoading.value = false;
  configLoadError.value = '';
  configs.value = [];
  configTombstoneVersions.value = new Map();
  configNamespaceCount.value = 0;
  configDialogVisible.value = false;
  configDeleteVisible.value = false;
});

watch(uploadVisible, (visible) => {
  if (!visible) resetUploadForm();
});

watch(configDialogVisible, (visible) => {
  if (!visible) resetConfigForm();
});

watch(configDeleteVisible, (visible) => {
  if (!visible) configDeleteTarget.value = null;
});

watch(detailTab, (tab) => {
  if (tab === 'config') void loadConfigs();
});

onMounted(() => loadPlugins());
</script>

<template>
  <div class="page-grid plugin-page">
    <AdminMetricStrip class="span-12" :cards="metricCards" />

    <AdminPanel :span="12" title="插件清单" subtitle="状态、兼容性与可信依赖">
      <template #actions>
        <div class="panel-actions">
          <button class="secondary-button" type="button" :disabled="loading" @click="loadPlugins()">
            <RefreshCcw :size="16" />刷新
          </button>
          <button class="primary-button" type="button" @click="openUpload">
            <Upload :size="16" />安装插件
          </button>
        </div>
      </template>

      <AdminFilterBar :show-search="false">
        <input v-model="keyword" type="search" placeholder="名称、ID、发布方或依赖" aria-label="搜索插件" />
        <select v-model="statusFilter" aria-label="按状态筛选">
          <option value="">全部状态</option>
          <option value="ACTIVE">运行中</option>
          <option value="INSTALLED">已安装</option>
          <option value="STOPPED">已停用</option>
          <option value="DISABLING">停用中</option>
          <option value="ERROR">异常</option>
          <option value="UNINSTALLED">已卸载</option>
        </select>
        <select v-model="typeFilter" aria-label="按类型筛选">
          <option value="">全部类型</option>
          <option v-for="type in typeOptions" :key="type" :value="type">{{ pluginTypeLabel(type) }}</option>
        </select>
      </AdminFilterBar>

      <div v-if="pluginLoadError" class="inline-load-error" role="alert">
        <span>{{ pluginLoadError }}</span>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadPlugins()"><RefreshCcw :size="15" />重试</button>
      </div>

      <AdminTable
        v-if="plugins.length || !pluginLoadError"
        interactive
        :columns="columns"
        :rows="filteredPlugins"
        :loading="loading"
        :selected-id="selectedPluginId"
        row-key="id"
        @row-click="selectPlugin"
      >
        <template #cell-name="{ row }">
          <div class="plugin-name-cell">
            <Box :size="18" />
            <div><strong>{{ row.name }}</strong><span>{{ row.id }}</span></div>
          </div>
        </template>
        <template #cell-version="{ row }"><div class="version-cell"><strong>{{ row.version }}</strong><span>SDK {{ row.sdkVersion }}</span></div></template>
        <template #cell-type="{ row }"><span class="status-pill">{{ pluginTypeLabel(row.type) }}</span></template>
        <template #cell-status="{ row }"><span :class="['status-pill', statusClass(row.status)]">{{ pluginStatusLabel(row.status) }}</span></template>
        <template #cell-dependencies="{ row }">
          <div v-if="row.dependencies.length" class="dependency-list compact">
            <span v-for="dependency in row.dependencies" :key="dependency.pluginId" class="dependency-chip">{{ dependency.pluginId }}</span>
          </div>
          <span v-else>-</span>
        </template>
        <template #cell-updatedAt="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        <template #cell-_actions="{ row }">
          <div class="row-actions" @click.stop>
            <button v-if="canEnable(row)" class="ghost-button" type="button" title="启用插件" :disabled="loading || lifecycleSubmitting" @click="requestLifecycle('enable', row)"><Play :size="15" />启用</button>
            <button v-if="canDisable(row)" class="ghost-button" type="button" title="停用插件" :disabled="loading || lifecycleSubmitting" @click="requestLifecycle('disable', row)"><CircleStop :size="15" />停用</button>
            <button v-if="canUninstall(row)" class="ghost-button danger" type="button" title="卸载插件" :disabled="loading || lifecycleSubmitting" @click="requestLifecycle('uninstall', row)"><Trash2 :size="15" />卸载</button>
          </div>
        </template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel
      v-if="selectedPlugin"
      :span="12"
      :title="selectedPlugin.name"
      :subtitle="selectedPlugin.id"
    >
      <template #actions>
        <span :class="['status-pill', statusClass(selectedPlugin.status)]">{{ pluginStatusLabel(selectedPlugin.status) }}</span>
      </template>

      <nav class="detail-tabs" role="tablist" aria-label="插件详情" @keydown="handleTabKeydown">
        <button id="plugin-overview-tab" data-plugin-tab="overview" :class="{ active: detailTab === 'overview' }" type="button" role="tab" aria-controls="plugin-overview-panel" :aria-selected="detailTab === 'overview'" :tabindex="detailTab === 'overview' ? 0 : -1" @click="setDetailTab('overview')">概览</button>
        <button id="plugin-config-tab" data-plugin-tab="config" :class="{ active: detailTab === 'config' }" type="button" role="tab" aria-controls="plugin-config-panel" :aria-selected="detailTab === 'config'" :tabindex="detailTab === 'config' ? 0 : -1" @click="setDetailTab('config')">配置</button>
      </nav>

      <section v-if="detailTab === 'overview'" id="plugin-overview-panel" class="overview-grid" role="tabpanel" aria-labelledby="plugin-overview-tab">
        <div class="detail-block">
          <h3>运行信息</h3>
          <dl class="fact-grid">
            <div><dt>版本</dt><dd>{{ selectedPlugin.version }}</dd></div>
            <div><dt>SDK</dt><dd>{{ selectedPlugin.sdkVersion }}</dd></div>
            <div><dt>Java</dt><dd>{{ selectedPlugin.javaVersion }}</dd></div>
            <div><dt>类型</dt><dd>{{ pluginTypeLabel(selectedPlugin.type) }}</dd></div>
            <div><dt>发布方</dt><dd>{{ selectedPlugin.provider || '-' }}</dd></div>
            <div><dt>安装时间</dt><dd>{{ formatDateTime(selectedPlugin.installedAt) }}</dd></div>
            <div><dt>宿主范围</dt><dd>{{ selectedPlugin.minHostVersion }} - {{ selectedPlugin.maxHostVersion }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ formatDateTime(selectedPlugin.updatedAt) }}</dd></div>
          </dl>
          <p v-if="selectedPlugin.description" class="plugin-description">{{ selectedPlugin.description }}</p>
        </div>

        <div class="detail-block">
          <h3>可信依赖</h3>
          <div v-if="selectedPlugin.dependencies.length" class="dependency-list">
            <div v-for="dependency in selectedPlugin.dependencies" :key="dependency.pluginId" class="dependency-row">
              <strong>{{ dependency.pluginId }}</strong><span>{{ dependencyRange(dependency) }}</span>
            </div>
          </div>
          <div v-else class="empty-state compact-empty">无外部依赖</div>
        </div>

        <div :class="['safe-diagnostic', { danger: selectedPlugin.status === 'ERROR' }]">
          <AlertTriangle v-if="selectedPlugin.status === 'ERROR'" :size="19" />
          <ShieldCheck v-else :size="19" />
          <div><strong>安全诊断</strong><span>{{ safeDiagnostic(selectedPlugin) }}</span></div>
        </div>

        <div class="detail-actions">
          <button v-if="canEnable(selectedPlugin)" class="primary-button" type="button" :disabled="loading || lifecycleSubmitting" @click="requestLifecycle('enable', selectedPlugin)"><Play :size="16" />启用</button>
          <button v-if="canDisable(selectedPlugin)" class="secondary-button" type="button" :disabled="loading || lifecycleSubmitting" @click="requestLifecycle('disable', selectedPlugin)"><CircleStop :size="16" />停用</button>
          <button v-if="canUninstall(selectedPlugin)" class="ghost-button danger" type="button" :disabled="loading || lifecycleSubmitting" @click="requestLifecycle('uninstall', selectedPlugin)"><Trash2 :size="16" />卸载</button>
        </div>
      </section>

      <section v-else id="plugin-config-panel" class="config-section" role="tabpanel" aria-labelledby="plugin-config-tab">
        <div class="section-toolbar">
          <div><strong>配置项</strong><span>{{ configNamespaceCount }} / 128 个命名空间</span></div>
          <div class="panel-actions">
            <button class="secondary-button" type="button" :disabled="configLoading" @click="loadConfigs(true)"><RefreshCcw :size="15" />刷新</button>
            <button class="primary-button" type="button" :disabled="configLoading || !canSaveSelectedConfig" @click="openConfigCreate"><Plus :size="15" />新建配置</button>
          </div>
        </div>
        <div v-if="configLoadError" class="inline-load-error" role="alert">
          <span>{{ configLoadError }}</span>
          <button class="secondary-button" type="button" :disabled="configLoading" @click="loadConfigs(true)"><RefreshCcw :size="15" />重试</button>
        </div>
        <AdminTable v-if="configs.length || !configLoadError" :columns="configColumns" :rows="configs" :loading="configLoading" row-key="key">
          <template #cell-key="{ row }"><div class="config-key-cell"><strong>{{ row.key }}</strong><span>{{ row.description || '-' }}</span></div></template>
          <template #cell-valueType="{ row }"><span class="status-pill"><KeyRound v-if="row.secret" :size="12" />{{ row.valueType }}</span></template>
          <template #cell-value="{ row }">{{ configDisplayValue(row) }}</template>
          <template #cell-requiresRestart="{ row }">{{ row.requiresRestart ? '需要' : '无需' }}</template>
          <template #cell-version="{ row }">v{{ row.version }}</template>
          <template #cell-_actions="{ row }">
            <div class="row-actions">
              <button class="ghost-button" type="button" :disabled="configLoading || !canSaveSelectedConfig" @click="openConfigEdit(row)"><Settings2 :size="15" />编辑</button>
              <button class="ghost-button danger" type="button" :disabled="configLoading || !canDeleteSelectedConfig" @click="requestConfigDelete(row)"><Trash2 :size="15" />删除</button>
            </div>
          </template>
        </AdminTable>
      </section>
    </AdminPanel>

    <AdminDialog v-model:visible="uploadVisible" title="安装插件" width="min(640px, 94vw)" :close-disabled="uploading">
      <form class="stack-form" :aria-describedby="uploadError ? 'plugin-upload-error' : undefined" @submit.prevent="submitUpload">
        <div class="file-grid">
          <label class="file-field">
            <span>插件 JAR</span>
            <input :key="`jar-${uploadInputKey}`" type="file" accept=".jar,application/java-archive" autofocus @change="readUploadFile($event, 'jar')" />
            <small>{{ uploadJar ? `${uploadJar.name} · ${(uploadJar.size / 1024 / 1024).toFixed(2)} MiB` : '未选择文件' }}</small>
          </label>
          <label class="file-field">
            <span>签名文件</span>
            <input :key="`signature-${uploadInputKey}`" type="file" accept=".sig,.signature,.bin,application/octet-stream" @change="readUploadFile($event, 'signature')" />
            <small>{{ uploadSignature ? `${uploadSignature.name} · ${uploadSignature.size} bytes` : '未选择文件' }}</small>
          </label>
        </div>
        <label><span>签名者 ID</span><input v-model="uploadForm.signerId" maxlength="128" autocomplete="off" /></label>
        <label><span>显示名称</span><input v-model="uploadForm.name" maxlength="128" /></label>
        <label><span>描述</span><textarea v-model="uploadForm.description" maxlength="512" rows="3"></textarea></label>
        <p v-if="uploadError" id="plugin-upload-error" class="field-error" role="alert" aria-live="polite">{{ uploadError }}</p>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="uploading" @click="uploadVisible = false">取消</button>
          <button class="primary-button" type="submit" :disabled="Boolean(uploadError) || uploading">
            <LoaderCircle v-if="uploading" :size="16" class="spin" /><Upload v-else :size="16" />{{ uploading ? '安装中' : '安装' }}
          </button>
        </div>
      </form>
    </AdminDialog>

    <AdminDialog v-model:visible="configDialogVisible" :title="configEditing ? '编辑配置' : '新建配置'" width="min(640px, 94vw)" :close-disabled="configSaving">
      <form class="stack-form" :aria-describedby="configError ? 'plugin-config-error' : undefined" @submit.prevent="saveConfig">
        <div class="config-form-grid">
          <label><span>配置键</span><input v-model="configForm.key" maxlength="128" :disabled="Boolean(configEditing)" autofocus /></label>
          <label><span>类型</span>
            <select v-model="configForm.valueType" :disabled="Boolean(configEditing)" @change="handleConfigTypeChange">
              <option value="STRING">STRING</option><option value="INT">INT</option><option value="BOOL">BOOL</option><option value="SECRET">SECRET</option>
            </select>
          </label>
        </div>

        <template v-if="configForm.valueType === 'SECRET'">
          <label><span>敏感值</span><input v-model="configForm.value" type="password" autocomplete="new-password" :placeholder="configEditing?.hasValue ? '留空保留现有值' : ''" /></label>
        </template>
        <template v-else>
          <label class="toggle-line"><input v-model="configForm.useValue" type="checkbox" /><span>设置当前值</span></label>
          <label v-if="configForm.useValue"><span>当前值</span>
            <select v-if="configForm.valueType === 'BOOL'" v-model="configForm.value"><option value="true">true</option><option value="false">false</option></select>
            <input v-else v-model="configForm.value" :inputmode="configForm.valueType === 'INT' ? 'numeric' : 'text'" />
          </label>
          <label class="toggle-line"><input v-model="configForm.useDefault" type="checkbox" /><span>设置默认值</span></label>
          <label v-if="configForm.useDefault"><span>默认值</span>
            <select v-if="configForm.valueType === 'BOOL'" v-model="configForm.defaultValue"><option value="true">true</option><option value="false">false</option></select>
            <input v-else v-model="configForm.defaultValue" :inputmode="configForm.valueType === 'INT' ? 'numeric' : 'text'" />
          </label>
        </template>

        <label><span>说明</span><textarea v-model="configForm.description" maxlength="256" rows="3"></textarea></label>
        <label class="toggle-line"><input v-model="configForm.requiresRestart" type="checkbox" /><span>变更后需要重启插件</span></label>
        <p v-if="configError" id="plugin-config-error" class="field-error" role="alert" aria-live="polite">{{ configError }}</p>
        <div class="dialog-actions">
          <button class="secondary-button" type="button" :disabled="configSaving" @click="configDialogVisible = false">取消</button>
          <button class="primary-button" type="submit" :disabled="Boolean(configError) || configSaving">
            <LoaderCircle v-if="configSaving" :size="16" class="spin" /><Save v-else :size="16" />{{ configSaving ? '保存中' : '保存' }}
          </button>
        </div>
      </form>
    </AdminDialog>

    <AdminConfirmDialog
      v-model:visible="lifecycleVisible"
      :title="lifecycleTitle"
      :message="lifecycleMessage"
      :confirm-label="lifecycleSubmitting ? '处理中' : lifecycleConfirmLabel"
      :danger="lifecycleAction !== 'enable'"
      :close-on-confirm="false"
      :confirm-disabled="lifecycleSubmitting"
      :cancel-disabled="lifecycleSubmitting"
      @confirm="confirmLifecycle"
    />

    <AdminConfirmDialog
      v-model:visible="configDeleteVisible"
      title="删除配置"
      :message="`删除配置 ${configDeleteTarget?.key || ''}？`"
      :confirm-label="configDeleting ? '删除中' : '删除'"
      danger
      :close-on-confirm="false"
      :confirm-disabled="configDeleting"
      :cancel-disabled="configDeleting"
      @confirm="confirmConfigDelete"
    />
  </div>
</template>

<style scoped>
.plugin-page { align-items: start; }

.panel-actions,
.row-actions,
.detail-actions,
.dialog-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.row-actions { flex-wrap: nowrap; }
.row-actions .ghost-button { min-height: 44px; padding: 0 var(--space-sm); }

.inline-load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-base);
  min-height: 52px;
  margin-bottom: var(--space-base);
  padding: var(--space-sm) var(--space-base);
  border: 1px solid var(--error-border);
  border-radius: var(--radius-sm);
  background: var(--error-bg);
  color: var(--red-dark);
}

.plugin-name-cell {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-width: 180px;
}

.plugin-name-cell > svg { color: var(--green-dark); flex: 0 0 auto; }
.plugin-name-cell div,
.version-cell,
.config-key-cell { display: grid; gap: 3px; min-width: 0; }
.plugin-name-cell strong,
.plugin-name-cell span { overflow-wrap: anywhere; }
.plugin-name-cell span,
.version-cell span,
.config-key-cell span { color: var(--muted); font-size: var(--text-xs); }

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--space-xs);
  width: fit-content;
}

.status-active { color: var(--success); background: var(--success-bg); border-color: var(--success-border); }
.status-error { color: var(--red-dark); background: var(--error-bg); border-color: var(--error-border); }
.status-warning { color: var(--ink); background: var(--pill-bg); border-color: var(--amber); }
.status-neutral { color: var(--muted); background: var(--surface); border-color: var(--line); }

.dependency-list { display: grid; gap: var(--space-sm); }
.dependency-list.compact { display: flex; gap: var(--space-xs); flex-wrap: wrap; }

.dependency-chip {
  display: inline-flex;
  width: fit-content;
  margin: 0;
  padding: 3px var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--surface);
  color: var(--muted);
  font-size: var(--text-xs);
  line-height: 1.4;
}

.detail-tabs {
  display: flex;
  align-items: center;
  gap: var(--space-xl);
  min-height: 44px;
  margin-bottom: var(--space-lg);
  border-bottom: 1px solid var(--line);
}

.detail-tabs button {
  align-self: stretch;
  min-height: 44px;
  padding: 0 var(--space-xs);
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--muted);
  font-weight: 700;
}

.detail-tabs button:hover { color: var(--green-dark); }
.detail-tabs button.active { border-bottom-color: var(--green); color: var(--green-dark); }

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(260px, 2fr);
  gap: var(--space-xl);
}

.detail-block { min-width: 0; }
.detail-block h3 { margin: 0 0 var(--space-base); font-size: var(--text-base); }

.fact-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-base) var(--space-xl);
  margin: 0;
}

.fact-grid div { min-width: 0; border-bottom: 1px solid var(--line); padding-bottom: var(--space-sm); }
.fact-grid dt { color: var(--muted); font-size: var(--text-xs); font-weight: 700; }
.fact-grid dd { margin: var(--space-xs) 0 0; color: var(--ink); font-size: var(--text-sm); overflow-wrap: anywhere; }

.plugin-description { margin: var(--space-base) 0 0; color: var(--muted); line-height: 1.6; }

.dependency-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-base);
  min-height: 42px;
  border-bottom: 1px solid var(--line);
}

.dependency-row span { color: var(--muted); font-size: var(--text-sm); }
.compact-empty { min-height: 80px; }

.safe-diagnostic {
  display: flex;
  align-items: flex-start;
  gap: var(--space-md);
  grid-column: 1 / -1;
  padding: var(--space-base);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--primary-soft);
  color: var(--green-dark);
}

.safe-diagnostic.danger { border-color: var(--error-border); background: var(--error-bg); color: var(--red-dark); }
.safe-diagnostic div { display: grid; gap: var(--space-xs); }
.safe-diagnostic span { color: var(--muted); font-size: var(--text-sm); line-height: 1.5; }
.detail-actions { grid-column: 1 / -1; justify-content: flex-end; }

.section-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-base);
  margin-bottom: var(--space-base);
}

.section-toolbar > div:first-child { display: flex; align-items: baseline; gap: var(--space-sm); }
.section-toolbar > div:first-child span { color: var(--muted); font-size: var(--text-xs); }

.file-grid,
.config-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-md); }

.file-field {
  min-width: 0;
  padding: var(--space-base);
  border: 1px dashed var(--control-border);
  border-radius: var(--radius-sm);
  background: var(--surface);
}

.file-field input { padding: var(--space-sm); background: var(--panel-strong); }
.file-field small { color: var(--muted); overflow-wrap: anywhere; }

.toggle-line {
  display: inline-flex !important;
  grid-template-columns: none !important;
  align-items: center;
  width: fit-content;
  gap: var(--space-sm) !important;
}

.toggle-line input { width: 18px; min-height: 18px; margin: 0; }
.dialog-actions { justify-content: flex-end; }
.spin { animation: spin 800ms linear infinite; }

@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 900px) {
  .overview-grid { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 640px) {
  .panel-actions,
  .section-toolbar,
  .detail-actions,
  .inline-load-error { align-items: stretch; flex-direction: column; }
  .panel-actions button,
  .detail-actions button { width: 100%; }
  .plugin-page :deep(.filter-row input),
  .plugin-page :deep(.filter-row select) { width: 100%; max-width: none; }
  .detail-tabs { gap: 0; }
  .detail-tabs button { flex: 1; }
  .fact-grid,
  .file-grid,
  .config-form-grid { grid-template-columns: minmax(0, 1fr); }
}

@media (prefers-reduced-motion: reduce) {
  .spin { animation: none; }
}
</style>
