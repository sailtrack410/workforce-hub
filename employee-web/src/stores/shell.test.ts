import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useShellStore, type ConversationMessage, type ConversationRecord } from './shell';

describe('useShellStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('filters message conversations by keyword', () => {
    const shell = useShellStore();

    shell.setSearch('messages', 'HR 助手');

    expect(shell.filteredConversations.map((item) => item.id)).toEqual(['hr-agent']);
  });

  it('filters message conversations by chip', () => {
    const shell = useShellStore();

    shell.setFilter('messages', '组织群');

    expect(shell.filteredConversations.map((item) => item.id)).toEqual(['operation-team']);
  });

  it('keeps approval progress data with one current step', () => {
    const shell = useShellStore();

    const steps = shell.activeConversation.workflowSteps;

    expect(steps).toHaveLength(4);
    expect(steps.filter((step) => step.status === 'current').map((step) => step.label)).toEqual(['直属上级审批']);
  });

  it('keeps group member count for compact conversation titles', () => {
    const shell = useShellStore();

    const group = shell.conversations.find((item) => item.id === 'operation-team');

    expect(group?.memberCount).toBe(12);
  });

  it('combines pending filter and keyword search for todos', () => {
    const shell = useShellStore();

    shell.setSearch('todos', '考勤');
    shell.setFilter('todos', '未处理');

    expect(shell.filteredTodos.map((item) => item.id)).toEqual(['todo-attendance']);
  });

  it('merges loaded backend conversations while preserving static fallback data', () => {
    const shell = useShellStore();
    const backendConversation: ConversationRecord = {
      ...shell.conversations[0],
      id: '7001',
      title: '张三与李四',
      subtitle: '下午同步一下排班。',
      status: 'ACTIVE',
      flow: '私聊',
    };

    shell.mergeBackendConversations([backendConversation]);

    expect(shell.conversations[0]).toMatchObject({
      id: '7001',
      title: '张三与李四',
      flow: '私聊',
    });
    expect(shell.conversations.some((item) => item.id === 'leave-approval')).toBe(true);
    expect(shell.active.messages).toBe('7001');
  });

  it('merges loaded backend messages into the active conversation', () => {
    const shell = useShellStore();
    const backendConversation: ConversationRecord = {
      ...shell.conversations[0],
      id: '7001',
      title: '张三与李四',
      messages: [],
    };
    const backendMessages: ConversationMessage[] = [
      {
        id: '7101',
        author: '用户 2003',
        role: 'USER · TEXT',
        body: '我上午可以处理补卡。',
        side: 'left',
      },
    ];

    shell.mergeBackendConversations([backendConversation]);
    shell.mergeBackendMessages('7001', backendMessages);

    expect(shell.activeConversation.messages).toEqual(backendMessages);
    expect(shell.activeConversation.subtitle).toBe('我上午可以处理补卡。');
  });

  it('returns empty filtered results without changing active item', () => {
    const shell = useShellStore();

    shell.setSearch('files', '不存在的文件');

    expect(shell.filteredFiles).toEqual([]);
    expect(shell.activeFile.id).toBe('personal');
  });
});
