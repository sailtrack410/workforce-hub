import { ref } from 'vue';
import { parseJsonPreservingUnsafeIntegers } from '@/api';
import type { ImMessageResponse } from '@/api';
import type { useImStore } from '@/stores/im';
import { mapImMessageToConversationMessage } from '@/stores/im';
import type { useSessionStore } from '@/stores/session';
import type { useShellStore } from '@/stores/shell';

interface UseImSocketOptions {
  im: ReturnType<typeof useImStore>;
  session: ReturnType<typeof useSessionStore>;
  shell: ReturnType<typeof useShellStore>;
  syncConversationMessages: (conversationId: string) => Promise<void>;
}

export function useImSocket({ im, session, shell, syncConversationMessages }: UseImSocketOptions) {
  const wsConnected = ref(false);
  let socket: WebSocket | null = null;
  let reconnectTimer: number | undefined;
  let heartbeatTimer: number | undefined;
  let reconnectAttempts = 0;
  const slowReconnectAfterAttempts = 3;
  const slowReconnectDelay = 60000;

  function websocketUrl() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws/employee/im`;
  }

  function closeImSocket() {
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
    reconnectAttempts = 0;
    wsConnected.value = false;
  }

  function scheduleImSocketReconnect() {
    if (!session.isAuthenticated || reconnectTimer) return;
    const delay = reconnectAttempts >= slowReconnectAfterAttempts
      ? slowReconnectDelay
      : Math.min(10000, 800 + reconnectAttempts * 1200);
    reconnectAttempts += 1;
    reconnectTimer = window.setTimeout(() => {
      reconnectTimer = undefined;
      connectImSocket();
    }, delay);
  }

  function connectImSocket() {
    if (!session.isAuthenticated || socket) return;
    try {
      const nextSocket = new WebSocket(websocketUrl());
      socket = nextSocket;
      nextSocket.onopen = () => {
        wsConnected.value = true;
        reconnectAttempts = 0;
        void refreshImAfterReconnect();
        if (heartbeatTimer) window.clearInterval(heartbeatTimer);
        heartbeatTimer = window.setInterval(() => {
          if (nextSocket.readyState === WebSocket.OPEN) nextSocket.send('PING');
        }, 30000);
      };
      nextSocket.onmessage = (event) => {
        void handleImSocketMessage(event.data);
      };
      nextSocket.onclose = () => {
        if (socket === nextSocket) socket = null;
        wsConnected.value = false;
        if (heartbeatTimer) {
          window.clearInterval(heartbeatTimer);
          heartbeatTimer = undefined;
        }
        scheduleImSocketReconnect();
      };
      nextSocket.onerror = () => {
        nextSocket.close();
      };
    } catch {
      scheduleImSocketReconnect();
    }
  }

  async function refreshImAfterReconnect() {
    if (!session.isAuthenticated) return;
    try {
      await im.loadConversations();
      shell.mergeBackendConversations(im.conversations);
      if (shell.active.messages) {
        await syncConversationMessages(shell.active.messages);
      }
    } catch {
      return;
    }
  }

  async function handleImSocketMessage(raw: string) {
    let event: {
      eventType?: string;
      conversationId?: string | number;
      payload?: unknown;
    };
    try {
      event = parseJsonPreservingUnsafeIntegers(raw);
    } catch {
      return;
    }
    if (!event.eventType || event.eventType === 'CONNECTED' || event.eventType === 'PONG') return;
    const conversationId = event.conversationId ? String(event.conversationId) : '';
    if (!conversationId) {
      await im.loadConversations();
      shell.mergeBackendConversations(im.conversations);
      return;
    }
    if (['IM_MESSAGE_CREATED', 'IM_AGENT_MESSAGE_CREATED', 'IM_MESSAGE_EDITED', 'IM_MESSAGE_RECALLED', 'IM_AGENT_REPLY_FAILED'].includes(event.eventType)) {
      if (isImMessageResponse(event.payload)) {
        im.upsertMessage(conversationId, mapImMessageToConversationMessage(event.payload, session.user?.userId));
        shell.mergeBackendMessages(conversationId, im.messagesByConversation[conversationId] ?? []);
      }
      if (shell.active.messages === conversationId) {
        const loadingMessages = (im.messagesByConversation[conversationId] ?? [])
          .filter((item) => item.id.startsWith('agent-loading-'));
        await syncConversationMessages(conversationId);
        const hasAgentPayload = isImMessageResponse(event.payload) && event.payload.senderType === 'AGENT';
        if (loadingMessages.length && !hasAgentPayload) {
          im.messagesByConversation[conversationId] = [
            ...(im.messagesByConversation[conversationId] ?? []).filter((item) => !item.id.startsWith('agent-loading-')),
            ...loadingMessages,
          ];
          shell.mergeBackendMessages(conversationId, im.messagesByConversation[conversationId]);
        }
      } else {
        await im.loadConversations();
        shell.mergeBackendConversations(im.conversations);
      }
      return;
    }
    if (['IM_CONVERSATION_PREFERENCE_UPDATED', 'IM_GROUP_PROFILE_UPDATED', 'IM_GROUP_MEMBERS_ADDED', 'IM_GROUP_MEMBERS_REMOVED', 'IM_GROUP_MEMBER_LEFT', 'IM_GROUP_DISMISSED'].includes(event.eventType)) {
      await im.loadConversations();
      shell.mergeBackendConversations(im.conversations);
      if (shell.active.messages === conversationId) {
        await im.loadConversationDetail(conversationId).catch(() => undefined);
        shell.mergeBackendConversations(im.conversations);
      }
    }
  }

  function isImMessageResponse(value: unknown): value is ImMessageResponse {
    if (!value || typeof value !== 'object') return false;
    return 'messageId' in value && 'conversationId' in value && 'seqNo' in value;
  }

  return {
    wsConnected,
    websocketUrl,
    closeImSocket,
    scheduleImSocketReconnect,
    connectImSocket,
    refreshImAfterReconnect,
    handleImSocketMessage,
    isImMessageResponse,
  };
}
