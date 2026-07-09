<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  ChevronLeft,
  Copy,
  Download,
  FolderPlus,
  Home,
  MoreHorizontal,
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
import { isFileTrashed } from '@/utils/file-record';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { buildStorageOverview, parseStorageSizeBytes } from '@/utils/cloud-storage-summary';
import { formatDateTime, statusText } from '@/utils/employee-format';
import type { PersonalFileRecord } from '@/stores/shell';
import cloudFolderImage from '@/assets/cloud-folder.png';
import archiveFileIcon from '@/assets/file-icons/archive.png';
import excelFileIcon from '@/assets/file-icons/excel.png';
import fileIcon from '@/assets/file-icons/file.png';
import folderFileIcon from '@/assets/file-icons/folder.png';
import pdfFileIcon from '@/assets/file-icons/pdf.png';
import pptFileIcon from '@/assets/file-icons/ppt.png';
import wordFileIcon from '@/assets/file-icons/word.png';
import AppButton from '../../components/ui/AppButton.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppLoadingState from '../../components/ui/AppLoadingState.vue';

const {
  im,
  fileUploading,
  activeFile,
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
type FileQuickView = 'all' | 'recentAccess' | 'recentUpload' | 'favorites' | 'shared';
type FilePlacementMode = 'move' | 'copy';

const fileTab = ref<FileTab>('all');
const quickView = ref<FileQuickView>('all');
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
const selectedFileRows = ref<string[]>([]);
let searchTimer: number | undefined;

const activeFiles = computed(() => activeFile.value.files.filter((file: PersonalFileRecord) => !isFileTrashed(file)));
const trashedFiles = computed(() => activeFile.value.files.filter((file: PersonalFileRecord) => isFileTrashed(file)));
const visibleTrashFiles = computed(() => trashedFiles.value);
const placementTitle = computed(() => placementTarget.value?.mode === 'copy' ? '复制到' : '移动到');
const recentUploadCount = computed(() => activeFiles.value.filter((file) => isWithinLastWeek(file.rawCreatedAt ?? file.createdAt)).length);
const recentAccessCount = computed(() => activeFiles.value.filter((file) => (recentAccessLog.value[fileIdentity(file)] ?? 0) >= weekAgoTime()).length);
const favoriteFileCount = computed(() => activeFiles.value.filter((file) => favoriteFileIds.value.includes(fileIdentity(file))).length);
const fileQuickCards = computed(() => [
  { key: 'recentAccess' as const, title: '最近访问', count: recentAccessCount.value },
  { key: 'recentUpload' as const, title: '最近上传', count: recentUploadCount.value },
  { key: 'favorites' as const, title: '收藏夹', count: favoriteFileCount.value },
  { key: 'shared' as const, title: '共享文件', count: 0 },
]);
const quickViewTitles: Record<FileQuickView, string> = {
  all: '全部文件',
  recentAccess: '最近访问',
  recentUpload: '最近上传',
  favorites: '收藏夹',
  shared: '共享文件',
};
const quickViewEmptyDescriptions: Record<FileQuickView, string> = {
  all: '上传或新建文件夹后开始管理',
  recentAccess: '预览或下载文件后会出现在这里',
  recentUpload: '最近 7 天上传的文件会出现在这里',
  favorites: '收藏文件后会出现在这里',
  shared: '暂无共享文件',
};
const quickViewFiles = computed(() => {
  const files = activeFiles.value
    .filter((file) => fileMatchesQuickView(file))
    .filter((file) => fileMatchesClientFilters(file));
  if (quickView.value === 'recentAccess') {
    return [...files].sort((a, b) => (recentAccessLog.value[fileIdentity(b)] ?? 0) - (recentAccessLog.value[fileIdentity(a)] ?? 0));
  }
  if (quickView.value === 'recentUpload') {
    return [...files].sort((a, b) => fileCreatedTime(b) - fileCreatedTime(a));
  }
  return files;
});
const visibleBrowserFolders = computed(() => quickView.value === 'all' ? browserFolders.value : []);
const visibleBrowserFiles = computed(() => quickView.value === 'all' ? browserFiles.value : quickViewFiles.value);
const currentFileViewTitle = computed(() => fileTab.value === 'trash' ? '回收站' : quickViewTitles[quickView.value]);
const currentFileViewCount = computed(() => {
  if (fileTab.value === 'trash') return `${visibleTrashFiles.value.length} 个文件`;
  const folderCount = visibleBrowserFolders.value.length;
  const fileCount = visibleBrowserFiles.value.length;
  return folderCount ? `${folderCount} 个文件夹 · ${fileCount} 个文件` : `${fileCount} 个文件`;
});
const emptyTitle = computed(() => {
  if (fileTab.value === 'trash') return '回收站为空';
  return quickView.value === 'all' ? '暂无文件' : `暂无${quickViewTitles[quickView.value]}`;
});
const emptyDescription = computed(() => fileTab.value === 'trash' ? '删除的文件会暂存在这里' : quickViewEmptyDescriptions[quickView.value]);
const storageUsedLabel = computed(() => formatStorageLabel(activeFile.value.summary[0]?.[0] ?? '0B'));
const storageTotalLabel = computed(() => formatStorageLabel(activeFile.value.summary[1]?.[0] ?? '0B'));
const storageUsedBytes = computed(() => parseStorageSizeBytes(storageUsedLabel.value));
const storageTotalBytes = computed(() => parseStorageSizeBytes(storageTotalLabel.value));
const storageOverview = computed(() => buildStorageOverview(activeFiles.value, storageUsedBytes.value, storageTotalBytes.value));
const storageSegments = computed(() => storageOverview.value.segments);
const storageUsagePercentLabel = computed(() => formatUsagePercent(storageOverview.value.usedPercent));

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
    status: file.deletedAt ? '回收站' : '已上传',
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

function fileCreatedTime(file: PersonalFileRecord) {
  const value = file.rawCreatedAt ?? file.createdAt;
  if (!value) return 0;
  if (value === '刚刚') return Date.now();
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : 0;
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

function formatStorageLabel(value: string) {
  return value.replace(/^([\d.]+)([A-Z]+)$/i, '$1 $2');
}

function formatUsagePercent(value: number) {
  if (value > 0 && value < 0.01) return '<0.01';
  return value < 1 ? value.toFixed(2) : String(Math.round(value));
}

function folderLabel(folder: EmployeeFileFolderResponse) {
  const parent = allFolders.value.find((item) => String(item.folderId) === String(folder.parentFolderId));
  return parent ? `${parent.folderName} / ${folder.folderName}` : folder.folderName;
}

function folderSelectionValue(folder: EmployeeFileFolderResponse) {
  return `folder:${folder.folderId}`;
}

function fileSelectionValue(file: PersonalFileRecord) {
  return `file:${fileIdentity(file)}`;
}

function fileIconAssetOf(file: PersonalFileRecord) {
  const name = file.name.toLowerCase();
  const mime = file.mimeType?.toLowerCase() ?? '';
  if (mime.includes('pdf') || name.endsWith('.pdf')) return pdfFileIcon;
  if (/\.(doc|docx)$/i.test(name) || mime.includes('wordprocessingml') || mime.includes('msword')) return wordFileIcon;
  if (/\.(xls|xlsx|csv)$/i.test(name) || mime.includes('spreadsheet')) return excelFileIcon;
  if (/\.(ppt|pptx)$/i.test(name) || mime.includes('presentation')) return pptFileIcon;
  if (/\.(zip|rar|7z|tar|gz)$/i.test(name)) return archiveFileIcon;
  return fileIcon;
}

function fileMatchesQuickView(file: PersonalFileRecord) {
  if (quickView.value === 'all') return true;
  if (quickView.value === 'recentAccess') return (recentAccessLog.value[fileIdentity(file)] ?? 0) >= weekAgoTime();
  if (quickView.value === 'recentUpload') return isWithinLastWeek(file.rawCreatedAt ?? file.createdAt);
  if (quickView.value === 'favorites') return favoriteFileIds.value.includes(fileIdentity(file));
  return false;
}

function fileMatchesClientFilters(file: PersonalFileRecord) {
  const keyword = fileKeyword.value.trim().toLowerCase();
  if (keyword) {
    const haystack = [file.name, file.type, file.status, file.mimeType ?? ''].join(' ').toLowerCase();
    if (!haystack.includes(keyword)) return false;
  }
  if (fileTypeFilter.value === 'all') return true;
  const name = file.name.toLowerCase();
  const type = file.type.toLowerCase();
  const mime = file.mimeType?.toLowerCase() ?? '';
  if (fileTypeFilter.value === 'document') return /(\.pdf|\.docx?|\.txt|\.rtf|\.md)$/i.test(name) || type.includes('文档') || mime.includes('pdf') || mime.includes('word');
  if (fileTypeFilter.value === 'image') return /\.(png|jpe?g|gif|webp|svg|bmp)$/i.test(name) || mime.startsWith('image/');
  if (fileTypeFilter.value === 'sheet') return /\.(xls|xlsx|csv)$/i.test(name) || type.includes('表格') || mime.includes('spreadsheet');
  if (fileTypeFilter.value === 'archive') return /\.(zip|rar|7z|tar|gz)$/i.test(name) || type.includes('压缩');
  if (fileTypeFilter.value === 'media') return /\.(mp3|mp4|wav|mov|avi|m4a)$/i.test(name) || mime.startsWith('audio/') || mime.startsWith('video/');
  if (fileTypeFilter.value === 'code') return /\.(js|ts|java|py|go|json|xml|yml|yaml|css|html)$/i.test(name) || type.includes('代码');
  return true;
}

async function loadBrowser() {
  if (fileTab.value !== 'all' || quickView.value !== 'all') {
    browserLoading.value = false;
    return;
  }
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

function selectAllFiles() {
  fileTab.value = 'all';
  quickView.value = 'all';
  selectedFileRows.value = [];
}

function selectTrashFiles() {
  fileTab.value = 'trash';
  quickView.value = 'all';
  selectedFileRows.value = [];
}

function selectQuickView(view: FileQuickView) {
  fileTab.value = 'all';
  quickView.value = view;
  selectedFileRows.value = [];
}

function uploadToCurrentFolder() {
  setUploadFolderId(quickView.value === 'all' ? currentFolderId.value : null);
  runAction(currentMeta.value.primaryAction);
}

watch([currentFolderId, fileTypeFilter, fileTab, quickView], () => {
  void loadBrowser();
});

watch(() => activeFile.value.files.length, () => {
  void loadBrowser();
});

watch(fileKeyword, () => {
  if (quickView.value !== 'all') return;
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

  <section class="content-stack cloud-content-stack">
    <section class="cloud-summary-grid" aria-label="云空间概览">
      <div class="cloud-quick-grid">
        <button
          v-for="card in fileQuickCards"
          :key="card.key"
          class="cloud-folder-card"
          :class="{ active: fileTab === 'all' && quickView === card.key }"
          type="button"
          :aria-pressed="fileTab === 'all' && quickView === card.key"
          @click="selectQuickView(card.key)"
        >
          <img :src="cloudFolderImage" alt="" aria-hidden="true" />
          <div>
            <h3>{{ card.title }}</h3>
            <p>{{ card.count }} 个文件</p>
          </div>
        </button>
      </div>

      <article class="storage-overview-card">
        <header>储存概览</header>
        <div class="storage-overview-body">
          <div class="storage-usage-line">
            <strong>{{ storageUsedLabel }}</strong>
            <span>of {{ storageTotalLabel }}</span>
          </div>
          <div class="storage-ratio-bar" aria-hidden="true">
            <div class="storage-used-fill" :style="{ width: `${storageOverview.usedPercent}%` }">
              <span
                v-for="item in storageSegments"
                v-show="item.percent > 0"
                :key="item.key"
                :style="{ width: `${item.percent}%`, background: item.color }"
              />
            </div>
          </div>
          <div class="storage-legend">
            <span v-for="item in storageSegments" :key="item.key">
              <i :style="{ background: item.color }" />
              {{ item.label }}
            </span>
          </div>
        </div>
        <footer>已使用 {{ storageUsagePercentLabel }}%</footer>
      </article>
    </section>

    <section class="detail-card">
      <div class="title-line file-tabs">
        <div class="settings-density-row">
          <button type="button" :class="{ active: fileTab === 'all' && quickView === 'all' }" @click="selectAllFiles">
            全部文件
            <span class="tab-count">{{ activeFiles.length }}</span>
          </button>
          <button type="button" :class="{ active: fileTab === 'trash' }" @click="selectTrashFiles">
            回收站
            <span class="tab-count">{{ trashedFiles.length }}</span>
          </button>
        </div>
        <div class="file-current-view">
          <strong>{{ currentFileViewTitle }}</strong>
          <span>{{ currentFileViewCount }}</span>
        </div>
      </div>

      <template v-if="fileTab === 'all'">
        <div class="file-browser-toolbar" :class="{ compact: quickView !== 'all' }">
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
          <label v-if="quickView === 'all'" class="file-folder-create">
            <input v-model="folderNameDraft" class="ui-inline-input" placeholder="新建文件夹" @keydown.enter.prevent="createFolder" />
            <AppButton variant="secondary" size="sm" @click="createFolder">
              <FolderPlus :size="15" />
              新建
            </AppButton>
          </label>
        </div>
        <p v-if="folderActionError" class="dialog-error">{{ folderActionError }}</p>

        <div v-if="quickView === 'all'" class="file-breadcrumb">
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

        <AppLoadingState v-if="quickView === 'all' && browserLoading" />
        <p v-else-if="quickView === 'all' && browserError" class="dialog-error">{{ browserError }}</p>
        <AppEmptyState
          v-else-if="!visibleBrowserFolders.length && !visibleBrowserFiles.length"
          :title="emptyTitle"
          :description="emptyDescription"
        />

        <div v-else class="file-list cloud-file-list">
          <div
            v-for="folder in visibleBrowserFolders"
            :key="folder.folderId"
            class="file-row folder-row"
            role="button"
            tabindex="0"
            @click="openFolder(folder.folderId)"
            @keydown.enter.prevent="openFolder(folder.folderId)"
          >
            <input v-model="selectedFileRows" type="checkbox" :value="folderSelectionValue(folder)" :aria-label="`选择${folder.folderName}`" @click.stop />
            <span class="file-thumb" aria-hidden="true">
              <img class="file-thumb-img" :src="folderFileIcon" alt="" />
            </span>
            <div>
              <strong>{{ folder.folderName }}</strong>
              <small class="file-meta-line">
                <span>文件夹</span>
                <span>{{ formatDateTime(folder.createdAt) }}</span>
              </small>
            </div>
          </div>

          <div v-for="file in visibleBrowserFiles" :key="file.id" class="file-row">
            <input v-model="selectedFileRows" type="checkbox" :value="fileSelectionValue(file)" :aria-label="`选择${file.name}`" />
            <span class="file-thumb" aria-hidden="true">
              <img class="file-thumb-img" :src="fileIconAssetOf(file)" alt="" />
            </span>
            <div>
              <button class="file-name-button" type="button" :disabled="!file.attachmentRefId" @click="previewBrowserFile(file)">
                {{ file.name }}
              </button>
              <small class="file-meta-line">
                <span>{{ file.type }}</span>
                <span>{{ file.size }}</span>
                <span>{{ file.createdAt }}</span>
                <span>{{ statusText(file.status) }}</span>
              </small>
            </div>
            <div class="file-actions">
              <button type="button" :disabled="!file.attachmentRefId || im.messageSending" @click="sendSelectedFileToConversation(file)">
                <Send :size="15" />
                发送
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="downloadBrowserFile(file)">
                <Download :size="15" />
                下载
              </button>
              <details class="file-more-menu">
                <summary>
                  <MoreHorizontal :size="15" />
                  更多
                </summary>
                <div class="file-more-panel">
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
              </details>
            </div>
          </div>
        </div>
      </template>

      <template v-else>
        <AppEmptyState v-if="!visibleTrashFiles.length" :title="emptyTitle" :description="emptyDescription" />
        <div v-else class="file-list cloud-file-list">
          <div v-for="file in visibleTrashFiles" :key="file.id" class="file-row">
            <input v-model="selectedFileRows" type="checkbox" :value="fileSelectionValue(file)" :aria-label="`选择${file.name}`" />
            <span class="file-thumb" aria-hidden="true">
              <img class="file-thumb-img" :src="fileIconAssetOf(file)" alt="" />
            </span>
            <div>
              <strong>{{ file.name }}</strong>
              <small class="file-meta-line">
                <span>{{ file.type }}</span>
                <span>{{ file.size }}</span>
                <span>{{ file.createdAt }}</span>
                <span>{{ statusText(file.status) }}</span>
              </small>
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
