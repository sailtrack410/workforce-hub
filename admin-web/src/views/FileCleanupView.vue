<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ArchiveRestore, Database, FileSearch, FileStack, HardDrive, Paperclip, Play, RefreshCcw, Trash2 } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { AdminFileResponse, CleanupJobRunResponse, StorageSummaryResponse } from '@/api/types';
import { cleanupJobTypeLabel, errorMessage, formatBytes, formatDateTime, safeBusinessMessage, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminDialog from '@/components/AdminDialog.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';
import AdminPagination from '@/components/AdminPagination.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';

const activeTab = ref<'catalog' | 'storage'>('catalog');
const cleanupForm = reactive({ jobType: 'ALL_EXPIRED', dryRun: true });
const fileFilters = reactive({ keyword: '', ownerType: '', uploaderType: '', scanStatus: '', lifecycleStatus: 'ACTIVE' });
const files = ref<AdminFileResponse[]>([]);
const filePageNo = ref(1);
const filePageSize = ref(20);
const fileTotal = ref(0);
const filesLoading = ref(false);
const summary = ref<StorageSummaryResponse | null>(null);
const result = ref<CleanupJobRunResponse | null>(null);
const toast = useToast();
const loading = ref(false);
const running = ref(false);
const setupVisible = ref(false);
const confirmVisible = ref(false);

const fileColumns: TableColumn[] = [
  { key: 'displayName', label: '文件', width: '30%' },
  { key: 'uploaderName', label: '所属用户' },
  { key: 'ownerType', label: '业务来源' },
  { key: 'fileSize', label: '大小', sortable: true },
  { key: 'scanStatus', label: '检测状态' },
  { key: 'createdAt', label: '上传时间', sortable: true },
];

const quotaRatio = computed(() => ratio(summary.value?.orgPoolUsedBytes, summary.value?.orgPoolQuotaBytes));
const cacheRatio = computed(() => ratio(summary.value?.cacheUsedBytes, summary.value?.cacheQuotaBytes));
const storageState = computed(() => {
  const data = summary.value;
  if (!data) return { label: '读取中', tone: 'neutral' };
  if (data.backendWritable === false || quotaRatio.value >= thresholdPercent(data.orgPoolBlockThreshold, 100)) {
    return { label: '写入受限', tone: 'danger' };
  }
  if (quotaRatio.value >= thresholdPercent(data.orgPoolWarningThreshold, 80)) {
    return { label: '接近阈值', tone: 'warning' };
  }
  return { label: '运行正常', tone: 'success' };
});
const candidateCount = computed(() => {
  const data = result.value;
  if (!data) return 0;
  return Number(data.expiredUploadTickets ?? 0)
    + Number(data.expiredDownloadTickets ?? 0)
    + Number(data.purgedAttachmentRefs ?? 0)
    + Number(data.deletedPhysicalObjects ?? 0);
});
const resultTone = computed(() => {
  if (!result.value) return 'neutral';
  if (Number(result.value.failedPhysicalObjects ?? 0) > 0) return 'warning';
  return result.value.jobStatus === 'FAILED' ? 'danger' : 'success';
});
const confirmationMessage = computed(() => {
  if (cleanupForm.dryRun) return '系统只统计符合条件的内容，不会删除文件。';
  const impact = result.value?.dryRun && result.value.jobType === cleanupForm.jobType
    ? `最近一次模拟发现 ${candidateCount.value} 项候选内容，预计释放 ${formatBytes(result.value.releasedBytes)}。`
    : '';
  return `${impact} 正式清理不可撤销，正在使用中的文件不受影响。`;
});

onMounted(() => {
  void loadFiles();
  void loadSummary();
});
watch([filePageNo, filePageSize], () => { void loadFiles(); });

async function loadFiles(resetPage = false) {
  if (resetPage && filePageNo.value !== 1) {
    filePageNo.value = 1;
    return;
  }
  filesLoading.value = true;
  try {
    const page = await adminApi.files.list({
      pageNo: filePageNo.value,
      pageSize: filePageSize.value,
      keyword: fileFilters.keyword.trim() || undefined,
      ownerType: fileFilters.ownerType || undefined,
      uploaderType: fileFilters.uploaderType || undefined,
      scanStatus: fileFilters.scanStatus || undefined,
      lifecycleStatus: fileFilters.lifecycleStatus,
    });
    files.value = page.list;
    fileTotal.value = page.total;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    filesLoading.value = false;
  }
}

function resetFileFilters() {
  Object.assign(fileFilters, { keyword: '', ownerType: '', uploaderType: '', scanStatus: '', lifecycleStatus: 'ACTIVE' });
  void loadFiles(true);
}

function ownerTypeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    PERSONAL_FILE: '个人文件',
    WF_REQUEST: '审批附件',
    IM_MESSAGE: '消息附件',
    IM_GROUP_FILE: '群文件',
    ADMIN_FILE: '后台文件',
    RAGFLOW_DOCUMENT: '知识库文件',
    USER_AVATAR: '用户头像',
  };
  return value ? labels[value] ?? value : '-';
}

