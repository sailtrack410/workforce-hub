// hub-web IM store：会话与消息数据层
import { defineStore } from 'pinia';
import { imApi } from '@/api';
import type {
  EntityId,
  ImConversationDetailResponse,
  ImConversationSummaryResponse,
  ImMessageResponse,
} from '@/api/types';

export interface ConversationView extends ImConversationSummaryResponse {
  kind: 'team' | 'agent' | 'private' | 'system';
}

export function kindOf(conversation: ImConversationSummaryResponse): ConversationView['kind'] {
  const type = (conversation.conversationType || '').toUpperCase();
  if (type === 'GROUP') return 'team';
  if (type === 'AGENT') return 'agent';
  if (type === 'PRIVATE') return 'private';
  return 'system';
}

const MESSAGE_TYPE_LABEL: Record<string, string> = {
  FILE: '文件',
  IMAGE: '图片',
  CARD: '卡片',
  AGENT_RESULT: '智能回答',
};

export function lastMessageText(conversation: ImConversationSummaryResponse): string {
  const last = conversation.lastMessage;
  const text = (last?.contentText || '').trim();
  const type = (last?.messageType || 'TEXT').toUpperCase();
  if (type !== 'TEXT') return `[${MESSAGE_TYPE_LABEL[type] ?? '消息'}]`;
  if (!text) return '暂无消息';
  if (text.startsWith('{')) return '[卡片]';
  return text;
}

function clientId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export const useImStore = defineStore('hw-im', {
  state: () => ({
    conversations: [] as ImConversationSummaryResponse[],
    messagesByConversation: {} as Record<string, ImMessageResponse[]>,
    detailsByConversation: {} as Record<string, ImConversationDetailResponse>,
    messagesLoading: false,
    sending: false,
  }),
  actions: {
    async loadConversations() {
      this.conversations = await imApi.conversations();
    },
    async loadMessages(conversationId: EntityId) {
      this.messagesLoading = true;
      try {
        const messages = await imApi.messages(conversationId, { limit: 100 });
        this.messagesByConversation[String(conversationId)] = messages;
      } finally {
        this.messagesLoading = false;
      }
    },
    async loadDetail(conversationId: EntityId) {
      const detail = await imApi.detail(conversationId);
      this.detailsByConversation[String(conversationId)] = detail;
      return detail;
    },
    upsertMessage(message: ImMessageResponse) {
      const key = String(message.conversationId);
      const list = this.messagesByConversation[key] ?? [];
      const index = list.findIndex((item) => String(item.messageId) === String(message.messageId));
      if (index >= 0) list[index] = message;
      else list.push(message);
      list.sort((a, b) => a.seqNo - b.seqNo);
      this.messagesByConversation[key] = list;
    },
    async sendText(conversationId: EntityId, text: string, options: { quotedMessageId?: EntityId; mentionUserIds?: EntityId[] } = {}) {
      this.sending = true;
      try {
        const message = await imApi.sendMessage(conversationId, {
          messageType: 'TEXT',
          text,
          clientMessageId: clientId(`hw-${conversationId}`),
          quotedMessageId: options.quotedMessageId,
          mentionUserIds: options.mentionUserIds?.length ? options.mentionUserIds : undefined,
        });
        this.upsertMessage(message);
        return message;
      } finally {
        this.sending = false;
      }
    },
    async sendFile(conversationId: EntityId, attachmentRefId: EntityId) {
      this.sending = true;
      try {
        const message = await imApi.sendMessage(conversationId, {
          messageType: 'FILE',
          attachmentRefIds: [attachmentRefId],
          clientMessageId: clientId(`hw-file-${conversationId}`),
        });
        this.upsertMessage(message);
        return message;
      } finally {
        this.sending = false;
      }
    },
    async recall(conversationId: EntityId, messageId: EntityId) {
      const message = await imApi.recallMessage(conversationId, messageId);
      this.upsertMessage(message);
    },
    async remove(conversationId: EntityId, messageId: EntityId) {
      await imApi.deleteMessage(conversationId, messageId);
      const key = String(conversationId);
      this.messagesByConversation[key] = (this.messagesByConversation[key] ?? [])
        .filter((item) => String(item.messageId) !== String(messageId));
    },
    async markRead(conversationId: EntityId) {
      const messages = this.messagesByConversation[String(conversationId)] ?? [];
      const lastSeq = messages.length ? messages[messages.length - 1].seqNo : 0;
      if (!lastSeq) return;
      try {
        const updated = await imApi.updateReadPosition(conversationId, lastSeq);
        const index = this.conversations.findIndex((c) => String(c.conversationId) === String(conversationId));
        if (index >= 0) this.conversations[index] = updated;
      } catch { /* 已读回写失败不阻断 */ }
    },
    upsertConversation(conversation: ImConversationSummaryResponse) {
      const index = this.conversations.findIndex(
        (c) => String(c.conversationId) === String(conversation.conversationId));
      if (index >= 0) this.conversations[index] = conversation;
      else this.conversations.unshift(conversation);
    },
  },
});
