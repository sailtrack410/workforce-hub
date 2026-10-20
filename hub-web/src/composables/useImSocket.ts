// hub-web IM WebSocket：原生实现，心跳 + 指数退避重连
import { ref } from 'vue';
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
}) {
  const im = useImStore();
  const session = useSessionStore();
  const connected = ref(false);
  let socket: WebSocket | null = null;
  let reconnectTimer: number | undefined;
  let heartbeatTimer: number | undefined;
  let attempts = 0;

  function url() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
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
      connected.value = true;
      attempts = 0;
      if (heartbeatTimer) window.clearInterval(heartbeatTimer);
      heartbeatTimer = window.setInterval(() => {
        if (next.readyState === WebSocket.OPEN) next.send('PING');
      }, 30000);
    };
    next.onmessage = (event) => handleEvent(String(event.data));
    next.onclose = () => {
      if (socket === next) socket = null;
      connected.value = false;
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
    connected.value = false;
  }

  return { connected, connect, close };
}
