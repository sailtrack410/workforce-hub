import { defineStore } from 'pinia';
import type {
  CurrentUserResponse,
  EmployeeFileResponse,
  EntityId,
  OrgUserDetailResponse,
  OrgUserSummaryResponse,
  QuotaResponse,
} from '../api';

export type SectionId = 'messages' | 'todos' | 'files' | 'workbench' | 'processes' | 'contacts' | 'reports' | 'settings';
export type ConversationTone = 'blue' | 'green' | 'amber' | 'red' | 'gray';
export type SectionFilter = '全部' | '未处理' | 'Agent' | '群聊' | '私聊';
export type ThemeMode = 'light' | 'dark';
export type MessageDensity = 'cozy' | 'compact';

export interface SectionAction {
  label: string;
  action: 'switch-section' | 'switch-item' | 'open-workbench' | 'upload-personal-file' | 'approve-todo' | 'reject-todo';
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
  type?: 'text' | 'process-card';
  author: string;
  role: string;
  body: string;
  side?: 'left' | 'right' | 'system';
  time?: string;
  seqNo?: number;
  senderType?: string;
  status?: string;
  attachments?: ConversationAttachment[];
  processCard?: ConversationProcessCard;
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
  members: Array<{ name: string; role: string; userId?: string | number; memberType?: string }>;
  tasks: string[];
  attachments: ConversationAttachment[];
  auditTrail?: ConversationAuditRecord[];
  agentEvidence?: ConversationEvidence[];
}

export interface ConversationRecord {
  id: string;
  kind: 'approval' | 'team' | 'agent' | 'attendance' | 'file';
  tone: ConversationTone;
  badge: string;
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
  status: string;
  attachmentRefId?: EntityId;
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
  role: string;
  department: string;
  relation: string;
  source: string;
  userId?: string | number;
}

