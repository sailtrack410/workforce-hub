// hub-web API 端点：直连后端契约（端口 8080，经 vite 代理）
import { http } from './http';
import type {
  AgentResponse,
  AttachmentRefResponse,
  AttendanceDailyResultResponse,
  AttendanceScheduleResponse,
  CurrentUserResponse,
  DownloadTicketResponse,
  EmployeeFileBrowserResponse,
  EmployeeFileFolderResponse,
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
  PersonalTodoResponse,
  SysNotificationResponse,
  TeamTaskResponse,
  FileTrashResponse,
  UploadTicketResponse,
  WorkflowCcResponse,
  WorkflowRequestCreateResponse,
  WorkflowRequestDetailResponse,
  WorkflowRequestStatusResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTaskActionResponse,
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
  dismissGroup: (conversationId: EntityId) =>
    http.post<void>(`/api/employee/im/group-conversations/${id(conversationId)}/dismiss`),
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
  listFriends: () => http.get<OrgUserSummaryResponse[]>('/api/employee/contacts/friends'),
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
  requestDetail: (requestId: EntityId) =>
    http.get<WorkflowRequestDetailResponse>(`/api/employee/workflow/requests/${id(requestId)}`),
  requestActions: (requestId: EntityId) =>
    http.get<WorkflowTaskActionResponse[]>(`/api/employee/workflow/requests/${id(requestId)}/actions`),
  submitGenericRequest: (request: { processCode: string; title?: string; summary?: string; formPayload?: Record<string, unknown> }) =>
    http.post<WorkflowRequestCreateResponse>('/api/employee/workflow/generic-requests', request),
  submitLeaveRequest: (request: { leaveTypeId: EntityId; startAt: string; endAt: string; durationMinutes: number; reason: string }) =>
    http.post<WorkflowRequestCreateResponse>('/api/employee/workflow/leave-requests', request),
  submitClockCorrectionRequest: (request: { targetDate: string; missingPunchType: 'ON_DUTY' | 'OFF_DUTY'; expectedPunchAt: string; reason: string }) =>
    http.post<WorkflowRequestCreateResponse>('/api/employee/workflow/clock-correction-requests', request),
  previewLeaveDuration: (startAt: string, endAt: string) =>
    http.get<{ durationMinutes: number }>('/api/employee/workflow/leave-duration', { startAt, endAt }),
  ccList: (query: { pageNo?: number; pageSize?: number } = {}) =>
    http.get<PageResult<WorkflowCcResponse>>('/api/employee/workflow/cc-list', query),
  markCcRead: (requestId: string) =>
    http.post<void>(`/api/employee/workflow/cc-list/${encodeURIComponent(requestId)}/read`),
  attachmentDownloadTicket: (requestId: EntityId, attachmentRefId: EntityId, downloadType: 'INLINE' | 'ATTACHMENT' = 'ATTACHMENT') =>
    http.post<DownloadTicketResponse>(`/api/employee/workflow/requests/${id(requestId)}/attachments/${id(attachmentRefId)}/download-tickets`, { downloadType }),
  attachmentDownloadUrl: (ticketNo: string) => `/api/employee/workflow/attachments/download/${encodeURIComponent(ticketNo)}`,
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
  unreadCount: () => http.get<{ unreadCount: number }>('/api/employee/notifications/unread-count'),
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
  browser: (query: { folderId?: EntityId; keyword?: string; fileType?: string } = {}) =>
    http.get<EmployeeFileBrowserResponse>('/api/employee/files/browser', query),
  folders: () => http.get<EmployeeFileFolderResponse[]>('/api/employee/files/folders'),
  createFolder: (request: { parentFolderId?: EntityId | null; folderName: string }) =>
    http.post<EmployeeFileFolderResponse>('/api/employee/files/folders', request),
  trash: (attachmentRefId: EntityId, reason?: string) =>
    http.post<FileTrashResponse>(`/api/employee/files/${id(attachmentRefId)}/trash`, { reason }),
};

