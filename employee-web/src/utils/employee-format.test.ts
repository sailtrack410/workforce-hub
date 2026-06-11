import { describe, expect, it } from 'vitest';
import {
  actionTypeLabel,
  bizTypeLabel,
  displayField,
  entityIdListFromText,
  formatDateTime,
  minutesToDayLabel,
  percentage,
  punchTypeLabel,
  statusText,
  toApiDateTime,
} from './employee-format';

describe('employee format helpers', () => {
  it('formats empty and known business values consistently', () => {
    expect(displayField('')).toBe('-');
    expect(displayField('  ')).toBe('-');
    expect(displayField(0)).toBe('0');
    expect(statusText('IN_APPROVAL')).toBe('审批中');
    expect(statusText('UNKNOWN')).toBe('UNKNOWN');
    expect(bizTypeLabel('LEAVE')).toBe('请假');
    expect(punchTypeLabel('OFF_DUTY')).toBe('下班');
    expect(actionTypeLabel('AGENT_PRE_REVIEW')).toBe('Agent预审');
  });

  it('keeps date, percent, duration, and id parsing behavior stable', () => {
    expect(formatDateTime('2026-05-15T09:30:45')).toBe('2026-05-15 09:30');
    expect(formatDateTime(null)).toBe('-');
    expect(toApiDateTime('2026-05-15T09:30')).toBe('2026-05-15 09:30:00');
    expect(minutesToDayLabel(720)).toBe('1.5 天');
    expect(percentage(2, 5)).toBe(40);
    expect(percentage(8, 5)).toBe(100);
    expect(entityIdListFromText('12, 34，56  78')).toEqual(['12', '34', '56', '78']);
  });
});
