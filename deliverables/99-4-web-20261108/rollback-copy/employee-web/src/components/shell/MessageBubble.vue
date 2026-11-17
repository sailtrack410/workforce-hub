<script setup lang="ts">
import { computed } from 'vue';
import AppAvatar from '../ui/AppAvatar.vue';
import type { ConversationMessage, MessageMention } from '../../stores/shell';
import type { EntityId } from '../../api';
import { renderMarkdownText } from '../../utils/markdown';
import { statusText } from '../../utils/employee-format';

interface MessageQuotePreview {
  id: string;
  author: string;
  body: string;
  visible: boolean;
}

const props = defineProps<{
  message: ConversationMessage;
  agent?: boolean;
  avatarUrl?: string;
  quote?: MessageQuotePreview | null;
  senderMention?: MessageMention | null;
}>();

const emit = defineEmits<{
  action: [message: ConversationMessage, event: MouseEvent];
  attachment: [message: ConversationMessage, attachmentId: EntityId, action: 'preview' | 'download'];
  quote: [messageId: EntityId | string];
  mention: [mention: MessageMention];
  insertMention: [mention: MessageMention];
}>();

const renderedMarkdown = computed(() => (
  props.agent && props.message.type === 'text'
    ? renderMarkdownText(props.message.body)
    : ''
));

const bubbleTitle = computed(() => (
  props.message.time ? `${props.message.author} · ${props.message.time}` : props.message.author
));

const bodyParts = computed(() => {
  const mentions = props.message.mentions ?? [];
  if (!mentions.length) return [{ type: 'text' as const, text: props.message.body }];
  const ranges: Array<{ start: number; end: number; mention: MessageMention }> = [];
  mentions.forEach((mention) => {
    const token = `@${mention.originalName || mention.name}`;
    let from = 0;
    while (from < props.message.body.length) {
      const start = props.message.body.indexOf(token, from);
      if (start < 0) break;
      ranges.push({ start, end: start + token.length, mention });
      from = start + token.length;
    }
  });
  ranges.sort((a, b) => a.start - b.start);
  const parts: Array<{ type: 'text'; text: string } | { type: 'mention'; text: string; mention: MessageMention }> = [];
  let cursor = 0;
  ranges.forEach((range) => {
    if (range.start < cursor) return;
    if (range.start > cursor) parts.push({ type: 'text', text: props.message.body.slice(cursor, range.start) });
    parts.push({ type: 'mention', text: props.message.body.slice(range.start, range.end), mention: range.mention });
    cursor = range.end;
  });
  if (cursor < props.message.body.length) parts.push({ type: 'text', text: props.message.body.slice(cursor) });
  return parts.length ? parts : [{ type: 'text' as const, text: props.message.body }];
});

function openMessageAction(event: MouseEvent) {
  if (props.message.status === 'RECALLED') return;
  event.preventDefault();
  emit('action', props.message, event);
}
</script>

<template>
  <article
    class="message-bubble"
    :class="[message.side, { agent }]"
    :title="bubbleTitle"
    @contextmenu="openMessageAction"
  >
    <AppAvatar
      :label="message.author"
      :image="avatarUrl"
      :kind="agent ? 'agent' : 'user'"
      :tone="agent ? 'success' : message.side === 'right' ? 'primary' : 'neutral'"
      :status="agent ? 'online' : ''"
    />
    <div class="bubble-stack">
      <div v-if="message.side !== 'right'" class="bubble-meta">
        <button
          v-if="senderMention"
          class="bubble-author-mention"
          type="button"
          @click.stop="emit('insertMention', senderMention)"
        >
          {{ message.author }}<span>@</span>
        </button>
        <strong v-else>{{ message.author }}</strong>
      </div>
      <div class="bubble-content">
        <button
          v-if="quote"
          class="bubble-quote"
          type="button"
          :disabled="!quote.visible"
          @click.stop="emit('quote', quote.id)"
        >
          <span class="bubble-quote-rail" aria-hidden="true" />
          <span class="bubble-quote-copy">
            <strong class="bubble-quote-author">{{ quote.author }}：</strong>
            <span class="bubble-quote-body">{{ quote.body }}</span>
          </span>
        </button>
        <div v-if="renderedMarkdown" class="bubble-markdown" v-html="renderedMarkdown" />
        <p v-else>
          <template v-for="(part, index) in bodyParts" :key="`${part.type}-${index}`">
            <button
              v-if="part.type === 'mention'"
              class="bubble-mention-token"
              type="button"
              @click.stop="emit('mention', part.mention)"
            >
              {{ part.text }}
            </button>
            <span v-else>{{ part.text }}</span>
          </template>
        </p>
        <p v-if="message.translatedText" class="bubble-translation">{{ message.translatedText }}</p>
        <div v-if="message.attachments?.length" class="bubble-attachments">
          <span v-for="file in message.attachments" :key="file.id" class="bubble-attachment">
            <strong>{{ file.name }}</strong>
            <small>{{ file.type }} · {{ file.size }} · {{ statusText(file.status) }}</small>
            <span class="attachment-actions">
              <button
                v-if="file.attachmentRefId"
                type="button"
                @click="emit('attachment', message, file.attachmentRefId, 'preview')"
              >
                预览
              </button>
              <button
                v-if="file.attachmentRefId"
                type="button"
                @click="emit('attachment', message, file.attachmentRefId, 'download')"
              >
                下载
              </button>
            </span>
          </span>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.message-bubble {
  display: flex;
  align-items: flex-end;
  gap: var(--space-10, 0.625rem);
  min-width: 0;
  width: fit-content;
  max-width: min(44rem, 100%);
}

