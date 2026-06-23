import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { ImConversationSummaryResponse, ImMessageResponse, SendImMessageRequest } from '../api';
import { mapImConversationToRecord, mapImMessageToConversationMessage, useImStore, type EmployeeImApi } from './im';

const message: ImMessageResponse = {
  messageId: 7102,
  conversationId: 7001,
  seqNo: 2,
  senderType: 'USER',
  senderId: 2002,
  messageType: 'TEXT',
  contentJson: '{"text":"下午同步一下排班。"}',
  contentText: '下午同步一下排班。',
  quotedMessageId: null,
  status: 'NORMAL',
  sentAt: '2026-05-09T10:10:00',
};

const conversation: ImConversationSummaryResponse = {
  conversationId: 7001,
  conversationType: 'PRIVATE',
  conversationKey: 'PRIVATE:2002:2003',
  title: '张三与李四',
  avatarFileId: null,
  boundAgentId: null,
  lastMessageSeq: 2,
  lastReadSeq: 1,
  unreadCount: 1,
  lastMessage: message,
  lastMessageAt: '2026-05-09T10:10:00',
  status: 'ACTIVE',
  pin: false,
  mute: false,
  memberRole: 'MEMBER',
};

function createImApi(): EmployeeImApi {
  return {
    conversations: () => Promise.resolve([conversation]),
    conversationDetail: () => Promise.resolve({
      conversation,
      members: [
        {
          memberId: 2002,
          memberType: 'USER',
          displayName: '张三',
          memberRole: 'MEMBER',
          status: 'ACTIVE',
          joinedAt: '2026-05-09T09:00:00',
        },
      ],
      groupProfile: null,
    }),
    groupMembers: () => Promise.resolve([]),
    groupProfile: () => Promise.resolve({
      conversationId: 7001,
      title: '项目推进群',
      groupType: 'CUSTOM',
      ownerUserId: 2002,
      status: 'ACTIVE',
    }),
    updateGroupProfile: (_conversationId, request) => Promise.resolve({
      conversationId: 7001,
      title: request.title ?? '项目推进群',
      groupType: 'CUSTOM',
      ownerUserId: 2002,
      noticeText: request.noticeText,
      status: 'ACTIVE',
    }),
    addGroupMembers: () => Promise.resolve([]),
    removeGroupMembers: () => Promise.resolve([]),
    leaveGroup: () => Promise.resolve([]),
    dismissGroup: () => Promise.resolve(),
    messages: () => Promise.resolve([message]),
    startPrivateConversation: () => Promise.resolve(conversation),
    createGroupConversation: () => Promise.resolve({
      ...conversation,
      conversationId: 7301,
      conversationType: 'GROUP',
      title: '项目推进群',
    }),
    startAgentConversation: () => Promise.resolve({
      ...conversation,
      conversationId: 7401,
      conversationType: 'AGENT',
      title: 'HR 助手',
    }),
    sendMessage: () => Promise.resolve({
      ...message,
      messageId: 7103,
      seqNo: 3,
      senderId: 2003,
      contentJson: '{"text":"我下午两点同步。"}',
      contentText: '我下午两点同步。',
      sentAt: '2026-05-09T10:12:00',
    }),
    editMessage: () => Promise.resolve(message),
    recallMessage: () => Promise.resolve({ ...message, status: 'RECALLED' }),
    translateMessage: () => Promise.resolve({ messageId: 7101, sourceText: '原文', translatedText: '译文' }),
    forwardMessage: () => Promise.resolve(message),
    favoriteMessage: () => Promise.resolve({ messageId: 7101, actionType: 'FAVORITE', status: 'ACTIVE' }),
    remindMessage: () => Promise.resolve({ messageId: 7101, actionType: 'REMINDER', status: 'ACTIVE' }),
    deleteMessage: () => Promise.resolve({ messageId: 7101, actionType: 'DELETE', status: 'ACTIVE' }),
    createMessageAttachmentDownloadTicket: () => Promise.resolve({
      downloadTicketId: 1,
      ticketNo: 'DL-1',
      downloadUrl: '/download/DL-1',
      fileName: '附件.pdf',
      fileSize: 1024,
    }),
    updateReadPosition: (_conversationId, request) => Promise.resolve({
      ...conversation,
      lastReadSeq: request.lastReadSeq,
      unreadCount: Math.max(conversation.lastMessageSeq - request.lastReadSeq, 0),
    }),
    updatePreference: () => Promise.resolve(conversation),
    downloadAttachmentUrl: (ticketNo) => `/api/employee/im/attachments/download/${ticketNo}`,
  };
}

