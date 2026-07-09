export type NotificationChannel = 'browser' | 'email';

export interface AppNotificationPayload {
  channel: NotificationChannel;
  topic: string;
  title: string;
  body: string;
  route?: string;
  senderId?: string | number | null;
  conversationId?: string | number;
}

export interface AppNotificationContext {
  enabled: boolean;
  focused: boolean;
  visible: boolean;
  mentionOnly?: boolean;
  currentUserId?: string | number | null;
  currentUserName?: string;
}

export async function ensureBrowserNotificationPermission() {
  if (!('Notification' in globalThis)) return false;
  if (Notification.permission === 'granted') return true;
  if (Notification.permission === 'denied') return false;
  return await Notification.requestPermission() === 'granted';
}

export async function dispatchNotification(payload: AppNotificationPayload, context: AppNotificationContext) {
  if (payload.channel !== 'browser') return false;
  if (!context.enabled || (context.focused && context.visible)) return false;
  if (context.currentUserId != null && payload.senderId != null && String(context.currentUserId) === String(payload.senderId)) return false;
  if (context.mentionOnly && context.currentUserName && !payload.body.includes(`@${context.currentUserName}`)) return false;
  if (!await ensureBrowserNotificationPermission()) return false;

  const notification = new Notification(payload.title, {
    body: payload.body,
    tag: payload.topic,
    data: { route: payload.route },
  });
  notification.onclick = () => {
    window.focus();
    if (payload.route && window.location.pathname !== payload.route) {
      window.location.assign(payload.route);
    }
  };
  return true;
}
