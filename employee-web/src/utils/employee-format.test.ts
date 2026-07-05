import { describe, expect, it } from 'vitest';
import {
  actionTypeLabel,
  bizTypeLabel,
  displayField,
  entityIdListFromText,
  formatDateTime,
  minutesToDayLabel,
  parseAgentPreReviewDecision,
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
    expect(actionTypeLabel('AGENT_PRE_REVIEW')).toBe('AI 预审');
  });

  it('parses agent pre-review decision suggestions', () => {
    expect(parseAgentPreReviewDecision('{"decision":"APPROVED","reason":"AI预审通过"}')).toMatchObject({
      decision: 'APPROVED',
      conclusion: '建议进入直属上级审批',
      suggestion: '建议直属上级核对请假时段、假期余额、工作交接和必要附件后审批',
    });
    expect(parseAgentPreReviewDecision('{"decision":"APPROVED","suggestion":"核对附件","checks":[{"name":"附件","result":"待核对"}]}')?.checks).toEqual([
      { name: '附件', result: '待核对', status: '' },
    ]);
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
