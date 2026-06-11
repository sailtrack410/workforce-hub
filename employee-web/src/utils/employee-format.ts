import type { EntityId } from '@/api';

export function displayField(value: unknown) {
  if (typeof value === 'number') return String(value);
  if (typeof value === 'string' && value.trim()) return value;
  return '-';
}

export function employmentStatusLabel(status?: string | null) {
  if (status === 'ACTIVE' || status === 'IN_SERVICE') return '在职';
  if (status === 'DISABLED') return '停用';
  if (status === 'RESIGNED') return '离职';
  return displayField(status);
}

export function percentage(value: number, max: number) {
  if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((value / max) * 100)));
}

export function statusText(status: string) {
  if (status === 'PENDING') return '待处理';
  if (status === 'PENDING_REVIEW') return '待预审';
  if (status === 'OPEN') return '待处理';
  if (status === 'IN_APPROVAL') return '审批中';
  if (status === 'ABNORMAL') return '异常';
  if (status === 'NORMAL') return '正常';
  if (status === 'ACTIVE') return '正常';
  if (status === 'READY') return '就绪';
  if (status === 'PASSED') return '已通过';
  if (status === 'FAILED') return '未通过';
  if (status === 'APPROVED') return '已通过';
  if (status === 'REJECTED') return '已驳回';
  if (status === 'WITHDRAWN') return '已撤回';
  if (status === 'NO_RULE') return '未排班';
  if (status === 'LEAVE') return '请假';
  if (status === 'EXPIRED') return '已过期';
  return status;
}

export function minutesToDayLabel(minutes?: number | null) {
  const safeMinutes = Math.max(0, Number(minutes ?? 0));
  const days = safeMinutes / 480;
  return `${Number.isInteger(days) ? days.toFixed(0) : days.toFixed(1)} 天`;
}

export function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

export function toApiDateTime(value: string) {
  return value ? `${value.replace('T', ' ')}:00`.slice(0, 19) : '';
}

export function bizTypeLabel(type?: string | null) {
  if (type === 'LEAVE') return '请假';
  if (type === 'CLOCK_CORRECTION') return '补卡';
  return displayField(type);
}

export function punchTypeLabel(type?: string | null) {
  if (type === 'ON_DUTY') return '上班';
  if (type === 'OFF_DUTY') return '下班';
  return displayField(type);
}

export function actionTypeLabel(type?: string | null) {
  if (type === 'APPROVE') return '通过';
  if (type === 'REJECT') return '驳回';
  if (type === 'WITHDRAW') return '撤回';
  if (type === 'AGENT_PRE_REVIEW') return 'Agent预审';
  return displayField(type);
}

export function entityIdListFromText(value: string): EntityId[] {
  return value
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function toneOfConversation(tone: string) {
  if (tone === 'green') return 'success';
  if (tone === 'amber') return 'warning';
  if (tone === 'red') return 'danger';
  if (tone === 'gray') return 'neutral';
  return 'primary';
}

export function toneOfStatus(status: string) {
  if (['PENDING', 'OPEN', 'IN_APPROVAL', 'ABNORMAL'].includes(status)) return 'warning';
  if (['ACTIVE', 'READY', 'APPROVED'].includes(status)) return 'success';
  if (['REJECTED', 'EXPIRED'].includes(status)) return 'danger';
  return 'neutral';
}
