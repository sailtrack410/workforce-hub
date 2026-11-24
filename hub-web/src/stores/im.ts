// hub-web IM store：会话与消息数据层
import { defineStore } from 'pinia';
import { imApi } from '@/api';
import type {
  EntityId,
  ImConversationDetailResponse,
  ImConversationSummaryResponse,
  ImMessageResponse,
} from '@/api/types';
import { cacheMessages, listCachedMessages } from '@/services/desktopMessages';
import { useSessionStore } from '@/stores/session';
import { currentContext, localStatus } from '@/services/localData';
import { durableRequest, pendingOperations } from '@/services/sync';
import { ApiError } from '@/api/http';

const MESSAGE_PAGE_SIZE = 100;

function mergeMessageRange(existing: ImMessageResponse[], page: ImMessageResponse[], range: { fromSeq: number; toSeq: number }) {
  const messages = new Map(existing.filter(message => message.status === 'PENDING' || message.seqNo < range.fromSeq || message.seqNo > range.toSeq)
    .map(message => [String(message.messageId), message]));
  for (const message of page) messages.set(String(message.messageId), message);
  return [...messages.values()].sort((a, b) => a.seqNo - b.seqNo);
}

export interface ConversationView extends ImConversationSummaryResponse {
  kind: 'team' | 'agent' | 'private' | 'system';
}

export interface MessageScrollAnchor {
  top: number;
  messageId: string;
  offset: number;
  atBottom: boolean;
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
  if (type === 'SYSTEM' && text) return text;
  if (type !== 'TEXT') return `[${MESSAGE_TYPE_LABEL[type] ?? '消息'}]`;
  if (!text) return '暂无消息';
  if (text.startsWith('{')) return '[卡片]';
  return text;
}

function clientId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function readActiveConversationId() {
  try { return localStorage.getItem('hw-active-conversation') ?? ''; } catch { return ''; }
}

