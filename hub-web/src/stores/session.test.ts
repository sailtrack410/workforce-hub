import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { authApi } from '@/api';
import { useSessionStore } from './session';
vi.mock('@/api', () => ({ authApi: { login: vi.fn(), me: vi.fn() }, orgApi: {} }));
vi.mock('@/stores/im', () => ({ useImStore: () => ({ $reset: vi.fn() }) }));
beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks(); vi.stubGlobal('window', { workforceDesktop: { bindCacheAccount: vi.fn(async () => null) } }); });
afterEach(() => vi.unstubAllGlobals());
it('需要邮箱验证或首次绑定时不标记为已登录、不绑定缓存账号', async () => {
  const session = useSessionStore();
  for (const flag of ['requiresEmailCode', 'requiresEmailBinding']) {
    vi.mocked(authApi.login).mockResolvedValue({ userId: '1', loginName: 'fixture', displayName: 'fixture', [flag]: true });
    const pending = await session.login('fixture', 'Fixture@2026');
    expect(pending[flag as 'requiresEmailCode']).toBe(true); expect(session.isAuthenticated).toBe(false);
    expect(window.workforceDesktop!.bindCacheAccount).not.toHaveBeenCalled();
  }
});
it('邮箱验证通过才建立登录状态，并向后端原样提交验证码和绑定邮箱', async () => {
  const session = useSessionStore();
  vi.mocked(authApi.login).mockResolvedValue({ userId: '1', loginName: 'fixture', displayName: 'fixture' });
  vi.mocked(authApi.me).mockResolvedValue({ userId: '1', loginName: 'fixture', displayName: 'fixture', email: 'fixture@example.test' });
  await session.login('fixture', 'Fixture@2026', { emailCode: 'fixture-code', bindEmail: 'fixture@example.test' });
  expect(authApi.login).toHaveBeenCalledWith('fixture', 'Fixture@2026', { emailCode: 'fixture-code', bindEmail: 'fixture@example.test' });
  expect(session.isAuthenticated).toBe(true); expect(window.workforceDesktop!.bindCacheAccount).toHaveBeenCalledOnce();
  expect(session.user?.email).toBe('fixture@example.test');
});
