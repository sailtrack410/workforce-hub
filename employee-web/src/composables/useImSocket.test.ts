import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createRenderer, defineComponent, h } from 'vue';
import { useImSocket } from './useImSocket';

class FakeWebSocket {
  static readonly OPEN = 1;
  static instances: FakeWebSocket[] = [];

  onopen: (() => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  readyState = 0;

  constructor(readonly url: string) {
    FakeWebSocket.instances.push(this);
  }

  close() {
    this.readyState = 3;
  }

  send() {
    return undefined;
  }
}

describe('useImSocket', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    FakeWebSocket.instances = [];
    Object.defineProperty(globalThis, 'window', {
      value: {
        location: { protocol: 'http:', host: '127.0.0.1:5175' },
        setTimeout: globalThis.setTimeout,
        clearTimeout: globalThis.clearTimeout,
        setInterval: globalThis.setInterval,
        clearInterval: globalThis.clearInterval,
      },
      configurable: true,
    });
    Object.defineProperty(globalThis, 'WebSocket', {
      value: FakeWebSocket,
      configurable: true,
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    Reflect.deleteProperty(globalThis, 'window');
    Reflect.deleteProperty(globalThis, 'WebSocket');
  });

  it('slows websocket reconnects after repeated handshake failures', () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    let socket: ReturnType<typeof useImSocket> | undefined;
    const Root = defineComponent({
      setup() {
        socket = useImSocket({
          im: {
            loadConversations: vi.fn(),
            conversations: [],
            messagesByConversation: {},
          } as never,
          session: {
            isAuthenticated: true,
            user: { userId: '1' },
          } as never,
          shell: {
            active: { messages: '' },
            mergeBackendConversations: vi.fn(),
            mergeBackendMessages: vi.fn(),
          } as never,
          syncConversationMessages: vi.fn(),
        });
        return () => h('span');
      },
    });

    createTestRenderer().createApp(Root).mount({});

    socket?.connectImSocket();

    latestSocket()?.onclose?.();
    expect(latestTimeoutDelay(setTimeoutSpy)).toBe(800);
    vi.advanceTimersByTime(800);

    latestSocket()?.onclose?.();
    expect(latestTimeoutDelay(setTimeoutSpy)).toBe(2000);
    vi.advanceTimersByTime(2000);

    latestSocket()?.onclose?.();
    expect(latestTimeoutDelay(setTimeoutSpy)).toBe(3200);
    vi.advanceTimersByTime(3200);

    latestSocket()?.onclose?.();
    expect(latestTimeoutDelay(setTimeoutSpy)).toBe(60000);
  });

  it('dispatches browser notification for incoming websocket messages', async () => {
    const notifyUser = vi.fn();
    let socket: ReturnType<typeof useImSocket> | undefined;
    const Root = defineComponent({
      setup() {
        socket = useImSocket({
          im: {
            loadConversations: vi.fn().mockResolvedValue(undefined),
            conversations: [{
              id: '1001',
              title: '运营一部群',
              messages: [],
              panel: { attachments: [] },
            }],
            messagesByConversation: {},
            upsertMessage: vi.fn((conversationId, message) => {
              socketIm.messagesByConversation[conversationId] = [message];
            }),
          } as never,
          session: {
            isAuthenticated: true,
            user: { userId: '1', displayName: '李雷' },
            displayName: '李雷',
          } as never,
          shell: {
            active: { messages: '' },
            notifDesktop: true,
            notifSound: true,
            notifMentionOnly: false,
            presenceStatus: 'ONLINE',
            conversations: [{ id: '1001', title: '运营一部群' }],
            mergeBackendConversations: vi.fn(),
            mergeBackendMessages: vi.fn(),
          } as never,
          syncConversationMessages: vi.fn(),
          notifyUser,
        });
        return () => h('span');
      },
    });
    const socketIm = {
      messagesByConversation: {} as Record<string, unknown[]>,
    };

    createTestRenderer().createApp(Root).mount({});
    await socket?.handleImSocketMessage(JSON.stringify({
      eventType: 'IM_MESSAGE_CREATED',
      conversationId: '1001',
      payload: {
        messageId: '9001',
        conversationId: '1001',
        seqNo: 3,
        senderType: 'USER',
        senderId: '2',
        messageType: 'TEXT',
        contentJson: '{"text":"收到新的消息"}',
        contentText: '收到新的消息',
        status: 'NORMAL',
        sentAt: '2026-07-06 10:00:00',
      },
    }));

    expect(notifyUser).toHaveBeenCalledWith(expect.objectContaining({
      channel: 'browser',
      topic: 'im.message.created',
      title: '运营一部群',
      body: '收到新的消息',
      route: '/messages/1001',
      senderId: '2',
      conversationId: '1001',
    }), expect.objectContaining({
      enabled: true,
      currentUserId: '1',
    }));
  });
});

function latestSocket() {
  return FakeWebSocket.instances[FakeWebSocket.instances.length - 1];
}

function latestTimeoutDelay(setTimeoutSpy: ReturnType<typeof vi.spyOn>) {
  const calls = setTimeoutSpy.mock.calls;
  return calls[calls.length - 1]?.[1];
}

function createTestRenderer() {
  return createRenderer({
    patchProp: () => undefined,
    insert: () => undefined,
    remove: () => undefined,
    createElement: () => ({}),
    createText: () => ({}),
    createComment: () => ({}),
    setText: () => undefined,
    setElementText: () => undefined,
    parentNode: () => null,
    nextSibling: () => null,
  });
}
