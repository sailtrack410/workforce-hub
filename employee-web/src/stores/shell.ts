import { defineStore } from 'pinia';
import { useImStore } from './im';
import type {
  AccentColor,
  BubbleStyle,
  CurrentUserResponse,
  EmployeeFileResponse,
  EntityId,
  FontScale,
  OrgDepartmentTreeNodeResponse,
  OrgUserDetailResponse,
  OrgUserSummaryResponse,
  PresenceStatus,
  QuotaResponse,
} from '../api';

export type {
  AccentColor,
  BubbleStyle,
  CurrentUserResponse,
  EmployeeFileResponse,
  EntityId,
  FontScale,
  OrgDepartmentTreeNodeResponse,
  OrgUserDetailResponse,
  OrgUserSummaryResponse,
  PresenceStatus,
  QuotaResponse,
} from '../api';
import type { ContactCategoryItem } from '../components/contact-category';
import type { KnowledgeAnswerCard } from '@/types/knowledge';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { createClientId } from '@/utils/request-id';

export type SectionId = 'messages' | 'todos' | 'files' | 'workbench' | 'processes' | 'contacts' | 'agents' | 'reports' | 'settings';
export type ContactsViewMode = 'list' | 'tree';
export type ConversationTone = 'blue' | 'green' | 'amber' | 'red' | 'gray';
export type SectionFilter = '全部' | '未处理' | 'AI' | '群聊' | '私聊' | '进行中' | '已完成' | '未开始';
export type ThemeMode = 'light' | 'dark';
export type MessageDensity = 'cozy' | 'compact';
export type TodoTagColor = 'blue' | 'green' | 'amber' | 'rose' | 'teal' | 'gray';

export interface StoredPreferences {
  theme: ThemeMode;
  messageDensity: MessageDensity;
  listWidth: number;
  signature: string;
  presenceStatus: PresenceStatus;
  notifSound: boolean;
  notifDesktop: boolean;
  notifMentionOnly: boolean;
  notifUnreadBadge: boolean;
  accentColor: AccentColor;
  fontScale: FontScale;
  bubbleStyle: BubbleStyle;
}

export interface SectionAction {
  label: string;
  action: 'switch-section' | 'switch-item' | 'open-workbench' | 'upload-personal-file';
  section?: SectionId;
  item?: string;
  app?: string;
}

export interface SectionMeta {
  kicker: string;
  title: string;
  subtitle: string;
  search: string;
  primaryAction?: SectionAction;
  secondaryAction?: SectionAction;
}

export interface NavRailItem {
  id: SectionId;
  label: string;
  glyph: string;
}

export interface ConversationMessage {
  id: string;
  type?: 'text' | 'process-card' | 'knowledge-card';
  author: string;
  originalAuthor?: string;
  role: string;
  body: string;
  side?: 'left' | 'right' | 'system';
  time?: string;
  seqNo?: number;
  senderType?: string;
  senderId?: EntityId | null;
  status?: string;
  quotedMessageId?: EntityId | null;
  mentions?: MessageMention[];
  translatedText?: string;
  attachments?: ConversationAttachment[];
  processCard?: ConversationProcessCard;
  knowledgeCard?: KnowledgeAnswerCard;
}

export interface MessageMention {
  userId: EntityId;
  name: string;
  originalName?: string;
  role?: string;
  department?: string;
  avatarUrl?: string;
}

export interface ConversationCard {
  label: string;
  value: string;
  hint: string;
}

export interface ConversationStep {
  label: string;
  status: 'done' | 'current' | 'pending';
  detail: string;
}

export interface ConversationAttachment {
  id: string;
  name: string;
  type: string;
  size: string;
  status: string;
  attachmentRefId?: EntityId;
  mimeType?: string;
}

export interface ConversationProcessCheck {
  label: string;
  result: string;
  passed: boolean;
}

export interface ConversationProcessCard {
  title: string;
  status: string;
  statusTone: 'info' | 'success' | 'warning' | 'neutral';
  body: string;
  fields?: Array<[string, string]>;
  checks?: ConversationProcessCheck[];
  file?: ConversationAttachment;
  actionLabel: string;
}

export interface ConversationAuditRecord {
  label: string;
  actor: string;
  time: string;
  status: string;
  statusTone: 'done' | 'current' | 'pending' | 'rejected';
  detail: string;
}

export interface ConversationEvidence {
  label: string;
  value: string;
}

export interface ConversationPanel {
  members: Array<{ name: string; originalName?: string; role: string; userId?: string | number; memberType?: string }>;
  tasks: string[];
  attachments: ConversationAttachment[];
  auditTrail?: ConversationAuditRecord[];
  agentEvidence?: ConversationEvidence[];
}

export interface ConversationRecord {
  id: string;
  kind: 'approval' | 'team' | 'agent' | 'attendance' | 'file' | 'private';
  tone: ConversationTone;
  badge: string;
  avatarUrl?: string;
  title: string;
  subtitle: string;
  memberCount?: number;
  meta: string;
  tag: string;
  unread: number;
  flow: string;
  status: string;
  pinned?: boolean;
  muted?: boolean;
  hasMentionMe?: boolean;
  hasReplyMe?: boolean;
  unreadLabel: string;
  summaryTitle: string;
  summaryBody: string;
  cards: ConversationCard[];
  workflowSteps: ConversationStep[];
  messages: ConversationMessage[];
  panel: ConversationPanel;
}

export interface TodoRecord {
  id: string;
  title: string;
  subtitle: string;
  note: string;
  time: string;
  status: string;
  priority: string;
  requestNo: string;
  taskId: string;
  currentStepName: string;
  summary: string;
  fields: Array<[string, string]>;
  timeline: string[];
  actions: SectionAction[];
  startDate?: string;
  endDate?: string;
  startAt?: string;
  endAt?: string;
  tagName?: string;
  tagColor?: TodoTagColor;
  completed?: boolean;
  createdAt?: string;
}

export interface FileSpaceRecord {
  id: string;
  title: string;
  subtitle: string;
  note: string;
  usage: number;
  label: string;
  summary: Array<[string, string]>;
  files: PersonalFileRecord[];
}

export interface PersonalFileRecord {
  id: string;
  name: string;
  type: string;
  size: string;
  createdAt: string;
  rawCreatedAt?: string;
  fileSizeBytes?: number;
  status: string;
  attachmentRefId?: EntityId;
  folderId?: EntityId | null;
  mimeType?: string;
  downloadUrl?: string;
}

export interface WorkbenchRecord {
  id: string;
  title: string;
  label: string;
  subtitle: string;
  note: string;
  tone: ConversationTone;
  status: string;
  fields: Array<[string, string]>;
  stats: Array<[string, string]>;
  steps: string[];
  disabled?: boolean;
}

export interface ProfileRecord {
  id: string;
  title: string;
  subtitle: string;
  note: string;
  tone: ConversationTone;
  stats: Array<[string, string]>;
  fields: Array<[string, string]>;
}

