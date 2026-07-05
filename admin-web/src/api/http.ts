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

// 后端用雪花算法生成 ID，超出 JS Number 安全整数范围，需要字符串保留精度
const unsafeIntegerFieldNames = new Set([
  'accountId',
  'aclId',
  'actionId',
  'adminUserId',
  'agentId',
  'agentTaskId',
  'agentVersionId',
  'applicantUserId',
  'attachmentRefId',
  'auditId',
  'bizId',
  'calendarId',
  'chunkId',
  'contextId',
  'dailyResultId',
  'documentId',
  'downloadTicketId',
  'fileObjectId',
  'groupId',
  'id',
  'leaveTypeId',
  'logId',
  'memberId',
  'permissionId',
  'policyId',
  'positionId',
  'processTypeId',
  'publishedBy',
  'publishedVersionId',
  'requestId',
  'roleId',
  'scheduleId',
  'shiftId',
  'spaceId',
  'subjectId',
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
