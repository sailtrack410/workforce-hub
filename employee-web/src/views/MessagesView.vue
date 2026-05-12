<script setup lang="ts">
import {
  ArrowUpRight,
  AtSign,
  BarChart3,
  CalendarDays,
  Camera,
  ChevronLeft,
  ChevronRight,
  CheckCircle2,
  CircleAlert,
  Clock3,
  ClipboardList,
  CloudUpload,
  ContactRound,
  Download,
  Eye,
  FileArchive,
  FileAudio,
  FileCode,
  FileImage,
  FileJson,
  FileSpreadsheet,
  FileText,
  FileType,
  FileVideo,
  FolderOpen,
  GitBranch,
  Info,
  LayoutGrid,
  MessageCircle,
  MoonStar,
  PanelLeftClose,
  PanelLeftOpen,
  Paperclip,
  PencilLine,
  Search,
  Send,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  SunMedium,
  Trash2,
  UserRound,
  UsersRound,
  X,
} from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { employeeApi, parseJsonPreservingUnsafeIntegers } from '../api';
import type {
  AgentResponse,
  AgentTaskResponse,
  AttendanceDailyResultResponse,
  AttendanceExceptionResponse,
  DownloadTicketResponse,
  EntityId,
  ImMessageResponse,
  LeaveAccountResponse,
  PunchRecordResponse,
  WorkflowRequestCreateResponse,
  WorkflowRequestAttachmentResponse,
  WorkflowRequestDetailResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTaskActionResponse,
} from '../api';
import type { ConversationMessage, PersonalFileRecord, SectionAction, SectionFilter, SectionId } from '../stores/shell';
import {
  primaryNavItems,
  sectionFilters,
  shellUser,
  useShellStore,
  normalizeSettingsPanel,
  normalizeWorkbenchApp,
} from '../stores/shell';
import { mapImMessageToConversationMessage, useImStore } from '../stores/im';
import { useSessionStore } from '../stores/session';
import { useWorkflowStore } from '../stores/workflow';
import AppAvatar from '../components/ui/AppAvatar.vue';
import AppBadge from '../components/ui/AppBadge.vue';
import AppButton from '../components/ui/AppButton.vue';
import AppDrawer from '../components/ui/AppDrawer.vue';
import AppDialog from '../components/ui/AppDialog.vue';
import AppEmptyState from '../components/ui/AppEmptyState.vue';
import AppErrorState from '../components/ui/AppErrorState.vue';
import AppInput from '../components/ui/AppInput.vue';
import AppListItem from '../components/ui/AppListItem.vue';
import AppLoadingState from '../components/ui/AppLoadingState.vue';
import AppTag from '../components/ui/AppTag.vue';
import ShellNavButton from '../components/shell/ShellNavButton.vue';
import { provideEmployeeShellContext } from './employee-shell-context';

const shell = useShellStore();
const im = useImStore();
const session = useSessionStore();
const workflow = useWorkflowStore();
const router = useRouter();
const route = useRoute();

const draft = ref('');
const authMode = ref<'login' | 'reset'>('login');
const loginName = ref('');
const password = ref('');
const resetEmail = ref('');
const resetCode = ref('');
const resetPasswordValue = ref('');
const searchOpen = ref(true);
const resizing = ref(false);
const toastMessage = ref('');
const messageStream = ref<HTMLElement | null>(null);
const filterScroller = ref<HTMLElement | null>(null);
const filtersOverflow = ref(false);
const personalFileInput = ref<HTMLInputElement | null>(null);
const resetCodeSending = ref(false);
const fileUploading = ref(false);
const bindEmail = ref('');
const bindEmailCode = ref('');
const bindEmailCodeSending = ref(false);
const bindEmailSubmitting = ref(false);
const bindEmailEditing = ref(false);
const bindEmailNotice = ref('');
const bindEmailError = ref('');
const mobileDraft = ref('');
const mobileSubmitting = ref(false);
const mobileUpdateNotice = ref('');
const mobileUpdateError = ref('');
const mobileInput = ref<HTMLInputElement | null>(null);
const authNotice = ref('');
const passwordChangeForm = ref({ code: '', newPassword: '' });
const passwordChangeCodeSending = ref(false);
const passwordChangeSubmitting = ref(false);
const passwordChangeNotice = ref('');
const passwordChangeError = ref('');
const fileSendTarget = ref<{ file: PersonalFileRecord; caption: string; submitting: boolean } | null>(null);
const fileSendTargetQuery = ref('');
const fileSendTargetError = ref('');
const currentAvatarUrl = ref('');
const avatarUploading = ref(false);
const avatarUpdateError = ref('');
const avatarPickerInput = ref<HTMLInputElement | null>(null);
const preferencesHydrated = ref(false);
let preferencePushTimer: number | undefined;
const leaveAccounts = ref<LeaveAccountResponse[]>([]);
const attendanceExceptions = ref<AttendanceExceptionResponse[]>([]);
const workbenchLoading = ref(false);
const todoActionMode = ref<'approve' | 'reject' | null>(null);
const todoActionComment = ref('');
const todoActionError = ref('');
const uploadPanelOpen = ref(false);
const uploadDraftFile = ref<File | null>(null);
const uploadDragActive = ref(false);
const uploadResult = ref<{ status: 'success' | 'error'; text: string } | null>(null);
const filePreview = ref<{ file: PersonalFileRecord | WorkflowRequestAttachmentResponse; url: string; inline: boolean; objectUrl?: string } | null>(null);
const filePreviewLoading = ref(false);
const fileActionLoading = ref<Record<string, boolean>>({});
const createConversationOpen = ref(false);
const createConversationMode = ref<'private' | 'group' | 'agent'>('private');
const createConversationForm = ref({
  targetUserId: '',
  groupTitle: '',
  memberUserIds: '',
  agentId: '',
});
const createConversationSubmitting = ref(false);
const createConversationError = ref('');
const groupPanelOpen = ref(false);
const groupProfileForm = ref({
  title: '',
  noticeText: '',
  memberUserIds: '',
  removeMemberUserIds: '',
});
const groupActionError = ref('');
const messageActionTarget = ref<ConversationMessage | null>(null);
const messageEditText = ref('');
const messageActionError = ref('');
const agentTaskForm = ref({
  agentId: '',
  inputText: '',
  taskId: '',
  comment: '',
  cancelReason: '',
});
const agentTaskSubmitting = ref(false);
const agentTaskResult = ref<AgentTaskResponse | null>(null);
const agentTaskError = ref('');
const agents = ref<AgentResponse[]>([]);
const agentsLoading = ref(false);
const wsConnected = ref(false);
const leaveForm = ref({
  leaveTypeId: '',
  startAt: '',
  endAt: '',
  durationMinutes: '480',
  reason: '',
});
const leaveAttachmentIds = ref<Array<number | string>>([]);
const leaveSubmitting = ref(false);
const leaveResult = ref<WorkflowRequestCreateResponse | null>(null);
const leaveError = ref('');
const workflowRequests = ref<WorkflowRequestSummaryResponse[]>([]);
const workflowRequestsTotal = ref(0);
const workflowRequestsLoading = ref(false);
const workflowRequestsError = ref('');
const selectedRequestId = ref<EntityId | null>(null);
const workflowDetail = ref<WorkflowRequestDetailResponse | null>(null);
const workflowActions = ref<WorkflowTaskActionResponse[]>([]);
const workflowDetailLoading = ref(false);
const workflowDetailError = ref('');
const withdrawComment = ref('');
const withdrawSubmitting = ref(false);
const attendanceDailyResults = ref<AttendanceDailyResultResponse[]>([]);
const teamAttendanceDailyResults = ref<AttendanceDailyResultResponse[]>([]);
const attendanceLoading = ref(false);
const attendanceError = ref('');
const punchLoading = ref<'ON_DUTY' | 'OFF_DUTY' | ''>('');
const punchResult = ref<PunchRecordResponse | null>(null);
const clockCorrectionForm = ref({
  targetDate: '',
  missingPunchType: 'OFF_DUTY',
  expectedPunchAt: '',
  reason: '',
});
const clockCorrectionAttachmentIds = ref<Array<number | string>>([]);
const clockCorrectionSubmitting = ref(false);
const clockCorrectionResult = ref<WorkflowRequestCreateResponse | null>(null);
const clockCorrectionError = ref('');
let toastTimer: number | undefined;
let imSocket: WebSocket | null = null;
let imSocketReconnectTimer: number | undefined;
let imSocketHeartbeatTimer: number | undefined;
let imSocketReconnectAttempts = 0;

const sectionIcons: Record<SectionId, typeof MessageCircle> = {
  messages: MessageCircle,
  todos: Clock3,
  files: FileText,
  workbench: LayoutGrid,
  processes: GitBranch,
  contacts: UsersRound,
  reports: BarChart3,
  settings: UserRound,
};

const workbenchIcons: Record<string, typeof MessageCircle> = {
  leave: CalendarDays,
  requests: ClipboardList,
  attendance: Clock3,
  balance: Sparkles,
  agent: Sparkles,
};

const profileIcons: Record<string, typeof MessageCircle> = {
  profile: UserRound,
  organization: UsersRound,
  security: ShieldCheck,
  preferences: SlidersHorizontal,
};

const activeSection = computed(() => shell.section);
const currentMeta = computed(() => shell.currentMeta);
const currentUserName = computed(() => session.displayName || shellUser.name);
const currentUserShortName = computed(() => session.shortName || shellUser.shortName);
const detailVisible = computed(() => shell.detailOpen);
const listWidth = computed(() => `${shell.listWidth}px`);
const mobileStage = computed(() => shell.mobileView);
const activeConversation = computed(() => shell.activeConversation);
const activeTodo = computed(() => shell.activeTodo);
const activeFile = computed(() => shell.activeFile);
const activeWorkbench = computed(() => shell.activeWorkbench);
const activeContact = computed(() => shell.activeContact);
const activeProfile = computed(() => shell.activeProfile);
const pendingTodoCount = computed(() => shell.pendingTodoCount);
const fileUsageCircumference = 2 * Math.PI * 52;
const activeFileUsage = computed(() => Math.max(0, Math.min(100, Number(activeFile.value.usage || 0))));
const activeFileUsageOffset = computed(() => fileUsageCircumference * (1 - activeFileUsage.value / 100));
const themeLabel = computed(() => (shell.theme === 'light' ? '浅色' : '深色'));
const searchModel = computed({
  get: () => shell.search[activeSection.value],
  set: (value: string) => shell.setSearch(activeSection.value, value),
});
const filterOptions = computed(() => {
  if (activeSection.value === 'messages') return sectionFilters;
  if (activeSection.value === 'todos') return sectionFilters.filter((item) => ['全部', '未处理'].includes(item));
  return [];
});
const activeFilter = computed(() => shell.filters[activeSection.value]);
const filteredConversations = computed(() => shell.filteredConversations);
const filteredTodos = computed(() => shell.filteredTodos);
const filteredFiles = computed(() => shell.filteredFiles);
const filteredWorkbench = computed(() => shell.filteredWorkbench);
const filteredContacts = computed(() => shell.filteredContacts);
const filteredProfile = computed(() => shell.filteredProfile);
const mobileNavItems = computed(() => primaryNavItems);
const listEmptyText = computed(() => {
  if (activeSection.value === 'messages') return '暂无消息';
  if (activeSection.value === 'todos') return '暂无待办';
  if (activeSection.value === 'files') return '暂无文件';
  return '暂无数据';
});
const showFilterBar = computed(() => filterOptions.value.length > 1);
const showFilterArrows = computed(() => filtersOverflow.value);
const todoActionTitle = computed(() => (todoActionMode.value === 'reject' ? '驳回待办' : '审批通过'));
const activeConversationTitle = computed(() => {
  const conversation = activeConversation.value;
  return conversation.memberCount ? `${conversation.title}（${conversation.memberCount}）` : conversation.title;
});
const activeConversationCurrentStep = computed(() => activeConversation.value.workflowSteps.find((step) => step.status === 'current'));
const mustHandleTodos = computed(() => filteredTodos.value.filter((item) => item.priority === '高').slice(0, 5));
const approvalTodos = computed(() => filteredTodos.value.filter((item) => item.priority !== '低'));
const deferredTodos = computed(() => filteredTodos.value.filter((item) => item.priority === '低'));
const workbenchLaunchers = computed(() => filteredWorkbench.value);
const agentConversation = computed(() => shell.conversations.find((item) => item.id === 'hr-agent' || item.kind === 'agent'));
const annualLeaveAccount = computed(() => leaveAccounts.value.find((item) => item.leaveCode === 'ANNUAL' || item.leaveName.includes('年假')) ?? leaveAccounts.value[0]);
const workbenchHighlights = computed(() => [
  {
    label: '待办',
    value: `${pendingTodoCount.value}`,
    note: '待处理',
  },
  {
    label: '考勤异常',
    value: `${attendanceExceptions.value.length}`,
    note: '异常记录',
  },
  {
    label: '假期余额',
    value: annualLeaveAccount.value ? minutesToDayLabel(annualLeaveAccount.value.availableMinutes) : '0 天',
    note: annualLeaveAccount.value?.leaveName ?? '假期',
  },
]);
const selectedProfileId = computed(() => activeProfile.value.id);
const profileStatusLabel = computed(() => employmentStatusLabel(session.profile?.employmentStatus ?? session.user?.employmentStatus));
const profileIdentityRows = computed(() => [
  ['姓名', displayField(session.profile?.realName || session.user?.displayName)],
  ['登录名', displayField(session.user?.loginName)],
  ['工号', displayField(session.profile?.employeeNo)],
  ['部门', displayField(session.profile?.primaryDeptName)],
  ['岗位', displayField(session.profile?.positionName)],
  ['工作地点', displayField(session.profile?.workLocation)],
  ['入职日期', displayField(session.profile?.hireDate)],
]);
const accountSecurityRows = computed(() => [
  ['用户类型', displayField(session.user?.userType)],
  ['会话状态', session.user ? '已登录' : '未登录'],
  ['登录名', displayField(session.user?.loginName)],
  ['邮箱', displayField(session.user?.email)],
]);
const currentEmail = computed(() => session.user?.email?.trim() ?? '');
const organizationRows = computed(() => [
  ['部门', relationTree.value.department],
  ['岗位', displayField(session.profile?.positionName)],
  ['直属上级', displayField(relationTree.value.managerName || session.profile?.managerName)],
  ['直属下级', `${relationTree.value.subordinates.length} 人`],
]);
const settingsMetrics = computed(() => [
  {
    label: '未读消息',
    value: String(shell.totalUnread),
    detail: '未读',
    level: percentage(shell.totalUnread, Math.max(shell.totalUnread, 10)),
  },
  {
    label: '待办',
    value: String(pendingTodoCount.value),
    detail: '待处理',
    level: percentage(pendingTodoCount.value, Math.max(pendingTodoCount.value, 10)),
  },
  {
    label: '个人空间',
    value: `${activeFileUsage.value}%`,
    detail: activeFile.value.subtitle || '个人云空间',
    level: activeFileUsage.value,
  },
  {
    label: '假期余额',
    value: annualLeaveAccount.value ? minutesToDayLabel(annualLeaveAccount.value.availableMinutes) : '0 天',
    detail: annualLeaveAccount.value?.leaveName ?? '假期',
    level: annualLeaveAccount.value ? percentage(annualLeaveAccount.value.availableMinutes, 4800) : 0,
  },
  {
    label: '考勤异常',
    value: String(attendanceExceptions.value.length),
    detail: '本月',
    level: percentage(attendanceExceptions.value.length, Math.max(attendanceExceptions.value.length, 5)),
  },
]);
const storageRows = computed(() => activeFile.value.summary.map(([value, label]) => [label, value]));
const recentFileRows = computed(() => activeFile.value.files.slice(0, 5));
const preferenceRows = computed(() => [
  ['颜色模式', themeLabel.value],
  ['消息密度', shell.messageDensity === 'cozy' ? '宽松' : '紧凑'],
]);
const selectedLeaveAccount = computed(() => leaveAccounts.value.find((item) => String(item.leaveTypeId) === leaveForm.value.leaveTypeId));
const activeWorkbenchTitle = computed(() => activeWorkbench.value.title || '工作台');
const activeConversationDetail = computed(() => im.detailsByConversation[shell.active.messages] ?? null);
const activeGroupProfile = computed(() => activeConversationDetail.value?.groupProfile ?? null);
const activeConversationPreferenceRows = computed(() => {
  const conversation = im.conversations.find((item) => item.id === shell.active.messages);
  return [
    ['置顶', conversation?.pinned ? '已开启' : '未开启'],
    ['免打扰', conversation?.muted ? '已开启' : '未开启'],
  ];
});
const selectedRequest = computed(() => workflowRequests.value.find((item) => item.requestId === selectedRequestId.value) ?? workflowRequests.value[0] ?? null);
const workflowDetailRows = computed(() => {
  const detail = workflowDetail.value;
  if (!detail) return [];
  const rows: Array<[string, string]> = [
    ['申请编号', detail.requestNo],
    ['业务类型', bizTypeLabel(detail.bizType)],
    ['状态', statusText(detail.status)],
    ['当前节点', displayField(detail.currentStepName)],
    ['提交时间', formatDateTime(detail.submittedAt)],
  ];
  if (detail.leave) {
    rows.push(
      ['开始时间', formatDateTime(detail.leave.startAt)],
      ['结束时间', formatDateTime(detail.leave.endAt)],
      ['请假时长', minutesToDayLabel(detail.leave.durationMinutes)],
      ['请假原因', displayField(detail.leave.reason)],
    );
  }
  if (detail.clockCorrection) {
    rows.push(
      ['考勤日期', displayField(detail.clockCorrection.targetDate)],
      ['缺卡类型', punchTypeLabel(detail.clockCorrection.missingPunchType)],
      ['补卡时间', formatDateTime(detail.clockCorrection.expectedPunchAt)],
      ['补卡原因', displayField(detail.clockCorrection.reason)],
    );
  }
  return rows;
});
const attendanceSummary = computed(() => [
  ['日结果', `${attendanceDailyResults.value.length} 条`],
  ['异常', `${attendanceExceptions.value.length} 条`],
  ['团队考勤', `${teamAttendanceDailyResults.value.length} 条`],
  ['本月范围', `${thisMonthRange().startDate} 至 ${thisMonthRange().endDate}`],
]);
const teamAttendanceRows = computed(() => teamAttendanceDailyResults.value.slice(0, 12));
const filePreviewTitle = computed(() => filePreview.value ? fileNameOf(filePreview.value.file) : '');
const filePreviewType = computed(() => filePreview.value ? fileMimeOf(filePreview.value.file) : '');
const relationTree = computed(() => {
  const ownName = session.displayName || currentUserName.value;
  const department = session.departmentName || '组织';
  const manager = session.manager;
  const subordinates = session.subordinates;
  const conversationMembers = shell.contacts.filter((contact) => (
    contact.relation !== '本人'
    && contact.relation !== '直属上级'
    && contact.relation !== '直属下级'
  ));
  return {
    department,
    managerName: manager?.realName ?? '',
    ownName,
    subordinates,
    conversationMembers,
  };
});
const colleagues = computed(() => session.colleagues);
const colleaguesLoading = computed(() => session.profileLoading);
const colleaguesError = computed(() => session.profileError);
const hasActiveConversation = computed(() => Boolean(activeConversation.value.id));
const hasActiveTodo = computed(() => Boolean(activeTodo.value.id));
const businessEmptyTitle = computed(() => {
  if (activeSection.value === 'processes') return '暂无流程';
  if (activeSection.value === 'contacts') return '暂无联系人';
  if (activeSection.value === 'reports') return '暂无报表';
  return '暂无数据';
});
const authModeTitle = computed(() => {
  if (authMode.value === 'reset') return '找回密码';
  return '员工登录';
});
const authModeSubtitle = computed(() => {
  if (authMode.value === 'reset') return '邮箱验证';
  return '';
});
const currentListLoading = computed(() => {
  if (activeSection.value === 'messages') return im.loading;
  if (activeSection.value === 'todos') return workflow.loading;
  return false;
});
const currentListError = computed(() => {
  if (activeSection.value === 'messages') return im.error;
  if (activeSection.value === 'todos') return workflow.error;
  return '';
});

