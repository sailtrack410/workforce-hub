import { beforeEach, describe, expect, it } from 'vitest';
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
    messages: () => Promise.resolve([message]),
    startPrivateConversation: () => Promise.resolve(conversation),
    createGroupConversation: () => Promise.resolve({
      ...conversation,
      conversationId: 7301,
      conversationType: 'GROUP',
      title: '项目推进群',
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
    updateReadPosition: (_conversationId, request) => Promise.resolve({
      ...conversation,
      lastReadSeq: request.lastReadSeq,
      unreadCount: Math.max(conversation.lastMessageSeq - request.lastReadSeq, 0),
    }),
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
      title: '张三与李四',
      subtitle: '下午同步一下排班。',
      unread: 1,
      flow: '私聊',
      status: 'ACTIVE',
      tag: 'PRIVATE · ACTIVE',
    });
    expect(record.cards).toEqual([
      { label: '会话类型', value: 'PRIVATE', hint: 'IM 类型' },
      { label: '未读数', value: '1', hint: '基于 lastReadSeq 计算' },
      { label: '最新序号', value: '2', hint: '消息序号' },
    ]);
  });

  it('maps backend message into shell message record', () => {
    const record = mapImMessageToConversationMessage(message, 2003);

    expect(record).toMatchObject({
      id: '7102',
      author: '用户 2002',
      role: 'USER · TEXT',
      body: '下午同步一下排班。',
      side: 'left',
      time: '2026-05-09 10:10',
    });
  });

  it('maps current user messages to the right side', () => {
    const record = mapImMessageToConversationMessage({
      ...message,
      messageId: 7101,
      senderId: 2003,
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

    await im.loadConversations(createImApi());
    await im.loadMessages(7001, 2003, {}, createImApi());

    expect(im.loading).toBe(false);
    expect(im.messageLoading).toBe(false);
    expect(im.conversations.map((item) => item.id)).toEqual(['7001']);
    expect(im.messagesByConversation['7001'].map((item) => item.id)).toEqual(['7102']);
    expect(im.lastMessageSeqByConversation['7001']).toBe(2);
    expect(im.lastReadSeqByConversation['7001']).toBe(1);
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

  it('updates read position and syncs unread count back to conversation list', async () => {
    const im = useImStore();
    await im.loadConversations(createImApi());

    const updated = await im.updateReadPosition(7001, 2, createImApi());

    expect(updated.lastReadSeq).toBe(2);
    expect(im.lastReadSeqByConversation['7001']).toBe(2);
    expect(im.conversations[0].unread).toBe(0);
    expect(im.conversations[0].unreadLabel).toBe('已读');
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
      flow: '团队协作',
    });
    expect(im.conversations[0].id).toBe('7301');
  });
});
