<script setup lang="ts">
/**
 * ImBubble：消息气泡（画布：右蓝左灰 r16 pad 16/12 + 36 圆头像）。
 * 媒体规则（批次⑨）：图片直接预览不包气泡、视频封面+播放标识、文件/文档显示类型卡片；
 * 时间戳不由气泡显示（消息流按间隔插入时间分隔条）。
 */
import { computed, onMounted, ref } from 'vue';
import {
  CornerUpLeft, FileSpreadsheet, FileText, File as FileIcon, MoreHorizontal, Play, X,
} from 'lucide-vue-next';
import HubAvatar from '../base/HubAvatar.vue';
import { imApi } from '@/api';
import type { ImMessageAttachment } from '@/api/types';
import { isImageAttachment, isVideoAttachment, type MessageView } from '@/utils/message';

const props = withDefaults(defineProps<{
  message: MessageView;
  avatarUrl?: string;
  avatarLabel?: string;
  showAuthor?: boolean;
  quote?: { id: string; author: string; body: string } | null;
  mentionNames?: string[];
}>(), {
  avatarUrl: '',
  avatarLabel: '',
  showAuthor: false,
  quote: null,
  mentionNames: () => [],
});

const emit = defineEmits<{
  (e: 'action', message: MessageView, event: MouseEvent): void;
  (e: 'attachment', file: ImMessageAttachment): void;
  (e: 'quote', id: string): void;
}>();

const isRight = computed(() => props.message.side === 'right');
const displayBody = computed(() => (props.message.recalled ? '消息已撤回' : props.message.body));

/* ---------- 附件分流：图片 / 视频 / 文件文档 ---------- */
const imageFiles = computed(() => props.message.attachments.filter(isImageAttachment));
const videoFiles = computed(() => props.message.attachments.filter(isVideoAttachment));
const docFiles = computed(() =>
  props.message.attachments.filter((a) => !isImageAttachment(a) && !isVideoAttachment(a)));
/** 纯媒体消息：无文本且无文档附件时，不包气泡背景 */
const mediaOnly = computed(() =>
  !displayBody.value.trim() && (imageFiles.value.length + videoFiles.value.length) > 0
  && docFiles.value.length === 0 && !props.message.recalled);

/* 图片预览：走 IM 消息附件 INLINE 票证换取可展示 URL（个人文件接口会 403） */
const previewUrls = ref<Record<string, string>>({});
const previewFailed = ref<Record<string, boolean>>({});
const lightboxUrl = ref('');
async function fetchPreviewUrl(f: ImMessageAttachment) {
  const key = String(f.attachmentRefId);
  if (previewUrls.value[key] || previewFailed.value[key]) return;
  try {
    const ticket = await imApi.createMessageAttachmentDownloadTicket(
      props.message.conversationId, props.message.id, f.attachmentRefId, 'INLINE');
    previewUrls.value[key] = ticket.downloadUrl || ticket.url || imApi.attachmentDownloadUrl(ticket.ticketNo);
  } catch {
    // 后端限制（需求表 #15：IM 附件票证「第一版仅支持单文件下载」）：降级为占位块
    previewFailed.value[key] = true;
  }
}
onMounted(() => {
  void Promise.allSettled(imageFiles.value.map(fetchPreviewUrl));
});
function openVideo(f: ImMessageAttachment) {
  const url = previewUrls.value[String(f.attachmentRefId)];
  if (url) window.open(url, '_blank');
  else emit('attachment', f);
}
function ensureVideoUrl(f: ImMessageAttachment) {
  void fetchPreviewUrl(f);
}

/* 文件类型图标 */
function docIcon(f: ImMessageAttachment) {
  const mime = f.mimeType ?? '';
  const name = f.displayName ?? '';
  if (mime.includes('sheet') || /\.(xlsx?|csv)$/i.test(name)) return FileSpreadsheet;
  if (mime.includes('pdf') || /\.pdf$/i.test(name)) return FileIcon;
  return FileText;
}
function docType(f: ImMessageAttachment) {
  const ext = (f.displayName.match(/\.([a-z0-9]+)$/i)?.[1] ?? '').toUpperCase();
  return ext || '文件';
}

// @提及高亮：把 @名字 片段切出来渲染成高亮 span
const bodySegments = computed(() => {
  const body = displayBody.value;
  const names = [...props.mentionNames]
    .filter(Boolean)
    .sort((a, b) => b.length - a.length);
  if (!body || !names.length) return [{ text: body, mention: false }];
  const segments: Array<{ text: string; mention: boolean }> = [];
  let rest = body;
  while (rest.length) {
    let hitIndex = -1;
    let hitName = '';
    for (const name of names) {
      const idx = rest.indexOf(`@${name}`);
      if (idx >= 0 && (hitIndex < 0 || idx < hitIndex)) {
        hitIndex = idx;
        hitName = name;
      }
    }
    if (hitIndex < 0) {
      segments.push({ text: rest, mention: false });
      break;
    }
    if (hitIndex > 0) segments.push({ text: rest.slice(0, hitIndex), mention: false });
    segments.push({ text: `@${hitName}`, mention: true });
    rest = rest.slice(hitIndex + hitName.length + 1);
  }
  return segments;
});

