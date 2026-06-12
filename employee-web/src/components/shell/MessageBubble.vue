<script setup lang="ts">
import AppAvatar from '../ui/AppAvatar.vue';
import AppTag from '../ui/AppTag.vue';
import type { ConversationMessage } from '../../stores/shell';
import type { EntityId } from '../../api';

defineProps<{
  message: ConversationMessage;
  agent?: boolean;
}>();

const emit = defineEmits<{
  action: [message: ConversationMessage];
  attachment: [message: ConversationMessage, attachmentId: EntityId, action: 'preview' | 'download'];
}>();
</script>

<template>
  <article class="message-bubble" :class="[message.side, { agent }]">
    <AppAvatar
      :label="message.author.slice(0, 1)"
      :tone="agent ? 'success' : message.side === 'right' ? 'primary' : 'neutral'"
      :status="agent ? 'online' : ''"
    />
    <div class="bubble-content">
      <div class="bubble-meta">
        <strong>{{ message.author }}</strong>
        <AppTag v-if="agent" tone="success">AI 助手</AppTag>
        <span v-else>{{ message.role }}</span>
        <time v-if="message.time">{{ message.time }}</time>
        <button
          v-if="message.side === 'right' && message.status !== 'RECALLED'"
          class="message-action"
          type="button"
          @click="emit('action', message)"
        >
          操作
        </button>
      </div>
      <p>{{ message.body }}</p>
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
  max-width: min(44rem, calc(100% - 4rem));
}

.message-bubble.right {
  margin-left: auto;
  flex-direction: row-reverse;
}

.bubble-content {
  padding: var(--space-10, 0.625rem) var(--space-14, 0.875rem);
  border: 1px solid #e2e8f0;
  border-radius: 1rem 1rem 1rem 0.375rem;
  background: #fff;
  box-shadow: 0 1px 1px rgba(16, 24, 40, 0.04);
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
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.bubble-meta strong {
  color: var(--text-primary);
}

.message-action {
  border: 0;
  background: transparent;
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.message-action:hover,
.message-action:focus-visible {
  color: var(--text-accent);
}

.bubble-content p {
  margin: var(--space-6, 0.375rem) 0 0;
  color: #1f2937;
  font-size: var(--text-body-sm);
  line-height: 1.65;
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
