import {
  ApiClientError,
  createApiClient as createSharedApiClient,
  parseJsonPreservingUnsafeIntegers as parseSharedJsonPreservingUnsafeIntegers,
  type ApiClient,
  type ApiClientOptions,
  type ApiRequestOptions,
} from '@shared/api-http';

export { ApiClientError };
export type { ApiClient, ApiClientOptions, ApiRequestOptions };

const unsafeIntegerFieldNames = new Set([
  'accountId',
  'agentId',
  'agentTaskId',
  'agentVersionId',
  'applicantUserId',
  'attachmentRefId',
  'avatarAttachmentRefId',
  'avatarFileId',
  'bizId',
  'boundAgentId',
  'conversationId',
  'dailyResultId',
  'deptId',
  'downloadTicketId',
  'exceptionId',
  'fileObjectId',
  'groupId',
  'leaveTypeId',
  'managerUserId',
  'memberId',
  'messageId',
  'ownerId',
  'ownerUserId',
  'policyId',
  'positionId',
  'primaryDeptId',
  'publishedVersionId',
  'punchRecordId',
  'quotedMessageId',
  'requestId',
  'scheduleId',
  'senderId',
  'shiftId',
  'subjectId',
  'taskId',
  'uploadTicketId',
  'uploaderId',
  'userId',
]);

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  return createSharedApiClient({
    ...options,
    unsafeIntegerFieldNames,
  });
}

export function parseJsonPreservingUnsafeIntegers(json: string) {
  return parseSharedJsonPreservingUnsafeIntegers(json, unsafeIntegerFieldNames);
}

export const defaultApiClient = createApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
});
