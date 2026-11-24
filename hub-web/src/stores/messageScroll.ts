// 消息滚动位置属于视图生命周期状态，不经过 Pinia 响应式代理，避免路由卸载时丢失 DOM 写入。
import type { MessageScrollAnchor } from '@/stores/im';
import { currentContext } from '@/services/localData';
import { useSessionStore } from '@/stores/session';

const positions = new Map<string, MessageScrollAnchor>();

export function saveMessageScroll(id: string, position: MessageScrollAnchor) {
  positions.set(`${useSessionStore().user?.userId}:${id}`, position);
  void currentContext().then(context => context ? window.workforceDesktop?.saveDraft(context, `message-scroll:${id}`, position) : undefined);
}

export function getMessageScroll(id: string) {
  return positions.get(`${useSessionStore().user?.userId}:${id}`);
}
export async function loadMessageScroll(id: string) {
  const context = await currentContext();
  if (!context) return;
  const saved = await window.workforceDesktop?.readDraft(context, `message-scroll:${id}`);
  if (saved) positions.set(`${useSessionStore().user?.userId}:${id}`, saved as MessageScrollAnchor);
}