export interface ContactRecord {
  id: string;
  name: string;
  originalName?: string;
  remarkName?: string;
  role: string;
  department: string;
  primaryDeptId?: EntityId | null;
  relation: string;
  source: string;
  userId?: string | number;
  avatarAttachmentRefId?: EntityId | null;
  avatarUrl?: string;
  employeeNo?: string;
  status?: string | null;
  employmentStatus?: string | null;
  signature?: string;
  presenceStatus?: PresenceStatus;
  managerName?: string | null;
}

export const sectionMeta: Record<SectionId, SectionMeta> = {
  messages: {
    kicker: '',
    title: '消息',
    subtitle: '',
    search: '搜索会话',
    secondaryAction: {
      label: '查看待办',
      action: 'switch-section',
      section: 'todos',
    },
  },
  todos: {
    kicker: '',
    title: '待办',
    subtitle: '',
    search: '搜索待办',
    primaryAction: {
      label: '添加待办',
      action: 'switch-section',
      section: 'todos',
    },
  },
  files: {
    kicker: '',
    title: '云空间',
    subtitle: '',
    search: '搜索文件',
    primaryAction: {
      label: '上传文件',
      action: 'upload-personal-file',
    },
  },
  workbench: {
    kicker: '',
    title: '工作台',
    subtitle: '',
    search: '搜索应用',
    primaryAction: {
      label: 'OA 申请',
      action: 'switch-item',
      item: 'oa',
    },
    secondaryAction: {
      label: '我的考勤',
      action: 'switch-item',
      item: 'attendance',
    },
  },
  processes: {
    kicker: '',
    title: '流程',
    subtitle: '',
    search: '搜索流程',
  },
  contacts: {
    kicker: '',
    title: '通讯录',
    subtitle: '',
    search: '搜索联系人',
  },
  agents: {
    kicker: '',
    title: 'AI 助手',
    subtitle: '',
    search: '搜索 AI 助手',
  },
  reports: {
    kicker: '',
    title: '报表',
    subtitle: '',
    search: '搜索报表',
  },
  settings: {
    kicker: '',
    title: '个人中心',
    subtitle: '',
    search: '搜索设置',
  },
};

export const navItems: Array<{ id: SectionId; label: string; glyph: string }> = [
  { id: 'messages', label: '消息', glyph: '息' },
  { id: 'todos', label: '待办', glyph: '办' },
  { id: 'files', label: '云空间', glyph: '云' },
  { id: 'workbench', label: '工作台', glyph: '台' },
  { id: 'contacts', label: '通讯录', glyph: '录' },
  { id: 'agents', label: 'AI 助手', glyph: '助' },
  { id: 'settings', label: '个人中心', glyph: '心' },
];

export const primaryNavItems: NavRailItem[] = navItems;

export const sectionFilters: SectionFilter[] = ['全部', '未处理', 'AI', '群聊', '私聊'];
export const todoSectionFilters: SectionFilter[] = ['全部', '进行中', '已完成', '未开始'];

const currentUser = {
  name: '员工',
  shortName: '员',
  loginName: '',
  title: '',
  department: '',
};

const preferenceStorageKey = 'workforce-hub.employee.preferences.v1';
const personalFilesStoragePrefix = 'workforce-hub.employee.personal-files.v1';
const contactRemarksStoragePrefix = 'workforce-hub.employee.contact-remarks.v1';
const todoStorageKey = 'workforce-hub.employee.todos.v1';

const defaultPreferences: StoredPreferences = {
  theme: 'light',
  messageDensity: 'cozy',
  listWidth: 400,
  signature: '',
  presenceStatus: 'ONLINE',
  notifSound: true,
  notifDesktop: true,
  notifMentionOnly: false,
  notifUnreadBadge: true,
  accentColor: 'indigo',
  fontScale: 'medium',
  bubbleStyle: 'rounded',
};

function normalizePreferenceInput(preferences: Partial<StoredPreferences> = {}): StoredPreferences {
  const width = typeof preferences.listWidth === 'number' && Number.isFinite(preferences.listWidth)
    ? Math.max(384, Math.min(520, Math.round(preferences.listWidth)))
    : defaultPreferences.listWidth;
  const signature = typeof preferences.signature === 'string'
    ? preferences.signature.trim().slice(0, 140)
    : defaultPreferences.signature;
  return {
    theme: preferences.theme === 'dark' || preferences.theme === 'light' ? preferences.theme : defaultPreferences.theme,
    messageDensity: preferences.messageDensity === 'compact' || preferences.messageDensity === 'cozy'
      ? preferences.messageDensity
      : defaultPreferences.messageDensity,
    listWidth: width,
    signature,
    presenceStatus: ['ONLINE', 'BUSY', 'AWAY', 'DND'].includes(String(preferences.presenceStatus))
      ? preferences.presenceStatus as PresenceStatus
      : defaultPreferences.presenceStatus,
    notifSound: typeof preferences.notifSound === 'boolean' ? preferences.notifSound : defaultPreferences.notifSound,
    notifDesktop: typeof preferences.notifDesktop === 'boolean' ? preferences.notifDesktop : defaultPreferences.notifDesktop,
    notifMentionOnly: typeof preferences.notifMentionOnly === 'boolean' ? preferences.notifMentionOnly : defaultPreferences.notifMentionOnly,
    notifUnreadBadge: typeof preferences.notifUnreadBadge === 'boolean' ? preferences.notifUnreadBadge : defaultPreferences.notifUnreadBadge,
    accentColor: ['indigo', 'violet', 'blue', 'teal', 'amber', 'rose'].includes(String(preferences.accentColor))
      ? preferences.accentColor as AccentColor
      : defaultPreferences.accentColor,
    fontScale: ['small', 'medium', 'large'].includes(String(preferences.fontScale))
      ? preferences.fontScale as FontScale
      : defaultPreferences.fontScale,
    bubbleStyle: preferences.bubbleStyle === 'sharp' || preferences.bubbleStyle === 'rounded'
      ? preferences.bubbleStyle
      : defaultPreferences.bubbleStyle,
  };
}

function readStoredPreferences(): StoredPreferences {
  try {
    const storage = globalThis.localStorage;
    const raw = storage?.getItem(preferenceStorageKey);
    if (!raw) return defaultPreferences;
    const parsed = JSON.parse(raw) as Partial<StoredPreferences>;
    return normalizePreferenceInput(parsed);
  } catch {
    return defaultPreferences;
  }
}

function writeStoredPreferences(preferences: StoredPreferences) {
  try {
    globalThis.localStorage?.setItem(preferenceStorageKey, JSON.stringify(preferences));
  } catch {
    // 浏览器隐私模式可能禁止写入本地存储，不阻断当前会话设置。
  }
}

function isStoredPersonalFile(value: unknown): value is PersonalFileRecord {
  if (!value || typeof value !== 'object') return false;
  const file = value as Partial<PersonalFileRecord>;
  return typeof file.id === 'string'
    && typeof file.name === 'string'
    && typeof file.type === 'string'
    && typeof file.size === 'string'
    && typeof file.createdAt === 'string'
    && typeof file.status === 'string';
}

function personalFilesStorageKey(ownerId?: string | number | null) {
  return `${personalFilesStoragePrefix}.${ownerId || 'anonymous'}`;
}

