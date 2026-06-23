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
  Trash2,
  X,
} from 'lucide-vue-next';
import { employeeApi, type EmployeeFileFolderResponse, type EmployeeFileResponse, type EntityId } from '@/api';
import { useFilesSectionContext } from '../employee-shell-context';
import { fileIconOf, fileToneOf, isFileTrashed } from '@/utils/file-record';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { formatDateTime, statusText } from '@/utils/employee-format';
import type { PersonalFileRecord } from '@/stores/shell';
import AppButton from '../../components/ui/AppButton.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppLoadingState from '../../components/ui/AppLoadingState.vue';

const {
  im,
  fileUploading,
  activeFile,
  fileUsageCircumference,
  activeFileUsage,
  activeFileUsageOffset,
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
let searchTimer: number | undefined;

const activeFiles = computed(() => activeFile.value.files.filter((file: PersonalFileRecord) => !isFileTrashed(file)));
const trashedFiles = computed(() => activeFile.value.files.filter((file: PersonalFileRecord) => isFileTrashed(file)));
const visibleTrashFiles = computed(() => trashedFiles.value);
const emptyTitle = computed(() => fileTab.value === 'trash' ? '回收站为空' : '暂无文件');
const emptyDescription = computed(() => fileTab.value === 'trash' ? '删除的文件会暂存在这里' : '上传或新建文件夹后开始管理');
const placementTitle = computed(() => placementTarget.value?.mode === 'copy' ? '复制到' : '移动到');

function mapBrowserFile(file: EmployeeFileResponse): PersonalFileRecord {
  const name = file.displayName || file.originalFileName || '未命名文件';
  return {
    id: `personal-file-${file.attachmentRefId}`,
    name,
    type: fileTypeLabelFromName(name, file.mimeType ?? ''),
    size: fileSizeLabel(file.fileSize),
    createdAt: formatDateTime(file.createdAt),
    status: file.deletedAt ? '回收站' : statusText(file.scanStatus),
    attachmentRefId: file.attachmentRefId,
    folderId: file.folderId ?? null,
    mimeType: file.mimeType ?? undefined,
  };
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
    <article class="hero-panel">
      <div>
        <h3>{{ activeFile.subtitle }}</h3>
        <p>{{ activeFile.note }}</p>
        <dl class="hero-stats">
          <div v-for="item in activeFile.summary" :key="item[0]">
            <dt>{{ item[1] }}</dt>
            <dd>{{ item[0] }}</dd>
          </div>
        </dl>
      </div>
      <div class="progress-ring">
        <svg viewBox="0 0 120 120" aria-hidden="true">
          <circle class="progress-ring-track" cx="60" cy="60" r="52" />
          <circle
            class="progress-ring-value"
            cx="60"
            cy="60"
            r="52"
            :stroke-dasharray="fileUsageCircumference"
            :stroke-dashoffset="activeFileUsageOffset"
          />
        </svg>
        <div class="progress-ring-text">
          <strong>{{ activeFileUsage }}%</strong>
          <span>已用空间</span>
        </div>
      </div>
    </article>

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
              <button type="button" :disabled="!file.attachmentRefId" @click="openPersonalFilePreview(file)">
                <Eye :size="15" />
                预览
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="downloadPersonalFile(file)">
                <Download :size="15" />
                下载
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
