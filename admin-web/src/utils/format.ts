import type { EntityId } from '@/api/types';

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const fullDateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDateTime(value?: string | null, fallback = '-') {
  if (!value) return fallback;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return fallback;
  return dateTimeFormatter.format(date);
}

export function formatFullDateTime(value?: string | null, fallback = '-') {
  if (!value) return fallback;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return fallback;
  return fullDateTimeFormatter.format(date);
}

export function formatBytes(value?: number | null) {
  if (value === undefined || value === null) return '-';
  if (value < 1024) return `${value} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = value / 1024;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size.toFixed(size >= 10 ? 1 : 2)} ${units[index]}`;
}

// 后端存分钟数，按 8h/天 换算，优先显示天数
export function formatMinutes(value?: number | null) {
  if (value === undefined || value === null) return '-';
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs % 480 === 0) return `${sign}${abs / 480} 天`;
  if (abs >= 480) return `${sign}${(abs / 480).toFixed(1)} 天`;
  if (abs % 60 === 0) return `${sign}${abs / 60} 小时`;
  return `${value} 分钟`;
}

export function formatId(value?: EntityId | null) {
  if (value === undefined || value === null || value === '') return '-';
  return String(value);
}

// 雪花 ID 太长，列表页只显示后 8 位
export function shortId(value?: EntityId | null) {
  const text = formatId(value);
  if (text === '-') return text;
  return text.length > 8 ? text.slice(-8) : text;
}

export function statusLabel(value?: string | null) {
  const labels: Record<string, string> = {
    ACTIVE: '启用',
    INACTIVE: '停用',
    PENDING: '待处理',
    RUNNING: '运行中',
    INDEXING: '向量化中',
    EMBEDDED: '已向量化',
    SUCCEEDED: '成功',
    SUCCESS: '成功',
    FAILED: '失败',
    PARTIAL_FAILED: '部分失败',
    WAITING_HUMAN_CONFIRM: '待确认',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
    SUBMITTED: '已提交',
    APPROVED: '已通过',
    REVOKED: '已撤回',
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
  };
  if (!value) return '-';
  return labels[value] ?? value;
}

// 后端返回的术语对用户不友好，统一替换成前端展示用语
export function humanizeText(value?: string | null) {
  if (!value) return '';
  return value
    .replace(/Agent预审/g, 'AI 预审')
    .replace(/Agent任务/g, 'AI 任务')
    .replace(/Agent会话/g, 'AI 会话')
    .replace(/Agent 不存在/g, 'AI 助手不存在')
    .replace(/Agent不存在/g, 'AI 助手不存在')
    .replace(/IM会话/g, '消息会话')
    .replace(/IM 会话/g, '消息会话')
    .replace(/Agent/g, 'AI 助手');
}

// 统一的错误消息提取，顺便做术语替换
export function errorMessage(error: unknown, fallback = '请求失败') {
  if (error instanceof Error && error.message) return humanizeText(error.message);
  return fallback;
}
