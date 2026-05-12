<script setup lang="ts">
import { computed, ref } from 'vue';
import {
  ChevronLeft,
  Download,
  Eye,
  RotateCcw,
  Send,
  Trash2,
} from 'lucide-vue-next';
import { useEmployeeShellContext } from '../employee-shell-context';
import AppButton from '../../components/ui/AppButton.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';

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
  isFileTrashed,
  fileToneOf,
  fileIconOf,
  sendSelectedFileToConversation,
  openPersonalFilePreview,
  downloadPersonalFile,
  trashPersonalFile,
  restorePersonalFile,
  purgePersonalFile,
} = useEmployeeShellContext();

type FileTab = 'all' | 'trash';
const fileTab = ref<FileTab>('all');

const activeFiles = computed(() => activeFile.value.files.filter((file: { status?: string }) => !isFileTrashed(file)));
const trashedFiles = computed(() => activeFile.value.files.filter((file: { status?: string }) => isFileTrashed(file)));
const visibleFiles = computed(() => fileTab.value === 'trash' ? trashedFiles.value : activeFiles.value);
const emptyTitle = computed(() => fileTab.value === 'trash' ? '回收站为空' : '暂无文件');
const emptyDescription = computed(() => fileTab.value === 'trash' ? '删除的文件会暂存在这里' : '点击右上角“上传文件”后开始管理');
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
      <AppButton variant="primary" size="sm" :disabled="fileUploading" @click="runAction(currentMeta.primaryAction)">
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

      <AppEmptyState v-if="!visibleFiles.length" :title="emptyTitle" :description="emptyDescription" />

      <div v-else class="file-list">
        <div v-for="file in visibleFiles" :key="file.id" class="file-row">
          <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
            <component :is="fileIconOf(file)" :size="18" />
          </span>
          <div>
            <strong>{{ file.name }}</strong>
            <small>{{ file.type }} · {{ file.size }} · {{ file.createdAt }} · {{ file.status }}</small>
          </div>
          <div class="file-actions">
            <template v-if="fileTab === 'all'">
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
              <button type="button" :disabled="!file.attachmentRefId" @click="trashPersonalFile(file)">
                <Trash2 :size="15" />
                删除
              </button>
            </template>
            <template v-else>
              <button type="button" :disabled="!file.attachmentRefId" @click="restorePersonalFile(file)">
                <RotateCcw :size="15" />
                恢复
              </button>
              <button type="button" :disabled="!file.attachmentRefId" @click="purgePersonalFile(file)">
                <Trash2 :size="15" />
                彻底删除
              </button>
            </template>
          </div>
        </div>
      </div>
    </section>
  </section>
</div>
</template>
