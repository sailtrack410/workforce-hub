import { describe, expect, it, vi } from 'vitest';
import { useMessagesWorkflowEntry } from './useMessagesWorkflowEntry';

describe('useMessagesWorkflowEntry', () => {
  it('opens a conversation workflow entry and routes actions to existing workflow surfaces', () => {
    const runAction = vi.fn();
    const entry = useMessagesWorkflowEntry({ runAction });

    expect(entry.workflowEntryOpen.value).toBe(false);

    entry.openWorkflowEntry();

    expect(entry.workflowEntryOpen.value).toBe(true);
    expect(entry.workflowEntryActions.map((item) => item.label)).toEqual([
      '查看待办',
      '发起请假',
      '我的申请',
      '考勤处理',
    ]);

    entry.triggerWorkflowEntry('leave');

    expect(entry.workflowEntryOpen.value).toBe(false);
    expect(runAction).toHaveBeenLastCalledWith({
      label: '发起请假',
      action: 'open-workbench',
      app: 'leave',
    });

    entry.openWorkflowEntry();
    entry.triggerWorkflowEntry('todos');

    expect(runAction).toHaveBeenLastCalledWith({
      label: '查看待办',
      action: 'switch-section',
      section: 'todos',
    });
  });
});