const sectionRouteNames: Partial<Record<SectionId, string>> = {
  messages: 'employee-messages',
  todos: 'employee-todos',
  files: 'employee-files',
  workbench: 'employee-workbench',
  contacts: 'employee-contacts',
  settings: 'employee-settings',
  processes: 'employee-processes',
  reports: 'employee-reports',
};

function routeNameForSection(section: SectionId, item?: string) {
  if (section === 'messages' && item) return 'employee-message-detail';
  if (section === 'todos' && item) return 'employee-todo-detail';
  if (section === 'workbench' && item) return 'employee-workbench-detail';
  if (section === 'settings' && item) return 'employee-settings-detail';
  return sectionRouteNames[section] ?? 'employee-messages';
}

function routeParamsForSection(section: SectionId, item?: string) {
  if (section === 'messages' && item) return { conversationId: item };
  if (section === 'todos' && item) return { todoId: item };
  if (section === 'workbench' && item) return { app: item };
  if (section === 'settings' && item) return { panel: item };
  return {};
}

function navigateSection(section: SectionId, item?: string) {
  const name = routeNameForSection(section, item);
  const params = routeParamsForSection(section, item);
  const sameRoute = route.name === name
    && Object.entries(params).every(([key, value]) => route.params[key] === value);
  shell.setSection(section);
  if (sameRoute) return;
  void router.push({ name, params });
}

function displayField(value: unknown) {
  if (typeof value === 'number') return String(value);
  if (typeof value === 'string' && value.trim()) return value;
  return '-';
}

function employmentStatusLabel(status?: string | null) {
  if (status === 'ACTIVE' || status === 'IN_SERVICE') return '在职';
  if (status === 'DISABLED') return '停用';
  if (status === 'RESIGNED') return '离职';
  return displayField(status);
}

function percentage(value: number, max: number) {
  if (!Number.isFinite(value) || !Number.isFinite(max) || max <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((value / max) * 100)));
}

function showToast(message: string) {
  toastMessage.value = message;
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toastMessage.value = '';
    toastTimer = undefined;
  }, 2600);
}

function toneOfConversation(tone: string) {
  if (tone === 'green') return 'success';
  if (tone === 'amber') return 'warning';
  if (tone === 'red') return 'danger';
  if (tone === 'gray') return 'neutral';
  return 'primary';
}

function toneOfStatus(status: string) {
  if (['PENDING', 'OPEN', 'IN_APPROVAL', 'ABNORMAL'].includes(status)) return 'warning';
  if (['ACTIVE', 'READY', 'APPROVED'].includes(status)) return 'success';
  if (['REJECTED', 'EXPIRED'].includes(status)) return 'danger';
  return 'neutral';
}

function statusText(status: string) {
  if (status === 'PENDING') return '待处理';
  if (status === 'PENDING_REVIEW') return '待预审';
  if (status === 'OPEN') return '待处理';
  if (status === 'IN_APPROVAL') return '审批中';
  if (status === 'ABNORMAL') return '异常';
  if (status === 'NORMAL') return '正常';
  if (status === 'ACTIVE') return '正常';
  if (status === 'READY') return '就绪';
  if (status === 'APPROVED') return '已通过';
  if (status === 'REJECTED') return '已驳回';
  if (status === 'WITHDRAWN') return '已撤回';
  if (status === 'NO_RULE') return '未排班';
  if (status === 'LEAVE') return '请假';
  if (status === 'EXPIRED') return '已过期';
  return status;
}

function runAction(action?: SectionAction) {
  if (!action) return;
  if (action.action === 'upload-personal-file') {
    openUploadPanel();
    return;
  }
  if (action.action === 'switch-section' && action.section) {
    navigateSection(action.section);
    shell.setMobileView('list');
    return;
  }
  if (action.action === 'switch-item' && action.item) {
    const section = action.section ?? activeSection.value;
    navigateSection(section, action.item);
    shell.selectItem(section, action.item);
    return;
  }
  if (action.action === 'open-workbench' && action.app) {
    navigateSection('workbench', action.app);
    shell.selectItem('workbench', action.app);
    return;
  }
  if (action.action === 'approve-todo') {
    openTodoAction('approve');
    return;
  }
  if (action.action === 'reject-todo') {
    openTodoAction('reject');
  }
}

function selectWorkbenchItem(id: string) {
  navigateSection('workbench', id);
  shell.selectItem('workbench', id);
  if (!session.isAuthenticated) return;
  if (id === 'requests') void loadWorkflowRequests();
  if (id === 'attendance') void refreshAttendancePanel();
  if (id === 'balance' || id === 'leave') void refreshWorkbenchData();
  if (id === 'files') void refreshFileCenter();
  if (id === 'agent') void loadAgents();
}

function selectSettingsItem(id: string) {
  const canonical = normalizeSettingsPanel(id);
  navigateSection('settings', canonical);
  shell.selectItem('settings', canonical);
}

