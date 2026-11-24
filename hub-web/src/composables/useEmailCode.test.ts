import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { authApi } from '@/api';
import { useEmailCode } from './useEmailCode';
vi.mock('@/api', () => ({ authApi: { sendEmailCode: vi.fn() } }));
beforeEach(() => { vi.useFakeTimers(); vi.resetAllMocks(); });
afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); });
it('验证码明确指定用途，成功后倒计时，冷却期不重复发送', async () => {
  vi.mocked(authApi.sendEmailCode).mockResolvedValue(undefined);
  const code = useEmailCode(() => 'fixture@example.test', 'RESET_PASSWORD');
  await code.send(); await code.send();
  expect(authApi.sendEmailCode).toHaveBeenCalledOnce(); expect(authApi.sendEmailCode).toHaveBeenCalledWith('fixture@example.test', 'RESET_PASSWORD');
  expect(code.remaining.value).toBe(60); vi.advanceTimersByTime(60000); expect(code.remaining.value).toBe(0);
  await code.send(); expect(authApi.sendEmailCode).toHaveBeenCalledTimes(2);
});
it('发送失败不启动倒计时，允许重试；无效邮箱不发请求', async () => {
  vi.mocked(authApi.sendEmailCode).mockRejectedValueOnce(new Error('mail unavailable'));
  const code = useEmailCode(() => 'fixture@example.test', 'BIND');
  await expect(code.send()).rejects.toThrow('mail unavailable'); expect(code.remaining.value).toBe(0); expect(code.sending.value).toBe(false);
  await expect(useEmailCode(() => 'invalid', 'BIND').send()).rejects.toThrow('有效邮箱'); expect(authApi.sendEmailCode).toHaveBeenCalledOnce();
});
