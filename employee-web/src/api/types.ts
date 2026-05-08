export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue | QueryValue[]>;

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
  userId: number;
  userType: string;
  displayName: string;
  loginName: string;
}

export interface CurrentUserResponse extends LoginResponse {
  primaryDeptId?: number | null;
  positionId?: number | null;
  managerUserId?: number | null;
  employmentStatus?: string | null;
}

export interface WorkflowRequestListQuery extends QueryParams {
  pageNo?: number;
  pageSize?: number;
  status?: string;
  bizType?: string;
}

export interface WorkflowTaskActionRequest {
  comment?: string;
}

export interface WorkflowTodoResponse {
  todoId: number;
  requestId: number;
  taskId: string;
  title: string;
  summary: string;
  status: string;
  priority?: string | null;
  createdAt?: string | null;
}

export interface WorkflowRequestSummaryResponse {
  requestId: number;
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

export interface LeaveRequestCreateRequest {
  leaveTypeId: number;
  startAt: string;
  endAt: string;
  durationMinutes: number;
  reason: string;
  attachmentRefIds?: number[];
}

export interface ClockCorrectionRequestCreateRequest {
  workDate: string;
  correctionType: string;
  correctedAt: string;
  reason: string;
  attachmentRefIds?: number[];
}

export interface AttendanceDateRangeQuery extends QueryParams {
  startDate?: string;
  endDate?: string;
}

export interface CreateUploadTicketRequest {
  ownerType: string;
  ownerId?: number | null;
  fileName: string;
  mimeType: string;
  fileSize: number;
  contentSha256: string;
  contentMd5?: string;
  clientRequestId?: string;
}

export interface ConfirmUploadRequest {
  ticketNo: string;
  objectKey: string;
}

export interface CreateDownloadTicketRequest {
  downloadType: string;
  reason?: string;
}

export interface FileTrashRequest {
  reason?: string;
}

export type UnknownRecord = Record<string, unknown>;
