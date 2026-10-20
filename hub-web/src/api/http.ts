// hub-web API 层：fetch 封装，对齐后端协议
// 1) 信封响应 {code,message,data,requestId}，code!==0 抛错
// 2) 非 GET 请求需要 XSRF-TOKEN（/api/csrf 引导）
// 3) 雪花 ID（16 位以上数字）按字符串解析，防止精度丢失

export class ApiError extends Error {
  status?: number;
  code?: number;
  requestId?: string | null;
  constructor(message: string, options: { status?: number; code?: number; requestId?: string | null } = {}) {
    super(message);
    this.status = options.status;
    this.code = options.code;
    this.requestId = options.requestId;
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined>;
}

// 需要按字符串保留精度的大整数 ID 字段
const UNSAFE_INTEGER_FIELDS = new Set([
  'accountId', 'agentId', 'agentTaskId', 'agentVersionId', 'applicantUserId',
  'attachmentRefId', 'avatarAttachmentRefId', 'avatarFileId', 'bizId', 'boundAgentId',
  'conversationId', 'dailyResultId', 'deptId', 'downloadTicketId', 'exceptionId',
  'fileObjectId', 'groupId', 'leaveTypeId', 'managerUserId', 'memberId', 'messageId',
  'ownerId', 'ownerUserId', 'policyId', 'positionId', 'primaryDeptId',
  'publishedVersionId', 'punchRecordId', 'quotedMessageId', 'requestId', 'scheduleId',
  'senderId', 'shiftId', 'subjectId', 'taskId', 'uploadTicketId', 'uploaderId', 'userId',
]);

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);
let csrfHeaderName = 'X-XSRF-TOKEN';
let csrfBootstrap: Promise<void> | null = null;

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  const raw = document.cookie.split(';').map((v) => v.trim()).find((v) => v.startsWith(prefix));
  if (!raw) return null;
  try {
    return decodeURIComponent(raw.slice(prefix.length));
  } catch {
    return null;
  }
}

async function ensureCsrfToken(): Promise<void> {
  if (readCookie('XSRF-TOKEN')) return;
  if (!csrfBootstrap) {
    csrfBootstrap = fetch('/api/csrf', { credentials: 'include' })
      .then(async (response) => {
        const payload = await response.json().catch(() => ({}));
        const headerName = (payload as { data?: { headerName?: string }; headerName?: string })?.data?.headerName
          ?? (payload as { headerName?: string }).headerName;
        if (headerName) csrfHeaderName = headerName;
      })
      .catch(() => undefined)
      .finally(() => { csrfBootstrap = null; });
  }
  await csrfBootstrap;
}

function buildQuery(query?: RequestOptions['query']): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    params.set(key, String(value));
  }
  const str = params.toString();
  return str ? `?${str}` : '';
}

/* ---- 大整数精度保护：把 16 位以上的数字字面量加引号 ---- */
function preserveUnsafeIntegers(json: string): string {
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
    if (!UNSAFE_INTEGER_FIELDS.has(fieldName)) continue;
    let cursor = skipWhitespace(json, index);
    if (json[cursor] !== ':') continue;
    cursor = skipWhitespace(json, cursor + 1);
    const numberStart = cursor;
    if (json[cursor] === '-') cursor += 1;
    const digitsStart = cursor;
    while (cursor < json.length && json[cursor] >= '0' && json[cursor] <= '9') cursor += 1;
    const digitCount = cursor - digitsStart;
    const boundary = skipWhitespace(json, cursor);
    if (digitCount < 16 || ![',', '}', ']'].includes(json[boundary] ?? '')) continue;
    output += `${json.slice(index, numberStart)}"${json.slice(numberStart, cursor)}"`;
    index = cursor;
  }
  return output;
}
function readStringEnd(json: string, start: number): number {
  let escaped = false;
  for (let i = start; i < json.length; i += 1) {
    if (escaped) { escaped = false; continue; }
    if (json[i] === '\\') { escaped = true; continue; }
    if (json[i] === '"') return i + 1;
  }
  return json.length;
}
function skipWhitespace(text: string, start: number): number {
  let i = start;
  while (i < text.length && /\s/.test(text[i])) i += 1;
  return i;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (!SAFE_METHODS.has(method)) {
    await ensureCsrfToken();
    const token = readCookie('XSRF-TOKEN');
    if (token) headers[csrfHeaderName] = token;
  }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';

  const response = await fetch(`${path}${buildQuery(options.query)}`, {
    method,
    credentials: 'include',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  const text = await response.text();
  let payload: unknown = null;
  if (text) {
    try {
      payload = JSON.parse(preserveUnsafeIntegers(text));
    } catch {
      payload = text;
    }
  }

  if (!response.ok) {
    const message = (payload as { message?: string })?.message || `请求失败（${response.status}）`;
    throw new ApiError(message, { status: response.status });
  }
  // 信封解包
  if (
    payload && typeof payload === 'object'
    && typeof (payload as { code?: unknown }).code === 'number'
    && 'data' in payload
  ) {
    const envelope = payload as { code: number; message?: string; data: T; requestId?: string | null };
    if (envelope.code !== 0) {
      throw new ApiError(envelope.message || '请求失败', { code: envelope.code, requestId: envelope.requestId });
    }
    return envelope.data;
  }
  return payload as T;
}

export const http = {
  get: <T>(path: string, query?: RequestOptions['query']) => request<T>(path, { query }),
  post: <T>(path: string, body?: unknown, query?: RequestOptions['query']) =>
    request<T>(path, { method: 'POST', body, query }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
