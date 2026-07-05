<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  ChevronLeft,
  Copy,
  Download,
  Eye,
  Folder,
  FolderPlus,
  Home,
  MoveRight,
  RotateCcw,
  Search,
  Send,
  Star,
  Trash2,
  X,
} from 'lucide-vue-next';
import { employeeApi, type EmployeeFileFolderResponse, type EmployeeFileResponse, type EntityId } from '@/api';
import { useFilesSectionContext } from '../employee-shell-context';
import { fileIconOf, fileToneOf, isFileTrashed } from '@/utils/file-record';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { formatDateTime, statusText } from '@/utils/employee-format';
import type { PersonalFileRecord } from '@/stores/shell';
import cloudFolderImage from '@/assets/cloud-folder.png';
import AppButton from '../../components/ui/AppButton.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppLoadingState from '../../components/ui/AppLoadingState.vue';

const {
  im,
  fileUploading,
  activeFile,
  activeFileUsage,
  currentMeta,
  backToList,
  runAction,
  showToast,
  setUploadFolderId,
  refreshFileCenter,
  sendSelectedFileToConversation,
  openPersonalFilePreview,
  downloadPersonalFile,
  trashPersonalFile,
  restorePersonalFile,
  purgePersonalFile,
} = useFilesSectionContext();

type FileTab = 'all' | 'trash';
type FilePlacementMode = 'move' | 'copy';

const fileTab = ref<FileTab>('all');
const currentFolderId = ref<EntityId | null>(null);
const browserLoading = ref(false);
const browserError = ref('');
const browserFolders = ref<EmployeeFileFolderResponse[]>([]);
const browserFiles = ref<PersonalFileRecord[]>([]);
const breadcrumbs = ref<EmployeeFileFolderResponse[]>([]);
const allFolders = ref<EmployeeFileFolderResponse[]>([]);
const fileKeyword = ref('');
const fileTypeFilter = ref('all');
const folderNameDraft = ref('');
const folderActionError = ref('');
const placementTarget = ref<{ mode: FilePlacementMode; file: PersonalFileRecord } | null>(null);
const placementFolderId = ref('root');
const copyNameDraft = ref('');
const accessStorageKey = 'workforce-hub.employee.file-access.v1';
const favoriteStorageKey = 'workforce-hub.employee.file-favorites.v1';
const recentAccessLog = ref<Record<string, number>>(readRecentAccessLog());
const favoriteFileIds = ref<string[]>(readFavoriteFileIds());
let searchTimer: number | undefined;

const activeFiles = computed(() => activeFile.value.files.filter((file: PersonalFileRecord) => !isFileTrashed(file)));
const trashedFiles = computed(() => activeFile.value.files.filter((file: PersonalFileRecord) => isFileTrashed(file)));
const visibleTrashFiles = computed(() => trashedFiles.value);
const emptyTitle = computed(() => fileTab.value === 'trash' ? '回收站为空' : '暂无文件');
const emptyDescription = computed(() => fileTab.value === 'trash' ? '删除的文件会暂存在这里' : '上传或新建文件夹后开始管理');
const placementTitle = computed(() => placementTarget.value?.mode === 'copy' ? '复制到' : '移动到');
const recentUploadCount = computed(() => activeFiles.value.filter((file) => isWithinLastWeek(file.rawCreatedAt ?? file.createdAt)).length);
const recentAccessCount = computed(() => activeFiles.value.filter((file) => (recentAccessLog.value[fileIdentity(file)] ?? 0) >= weekAgoTime()).length);
const favoriteFileCount = computed(() => activeFiles.value.filter((file) => favoriteFileIds.value.includes(fileIdentity(file))).length);
const fileQuickCards = computed(() => [
  { title: '最近访问', count: recentAccessCount.value },
  { title: '最近上传', count: recentUploadCount.value },
  { title: '收藏夹', count: favoriteFileCount.value },
]);
const storageUsedLabel = computed(() => formatStorageLabel(activeFile.value.summary[0]?.[0] ?? '0B'));
const storageTotalLabel = computed(() => formatStorageLabel(activeFile.value.summary[1]?.[0] ?? '0B'));
const storageSegments = computed(() => {
  const totals = activeFiles.value.reduce((next, file) => {
    const key = storageTypeOf(file);
    next[key] += file.fileSizeBytes ?? parseSizeBytes(file.size);
    return next;
  }, {
    document: 0,
    image: 0,
    video: 0,
    other: 0,
  });
  const total = Math.max(0, Object.values(totals).reduce((sum, value) => sum + value, 0));
  return [
    { key: 'document', label: '文档', color: '#ef4444', value: totals.document },
    { key: 'image', label: '图像', color: '#2f80ed', value: totals.image },
    { key: 'video', label: '视频', color: '#f4c20d', value: totals.video },
    { key: 'other', label: '其他', color: '#22c55e', value: totals.other },
  ].map((item) => ({
    ...item,
    percent: total > 0 ? (item.value / total) * 100 : 0,
  }));
});

