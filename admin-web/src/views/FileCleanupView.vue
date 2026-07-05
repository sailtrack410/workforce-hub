<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Play, RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { CleanupJobRunResponse, StorageSummaryResponse } from '@/api/types';
import { errorMessage, formatBytes, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const cleanupForm = reactive({ jobType: 'ALL_EXPIRED', dryRun: true });

const summary = ref<StorageSummaryResponse | null>(null);
const result = ref<CleanupJobRunResponse | null>(null);
const toast = useToast();
const loading = ref(false);
const running = ref(false);
const confirmVisible = ref(false);

const quotaRatio = computed(() => {
  const used = summary.value?.orgPoolUsedBytes ?? 0;
  const quota = summary.value?.orgPoolQuotaBytes ?? 0;
  if (!quota) return 0;
  return Math.min(100, Math.round((used / quota) * 100));
});

const storageRows = computed<DetailRow[]>(() => {
  const s = summary.value;
  if (!s) return [];
  return [
    { label: '文件对象', value: String(s.fileObjectCount ?? 0) },
    { label: '附件引用', value: String(s.attachmentRefCount ?? 0) },
    { label: '回收站', value: String(s.recycleBinCount ?? 0) },
    { label: '待清理对象', value: String(s.pendingCleanupObjectCount ?? 0) },
    { label: '写入状态', value: s.backendWritable === false ? '受限' : '可写' },
    { label: '受限原因', value: s.writeProtectReason || '-' },
  ];
});

const resultRows = computed<DetailRow[]>(() => {
  const r = result.value;
  if (!r) return [];
  return [
    { label: '任务', value: r.jobRunId },
    { label: '类型', value: r.jobType },
    { label: '模式', value: r.dryRun ? '模拟清理' : '正式清理' },
    { label: '状态', value: statusLabel(r.jobStatus) },
    { label: '过期上传票据', value: String(r.expiredUploadTickets ?? 0) },
    { label: '过期下载票据', value: String(r.expiredDownloadTickets ?? 0) },
    { label: '清理附件引用', value: String(r.purgedAttachmentRefs ?? 0) },
    { label: '删除物理对象', value: String(r.deletedPhysicalObjects ?? 0) },
    { label: '失败物理对象', value: String(r.failedPhysicalObjects ?? 0) },
    { label: '释放空间', value: formatBytes(r.releasedBytes) },
  ];
});

onMounted(() => { void loadSummary(); });

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

async function runCleanupJob() {
  running.value = true;

  result.value = null;
  try {
    result.value = await adminApi.files.runCleanupJob({
      jobType: cleanupForm.jobType,
      dryRun: cleanupForm.dryRun,
    });
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
    <AdminPanel :span="12" title="存储摘要">
      <template #actions>
        <button class="secondary-button" :disabled="loading" @click="loadSummary">
          <RefreshCcw :size="16" />
          <span>刷新</span>
        </button>
      </template>
    </AdminPanel>

    <AdminPanel :span="7" title="存储详情">
      <div v-if="summary" class="storage-summary">
        <div class="usage-ring">
          <strong>{{ quotaRatio }}%</strong>
          <span>长期池</span>
        </div>
        <div class="metric-strip">
          <div class="metric-card">
            <span>长期池使用</span>
            <strong>{{ formatBytes(summary.orgPoolUsedBytes) }}</strong>
          </div>
          <div class="metric-card">
            <span>长期池配额</span>
            <strong>{{ formatBytes(summary.orgPoolQuotaBytes) }}</strong>
          </div>
          <div class="metric-card">
            <span>缓存池使用</span>
            <strong>{{ formatBytes(summary.cacheUsedBytes) }}</strong>
          </div>
          <div class="metric-card">
            <span>后端可用</span>
            <strong>{{ formatBytes(summary.backendFreeBytes) }}</strong>
          </div>
        </div>
        <AdminDetailList :rows="storageRows" />
      </div>
      <div v-else class="empty-state">无数据</div>
    </AdminPanel>

    <AdminPanel :span="5" title="清理任务">
      <form class="stack-form" @submit.prevent="confirmVisible = true">
        <label>
          <span>任务类型</span>
          <select v-model="cleanupForm.jobType">
            <option value="ALL_EXPIRED">全部到期清理</option>
            <option value="EXPIRED_UPLOAD_TICKET">过期上传票据</option>
            <option value="EXPIRED_DOWNLOAD_TICKET">过期下载票据</option>
            <option value="EXPIRED_RECYCLE_REF">到期回收站引用</option>
            <option value="UNREFERENCED_FILE_OBJECT">无引用物理对象</option>
            <option value="FAILED_PHYSICAL_OBJECT">失败物理对象重试</option>
          </select>
        </label>
        <label class="check-row">
          <input v-model="cleanupForm.dryRun" type="checkbox" />
          <span>先模拟，不真正删除</span>
        </label>
        <button :class="cleanupForm.dryRun ? 'primary-button' : 'danger-button'" :disabled="running" type="submit">
          <Play :size="16" />
          <span>{{ cleanupForm.dryRun ? '开始模拟' : '正式清理' }}</span>
        </button>
      </form>
    </AdminPanel>

    <AdminPanel :span="12" title="执行结果">
      <AdminDetailList v-if="result" :rows="resultRows" :error="result.errorMessage ?? ''" />
      <div v-else class="empty-state">无数据</div>
    </AdminPanel>

    <AdminConfirmDialog
      v-model:visible="confirmVisible"
      :title="cleanupForm.dryRun ? '确认开始模拟' : '确认正式清理'"
      :message="cleanupForm.dryRun ? '系统只会计算可清理内容，不会删除文件。' : '将删除符合条件的文件，此操作不可撤销。'"
      :confirm-label="cleanupForm.dryRun ? '开始模拟' : '正式清理'"
      :danger="!cleanupForm.dryRun"
      @confirm="runCleanupJob"
    />
  </main>
</template>

<style scoped>
.storage-summary {
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: var(--space-xl);
  align-items: center;
}

.usage-ring {
  width: 138px;
  height: 138px;
  display: grid;
  place-items: center;
  align-content: center;
  border: 12px solid var(--ring-bg);
  border-radius: var(--radius-circle);
  background: var(--panel-strong);
  box-shadow: var(--shadow-sm);
}

.usage-ring strong { color: var(--green-dark); font-size: 28px; }
.usage-ring span { color: var(--muted); font-size: var(--text-xs); }

.metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: var(--space-md);
  width: 100%;
}

.metric-card {
  display: grid;
  gap: var(--space-xs);
  min-height: 76px;
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  box-shadow: var(--shadow-sm);
}

.metric-card span {
  color: var(--muted);
  font-size: var(--text-xs);
}

.metric-card strong { font-size: var(--text-lg); }

@media (max-width: 980px) {
  .storage-summary,
  .metric-strip { grid-template-columns: 1fr; }
}
</style>
