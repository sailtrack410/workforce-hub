// hub-web API 端点：直连后端契约（端口 8080，经 vite 代理）
import { http } from './http';
import type {
  AgentResponse,
  AttachmentRefResponse,
  AttendanceDailyResultResponse,
  AttendanceScheduleResponse,
  CurrentUserResponse,
  DownloadTicketResponse,
  EmployeeFileResponse,
  EntityId,
  ImConversationDetailResponse,
  ImConversationMemberResponse,
  ImConversationSummaryResponse,
  ImGroupFileResponse,
  ImGroupNoticeResponse,
  ImGroupProfileResponse,
  ImMessageResponse,
  LeaveAccountResponse,
  OrgUserDetailResponse,
  OrgUserSummaryResponse,
  PageResult,
  SysNotificationResponse,
  UploadTicketResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTodoResponse,
} from './types';

const id = (value: EntityId) => encodeURIComponent(String(value));

export const authApi = {
  login: (loginName: string, password: string) =>
    http.post<CurrentUserResponse>('/api/employee/auth/login', { loginName, password }),
  me: () => http.get<CurrentUserResponse>('/api/employee/auth/me'),
  logout: () => http.post<void>('/api/employee/auth/logout'),
};

export const orgApi = {
  profile: () => http.get<OrgUserDetailResponse>('/api/employee/me/profile'),
  manager: () => http.get<OrgUserSummaryResponse | null>('/api/employee/me/manager'),
  subordinates: () => http.get<OrgUserSummaryResponse[]>('/api/employee/me/subordinates'),
  colleagues: () => http.get<OrgUserSummaryResponse[]>('/api/employee/me/colleagues'),
};

