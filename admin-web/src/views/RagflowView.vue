<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { adminApi } from '@/api/admin';
import type { EntityId, LlmModelResponse, OrgDepartmentTreeNodeResponse, OrgPositionResponse, RagflowAclResponse, RagflowDocumentResponse, RagflowGlobalSettingResponse, RagflowSearchTestResponse, RagflowSpaceResponse, RbacRoleResponse } from '@/api/types';
import { errorMessage, roleCodeLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import type { TableColumn } from '@/components/AdminTable.vue';
import RagflowCreateDialog from '@/components/ragflow/RagflowCreateDialog.vue';
import RagflowDetailPanel from '@/components/ragflow/RagflowDetailPanel.vue';
import RagflowDocumentDeleteDialog from '@/components/ragflow/RagflowDocumentDeleteDialog.vue';
import RagflowGlobalSettingsDialog from '@/components/ragflow/RagflowGlobalSettingsDialog.vue';
import RagflowListPanel from '@/components/ragflow/RagflowListPanel.vue';
import RagflowSettingsDialog from '@/components/ragflow/RagflowSettingsDialog.vue';
import RagflowSpaceDeleteDialog from '@/components/ragflow/RagflowSpaceDeleteDialog.vue';
import RagflowUploadDialog from '@/components/ragflow/RagflowUploadDialog.vue';
import '@/styles/ragflow.css';

type UploadQueueStatus = 'READY' | 'HASHING' | 'TICKET' | 'UPLOADING' | 'CONFIRMING' | 'REGISTERING' | 'DONE' | 'FAILED';

interface UploadQueueItem {
  id: string;
  file: File;
  progress: number;
  status: UploadQueueStatus;
  message: string;
}

const toast = useToast();

const loading = ref(false);
const saving = ref(false);
const aclLoading = ref(false);
const docLoading = ref(false);
const uploading = ref(false);
const searchLoading = ref(false);
const reingestingId = ref<EntityId | null>(null);
const deletingId = ref<EntityId | null>(null);
const deletingSpace = ref(false);
const items = ref<RagflowSpaceResponse[]>([]);
const globalSetting = ref<RagflowGlobalSettingResponse | null>(null);
const selected = ref<RagflowSpaceResponse | null>(null);
const settingsSpace = ref<RagflowSpaceResponse | null>(null);
const documents = ref<RagflowDocumentResponse[]>([]);
const aclItems = ref<RagflowAclResponse[]>([]);
const roles = ref<RbacRoleResponse[]>([]);
const departments = ref<OrgDepartmentTreeNodeResponse[]>([]);
const positions = ref<OrgPositionResponse[]>([]);
const llmModels = ref<LlmModelResponse[]>([]);
const keyword = ref('');
const createVisible = ref(false);
const settingsVisible = ref(false);
const globalSettingsVisible = ref(false);
const uploadVisible = ref(false);
const uploadDragActive = ref(false);
const uploadQueue = ref<UploadQueueItem[]>([]);
const searchQuery = ref('');
const searchResult = ref<RagflowSearchTestResponse | null>(null);
const documentDeleteVisible = ref(false);
const documentDeleteTarget = ref<RagflowDocumentResponse | null>(null);
const spaceDeleteVisible = ref(false);
const spaceDeleteTarget = ref<RagflowSpaceResponse | null>(null);

const form = reactive({
  spaceName: '',
  spaceCode: '',
  description: '',
  visibility: 'PRIVATE',
  status: 'ACTIVE',
  embeddingModelId: '',
  embeddingDimension: '',
  rerankEnabled: false,
  rerankModelId: '',
  chunkSize: '',
  chunkOverlap: '',
  retrievalTopK: '',
  scoreThreshold: '',
});

const createForm = reactive({
  spaceName: '',
  spaceCode: '',
  description: '',
  visibility: 'PRIVATE',
  embeddingModelId: '',
  rerankEnabled: false,
  rerankModelId: '',
  chunkSize: '800',
  chunkOverlap: '100',
  retrievalTopK: '5',
  scoreThreshold: '',
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
  ocrModelId: '',
  embeddingBaseUrl: '',
  embeddingApiKey: '',
  embeddingModel: '',
  embeddingModelId: '',
  embeddingDimension: '',
  rerankEnabled: false,
  rerankBaseUrl: '',
  rerankApiKey: '',
  rerankModel: '',
  rerankModelId: '',
  chunkSize: '800',
  chunkOverlap: '100',
  retrievalTopK: '5',
  scoreThreshold: '',
});

const scoreThresholdOptions = [
  { value: '', label: '默认阈值' },
  { value: '0.2', label: '宽松匹配' },
  { value: '0.5', label: '标准匹配' },
  { value: '0.8', label: '严格匹配' },
];

const ocrModelOptions = computed(() => activeModels('OCR'));
const embeddingModelOptions = computed(() => activeModels('EMBEDDING'));
const rerankModelOptions = computed(() => activeModels('RERANK'));

const columns: TableColumn[] = [
  { key: 'spaceName', label: '知识库' },
  { key: 'visibility', label: '查阅范围' },
  { key: 'documentCount', label: '文件数' },
  { key: 'chunkCount', label: '切片数' },
  { key: 'status', label: '状态' },
  { key: 'updatedAt', label: '更新时间' },
  { key: '_actions', label: '操作', width: '230px' },
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
  { key: 'subjectId', label: '对象' },
  { key: 'permCode', label: '权限' },
  { key: '_actions', label: '操作', width: '90px' },
];

const inDetail = computed(() => Boolean(selected.value));
const currentTitle = computed(() => selected.value?.spaceName ?? '知识库');
const aclSubjectOptions = computed(() => {
  if (aclForm.subjectType === 'ROLE') {
    return roles.value.map((item) => ({ id: item.roleId, name: item.roleName || roleCodeLabel(item.roleCode) }));
  }
  if (aclForm.subjectType === 'POSITION') {
    return positions.value.map((item) => ({ id: item.id, name: item.positionName }));
  }
  return flattenDepartments(departments.value).map((item) => ({ id: item.id, name: item.deptName }));
});

onMounted(() => {
  void loadList();
  void loadGlobalSetting();
  void loadAclOptions();
  void loadLlmModels();
});

async function loadAclOptions() {
  try {
    const [roleRows, departmentRows, positionRows] = await Promise.all([
      adminApi.rbac.roles(),
      adminApi.org.departmentTree(),
      adminApi.org.positions(),
    ]);
    roles.value = roleRows;
    departments.value = departmentRows;
    positions.value = positionRows;
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

function flattenDepartments(nodes: OrgDepartmentTreeNodeResponse[]): OrgDepartmentTreeNodeResponse[] {
  return nodes.flatMap((node) => [node, ...flattenDepartments(node.children ?? [])]);
}

async function loadList() {
  loading.value = true;
  try {
    const res = await adminApi.ragflow.spaces(1, 100, keyword.value.trim() || undefined);
    items.value = res.list;
    if (selected.value) {
      const latest = res.list.find((item) => String(item.spaceId) === String(selected.value?.spaceId));
      if (latest) {
        selected.value = latest;
      } else {
        backToList();
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

async function loadLlmModels() {
  try {
    llmModels.value = await adminApi.llmModels.list();
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

function activeModels(type: string) {
  return llmModels.value.filter((item) => item.status === 'ACTIVE' && item.modelType === type);
}

function openGlobalSettings() {
  const row = globalSetting.value;
  globalForm.ocrBaseUrl = row?.ocrBaseUrl ?? '';
  globalForm.ocrApiKey = '';
  globalForm.ocrModel = row?.ocrModel ?? '';
  globalForm.ocrModelId = row?.ocrModelId == null ? '' : String(row.ocrModelId);
  globalForm.embeddingBaseUrl = row?.embeddingBaseUrl ?? '';
  globalForm.embeddingApiKey = '';
  globalForm.embeddingModel = row?.embeddingModel ?? '';
  globalForm.embeddingModelId = row?.embeddingModelId == null ? '' : String(row.embeddingModelId);
  globalForm.embeddingDimension = row?.embeddingDimension == null ? '' : String(row.embeddingDimension);
  globalForm.rerankEnabled = Boolean(row?.rerankEnabled);
  globalForm.rerankBaseUrl = row?.rerankBaseUrl ?? '';
  globalForm.rerankApiKey = '';
  globalForm.rerankModel = row?.rerankModel ?? '';
  globalForm.rerankModelId = row?.rerankModelId == null ? '' : String(row.rerankModelId);
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
      ocrModelId: globalForm.ocrModelId || null,
      embeddingBaseUrl: globalForm.embeddingBaseUrl.trim() || null,
      embeddingApiKey: globalForm.embeddingApiKey.trim() || null,
      embeddingModel: globalForm.embeddingModel.trim() || null,
      embeddingModelId: globalForm.embeddingModelId || null,
      rerankEnabled: globalForm.rerankEnabled,
      rerankBaseUrl: globalForm.rerankBaseUrl.trim() || null,
      rerankApiKey: globalForm.rerankApiKey.trim() || null,
      rerankModel: globalForm.rerankModel.trim() || null,
      rerankModelId: globalForm.rerankModelId || null,
      chunkSize: numberOrNull(globalForm.chunkSize) ?? 800,
      chunkOverlap: numberOrNull(globalForm.chunkOverlap) ?? 100,
      retrievalTopK: numberOrNull(globalForm.retrievalTopK) ?? 5,
      scoreThreshold: textOrNull(globalForm.scoreThreshold),
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
  form.embeddingModelId = row.embeddingModelId == null ? '' : String(row.embeddingModelId);
  form.embeddingDimension = row.embeddingDimension == null ? '' : String(row.embeddingDimension);
  form.rerankEnabled = Boolean(row.rerankEnabled);
  form.rerankModelId = row.rerankModelId == null ? '' : String(row.rerankModelId);
  form.chunkSize = row.chunkSize == null ? '' : String(row.chunkSize);
  form.chunkOverlap = row.chunkOverlap == null ? '' : String(row.chunkOverlap);
  form.retrievalTopK = row.retrievalTopK == null ? '' : String(row.retrievalTopK);
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
  const setting = globalSetting.value;
  createForm.spaceName = '';
  createForm.spaceCode = `KB_${Date.now()}`;
  createForm.description = '';
  createForm.visibility = 'PRIVATE';
  createForm.embeddingModelId = '';
  createForm.rerankEnabled = false;
  createForm.rerankModelId = '';
  createForm.chunkSize = String(setting?.chunkSize ?? 800);
  createForm.chunkOverlap = String(setting?.chunkOverlap ?? 100);
  createForm.retrievalTopK = String(setting?.retrievalTopK ?? 5);
  createForm.scoreThreshold = setting?.scoreThreshold == null ? '' : String(setting.scoreThreshold);
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
      embeddingModelId: createForm.embeddingModelId || null,
      rerankEnabled: createForm.rerankModelId ? createForm.rerankEnabled : null,
      rerankModelId: createForm.rerankModelId || null,
      chunkSize: numberOrNull(createForm.chunkSize),
      chunkOverlap: numberOrNull(createForm.chunkOverlap),
      retrievalTopK: numberOrNull(createForm.retrievalTopK),
      scoreThreshold: textOrNull(createForm.scoreThreshold),
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
      rerankEnabled: form.rerankEnabled,
      rerankModelId: form.rerankModelId || null,
      retrievalTopK: numberOrNull(form.retrievalTopK),
      scoreThreshold: textOrNull(form.scoreThreshold),
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
  const subject = aclSubjectOptions.value.find((item) => String(item.id) === subjectId);
  aclItems.value = [
    {
      aclId: createClientId('tmp'),
      spaceId: settingsSpace.value.spaceId,
      subjectType: aclForm.subjectType,
      subjectId,
      subjectName: subject?.name,
      permCode: aclForm.permCode,
      status: 'ACTIVE',
    },
    ...aclItems.value.filter((item) => !(item.subjectType === aclForm.subjectType && String(item.subjectId) === subjectId)),
  ];
  aclForm.subjectId = '';
}

function aclSubjectName(row: RagflowAclResponse) {
  if (row.subjectName) return row.subjectName;
  const options = row.subjectType === 'ROLE'
    ? roles.value.map((item) => ({ id: item.roleId, name: item.roleName || roleCodeLabel(item.roleCode) }))
    : row.subjectType === 'POSITION'
      ? positions.value.map((item) => ({ id: item.id, name: item.positionName }))
      : flattenDepartments(departments.value).map((item) => ({ id: item.id, name: item.deptName }));
  return options.find((item) => String(item.id) === String(row.subjectId))?.name || '未命名对象';
}

function removeAcl(row: RagflowAclResponse) {
  aclItems.value = aclItems.value.filter((item) => item !== row);
}

function openUploadDialog() {
  uploadQueue.value = [];
  uploadDragActive.value = false;
  uploadVisible.value = true;
}

function closeUploadDialog() {
  if (uploading.value) return;
  uploadVisible.value = false;
  uploadDragActive.value = false;
  uploadQueue.value = [];
}

function setUploadFiles(files: File[]) {
  if (!files.length || uploading.value) return;
  const keys = new Set(uploadQueue.value.map((item) => uploadFileKey(item.file)));
  const nextItems = files
    .filter((file) => {
      const key = uploadFileKey(file);
      if (keys.has(key)) return false;
      keys.add(key);
      return true;
    })
    .map((file) => ({
      id: createClientId(`upload-${file.name}`),
      file,
      progress: 0,
      status: 'READY' as UploadQueueStatus,
      message: '待上传',
    }));
  uploadQueue.value = [...uploadQueue.value, ...nextItems];
}

function removeUploadFile(id: string) {
  if (uploading.value) return;
  uploadQueue.value = uploadQueue.value.filter((item) => item.id !== id);
}

function clearUploadQueue() {
  if (uploading.value) return;
  uploadQueue.value = [];
}

async function uploadDocuments() {
  if (!selected.value || !uploadQueue.value.length) return;
  uploading.value = true;
  let successCount = 0;
  let failedCount = 0;
  try {
    for (const item of uploadQueue.value) {
      if (item.status === 'DONE') continue;
      const uploaded = await uploadSingleDocument(item);
      if (uploaded) {
        successCount += 1;
      } else {
        failedCount += 1;
      }
    }
    await loadDocuments();
    await loadList();
    if (failedCount === 0) {
      uploadVisible.value = false;
      uploadDragActive.value = false;
      uploadQueue.value = [];
      toast.success(`${successCount} 个文件已上传，正在后台入库`);
    } else {
      toast.error(`上传完成：成功 ${successCount} 个，失败 ${failedCount} 个`);
    }
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    uploading.value = false;
  }
}

async function uploadSingleDocument(item: UploadQueueItem) {
  if (!selected.value) return false;
  const file = item.file;
  try {
    updateUploadItem(item.id, { progress: 5, status: 'HASHING', message: '校验文件' });
    const contentSha256 = await sha256Hex(file);
    updateUploadItem(item.id, { progress: 12, status: 'TICKET', message: '创建上传票据' });
    const ticket = await adminApi.ragflow.createUploadTicket({
      ownerType: 'RAGFLOW_DOCUMENT',
      ownerId: selected.value.spaceId,
      fileName: file.name,
      mimeType: file.type || 'application/octet-stream',
      fileSize: file.size,
      contentSha256,
      clientRequestId: createClientId(`ragflow-${selected.value.spaceId}-${file.name}`),
    });
    updateUploadItem(item.id, { progress: 20, status: 'UPLOADING', message: ticket.deduplicated ? '复用文件' : '上传文件' });
    let attachmentRefId = ticket.attachmentRefId;
    if (!ticket.deduplicated) {
      if (!ticket.uploadTicketId || !ticket.objectKey || !ticket.uploadUrl) {
        throw new Error('上传地址不可用');
      }
      const uploadResponse = await uploadToTicketTarget(ticket.uploadUrl, file, normalizeUploadHeaders(ticket.headers, file), (percent) => {
        updateUploadItem(item.id, { progress: Math.max(20, Math.min(76, 20 + Math.round(percent * 0.56))) });
      });
      if (!uploadResponse.ok) {
        throw new Error(await uploadResponse.text().catch(() => '') || '文件上传失败');
      }
      updateUploadItem(item.id, { progress: 82, status: 'CONFIRMING', message: '确认文件' });
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
    }
    if (!attachmentRefId) throw new Error('附件引用不可用');
    updateUploadItem(item.id, { progress: 92, status: 'REGISTERING', message: '登记入库任务' });
    await adminApi.ragflow.addDocument(selected.value.spaceId, attachmentRefId);
    updateUploadItem(item.id, { progress: 100, status: 'DONE', message: '后台入库中' });
    return true;
  } catch (e) {
    updateUploadItem(item.id, { progress: 0, status: 'FAILED', message: errorMessage(e) });
    return false;
  }
}

function updateUploadItem(id: string, patch: Partial<UploadQueueItem>) {
  uploadQueue.value = uploadQueue.value.map((item) => (item.id === id ? { ...item, ...patch } : item));
}

function uploadFileKey(file: File) {
  return `${file.name}:${file.size}:${file.lastModified}`;
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

function deleteDocument(row: RagflowDocumentResponse) {
  if (!selected.value) return;
  documentDeleteTarget.value = row;
  documentDeleteVisible.value = true;
}

function deleteSpace(row: RagflowSpaceResponse) {
  spaceDeleteTarget.value = row;
  spaceDeleteVisible.value = true;
}

async function confirmDeleteSpace() {
  const row = spaceDeleteTarget.value;
  if (!row) return;
  deletingSpace.value = true;
  try {
    await adminApi.ragflow.deleteSpace(row.spaceId);
    if (selected.value && String(selected.value.spaceId) === String(row.spaceId)) {
      backToList();
    }
    await loadList();
    toast.success('知识库已删除');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    deletingSpace.value = false;
    spaceDeleteTarget.value = null;
  }
}

async function confirmDeleteDocument() {
  const row = documentDeleteTarget.value;
  if (!selected.value || !row) return;
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
    documentDeleteTarget.value = null;
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

function numberOrNull(value: unknown) {
  if (value == null || value === '') return null;
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  const text = String(value).trim();
  if (!text) return null;
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : null;
}

function textOrNull(value: unknown) {
  const text = value == null ? '' : String(value).trim();
  return text || null;
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
      'X-Upload-Path': encodeURIComponent(uploadUrl),
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
    <RagflowListPanel
      v-if="!inDetail"
      v-model:keyword="keyword"
      :columns="columns"
      :global-setting="globalSetting"
      :items="items"
      :llm-models="llmModels"
      :loading="loading"
      @create="openCreate"
      @load="loadList"
      @delete-space="deleteSpace"
      @open-global-settings="openGlobalSettings"
      @open-settings="openSettings"
      @open-space="openSpace"
    />

    <RagflowDetailPanel
      v-else
      v-model:search-query="searchQuery"
      :current-title="currentTitle"
      :deleting-id="deletingId"
      :deleting-space="deletingSpace"
      :doc-loading="docLoading"
      :document-columns="documentColumns"
      :documents="documents"
      :reingesting-id="reingestingId"
      :search-loading="searchLoading"
      :search-result="searchResult"
      :selected="selected"
      @back="backToList"
      @delete="deleteDocument"
      @delete-space="deleteSpace"
      @download="downloadDocument"
      @load-documents="loadDocuments"
      @open-settings="openSettings"
      @open-upload="openUploadDialog"
      @reingest="reingestDocument"
      @search="runSearchTest"
    />

    <RagflowCreateDialog
      v-model:visible="createVisible"
      :embedding-model-options="embeddingModelOptions"
      :form="createForm"
      :rerank-model-options="rerankModelOptions"
      :score-threshold-options="scoreThresholdOptions"
      @confirm="handleCreate"
    />

    <RagflowGlobalSettingsDialog
      v-model:visible="globalSettingsVisible"
      :embedding-model-options="embeddingModelOptions"
      :form="globalForm"
      :ocr-model-options="ocrModelOptions"
      :rerank-model-options="rerankModelOptions"
      :score-threshold-options="scoreThresholdOptions"
      @confirm="saveGlobalSettings"
    />

    <RagflowSettingsDialog
      v-model:visible="settingsVisible"
      :acl-columns="aclColumns"
      :acl-form="aclForm"
      :acl-items="aclItems"
      :acl-loading="aclLoading"
      :acl-subject-name="aclSubjectName"
      :acl-subject-options="aclSubjectOptions"
      :embedding-model-options="embeddingModelOptions"
      :form="form"
      :rerank-model-options="rerankModelOptions"
      :saving="saving"
      :score-threshold-options="scoreThresholdOptions"
      :settings-space="settingsSpace"
      @add-acl="addAcl"
      @confirm="handleUpdate"
      @remove-acl="removeAcl"
      @save-acl="saveAcl"
    />

    <RagflowUploadDialog
      v-model:upload-drag-active="uploadDragActive"
      v-model:visible="uploadVisible"
      :items="uploadQueue"
      :uploading="uploading"
      @clear="clearUploadQueue"
      @close="closeUploadDialog"
      @file-change="setUploadFiles"
      @remove-file="removeUploadFile"
      @submit="uploadDocuments"
    />

    <RagflowDocumentDeleteDialog
      v-model:visible="documentDeleteVisible"
      :target="documentDeleteTarget"
      @confirm="confirmDeleteDocument"
      @cancel="documentDeleteTarget = null"
    />

    <RagflowSpaceDeleteDialog
      v-model:visible="spaceDeleteVisible"
      :target="spaceDeleteTarget"
      @confirm="confirmDeleteSpace"
      @cancel="spaceDeleteTarget = null"
    />
  </main>
</template>
