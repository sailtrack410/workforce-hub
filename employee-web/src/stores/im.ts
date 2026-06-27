import { defineStore } from 'pinia';
import { employeeApi } from '../api';
import type {
  CreateGroupConversationRequest,
  CreateDownloadTicketRequest,
  DownloadTicketResponse,
  EditImMessageRequest,
  EntityId,
  GroupMemberUserIdsRequest,
  ImConversationListQuery,
  ImConversationDetailResponse,
  ImConversationMemberResponse,
  ImConversationSummaryResponse,
  ImGroupProfileResponse,
  ImMessageListQuery,
  ImMessageResponse,
  SendImMessageRequest,
  StartAgentConversationRequest,
  StartPrivateConversationRequest,
  UpdateConversationPreferenceRequest,
  UpdateGroupConversationRequest,
  UpdateReadPositionRequest,
} from '../api';
import type {
  ConversationAttachment,
  ConversationMessage,
  ConversationRecord,
  ConversationTone,
} from './shell';
import type {
  AgentResultData,
  AgentResultFlow,
  AgentResultRecord,
  KnowledgeAnswerAction,
  KnowledgeAnswerCard,
  KnowledgeAnswerState,
  KnowledgeApiRef,
  KnowledgeCitation,
  KnowledgeRelatedFlow,
  KnowledgeAnswerRelatedRef,
} from '@/types/knowledge';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';

export interface EmployeeImApi {
  conversations(query?: ImConversationListQuery): Promise<ImConversationSummaryResponse[]>;
  conversationDetail(conversationId: string | number): Promise<ImConversationDetailResponse>;
  groupMembers(conversationId: string | number): Promise<ImConversationMemberResponse[]>;
  groupProfile(conversationId: string | number): Promise<ImGroupProfileResponse>;
  updateGroupProfile(conversationId: string | number, request: UpdateGroupConversationRequest): Promise<ImGroupProfileResponse>;
  addGroupMembers(conversationId: string | number, request: GroupMemberUserIdsRequest): Promise<ImConversationMemberResponse[]>;
  removeGroupMembers(conversationId: string | number, request: GroupMemberUserIdsRequest): Promise<ImConversationMemberResponse[]>;
  leaveGroup(conversationId: string | number): Promise<ImConversationMemberResponse[]>;
  dismissGroup(conversationId: string | number): Promise<void>;
  messages(conversationId: string | number, query?: ImMessageListQuery): Promise<ImMessageResponse[]>;
  startPrivateConversation(request: StartPrivateConversationRequest): Promise<ImConversationSummaryResponse>;
  createGroupConversation(request: CreateGroupConversationRequest): Promise<ImConversationSummaryResponse>;
  startAgentConversation(request: StartAgentConversationRequest): Promise<ImConversationSummaryResponse>;
  sendMessage(conversationId: string | number, request: SendImMessageRequest): Promise<ImMessageResponse>;
  editMessage(conversationId: string | number, messageId: string | number, request: EditImMessageRequest): Promise<ImMessageResponse>;
  recallMessage(conversationId: string | number, messageId: string | number): Promise<ImMessageResponse>;
  createMessageAttachmentDownloadTicket(
    conversationId: string | number,
    messageId: string | number,
    attachmentRefId: EntityId,
    request: CreateDownloadTicketRequest,
  ): Promise<DownloadTicketResponse>;
  updateReadPosition(conversationId: string | number, request: UpdateReadPositionRequest): Promise<ImConversationSummaryResponse>;
  updatePreference(conversationId: string | number, request: UpdateConversationPreferenceRequest): Promise<ImConversationSummaryResponse>;
  downloadAttachmentUrl(ticketNo: string): string;
}

export interface EmployeeFilesApi {
  createDownloadTicket(attachmentRefId: EntityId, request: CreateDownloadTicketRequest): Promise<DownloadTicketResponse>;
  downloadUrl(ticketNo: string): string;
}