export const imApi = {
  conversations: () => http.get<ImConversationSummaryResponse[]>('/api/employee/im/conversations'),
  detail: (conversationId: EntityId) =>
    http.get<ImConversationDetailResponse>(`/api/employee/im/conversations/${id(conversationId)}`),
  messages: (conversationId: EntityId, query: { limit?: number } = {}) =>
    http.get<ImMessageResponse[]>(`/api/employee/im/conversations/${id(conversationId)}/messages`, query),
  sendMessage: (conversationId: EntityId, request: {
    messageType: 'TEXT' | 'FILE';
    text?: string;
    attachmentRefIds?: EntityId[];
    clientMessageId: string;
    quotedMessageId?: EntityId;
    mentionUserIds?: EntityId[];
  }) => http.post<ImMessageResponse>(`/api/employee/im/conversations/${id(conversationId)}/messages`, request),
  recallMessage: (conversationId: EntityId, messageId: EntityId) =>
    http.post<ImMessageResponse>(`/api/employee/im/conversations/${id(conversationId)}/messages/${id(messageId)}/recall`),
  deleteMessage: (conversationId: EntityId, messageId: EntityId) =>
    http.post<void>(`/api/employee/im/conversations/${id(conversationId)}/messages/${id(messageId)}/delete`),
  startPrivateConversation: (targetUserId: EntityId) =>
    http.post<ImConversationSummaryResponse>('/api/employee/im/private-conversations', { targetUserId }),
  createGroupConversation: (title: string, memberUserIds: EntityId[]) =>
    http.post<ImConversationSummaryResponse>('/api/employee/im/group-conversations', { title, memberUserIds }),
  startAgentConversation: (agentId: EntityId) =>
    http.post<ImConversationSummaryResponse>('/api/employee/im/agent-conversations', { agentId }),
  updateGroupProfile: (conversationId: EntityId, request: { title?: string; noticeText?: string }) =>
    http.post<ImGroupProfileResponse>(`/api/employee/im/group-conversations/${id(conversationId)}/profile`, request),
  addGroupMembers: (conversationId: EntityId, memberUserIds: EntityId[]) =>
    http.post<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${id(conversationId)}/members`, { memberUserIds }),
  removeGroupMembers: (conversationId: EntityId, memberUserIds: EntityId[]) =>
    http.post<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${id(conversationId)}/members/remove`, { memberUserIds }),
  leaveGroup: (conversationId: EntityId) =>
    http.post<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${id(conversationId)}/leave`),
  updateReadPosition: (conversationId: EntityId, lastReadSeq: number) =>
    http.post<ImConversationSummaryResponse>(`/api/employee/im/conversations/${id(conversationId)}/read-position`, { lastReadSeq }),
  updatePreference: (conversationId: EntityId, request: { pin?: boolean; mute?: boolean; saveToContacts?: boolean; showMemberNickname?: boolean }) =>
    http.post<ImConversationSummaryResponse>(`/api/employee/im/conversations/${id(conversationId)}/preference`, request),
  setMyNickname: (conversationId: EntityId, nickname: string) =>
    http.post<ImConversationMemberResponse>(`/api/employee/im/group-conversations/${id(conversationId)}/my-nickname`, { nickname }),
  clearMessages: (conversationId: EntityId) =>
    http.post<{ cleared: boolean }>(`/api/employee/im/conversations/${id(conversationId)}/messages/clear`),
  groupNotices: (conversationId: EntityId) =>
    http.get<ImGroupNoticeResponse[]>(`/api/employee/im/group-conversations/${id(conversationId)}/notices`),
  publishGroupNotice: (conversationId: EntityId, request: { title?: string; content: string }) =>
    http.post<ImGroupNoticeResponse>(`/api/employee/im/group-conversations/${id(conversationId)}/notices`, request),
  groupFiles: (conversationId: EntityId) =>
    http.get<ImGroupFileResponse[]>(`/api/employee/im/group-conversations/${id(conversationId)}/files`),
  createGroupFileDownloadTicket: (conversationId: EntityId, attachmentRefId: EntityId) =>
    http.post<DownloadTicketResponse>(`/api/employee/im/group-conversations/${id(conversationId)}/files/${id(attachmentRefId)}/download-tickets`, { downloadType: 'ATTACHMENT' }),
  attachmentDownloadUrl: (ticketNo: string) => `/api/employee/im/attachments/download/${encodeURIComponent(ticketNo)}`,
  createMessageAttachmentDownloadTicket: (conversationId: EntityId, messageId: EntityId, attachmentRefId: EntityId, downloadType: 'INLINE' | 'ATTACHMENT' = 'INLINE') =>
    http.post<DownloadTicketResponse>(`/api/employee/im/conversations/${id(conversationId)}/messages/${id(messageId)}/attachments/${id(attachmentRefId)}/download-tickets`, { downloadType }),
};

export const contactsApi = {
  updateFriendRemark: (userId: EntityId, remarkName: string) =>
    http.put<OrgUserSummaryResponse>(`/api/employee/contacts/friends/${id(userId)}`, { remarkName }),
};

export const agentApi = {
  list: (keyword?: string) => http.get<AgentResponse[]>('/api/employee/agents', { keyword }),
};

export const workflowApi = {
  todos: () => http.get<WorkflowTodoResponse[]>('/api/employee/workflow/todos'),
  requests: (query: { pageNo?: number; pageSize?: number; status?: string; bizType?: string } = {}) =>
    http.get<PageResult<WorkflowRequestSummaryResponse>>('/api/employee/workflow/requests', query),
  approveTask: (taskId: string, comment?: string) =>
    http.post<void>(`/api/employee/workflow/tasks/${encodeURIComponent(taskId)}/approve`, { comment }),
  rejectTask: (taskId: string, comment: string) =>
    http.post<void>(`/api/employee/workflow/tasks/${encodeURIComponent(taskId)}/reject`, { comment }),
  withdrawRequest: (requestId: EntityId, comment: string) =>
    http.post<void>(`/api/employee/workflow/requests/${id(requestId)}/withdraw`, { comment }),
};

export const attendanceApi = {
  dailyResults: (query: { startDate?: string; endDate?: string } = {}) =>
    http.get<AttendanceDailyResultResponse[]>('/api/employee/attendance/daily-results', query),
  schedules: (query: { startDate?: string; endDate?: string } = {}) =>
    http.get<AttendanceScheduleResponse[]>('/api/employee/attendance/schedules', query),
  punch: (punchType: 'IN' | 'OUT', locationText?: string) =>
    http.post<{ punchRecordId: EntityId }>('/api/employee/attendance/punch', {
      punchType, sourceType: 'WEB', locationText,
    }),
};

export const notificationApi = {
  list: (query: { pageNo?: number; pageSize?: number } = {}) =>
    http.get<PageResult<SysNotificationResponse> | SysNotificationResponse[]>('/api/employee/notifications', query),
  unreadCount: () => http.get<number>('/api/employee/notifications/unread-count'),
  markRead: (notificationId: EntityId) =>
    http.post<void>(`/api/employee/notifications/${id(notificationId)}/read`),
  markAllRead: () => http.post<void>('/api/employee/notifications/read-all'),
};

export const leaveApi = {
  accounts: () => http.get<LeaveAccountResponse[]>('/api/employee/leave/accounts'),
};

export const fileApi = {
  list: (query: { pageNo?: number; pageSize?: number; keyword?: string } = {}) =>
    http.get<PageResult<EmployeeFileResponse>>('/api/employee/files', query),
  createUploadTicket: (request: {
    ownerType: string;
    ownerId?: EntityId | null;
    folderId?: EntityId | null;
    fileName: string;
    mimeType: string;
    fileSize: number;
    contentSha256: string;
    clientRequestId: string;
  }) => http.post<UploadTicketResponse>('/api/employee/files/upload-tickets', request),
  confirmUpload: (request: {
    uploadTicketId: string;
    objectKey: string;
    contentSha256: string;
    fileSize: number;
    ownerType: string;
    ownerId?: EntityId | null;
    displayName: string;
  }) => http.post<AttachmentRefResponse>('/api/employee/files/confirm-upload', request),
  createDownloadTicket: (attachmentRefId: EntityId, downloadType: 'INLINE' | 'ATTACHMENT' = 'INLINE') =>
    http.post<DownloadTicketResponse>(`/api/employee/files/${id(attachmentRefId)}/download-tickets`, { downloadType }),
  downloadUrl: (ticketNo: string) => `/api/employee/files/download/${encodeURIComponent(ticketNo)}`,
};
