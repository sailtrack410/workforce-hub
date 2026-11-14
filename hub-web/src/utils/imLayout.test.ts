import { describe, expect, it } from 'vitest';
import { conversationTime, conversationWidth } from './imLayout';

describe('conversationWidth', () => {
  it('限制在 240–360px，并按 4px 步进', () => {
    expect(conversationWidth(280, -100)).toBe(240);
    expect(conversationWidth(280, 100)).toBe(360);
    expect(conversationWidth(280, 11)).toBe(292);
  });

  it('空间不足时优先保证最小可读宽度', () => {
    expect(conversationWidth(360, 0, 200)).toBe(240);
  });
});

describe('conversationTime', () => {
  const now = new Date('2026-11-14T12:00:00');
  it('区分今天、昨天和更早消息', () => {
    expect(conversationTime('2026-11-14T09:24:00', now)).toBe('09:24');
    expect(conversationTime('2026-11-13T09:02:00', now)).toBe('昨天');
    expect(conversationTime('2026-11-10T09:02:00', now)).toBe('11/10');
  });

  it('空值与异常值不阻断列表', () => {
    expect(conversationTime('', now)).toBe('');
    expect(conversationTime('invalid-time', now)).toBe('id-ti');
  });
});
