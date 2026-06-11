import { describe, expect, it, vi } from 'vitest';
import { createApiClient, parseJsonPreservingUnsafeIntegers } from './http';
import { createAdminApi } from './admin';

type FetcherMock = ReturnType<typeof vi.fn<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>>;

function jsonResponse(data: unknown) {
  return new Response(JSON.stringify({ code: 0, message: 'ok', data }), {
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('adminApi', () => {
  it('maps workflow request endpoints', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse({ pageNo: 1, pageSize: 20, total: 0, list: [] }));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.workflow.requests({ status: 'APPROVED', pageNo: 2 });

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/workflow/requests?status=APPROVED&pageNo=2');
  });

  it('maps high-risk operation endpoints', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse({ jobRunId: 'x', jobType: 'ALL_EXPIRED', dryRun: true, jobStatus: 'SUCCESS' }));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.files.runCleanupJob({ jobType: 'ALL_EXPIRED', dryRun: true });

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/files/cleanup-jobs/run');
    expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body))).toEqual({ jobType: 'ALL_EXPIRED', dryRun: true });
  });

  it('maps agent workflow validation endpoint', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse({ valid: true, messages: [] }));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.agentConfig.validateWorkflow({ workflowConfigJson: '{"nodes":[]}' });

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/agents/workflows/validate');
    expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body))).toEqual({ workflowConfigJson: '{"nodes":[]}' });
  });

  it('maps agent version update endpoint', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse({ versionId: '9001' }));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.agentConfig.updateVersion('9001', { systemPrompt: 'updated' });

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/agents/versions/9001');
    expect(fetcher.mock.calls[0][1]?.method).toBe('PUT');
    expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body))).toEqual({ systemPrompt: 'updated' });
  });

  it('maps ragflow global settings endpoint', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse({ settingId: '1' }));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.ragflow.updateSettings({ ocrBaseUrl: 'http://ocr.local', ocrApiKey: 'test-key', ocrModel: 'ocr-v1', retrievalTopK: 8 });

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/ragflow/settings');
    expect(fetcher.mock.calls[0][1]?.method).toBe('PUT');
    expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body))).toEqual({ ocrBaseUrl: 'http://ocr.local', ocrApiKey: 'test-key', ocrModel: 'ocr-v1', retrievalTopK: 8 });
  });

  it('preserves unsafe integer identifiers', () => {
    const payload = parseJsonPreservingUnsafeIntegers('{"taskId":9223372036854775807,"publishedVersionId":2057829816590286850,"count":9223372036854775807}');

    expect(payload).toEqual({
      taskId: '9223372036854775807',
      publishedVersionId: '2057829816590286850',
      count: 9223372036854776000,
    });
  });
});
