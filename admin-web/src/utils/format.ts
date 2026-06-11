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

export function errorMessage(error: unknown, fallback = '请求失败') {
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}
