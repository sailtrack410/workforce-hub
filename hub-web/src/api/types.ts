// hub-web API 类型：与后端契约对齐（字段名即接口文档），全新声明
export type EntityId = string | number;

/* ---------- 认证与组织 ---------- */
export interface CurrentUserResponse {
  userId: EntityId;
  loginName: string;
  displayName: string;
  email?: string | null;
}
export interface LoginResponse extends CurrentUserResponse {
  requiresEmailCode?: boolean;
  requiresEmailBinding?: boolean;
  maskedEmail?: string;
}
export interface OrgUserDetailResponse {
  mobile?: string | null;
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
  aiVerdict?: string | null;
  aiVerdictName?: string | null;
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
export interface WorkflowRequestCreateResponse {
  requestId: EntityId;
  requestNo: string;
  status: string;
  statusName?: string | null;
}
export interface WorkflowRequestStatusResponse {
  requestId: EntityId;
  status: string;
  statusName?: string | null;
}
export interface WorkflowLeaveDetailResponse {
  leaveTypeId?: EntityId | null;
  leaveName?: string | null;
  startAt?: string | null;
  endAt?: string | null;
  durationMinutes?: number | null;
  reason?: string | null;
}
export interface WorkflowClockCorrectionDetailResponse {
  targetDate?: string | null;
  missingPunchType?: string | null;
  missingPunchTypeName?: string | null;
  expectedPunchAt?: string | null;
  reason?: string | null;
}
export interface WorkflowRequestAttachmentResponse {
  attachmentRefId: EntityId;
  displayName?: string | null;
  mimeType?: string | null;
  fileSize?: number | null;
}
export interface WorkflowRequestDetailResponse {
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
  leave?: WorkflowLeaveDetailResponse | null;
  clockCorrection?: WorkflowClockCorrectionDetailResponse | null;
  generic?: { processCode?: string | null; summary?: string | null; formPayload?: unknown } | null;
  attachments: WorkflowRequestAttachmentResponse[];
}
export interface WorkflowTaskActionResponse {
  actionId: EntityId;
  stepName?: string | null;
  actorName?: string | null;
  actionType: string;
  actionTypeName?: string | null;
  comment?: string | null;
  actedAt?: string | null;
}
export interface WorkflowCcResponse {
  requestId: string;
  requestNo: string;
  bizTypeName?: string | null;
  title: string;
  applicantName?: string | null;
  ccAt?: string | null;
  status: string;
  statusName?: string | null;
  read: boolean;
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
export interface EmployeeFileFolderResponse {
  folderId: EntityId;
  parentFolderId?: EntityId | null;
  folderName: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}
export interface EmployeeFileBrowserResponse {
  currentFolderId?: EntityId | null;
  breadcrumbs: EmployeeFileFolderResponse[];
  folders: EmployeeFileFolderResponse[];
  files: EmployeeFileResponse[];
}
export interface FileTrashResponse {
  attachmentRefId: EntityId;
  deletedAt?: string | null;
  purgeAfter?: string | null;
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

/* ---------- 团队任务 ---------- */
export interface TeamTaskResponse {
  taskId: EntityId;
  title: string;
  description?: string | null;
  assigneeId: EntityId;
  assigneeName?: string | null;
  creatorId?: EntityId | null;
  creatorName?: string | null;
  canEdit: boolean;
  canDelete: boolean;
  canStart: boolean;
  canComplete: boolean;
  canReopen: boolean;
  status: 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE';
  startDate: string | null;
  endDate: string | null;
  groupId?: EntityId | null;
  groupName?: string | null;
  watching?: boolean;
  watcherNames?: string[];
  subtasks?: TeamTaskSubtaskResponse[];
  activities?: TeamTaskActivityResponse[];
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface TeamTaskListResponse {
  listId: EntityId;
  name: string;
  ownerId: EntityId;
  taskCount: number;
  canEdit: boolean;
}

export interface TeamTaskSubtaskResponse { subtaskId: EntityId; title: string; completed: boolean }
export interface TeamTaskAttachmentResponse { attachmentRefId: EntityId; fileName: string; fileSize: number; mimeType: string; scanStatus: string; canRemove: boolean }
export interface TeamTaskActivityResponse {
  activityId: EntityId;
  actorId: EntityId;
  actorName?: string | null;
  kind: string;
  content: string;
  createdAt: string;
}

/* ---------- 个人待办 ---------- */
export interface PersonalTodoResponse {
  version?: number;
  syncState?: 'pending' | 'conflict';
  todoId: EntityId;
  ownerId: EntityId;
  title: string;
  category: 'APPROVAL' | 'MEETING' | 'TASK' | 'REMINDER';
  status: 'PENDING' | 'DONE';
  startAt?: string | null;
  dueAt?: string | null;
  allDay?: boolean;
  reminderMinutes?: number | null;
  reminderAt?: string | null;
  note?: string | null;
  createdAt?: string | null;
  completedAt?: string | null;
}