function readStoredPersonalFiles(ownerId?: string | number | null): PersonalFileRecord[] {
  try {
    const raw = globalThis.localStorage?.getItem(personalFilesStorageKey(ownerId));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(isStoredPersonalFile).slice(0, 24);
  } catch {
    return [];
  }
}

function writeStoredPersonalFiles(files: PersonalFileRecord[], ownerId?: string | number | null) {
  try {
    globalThis.localStorage?.setItem(personalFilesStorageKey(ownerId), JSON.stringify(files.slice(0, 24)));
  } catch {
    // 本地存储不可写时只影响刷新后的文件列表保留，不影响本次上传链路。
  }
}

function contactRemarksStorageKey(ownerId?: string | number | null) {
  return `${contactRemarksStoragePrefix}.${ownerId || 'anonymous'}`;
}

function readStoredContactRemarks(ownerId?: string | number | null): Record<string, string> {
  try {
    const raw = globalThis.localStorage?.getItem(contactRemarksStorageKey(ownerId));
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {};
    return Object.fromEntries(Object.entries(parsed as Record<string, unknown>)
      .map(([key, value]) => [key, typeof value === 'string' ? value.trim().slice(0, 30) : ''])
      .filter(([, value]) => value));
  } catch {
    return {};
  }
}

function writeStoredContactRemarks(ownerId: string | number | null | undefined, remarks: Record<string, string>) {
  try {
    globalThis.localStorage?.setItem(contactRemarksStorageKey(ownerId), JSON.stringify(remarks));
  } catch {
    // 本地存储不可写时只影响刷新后的备注保留。
  }
}

function todayDateString() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function isDateString(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value);
}

function isDateTimeMinuteString(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value);
}

function datePart(value: string) {
  return value.slice(0, 10);
}

function formatTodoRange(startAt: string, endAt: string) {
  const start = startAt.replace('T', ' ');
  const end = endAt.replace('T', ' ');
  if (datePart(startAt) === datePart(endAt)) return `${start} - ${endAt.slice(11)}`;
  return `${start} 至 ${end}`;
}

function normalizeTodoColor(value: unknown): TodoTagColor {
  if (['blue', 'green', 'amber', 'rose', 'teal', 'gray'].includes(String(value))) {
    return value as TodoTagColor;
  }
  return 'blue';
}

