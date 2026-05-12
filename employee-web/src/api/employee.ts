import { defaultApiClient, type ApiClient } from './http';
import type {
  AttendanceDateRangeQuery,
  AttendanceDailyResultResponse,
  AttendanceExceptionResponse,
  AgentResponse,
  AgentTaskResponse,
  AttachmentRefResponse,
  CancelAgentTaskRequest,
  ClockCorrectionRequestCreateRequest,
  ConfirmAgentTaskRequest,
  ConfirmUploadRequest,
  CreateAgentTaskRequest,
  CreateGroupConversationRequest,
  CreateDownloadTicketRequest,
  CreatePunchRecordRequest,
  CreateUploadTicketRequest,
  CurrentUserResponse,
  DownloadTicketResponse,
  EditImMessageRequest,
  EmployeeFileResponse,
  EmployeePreferenceResponse,
  EntityId,
  FileListQuery,
  FilePurgeResponse,
  FileRestoreResponse,
  ImMessageListQuery,
  FileTrashRequest,
  FileTrashResponse,
  GroupMemberUserIdsRequest,
  ImConversationListQuery,
  ImConversationDetailResponse,
  ImConversationMemberResponse,
  ImConversationSummaryResponse,
  ImGroupProfileResponse,
  ImMessageResponse,
  LeaveRequestCreateRequest,
  LeaveAccountResponse,
  LoginRequest,
  LoginResponse,
  OrgUserDetailResponse,
  OrgUserSummaryResponse,
  PageResult,
  PunchRecordResponse,
  QuotaResponse,
  ResetPasswordRequest,
  SendImMessageRequest,
  SendEmailCodeRequest,
  StartAgentConversationRequest,
  StartPrivateConversationRequest,
  UpdateConversationPreferenceRequest,
  UpdateEmployeePreferenceRequest,
  UpdateEmployeeProfileRequest,
  UpdateGroupConversationRequest,
  UpdateReadPositionRequest,
  UploadTicketResponse,
  WorkflowRequestListQuery,
  WorkflowRequestCreateResponse,
  WorkflowRequestDetailResponse,
  WorkflowRequestStatusResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTaskActionResponse,
  WorkflowTaskApproveRequest,
  WorkflowTaskRejectRequest,
  WorkflowRequestWithdrawRequest,
  WorkflowTodoResponse,
} from './types';

const pathId = (value: EntityId) => encodeURIComponent(String(value));

