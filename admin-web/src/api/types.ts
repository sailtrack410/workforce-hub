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
  roleCodes?: string[];
  permissionCodes?: string[];
  primaryDeptId?: EntityId | null;
  positionId?: EntityId | null;
  managerUserId?: EntityId | null;
  employmentStatus?: string | null;
}

export interface RbacPermissionResponse {
  permissionId: EntityId;
  permCode: string;
  permName: string;
  moduleCode?: string | null;
  actionCode?: string | null;
  status: string;
}

export interface RbacRoleResponse {
  roleId: EntityId;
  roleCode: string;
  roleName: string;
  status: string;
  createdAt?: string | null;
  updatedAt?: string | null;
  permissions?: RbacPermissionResponse[];
}

export interface RbacRoleSaveRequest {
  roleCode: string;
  roleName: string;
  status: string;
}

export interface RbacAdminUserRoleResponse {
  adminUserId: EntityId;
  username: string;
  displayName?: string | null;
  roles: RbacRoleResponse[];
}

export interface AdminAgentTaskResponse {
  taskId: EntityId;
  agentId: EntityId;
  agentCode?: string | null;
  agentName?: string | null;
  agentVersionId?: EntityId | null;
  triggerType?: string | null;
  triggerTypeName?: string | null;
  contextType?: string | null;
  contextTypeName?: string | null;
  contextId?: EntityId | null;
  contextDisplayName?: string | null;
  contextNo?: string | null;
  triggeredByType?: string | null;
  triggeredByTypeName?: string | null;
  triggeredById?: EntityId | null;
  triggeredByName?: string | null;
  status: string;
  statusName?: string | null;
  toolCallCount?: number | null;
  waitingDeadlineAt?: string | null;
  workflowSnapshotJson?: string | null;
  executionTraceJson?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AgentTaskListQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  status?: string;
  agentId?: EntityId;
  contextType?: string;
  triggeredByType?: string;
  triggeredById?: EntityId;
  createdFrom?: string;
  createdTo?: string;
}

export interface AdminAgentTaskMetricsResponse {
  totalTaskCount: number;
  pendingTaskCount: number;
  runningTaskCount: number;
  succeededTaskCount: number;
  failedTaskCount: number;
  waitingHumanConfirmTaskCount: number;
  rejectedTaskCount: number;
  cancelledTaskCount: number;
  totalToolCallCount: number;
  failedToolCallCount: number;
  averageFinishedDurationMillis?: number | null;
  lastTaskCreatedAt?: string | null;
}

export interface AdminAgentTaskFailureSummaryResponse {
  errorMessage?: string | null;
  taskCount: number;
  toolCallCount: number;
  firstOccurredAt?: string | null;
  lastOccurredAt?: string | null;
}

export interface AdminAgentTaskToolCallResponse {
  logId: EntityId;
  taskId: EntityId;
  toolId?: EntityId | null;
  toolCode?: string | null;
  toolName?: string | null;
  toolType?: string | null;
  toolTypeName?: string | null;
  riskLevel?: string | null;
  riskLevelName?: string | null;
  readOnlyFlag?: boolean | null;
  requestPayloadJson?: string | null;
  responsePayloadJson?: string | null;
  approvalStatus?: string | null;
  approvalStatusName?: string | null;
  resultStatus?: string | null;
  resultStatusName?: string | null;
  calledAt?: string | null;
}

export interface AdminAgentTaskAuditResponse {
  auditId: EntityId;
  taskId: EntityId;
  actionType: string;
  actionTypeName?: string | null;
  targetType?: string | null;
  targetTypeName?: string | null;
  targetId?: EntityId | null;
  targetDisplayName?: string | null;
  decisionSummary?: string | null;
  riskFlag?: boolean | null;
  occurredAt?: string | null;
}

export interface AgentTaskTimeoutRunQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  triggerType?: string;
  status?: string;
  startedFrom?: string;
  startedTo?: string;
}

export interface AgentTaskTimeoutScanResponse {
  jobRunId: string;
  jobRunNo?: string | null;
  triggerType: string;
  triggerTypeName?: string | null;
  jobStatus: string;
  jobStatusName?: string | null;
  scannedCount: number;
  cancelledCount: number;
  skippedCount: number;
}