function openMenu(event: MouseEvent) {
  event.stopPropagation();
  emit('action', props.message, event);
}
function fileSize(bytes?: number | null) {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}
</script>

<template>
  <div class="im-bubble-row" :class="{ right: isRight }">
    <HubAvatar
      v-if="!isRight"
      :label="avatarLabel || message.author"
      :image="avatarUrl"
      size="md"
      :agent="message.senderType === 'AGENT'"
    />
    <div class="im-bubble-stack" :class="{ right: isRight }">
      <small v-if="showAuthor && !isRight" class="im-bubble-author">{{ message.author }}</small>
      <div class="im-bubble-wrap" :class="{ right: isRight }">
        <!-- 引用卡独立于气泡上方：纵向堆叠，不与气泡同行 -->
        <button
          v-if="quote" type="button" class="im-bubble-quote"
          @click="emit('quote', quote.id)"
        >
          <span class="im-bubble-quote-icon">
            <CornerUpLeft :size="12" :stroke-width="2.5" />
          </span>
          <span class="im-bubble-quote-copy">
            <strong>{{ quote.author }}</strong>
            <em>{{ quote.body }}</em>
          </span>
        </button>
        <div class="im-bubble-line">
          <div
            class="im-bubble"
            :class="{ right: isRight, recalled: message.recalled, 'media-only': mediaOnly }"
          >
            <p v-if="displayBody" class="im-bubble-text">
              <template v-for="(seg, i) in bodySegments" :key="i">
                <span v-if="seg.mention" class="im-bubble-mention">{{ seg.text }}</span>
                <template v-else>{{ seg.text }}</template>
              </template>
            </p>
            <!-- 图片：直接预览，不用气泡包裹 -->
            <div v-if="imageFiles.length" class="im-bubble-media">
              <button
                v-for="f in imageFiles" :key="String(f.attachmentRefId)" type="button"
                class="im-media-img" :class="{ failed: previewFailed[String(f.attachmentRefId)] }"
                @click="previewUrls[String(f.attachmentRefId)] && (lightboxUrl = previewUrls[String(f.attachmentRefId)])"
              >
                <img
                  v-if="previewUrls[String(f.attachmentRefId)]"
                  :src="previewUrls[String(f.attachmentRefId)]" :alt="f.displayName"
                />
                <span v-else-if="previewFailed[String(f.attachmentRefId)]" class="im-media-failed">
                  <FileIcon :size="18" :stroke-width="2" />
                  <em>{{ f.displayName }}</em>
                  <small>预览通道待后端支持</small>
                </span>
                <span v-else class="im-media-loading">加载中…</span>
              </button>
            </div>
            <!-- 视频：封面 + 播放标识 -->
            <div v-if="videoFiles.length" class="im-bubble-media">
              <button
                v-for="f in videoFiles" :key="String(f.attachmentRefId)" type="button"
                class="im-media-video" @mouseenter="ensureVideoUrl(f)" @click="openVideo(f)"
              >
                <span class="im-media-play"><Play :size="20" :stroke-width="2" /></span>
                <span class="im-media-video-name">{{ f.displayName }}</span>
              </button>
            </div>
            <!-- 文件/文档：类型卡片 -->
            <div v-if="docFiles.length" class="im-bubble-files">
              <button
                v-for="file in docFiles" :key="String(file.attachmentRefId)" type="button"
                class="im-bubble-file" @click="emit('attachment', file)"
              >
                <span class="im-bubble-file-icon">
                  <component :is="docIcon(file)" :size="16" :stroke-width="2" />
                </span>
                <span class="im-bubble-file-copy">
                  <strong>{{ file.displayName }}</strong>
                  <small>{{ docType(file) }}<template v-if="fileSize(file.fileSize)"> · {{ fileSize(file.fileSize) }}</template></small>
                </span>
              </button>
            </div>
          </div>
          <button type="button" class="im-bubble-more" aria-label="消息操作" @click="openMenu">
            <MoreHorizontal :size="15" :stroke-width="2" />
          </button>
        </div>
      </div>
    </div>
    <HubAvatar v-if="isRight" label="我" :image="avatarUrl" size="md" />

    <!-- 图片放大预览 -->
    <Teleport to="body">
      <div v-if="lightboxUrl" class="im-lightbox" @click="lightboxUrl = ''">
        <img :src="lightboxUrl" alt="预览" @click.stop />
        <button type="button" class="im-lightbox-close" aria-label="关闭" @click="lightboxUrl = ''">
          <X :size="18" :stroke-width="2" />
        </button>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.im-bubble-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 78%;
}
.im-bubble-row.right { align-self: flex-end; }
.im-bubble-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.im-bubble-stack.right { align-items: flex-end; }
.im-bubble-author { font-size: 12px; color: var(--hw-muted); padding-left: 2px; }
.im-bubble-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
}
.im-bubble-wrap.right { align-items: flex-end; }
.im-bubble-line {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  max-width: 100%;
}
.im-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  background: var(--hw-inner);
  color: var(--hw-ink);
  min-width: 0;
}
.im-bubble.right { background: var(--hw-primary); color: #fff; }
.im-bubble.recalled {
  background: var(--hw-chip);
  color: var(--hw-muted);
  font-style: italic;
}
/* 纯媒体消息：不包气泡背景 */
.im-bubble.media-only {
  padding: 0;
  background: transparent;
}
.im-bubble-media {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.im-media-img {
  display: block;
  padding: 0;
  border: 0;
  border-radius: 12px;
  overflow: hidden;
  background: var(--hw-inner);
  cursor: zoom-in;
  max-width: 260px;
  max-height: 200px;
}
.im-media-img img {
  display: block;
  max-width: 260px;
  max-height: 200px;
  object-fit: cover;
}
.im-media-loading {
  display: grid;
  place-items: center;
  width: 160px;
  height: 100px;
  font-size: 12px;
  color: var(--hw-muted);
}
.im-media-img.failed { cursor: default; }
.im-media-failed {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 200px;
  height: 110px;
  padding: 8px;
  color: var(--hw-muted);
}
.im-media-failed em {
  font-style: normal;
  font-size: 12px;
  color: var(--hw-secondary);
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.im-media-failed small { font-size: 10px; }
.im-media-video {
  position: relative;
  display: grid;
  place-items: center;
  width: 240px;
  height: 140px;
  border: 0;
  border-radius: 12px;
  background: linear-gradient(135deg, #2b3648, #141a26);
  color: #fff;
  cursor: pointer;
  overflow: hidden;
}
.im-media-play {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(2px);
}
.im-media-video-name {
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: 8px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.85);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: left;
}
.im-lightbox {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: grid;
  place-items: center;
  background: rgba(10, 14, 20, 0.82);
}
.im-lightbox img {
  max-width: 88vw;
  max-height: 88vh;
  border-radius: 8px;
}
.im-lightbox-close {
  position: absolute;
  top: 20px;
  right: 20px;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
  cursor: pointer;
}
.im-bubble-text {
  font-size: 14px;
  line-height: 22px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.im-bubble-mention {
  color: var(--hw-primary);
  font-weight: 500;
}
.im-bubble.right .im-bubble-mention { color: #ffe98a; font-weight: 600; }
.im-bubble-quote {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: 1px solid var(--hw-track);
  border-radius: 10px;
  background: var(--hw-card);
  text-align: left;
  cursor: pointer;
  max-width: 100%;
  transition: background 0.15s;
}
.im-bubble-quote:hover { background: var(--hw-chip); }
.im-bubble-quote-icon {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  background: var(--hw-sky);
  color: var(--hw-primary);
  flex: none;
}
.im-bubble-quote.right .im-bubble-quote-icon {
  background: rgba(255, 255, 255, 0.24);
  color: #fff;
}
.im-bubble-quote-copy {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.im-bubble-quote-copy strong {
  font-size: 12px;
  font-weight: 500;
  color: var(--hw-primary);
}
.im-bubble-quote-copy em {
  font-style: normal;
  font-size: 12px;
  color: var(--hw-tertiary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.im-bubble-files {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}
.im-bubble-file {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 0;
  border-radius: 10px;
  background: var(--hw-card);
  color: var(--hw-ink);
  text-align: left;
  cursor: pointer;
  max-width: 280px;
}
.im-bubble-file-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--hw-sky);
  color: var(--hw-primary);
  flex: none;
}
.im-bubble-file-copy { display: flex; flex-direction: column; min-width: 0; }
.im-bubble-file-copy strong {
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.im-bubble-file-copy small { font-size: 11px; color: var(--hw-muted); }
.im-bubble-more {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--hw-muted);
  cursor: pointer;
  opacity: 0;
  flex: none;
  transition: opacity 0.15s, background 0.15s;
}
.im-bubble-line:hover .im-bubble-more { opacity: 1; }
.im-bubble-more:hover { background: var(--hw-chip); }
</style>