function createTodoRecord(input: Partial<TodoRecord> & { title: string }): TodoRecord {
  const fallbackStartAt = `${isDateString(input.startDate) ? input.startDate : todayDateString()}T09:00`;
  const startAt = isDateTimeMinuteString(input.startAt) ? input.startAt : fallbackStartAt;
  const fallbackEndDate = isDateString(input.endDate) && input.endDate >= datePart(startAt) ? input.endDate : datePart(startAt);
  const fallbackEndAt = `${fallbackEndDate}T${fallbackEndDate === datePart(startAt) ? '18:00' : '18:00'}`;
  const endAt = isDateTimeMinuteString(input.endAt) && input.endAt >= startAt ? input.endAt : (fallbackEndAt >= startAt ? fallbackEndAt : startAt);
  const startDate = datePart(startAt);
  const endDate = datePart(endAt);
  const tagName = typeof input.tagName === 'string' && input.tagName.trim()
    ? input.tagName.trim().slice(0, 12)
    : '工作';
  const note = typeof input.note === 'string' ? input.note.trim().slice(0, 300) : '';
  const completed = Boolean(input.completed);
  return {
    id: input.id || `todo-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
    title: input.title.trim().slice(0, 60),
    subtitle: formatTodoRange(startAt, endAt),
    note,
    time: startAt.replace('T', ' '),
    status: completed ? 'DONE' : 'OPEN',
    priority: tagName.slice(0, 1),
    requestNo: '',
    taskId: '',
    currentStepName: completed ? '已完成' : '待处理',
    summary: note || input.title.trim().slice(0, 60),
    fields: [],
    timeline: [],
    actions: [],
    startDate,
    endDate,
    startAt,
    endAt,
    tagName,
    tagColor: normalizeTodoColor(input.tagColor),
    completed,
    createdAt: input.createdAt || new Date().toISOString(),
  };
}

function readStoredTodos(): TodoRecord[] {
  try {
    const raw = globalThis.localStorage?.getItem(todoStorageKey);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item): item is Partial<TodoRecord> & { title: string } => (
        Boolean(item)
        && typeof item === 'object'
        && typeof (item as Partial<TodoRecord>).title === 'string'
        && Boolean((item as Partial<TodoRecord>).title?.trim())
      ))
      .map(createTodoRecord)
      .slice(0, 200);
  } catch {
    return [];
  }
}

function writeStoredTodos(todos: TodoRecord[]) {
  try {
    globalThis.localStorage?.setItem(todoStorageKey, JSON.stringify(todos.slice(0, 200)));
  } catch {
    return;
  }
}

function buildContactCategoryItems(deptTree: OrgDepartmentTreeNodeResponse[]): ContactCategoryItem[] {
  return deptTree.map((node) => ({
    id: `dept-${node.id}`,
    type: 'dept' as const,
    label: node.deptName,
    icon: 'folder',
    deptId: node.id,
    deptName: node.deptName,
    children: node.children?.length ? buildContactCategoryItems(node.children) : undefined,
  }));
}

const emptyConversation: ConversationRecord = {
  id: '',
  kind: 'team',
  tone: 'gray',
  badge: '',
  title: '暂无会话',
  subtitle: '',
  meta: '',
  tag: '',
  unread: 0,
  flow: '会话',
  status: '',
  pinned: false,
  muted: false,
  unreadLabel: '',
  summaryTitle: '',
  summaryBody: '',
  cards: [],
  workflowSteps: [],
  messages: [],
  panel: {
    members: [],
    tasks: [],
    attachments: [],
    auditTrail: [],
    agentEvidence: [],
  },
};

const emptyTodo: TodoRecord = {
  id: '',
  title: '暂无待办',
  subtitle: '',
  note: '',
  time: '',
  status: '',
  priority: '',
  requestNo: '',
  taskId: '',
  currentStepName: '',
  summary: '',
  fields: [],
  timeline: [],
  actions: [],
};

function createFileSpaces(files: PersonalFileRecord[] = []): FileSpaceRecord[] {
  return [{
    id: 'personal',
    title: '云空间',
    subtitle: '个人空间',
    note: '',
    usage: 0,
    label: '个人',
    summary: [['0B', '已用空间'], ['0B', '个人配额'], ['可上传', '上传状态']],
    files,
  }];
}

const fileSpaces = createFileSpaces();

const workbenchItems: WorkbenchRecord[] = [
  {
    id: 'today',
    title: '今日',
    label: 'today',
    subtitle: '今日考勤 / 申请 / 待办 / 假期一览',
    note: '',
    tone: 'blue',
    status: '可用',
    fields: [],
    stats: [],
    steps: [],
  },
  {
    id: 'oa',
    title: 'OA 申请',
    label: 'oa',
    subtitle: '请假 / 补卡 / 报销 / 出差 / 加班',
    note: '',
    tone: 'blue',
    status: '可用',
    fields: [],
    stats: [],
    steps: [],
  },
  {
    id: 'attendance',
    title: '我的考勤',
    label: 'attendance',
    subtitle: '考勤结果',
    note: '',
    tone: 'amber',
    status: '',
    fields: [],
    stats: [],
    steps: [],
  },
  {
    id: 'balance',
    title: '我的假期',
    label: 'balance',
    subtitle: '假期余额',
    note: '',
    tone: 'green',
    status: '',
    fields: [],
    stats: [],
    steps: [],
  },
];

const contactItems: ContactRecord[] = [];

const profileItems: ProfileRecord[] = [
  {
    id: 'profile',
    title: '个人资料',
    subtitle: '身份 / 组织 / 账号 / 偏好',
    note: '',
    tone: 'blue',
    stats: [],
    fields: [
      ['身份状态', '姓名 任职状态 岗位 入职日期'],
      ['组织状态', '所属部门 直属上级 直属下级'],
      ['账号状态', '手机号 绑定邮箱 修改密码'],
      ['偏好状态', '在线状态 主题 消息密度'],
    ],
  },
  {
    id: 'organization',
    title: '组织归属',
    subtitle: '部门 / 上级 / 下级',
    note: '',
    tone: 'green',
    stats: [],
    fields: [
      ['组织关系', '所属部门 岗位 直属上级 直属下级 组织链路'],
    ],
  },
  {
    id: 'security',
    title: '账号安全',
    subtitle: '手机号 / 邮箱 / 密码',
    note: '',
    tone: 'amber',
    stats: [],
    fields: [
      ['账号安全', '登录名 手机号 绑定邮箱 换绑邮箱 修改密码 退出登录'],
    ],
  },
  {
    id: 'preferences',
    title: '工作偏好',
    subtitle: '状态 / 外观 / 通知',
    note: '',
    tone: 'gray',
    stats: [],
    fields: [
      ['偏好设置', '工作状态 通知设置 界面设置 消息样式 在线 忙碌 离开 免打扰'],
    ],
  },
];

const SETTINGS_ALIAS: Record<string, string> = {
  center: 'profile',
};

export function normalizeSettingsPanel(id?: string | null): string {
  if (!id) return 'profile';
  if (SETTINGS_ALIAS[id]) return SETTINGS_ALIAS[id];
  return profileItems.some((item) => item.id === id) ? id : 'profile';
}

const WORKBENCH_ALIAS: Record<string, string> = {
  agent: 'oa',
  files: 'oa',
  leave: 'oa',
  requests: 'oa',
};

const RESERVED_WORKBENCH_IDS = new Set(['today', 'oa', 'attendance', 'balance', 'agent', 'files', 'leave', 'requests']);

export function normalizeWorkbenchApp(id?: string | null, list?: WorkbenchRecord[]): string {
  const items = list ?? workbenchItems;
  if (!id) return items[0]?.id ?? 'oa';
  if (WORKBENCH_ALIAS[id]) return WORKBENCH_ALIAS[id];
  return items.some((item) => item.id === id) ? id : (items[0]?.id ?? 'oa');
}

export function resolveWorkbenchRoute(
  candidate: string | undefined,
  initialized: boolean,
  authenticated: boolean,
  pluginsResolved: boolean,
  workbenchIds: string[],
): { id: string; replace: boolean; pending: boolean } {
  if (!candidate) return { id: 'today', replace: false, pending: false };
  if (!initialized) return { id: candidate, replace: false, pending: true };
  if (!authenticated) return { id: 'today', replace: candidate !== 'today', pending: false };
  if (WORKBENCH_ALIAS[candidate]) return { id: 'oa', replace: true, pending: false };
  if (workbenchIds.includes(candidate)) return { id: candidate, replace: false, pending: false };
  if (!pluginsResolved) return { id: candidate, replace: false, pending: true };
  return { id: 'today', replace: true, pending: false };
}

function buildWorkbenchList(manifest: { id: string; name: string }[], loadedIds: string[]): WorkbenchRecord[] {
  const loaded = new Set(loadedIds);
  const seen = new Set(workbenchItems.map((item) => item.id));
  const pluginItems: WorkbenchRecord[] = [];
  for (const p of manifest) {
    if (RESERVED_WORKBENCH_IDS.has(p.id)) continue;
    if (!loaded.has(p.id)) continue;
    if (seen.has(p.id)) continue;
    seen.add(p.id);
    pluginItems.push({
      id: p.id,
      title: p.name,
      label: p.id,
      subtitle: '',
      note: '',
      tone: 'gray',
      status: '',
      fields: [],
      stats: [],
      steps: [],
    });
  }
  return [...workbenchItems, ...pluginItems];
}

function findById<T extends { id: string }>(items: T[], id: string, fallback: T) {
  return items.find((item) => item.id === id) ?? items[0] ?? fallback;
}

function includesKeyword(values: Array<string | number | undefined>, keyword: string) {
  if (!keyword) return true;
  const normalized = keyword.toLowerCase();
  return values.some((value) => String(value ?? '').toLowerCase().includes(normalized));
}

function displayValue(value: unknown) {
  if (typeof value === 'number') return String(value);
  if (typeof value === 'string' && value.trim()) return value;
  return '暂无';
}

function statusValue(value?: string | null) {
  if (value === 'ACTIVE' || value === 'IN_SERVICE') return '在职';
  if (value === 'DISABLED') return '停用';
  if (value === 'RESIGNED') return '离职';
  return displayValue(value);
}

function fileStatusValue(value?: string | null) {
  if (value === 'PENDING') return '待处理';
  if (value === 'READY' || value === 'CLEAN') return '已确认';
  if (value === 'REJECTED' || value === 'INFECTED') return '异常';
  return statusValue(value);
}

function formatDisplayDateTime(value?: string | null) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function mapEmployeeFile(file: EmployeeFileResponse): PersonalFileRecord {
  const name = file.displayName || file.originalFileName || '未命名文件';
  return {
    id: `personal-file-${file.attachmentRefId}`,
    name,
    type: fileTypeLabelFromName(name, file.mimeType ?? ''),
    size: fileSizeLabel(file.fileSize),
    createdAt: formatDisplayDateTime(file.createdAt) || '刚刚',
    rawCreatedAt: file.createdAt ?? undefined,
    fileSizeBytes: file.fileSize,
    status: file.deletedAt ? '回收站' : fileStatusValue(file.scanStatus),
    attachmentRefId: file.attachmentRefId,
    folderId: file.folderId ?? null,
    mimeType: file.mimeType ?? undefined,
  };
}

function isPendingStatus(status: string) {
  return ['PENDING', 'OPEN', 'IN_APPROVAL', 'ABNORMAL'].includes(status);
}

export function todoLifecycle(todo: TodoRecord, now = new Date()) {
  if (todo.completed || todo.status === 'DONE') return '已完成';
  const nowKey = toDateTimeMinuteString(now);
  if (todo.startAt && todo.startAt > nowKey) return '未开始';
  return '进行中';
}

function toDateTimeMinuteString(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function matchesTodoFilter(item: TodoRecord, filter: SectionFilter) {
  if (filter === '全部') return true;
  if (filter === '进行中' || filter === '已完成' || filter === '未开始') return todoLifecycle(item) === filter;
  return true;
}

function matchesConversationFilter(item: ConversationRecord, filter: SectionFilter) {
  if (filter === '全部') return true;
  if (filter === '未处理') return item.unread > 0 || isPendingStatus(item.status);
  if (filter === 'AI') return item.kind === 'agent' || item.title.includes('AI') || item.tag.includes('AI');
  if (filter === '群聊') return item.kind === 'team';
  if (filter === '私聊') return item.kind === 'approval' || item.kind === 'attendance' || item.kind === 'file' || item.kind === 'private';
  return true;
}

export const useShellStore = defineStore('employee-shell', {
  state: () => {
    const storedPreferences = readStoredPreferences();
    return {
    section: 'messages' as SectionId,
    detailOpen: false,
    mobileView: 'list' as 'list' | 'detail',
    listWidth: storedPreferences.listWidth,
    theme: storedPreferences.theme,
    messageDensity: storedPreferences.messageDensity,
    signature: storedPreferences.signature,
    presenceStatus: storedPreferences.presenceStatus,
    notifSound: storedPreferences.notifSound,
    notifDesktop: storedPreferences.notifDesktop,
    notifMentionOnly: storedPreferences.notifMentionOnly,
    notifUnreadBadge: storedPreferences.notifUnreadBadge,
    accentColor: storedPreferences.accentColor,
    fontScale: storedPreferences.fontScale,
    bubbleStyle: storedPreferences.bubbleStyle,
    search: {
      messages: '',
      todos: '',
      files: '',
      workbench: '',
      processes: '',
      contacts: '',
      agents: '',
      reports: '',
      settings: '',
    },
    filters: {
      messages: '全部' as SectionFilter,
      todos: '进行中' as SectionFilter,
      files: '全部' as SectionFilter,
      workbench: '全部' as SectionFilter,
      processes: '全部' as SectionFilter,
      contacts: '全部' as SectionFilter,
      agents: '全部' as SectionFilter,
      reports: '全部' as SectionFilter,
      settings: '全部' as SectionFilter,
    },
    active: {
      messages: '',
      todos: '',
      files: 'personal',
      workbench: 'today',
      processes: 'requests',
      contacts: '',
      agents: '',
      reports: 'overview',
      settings: 'profile',
    },
    todos: readStoredTodos(),
    files: createFileSpaces(),
    personalFileOwnerId: '',
    workbench: workbenchItems,
    contacts: contactItems,
    contactRemarks: readStoredContactRemarks('') as Record<string, string>,
    friends: [] as OrgUserSummaryResponse[],
    deptTree: [] as OrgDepartmentTreeNodeResponse[],
    contactsViewMode: 'list' as ContactsViewMode,
    contactsCategoryId: 'friends' as string,
    contactsExpandedCategoryIds: new Set(['enterprise']) as Set<string>,
    profile: profileItems,
    colleagues: [] as OrgUserSummaryResponse[],
    avatarUrl: '' as string,
    };
  },
  getters: {
    conversations(): ConversationRecord[] {
      return useImStore().conversations;
    },
    totalUnread(): number {
      return this.conversations.reduce((sum, item) => sum + item.unread, 0);
    },
    pendingTodoCount(state): number {
      return state.todos.filter((item) => isPendingStatus(item.status)).length;
    },
    activeConversation(state): ConversationRecord {
      return findById(this.conversations, state.active.messages, emptyConversation);
    },
    activeTodo(state): TodoRecord {
      return findById(state.todos, state.active.todos, emptyTodo);
    },
    activeFile(state): FileSpaceRecord {
      return findById(state.files, state.active.files, fileSpaces[0]);
    },
    activeWorkbench(state): WorkbenchRecord {
      return findById(state.workbench, state.active.workbench, workbenchItems[0]);
    },
    activeContact(state): ContactRecord | null {
      return state.contacts.find((item) => item.id === state.active.contacts) ?? state.contacts[0] ?? null;
    },
    activeProfile(state): ProfileRecord {
      return findById(state.profile, state.active.settings, profileItems[0]);
    },
    currentMeta(state): SectionMeta {
      return sectionMeta[state.section];
    },
    filteredConversations(state): ConversationRecord[] {
      return this.conversations.filter((item) => matchesConversationFilter(item, state.filters.messages))
        .filter((item) => includesKeyword([
          item.title,
          item.subtitle,
          item.tag,
          item.flow,
          item.status,
          item.panel.members.map((member) => `${member.name} ${member.role}`).join(' '),
          item.panel.attachments.map((file) => file.name).join(' '),
        ], state.search.messages))
        .sort((a, b) => Number(Boolean(b.pinned)) - Number(Boolean(a.pinned)));
    },
    filteredTodos(state): TodoRecord[] {
      return [...state.todos]
        .filter((item) => matchesTodoFilter(item, state.filters.todos))
        .filter((item) => includesKeyword([
          item.title,
          item.subtitle,
          item.note,
          item.status,
          item.priority,
          item.requestNo,
          item.taskId,
          item.currentStepName,
          item.startDate,
          item.endDate,
          item.startAt,
          item.endAt,
          item.tagName,
        ], state.search.todos))
        .sort((a, b) => String(a.startAt ?? a.startDate ?? '').localeCompare(String(b.startAt ?? b.startDate ?? ''))
          || String(a.createdAt ?? '').localeCompare(String(b.createdAt ?? '')));
    },
    filteredFiles(state): FileSpaceRecord[] {
      return state.files.filter((item) => includesKeyword([
        item.title,
        item.subtitle,
        item.note,
        item.label,
        item.files.map((file) => [file.name, file.type, file.size, file.status].join(' ')).join(' '),
      ], state.search.files));
    },
    filteredWorkbench(state): WorkbenchRecord[] {
      return state.workbench
        .filter((item) => includesKeyword([
          item.title,
          item.subtitle,
          item.note,
          item.status,
          item.label,
        ], state.search.workbench));
    },
    filteredProfile(state): ProfileRecord[] {
      return state.profile.filter((item) => includesKeyword([
        item.title,
        item.subtitle,
        item.note,
        item.tone,
        item.fields.map((field) => field.join(' ')).join(' '),
      ], state.search.settings));
    },
    filteredContacts(state): ContactRecord[] {
      return state.contacts.filter((item) => includesKeyword([
        item.name,
        item.originalName,
        item.remarkName,
        item.role,
        item.department,
        item.relation,
        item.source,
      ], state.search.contacts));
    },
    friendUserIds(state): Set<string> {
      return new Set(state.friends.map((item) => String(item.userId)));
    },
    contactsCategoryItems(state): ContactCategoryItem[] {
      const deptCategories = buildContactCategoryItems(state.deptTree).map((item) => ({ ...item, type: 'dept' as const }));
      return [
        { id: 'friends', type: 'category', label: '我的好友', icon: 'users' },
        { id: 'new-friends', type: 'category', label: '新的好友', icon: 'userPlus' },
        { id: 'invitations', type: 'category', label: '企业/团队邀请', icon: 'mail' },
        { id: 'create-join-org', type: 'category', label: '创建或加入企业/团队', icon: 'building' },
        {
          id: 'enterprise',
          type: 'category',
          label: '企业内',
          icon: 'building',
          children: [
            { id: 'org-structure', type: 'category', label: '组织架构', icon: 'sitemap' },
            ...deptCategories,
          ],
        },
        { id: 'ai', type: 'ai', label: 'AI 助手', icon: 'sparkles' },
        {
          id: 'enterprise-service',
          type: 'category',
          label: '企业服务',
          icon: 'briefcase',
          children: [
            { id: 'service-window', type: 'service', label: '服务窗', icon: 'monitor' },
            { id: 'customer-management', type: 'service', label: '客户管理', icon: 'briefcase' },
          ],
        },
      ];
    },
  },
  actions: {
    setSection(section: SectionId) {
      this.section = section;
    },
    setSearch(section: SectionId, value: string) {
      this.search[section] = value;
    },
    setFilter(section: SectionId, filter: SectionFilter) {
      this.filters[section] = filter;
    },
    selectItem(section: SectionId, id: string) {
      let normalized = id;
      if (section === 'settings') normalized = normalizeSettingsPanel(id);
      else if (section === 'workbench') normalized = normalizeWorkbenchApp(id, this.workbench);
      this.active[section] = normalized;
      this.mobileView = 'detail';
      if (section === 'workbench') {
        this.detailOpen = false;
      }
      if (section === 'messages') {
        const active = this.conversations.find((item) => item.id === normalized);
        if (active) active.unread = 0;
      }
    },
    setDetailOpen(open: boolean) {
      this.detailOpen = open;
    },
    toggleDetail() {
      this.detailOpen = !this.detailOpen;
    },
    setMobileView(view: 'list' | 'detail') {
      this.mobileView = view;
    },
    setListWidth(width: number) {
      this.listWidth = Math.max(384, Math.min(520, Math.round(width)));
      this.persistPreferences();
    },
    setTheme(theme: ThemeMode) {
      this.theme = theme;
      this.persistPreferences();
    },
    toggleTheme() {
      this.theme = this.theme === 'light' ? 'dark' : 'light';
      this.persistPreferences();
    },
    setMessageDensity(density: MessageDensity) {
      this.messageDensity = density;
      this.persistPreferences();
    },
    setSignature(signature: string) {
      this.signature = signature.trim().slice(0, 140);
      this.persistPreferences();
    },
    setPresenceStatus(status: PresenceStatus) {
      this.presenceStatus = status;
      this.persistPreferences();
    },
    setNotificationPreference(field: 'notifSound' | 'notifDesktop' | 'notifMentionOnly' | 'notifUnreadBadge', value: boolean) {
      if (field === 'notifSound') this.notifSound = value;
      if (field === 'notifDesktop') this.notifDesktop = value;
      if (field === 'notifMentionOnly') this.notifMentionOnly = value;
      if (field === 'notifUnreadBadge') this.notifUnreadBadge = value;
      this.persistPreferences();
    },
    setAccentColor(color: AccentColor) {
      this.accentColor = color;
      this.persistPreferences();
    },
    setFontScale(scale: FontScale) {
      this.fontScale = scale;
      this.persistPreferences();
    },
    setBubbleStyle(style: BubbleStyle) {
      this.bubbleStyle = style;
      this.persistPreferences();
    },
    contactRemarkFor(userId: string | number | null | undefined) {
      if (userId === undefined || userId === null) return '';
      return this.contactRemarks[String(userId)] ?? '';
    },
    displayNameForUser(userId: string | number | null | undefined, fallback = '') {
      if (userId === undefined || userId === null) return fallback;
      return this.contactRemarkFor(userId) || fallback;
    },
    setContactRemark(userId: string | number, remark: string) {
      const key = String(userId);
      const value = remark.trim().slice(0, 30);
      const next = { ...this.contactRemarks };
      if (value) next[key] = value;
      else delete next[key];
      this.contactRemarks = next;
      writeStoredContactRemarks(this.personalFileOwnerId, next);
      this.applyContactRemarks();
    },
    applyContactRemarks() {
      this.contacts = this.contacts.map((contact) => applyContactRemark(contact, this.contactRemarks));
      const im = useImStore();
      im.conversations = im.conversations.map((conversation) => applyConversationRemarks(conversation, this.contactRemarks, this.personalFileOwnerId));
    },
    currentPreferencePayload(): StoredPreferences {
      return {
        theme: this.theme,
        messageDensity: this.messageDensity,
        listWidth: this.listWidth,
        signature: this.signature,
        presenceStatus: this.presenceStatus,
        notifSound: this.notifSound,
        notifDesktop: this.notifDesktop,
        notifMentionOnly: this.notifMentionOnly,
        notifUnreadBadge: this.notifUnreadBadge,
        accentColor: this.accentColor,
        fontScale: this.fontScale,
        bubbleStyle: this.bubbleStyle,
      };
    },
    persistPreferences() {
      writeStoredPreferences(this.currentPreferencePayload());
    },
    hydratePreferencesFromServer(prefs: Partial<StoredPreferences>) {
      const normalized = normalizePreferenceInput(prefs);
      this.theme = normalized.theme;
      this.messageDensity = normalized.messageDensity;
      this.listWidth = normalized.listWidth;
      this.signature = normalized.signature;
      this.presenceStatus = normalized.presenceStatus;
      this.notifSound = normalized.notifSound;
      this.notifDesktop = normalized.notifDesktop;
      this.notifMentionOnly = normalized.notifMentionOnly;
      this.notifUnreadBadge = normalized.notifUnreadBadge;
      this.accentColor = normalized.accentColor;
      this.fontScale = normalized.fontScale;
      this.bubbleStyle = normalized.bubbleStyle;
      this.persistPreferences();
    },
    applyServerPreferences(preferences: Partial<StoredPreferences>) {
      this.hydratePreferencesFromServer(preferences);
    },
    setColleagues(rows: OrgUserSummaryResponse[]) {
      this.colleagues = rows ?? [];
    },
    rebuildWorkbench(manifest: { id: string; name: string }[], loadedIds: string[]) {
      this.workbench = buildWorkbenchList(manifest, loadedIds);
      if (!this.workbench.some((item) => item.id === this.active.workbench)) {
        this.active.workbench = this.workbench[0]?.id ?? 'oa';
      }
    },
    setFriends(rows: OrgUserSummaryResponse[]) {
      this.friends = rows ?? [];
    },
    setDeptTree(tree: OrgDepartmentTreeNodeResponse[]) {
      this.deptTree = tree ?? [];
    },
    setContactsViewMode(mode: ContactsViewMode) {
      this.contactsViewMode = mode;
    },
    setContactsCategoryId(id: string) {
      this.contactsCategoryId = id;
    },
    setContactsExpandedCategoryIds(ids: Set<string> | string[]) {
      this.contactsExpandedCategoryIds = new Set(ids);
    },
    toggleContactsExpandedCategoryId(id: string) {
      const next = new Set(this.contactsExpandedCategoryIds);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      this.contactsExpandedCategoryIds = next;
    },
    setAvatarUrl(url: string | null | undefined) {
      this.avatarUrl = url ?? '';
      const ownerId = this.personalFileOwnerId;
      const ownContact = this.contacts.find((item) => item.userId != null && String(item.userId) === ownerId);
      if (ownContact) ownContact.avatarUrl = this.avatarUrl;
    },
    syncSessionProfile(
      user: CurrentUserResponse | null,
      profile: OrgUserDetailResponse | null,
      manager: OrgUserSummaryResponse | null,
      subordinates: OrgUserSummaryResponse[],
    ) {
      const ownerId = String(profile?.userId ?? user?.userId ?? '');
      if (ownerId !== this.personalFileOwnerId) {
        this.personalFileOwnerId = ownerId;
        this.contactRemarks = readStoredContactRemarks(ownerId);
        const personal = this.files.find((item) => item.id === 'personal');
        if (personal) personal.files = readStoredPersonalFiles(ownerId);
      }
      this.profile = profileItems;
      if (!this.profile.some((item) => item.id === this.active.settings)) {
        this.active.settings = this.profile[0]?.id ?? 'profile';
      }
      this.contacts = collectContacts({
        user,
        profile,
        manager,
        colleagues: this.colleagues,
        subordinates,
        friends: this.friends,
        signature: this.signature,
        presenceStatus: this.presenceStatus,
      }).map((contact) => applyContactRemark(contact, this.contactRemarks));
      this.applyContactRemarks();
      if (!this.contacts.some((item) => item.id === this.active.contacts)) {
        this.active.contacts = this.contacts[0]?.id ?? '';
      }
    },
    syncFileQuota(quota: QuotaResponse) {
      const personal = this.files.find((item) => item.id === 'personal');
      if (!personal) return;
      const used = fileSizeLabel(quota.personalUsedBytes);
      const total = fileSizeLabel(quota.personalQuotaBytes);
      personal.subtitle = `个人空间 · ${used} / ${total}`;
      personal.usage = Math.max(0, Math.min(100, Math.round(Number(quota.personalUsagePercent ?? 0))));
      personal.summary = [
        [used, '已用空间'],
        [total, '个人配额'],
        [quota.uploadWritable ? '可上传' : statusValue(quota.blockReason), '上传状态'],
      ];
      if (!quota.uploadWritable && quota.blockReason) {
        personal.note = quota.blockReason;
      } else {
        personal.note = '';
      }
    },
    mergeBackendPersonalFiles(activeFiles: EmployeeFileResponse[], recycleBinFiles: EmployeeFileResponse[] = []) {
      const personal = this.files.find((item) => item.id === 'personal');
      if (!personal) return;
      const rows = [...activeFiles, ...recycleBinFiles].map(mapEmployeeFile);
      personal.files = rows.slice(0, 24);
      writeStoredPersonalFiles(personal.files, this.personalFileOwnerId);
    },
    addPersonalFile(file: {
      name: string;
      type: string;
      size: string;
      status: string;
      attachmentRefId?: EntityId;
      folderId?: EntityId | null;
      mimeType?: string;
      downloadUrl?: string;
      createdAt?: string;
      rawCreatedAt?: string;
      fileSizeBytes?: number;
    }) {
      const personal = this.files.find((item) => item.id === 'personal');
      if (!personal) return;
      const id = file.attachmentRefId ? `personal-file-${file.attachmentRefId}` : createClientId(`personal-file-${file.name}`);
      const row: PersonalFileRecord = {
        id,
        name: file.name,
        type: file.type,
        size: file.size,
        createdAt: file.createdAt ?? '刚刚',
        rawCreatedAt: file.rawCreatedAt,
        fileSizeBytes: file.fileSizeBytes,
        status: file.status,
        attachmentRefId: file.attachmentRefId,
        folderId: file.folderId ?? null,
        mimeType: file.mimeType,
        downloadUrl: file.downloadUrl,
      };
      personal.files = [row, ...personal.files]
        .filter((item, index, rows) => rows.findIndex((next) => (
          (row.attachmentRefId && next.attachmentRefId === row.attachmentRefId)
          || (!row.attachmentRefId && next.name === row.name && next.createdAt === row.createdAt)
        )) === index)
        .slice(0, 24);
      writeStoredPersonalFiles(personal.files, this.personalFileOwnerId);
    },
    removePersonalFile(attachmentRefId: EntityId) {
      const personal = this.files.find((item) => item.id === 'personal');
      if (!personal) return;
      personal.files = personal.files.filter((item) => item.attachmentRefId !== attachmentRefId);
      writeStoredPersonalFiles(personal.files, this.personalFileOwnerId);
    },
    updatePersonalFile(attachmentRefId: EntityId, patch: Partial<PersonalFileRecord>) {
      const personal = this.files.find((item) => item.id === 'personal');
      if (!personal) return;
      personal.files = personal.files.map((item) => (
        item.attachmentRefId === attachmentRefId ? { ...item, ...patch } : item
      ));
      writeStoredPersonalFiles(personal.files, this.personalFileOwnerId);
    },
    addTodoTask(input: Pick<TodoRecord, 'title' | 'startAt' | 'endAt' | 'note' | 'tagName' | 'tagColor'>) {
      const record = createTodoRecord({ ...input, title: input.title });
      this.todos = [...this.todos, record];
      this.active.todos = record.id;
      writeStoredTodos(this.todos);
      return record;
    },
    updateTodoTask(id: string, input: Pick<TodoRecord, 'title' | 'startAt' | 'endAt' | 'note' | 'tagName' | 'tagColor'>) {
      this.todos = this.todos.map((item) => {
        if (item.id !== id) return item;
        return {
          ...item,
          title: input.title,
          note: input.note,
          startAt: input.startAt,
          endAt: input.endAt,
          tagName: input.tagName,
          tagColor: input.tagColor,
        };
      });
      writeStoredTodos(this.todos);
    },
    toggleTodoTask(id: string) {
      this.todos = this.todos.map((item) => {
        if (item.id !== id) return item;
        const completed = !item.completed;
        return {
          ...item,
          completed,
          status: completed ? 'DONE' : 'OPEN',
          currentStepName: completed ? '已完成' : '待处理',
        };
      });
      writeStoredTodos(this.todos);
    },
    reloadStoredTodos() {
      this.todos = readStoredTodos();
      if (!this.todos.some((item) => item.id === this.active.todos)) {
        this.active.todos = this.todos[0]?.id ?? '';
      }
    },
    // 后端待办同步到本地，如果当前选中的待办被删了就自动切到第一条
    mergeBackendTodos(backendTodos: TodoRecord[]) {
      const backendIds = new Set(backendTodos.map((item) => item.id));
      this.todos = backendTodos;
      if (!backendIds.has(this.active.todos)) {
        this.active.todos = this.todos[0]?.id ?? '';
      }
    },
    // 会话列表同步，顺便把本地备注名刷上去
    mergeBackendConversations(backendConversations: ConversationRecord[]) {
      const backendIds = new Set(backendConversations.map((item) => item.id));
      useImStore().conversations = backendConversations.map((conversation) => (
        applyConversationRemarks(conversation, this.contactRemarks, this.personalFileOwnerId)
      ));
      if (!backendIds.has(this.active.messages)) {
        this.active.messages = this.conversations[0]?.id ?? '';
      }
    },
    // 单个会话的消息同步，同时刷新附件列表和最后一条消息摘要
    mergeBackendMessages(conversationId: string, backendMessages: ConversationMessage[]) {
      const conversation = this.conversations.find((item) => item.id === conversationId);
      if (!conversation || !backendMessages.length) return;
      const localMessages = new Map(conversation.messages.map((message) => [message.id, message]));
      const mentionMembers = conversation.panel.members
        .filter((member) => member.userId !== undefined && member.userId !== null)
        .map((member) => ({
          userId: member.userId as EntityId,
          name: member.originalName || member.name,
          originalName: member.originalName || member.name,
          role: member.role,
        }));
      const nextMessages = backendMessages.map((message) => ({
        ...message,
        mentions: message.mentions ?? localMessages.get(message.id)?.mentions ?? mentionMembers.filter((member) => (
          hasMentionToken(message.body, member.originalName || member.name)
        )),
      }));
      conversation.messages = applyMessageRemarks(nextMessages, conversation, this.contactRemarks);
      conversation.panel.attachments = collectConversationAttachments(backendMessages);
      const latestMessage = conversation.messages[conversation.messages.length - 1];
      conversation.subtitle = latestMessage.body;
      conversation.meta = formatListDate(latestMessage.time) || conversation.meta;
    },
    appendMessage(text: string) {
      const conversation = this.activeConversation;
      conversation.messages.push({
        id: createClientId(conversation.id),
        author: currentUser.name,
        role: '申请人',
        body: text,
        side: 'right',
      });
      conversation.subtitle = text;
      conversation.meta = '刚刚';
      conversation.unread = 0;
    },
  },
});

export const shellUser = currentUser;

function formatListDate(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

function collectConversationAttachments(messages: ConversationMessage[]) {
  const byId = new Map<string, ConversationAttachment>();
  for (const message of messages) {
    for (const attachment of message.attachments ?? []) {
      const key = attachment.attachmentRefId == null ? attachment.id : String(attachment.attachmentRefId);
      byId.set(key, attachment);
    }
  }
  return Array.from(byId.values());
}

function contactFromOrgUser(user: OrgUserSummaryResponse, relation: string, source: string): ContactRecord {
  return {
    id: `user-${user.userId}`,
    name: user.realName,
    originalName: user.realName,
    role: displayValue(user.positionName),
    department: displayValue(user.primaryDeptName),
    primaryDeptId: user.primaryDeptId,
    relation,
    source,
    userId: user.userId,
    avatarAttachmentRefId: user.avatarAttachmentRefId,
    employeeNo: user.employeeNo,
    status: user.status,
    employmentStatus: user.employmentStatus,
    managerName: user.managerName,
  };
}

interface CollectContactsInput {
  user: CurrentUserResponse | null;
  profile: OrgUserDetailResponse | null;
  manager: OrgUserSummaryResponse | null;
  colleagues: OrgUserSummaryResponse[];
  subordinates: OrgUserSummaryResponse[];
  friends?: OrgUserSummaryResponse[];
  signature: string;
  presenceStatus: PresenceStatus;
}

function collectContacts(input: CollectContactsInput): ContactRecord[] {
  const { user, profile, manager, colleagues, subordinates, friends, signature, presenceStatus } = input;
  const contacts = new Map<string, ContactRecord>();
  const add = (contact: ContactRecord) => {
    if (contact.name && contact.name !== '暂无') {
      contacts.set(contact.id, contact);
    }
  };
  if (profile || user) {
    const userId = profile?.userId ?? user?.userId;
    if (userId) {
      add({
        id: `user-${userId}`,
        name: profile?.realName || user?.displayName || currentUser.name,
        originalName: profile?.realName || user?.displayName || currentUser.name,
        role: displayValue(profile?.positionName),
        department: displayValue(profile?.primaryDeptName),
        primaryDeptId: profile?.primaryDeptId ?? user?.primaryDeptId,
        relation: '本人',
        source: '个人资料',
        userId,
        avatarAttachmentRefId: profile?.avatarAttachmentRefId,
        avatarUrl: '',
        employeeNo: profile?.employeeNo,
        status: profile?.status ?? null,
        employmentStatus: profile?.employmentStatus ?? user?.employmentStatus ?? null,
        signature,
        presenceStatus,
        managerName: profile?.managerName,
      });
    }
  }
  if (manager) add(contactFromOrgUser(manager, '直属上级', '组织关系'));
  colleagues.forEach((item) => add(contactFromOrgUser(item, '同部门', '组织通讯录')));
  subordinates.forEach((item) => add(contactFromOrgUser(item, '直属下级', '组织关系')));
  friends?.forEach((item) => add(contactFromOrgUser(item, '好友', '我的好友')));
  return [...contacts.values()];
}

function applyContactRemark(contact: ContactRecord, remarks: Record<string, string>): ContactRecord {
  if (contact.userId === undefined || contact.userId === null) return contact;
  const originalName = contact.originalName || contact.name;
  const remarkName = remarks[String(contact.userId)] || '';
  return {
    ...contact,
    originalName,
    remarkName,
    name: remarkName || originalName,
  };
}

function applyConversationRemarks(conversation: ConversationRecord, remarks: Record<string, string>, currentUserId?: string | number | null): ConversationRecord {
  const panel = {
    ...conversation.panel,
    members: conversation.panel.members.map((member) => {
      if (member.userId === undefined || member.userId === null) return member;
      const originalName = member.originalName || member.name;
      return {
        ...member,
        originalName,
        name: remarks[String(member.userId)] || originalName,
      };
    }),
  };
  const next: ConversationRecord = { ...conversation, panel };
  if (next.kind === 'private') {
    const target = panel.members.find((member) => (
      member.userId !== undefined && member.userId !== null && String(member.userId) !== String(currentUserId ?? '')
    )) ?? panel.members.find((member) => member.userId !== undefined && member.userId !== null);
    if (target?.userId !== undefined && target.userId !== null) {
      const title = remarks[String(target.userId)] || target.originalName || target.name;
      next.title = title;
      next.summaryTitle = title;
      next.badge = title.slice(0, 2);
    }
  }
  next.messages = applyMessageRemarks(next.messages, next, remarks);
  return next;
}

function applyMessageRemarks(messages: ConversationMessage[], conversation: ConversationRecord, remarks: Record<string, string>) {
  return messages.map((message) => {
    if (message.senderType !== 'USER' || message.senderId === undefined || message.senderId === null || message.side === 'right') {
      return message;
    }
    const member = conversation.panel.members.find((item) => (
      item.userId !== undefined && item.userId !== null && String(item.userId) === String(message.senderId)
    ));
    const originalAuthor = message.originalAuthor || member?.originalName || member?.name || message.author;
    const remark = remarks[String(message.senderId)] || '';
    return {
      ...message,
      originalAuthor,
      author: remark ? (conversation.kind === 'team' ? `${remark} @ ${originalAuthor}` : remark) : originalAuthor,
    };
  });
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function hasMentionToken(text: string, name: string) {
  return new RegExp(`(^|[\\s，。,.!?！？、:：；;])@${escapeRegExp(name)}(?=$|[\\s，。,.!?！？、:：；;])`).test(text);
}