export interface AdminAgentTaskTimeoutRunResponse {
  jobRunId: string;
  jobRunNo?: string | null;
  triggerType: string;
  triggerTypeName?: string | null;
  status: string;
  statusName?: string | null;
  batchSize?: number | null;
  scannedCount?: number | null;
  cancelledCount?: number | null;
  skippedCount?: number | null;
  lockOwner?: string | null;
  errorMessage?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AdminImOutboxEventQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  status?: string;
  eventType?: string;
  conversationId?: EntityId;
  messageId?: EntityId;
  actorUserId?: EntityId;
  createdFrom?: string;
  createdTo?: string;
}

export interface AdminImOutboxEventResponse {
  eventId: EntityId;
  eventType: string;
  eventTypeName?: string | null;
  conversationId: EntityId;
  messageId?: EntityId | null;
  seqNo?: EntityId | null;
  actorUserId?: EntityId | null;
  actorName?: string | null;
  recipientUserIdsJson?: string | null;
  payloadJson: string;
  status: string;
  statusName?: string | null;
  retryCount: number;
  nextRetryAt?: string | null;
  lastError?: string | null;
  claimedAt?: string | null;
  sentAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AdminImOutboxSummaryResponse {
  totalCount: number;
  dueDispatchCount: number;
  staleSendingCount: number;
  earliestCreatedAt?: string | null;
  earliestNextRetryAt?: string | null;
  generatedAt?: string | null;
  sendingStaleBefore?: string | null;
  statusCounts: Array<{ status: string; statusName?: string | null; count: number }>;
}

export interface AdminImOutboxFailureSummaryResponse {
  status: string;
  statusName?: string | null;
  lastError?: string | null;
  eventCount: number;
  maxRetryCount: number;
  firstOccurredAt?: string | null;
  lastOccurredAt?: string | null;
}

export interface AdminImOutboxAuditResponse {
  auditId: EntityId;
  operatorType: string;
  operatorTypeName?: string | null;
  operatorId?: EntityId | null;
  operatorName?: string | null;
  actionType: string;
  actionTypeName?: string | null;
  targetType: string;
  targetTypeName?: string | null;
  targetId?: EntityId | null;
  targetDisplayName?: string | null;
  result: string;
  resultName?: string | null;
  detailJson?: string | null;
  occurredAt?: string | null;
}

export interface AdminImOutboxBatchRetryResponse {
  requestedCount: number;
  succeededCount: number;
  skippedCount: number;
  items: Array<{
    eventId: EntityId;
    succeeded: boolean;
    beforeStatus?: string | null;
    beforeStatusName?: string | null;
    afterStatus?: string | null;
    afterStatusName?: string | null;
    skipReason?: string | null;
  }>;
}

export interface AdminImOutboxDispatchResponse {
  requestedBatchSize: number;
  dispatchedCount: number;
}

export interface WorkflowRequestListQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  status?: string;
  bizType?: string;
  applicantUserId?: EntityId;
  keyword?: string;
}

export interface WorkflowRequestSummaryResponse {
  requestId: EntityId;
  requestNo: string;
  bizType: string;
  bizTypeName?: string | null;
  bizId: EntityId;
  title: string;
  status: string;
  statusName?: string | null;
  currentStepKey?: string | null;
  currentStepName?: string | null;
  submittedAt?: string | null;
  finishedAt?: string | null;
}

export interface WorkflowLeaveDetailResponse {
  leaveTypeId: EntityId;
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
  displayName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  scanStatus: string;
  scanStatusName?: string | null;
}

export interface WorkflowRequestDetailResponse extends WorkflowRequestSummaryResponse {
  applicantUserId: EntityId;
  applicantName?: string | null;
  applicantEmployeeNo?: string | null;
  flowableProcessInstanceId?: string | null;
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
  actorTypeName?: string | null;
  actorId?: EntityId | null;
  actorName?: string | null;
  actorAccount?: string | null;
  actionType: string;
  actionTypeName?: string | null;
  comment?: string | null;
  agentDecisionJson?: string | null;
  beforeStatus?: string | null;
  beforeStatusName?: string | null;
  afterStatus?: string | null;
  afterStatusName?: string | null;
  actedAt?: string | null;
}

export interface WorkflowProcessTypeCreateRequest {
  processCode: string;
  processName: string;
  bizType?: string | null;
  status?: string | null;
}