export const useImStore = defineStore('employee-im', {
  state: () => ({
    conversations: [] as ConversationRecord[],
    messagesByConversation: {} as Record<string, ConversationMessage[]>,
    detailsByConversation: {} as Record<string, ImConversationDetailResponse>,
    lastMessageSeqByConversation: {} as Record<string, number>,
    lastReadSeqByConversation: {} as Record<string, number>,
    loading: false,
    messageLoading: false,
    messageSending: false,
    actionLoading: false,
    readPositionUpdating: false,
    error: '',
    loadedAt: null as string | null,
    _loadMessagesSeq: 0,
    _loadDetailSeq: 0,
  }),
  actions: {
    async loadConversations(
      query: ImConversationListQuery = {},
      imApi: EmployeeImApi = employeeApi.im,
      filesApi: EmployeeFilesApi = employeeApi.files,
    ) {
      this.loading = true;
      this.error = '';
      try {
        const conversations = await imApi.conversations(query);
        this.conversations = conversations.map(mapImConversationToRecord);
        await Promise.all(conversations.map(async (conversation, index) => {
          if (!conversation.avatarFileId) return;
          try {
            const ticket = await filesApi.createDownloadTicket(conversation.avatarFileId, { downloadType: 'INLINE' });
            const url = ticket.downloadUrl || filesApi.downloadUrl(ticket.ticketNo);
            this.conversations[index].avatarUrl = /^https?:\/\//i.test(url) || url.startsWith('/') ? url : `/${url}`;
          } catch {
            this.conversations[index].avatarUrl = '';
          }
        }));
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
      conversationId: string | number,
      currentUserId?: EntityId,
      query: ImMessageListQuery = {},
      imApi: EmployeeImApi = employeeApi.im,
    ) {
      const seq = ++this._loadMessagesSeq;
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
        if (seq !== this._loadMessagesSeq) return;
        this.error = error instanceof Error ? error.message : '消息加载失败';
      } finally {
        if (seq === this._loadMessagesSeq) {
          this.messageLoading = false;
        }
      }
    },
    async loadConversationDetail(conversationId: string | number, imApi: EmployeeImApi = employeeApi.im) {
      const seq = ++this._loadDetailSeq;
      this.error = '';
      try {
        const detail = await imApi.conversationDetail(conversationId);
        const key = String(conversationId);
        this.detailsByConversation[key] = detail;
        const record = this.conversations.find((item) => item.id === key);
        if (record) {
          const mapped = mapImConversationToRecord(detail.conversation);
          record.panel.members = detail.members.map(mapConversationMember);
          if (record.kind === 'team') {
            record.memberCount = detail.members.length || record.memberCount;
          } else {
            record.memberCount = undefined;
          }
          record.title = detail.groupProfile?.title || mapped.title;
          record.summaryTitle = record.title;
          record.cards = [
            { label: '会话类型', value: mapped.flow, hint: '会话类型' },
            { label: '成员数', value: String(detail.members.length), hint: '成员' },
            { label: '会话状态', value: statusLabel(detail.conversation.status), hint: '状态' },
          ];
          if (detail.groupProfile?.noticeText) {
            record.summaryBody = detail.groupProfile.noticeText;
          }
        }
        return detail;
      } catch (error) {
        if (seq !== this._loadDetailSeq) throw error;
        this.error = error instanceof Error ? error.message : '会话详情加载失败';
        throw error;
      }
    },
    async loadVisibleConversationDetails(conversationIds?: Array<string | number>, imApi: EmployeeImApi = employeeApi.im) {
      const ids = (conversationIds?.length ? conversationIds : this.conversations.map((item) => item.id))
        .map((item) => String(item))
        .filter(Boolean)
        .slice(0, 20);
      await Promise.all(ids.map((conversationId) => this.loadConversationDetail(conversationId, imApi).catch(() => undefined)));
    },
    async loadOlderMessages(
      conversationId: string | number,
      beforeSeq: number,
      currentUserId?: EntityId,
      size = 20,
      imApi: EmployeeImApi = employeeApi.im,
    ) {
      return this.loadMessages(conversationId, currentUserId, { beforeSeq, size }, imApi);
    },
    async pollAgentReply(
      conversationId: string | number,
      afterSeq: number,
      currentUserId?: EntityId,
      options: {
        imApi?: EmployeeImApi;
        attempts?: number;
        intervalMs?: number;
      } = {},
    ) {
      const key = String(conversationId);
      const imApi = options.imApi ?? employeeApi.im;
      const attempts = Math.max(1, options.attempts ?? 8);
      const intervalMs = Math.max(0, options.intervalMs ?? 1200);
      for (let attempt = 0; attempt < attempts; attempt += 1) {
        if (attempt > 0 && intervalMs > 0) {
          await new Promise((resolve) => window.setTimeout(resolve, intervalMs));
        }
        const messages = await imApi.messages(conversationId);
        const mappedMessages = messages.map((message) => mapImMessageToConversationMessage(message, currentUserId));
        this.messagesByConversation[key] = mappedMessages;
        const lastSeq = messages[messages.length - 1]?.seqNo;
        if (typeof lastSeq === 'number') {
          this.lastMessageSeqByConversation[key] = Math.max(this.lastMessageSeqByConversation[key] ?? 0, lastSeq);
        }
        const conversation = this.conversations.find((item) => item.id === key);
        if (conversation) {
          conversation.messages = mappedMessages;
          const lastMessage = mappedMessages[mappedMessages.length - 1];
          if (lastMessage) {
            conversation.subtitle = lastMessage.body;
            conversation.meta = formatConversationDate(lastMessage.time);
          }
        }
        if (messages.some((message) => message.seqNo > afterSeq && message.senderType === 'AGENT')) {
          return true;
        }
      }
      return false;
    },
    async startPrivateConversation(targetUserId: EntityId, imApi: EmployeeImApi = employeeApi.im) {
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
    async createGroupConversation(title: string, memberUserIds: EntityId[], imApi: EmployeeImApi = employeeApi.im) {
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
    async startAgentConversation(agentId: EntityId, imApi: EmployeeImApi = employeeApi.im) {
      this.loading = true;
      this.error = '';
      try {
        const conversation = await imApi.startAgentConversation({ agentId });
        return this.upsertConversation(conversation);
      } catch (error) {
        this.error = error instanceof Error ? error.message : 'Agent 会话创建失败';
        throw error;
      } finally {
        this.loading = false;
      }
    },
    async sendMessage(conversationId: string | number, text: string, currentUserId?: EntityId, imApi: EmployeeImApi = employeeApi.im) {
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
        this.upsertMessage(key, record);
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
    async sendFileMessage(
      conversationId: string | number,
      attachmentRefId: EntityId,
      text = '',
      currentUserId?: EntityId,
      imApi: EmployeeImApi = employeeApi.im,
    ) {
      if (!attachmentRefId) return null;
      this.messageSending = true;
      this.error = '';
      try {
        const key = String(conversationId);
        const message = await imApi.sendMessage(conversationId, {
          messageType: text.trim() ? 'TEXT' : 'FILE',
          text: text.trim() || undefined,
          attachmentRefIds: [attachmentRefId],
          clientMessageId: `web-file-${conversationId}-${attachmentRefId}-${Date.now()}`,
        });
        const record = mapImMessageToConversationMessage(message, currentUserId);
        this.upsertMessage(key, record);
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
    async editMessage(
      conversationId: string | number,
      messageId: string | number,
      text: string,
      currentUserId?: EntityId,
      imApi: EmployeeImApi = employeeApi.im,
    ) {
      const normalizedText = text.trim();
      if (!normalizedText) return null;
      this.actionLoading = true;
      this.error = '';
      try {
        const key = String(conversationId);
        const message = await imApi.editMessage(conversationId, messageId, { text: normalizedText });
        const record = mapImMessageToConversationMessage(message, currentUserId);
        this.upsertMessage(key, record);
        return record;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '消息编辑失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
    async recallMessage(
      conversationId: string | number,
      messageId: string | number,
      currentUserId?: EntityId,
      imApi: EmployeeImApi = employeeApi.im,
    ) {
      this.actionLoading = true;
      this.error = '';
      try {
        const key = String(conversationId);
        const message = await imApi.recallMessage(conversationId, messageId);
        const record = mapImMessageToConversationMessage(message, currentUserId);
        this.upsertMessage(key, record);
        return record;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '消息撤回失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
    async updatePreference(
      conversationId: string | number,
      request: UpdateConversationPreferenceRequest,
      imApi: EmployeeImApi = employeeApi.im,
    ) {
      this.actionLoading = true;
      this.error = '';
      try {
        const conversation = await imApi.updatePreference(conversationId, request);
        return this.upsertConversation(conversation);
      } catch (error) {
        this.error = error instanceof Error ? error.message : '会话偏好保存失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
    async updateGroupProfile(
      conversationId: string | number,
      request: UpdateGroupConversationRequest,
      imApi: EmployeeImApi = employeeApi.im,
    ) {
      this.actionLoading = true;
      this.error = '';
      try {
        const profile = await imApi.updateGroupProfile(conversationId, request);
        const detail = this.detailsByConversation[String(conversationId)];
        if (detail) detail.groupProfile = profile;
        const record = this.conversations.find((item) => item.id === String(conversationId));
        if (record) {
          record.title = profile.title;
          record.summaryTitle = profile.title;
          if (profile.noticeText) record.summaryBody = profile.noticeText;
        }
        return profile;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '群资料保存失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
    async addGroupMembers(conversationId: string | number, memberUserIds: EntityId[], imApi: EmployeeImApi = employeeApi.im) {
      this.actionLoading = true;
      this.error = '';
      try {
        const members = await imApi.addGroupMembers(conversationId, { memberUserIds });
        this.syncMembers(conversationId, members);
        return members;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '群成员添加失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
    async removeGroupMembers(conversationId: string | number, memberUserIds: EntityId[], imApi: EmployeeImApi = employeeApi.im) {
      this.actionLoading = true;
      this.error = '';
      try {
        const members = await imApi.removeGroupMembers(conversationId, { memberUserIds });
        this.syncMembers(conversationId, members);
        return members;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '群成员移除失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
    async leaveGroup(conversationId: string | number, imApi: EmployeeImApi = employeeApi.im) {
      this.actionLoading = true;
      this.error = '';
      try {
        const members = await imApi.leaveGroup(conversationId);
        this.syncMembers(conversationId, members);
        return members;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '退群失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
    async dismissGroup(conversationId: string | number, imApi: EmployeeImApi = employeeApi.im) {
      this.actionLoading = true;
      this.error = '';
      try {
        await imApi.dismissGroup(conversationId);
        this.conversations = this.conversations.filter((item) => item.id !== String(conversationId));
        delete this.messagesByConversation[String(conversationId)];
      } catch (error) {
        this.error = error instanceof Error ? error.message : '解散群失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
    async updateReadPosition(conversationId: string | number, lastReadSeq: number, imApi: EmployeeImApi = employeeApi.im) {
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
          avatarUrl: record.avatarUrl || next[existingIndex].avatarUrl,
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
    upsertMessage(conversationId: string, message: ConversationMessage) {
      const existing = (this.messagesByConversation[conversationId] ?? [])
        .filter((item) => !(message.senderType === 'AGENT' && item.id.startsWith('agent-loading-')));
      const duplicateIndex = existing.findIndex((item) => item.id === message.id);
      if (duplicateIndex >= 0) {
        const next = [...existing];
        next[duplicateIndex] = message;
        this.messagesByConversation[conversationId] = next;
      } else {
        this.messagesByConversation[conversationId] = [...existing, message];
      }
      const conversation = this.conversations.find((item) => item.id === conversationId);
      if (conversation) {
        conversation.messages = this.messagesByConversation[conversationId];
        conversation.subtitle = message.body;
        conversation.meta = formatConversationDate(message.time);
      }
    },
    syncMembers(conversationId: string | number, members: ImConversationMemberResponse[]) {
      const key = String(conversationId);
      const record = this.conversations.find((item) => item.id === key);
      if (record) {
        record.panel.members = members.map(mapConversationMember);
        record.memberCount = record.kind === 'team' ? members.length : undefined;
      }
      const detail = this.detailsByConversation[key];
      if (detail) detail.members = members;
    },
    replaceConversations(records: ConversationRecord[]) {
      this.conversations = records;
    },
    mergeBackendMessages(conversationId: string, backendMessages: ConversationMessage[]) {
      const conversation = this.conversations.find((item) => item.id === conversationId);
      if (!conversation || !backendMessages.length) return;
      conversation.messages = backendMessages;
      conversation.panel.attachments = collectConversationAttachments(backendMessages);
      const latestMessage = backendMessages[backendMessages.length - 1];
      conversation.subtitle = latestMessage.body;
      conversation.meta = formatConversationDate(latestMessage.time) || conversation.meta;
    },
  },
});

function mapConversationMember(member: ImConversationMemberResponse) {
  return {
    name: member.displayName,
    role: memberRoleLabel(member),
    userId: member.memberType === 'USER' ? member.memberId : undefined,
    memberType: member.memberType,
  };
}

function memberRoleLabel(member: ImConversationMemberResponse) {
  if (member.memberType === 'AGENT') return 'Agent';
  if (member.memberRole === 'OWNER') return '群主';
  return '成员';
}

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
    avatarUrl: '',
    title,
    subtitle: summaryBody,
    meta: formatConversationDate(conversation.lastMessageAt),
    tag: `${flowOfConversationType(conversation.conversationType)} · ${statusLabel(conversation.status)}`,
    unread: conversation.unreadCount,
    flow: flowOfConversationType(conversation.conversationType),
    status: conversation.status,
    pinned: conversation.pin,
    muted: conversation.mute,
    unreadLabel: conversation.unreadCount > 0 ? '未读消息' : '已读',
    summaryTitle: title,
    summaryBody,
    cards: [
      { label: '会话类型', value: flowOfConversationType(conversation.conversationType), hint: '会话类型' },
      { label: '未读数', value: String(conversation.unreadCount), hint: '未读消息' },
      { label: '会话状态', value: statusLabel(conversation.status), hint: '状态' },
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

export function mapImMessageToConversationMessage(message: ImMessageResponse, currentUserId?: EntityId): ConversationMessage {
  const sentByCurrentUser = currentUserId !== undefined && message.senderType === 'USER' && String(message.senderId) === String(currentUserId);
  const content = parseMessageContentJson(message.contentJson);
  const knowledgeCard = message.messageType === 'CARD' ? parseKnowledgeAnswerCard(content) : undefined;
  const parsedContentText = parseMessageContentJson(message.contentText);
  const contentText = parsedContentText
    ? businessTextFromContent(parsedContentText)
    : message.contentText || businessTextFromContent(content);
  const attachments = (message.attachments ?? []).map((attachment) => ({
    id: `im-attachment-${attachment.attachmentRefId}`,
    name: attachment.displayName,
    type: fileTypeLabelFromName(attachment.displayName, attachment.mimeType),
    size: fileSizeLabel(attachment.fileSize),
    status: statusLabel(attachment.scanStatus),
    attachmentRefId: attachment.attachmentRefId,
    mimeType: attachment.mimeType,
  }));
  const recalled = message.status === 'RECALLED';
  return {
    id: String(message.messageId),
    type: knowledgeCard ? 'knowledge-card' : (message.messageType === 'CARD' ? 'process-card' : 'text'),
    author: sentByCurrentUser ? '我' : authorOfMessage(message),
    role: messageRole(message, knowledgeCard),
    body: recalled
      ? '消息已撤回'
      : contentText || knowledgeCard?.title || (attachments.length ? '文件消息' : '[非文本消息]'),
    side: resolveMessageSide(message, sentByCurrentUser),
    time: formatMessageTime(message.sentAt),
    seqNo: message.seqNo,
    senderType: message.senderType,
    status: message.status,
    attachments,
    knowledgeCard,
  };
}

function resolveMessageSide(message: ImMessageResponse, sentByCurrentUser: boolean): ConversationMessage['side'] {
  if (message.senderType === 'SYSTEM') return 'system';
  return sentByCurrentUser ? 'right' : 'left';
}

function normalizeConversationKind(conversationType: string): ConversationRecord['kind'] {
  if (conversationType === 'GROUP') return 'team';
  if (conversationType === 'AGENT') return 'agent';
  if (conversationType === 'PRIVATE') return 'private';
  return 'file';
}

function toneOfKind(kind: ConversationRecord['kind']): ConversationTone {
  if (kind === 'agent') return 'green';
  if (kind === 'team') return 'blue';
  return 'gray';
}

function flowOfConversationType(conversationType: string) {
  if (conversationType === 'AGENT') return 'Agent 会话';
  if (conversationType === 'GROUP') return '群聊';
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

function messageRole(message: ImMessageResponse, knowledgeCard?: KnowledgeAnswerCard) {
  if (message.senderType === 'SYSTEM') return '系统消息';
  if (knowledgeCard) return '知识库 Agent';
  if (message.senderType === 'AGENT') return message.messageType === 'CARD' ? '流程 Agent' : 'AI 助手';
  if (message.messageType === 'CARD') return '流程消息';
  return '成员';
}

function statusLabel(status: string) {
  if (status === 'ACTIVE') return '正常';
  if (status === 'READY') return '就绪';
  if (status === 'PENDING') return '待处理';
  if (status === 'CLOSED') return '已关闭';
  if (status === 'NORMAL') return '正常';
  if (status === 'RECALLED') return '已撤回';
  if (status === 'CLEAN') return '已通过';
  if (status === 'QUARANTINED') return '已隔离';
  return status;
}

function formatConversationDate(value?: string | null) {
  if (!value) return '';
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const date = new Date(normalized);
  if (!Number.isNaN(date.getTime())) {
    return `${date.getMonth() + 1}月${date.getDate()}日`;
  }
  const match = value.match(/^\d{4}-(\d{1,2})-(\d{1,2})/);
  if (match) {
    return `${Number(match[1])}月${Number(match[2])}日`;
  }
  return value.replace('T', ' ').slice(0, 16);
}

function collectConversationAttachments(messages: ConversationMessage[]) {
  const byId = new Map<string, ConversationAttachment>();
  for (const message of messages) {
    for (const attachment of message.attachments ?? []) {
      const key = attachment.attachmentRefId == null ? attachment.id : String(attachment.attachmentRefId);
      byId.set(key, attachment);
    }
  }
  return Array.from(byId.values());
}

function formatMessageTime(value?: string | null) {
  if (!value) return '';
  return value.replace('T', ' ').slice(0, 16);
}

function parseMessageContentJson(contentJson?: string | null): Record<string, unknown> | null {
  if (!contentJson) return null;
  try {
    const content = JSON.parse(contentJson);
    return content && typeof content === 'object' && !Array.isArray(content) ? content as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

function parseTextFromContent(content: Record<string, unknown> | null) {
  return typeof content?.text === 'string' ? content.text : '';
}

function businessTextFromContent(content: Record<string, unknown> | null) {
  const text = parseTextFromContent(content);
  if (text) return text;
  const intent = optionalString(content?.intent);
  const action = optionalString(content?.action);
  const target = optionalString(content?.target);
  if (target === 'LEAVE_BALANCE') return '假期余额查询';
  if (action === 'OPEN_LEAVE_APPLY' || target === 'LEAVE_WORKFLOW') return '请假申请入口';
  if (target === 'QA') return intent ? `${intent}答复` : '问答答复';
  if (intent) return intent;
  return '';
}

function parseKnowledgeAnswerCard(content: Record<string, unknown> | null): KnowledgeAnswerCard | undefined {
  if (!content) return undefined;
  if (content.cardType === 'agent_result') return parseAgentResultCard(content);
  if (content.cardType !== 'knowledge_answer') return undefined;
  return {
    state: normalizeKnowledgeState(content.state),
    title: stringValue(content.title, '知识库回复'),
    answer: stringValue(content.answer || content.text, ''),
    confidence: normalizeConfidence(content.confidence),
    citations: arrayValue(content.citations).map(parseKnowledgeCitation).filter(Boolean) as KnowledgeCitation[],
    relatedFlows: arrayValue(content.relatedFlows).map(parseKnowledgeRelatedFlow).filter(Boolean) as KnowledgeRelatedFlow[],
    missingReason: optionalString(content.missingReason),
    suggestedActions: arrayValue(content.suggestedActions).map(parseKnowledgeAction).filter(Boolean) as KnowledgeAnswerAction[],
    relatedBusiness: arrayValue(content.relatedBusiness).map(parseKnowledgeRelatedRef).filter(Boolean) as KnowledgeAnswerRelatedRef[],
    generatedAt: optionalString(content.generatedAt),
  };
}

function parseAgentResultCard(content: Record<string, unknown>): KnowledgeAnswerCard {
  const data = parseAgentResultData(content.data);
  return {
    state: normalizeAgentResultState(content.status),
    category: normalizeAgentResultCategory(content.category),
    status: normalizeAgentResultStatus(content.status),
    title: stringValue(content.title, 'Agent 回复'),
    answer: stringValue(content.answer || content.summary, ''),
    confidence: normalizeConfidence(content.confidence),
    citations: arrayValue(content.citations).map(parseKnowledgeCitation).filter(Boolean) as KnowledgeCitation[],
    relatedFlows: data.flow ? [toKnowledgeRelatedFlow(data.flow)] : [],
    missingReason: optionalString(content.reason),
    suggestedActions: arrayValue(content.actions).map(parseAgentResultAction).filter(Boolean) as KnowledgeAnswerAction[],
    relatedBusiness: data.fields ?? [],
    generatedAt: optionalString(content.generatedAt),
  };
}

function parseAgentResultData(value: unknown): AgentResultData {
  const item = objectValue(value);
  if (!item) return {};
  return {
    fields: arrayValue(item.fields).map(parseKnowledgeRelatedRef).filter(Boolean) as KnowledgeAnswerRelatedRef[],
    flow: parseAgentResultFlow(item.flow),
    records: arrayValue(item.records).map(parseAgentResultRecord).filter(Boolean) as AgentResultRecord[],
  };
}

function parseAgentResultFlow(value: unknown): AgentResultFlow | null {
  const item = objectValue(value);
  if (!item) return null;
  return {
    requestId: optionalString(item.requestId),
    requestNo: optionalString(item.requestNo),
    title: optionalString(item.title),
    status: optionalString(item.status),
    currentNode: optionalString(item.currentNode),
    detailUrl: optionalString(item.detailUrl),
  };
}

function parseAgentResultRecord(value: unknown): AgentResultRecord | undefined {
  const item = objectValue(value);
  if (!item) return undefined;
  const title = optionalString(item.title);
  if (!title) return undefined;
  return {
    title,
    summary: optionalString(item.summary),
    fields: arrayValue(item.fields).map(parseFieldTuple).filter(Boolean) as Array<[string, string]>,
  };
}

function toKnowledgeRelatedFlow(flow: AgentResultFlow): KnowledgeRelatedFlow {
  const fields: Array<[string, string]> = [];
  if (flow.status) fields.push(['状态', flow.status]);
  if (flow.currentNode) fields.push(['当前节点', flow.currentNode]);
  if (flow.detailUrl) fields.push(['详情', flow.detailUrl]);
  return {
    id: flow.requestId ?? flow.requestNo ?? flow.title ?? 'agent-flow',
    title: flow.requestNo ? `${flow.title ?? '流程'} ${flow.requestNo}` : flow.title ?? '相关流程',
    status: flow.status ?? '待处理',
    statusTone: normalizeBadgeTone(undefined),
    summary: flow.currentNode ? `当前节点：${flow.currentNode}` : undefined,
    fields,
    apiRef: flow.detailUrl ? {
      status: 'available',
      method: 'GET',
      path: flow.detailUrl,
      description: '流程详情',
    } : undefined,
  };
}

function parseAgentResultAction(value: unknown): KnowledgeAnswerAction | undefined {
  const item = objectValue(value);
  if (!item) return undefined;
  const label = optionalString(item.label);
  const action = optionalString(item.type) ?? optionalString(item.action);
  if (!label || !action) return undefined;
  const target = optionalString(item.target);
  return {
    label,
    action,
    apiRef: target ? {
      status: 'available',
      method: 'GET',
      path: target,
    } : parseKnowledgeApiRef(item.apiRef),
    payload: objectValue(item.payload),
    disabled: item.disabled === true,
  };
}

function normalizeAgentResultState(value: unknown): KnowledgeAnswerState {
  if (value === 'LOADING') return 'INGESTING';
  if (value === 'EMPTY') return 'NO_MATCH';
  if (value === 'NO_PERMISSION') return 'NO_PERMISSION';
  if (value === 'FAILED') return 'SEARCH_FAILED';
  return 'ANSWERED';
}

function normalizeAgentResultStatus(value: unknown): KnowledgeAnswerCard['status'] {
  if (value === 'LOADING' || value === 'SUCCESS' || value === 'EMPTY' || value === 'NO_PERMISSION' || value === 'FAILED') return value;
  return 'SUCCESS';
}

function normalizeAgentResultCategory(value: unknown): KnowledgeAnswerCard['category'] {
  if (value === 'QA' || value === 'OA' || value === 'KNOWLEDGE') return value;
  return 'QA';
}

function normalizeKnowledgeState(value: unknown): KnowledgeAnswerState {
  if (
    value === 'ANSWERED'
    || value === 'NO_MATCH'
    || value === 'LOW_CONFIDENCE'
    || value === 'NO_PERMISSION'
    || value === 'INGESTING'
    || value === 'SEARCH_FAILED'
  ) {
    return value;
  }
  return 'ANSWERED';
}

function normalizeConfidence(value: unknown): KnowledgeAnswerCard['confidence'] {
  if (value === 'high' || value === 'medium' || value === 'low') return value;
  return undefined;
}

function parseKnowledgeCitation(value: unknown): KnowledgeCitation | undefined {
  const item = objectValue(value);
  if (!item) return undefined;
  const title = optionalString(item.title);
  if (!title) return undefined;
  return {
    id: optionalString(item.id),
    title,
    snippet: optionalString(item.snippet),
    sourceType: normalizeCitationSourceType(item.sourceType),
    score: numberValue(item.score),
    apiRef: parseKnowledgeApiRef(item.apiRef),
  };
}

function parseKnowledgeRelatedFlow(value: unknown): KnowledgeRelatedFlow | undefined {
  const item = objectValue(value);
  if (!item) return undefined;
  const id = optionalString(item.id);
  const title = optionalString(item.title);
  if (!id || !title) return undefined;
  return {
    id,
    title,
    status: stringValue(item.status, '待处理'),
    statusTone: normalizeBadgeTone(item.statusTone),
    summary: optionalString(item.summary),
    fields: arrayValue(item.fields).map(parseFieldTuple).filter(Boolean) as Array<[string, string]>,
    apiRef: parseKnowledgeApiRef(item.apiRef),
  };
}

function parseKnowledgeAction(value: unknown): KnowledgeAnswerAction | undefined {
  const item = objectValue(value);
  if (!item) return undefined;
  const label = optionalString(item.label);
  const action = optionalString(item.action);
  if (!label || !action) return undefined;
  return {
    label,
    action,
    apiRef: parseKnowledgeApiRef(item.apiRef),
    payload: objectValue(item.payload),
    disabled: item.disabled === true,
  };
}

function parseKnowledgeRelatedRef(value: unknown): KnowledgeAnswerRelatedRef | undefined {
  const item = objectValue(value);
  if (!item) return undefined;
  const label = optionalString(item.label);
  const relatedValue = optionalString(item.value);
  if (!label || !relatedValue) return undefined;
  return {
    label,
    value: relatedValue,
    tone: normalizeRelatedTone(item.tone),
  };
}

function parseKnowledgeApiRef(value: unknown): KnowledgeApiRef | undefined {
  const item = objectValue(value);
  if (!item) return undefined;
  const path = optionalString(item.path);
  if (!path) return undefined;
  return {
    status: normalizeApiStatus(item.status),
    method: normalizeApiMethod(item.method),
    path,
    description: optionalString(item.description),
  };
}

function parseFieldTuple(value: unknown): [string, string] | undefined {
  if (!Array.isArray(value) || value.length < 2) return undefined;
  const label = optionalString(value[0]);
  const fieldValue = optionalString(value[1]);
  return label && fieldValue ? [label, fieldValue] : undefined;
}

function normalizeCitationSourceType(value: unknown): KnowledgeCitation['sourceType'] {
  if (value === 'DOC' || value === 'FILE' || value === 'WORKFLOW' || value === 'POLICY' || value === 'UNKNOWN') return value;
  return 'UNKNOWN';
}

function normalizeBadgeTone(value: unknown): KnowledgeRelatedFlow['statusTone'] {
  if (value === 'primary' || value === 'success' || value === 'warning' || value === 'danger' || value === 'neutral') return value;
  return 'neutral';
}

function normalizeRelatedTone(value: unknown): KnowledgeAnswerRelatedRef['tone'] {
  if (value === 'blue' || value === 'green' || value === 'amber' || value === 'red' || value === 'gray') return value;
  return 'gray';
}

function normalizeApiStatus(value: unknown): KnowledgeApiRef['status'] {
  if (value === 'available' || value === 'planned' || value === 'unavailable') return value;
  return 'planned';
}

function normalizeApiMethod(value: unknown): KnowledgeApiRef['method'] {
  if (value === 'GET' || value === 'POST' || value === 'PUT' || value === 'DELETE') return value;
  return 'GET';
}

function objectValue(value: unknown): Record<string, unknown> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : undefined;
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : [];
}

function optionalString(value: unknown) {
  if (typeof value === 'number' && Number.isFinite(value)) return String(value);
  return typeof value === 'string' && value.trim() ? value : undefined;
}

function stringValue(value: unknown, fallback: string) {
  return optionalString(value) ?? fallback;
}

function numberValue(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}