export function createEmployeeApi(client: ApiClient = defaultApiClient) {
  return {
    auth: {
      login: (request: LoginRequest) => client.post<LoginResponse>('/api/employee/auth/login', request),
      logout: () => client.post<void>('/api/employee/auth/logout'),
      me: () => client.get<CurrentUserResponse>('/api/employee/auth/me'),
      sendEmailCode: (request: SendEmailCodeRequest) => client.post<void>('/api/employee/auth/email/send-code', request),
      bindEmail: (request: { email: string; code: string }) => client.post<void>('/api/employee/auth/email/bind', request),
      resetPassword: (request: ResetPasswordRequest) => client.post<void>('/api/employee/auth/password/reset', request),
    },
    me: {
      profile: () => client.get<OrgUserDetailResponse>('/api/employee/me/profile'),
      updateProfile: (request: UpdateEmployeeProfileRequest) => client.put<OrgUserDetailResponse>('/api/employee/me/profile', request),
      manager: () => client.get<OrgUserSummaryResponse | null>('/api/employee/me/manager'),
      subordinates: () => client.get<OrgUserSummaryResponse[]>('/api/employee/me/subordinates'),
      colleagues: () => client.get<OrgUserSummaryResponse[]>('/api/employee/me/colleagues'),
      preferences: () => client.get<EmployeePreferenceResponse>('/api/employee/me/preferences'),
      updatePreferences: (request: UpdateEmployeePreferenceRequest) => client.put<EmployeePreferenceResponse>('/api/employee/me/preferences', request),
    },
    workflow: {
      submitLeaveRequest: (request: LeaveRequestCreateRequest) => client.post<WorkflowRequestCreateResponse>('/api/employee/workflow/leave-requests', request),
      submitClockCorrectionRequest: (request: ClockCorrectionRequestCreateRequest) => client.post<WorkflowRequestCreateResponse>('/api/employee/workflow/clock-correction-requests', request),
      todos: () => client.get<WorkflowTodoResponse[]>('/api/employee/workflow/todos'),
      requests: (query: WorkflowRequestListQuery = {}) => client.get<PageResult<WorkflowRequestSummaryResponse>>('/api/employee/workflow/requests', { query }),
      approveTask: (taskId: string, request: WorkflowTaskApproveRequest) => client.post<WorkflowRequestStatusResponse>(`/api/employee/workflow/tasks/${encodeURIComponent(taskId)}/approve`, request),
      rejectTask: (taskId: string, request: WorkflowTaskRejectRequest) => client.post<WorkflowRequestStatusResponse>(`/api/employee/workflow/tasks/${encodeURIComponent(taskId)}/reject`, request),
      withdrawRequest: (requestId: EntityId, request: WorkflowRequestWithdrawRequest) => client.post<WorkflowRequestStatusResponse>(`/api/employee/workflow/requests/${pathId(requestId)}/withdraw`, request),
      requestDetail: (requestId: EntityId) => client.get<WorkflowRequestDetailResponse>(`/api/employee/workflow/requests/${pathId(requestId)}`),
      requestActions: (requestId: EntityId) => client.get<WorkflowTaskActionResponse[]>(`/api/employee/workflow/requests/${pathId(requestId)}/actions`),
      createAttachmentDownloadTicket: (requestId: EntityId, attachmentRefId: EntityId, request: CreateDownloadTicketRequest) => (
        client.post<DownloadTicketResponse>(`/api/employee/workflow/requests/${pathId(requestId)}/attachments/${pathId(attachmentRefId)}/download-tickets`, request)
      ),
      downloadAttachmentUrl: (ticketNo: string) => `/api/employee/workflow/attachments/download/${encodeURIComponent(ticketNo)}`,
    },
    attendance: {
      punch: (request: CreatePunchRecordRequest) => client.post<PunchRecordResponse>('/api/employee/attendance/punch', request),
      dailyResults: (query: AttendanceDateRangeQuery = {}) => client.get<AttendanceDailyResultResponse[]>('/api/employee/attendance/daily-results', { query }),
      exceptions: (query: AttendanceDateRangeQuery = {}) => client.get<AttendanceExceptionResponse[]>('/api/employee/attendance/exceptions', { query }),
      teamDailyResults: (query: AttendanceDateRangeQuery & { userId?: EntityId } = {}) => client.get<AttendanceDailyResultResponse[]>('/api/employee/attendance/team/daily-results', { query }),
    },
    leave: {
      accounts: () => client.get<LeaveAccountResponse[]>('/api/employee/leave/accounts'),
    },
    im: {
      conversations: (query: ImConversationListQuery = {}) => client.get<ImConversationSummaryResponse[]>('/api/employee/im/conversations', { query }),
      conversationDetail: (conversationId: string | number) => client.get<ImConversationDetailResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}`),
      groupMembers: (conversationId: string | number) => (
        client.get<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/members`)
      ),
      groupProfile: (conversationId: string | number) => (
        client.get<ImGroupProfileResponse>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/profile`)
      ),
      updateGroupProfile: (conversationId: string | number, request: UpdateGroupConversationRequest) => (
        client.post<ImGroupProfileResponse>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/profile`, request)
      ),
      addGroupMembers: (conversationId: string | number, request: GroupMemberUserIdsRequest) => (
        client.post<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/members`, request)
      ),
      removeGroupMembers: (conversationId: string | number, request: GroupMemberUserIdsRequest) => (
        client.post<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/members/remove`, request)
      ),
      leaveGroup: (conversationId: string | number) => (
        client.post<ImConversationMemberResponse[]>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/leave`)
      ),
      dismissGroup: (conversationId: string | number) => (
        client.post<void>(`/api/employee/im/group-conversations/${encodeURIComponent(conversationId)}/dismiss`)
      ),
      messages: (conversationId: string | number, query: ImMessageListQuery = {}) => (
        client.get<ImMessageResponse[]>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages`, { query })
      ),
      startPrivateConversation: (request: StartPrivateConversationRequest) => (
        client.post<ImConversationSummaryResponse>('/api/employee/im/private-conversations', request)
      ),
      createGroupConversation: (request: CreateGroupConversationRequest) => (
        client.post<ImConversationSummaryResponse>('/api/employee/im/group-conversations', request)
      ),
      startAgentConversation: (request: StartAgentConversationRequest) => (
        client.post<ImConversationSummaryResponse>('/api/employee/im/agent-conversations', request)
      ),
      sendMessage: (conversationId: string | number, request: SendImMessageRequest) => (
        client.post<ImMessageResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages`, request)
      ),
      editMessage: (conversationId: string | number, messageId: string | number, request: EditImMessageRequest) => (
        client.post<ImMessageResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/edit`, request)
      ),
      recallMessage: (conversationId: string | number, messageId: string | number) => (
        client.post<ImMessageResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/messages/${encodeURIComponent(messageId)}/recall`)
      ),
      createMessageAttachmentDownloadTicket: (
        conversationId: string | number,
        messageId: string | number,
        attachmentRefId: EntityId,
        request: CreateDownloadTicketRequest,
      ) => (
        client.post<DownloadTicketResponse>(
          `/api/employee/im/conversations/${encodeURIComponent(String(conversationId))}/messages/${encodeURIComponent(String(messageId))}/attachments/${pathId(attachmentRefId)}/download-tickets`,
          request,
        )
      ),
      updateReadPosition: (conversationId: string | number, request: UpdateReadPositionRequest) => (
        client.post<ImConversationSummaryResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/read-position`, request)
      ),
      updatePreference: (conversationId: string | number, request: UpdateConversationPreferenceRequest) => (
        client.post<ImConversationSummaryResponse>(`/api/employee/im/conversations/${encodeURIComponent(conversationId)}/preference`, request)
      ),
      downloadAttachmentUrl: (ticketNo: string) => `/api/employee/im/attachments/download/${encodeURIComponent(ticketNo)}`,
    },
    agents: {
      list: () => client.get<AgentResponse[]>('/api/employee/agents'),
      createTask: (agentId: EntityId, request: CreateAgentTaskRequest) => (
        client.post<AgentTaskResponse>(`/api/employee/agents/${pathId(agentId)}/tasks`, request)
      ),
    },
    agentTasks: {
      confirm: (taskId: EntityId, request: ConfirmAgentTaskRequest) => (
        client.post<AgentTaskResponse>(`/api/employee/agent-tasks/${pathId(taskId)}/confirm`, request)
      ),
      cancel: (taskId: EntityId, request: CancelAgentTaskRequest) => (
        client.post<AgentTaskResponse>(`/api/employee/agent-tasks/${pathId(taskId)}/cancel`, request)
      ),
    },
    files: {
      list: (query: FileListQuery = {}) => client.get<PageResult<EmployeeFileResponse>>('/api/employee/files', { query }),
      recycleBin: (query: FileListQuery = {}) => client.get<PageResult<EmployeeFileResponse>>('/api/employee/files/recycle-bin', { query }),
      createUploadTicket: (request: CreateUploadTicketRequest) => client.post<UploadTicketResponse>('/api/employee/files/upload-tickets', request),
      confirmUpload: (request: ConfirmUploadRequest) => client.post<AttachmentRefResponse>('/api/employee/files/confirm-upload', request),
      quota: () => client.get<QuotaResponse>('/api/employee/files/quota'),
      createDownloadTicket: (attachmentRefId: EntityId, request: CreateDownloadTicketRequest) => client.post<DownloadTicketResponse>(`/api/employee/files/${pathId(attachmentRefId)}/download-tickets`, request),
      trash: (attachmentRefId: EntityId, request: FileTrashRequest) => client.post<FileTrashResponse>(`/api/employee/files/${pathId(attachmentRefId)}/trash`, request),
      restore: (attachmentRefId: EntityId, request: FileTrashRequest) => client.post<FileRestoreResponse>(`/api/employee/files/${pathId(attachmentRefId)}/restore`, request),
      purge: (attachmentRefId: EntityId) => client.delete<FilePurgeResponse>(`/api/employee/files/${pathId(attachmentRefId)}`),
      downloadUrl: (ticketNo: string) => `/api/employee/files/download/${encodeURIComponent(ticketNo)}`,
    },
  };
}

export const employeeApi = createEmployeeApi();
