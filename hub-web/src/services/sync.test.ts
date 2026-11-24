import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, ApiError } from '@/api/http';
import { durableRequest, pendingOperations, syncPending, type SyncOperation } from './sync';
import { localStatus } from './localData';
vi.mock('@/api/http', () => ({ http: { post: vi.fn(), put: vi.fn(), delete: vi.fn() }, ApiError: class extends Error { status?: number; constructor(message: string, options: { status?: number }) { super(message); this.status = options.status; } } }));
let queue: SyncOperation[];
beforeEach(() => {
  queue = []; localStatus.suspended = false; vi.clearAllMocks();
  vi.stubGlobal('CustomEvent', class { type: string; detail: unknown; constructor(type: string, options: { detail: unknown }) { this.type = type; this.detail = options.detail; } });
  vi.stubGlobal('window', { dispatchEvent: vi.fn(), workforceDesktop: {
    cacheContext: async () => 'account-a', pendingOperations: async () => queue.map(op => ({ ...op })),
    enqueue: async (_context: string, op: SyncOperation) => { const index = queue.findIndex(o => o.id === op.id); if (index >= 0) queue[index] = { ...op }; else queue.push({ ...op, state: 'pending' }); },
    setOperationState: async (_context: string, id: string, state: string, error?: string) => { if (state === 'done') queue = queue.filter(op => op.id !== id); else Object.assign(queue.find(op => op.id === id)!, { state, error }); },
  } });
});
afterEach(() => vi.unstubAllGlobals());
describe('待同步队列', () => {
  it('先持久化再发送，断网保留同一幂等标识，重连只成功一次', async () => {
    vi.mocked(http.post).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValue({ todoId: '123', version: 0 });
    const op: SyncOperation = { id: 'op-1', method: 'POST', path: '/api/employee/personal-todos', body: { clientRequestId: 'op-1' }, entityKey: 'local-1' };
    expect(await durableRequest(op)).toEqual({ queued: true });
    expect(queue).toHaveLength(1);
    await syncPending(); await syncPending();
    expect(queue).toHaveLength(0); expect(http.post).toHaveBeenCalledTimes(2);
    expect(vi.mocked(http.post).mock.calls[1][1]).toEqual({ clientRequestId: 'op-1' });
  });
  it('创建后重映射本地 ID 和版本，后续修改按顺序执行', async () => {
    queue = [{ id: 'create', method: 'POST', path: '/api/employee/personal-todos', entityKey: 'local-1' }, { id: 'edit', method: 'PUT', path: '/api/employee/personal-todos/local-1', entityKey: 'local-1', body: { title: 'edit' } }];
    vi.mocked(http.post).mockResolvedValue({ todoId: '123', version: 0 });
    vi.mocked(http.put).mockResolvedValue({ todoId: '123', version: 1 });
    await syncPending();
    expect(http.put).toHaveBeenCalledWith('/api/employee/personal-todos/123', { title: 'edit', expectedVersion: 0 });
    expect(queue).toHaveLength(0);
  });
  it('冲突暂停该待办的后续修改，其他待办继续同步', async () => {
    queue = [{ id: 'a', method: 'PUT', path: '/api/employee/personal-todos/1', entityKey: '1', state: 'pending' }, { id: 'b', method: 'PUT', path: '/api/employee/personal-todos/1/complete', entityKey: '1', state: 'pending' }, { id: 'c', method: 'PUT', path: '/api/employee/personal-todos/2', entityKey: '2', state: 'pending' }];
    vi.mocked(http.put).mockRejectedValueOnce(new ApiError('changed', { status: 409 })).mockResolvedValue({ todoId: '2' });
    await syncPending();
    expect(http.put).toHaveBeenCalledTimes(2); expect(queue[0].state).toBe('conflict'); expect(queue).toHaveLength(2);
    expect(await pendingOperations()).toHaveLength(2);
  });
  it('同一待办连续修改不重新入队已完成操作', async () => {
    queue = [{ id: 'a', method: 'PUT', path: '/api/employee/personal-todos/1', entityKey: '1', body: { title: 'first' } }, { id: 'b', method: 'PUT', path: '/api/employee/personal-todos/1', entityKey: '1', body: { title: 'second' } }];
    vi.mocked(http.put).mockResolvedValueOnce({ todoId: '1', version: 1 }).mockResolvedValueOnce({ todoId: '1', version: 2 });
    await syncPending(); await syncPending();
    expect(queue).toHaveLength(0); expect(http.put).toHaveBeenCalledTimes(2);
    expect(http.put).toHaveBeenLastCalledWith('/api/employee/personal-todos/1', { title: 'second', expectedVersion: 1 });
  });
  it('退出或切换账号时暂停队列，不把前一账号操作发送给新会话', async () => {
    queue = [{ id: 'a', method: 'POST', path: '/api/employee/personal-todos' }, { id: 'b', method: 'POST', path: '/api/employee/personal-todos' }];
    vi.mocked(http.post).mockImplementationOnce(async () => { localStatus.suspended = true; return { todoId: '1' }; });
    await syncPending();
    expect(http.post).toHaveBeenCalledTimes(1); expect(queue).toHaveLength(1); expect(queue[0].id).toBe('b');
    await syncPending(); expect(http.post).toHaveBeenCalledTimes(1);
  });
});
