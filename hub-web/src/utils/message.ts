// hub-web 消息视图模型映射：ImMessageResponse → 视图结构（防御式解析 contentJson 卡片）
import type { ImMessageAttachment, ImMessageResponse } from '@/api/types';

export interface KnowledgeCardData {
  title: string;
  answer: string;
  citations: Array<{ id?: string; title: string }>;
  actions: Array<{ label: string; action?: string }>;
  loading: boolean;
}

export interface ProcessCardData {
  title: string;
  status: string;
  statusTone: 'ok' | 'warn' | 'info' | 'neutral';
  body: string;
  fields: Array<[string, string]>;
  actionLabel: string;
}

export interface MessageView {
  id: string;
  conversationId: string;
  seqNo: number;
  side: 'left' | 'right' | 'system';
  author: string;
  senderType: string;
  senderId?: number | string | null;
  body: string;
  time: string;
  sentAt: string;
  status: string;
  recalled: boolean;
  quotedMessageId?: number | string | null;
  mentionUserIds: Array<number | string>;
  attachments: ImMessageAttachment[];
  knowledgeCard: KnowledgeCardData | null;
  processCard: ProcessCardData | null;
}

function parseJson(raw?: string | null): Record<string, unknown> | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as unknown;
    return value && typeof value === 'object' ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function str(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function strArray(value: unknown): Array<Record<string, unknown>> {
  return Array.isArray(value) ? (value.filter((item) => item && typeof item === 'object') as Array<Record<string, unknown>>) : [];
}

export function parseKnowledgeCard(message: ImMessageResponse): KnowledgeCardData | null {
  const content = parseJson(message.contentJson);
  if (!content) return null;
  const cardType = str(content.cardType);
  if (cardType !== 'agent_result' && cardType !== 'knowledge_answer') return null;
  const citations = strArray(content.citations ?? content.sources).map((c, i) => ({
    id: str(c.id) || `citation-${i}`,
    title: str(c.title) || str(c.name) || '未命名来源',
  }));
  const actions = strArray(content.actions ?? content.suggestedActions).map((a) => ({
    label: str(a.label),
    action: str(a.action),
  })).filter((a) => a.label);
  const answer = str(content.answer) || str(content.text) || str(content.summary);
  return {
    title: str(content.title) || '知识回答',
    answer,
    citations,
    actions,
    loading: !answer && message.messageType !== 'TEXT',
  };
}

export function parseProcessCard(message: ImMessageResponse): ProcessCardData | null {
  const content = parseJson(message.contentJson);
  if (!content) return null;
  const cardType = str(content.cardType);
  if (cardType !== 'process' && cardType !== 'workflow' && cardType !== 'flow') return null;
  const rawFields = content.fields;
  const fields: Array<[string, string]> = Array.isArray(rawFields)
    ? rawFields.map((item) => {
        if (Array.isArray(item)) return [str(item[0]), str(item[1])] as [string, string];
        if (item && typeof item === 'object') {
          const record = item as Record<string, unknown>;
          return [str(record.label ?? record.key), str(record.value)] as [string, string];
        }
        return ['', ''] as [string, string];
      }).filter(([k, v]) => k || v)
    : [];
  const toneText = (str(content.statusTone) || str(content.status)).toLowerCase();
  const statusTone: ProcessCardData['statusTone'] =
    toneText.includes('通过') || toneText.includes('success') || toneText.includes('ok') ? 'ok'
    : toneText.includes('待') || toneText.includes('warn') || toneText.includes('审') ? 'warn'
    : toneText.includes('info') || toneText.includes('中') ? 'info'
    : 'neutral';
  return {
    title: str(content.title) || '流程卡',
    status: str(content.status),
    statusTone,
    body: str(content.body) || str(content.summary),
    fields,
    actionLabel: str(content.actionLabel) || str(content.action),
  };
}

export function toMessageView(message: ImMessageResponse, currentUserId?: number | string): MessageView {
  const system = message.senderType === 'SYSTEM';
  const right = !system && currentUserId !== undefined && String(message.senderId) === String(currentUserId);
  return {
    id: String(message.messageId),
    conversationId: String(message.conversationId),
    seqNo: message.seqNo,
    side: system ? 'system' : right ? 'right' : 'left',
    author: message.senderName || (system ? '系统' : '成员'),
    senderType: message.senderType,
    senderId: message.senderId,
    body: message.contentText ?? '',
    time: (message.sentAt || '').slice(5, 16),
    sentAt: message.sentAt ?? '',
    status: message.status ?? '',
    recalled: message.status === 'RECALLED',
    quotedMessageId: message.quotedMessageId,
    mentionUserIds: (message.mentions ?? []).map((m) => m.userId),
    attachments: message.attachments ?? [],
    knowledgeCard: parseKnowledgeCard(message),
    processCard: parseProcessCard(message),
  };
}

/* ---------- 附件类型判定（批次⑨） ---------- */
export function isImageAttachment(a: ImMessageAttachment): boolean {
  return Boolean(a.mimeType?.startsWith('image/'));
}
export function isVideoAttachment(a: ImMessageAttachment): boolean {
  return Boolean(a.mimeType?.startsWith('video/'));
}

/* ---------- 微信式时间分隔（批次⑨） ---------- */
/** 两条消息间隔超过该毫秒数时插入时间分隔条 */
export const TIME_DIVIDER_GAP = 5 * 60 * 1000;

export function friendlyTime(isoText: string): string {
  if (!isoText) return '';
  const d = new Date(isoText);
  if (Number.isNaN(d.getTime())) return '';
  const now = new Date();
  const hm = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  const startOf = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const dayDiff = Math.round((startOf(now) - startOf(d)) / 86400000);
  if (dayDiff <= 0) return hm;
  if (dayDiff === 1) return `昨天 ${hm}`;
  if (d.getFullYear() === now.getFullYear()) return `${d.getMonth() + 1}月${d.getDate()}日 ${hm}`;
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${hm}`;
}