export const teamTaskApi = {
  attachments: (taskId: EntityId) => http.get<import('./types').TeamTaskAttachmentResponse[]>(`/api/employee/team-tasks/${id(taskId)}/attachments`),
  attach: (taskId: EntityId, attachmentRefId: EntityId) => http.put<import('./types').TeamTaskAttachmentResponse[]>(`/api/employee/team-tasks/${id(taskId)}/attachments/${id(attachmentRefId)}`),
  removeAttachment: (taskId: EntityId, attachmentRefId: EntityId) => http.delete<void>(`/api/employee/team-tasks/${id(taskId)}/attachments/${id(attachmentRefId)}`),
  attachmentTicket: (taskId: EntityId, attachmentRefId: EntityId) => http.post<DownloadTicketResponse>(`/api/employee/team-tasks/${id(taskId)}/attachments/${id(attachmentRefId)}/download-tickets`),
  lists: () => http.get<import('./types').TeamTaskListResponse[]>('/api/employee/team-tasks/lists'),
  createList: (name: string) => http.post<import('./types').TeamTaskListResponse>('/api/employee/team-tasks/lists', { content: name }),
  renameList: (listId: EntityId, name: string) => http.put<import('./types').TeamTaskListResponse>(`/api/employee/team-tasks/lists/${id(listId)}`, { content: name }),
  removeList: (listId: EntityId) => http.delete<void>(`/api/employee/team-tasks/lists/${id(listId)}`),
  watch: (taskId: EntityId, watching: boolean) => http.put<TeamTaskResponse>(`/api/employee/team-tasks/${id(taskId)}/watch?watching=${watching}`),
  comment: (taskId: EntityId, content: string) => http.post<TeamTaskResponse>(`/api/employee/team-tasks/${id(taskId)}/comments`, { content }),
  addSubtask: (taskId: EntityId, title: string) => http.post<TeamTaskResponse>(`/api/employee/team-tasks/${id(taskId)}/subtasks`, { title, completed: false }),
  updateSubtask: (taskId: EntityId, subtaskId: EntityId, title: string, completed: boolean) => http.put<TeamTaskResponse>(`/api/employee/team-tasks/${id(taskId)}/subtasks/${id(subtaskId)}`, { title, completed }),
  removeSubtask: (taskId: EntityId, subtaskId: EntityId) => http.delete<TeamTaskResponse>(`/api/employee/team-tasks/${id(taskId)}/subtasks/${id(subtaskId)}`),
  list: (query: { scope?: 'ALL' | 'ASSIGNED' | 'CREATED'; assigneeId?: string; status?: string; start?: string; end?: string } = {}) =>
    http.get<TeamTaskResponse[]>('/api/employee/team-tasks', query),
  assignees: () => http.get<Pick<OrgUserSummaryResponse, 'userId' | 'realName' | 'primaryDeptId'>[]>('/api/employee/team-tasks/assignees'),
  create: (request: { title: string; description?: string; assigneeId: string; startDate: string; endDate: string; groupId?: string }) =>
    http.post<TeamTaskResponse>('/api/employee/team-tasks', request),
  detail: (taskId: EntityId) => http.get<TeamTaskResponse>(`/api/employee/team-tasks/${id(taskId)}`),
  update: (taskId: EntityId, request: { title: string; description?: string; assigneeId: string; startDate: string; endDate: string; groupId?: string }) =>
    http.put<TeamTaskResponse>(`/api/employee/team-tasks/${id(taskId)}`, request),
  updateStatus: (taskId: EntityId, status: 'TODO' | 'IN_PROGRESS' | 'DONE') =>
    http.put<TeamTaskResponse>(`/api/employee/team-tasks/${id(taskId)}/status`, { status }),
  remove: (taskId: EntityId) => http.delete<void>(`/api/employee/team-tasks/${id(taskId)}`),
};

export const personalTodoApi = {
  list: (query: { category?: string; status?: string; start?: string; end?: string } = {}) =>
    http.get<PersonalTodoResponse[]>('/api/employee/personal-todos', query),
  create: (request: { title: string; category: 'APPROVAL' | 'MEETING' | 'TASK' | 'REMINDER'; startAt?: string; dueAt?: string; note?: string; allDay?: boolean; reminderMinutes?: number | null }) =>
    http.post<PersonalTodoResponse>('/api/employee/personal-todos', request),
  update: (todoId: EntityId, request: { title: string; category: 'APPROVAL' | 'MEETING' | 'TASK' | 'REMINDER'; startAt?: string; dueAt?: string; note?: string; allDay?: boolean; reminderMinutes?: number | null }) =>
    http.put<PersonalTodoResponse>(`/api/employee/personal-todos/${id(todoId)}`, request),
  complete: (todoId: EntityId) => http.put<PersonalTodoResponse>(`/api/employee/personal-todos/${id(todoId)}/complete`),
  reopen: (todoId: EntityId) => http.put<PersonalTodoResponse>(`/api/employee/personal-todos/${id(todoId)}/reopen`),
  remove: (todoId: EntityId) => http.delete<void>(`/api/employee/personal-todos/${id(todoId)}`),
};
