// 桌面消息缓存：仅在 Electron preload 明确暴露能力时启用。
import type { EntityId, ImMessageResponse } from '@/api/types';

const desktop = () => window.workforceDesktop;

export function isDesktopApp() {
  return Boolean(desktop());
}

export async function listCachedMessages(userId: EntityId, conversationId: EntityId, options?: { beforeSeq?: number; size?: number }, context?: string) {
  return desktop()?.listMessages(String(userId), String(conversationId), options, context) ?? [];
}

export async function cacheMessages(userId: EntityId, messages: ImMessageResponse[]) {
  if (messages.length) await desktop()?.saveMessages(String(userId), messages);
}
