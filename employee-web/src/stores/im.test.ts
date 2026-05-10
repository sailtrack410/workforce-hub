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
    sendMessage: () => Promise.resolve({
      ...message,
      messageId: 7103,
      seqNo: 3,
      senderId: 2003,
      contentJson: '{"text":"我下午两点同步。"}',
      contentText: '我下午两点同步。',
      sentAt: '2026-05-09T10:12:00',
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
    await im.loadMessages(7001, 2003, createImApi());

    expect(im.loading).toBe(false);
    expect(im.messageLoading).toBe(false);
    expect(im.conversations.map((item) => item.id)).toEqual(['7001']);
    expect(im.messagesByConversation['7001'].map((item) => item.id)).toEqual(['7102']);
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

    expect(requests).toEqual([{ messageType: 'TEXT', text: '我下午两点同步。' }]);
    expect(sent).toMatchObject({
      id: '7103',
      author: '我',
      body: '我下午两点同步。',
      side: 'right',
    });
    expect(im.messagesByConversation['7001'].map((item) => item.id)).toEqual(['7102', '7103']);
    expect(im.messageSending).toBe(false);
  });
});
