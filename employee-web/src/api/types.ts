export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue | QueryValue[]>;
export type EntityId = string | number;

export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  requestId?: string | null;
}

export interface PageResult<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  list: T[];
}

export interface LoginRequest {
  loginName: string;
  password: string;
  emailCode?: string;
  bindEmail?: string;
}

export interface LoginSendCodeRequest {
  identifier: string;
}

export interface LoginSendCodeResponse {
  maskedEmail?: string | null;
}

export interface SendEmailCodeRequest {
  email: string;
  purpose: 'BIND' | 'RESET_PASSWORD';
}

export interface ResetPasswordRequest {
  email: string;
  code: string;
  newPassword: string;
}

export interface LoginResponse {
  userId: EntityId;
  userType: string;
  displayName: string;
  loginName: string;
  requiresEmailCode?: boolean;
  maskedEmail?: string | null;
  requiresEmailBinding?: boolean;
}

export interface CurrentUserResponse extends LoginResponse {
  email?: string | null;
  primaryDeptId?: EntityId | null;
  positionId?: EntityId | null;
  managerUserId?: EntityId | null;
  employmentStatus?: string | null;
}

export interface OrgUserDetailResponse {
  userId: EntityId;
  employeeNo: string;
  realName: string;
  gender?: string | null;
  mobile?: string | null;
  avatarAttachmentRefId?: EntityId | null;
  primaryDeptId?: EntityId | null;
  primaryDeptName?: string | null;
  positionId?: EntityId | null;
  positionName?: string | null;
  managerUserId?: EntityId | null;
  managerName?: string | null;
  employmentStatus?: string | null;
  status?: string | null;
  hireDate?: string | null;
  regularizedAt?: string | null;
  serviceStartDate?: string | null;
  annualLeaveStartDate?: string | null;
  workLocation?: string | null;
}

export interface UpdateEmployeeProfileRequest {
  mobile?: string | null;
  avatarAttachmentRefId?: EntityId | null;
}

export type PresenceStatus = 'ONLINE' | 'BUSY' | 'AWAY' | 'DND';
export type AccentColor = 'indigo' | 'violet' | 'blue' | 'teal' | 'amber' | 'rose';
export type FontScale = 'small' | 'medium' | 'large';
export type BubbleStyle = 'rounded' | 'sharp';

export interface EmployeePreferenceResponse {
  theme: 'light' | 'dark';
  messageDensity: 'cozy' | 'compact';
  listWidth: number;
  signature: string;
  presenceStatus: PresenceStatus;
  notifSound: boolean;
  notifDesktop: boolean;
  notifMentionOnly: boolean;
  notifUnreadBadge: boolean;
  accentColor: AccentColor;
  fontScale: FontScale;
  bubbleStyle: BubbleStyle;
}

export interface UpdateEmployeePreferenceRequest {
  theme?: 'light' | 'dark';
  messageDensity?: 'cozy' | 'compact';
  listWidth?: number;
  signature?: string;
  presenceStatus?: PresenceStatus;
  notifSound?: boolean;
  notifDesktop?: boolean;
  notifMentionOnly?: boolean;
  notifUnreadBadge?: boolean;
  accentColor?: AccentColor;
  fontScale?: FontScale;
  bubbleStyle?: BubbleStyle;
}

export interface OrgUserSummaryResponse {
  userId: EntityId;
  employeeNo: string;
  realName: string;
  avatarAttachmentRefId?: EntityId | null;
  primaryDeptId?: EntityId | null;
  primaryDeptName?: string | null;
  positionId?: EntityId | null;
  positionName?: string | null;
  managerUserId?: EntityId | null;
  managerName?: string | null;
  employmentStatus?: string | null;
  status?: string | null;
}

export interface WorkflowRequestListQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  status?: string;
  bizType?: string;
}

export interface WorkflowTaskApproveRequest {
  comment?: string;
}

export interface WorkflowTaskRejectRequest {
  comment: string;
}

export interface WorkflowRequestWithdrawRequest {
  comment: string;
}

