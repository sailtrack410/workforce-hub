<script setup lang="ts">
import { computed } from 'vue';
import AppAvatar from '../ui/AppAvatar.vue';
import type { ConversationMessage } from '../../stores/shell';
import type { EntityId } from '../../api';
import { renderMarkdownText } from '../../utils/markdown';

const props = defineProps<{
  message: ConversationMessage;
  agent?: boolean;
  avatarUrl?: string;
}>();

const emit = defineEmits<{
  action: [message: ConversationMessage];
  attachment: [message: ConversationMessage, attachmentId: EntityId, action: 'preview' | 'download'];
}>();

const renderedMarkdown = computed(() => (
  props.agent && props.message.type === 'text'
    ? renderMarkdownText(props.message.body)
    : ''
));

const bubbleTitle = computed(() => (
  props.message.time ? `${props.message.author} · ${props.message.time}` : props.message.author
));

const canOpenMessageAction = computed(() => (
  props.message.side === 'right' && props.message.status !== 'RECALLED'
));

function openMessageAction(event: MouseEvent) {
  if (!canOpenMessageAction.value) return;
  event.preventDefault();
  emit('action', props.message);
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
      :tone="agent ? 'success' : message.side === 'right' ? 'primary' : 'neutral'"
      :status="agent ? 'online' : ''"
    />
    <div class="bubble-content">
      <div class="bubble-meta">
        <strong>{{ message.author }}</strong>
      </div>
      <div v-if="renderedMarkdown" class="bubble-markdown" v-html="renderedMarkdown" />
      <p v-else>{{ message.body }}</p>
      <div v-if="message.attachments?.length" class="bubble-attachments">
        <span v-for="file in message.attachments" :key="file.id" class="bubble-attachment">
          <strong>{{ file.name }}</strong>
          <small>{{ file.type }} · {{ file.size }} · {{ file.status }}</small>
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

.bubble-content {
  flex: 1 1 auto;
  min-width: 0;
  max-width: 100%;
  padding: var(--space-10, 0.625rem) var(--space-14, 0.875rem);
  border: 1px solid #e2e8f0;
  border-radius: 1rem 1rem 1rem 0.375rem;
  background: #fff;
  box-shadow: 0 1px 1px rgba(16, 24, 40, 0.04);
  overflow-wrap: anywhere;
}

.message-bubble.right .bubble-content {
  border-color: #c8e6c9;
  border-radius: 1rem 1rem 0.375rem 1rem;
  background: #d9fdd3;
}

.message-bubble.agent .bubble-content {
  border-color: #cfe3ff;
  background: #f7fbff;
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

.bubble-meta strong,
.bubble-meta span,
.bubble-meta time {
  min-width: 0;
  max-width: 100%;
  overflow-wrap: anywhere;
}

.bubble-content p {
  margin: var(--space-6, 0.375rem) 0 0;
  color: #1f2937;
  font-size: var(--text-body-sm);
  line-height: 1.65;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-markdown {
  display: grid;
  gap: var(--space-8, 0.5rem);
  margin-top: var(--space-8, 0.5rem);
  color: #1f2937;
  font-size: var(--text-body-sm);
  line-height: 1.65;
  overflow-wrap: anywhere;
  word-break: break-word;
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
  border-left: 3px solid #9ec5fe;
  color: var(--text-secondary);
}

.bubble-markdown :deep(code) {
  padding: 0.08rem 0.25rem;
  border-radius: 0.25rem;
  background: #e8f2ff;
  color: #0f4f8f;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.92em;
}

.bubble-markdown :deep(pre) {
  max-width: 100%;
  overflow: auto;
  padding: var(--space-10, 0.625rem);
  border-radius: var(--radius-8);
  background: #102033;
  color: #e8f2ff;
}

.bubble-markdown :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

.bubble-markdown :deep(a) {
  color: var(--text-accent);
  font-weight: 700;
}

.bubble-attachments {
  display: grid;
  gap: var(--space-8);
  margin-top: var(--space-12);
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
