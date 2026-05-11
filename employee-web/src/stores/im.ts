import { defineStore } from 'pinia';
import { employeeApi } from '../api';
import type {
  CreateGroupConversationRequest,
  ImConversationSummaryResponse,
  ImMessageListQuery,
  ImMessageResponse,
  SendImMessageRequest,
  StartPrivateConversationRequest,
  UpdateReadPositionRequest,
} from '../api';
import type { ConversationMessage, ConversationRecord, ConversationTone } from './shell';

export interface EmployeeImApi {
  conversations(): Promise<ImConversationSummaryResponse[]>;
  messages(conversationId: number, query?: ImMessageListQuery): Promise<ImMessageResponse[]>;
  startPrivateConversation(request: StartPrivateConversationRequest): Promise<ImConversationSummaryResponse>;
  createGroupConversation(request: CreateGroupConversationRequest): Promise<ImConversationSummaryResponse>;
  sendMessage(conversationId: number, request: SendImMessageRequest): Promise<ImMessageResponse>;
  updateReadPosition(conversationId: number, request: UpdateReadPositionRequest): Promise<ImConversationSummaryResponse>;
}

export const useImStore = defineStore('employee-im', {
  state: () => ({
    conversations: [] as ConversationRecord[],
    messagesByConversation: {} as Record<string, ConversationMessage[]>,
    lastMessageSeqByConversation: {} as Record<string, number>,
    lastReadSeqByConversation: {} as Record<string, number>,
    loading: false,
    messageLoading: false,
    messageSending: false,
    readPositionUpdating: false,
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
        this.lastMessageSeqByConversation = Object.fromEntries(
          conversations.map((conversation) => [String(conversation.conversationId), conversation.lastMessageSeq]),
        );
        this.lastReadSeqByConversation = Object.fromEntries(
          conversations.map((conversation) => [String(conversation.conversationId), conversation.lastReadSeq]),
        );
        this.loadedAt = new Date().toISOString();
      } catch (error) {
        this.error = error instanceof Error ? error.message : '会话加载失败';
      } finally {
        this.loading = false;
      }
    },
    async loadMessages(
      conversationId: number,
      currentUserId?: number,
      query: ImMessageListQuery = {},
      imApi: EmployeeImApi = employeeApi.im,
    ) {
      this.messageLoading = true;
      this.error = '';
      try {
        const messages = await imApi.messages(conversationId, query);
        const key = String(conversationId);
        const mappedMessages = messages.map((message) => mapImMessageToConversationMessage(message, currentUserId));
        if (query.beforeSeq) {
          const existing = this.messagesByConversation[key] ?? [];
          const existingIds = new Set(existing.map((message) => message.id));
          const prepended = mappedMessages.filter((message) => !existingIds.has(message.id));
          this.messagesByConversation[key] = [...prepended, ...existing];
        } else {
          this.messagesByConversation[key] = mappedMessages;
        }
        const lastSeq = messages[messages.length - 1]?.seqNo;
        if (typeof lastSeq === 'number') {
          this.lastMessageSeqByConversation[key] = Math.max(this.lastMessageSeqByConversation[key] ?? 0, lastSeq);
        }
      } catch (error) {
        this.error = error instanceof Error ? error.message : '消息加载失败';
      } finally {
        this.messageLoading = false;
      }
    },
    async loadOlderMessages(
      conversationId: number,
      beforeSeq: number,
      currentUserId?: number,
      size = 20,
      imApi: EmployeeImApi = employeeApi.im,
    ) {
      return this.loadMessages(conversationId, currentUserId, { beforeSeq, size }, imApi);
    },
    async startPrivateConversation(targetUserId: number, imApi: EmployeeImApi = employeeApi.im) {
      this.loading = true;
      this.error = '';
      try {
        const conversation = await imApi.startPrivateConversation({ targetUserId });
        return this.upsertConversation(conversation);
      } catch (error) {
        this.error = error instanceof Error ? error.message : '私聊创建失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async createGroupConversation(title: string, memberUserIds: number[], imApi: EmployeeImApi = employeeApi.im) {
      this.loading = true;
      this.error = '';
      try {
        const conversation = await imApi.createGroupConversation({ title, memberUserIds });
        return this.upsertConversation(conversation);
      } catch (error) {
        this.error = error instanceof Error ? error.message : '群聊创建失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async sendMessage(conversationId: number, text: string, currentUserId?: number, imApi: EmployeeImApi = employeeApi.im) {
      const normalizedText = text.trim();
      if (!normalizedText) return null;
      this.messageSending = true;
      this.error = '';
      try {
        const key = String(conversationId);
        const message = await imApi.sendMessage(conversationId, {
          messageType: 'TEXT',
          text: normalizedText,
          clientMessageId: `web-${conversationId}-${Date.now()}`,
        });
        const record = mapImMessageToConversationMessage(message, currentUserId);
        const existing = this.messagesByConversation[key] ?? [];
        const duplicateIndex = existing.findIndex((item) => item.id === record.id);
        if (duplicateIndex >= 0) {
          const next = [...existing];
          next[duplicateIndex] = record;
          this.messagesByConversation[key] = next;
        } else {
          this.messagesByConversation[key] = [...existing, record];
        }
        this.lastMessageSeqByConversation[key] = Math.max(this.lastMessageSeqByConversation[key] ?? 0, message.seqNo);
        this.lastReadSeqByConversation[key] = Math.max(this.lastReadSeqByConversation[key] ?? 0, message.seqNo);
        return record;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '消息发送失败';
        throw error;
      } finally {
        this.messageSending = false;
      }
    },
    async updateReadPosition(conversationId: number, lastReadSeq: number, imApi: EmployeeImApi = employeeApi.im) {
      this.readPositionUpdating = true;
      this.error = '';
      try {
        const conversation = await imApi.updateReadPosition(conversationId, { lastReadSeq });
        const key = String(conversationId);
        this.lastReadSeqByConversation[key] = conversation.lastReadSeq;
        this.lastMessageSeqByConversation[key] = conversation.lastMessageSeq;
        const record = this.conversations.find((item) => item.id === key);
        if (record) {
          record.unread = conversation.unreadCount;
          record.unreadLabel = conversation.unreadCount > 0 ? '未读消息' : '已读';
        }
        return conversation;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '已读状态更新失败';
        throw error;
      } finally {
        this.readPositionUpdating = false;
      }
    },
    upsertConversation(conversation: ImConversationSummaryResponse) {
      const record = mapImConversationToRecord(conversation);
      const key = String(conversation.conversationId);
      const existingIndex = this.conversations.findIndex((item) => item.id === key);
      if (existingIndex >= 0) {
        const next = [...this.conversations];
        next[existingIndex] = {
          ...next[existingIndex],
          ...record,
          messages: this.messagesByConversation[key] ?? next[existingIndex].messages,
        };
        this.conversations = next;
      } else {
        this.conversations = [record, ...this.conversations];
      }
      this.lastMessageSeqByConversation[key] = conversation.lastMessageSeq;
      this.lastReadSeqByConversation[key] = conversation.lastReadSeq;
      return record;
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
