import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { dispatchNotification, ensureBrowserNotificationPermission } from './notifications';

class FakeNotification {
  static permission: NotificationPermission = 'default';
  static requestPermission = vi.fn<() => Promise<NotificationPermission>>();
  static instances: FakeNotification[] = [];

  onclick: (() => void) | null = null;

  constructor(readonly title: string, readonly options?: NotificationOptions) {
    FakeNotification.instances.push(this);
  }
}

describe('notifications', () => {
  beforeEach(() => {
    FakeNotification.permission = 'default';
    FakeNotification.requestPermission.mockReset();
    FakeNotification.requestPermission.mockResolvedValue('granted');
    FakeNotification.instances = [];
    Object.defineProperty(globalThis, 'Notification', {
      value: FakeNotification,
      configurable: true,
    });
    Object.defineProperty(globalThis, 'window', {
      value: {
        focus: vi.fn(),
        location: { pathname: '/messages', assign: vi.fn() },
      },
      configurable: true,
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    Reflect.deleteProperty(globalThis, 'Notification');
    Reflect.deleteProperty(globalThis, 'window');
  });

  it('requests permission before enabling browser notifications', async () => {
    await expect(ensureBrowserNotificationPermission()).resolves.toBe(true);
    expect(FakeNotification.requestPermission).toHaveBeenCalledTimes(1);
  });

  it('sends browser notifications through the unified payload', async () => {
    FakeNotification.permission = 'granted';

    await dispatchNotification({
      channel: 'browser',
      topic: 'im.message.created',
      title: '运营一部群',
      body: '收到新的消息',
      route: '/messages/1001',
      senderId: '2',
    }, {
      enabled: true,
      focused: false,
      visible: false,
      currentUserId: '1',
    });

    expect(FakeNotification.instances).toHaveLength(1);
    expect(FakeNotification.instances[0].title).toBe('运营一部群');
    expect(FakeNotification.instances[0].options).toMatchObject({
      body: '收到新的消息',
      tag: 'im.message.created',
      data: { route: '/messages/1001' },
    });
  });

  it('skips foreground, own, disabled and mention-only notifications', async () => {
    FakeNotification.permission = 'granted';
    const payload = {
      channel: 'browser' as const,
      topic: 'im.message.created',
      title: '消息',
      body: '收到新的消息',
      senderId: '1',
    };

    await dispatchNotification(payload, { enabled: false, focused: false, visible: false });
    await dispatchNotification(payload, { enabled: true, focused: true, visible: true });
    await dispatchNotification(payload, { enabled: true, focused: false, visible: false, currentUserId: '1' });
    await dispatchNotification({ ...payload, senderId: '2' }, {
      enabled: true,
      focused: false,
      visible: false,
      mentionOnly: true,
      currentUserName: '李雷',
    });

    expect(FakeNotification.instances).toHaveLength(0);
  });
});
