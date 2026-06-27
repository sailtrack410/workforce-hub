import type { ApiEnvelope, QueryParams } from './types';

export class ApiClientError extends Error {
  readonly status?: number;
  readonly code?: number;
  readonly requestId?: string | null;
  readonly detail?: unknown;

  constructor(message: string, options: { status?: number; code?: number; requestId?: string | null; detail?: unknown } = {}) {
    super(message);
    this.name = 'ApiClientError';
    this.status = options.status;
    this.code = options.code;
    this.requestId = options.requestId;
    this.detail = options.detail;
  }
}

export interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  query?: QueryParams;
  body?: unknown;
}

export interface ApiClient {
  get<T = unknown>(path: string, options?: ApiRequestOptions): Promise<T>;
  post<T = unknown>(path: string, body?: unknown, options?: ApiRequestOptions): Promise<T>;
  put<T = unknown>(path: string, body?: unknown, options?: ApiRequestOptions): Promise<T>;
  delete<T = unknown>(path: string, options?: ApiRequestOptions): Promise<T>;
}

export function createApiClient(options: { baseUrl?: string; fetcher?: typeof fetch } = {}): ApiClient {
  const baseUrl = normalizeBaseUrl(options.baseUrl ?? '');
  const fetcher = options.fetcher ?? fetch.bind(globalThis);

  async function request<T>(path: string, requestOptions: ApiRequestOptions = {}): Promise<T> {
    const headers = {
      Accept: 'application/json',
      ...headersToRecord(requestOptions.headers),
    };
    const response = await fetcher(buildUrl(baseUrl, path, requestOptions.query), {
      ...requestOptions,
      body: normalizeBody(requestOptions.body, headers),
      credentials: requestOptions.credentials ?? 'include',
      headers,
    });
    const payload = await parsePayload(response);
    if (!response.ok) {
      throw new ApiClientError(resolveErrorMessage(payload, response.statusText), {
        status: response.status,
        detail: payload,
      });
    }
    if (isApiEnvelope(payload)) {
      if (payload.code !== 0) {
        throw new ApiClientError(payload.message || '请求失败', {
          code: payload.code,
          requestId: payload.requestId,
          detail: payload,
        });
      }
      return payload.data as T;
    }
    return payload as T;
  }

  return {
    get: <T>(path: string, requestOptions?: ApiRequestOptions) => request<T>(path, { ...requestOptions, method: 'GET' }),
    post: <T>(path: string, body?: unknown, requestOptions?: ApiRequestOptions) => request<T>(path, { ...requestOptions, method: 'POST', body }),
    put: <T>(path: string, body?: unknown, requestOptions?: ApiRequestOptions) => request<T>(path, { ...requestOptions, method: 'PUT', body }),
    delete: <T>(path: string, requestOptions?: ApiRequestOptions) => request<T>(path, { ...requestOptions, method: 'DELETE' }),
  };
}

function buildUrl(baseUrl: string, path: string, query?: QueryParams) {
  const url = `${baseUrl}${path.startsWith('/') ? path : `/${path}`}`;
  const queryString = serializeQuery(query);
  return queryString ? `${url}?${queryString}` : url;
}

function serializeQuery(query?: QueryParams) {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, rawValue] of Object.entries(query)) {
    const values = Array.isArray(rawValue) ? rawValue : [rawValue];
    for (const value of values) {
      if (value === undefined || value === null || value === '') continue;
      params.append(key, String(value));
    }
  }
  return params.toString();
}

function normalizeBaseUrl(baseUrl: string) {
  return baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl;
}

function headersToRecord(headers?: HeadersInit) {
  if (!headers) return {};
  return Object.fromEntries(new Headers(headers).entries());
}

function normalizeBody(body: ApiRequestOptions['body'], headers: Record<string, string>) {
  if (body === undefined || body === null) return undefined;
  if (isNativeBody(body)) return body;
  headers['Content-Type'] = headers['Content-Type'] ?? 'application/json';
  return JSON.stringify(body);
}

function isNativeBody(body: unknown): body is BodyInit {
  return (
    typeof body === 'string'
    || body instanceof ArrayBuffer
    || body instanceof URLSearchParams
    || (typeof Blob !== 'undefined' && body instanceof Blob)
    || (typeof FormData !== 'undefined' && body instanceof FormData)
  );
}

async function parsePayload(response: Response) {
  if (response.status === 204) return null;
  const contentType = response.headers.get('Content-Type') ?? '';
  if (contentType.includes('application/json')) {
    const text = await response.text();
    return text ? parseJsonPreservingUnsafeIntegers(text) : null;
  }
  return response.text();
}

export function parseJsonPreservingUnsafeIntegers(json: string) {
  return JSON.parse(preserveUnsafeIntegers(json));
}

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

function preserveUnsafeIntegers(json: string) {
  let output = '';
  let index = 0;

  while (index < json.length) {
    if (json[index] !== '"') {
      output += json[index];
      index += 1;
      continue;
    }

    const stringStart = index;
    index = readStringEnd(json, index + 1);
    const literal = json.slice(stringStart, index);
    output += literal;

    const fieldName = literal.includes('\\') ? '' : literal.slice(1, -1);
    if (!unsafeIntegerFieldNames.has(fieldName)) continue;

    const afterField = index;
    let cursor = skipWhitespace(json, afterField);
    if (json[cursor] !== ':') continue;
    cursor = skipWhitespace(json, cursor + 1);

    const numberStart = cursor;
    if (json[cursor] === '-') cursor += 1;
    const digitsStart = cursor;
    while (cursor < json.length && isDigit(json[cursor])) cursor += 1;
    const digitCount = cursor - digitsStart;
    if (digitCount < 16 || !isNumberBoundary(json, cursor)) continue;

    output += `${json.slice(afterField, numberStart)}"${json.slice(numberStart, cursor)}"`;
    index = cursor;
  }

  return output;
}

function readStringEnd(json: string, start: number) {
  let escaped = false;
  for (let index = start; index < json.length; index += 1) {
    const char = json[index];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (char === '\\') {
      escaped = true;
      continue;
    }
    if (char === '"') return index + 1;
  }
  return json.length;
}

function skipWhitespace(text: string, start: number) {
  let index = start;
  while (index < text.length && /\s/.test(text[index])) index += 1;
  return index;
}

function isDigit(char: string) {
  return char >= '0' && char <= '9';
}

function isNumberBoundary(json: string, index: number) {
  const cursor = skipWhitespace(json, index);
  return [',', '}', ']'].includes(json[cursor] ?? '');
}

function isApiEnvelope(payload: unknown): payload is ApiEnvelope<unknown> {
  return Boolean(
    payload
    && typeof payload === 'object'
    && typeof (payload as ApiEnvelope<unknown>).code === 'number'
    && 'message' in payload
    && 'data' in payload,
  );
}

function resolveErrorMessage(payload: unknown, fallback: string) {
  if (payload && typeof payload === 'object' && 'message' in payload) {
    const message = (payload as { message?: unknown }).message;
    if (typeof message === 'string' && message) return message;
  }
  return fallback || '请求失败';
}

export const defaultApiClient = createApiClient({
  baseUrl: import.meta.env.VITE_API_BASE_URL ?? '',
});
