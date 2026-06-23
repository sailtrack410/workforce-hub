import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { ref } from 'vue';
import { canRecallMessage, useMessagesState } from './useMessagesState';
import { useImStore } from '@/stores/im';
import { useShellStore, type ConversationMessage, type ConversationRecord } from '@/stores/shell';
import { useSessionStore } from '@/stores/session';

const agentConversation: ConversationRecord = {
  id: '7001',
  kind: 'agent',
  tone: 'green',
  badge: 'A',
  title: '通用响应卡片助手',
  subtitle: '',
  meta: '',
  tag: 'Agent',
  unread: 0,
  flow: 'Agent 会话',
  status: 'ACTIVE',
  pinned: false,
  muted: false,
  unreadLabel: '已读',
  summaryTitle: '通用响应卡片助手',
  summaryBody: '',
  cards: [],
  workflowSteps: [],
  messages: [],
  panel: {
    members: [],
    tasks: [],
    attachments: [],
  },
};

describe('useMessagesState', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('shows agent loading card before send API finishes', async () => {
    const shell = useShellStore();
    const im = useImStore();
    const session = useSessionStore();
    let resolveSend: ((value: unknown) => void) | undefined;
    im.conversations = [agentConversation];
    shell.selectItem('messages', '7001');
    session.user = {
      userId: 22002,
      userType: 'USER',
      loginName: 'demo.employee1',
      displayName: '演示员工一',
    };
    im.sendMessage = vi.fn(() => new Promise((resolve) => {
      resolveSend = resolve;
    })) as typeof im.sendMessage;
    im.pollAgentReply = vi.fn(() => Promise.resolve(false)) as typeof im.pollAgentReply;

    const state = useMessagesState({
      shell,
      im,
      session,
      wsConnected: ref(false),
      showToast: vi.fn(),
      navigateMessagesConversation: vi.fn(),
      syncConversationMessages: vi.fn(),
      openFilePreviewFromUrl: vi.fn(),
    });
    state.draft.value = '帮我查询公司的请假制度';
    const sending = state.sendMessage();

    expect(shell.activeConversation.messages).toHaveLength(1);
    expect(shell.activeConversation.messages[0]).toMatchObject({
      id: expect.stringMatching(/^agent-loading-/),
      type: 'knowledge-card',
      body: '正在查询',
      knowledgeCard: {
        state: 'INGESTING',
        status: 'LOADING',
      },
    });

    resolveSend?.({
      id: '7103',
      body: '帮我查询公司的请假制度',
      seqNo: 3,
    });
    await sending;
  });

  it('allows recall only within two minutes for current user messages', () => {
    const message: ConversationMessage = {
      id: 'm-1',
      author: '我',
      role: '成员',
      body: '测试消息',
      side: 'right',
      time: '2026-06-23 13:40',
    };

    expect(canRecallMessage(message, new Date('2026-06-23T13:41:59'))).toBe(true);
    expect(canRecallMessage(message, new Date('2026-06-23T13:42:01'))).toBe(false);
    expect(canRecallMessage({ ...message, side: 'left' }, new Date('2026-06-23T13:41:00'))).toBe(false);
  });
});
