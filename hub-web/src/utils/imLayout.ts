// IM 桌面布局与会话列表的纯展示规则，便于独立验证。
export const CONVERSATION_MIN_WIDTH = 240;
export const CONVERSATION_MAX_WIDTH = 360;

export function conversationWidth(startWidth: number, deltaX: number, availableWidth = 360) {
  const max = Math.max(CONVERSATION_MIN_WIDTH, Math.min(CONVERSATION_MAX_WIDTH, availableWidth));
  return Math.round(Math.max(CONVERSATION_MIN_WIDTH, Math.min(max, startWidth + deltaX)) / 4) * 4;
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
