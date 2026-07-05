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

  it('maps workflow template publish endpoint', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse({ versionId: '9001', status: 'PUBLISHED' }));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.workflow.publishTemplateVersion('9001', { flowableDefinitionKey: 'leave_manager_approval' });

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/workflow/template-versions/9001/publish');
    expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body))).toEqual({ flowableDefinitionKey: 'leave_manager_approval' });
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

  it('maps leave type update and delete endpoints', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse({ leaveTypeId: '7001' }));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.leave.updateType('7001', { leaveCode: 'ANNUAL', leaveName: '年假', balanceMode: 'DEDUCT', status: 'ACTIVE' });
    await api.leave.deleteType('7001');

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/leave/types/7001');
    expect(fetcher.mock.calls[0][1]?.method).toBe('PUT');
    expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body))).toEqual({
      leaveCode: 'ANNUAL',
      leaveName: '年假',
      balanceMode: 'DEDUCT',
      status: 'ACTIVE',
    });
    expect(fetcher.mock.calls[1][0]).toBe('/api/admin/leave/types/7001');
    expect(fetcher.mock.calls[1][1]?.method).toBe('DELETE');
  });

  it('maps system governance endpoints', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse([]));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.system.roles();
    await api.system.permissions();
    await api.system.auditLogs({ pageNo: 2, pageSize: 10, actionType: 'UPLOAD' });

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/system/roles');
    expect(fetcher.mock.calls[1][0]).toBe('/api/admin/system/permissions');
    expect(fetcher.mock.calls[2][0]).toBe('/api/admin/system/audit-logs?pageNo=2&pageSize=10&actionType=UPLOAD');
  });

  it('maps ragflow global settings endpoint', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse({ settingId: '1' }));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.ragflow.updateSettings({ ocrBaseUrl: 'http://ocr.local', ocrApiKey: 'test-key', ocrModel: 'ocr-v1', retrievalTopK: 8 });

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/ragflow/settings');
    expect(fetcher.mock.calls[0][1]?.method).toBe('PUT');
    expect(JSON.parse(String(fetcher.mock.calls[0][1]?.body))).toEqual({ ocrBaseUrl: 'http://ocr.local', ocrApiKey: 'test-key', ocrModel: 'ocr-v1', retrievalTopK: 8 });
  });

  it('maps ragflow upload endpoints under ragflow permission path', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse({ uploadTicketId: '1', deduplicated: false, headers: {} }));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.ragflow.createUploadTicket({
      ownerType: 'RAGFLOW_DOCUMENT',
      ownerId: '9001',
      fileName: 'handbook.pdf',
      mimeType: 'application/pdf',
      fileSize: 12,
      contentSha256: 'a'.repeat(64),
    });
    await api.ragflow.confirmUpload({
      uploadTicketId: '1',
      objectKey: 'uploads/handbook.pdf',
      contentSha256: 'a'.repeat(64),
      fileSize: 12,
      ownerType: 'RAGFLOW_DOCUMENT',
      ownerId: '9001',
      displayName: 'handbook.pdf',
    });

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/ragflow/files/upload-tickets');
    expect(fetcher.mock.calls[1][0]).toBe('/api/admin/ragflow/files/confirm-upload');
  });

  it('maps rbac endpoints', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse([]));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.rbac.roles('admin');
    await api.rbac.assignRolePermissions('6001', ['7001']);
    await api.rbac.assignAdminRoles('8001', ['6001']);

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/rbac/roles?keyword=admin');
    expect(fetcher.mock.calls[1][0]).toBe('/api/admin/rbac/roles/6001/permissions');
    expect(fetcher.mock.calls[1][1]?.method).toBe('PUT');
    expect(JSON.parse(String(fetcher.mock.calls[1][1]?.body))).toEqual({ permissionIds: ['7001'] });
    expect(fetcher.mock.calls[2][0]).toBe('/api/admin/rbac/admin-users/8001/roles');
    expect(JSON.parse(String(fetcher.mock.calls[2][1]?.body))).toEqual({ roleIds: ['6001'] });
  });

  it('maps attendance governance endpoints', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse([]));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.attendance.shifts();
    await api.attendance.groups();
    await api.attendance.schedules({ userId: 2003, startDate: '2026-05-01', endDate: '2026-05-31' });
    await api.attendance.dailyResults({ userId: 2003, startDate: '2026-05-01', endDate: '2026-05-31' });

    expect(fetcher.mock.calls.map((call) => call[0])).toEqual([
      '/api/admin/attendance/shifts',
      '/api/admin/attendance/groups',
      '/api/admin/attendance/schedules?userId=2003&startDate=2026-05-01&endDate=2026-05-31',
      '/api/admin/attendance/daily-results?userId=2003&startDate=2026-05-01&endDate=2026-05-31',
    ]);
  });

  it('maps org chart users endpoint', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse([]));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.org.orgChartUsers();

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/users/org-chart');
  });

  it('maps department delete endpoint', async () => {
    const fetcher: FetcherMock = vi.fn(async () => jsonResponse(null));
    const api = createAdminApi(createApiClient({ fetcher: fetcher as unknown as typeof fetch }));

    await api.org.deleteDepartment('110');

    expect(fetcher.mock.calls[0][0]).toBe('/api/admin/departments/110');
    expect(fetcher.mock.calls[0][1]?.method).toBe('DELETE');
  });

  it('preserves unsafe integer identifiers', () => {
    const payload = parseJsonPreservingUnsafeIntegers('{"taskId":9223372036854775807,"publishedVersionId":2057829816590286850,"count":9223372036854775807}');

    expect(payload).toEqual({
      taskId: '9223372036854775807',
      publishedVersionId: '2057829816590286850',
      count: 9223372036854776000,
    });
  });

  it('preserves generic node ids in organization trees', () => {
    const payload = parseJsonPreservingUnsafeIntegers('{"id":2070202517426503681,"deptCode":"UI"}');

    expect(payload).toEqual({
      id: '2070202517426503681',
      deptCode: 'UI',
    });
  });
});