async function sha256Hex(file: File) {
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

function fileSizeLabel(bytes: number) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(bytes >= 10 * 1024 * 1024 ? 0 : 1)}MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(bytes >= 10 * 1024 ? 0 : 1)}KB`;
  return `${bytes}B`;
}

function fileTypeLabel(file: File) {
  const ext = file.name.split('.').pop();
  return (ext || file.type || '文件').slice(0, 8).toUpperCase();
}

function fileTypeLabelFromName(name: string, mimeType = '') {
  const ext = name.split('.').pop()?.trim();
  if (ext) return ext.slice(0, 8).toUpperCase();
  return (mimeType.split('/').pop() || '文件').slice(0, 8).toUpperCase();
}

function fileNameOf(file: PersonalFileRecord | WorkflowRequestAttachmentResponse) {
  return 'displayName' in file ? file.displayName : file.name;
}

function fileSizeOf(file: PersonalFileRecord | WorkflowRequestAttachmentResponse) {
  return 'fileSize' in file ? fileSizeLabel(file.fileSize) : file.size;
}

function fileMimeOf(file: PersonalFileRecord | WorkflowRequestAttachmentResponse) {
  return file.mimeType ?? '';
}

function fileIconOf(file: PersonalFileRecord | WorkflowRequestAttachmentResponse) {
  const name = fileNameOf(file).toLowerCase();
  const mimeType = fileMimeOf(file).toLowerCase();
  if (mimeType.startsWith('image/') || /\.(png|jpe?g|gif|webp|svg)$/i.test(name)) return FileImage;
  if (mimeType.startsWith('audio/') || /\.(mp3|wav|aac|flac|m4a)$/i.test(name)) return FileAudio;
  if (mimeType.startsWith('video/') || /\.(mp4|mov|webm|avi|mkv)$/i.test(name)) return FileVideo;
  if (/\.(xls|xlsx|csv)$/i.test(name)) return FileSpreadsheet;
  if (/\.(json|jsonl)$/i.test(name) || mimeType.includes('json')) return FileJson;
  if (/\.(js|ts|tsx|vue|java|sql|xml|html|css|scss|yml|yaml)$/i.test(name)) return FileCode;
  if (/\.(zip|rar|7z|tar|gz)$/i.test(name)) return FileArchive;
  if (/\.(doc|docx|pdf|txt|md)$/i.test(name)) return FileText;
  return FileType;
}

function fileToneOf(file: PersonalFileRecord | WorkflowRequestAttachmentResponse) {
  const name = fileNameOf(file).toLowerCase();
  const mimeType = fileMimeOf(file).toLowerCase();
  if (mimeType.startsWith('image/') || /\.(png|jpe?g|gif|webp|svg)$/i.test(name)) return 'image';
  if (mimeType.startsWith('audio/') || /\.(mp3|wav|aac|flac|m4a)$/i.test(name)) return 'audio';
  if (mimeType.startsWith('video/') || /\.(mp4|mov|webm|avi|mkv)$/i.test(name)) return 'video';
  if (/\.(xls|xlsx|csv)$/i.test(name)) return 'sheet';
  if (/\.(json|jsonl|js|ts|tsx|vue|java|sql|xml|html|css|scss|yml|yaml)$/i.test(name) || mimeType.includes('json')) return 'code';
  if (/\.(zip|rar|7z|tar|gz)$/i.test(name)) return 'archive';
  if (/\.(pdf|doc|docx|txt|md)$/i.test(name)) return 'document';
  return 'default';
}

function canInlinePreview(file: PersonalFileRecord | WorkflowRequestAttachmentResponse) {
  const name = fileNameOf(file).toLowerCase();
  const mimeType = fileMimeOf(file).toLowerCase();
  return (
    mimeType.startsWith('image/')
    || mimeType === 'application/pdf'
    || mimeType.startsWith('text/')
    || mimeType.includes('json')
    || /\.(png|jpe?g|gif|webp|svg|pdf|txt|md|json|csv|log)$/i.test(name)
  );
}

function isFileTrashed(file: PersonalFileRecord) {
  return file.status === '回收站' || file.status === '已删除';
}

function minutesToDayLabel(minutes?: number | null) {
  const safeMinutes = Math.max(0, Number(minutes ?? 0));
  const days = safeMinutes / 480;
  return `${Number.isInteger(days) ? days.toFixed(0) : days.toFixed(1)} 天`;
}

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function toApiDateTime(value: string) {
  return value ? `${value.replace('T', ' ')}:00`.slice(0, 19) : '';
}

function bizTypeLabel(type?: string | null) {
  if (type === 'LEAVE') return '请假';
  if (type === 'CLOCK_CORRECTION') return '补卡';
  return displayField(type);
}

function punchTypeLabel(type?: string | null) {
  if (type === 'ON_DUTY') return '上班';
  if (type === 'OFF_DUTY') return '下班';
  return displayField(type);
}

function actionTypeLabel(type?: string | null) {
  if (type === 'APPROVE') return '通过';
  if (type === 'REJECT') return '驳回';
  if (type === 'WITHDRAW') return '撤回';
  if (type === 'AGENT_PRE_REVIEW') return 'Agent预审';
  return displayField(type);
}

function entityIdListFromText(value: string): EntityId[] {
  return value
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function activeConversationId() {
  return shell.active.messages.trim();
}

function isCurrentUserMessage(message: ConversationMessage) {
  return message.side === 'right' && message.status !== 'RECALLED';
}

async function loadAgents() {
  if (agentsLoading.value) return;
  agentsLoading.value = true;
  try {
    agents.value = await employeeApi.agents.list();
    if (!createConversationForm.value.agentId && agents.value[0]) {
      createConversationForm.value.agentId = String(agents.value[0].agentId);
    }
    if (!agentTaskForm.value.agentId && agents.value[0]) {
      agentTaskForm.value.agentId = String(agents.value[0].agentId);
    }
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Agent加载失败');
  } finally {
    agentsLoading.value = false;
  }
}

function openCreateConversation(mode: 'private' | 'group' | 'agent') {
  createConversationMode.value = mode;
  createConversationOpen.value = true;
  createConversationError.value = '';
  if (mode === 'agent') void loadAgents();
}

function closeCreateConversation() {
  if (createConversationSubmitting.value) return;
  createConversationOpen.value = false;
  createConversationError.value = '';
}

async function submitCreateConversation() {
  if (createConversationSubmitting.value) return;
  createConversationSubmitting.value = true;
  createConversationError.value = '';
  try {
    let conversation;
    if (createConversationMode.value === 'private') {
      const targetUserId = createConversationForm.value.targetUserId.trim();
      if (!targetUserId) throw new Error('请选择成员');
      conversation = await im.startPrivateConversation(targetUserId);
    } else if (createConversationMode.value === 'group') {
      const title = createConversationForm.value.groupTitle.trim();
      const memberUserIds = entityIdListFromText(createConversationForm.value.memberUserIds);
      if (!title || !memberUserIds.length) throw new Error('请填写群名称和成员');
      conversation = await im.createGroupConversation(title, memberUserIds);
    } else {
      const agentId = createConversationForm.value.agentId.trim();
      if (!agentId) throw new Error('请选择Agent');
      conversation = await im.startAgentConversation(agentId);
    }
    shell.mergeBackendConversations(im.conversations);
    navigateSection('messages', conversation.id);
    shell.selectItem('messages', conversation.id);
    await syncConversationMessages(conversation.id);
    closeCreateConversation();
    showToast('会话已创建');
  } catch (error) {
    createConversationError.value = error instanceof Error ? error.message : '会话创建失败';
  } finally {
    createConversationSubmitting.value = false;
  }
}

async function openGroupPanel() {
  const conversationId = activeConversationId();
  if (!conversationId) return;
  try {
    const detail = await im.loadConversationDetail(conversationId);
    groupProfileForm.value.title = detail.groupProfile?.title ?? activeConversation.value.title;
    groupProfileForm.value.noticeText = detail.groupProfile?.noticeText ?? '';
    groupProfileForm.value.memberUserIds = '';
    groupProfileForm.value.removeMemberUserIds = '';
    groupActionError.value = '';
    groupPanelOpen.value = true;
  } catch (error) {
    showToast(error instanceof Error ? error.message : '群资料加载失败');
  }
}

function closeGroupPanel() {
  if (im.actionLoading) return;
  groupPanelOpen.value = false;
  groupActionError.value = '';
}

async function saveGroupProfile() {
  const conversationId = activeConversationId();
  if (!conversationId) return;
  groupActionError.value = '';
  try {
    await im.updateGroupProfile(conversationId, {
      title: groupProfileForm.value.title.trim() || undefined,
      noticeText: groupProfileForm.value.noticeText.trim() || undefined,
    });
    shell.mergeBackendConversations(im.conversations);
    showToast('群资料已保存');
  } catch (error) {
    groupActionError.value = error instanceof Error ? error.message : '保存失败';
  }
}

async function addGroupMembers() {
  const conversationId = activeConversationId();
  const memberUserIds = entityIdListFromText(groupProfileForm.value.memberUserIds);
  if (!conversationId || !memberUserIds.length) return;
  groupActionError.value = '';
  try {
    await im.addGroupMembers(conversationId, memberUserIds);
    groupProfileForm.value.memberUserIds = '';
    shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    showToast('成员已添加');
  } catch (error) {
    groupActionError.value = error instanceof Error ? error.message : '添加失败';
  }
}

async function removeGroupMembers() {
  const conversationId = activeConversationId();
  const memberUserIds = entityIdListFromText(groupProfileForm.value.removeMemberUserIds);
  if (!conversationId || !memberUserIds.length) return;
  groupActionError.value = '';
  try {
    await im.removeGroupMembers(conversationId, memberUserIds);
    groupProfileForm.value.removeMemberUserIds = '';
    shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    showToast('成员已移除');
  } catch (error) {
    groupActionError.value = error instanceof Error ? error.message : '移除失败';
  }
}

async function leaveActiveGroup() {
  const conversationId = activeConversationId();
  if (!conversationId) return;
  try {
    await im.leaveGroup(conversationId);
    await im.loadConversations();
    shell.mergeBackendConversations(im.conversations);
    closeGroupPanel();
    showToast('已退出群聊');
  } catch (error) {
    groupActionError.value = error instanceof Error ? error.message : '退群失败';
  }
}

async function dismissActiveGroup() {
  const conversationId = activeConversationId();
  if (!conversationId) return;
  try {
    await im.dismissGroup(conversationId);
    shell.mergeBackendConversations(im.conversations);
    closeGroupPanel();
    showToast('群聊已解散');
  } catch (error) {
    groupActionError.value = error instanceof Error ? error.message : '解散失败';
  }
}

async function toggleActiveConversationPreference(field: 'pin' | 'mute') {
  const conversationId = activeConversationId();
  if (!conversationId) return;
  const current = im.conversations.find((item) => item.id === String(conversationId));
  if (!current) return;
  try {
    await im.updatePreference(conversationId, {
      pin: field === 'pin' ? !current.pinned : current.pinned,
      mute: field === 'mute' ? !current.muted : current.muted,
    });
    shell.mergeBackendConversations(im.conversations);
    showToast('会话设置已保存');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '保存失败');
  }
}

function openMessageAction(message: ConversationMessage) {
  if (!isCurrentUserMessage(message)) return;
  messageActionTarget.value = message;
  messageEditText.value = message.body;
  messageActionError.value = '';
}

function closeMessageAction() {
  if (im.actionLoading) return;
  messageActionTarget.value = null;
  messageEditText.value = '';
  messageActionError.value = '';
}

async function submitMessageEdit() {
  const message = messageActionTarget.value;
  const conversationId = activeConversationId();
  if (!message || !conversationId) return;
  try {
    await im.editMessage(conversationId, message.id, messageEditText.value, session.user?.userId);
    shell.mergeBackendMessages(String(conversationId), im.messagesByConversation[String(conversationId)] ?? []);
    closeMessageAction();
    showToast('消息已更新');
  } catch (error) {
    messageActionError.value = error instanceof Error ? error.message : '编辑失败';
  }
}

async function submitMessageRecall() {
  const message = messageActionTarget.value;
  const conversationId = activeConversationId();
  if (!message || !conversationId) return;
  try {
    await im.recallMessage(conversationId, message.id, session.user?.userId);
    shell.mergeBackendMessages(String(conversationId), im.messagesByConversation[String(conversationId)] ?? []);
    closeMessageAction();
    showToast('消息已撤回');
  } catch (error) {
    messageActionError.value = error instanceof Error ? error.message : '撤回失败';
  }
}

async function openImAttachment(message: ConversationMessage, attachmentRefId: EntityId, action: 'preview' | 'download') {
  const conversationId = activeConversationId();
  if (!conversationId) return;
  try {
    const ticket = await employeeApi.im.createMessageAttachmentDownloadTicket(conversationId, message.id, attachmentRefId, {
      downloadType: 'SINGLE',
      reason: action === 'preview' ? '预览附件' : '下载附件',
    });
    const attachment = message.attachments?.find((item) => item.attachmentRefId === attachmentRefId);
    const file = {
      id: attachment?.id ?? `im-attachment-${attachmentRefId}`,
      name: attachment?.name ?? ticket.fileName,
      type: attachment?.type ?? fileTypeLabelFromName(ticket.fileName),
      size: attachment?.size ?? fileSizeLabel(ticket.fileSize),
      createdAt: '',
      status: attachment?.status ?? '',
      attachmentRefId,
      mimeType: attachment?.mimeType,
    };
    const url = resolveDownloadUrl(ticket, employeeApi.im.downloadAttachmentUrl(ticket.ticketNo));
    if (action === 'download') {
      window.open(url, '_blank', 'noopener');
      return;
    }
    await openFilePreviewFromUrl(file, url);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '附件打开失败');
  }
}

async function submitAgentTask() {
  const agentId = agentTaskForm.value.agentId.trim();
  const inputText = agentTaskForm.value.inputText.trim();
  if (!agentId || agentTaskSubmitting.value) return;
  agentTaskSubmitting.value = true;
  agentTaskError.value = '';
  agentTaskResult.value = null;
  try {
    agentTaskResult.value = await employeeApi.agents.createTask(agentId, {
      triggerType: 'MANUAL',
      contextType: 'USER_QUERY',
      contextId: session.user?.userId,
      inputPayloadJson: JSON.stringify({ query: inputText }),
    });
    if (agentTaskResult.value.taskId) {
      agentTaskForm.value.taskId = String(agentTaskResult.value.taskId);
    }
    showToast('Agent任务已创建');
  } catch (error) {
    agentTaskError.value = error instanceof Error ? error.message : '任务创建失败';
  } finally {
    agentTaskSubmitting.value = false;
  }
}

async function confirmAgentTask(approved: boolean) {
  const taskId = agentTaskForm.value.taskId.trim();
  if (!taskId || agentTaskSubmitting.value) return;
  agentTaskSubmitting.value = true;
  agentTaskError.value = '';
  try {
    agentTaskResult.value = await employeeApi.agentTasks.confirm(taskId, {
      approved,
      comment: agentTaskForm.value.comment.trim() || undefined,
    });
    showToast(approved ? 'Agent任务已确认' : 'Agent任务已拒绝');
  } catch (error) {
    agentTaskError.value = error instanceof Error ? error.message : '操作失败';
  } finally {
    agentTaskSubmitting.value = false;
  }
}

async function cancelAgentTask() {
  const taskId = agentTaskForm.value.taskId.trim();
  const reason = agentTaskForm.value.cancelReason.trim();
  if (!taskId || !reason || agentTaskSubmitting.value) return;
  agentTaskSubmitting.value = true;
  agentTaskError.value = '';
  try {
    agentTaskResult.value = await employeeApi.agentTasks.cancel(taskId, { reason });
    showToast('Agent任务已取消');
  } catch (error) {
    agentTaskError.value = error instanceof Error ? error.message : '取消失败';
  } finally {
    agentTaskSubmitting.value = false;
  }
}

function thisMonthRange() {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  return {
    startDate: start.toISOString().slice(0, 10),
    endDate: end.toISOString().slice(0, 10),
  };
}

async function refreshWorkbenchData() {
  if (workbenchLoading.value) return;
  workbenchLoading.value = true;
  const dateRange = thisMonthRange();
  try {
    const [accounts, exceptions, dailyResults] = await Promise.all([
      employeeApi.leave.accounts().catch(() => []),
      employeeApi.attendance.exceptions(dateRange).catch(() => []),
      employeeApi.attendance.dailyResults(dateRange).catch(() => []),
    ]);
    leaveAccounts.value = accounts;
    attendanceExceptions.value = exceptions;
    attendanceDailyResults.value = dailyResults;
    if (!leaveForm.value.leaveTypeId && accounts[0]) {
      leaveForm.value.leaveTypeId = String(accounts[0].leaveTypeId);
    }
  } finally {
    workbenchLoading.value = false;
  }
}

async function refreshFileQuota() {
  try {
    shell.syncFileQuota(await employeeApi.files.quota());
  } catch {
    return;
  }
}

async function refreshPersonalFiles() {
  try {
    const [activeFiles, recycleBinFiles] = await Promise.all([
      employeeApi.files.list({ pageNo: 1, pageSize: 20 }),
      employeeApi.files.recycleBin({ pageNo: 1, pageSize: 20 }),
    ]);
    shell.mergeBackendPersonalFiles(activeFiles.list, recycleBinFiles.list);
  } catch {
    return;
  }
}

async function refreshFileCenter() {
  await Promise.all([refreshFileQuota(), refreshPersonalFiles()]);
}

async function handlePersonalFileSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  uploadDraftFile.value = file;
  uploadResult.value = null;
  uploadPanelOpen.value = true;
  uploadDragActive.value = false;
}

function openUploadPanel() {
  uploadPanelOpen.value = true;
  uploadResult.value = null;
  uploadDragActive.value = false;
}

function closeUploadPanel() {
  if (fileUploading.value) return;
  uploadPanelOpen.value = false;
  uploadDraftFile.value = null;
  uploadResult.value = null;
  uploadDragActive.value = false;
}

function openFilePicker() {
  personalFileInput.value?.click();
}

function handleUploadDrop(event: DragEvent) {
  uploadDragActive.value = false;
  const file = event.dataTransfer?.files?.[0];
  if (!file) return;
  uploadDraftFile.value = file;
  uploadResult.value = null;
}

async function submitPersonalFileUpload() {
  const file = uploadDraftFile.value;
  if (!file || fileUploading.value) return;
  const attachmentRefId = await uploadPersonalFile(file);
  if (attachmentRefId) {
    uploadDraftFile.value = null;
  }
}

function uploadedPersonalFile(attachmentRefId: EntityId) {
  return activeFile.value.files.find((item) => item.attachmentRefId === attachmentRefId);
}

async function uploadPersonalFile(file: File): Promise<EntityId | null> {
  fileUploading.value = true;
  uploadResult.value = null;
  try {
    const contentSha256 = await sha256Hex(file);
    const ticket = await employeeApi.files.createUploadTicket({
      ownerType: 'PERSONAL_FILE',
      ownerId: null,
      fileName: file.name,
      mimeType: file.type || 'application/octet-stream',
      fileSize: file.size,
      contentSha256,
      clientRequestId: `personal-${Date.now()}-${file.name}`,
    });

    if (ticket.deduplicated && ticket.attachmentRefId) {
      const attachmentRefId = ticket.attachmentRefId;
      shell.addPersonalFile({
        name: file.name,
        type: fileTypeLabel(file),
        size: fileSizeLabel(file.size),
        status: '已确认',
        attachmentRefId,
        mimeType: file.type,
      });
      uploadResult.value = { status: 'success', text: '上传成功' };
      await refreshFileCenter();
      const uploaded = uploadedPersonalFile(attachmentRefId);
      if (uploaded && activeSection.value === 'messages' && hasActiveConversation.value) await sendSelectedFileToConversation(uploaded);
      return attachmentRefId;
    }

    if (!ticket.uploadTicketId || !ticket.objectKey || !ticket.uploadUrl) {
      throw new Error('上传地址不可用');
    }

    const uploadHeaders = { ...ticket.headers };
    if (uploadHeaders['content-type'] && !uploadHeaders['Content-Type']) {
      uploadHeaders['Content-Type'] = uploadHeaders['content-type'];
      delete uploadHeaders['content-type'];
    }

    if (uploadHeaders['Content-Type'] === 'application/octet-stream' && file.type) {
      uploadHeaders['Content-Type'] = file.type;
    }

    if (!uploadHeaders['Content-Type']) {
      uploadHeaders['Content-Type'] = file.type || 'application/octet-stream';
    }

    const uploadResponse = await uploadToTicketTarget(ticket.uploadUrl, file, uploadHeaders);
    if (!uploadResponse.ok) {
      throw new Error(await uploadResponse.text().catch(() => '') || '文件上传失败');
    }

    const attachment = await employeeApi.files.confirmUpload({
      uploadTicketId: ticket.uploadTicketId,
      objectKey: ticket.objectKey,
      contentSha256,
      fileSize: file.size,
      ownerType: 'PERSONAL_FILE',
      ownerId: null,
      displayName: file.name,
    });
    shell.addPersonalFile({
      name: attachment.displayName || file.name,
      type: fileTypeLabelFromName(attachment.displayName || file.name, attachment.mimeType),
      size: fileSizeLabel(attachment.fileSize || file.size),
      status: attachment.scanStatus ? statusText(attachment.scanStatus) : '已确认',
      attachmentRefId: attachment.attachmentRefId,
      mimeType: attachment.mimeType || file.type,
      createdAt: formatDateTime(attachment.createdAt),
    });
    uploadResult.value = { status: 'success', text: '上传成功' };
    await refreshFileCenter();
    const uploaded = uploadedPersonalFile(attachment.attachmentRefId);
    if (uploaded && activeSection.value === 'messages' && hasActiveConversation.value) await sendSelectedFileToConversation(uploaded);
    return attachment.attachmentRefId;
  } catch (error) {
    uploadResult.value = { status: 'error', text: error instanceof Error ? error.message : '上传失败' };
    return null;
  } finally {
    fileUploading.value = false;
  }
}

function uploadToTicketTarget(uploadUrl: string, file: File, uploadHeaders: Record<string, string>) {
  if (/^https?:\/\//i.test(uploadUrl)) {
    return fetch(uploadUrl, {
      method: 'PUT',
      body: file,
      headers: uploadHeaders,
    });
  }
  return fetch('/__employee-local-upload', {
    method: 'PUT',
      body: file,
      headers: {
      'Content-Type': uploadHeaders['Content-Type'] ?? (file.type || 'application/octet-stream'),
      'X-Upload-Path': uploadUrl,
    },
  });
}

function resolveDownloadUrl(ticket: DownloadTicketResponse, fallback: string) {
  const url = ticket.downloadUrl || fallback;
  if (/^https?:\/\//i.test(url)) return url;
  return url.startsWith('/') ? url : `/${url}`;
}

async function createPersonalDownloadTicket(file: PersonalFileRecord) {
  if (!file.attachmentRefId) throw new Error('文件不可下载');
  return employeeApi.files.createDownloadTicket(file.attachmentRefId, { downloadType: 'SINGLE' });
}

async function openPersonalFilePreview(file: PersonalFileRecord) {
  if (isFileTrashed(file)) return;
  const actionKey = `preview-${file.id}`;
  fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
  try {
    let url = file.downloadUrl;
    if (!url) {
      const ticket = await createPersonalDownloadTicket(file);
      url = resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo));
    }
    await openFilePreviewFromUrl(file, url);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '预览失败');
  } finally {
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
  }
}

async function openFilePreviewFromUrl(file: PersonalFileRecord | WorkflowRequestAttachmentResponse, url: string) {
  closeFilePreview();
  const inline = canInlinePreview(file);
  if (!inline) {
    filePreview.value = { file, url, inline: false };
    return;
  }
  filePreviewLoading.value = true;
  try {
    const response = await fetch(url, { credentials: 'include' });
    if (!response.ok) throw new Error('文件预览失败');
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    filePreview.value = { file, url: objectUrl, inline: true, objectUrl };
  } catch (error) {
    filePreview.value = { file, url, inline: false };
    throw error;
  } finally {
    filePreviewLoading.value = false;
  }
}

async function downloadPersonalFile(file: PersonalFileRecord) {
  if (isFileTrashed(file)) return;
  const actionKey = `download-${file.id}`;
  fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
  try {
    const ticket = await createPersonalDownloadTicket(file);
    window.open(resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo)), '_blank', 'noopener');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '下载失败');
  } finally {
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
  }
}

async function trashPersonalFile(file: PersonalFileRecord) {
  if (!file.attachmentRefId) return;
  const actionKey = `trash-${file.id}`;
  fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
  try {
    await employeeApi.files.trash(file.attachmentRefId, { reason: '删除' });
    shell.updatePersonalFile(file.attachmentRefId, { status: '回收站' });
    await refreshFileCenter();
    showToast('已移入回收站');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '删除失败');
  } finally {
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
  }
}

async function restorePersonalFile(file: PersonalFileRecord) {
  if (!file.attachmentRefId) return;
  const actionKey = `restore-${file.id}`;
  fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
  try {
    await employeeApi.files.restore(file.attachmentRefId, { reason: '恢复' });
    shell.updatePersonalFile(file.attachmentRefId, { status: '已恢复' });
    await refreshFileCenter();
    showToast('文件已恢复');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '恢复失败');
  } finally {
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
  }
}

async function purgePersonalFile(file: PersonalFileRecord) {
  if (!file.attachmentRefId) return;
  const actionKey = `purge-${file.id}`;
  fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
  try {
    await employeeApi.files.purge(file.attachmentRefId);
    shell.removePersonalFile(file.attachmentRefId);
    await refreshFileCenter();
    showToast('文件已彻底删除');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '彻底删除失败');
  } finally {
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
  }
}

function closeFilePreview() {
  if (filePreview.value?.objectUrl) {
    URL.revokeObjectURL(filePreview.value.objectUrl);
  }
  filePreview.value = null;
}

function openPreviewExternal() {
  if (!filePreview.value?.url) return;
  window.open(filePreview.value.url, '_blank', 'noopener');
}

async function submitLeaveRequest() {
  leaveError.value = '';
  leaveResult.value = null;
  const leaveTypeId = leaveForm.value.leaveTypeId.trim();
  const durationMinutes = Number(leaveForm.value.durationMinutes);
  const reason = leaveForm.value.reason.trim();
  if (!leaveTypeId || !leaveForm.value.startAt || !leaveForm.value.endAt || !durationMinutes || !reason) {
    leaveError.value = '请完整填写请假申请';
    return;
  }
  leaveSubmitting.value = true;
  try {
    leaveResult.value = await employeeApi.workflow.submitLeaveRequest({
      leaveTypeId,
      startAt: toApiDateTime(leaveForm.value.startAt),
      endAt: toApiDateTime(leaveForm.value.endAt),
      durationMinutes,
      reason,
      attachmentRefIds: leaveAttachmentIds.value,
    });
    leaveForm.value.reason = '';
    leaveAttachmentIds.value = [];
    await Promise.all([refreshWorkbenchData(), loadWorkflowRequests()]);
    showToast('请假申请已提交');
  } catch (error) {
    leaveError.value = error instanceof Error ? error.message : '提交失败';
  } finally {
    leaveSubmitting.value = false;
  }
}

async function loadWorkflowRequests() {
  if (!session.isAuthenticated || workflowRequestsLoading.value) return;
  workflowRequestsLoading.value = true;
  workflowRequestsError.value = '';
  try {
    const page = await employeeApi.workflow.requests({ pageNo: 1, pageSize: 20 });
    workflowRequests.value = page.list;
    workflowRequestsTotal.value = page.total;
    if (!selectedRequestId.value || !page.list.some((item) => item.requestId === selectedRequestId.value)) {
      selectedRequestId.value = page.list[0]?.requestId ?? null;
    }
    if (selectedRequestId.value) {
      await loadWorkflowRequestDetail(selectedRequestId.value);
    } else {
      workflowDetail.value = null;
      workflowActions.value = [];
    }
  } catch (error) {
    workflowRequestsError.value = error instanceof Error ? error.message : '申请加载失败';
  } finally {
    workflowRequestsLoading.value = false;
  }
}

async function loadWorkflowRequestDetail(requestId: EntityId) {
  selectedRequestId.value = requestId;
  workflowDetailLoading.value = true;
  workflowDetailError.value = '';
  try {
    const [detail, actions] = await Promise.all([
      employeeApi.workflow.requestDetail(requestId),
      employeeApi.workflow.requestActions(requestId).catch(() => []),
    ]);
    workflowDetail.value = detail;
    workflowActions.value = actions;
  } catch (error) {
    workflowDetailError.value = error instanceof Error ? error.message : '详情加载失败';
  } finally {
    workflowDetailLoading.value = false;
  }
}

async function withdrawWorkflowRequest() {
  const requestId = selectedRequestId.value;
  const comment = withdrawComment.value.trim();
  if (!requestId || withdrawSubmitting.value) return;
  if (!comment) {
    workflowDetailError.value = '撤回原因不能为空';
    return;
  }
  withdrawSubmitting.value = true;
  workflowDetailError.value = '';
  try {
    await employeeApi.workflow.withdrawRequest(requestId, { comment });
    withdrawComment.value = '';
    await loadWorkflowRequests();
    showToast('申请已撤回');
  } catch (error) {
    workflowDetailError.value = error instanceof Error ? error.message : '撤回失败';
  } finally {
    withdrawSubmitting.value = false;
  }
}

async function downloadWorkflowAttachment(file: WorkflowRequestAttachmentResponse) {
  const requestId = selectedRequestId.value;
  if (!requestId) return;
  const actionKey = `wf-download-${file.attachmentRefId}`;
  fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
  try {
    const ticket = await employeeApi.workflow.createAttachmentDownloadTicket(requestId, file.attachmentRefId, {
      downloadType: 'SINGLE',
      reason: '下载附件',
    });
    window.open(resolveDownloadUrl(ticket, employeeApi.workflow.downloadAttachmentUrl(ticket.ticketNo)), '_blank', 'noopener');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '下载失败');
  } finally {
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
  }
}

async function previewWorkflowAttachment(file: WorkflowRequestAttachmentResponse) {
  const requestId = selectedRequestId.value;
  if (!requestId) return;
  const actionKey = `wf-preview-${file.attachmentRefId}`;
  fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
  try {
    const ticket = await employeeApi.workflow.createAttachmentDownloadTicket(requestId, file.attachmentRefId, {
      downloadType: 'SINGLE',
      reason: '预览附件',
    });
    await openFilePreviewFromUrl(file, resolveDownloadUrl(ticket, employeeApi.workflow.downloadAttachmentUrl(ticket.ticketNo)));
  } catch (error) {
    showToast(error instanceof Error ? error.message : '预览失败');
  } finally {
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
  }
}

async function refreshAttendancePanel() {
  if (attendanceLoading.value) return;
  attendanceLoading.value = true;
  attendanceError.value = '';
  const range = thisMonthRange();
  try {
    const [dailyResults, exceptions] = await Promise.all([
      employeeApi.attendance.dailyResults(range),
      employeeApi.attendance.exceptions(range),
    ]);
    attendanceDailyResults.value = dailyResults;
    attendanceExceptions.value = exceptions;
    const subordinateResults = await Promise.all(
      session.subordinates.map((user) => employeeApi.attendance.teamDailyResults({ ...range, userId: user.userId }).catch(() => [])),
    );
    teamAttendanceDailyResults.value = subordinateResults.flat();
  } catch (error) {
    attendanceError.value = error instanceof Error ? error.message : '考勤加载失败';
  } finally {
    attendanceLoading.value = false;
  }
}

async function punchAttendance(punchType: 'ON_DUTY' | 'OFF_DUTY') {
  if (punchLoading.value) return;
  punchLoading.value = punchType;
  attendanceError.value = '';
  try {
    punchResult.value = await employeeApi.attendance.punch({
      punchType,
      sourceType: 'PC',
      deviceId: 'employee-web',
    });
    await refreshAttendancePanel();
    showToast(`${punchTypeLabel(punchType)}打卡成功`);
  } catch (error) {
    attendanceError.value = error instanceof Error ? error.message : '打卡失败';
  } finally {
    punchLoading.value = '';
  }
}

async function submitClockCorrectionRequest() {
  clockCorrectionError.value = '';
  clockCorrectionResult.value = null;
  const targetDate = clockCorrectionForm.value.targetDate;
  const reason = clockCorrectionForm.value.reason.trim();
  if (!targetDate || !clockCorrectionForm.value.expectedPunchAt || !reason) {
    clockCorrectionError.value = '请完整填写补卡申请';
    return;
  }
  clockCorrectionSubmitting.value = true;
  try {
    clockCorrectionResult.value = await employeeApi.workflow.submitClockCorrectionRequest({
      targetDate,
      missingPunchType: clockCorrectionForm.value.missingPunchType,
      expectedPunchAt: toApiDateTime(clockCorrectionForm.value.expectedPunchAt),
      reason,
      attachmentRefIds: clockCorrectionAttachmentIds.value,
    });
    clockCorrectionForm.value.reason = '';
    clockCorrectionAttachmentIds.value = [];
    await Promise.all([workflow.loadTodos().then(() => shell.mergeBackendTodos(workflow.todos)), loadWorkflowRequests()]);
    showToast('补卡申请已提交');
  } catch (error) {
    clockCorrectionError.value = error instanceof Error ? error.message : '提交失败';
  } finally {
    clockCorrectionSubmitting.value = false;
  }
}

async function openMobileReset() {
  mobileDraft.value = session.profile?.mobile ?? '';
  mobileUpdateNotice.value = '';
  mobileUpdateError.value = '';
  selectSettingsItem('security');
  await nextTick();
  mobileInput.value?.focus();
}

async function submitMobileUpdate() {
  const mobile = mobileDraft.value.trim();
  if (mobileSubmitting.value) return;
  mobileUpdateNotice.value = '';
  mobileUpdateError.value = '';
  mobileSubmitting.value = true;
  try {
    await employeeApi.me.updateProfile({ mobile: mobile || null });
    await session.loadCurrentUser();
    await session.loadOrgProfile();
    shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    mobileDraft.value = session.profile?.mobile ?? '';
    mobileUpdateNotice.value = '手机号已更新';
    showToast('手机号已更新');
  } catch (error) {
    mobileUpdateError.value = error instanceof Error ? error.message : '手机号更新失败';
    showToast(mobileUpdateError.value);
  } finally {
    mobileSubmitting.value = false;
  }
}

async function sendPasswordChangeCode() {
  const email = currentEmail.value;
  if (!email || passwordChangeCodeSending.value) return;
  passwordChangeCodeSending.value = true;
  passwordChangeNotice.value = '';
  passwordChangeError.value = '';
  try {
    await session.sendPasswordResetCode(email);
    passwordChangeNotice.value = '验证码已发送至当前邮箱';
    showToast(passwordChangeNotice.value);
  } catch (error) {
    passwordChangeError.value = error instanceof Error ? error.message : '验证码发送失败';
    showToast(passwordChangeError.value);
  } finally {
    passwordChangeCodeSending.value = false;
  }
}

async function submitPasswordChange() {
  const email = currentEmail.value;
  const code = passwordChangeForm.value.code.trim();
  const newPassword = passwordChangeForm.value.newPassword;
  if (!email || !code || newPassword.length < 8 || passwordChangeSubmitting.value) return;
  passwordChangeSubmitting.value = true;
  passwordChangeNotice.value = '';
  passwordChangeError.value = '';
  try {
    await session.resetPassword({ email, code, newPassword });
    passwordChangeForm.value.code = '';
    passwordChangeForm.value.newPassword = '';
    passwordChangeNotice.value = '密码已更新，下次登录请使用新密码';
    showToast(passwordChangeNotice.value);
  } catch (error) {
    passwordChangeError.value = error instanceof Error ? error.message : '密码修改失败';
    showToast(passwordChangeError.value);
  } finally {
    passwordChangeSubmitting.value = false;
  }
}

function openSendFileTarget(file: PersonalFileRecord) {
  if (!file?.attachmentRefId || isFileTrashed(file)) return;
  fileSendTarget.value = { file, caption: '', submitting: false };
  fileSendTargetQuery.value = '';
  fileSendTargetError.value = '';
}

function closeSendFileTarget() {
  if (fileSendTarget.value?.submitting) return;
  fileSendTarget.value = null;
  fileSendTargetQuery.value = '';
  fileSendTargetError.value = '';
}

async function confirmSendFileTarget(conversationId: EntityId) {
  const target = fileSendTarget.value;
  if (!target || target.submitting) return;
  if (!target.file.attachmentRefId) return;
  target.submitting = true;
  fileSendTargetError.value = '';
  try {
    await im.sendFileMessage(conversationId, target.file.attachmentRefId, target.caption.trim(), session.user?.userId);
    const messages = im.messagesByConversation[String(conversationId)];
    if (messages?.length) shell.mergeBackendMessages(String(conversationId), messages);
    shell.mergeBackendConversations(im.conversations);
    fileSendTarget.value = null;
    fileSendTargetQuery.value = '';
    showToast('文件已发送');
  } catch (error) {
    target.submitting = false;
    fileSendTargetError.value = error instanceof Error ? error.message : '文件发送失败';
  }
}
function selectNav(section: SectionId) {
  navigateSection(section);
  shell.setMobileView('list');
}

function openTodoAction(mode: 'approve' | 'reject') {
  if (!hasActiveTodo.value || workflow.actionLoading) return;
  todoActionMode.value = mode;
  todoActionComment.value = '';
  todoActionError.value = '';
}

function closeTodoAction() {
  if (workflow.actionLoading) return;
  todoActionMode.value = null;
  todoActionComment.value = '';
  todoActionError.value = '';
}

async function submitTodoAction() {
  const todo = activeTodo.value;
  if (!todo.id || !todo.taskId || !todoActionMode.value) return;
  const comment = todoActionComment.value.trim();
  if (todoActionMode.value === 'reject' && !comment) {
    todoActionError.value = '驳回原因不能为空';
    return;
  }
  todoActionError.value = '';
  try {
    if (todoActionMode.value === 'approve') {
      await workflow.approveTask(todo.taskId, comment);
      showToast('已通过');
    } else {
      await workflow.rejectTask(todo.taskId, comment);
      showToast('已驳回');
    }
    shell.mergeBackendTodos(workflow.todos);
    closeTodoAction();
  } catch (error) {
    todoActionError.value = error instanceof Error ? error.message : '提交失败';
  }
}

function setAuthMode(mode: 'login' | 'reset') {
  authMode.value = mode;
  session.error = '';
  authNotice.value = '';
}

async function loadAuthenticatedWorkspace() {
  if (!session.isAuthenticated) return;
  await Promise.all([
    session.loadOrgProfile().then(() => {
      shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    }),
    workflow.loadTodos().then(() => {
      shell.mergeBackendTodos(workflow.todos);
    }),
    im.loadConversations().then(() => {
      shell.mergeBackendConversations(im.conversations);
    }),
    refreshFileCenter(),
    refreshWorkbenchData(),
    loadWorkflowRequests(),
    loadAgents(),
    hydrateUserPreferences(),
  ]);
  await im.loadVisibleConversationDetails();
  shell.mergeBackendConversations(im.conversations);
  shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
  void refreshCurrentUserAvatarUrl();
  if (shell.conversations.length) {
    await syncConversationMessages(shell.active.messages);
  }
  connectImSocket();
}

async function hydrateUserPreferences() {
  try {
    const prefs = await employeeApi.me.preferences();
    shell.hydratePreferencesFromServer(prefs);
  } catch {
    // 服务器没有偏好记录时维持本地缺省值。
  } finally {
    preferencesHydrated.value = true;
  }
}

function schedulePreferencePush() {
  if (!preferencesHydrated.value) return;
  if (preferencePushTimer) window.clearTimeout(preferencePushTimer);
  preferencePushTimer = window.setTimeout(async () => {
    preferencePushTimer = undefined;
    try {
      await employeeApi.me.updatePreferences({
        theme: shell.theme,
        messageDensity: shell.messageDensity,
        listWidth: shell.listWidth,
      });
    } catch {
      // 远端更新失败不影响本地体验，下次自动重试。
    }
  }, 600);
}

async function refreshCurrentUserAvatarUrl() {
  const refId = session.profile?.avatarAttachmentRefId;
  if (!refId) {
    currentAvatarUrl.value = '';
    return;
  }
  try {
    const ticket = await employeeApi.files.createDownloadTicket(refId, { downloadType: 'INLINE' });
    currentAvatarUrl.value = resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo));
  } catch {
    currentAvatarUrl.value = '';
  }
}

function openAvatarPicker() {
  if (avatarUploading.value) return;
  avatarUpdateError.value = '';
  avatarPickerInput.value?.click();
}

async function handleAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (!file.type.startsWith('image/')) {
    avatarUpdateError.value = '请选择图片文件';
    showToast(avatarUpdateError.value);
    return;
  }
  avatarUploading.value = true;
  avatarUpdateError.value = '';
  try {
    const attachmentRefId = await uploadPersonalFile(file);
    if (!attachmentRefId) {
      avatarUpdateError.value = uploadResult.value?.text || '头像上传失败';
      return;
    }
    await employeeApi.me.updateProfile({ avatarAttachmentRefId: attachmentRefId });
    await session.loadOrgProfile();
    shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    await refreshCurrentUserAvatarUrl();
    showToast('头像已更新');
  } catch (error) {
    avatarUpdateError.value = error instanceof Error ? error.message : '头像更新失败';
    showToast(avatarUpdateError.value);
  } finally {
    avatarUploading.value = false;
  }
}

async function submitLogin() {
  authNotice.value = '';
  try {
    await session.login({
      loginName: loginName.value.trim(),
      password: password.value,
    });
    password.value = '';
    await loadAuthenticatedWorkspace();
  } catch {
    // session.error 已经承载后端返回的业务错误。
  }
}

async function sendResetCode() {
  const email = resetEmail.value.trim();
  if (!email || resetCodeSending.value) return;
  resetCodeSending.value = true;
  authNotice.value = '';
  try {
    await session.sendPasswordResetCode(email);
    authNotice.value = '验证码已发送';
  } catch {
    // session.error 已经承载后端返回的业务错误。
  } finally {
    resetCodeSending.value = false;
  }
}

async function submitPasswordReset() {
  authNotice.value = '';
  try {
    await session.resetPassword({
      email: resetEmail.value.trim(),
      code: resetCode.value.trim(),
      newPassword: resetPasswordValue.value,
    });
    resetCode.value = '';
    resetPasswordValue.value = '';
    authNotice.value = '密码已重置';
    authMode.value = 'login';
  } catch {
    // session.error 已经承载后端返回的业务错误。
  }
}

async function syncConversationMessages(id: string) {
  const conversationId = id.trim();
  if (!conversationId) return;
  if (!session.isAuthenticated) return;
  await Promise.all([
    im.loadMessages(conversationId, session.user?.userId),
    im.loadConversationDetail(conversationId).catch(() => undefined),
  ]);
  shell.mergeBackendConversations(im.conversations);
  shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
  const messages = im.messagesByConversation[id];
  if (messages?.length) {
    shell.mergeBackendMessages(id, messages);
    const lastSeq = im.lastMessageSeqByConversation[id];
    const lastReadSeq = im.lastReadSeqByConversation[id] ?? 0;
    if (typeof lastSeq === 'number' && lastSeq > lastReadSeq) {
      try {
        const updated = await im.updateReadPosition(conversationId, lastSeq);
        const conversation = shell.conversations.find((item) => item.id === id);
        if (conversation) {
          conversation.unread = updated.unreadCount;
          conversation.unreadLabel = updated.unreadCount > 0 ? '未读消息' : '已读';
        }
      } catch {
        // 已读回写失败不阻断消息展示
      }
    }
    await scrollMessagesToLatest('auto');
  }
}

function websocketUrl() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${window.location.host}/ws/employee/im`;
}

