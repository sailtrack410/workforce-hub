import { afterEach, expect, it, vi } from 'vitest';
import { http } from './http';
afterEach(() => vi.unstubAllGlobals());
it('断网和服务不可用时可读缓存，401/403 不使用缓存掩盖权限失效', async () => {
  const cacheGet = vi.fn(async () => ({ value: [{ title: 'cached' }], updatedAt: 123 }));
  const cacheInvalidate = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal('window', { location: { protocol: 'http:' }, workforceDesktop: { cacheContext: async () => 'context', cacheGet, cacheInvalidate } });
  vi.stubGlobal('document', { cookie: '' });
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
  expect(await http.get('/api/employee/team-tasks')).toEqual([{ title: 'cached' }]);
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 503 })));
  expect(await http.get('/api/employee/team-tasks')).toEqual([{ title: 'cached' }]);
  const reads = cacheGet.mock.calls.length;
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"message":"forbidden"}', { status: 403 })));
  await expect(http.get('/api/employee/team-tasks')).rejects.toThrow('forbidden');
  expect(cacheGet.mock.calls.length).toBe(reads);
  expect(cacheInvalidate).toHaveBeenCalledWith('context', '/api/employee/team-tasks');
});
