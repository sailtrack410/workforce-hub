import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { http, ApiError } from '@/api/http';
import { readTodoConflict, resolveTodoConflict, type SyncOperation } from './sync';
import { localStatus } from './localData';

vi.mock('@/api/http', () => ({ http: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }, ApiError: class extends Error { status?: number; constructor(message: string, options: { status: number }) { super(message); this.status = options.status; } } }));
const remote = { todoId: '123', title: '远端名称', note: '远端备注', category: 'TASK', status: 'PENDING', version: 3 };
let queue: SyncOperation[], context: string, discard: ReturnType<typeof vi.fn>;
beforeEach(() => {
  vi.resetAllMocks(); localStatus.suspended = false; context = 'account-a';
  queue = [{ id: 'conflict', method: 'PUT', path: '/api/employee/personal-todos/123', entityKey: '123', version: 1, state: 'conflict',
    body: { title: '本地名称' }, snapshot: { ...remote, title: '本地名称', version: 1 } },
  { id: 'later', method: 'PUT', path: '/api/employee/personal-todos/123/complete', entityKey: '123', snapshot: { ...remote, title: '本地名称', status: 'DONE', version: 1 }, state: 'pending' }];
  discard = vi.fn(async (_context: string, ids: string[]) => { queue = queue.filter(op => !ids.includes(op.id)); });
  vi.stubGlobal('CustomEvent', class { type: string; detail: unknown; constructor(type: string, options: { detail: unknown }) { this.type = type; this.detail = options.detail; } });
  vi.stubGlobal('window', { dispatchEvent: vi.fn(), workforceDesktop: { cacheContext: async () => context,
    pendingOperations: async () => structuredClone(queue), discardOperations: discard, cacheInvalidate: vi.fn(async () => undefined) } });
  vi.mocked(http.get).mockResolvedValue(remote);
});
afterEach(() => vi.unstubAllGlobals());

it('版本对比直接读取远端，并汇总该待办最后的本地修改', async () => {
  const conflict = await readTodoConflict('conflict');
  expect(conflict.local.status).toBe('DONE'); expect(conflict.remote).toEqual(remote);
  expect(http.get).toHaveBeenCalledWith('/api/employee/personal-todos/123', undefined, { cache: false });
});
it('采用远端只删除该待办整组修改，保留其他实体队列', async () => {
  queue.push({ id: 'other', method: 'PUT', path: '/api/employee/personal-todos/456', entityKey: '456' });
  await resolveTodoConflict(await readTodoConflict('conflict'), 'remote');
  expect(queue.map(op => op.id)).toEqual(['other']); expect(http.put).not.toHaveBeenCalled();
});
it('保留本地按最新版本更新内容和状态，全部成功后再清理队列', async () => {
  vi.mocked(http.put).mockResolvedValueOnce({ ...remote, title: '本地名称', version: 4 }).mockResolvedValueOnce({ ...remote, status: 'DONE', version: 5 });
  await resolveTodoConflict(await readTodoConflict('conflict'), 'local');
  expect(http.put).toHaveBeenNthCalledWith(1, '/api/employee/personal-todos/123', expect.objectContaining({ title: '本地名称', expectedVersion: 3 }));
  expect(http.put).toHaveBeenNthCalledWith(2, '/api/employee/personal-todos/123/complete?expectedVersion=4');
  expect(queue).toEqual([]);
});
it('合并使用编辑结果，并阻止用旧对比覆盖新远端版本', async () => {
  const conflict = await readTodoConflict('conflict');
  vi.mocked(http.get).mockResolvedValueOnce({ ...remote, version: 4 });
  await expect(resolveTodoConflict(conflict, 'merge', { ...conflict.local, note: '合并备注' })).rejects.toThrow('版本已变化');
  expect(http.put).not.toHaveBeenCalled(); expect(discard).not.toHaveBeenCalled();
  vi.mocked(http.put).mockResolvedValue({ ...remote, version: 4 });
  await resolveTodoConflict(conflict, 'merge', { ...conflict.local, note: '合并备注', status: 'PENDING' });
  expect(http.put).toHaveBeenCalledWith('/api/employee/personal-todos/123', expect.objectContaining({ note: '合并备注', expectedVersion: 3 }));
});
it('保存中途失败保留整组本地修改，允许重新对比和重试', async () => {
  vi.mocked(http.put).mockResolvedValueOnce({ ...remote, version: 4 }).mockRejectedValueOnce(new TypeError('offline'));
  await expect(resolveTodoConflict(await readTodoConflict('conflict'), 'local')).rejects.toThrow('offline');
  expect(queue).toHaveLength(2); expect(discard).not.toHaveBeenCalled();
});
it('远端删除后可保留本地为新待办，重试使用同一创建标识', async () => {
  vi.mocked(http.get).mockRejectedValue(new ApiError('deleted', { status: 404 }));
  vi.mocked(http.post).mockResolvedValue({ ...remote, todoId: '456', version: 0 });
  vi.mocked(http.put).mockResolvedValue({ ...remote, todoId: '456', status: 'DONE', version: 1 });
  await resolveTodoConflict(await readTodoConflict('conflict'), 'local');
  expect(http.post).toHaveBeenCalledWith('/api/employee/personal-todos', expect.objectContaining({ clientRequestId: 'resolve-conflict' }));
  expect(http.put).toHaveBeenCalledWith('/api/employee/personal-todos/456/complete?expectedVersion=0');
  expect(queue).toEqual([]);
});
it('切换账号或本地队列变化后拒绝使用旧对比结果', async () => {
  const conflict = await readTodoConflict('conflict'); context = 'account-b';
  await expect(resolveTodoConflict(conflict, 'remote')).rejects.toThrow('登录状态已变化');
  expect(discard).not.toHaveBeenCalled(); context = 'account-a'; queue[1].snapshot = { ...remote, title: '后续编辑' };
  await expect(resolveTodoConflict(conflict, 'remote')).rejects.toThrow('版本已变化');
});