function closeImSocket() {
  if (imSocketReconnectTimer) {
    window.clearTimeout(imSocketReconnectTimer);
    imSocketReconnectTimer = undefined;
  }
  if (imSocketHeartbeatTimer) {
    window.clearInterval(imSocketHeartbeatTimer);
    imSocketHeartbeatTimer = undefined;
  }
  if (imSocket) {
    imSocket.onopen = null;
    imSocket.onclose = null;
    imSocket.onerror = null;
    imSocket.onmessage = null;
    imSocket.close();
    imSocket = null;
  }
  wsConnected.value = false;
}

function scheduleImSocketReconnect() {
  if (!session.isAuthenticated || imSocketReconnectTimer) return;
  const delay = Math.min(10000, 800 + imSocketReconnectAttempts * 1200);
  imSocketReconnectAttempts += 1;
  imSocketReconnectTimer = window.setTimeout(() => {
    imSocketReconnectTimer = undefined;
    connectImSocket();
  }, delay);
}

function connectImSocket() {
  if (!session.isAuthenticated || imSocket) return;
  try {
    const socket = new WebSocket(websocketUrl());
    imSocket = socket;
    socket.onopen = () => {
      wsConnected.value = true;
      imSocketReconnectAttempts = 0;
      void refreshImAfterReconnect();
      if (imSocketHeartbeatTimer) window.clearInterval(imSocketHeartbeatTimer);
      imSocketHeartbeatTimer = window.setInterval(() => {
        if (socket.readyState === WebSocket.OPEN) socket.send('PING');
      }, 30000);
    };
    socket.onmessage = (event) => {
      void handleImSocketMessage(event.data);
    };
    socket.onclose = () => {
      if (imSocket === socket) imSocket = null;
      wsConnected.value = false;
      if (imSocketHeartbeatTimer) {
        window.clearInterval(imSocketHeartbeatTimer);
        imSocketHeartbeatTimer = undefined;
      }
      scheduleImSocketReconnect();
    };
    socket.onerror = () => {
      socket.close();
    };
  } catch {
    scheduleImSocketReconnect();
  }
}

