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
import type { ConversationMessage, ConversationRecord, ConversationTone } from './shell';

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
  }),
  actions: {
    async loadConversations(query: ImConversationListQuery = {}, imApi: EmployeeImApi = employeeApi.im) {
      this.loading = true;
      this.error = '';
      try {
        const conversations = await imApi.conversations(query);
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
      conversationId: string | number,
      currentUserId?: EntityId,
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
    async loadConversationDetail(conversationId: string | number, imApi: EmployeeImApi = employeeApi.im) {
      this.error = '';
      try {
        const detail = await imApi.conversationDetail(conversationId);
        const key = String(conversationId);
        this.detailsByConversation[key] = detail;
        const record = this.conversations.find((item) => item.id === key);
        if (record) {
          const mapped = mapImConversationToRecord(detail.conversation);
          record.panel.members = detail.members.map(mapConversationMember);
          record.memberCount = detail.members.length || record.memberCount;
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
      const existing = this.messagesByConversation[conversationId] ?? [];
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
        record.memberCount = members.length;
      }
      const detail = this.detailsByConversation[key];
      if (detail) detail.members = members;
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
    type: message.messageType === 'CARD' ? 'process-card' : 'text',
    author: sentByCurrentUser ? '我' : authorOfMessage(message),
    role: messageRole(message),
    body: recalled
      ? '消息已撤回'
      : message.contentText || parseTextFromContentJson(message.contentJson) || (attachments.length ? '文件消息' : '[非文本消息]'),
    side: resolveMessageSide(message, sentByCurrentUser),
    time: formatMessageTime(message.sentAt),
    seqNo: message.seqNo,
    senderType: message.senderType,
    status: message.status,
    attachments,
  };
}

function resolveMessageSide(message: ImMessageResponse, sentByCurrentUser: boolean): ConversationMessage['side'] {
  if (message.senderType === 'SYSTEM') return 'system';
  return sentByCurrentUser ? 'right' : 'left';
}

function normalizeConversationKind(conversationType: string): ConversationRecord['kind'] {
  if (conversationType === 'GROUP') return 'team';
  if (conversationType === 'AGENT') return 'agent';
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

function messageRole(message: ImMessageResponse) {
  if (message.senderType === 'SYSTEM') return '系统消息';
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

function fileSizeLabel(bytes: number) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(bytes >= 10 * 1024 * 1024 ? 0 : 1)}MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(bytes >= 10 * 1024 ? 0 : 1)}KB`;
  return `${bytes}B`;
}

function fileTypeLabelFromName(name: string, mimeType = '') {
  const ext = name.split('.').pop()?.trim();
  if (ext) return ext.slice(0, 8).toUpperCase();
  return (mimeType.split('/').pop() || '文件').slice(0, 8).toUpperCase();
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

function formatMessageTime(value?: string | null) {
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