function mapBrowserFile(file: EmployeeFileResponse): PersonalFileRecord {
  const name = file.displayName || file.originalFileName || '未命名文件';
  return {
    id: `personal-file-${file.attachmentRefId}`,
    name,
    type: fileTypeLabelFromName(name, file.mimeType ?? ''),
    size: fileSizeLabel(file.fileSize),
    createdAt: formatDateTime(file.createdAt),
    rawCreatedAt: file.createdAt ?? undefined,
    fileSizeBytes: file.fileSize,
    status: file.deletedAt ? '回收站' : statusText(file.scanStatus),
    attachmentRefId: file.attachmentRefId,
    folderId: file.folderId ?? null,
    mimeType: file.mimeType ?? undefined,
  };
}

function weekAgoTime() {
  return Date.now() - 7 * 24 * 60 * 60 * 1000;
}

function isWithinLastWeek(value?: string | null) {
  if (!value) return false;
  if (value === '刚刚') return true;
  const time = new Date(value).getTime();
  return Number.isFinite(time) && time >= weekAgoTime();
}

function readRecentAccessLog() {
  try {
    return JSON.parse(globalThis.localStorage?.getItem(accessStorageKey) || '{}') as Record<string, number>;
  } catch {
    return {};
  }
}

function readFavoriteFileIds() {
  try {
    const value = JSON.parse(globalThis.localStorage?.getItem(favoriteStorageKey) || '[]') as unknown;
    return Array.isArray(value) ? value.map(String) : [];
  } catch {
    return [];
  }
}

function fileIdentity(file: PersonalFileRecord) {
  return String(file.attachmentRefId ?? file.id);
}

function recordFileAccess(file: PersonalFileRecord) {
  const next = { ...recentAccessLog.value, [fileIdentity(file)]: Date.now() };
  recentAccessLog.value = next;
  globalThis.localStorage?.setItem(accessStorageKey, JSON.stringify(next));
}

function toggleFavorite(file: PersonalFileRecord) {
  const id = fileIdentity(file);
  const next = favoriteFileIds.value.includes(id)
    ? favoriteFileIds.value.filter((item) => item !== id)
    : [...favoriteFileIds.value, id];
  favoriteFileIds.value = next;
  globalThis.localStorage?.setItem(favoriteStorageKey, JSON.stringify(next));
}

function isFavorite(file: PersonalFileRecord) {
  return favoriteFileIds.value.includes(fileIdentity(file));
}

function parseSizeBytes(value?: string) {
  const match = String(value ?? '').trim().match(/^([\d.]+)\s*(B|KB|MB|GB|TB)$/i);
  if (!match) return 0;
  const size = Number(match[1]);
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const unitIndex = units.indexOf(match[2].toUpperCase());
  return Number.isFinite(size) && unitIndex >= 0 ? size * (1024 ** unitIndex) : 0;
}

function formatStorageLabel(value: string) {
  return value.replace(/^([\d.]+)([A-Z]+)$/i, '$1 $2');
}

