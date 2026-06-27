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
  'agentTaskId',
  'agentId',
  'agentVersionId',
  'publishedVersionId',
  'applicantUserId',
  'attachmentRefId',
  'avatarAttachmentRefId',
  'bizId',
  'leaveTypeId',
  'accountId',
  'avatarFileId',
  'boundAgentId',
  'conversationId',
  'dailyResultId',
  'deptId',
  'primaryDeptId',
  'positionId',
  'managerUserId',
  'userId',
  'downloadTicketId',
  'exceptionId',
  'fileObjectId',
  'groupId',
  'memberId',
  'messageId',
  'ownerId',
  'ownerUserId',
  'policyId',
  'punchRecordId',
  'quotedMessageId',
  'requestId',
  'scheduleId',
  'shiftId',
  'subjectId',
  'taskId',
  'uploadTicketId',
  'senderId',
  'uploaderId',
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
