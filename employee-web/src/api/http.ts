import type { ApiEnvelope, QueryParams, QueryValue } from './types';

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

export interface ApiClientOptions {
  baseUrl?: string;
  fetcher?: typeof fetch;
  defaultHeaders?: HeadersInit;
}

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const baseUrl = normalizeBaseUrl(options.baseUrl ?? '');
  const fetcher = options.fetcher ?? fetch.bind(globalThis);
  const defaultHeaders = headersToRecord(options.defaultHeaders);

  async function request<T>(path: string, requestOptions: ApiRequestOptions = {}): Promise<T> {
    const headers = {
      Accept: 'application/json',
      ...defaultHeaders,
      ...headersToRecord(requestOptions.headers),
    };
    const body = normalizeBody(requestOptions.body, headers);
    const response = await fetcher(buildUrl(baseUrl, path, requestOptions.query), {
      ...requestOptions,
      body,
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
    return response.json();
  }
  return response.text();
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