function storageTypeOf(file: PersonalFileRecord): 'document' | 'image' | 'video' | 'other' {
  const mime = file.mimeType?.toLowerCase() ?? '';
  const type = file.type.toLowerCase();
  const name = file.name.toLowerCase();
  if (mime.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].some((ext) => type.includes(ext) || name.endsWith(`.${ext}`))) return 'image';
  if (mime.startsWith('video/') || ['mp4', 'mov', 'avi', 'mkv', 'webm'].some((ext) => type.includes(ext) || name.endsWith(`.${ext}`))) return 'video';
  if (
    mime.includes('pdf')
    || mime.includes('document')
    || mime.includes('spreadsheet')
    || mime.includes('presentation')
    || ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv'].some((ext) => type.includes(ext) || name.endsWith(`.${ext}`))
  ) return 'document';
  return 'other';
}

function folderLabel(folder: EmployeeFileFolderResponse) {
  const parent = allFolders.value.find((item) => String(item.folderId) === String(folder.parentFolderId));
  return parent ? `${parent.folderName} / ${folder.folderName}` : folder.folderName;
}

async function loadBrowser() {
  if (fileTab.value !== 'all') return;
  browserLoading.value = true;
  browserError.value = '';
  try {
    const [browser, folders] = await Promise.all([
      employeeApi.files.browser({
        folderId: currentFolderId.value ?? undefined,
        keyword: fileKeyword.value.trim() || undefined,
        fileType: fileTypeFilter.value,
      }),
      employeeApi.files.folders(),
    ]);
    browserFolders.value = browser.folders;
    browserFiles.value = browser.files.map(mapBrowserFile);
    breadcrumbs.value = browser.breadcrumbs;
    allFolders.value = folders;
  } catch (error) {
    browserError.value = error instanceof Error ? error.message : '文件加载失败';
  } finally {
    browserLoading.value = false;
  }
}

function openRootFolder() {
  currentFolderId.value = null;
}

function openFolder(folderId: EntityId) {
  currentFolderId.value = folderId;
}

async function createFolder() {
  const folderName = folderNameDraft.value.trim();
  folderActionError.value = '';
  if (!folderName) {
    folderActionError.value = '文件夹名称不能为空';
    return;
  }
  try {
    await employeeApi.files.createFolder({
      parentFolderId: currentFolderId.value,
      folderName,
    });
    folderNameDraft.value = '';
    await loadBrowser();
    showToast('文件夹已创建');
  } catch (error) {
    folderActionError.value = error instanceof Error ? error.message : '创建失败';
  }
}

function openPlacement(file: PersonalFileRecord, mode: FilePlacementMode) {
  placementTarget.value = { file, mode };
  placementFolderId.value = currentFolderId.value == null ? 'root' : String(currentFolderId.value);
  copyNameDraft.value = mode === 'copy' ? file.name : '';
}

function closePlacement() {
  placementTarget.value = null;
  placementFolderId.value = 'root';
  copyNameDraft.value = '';
  folderActionError.value = '';
}

async function submitPlacement() {
  const target = placementTarget.value;
  if (!target?.file.attachmentRefId) return;
  const targetFolderId = placementFolderId.value === 'root' ? null : placementFolderId.value;
  try {
    if (target.mode === 'copy') {
      await employeeApi.files.copy(target.file.attachmentRefId, {
        targetFolderId,
        displayName: copyNameDraft.value.trim() || undefined,
      });
      showToast('文件已复制');
    } else {
      await employeeApi.files.move(target.file.attachmentRefId, { targetFolderId });
      showToast('文件已移动');
    }
    closePlacement();
    await Promise.all([refreshFileCenter(), loadBrowser()]);
  } catch (error) {
    folderActionError.value = error instanceof Error ? error.message : '操作失败';
  }
}

async function trashBrowserFile(file: PersonalFileRecord) {
  await trashPersonalFile(file);
  await loadBrowser();
}

async function previewBrowserFile(file: PersonalFileRecord) {
  recordFileAccess(file);
  await openPersonalFilePreview(file);
}

async function downloadBrowserFile(file: PersonalFileRecord) {
  recordFileAccess(file);
  await downloadPersonalFile(file);
}

