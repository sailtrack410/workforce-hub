import { afterEach, describe, expect, it, vi } from 'vitest';
import { createApiClient } from './http';

function jsonResponse(body: unknown, init: ResponseInit = {}) {
  return new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: {
      'Content-Type': 'application/json',
      ...Object.fromEntries(new Headers(init.headers).entries()),
    },
  });
}

function csrfResponse(token = 'bootstrap-token', headerName = 'X-XSRF-TOKEN') {
  return jsonResponse({
    code: 0,
    message: 'ok',
    data: { headerName, token },
  });
}

function successResponse(data: unknown = null) {
  return jsonResponse({ code: 0, message: 'ok', data });
}

function stubDocumentCookie(cookie: string) {
  const documentStub = { cookie };
  vi.stubGlobal('document', documentStub);
  return documentStub;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('createApiClient csrf', () => {
  it('bootstraps and adds csrf header before the first unsafe request', async () => {
    vi.stubGlobal('document', undefined);
    const fetcher = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(successResponse({ saved: true }));
    const client = createApiClient({ baseUrl: 'http://localhost:8080', fetcher });

    await expect(client.post('/api/admin/settings', { enabled: true })).resolves.toEqual({ saved: true });

    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher.mock.calls[0][0]).toBe('http://localhost:8080/api/csrf');
    expect(fetcher.mock.calls[0][1]).toMatchObject({ method: 'GET', credentials: 'include' });
    expect(new Headers(fetcher.mock.calls[0][1]?.headers).has('X-XSRF-TOKEN')).toBe(false);
    expect(fetcher.mock.calls[1][0]).toBe('http://localhost:8080/api/admin/settings');
    expect(new Headers(fetcher.mock.calls[1][1]?.headers).get('X-XSRF-TOKEN')).toBe('bootstrap-token');
  });

  it('adds csrf to POST, PUT, and DELETE but not GET', async () => {
    stubDocumentCookie('XSRF-TOKEN=cookie-token');
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(successResponse()));
    const client = createApiClient({ fetcher });

    await client.post('/api/post');
    await client.put('/api/put');
    await client.delete('/api/delete');
    await client.get('/api/get');

    expect(fetcher).toHaveBeenCalledTimes(4);
    expect(new Headers(fetcher.mock.calls[0][1]?.headers).get('X-XSRF-TOKEN')).toBe('cookie-token');
    expect(new Headers(fetcher.mock.calls[1][1]?.headers).get('X-XSRF-TOKEN')).toBe('cookie-token');
    expect(new Headers(fetcher.mock.calls[2][1]?.headers).get('X-XSRF-TOKEN')).toBe('cookie-token');
    expect(new Headers(fetcher.mock.calls[3][1]?.headers).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('reads the latest csrf cookie for every unsafe request', async () => {
    const documentStub = stubDocumentCookie('XSRF-TOKEN=first-token');
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(successResponse()));
    const client = createApiClient({ fetcher });

    await client.post('/api/first');
    documentStub.cookie = 'XSRF-TOKEN=second-token';
    await client.put('/api/second');

    expect(new Headers(fetcher.mock.calls[0][1]?.headers).get('X-XSRF-TOKEN')).toBe('first-token');
    expect(new Headers(fetcher.mock.calls[1][1]?.headers).get('X-XSRF-TOKEN')).toBe('second-token');
  });

  it('matches the exact csrf cookie name and URL decodes its value', async () => {
    stubDocumentCookie('MY-XSRF-TOKEN=wrong; XSRF-TOKEN=token%20with%2Fslash; XSRF-TOKEN-OLD=stale');
    const fetcher = vi.fn().mockResolvedValue(successResponse());
    const client = createApiClient({ fetcher });

    await client.post('/api/exact-cookie');

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(new Headers(fetcher.mock.calls[0][1]?.headers).get('X-XSRF-TOKEN')).toBe('token with/slash');
  });

  it('falls back to bootstrap for damaged cookie encoding and without document', async () => {
    const documentStub = stubDocumentCookie('XSRF-TOKEN=%E0%A4%A');
    const damagedFetcher = vi.fn()
      .mockResolvedValueOnce(csrfResponse('damaged-fallback'))
      .mockResolvedValueOnce(successResponse());
    await createApiClient({ fetcher: damagedFetcher }).post('/api/damaged-cookie');
    expect(new Headers(damagedFetcher.mock.calls[1][1]?.headers).get('X-XSRF-TOKEN')).toBe('damaged-fallback');

    documentStub.cookie = '';
    vi.stubGlobal('document', undefined);
    const documentlessFetcher = vi.fn()
      .mockResolvedValueOnce(csrfResponse('documentless-fallback'))
      .mockResolvedValueOnce(successResponse());
    await createApiClient({ fetcher: documentlessFetcher }).post('/api/documentless');
    expect(new Headers(documentlessFetcher.mock.calls[1][1]?.headers).get('X-XSRF-TOKEN')).toBe('documentless-fallback');
  });

  it('keeps a caller csrf header and skips bootstrap', async () => {
    vi.stubGlobal('document', undefined);
    const fetcher = vi.fn().mockResolvedValue(successResponse());
    const client = createApiClient({ fetcher });

    await client.post('/api/caller-token', null, {
      headers: { 'x-xsrf-token': 'caller-token' },
    });

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][0]).toBe('/api/caller-token');
    expect(new Headers(fetcher.mock.calls[0][1]?.headers).get('X-XSRF-TOKEN')).toBe('caller-token');
  });

  it('shares one bootstrap across concurrent first unsafe requests', async () => {
    vi.stubGlobal('document', undefined);
    const fetcher = vi.fn()
      .mockResolvedValueOnce(csrfResponse('shared-token'))
      .mockImplementation(() => Promise.resolve(successResponse()));
    const client = createApiClient({ fetcher });

    await Promise.all([
      client.post('/api/first'),
      client.put('/api/second'),
      client.delete('/api/third'),
    ]);

    expect(fetcher.mock.calls.filter(([url]) => url === '/api/csrf')).toHaveLength(1);
    expect(fetcher).toHaveBeenCalledTimes(4);
    for (const call of fetcher.mock.calls.slice(1)) {
      expect(new Headers(call[1]?.headers).get('X-XSRF-TOKEN')).toBe('shared-token');
    }
  });

  it('prefers a cookie set by bootstrap over the bootstrap response token', async () => {
    const documentStub = stubDocumentCookie('');
    const fetcher = vi.fn()
      .mockImplementationOnce(async () => {
        documentStub.cookie = 'XSRF-TOKEN=cookie-after-bootstrap';
        return csrfResponse('body-token');
      })
      .mockResolvedValueOnce(successResponse());
    const client = createApiClient({ fetcher });

    await client.post('/api/cookie-priority');

    expect(new Headers(fetcher.mock.calls[1][1]?.headers).get('X-XSRF-TOKEN')).toBe('cookie-after-bootstrap');
  });

  it('keeps the bootstrap header name for later requests and honors a caller override', async () => {
    const documentStub = stubDocumentCookie('');
    const fetcher = vi.fn()
      .mockImplementationOnce(async () => {
        documentStub.cookie = 'XSRF-TOKEN=first-cookie';
        return csrfResponse('bootstrap-body-token', 'X-CSRF-TOKEN');
      })
      .mockImplementation(() => Promise.resolve(successResponse()));
    const client = createApiClient({ fetcher });

    await client.post('/api/custom-first');
    documentStub.cookie = 'XSRF-TOKEN=second-cookie';
    await client.put('/api/custom-second');
    await client.delete('/api/custom-caller', { headers: { 'X-CSRF-TOKEN': 'caller-token' } });

    expect(new Headers(fetcher.mock.calls[1][1]?.headers).get('X-CSRF-TOKEN')).toBe('first-cookie');
    expect(new Headers(fetcher.mock.calls[1][1]?.headers).has('X-XSRF-TOKEN')).toBe(false);
    expect(new Headers(fetcher.mock.calls[2][1]?.headers).get('X-CSRF-TOKEN')).toBe('second-cookie');
    expect(new Headers(fetcher.mock.calls[2][1]?.headers).has('X-XSRF-TOKEN')).toBe(false);
    expect(new Headers(fetcher.mock.calls[3][1]?.headers).get('X-CSRF-TOKEN')).toBe('caller-token');
    expect(new Headers(fetcher.mock.calls[3][1]?.headers).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('allows a later unsafe request to retry after bootstrap fails', async () => {
    vi.stubGlobal('document', undefined);
    const fetcher = vi.fn()
      .mockRejectedValueOnce(new Error('bootstrap failed'))
      .mockResolvedValueOnce(csrfResponse('retry-token'))
      .mockResolvedValueOnce(successResponse());
    const client = createApiClient({ fetcher });

    await expect(client.post('/api/first')).rejects.toThrow('bootstrap failed');
    await expect(client.post('/api/retry')).resolves.toBeNull();

    expect(fetcher).toHaveBeenCalledTimes(3);
    expect(fetcher.mock.calls[1][0]).toBe('/api/csrf');
    expect(new Headers(fetcher.mock.calls[2][1]?.headers).get('X-XSRF-TOKEN')).toBe('retry-token');
  });

  it('keeps a business 403 as ApiClientError without replaying the request', async () => {
    vi.stubGlobal('document', undefined);
    const fetcher = vi.fn()
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({ message: '禁止访问' }, { status: 403 }));
    const client = createApiClient({ fetcher });

    await expect(client.post('/api/forbidden')).rejects.toMatchObject({
      name: 'ApiClientError',
      status: 403,
    });
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
});
