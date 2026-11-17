import { describe, expect, it } from 'vitest';
import { attendanceResultLabel } from './format';

describe('attendanceResultLabel', () => {
  it('renders scheduled rest days as rest instead of an unknown attendance result', () => {
    expect(attendanceResultLabel('REST')).toBe('休息');
    expect(attendanceResultLabel('NORMAL')).toBe('正常');
    expect(attendanceResultLabel('ABNORMAL')).toBe('异常');
    expect(attendanceResultLabel('NO_RULE')).toBe('未排班');
  });
});