function uploadToCurrentFolder() {
  setUploadFolderId(currentFolderId.value);
  runAction(currentMeta.value.primaryAction);
}

watch([currentFolderId, fileTypeFilter, fileTab], () => {
  void loadBrowser();
});

watch(() => activeFile.value.files.length, () => {
  void loadBrowser();
});

watch(fileKeyword, () => {
  if (searchTimer) window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(() => {
    void loadBrowser();
  }, 240);
});

onMounted(() => {
  void loadBrowser();
});
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">{{ activeFile.title }}</h2>
        <div class="workspace-meta-line">
          <span>{{ activeFile.subtitle }}</span>
          <span>{{ activeFile.note }}</span>
        </div>
      </div>
    </div>
    <div class="head-actions">
      <AppButton variant="primary" size="sm" :disabled="fileUploading" @click="uploadToCurrentFolder">
        {{ fileUploading ? '上传中' : currentMeta.primaryAction?.label }}
      </AppButton>
    </div>
  </header>

  <section class="content-stack">
    <section class="cloud-summary-grid" aria-label="云空间概览">
      <div class="cloud-quick-grid">
        <article v-for="card in fileQuickCards" :key="card.title" class="cloud-folder-card">
          <img :src="cloudFolderImage" alt="" aria-hidden="true" />
          <div>
            <h3>{{ card.title }}</h3>
            <p>{{ card.count }} 个文件</p>
          </div>
        </article>
      </div>

      <article class="storage-overview-card">
        <header>储存概览</header>
        <div class="storage-overview-body">
          <div class="storage-usage-line">
            <strong>{{ storageUsedLabel }}</strong>
            <span>of {{ storageTotalLabel }}</span>
          </div>
          <div class="storage-ratio-bar" aria-hidden="true">
            <span
              v-for="item in storageSegments"
              v-show="item.percent > 0"
              :key="item.key"
              :style="{ width: `${item.percent}%`, background: item.color }"
            />
          </div>
          <div class="storage-legend">
            <span v-for="item in storageSegments" :key="item.key">
              <i :style="{ background: item.color }" />
              {{ item.label }}
            </span>
          </div>
        </div>
        <footer>已使用 {{ activeFileUsage }}%</footer>
      </article>
    </section>

    <section class="detail-card">
      <div class="title-line file-tabs">
        <div class="settings-density-row">
          <button type="button" :class="{ active: fileTab === 'all' }" @click="fileTab = 'all'">
            全部文件
            <span class="tab-count">{{ activeFiles.length }}</span>
          </button>
          <button type="button" :class="{ active: fileTab === 'trash' }" @click="fileTab = 'trash'">
            回收站
            <span class="tab-count">{{ trashedFiles.length }}</span>
          </button>
        </div>
      </div>

      <template v-if="fileTab === 'all'">
        <div class="file-browser-toolbar">
          <label class="file-search-field">
            <Search :size="16" />
            <input v-model="fileKeyword" type="search" placeholder="搜索文件或文件夹" />
          </label>
          <select v-model="fileTypeFilter" class="ui-inline-input file-type-select">
            <option value="all">全部类型</option>
            <option value="document">文档</option>
            <option value="image">图片</option>
            <option value="sheet">表格</option>
            <option value="archive">压缩包</option>
            <option value="media">音视频</option>
            <option value="code">代码</option>
          </select>
          <label class="file-folder-create">
            <input v-model="folderNameDraft" class="ui-inline-input" placeholder="新建文件夹" @keydown.enter.prevent="createFolder" />
            <AppButton variant="secondary" size="sm" @click="createFolder">
              <FolderPlus :size="15" />
              新建
            </AppButton>
          </label>
        </div>
        <p v-if="folderActionError" class="dialog-error">{{ folderActionError }}</p>

        <div class="file-breadcrumb">
          <button type="button" :class="{ active: currentFolderId === null }" @click="openRootFolder">
            <Home :size="15" />
            根目录
          </button>
          <button
            v-for="folder in breadcrumbs"
            :key="folder.folderId"
            type="button"
            :class="{ active: String(currentFolderId) === String(folder.folderId) }"
            @click="openFolder(folder.folderId)"
          >
            {{ folder.folderName }}
          </button>
        </div>

        <AppLoadingState v-if="browserLoading" />
        <p v-else-if="browserError" class="dialog-error">{{ browserError }}</p>
        <AppEmptyState
          v-else-if="!browserFolders.length && !browserFiles.length"
          :title="emptyTitle"
          :description="emptyDescription"
        />

        <div v-else class="file-list">
          <div v-for="folder in browserFolders" :key="folder.folderId" class="file-row folder-row">
            <span class="file-icon folder-icon">
              <Folder :size="18" />
            </span>
            <div>
              <strong>{{ folder.folderName }}</strong>
              <small>文件夹 · {{ formatDateTime(folder.createdAt) }}</small>
            </div>
            <div class="file-actions">
              <button type="button" @click="openFolder(folder.folderId)">
                打开
              </button>
            </div>
          </div>

          <div v-for="file in browserFiles" :key="file.id" class="file-row">
            <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
              <component :is="fileIconOf(file)" :size="18" />
            </span>
            <div>
              <strong>{{ file.name }}</strong>
              <small>{{ file.type }} · {{ file.size }} · {{ file.createdAt }} · {{ file.status }}</small>
            </div>
            <div class="file-actions">
              <button type="button" :disabled="!file.attachmentRefId || im.messageSending" @click="sendSelectedFileToConversation(file)">
                <Send :size="15" />
                发送
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="previewBrowserFile(file)">
                <Eye :size="15" />
                预览
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="downloadBrowserFile(file)">
                <Download :size="15" />
                下载
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="toggleFavorite(file)">
                <Star :size="15" />
                {{ isFavorite(file) ? '已收藏' : '收藏' }}
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="openPlacement(file, 'move')">
                <MoveRight :size="15" />
                移动
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="openPlacement(file, 'copy')">
                <Copy :size="15" />
                复制
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="trashBrowserFile(file)">
                <Trash2 :size="15" />
                删除
              </button>
            </div>
          </div>
        </div>
      </template>

      <template v-else>
        <AppEmptyState v-if="!visibleTrashFiles.length" :title="emptyTitle" :description="emptyDescription" />
        <div v-else class="file-list">
          <div v-for="file in visibleTrashFiles" :key="file.id" class="file-row">
            <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
              <component :is="fileIconOf(file)" :size="18" />
            </span>
            <div>
              <strong>{{ file.name }}</strong>
              <small>{{ file.type }} · {{ file.size }} · {{ file.createdAt }} · {{ file.status }}</small>
            </div>
            <div class="file-actions">
              <button type="button" :disabled="!file.attachmentRefId" @click="restorePersonalFile(file)">
                <RotateCcw :size="15" />
                恢复
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="purgePersonalFile(file)">
                <Trash2 :size="15" />
                彻底删除
              </button>
            </div>
          </div>
        </div>
      </template>
    </section>
  </section>
</div>

<AppDialog v-if="placementTarget">
  <section class="file-placement-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>{{ placementTitle }}</h2>
        <p>{{ placementTarget.file.name }}</p>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="closePlacement">
        <X :size="15" />
      </button>
    </header>
    <label class="security-field">
      <span>目标目录</span>
      <select v-model="placementFolderId" class="ui-inline-input">
        <option value="root">根目录</option>
        <option v-for="folder in allFolders" :key="folder.folderId" :value="String(folder.folderId)">
          {{ folderLabel(folder) }}
        </option>
      </select>
    </label>
    <label v-if="placementTarget.mode === 'copy'" class="security-field">
      <span>副本名称</span>
      <input v-model="copyNameDraft" class="ui-inline-input" />
    </label>
    <p v-if="folderActionError" class="dialog-error">{{ folderActionError }}</p>
    <div class="form-actions">
      <AppButton variant="secondary" @click="closePlacement">取消</AppButton>
      <AppButton variant="primary" @click="submitPlacement">确认</AppButton>
    </div>
  </section>
</AppDialog>
</template>
