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
    members: [
      { name: '同部门王', originalName: '同部门王', role: '成员', userId: 3001 },
    ],
    tasks: [],
    attachments: [],
  },
};

const teamConversation: ConversationRecord = {
  id: '8001',
  kind: 'team',
  tone: 'blue',
  badge: '群',
  title: '项目群',
  subtitle: '',
  meta: '',
  tag: '群聊',
  unread: 0,
  flow: '群聊',
  status: 'ACTIVE',
  pinned: false,
  muted: false,
  unreadLabel: '已读',
  summaryTitle: '项目群',
  summaryBody: '',
  cards: [],
  workflowSteps: [],
  messages: [
    {
      id: 'm-100',
      author: '张三',
      role: '成员',
      body: '原始消息',
      side: 'left',
      time: '2026-07-06 10:00',
    },
  ],
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

  it('resolves quoted preview and clears quote on cancel', () => {
    const shell = useShellStore();
    const im = useImStore();
    const session = useSessionStore();
    im.conversations = [teamConversation];
    shell.selectItem('messages', '8001');
    session.user = {
      userId: 22002,
      userType: 'USER',
      loginName: 'demo.employee1',
      displayName: '演示员工一',
    };

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

    state.runMessageMenuAction = state.runMessageMenuAction.bind(state);
    state.messageActionTarget.value = shell.activeConversation.messages[0];

    void state.runMessageMenuAction('quote');

    expect(state.quotedMessagePreview.value).toMatchObject({
      id: 'm-100',
      author: '张三',
      body: '原始消息',
      visible: true,
    });

    state.cancelQuote();
    expect(state.quotedMessagePreview.value).toBeNull();
  });

  it('sends quoted message id and clears quote after send', async () => {
    const shell = useShellStore();
    const im = useImStore();
    const session = useSessionStore();
    im.conversations = [teamConversation];
    shell.selectItem('messages', '8001');
    session.user = {
      userId: 22002,
      userType: 'USER',
      loginName: 'demo.employee1',
      displayName: '演示员工一',
    };
    im.sendMessage = vi.fn(async () => ({
      id: 'm-101',
      author: '我',
      role: '成员',
      body: '回复消息',
      side: 'right',
      quotedMessageId: 'm-100',
    })) as typeof im.sendMessage;

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

    state.messageActionTarget.value = shell.activeConversation.messages[0];
    await state.runMessageMenuAction('quote');
    state.draft.value = '回复消息';
    await state.sendMessage();

    expect(im.sendMessage).toHaveBeenCalledWith(
      '8001',
      '回复消息',
      22002,
      expect.anything(),
      'm-100',
      [],
    );
    expect(state.quotedMessagePreview.value).toBeNull();
    expect(state.draft.value).toBe('');
  });

  it('uses original member name for mentions and keeps mention metadata after send', async () => {
    const shell = useShellStore();
    const im = useImStore();
    const session = useSessionStore();
    im.conversations = [teamConversation];
    shell.selectItem('messages', '8001');
    shell.contacts = [
      {
        id: 'contact-3001',
        name: '前端联系人',
        originalName: '同部门王',
        remarkName: '前端联系人',
        role: '成员',
        department: '研发部',
        relation: '好友',
        source: '我的好友',
        userId: 3001,
      },
    ];
    session.user = {
      userId: 22002,
      userType: 'USER',
      loginName: 'demo.employee1',
      displayName: '演示员工一',
    };
    im.sendMessage = vi.fn(async () => {
      const sent: ConversationMessage = {
        id: 'm-102',
        author: '我',
        role: '成员',
        body: '@同部门王 收到',
        side: 'right',
      };
      im.messagesByConversation['8001'] = [...teamConversation.messages, sent];
      return sent;
    }) as typeof im.sendMessage;

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

    state.insertMemberMention(state.mentionableMembers.value[0]);
    expect(state.draft.value).toBe('@同部门王 ');
    expect(state.draftMentions.value.map((item) => item.userId)).toEqual([3001]);

    state.draft.value += '收到';
    await state.sendMessage();

    expect(im.sendMessage).toHaveBeenCalledWith(
      '8001',
      '@同部门王 收到',
      22002,
      expect.anything(),
      undefined,
      [3001],
    );

    expect(shell.activeConversation.messages[shell.activeConversation.messages.length - 1]?.mentions).toEqual([
      expect.objectContaining({
        userId: 3001,
        name: '同部门王',
        originalName: '同部门王',
      }),
    ]);
  });

  it('does not match mention name prefixes', () => {
    const shell = useShellStore();
    const im = useImStore();
    const session = useSessionStore();
    im.conversations = [{
      ...teamConversation,
      panel: {
        ...teamConversation.panel,
        members: [
          { name: '王', originalName: '王', role: '成员', userId: 3001 },
          { name: '王五', originalName: '王五', role: '成员', userId: 3002 },
        ],
      },
    }];
    shell.selectItem('messages', '8001');

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

    expect(state.mentionsFromText('@王五 收到').map((item) => item.userId)).toEqual([3002]);
  });
});