export interface WorkflowTodoResponse {
  todoId: EntityId;
  requestId: EntityId;
  taskId: string;
  title: string;
  summary: string;
  status: string;
  priority?: string | null;
  createdAt?: string | null;
}

export interface WorkflowRequestSummaryResponse {
  requestId: EntityId;
  requestNo: string;
  bizType: string;
  bizId: number;
  title: string;
  status: string;
  currentStepKey?: string | null;
  currentStepName?: string | null;
  submittedAt?: string | null;
  finishedAt?: string | null;
}

export interface WorkflowRequestCreateResponse {
  requestId: EntityId;
  requestNo: string;
  status: string;
  flowableProcessInstanceId?: string | null;
  agentTaskId?: EntityId | null;
}

export interface WorkflowRequestStatusResponse {
  requestId: EntityId;
  status: string;
}

export interface WorkflowLeaveDetailResponse {
  leaveTypeId: EntityId;
  startAt?: string | null;
  endAt?: string | null;
  durationMinutes?: number | null;
  reason?: string | null;
}

export interface WorkflowClockCorrectionDetailResponse {
  targetDate?: string | null;
  missingPunchType?: string | null;
  expectedPunchAt?: string | null;
  reason?: string | null;
}

export interface WorkflowRequestAttachmentResponse {
  attachmentRefId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  scanStatus: string;
}

export interface WorkflowRequestDetailResponse {
  requestId: EntityId;
  requestNo: string;
  bizType: string;
  bizId: EntityId;
  applicantUserId: EntityId;
  title: string;
  status: string;
  currentStepKey?: string | null;
  currentStepName?: string | null;
  flowableProcessInstanceId?: string | null;
  submittedAt?: string | null;
  finishedAt?: string | null;
  leave?: WorkflowLeaveDetailResponse | null;
  clockCorrection?: WorkflowClockCorrectionDetailResponse | null;
  attachments: WorkflowRequestAttachmentResponse[];
}

export interface WorkflowTaskActionResponse {
  actionId: EntityId;
  requestId: EntityId;
  taskId?: string | null;
  stepKey?: string | null;
  stepName?: string | null;
  actorType?: string | null;
  actorId?: EntityId | null;
  actionType: string;
  comment?: string | null;
  agentDecisionJson?: string | null;
  beforeStatus?: string | null;
  afterStatus?: string | null;
  actedAt?: string | null;
}

export interface ImMessageResponse {
  messageId: string | number;
  conversationId: string | number;
  seqNo: number;
  senderType: string;
  senderId: string | number;
  messageType: string;
  contentJson: string;
  contentText: string;
  attachments?: ImMessageAttachmentResponse[];
  quotedMessageId?: string | number | null;
  status: string;
  sentAt?: string | null;
}

export interface ImMessageAttachmentResponse {
  attachmentRefId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  scanStatus: string;
}

export interface SendImMessageRequest {
  messageType?: 'TEXT' | 'FILE';
  text?: string;
  attachmentRefIds?: EntityId[];
  clientMessageId?: string;
  quotedMessageId?: EntityId;
}

export interface TranslateImMessageResponse {
  messageId: EntityId;
  sourceText: string;
  translatedText: string;
}

export interface ForwardImMessageRequest {
  targetConversationId: EntityId;
}

export interface ImMessageActionResponse {
  messageId: EntityId;
  actionType: string;
  status: string;
}

export interface RemindImMessageRequest {
  remindAt?: string | null;
  note?: string | null;
}

export interface ImConversationListQuery extends QueryParams {
  beforeLastMessageAt?: string;
  beforeConversationId?: string | number;
  size?: number;
}

export interface ImMessageListQuery extends QueryParams {
  beforeSeq?: number;
  size?: number;
}

export interface ImConversationSummaryResponse {
  conversationId: string | number;
  conversationType: string;
  conversationKey?: string | null;
  title?: string | null;
  avatarFileId?: string | number | null;
  boundAgentId?: string | number | null;
  lastMessageSeq: number;
  lastReadSeq: number;
  unreadCount: number;
  lastMessage?: ImMessageResponse | null;
  lastMessageAt?: string | null;
  status: string;
  pin: boolean;
  mute: boolean;
  memberRole: string;
}

