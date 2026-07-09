import { describe, expect, it } from 'vitest';
import { trimFormValue } from './form-values';

describe('form value helpers', () => {
  it('normalizes text and numeric input values before submit validation', () => {
    expect(trimFormValue('  出差  ')).toBe('出差');
    expect(trimFormValue(1200.5)).toBe('1200.5');
    expect(trimFormValue(0)).toBe('0');
    expect(trimFormValue(null)).toBe('');
    expect(trimFormValue(undefined)).toBe('');
  });
});