async function loadSummary() {
  loading.value = true;
  try {
    summary.value = await adminApi.files.storageSummary();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function ratio(used?: number | null, quota?: number | null) {
  if (!quota) return 0;
  return Math.min(100, Math.round((Number(used ?? 0) / Number(quota)) * 100));
}

function thresholdPercent(value: number | string | null | undefined, fallback: number) {
  const threshold = Number(value);
  if (!Number.isFinite(threshold)) return fallback;
  return threshold <= 1 ? threshold * 100 : threshold;
}

function requestRun() {
  setupVisible.value = false;
  confirmVisible.value = true;
}

function openCleanupSetup() {
  cleanupForm.dryRun = true;
  setupVisible.value = true;
}

function requestFormalRun() {
  cleanupForm.dryRun = false;
  confirmVisible.value = true;
}

async function runCleanupJob() {
  running.value = true;
  try {
    result.value = await adminApi.files.runCleanupJob({ ...cleanupForm });
    confirmVisible.value = false;
    toast.success(cleanupForm.dryRun ? '模拟完成，未删除任何文件' : '清理任务执行完成');
    await loadSummary();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    running.value = false;
  }
}
</script>

<template>
  <main class="page-grid">
    <nav class="file-tabs span-12" role="tablist" aria-label="文件空间功能">
      <button :class="{ active: activeTab === 'catalog' }" type="button" role="tab" :aria-selected="activeTab === 'catalog'" @click="activeTab = 'catalog'">文件目录</button>
      <button :class="{ active: activeTab === 'storage' }" type="button" role="tab" :aria-selected="activeTab === 'storage'" @click="activeTab = 'storage'">空间与清理</button>
    </nav>

    <AdminPanel v-if="activeTab === 'catalog'" :span="12" title="企业文件">
      <template #actions>
        <div class="catalog-actions">
          <span>共 {{ fileTotal }} 个附件引用</span>
          <button class="icon-button" type="button" title="刷新文件目录" aria-label="刷新文件目录" :disabled="filesLoading" @click="loadFiles()"><RefreshCcw :size="16" /></button>
        </div>
      </template>

      <form class="catalog-filters" @submit.prevent="loadFiles(true)">
        <input v-model="fileFilters.keyword" aria-label="文件名或员工姓名" placeholder="搜索文件名或员工" />
        <select v-model="fileFilters.ownerType" aria-label="业务来源">
          <option value="">全部来源</option><option value="PERSONAL_FILE">个人文件</option><option value="WF_REQUEST">审批附件</option><option value="IM_MESSAGE">消息附件</option><option value="IM_GROUP_FILE">群文件</option><option value="ADMIN_FILE">后台文件</option><option value="RAGFLOW_DOCUMENT">知识库文件</option><option value="USER_AVATAR">用户头像</option>
        </select>
        <select v-model="fileFilters.uploaderType" aria-label="上传主体">
          <option value="">全部上传主体</option><option value="USER">员工</option><option value="ADMIN">管理员</option><option value="AGENT">AI 助手</option>
        </select>
        <select v-model="fileFilters.scanStatus" aria-label="检测状态">
          <option value="">全部检测状态</option><option value="PENDING">检测中</option><option value="PASSED">已通过</option><option value="FAILED">未通过</option><option value="SKIPPED">已跳过</option>
        </select>
        <select v-model="fileFilters.lifecycleStatus" aria-label="文件状态">
          <option value="ACTIVE">在用文件</option><option value="TRASHED">回收站</option>
        </select>
        <button class="secondary-button filter-submit" type="submit"><FileSearch :size="16" /><span>筛选</span></button>
      </form>

      <AdminTable v-if="filesLoading || files.length" :columns="fileColumns" :rows="files" :loading="filesLoading" row-key="attachmentRefId">
        <template #cell-displayName="{ row }"><div class="file-name-cell"><FileStack :size="18" /><div><strong>{{ row.displayName || row.originalFileName || '未命名文件' }}</strong><span>{{ row.mimeType || '未知类型' }}</span></div></div></template>
        <template #cell-uploaderName="{ row }"><strong>{{ row.uploaderName || row.uploaderTypeName || '未知主体' }}</strong><span>{{ row.employeeNo || row.uploaderTypeName || row.uploaderType }}</span></template>
        <template #cell-ownerType="{ row }"><span class="status-pill">{{ row.ownerTypeName || ownerTypeLabel(row.ownerType) }}</span></template>
        <template #cell-fileSize="{ row }">{{ formatBytes(row.fileSize) }}</template>
        <template #cell-scanStatus="{ row }"><span class="status-pill">{{ row.scanStatusName || statusLabel(row.scanStatus) }}</span></template>
        <template #cell-createdAt="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </AdminTable>
      <div v-else class="catalog-empty">
        <FileSearch :size="28" />
        <strong>{{ fileFilters.lifecycleStatus === 'TRASHED' ? '回收站中没有匹配文件' : '没有找到匹配文件' }}</strong>
        <span>清除筛选条件后查看全部企业文件</span>
        <button class="secondary-button" type="button" @click="resetFileFilters">清除筛选</button>
      </div>
      <AdminPagination v-model:page-no="filePageNo" v-model:page-size="filePageSize" :total="fileTotal" />
    </AdminPanel>

    <template v-else>
      <AdminPanel :span="12" title="空间状态" subtitle="查看文件空间健康度与可清理内容">
        <template #actions>
          <button class="secondary-button" :disabled="loading" @click="loadSummary"><RefreshCcw :size="16" /><span>{{ loading ? '刷新中' : '刷新' }}</span></button>
        </template>
        <div v-if="summary" class="space-overview">
          <div class="health-card">
            <div class="health-heading"><span>长期池使用率</span><strong>{{ quotaRatio }}%</strong></div>
            <div class="usage-track" role="progressbar" aria-label="长期池使用率" :aria-valuenow="quotaRatio" aria-valuemin="0" aria-valuemax="100"><span :class="`tone-${storageState.tone}`" :style="{ width: `${quotaRatio}%` }" /></div>
            <div class="health-meta"><span>{{ formatBytes(summary.orgPoolUsedBytes) }} / {{ formatBytes(summary.orgPoolQuotaBytes) }}</span><strong :class="['state-label', `tone-${storageState.tone}`]">{{ storageState.label }}</strong></div>
            <p v-if="summary.backendWritable === false" class="write-warning">{{ summary.writeProtectReason || '存储后端当前不可写，请检查服务状态。' }}</p>
          </div>
          <div class="capacity-grid">
            <article><Database :size="20" /><span>缓存池</span><strong>{{ formatBytes(summary.cacheUsedBytes) }} / {{ formatBytes(summary.cacheQuotaBytes) }}</strong><small>已使用 {{ cacheRatio }}%</small></article>
            <article><HardDrive :size="20" /><span>后端可用空间</span><strong>{{ formatBytes(summary.backendFreeBytes) }}</strong><small>{{ summary.backendWritable === false ? '当前不可写' : '存储后端可写' }}</small></article>
          </div>
        </div>
        <div v-else class="empty-state">{{ loading ? '正在读取空间状态…' : '暂时无法获取空间状态' }}</div>
      </AdminPanel>

      <AdminPanel :span="12" title="文件与待处理对象">
        <template #actions><button class="primary-button" :disabled="running" @click="openCleanupSetup"><Play :size="16" /><span>运行清理</span></button></template>
        <div v-if="summary" class="object-grid">
          <article><FileStack :size="20" /><span>文件对象</span><strong>{{ summary.fileObjectCount ?? 0 }}</strong></article>
          <article><Paperclip :size="20" /><span>附件引用</span><strong>{{ summary.attachmentRefCount ?? 0 }}</strong></article>
          <article><ArchiveRestore :size="20" /><span>回收站</span><strong>{{ summary.recycleBinCount ?? 0 }}</strong></article>
          <article class="attention"><Trash2 :size="20" /><span>待清理对象</span><strong>{{ summary.pendingCleanupObjectCount ?? 0 }}</strong></article>
        </div>
      </AdminPanel>

      <AdminPanel v-if="result" :span="12" title="最近一次执行结果">
        <div class="result-header">
          <div><span :class="['state-label', `tone-${resultTone}`]">{{ statusLabel(result.jobStatus) }}</span><strong>{{ cleanupJobTypeLabel(result.jobType) }}</strong><span>{{ result.dryRun ? '模拟清理' : '正式清理' }}</span></div>
          <button v-if="result.dryRun" class="danger-button" :disabled="running" @click="requestFormalRun">按此条件正式清理</button>
        </div>
        <div class="result-grid">
          <article><span>过期票据</span><strong>{{ Number(result.expiredUploadTickets ?? 0) + Number(result.expiredDownloadTickets ?? 0) }}</strong></article>
          <article><span>附件引用</span><strong>{{ result.purgedAttachmentRefs ?? 0 }}</strong></article>
          <article><span>物理对象</span><strong>{{ result.deletedPhysicalObjects ?? 0 }}</strong></article>
          <article><span>{{ result.dryRun ? '预计释放' : '已释放' }}</span><strong>{{ formatBytes(result.releasedBytes) }}</strong></article>
          <article v-if="Number(result.failedPhysicalObjects ?? 0) > 0" class="attention"><span>处理失败</span><strong>{{ result.failedPhysicalObjects }}</strong></article>
        </div>
        <p v-if="result.errorMessage" class="result-error">{{ safeBusinessMessage(result.errorMessage) }}</p>
        <p v-if="Number(result.failedPhysicalObjects ?? 0) > 0" class="result-hint">存在失败对象，可选择“失败物理对象重试”再次运行。</p>
      </AdminPanel>
      <p v-else class="no-result span-12">尚未运行清理任务。建议先模拟，确认影响范围后再正式执行。</p>

      <AdminDialog v-model:visible="setupVisible" title="清理文件" width="min(560px, 92vw)" :close-disabled="running">
        <form class="cleanup-form" @submit.prevent="requestRun">
          <label><span>任务类型</span><select v-model="cleanupForm.jobType" autofocus><option value="ALL_EXPIRED">全部到期清理</option><option value="EXPIRED_UPLOAD_TICKET">过期上传票据</option><option value="EXPIRED_DOWNLOAD_TICKET">过期下载票据</option><option value="EXPIRED_RECYCLE_REF">到期回收站引用</option><option value="UNREFERENCED_FILE_OBJECT">无引用物理对象</option><option value="FAILED_PHYSICAL_OBJECT">失败物理对象重试</option></select></label>
          <fieldset>
            <legend>执行方式</legend>
            <label :class="['mode-option', { selected: cleanupForm.dryRun }]"><input v-model="cleanupForm.dryRun" type="radio" :value="true" /><span><strong>模拟清理</strong><small>预览影响，不删除文件</small></span></label>
            <label :class="['mode-option', 'danger-option', { selected: !cleanupForm.dryRun }]"><input v-model="cleanupForm.dryRun" type="radio" :value="false" /><span><strong>正式清理</strong><small>立即执行，不可撤销</small></span></label>
          </fieldset>
          <div class="dialog-actions"><button class="secondary-button" type="button" @click="setupVisible = false">取消</button><button :class="cleanupForm.dryRun ? 'primary-button' : 'danger-button'" type="submit">下一步</button></div>
        </form>
      </AdminDialog>
      <AdminConfirmDialog v-model:visible="confirmVisible" :title="cleanupForm.dryRun ? '确认开始模拟' : '确认正式清理'" :message="confirmationMessage" :confirm-label="running ? '执行中…' : cleanupForm.dryRun ? '开始模拟' : '正式清理'" :danger="!cleanupForm.dryRun" :close-on-confirm="false" :confirm-disabled="running" :cancel-disabled="running" @confirm="runCleanupJob" />
    </template>
  </main>
</template>

<style scoped>
.file-tabs { display: flex; align-items: center; gap: var(--space-xl); min-height: 44px; border-bottom: 1px solid var(--line); }
.file-tabs button { align-self: stretch; min-height: 44px; padding: 0 var(--space-xs); border: 0; border-bottom: 2px solid transparent; background: transparent; color: var(--muted); font-weight: 700; }
.file-tabs button:hover { color: var(--green-dark); }
.file-tabs button.active { border-bottom-color: var(--green); color: var(--green-dark); }
.catalog-actions { display: flex; align-items: center; gap: var(--space-sm); color: var(--muted); font-size: var(--text-sm); }
.catalog-filters { display: grid; grid-template-columns: minmax(220px, 1.6fr) repeat(4, minmax(130px, 1fr)) auto; gap: var(--space-sm); margin-bottom: var(--space-base); }
.filter-submit { min-width: 88px; }
.file-name-cell { display: flex; align-items: center; gap: var(--space-sm); min-width: 220px; }
.file-name-cell > svg { flex: 0 0 auto; color: var(--green-dark); }
.catalog-empty { min-height: 220px; display: grid; place-items: center; align-content: center; gap: var(--space-sm); border: 1px dashed var(--line); border-radius: var(--radius-md); color: var(--muted); text-align: center; }
.catalog-empty svg { color: var(--muted-light); }
.catalog-empty strong { color: var(--ink); }
.catalog-empty span { font-size: var(--text-sm); }
.catalog-empty button { margin-top: var(--space-xs); }
.space-overview { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(300px, 1fr); gap: var(--space-lg); }
.health-card, .capacity-grid article, .object-grid article, .result-grid article { border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--panel-strong); }
.health-card { display: grid; gap: var(--space-md); padding: var(--space-lg); }
.health-heading, .health-meta, .result-header, .result-header > div { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); }
.health-heading span, .health-meta span, .capacity-grid span, .capacity-grid small, .object-grid span, .result-grid span, .result-header span { color: var(--muted); }
.health-heading strong { font-size: 28px; }
.usage-track { height: 10px; overflow: hidden; border-radius: var(--radius-pill); background: var(--ring-bg); }
.usage-track span { display: block; height: 100%; min-width: 2px; border-radius: inherit; background: var(--green); }
.capacity-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-md); }
.capacity-grid article { display: grid; gap: var(--space-sm); padding: var(--space-lg); }
.capacity-grid svg, .object-grid svg { color: var(--green-dark); }
.capacity-grid strong { font-size: var(--text-lg); }
.capacity-grid small { color: var(--muted); font-size: var(--text-xs); }
.state-label { width: fit-content; padding: 4px 9px; border-radius: var(--radius-pill); background: var(--primary-soft); color: var(--green-dark); font-size: var(--text-xs); }
.state-label.tone-success { background: var(--success-bg); color: var(--success); }
.state-label.tone-warning, .state-label.tone-danger { background: var(--error-bg); color: var(--red); }
.usage-track .tone-warning, .usage-track .tone-danger { background: var(--red); }
.write-warning, .result-error, .result-hint { margin: 0; color: var(--red); font-size: var(--text-sm); line-height: 1.5; }
.object-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-md); }
.object-grid article { display: grid; grid-template-columns: auto 1fr; align-items: center; gap: var(--space-sm) var(--space-md); padding: var(--space-lg); }
.object-grid strong { grid-column: 2; font-size: 24px; }
.object-grid article.attention, .result-grid article.attention { border-color: var(--error-border); background: var(--error-bg); }
.object-grid article.attention svg { color: var(--red); }
.result-header { margin-bottom: var(--space-lg); }
.result-header > div { justify-content: flex-start; }
.result-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: var(--space-md); }
.result-grid article { display: grid; gap: var(--space-xs); padding: var(--space-md); }
.result-grid strong { font-size: var(--text-lg); }
.result-error, .result-hint { margin-top: var(--space-md); }
.no-result { margin: 0; color: var(--muted); font-size: var(--text-sm); }
.cleanup-form { display: grid; gap: var(--space-lg); }
.cleanup-form > label { display: grid; gap: var(--space-sm); }
.cleanup-form fieldset { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-sm); margin: 0; padding: 0; border: 0; }
.cleanup-form legend { grid-column: 1 / -1; margin-bottom: var(--space-xs); font-weight: 700; }
.mode-option { display: grid; grid-template-columns: 18px minmax(0, 1fr); align-items: start; gap: var(--space-sm); min-height: 78px; padding: var(--space-md); border: 1px solid var(--line); border-radius: var(--radius-md); cursor: pointer; transition: border-color 200ms ease, background 200ms ease; }
.mode-option:hover { border-color: var(--green-light); }
.mode-option.selected { border-color: var(--green); background: var(--primary-soft); }
.mode-option input { width: 18px; height: 18px; min-height: 18px; margin: 1px 0 0; padding: 0; box-shadow: none; }
.mode-option span { display: grid; gap: var(--space-xs); }
.mode-option small { color: var(--muted); }
.danger-option strong { color: var(--red); }
.danger-option.selected { border-color: var(--red); background: var(--error-bg); }
.dialog-actions { display: flex; justify-content: flex-end; gap: var(--space-sm); }

@media (max-width: 1200px) {
  .catalog-filters { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .catalog-filters > :first-child { grid-column: span 2; }
}
@media (max-width: 980px) {
  .catalog-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .catalog-filters > :first-child { grid-column: 1 / -1; }
  .space-overview, .capacity-grid { grid-template-columns: 1fr; }
  .object-grid, .result-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .catalog-filters, .cleanup-form fieldset, .object-grid, .result-grid { grid-template-columns: 1fr; }
  .catalog-filters > :first-child { grid-column: auto; }
  .result-header, .result-header > div { align-items: flex-start; flex-direction: column; }
}
</style>
