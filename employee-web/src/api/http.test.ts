import { describe, expect, it, vi } from 'vitest';
import { ApiClientError, createApiClient, parseJsonPreservingUnsafeIntegers } from './http';

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

  it('preserves unsafe integer ids from JSON responses as strings', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(
      '{"code":0,"message":"ok","data":{"conversationId":2053928981082898411,"seqNo":2,"uploadTicketId":2053928981082898444,"downloadTicketId":2053928981082898455,"primaryDeptId":2053928808839610369,"positionId":2053928809028354049,"leaveTypeId":2053928811234567890,"agentId":2053928980726382594,"nested":{"messageId":2053928981082898422},"contentJson":"{\\"agentTaskId\\": 2053928981082898433}"}}',
      { status: 200, headers: { 'Content-Type': 'application/json' } },
    ));
    const client = createApiClient({ fetcher });

    const data = await client.get<{
      conversationId: string;
      seqNo: number;
      uploadTicketId: string;
      downloadTicketId: string;
      primaryDeptId: string;
      positionId: string;
      leaveTypeId: string;
      agentId: string;
      nested: { messageId: string };
      contentJson: string;
    }>('/api/employee/im/conversations');

    expect(data).toEqual({
      conversationId: '2053928981082898411',
      seqNo: 2,
      uploadTicketId: '2053928981082898444',
      downloadTicketId: '2053928981082898455',
      primaryDeptId: '2053928808839610369',
      positionId: '2053928809028354049',
      leaveTypeId: '2053928811234567890',
      agentId: '2053928980726382594',
      nested: { messageId: '2053928981082898422' },
      contentJson: '{"agentTaskId": 2053928981082898433}',
    });
  });

  it('preserves unsafe integer ids when parsing realtime event payloads', () => {
    const payload = parseJsonPreservingUnsafeIntegers(
      '{"eventType":"IM_MESSAGE_CREATED","conversationId":2053928981082898411,"payload":{"messageId":2053928981082898422,"conversationId":2053928981082898411,"senderId":2053928981082898403,"seqNo":3}}',
    ) as {
      conversationId: string;
      payload: {
        messageId: string;
        conversationId: string;
        senderId: string;
        seqNo: number;
      };
    };

    expect(payload.conversationId).toBe('2053928981082898411');
    expect(payload.payload.messageId).toBe('2053928981082898422');
    expect(payload.payload.conversationId).toBe('2053928981082898411');
    expect(payload.payload.senderId).toBe('2053928981082898403');
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
