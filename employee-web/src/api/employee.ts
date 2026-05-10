import { defaultApiClient, type ApiClient } from './http';
import type {
  AttendanceDateRangeQuery,
  ClockCorrectionRequestCreateRequest,
  ConfirmUploadRequest,
  CreateDownloadTicketRequest,
  CreateUploadTicketRequest,
  CurrentUserResponse,
  FileTrashRequest,
  ImConversationSummaryResponse,
  ImMessageResponse,
  LeaveRequestCreateRequest,
  LoginRequest,
  LoginResponse,
  PageResult,
  SendImMessageRequest,
  UnknownRecord,
  WorkflowRequestListQuery,
  WorkflowRequestSummaryResponse,
  WorkflowTaskActionRequest,
  WorkflowTodoResponse,
} from './types';

export function createEmployeeApi(client: ApiClient = defaultApiClient) {
  return {
    auth: {
      login: (request: LoginRequest) => client.post<LoginResponse>('/api/employee/auth/login', request),
      logout: () => client.post<void>('/api/employee/auth/logout'),
      me: () => client.get<CurrentUserResponse>('/api/employee/auth/me'),
      sendEmailCode: (request: { email: string; purpose: string }) => client.post<void>('/api/employee/auth/email/send-code', request),
      bindEmail: (request: { email: string; code: string }) => client.post<void>('/api/employee/auth/email/bind', request),
      resetPassword: (request: { loginName: string; email: string; code: string; newPassword: string }) => client.post<void>('/api/employee/auth/password/reset', request),
    },
    me: {
      profile: () => client.get<UnknownRecord>('/api/employee/me/profile'),
      manager: () => client.get<UnknownRecord>('/api/employee/me/manager'),
      subordinates: () => client.get<UnknownRecord[]>('/api/employee/me/subordinates'),
    },
    workflow: {
      submitLeaveRequest: (request: LeaveRequestCreateRequest) => client.post<UnknownRecord>('/api/employee/workflow/leave-requests', request),
      submitClockCorrectionRequest: (request: ClockCorrectionRequestCreateRequest) => client.post<UnknownRecord>('/api/employee/workflow/clock-correction-requests', request),
      todos: () => client.get<WorkflowTodoResponse[]>('/api/employee/workflow/todos'),
      requests: (query: WorkflowRequestListQuery = {}) => client.get<PageResult<WorkflowRequestSummaryResponse>>('/api/employee/workflow/requests', { query }),
      approveTask: (taskId: string, request: WorkflowTaskActionRequest) => client.post<UnknownRecord>(`/api/employee/workflow/tasks/${encodeURIComponent(taskId)}/approve`, request),
      rejectTask: (taskId: string, request: WorkflowTaskActionRequest) => client.post<UnknownRecord>(`/api/employee/workflow/tasks/${encodeURIComponent(taskId)}/reject`, request),
      withdrawRequest: (requestId: number, request: { reason?: string }) => client.post<UnknownRecord>(`/api/employee/workflow/requests/${requestId}/withdraw`, request),
      requestDetail: (requestId: number) => client.get<UnknownRecord>(`/api/employee/workflow/requests/${requestId}`),
      requestActions: (requestId: number) => client.get<UnknownRecord[]>(`/api/employee/workflow/requests/${requestId}/actions`),
      createAttachmentDownloadTicket: (requestId: number, attachmentRefId: number, request: CreateDownloadTicketRequest) => (
        client.post<UnknownRecord>(`/api/employee/workflow/requests/${requestId}/attachments/${attachmentRefId}/download-tickets`, request)
      ),
    },
    attendance: {
      punch: (request: UnknownRecord) => client.post<UnknownRecord>('/api/employee/attendance/punch', request),
      dailyResults: (query: AttendanceDateRangeQuery = {}) => client.get<UnknownRecord[]>('/api/employee/attendance/daily-results', { query }),
      exceptions: (query: AttendanceDateRangeQuery = {}) => client.get<UnknownRecord[]>('/api/employee/attendance/exceptions', { query }),
      teamDailyResults: (query: AttendanceDateRangeQuery & { userId?: number } = {}) => client.get<UnknownRecord[]>('/api/employee/attendance/team/daily-results', { query }),
    },
    leave: {
      accounts: () => client.get<UnknownRecord[]>('/api/employee/leave/accounts'),
    },
    im: {
      conversations: () => client.get<ImConversationSummaryResponse[]>('/api/employee/im/conversations'),
      messages: (conversationId: number) => (
        client.get<ImMessageResponse[]>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages`)
      ),
      sendMessage: (conversationId: number, request: SendImMessageRequest) => (
        client.post<ImMessageResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages`, request)
      ),
    },
    files: {
      createUploadTicket: (request: CreateUploadTicketRequest) => client.post<UnknownRecord>('/api/employee/files/upload-tickets', request),
      confirmUpload: (request: ConfirmUploadRequest) => client.post<UnknownRecord>('/api/employee/files/confirm-upload', request),
      quota: () => client.get<UnknownRecord>('/api/employee/files/quota'),
      createDownloadTicket: (attachmentRefId: number, request: CreateDownloadTicketRequest) => client.post<UnknownRecord>(`/api/employee/files/${attachmentRefId}/download-tickets`, request),
      trash: (attachmentRefId: number, request: FileTrashRequest) => client.post<UnknownRecord>(`/api/employee/files/${attachmentRefId}/trash`, request),
      restore: (attachmentRefId: number, request: FileTrashRequest) => client.post<UnknownRecord>(`/api/employee/files/${attachmentRefId}/restore`, request),
      purge: (attachmentRefId: number) => client.delete<UnknownRecord>(`/api/employee/files/${attachmentRefId}`),
    },
  };
}

export const employeeApi = createEmployeeApi();
