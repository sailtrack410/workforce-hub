// 消息滚动位置属于视图生命周期状态，不经过 Pinia 响应式代理，避免路由卸载时丢失 DOM 写入。
import type { MessageScrollAnchor } from '@/stores/im';

const positions = new Map<string, MessageScrollAnchor>();

export function saveMessageScroll(id: string, position: MessageScrollAnchor) {
  positions.set(id, position);
}

export function getMessageScroll(id: string) {
  return positions.get(id);
}
