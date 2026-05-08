import { describe, expect, it, vi } from 'vitest';
import { ApiClientError, createApiClient } from './http';

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: {
      'Content-Type': 'application/json',
      ...Object.fromEntries(new Headers(init.headers).entries()),
    },
  });
}

describe('createApiClient', () => {
  it('unwraps backend R data and sends JSON with session credentials', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({
      code: 0,
      message: 'ok',
      data: { userId: 12 },
      requestId: 'req-1',
    }));
    const client = createApiClient({ baseUrl: 'http://localhost:8080', fetcher });

    const data = await client.post('/api/employee/auth/login', {
      loginName: 'lin.che',
      password: 'secret',
    });

    expect(data).toEqual({ userId: 12 });
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][0]).toBe('http://localhost:8080/api/employee/auth/login');
    expect(fetcher.mock.calls[0][1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ loginName: 'lin.che', password: 'secret' }),
    });
    expect(fetcher.mock.calls[0][1]?.headers).toMatchObject({
      Accept: 'application/json',
      'Content-Type': 'application/json',
    });
  });

  it('serializes query params and drops empty values', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ code: 0, message: 'ok', data: [] }));
    const client = createApiClient({ fetcher });

    await client.get('/api/employee/workflow/requests', {
      query: {
        pageNo: 2,
        pageSize: 20,
        status: 'PENDING',
        ignored: undefined,
      },
    });

    expect(fetcher.mock.calls[0][0]).toBe('/api/employee/workflow/requests?pageNo=2&pageSize=20&status=PENDING');
  });

  it('throws ApiClientError when backend R code is not ok', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({
      code: 401,
      message: '未登录',
      data: null,
      requestId: 'req-auth',
    }));
    const client = createApiClient({ fetcher });

    await expect(client.get('/api/employee/auth/me')).rejects.toMatchObject({
      name: 'ApiClientError',
      code: 401,
      requestId: 'req-auth',
      message: '未登录',
    });
  });

  it('throws ApiClientError when HTTP status is not ok', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ message: 'server error' }, { status: 500 }));
    const client = createApiClient({ fetcher });

    await expect(client.get('/api/employee/auth/me')).rejects.toMatchObject({
      name: 'ApiClientError',
      status: 500,
    });
  });
});
