import { describe, expect, it } from 'vitest';
import { codeFormat, maxLength, mobileFormat, requiredText } from './validation';

describe('admin form validation', () => {
  it('validates required and maximum length fields', () => {
    expect(requiredText('  ', '姓名')).toBe('请输入姓名');
    expect(requiredText('张三', '姓名')).toBe('');
    expect(maxLength('12345', 4, '编码')).toBe('编码不能超过 4 个字符');
  });

  it('validates business codes', () => {
    expect(codeFormat('LEAVE_HELPER_2', '助手编码')).toBe('');
    expect(codeFormat('leave-helper', '助手编码')).toContain('仅支持大写字母');
  });

  it('allows optional mobile values and rejects invalid formats', () => {
    expect(mobileFormat('')).toBe('');
    expect(mobileFormat('+86 138-0013-8000')).toBe('');
    expect(mobileFormat('138abc')).toContain('有效手机号');
  });
});