export interface ImConversationDetailResponse {
  conversation: ImConversationSummaryResponse;
  members: ImConversationMemberResponse[];
  groupProfile?: ImGroupProfileResponse | null;
}

export interface ImConversationMemberResponse {
  memberId: EntityId;
  memberType: string;
  displayName: string;
  memberRole: string;
  status: string;
  joinedAt?: string | null;
}

export interface ImGroupProfileResponse {
  conversationId: string | number;
  title: string;
  avatarFileId?: string | number | null;
  groupType: string;
  ownerUserId: string | number;
  noticeText?: string | null;
  status: string;
  currentMemberRole?: string | null;
}

export interface ImGroupFileResponse {
  attachmentRefId: EntityId;
  conversationId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  scanStatus: string;
  uploaderId?: EntityId | null;
  uploaderName?: string | null;
  createdAt?: string | null;
  canDelete: boolean;
}

export interface StartPrivateConversationRequest {
  targetUserId: EntityId;
}

export interface CreateGroupConversationRequest {
  title: string;
  memberUserIds: EntityId[];
}

export interface StartAgentConversationRequest {
  agentId: EntityId;
}

export interface UpdateReadPositionRequest {
  lastReadSeq: number;
}

export interface UpdateConversationPreferenceRequest {
  pin?: boolean;
  mute?: boolean;
}

export interface UpdateGroupConversationRequest {
  title?: string;
  noticeText?: string;
}

export interface GroupMemberUserIdsRequest {
  memberUserIds: EntityId[];
}

export interface EditImMessageRequest {
  text: string;
}

export interface LeaveRequestCreateRequest {
  leaveTypeId: EntityId;
  startAt: string;
  endAt: string;
  durationMinutes: number;
  reason: string;
  attachmentRefIds?: EntityId[];
}

export interface ClockCorrectionRequestCreateRequest {
  targetDate: string;
  missingPunchType: string;
  expectedPunchAt: string;
  reason: string;
  attachmentRefIds?: EntityId[];
}

export interface AttendanceDateRangeQuery extends QueryParams {
  startDate?: string;
  endDate?: string;
}

export interface CreatePunchRecordRequest {
  punchAt?: string;
  punchType: string;
  sourceType: string;
  locationText?: string;
  ipAddress?: string;
  deviceId?: string;
}

export interface PunchRecordResponse {
  punchRecordId: EntityId;
  userId: EntityId;
  punchAt?: string | null;
  punchType: string;
  sourceType: string;
  status: string;
  dailyResult?: AttendanceDailyResultResponse | null;
}

export interface AttendanceDailyResultResponse {
  dailyResultId: EntityId;
  userId: EntityId;
  workDate: string;
  scheduleId?: EntityId | null;
  groupId?: EntityId | null;
  shiftId?: EntityId | null;
  resultStatus: string;
  lateMinutes?: number | null;
  earlyLeaveMinutes?: number | null;
  absenceMinutes?: number | null;
  workMinutes?: number | null;
  exceptionFlag?: boolean | null;
  calculatedAt?: string | null;
}

export interface AttendanceExceptionResponse {
  exceptionId: EntityId;
  dailyResultId: EntityId;
  userId: EntityId;
  workDate: string;
  exceptionType: string;
  exceptionDesc?: string | null;
  status: string;
}

export interface LeaveAccountResponse {
  accountId: EntityId;
  userId: EntityId;
  leaveTypeId: EntityId;
  leaveCode: string;
  leaveName: string;
  balanceMode: string;
  availableMinutes: number;
  frozenMinutes: number;
  status: string;
  lastTxnType?: string | null;
  lastChangeMinutes?: number | null;
}

export interface CreateUploadTicketRequest {
  ownerType: string;
  ownerId?: EntityId | null;
  folderId?: EntityId | null;
  fileName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  contentMd5?: string;
  clientRequestId?: string;
}