async function refreshImAfterReconnect() {
  if (!session.isAuthenticated) return;
  try {
    await im.loadConversations();
    shell.mergeBackendConversations(im.conversations);
    if (shell.active.messages) {
      await syncConversationMessages(shell.active.messages);
    }
  } catch {
    return;
  }
}

async function handleImSocketMessage(raw: string) {
  let event: {
    eventType?: string;
    conversationId?: string | number;
    payload?: unknown;
  };
  try {
    event = parseJsonPreservingUnsafeIntegers(raw);
  } catch {
    return;
  }
  if (!event.eventType || event.eventType === 'CONNECTED' || event.eventType === 'PONG') return;
  const conversationId = event.conversationId ? String(event.conversationId) : '';
  if (!conversationId) {
    await im.loadConversations();
    shell.mergeBackendConversations(im.conversations);
    return;
  }
  if (['IM_MESSAGE_CREATED', 'IM_AGENT_MESSAGE_CREATED', 'IM_MESSAGE_EDITED', 'IM_MESSAGE_RECALLED', 'IM_AGENT_REPLY_FAILED'].includes(event.eventType)) {
    if (isImMessageResponse(event.payload)) {
      im.upsertMessage(conversationId, mapImMessageToConversationMessage(event.payload, session.user?.userId));
      shell.mergeBackendMessages(conversationId, im.messagesByConversation[conversationId] ?? []);
    }
    if (shell.active.messages === conversationId) {
      await syncConversationMessages(conversationId);
    } else {
      await im.loadConversations();
      shell.mergeBackendConversations(im.conversations);
    }
    return;
  }
  if (['IM_CONVERSATION_PREFERENCE_UPDATED', 'IM_GROUP_PROFILE_UPDATED', 'IM_GROUP_MEMBERS_ADDED', 'IM_GROUP_MEMBERS_REMOVED', 'IM_GROUP_MEMBER_LEFT', 'IM_GROUP_DISMISSED'].includes(event.eventType)) {
    await im.loadConversations();
    shell.mergeBackendConversations(im.conversations);
    if (shell.active.messages === conversationId) {
      await im.loadConversationDetail(conversationId).catch(() => undefined);
      shell.mergeBackendConversations(im.conversations);
    }
  }
}

function isImMessageResponse(value: unknown): value is ImMessageResponse {
  if (!value || typeof value !== 'object') return false;
  return 'messageId' in value && 'conversationId' in value && 'seqNo' in value;
}

function selectConversation(id: string) {
  navigateSection('messages', id);
  shell.selectItem('messages', id);
  void syncConversationMessages(id);
}

function backToList() {
  shell.setMobileView('list');
}

function openDetail() {
  shell.setDetailOpen(true);
}

function closeDetail() {
  shell.setDetailOpen(false);
}

function insertAgentMention() {
  if (!draft.value.includes('@Agent')) {
    draft.value = draft.value ? `${draft.value} @Agent ` : '@Agent ';
  }
}

async function scrollMessagesToLatest(behavior: ScrollBehavior = 'smooth') {
  await nextTick();
  const stream = messageStream.value;
  if (!stream) return;
  stream.scrollTo({ top: stream.scrollHeight, behavior });
}

async function sendMessage() {
  const text = draft.value.trim();
  if (!text || im.messageSending) return;
  if (!session.isAuthenticated) {
    showToast('未登录');
    return;
  }
  const conversationId = shell.active.messages.trim();
  if (!conversationId) {
    showToast('不可发送');
    return;
  }
  try {
    await im.sendMessage(conversationId, text, session.user?.userId);
    const conversationKey = String(conversationId);
    const messages = im.messagesByConversation[conversationKey];
    if (messages?.length) {
      shell.mergeBackendMessages(conversationKey, messages);
    }
    draft.value = '';
    await scrollMessagesToLatest();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '消息发送失败');
  }
}

async function sendSelectedFileToConversation(file: PersonalFileRecord) {
  if (!file?.attachmentRefId) return;
  if (isFileTrashed(file)) {
    showToast('已删除的文件不能发送');
    return;
  }
  const conversationId = activeConversationId();
  if (!conversationId || activeSection.value !== 'messages') {
    openSendFileTarget(file);
    return;
  }
  try {
    await im.sendFileMessage(conversationId, file.attachmentRefId, draft.value, session.user?.userId);
    const messages = im.messagesByConversation[String(conversationId)];
    if (messages?.length) {
      shell.mergeBackendMessages(String(conversationId), messages);
    }
    draft.value = '';
    await scrollMessagesToLatest();
    showToast('文件已发送');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '文件发送失败');
  }
}

function selectFilter(filter: SectionFilter) {
  shell.setFilter(activeSection.value, filter);
}

