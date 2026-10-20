// hub-web API 类型：与后端契约对齐（字段名即接口文档），全新声明
export type EntityId = string | number;

/* ---------- 认证与组织 ---------- */
export interface CurrentUserResponse {
  userId: EntityId;
  loginName: string;
  displayName: string;
  email?: string | null;
}
export interface OrgUserDetailResponse {
  realName?: string | null;
  primaryDeptName?: string | null;
  positionName?: string | null;
  avatarAttachmentRefId?: EntityId | null;
}
export interface OrgUserSummaryResponse {
  userId: EntityId;
  employeeNo?: string | null;
  realName: string;
  avatarAttachmentRefId?: EntityId | null;
  primaryDeptId?: EntityId | null;
  primaryDeptName?: string | null;
  positionId?: EntityId | null;
  positionName?: string | null;
  managerUserId?: EntityId | null;
  managerName?: string | null;
  employmentStatus?: string | null;
}

/* ---------- IM ---------- */
export interface ImConversationLastMessage {
  contentText?: string | null;
  messageType?: string | null;
  senderName?: string | null;
}
export interface ImConversationSummaryResponse {
  conversationId: EntityId;
  conversationType: string;
  conversationTypeName?: string | null;
  title: string;
  avatarFileId?: EntityId | null;
  boundAgentId?: EntityId | null;
  lastMessageSeq?: number | null;
  lastReadSeq?: number | null;
  unreadCount: number;
  lastMessage?: ImConversationLastMessage | null;
  lastMessageAt?: string | null;
  status?: string | null;
  statusName?: string | null;
  pin?: boolean;
  mute?: boolean;
  memberRole?: string | null;
  memberRoleName?: string | null;
  hasMentionMe?: boolean;
  hasReplyMe?: boolean;
  memberCount?: number | null;
  saveToContacts?: boolean;
  showMemberNickname?: boolean;
}
export interface ImConversationMemberResponse {
  memberId: EntityId;
  memberType: string;
  memberTypeName?: string | null;
  displayName: string;
  memberRole: string;
  memberRoleName?: string | null;
  status: string;
  joinedAt?: string | null;
}
export interface ImGroupProfileResponse {
  conversationId: EntityId;
  title: string;
  groupType: string;
  groupTypeName?: string | null;
  ownerUserId: EntityId;
  noticeText?: string | null;
  status: string;
  currentMemberRole?: string | null;
}
export interface ImConversationDetailResponse {
  conversation: ImConversationSummaryResponse;
  members: ImConversationMemberResponse[];
  groupProfile?: ImGroupProfileResponse | null;
}
export interface ImMessageAttachment {
  attachmentRefId: EntityId;
  displayName: string;
  mimeType?: string | null;
  fileSize?: number | null;
}
export interface ImMessageResponse {
  messageId: EntityId;
  conversationId: EntityId;
  seqNo: number;
  senderId?: EntityId | null;
  senderType: string;
  senderTypeName?: string | null;
  senderName?: string | null;
  messageType: string;
  messageTypeName?: string | null;
  contentText?: string | null;
  contentJson?: string | null;
  attachments?: ImMessageAttachment[] | null;
  mentions?: Array<{ userId: EntityId; name?: string | null; originalName?: string | null }> | null;
  quotedMessageId?: EntityId | null;
  status?: string | null;
  statusName?: string | null;
  sentAt?: string | null;
}
export interface AgentResponse {
  agentId: EntityId;
  agentCode: string;
  agentName: string;
  agentType: string;
  agentTypeName?: string | null;
  avatarAttachmentRefId?: EntityId | null;
  status: string;
}

/* ---------- 工作流 ---------- */
export interface WorkflowTodoResponse {
  todoId: EntityId;
  requestId: EntityId;
  taskId: string;
  title: string;
  summary: string;
  status: string;
  statusName?: string | null;
  priority?: string | null;
  priorityName?: string | null;
  createdAt?: string | null;
}
export interface WorkflowRequestSummaryResponse {
  requestId: EntityId;
  requestNo: string;
  bizType: string;
  bizTypeName?: string | null;
  title: string;
  status: string;
  statusName?: string | null;
  currentStepName?: string | null;
  submittedAt?: string | null;
  finishedAt?: string | null;
}
export interface PageResult<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  list: T[];
}

/* ---------- 文件 ---------- */
export interface UploadTicketResponse {
  uploadTicketId?: string;
  objectKey?: string;
  uploadUrl?: string;
  headers?: Record<string, string>;
  deduplicated?: boolean;
  attachmentRefId?: EntityId;
}
export interface AttachmentRefResponse {
  attachmentRefId: EntityId;
  displayName?: string;
  mimeType?: string;
  fileSize?: number;
}
export interface DownloadTicketResponse {
  ticketNo: string;
  downloadUrl?: string;
  url?: string;
}

/* ---------- 考勤 ---------- */
export interface AttendanceDailyResultResponse {
  dailyResultId: EntityId;
  userId: EntityId;
  workDate: string;
  resultStatus: string;
  lateMinutes?: number | null;
  earlyLeaveMinutes?: number | null;
  absenceMinutes?: number | null;
  workMinutes?: number | null;
  exceptionFlag?: boolean | null;
  firstPunchAt?: string | null;
  lastPunchAt?: string | null;
}

/* ---------- 假期 ---------- */
export interface LeaveAccountResponse {
  accountId: EntityId;
  leaveCode: string;
  leaveName: string;
  balanceMode: string;
  availableMinutes: number;
  frozenMinutes: number;
  status: string;
  lastTxnType?: string | null;
  lastChangeMinutes?: number | null;
  entitlementMinutes?: number | null;
}

/* ---------- 群文件 ---------- */
export interface ImGroupFileResponse {
  attachmentRefId: EntityId;
  conversationId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  uploaderId?: EntityId | null;
  uploaderName?: string | null;
  createdAt?: string | null;
  canDelete: boolean;
}

/* ---------- 云空间文件 ---------- */
export interface EmployeeFileResponse {
  attachmentRefId: EntityId;
  displayName: string;
  originalFileName?: string | null;
  mimeType?: string | null;
  fileSize?: number | null;
  folderId?: EntityId | null;
  uploaderName?: string | null;
  createdAt?: string | null;
}

/* ---------- 批次⑫ 联调新增（2026-10-15 后端已实现） ---------- */
export interface ImGroupNoticeResponse {
  noticeId: EntityId;
  title?: string | null;
  content: string;
  publisherName: string;
  publishedAt: string;
}

export interface AttendanceScheduleResponse {
  scheduleId: EntityId;
  workDate: string;
  shiftId?: EntityId | null;
  shiftName?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  isWorkday: boolean;
}

export interface SysNotificationResponse {
  notificationId: EntityId;
  type: string;
  title: string;
  content?: string | null;
  linkType?: string | null;
  linkId?: string | null;
  read: boolean;
  createdAt: string;
}