.message-bubble .ui-avatar-wrap {
  flex: 0 0 auto;
}

.message-bubble.right {
  margin-left: auto;
  flex-direction: row-reverse;
}

.bubble-stack {
  display: grid;
  gap: var(--space-4, 0.25rem);
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
}

.message-bubble.right .bubble-stack {
  justify-items: end;
}

.bubble-content {
  display: grid;
  gap: var(--space-10, 0.625rem);
  max-width: 100%;
  padding: var(--space-10, 0.625rem) var(--space-14, 0.875rem);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8) var(--radius-8) var(--radius-8) var(--radius-4);
  background: var(--surface-panel);
  box-shadow: var(--shadow-xs);
  overflow-wrap: anywhere;
}

.message-bubble.right .bubble-content {
  border-color: color-mix(in srgb, var(--success-300) 48%, var(--border-subtle));
  border-radius: var(--radius-8) var(--radius-8) var(--radius-4) var(--radius-8);
  background: color-mix(in srgb, var(--success-100) 46%, var(--surface-panel));
}

:global([data-theme='dark']) .message-bubble.right .bubble-content {
  border-color: color-mix(in srgb, var(--success-500) 42%, var(--border-subtle));
  background: color-mix(in srgb, var(--success-900) 38%, var(--surface-panel));
}

.message-bubble.agent .bubble-content {
  border-color: color-mix(in srgb, var(--primary-300) 46%, var(--border-subtle));
  background: color-mix(in srgb, var(--surface-accent) 58%, var(--surface-panel));
}

.bubble-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-8);
  min-width: 0;
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.bubble-meta strong {
  color: var(--text-primary);
}