async function scrollFilters(direction: 'prev' | 'next') {
  const scroller = filterScroller.value;
  if (!scroller) return;
  if (scroller.scrollWidth <= scroller.clientWidth + 1) {
    const options = filterOptions.value;
    const currentIndex = options.findIndex((item) => item === activeFilter.value);
    const nextIndex = direction === 'next'
      ? (currentIndex + 1) % options.length
      : (currentIndex - 1 + options.length) % options.length;
    const nextFilter = options[nextIndex];
    if (nextFilter) shell.setFilter(activeSection.value, nextFilter);
    await updateFilterOverflow();
    return;
  }
  const step = Math.max(96, Math.round(scroller.clientWidth * 0.72));
  const maxScrollLeft = Math.max(0, scroller.scrollWidth - scroller.clientWidth);
  const current = scroller.scrollLeft;
  let left = direction === 'next' ? current + step : current - step;
  if (direction === 'next' && current >= maxScrollLeft - 2) left = 0;
  if (direction === 'prev' && current <= 2) left = maxScrollLeft;
  scroller.scrollTo({ left: Math.max(0, Math.min(maxScrollLeft, left)), behavior: 'smooth' });
  window.setTimeout(() => {
    void updateFilterOverflow();
  }, 220);
}

async function updateFilterOverflow() {
  await nextTick();
  const scroller = filterScroller.value;
  filtersOverflow.value = Boolean(scroller && scroller.scrollWidth > scroller.clientWidth + 1);
}

async function sendBindEmailCode() {
  const email = bindEmail.value.trim();
  if (!email || bindEmailCodeSending.value) return;
  bindEmailCodeSending.value = true;
  bindEmailNotice.value = '';
  bindEmailError.value = '';
  try {
    await employeeApi.auth.sendEmailCode({ email, purpose: 'BIND' });
    bindEmailNotice.value = '验证码已发送';
    showToast('验证码已发送');
  } catch (error) {
    bindEmailError.value = error instanceof Error ? error.message : '验证码发送失败';
    showToast(bindEmailError.value);
  } finally {
    bindEmailCodeSending.value = false;
  }
}

async function submitBindEmail() {
  const email = bindEmail.value.trim();
  const code = bindEmailCode.value.trim();
  if (!email || !code || bindEmailSubmitting.value) return;
  bindEmailSubmitting.value = true;
  bindEmailNotice.value = '';
  bindEmailError.value = '';
  try {
    const hadEmail = Boolean(currentEmail.value);
    await employeeApi.auth.bindEmail({ email, code });
    bindEmail.value = '';
    bindEmailCode.value = '';
    bindEmailEditing.value = false;
    await session.loadCurrentUser();
    await session.loadOrgProfile();
    shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    bindEmailNotice.value = hadEmail ? '邮箱已换绑' : '邮箱已绑定';
    showToast(bindEmailNotice.value);
  } catch (error) {
    bindEmailError.value = error instanceof Error ? error.message : '邮箱绑定失败';
    showToast(bindEmailError.value);
  } finally {
    bindEmailSubmitting.value = false;
  }
}

function startBindEmailEditing() {
  bindEmailEditing.value = true;
  bindEmail.value = '';
  bindEmailCode.value = '';
  bindEmailNotice.value = '';
  bindEmailError.value = '';
}

function cancelBindEmailEditing() {
  if (bindEmailSubmitting.value) return;
  bindEmailEditing.value = false;
  bindEmail.value = '';
  bindEmailCode.value = '';
  bindEmailError.value = '';
}

function startResize(event: PointerEvent) {
  resizing.value = true;
  const startX = event.clientX;
  const startWidth = shell.listWidth;
  const onMove = (moveEvent: PointerEvent) => {
    if (!resizing.value) return;
    shell.setListWidth(startWidth + (moveEvent.clientX - startX));
    void updateFilterOverflow();
  };
  const stop = () => {
    resizing.value = false;
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', stop);
    window.removeEventListener('pointercancel', stop);
  };
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', stop);
  window.addEventListener('pointercancel', stop);
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && detailVisible.value) closeDetail();
}

async function retryMessages() {
  if (!session.isAuthenticated) return;
  await im.loadConversations();
}

async function retryTodos() {
  if (!session.isAuthenticated) return;
  await workflow.loadTodos();
}

onMounted(async () => {
  window.addEventListener('keydown', handleKeydown);
  window.addEventListener('resize', updateFilterOverflow);
  if (!session.initialized) {
    await session.loadCurrentUser().catch(() => undefined);
    if (!session.isAuthenticated) {
      session.error = '';
    }
  }
  if (session.isAuthenticated) {
    void loadAuthenticatedWorkspace();
  }
  void updateFilterOverflow();
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown);
  window.removeEventListener('resize', updateFilterOverflow);
  if (toastTimer) window.clearTimeout(toastTimer);
  if (preferencePushTimer) window.clearTimeout(preferencePushTimer);
  closeImSocket();
});

watch(
  () => [shell.theme, shell.messageDensity, shell.listWidth] as const,
  () => {
    schedulePreferencePush();
  },
);

watch(() => session.profile?.avatarAttachmentRefId, () => {
  void refreshCurrentUserAvatarUrl();
});

watch([filterOptions, activeSection, searchOpen], () => {
  void updateFilterOverflow();
});

watch(() => shell.active.workbench, (id) => {
  if (!session.isAuthenticated) return;
  if (id === 'requests') void loadWorkflowRequests();
  if (id === 'attendance') void refreshAttendancePanel();
  if (id === 'balance' || id === 'leave') void refreshWorkbenchData();
  if (id === 'files') void refreshFileQuota();
  if (id === 'agent') void loadAgents();
}, { immediate: true });

watch(
  () => route.meta.section,
  (section) => {
    if (typeof section === 'string' && section !== shell.section) {
      shell.setSection(section as SectionId);
    }
  },
  { immediate: true },
);

watch(
  () => route.params.conversationId,
  (conversationId) => {
    if (typeof conversationId === 'string') {
      shell.selectItem('messages', conversationId);
      void syncConversationMessages(conversationId);
    }
  },
  { immediate: true },
);

watch(
  () => route.params.todoId,
  (todoId) => {
    if (typeof todoId === 'string') {
      shell.selectItem('todos', todoId);
    }
  },
  { immediate: true },
);

watch(
  () => route.params.app,
  (app) => {
    if (typeof app === 'string') {
      const canonical = normalizeWorkbenchApp(app);
      shell.selectItem('workbench', canonical);
      if (canonical !== app) {
        void router.replace({ name: 'employee-workbench-detail', params: { app: canonical } });
      }
    }
  },
  { immediate: true },
);

watch(
  () => route.params.panel,
  (panel) => {
    if (typeof panel === 'string') {
      const canonical = normalizeSettingsPanel(panel);
      shell.selectItem('settings', canonical);
      if (canonical !== panel) {
        void router.replace({ name: 'employee-settings-detail', params: { panel: canonical } });
      }
    }
  },
  { immediate: true },
);

watch(() => session.profile?.mobile, (mobile) => {
  if (!mobileSubmitting.value) {
    mobileDraft.value = mobile ?? '';
  }
}, { immediate: true });

watch(() => session.isAuthenticated, (authenticated) => {
  if (authenticated) {
    connectImSocket();
  } else {
    closeImSocket();
  }
});

const employeeShellContext = {
  shell,
  im,
  session,
  workflow,
  draft,
  authMode,
  loginName,
  password,
  resetEmail,
  resetCode,
  resetPasswordValue,
  searchOpen,
  resizing,
  toastMessage,
  messageStream,
  filterScroller,
  filtersOverflow,
  personalFileInput,
  resetCodeSending,
  fileUploading,
  bindEmail,
  bindEmailCode,
  bindEmailCodeSending,
  bindEmailSubmitting,
  bindEmailEditing,
  bindEmailNotice,
  bindEmailError,
  mobileDraft,
  mobileSubmitting,
  mobileUpdateNotice,
  mobileUpdateError,
  mobileInput,
  passwordChangeForm,
  passwordChangeCodeSending,
  passwordChangeSubmitting,
  passwordChangeNotice,
  passwordChangeError,
  fileSendTarget,
  fileSendTargetQuery,
  fileSendTargetError,
  currentAvatarUrl,
  avatarUploading,
  avatarUpdateError,
  avatarPickerInput,
  colleagues,
  colleaguesLoading,
  colleaguesError,
  authNotice,
  leaveAccounts,
  attendanceExceptions,
  workbenchLoading,
  todoActionMode,
  todoActionComment,
  todoActionError,
  uploadPanelOpen,
  uploadDraftFile,
  uploadDragActive,
  uploadResult,
  filePreview,
  filePreviewLoading,
  fileActionLoading,
  createConversationOpen,
  createConversationMode,
  createConversationForm,
  createConversationSubmitting,
  createConversationError,
  groupPanelOpen,
  groupProfileForm,
  groupActionError,
  messageActionTarget,
  messageEditText,
  messageActionError,
  agentTaskForm,
  agentTaskSubmitting,
  agentTaskResult,
  agentTaskError,
  agents,
  agentsLoading,
  wsConnected,
  leaveForm,
  leaveAttachmentIds,
  leaveSubmitting,
  leaveResult,
  leaveError,
  workflowRequests,
  workflowRequestsTotal,
  workflowRequestsLoading,
  workflowRequestsError,
  selectedRequestId,
  workflowDetail,
  workflowActions,
  workflowDetailLoading,
  workflowDetailError,
  withdrawComment,
  withdrawSubmitting,
  attendanceDailyResults,
  teamAttendanceDailyResults,
  attendanceLoading,
  attendanceError,
  punchLoading,
  punchResult,
  clockCorrectionForm,
  clockCorrectionAttachmentIds,
  clockCorrectionSubmitting,
  clockCorrectionResult,
  clockCorrectionError,
  sectionIcons,
  workbenchIcons,
  profileIcons,
  activeSection,
  currentMeta,
  currentUserName,
  currentUserShortName,
  detailVisible,
  listWidth,
  mobileStage,
  activeConversation,
  activeTodo,
  activeFile,
  activeWorkbench,
  activeContact,
  activeProfile,
  pendingTodoCount,
  fileUsageCircumference,
  activeFileUsage,
  activeFileUsageOffset,
  themeLabel,
  searchModel,
  filterOptions,
  activeFilter,
  filteredConversations,
  filteredTodos,
  filteredFiles,
  filteredWorkbench,
  filteredContacts,
  filteredProfile,
  mobileNavItems,
  listEmptyText,
  showFilterBar,
  showFilterArrows,
  todoActionTitle,
  activeConversationTitle,
  activeConversationCurrentStep,
  mustHandleTodos,
  approvalTodos,
  deferredTodos,
  workbenchLaunchers,
  agentConversation,
  annualLeaveAccount,
  workbenchHighlights,
  selectedProfileId,
  profileStatusLabel,
  profileIdentityRows,
  accountSecurityRows,
  currentEmail,
  organizationRows,
  settingsMetrics,
  storageRows,
  recentFileRows,
  preferenceRows,
  selectedLeaveAccount,
  activeWorkbenchTitle,
  activeConversationDetail,
  activeGroupProfile,
  activeConversationPreferenceRows,
  selectedRequest,
  workflowDetailRows,
  attendanceSummary,
  teamAttendanceRows,
  filePreviewTitle,
  filePreviewType,
  relationTree,
  hasActiveConversation,
  hasActiveTodo,
  businessEmptyTitle,
  authModeTitle,
  authModeSubtitle,
  currentListLoading,
  currentListError,
  displayField,
  employmentStatusLabel,
  percentage,
  showToast,
  toneOfConversation,
  toneOfStatus,
  statusText,
  runAction,
  selectWorkbenchItem,
  sha256Hex,
  fileSizeLabel,
  fileTypeLabel,
  fileTypeLabelFromName,
  fileNameOf,
  fileSizeOf,
  fileMimeOf,
  fileIconOf,
  fileToneOf,
  canInlinePreview,
  isFileTrashed,
  minutesToDayLabel,
  formatDateTime,
  toApiDateTime,
  bizTypeLabel,
  punchTypeLabel,
  actionTypeLabel,
  entityIdListFromText,
  activeConversationId,
  isCurrentUserMessage,
  loadAgents,
  openCreateConversation,
  closeCreateConversation,
  submitCreateConversation,
  openGroupPanel,
  closeGroupPanel,
  saveGroupProfile,
  addGroupMembers,
  removeGroupMembers,
  leaveActiveGroup,
  dismissActiveGroup,
  toggleActiveConversationPreference,
  openMessageAction,
  closeMessageAction,
  submitMessageEdit,
  submitMessageRecall,
  openImAttachment,
  submitAgentTask,
  confirmAgentTask,
  cancelAgentTask,
  thisMonthRange,
  refreshWorkbenchData,
  refreshFileQuota,
  refreshPersonalFiles,
  refreshFileCenter,
  handlePersonalFileSelected,
  openUploadPanel,
  closeUploadPanel,
  openFilePicker,
  handleUploadDrop,
  submitPersonalFileUpload,
  uploadedPersonalFile,
  uploadPersonalFile,
  uploadToTicketTarget,
  resolveDownloadUrl,
  createPersonalDownloadTicket,
  openPersonalFilePreview,
  openFilePreviewFromUrl,
  downloadPersonalFile,
  trashPersonalFile,
  restorePersonalFile,
  purgePersonalFile,
  closeFilePreview,
  openPreviewExternal,
  submitLeaveRequest,
  loadWorkflowRequests,
  loadWorkflowRequestDetail,
  withdrawWorkflowRequest,
  downloadWorkflowAttachment,
  previewWorkflowAttachment,
  refreshAttendancePanel,
  punchAttendance,
  submitClockCorrectionRequest,
  openMobileReset,
  submitMobileUpdate,
  sendPasswordChangeCode,
  submitPasswordChange,
  openSendFileTarget,
  closeSendFileTarget,
  confirmSendFileTarget,
  openAvatarPicker,
  handleAvatarSelected,
  selectNav,
  openTodoAction,
  closeTodoAction,
  submitTodoAction,
  setAuthMode,
  loadAuthenticatedWorkspace,
  submitLogin,
  sendResetCode,
  submitPasswordReset,
  syncConversationMessages,
  websocketUrl,
  closeImSocket,
  scheduleImSocketReconnect,
  connectImSocket,
  refreshImAfterReconnect,
  handleImSocketMessage,
  isImMessageResponse,
  selectConversation,
  backToList,
  openDetail,
  closeDetail,
  insertAgentMention,
  scrollMessagesToLatest,
  sendMessage,
  sendSelectedFileToConversation,
  selectFilter,
  scrollFilters,
  updateFilterOverflow,
  selectSettingsItem,
  sendBindEmailCode,
  submitBindEmail,
  startBindEmailEditing,
  cancelBindEmailEditing,
  startResize,
  handleKeydown,
  retryMessages,
  retryTodos,
};

provideEmployeeShellContext(employeeShellContext);

</script>