export const useImStore = defineStore('hw-im', {
  state: () => ({
    socketConnected: false,
    receivedMessage: null as ImMessageResponse | null,
    conversations: [] as ImConversationSummaryResponse[],
    messagesByConversation: {} as Record<string, ImMessageResponse[]>,
    detailsByConversation: {} as Record<string, ImConversationDetailResponse>,
    activeConversationId: '',
    loadingConversations: {} as Record<string, boolean>,
    loadingHistory: {} as Record<string, boolean>,
    hasMoreMessages: {} as Record<string, boolean>,
    messageErrors: {} as Record<string, { message: string; history: boolean } | undefined>,
    syncedMessageSeq: {} as Record<string, number>,
    refreshRequested: {} as Record<string, boolean>,
    historyRevision: {} as Record<string, number>,
    sending: false,
  }),
  actions: {
    selectConversation(conversationId: EntityId) {
      this.activeConversationId = String(conversationId);
      try { localStorage.setItem('hw-active-conversation', this.activeConversationId); } catch { /* 隐私模式不阻断 */ }
      void currentContext().then(context => { if (context) return window.workforceDesktop?.saveDraft(context, 'active-conversation', this.activeConversationId); });
    },
    async loadConversations() {
      const session = useSessionStore(), user = session.user;
      const conversations = await imApi.conversations();
      if (session.user !== user) return;
      this.conversations = conversations;
      const context = await currentContext();
      if (context && !this.activeConversationId) {
        const active = await window.workforceDesktop?.readDraft(context, 'active-conversation');
        if (session.user === user && !this.activeConversationId) this.activeConversationId = active as string || '';
      }
    },
    async syncMessagesAfterReconnect() {
      const user = useSessionStore().user;
      if (!user) return;
      await this.loadConversations().catch(() => undefined);
      if (useSessionStore().user !== user) return;
      const ids = new Set([this.activeConversationId, ...Object.keys(this.messagesByConversation).filter(id => this.messagesByConversation[id].length)]);
      for (const id of ids) {
        if (useSessionStore().user !== user) return;
        if (id) await this.loadMessages(id).catch(() => undefined);
      }
    },
    async loadMessages(conversationId: EntityId): Promise<void> {
      const key = String(conversationId);
      if (this.loadingConversations[key] || this.loadingHistory[key]) { this.refreshRequested[key] = true; return; }
      const session = useSessionStore(), user = session.user;
      if (!user) return;
      const loading = this.loadingConversations, revision = this.historyRevision[key];
      const current = () => session.user === user && this.loadingConversations === loading && this.historyRevision[key] === revision;
      loading[key] = true;
      delete this.messageErrors[key];
      // 桌面端先显示 SQLite 缓存，再以后台服务端结果覆盖；缓存为空不能当作有效离线数据。
      try {
        const context = await currentContext();
        if (!current()) return;
        if (context && !this.messagesByConversation[key]?.length) {
          const cached = await listCachedMessages(user.userId, conversationId, undefined, context).catch(() => []);
          if (!current()) return;
          if (cached.length) this.messagesByConversation[key] = cached;
        }
        const operations = await pendingOperations();
        if (!current()) return;
        const pendingIds = new Set(operations.filter(op => op.path === `/api/employee/im/conversations/${key}/messages` && op.snapshot)
          .map(op => String((op.snapshot as ImMessageResponse).messageId)));
        this.messagesByConversation[key] = (this.messagesByConversation[key] ?? []).filter(message => message.status !== 'PENDING' || pendingIds.has(String(message.messageId)));
        for (const op of operations) {
          if (op.path === `/api/employee/im/conversations/${key}/messages` && op.snapshot) this.upsertMessage(op.snapshot as ImMessageResponse);
        }
        const existing = (this.messagesByConversation[key] ?? []).filter(message => message.status !== 'PENDING');
        const saved = context && this.syncedMessageSeq[key] === undefined
          ? await window.workforceDesktop?.readDraft(context, `im-synced-seq:${key}`).catch(() => null) : null;
        if (!current()) return;
        const knownSeq = this.syncedMessageSeq[key] ?? (typeof saved === 'number' && Number.isSafeInteger(saved) ? saved : existing[0]?.seqNo ?? 0);
        const previousTop = existing.reduce((max, message) => Math.max(max, message.seqNo), knownSeq);
        let page = await imApi.messages(conversationId, { size: MESSAGE_PAGE_SIZE });
        if (!current()) return;
        const messages = [...page];
        const topSeq = Math.max(previousTop, page[page.length - 1]?.seqNo ?? 0);
        // WebSocket 收到的单条消息不能推进补拉游标，否则重连时会跳过中间缺失的消息。
        while (knownSeq > 0 && page.length === MESSAGE_PAGE_SIZE && page[0].seqNo > knownSeq) {
          const beforeSeq = page[0].seqNo;
          page = await imApi.messages(conversationId, { beforeSeq, size: MESSAGE_PAGE_SIZE });
          if (!current()) return;
          if (page.some(message => message.seqNo >= beforeSeq)) throw new Error('消息分页游标未前进，请重试');
          messages.unshift(...page);
        }
        const fromSeq = page.length < MESSAGE_PAGE_SIZE ? 0 : messages[0].seqNo;
        const range = { fromSeq, toSeq: topSeq };
        this.messagesByConversation[key] = mergeMessageRange(this.messagesByConversation[key] ?? [], messages, range);
        this.hasMoreMessages[key] = fromSeq > 0;
        if (context) {
          const cached = await window.workforceDesktop?.replaceMessages(context, key, messages, range).then(() => true).catch(() => { localStatus.error = '消息缓存写入失败'; return false; });
          if (!current()) return;
          if (cached) await window.workforceDesktop?.saveDraft(context, `im-synced-seq:${key}`, topSeq).catch(() => undefined);
        }
        if (current()) this.syncedMessageSeq[key] = topSeq;
      } catch (error) {
        if (!current()) return;
        this.messageErrors[key] = { message: error instanceof Error ? error.message : '消息同步失败', history: false };
        if (!this.messagesByConversation[key]?.length) throw error;
      } finally {
        if (this.loadingConversations === loading) loading[key] = false;
        if (current() && this.refreshRequested[key]) { this.refreshRequested[key] = false; await this.loadMessages(key); }
      }
    },
    async loadOlderMessages(conversationId: EntityId): Promise<void> {
      const key = String(conversationId);
      if (this.loadingConversations[key] || this.loadingHistory[key] || this.hasMoreMessages[key] === false) return;
      const session = useSessionStore(), user = session.user;
      if (!user) return;
      const beforeSeq = (this.messagesByConversation[key] ?? []).find(message => message.status !== 'PENDING')?.seqNo;
      if (!beforeSeq) return this.loadMessages(conversationId);
      const loading = this.loadingHistory, revision = this.historyRevision[key];
      const current = () => session.user === user && this.loadingHistory === loading && this.historyRevision[key] === revision;
      loading[key] = true;
      delete this.messageErrors[key];
      try {
        const context = await currentContext();
        if (!current()) return;
        let page: ImMessageResponse[];
        try {
          page = await imApi.messages(conversationId, { beforeSeq, size: MESSAGE_PAGE_SIZE });
        } catch (error) {
          if (!(error instanceof TypeError || error instanceof ApiError && [502, 503, 504].includes(error.status ?? 0)) || !context) throw error;
          const cached = await listCachedMessages(user.userId, conversationId, { beforeSeq, size: MESSAGE_PAGE_SIZE }, context);
          if (!current()) return;
          if (!cached.length) throw error;
          this.messagesByConversation[key] = mergeMessageRange(this.messagesByConversation[key] ?? [], cached, { fromSeq: cached[0].seqNo, toSeq: beforeSeq - 1 });
          return;
        }
        if (!current()) return;
        if (page.some(message => message.seqNo >= beforeSeq)) throw new Error('消息分页游标未前进，请重试');
        const range = { fromSeq: page.length < MESSAGE_PAGE_SIZE ? 0 : page[0].seqNo, toSeq: beforeSeq - 1 };
        this.messagesByConversation[key] = mergeMessageRange(this.messagesByConversation[key] ?? [], page, range);
        this.hasMoreMessages[key] = page.length === MESSAGE_PAGE_SIZE;
        if (context) await window.workforceDesktop?.replaceMessages(context, key, page, range).catch(() => { localStatus.error = '消息缓存写入失败'; });
      } catch (error) {
        if (!current()) return;
        this.messageErrors[key] = { message: error instanceof Error ? error.message : '历史消息加载失败', history: true };
        throw error;
      } finally {
        if (this.loadingHistory === loading) loading[key] = false;
        if (current() && this.refreshRequested[key]) { this.refreshRequested[key] = false; await this.loadMessages(key); }
      }
    },
    async resetMessageHistory(conversationId: EntityId) {
      const key = String(conversationId);
      const user = useSessionStore().user;
      this.historyRevision[key] = (this.historyRevision[key] ?? 0) + 1;
      this.messagesByConversation[key] = [];
      delete this.syncedMessageSeq[key];
      delete this.hasMoreMessages[key];
      delete this.messageErrors[key];
      const context = await currentContext();
      if (context && useSessionStore().user === user) {
        await window.workforceDesktop?.clearMessages(context, key);
        await window.workforceDesktop?.saveDraft(context, `im-synced-seq:${key}`, null);
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
      const userId = useSessionStore().user?.userId;
      if (userId && message.status !== 'PENDING') void cacheMessages(userId, [message]).catch(() => undefined);
    },
    async sendText(conversationId: EntityId, text: string, options: { quotedMessageId?: EntityId; mentionUserIds?: EntityId[] } = {}) {
      this.sending = true;
      try {
        const id = clientId(`hw-${conversationId}`);
        const body = {
          messageType: 'TEXT',
          text,
          clientMessageId: id,
          quotedMessageId: options.quotedMessageId,
          mentionUserIds: options.mentionUserIds?.length ? options.mentionUserIds : undefined,
        };
        const snapshot: ImMessageResponse = { messageId: `local-${id}`, conversationId, seqNo: Date.now(), senderId: useSessionStore().user?.userId, senderType: 'USER', messageType: 'TEXT', contentText: text, status: 'PENDING', sentAt: new Date().toISOString() };
        const result = await durableRequest<ImMessageResponse>({ id, method: 'POST', path: `/api/employee/im/conversations/${conversationId}/messages`, body, snapshot });
        const message = result.queued ? snapshot : result.value!;
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
      const context = await currentContext();
      if (context) await window.workforceDesktop?.removeMessage(context, String(messageId));
      const key = String(conversationId);
      this.messagesByConversation[key] = (this.messagesByConversation[key] ?? [])
        .filter((item) => String(item.messageId) !== String(messageId));
    },
    async markRead(conversationId: EntityId) {
      const key = String(conversationId);
      const messages = (this.messagesByConversation[key] ?? []).filter(m => m.status !== 'PENDING');
      const lastSeq = this.loadingConversations[key] || this.messageErrors[key]
        ? this.syncedMessageSeq[key] ?? 0 : messages.length ? messages[messages.length - 1].seqNo : 0;
      if (!lastSeq) return;
      try {
        const result = await durableRequest<ImConversationSummaryResponse>({ id: `read-${conversationId}-${lastSeq}`, method: 'POST', path: `/api/employee/im/conversations/${conversationId}/read-position`, body: { lastReadSeq: lastSeq } });
        const updated = result.value;
        const index = this.conversations.findIndex((c) => String(c.conversationId) === String(conversationId));
        if (index >= 0 && updated) this.conversations[index] = updated;
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