.bubble-author-mention {
  display: inline-flex;
  align-items: center;
  gap: 0.125rem;
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.bubble-author-mention span {
  display: none;
}

.bubble-author-mention:hover,
.bubble-author-mention:focus-visible {
  color: var(--text-accent);
  outline: none;
}

.bubble-author-mention:hover span,
.bubble-author-mention:focus-visible span {
  display: inline;
}

.bubble-meta strong,
.bubble-meta span,
.bubble-meta time {
  min-width: 0;
  max-width: 100%;
  overflow-wrap: anywhere;
}

.bubble-content p {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
  line-height: 1.65;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-mention-token {
  display: inline;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-accent);
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.bubble-mention-token:hover,
.bubble-mention-token:focus-visible {
  outline: none;
  text-decoration: underline;
  text-underline-offset: 0.125rem;
}

.bubble-quote {
  display: grid;
  grid-template-columns: 0.1875rem minmax(0, 1fr);
  align-items: start;
  gap: var(--space-10, 0.625rem);
  width: 100%;
  max-width: 100%;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
  color: var(--text-tertiary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.bubble-quote:disabled {
  cursor: default;
  opacity: 0.82;
}

.bubble-quote:hover:not(:disabled),
.bubble-quote:focus-visible {
  outline: none;
  color: var(--text-secondary);
}

.bubble-quote-copy {
  display: grid;
  gap: var(--space-4, 0.25rem);
  min-width: 0;
}

.bubble-quote-rail {
  width: 0.1875rem;
  min-height: 100%;
  border-radius: 999px;
  background: color-mix(in srgb, var(--primary-200) 92%, var(--surface-panel));
}

.message-bubble.right .bubble-quote-rail {
  background: color-mix(in srgb, var(--success-200) 92%, var(--surface-panel));
}

.message-bubble.agent .bubble-quote-rail {
  background: color-mix(in srgb, var(--primary-300) 88%, var(--surface-panel));
}

.bubble-quote-author {
  display: block;
  min-width: 0;
  overflow: hidden;
  color: color-mix(in srgb, var(--text-secondary) 78%, var(--surface-panel));
  font-size: var(--text-body-sm);
  line-height: 1.3;
  font-weight: 600;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bubble-quote-body {
  display: -webkit-box;
  min-width: 0;
  overflow: hidden;
  color: color-mix(in srgb, var(--text-secondary) 72%, var(--surface-panel));
  font-size: var(--text-body-sm);
  line-height: 1.4;
  text-align: left;
  text-overflow: ellipsis;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.bubble-translation {
  padding-top: var(--space-2, 0.125rem);
  border-top: 1px solid var(--border-subtle);
  color: var(--text-secondary) !important;
}

.bubble-markdown {
  display: grid;
  gap: var(--space-8, 0.5rem);
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
  line-height: 1.65;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.bubble-markdown:first-child {
  margin-top: 0;
}

.bubble-markdown :deep(p),
.bubble-markdown :deep(ul),
.bubble-markdown :deep(ol),
.bubble-markdown :deep(blockquote),
.bubble-markdown :deep(pre),
.bubble-markdown :deep(h3),
.bubble-markdown :deep(h4),
.bubble-markdown :deep(h5),
.bubble-markdown :deep(h6) {
  margin: 0;
}

.bubble-markdown :deep(h3),
.bubble-markdown :deep(h4),
.bubble-markdown :deep(h5),
.bubble-markdown :deep(h6) {
  color: var(--text-primary);
  font-size: var(--text-body-md);
  line-height: 1.45;
}

.bubble-markdown :deep(ul),
.bubble-markdown :deep(ol) {
  display: grid;
  gap: var(--space-4, 0.25rem);
  padding-left: 1.15rem;
}

.bubble-markdown :deep(blockquote) {
  padding-left: var(--space-10, 0.625rem);
  border-left: 3px solid var(--primary-300);
  color: var(--text-secondary);
}

.bubble-markdown :deep(code) {
  padding: 0.08rem 0.25rem;
  border-radius: 0.25rem;
  background: var(--surface-accent);
  color: var(--text-accent);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.92em;
}

.bubble-markdown :deep(pre) {
  max-width: 100%;
  overflow: auto;
  padding: var(--space-10, 0.625rem);
  border-radius: var(--radius-8);
  background: var(--surface-rail);
  color: var(--text-rail);
}

.bubble-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.bubble-markdown :deep(.json-fold) {
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-sunken, var(--surface-accent));
  overflow: hidden;
}

.bubble-markdown :deep(.json-fold summary) {
  padding: var(--space-8) var(--space-12);
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
  cursor: pointer;
  user-select: none;
  list-style: none;
  display: flex;
  align-items: center;
  gap: var(--space-4);
}

.bubble-markdown :deep(.json-fold summary)::before {
  content: '▸';
  display: inline-block;
  transition: transform var(--transition-fast, 0.15s ease);
}

.bubble-markdown :deep(.json-fold[open] summary)::before {
  transform: rotate(90deg);
}

.bubble-markdown :deep(.json-fold summary:hover) {
  color: var(--text-secondary);
}

.bubble-markdown :deep(.json-fold pre) {
  margin: 0;
  border-radius: 0;
  border-top: 1px solid var(--border-subtle);
  max-height: 16rem;
}

.bubble-markdown :deep(a) {
  color: var(--text-accent);
  font-weight: 700;
}

.bubble-attachments {
  display: grid;
  gap: var(--space-8);
}

.bubble-attachment {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-8) var(--space-12);
  align-items: center;
  padding: var(--space-10) var(--space-12);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-panel-muted);
}

.bubble-attachment strong,
.bubble-attachment small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bubble-attachment small {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.attachment-actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: var(--space-8);
}

.attachment-actions button {
  border: 0;
  background: transparent;
  color: var(--text-accent);
  font-size: var(--text-caption-md);
  font-weight: 700;
}
</style>
