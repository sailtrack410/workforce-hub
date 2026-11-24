import { afterEach, expect, it, vi } from 'vitest';
import { http } from './http';
import { teamTaskApi, personalTodoApi } from './index';

afterEach(() => vi.unstubAllGlobals());
function mockFetch(text = '[]') {
  vi.stubGlobal('window', { location: { protocol: 'http:' } });
  vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=test-fixture' });
  const fetcher = vi.fn().mockImplementation(async () => new Response(text));
  vi.stubGlobal('fetch', fetcher); return fetcher;
}
it('任务角色和待办 ID 保留雪花精度', async () => {
  mockFetch('{"taskId":9007199254740993,"assigneeId":9007199254740995,"creatorId":9007199254740997,"todoId":9007199254740999}');
  expect(await http.get('/fixture')).toEqual({ taskId: '9007199254740993', assigneeId: '9007199254740995', creatorId: '9007199254740997', todoId: '9007199254740999' });
});
it('候选人与列表范围使用任务 API', async () => {
  const fetcher = mockFetch(); await teamTaskApi.assignees(); await teamTaskApi.list({ scope: 'CREATED' });
  expect(fetcher.mock.calls.map(c => c[0])).toEqual(['/api/employee/team-tasks/assignees', '/api/employee/team-tasks?scope=CREATED']);
});
it('清除待办时间使用完整更新并省略日期', async () => {
  const fetcher = mockFetch('{}'); await personalTodoApi.update('9007199254740993', { title: '待办', category: 'TASK', dueAt: undefined });
  expect(fetcher.mock.calls[0][0]).toBe('/api/employee/personal-todos/9007199254740993');
  expect(fetcher.mock.calls[0][1].method).toBe('PUT');
  expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ title: '待办', category: 'TASK' });
});
