// IM 桌面布局与会话列表的纯展示规则，便于独立验证。
export const CONVERSATION_MIN_WIDTH = 240;
export const CONVERSATION_MAX_WIDTH = 360;

export function conversationWidth(startWidth: number, deltaX: number, availableWidth = 360) {
  const max = Math.max(CONVERSATION_MIN_WIDTH, Math.min(CONVERSATION_MAX_WIDTH, availableWidth));
  return Math.round(Math.max(CONVERSATION_MIN_WIDTH, Math.min(max, startWidth + deltaX)) / 4) * 4;
}

export const NAV_MIN_WIDTH = 64;
export const NAV_MAX_WIDTH = 280;
const NAV_COMPACT_SNAP_WIDTH = 96;

export function navigationWidth(startWidth: number, deltaX: number, availableWidth = NAV_MAX_WIDTH) {
  const max = Math.max(NAV_MIN_WIDTH, Math.min(NAV_MAX_WIDTH, availableWidth));
  const width = Math.max(NAV_MIN_WIDTH, Math.min(max, startWidth + deltaX));
  // 接近纯图标宽度时自动吸附；中间宽度仍保留文字，不按文字数量折叠。
  return width <= NAV_COMPACT_SNAP_WIDTH ? NAV_MIN_WIDTH : Math.round(width / 4) * 4;
}

export function resolveConversationId(routeId: string, rememberedId: string, conversationIds: string[]) {
  if (routeId) return routeId;
  if (rememberedId && conversationIds.includes(rememberedId)) return rememberedId;
  return conversationIds[0] ?? '';
}

export function conversationTime(value?: string | null, now = new Date()) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value.slice(5, 10);
  const sameDay = (a: Date, b: Date) => a.getFullYear() === b.getFullYear()
    && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
  if (sameDay(date, now)) return value.slice(11, 16) || value.slice(5, 10);
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (sameDay(date, yesterday)) return '昨天';
  return value.slice(5, 10).replace('-', '/');
}
