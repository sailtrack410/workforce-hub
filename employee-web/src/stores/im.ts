import { defineStore } from 'pinia';
import { employeeApi } from '../api';
import type { ImConversationSummaryResponse, ImMessageResponse, SendImMessageRequest } from '../api';
import type { ConversationMessage, ConversationRecord, ConversationTone } from './shell';

export interface EmployeeImApi {
  conversations(): Promise<ImConversationSummaryResponse[]>;
  messages(conversationId: number): Promise<ImMessageResponse[]>;
  sendMessage(conversationId: number, request: SendImMessageRequest): Promise<ImMessageResponse>;
}

export const useImStore = defineStore('employee-im', {
  state: () => ({
    conversations: [] as ConversationRecord[],
    messagesByConversation: {} as Record<string, ConversationMessage[]>,
    loading: false,
    messageLoading: false,
    messageSending: false,
    error: '',
    loadedAt: null as string | null,
  }),
  actions: {
    async loadConversations(imApi: EmployeeImApi = employeeApi.im) {
      this.loading = true;
      this.error = '';
      try {
        const conversations = await imApi.conversations();
        this.conversations = conversations.map(mapImConversationToRecord);
        this.loadedAt = new Date().toISOString();
      } catch (error) {
        this.error = error instanceof Error ? error.message : '会话加载失败';
      } finally {
        this.loading = false;
      }
    },
    async loadMessages(conversationId: number, currentUserId?: number, imApi: EmployeeImApi = employeeApi.im) {
      this.messageLoading = true;
      this.error = '';
      try {
        const messages = await imApi.messages(conversationId);
        this.messagesByConversation[String(conversationId)] = messages
          .map((message) => mapImMessageToConversationMessage(message, currentUserId));
      } catch (error) {
        this.error = error instanceof Error ? error.message : '消息加载失败';
      } finally {
        this.messageLoading = false;
      }
    },
    async sendMessage(conversationId: number, text: string, currentUserId?: number, imApi: EmployeeImApi = employeeApi.im) {
      const normalizedText = text.trim();
      if (!normalizedText) return null;
      this.messageSending = true;
      this.error = '';
      try {
        const message = await imApi.sendMessage(conversationId, {
          messageType: 'TEXT',
          text: normalizedText,
        });
        const record = mapImMessageToConversationMessage(message, currentUserId);
        const key = String(conversationId);
        this.messagesByConversation[key] = [
          ...(this.messagesByConversation[key] ?? []),
          record,
        ];
        return record;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '消息发送失败';
        throw error;
      } finally {
        this.messageSending = false;
      }
    },
  },
});

export function mapImConversationToRecord(conversation: ImConversationSummaryResponse): ConversationRecord {
  const kind = normalizeConversationKind(conversation.conversationType);
  const title = conversation.title || fallbackConversationTitle(conversation.conversationType);
  const lastMessage = conversation.lastMessage ? mapImMessageToConversationMessage(conversation.lastMessage) : null;
  const summaryBody = lastMessage?.body || '暂无消息';
  return {
    id: String(conversation.conversationId),
    kind,
    tone: toneOfKind(kind),
    badge: title.slice(0, 2),
    title,
    subtitle: summaryBody,
    meta: formatConversationTime(conversation.lastMessageAt),
    tag: `${conversation.conversationType} · ${conversation.status}`,
    unread: conversation.unreadCount,
    flow: flowOfConversationType(conversation.conversationType),
    status: conversation.status,
    unreadLabel: conversation.unreadCount > 0 ? '未读消息' : '已读',
    summaryTitle: title,
    summaryBody,
    cards: [
      { label: '会话类型', value: conversation.conversationType, hint: 'IM 类型' },
      { label: '未读数', value: String(conversation.unreadCount), hint: '基于 lastReadSeq 计算' },
      { label: '最新序号', value: String(conversation.lastMessageSeq), hint: '消息序号' },
    ],
    workflowSteps: [],
    messages: lastMessage ? [lastMessage] : [],
    panel: {
      members: [],
      tasks: [],
      attachments: [],
    },
  };
}

export function mapImMessageToConversationMessage(message: ImMessageResponse, currentUserId?: number): ConversationMessage {
  const sentByCurrentUser = currentUserId !== undefined && message.senderType === 'USER' && message.senderId === currentUserId;
  return {
    id: String(message.messageId),
    type: message.messageType === 'CARD' ? 'process-card' : 'text',
    author: sentByCurrentUser ? '我' : authorOfMessage(message),
    role: `${message.senderType} · ${message.messageType}`,
    body: message.contentText || parseTextFromContentJson(message.contentJson) || '[非文本消息]',
    side: resolveMessageSide(message, sentByCurrentUser),
    time: formatConversationTime(message.sentAt),
  };
}

function resolveMessageSide(message: ImMessageResponse, sentByCurrentUser: boolean): ConversationMessage['side'] {
  if (message.senderType === 'SYSTEM') return 'system';
  return sentByCurrentUser ? 'right' : 'left';
}

function normalizeConversationKind(conversationType: string): ConversationRecord['kind'] {
  if (conversationType === 'GROUP') return 'team';
  if (conversationType === 'AGENT') return 'agent';
  return 'team';
}

function toneOfKind(kind: ConversationRecord['kind']): ConversationTone {
  if (kind === 'agent') return 'green';
  if (kind === 'team') return 'blue';
  return 'gray';
}

function flowOfConversationType(conversationType: string) {
  if (conversationType === 'AGENT') return 'Agent 会话';
  if (conversationType === 'GROUP') return '团队协作';
  if (conversationType === 'PRIVATE') return '私聊';
  return '会话';
}

function fallbackConversationTitle(conversationType: string) {
  if (conversationType === 'GROUP') return '群组会话';
  if (conversationType === 'AGENT') return 'Agent 会话';
  return '私聊会话';
}

function authorOfMessage(message: ImMessageResponse) {
  if (message.senderType === 'SYSTEM') return '系统';
  if (message.senderType === 'AGENT') return 'Agent';
  return `用户 ${message.senderId}`;
}

function formatConversationTime(value?: string | null) {
  if (!value) return '';
  return value.replace('T', ' ').slice(0, 16);
}

function parseTextFromContentJson(contentJson?: string | null) {
  if (!contentJson) return '';
  try {
    const content = JSON.parse(contentJson) as { text?: unknown };
    return typeof content.text === 'string' ? content.text : '';
  } catch {
    return '';
  }
}
