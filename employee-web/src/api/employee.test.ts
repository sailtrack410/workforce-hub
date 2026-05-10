import { describe, expect, it } from 'vitest';
import { createEmployeeApi } from './employee';
import type { ApiClient } from './http';

function createSpyClient() {
  const calls: Array<[string, string, unknown?]> = [];
  const client: ApiClient = {
    get: <T,>(path: string, options?: unknown) => {
      calls.push(['get', path, options]);
      return Promise.resolve(null as T);
    },
    post: <T,>(path: string, body?: unknown) => {
      calls.push(['post', path, body]);
      return Promise.resolve(null as T);
    },
    put: <T,>(path: string, body?: unknown) => {
      calls.push(['put', path, body]);
      return Promise.resolve(null as T);
    },
    delete: <T,>(path: string, options?: unknown) => {
      calls.push(['delete', path, options]);
      return Promise.resolve(null as T);
    },
  };
  return { client, calls };
}

describe('createEmployeeApi', () => {
  it('maps auth endpoints to backend employee auth controller', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.auth.login({ loginName: 'lin.che', password: 'secret' });
    await api.auth.me();
    await api.auth.logout();

    expect(calls).toEqual([
      ['post', '/api/employee/auth/login', { loginName: 'lin.che', password: 'secret' }],
      ['get', '/api/employee/auth/me', undefined],
      ['post', '/api/employee/auth/logout', undefined],
    ]);
  });

  it('maps workflow query and task actions to backend workflow controller', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.workflow.requests({ pageNo: 2, pageSize: 20, status: 'PENDING' });
    await api.workflow.approveTask('TASK-1', { comment: '同意' });

    expect(calls).toEqual([
      ['get', '/api/employee/workflow/requests', { query: { pageNo: 2, pageSize: 20, status: 'PENDING' } }],
      ['post', '/api/employee/workflow/tasks/TASK-1/approve', { comment: '同意' }],
    ]);
  });

  it('maps attendance, leave and file endpoints without mock-only paths', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.attendance.exceptions({ startDate: '2026-05-01', endDate: '2026-05-09' });
    await api.leave.accounts();
    await api.files.createDownloadTicket(42, { downloadType: 'PERSONAL_FILE', reason: '查看材料' });

    expect(calls).toEqual([
      ['get', '/api/employee/attendance/exceptions', { query: { startDate: '2026-05-01', endDate: '2026-05-09' } }],
      ['get', '/api/employee/leave/accounts', undefined],
      ['post', '/api/employee/files/42/download-tickets', { downloadType: 'PERSONAL_FILE', reason: '查看材料' }],
    ]);
  });

  it('maps IM conversations and messages to backend IM controller', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.im.conversations();
    await api.im.messages(7001);
    await api.im.sendMessage(7001, {
      messageType: 'TEXT',
      text: '我下午两点同步。',
      clientMessageId: 'client-msg-001',
    });

    expect(calls).toEqual([
      ['get', '/api/employee/im/conversations', undefined],
      ['get', '/api/employee/im/conversations/7001/messages', undefined],
      ['post', '/api/employee/im/conversations/7001/messages', {
        messageType: 'TEXT',
        text: '我下午两点同步。',
        clientMessageId: 'client-msg-001',
      }],
    ]);
  });
});
