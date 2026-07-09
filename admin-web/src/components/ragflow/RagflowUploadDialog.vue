<script setup lang="ts">
import { computed, ref } from 'vue';
import { AlertCircle, CheckCircle2, FileText, Loader2, Upload, X } from 'lucide-vue-next';
import { formatBytes } from '@/utils/format';
import AdminDialog from '@/components/AdminDialog.vue';

type UploadQueueStatus = 'READY' | 'HASHING' | 'TICKET' | 'UPLOADING' | 'CONFIRMING' | 'REGISTERING' | 'DONE' | 'FAILED';

interface UploadQueueItem {
  id: string;
  file: File;
  progress: number;
  status: UploadQueueStatus;
  message: string;
}

const props = defineProps<{
  items: UploadQueueItem[];
  uploadDragActive: boolean;
  uploading: boolean;
  visible: boolean;
}>();

const emit = defineEmits<{
  clear: [];
  close: [];
  'file-change': [value: File[]];
  'remove-file': [value: string];
  submit: [];
  'update:uploadDragActive': [value: boolean];
  'update:visible': [value: boolean];
}>();

const fileInputRef = ref<HTMLInputElement | null>(null);
const opened = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

const selectedText = computed(() => (props.items.length ? `已选择 ${props.items.length} 个文件` : '拖拽上传'));
const canClear = computed(() => props.items.length > 0 && !props.uploading);

function openFilePicker() {
  fileInputRef.value?.click();
}

function handleFileChange(event: Event) {
  emitFiles((event.target as HTMLInputElement).files);
  if (fileInputRef.value) fileInputRef.value.value = '';
}

function handleFileDrop(event: DragEvent) {
  emit('update:uploadDragActive', false);
  emitFiles(event.dataTransfer?.files);
  if (fileInputRef.value) fileInputRef.value.value = '';
}

function emitFiles(files?: FileList | null) {
  emit('file-change', files ? Array.from(files) : []);
}

function itemStatusLabel(item: UploadQueueItem) {
  if (item.message) return item.message;
  if (item.status === 'READY') return '待上传';
  if (item.status === 'HASHING') return '校验文件';
  if (item.status === 'TICKET') return '创建上传票据';
  if (item.status === 'UPLOADING') return '上传文件';
  if (item.status === 'CONFIRMING') return '确认文件';
  if (item.status === 'REGISTERING') return '登记入库任务';
  if (item.status === 'DONE') return '后台入库中';
  if (item.status === 'FAILED') return '上传失败';
  return '待上传';
}
</script>

<template>
  <AdminDialog
    v-model:visible="opened"
    title="上传文件"
    labelled-by="upload-dialog-title"
    width="min(640px, 92vw)"
    :close-disabled="uploading"
    @close="emit('close')"
  >
    <form class="upload-dialog-form" @submit.prevent="emit('submit')">
      <input ref="fileInputRef" class="file-picker-input" type="file" multiple accept=".txt,.md,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.csv,.png,.jpg,.jpeg,.webp" @change="handleFileChange" />
      <button
        :class="['upload-dropzone', { active: uploadDragActive }]"
        type="button"
        @click="openFilePicker"
        @dragenter.prevent="emit('update:uploadDragActive', true)"
        @dragover.prevent="emit('update:uploadDragActive', true)"
        @dragleave.prevent="emit('update:uploadDragActive', false)"
        @drop.prevent="handleFileDrop"
      >
        <Upload :size="22" />
        <strong>{{ selectedText }}</strong>
        <span>{{ items.length ? '上传后自动转入后台入库' : '选择本地文件' }}</span>
      </button>

      <div v-if="items.length" class="upload-queue" role="status" aria-live="polite">
        <article v-for="item in items" :key="item.id" class="upload-queue-item" :class="item.status.toLowerCase()">
          <div class="upload-file-icon">
            <CheckCircle2 v-if="item.status === 'DONE'" :size="18" />
            <AlertCircle v-else-if="item.status === 'FAILED'" :size="18" />
            <Loader2 v-else-if="uploading && item.status !== 'READY'" :size="18" />
            <FileText v-else :size="18" />
          </div>
          <div class="upload-file-main">
            <div class="upload-file-line">
              <strong>{{ item.file.name }}</strong>
              <span>{{ item.progress }}%</span>
            </div>
            <div class="upload-file-meta">
              <span>{{ formatBytes(item.file.size) }}</span>
              <span>{{ itemStatusLabel(item) }}</span>
            </div>
            <progress :value="item.progress" max="100">{{ item.progress }}%</progress>
          </div>
          <button
            v-if="!uploading"
            class="icon-button upload-remove"
            type="button"
            title="移除"
            @click="emit('remove-file', item.id)"
          >
            <X :size="14" />
          </button>
        </article>
      </div>

      <div class="upload-actions">
        <button class="ghost-button" type="button" :disabled="!canClear" @click="emit('clear')">清空</button>
        <button class="secondary-button" type="button" :disabled="uploading" @click="openFilePicker">选择文件</button>
        <button class="primary-button" type="submit" :disabled="uploading || !items.length">
          <Upload :size="16" />
          <span>{{ uploading ? '上传中' : (items.length ? `上传 ${items.length} 个文件` : '上传文件') }}</span>
        </button>
      </div>
    </form>
  </AdminDialog>
</template>
