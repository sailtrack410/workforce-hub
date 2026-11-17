import { describe, expect, it } from 'vitest';
import { loginEmailCodeSubtitle } from './auth-login-ui';

describe('auth login UI state', () => {
  it('does not claim email code delivery before the send request succeeds', () => {
    expect(loginEmailCodeSubtitle({
      requiresEmailCode: true,
      maskedEmail: 'w****s@1**.com',
      sending: true,
      cooldownActive: false,
    })).toBe('发送中');

    expect(loginEmailCodeSubtitle({
      requiresEmailCode: true,
      maskedEmail: 'w****s@1**.com',
      sending: false,
      cooldownActive: false,
    })).toBe('请输入邮箱验证码');
  });

  it('shows the masked target only after cooldown starts from a successful send', () => {
    expect(loginEmailCodeSubtitle({
      requiresEmailCode: true,
      maskedEmail: 'w****s@1**.com',
      sending: false,
      cooldownActive: true,
    })).toBe('验证码已发送至 w****s@1**.com');
  });
});
