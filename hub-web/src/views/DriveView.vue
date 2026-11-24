<script setup lang="ts">
import { downloadFile } from '@/services/download';
/**
 * 我的云空间：文件/文件夹浏览、搜索、类型筛选、上传、新建文件夹、下载、删除。
 * 分享暂无后端接口，按钮 toast 占位。
 */
import { computed, onMounted, ref } from 'vue';
import { FolderPlus, Upload, Download, Share2, Trash2, Search, Folder, FileText } from 'lucide-vue-next';
import { fileApi } from '@/api';
import type { EmployeeFileBrowserResponse, EmployeeFileResponse, EmployeeFileFolderResponse } from '@/api/types';
import { uploadEmployeeFile } from '@/composables/useUpload';
import { useToast } from '@/composables/useToast';
import HubButton from '@/components/base/HubButton.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';
import HubToast from '@/components/base/HubToast.vue';

const { message: toastMessage, show: showToast } = useToast();

const browser = ref<EmployeeFileBrowserResponse | null>(null);
const loading = ref(true);
const keyword = ref('');
const typeFilter = ref<'all' | 'folder' | 'doc'>('all');
const currentFolderId = ref<string | null>(null);
const deleting = ref<EmployeeFileResponse | null>(null);
const sharing = ref<EmployeeFileResponse | null>(null);
const showUpload = ref(false);
const uploading = ref(false);
const showFolder = ref(false);
const creatingFolder = ref(false);
const folderName = ref('');
let searchTimer: number | undefined;

async function load() {
  loading.value = true;
  try {
    browser.value = await fileApi.browser({
      folderId: currentFolderId.value ?? undefined,
      keyword: keyword.value.trim() || undefined,
    });
  } catch (error) {
    showToast(error instanceof Error ? error.message : '文件加载失败');
  } finally {
    loading.value = false;
  }
}
onMounted(load);

function onSearch() {
  window.clearTimeout(searchTimer);
  searchTimer = window.setTimeout(load, 300);
}

const folders = computed(() => browser.value?.folders ?? []);
const files = computed(() => (browser.value?.files ?? []).filter((f) => {
  if (typeFilter.value === 'folder') return false;
  if (typeFilter.value === 'doc') return !(f.mimeType || '').startsWith('image/');
  return true;
}));
const isEmpty = computed(() => !folders.value.length && !files.value.length && !loading.value);

function formatSize(bytes?: number | null) {
  if (!bytes) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}
function timeText(t?: string | null) {
  return t ? t.slice(0, 16).replace('T', ' ') : '-';
}
function typeLabel(f: EmployeeFileResponse) {
  const mime = f.mimeType || '';
  if (mime.startsWith('image/')) return '图片';
  if (mime.includes('pdf')) return 'PDF';
  if (mime.includes('word')) return '文档';
  if (mime.includes('sheet') || mime.includes('excel')) return '表格';
  return '文件';
}

function openFolder(folder: EmployeeFileFolderResponse) {
  currentFolderId.value = String(folder.folderId);
  load();
}
function goBreadcrumb(folder: EmployeeFileFolderResponse | null) {
  currentFolderId.value = folder ? String(folder.folderId) : null;
  load();
}

async function submitUpload(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  uploading.value = true;
  try {
    await uploadEmployeeFile({ file, ownerType: 'PERSONAL_FILE', folderId: currentFolderId.value });
    showToast('上传成功');
    showUpload.value = false;
    await load();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '上传失败');
  } finally {
    uploading.value = false;
    input.value = '';
  }
}

async function submitFolder() {
  if (!folderName.value.trim()) { showToast('请填写文件夹名称'); return; }
  creatingFolder.value = true;
  try {
    await fileApi.createFolder({ parentFolderId: currentFolderId.value, folderName: folderName.value.trim() });
    showToast('文件夹已创建');
    showFolder.value = false;
    folderName.value = '';
    await load();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '创建失败');
  } finally {
    creatingFolder.value = false;
  }
}

async function download(file: EmployeeFileResponse) {
  try {
    const ticket = await fileApi.createDownloadTicket(file.attachmentRefId, 'ATTACHMENT');
    await downloadFile(fileApi.downloadUrl(ticket.ticketNo));
  } catch (error) {
    showToast(error instanceof Error ? error.message : '下载失败');
  }
}

function share(file: EmployeeFileResponse) {
  sharing.value = file;
}

async function confirmDelete() {
  if (!deleting.value) return;
  try {
    await fileApi.trash(deleting.value.attachmentRefId, '云空间手动删除');
    showToast('已移入回收站');
    deleting.value = null;
    await load();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '删除失败');
  }
}
</script>