export const sectionMeta: Record<SectionId, SectionMeta> = {
  messages: {
    kicker: '',
    title: '消息',
    subtitle: '',
    search: '搜索会话、人员、文件',
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
    search: '搜索待办、申请号、处理人',
    primaryAction: {
      label: '刷新列表',
      action: 'switch-section',
      section: 'todos',
    },
    secondaryAction: {
      label: '打开申请',
      action: 'open-workbench',
      app: 'requests',
    },
  },
  files: {
    kicker: '',
    title: '文档',
    subtitle: '',
    search: '搜索文件、材料、状态',
    primaryAction: {
      label: '上传文件',
      action: 'upload-personal-file',
    },
  },
  workbench: {
    kicker: '',
    title: '工作台',
    subtitle: '',
    search: '搜索请假、考勤、假期、文件',
    primaryAction: {
      label: '请假申请',
      action: 'switch-item',
      item: 'leave',
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
    search: '搜索流程、审批、编号',
  },
  contacts: {
    kicker: '',
    title: '通讯录',
    subtitle: '',
    search: '搜索姓名、部门、岗位',
  },
  reports: {
    kicker: '',
    title: '报表',
    subtitle: '',
    search: '搜索报表、指标、时间',
  },
  settings: {
    kicker: '',
    title: '个人中心',
    subtitle: '',
    search: '搜索资料、账号、偏好',
  },
};

export const navItems: Array<{ id: SectionId; label: string; glyph: string }> = [
  { id: 'messages', label: '消息', glyph: '息' },
  { id: 'todos', label: '待办', glyph: '办' },
  { id: 'files', label: '文档', glyph: '档' },
  { id: 'workbench', label: '工作台', glyph: '台' },
  { id: 'contacts', label: '通讯录', glyph: '录' },
  { id: 'settings', label: '个人中心', glyph: '心' },
];

export const primaryNavItems: NavRailItem[] = navItems;

export const sectionFilters: SectionFilter[] = ['全部', '未处理', 'Agent', '群聊', '私聊'];

const currentUser = {
  name: '员工',
  shortName: '员',
  loginName: '',
  title: '',
  department: '',
};

const preferenceStorageKey = 'workforce-hub.employee.preferences.v1';
const personalFilesStoragePrefix = 'workforce-hub.employee.personal-files.v1';

type StoredPreferences = { theme: ThemeMode; messageDensity: MessageDensity; listWidth: number };

function readStoredPreferences(): StoredPreferences {
  const defaults: StoredPreferences = { theme: 'light', messageDensity: 'cozy', listWidth: 400 };
  try {
    const storage = globalThis.localStorage;
    const raw = storage?.getItem(preferenceStorageKey);
    if (!raw) return defaults;
    const parsed = JSON.parse(raw) as Partial<StoredPreferences>;
    const width = typeof parsed.listWidth === 'number' && Number.isFinite(parsed.listWidth)
      ? Math.max(384, Math.min(520, Math.round(parsed.listWidth)))
      : defaults.listWidth;
    return {
      theme: parsed.theme === 'dark' || parsed.theme === 'light' ? parsed.theme : defaults.theme,
      messageDensity: parsed.messageDensity === 'compact' || parsed.messageDensity === 'cozy'
        ? parsed.messageDensity
        : defaults.messageDensity,
      listWidth: width,
    };
  } catch {
    return defaults;
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
    title: '我的云空间',
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
    id: 'leave',
    title: '请假申请',
    label: 'leave',
    subtitle: '请假审批',
    note: '',
    tone: 'blue',
    status: '可用',
    fields: [],
    stats: [],
    steps: [],
  },
  {
    id: 'requests',
    title: '我的申请',
    label: 'requests',
    subtitle: '申请记录',
    note: '',
    tone: 'gray',
    status: '',
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
  {
    id: 'agent',
    title: 'Agent任务',
    label: 'Agent',
    subtitle: 'Agent任务',
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
    title: '个人主页',
    subtitle: '资料与组织',
    note: '',
    tone: 'blue',
    stats: [],
    fields: [],
  },
  {
    id: 'security',
    title: '账号与安全',
    subtitle: '手机号 / 邮箱 / 密码',
    note: '',
    tone: 'amber',
    stats: [],
    fields: [],
  },
  {
    id: 'preferences',
    title: '偏好设置',
    subtitle: '主题与密度',
    note: '',
    tone: 'gray',
    stats: [],
    fields: [],
  },
];

const SETTINGS_ALIAS: Record<string, string> = {
  center: 'profile',
  organization: 'profile',
  storage: 'profile',
};

export function normalizeSettingsPanel(id?: string | null): string {
  if (!id) return 'profile';
  if (SETTINGS_ALIAS[id]) return SETTINGS_ALIAS[id];
  return profileItems.some((item) => item.id === id) ? id : 'profile';
}

const WORKBENCH_ALIAS: Record<string, string> = {
  files: 'leave',
};

export function normalizeWorkbenchApp(id?: string | null): string {
  if (!id) return workbenchItems[0]?.id ?? 'leave';
  if (WORKBENCH_ALIAS[id]) return WORKBENCH_ALIAS[id];
  return workbenchItems.some((item) => item.id === id) ? id : (workbenchItems[0]?.id ?? 'leave');
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

function bytesToSize(bytes?: number | null) {
  if (!bytes || bytes <= 0) return '0B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = bytes;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  const precision = size >= 10 || unitIndex === 0 ? 0 : 1;
  return `${size.toFixed(precision)}${units[unitIndex]}`;
}

function fileTypeLabelFromName(name?: string | null, mimeType = '') {
  const ext = name?.split('.').pop()?.trim();
  if (ext && ext !== name) return ext.slice(0, 8).toUpperCase();
  if (mimeType) return mimeType.split('/').pop()?.slice(0, 8).toUpperCase() || '文件';
  return '文件';
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
    size: bytesToSize(file.fileSize),
    createdAt: formatDisplayDateTime(file.createdAt) || '刚刚',
    status: file.deletedAt ? '回收站' : fileStatusValue(file.scanStatus),
    attachmentRefId: file.attachmentRefId,
    mimeType: file.mimeType ?? undefined,
  };
}

function isPendingStatus(status: string) {
  return ['PENDING', 'OPEN', 'IN_APPROVAL', 'ABNORMAL'].includes(status);
}

function matchesConversationFilter(item: ConversationRecord, filter: SectionFilter) {
  if (filter === '全部') return true;
  if (filter === '未处理') return item.unread > 0 || isPendingStatus(item.status);
  if (filter === 'Agent') return item.kind === 'agent' || item.title.includes('Agent') || item.tag.includes('Agent');
  if (filter === '群聊') return item.kind === 'team';
  if (filter === '私聊') return item.kind === 'approval' || item.kind === 'attendance' || item.kind === 'file';
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
    search: {
      messages: '',
      todos: '',
      files: '',
      workbench: '',
      processes: '',
      contacts: '',
      reports: '',
      settings: '',
    },
    filters: {
      messages: '全部' as SectionFilter,
      todos: '全部' as SectionFilter,
      files: '全部' as SectionFilter,
      workbench: '全部' as SectionFilter,
      processes: '全部' as SectionFilter,
      contacts: '全部' as SectionFilter,
      reports: '全部' as SectionFilter,
      settings: '全部' as SectionFilter,
    },
    active: {
      messages: '',
      todos: '',
      files: 'personal',
      workbench: 'leave',
      processes: 'requests',
      contacts: '',
      reports: 'overview',
      settings: 'profile',
    },
    conversations: [] as ConversationRecord[],
    todos: [] as TodoRecord[],
    files: createFileSpaces(),
    personalFileOwnerId: '',
    workbench: workbenchItems,
    contacts: contactItems,
    profile: profileItems,
    };
  },
  getters: {
    totalUnread(state): number {
      return state.conversations.reduce((sum, item) => sum + item.unread, 0);
    },
    pendingTodoCount(state): number {
      return state.todos.filter((item) => isPendingStatus(item.status)).length;
    },
    activeConversation(state): ConversationRecord {
      return findById(state.conversations, state.active.messages, emptyConversation);
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
      return state.conversations.filter((item) => matchesConversationFilter(item, state.filters.messages))
        .filter((item) => includesKeyword([
          item.title,
          item.subtitle,
          item.tag,
          item.flow,
          item.status,
          item.panel.members.map((member) => `${member.name} ${member.role}`).join(' '),
          item.panel.attachments.map((file) => file.name).join(' '),
        ], state.search.messages));
    },
    filteredTodos(state): TodoRecord[] {
      return state.todos
        .filter((item) => state.filters.todos !== '未处理' || isPendingStatus(item.status))
        .filter((item) => includesKeyword([
          item.title,
          item.subtitle,
          item.note,
          item.status,
          item.priority,
          item.requestNo,
          item.taskId,
          item.currentStepName,
        ], state.search.todos));
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
        item.role,
        item.department,
        item.relation,
        item.source,
      ], state.search.contacts));
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
      else if (section === 'workbench') normalized = normalizeWorkbenchApp(id);
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
      writeStoredPreferences({ theme: this.theme, messageDensity: this.messageDensity, listWidth: this.listWidth });
    },
    setTheme(theme: ThemeMode) {
      this.theme = theme;
      writeStoredPreferences({ theme: this.theme, messageDensity: this.messageDensity, listWidth: this.listWidth });
    },
    toggleTheme() {
      this.theme = this.theme === 'light' ? 'dark' : 'light';
      writeStoredPreferences({ theme: this.theme, messageDensity: this.messageDensity, listWidth: this.listWidth });
    },
    setMessageDensity(density: MessageDensity) {
      this.messageDensity = density;
      writeStoredPreferences({ theme: this.theme, messageDensity: this.messageDensity, listWidth: this.listWidth });
    },
    syncSessionProfile(
      user: CurrentUserResponse | null,
      profile: OrgUserDetailResponse | null,
      manager: OrgUserSummaryResponse | null,
      subordinates: OrgUserSummaryResponse[],
    ) {
      const name = profile?.realName || user?.displayName || currentUser.name;
      const employmentStatus = statusValue(profile?.employmentStatus ?? user?.employmentStatus);
      const ownerId = String(profile?.userId ?? user?.userId ?? '');
      if (ownerId !== this.personalFileOwnerId) {
        this.personalFileOwnerId = ownerId;
        const personal = this.files.find((item) => item.id === 'personal');
        if (personal) personal.files = readStoredPersonalFiles(ownerId);
      }
      this.profile = [
        {
          id: 'center',
          title: '个人中心',
          subtitle: '仪表盘',
          note: '',
          tone: 'blue',
          stats: [[name, '姓名'], [employmentStatus, '状态'], [displayValue(profile?.workLocation), '地点']],
          fields: [
            ['姓名', name],
            ['登录名', displayValue(user?.loginName)],
            ['工号', displayValue(profile?.employeeNo)],
            ['手机号', displayValue(profile?.mobile)],
          ],
        },
        {
          id: 'profile',
          title: '个人资料',
          subtitle: '身份信息',
          note: '',
          tone: 'blue',
          stats: [[name, '姓名'], [employmentStatus, '状态'], [displayValue(profile?.workLocation), '地点']],
          fields: [
            ['姓名', name],
            ['登录名', displayValue(user?.loginName)],
            ['工号', displayValue(profile?.employeeNo)],
            ['手机号', displayValue(profile?.mobile)],
            ['部门', displayValue(profile?.primaryDeptName)],
            ['岗位', displayValue(profile?.positionName)],
            ['入职日期', displayValue(profile?.hireDate)],
            ['工作地点', displayValue(profile?.workLocation)],
          ],
        },
        {
          id: 'organization',
          title: '组织关系',
          subtitle: '组织架构',
          note: '',
          tone: 'green',
          stats: [
            [displayValue(profile?.primaryDeptName), '主部门'],
            [displayValue(manager?.realName ?? profile?.managerName), '直属上级'],
            [`${subordinates.length} 人`, '直属下级'],
          ],
          fields: [
            ['部门', displayValue(profile?.primaryDeptName)],
            ['岗位', displayValue(profile?.positionName)],
            ['直属上级', displayValue(manager?.realName ?? profile?.managerName)],
            ['直属下级', subordinates.length ? subordinates.map((item) => item.realName).join('、') : '暂无'],
          ],
        },
        {
          id: 'security',
          title: '账号安全',
          subtitle: '账号安全',
          note: '',
          tone: 'amber',
          stats: [['邮箱', '验证'], ['会话', '状态']],
          fields: [
            ['用户类型', displayValue(user?.userType)],
            ['会话状态', user ? '已登录' : '未登录'],
          ],
        },
        {
          id: 'preferences',
          title: '偏好设置',
          subtitle: '显示与会话',
          note: '',
          tone: 'blue',
          stats: [],
          fields: [],
        },
        {
          id: 'storage',
          title: '文件空间',
          subtitle: '个人云空间',
          note: '',
          tone: 'gray',
          stats: [],
          fields: [],
        },
      ];
      if (!this.profile.some((item) => item.id === this.active.settings)) {
        this.active.settings = this.profile[0]?.id ?? 'profile';
      }
      this.contacts = collectContacts(user, profile, manager, subordinates, this.conversations);
      if (!this.contacts.some((item) => item.id === this.active.contacts)) {
        this.active.contacts = this.contacts[0]?.id ?? '';
      }
    },
    syncFileQuota(quota: QuotaResponse) {
      const personal = this.files.find((item) => item.id === 'personal');
      if (!personal) return;
      const used = bytesToSize(quota.personalUsedBytes);
      const total = bytesToSize(quota.personalQuotaBytes);
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
      mimeType?: string;
      downloadUrl?: string;
      createdAt?: string;
    }) {
      const personal = this.files.find((item) => item.id === 'personal');
      if (!personal) return;
      const id = file.attachmentRefId ? `personal-file-${file.attachmentRefId}` : `personal-file-${Date.now()}-${file.name}`;
      const row: PersonalFileRecord = {
        id,
        name: file.name,
        type: file.type,
        size: file.size,
        createdAt: file.createdAt ?? '刚刚',
        status: file.status,
        attachmentRefId: file.attachmentRefId,
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
    mergeBackendTodos(backendTodos: TodoRecord[]) {
      const backendIds = new Set(backendTodos.map((item) => item.id));
      this.todos = backendTodos;
      if (!backendIds.has(this.active.todos)) {
        this.active.todos = this.todos[0]?.id ?? '';
      }
    },
    mergeBackendConversations(backendConversations: ConversationRecord[]) {
      const backendIds = new Set(backendConversations.map((item) => item.id));
      this.conversations = backendConversations;
      if (!backendIds.has(this.active.messages)) {
        this.active.messages = this.conversations[0]?.id ?? '';
      }
    },
    mergeBackendMessages(conversationId: string, backendMessages: ConversationMessage[]) {
      const conversation = this.conversations.find((item) => item.id === conversationId);
      if (!conversation || !backendMessages.length) return;
      conversation.messages = backendMessages;
      const latestMessage = backendMessages[backendMessages.length - 1];
      conversation.subtitle = latestMessage.body;
      conversation.meta = formatListDate(latestMessage.time) || conversation.meta;
    },
    appendMessage(text: string) {
      const conversation = this.activeConversation;
      conversation.messages.push({
        id: `${conversation.id}-${Date.now()}`,
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

function formatListDate(value?: string) {
  if (!value) return '';
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const date = new Date(normalized);
  if (!Number.isNaN(date.getTime())) {
    return `${date.getMonth() + 1}月${date.getDate()}日`;
  }
  const match = value.match(/^\d{4}-(\d{1,2})-(\d{1,2})/);
  if (match) return `${Number(match[1])}月${Number(match[2])}日`;
  return value;
}

export const shellUser = currentUser;

function contactFromOrgUser(user: OrgUserSummaryResponse, relation: string, source: string): ContactRecord {
  return {
    id: `user-${user.userId}`,
    name: user.realName,
    role: displayValue(user.positionName),
    department: displayValue(user.primaryDeptName),
    relation,
    source,
    userId: user.userId,
  };
}

function collectContacts(
  user: CurrentUserResponse | null,
  profile: OrgUserDetailResponse | null,
  manager: OrgUserSummaryResponse | null,
  subordinates: OrgUserSummaryResponse[],
  conversations: ConversationRecord[],
): ContactRecord[] {
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
        role: displayValue(profile?.positionName),
        department: displayValue(profile?.primaryDeptName),
        relation: '本人',
        source: '个人资料',
        userId,
      });
    }
  }
  if (manager) add(contactFromOrgUser(manager, '直属上级', '组织关系'));
  subordinates.forEach((item) => add(contactFromOrgUser(item, '直属下级', '组织关系')));
  conversations.forEach((conversation) => {
    conversation.panel.members.forEach((member) => {
      if (member.name === (profile?.realName || user?.displayName)) return;
      add({
        id: member.userId ? `user-${member.userId}` : `member-${member.name}-${member.role}`,
        name: member.name,
        role: member.role,
        department: '',
        relation: conversation.flow,
        source: conversation.title,
        userId: member.userId,
      });
    });
  });
  return [...contacts.values()];
}