export interface WorkflowProcessTypeResponse {
  processTypeId: EntityId;
  processCode: string;
  processName: string;
  bizType?: string | null;
  bizTypeName?: string | null;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface WorkflowTemplateCreateRequest {
  processTypeId: EntityId;
  templateCode: string;
  templateName: string;
  status?: string | null;
}

export interface WorkflowTemplateVersionCreateRequest {
  versionNo: number;
  formSchemaJson?: string | null;
  ruleSnapshotJson?: string | null;
}

export interface WorkflowTemplateResponse {
  templateId: EntityId;
  processTypeId: EntityId;
  processCode: string;
  processName: string;
  templateCode: string;
  templateName: string;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  currentVersion?: WorkflowTemplateVersionResponse | null;
}

export interface WorkflowTemplateVersionResponse {
  versionId: EntityId;
  templateId: EntityId;
  versionNo: number;
  flowableDefinitionKey?: string | null;
  flowableDefinitionId?: string | null;
  formSchemaJson?: string | null;
  ruleSnapshotJson?: string | null;
  publishedBy?: EntityId | null;
  publishedAt?: string | null;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface LeaveTypeResponse {
  leaveTypeId: EntityId;
  leaveCode: string;
  leaveName: string;
  balanceMode: string;
  balanceModeName?: string | null;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface LeaveAccountResponse {
  accountId: EntityId;
  userId: EntityId;
  employeeNo?: string | null;
  realName?: string | null;
  leaveTypeId: EntityId;
  leaveCode: string;
  leaveName: string;
  balanceMode: string;
  balanceModeName?: string | null;
  availableMinutes: number;
  frozenMinutes: number;
  status: string;
  statusName?: string | null;
  lastTxnType?: string | null;
  lastChangeMinutes?: number | null;
}

export interface AdjustLeaveAccountRequest {
  userId: EntityId;
  leaveTypeId: EntityId;
  changeMinutes: number;
  reason?: string | null;
  sourceTxnKey?: string | null;
}

export interface AdminRoleResponse {
  roleId: EntityId;
  roleCode: string;
  roleName: string;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AdminPermissionResponse {
  permissionId: EntityId;
  permCode: string;
  permName: string;
  moduleCode?: string | null;
  moduleName?: string | null;
  actionCode?: string | null;
  actionName?: string | null;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AdminAuditLogQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  operatorType?: string;
  actionType?: string;
  targetType?: string;
}

export interface AdminAuditLogResponse {
  auditLogId: EntityId;
  operatorType: string;
  operatorTypeName?: string | null;
  operatorId?: EntityId | null;
  operatorName?: string | null;
  operatorAccount?: string | null;
  actionType: string;
  actionTypeName?: string | null;
  targetType?: string | null;
  targetTypeName?: string | null;
  targetId?: EntityId | null;
  targetDisplayName?: string | null;
  targetNo?: string | null;
  result?: string | null;
  resultName?: string | null;
  detailJson?: string | null;
  occurredAt?: string | null;
}

export interface AttendanceCalendarResponse {
  calendarId: EntityId;
  calendarCode: string;
  calendarName: string;
  calendarType: string;
  calendarTypeName?: string | null;
  status: string;
  statusName?: string | null;
}

export interface CreateAttendanceCalendarRequest {
  calendarCode: string;
  calendarName: string;
  calendarType: string;
  status: string;
}

export interface AttendanceShiftResponse {
  shiftId: EntityId;
  shiftCode: string;
  shiftName: string;
  startTime: string;
  endTime: string;
  onDutyPunchStart?: string | null;
  onDutyPunchEnd?: string | null;
  offDutyPunchStart?: string | null;
  offDutyPunchEnd?: string | null;
  status: string;
  statusName?: string | null;
}

export interface CreateAttendanceShiftRequest {
  shiftCode: string;
  shiftName: string;
  startTime: string;
  endTime: string;
  onDutyPunchStart?: string | null;
  onDutyPunchEnd?: string | null;
  offDutyPunchStart?: string | null;
  offDutyPunchEnd?: string | null;
  status: string;
}

export interface AttendanceGroupResponse {
  groupId: EntityId;
  groupCode: string;
  groupName: string;
  calendarId: EntityId;
  calendarName?: string | null;
  defaultShiftId: EntityId;
  defaultShiftName?: string | null;
  lateGraceMinutes: number;
  earlyLeaveGraceMinutes: number;
  status: string;
  statusName?: string | null;
}

export interface CreateAttendanceGroupRequest {
  groupCode: string;
  groupName: string;
  calendarId: EntityId;
  defaultShiftId: EntityId;
  lateGraceMinutes: number;
  earlyLeaveGraceMinutes: number;
  status: string;
}

export interface AttendanceGroupMemberResponse {
  memberId: EntityId;
  groupId: EntityId;
  groupName?: string | null;
  userId: EntityId;
  employeeNo?: string | null;
  realName?: string | null;
  effectiveFrom: string;
  effectiveTo?: string | null;
  sourceType: string;
  sourceTypeName?: string | null;
  status: string;
  statusName?: string | null;
}

export interface AddAttendanceGroupMemberRequest {
  userId: EntityId;
  effectiveFrom: string;
  effectiveTo?: string | null;
  sourceType: string;
}

export interface AttendanceScheduleResponse {
  scheduleId: EntityId;
  userId: EntityId;
  employeeNo?: string | null;
  realName?: string | null;
  workDate: string;
  shiftId: EntityId;
  shiftName?: string | null;
  calendarId?: EntityId | null;
  calendarName?: string | null;
  scheduleSource: string;
  scheduleSourceName?: string | null;
  status: string;
  statusName?: string | null;
}

export interface CreateAttendanceScheduleRequest {
  userId: EntityId;
  workDate: string;
  shiftId: EntityId;
  calendarId?: EntityId | null;
  scheduleSource: string;
}

export interface UpdateAttendanceScheduleRequest {
  shiftId: EntityId;
  calendarId?: EntityId | null;
  scheduleSource: string;
  status: string;
}

export interface AttendanceDailyResultResponse {
  dailyResultId: EntityId;
  userId: EntityId;
  employeeNo?: string | null;
  realName?: string | null;
  workDate: string;
  scheduleId?: EntityId | null;
  groupId?: EntityId | null;
  groupName?: string | null;
  shiftId?: EntityId | null;
  shiftName?: string | null;
  resultStatus: string;
  resultStatusName?: string | null;
  lateMinutes: number;
  earlyLeaveMinutes: number;
  absenceMinutes: number;
  workMinutes: number;
  exceptionFlag: boolean;
  calculatedAt?: string | null;
}

export interface AttendanceDateRangeQuery extends QueryParams {
  userId?: EntityId;
  startDate: string;
  endDate: string;
}

export interface StorageSummaryResponse {
  orgPoolQuotaBytes?: number | null;
  orgPoolUsedBytes?: number | null;
  orgPoolWarningThreshold?: number | string | null;
  orgPoolBlockThreshold?: number | string | null;
  cacheQuotaBytes?: number | null;
  cacheUsedBytes?: number | null;
  backendFreeBytes?: number | null;
  backendWritable?: boolean | null;
  writeProtectReason?: string | null;
  fileObjectCount?: number | null;
  attachmentRefCount?: number | null;
  recycleBinCount?: number | null;
  pendingCleanupObjectCount?: number | null;
}

export interface AdminFileResponse {
  attachmentRefId: EntityId;
  fileObjectId: EntityId;
  ownerType: string;
  ownerTypeName?: string | null;
  ownerId?: EntityId | null;
  originalFileName?: string | null;
  displayName?: string | null;
  uploaderType: string;
  uploaderTypeName?: string | null;
  uploaderId?: EntityId | null;
  uploaderName?: string | null;
  employeeNo?: string | null;
  mimeType?: string | null;
  fileSize?: number | null;
  scanStatus: string;
  scanStatusName?: string | null;
  deletedAt?: string | null;
  purgeAfter?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AdminFileQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  ownerType?: string;
  uploaderType?: string;
  uploaderId?: EntityId;
  scanStatus?: string;
  lifecycleStatus?: string;
}

export interface CleanupJobRunResponse {
  jobRunId: string;
  jobRunNo?: string | null;
  jobType: string;
  jobTypeName?: string | null;
  dryRun: boolean;
  jobStatus: string;
  jobStatusName?: string | null;
  expiredUploadTickets?: number | null;
  expiredDownloadTickets?: number | null;
  purgedAttachmentRefs?: number | null;
  deletedPhysicalObjects?: number | null;
  failedPhysicalObjects?: number | null;
  releasedBytes?: number | null;
  errorMessage?: string | null;
}

export interface RunCleanupJobRequest {
  jobType: string;
  dryRun?: boolean;
}

export interface RagflowSpaceResponse {
  spaceId: EntityId;
  spaceName: string;
  spaceCode: string;
  description?: string | null;
  visibility: string;
  documentCount: number;
  chunkCount: number;
  embeddingProvider?: string | null;
  embeddingModel?: string | null;
  embeddingModelId?: EntityId | null;
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankProvider?: string | null;
  rerankModel?: string | null;
  rerankModelId?: EntityId | null;
  chunkSize?: number | null;
  chunkOverlap?: number | null;
  retrievalTopK?: number | null;
  scoreThreshold?: number | string | null;
  status: string;
  visibilityName?: string | null;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface RagflowGlobalSettingResponse {
  settingId: EntityId;
  ocrBaseUrl?: string | null;
  ocrApiKeyConfigured?: boolean | null;
  ocrModel?: string | null;
  ocrModelId?: EntityId | null;
  embeddingBaseUrl?: string | null;
  embeddingApiKeyConfigured?: boolean | null;
  embeddingModel?: string | null;
  embeddingModelId?: EntityId | null;
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankBaseUrl?: string | null;
  rerankApiKeyConfigured?: boolean | null;
  rerankModel?: string | null;
  rerankModelId?: EntityId | null;
  chunkSize?: number | null;
  chunkOverlap?: number | null;
  retrievalTopK?: number | null;
  scoreThreshold?: number | string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface RagflowAclResponse {
  aclId: EntityId;
  spaceId: EntityId;
  subjectType: string;
  subjectId: EntityId;
  subjectName?: string | null;
  subjectCode?: string | null;
  permCode: string;
  subjectTypeName?: string | null;
  permCodeName?: string | null;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
}

export interface CreateRagflowSpaceRequest {
  spaceName: string;
  spaceCode: string;
  description?: string | null;
  visibility?: string | null;
  status?: string | null;
  embeddingProvider?: string | null;
  embeddingModel?: string | null;
  embeddingModelId?: EntityId | null;
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankProvider?: string | null;
  rerankModel?: string | null;
  rerankModelId?: EntityId | null;
  chunkSize?: number | null;
  chunkOverlap?: number | null;
  retrievalTopK?: number | null;
  scoreThreshold?: number | string | null;
}

export interface UpdateRagflowSpaceRequest {
  spaceName: string;
  description?: string | null;
  visibility?: string | null;
  status?: string | null;
  embeddingProvider?: string | null;
  embeddingModel?: string | null;
  embeddingModelId?: EntityId | null;
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankProvider?: string | null;
  rerankModel?: string | null;
  rerankModelId?: EntityId | null;
  chunkSize?: number | null;
  chunkOverlap?: number | null;
  retrievalTopK?: number | null;
  scoreThreshold?: number | string | null;
}

export interface UpdateRagflowGlobalSettingRequest {
  ocrBaseUrl?: string | null;
  ocrApiKey?: string | null;
  ocrModel?: string | null;
  ocrModelId?: EntityId | null;
  embeddingBaseUrl?: string | null;
  embeddingApiKey?: string | null;
  embeddingModel?: string | null;
  embeddingModelId?: EntityId | null;
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankBaseUrl?: string | null;
  rerankApiKey?: string | null;
  rerankModel?: string | null;
  rerankModelId?: EntityId | null;
  chunkSize?: number | null;
  chunkOverlap?: number | null;
  retrievalTopK?: number | null;
  scoreThreshold?: number | string | null;
}

export interface SaveRagflowAclRequest {
  items: Array<{
    subjectType: string;
    subjectId: EntityId;
    permCode?: string | null;
  }>;
}

export interface RagflowDocumentResponse {
  documentId: EntityId;
  spaceId: EntityId;
  attachmentRefId?: EntityId | null;
  sourceFileObjectId?: EntityId | null;
  title: string;
  mimeType?: string | null;
  fileSize?: number | null;
  contentHash?: string | null;
  parserType?: string | null;
  parserTypeName?: string | null;
  ingestStatus: string;
  ingestStatusName?: string | null;
  latestIngestJobId?: EntityId | null;
  ingestJobStatus?: string | null;
  ingestJobStatusName?: string | null;
  vectorStatus?: string | null;
  vectorStatusName?: string | null;
  chunkCount?: number | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateUploadTicketRequest {
  ownerType: string;
  ownerId?: EntityId | null;
  fileName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  contentMd5?: string | null;
  clientRequestId?: string | null;
}

export interface UploadTicketResponse {
  uploadTicketId?: EntityId | null;
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

export interface ConfirmUploadRequest {
  uploadTicketId: EntityId;
  objectKey: string;
  contentSha256: string;
  fileSize: number;
  ownerType: string;
  ownerId?: EntityId | null;
  displayName: string;
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
  scanStatusName?: string | null;
  createdAt?: string | null;
}

export interface CreateDownloadTicketRequest {
  downloadType: string;
  reason?: string | null;
}

export interface DownloadTicketResponse {
  downloadTicketId: EntityId;
  ticketNo: string;
  downloadUrl: string;
  expireAt?: string | null;
  fileName?: string | null;
  fileSize?: number | null;
}

export interface RagflowSearchTestResponse {
  query: string;
  hits: Array<{
    chunkId: EntityId;
    documentId: EntityId;
    documentTitle?: string | null;
    chunkNo?: number | null;
    contentText?: string | null;
    pageNo?: number | null;
    score?: number | null;
  }>;
}

/* =========================================================
   Org – 组织管理
   ========================================================= */

export interface OrgDepartmentTreeNodeResponse {
  id: EntityId;
  parentId?: EntityId | null;
  treePath?: string | null;
  deptCode: string;
  deptName: string;
  deptType?: string | null;
  deptLevel?: number | null;
  leaderUserId?: EntityId | null;
  leaderName?: string | null;
  status: string;
  children?: OrgDepartmentTreeNodeResponse[];
}

export interface OrgDepartmentSaveRequest {
  parentId?: EntityId | null;
  deptCode: string;
  deptName: string;
  deptType?: string | null;
  leaderUserId?: EntityId | null;
  status?: string | null;
}

export interface OrgPositionResponse {
  id: EntityId;
  positionCode: string;
  positionName: string;
  positionCategory?: string | null;
  status: string;
}

export interface OrgPositionSaveRequest {
  positionCode: string;
  positionName: string;
  positionCategory?: string | null;
  status?: string | null;
}

export interface OrgUserListQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  keyword?: string;
  deptId?: EntityId;
  employmentStatus?: string;
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
  status?: string | null;
}

export interface OrgUserDetailResponse {
  userId: EntityId;
  employeeNo?: string | null;
  realName: string;
  gender?: string | null;
  genderName?: string | null;
  mobile?: string | null;
  email?: string | null;
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

export interface OrgUserCreateRequest {
  employeeNo?: string | null;
  realName: string;
  gender?: string | null;
  mobile?: string | null;
  primaryDeptId?: EntityId | null;
  positionId?: EntityId | null;
  managerUserId?: EntityId | null;
  employmentStatus?: string | null;
  hireDate?: string | null;
  regularizedAt?: string | null;
  serviceStartDate?: string | null;
  annualLeaveStartDate?: string | null;
  workLocation?: string | null;
  status?: string | null;
}

/* =========================================================
   Agent 配置
   ========================================================= */

export interface AgentResponse {
  agentId: EntityId;
  agentCode?: string | null;
  agentName?: string | null;
  avatarAttachmentRefId?: EntityId | null;
  agentType?: string | null;
  agentTypeName?: string | null;
  identityMode?: string | null;
  identityModeName?: string | null;
  publishedVersionId?: EntityId | null;
  publishedVersionNo?: number | null;
  publishedVersionLabel?: string | null;
  knowledgeIssueCount?: number | null;
  knowledgeIssueMessage?: string | null;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateAgentRequest {
  agentCode: string;
  agentName: string;
  avatarAttachmentRefId?: EntityId | null;
  agentType?: string | null;
  identityMode?: string | null;
}

export interface UpdateAgentRequest {
  agentName: string;
  avatarAttachmentRefId?: EntityId | null;
}

export interface AgentVersionResponse {
  versionId: EntityId;
  agentId: EntityId;
  versionNo: number;
  versionLabel?: string | null;
  modelConfigJson?: string | null;
  systemPrompt?: string | null;
  workflowConfigJson?: string | null;
  publishStatus: string;
  workflowTestSignature?: string | null;
  workflowTestedAt?: string | null;
  publishedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AgentAclResponse {
  aclId: EntityId;
  agentId: EntityId;
  subjectType: string;
  subjectTypeName?: string | null;
  subjectId: EntityId;
  subjectName?: string | null;
  subjectCode?: string | null;
  permCode: string;
  permCodeName?: string | null;
  status: string;
  statusName?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface GrantAgentAclRequest {
  subjectType: string;
  subjectId: EntityId;
  permCode: string;
  status?: string | null;
}

export interface CreateAgentVersionRequest {
  versionNo?: number | null;
  versionLabel?: string | null;
  modelConfigJson?: string | null;
  systemPrompt?: string | null;
  workflowConfigJson?: string | null;
}

export interface UpdateAgentVersionRequest {
  modelConfigJson?: string | null;
  systemPrompt?: string | null;
  workflowConfigJson?: string | null;
}

export interface WorkflowValidateRequest {
  workflowConfigJson: string;
}

export interface WorkflowValidationResponse {
  valid: boolean;
  messages: string[];
}

export interface AgentToolResponse {
  toolId: EntityId;
  toolCode?: string | null;
  toolName?: string | null;
  toolType?: string | null;
  connectorId?: EntityId | null;
  riskLevel?: string | null;
  readOnlyFlag?: boolean | null;
  status?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

/* =========================================================
   Leave Type CRUD
   ========================================================= */

export interface CreateLeaveTypeRequest {
  leaveCode: string;
  leaveName: string;
  balanceMode?: string | null;
  status?: string | null;
}

/* =========================================================
   LLM Model Management
   ========================================================= */

export interface LlmModelResponse {
  modelId: EntityId;
  provider: string;
  providerName?: string | null;
  modelType?: string | null;
  modelTypeName?: string | null;
  modelName: string;
  displayName: string;
  endpoint?: string | null;
  apiKeyConfigured?: boolean | null;
  defaultFlag?: boolean | null;
  streamOutputFlag?: boolean | null;
  visionFlag?: boolean | null;
  toolCallFlag?: boolean | null;
  structuredOutputFlag?: boolean | null;
  reasoningFlag?: boolean | null;
  status: string;
  lastTestSuccess?: boolean | null;
  lastTestMessage?: string | null;
  lastTestedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface CreateLlmModelRequest {
  provider: string;
  modelType?: string | null;
  modelName: string;
  displayName: string;
  endpoint?: string | null;
  apiKey?: string | null;
  defaultFlag?: boolean | null;
  streamOutputFlag?: boolean | null;
  visionFlag?: boolean | null;
  toolCallFlag?: boolean | null;
  structuredOutputFlag?: boolean | null;
  reasoningFlag?: boolean | null;
  status?: string | null;
}

export interface TestLlmModelRequest {
  provider: string;
  modelType?: string | null;
  modelName: string;
  endpoint?: string | null;
  apiKey?: string | null;
  visionFlag?: boolean | null;
}

export interface AiProviderResponse {
  providerId: EntityId;
  providerType: string;
  providerTypeName?: string | null;
  displayName: string;
  baseUrl?: string | null;
  apiKeyConfigured?: boolean | null;
  enabled?: boolean | null;
  lastTestSuccess?: boolean | null;
  lastTestMessage?: string | null;
  lastTestedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ProviderModelOptionResponse {
  modelName: string;
  displayName?: string | null;
  modelType?: string | null;
}

export interface SaveAiProviderRequest {
  providerType: string;
  displayName: string;
  baseUrl?: string | null;
  apiKey?: string | null;
  enabled?: boolean | null;
}

/* =========================================================
   Notification Channel Settings
   ========================================================= */

export interface NotificationChannelResponse {
  channelCode: string;
  channelType: string;
  channelName: string;
  enabled: boolean;
  host?: string | null;
  port?: number | null;
  username?: string | null;
  hasSecret: boolean;
  fromAddress?: string | null;
  fromName?: string | null;
  sslEnabled: boolean;
  starttlsEnabled: boolean;
  timeoutMillis?: number | null;
  lastTestSuccess?: boolean | null;
  lastTestMessage?: string | null;
  lastTestedAt?: string | null;
  updatedAt?: string | null;
}

export interface NotificationChannelSaveRequest {
  enabled: boolean;
  host: string;
  port: number;
  username?: string | null;
  secretValue?: string | null;
  fromAddress: string;
  fromName?: string | null;
  sslEnabled: boolean;
  starttlsEnabled: boolean;
  timeoutMillis: number;
}

export interface NotificationChannelTestRequest extends NotificationChannelSaveRequest {
  recipient: string;
}

export interface NotificationChannelTestResponse {
  success: boolean;
  message: string;
  testedAt?: string | null;
}