<template>
  <div class="dv-page">
    <header class="dv-head">
      <div><h1>我的云空间</h1><p>个人文件上传、下载与整理</p></div>
      <span class="dv-head-spacer" />
      <button type="button" class="dv-btn-secondary" @click="showFolder = true"><FolderPlus :size="16" />新建文件夹</button>
      <button type="button" class="dv-btn-primary" @click="showUpload = true"><Upload :size="16" />上传文件</button>
    </header>

    <div class="dv-toolbar">
      <div class="dv-search">
        <Search :size="14" />
        <input v-model="keyword" placeholder="搜索文件名" @input="onSearch" />
      </div>
      <div class="dv-tabs">
        <button type="button" :class="{ active: typeFilter === 'all' }" @click="typeFilter = 'all'">全部</button>
        <button type="button" :class="{ active: typeFilter === 'folder' }" @click="typeFilter = 'folder'">文件夹</button>
        <button type="button" :class="{ active: typeFilter === 'doc' }" @click="typeFilter = 'doc'">文档</button>
      </div>
    </div>

    <div v-if="browser?.breadcrumbs?.length" class="dv-crumbs">
      <button type="button" @click="goBreadcrumb(null)">全部文件</button>
      <template v-for="b in browser.breadcrumbs" :key="String(b.folderId)">
        <span>/</span>
        <button type="button" @click="goBreadcrumb(b)">{{ b.folderName }}</button>
      </template>
    </div>

    <section class="dv-card">
      <div v-if="!isEmpty" class="dv-table-head">
        <span class="col-name">名称</span>
        <span class="col-owner">所有者</span>
        <span class="col-time">更新时间</span>
        <span class="col-size">大小</span>
        <span class="col-actions" />
      </div>

      <button v-for="f in folders" :key="`f-${f.folderId}`" type="button" class="dv-row" @click="openFolder(f)">
        <span class="col-name"><Folder :size="16" class="icon-folder" /><strong>{{ f.folderName }}</strong><small>文件夹</small></span>
        <span class="col-owner">我</span>
        <span class="col-time">{{ timeText(f.updatedAt) }}</span>
        <span class="col-size">-</span>
        <span class="col-actions" />
      </button>

      <div v-for="f in files" :key="String(f.attachmentRefId)" class="dv-row">
        <span class="col-name"><FileText :size="16" class="icon-file" /><strong>{{ f.displayName }}</strong><small>{{ typeLabel(f) }}</small></span>
        <span class="col-owner">{{ f.uploaderName || '我' }}</span>
        <span class="col-time">{{ timeText(f.createdAt) }}</span>
        <span class="col-size">{{ formatSize(f.fileSize) }}</span>
        <span class="col-actions">
          <button type="button" title="下载" @click="download(f)"><Download :size="15" /></button>
          <button type="button" title="分享" @click="share(f)"><Share2 :size="15" /></button>
          <button type="button" title="删除" @click="deleting = f"><Trash2 :size="15" /></button>
        </span>
      </div>

      <HubEmpty v-if="isEmpty" title="暂无文件" note="点击右上角上传文件或新建文件夹" />
    </section>

    <HubDialog v-if="showUpload" @close="showUpload = false">
      <form class="dv-form" @submit.prevent>
        <h2>上传文件</h2>
        <label>选择文件<input type="file" @change="submitUpload" /></label>
        <div class="dv-form-actions">
          <HubButton type="button" :disabled="uploading" @click="showUpload = false">{{ uploading ? '上传中…' : '关闭' }}</HubButton>
        </div>
      </form>
    </HubDialog>

    <HubDialog v-if="showFolder" @close="showFolder = false">
      <form class="dv-form" @submit.prevent="submitFolder">
        <h2>新建文件夹</h2>
        <label>名称<input v-model="folderName" placeholder="文件夹名称" /></label>
        <div class="dv-form-actions">
          <HubButton type="button" @click="showFolder = false">取消</HubButton>
          <HubButton variant="primary" type="submit" :disabled="creatingFolder">创建</HubButton>
        </div>
      </form>
    </HubDialog>

    <HubDialog v-if="sharing" @close="sharing = null">
      <div class="dv-form">
        <h2>分享文件</h2>
        <p class="dv-share-name"><FileText :size="16" />{{ sharing.displayName }}</p>
        <p class="dv-share-note">仅企业成员可见，暂不支持外部分享链接。</p>
        <div class="dv-form-actions">
          <HubButton variant="primary" @click="sharing = null">知道了</HubButton>
        </div>
      </div>
    </HubDialog>

    <HubDialog v-if="deleting" @close="deleting = null">
      <div class="dv-form">
        <h2>删除文件</h2>
        <p class="dv-share-note">确定将「{{ deleting.displayName }}」移入回收站？30 天内可联系管理员恢复。</p>
        <div class="dv-form-actions">
          <HubButton @click="deleting = null">取消</HubButton>
          <HubButton variant="danger" @click="confirmDelete">删除</HubButton>
        </div>
      </div>
    </HubDialog>

    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped src="../styles/drive.css" />