export interface ConfirmUploadRequest {
  uploadTicketId: string | number;
  objectKey: string;
  contentSha256: string;
  fileSize: number;
  ownerType: string;
  ownerId?: EntityId | null;
  folderId?: EntityId | null;
  displayName: string;
}

export interface CreateDownloadTicketRequest {
  downloadType: string;
  reason?: string;
}

export interface FileListQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  folderId?: EntityId | null;
  keyword?: string;
  fileType?: string;
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

export interface CreateEmployeeFileFolderRequest {
  parentFolderId?: EntityId | null;
  folderName: string;
}

export interface MoveEmployeeFileRequest {
  targetFolderId?: EntityId | null;
}

export interface CopyEmployeeFileRequest {
  targetFolderId?: EntityId | null;
  displayName?: string;
}

export interface FileTrashRequest {
  reason?: string;
}

export interface FileTrashResponse {
  attachmentRefId: EntityId;
  deletedAt?: string | null;
  purgeAfter?: string | null;
}

export interface FileRestoreResponse {
  attachmentRefId: EntityId;
  restoredAt?: string | null;
}

export interface FilePurgeResponse {
  attachmentRefId: EntityId;
  purgedAt?: string | null;
  physicalCleanupScheduled?: boolean | null;
}

export interface UploadTicketResponse {
  uploadTicketId?: string | number | null;
  ticketNo?: string | null;
  deduplicated: boolean;
  attachmentRefId?: EntityId | null;
  storageProvider: string;
  bucketName?: string | null;
  objectKey?: string | null;
  uploadUrl?: string | null;
  headers: Record<string, string>;
  expireAt?: string | null;
}

export interface AttachmentRefResponse {
  attachmentRefId: EntityId;
  fileObjectId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  deduplicated: boolean;
  scanStatus: string;
  createdAt?: string | null;
}

export interface EmployeeFileResponse {
  attachmentRefId: EntityId;
  fileObjectId: EntityId;
  ownerType: string;
  ownerId?: EntityId | null;
  folderId?: EntityId | null;
  originalFileName?: string | null;
  displayName: string;
  uploaderType: string;
  uploaderId?: EntityId | null;
  mimeType?: string | null;
  fileSize: number;
  scanStatus: string;
  deletedAt?: string | null;
  purgeAfter?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface QuotaResponse {
  personalQuotaBytes: number;
  personalUsedBytes: number;
  personalUsagePercent: number;
  orgPoolQuotaBytes?: number | null;
  orgPoolUsedBytes?: number | null;
  cacheQuotaBytes?: number | null;
  cacheUsedBytes?: number | null;
  uploadWritable: boolean;
  blockReason?: string | null;
}

export interface DownloadTicketResponse {
  downloadTicketId: EntityId;
  ticketNo: string;
  downloadUrl: string;
  expireAt?: string | null;
  fileName: string;
  fileSize: number;
}

export interface AgentResponse {
  agentId: EntityId;
  agentCode: string;
  agentName: string;
  avatarAttachmentRefId?: EntityId | null;
  agentType: string;
  identityMode: string;
  publishedVersionId?: EntityId | null;
  publishedVersionNo?: number | null;
  publishedVersionLabel?: string | null;
  status: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateAgentTaskRequest {
  triggerType: string;
  contextType?: string;
  contextId?: EntityId;
  inputPayloadJson?: string;
  toolCalls?: UnknownRecord[];
}

export interface AgentTaskResponse {
  taskId: EntityId;
  agentId: EntityId;
  agentVersionId?: EntityId | null;
  triggerType: string;
  contextType?: string | null;
  contextId?: EntityId | null;
  status: string;
  toolCallCount: number;
  waitingDeadlineAt?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ConfirmAgentTaskRequest {
  approved: boolean;
  comment?: string;
}

export interface CancelAgentTaskRequest {
  reason: string;
}

export type UnknownRecord = Record<string, unknown>;
