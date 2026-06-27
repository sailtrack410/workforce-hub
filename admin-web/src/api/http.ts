import {
  ApiClientError,
  createApiClient as createSharedApiClient,
  parseJsonPreservingUnsafeIntegers as parseSharedJsonPreservingUnsafeIntegers,
  type ApiClient,
  type ApiClientOptions,
  type ApiRequestOptions,
} from '@shared/api-http';

export { ApiClientError };
export type { ApiClient, ApiRequestOptions };

const unsafeIntegerFieldNames = new Set([
  'accountId',
  'actionId',
  'agentId',
  'agentTaskId',
  'agentVersionId',
  'adminUserId',
  'applicantUserId',
  'attachmentRefId',
  'chunkId',
  'auditId',
  'bizId',
  'contextId',
  'fileObjectId',
  'id',
  'documentId',
  'downloadTicketId',
  'leaveTypeId',
  'logId',
  'policyId',
  'processTypeId',
  'publishedVersionId',
  'publishedBy',
  'permissionId',
  'requestId',
  'aclId',
  'roleId',
  'subjectId',
  'spaceId',
  'targetId',
  'taskId',
  'templateId',
  'toolId',
  'triggeredById',
  'uploadTicketId',
  'userId',
  'versionId',
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
