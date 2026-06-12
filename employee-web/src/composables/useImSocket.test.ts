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
