import { afterEach, describe, expect, it, vi } from 'vitest';
import { createEmailCodeCooldown } from './emailCodeCooldown';

describe('createEmailCodeCooldown', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('keeps a 60 second resend countdown after code delivery succeeds', async () => {
    vi.useFakeTimers();
    const cooldown = createEmailCodeCooldown();

    cooldown.start();

    expect(cooldown.remainingSeconds.value).toBe(60);
    expect(cooldown.isActive.value).toBe(true);
    expect(cooldown.label.value).toBe('60 秒后重发');

    await vi.advanceTimersByTimeAsync(31_000);
    expect(cooldown.remainingSeconds.value).toBe(29);
    expect(cooldown.label.value).toBe('29 秒后重发');

    await vi.advanceTimersByTimeAsync(29_000);
    expect(cooldown.remainingSeconds.value).toBe(0);
    expect(cooldown.isActive.value).toBe(false);
    expect(cooldown.label.value).toBe('发送验证码');
  });

  it('can be reset before the countdown naturally finishes', async () => {
    vi.useFakeTimers();
    const cooldown = createEmailCodeCooldown();

    cooldown.start(10);
    await vi.advanceTimersByTimeAsync(3_000);
    cooldown.reset();

    expect(cooldown.remainingSeconds.value).toBe(0);
    expect(cooldown.isActive.value).toBe(false);
    expect(cooldown.label.value).toBe('发送验证码');
  });
});