describe('IM store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('maps backend conversation summary into shell conversation record', () => {
    const record = mapImConversationToRecord(conversation);

    expect(record).toMatchObject({
      id: '7001',
      kind: 'private',
      title: '张三与李四',
      subtitle: '下午同步一下排班。',
      unread: 1,
      flow: '私聊',
      status: 'ACTIVE',
      tag: '私聊 · 正常',
    });
    expect(record.cards).toEqual([
      { label: '会话类型', value: '私聊', hint: '会话类型' },
      { label: '未读数', value: '1', hint: '未读消息' },
      { label: '会话状态', value: '正常', hint: '状态' },
    ]);
  });

  it('maps backend message into shell message record', () => {
    const record = mapImMessageToConversationMessage(message, 2003);

    expect(record).toMatchObject({
      id: '7102',
      senderId: 2002,
      author: '用户 2002',
      role: '成员',
      body: '下午同步一下排班。',
      side: 'left',
      time: '2026-05-09 10:10',
    });
  });

  it('maps knowledge answer CARD content into a knowledge card message', () => {
    const record = mapImMessageToConversationMessage({
      ...message,
      messageId: 7104,
      senderType: 'AGENT',
      senderId: 9001,
      messageType: 'CARD',
      contentText: '',
      contentJson: JSON.stringify({
        cardType: 'knowledge_answer',
        state: 'ANSWERED',
        title: '年假与考勤影响',
        answer: '审批通过后会扣减年假余额，并同步生成请假考勤结果。',
        confidence: 'high',
        generatedAt: '15:24',
        relatedBusiness: [
          { label: '当前余额', value: '6.5 天', tone: 'green' },
        ],
        relatedFlows: [
          {
            id: 'WF-20260514-001',
            title: '请假申请 WF-20260514-001',
            status: '审批中',
            statusTone: 'primary',
          },
        ],
        citations: [
          {
            id: 'policy-annual-leave',
            title: '员工年假制度',
            sourceType: 'POLICY',
            score: 0.87,
          },
        ],
        suggestedActions: [
          { label: '查看引用', action: 'open_citation' },
        ],
      }),
    }, 2003);

    expect(record.type).toBe('knowledge-card');
    expect(record.knowledgeCard).toMatchObject({
      state: 'ANSWERED',
      title: '年假与考勤影响',
      answer: '审批通过后会扣减年假余额，并同步生成请假考勤结果。',
      confidence: 'high',
      citations: [{ title: '员工年假制度', sourceType: 'POLICY' }],
      relatedFlows: [{ id: 'WF-20260514-001', title: '请假申请 WF-20260514-001' }],
      suggestedActions: [{ label: '查看引用', action: 'open_citation' }],
    });
    expect(record.role).toBe('知识库 AI');
  });

  it('maps current user messages to the right side', () => {
    const record = mapImMessageToConversationMessage({
      ...message,
      messageId: 7101,
      senderId: '2003',
      contentText: '我上午可以处理补卡。',
    }, 2003);

    expect(record).toMatchObject({
      id: '7101',
      author: '我',
      body: '我上午可以处理补卡。',
      side: 'right',
    });
  });

  it('loads conversations and messages from employee IM API', async () => {
    const im = useImStore();

    await im.loadConversations({}, createImApi());
    await im.loadMessages(7001, 2003, {}, createImApi());

    expect(im.loading).toBe(false);
    expect(im.messageLoading).toBe(false);
    expect(im.conversations.map((item) => item.id)).toEqual(['7001']);
    expect(im.messagesByConversation['7001'].map((item) => item.id)).toEqual(['7102']);
    expect(im.lastMessageSeqByConversation['7001']).toBe(2);
    expect(im.lastReadSeqByConversation['7001']).toBe(1);
  });

  it('loads conversation avatar download url from avatar file id', async () => {
    const im = useImStore();
    const filesApi = {
      createDownloadTicket: vi.fn().mockResolvedValue({
        downloadTicketId: 2,
        ticketNo: 'AVATAR-1',
        downloadUrl: '/files/avatar-1.png',
        fileName: 'avatar.png',
        fileSize: 128,
      }),
      downloadUrl: vi.fn((ticketNo: string) => `/api/employee/files/download/${ticketNo}`),
    };

    await im.loadConversations({}, {
      ...createImApi(),
      conversations: () => Promise.resolve([{ ...conversation, avatarFileId: 9901 }]),
    }, filesApi);

    expect(filesApi.createDownloadTicket).toHaveBeenCalledWith(9901, { downloadType: 'INLINE' });
    expect(im.conversations[0].avatarUrl).toBe('/files/avatar-1.png');
  });

  it('uses separate default avatars when conversation has no avatar file', () => {
    const privateRecord = mapImConversationToRecord({ ...conversation, avatarFileId: null });
    const groupRecord = mapImConversationToRecord({ ...conversation, conversationType: 'GROUP', avatarFileId: null });
    const agentRecord = mapImConversationToRecord({ ...conversation, conversationType: 'AGENT', avatarFileId: null });

    expect(privateRecord.avatarUrl).toBe('/default-user-avatar.svg');
    expect(groupRecord.avatarUrl).toBe('/default-group-avatar.svg');
    expect(agentRecord.avatarUrl).toBe('/default-agent-avatar.svg');
  });

  it('loads conversation members into shell panel', async () => {
    const im = useImStore();

    await im.loadConversations({}, createImApi());
    await im.loadConversationDetail(7001, createImApi());

    expect(im.conversations[0].panel.members).toEqual([{
      name: '张三',
      originalName: '张三',
      role: '成员',
      userId: 2002,
      memberType: 'USER',
    }]);
    expect(im.conversations[0].title).toBe('张三');
    expect(im.conversations[0].memberCount).toBeUndefined();
  });

  it('uses the other private chat member as conversation title when both members are returned', async () => {
    const im = useImStore();
    const imApi: EmployeeImApi = {
      ...createImApi(),
      conversationDetail: () => Promise.resolve({
        conversation,
        members: [
          {
            memberId: 2003,
            memberType: 'USER',
            displayName: '我',
            memberRole: 'MEMBER',
            status: 'ACTIVE',
            joinedAt: '2026-05-09T09:00:00',
          },
          {
            memberId: 2002,
            memberType: 'USER',
            displayName: '张三',
            memberRole: 'MEMBER',
            status: 'ACTIVE',
            joinedAt: '2026-05-09T09:00:00',
          },
        ],
        groupProfile: null,
      }),
    };

    await im.loadConversations({}, imApi);
    await im.loadConversationDetail(7001, imApi, 2003);

    expect(im.conversations[0].title).toBe('张三');
    expect(im.conversations[0].summaryTitle).toBe('张三');
    expect(im.conversations[0].badge).toBe('张三');
  });

  it('only writes member count back to group conversations', async () => {
    const im = useImStore();
    const imApi: EmployeeImApi = {
      ...createImApi(),
      conversations: () => Promise.resolve([{ ...conversation, conversationType: 'GROUP', title: '项目推进群' }]),
      conversationDetail: () => Promise.resolve({
        conversation: { ...conversation, conversationType: 'GROUP', title: '项目推进群' },
        members: [
          {
            memberId: 2002,
            memberType: 'USER',
            displayName: '张三',
            memberRole: 'OWNER',
            status: 'ACTIVE',
            joinedAt: '2026-05-09T09:00:00',
          },
          {
            memberId: 2003,
            memberType: 'USER',
            displayName: '李四',
            memberRole: 'MEMBER',
            status: 'ACTIVE',
            joinedAt: '2026-05-09T09:00:00',
          },
        ],
        groupProfile: null,
      }),
    };

    await im.loadConversations({}, imApi);
    await im.loadConversationDetail(7001, imApi);

    expect(im.conversations[0]).toMatchObject({
      kind: 'team',
      memberCount: 2,
    });
  });

  it('loads visible conversation details so contacts can use real members', async () => {
    const im = useImStore();
    let detailCalls = 0;
    const imApi: EmployeeImApi = {
      ...createImApi(),
      conversationDetail: (conversationId) => {
        detailCalls += 1;
        return Promise.resolve({
          conversation: { ...conversation, conversationId },
          members: [
            {
              memberId: '2053928981082898411',
              memberType: 'USER',
              displayName: '张三',
              memberRole: 'MEMBER',
              status: 'ACTIVE',
              joinedAt: '2026-05-09T09:00:00',
            },
          ],
          groupProfile: null,
        });
      },
    };

    await im.loadConversations({}, imApi);
    await im.loadVisibleConversationDetails(undefined, imApi);

    expect(detailCalls).toBe(1);
    expect(im.conversations[0].panel.members[0]).toMatchObject({
      name: '张三',
      userId: '2053928981082898411',
    });
  });

  it('sends text message and appends mapped backend response', async () => {
    const im = useImStore();
    const requests: SendImMessageRequest[] = [];
    const imApi: EmployeeImApi = {
      ...createImApi(),
      sendMessage: (_conversationId, request) => {
        requests.push(request);
        return Promise.resolve({
          ...message,
          messageId: 7103,
          seqNo: 3,
          senderId: 2003,
          contentJson: '{"text":"我下午两点同步。"}',
          contentText: '我下午两点同步。',
          sentAt: '2026-05-09T10:12:00',
        });
      },
    };

    im.messagesByConversation['7001'] = [mapImMessageToConversationMessage(message, 2003)];
    const sent = await im.sendMessage(7001, '我下午两点同步。', 2003, imApi);

    expect(requests).toHaveLength(1);
    expect(requests[0]).toMatchObject({ messageType: 'TEXT', text: '我下午两点同步。' });
    expect(requests[0].clientMessageId).toMatch(/^web-7001-/);
    expect(sent).toMatchObject({
      id: '7103',
      author: '我',
      body: '我下午两点同步。',
      side: 'right',
    });
    expect(im.messagesByConversation['7001'].map((item) => item.id)).toEqual(['7102', '7103']);
    expect(im.messageSending).toBe(false);
  });

  it('polls agent conversation replies and appends backend agent response', async () => {
    const im = useImStore();
    const agentReply: ImMessageResponse = {
      ...message,
      messageId: 7104,
      seqNo: 4,
      senderType: 'AGENT',
      senderId: 9001,
      contentJson: '{"text":"Agent真实模型已接通。"}',
      contentText: 'Agent真实模型已接通。',
      sentAt: '2026-05-09T10:13:00',
    };
    const imApi: EmployeeImApi = {
      ...createImApi(),
      messages: () => Promise.resolve([message, agentReply]),
    };

    im.messagesByConversation['7001'] = [mapImMessageToConversationMessage(message, 2003)];

    const found = await im.pollAgentReply(7001, 2, 2003, {
      imApi,
      attempts: 1,
      intervalMs: 0,
    });

    expect(found).toBe(true);
    expect(im.messagesByConversation['7001'].map((item) => item.id)).toEqual(['7102', '7104']);
    expect(im.messagesByConversation['7001'][1]).toMatchObject({
      author: 'AI 助手',
      role: 'AI 助手',
      body: 'Agent真实模型已接通。',
      side: 'left',
    });
    expect(im.lastMessageSeqByConversation['7001']).toBe(4);
  });

  it('updates read position and syncs unread count back to conversation list', async () => {
    const im = useImStore();
    await im.loadConversations({}, createImApi());

    const updated = await im.updateReadPosition(7001, 2, createImApi());

    expect(updated.lastReadSeq).toBe(2);
    expect(im.lastReadSeqByConversation['7001']).toBe(2);
    expect(im.conversations[0].unread).toBe(0);
    expect(im.conversations[0].unreadLabel).toBe('已读');
  });

  it('updates conversation pin preference in the conversation list', async () => {
    const im = useImStore();
    const imApi: EmployeeImApi = {
      ...createImApi(),
      updatePreference: (_conversationId, request) => Promise.resolve({
        ...conversation,
        pin: Boolean(request.pin),
        mute: Boolean(request.mute),
      }),
    };

    await im.loadConversations({}, imApi);
    const updated = await im.updatePreference(7001, { pin: true, mute: false }, imApi);

    expect(updated.pinned).toBe(true);
    expect(im.conversations[0].pinned).toBe(true);
  });

  it('prepends older message page without duplicating existing messages', async () => {
    const im = useImStore();
    im.messagesByConversation['7001'] = [mapImMessageToConversationMessage(message, 2003)];
    const olderMessage: ImMessageResponse = {
      ...message,
      messageId: 7101,
      seqNo: 1,
      senderId: 2003,
      contentJson: '{"text":"我上午可以处理补卡。"}',
      contentText: '我上午可以处理补卡。',
      sentAt: '2026-05-09T10:00:00',
    };
    const imApi: EmployeeImApi = {
      ...createImApi(),
      messages: () => Promise.resolve([olderMessage, message]),
    };

    await im.loadMessages(7001, 2003, { beforeSeq: 2, size: 20 }, imApi);

    expect(im.messagesByConversation['7001'].map((item) => item.id)).toEqual(['7101', '7102']);
  });

  it('creates private conversation and inserts it into conversation list', async () => {
    const im = useImStore();

    const record = await im.startPrivateConversation(2002, createImApi());

    expect(record).toMatchObject({
      id: '7001',
      title: '张三与李四',
      flow: '私聊',
    });
    expect(im.conversations[0].id).toBe('7001');
  });

  it('creates group conversation and inserts it into conversation list', async () => {
    const im = useImStore();

    const record = await im.createGroupConversation('项目推进群', [2002, 2004], createImApi());

    expect(record).toMatchObject({
      id: '7301',
      title: '项目推进群',
      flow: '群聊',
    });
    expect(im.conversations[0].id).toBe('7301');
  });

  it('discards stale loadMessages result when a newer request resolves first', async () => {
    const im = useImStore();
    type DeferredMessages = { promise: Promise<ImMessageResponse[]>; resolve: (v: ImMessageResponse[]) => void };
    let resolveA: DeferredMessages['resolve'];
    let resolveB: DeferredMessages['resolve'];
    const deferredA: DeferredMessages = { promise: new Promise((r) => { resolveA = r; }), resolve: null! };
    const deferredB: DeferredMessages = { promise: new Promise((r) => { resolveB = r; }), resolve: null! };
    deferredA.resolve = resolveA!;
    deferredB.resolve = resolveB!;

    const messageB: ImMessageResponse = { ...message, messageId: 8100, contentText: '会话B消息' };
    const messageA: ImMessageResponse = { ...message, messageId: 8200, contentText: '会话A消息' };

    const imApi: EmployeeImApi = {
      ...createImApi(),
      messages: vi.fn()
        .mockImplementationOnce(() => deferredA.promise)
        .mockImplementationOnce(() => deferredB.promise),
    };

    // Fire two concurrent loadMessages calls
    const loadA = im.loadMessages('A', 2003, {}, imApi);
    const loadB = im.loadMessages('B', 2003, {}, imApi);

    // B resolves first
    deferredB.resolve([messageB]);
    await loadB;
    expect(im.messageLoading).toBe(false);
    expect(im.messagesByConversation['B'].map((m) => m.id)).toEqual(['8100']);

    // A resolves later — its data should still be written (per-key storage),
    // but loading/error should not be clobbered
    deferredA.resolve([messageA]);
    await loadA;
    expect(im.messageLoading).toBe(false);
    expect(im.error).toBe('');
    expect(im.messagesByConversation['A'].map((m) => m.id)).toEqual(['8200']);
    // B's data should remain untouched
    expect(im.messagesByConversation['B'].map((m) => m.id)).toEqual(['8100']);
  });
});
