import { afterEach, expect, it, vi } from 'vitest';
import { http } from '@/api/http';
import { listPersonalTodos, savePersonalTodo } from './personalTodos';
import { pendingOperations, durableRequest } from './sync';
vi.mock('@/api/http', () => ({ http: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }));
vi.mock('./sync', () => ({ pendingOperations: vi.fn(), durableRequest: vi.fn() }));
afterEach(() => { vi.unstubAllGlobals(); vi.clearAllMocks(); });
it('离线列表叠加未同步修改及删除，更新携带缓存版本', async () => {
  vi.stubGlobal('window', { workforceDesktop: {} });
  vi.mocked(http.get).mockResolvedValue([{ todoId: '1', title: 'server', category: 'TASK', version: 4 }, { todoId: '2', title: 'deleted' }]);
  vi.mocked(pendingOperations).mockResolvedValue([{ id: 'a', method: 'PUT', path: '/api/employee/personal-todos/1', entityKey: '1', snapshot: { todoId: '1', title: 'offline', category: 'TASK', version: 4 }, state: 'pending' }, { id: 'b', method: 'DELETE', path: '/api/employee/personal-todos/2', entityKey: '2' }]);
  const todos = await listPersonalTodos();
  expect(todos).toHaveLength(1); expect(todos[0].title).toBe('offline'); expect(todos[0].syncState).toBe('pending');
  vi.mocked(durableRequest).mockResolvedValue({ queued: true });
  const updated = await savePersonalTodo('update', '1', { title: 'next', category: 'TASK' });
  expect(updated?.syncState).toBe('pending');
  expect(durableRequest).toHaveBeenCalledWith(expect.objectContaining({ body: { title: 'next', category: 'TASK', expectedVersion: 4 } }));
});
