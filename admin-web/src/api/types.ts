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
  contextType?: string | null;
  contextId?: EntityId | null;
  triggeredByType?: string | null;
  triggeredById?: EntityId | null;
  status: string;
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
  riskLevel?: string | null;
  readOnlyFlag?: boolean | null;
  requestPayloadJson?: string | null;
  responsePayloadJson?: string | null;
  approvalStatus?: string | null;
  resultStatus?: string | null;
  calledAt?: string | null;
}

export interface AdminAgentTaskAuditResponse {
  auditId: EntityId;
  taskId: EntityId;
  actionType: string;
  targetType?: string | null;
  targetId?: EntityId | null;
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
  triggerType: string;
  status: string;
  scannedCount: number;
  cancelledCount: number;
  skippedCount: number;
}

export interface AdminAgentTaskTimeoutRunResponse {
  jobRunId: string;
  triggerType: string;
  status: string;
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
  bizId: EntityId;
  title: string;
  status: string;
  currentStepKey?: string | null;
  currentStepName?: string | null;
  submittedAt?: string | null;
  finishedAt?: string | null;
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

export interface WorkflowRequestDetailResponse extends WorkflowRequestSummaryResponse {
  applicantUserId: EntityId;
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
  actorId?: EntityId | null;
  actionType: string;
  comment?: string | null;
  agentDecisionJson?: string | null;
  beforeStatus?: string | null;
  afterStatus?: string | null;
  actedAt?: string | null;
}

export interface WorkflowTemplateResponse {
  templateId: EntityId;
  processTypeId: EntityId;
  processCode: string;
  processName: string;
  templateCode: string;
  templateName: string;
  status: string;
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
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface LeaveTypeResponse {
  leaveTypeId: EntityId;
  leaveCode: string;
  leaveName: string;
  balanceMode: string;
  status: string;
  createdAt?: string | null;
  updatedAt?: string | null;
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
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface AdminPermissionResponse {
  permissionId: EntityId;
  permCode: string;
  permName: string;
  moduleCode?: string | null;
  actionCode?: string | null;
  status: string;
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
  operatorId?: EntityId | null;
  actionType: string;
  targetType?: string | null;
  targetId?: EntityId | null;
  result?: string | null;
  detailJson?: string | null;
  occurredAt?: string | null;
}

export interface AttendanceCalendarResponse {
  calendarId: EntityId;
  calendarCode: string;
  calendarName: string;
  calendarType: string;
  status: string;
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
  defaultShiftId: EntityId;
  lateGraceMinutes: number;
  earlyLeaveGraceMinutes: number;
  status: string;
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
  userId: EntityId;
  effectiveFrom: string;
  effectiveTo?: string | null;
  sourceType: string;
  status: string;
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
  workDate: string;
  shiftId: EntityId;
  calendarId?: EntityId | null;
  scheduleSource: string;
  status: string;
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
  workDate: string;
  scheduleId?: EntityId | null;
  groupId?: EntityId | null;
  shiftId?: EntityId | null;
  resultStatus: string;
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

export interface CleanupJobRunResponse {
  jobRunId: string;
  jobType: string;
  dryRun: boolean;
  jobStatus: string;
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
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankProvider?: string | null;
  rerankModel?: string | null;
  chunkSize?: number | null;
  chunkOverlap?: number | null;
  retrievalTopK?: number | null;
  scoreThreshold?: number | string | null;
  status: string;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface RagflowGlobalSettingResponse {
  settingId: EntityId;
  ocrBaseUrl?: string | null;
  ocrApiKeyConfigured?: boolean | null;
  ocrModel?: string | null;
  embeddingBaseUrl?: string | null;
  embeddingApiKeyConfigured?: boolean | null;
  embeddingModel?: string | null;
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankBaseUrl?: string | null;
  rerankApiKeyConfigured?: boolean | null;
  rerankModel?: string | null;
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
  permCode: string;
  status: string;
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
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankProvider?: string | null;
  rerankModel?: string | null;
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
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankProvider?: string | null;
  rerankModel?: string | null;
  chunkSize?: number | null;
  chunkOverlap?: number | null;
  retrievalTopK?: number | null;
  scoreThreshold?: number | string | null;
}

export interface UpdateRagflowGlobalSettingRequest {
  ocrBaseUrl?: string | null;
  ocrApiKey?: string | null;
  ocrModel?: string | null;
  embeddingBaseUrl?: string | null;
  embeddingApiKey?: string | null;
  embeddingModel?: string | null;
  embeddingDimension?: number | null;
  rerankEnabled?: boolean | null;
  rerankBaseUrl?: string | null;
  rerankApiKey?: string | null;
  rerankModel?: string | null;
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
  ingestStatus: string;
  latestIngestJobId?: EntityId | null;
  ingestJobStatus?: string | null;
  vectorStatus?: string | null;
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
  employeeNo: string;
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
  identityMode?: string | null;
  publishedVersionId?: EntityId | null;
  publishedVersionNo?: number | null;
  publishedVersionLabel?: string | null;
  status: string;
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
  subjectId: EntityId;
  permCode: string;
  status: string;
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
  modelName: string;
  displayName: string;
  endpoint?: string | null;
  apiKey?: string | null;
  status: string;
  createdAt?: string | null;
}

export interface CreateLlmModelRequest {
  provider: string;
  modelName: string;
  displayName: string;
  endpoint?: string | null;
  apiKey?: string | null;
  status?: string | null;
}
