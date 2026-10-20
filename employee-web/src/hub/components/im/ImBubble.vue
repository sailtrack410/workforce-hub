<script setup lang="ts">
/**
 * ImBubble：消息气泡，对齐画布「员工端-新消息」气泡规范。
 * 右侧（我）：蓝底白字 r16 pad 16/12 + 36 圆头像在右；
 * 左侧（他人/AI）：浅底 r16 + 36 头像在左，群聊显示发送者名。
 * 支持：引用块、附件卡、@提及高亮、悬停操作按钮。
 */
import { computed } from 'vue';
import { FileText, MoreHorizontal } from 'lucide-vue-next';
import HubAvatar from '../HubAvatar.vue';
import type { ConversationAttachment, ConversationMessage, MessageMention } from '@/stores/shell';

const props = withDefaults(defineProps<{
  message: ConversationMessage;
  avatarUrl?: string;
  showAuthor?: boolean;
  quote?: { id: string; author: string; body: string; visible: boolean } | null;
}>(), {
  avatarUrl: '',
  showAuthor: false,
  quote: null,
});

const emit = defineEmits<{
  (e: 'action', message: ConversationMessage, event: MouseEvent): void;
  (e: 'attachment', message: ConversationMessage, attachment: ConversationAttachment): void;
  (e: 'quote', id: string): void;
}>();

const isRight = computed(() => props.message.side === 'right');

// @提及高亮：把消息文本中的 @名字 片段替换为高亮 span
const bodySegments = computed(() => {
  const body = props.message.body || '';
  const mentions = props.message.mentions ?? [];
  if (!mentions.length) return [{ text: body, mention: null as MessageMention | null }];
  const names = mentions
    .flatMap((m) => [m.name, m.originalName].filter(Boolean) as string[])
    .filter((v, i, arr) => arr.indexOf(v) === i)
    .sort((a, b) => b.length - a.length);
  const segments: Array<{ text: string; mention: MessageMention | null }> = [];
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
      segments.push({ text: rest, mention: null });
      break;
    }
    if (hitIndex > 0) segments.push({ text: rest.slice(0, hitIndex), mention: null });
    const mention = mentions.find((m) => m.name === hitName || m.originalName === hitName) ?? null;
    segments.push({ text: `@${hitName}`, mention });
    rest = rest.slice(hitIndex + hitName.length + 1);
  }
  return segments;
});

function openMenu(event: MouseEvent) {
  event.stopPropagation();
  emit('action', props.message, event);
}
</script>

<template>
  <div class="im-bubble-row" :class="{ right: isRight }">
    <HubAvatar
      v-if="!isRight"
      :label="message.author"
      :image="avatarUrl"
      size="md"
      :agent="message.senderType === 'AGENT'"
    />
    <div class="im-bubble-stack" :class="{ right: isRight }">
      <small v-if="showAuthor && !isRight" class="im-bubble-author">{{ message.author }}</small>
      <div class="im-bubble-body-wrap">
        <div class="im-bubble" :class="{ right: isRight, recalled: message.status === 'RECALLED' }">
          <button
            v-if="quote" type="button" class="im-bubble-quote"
            :disabled="!quote.visible" @click="emit('quote', quote.id)"
          >
            <strong>{{ quote.author }}</strong>
            <em>{{ quote.body }}</em>
          </button>
          <p v-if="message.body" class="im-bubble-text">
            <template v-for="(seg, i) in bodySegments" :key="i">
              <span v-if="seg.mention" class="im-bubble-mention">{{ seg.text }}</span>
              <template v-else>{{ seg.text }}</template>
            </template>
          </p>
          <div v-if="message.attachments?.length" class="im-bubble-files">
            <button
              v-for="file in message.attachments" :key="file.id" type="button"
              class="im-bubble-file" @click="emit('attachment', message, file)"
            >
              <span class="im-bubble-file-icon"><FileText :size="16" :stroke-width="2" /></span>
              <span class="im-bubble-file-copy">
                <strong>{{ file.name }}</strong>
                <small>{{ file.size }}</small>
              </span>
            </button>
          </div>
        </div>
        <button
          type="button" class="im-bubble-more" aria-label="消息操作"
          @click="openMenu"
        >
          <MoreHorizontal :size="15" :stroke-width="2" />
        </button>
      </div>
      <small v-if="message.time" class="im-bubble-time">{{ message.time.slice(5, 16) }}</small>
    </div>
    <HubAvatar v-if="isRight" label="我" :image="avatarUrl" size="md" />
  </div>
</template>

<style scoped>
.im-bubble-row {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 78%;
}
.im-bubble-row.right {
  align-self: flex-end;
}
.im-bubble-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.im-bubble-stack.right { align-items: flex-end; }
.im-bubble-author {
  font-size: 12px;
  color: var(--hub-muted, #9ba6b4);
  padding-left: 2px;
}
.im-bubble-body-wrap {
  position: relative;
  display: flex;
  align-items: center;
  gap: 4px;
}
.im-bubble {
  padding: 12px 16px;
  border-radius: 16px;
  background: var(--hub-inner, #f6f8fb);
  color: var(--hub-ink, #181d23);
  min-width: 0;
}
.im-bubble.right {
  background: var(--hub-primary, #1677ff);
  color: #fff;
}
.im-bubble.recalled {
  background: var(--hub-chip, #f2f5f9);
  color: var(--hub-muted, #9ba6b4);
  font-style: italic;
}
.im-bubble-text {
  margin: 0;
  font-size: 14px;
  line-height: 22px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}
.im-bubble-mention {
  color: var(--hub-primary, #1677ff);
  font-weight: 500;
}
.im-bubble.right .im-bubble-mention { color: #d9ebff; }
.im-bubble-quote {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 8px;
  padding: 6px 10px;
  border: 0;
  border-left: 3px solid var(--hub-primary, #1677ff);
  border-radius: 8px;
  background: rgba(22, 119, 255, 0.08);
  text-align: left;
  cursor: pointer;
  max-width: 100%;
}
.im-bubble.right .im-bubble-quote {
  border-left-color: rgba(255, 255, 255, 0.6);
  background: rgba(255, 255, 255, 0.16);
}
.im-bubble-quote strong { font-size: 12px; }
.im-bubble-quote em {
  font-style: normal;
  font-size: 12px;
  opacity: 0.75;
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
  background: var(--hub-card, #fff);
  color: var(--hub-ink, #181d23);
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
  background: var(--hub-sky, #d9ebff);
  color: var(--hub-primary, #1677ff);
  flex: none;
}
.im-bubble-file-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.im-bubble-file-copy strong {
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.im-bubble-file-copy small { font-size: 11px; color: var(--hub-muted, #9ba6b4); }
.im-bubble-more {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--hub-muted, #9ba6b4);
  cursor: pointer;
  opacity: 0;
  flex: none;
  transition: opacity 0.15s, background 0.15s;
}
.im-bubble-body-wrap:hover .im-bubble-more { opacity: 1; }
.im-bubble-more:hover { background: var(--hub-chip, #f2f5f9); }
.im-bubble-time {
  font-size: 11px;
  color: var(--hub-muted, #9ba6b4);
  padding: 0 2px;
}
</style>
