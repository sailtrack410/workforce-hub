import { afterEach, describe, expect, it, vi } from 'vitest';
import { cachedResponse, cacheable, invalidateResponse, localStatus, storeResponse } from './localData';

afterEach(() => vi.unstubAllGlobals());
describe('本地业务缓存', () => {
  it('认证与下载票据不进入业务缓存', () => {
    expect(cacheable('/api/employee/auth/me')).toBe(false);
    expect(cacheable('/api/employee/files/download/token')).toBe(false);
    expect(cacheable('/api/employee/personal-todos')).toBe(true);
    expect(cacheable('/api/employee/team-tasks/123')).toBe(true);
  });
  it('读取缓存带更新时间，并用请求开始时的账号上下文写入和失效', async () => {
    const cachePut = vi.fn().mockResolvedValue(undefined), cacheInvalidate = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('window', { workforceDesktop: { cacheGet: async (context: string) => context === 'account-a' ? { value: [{ title: 'cached' }], updatedAt: 123 } : null, cachePut, cacheInvalidate } });
    expect(await cachedResponse('account-b', '/api/employee/personal-todos')).toBeUndefined();
    expect(await cachedResponse('account-a', '/api/employee/personal-todos')).toEqual([{ title: 'cached' }]);
    expect(localStatus.offline).toBe(true); expect(localStatus.cachedAt).toBe(123);
    await storeResponse('account-a', '/api/employee/personal-todos', []);
    expect(cachePut).toHaveBeenCalledWith('account-a', '/api/employee/personal-todos', []);
    await invalidateResponse('account-a', '/api/employee/team-tasks/123/comments');
    expect(cacheInvalidate).toHaveBeenCalledWith('account-a', '/api/employee/team-tasks');
  });
});
