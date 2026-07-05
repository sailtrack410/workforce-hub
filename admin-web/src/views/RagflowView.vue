<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ArrowLeft, Download, Plus, RefreshCcw, Save, Search, Settings, Trash2, Upload, X } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { EntityId, RagflowAclResponse, RagflowDocumentResponse, RagflowGlobalSettingResponse, RagflowSearchTestResponse, RagflowSpaceResponse } from '@/api/types';
import { errorMessage, formatBytes, formatDateTime, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const toast = useToast();

const loading = ref(false);
const saving = ref(false);
const aclLoading = ref(false);
const docLoading = ref(false);
const uploading = ref(false);
const searchLoading = ref(false);
const reingestingId = ref<EntityId | null>(null);
const deletingId = ref<EntityId | null>(null);
const items = ref<RagflowSpaceResponse[]>([]);
const globalSetting = ref<RagflowGlobalSettingResponse | null>(null);
const selected = ref<RagflowSpaceResponse | null>(null);
const settingsSpace = ref<RagflowSpaceResponse | null>(null);
const documents = ref<RagflowDocumentResponse[]>([]);
const aclItems = ref<RagflowAclResponse[]>([]);
const keyword = ref('');
const createVisible = ref(false);
const settingsVisible = ref(false);
const globalSettingsVisible = ref(false);
const uploadVisible = ref(false);
const uploadDragActive = ref(false);
const uploadProgress = ref(0);
const uploadStatus = ref('');
const selectedFile = ref<File | null>(null);
const fileInputRef = ref<HTMLInputElement | null>(null);
const searchQuery = ref('');
const searchResult = ref<RagflowSearchTestResponse | null>(null);

const form = reactive({
  spaceName: '',
  spaceCode: '',
  description: '',
  visibility: 'PRIVATE',
  status: 'ACTIVE',
  embeddingProvider: '',
  embeddingModel: '',
  embeddingDimension: '',
  rerankEnabled: false,
  rerankProvider: '',
  rerankModel: '',
  chunkSize: '800',
  chunkOverlap: '100',
  retrievalTopK: '5',
  scoreThreshold: '',
});

const createForm = reactive({
  spaceName: '',
  spaceCode: '',
  description: '',
  visibility: 'PRIVATE',
});

const aclForm = reactive({
  subjectType: 'DEPARTMENT',
  subjectId: '',
  permCode: 'read',
});

const globalForm = reactive({
  ocrBaseUrl: '',
  ocrApiKey: '',
  ocrModel: '',
  embeddingBaseUrl: '',
  embeddingApiKey: '',
  embeddingModel: '',
  embeddingDimension: '',
  rerankEnabled: false,
  rerankBaseUrl: '',
  rerankApiKey: '',
  rerankModel: '',
  chunkSize: '800',
  chunkOverlap: '100',
  retrievalTopK: '5',
  scoreThreshold: '',
});

const columns: TableColumn[] = [
  { key: 'spaceName', label: '知识库' },
  { key: 'visibility', label: '查阅范围' },
  { key: 'documentCount', label: '文件数' },
  { key: 'chunkCount', label: '切片数' },
  { key: 'status', label: '状态' },
  { key: 'updatedAt', label: '更新时间' },
  { key: '_actions', label: '操作', width: '170px' },
];

const documentColumns: TableColumn[] = [
  { key: 'title', label: '文件' },
  { key: 'parserType', label: '解析' },
  { key: 'ingestStatus', label: '入库状态' },
  { key: 'vectorStatus', label: '向量状态' },
  { key: 'chunkCount', label: '切片' },
  { key: 'createdAt', label: '上传时间' },
  { key: '_actions', label: '操作', width: '126px' },
];

const aclColumns: TableColumn[] = [
  { key: 'subjectType', label: '类型' },
  { key: 'subjectId', label: '对象 ID' },
  { key: 'permCode', label: '权限' },
  { key: '_actions', label: '操作', width: '90px' },
];

const inDetail = computed(() => Boolean(selected.value));
const currentTitle = computed(() => selected.value?.spaceName ?? '知识库');

const visibilityLabel = (value?: string | null) => {
  const labels: Record<string, string> = {
    PRIVATE: '私有',
    ROLE: '按角色',
    DEPARTMENT: '按部门',
    POSITION: '按岗位',
    PUBLIC: '全员可查阅',
  };
  return value ? labels[value] ?? value : '-';
};

const parserTypeLabel = (value?: string | null) => {
  const labels: Record<string, string> = {
    TEXT: '文本',
    WORD: '文档',
    DOCUMENT: '文档',
    PDF: 'PDF',
    PPT: '演示文稿',
    TABLE: '表格',
    OCR: '图片 OCR',
  };
  return value ? labels[value.toUpperCase()] ?? value : '-';
};

onMounted(() => {
  void loadList();
  void loadGlobalSetting();
});

async function loadList() {
  loading.value = true;
  try {
    const res = await adminApi.ragflow.spaces(1, 100, keyword.value.trim() || undefined);
    items.value = res.list;
    if (selected.value) {
      const latest = res.list.find((item) => String(item.spaceId) === String(selected.value?.spaceId));
      if (latest) {
        selected.value = latest;
      }
    }
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

async function loadGlobalSetting() {
  try {
    globalSetting.value = await adminApi.ragflow.settings();
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

function openGlobalSettings() {
  const row = globalSetting.value;
  globalForm.ocrBaseUrl = row?.ocrBaseUrl ?? '';
  globalForm.ocrApiKey = '';
  globalForm.ocrModel = row?.ocrModel ?? '';
  globalForm.embeddingBaseUrl = row?.embeddingBaseUrl ?? '';
  globalForm.embeddingApiKey = '';
  globalForm.embeddingModel = row?.embeddingModel ?? '';
  globalForm.embeddingDimension = row?.embeddingDimension == null ? '' : String(row.embeddingDimension);
  globalForm.rerankEnabled = Boolean(row?.rerankEnabled);
  globalForm.rerankBaseUrl = row?.rerankBaseUrl ?? '';
  globalForm.rerankApiKey = '';
  globalForm.rerankModel = row?.rerankModel ?? '';
  globalForm.chunkSize = row?.chunkSize == null ? '800' : String(row.chunkSize);
  globalForm.chunkOverlap = row?.chunkOverlap == null ? '100' : String(row.chunkOverlap);
  globalForm.retrievalTopK = row?.retrievalTopK == null ? '5' : String(row.retrievalTopK);
  globalForm.scoreThreshold = row?.scoreThreshold == null ? '' : String(row.scoreThreshold);
  globalSettingsVisible.value = true;
}

async function saveGlobalSettings() {
  saving.value = true;
  try {
    globalSetting.value = await adminApi.ragflow.updateSettings({
      ocrBaseUrl: globalForm.ocrBaseUrl.trim() || null,
      ocrApiKey: globalForm.ocrApiKey.trim() || null,
      ocrModel: globalForm.ocrModel.trim() || null,
      embeddingBaseUrl: globalForm.embeddingBaseUrl.trim() || null,
      embeddingApiKey: globalForm.embeddingApiKey.trim() || null,
      embeddingModel: globalForm.embeddingModel.trim() || null,
      embeddingDimension: numberOrNull(globalForm.embeddingDimension),
      rerankEnabled: globalForm.rerankEnabled,
      rerankBaseUrl: globalForm.rerankBaseUrl.trim() || null,
      rerankApiKey: globalForm.rerankApiKey.trim() || null,
      rerankModel: globalForm.rerankModel.trim() || null,
      chunkSize: numberOrNull(globalForm.chunkSize) ?? 800,
      chunkOverlap: numberOrNull(globalForm.chunkOverlap) ?? 100,
      retrievalTopK: numberOrNull(globalForm.retrievalTopK) ?? 5,
      scoreThreshold: globalForm.scoreThreshold.trim() || null,
    });
    globalSettingsVisible.value = false;
    toast.success('知识库模型设置已保存');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

async function openSpace(row: RagflowSpaceResponse) {
  selected.value = row;
  searchResult.value = null;
  await loadDocuments();
}

function backToList() {
  selected.value = null;
  documents.value = [];
  searchResult.value = null;
}

async function openSettings(row: RagflowSpaceResponse) {
  settingsSpace.value = row;
  fillForm(row);
  settingsVisible.value = true;
  await loadAcl(row.spaceId);
}

function fillForm(row: RagflowSpaceResponse) {
  form.spaceName = row.spaceName;
  form.spaceCode = row.spaceCode;
  form.description = row.description ?? '';
  form.visibility = row.visibility;
  form.status = row.status;
  form.embeddingProvider = row.embeddingProvider ?? '';
  form.embeddingModel = row.embeddingModel ?? '';
  form.embeddingDimension = row.embeddingDimension == null ? '' : String(row.embeddingDimension);
  form.rerankEnabled = Boolean(row.rerankEnabled);
  form.rerankProvider = row.rerankProvider ?? '';
  form.rerankModel = row.rerankModel ?? '';
  form.chunkSize = row.chunkSize == null ? '800' : String(row.chunkSize);
  form.chunkOverlap = row.chunkOverlap == null ? '100' : String(row.chunkOverlap);
  form.retrievalTopK = row.retrievalTopK == null ? '5' : String(row.retrievalTopK);
  form.scoreThreshold = row.scoreThreshold == null ? '' : String(row.scoreThreshold);
}

async function loadAcl(spaceId: EntityId) {
  aclLoading.value = true;
  try {
    aclItems.value = await adminApi.ragflow.acl(spaceId);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    aclLoading.value = false;
  }
}

async function loadDocuments() {
  if (!selected.value) return;
  docLoading.value = true;
  try {
    documents.value = await adminApi.ragflow.documents(selected.value.spaceId);
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    docLoading.value = false;
  }
}

function openCreate() {
  createForm.spaceName = '';
  createForm.spaceCode = '';
  createForm.description = '';
  createForm.visibility = 'PRIVATE';
  createVisible.value = true;
}

async function handleCreate() {
  if (!createForm.spaceName.trim() || !createForm.spaceCode.trim()) return;
  saving.value = true;
  try {
    const created = await adminApi.ragflow.createSpace({
      spaceName: createForm.spaceName.trim(),
      spaceCode: createForm.spaceCode.trim(),
      description: createForm.description.trim() || null,
      visibility: createForm.visibility,
      status: 'ACTIVE',
      chunkSize: 800,
      chunkOverlap: 100,
      retrievalTopK: 5,
    });
    createVisible.value = false;
    toast.success('知识库已创建');
    await loadList();
    await openSpace(created);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

async function handleUpdate() {
  if (!settingsSpace.value || !form.spaceName.trim()) return;
  saving.value = true;
  try {
    const updated = await adminApi.ragflow.updateSpace(settingsSpace.value.spaceId, {
      spaceName: form.spaceName.trim(),
      description: form.description.trim() || null,
      visibility: form.visibility,
      status: form.status,
      embeddingProvider: form.embeddingProvider.trim() || null,
      embeddingModel: form.embeddingModel.trim() || null,
      embeddingDimension: numberOrNull(form.embeddingDimension),
      rerankEnabled: form.rerankEnabled,
      rerankProvider: form.rerankProvider.trim() || null,
      rerankModel: form.rerankModel.trim() || null,
      chunkSize: numberOrNull(form.chunkSize) ?? 800,
      chunkOverlap: numberOrNull(form.chunkOverlap) ?? 100,
      retrievalTopK: numberOrNull(form.retrievalTopK) ?? 5,
      scoreThreshold: form.scoreThreshold.trim() || null,
    });
    settingsSpace.value = updated;
    if (selected.value && String(selected.value.spaceId) === String(updated.spaceId)) {
      selected.value = updated;
    }
    settingsVisible.value = false;
    toast.success('知识库已保存');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

async function saveAcl() {
  if (!settingsSpace.value) return;
  saving.value = true;
  try {
    aclItems.value = await adminApi.ragflow.saveAcl(settingsSpace.value.spaceId, {
      items: aclItems.value.map((item) => ({
        subjectType: item.subjectType,
        subjectId: item.subjectId,
        permCode: item.permCode,
      })),
    });
    toast.success('权限已保存');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    saving.value = false;
  }
}

function addAcl() {
  const subjectId = aclForm.subjectId.trim();
  if (!settingsSpace.value || !subjectId) return;
  aclItems.value = [
    {
      aclId: createClientId('tmp'),
      spaceId: settingsSpace.value.spaceId,
      subjectType: aclForm.subjectType,
      subjectId,
      permCode: aclForm.permCode,
      status: 'ACTIVE',
    },
    ...aclItems.value.filter((item) => !(item.subjectType === aclForm.subjectType && String(item.subjectId) === subjectId)),
  ];
  aclForm.subjectId = '';
}

function removeAcl(row: RagflowAclResponse) {
  aclItems.value = aclItems.value.filter((item) => item !== row);
}

function handleFileChange(event: Event) {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0] ?? null;
  uploadProgress.value = 0;
  uploadStatus.value = '';
}

function openFilePicker() {
  fileInputRef.value?.click();
}

function openUploadDialog() {
  selectedFile.value = null;
  uploadDragActive.value = false;
  uploadProgress.value = 0;
  uploadStatus.value = '';
  if (fileInputRef.value) fileInputRef.value.value = '';
  uploadVisible.value = true;
}

function closeUploadDialog() {
  if (uploading.value) return;
  uploadVisible.value = false;
  uploadDragActive.value = false;
}

function handleFileDrop(event: DragEvent) {
  uploadDragActive.value = false;
  selectedFile.value = event.dataTransfer?.files?.[0] ?? null;
  uploadProgress.value = 0;
  uploadStatus.value = '';
  if (fileInputRef.value) fileInputRef.value.value = '';
}

async function uploadDocument() {
  if (!selected.value || !selectedFile.value) return;
  uploading.value = true;
  uploadProgress.value = 0;
  uploadStatus.value = '校验文件';
  try {
    const file = selectedFile.value;
    const contentSha256 = await sha256Hex(file);
    uploadProgress.value = 10;
    uploadStatus.value = '创建上传票据';
    const ticket = await adminApi.ragflow.createUploadTicket({
      ownerType: 'RAGFLOW_DOCUMENT',
      ownerId: selected.value.spaceId,
      fileName: file.name,
      mimeType: file.type || 'application/octet-stream',
      fileSize: file.size,
      contentSha256,
      clientRequestId: createClientId(`ragflow-${selected.value.spaceId}-${file.name}`),
    });
    uploadProgress.value = 20;
    let attachmentRefId = ticket.attachmentRefId;
    if (!ticket.deduplicated) {
      if (!ticket.uploadTicketId || !ticket.objectKey || !ticket.uploadUrl) {
        throw new Error('上传地址不可用');
      }
      uploadStatus.value = '上传文件';
      const uploadResponse = await uploadToTicketTarget(ticket.uploadUrl, file, normalizeUploadHeaders(ticket.headers, file), (percent) => {
        uploadProgress.value = Math.max(uploadProgress.value, Math.min(80, 20 + Math.round(percent * 0.6)));
      });
      if (!uploadResponse.ok) {
        throw new Error(await uploadResponse.text().catch(() => '') || '文件上传失败');
      }
      uploadProgress.value = 85;
      uploadStatus.value = '确认文件';
      const attachment = await adminApi.ragflow.confirmUpload({
        uploadTicketId: ticket.uploadTicketId,
        objectKey: ticket.objectKey,
        contentSha256,
        fileSize: file.size,
        ownerType: 'RAGFLOW_DOCUMENT',
        ownerId: selected.value.spaceId,
        displayName: file.name,
      });
      attachmentRefId = attachment.attachmentRefId;
    } else {
      uploadProgress.value = 85;
      uploadStatus.value = '复用文件';
    }
    if (!attachmentRefId) throw new Error('附件引用不可用');
    uploadProgress.value = 90;
    uploadStatus.value = '加入知识库';
    await adminApi.ragflow.addDocument(selected.value.spaceId, attachmentRefId);
    uploadProgress.value = 95;
    uploadStatus.value = '刷新列表';
    selectedFile.value = null;
    if (fileInputRef.value) fileInputRef.value.value = '';
    await loadDocuments();
    await loadList();
    uploadProgress.value = 100;
    uploadStatus.value = '上传完成';
    await new Promise((resolve) => window.setTimeout(resolve, 600));
    uploadVisible.value = false;
    toast.success('文件已上传');
  } catch (e) {
    uploadProgress.value = 0;
    uploadStatus.value = errorMessage(e);
    toast.error(uploadStatus.value);
  } finally {
    uploading.value = false;
  }
}

async function downloadDocument(row: RagflowDocumentResponse) {
  if (!row.attachmentRefId) return;
  try {
    const ticket = await adminApi.files.createDownloadTicket(row.attachmentRefId, { downloadType: 'SINGLE', reason: '知识库查看文件' });
    window.open(ticket.downloadUrl || adminApi.files.downloadUrl(ticket.ticketNo), '_blank', 'noopener');
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

async function reingestDocument(row: RagflowDocumentResponse) {
  if (!selected.value) return;
  reingestingId.value = row.documentId;
  try {
    await adminApi.ragflow.reingestDocument(selected.value.spaceId, row.documentId);
    await loadDocuments();
    await loadList();
    toast.success('已重新入库');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    reingestingId.value = null;
  }
}

async function deleteDocument(row: RagflowDocumentResponse) {
  if (!selected.value || !window.confirm(`删除文件「${row.title}」？`)) return;
  deletingId.value = row.documentId;
  try {
    await adminApi.ragflow.deleteDocument(selected.value.spaceId, row.documentId);
    await loadDocuments();
    await loadList();
    toast.success('文件已删除');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    deletingId.value = null;
  }
}

async function runSearchTest() {
  if (!selected.value || !searchQuery.value.trim()) return;
  searchLoading.value = true;
  try {
    searchResult.value = await adminApi.ragflow.searchTest(selected.value.spaceId, searchQuery.value.trim());
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    searchLoading.value = false;
  }
}

function numberOrNull(value: string) {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

async function sha256Hex(file: File) {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) throw new Error('当前环境不支持安全文件校验，请使用 HTTPS 或 localhost');
  const digest = await subtle.digest('SHA-256', await file.arrayBuffer());
  return [...new Uint8Array(digest)].map((item) => item.toString(16).padStart(2, '0')).join('');
}

function createClientId(prefix: string) {
  return `${prefix}-${globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`}`;
}

function uploadToTicketTarget(uploadUrl: string, file: File, uploadHeaders: Record<string, string>, onProgress: (percent: number) => void) {
  const remote = /^https?:\/\//i.test(uploadUrl);
  const requestUrl = remote ? uploadUrl : '/__admin-local-upload';
  const headers = remote
    ? uploadHeaders
    : {
      'Content-Type': uploadHeaders['Content-Type'] ?? (file.type || 'application/octet-stream'),
      'X-Upload-Path': uploadUrl,
    };
  return new Promise<Response>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', requestUrl);
    Object.entries(headers).forEach(([key, value]) => {
      if (value) xhr.setRequestHeader(key, value);
    });
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => resolve(new Response(xhr.responseText, { status: xhr.status, statusText: xhr.statusText }));
    xhr.onerror = () => reject(new Error('文件上传失败'));
    xhr.onabort = () => reject(new Error('文件上传已取消'));
    xhr.send(file);
  });
}

function normalizeUploadHeaders(headers: Record<string, string>, file: File) {
  const uploadHeaders = { ...headers };
  if (uploadHeaders['content-type'] && !uploadHeaders['Content-Type']) {
    uploadHeaders['Content-Type'] = uploadHeaders['content-type'];
    delete uploadHeaders['content-type'];
  }
  if (!uploadHeaders['Content-Type']) {
    uploadHeaders['Content-Type'] = file.type || 'application/octet-stream';
  }
  return uploadHeaders;
}
</script>

<template>
  <main class="page-grid ragflow-page">
    <template v-if="!inDetail">
      <AdminPanel :span="12" title="知识库模型设置">
        <template #actions>
          <button class="secondary-button" @click="openGlobalSettings">
            <Settings :size="16" />
            <span>设置</span>
          </button>
        </template>
        <div class="model-setting-grid">
          <div>
            <span>OCR</span>
            <strong>{{ globalSetting?.ocrModel || '-' }}</strong>
            <small>{{ globalSetting?.ocrApiKeyConfigured ? 'Key 已配置' : 'Key 未配置' }}</small>
          </div>
          <div>
            <span>嵌入</span>
            <strong>{{ globalSetting?.embeddingModel || '-' }}</strong>
            <small>{{ globalSetting?.embeddingApiKeyConfigured ? 'Key 已配置' : 'Key 未配置' }}{{ globalSetting?.embeddingDimension ? ` · ${globalSetting.embeddingDimension}` : '' }}</small>
          </div>
          <div>
            <span>重排</span>
            <strong>{{ globalSetting?.rerankEnabled ? (globalSetting?.rerankModel || '-') : '未启用' }}</strong>
            <small>{{ globalSetting?.rerankApiKeyConfigured ? 'Key 已配置' : 'Key 未配置' }}</small>
          </div>
          <div>
            <span>检索</span>
            <strong>Top {{ globalSetting?.retrievalTopK ?? 5 }}</strong>
            <small>{{ globalSetting?.chunkSize ?? 800 }} / {{ globalSetting?.chunkOverlap ?? 100 }}</small>
          </div>
        </div>
      </AdminPanel>

      <AdminPanel :span="12" title="知识库">
        <template #actions>
          <form class="ragflow-search" @submit.prevent="loadList">
            <input v-model="keyword" placeholder="搜索名称 / 编码" />
            <button class="secondary-button" title="搜索" type="submit">
              <Search :size="16" />
            </button>
          </form>
          <button class="secondary-button" title="刷新" :disabled="loading" @click="loadList">
            <RefreshCcw :size="16" />
          </button>
          <button class="primary-button" @click="openCreate">
            <Plus :size="16" />
            <span>新建知识库</span>
          </button>
        </template>

        <AdminTable :columns="columns" :rows="items" :loading="loading" row-key="spaceId" @row-click="openSpace">
          <template #cell-spaceName="{ row }">
            <strong>{{ row.spaceName }}</strong>
            <span>{{ row.spaceCode }}</span>
          </template>
          <template #cell-visibility="{ row }">
            {{ visibilityLabel(row.visibility) }}
          </template>
          <template #cell-status="{ row }">
            <span class="status-pill">{{ statusLabel(row.status) }}</span>
          </template>
          <template #cell-updatedAt="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
          <template #cell-_actions="{ row }">
            <div class="row-actions">
              <button class="secondary-button action-btn" @click.stop="openSpace(row)">进入</button>
              <button class="secondary-button action-btn" @click.stop="openSettings(row)">
                <Settings :size="14" />
                <span>设置</span>
              </button>
            </div>
          </template>
        </AdminTable>
      </AdminPanel>
    </template>

    <template v-else>
      <AdminPanel :span="12">
        <div class="kb-detail-head">
          <button class="icon-button" title="返回知识库列表" @click="backToList">
            <ArrowLeft :size="16" />
          </button>
          <div class="kb-detail-title">
            <h2>{{ currentTitle }}</h2>
            <span>文件、向量化进度、知识检索</span>
          </div>
          <div class="kb-detail-actions">
            <button v-if="selected" class="secondary-button" @click="openSettings(selected)">
              <Settings :size="16" />
              <span>设置</span>
            </button>
            <button class="icon-button" title="刷新文件列表" :disabled="docLoading" @click="loadDocuments">
              <RefreshCcw :size="16" />
            </button>
          </div>
        </div>

        <div class="detail-summary">
          <span>文件 {{ selected?.documentCount ?? 0 }}</span>
          <span>切片 {{ selected?.chunkCount ?? 0 }}</span>
          <span>{{ visibilityLabel(selected?.visibility) }}</span>
        </div>
      </AdminPanel>

      <section class="ragflow-detail-layout">
      <AdminPanel title="文件列表">
        <template #actions>
          <button class="primary-button" type="button" @click="openUploadDialog">
            <Upload :size="16" />
            <span>上传文件</span>
          </button>
        </template>

        <AdminTable :columns="documentColumns" :rows="documents" :loading="docLoading" row-key="documentId">
          <template #cell-title="{ row }">
            <strong>{{ row.title }}</strong>
            <span>{{ formatBytes(row.fileSize) }}</span>
          </template>
          <template #cell-parserType="{ row }">
            {{ parserTypeLabel(row.parserType) }}
          </template>
          <template #cell-ingestStatus="{ row }">
            <span class="status-pill">{{ statusLabel(row.ingestStatus) }}</span>
            <small v-if="row.errorMessage" class="cell-note error-text">{{ row.errorMessage }}</small>
          </template>
          <template #cell-vectorStatus="{ row }">
            <span class="status-pill">{{ statusLabel(row.vectorStatus || 'PENDING') }}</span>
          </template>
          <template #cell-createdAt="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
          <template #cell-_actions="{ row }">
            <button class="secondary-button action-btn" title="重新入库" aria-label="重新入库" :disabled="reingestingId === row.documentId" @click.stop="reingestDocument(row)">
              <RefreshCcw :size="14" />
            </button>
            <button class="secondary-button action-btn" title="下载文件" aria-label="下载文件" :disabled="!row.attachmentRefId" @click.stop="downloadDocument(row)">
              <Download :size="14" />
            </button>
            <button class="danger-button action-btn" title="删除文件" aria-label="删除文件" :disabled="deletingId === row.documentId" @click.stop="deleteDocument(row)">
              <Trash2 :size="14" />
            </button>
          </template>
        </AdminTable>
      </AdminPanel>

      <AdminPanel title="检索测试">
        <form class="search-test" @submit.prevent="runSearchTest">
          <input v-model="searchQuery" placeholder="输入问题或关键词" />
          <button class="secondary-button" :disabled="searchLoading" type="submit">
            <Search :size="16" />
            <span>{{ searchLoading ? '检索中' : '测试检索' }}</span>
          </button>
        </form>

        <div v-if="searchLoading" class="empty-state">检索中…</div>
        <div v-else-if="searchResult?.hits.length" class="hit-list">
          <article v-for="hit in searchResult.hits" :key="String(hit.chunkId)" class="hit-item">
            <strong>{{ hit.documentTitle || '-' }}</strong>
            <span>切片 {{ hit.chunkNo ?? '-' }}{{ hit.pageNo ? ` · 第 ${hit.pageNo} 页` : '' }}{{ hit.score == null ? '' : ` · ${(hit.score * 100).toFixed(1)}%` }}</span>
            <p>{{ hit.contentText }}</p>
          </article>
        </div>
        <div v-else class="empty-state">暂无结果</div>
      </AdminPanel>
      </section>
    </template>

    <AdminConfirmDialog v-model:visible="createVisible" title="新建知识库" message="" confirm-label="创建" @confirm="handleCreate">
      <template #default>
        <div class="dialog-form">
          <label>
            <span>名称</span>
            <input v-model="createForm.spaceName" placeholder="如 公司制度知识库" autofocus />
          </label>
          <label>
            <span>编码</span>
            <input v-model="createForm.spaceCode" placeholder="如 COMPANY_POLICY" />
          </label>
          <label>
            <span>描述</span>
            <textarea v-model="createForm.description" rows="3" />
          </label>
          <label>
            <span>查阅范围</span>
            <select v-model="createForm.visibility">
              <option value="PRIVATE">私有</option>
              <option value="ROLE">按角色</option>
              <option value="DEPARTMENT">按部门</option>
              <option value="POSITION">按岗位</option>
              <option value="PUBLIC">全员可查阅</option>
            </select>
          </label>
        </div>
      </template>
    </AdminConfirmDialog>

    <AdminConfirmDialog v-model:visible="globalSettingsVisible" title="知识库模型设置" message="" confirm-label="保存" @confirm="saveGlobalSettings">
      <template #default>
        <div class="dialog-form">
          <h3 class="full-field form-subtitle">OCR</h3>
          <label>
            <span>服务 URL</span>
            <input v-model="globalForm.ocrBaseUrl" />
          </label>
          <label>
            <span>API Key</span>
            <input v-model="globalForm.ocrApiKey" type="password" :placeholder="globalSetting?.ocrApiKeyConfigured ? '已配置' : ''" />
          </label>
          <label>
            <span>OCR 模型</span>
            <input v-model="globalForm.ocrModel" />
          </label>

          <h3 class="full-field form-subtitle">嵌入</h3>
          <label>
            <span>服务 URL</span>
            <input v-model="globalForm.embeddingBaseUrl" />
          </label>
          <label>
            <span>API Key</span>
            <input v-model="globalForm.embeddingApiKey" type="password" :placeholder="globalSetting?.embeddingApiKeyConfigured ? '已配置' : ''" />
          </label>
          <label>
            <span>嵌入模型</span>
            <input v-model="globalForm.embeddingModel" />
          </label>
          <label>
            <span>向量维度</span>
            <input v-model="globalForm.embeddingDimension" type="number" min="1" />
          </label>
          <label>
            <span>切片大小</span>
            <input v-model="globalForm.chunkSize" type="number" min="100" />
          </label>
          <label>
            <span>切片重叠</span>
            <input v-model="globalForm.chunkOverlap" type="number" min="0" />
          </label>
          <label>
            <span>召回数量</span>
            <input v-model="globalForm.retrievalTopK" type="number" min="1" />
          </label>
          <label>
            <span>分数阈值</span>
            <input v-model="globalForm.scoreThreshold" placeholder="0-1，可空" />
          </label>
          <label class="check-row">
            <input v-model="globalForm.rerankEnabled" type="checkbox" />
            <span>启用重排序</span>
          </label>

          <h3 class="full-field form-subtitle">重排序</h3>
          <label>
            <span>服务 URL</span>
            <input v-model="globalForm.rerankBaseUrl" />
          </label>
          <label>
            <span>API Key</span>
            <input v-model="globalForm.rerankApiKey" type="password" :placeholder="globalSetting?.rerankApiKeyConfigured ? '已配置' : ''" />
          </label>
          <label>
            <span>重排序模型</span>
            <input v-model="globalForm.rerankModel" />
          </label>
        </div>
      </template>
    </AdminConfirmDialog>

    <AdminConfirmDialog v-model:visible="settingsVisible" title="知识库设置" message="" confirm-label="保存" @confirm="handleUpdate">
      <template #default>
        <div class="settings-dialog">
          <section class="dialog-form">
            <label>
              <span>名称</span>
              <input v-model="form.spaceName" />
            </label>
            <label>
              <span>编码</span>
              <input v-model="form.spaceCode" disabled />
            </label>
            <label class="full-field">
              <span>描述</span>
              <textarea v-model="form.description" rows="3" />
            </label>
            <label>
              <span>查阅范围</span>
              <select v-model="form.visibility">
                <option value="PRIVATE">私有</option>
                <option value="ROLE">按角色</option>
                <option value="DEPARTMENT">按部门</option>
                <option value="POSITION">按岗位</option>
                <option value="PUBLIC">全员可查阅</option>
              </select>
            </label>
            <label>
              <span>状态</span>
              <select v-model="form.status">
                <option value="ACTIVE">启用</option>
                <option value="INACTIVE">停用</option>
              </select>
            </label>
          </section>

          <section class="dialog-section">
            <h3>模型设置</h3>
            <div class="dialog-form">
              <label>
                <span>嵌入供应商</span>
                <input v-model="form.embeddingProvider" placeholder="如 openai" />
              </label>
              <label>
                <span>嵌入模型</span>
                <input v-model="form.embeddingModel" placeholder="如 text-embedding-3-small" />
              </label>
              <label>
                <span>向量维度</span>
                <input v-model="form.embeddingDimension" type="number" min="1" />
              </label>
              <label>
                <span>切片大小</span>
                <input v-model="form.chunkSize" type="number" min="100" />
              </label>
              <label>
                <span>切片重叠</span>
                <input v-model="form.chunkOverlap" type="number" min="0" />
              </label>
              <label>
                <span>召回数量</span>
                <input v-model="form.retrievalTopK" type="number" min="1" />
              </label>
              <label>
                <span>分数阈值</span>
                <input v-model="form.scoreThreshold" placeholder="0-1，可空" />
              </label>
              <label class="check-row">
                <input v-model="form.rerankEnabled" type="checkbox" />
                <span>启用重排序</span>
              </label>
              <label>
                <span>重排序供应商</span>
                <input v-model="form.rerankProvider" />
              </label>
              <label>
                <span>重排序模型</span>
                <input v-model="form.rerankModel" />
              </label>
            </div>
          </section>

          <section class="dialog-section">
            <div class="section-head">
              <h3>查阅权限</h3>
              <button class="secondary-button" :disabled="!settingsSpace || saving" @click.stop="saveAcl">
                <Save :size="16" />
                <span>保存权限</span>
              </button>
            </div>
            <form class="acl-form" @submit.prevent="addAcl">
              <select v-model="aclForm.subjectType">
                <option value="ROLE">角色</option>
                <option value="DEPARTMENT">部门</option>
                <option value="POSITION">岗位</option>
              </select>
              <input v-model="aclForm.subjectId" placeholder="角色/部门/岗位 ID" />
              <select v-model="aclForm.permCode">
                <option value="read">可查阅</option>
                <option value="manage">可管理</option>
              </select>
              <button class="secondary-button" type="submit">添加</button>
            </form>
            <AdminTable :columns="aclColumns" :rows="aclItems" :loading="aclLoading" row-key="aclId">
              <template #cell-subjectType="{ row }">
                {{ row.subjectType === 'ROLE' ? '角色' : row.subjectType === 'DEPARTMENT' ? '部门' : '岗位' }}
              </template>
              <template #cell-permCode="{ row }">
                {{ row.permCode === 'manage' ? '可管理' : '可查阅' }}
              </template>
              <template #cell-_actions="{ row }">
                <button class="danger-button action-btn" @click.stop="removeAcl(row)">移除</button>
              </template>
            </AdminTable>
          </section>
        </div>
      </template>
    </AdminConfirmDialog>

    <Teleport to="body">
      <div v-if="uploadVisible" class="upload-overlay" @click.self="closeUploadDialog">
        <section class="upload-dialog" role="dialog" aria-modal="true" aria-labelledby="upload-dialog-title">
          <header class="upload-dialog-head">
            <h3 id="upload-dialog-title">上传文件</h3>
            <button class="icon-button" type="button" title="关闭" :disabled="uploading" @click="closeUploadDialog">
              <X :size="16" />
            </button>
          </header>

          <form class="upload-dialog-form" @submit.prevent="uploadDocument">
            <input ref="fileInputRef" class="file-picker-input" type="file" accept=".txt,.md,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.csv,.png,.jpg,.jpeg,.webp" @change="handleFileChange" />
            <button
              :class="['upload-dropzone', { active: uploadDragActive }]"
              type="button"
              @click="openFilePicker"
              @dragenter.prevent="uploadDragActive = true"
              @dragover.prevent="uploadDragActive = true"
              @dragleave.prevent="uploadDragActive = false"
              @drop.prevent="handleFileDrop"
            >
              <Upload :size="22" />
              <strong>{{ selectedFile?.name || '拖拽上传' }}</strong>
              <span>{{ selectedFile ? formatBytes(selectedFile.size) : '选择本地文件' }}</span>
            </button>

            <div v-if="uploading || uploadStatus" class="upload-progress" role="status" aria-live="polite">
              <div>
                <span>{{ uploadStatus || '上传中' }}</span>
                <strong>{{ uploadProgress }}%</strong>
              </div>
              <progress :value="uploadProgress" max="100">{{ uploadProgress }}%</progress>
            </div>

            <div class="upload-actions">
              <button class="secondary-button" type="button" :disabled="uploading" @click="openFilePicker">选择本地文件</button>
              <button class="primary-button" type="submit" :disabled="uploading || !selectedFile">
                <Upload :size="16" />
                <span>{{ uploading ? '上传中' : '上传文件' }}</span>
              </button>
            </div>
          </form>
        </section>
      </div>
    </Teleport>
  </main>
</template>

<style scoped>
.ragflow-search,
.acl-form,
.row-actions,
.search-test,
.section-head {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.ragflow-search input {
  width: 220px;
}

.model-setting-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-md);
}

.model-setting-grid div {
  display: grid;
  gap: var(--space-xs);
  min-height: 72px;
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  box-shadow: var(--shadow-sm);
}

.model-setting-grid span,
.model-setting-grid small {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.model-setting-grid strong {
  min-width: 0;
  overflow: hidden;
  color: var(--ink);
  font-size: var(--text-base);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-actions {
  flex-wrap: nowrap;
}

.action-btn {
  min-height: 30px;
  padding: 0 8px;
  font-size: 12px;
}

.cell-note {
  display: block;
  margin-top: 4px;
  font-size: var(--text-xs);
}

.error-text {
  color: var(--red);
}

.kb-detail-head {
  display: grid;
  grid-template-columns: var(--control-h) minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-base);
  margin-bottom: var(--space-md);
}

.kb-detail-title h2 {
  margin: 0;
  font-size: var(--text-md);
}

.kb-detail-title span {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: var(--text-sm);
}

.kb-detail-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.ragflow-detail-layout {
  grid-column: span 12;
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(280px, 3fr);
  gap: var(--space-base);
}

.detail-summary {
  display: flex;
  align-items: center;
  gap: var(--space-base);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.file-picker-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.search-test {
  flex-direction: column;
  align-items: stretch;
  margin-bottom: var(--space-lg);
}

.hit-list,
.settings-dialog {
  display: grid;
  gap: var(--space-lg);
}

.hit-item {
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  box-shadow: var(--shadow-sm);
}

.hit-item strong,
.hit-item span {
  display: block;
}

.hit-item span {
  margin-top: 3px;
  color: var(--muted);
  font-size: var(--text-xs);
}

.hit-item p {
  margin: var(--space-sm) 0 0;
  color: var(--ink);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.dialog-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-lg);
  margin-top: var(--space-md);
}

.dialog-form label,
.dialog-form .check-row {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.dialog-form .check-row {
  display: flex;
  align-items: center;
  color: var(--ink);
}

.dialog-form .check-row input {
  width: 18px;
  min-height: 18px;
}

.form-subtitle {
  margin: 0;
  padding-top: var(--space-xs);
  color: var(--ink);
  font-size: var(--text-base);
}

.full-field {
  grid-column: 1 / -1;
}

.settings-dialog {
  max-height: 70vh;
  overflow: auto;
  padding-right: 4px;
}

.dialog-section {
  padding-top: var(--space-md);
  border-top: 1px solid var(--line);
}

.dialog-section h3,
.section-head h3 {
  margin: 0;
  font-size: var(--text-base);
}

.section-head {
  justify-content: space-between;
  margin-bottom: var(--space-md);
}

.acl-form {
  margin-bottom: var(--space-md);
}

.upload-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: grid;
  place-items: center;
  padding: var(--space-xl);
  background: rgba(0, 20, 60, 0.4);
}

.upload-dialog {
  width: min(520px, 92vw);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel);
  box-shadow: var(--shadow-sm);
  padding: var(--space-xl);
}

.upload-dialog-head,
.upload-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.upload-dialog-head {
  margin-bottom: var(--space-base);
}

.upload-dialog-head h3 {
  margin: 0;
  font-size: var(--text-md);
}

.upload-dialog-form {
  display: grid;
  gap: var(--space-base);
}

.upload-dropzone {
  min-height: 180px;
  display: grid;
  place-items: center;
  gap: var(--space-sm);
  padding: var(--space-xl);
  border: 1px dashed var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--muted);
  text-align: center;
  transition: all 200ms ease;
}

.upload-dropzone:hover,
.upload-dropzone.active {
  border-color: var(--green);
  background: var(--hover-bg);
  color: var(--green-dark);
}

.upload-dropzone strong {
  max-width: 100%;
  overflow: hidden;
  color: var(--ink);
  font-size: var(--text-base);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-dropzone span {
  color: var(--muted);
  font-size: var(--text-sm);
}

.upload-progress {
  display: grid;
  gap: var(--space-xs);
}

.upload-progress div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-sm);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.upload-progress strong {
  color: var(--green-dark);
}

.upload-progress progress {
  width: 100%;
  height: 8px;
  overflow: hidden;
  border: 0;
  border-radius: 999px;
  background: var(--surface);
}

.upload-progress progress::-webkit-progress-bar {
  background: var(--surface);
}

.upload-progress progress::-webkit-progress-value {
  border-radius: 999px;
  background: var(--green);
}

.upload-progress progress::-moz-progress-bar {
  border-radius: 999px;
  background: var(--green);
}

@media (max-width: 980px) {
  .ragflow-search,
  .acl-form {
    flex-wrap: wrap;
  }

  .model-setting-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ragflow-detail-layout {
    grid-template-columns: 1fr;
  }

  .kb-detail-head {
    grid-template-columns: var(--control-h) minmax(0, 1fr);
  }

  .kb-detail-actions {
    grid-column: 1 / -1;
    justify-content: flex-end;
  }

  .dialog-form {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .model-setting-grid {
    grid-template-columns: 1fr;
  }

  .upload-dialog {
    padding: var(--space-base);
  }

  .upload-actions {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