<template>
  <main v-if="!session.initialized && session.loading" class="auth-shell auth-shell-loading" :data-theme="shell.theme">
    <section class="auth-card loading-card">
      <AppLoadingState />
    </section>
  </main>

  <main v-else-if="!session.isAuthenticated" class="auth-shell" :data-theme="shell.theme">
    <section class="auth-hero" aria-label="员工端登录">
      <div class="auth-brand-row">
        <div class="brand-mark">企</div>
        <button class="theme-toggle auth-theme-toggle" type="button" @click="shell.toggleTheme()">
          <component :is="shell.theme === 'light' ? MoonStar : SunMedium" :size="16" />
          <span>{{ themeLabel }}</span>
        </button>
      </div>
      <div class="auth-hero-copy">
        <p>员工端</p>
        <h1>企业协同</h1>
      </div>
    </section>

    <section class="auth-card" :aria-label="authModeTitle">
      <div class="auth-mode-tabs" role="tablist" aria-label="认证方式">
        <button type="button" :class="{ active: authMode === 'login' }" @click="setAuthMode('login')">登录</button>
        <button type="button" :class="{ active: authMode === 'reset' }" @click="setAuthMode('reset')">找回</button>
      </div>

      <div class="auth-card-head">
        <h2>{{ authModeTitle }}</h2>
        <p v-if="authModeSubtitle">{{ authModeSubtitle }}</p>
      </div>

      <form v-if="authMode === 'login'" class="auth-form" @submit.prevent="submitLogin">
        <label class="auth-field">
          <span>登录名</span>
          <AppInput v-model="loginName" placeholder="员工登录名" />
        </label>
        <label class="auth-field">
          <span>密码</span>
          <AppInput v-model="password" type="password" placeholder="密码" />
        </label>
        <p v-if="session.error" class="auth-error">{{ session.error }}</p>
        <p v-if="authNotice" class="auth-notice">{{ authNotice }}</p>
        <AppButton variant="primary" size="lg" block type="submit" :disabled="session.loading || !loginName.trim() || !password">
          {{ session.loading ? '登录中' : '进入工作区' }}
        </AppButton>
      </form>

      <form v-else class="auth-form" @submit.prevent="submitPasswordReset">
        <label class="auth-field">
          <span>绑定邮箱</span>
          <AppInput v-model="resetEmail" type="email" placeholder="已绑定邮箱" />
        </label>
        <div class="auth-code-row">
          <label class="auth-field">
            <span>验证码</span>
            <AppInput v-model="resetCode" placeholder="验证码" />
          </label>
          <AppButton
            variant="secondary"
            type="button"
            :disabled="resetCodeSending || !resetEmail.trim()"
            @click="sendResetCode"
          >
            {{ resetCodeSending ? '发送中' : '发送验证码' }}
          </AppButton>
        </div>
        <label class="auth-field">
          <span>新密码</span>
          <AppInput v-model="resetPasswordValue" type="password" placeholder="8-64 位新密码" />
        </label>
        <p v-if="session.error" class="auth-error">{{ session.error }}</p>
        <p v-if="authNotice" class="auth-notice">{{ authNotice }}</p>
        <AppButton
          variant="primary"
          size="lg"
          block
          type="submit"
          :disabled="session.loading || !resetEmail.trim() || !resetCode.trim() || resetPasswordValue.length < 8"
        >
          {{ session.loading ? '提交中' : '重置密码' }}
        </AppButton>
      </form>
    </section>
  </main>

  <main
    v-else
    class="employee-shell"
    :class="[
      `section-${activeSection}`,
      `density-${shell.messageDensity}`,
      { 'detail-open': detailVisible, 'mobile-detail-stage': mobileStage === 'detail' },
    ]"
    :data-theme="shell.theme"
    :style="{ '--list-width': listWidth }"
  >
    <input ref="personalFileInput" class="visually-hidden-input" type="file" @change="handlePersonalFileSelected" />

    <aside class="side-rail" aria-label="员工端功能">
      <div class="brand-block">
        <div class="brand-mark">企</div>
        <div class="brand-copy">
          <strong>企业协同</strong>
        </div>
      </div>

      <nav class="rail-nav" aria-label="主功能">
        <ShellNavButton
          v-for="item in primaryNavItems"
          :key="item.id"
          :active="item.id === activeSection"
          @click="selectNav(item.id)"
        >
          <template #icon>
            <component :is="sectionIcons[item.id]" :size="19" stroke-width="2" />
          </template>
          {{ item.label }}
        </ShellNavButton>
      </nav>

      <div class="rail-footer">
        <button class="theme-toggle" type="button" @click="shell.toggleTheme()">
          <component :is="shell.theme === 'light' ? MoonStar : SunMedium" :size="16" />
          <span>{{ themeLabel }}</span>
        </button>
        <button class="profile-button" type="button" @click="selectNav('settings')">
          <AppAvatar :label="currentUserShortName" :image="currentAvatarUrl" tone="primary" size="lg" status="online" />
          <span class="profile-copy">
            <strong>{{ currentUserName }}</strong>
            <small>{{ session.positionName || session.departmentName }}</small>
          </span>
        </button>
      </div>
    </aside>

    <section class="list-pane" :class="{ 'mobile-visible': mobileStage === 'list' }">
      <header class="pane-head">
        <div class="pane-title-row">
          <div class="pane-head-copy">
            <h1 class="pane-title">{{ currentMeta.title }}</h1>
          </div>
          <div class="pane-actions">
            <AppBadge v-if="activeSection === 'todos' && pendingTodoCount" tone="danger" kind="count">
              {{ pendingTodoCount }}
            </AppBadge>
            <AppButton v-if="activeSection === 'messages'" variant="secondary" size="sm" @click="openCreateConversation('private')">
              私聊
            </AppButton>
            <AppButton v-if="activeSection === 'messages'" variant="secondary" size="sm" @click="openCreateConversation('group')">
              群聊
            </AppButton>
            <AppButton variant="ghost" size="sm" @click="searchOpen = !searchOpen">
              <Search :size="16" />
            </AppButton>
          </div>
        </div>

        <div v-if="searchOpen" class="search-wrap">
          <AppInput v-model="searchModel" type="search" :placeholder="currentMeta.search" />
        </div>

        <div v-if="showFilterBar" class="pane-filter-shell" :aria-label="`${currentMeta.title} 筛选`">
          <button v-if="showFilterArrows" class="filter-arrow" type="button" aria-label="上一组筛选" @click="scrollFilters('prev')">‹</button>
          <div ref="filterScroller" class="pane-filter-row">
            <AppButton
              v-for="filter in filterOptions"
              :key="filter"
              variant="ghost"
              size="sm"
              :selected="filter === activeFilter"
              @click="selectFilter(filter)"
            >
              {{ filter }}
            </AppButton>
          </div>
          <button v-if="showFilterArrows" class="filter-arrow" type="button" aria-label="下一组筛选" @click="scrollFilters('next')">›</button>
        </div>
      </header>

      <div class="list-body">
        <div v-if="currentListLoading" class="loading-wrap">
          <AppLoadingState />
        </div>

        <AppErrorState
          v-else-if="currentListError && activeSection === 'messages' && !filteredConversations.length"
          title="消息同步失败"
          :description="currentListError"
          action-label="重试"
          @retry="retryMessages"
        />

        <AppErrorState
          v-else-if="currentListError && activeSection === 'todos' && !filteredTodos.length"
          title="待办同步失败"
          :description="currentListError"
          action-label="重试"
          @retry="retryTodos"
        />

        <div v-else-if="activeSection === 'messages'" class="list-items">
          <AppEmptyState
            v-if="!filteredConversations.length"
            title="会话暂时为空"
            :description="listEmptyText"
          />
          <AppListItem
            v-for="item in filteredConversations"
            v-else
            :key="item.id"
            :active="item.id === activeConversation.id"
            @click="selectConversation(item.id)"
          >
            <div class="message-row" :class="`message-row-${item.kind === 'team' ? 'team' : item.kind === 'agent' ? 'agent' : 'private'}`">
              <div class="message-row-main">
                <div class="message-avatar-wrap">
                  <AppAvatar
                    :label="item.badge"
                    :tone="toneOfConversation(item.tone)"
                    :status="item.kind === 'agent' ? 'online' : ''"
                  />
                  <span v-if="item.kind === 'team'" class="conversation-kind-glyph kind-team" aria-hidden="true">
                    <UsersRound :size="11" />
                  </span>
                  <span v-else-if="item.kind === 'agent'" class="conversation-kind-glyph kind-agent" aria-hidden="true">
                    <Sparkles :size="11" />
                  </span>
                </div>
                <div class="message-copy">
                  <div class="message-title-line">
                    <strong>{{ item.title }}</strong>
                    <span v-if="item.kind === 'team'" class="conversation-kind-pill pill-team">群{{ item.memberCount ? ` · ${item.memberCount}` : '' }}</span>
                    <span v-else-if="item.kind === 'agent'" class="conversation-kind-pill pill-agent">AI</span>
                    <span v-else class="conversation-kind-pill pill-private">私聊</span>
                  </div>
                  <p>{{ item.subtitle }}</p>
                </div>
              </div>
              <div class="message-row-side">
                <span class="meta-time">{{ item.meta }}</span>
                <AppBadge v-if="item.unread" tone="danger" kind="count">{{ item.unread }}</AppBadge>
              </div>
            </div>
          </AppListItem>
        </div>

        <div v-else-if="activeSection === 'todos'" class="list-items">
          <AppEmptyState v-if="!filteredTodos.length" title="待办已清空" :description="listEmptyText" />
          <AppListItem
            v-for="item in filteredTodos"
            v-else
            :key="item.id"
            :active="item.id === activeTodo.id"
            @click="navigateSection('todos', item.id); shell.selectItem('todos', item.id)"
          >
            <div class="generic-row">
              <AppAvatar :label="item.priority" tone="warning" />
              <div class="generic-copy">
                <div class="title-line">
                  <strong>{{ item.title }}</strong>
                  <span class="meta-time">{{ item.time }}</span>
                </div>
                <p>{{ item.subtitle }}</p>
              </div>
              <AppTag :tone="toneOfStatus(item.status)">{{ statusText(item.status) }}</AppTag>
            </div>
          </AppListItem>
        </div>

        <div v-else-if="activeSection === 'files'" class="list-items">
          <AppEmptyState v-if="!filteredFiles.length" title="文件空间为空" :description="listEmptyText" />
          <AppListItem
            v-for="item in filteredFiles"
            v-else
            :key="item.id"
            :active="item.id === activeFile.id"
            @click="shell.selectItem('files', item.id)"
          >
            <div class="generic-row">
              <AppAvatar :label="item.label" tone="neutral" />
              <div class="generic-copy">
                <div class="title-line">
                  <strong>{{ item.title }}</strong>
                  <span class="meta-time">{{ item.subtitle }}</span>
                </div>
                <p>{{ item.note }}</p>
              </div>
            </div>
          </AppListItem>
        </div>

        <div v-else-if="activeSection === 'workbench'" class="list-items">
          <AppListItem
            v-for="item in workbenchLaunchers"
            :key="item.id"
            :active="item.id === activeWorkbench.id"
            :disabled="item.disabled"
            @click="!item.disabled && selectWorkbenchItem(item.id)"
          >
            <div class="generic-row">
              <span class="list-icon" :class="`tone-${item.tone}`">
                <component :is="workbenchIcons[item.id] ?? LayoutGrid" :size="18" />
              </span>
              <div class="generic-copy">
                <div class="title-line">
                  <strong>{{ item.title }}</strong>
                  <span class="meta-time">{{ item.status }}</span>
                </div>
                <p>{{ item.note }}</p>
              </div>
            </div>
          </AppListItem>
        </div>

        <div v-else-if="activeSection === 'contacts'" class="list-items">
          <AppEmptyState v-if="!filteredContacts.length" title="暂无联系人" description="暂无联系人" />
          <AppListItem
            v-for="item in filteredContacts"
            v-else
            :key="item.id"
            :active="activeContact?.id === item.id"
            @click="shell.selectItem('contacts', item.id)"
          >
            <div class="generic-row">
              <AppAvatar :label="item.name.slice(0, 1)" tone="primary" />
              <div class="generic-copy">
                <div class="title-line">
                  <strong>{{ item.name }}</strong>
                  <span class="meta-time">{{ item.relation }}</span>
                </div>
                <p>{{ item.department || item.role }}</p>
              </div>
            </div>
          </AppListItem>
        </div>

        <div v-else-if="activeSection === 'settings'" class="list-items">
          <AppListItem
            v-for="item in filteredProfile"
            :key="item.id"
            :active="item.id === activeProfile.id"
            @click="selectSettingsItem(item.id)"
          >
            <div class="generic-row">
              <span class="list-icon tone-gray">
                <component :is="profileIcons[item.id] ?? Settings" :size="18" />
              </span>
              <div class="generic-copy">
                <div class="title-line">
                  <strong>{{ item.title }}</strong>
                  <span class="meta-time">{{ item.subtitle }}</span>
                </div>
                <p v-if="item.note">{{ item.note }}</p>
              </div>
            </div>
          </AppListItem>
        </div>

        <div v-else class="list-items">
          <AppEmptyState :title="businessEmptyTitle" :description="listEmptyText" />
        </div>
      </div>
    </section>

    <div class="resize-handle" role="separator" aria-orientation="vertical" aria-label="调整列表宽度" @pointerdown.prevent="startResize" />

    <section class="main-pane" :class="{ 'mobile-visible': mobileStage === 'detail' }">
      <RouterView v-slot="{ Component }">
        <component :is="Component" />
      </RouterView>
    </section>

    <aside class="context-panel" :class="{ open: detailVisible }">
      <AppDrawer>
        <header class="context-head">
          <div>
            <h2>{{ activeSection === 'messages' ? activeConversation.title : currentMeta.title }}</h2>
          </div>
          <AppButton variant="ghost" size="sm" @click="shell.toggleDetail()">
            <component :is="detailVisible ? PanelLeftClose : PanelLeftOpen" :size="18" />
          </AppButton>
        </header>

        <template v-if="activeSection === 'messages' && hasActiveConversation">
          <section class="context-block">
            <h3>审核链路</h3>
            <div class="audit-chain">
              <span
                v-for="record in activeConversation.panel.auditTrail"
                :key="`${record.label}-${record.actor}`"
                class="audit-step"
                :class="record.statusTone"
              >
                <i />
                <span class="audit-copy">
                  <strong>{{ record.label }}</strong>
                  <em>{{ record.actor }} · {{ record.time }}</em>
                  <small>{{ record.detail }}</small>
                </span>
                <b>{{ record.status }}</b>
              </span>
            </div>
          </section>

          <section class="context-block">
            <h3>Agent 审核依据</h3>
            <div v-if="activeConversation.panel.agentEvidence?.length" class="stack-list">
              <span v-for="item in activeConversation.panel.agentEvidence" :key="item.label">
                <small>{{ item.label }}</small>
                <strong>{{ item.value }}</strong>
              </span>
            </div>
            <p v-else class="empty-inline">暂无审核依据</p>
          </section>

          <section class="context-block">
            <h3>相关材料</h3>
            <div v-if="activeConversation.panel.attachments.length" class="stack-list">
              <button
                v-for="file in activeConversation.panel.attachments"
                :key="file.id"
                class="attachment-item"
                type="button"
                @click="runAction({ action: 'switch-section', section: 'files', label: '查看材料' })"
              >
                <span class="attachment-type">{{ file.type }}</span>
                <span>
                  <strong>{{ file.name }}</strong>
                  <small>{{ file.size }} · {{ file.status }}</small>
                </span>
                <ArrowUpRight :size="14" />
              </button>
            </div>
            <p v-else class="empty-inline">无文件</p>
          </section>
        </template>

        <template v-else>
          <section class="context-block">
            <h3>可用操作</h3>
            <div class="task-list">
              <span v-if="currentMeta.primaryAction">{{ currentMeta.primaryAction.label }}</span>
              <span v-if="currentMeta.secondaryAction">{{ currentMeta.secondaryAction.label }}</span>
            </div>
          </section>
        </template>
      </AppDrawer>
    </aside>

    <nav class="mobile-rail" aria-label="底部导航">
      <button
        v-for="item in mobileNavItems"
        :key="item.id"
        type="button"
        class="mobile-rail-button"
        :class="{ active: item.id === activeSection }"
        @click="selectNav(item.id)"
      >
        <span class="mobile-icon-wrap">
          <component :is="sectionIcons[item.id]" :size="18" />
          <AppBadge v-if="item.id === 'todos' && pendingTodoCount" tone="danger" kind="count" class="mobile-badge">
            {{ pendingTodoCount }}
          </AppBadge>
        </span>
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <div v-if="activeSection === 'messages' && hasActiveConversation && mobileStage === 'detail' && detailVisible" class="mobile-detail-shell">
      <div class="mobile-detail-card">
        <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
          <ChevronLeft :size="22" />
        </button>
        <div class="mobile-detail-body">
          <p class="pane-kicker">会话详情</p>
          <h3>{{ activeConversation.title }}</h3>
          <section class="mobile-detail-section">
            <h4>流程进度</h4>
            <div class="context-steps">
              <span v-for="step in activeConversation.workflowSteps" :key="step.label" :class="step.status">
                <strong>{{ step.label }}</strong>
                {{ step.detail }}
              </span>
            </div>
          </section>
          <section class="mobile-detail-section">
            <h4>相关材料</h4>
            <div v-if="activeConversation.panel.attachments.length" class="stack-list">
              <span v-for="file in activeConversation.panel.attachments" :key="file.id">
                <strong>{{ file.name }}</strong>
                <small>{{ file.size }} · {{ file.status }}</small>
              </span>
            </div>
            <p v-else class="empty-inline">无文件</p>
          </section>
          <AppButton variant="secondary" block @click="closeDetail">关闭</AppButton>
        </div>
      </div>
    </div>

    <div v-if="toastMessage" class="toast-message" role="status" aria-live="polite">
      {{ toastMessage }}
    </div>

    <AppDialog v-if="todoActionMode">
      <form class="todo-action-dialog" @submit.prevent="submitTodoAction">
        <header class="dialog-head">
          <h2>{{ todoActionTitle }}</h2>
          <p>{{ activeTodo.title }}</p>
        </header>
        <label class="dialog-field">
          <span>{{ todoActionMode === 'reject' ? '驳回原因' : '审批意见' }}</span>
          <textarea
            v-model="todoActionComment"
            class="ui-textarea"
            :required="todoActionMode === 'reject'"
            :placeholder="todoActionMode === 'reject' ? '驳回原因' : '审批意见'"
            rows="5"
          />
        </label>
        <p v-if="todoActionError" class="dialog-error">{{ todoActionError }}</p>
        <div class="dialog-actions">
          <AppButton variant="ghost" type="button" :disabled="workflow.actionLoading" @click="closeTodoAction">
            取消
          </AppButton>
          <AppButton variant="primary" type="submit" :disabled="workflow.actionLoading">
            {{ workflow.actionLoading ? '提交中' : '提交' }}
          </AppButton>
        </div>
      </form>
    </AppDialog>

    <AppDialog v-if="createConversationOpen">
      <form class="conversation-dialog" @submit.prevent="submitCreateConversation">
        <header class="dialog-head dialog-head-row">
          <div>
            <h2>{{ createConversationMode === 'private' ? '新建私聊' : createConversationMode === 'group' ? '新建群聊' : 'Agent会话' }}</h2>
          </div>
          <button class="field-icon-button" type="button" :disabled="createConversationSubmitting" aria-label="关闭" @click="closeCreateConversation">
            <X :size="15" />
          </button>
        </header>

        <div class="conversation-mode-row">
          <button type="button" :class="{ active: createConversationMode === 'private' }" @click="createConversationMode = 'private'">私聊</button>
          <button type="button" :class="{ active: createConversationMode === 'group' }" @click="createConversationMode = 'group'">群聊</button>
          <button type="button" :class="{ active: createConversationMode === 'agent' }" @click="openCreateConversation('agent')">Agent</button>
        </div>

        <label v-if="createConversationMode === 'private'" class="security-field">
          <span>成员ID</span>
          <input v-model="createConversationForm.targetUserId" class="ui-inline-input" inputmode="numeric" />
        </label>

        <template v-else-if="createConversationMode === 'group'">
          <label class="security-field">
            <span>群名称</span>
            <input v-model="createConversationForm.groupTitle" class="ui-inline-input" />
          </label>
          <label class="security-field">
            <span>成员ID</span>
            <textarea v-model="createConversationForm.memberUserIds" class="ui-textarea compact-textarea" rows="3" />
          </label>
        </template>

        <label v-else class="security-field">
          <span>Agent</span>
          <select v-model="createConversationForm.agentId" class="ui-inline-input">
            <option v-for="agent in agents" :key="agent.agentId" :value="String(agent.agentId)">
              {{ agent.agentName }}
            </option>
          </select>
        </label>

        <p v-if="createConversationError" class="dialog-error">{{ createConversationError }}</p>
        <div class="dialog-actions">
          <AppButton variant="ghost" type="button" :disabled="createConversationSubmitting" @click="closeCreateConversation">
            取消
          </AppButton>
          <AppButton variant="primary" type="submit" :disabled="createConversationSubmitting">
            {{ createConversationSubmitting ? '创建中' : '创建' }}
          </AppButton>
        </div>
      </form>
    </AppDialog>

    <AppDialog v-if="groupPanelOpen">
      <section class="group-dialog">
        <header class="dialog-head dialog-head-row">
          <div>
            <h2>群管理</h2>
            <p>{{ activeGroupProfile?.title || activeConversation.title }}</p>
          </div>
          <button class="field-icon-button" type="button" :disabled="im.actionLoading" aria-label="关闭" @click="closeGroupPanel">
            <X :size="15" />
          </button>
        </header>

        <section class="dialog-section">
          <h3>群资料</h3>
          <label class="security-field">
            <span>群名称</span>
            <input v-model="groupProfileForm.title" class="ui-inline-input" />
          </label>
          <label class="security-field">
            <span>群公告</span>
            <textarea v-model="groupProfileForm.noticeText" class="ui-textarea compact-textarea" rows="3" />
          </label>
          <AppButton variant="primary" size="sm" :disabled="im.actionLoading" @click="saveGroupProfile">
            保存群资料
          </AppButton>
        </section>

        <section class="dialog-section">
          <h3>成员</h3>
          <div class="member-chip-list">
            <span v-for="member in activeConversation.panel.members" :key="`${member.name}-${member.role}`">
              {{ member.name }} · {{ member.role }}
            </span>
          </div>
          <label class="security-field">
            <span>添加成员ID</span>
            <input v-model="groupProfileForm.memberUserIds" class="ui-inline-input" />
          </label>
          <label class="security-field">
            <span>移除成员ID</span>
            <input v-model="groupProfileForm.removeMemberUserIds" class="ui-inline-input" />
          </label>
          <div class="form-actions">
            <AppButton variant="secondary" size="sm" :disabled="im.actionLoading || !groupProfileForm.memberUserIds.trim()" @click="addGroupMembers">
              添加成员
            </AppButton>
            <AppButton variant="secondary" size="sm" :disabled="im.actionLoading || !groupProfileForm.removeMemberUserIds.trim()" @click="removeGroupMembers">
              移除成员
            </AppButton>
          </div>
        </section>

        <section class="dialog-section">
          <h3>会话设置</h3>
          <dl class="settings-list compact">
            <div v-for="row in activeConversationPreferenceRows" :key="row[0]">
              <dt>{{ row[0] }}</dt>
              <dd>{{ row[1] }}</dd>
            </div>
          </dl>
          <div class="form-actions">
            <AppButton variant="secondary" size="sm" :disabled="im.actionLoading" @click="toggleActiveConversationPreference('pin')">
              切换置顶
            </AppButton>
            <AppButton variant="secondary" size="sm" :disabled="im.actionLoading" @click="toggleActiveConversationPreference('mute')">
              切换免打扰
            </AppButton>
          </div>
        </section>

        <p v-if="groupActionError" class="dialog-error">{{ groupActionError }}</p>
        <div class="dialog-actions">
          <AppButton variant="ghost" type="button" :disabled="im.actionLoading" @click="leaveActiveGroup">退出群聊</AppButton>
          <AppButton variant="destructive" type="button" :disabled="im.actionLoading" @click="dismissActiveGroup">解散群聊</AppButton>
          <AppButton variant="secondary" type="button" :disabled="im.actionLoading" @click="closeGroupPanel">关闭</AppButton>
        </div>
      </section>
    </AppDialog>

    <AppDialog v-if="messageActionTarget">
      <form class="message-action-dialog" @submit.prevent="submitMessageEdit">
        <header class="dialog-head dialog-head-row">
          <div>
            <h2>消息操作</h2>
          </div>
          <button class="field-icon-button" type="button" :disabled="im.actionLoading" aria-label="关闭" @click="closeMessageAction">
            <X :size="15" />
          </button>
        </header>
        <label class="dialog-field">
          <span>消息内容</span>
          <textarea v-model="messageEditText" class="ui-textarea" rows="5" />
        </label>
        <p v-if="messageActionError" class="dialog-error">{{ messageActionError }}</p>
        <div class="dialog-actions">
          <AppButton variant="ghost" type="button" :disabled="im.actionLoading" @click="closeMessageAction">
            取消
          </AppButton>
          <AppButton variant="destructive" type="button" :disabled="im.actionLoading" @click="submitMessageRecall">
            撤回
          </AppButton>
          <AppButton variant="primary" type="submit" :disabled="im.actionLoading || !messageEditText.trim()">
            {{ im.actionLoading ? '提交中' : '保存' }}
          </AppButton>
        </div>
      </form>
    </AppDialog>

    <AppDialog v-if="uploadPanelOpen">
      <form class="upload-dialog" @submit.prevent="submitPersonalFileUpload">
        <header class="dialog-head dialog-head-row">
          <div>
            <h2>上传文件</h2>
          </div>
          <button class="field-icon-button" type="button" :disabled="fileUploading" aria-label="关闭" @click="closeUploadPanel">
            <X :size="15" />
          </button>
        </header>

        <button
          class="upload-drop-zone"
          :class="{ active: uploadDragActive }"
          type="button"
          @click="openFilePicker"
          @dragover.prevent="uploadDragActive = true"
          @dragleave.prevent="uploadDragActive = false"
          @drop.prevent="handleUploadDrop"
        >
          <CloudUpload :size="28" />
          <strong>选择文件</strong>
          <span>拖拽文件到此区域</span>
        </button>

        <div v-if="uploadDraftFile" class="upload-file-row">
          <span
            class="file-icon"
            :class="`file-tone-${fileToneOf({ id: 'draft', name: uploadDraftFile.name, type: fileTypeLabel(uploadDraftFile), size: fileSizeLabel(uploadDraftFile.size), createdAt: '', status: '', mimeType: uploadDraftFile.type })}`"
          >
            <component :is="fileIconOf({ id: 'draft', name: uploadDraftFile.name, type: fileTypeLabel(uploadDraftFile), size: fileSizeLabel(uploadDraftFile.size), createdAt: '', status: '', mimeType: uploadDraftFile.type })" :size="18" />
          </span>
          <div>
            <strong>{{ uploadDraftFile.name }}</strong>
            <small>{{ fileTypeLabel(uploadDraftFile) }} · {{ fileSizeLabel(uploadDraftFile.size) }}</small>
          </div>
        </div>

        <p v-if="uploadResult" class="upload-result" :class="uploadResult.status">
          <component :is="uploadResult.status === 'success' ? CheckCircle2 : CircleAlert" :size="16" />
          {{ uploadResult.text }}
        </p>

        <div class="dialog-actions">
          <AppButton variant="ghost" type="button" :disabled="fileUploading" @click="closeUploadPanel">
            取消
          </AppButton>
          <AppButton variant="primary" type="submit" :disabled="fileUploading || !uploadDraftFile">
            {{ fileUploading ? '上传中' : '上传' }}
          </AppButton>
        </div>
      </form>
    </AppDialog>

    <AppDialog v-if="fileSendTarget">
      <section class="send-file-dialog">
        <header class="dialog-head dialog-head-row">
          <div>
            <h2>发送到会话</h2>
            <p>选择要发送 “{{ fileSendTarget.file.name }}” 的会话</p>
          </div>
          <button class="field-icon-button" type="button" :disabled="fileSendTarget.submitting" aria-label="关闭" @click="closeSendFileTarget">
            <X :size="15" />
          </button>
        </header>

        <label class="security-field">
          <span>附言（可选）</span>
          <textarea v-model="fileSendTarget.caption" class="ui-textarea compact-textarea" rows="2" placeholder="对接收人说一句话" />
        </label>

        <label class="security-field">
          <span>搜索会话</span>
          <AppInput v-model="fileSendTargetQuery" placeholder="按会话名 / 备注搜索" />
        </label>

        <div class="send-file-list">
          <AppEmptyState v-if="!shell.conversations.length" title="暂无会话" description="先在消息页发起会话后再来发送文件。" />
          <template v-else>
            <button
              v-for="conv in shell.conversations.filter((item) => !fileSendTargetQuery.trim() || [item.title, item.subtitle, item.tag].some((text) => (text || '').toLowerCase().includes(fileSendTargetQuery.trim().toLowerCase())))"
              :key="conv.id"
              type="button"
              class="send-file-row"
              :disabled="fileSendTarget.submitting"
              @click="confirmSendFileTarget(conv.id)"
            >
              <AppAvatar :label="conv.badge || conv.title.slice(0, 2)" :tone="toneOfConversation(conv.tone)" size="sm" />
              <div>
                <strong>{{ conv.title }}</strong>
                <small>{{ conv.subtitle || conv.tag || conv.flow }}</small>
              </div>
              <Send :size="14" />
            </button>
          </template>
        </div>

        <p v-if="fileSendTargetError" class="dialog-error">{{ fileSendTargetError }}</p>

        <div class="dialog-actions">
          <AppButton variant="ghost" type="button" :disabled="fileSendTarget.submitting" @click="closeSendFileTarget">
            取消
          </AppButton>
        </div>
      </section>
    </AppDialog>

    <AppDialog v-if="filePreview">
      <section class="preview-dialog">
        <header class="dialog-head dialog-head-row">
          <div>
            <h2>{{ filePreviewTitle }}</h2>
            <p>{{ filePreviewType || '文件' }}</p>
          </div>
          <button class="field-icon-button" type="button" aria-label="关闭" @click="closeFilePreview">
            <X :size="15" />
          </button>
        </header>

        <div class="preview-frame">
          <AppLoadingState v-if="filePreviewLoading" />
          <img v-else-if="filePreview.inline && (filePreviewType || '').startsWith('image/')" :src="filePreview.url" :alt="filePreviewTitle" />
          <iframe v-else-if="filePreview.inline" :src="filePreview.url" title="文件预览" />
          <div v-else class="preview-fallback">
            <component :is="fileIconOf(filePreview.file)" :size="42" />
            <strong>{{ filePreviewTitle }}</strong>
          </div>
        </div>

        <div class="dialog-actions">
          <AppButton variant="secondary" type="button" @click="openPreviewExternal">
            打开文件
          </AppButton>
          <AppButton variant="ghost" type="button" @click="closeFilePreview">
            关闭
          </AppButton>
        </div>
      </section>
    </AppDialog>
  </main>
</template>
