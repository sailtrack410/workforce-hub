// hub-web IM WebSocket：原生实现，心跳 + 指数退避重连
import { computed } from 'vue';
import { useImStore } from '@/stores/im';
import { useSessionStore } from '@/stores/session';
import type { ImMessageResponse } from '@/api/types';

interface SocketEvent {
  eventType?: string;
  conversationId?: string | number;
  payload?: unknown;
}

function isMessagePayload(value: unknown): value is ImMessageResponse {
  return Boolean(value && typeof value === 'object' && 'messageId' in (value as object));
}

export function useImSocket(options: {
  onMessage: (conversationId: string) => void;
  onCreated?: (message: ImMessageResponse) => void;
}) {
  const im = useImStore();
  const session = useSessionStore();
  const connected = computed(() => im.socketConnected);
  let socket: WebSocket | null = null;
  let reconnectTimer: number | undefined;
  let heartbeatTimer: number | undefined;
  let attempts = 0;

  function url() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    if (window.workforceDesktop && window.location.protocol === 'file:') {
      const api = new URL(import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080');
      return `${api.protocol === 'https:' ? 'wss:' : 'ws:'}//${api.host}/ws/employee/im`;
    }
    return `${protocol}//${window.location.host}/ws/employee/im`;
  }

  function scheduleReconnect() {
    if (!session.isAuthenticated || reconnectTimer) return;
    const delay = attempts >= 3 ? 30000 : Math.min(8000, 800 + attempts * 1200);
    attempts += 1;
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = undefined;
      connect();
    }, delay);
  }

  function handleEvent(raw: string) {
    let event: SocketEvent;
    try {
      event = JSON.parse(raw) as SocketEvent;
    } catch {
      return;
    }
    if (isMessagePayload(event.payload)) {
      im.upsertMessage(event.payload);
      im.receivedMessage = event.payload;
      if (event.eventType === 'IM_MESSAGE_CREATED' || event.eventType === 'IM_AGENT_MESSAGE_CREATED') options.onCreated?.(event.payload);
      if (event.conversationId !== undefined && event.conversationId !== null) {
        options.onMessage(String(event.conversationId));
      }
      return;
    }
    // 会话级变更（群资料/成员/偏好）：刷新会话列表
    if (typeof event.eventType === 'string' && event.eventType.startsWith('IM_')) {
      void im.loadConversations().catch(() => undefined);
    }
  }

  function connect() {
    if (!session.isAuthenticated || socket) return;
    const next = new WebSocket(url());
    socket = next;
    next.onopen = () => {
      im.socketConnected = true;
      attempts = 0;
      void im.loadConversations().then(() => im.activeConversationId ? im.loadMessages(im.activeConversationId) : undefined).catch(() => undefined);
      if (heartbeatTimer) window.clearInterval(heartbeatTimer);
      heartbeatTimer = window.setInterval(() => {
        if (next.readyState === WebSocket.OPEN) next.send('PING');
      }, 30000);
    };
    next.onmessage = (event) => handleEvent(String(event.data));
    next.onclose = () => {
      if (socket === next) socket = null;
      im.socketConnected = false;
      if (heartbeatTimer) {
        window.clearInterval(heartbeatTimer);
        heartbeatTimer = undefined;
      }
      scheduleReconnect();
    };
    next.onerror = () => next.close();
  }

  function close() {
    if (reconnectTimer) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = undefined;
    }
    if (heartbeatTimer) {
      window.clearInterval(heartbeatTimer);
      heartbeatTimer = undefined;
    }
    if (socket) {
      socket.onopen = null;
      socket.onclose = null;
      socket.onerror = null;
      socket.onmessage = null;
      socket.close();
      socket = null;
    }
    im.socketConnected = false;
  }

  return { connected, connect, close };
}
