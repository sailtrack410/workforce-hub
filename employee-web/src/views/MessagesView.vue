<script setup lang="ts">
import {
  ArrowUpRight,
  BarChart3,
  CalendarDays,
  Check,
  ChevronLeft,
  CheckCircle2,
  CircleAlert,
  Clock3,
  ClipboardList,
  CloudUpload,
  FileText,
  GitBranch,
  LayoutGrid,
  MessageCircle,
  MoonStar,
  PanelLeftClose,
  PanelLeftOpen,
  Pencil,
  Pin,
  PinOff,
  Search,
  Send,
  Settings,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  SunMedium,
  UserRound,
  UsersRound,
  X,
} from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { employeeApi } from '../api';
import type {
  AgentResponse,
  EntityId,
} from '../api';
import type { ContactRecord, ConversationRecord, PersonalFileRecord, SectionAction, SectionFilter, SectionId, TodoRecord } from '../stores/shell';
import {
  primaryNavItems,
  sectionFilters,
  todoLifecycle,
  todoSectionFilters,
  shellUser,
  useShellStore,
  normalizeSettingsPanel,
  resolveWorkbenchRoute,
} from '../stores/shell';
import { useImStore } from '../stores/im';
import { useSessionStore } from '../stores/session';
import { usePluginsStore } from '../stores/plugins';
import { useCurrentUserAvatarUrl } from '../composables/useCurrentUserAvatarUrl';
import { useEmployeeAuthFlow } from '../composables/useEmployeeAuthFlow';
import { useFileUpload } from '../composables/useFileUpload';
import { useImSocket } from '../composables/useImSocket';
import { usePersonalFileTransfer } from '../composables/usePersonalFileTransfer';
import { fileSizeLabel, fileTypeLabel } from '@/utils/file-format';
import {
  statusText,
  agentTypeLabel,
  humanizeText,
  toneOfConversation,
  toneOfStatus,
} from '@/utils/employee-format';
import {
  fileIconOf,
  fileToneOf,
  isFileTrashed,
} from '@/utils/file-record';
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
import AppSkeleton from '../components/ui/AppSkeleton.vue';
import AppTag from '../components/ui/AppTag.vue';
import ShellNavButton from '../components/shell/ShellNavButton.vue';
import ProfileMenu from '../components/shell/ProfileMenu.vue';
import ContactsCategoryNav from '../components/ContactsCategoryNav.vue';
import authHeroImage from '../assets/auth-hero-employee.jpg';
import workbenchIconOa from '../assets/brand/app-icon-oa.jpg';
import workbenchIconAttendance from '../assets/brand/app-icon-attendance.jpg';
import workbenchIconLeave from '../assets/brand/app-icon-leave.jpg';
import workbenchIconToday from '../assets/brand/app-icon-today.jpg';
import { provideEmployeeShellContexts } from './employee-shell-context';
import type { EmployeeShellContexts } from './employee-shell-context';

const shell = useShellStore();
const im = useImStore();
const session = useSessionStore();
const plugins = usePluginsStore();
const router = useRouter();
const route = useRoute();

const searchOpen = ref(true);
const resizing = ref(false);
const logoutConfirmOpen = ref(false);
const logoutSubmitting = ref(false);
const toastMessage = ref('');
const filterScroller = ref<HTMLElement | null>(null);
const filtersOverflow = ref(false);
const completingTodoIds = ref<Set<string>>(new Set());
const dismissedTodoIds = ref<Set<string>>(new Set());
const fileSendTarget = ref<{ file: PersonalFileRecord; caption: string; submitting: boolean } | null>(null);
const fileSendTargetQuery = ref('');
const fileSendTargetError = ref('');
let toastTimer: number | undefined;

const sectionIcons: Record<SectionId, typeof MessageCircle> = {
  messages: MessageCircle,
  todos: Clock3,
  files: FileText,
  workbench: LayoutGrid,
  processes: GitBranch,
  contacts: UsersRound,
  agents: Sparkles,
  reports: BarChart3,
  settings: UserRound,
};

const workbenchIcons: Record<string, typeof MessageCircle> = {
  oa: ClipboardList,
  attendance: Clock3,
  balance: Sparkles,
  agent: Sparkles,
};

// 工作台应用入口使用 3D 品牌图标（生成的素材），替代色块+线性图标。
const workbenchIconImages: Record<string, string> = {
  today: workbenchIconToday,
  oa: workbenchIconOa,
  attendance: workbenchIconAttendance,
  balance: workbenchIconLeave,
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
// 登录页品牌标语轮播：来自公开品牌配置（管理端企业设置维护），失败时回退内置文案
const DEFAULT_TAGLINE = '消息、待办、审批与智能助手，在同一个工作区内持续协同。';
const brandTaglines = ref<string[]>([DEFAULT_TAGLINE]);
const taglineIndex = ref(0);
const activeTagline = computed(() => brandTaglines.value[taglineIndex.value % brandTaglines.value.length] || DEFAULT_TAGLINE);
let taglineTimer: number | undefined;
const searchModel = computed({
  get: () => shell.search[activeSection.value],
  set: (value: string) => shell.setSearch(activeSection.value, value),
});
const filterOptions = computed(() => {
  if (activeSection.value === 'messages') return sectionFilters;
  if (activeSection.value === 'todos') return todoSectionFilters;
  return [];
});
const activeFilter = computed(() => shell.filters[activeSection.value]);
const filteredConversations = computed(() => shell.filteredConversations);
const conversationContextMenu = ref<{ conversationId: string; x: number; y: number } | null>(null);
const contactRemarkTarget = ref<ContactRecord | null>(null);
const contactRemarkDraft = ref('');
const contactRemarkError = ref('');
const contextConversation = computed(() => {
  const menu = conversationContextMenu.value;
  if (!menu) return null;
  return shell.conversations.find((item) => item.id === menu.conversationId) ?? null;
});
const contextPrivateTargetContact = computed(() => {
  const conversation = contextConversation.value;
  if (!conversation || conversation.kind !== 'private') return null;
  return contactFromPrivateConversation(conversation);
});
const searchAgentResults = ref<AgentResponse[]>([]);
const searchAgentAvatarUrls = ref<Record<string, string>>({});
const searchAgentsLoading = ref(false);
const searchAgentsError = ref('');
let searchAgentsTimer: number | undefined;
let searchAgentsRequestSeq = 0;
const existingAgentCodes = computed(() => new Set(
  shell.conversations
    .map((item) => (item.kind === 'agent' ? item.tag : ''))
    .filter(Boolean),
));
const filteredSearchAgentResults = computed(() => {
  const keyword = shell.search.messages.trim();
  if (!keyword) return [];
  return searchAgentResults.value.filter((agent) => !existingAgentCodes.value.has(agent.agentCode));
});
const filteredTodos = computed(() => shell.filteredTodos);
const visibleTodoList = computed(() => filteredTodos.value.filter((item) => (
  shell.filters.todos !== '进行中' || !dismissedTodoIds.value.has(item.id)
)));
const compactTodoRange = (item: TodoRecord) => {
  if (!item.startAt || !item.endAt) return item.subtitle || item.time;
  const start = `${Number(item.startAt.slice(5, 7))}月${Number(item.startAt.slice(8, 10))}日 ${item.startAt.slice(11)}`;
  if (item.startAt.slice(0, 10) === item.endAt.slice(0, 10)) return `${start} - ${item.endAt.slice(11)}`;
  const end = `${Number(item.endAt.slice(5, 7))}月${Number(item.endAt.slice(8, 10))}日 ${item.endAt.slice(11)}`;
  return `${start} - ${end}`;
};
const todoStatusTone = (item: TodoRecord): 'success' | 'warning' | 'neutral' => {
  const lifecycle = todoLifecycle(item);
  if (lifecycle === '已完成') return 'success';
  if (lifecycle === '未开始') return 'neutral';
  return 'warning';
};
function completeTodoFromList(item: TodoRecord) {
  if (item.completed || completingTodoIds.value.has(item.id)) return;
  completingTodoIds.value = new Set([...completingTodoIds.value, item.id]);
  if (shell.filters.todos === '进行中') {
    dismissedTodoIds.value = new Set([...dismissedTodoIds.value, item.id]);
  }
  window.setTimeout(() => {
    shell.toggleTodoTask(item.id);
    const next = new Set(completingTodoIds.value);
    next.delete(item.id);
    completingTodoIds.value = next;
  }, 240);
}

function conversationNoticeLabel(conversation: ConversationRecord) {
  if (conversation.hasMentionMe) return '[有人@我]';
  if (conversation.hasReplyMe) return '[有人回复我]';
  const userId = session.user?.userId;
  if (userId === undefined || userId === null) return '';
  const unreadMessages = conversation.unread > 0
    ? conversation.messages.slice(-conversation.unread)
    : conversation.messages.slice(-1);
  if (unreadMessages.some((message) => (
    message.side !== 'right'
    && message.mentions?.some((mention) => String(mention.userId) === String(userId))
  ))) return '[有人@我]';
  const ownMessageIds = new Set(conversation.messages
    .filter((message) => message.side === 'right' || (message.senderId !== undefined && message.senderId !== null && String(message.senderId) === String(userId)))
    .map((message) => String(message.id)));
  if (unreadMessages.some((message) => (
    message.side !== 'right'
    && message.quotedMessageId !== undefined
    && message.quotedMessageId !== null
    && ownMessageIds.has(String(message.quotedMessageId))
  ))) return '[有人回复我]';
  return '';
}
const filteredFiles = computed(() => shell.filteredFiles);
const filteredWorkbench = computed(() => shell.filteredWorkbench);
const filteredContacts = computed(() => shell.filteredContacts);
const filteredProfile = computed(() => shell.filteredProfile);
const mobileNavItems = computed(() => primaryNavItems);
const showFilterBar = computed(() => filterOptions.value.length > 1);
const showFilterArrows = computed(() => filtersOverflow.value);
const workbenchLaunchers = computed(() => filteredWorkbench.value);
const hasActiveConversation = computed(() => Boolean(activeConversation.value.id));
const uploadFolderId = ref<EntityId | null>(null);
const { resolveDownloadUrl } = usePersonalFileTransfer();
const {
  personalFileInput,
  fileUploading,
  uploadPanelOpen,
  uploadDraftFile,
  uploadDragActive,
  uploadResult,
  filePreview,
  filePreviewLoading,
  filePreviewTitle,
  filePreviewType,
  refreshFileQuota,
  refreshPersonalFiles,
  refreshFileCenter,
  handlePersonalFileSelected,
  openUploadPanel,
  closeUploadPanel,
  openFilePicker,
  handleUploadDrop,
  submitPersonalFileUpload,
  openPersonalFilePreview,
  openFilePreviewFromUrl,
  downloadPersonalFile,
  trashPersonalFile,
  restorePersonalFile,
  purgePersonalFile,
  closeFilePreview,
  openPreviewExternal,
} = useFileUpload({
  shell,
  activeFile,
  activeSection,
  uploadFolderId,
  hasActiveConversation,
  showToast,
  sendSelectedFileToConversation,
});
const { refreshCurrentUserAvatarUrl } = useCurrentUserAvatarUrl(shell, session);
const businessEmptyTitle = computed(() => {
  if (activeSection.value === 'processes') return '暂无流程';
  if (activeSection.value === 'contacts') return '暂无联系人';
  if (activeSection.value === 'reports') return '暂无报表';
  return '暂无数据';
});
const currentListLoading = computed(() => {
  if (activeSection.value === 'messages') return im.loading;
  return false;
});
const currentListError = computed(() => {
  if (activeSection.value === 'messages') return im.error;
  return '';
});

const sectionRouteNames: Partial<Record<SectionId, string>> = {
  messages: 'employee-messages',
  todos: 'employee-todos',
  files: 'employee-files',
  workbench: 'employee-workbench',
  contacts: 'employee-contacts',
  agents: 'employee-agents',
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

function showToast(message: string) {
  toastMessage.value = message;
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toastMessage.value = '';
    toastTimer = undefined;
  }, 2600);
}

async function refreshContactAvatarUrls() {
  const contacts = shell.contacts.filter((item) => item.avatarAttachmentRefId && !item.avatarUrl);
  await Promise.all(contacts.map(async (contact) => {
    if (!contact.avatarAttachmentRefId) return;
    try {
      const ticket = await employeeApi.files.createDownloadTicket(contact.avatarAttachmentRefId, { downloadType: 'INLINE' });
      contact.avatarUrl = resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo));
    } catch {
      contact.avatarUrl = '';
    }
  }));
}

async function refreshSearchAgentAvatarUrls(agents: AgentResponse[]) {
  const entries = await Promise.all(agents.map(async (agent) => {
    if (!agent.avatarAttachmentRefId) return [String(agent.agentId), ''] as const;
    try {
      const ticket = await employeeApi.files.createDownloadTicket(agent.avatarAttachmentRefId, { downloadType: 'INLINE' });
      return [String(agent.agentId), resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo))] as const;
    } catch {
      return [String(agent.agentId), ''] as const;
    }
  }));
  searchAgentAvatarUrls.value = Object.fromEntries(entries);
}

const {
  authMode,
  loginName,
  password,
  loginEmailCode,
  loginRequiresEmailCode,
  loginRequiresEmailBinding,
  loginBindEmail,
  loginCodeSending,
  loginCodeCooldown,
  resetEmail,
  resetCode,
  resetPasswordValue,
  resetCodeSending,
  resetCodeCooldown,
  authNotice,
  authModeTitle,
  authModeSubtitle,
  loginCodeButtonText,
  resetCodeButtonText,
  setAuthMode,
  submitLogin,
  sendLoginCode,
  sendResetCode,
  submitPasswordReset,
  resetAuthCooldowns,
} = useEmployeeAuthFlow({
  session,
  loadAuthenticatedWorkspace,
});
const showAuthModeHead = computed(() => loginRequiresEmailBinding.value || loginRequiresEmailCode.value || Boolean(authModeSubtitle.value));

function runAction(action?: SectionAction) {
  if (!action) return;
  if (action.action === 'upload-personal-file') {
    if (activeSection.value !== 'files') uploadFolderId.value = null;
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
  }
}

function selectWorkbenchItem(id: string) {
  navigateSection('workbench', id);
  shell.selectItem('workbench', id);
  if (!session.isAuthenticated) return;
  if (id === 'files') void refreshFileCenter();
}

function selectSettingsItem(id: string) {
  const canonical = normalizeSettingsPanel(id);
  navigateSection('settings', canonical);
  shell.selectItem('settings', canonical);
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
  closeConversationContextMenu();
  if (section === 'settings') {
    selectSettingsItem('profile');
    shell.setMobileView('list');
    return;
  }
  navigateSection(section);
  shell.setMobileView('list');
}

function openProfileSettings() {
  selectSettingsItem('profile');
}

async function confirmLogout() {
  if (logoutSubmitting.value) return;
  logoutSubmitting.value = true;
  try {
    await session.logout();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '退出登录失败');
  } finally {
    window.location.reload();
  }
}

async function loadAuthenticatedWorkspace() {
  if (!session.isAuthenticated) return;
  void plugins.loadAccessiblePlugins().catch(() => undefined);
  await Promise.all([
    session.loadOrgProfile().then(() => {
      shell.setColleagues(session.colleagues);
      shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    }),
    im.loadConversations().then(() => {
      shell.mergeBackendConversations(im.conversations);
    }),
    refreshFileCenter(),
    hydrateUserPreferences(),
    employeeApi.contacts.friends().then((rows) => shell.setFriends(rows)),
    employeeApi.contacts.deptTree().then((tree) => {
      shell.setDeptTree(tree);
      if (shell.contactsCategoryId === 'friends' && tree.length) {
        const firstRoot = tree[0];
        if (firstRoot?.children?.length) {
          const firstChild = firstRoot.children[0];
          shell.setContactsCategoryId(`dept-${firstChild.id}`);
          shell.setContactsExpandedCategoryIds(new Set(['enterprise', `dept-${firstRoot.id}`]));
        }
      }
    }),
  ]);
  await im.loadVisibleConversationDetails(undefined, undefined, session.user?.userId);
  shell.mergeBackendConversations(im.conversations);
  shell.setColleagues(session.colleagues);
  shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
  await refreshCurrentUserAvatarUrl();
  await refreshContactAvatarUrls();
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
    return;
  }
}

async function syncConversationMessages(id: string) {
  const conversationId = id.trim();
  if (!conversationId) return;
  if (!session.isAuthenticated) return;
  await Promise.all([
    im.loadMessages(conversationId, session.user?.userId),
    im.loadConversationDetail(conversationId, undefined, session.user?.userId).catch(() => undefined),
  ]);
  shell.mergeBackendConversations(im.conversations);
  shell.setColleagues(session.colleagues);
  shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
  void refreshContactAvatarUrls();
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
  }
}

const {
  wsConnected,
  websocketUrl,
  closeImSocket,
  scheduleImSocketReconnect,
  connectImSocket,
  refreshImAfterReconnect,
  handleImSocketMessage,
  isImMessageResponse,
} = useImSocket({
  im,
  session,
  shell,
  syncConversationMessages,
});

function navigateMessagesConversation(id: string) {
  navigateSection('messages', id);
  shell.selectItem('messages', id);
}

function selectConversation(id: string) {
  closeConversationContextMenu();
  navigateMessagesConversation(id);
  void syncConversationMessages(id);
}

function openConversationContextMenu(event: MouseEvent, conversationId: string) {
  const menuWidth = 176;
  const menuHeight = 96;
  conversationContextMenu.value = {
    conversationId,
    x: Math.max(8, Math.min(event.clientX, window.innerWidth - menuWidth - 8)),
    y: Math.max(8, Math.min(event.clientY, window.innerHeight - menuHeight - 8)),
  };
}

function closeConversationContextMenu() {
  conversationContextMenu.value = null;
}

function contactFromPrivateConversation(conversation: ConversationRecord): ContactRecord | null {
  const target = conversation.panel.members.find((member) => (
    member.userId !== undefined && member.userId !== null && String(member.userId) !== String(session.user?.userId ?? '')
  )) ?? conversation.panel.members.find((member) => member.userId !== undefined && member.userId !== null);
  if (!target?.userId) return null;
  return shell.contacts.find((item) => String(item.userId) === String(target.userId)) ?? {
    id: `user-${target.userId}`,
    name: shell.displayNameForUser(target.userId, target.name),
    originalName: target.originalName || target.name,
    remarkName: shell.contactRemarkFor(target.userId),
    role: target.role,
    department: '',
    relation: '私聊',
    source: '会话成员',
    userId: target.userId,
  };
}

function openContactRemarkEditor(contact: ContactRecord) {
  if (!contact.userId) return;
  contactRemarkTarget.value = contact;
  contactRemarkDraft.value = shell.contactRemarkFor(contact.userId);
  contactRemarkError.value = '';
}

function closeContactRemarkEditor() {
  contactRemarkTarget.value = null;
  contactRemarkDraft.value = '';
  contactRemarkError.value = '';
}

function submitContactRemark() {
  const contact = contactRemarkTarget.value;
  if (!contact?.userId) return;
  shell.setContactRemark(contact.userId, contactRemarkDraft.value);
  closeContactRemarkEditor();
  showToast(contactRemarkDraft.value.trim() ? '备注已保存' : '备注已清除');
}

function openConversationRemarkFromMenu() {
  const contact = contextPrivateTargetContact.value;
  closeConversationContextMenu();
  if (contact) openContactRemarkEditor(contact);
}

async function startPrivateConversationFromContact(contact: ContactRecord) {
  if (!contact.userId) return;
  try {
    const conversation = await im.startPrivateConversation(contact.userId);
    shell.mergeBackendConversations(im.conversations);
    navigateMessagesConversation(conversation.id);
    await syncConversationMessages(conversation.id);
    showToast('已打开会话');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '私聊创建失败');
  }
}

async function toggleConversationPinnedFromMenu() {
  const conversation = contextConversation.value;
  if (!conversation || im.actionLoading) return;
  const nextPinned = !conversation.pinned;
  try {
    await im.updatePreference(conversation.id, {
      pin: nextPinned,
      mute: Boolean(conversation.muted),
    });
    shell.mergeBackendConversations(im.conversations);
    closeConversationContextMenu();
    showToast(nextPinned ? '已置顶' : '已取消置顶');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '会话偏好保存失败');
  }
}

async function startAgentConversationFromSearch(agent: AgentResponse) {
  try {
    const conversation = await im.startAgentConversation(agent.agentId);
    shell.mergeBackendConversations(im.conversations);
    navigateMessagesConversation(conversation.id);
    await syncConversationMessages(conversation.id);
    shell.search.messages = '';
    showToast('AI 会话已创建');
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'AI 会话创建失败');
  }
}

function backToList() {
  shell.setMobileView('list');
}

function openDetail(payload?: unknown) {
  const action = payload && typeof payload === 'object' && 'action' in payload
    ? payload as { action?: unknown; payload?: Record<string, unknown>; apiRef?: { path?: string } }
    : null;
  if (action?.action === 'OPEN_FLOW') {
    const target = action.payload?.targetApp;
    const path = action.apiRef?.path;
    if (target === 'requests' || path === '/workbench/requests') {
      navigateSection('workbench', 'requests');
      shell.selectItem('workbench', 'requests');
      return;
    }
  }
  shell.setDetailOpen(true);
}

function closeDetail() {
  shell.setDetailOpen(false);
}

async function sendSelectedFileToConversation(file: PersonalFileRecord) {
  if (!file?.attachmentRefId) return;
  if (isFileTrashed(file)) {
    showToast('已删除的文件不能发送');
    return;
  }
  const conversationId = shell.active.messages.trim();
  if (!conversationId || activeSection.value !== 'messages') {
    openSendFileTarget(file);
    return;
  }
  try {
    await im.sendFileMessage(conversationId, file.attachmentRefId, '', session.user?.userId);
    const messages = im.messagesByConversation[String(conversationId)];
    if (messages?.length) {
      shell.mergeBackendMessages(String(conversationId), messages);
    }
    showToast('文件已发送');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '文件发送失败');
  }
}

function selectFilter(filter: SectionFilter) {
  closeConversationContextMenu();
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
  if (event.key === 'Escape' && conversationContextMenu.value) {
    closeConversationContextMenu();
    return;
  }
  if (event.key === 'Escape' && detailVisible.value) closeDetail();
}

// 全局右键拦截：屏蔽浏览器默认菜单，输入类元素（input/textarea/contenteditable）放行以保留系统编辑菜单。
// 已有自定义右键菜单的元素（消息气泡、会话列表项）自带 .stop，不会传到这里。
function handleGlobalContextMenu(event: MouseEvent) {
  const target = event.target as HTMLElement;
  if (target.closest('input, textarea, [contenteditable="true"]')) return;
  event.preventDefault();
}

async function retryMessages() {
  if (!session.isAuthenticated) return;
  await im.loadConversations();
}

onMounted(async () => {
  document.body.dataset.theme = shell.theme;
  window.addEventListener('keydown', handleKeydown);
  window.addEventListener('click', closeConversationContextMenu);
  window.addEventListener('resize', updateFilterOverflow);
  // 全局右键拦截：屏蔽浏览器默认菜单，输入类元素放行以保留系统编辑菜单
  window.addEventListener('contextmenu', handleGlobalContextMenu);
  employeeApi.auth.branding()
    .then((res) => {
      if (res.taglines?.length) brandTaglines.value = res.taglines;
    })
    .catch(() => undefined);
  taglineTimer = window.setInterval(() => {
    taglineIndex.value = (taglineIndex.value + 1) % Math.max(1, brandTaglines.value.length);
  }, 4500);
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
  delete document.body.dataset.theme;
  window.removeEventListener('keydown', handleKeydown);
  window.removeEventListener('click', closeConversationContextMenu);
  window.removeEventListener('resize', updateFilterOverflow);
  window.removeEventListener('contextmenu', handleGlobalContextMenu);
  if (toastTimer) window.clearTimeout(toastTimer);
  if (searchAgentsTimer) window.clearTimeout(searchAgentsTimer);
  if (taglineTimer) window.clearInterval(taglineTimer);
  resetAuthCooldowns();
  closeImSocket();
});

watch(() => session.profile?.avatarAttachmentRefId, () => {
  void refreshCurrentUserAvatarUrl().then(refreshContactAvatarUrls);
});

watch(() => shell.theme, (theme) => {
  document.body.dataset.theme = theme;
});

watch([filterOptions, activeSection, searchOpen], () => {
  void updateFilterOverflow();
});

watch(
  () => [activeSection.value, shell.search.messages] as const,
  ([section, keyword]) => {
    if (searchAgentsTimer) window.clearTimeout(searchAgentsTimer);
    searchAgentsError.value = '';
    const trimmed = keyword.trim();
    if (section !== 'messages' || !trimmed || !session.isAuthenticated) {
      searchAgentResults.value = [];
      searchAgentsLoading.value = false;
      return;
    }
    searchAgentsLoading.value = true;
    searchAgentsTimer = window.setTimeout(() => {
      const requestSeq = ++searchAgentsRequestSeq;
      employeeApi.agents.list({ keyword: trimmed })
        .then((agents) => {
          if (requestSeq === searchAgentsRequestSeq) {
            searchAgentResults.value = agents;
            void refreshSearchAgentAvatarUrls(agents);
          }
        })
        .catch((error) => {
          if (requestSeq !== searchAgentsRequestSeq) return;
          searchAgentResults.value = [];
          searchAgentsError.value = error instanceof Error ? error.message : 'AI 助手搜索失败';
        })
        .finally(() => {
          if (requestSeq === searchAgentsRequestSeq) searchAgentsLoading.value = false;
        });
    }, 240);
  },
);

watch(() => shell.active.workbench, (id) => {
  if (!session.isAuthenticated) return;
  if (id === 'files') void refreshFileQuota();
}, { immediate: true });

watch(
  () => [route.meta.section, route.params.conversationId, route.params.todoId, route.params.app, route.params.panel] as const,
  ([section, conversationId, todoId, app, panel]) => {
    if (typeof section !== 'string') return;
    const typedSection = section as SectionId;
    if (typedSection !== shell.section) shell.setSection(typedSection);
    if (typedSection === 'messages') {
      if (typeof conversationId === 'string') {
        shell.selectItem('messages', conversationId);
        void syncConversationMessages(conversationId);
      } else {
        shell.setMobileView('list');
      }
    } else if (typedSection === 'todos') {
      if (typeof todoId === 'string') shell.selectItem('todos', todoId);
      else shell.setMobileView('list');
    } else if (typedSection === 'workbench') {
      if (typeof app !== 'string') {
        shell.setMobileView('list');
      }
    } else if (typedSection === 'settings') {
      if (typeof panel === 'string') {
        const canonical = normalizeSettingsPanel(panel);
        shell.selectItem('settings', canonical);
        if (canonical !== panel) {
          void router.replace({ name: 'employee-settings-detail', params: { panel: canonical } });
        }
      } else {
        shell.selectItem('settings', 'profile');
        void router.replace({ name: 'employee-settings-detail', params: { panel: 'profile' } });
        shell.setMobileView('list');
      }
    } else {
      shell.setMobileView('list');
    }
  },
  { immediate: true },
);

watch(() => session.isAuthenticated, (authenticated) => {
  if (authenticated) {
    connectImSocket();
  } else {
    closeImSocket();
    plugins.reset();
  }
});

let pendingReplaceTarget: string | null = null;

watch(
  () => [
    route.meta.section,
    route.params.app,
    session.initialized,
    session.isAuthenticated,
    plugins.resolved,
    plugins.manifest,
    plugins.loadedIds,
  ] as const,
  ([section, app, initialized, authenticated, resolved, manifest, loadedIds]) => {
    shell.rebuildWorkbench(manifest, loadedIds);
    if (section !== 'workbench' || typeof app !== 'string') return;

    const workbenchIds = shell.workbench.map((item) => item.id);
    const resolution = resolveWorkbenchRoute(app, initialized, authenticated, resolved, workbenchIds);
    if (resolution.pending) return;

    shell.selectItem('workbench', resolution.id);
    if (resolution.replace && resolution.id !== pendingReplaceTarget) {
      pendingReplaceTarget = resolution.id;
      void router.replace({ name: 'employee-workbench-detail', params: { app: resolution.id } })
        .finally(() => { pendingReplaceTarget = null; })
        .catch(() => {});
    }
  },
  { immediate: true },
);

const employeeShellContexts = {
  messages: {
    shell,
    im,
    session,
    wsConnected,
    showToast,
    runAction,
    openDetail,
    openUploadPanel,
    syncConversationMessages,
    navigateMessagesConversation,
    startAgentConversationFromSearch,
    openFilePreviewFromUrl,
  },
  todos: {
    shell,
    pendingTodoCount,
    backToList,
  },
  workbench: {
    shell,
    session,
    showToast,
  },
  files: {
    im,
    fileUploading,
    activeFile,
    fileUsageCircumference,
    activeFileUsage,
    activeFileUsageOffset,
    currentMeta,
    backToList,
    runAction,
    showToast,
    setUploadFolderId: (folderId) => {
      uploadFolderId.value = folderId ?? null;
    },
    handleFileDrop: (file: File) => {
      uploadDraftFile.value = file;
      openUploadPanel();
    },
    refreshFileCenter,
    sendSelectedFileToConversation,
    openPersonalFilePreview,
    downloadPersonalFile,
    trashPersonalFile,
    restorePersonalFile,
    purgePersonalFile,
  },
  settings: {
    shell,
    session,
    showToast,
  },
  contacts: {
    shell,
    activeContact,
    backToList,
    startPrivateConversationFromContact,
    openContactRemarkEditor,
  },
  agents: {
    shell,
    im,
    showToast,
    navigateMessagesConversation,
    startAgentConversationFromSearch,
  },
  empty: {
    currentMeta,
    businessEmptyTitle,
    backToList,
  },
} satisfies EmployeeShellContexts;

provideEmployeeShellContexts(employeeShellContexts);

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
        <div class="auth-brand-identity">
          <AppAvatar label="Workforce Hub" kind="org" tone="neutral" size="md" />
          <span>
            <strong>Workforce Hub</strong>
            <small>员工协同端</small>
          </span>
        </div>
        <button class="theme-toggle auth-theme-toggle" type="button" @click="shell.toggleTheme()">
          <component :is="shell.theme === 'light' ? MoonStar : SunMedium" :size="16" />
          <span>{{ themeLabel }}</span>
        </button>
      </div>
      <div class="auth-hero-copy">
        <p class="auth-kicker">EMPLOYEE WORKSPACE</p>
        <h1>Workforce Hub</h1>
        <p class="auth-hero-summary">
          <Transition name="tagline-fade" mode="out-in">
            <span :key="activeTagline" class="auth-tagline">{{ activeTagline }}</span>
          </Transition>
        </p>
      </div>
      <div class="auth-hero-visual" aria-label="员工协同工作流主视觉">
        <img class="auth-hero-image" :src="authHeroImage" alt="" aria-hidden="true" />
      </div>
    </section>

    <section class="auth-card" :aria-label="authModeTitle">
      <div class="auth-mode-tabs" role="tablist" aria-label="认证方式">
        <button type="button" :class="{ active: authMode === 'login' }" @click="setAuthMode('login')">登录</button>
        <button type="button" :class="{ active: authMode === 'reset' }" @click="setAuthMode('reset')">找回</button>
      </div>

      <div v-if="showAuthModeHead" class="auth-card-head">
        <h2>{{ authModeTitle }}</h2>
        <p v-if="authModeSubtitle">{{ authModeSubtitle }}</p>
      </div>

      <form v-if="authMode === 'login'" class="auth-form" @submit.prevent="submitLogin">
        <label class="auth-field">
          <span>登录名</span>
          <AppInput v-model="loginName" autocomplete="username" placeholder="员工登录名" />
        </label>
        <label class="auth-field">
          <span>密码</span>
          <AppInput v-model="password" type="password" autocomplete="current-password" placeholder="密码" />
        </label>
        <label v-if="loginRequiresEmailBinding" class="auth-field">
          <span>绑定邮箱</span>
          <AppInput v-model="loginBindEmail" type="email" autocomplete="email" placeholder="邮箱地址" />
        </label>
        <div v-if="loginRequiresEmailCode || loginRequiresEmailBinding" class="auth-code-row">
          <label class="auth-field">
            <span>邮箱验证码</span>
            <AppInput v-model="loginEmailCode" autocomplete="one-time-code" placeholder="邮箱验证码" />
          </label>
          <AppButton
            variant="secondary"
            type="button"
            :disabled="loginCodeSending || loginCodeCooldown.isActive.value || (loginRequiresEmailBinding ? !loginBindEmail.trim() : !loginName.trim())"
            @click="sendLoginCode"
          >
            {{ loginCodeButtonText }}
          </AppButton>
        </div>
        <p v-if="session.error" class="auth-error">{{ session.error }}</p>
        <p v-if="authNotice" class="auth-notice">{{ authNotice }}</p>
        <AppButton variant="primary" size="lg" block type="submit" :disabled="session.loading || !loginName.trim() || !password">
          {{ session.loading ? '登录中' : (loginRequiresEmailCode || loginRequiresEmailBinding) ? '验证并进入' : '进入工作区' }}
        </AppButton>
      </form>

      <form v-else class="auth-form" @submit.prevent="submitPasswordReset">
        <label class="auth-field">
          <span>绑定邮箱</span>
          <AppInput v-model="resetEmail" type="email" autocomplete="email" placeholder="已绑定邮箱" />
        </label>
        <div class="auth-code-row">
          <label class="auth-field">
            <span>验证码</span>
            <AppInput v-model="resetCode" autocomplete="one-time-code" placeholder="验证码" />
          </label>
          <AppButton
            variant="secondary"
            type="button"
            :disabled="resetCodeSending || resetCodeCooldown.isActive.value || !resetEmail.trim()"
            @click="sendResetCode"
          >
            {{ resetCodeButtonText }}
          </AppButton>
        </div>
        <label class="auth-field">
          <span>新密码</span>
          <AppInput v-model="resetPasswordValue" type="password" autocomplete="new-password" placeholder="8-64 位新密码" />
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
      `font-scale-${shell.fontScale}`,
      `bubble-style-${shell.bubbleStyle}`,
      { 'detail-open': detailVisible, 'mobile-detail-stage': mobileStage === 'detail' },
    ]"
    :data-theme="shell.theme"
    :data-accent="shell.accentColor"
    :style="{ '--list-width': listWidth }"
  >
    <input ref="personalFileInput" class="visually-hidden-input" type="file" @change="handlePersonalFileSelected" />

    <aside class="side-rail" aria-label="员工端功能">
      <div class="brand-block">
        <AppAvatar label="Workforce Hub" kind="org" tone="neutral" size="md" />
        <div class="brand-copy">
          <strong>Workforce Hub</strong>
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
        <ProfileMenu @logout="logoutConfirmOpen = true" />
      </div>
    </aside>

    <section v-if="activeSection !== 'files' && activeSection !== 'agents'" class="list-pane" :class="{ 'mobile-visible': mobileStage === 'list' }">
      <header class="pane-head">
        <div class="pane-title-row">
          <div class="pane-head-copy">
            <h1 class="pane-title">{{ currentMeta.title }}</h1>
          </div>
          <div class="pane-actions">
            <AppBadge v-if="activeSection === 'todos' && pendingTodoCount" tone="danger" kind="count">
              {{ pendingTodoCount }}
            </AppBadge>
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
          <AppSkeleton variant="list" :count="6" />
        </div>

        <AppErrorState
          v-else-if="currentListError && activeSection === 'messages' && !filteredConversations.length"
          title="消息同步失败"
          :description="currentListError"
          action-label="重试"
          @retry="retryMessages"
        />

        <div v-else-if="activeSection === 'messages'" class="list-items">
          <AppEmptyState
            v-if="!filteredConversations.length && !filteredSearchAgentResults.length && !searchAgentsLoading && !searchAgentsError"
            title="暂无会话"
            icon="messages"
          />
          <template v-else>
            <AppListItem
              v-for="agent in filteredSearchAgentResults"
              :key="`agent-search-${agent.agentId}`"
              :disabled="im.loading"
              @click="startAgentConversationFromSearch(agent)"
            >
              <div class="message-row message-row-agent agent-discovery-row">
                <div class="message-row-main">
                  <div class="message-avatar-wrap">
                    <AppAvatar
                      :label="humanizeText(agent.agentName)"
                      :image="searchAgentAvatarUrls[String(agent.agentId)]"
                      kind="agent"
                      tone="success"
                      status="online"
                    />
                    <span class="conversation-kind-glyph kind-agent" aria-hidden="true">
                      <Sparkles :size="11" />
                    </span>
                  </div>
                  <div class="message-copy">
                    <div class="message-title-line">
                      <strong>{{ humanizeText(agent.agentName) }}</strong>
                      <span class="conversation-kind-pill pill-agent">可添加</span>
                    </div>
                    <p>{{ agent.agentTypeName || agentTypeLabel(agent.agentType) }}</p>
                  </div>
                </div>
                <div class="message-row-side">
                  <span class="meta-time">开始会话</span>
                </div>
              </div>
            </AppListItem>
            <div v-if="searchAgentsLoading" class="agent-search-hint">正在搜索可添加 AI 助手</div>
            <div v-else-if="searchAgentsError" class="agent-search-hint error">{{ searchAgentsError }}</div>
            <AppListItem
              v-for="item in filteredConversations"
              :key="item.id"
              :active="item.id === activeConversation.id"
              @click="selectConversation(item.id)"
              @contextmenu.stop.prevent="openConversationContextMenu($event, item.id)"
            >
              <div class="message-row" :class="`message-row-${item.kind === 'team' ? 'team' : item.kind === 'agent' ? 'agent' : 'private'}`">
                <div class="message-row-main">
                  <div class="message-avatar-wrap">
                    <AppAvatar
                      :label="item.title"
                      :image="item.avatarUrl"
                      :kind="item.kind === 'agent' ? 'agent' : 'user'"
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
                      <span v-if="item.pinned" class="conversation-kind-pill pill-pinned">
                        <Pin :size="11" />
                        置顶
                      </span>
                    </div>
                    <p>
                      <span v-if="conversationNoticeLabel(item)" class="conversation-notice-label">{{ conversationNoticeLabel(item) }}</span>
                      {{ item.subtitle }}
                    </p>
                  </div>
                </div>
                <div class="message-row-side">
                  <span class="meta-time">{{ item.meta }}</span>
                  <AppBadge v-if="item.unread" tone="danger" kind="count">{{ item.unread }}</AppBadge>
                </div>
              </div>
            </AppListItem>
          </template>
        </div>

        <div v-else-if="activeSection === 'todos'" class="list-items">
          <AppEmptyState v-if="!visibleTodoList.length" title="待办已清空" icon="todos" />
          <TransitionGroup v-else name="todo-list" tag="div" class="todo-list-stack">
            <article
              v-for="item in visibleTodoList"
              :key="item.id"
              class="todo-list-item"
              :class="{ active: item.id === activeTodo.id, 'todo-item-completing': completingTodoIds.has(item.id) }"
            >
              <div class="generic-row todo-list-row">
                <button
                  class="todo-list-check"
                  type="button"
                  :class="{ checked: item.completed }"
                  :aria-label="item.completed ? '已完成' : '完成待办'"
                  @click.stop="completeTodoFromList(item)"
                >
                  <Check :size="14" />
                </button>
                <button
                  class="todo-list-main"
                  type="button"
                  @click="navigateSection('todos', item.id); shell.selectItem('todos', item.id)"
                >
                  <div class="generic-copy todo-list-copy">
                    <div class="title-line">
                      <strong class="todo-title-with-dot" :class="`todo-color-${item.tagColor ?? 'blue'}`">
                        <i aria-hidden="true" />
                        <span>{{ item.title }}</span>
                      </strong>
                      <span class="meta-time">{{ item.startAt?.slice(11) ?? item.startDate }}</span>
                    </div>
                    <p class="todo-list-meta">
                      <span>{{ item.tagName }}</span>
                      <span>· {{ compactTodoRange(item) }}</span>
                    </p>
                    <p v-if="item.note" class="todo-list-note">{{ item.note }}</p>
                  </div>
                </button>
                <AppTag :tone="todoStatusTone(item)">{{ todoLifecycle(item) }}</AppTag>
              </div>
            </article>
          </TransitionGroup>
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
                <img v-if="workbenchIconImages[item.id]" :src="workbenchIconImages[item.id]" :alt="item.title" class="app-icon-img" />
                <component v-else :is="workbenchIcons[item.id]" :size="18" />
              </span>
              <div class="generic-copy">
                <div class="title-line">
                  <strong>{{ item.title }}</strong>
                  <span v-if="item.disabled" class="meta-time">{{ statusText(item.status) }}</span>
                </div>
                <p>{{ item.note }}</p>
              </div>
            </div>
          </AppListItem>
        </div>

        <div v-else-if="activeSection === 'contacts'" class="list-items contacts-list-pane">
          <ContactsCategoryNav
            :items="shell.contactsCategoryItems"
            :active-id="shell.contactsCategoryId"
            :expanded-ids="shell.contactsExpandedCategoryIds"
            @select="shell.setContactsCategoryId($event)"
            @toggle="shell.toggleContactsExpandedCategoryId($event)"
          />
        </div>

        <div v-else-if="activeSection === 'settings'" class="list-items">
          <AppEmptyState v-if="!filteredProfile.length" title="无匹配结果">
            <AppButton variant="secondary" size="sm" @click="shell.setSearch('settings', '')">
              清空搜索
            </AppButton>
          </AppEmptyState>
          <template v-else>
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
                  </div>
                  <p v-if="item.note">{{ item.note }}</p>
                </div>
              </div>
            </AppListItem>
          </template>
        </div>

        <div v-else class="list-items">
          <AppEmptyState :title="businessEmptyTitle" />
        </div>
      </div>
    </section>

    <div v-if="activeSection !== 'files' && activeSection !== 'agents'" class="resize-handle" role="separator" aria-orientation="vertical" aria-label="调整列表宽度" @pointerdown.prevent="startResize" />

    <section class="main-pane" :class="{ 'mobile-visible': mobileStage === 'detail' || activeSection === 'files' || activeSection === 'agents' }">
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
          <AppButton variant="ghost" size="sm" :aria-label="detailVisible ? '关闭详情' : '打开详情'" @click="shell.toggleDetail()">
            <component :is="detailVisible ? PanelLeftClose : PanelLeftOpen" :size="18" />
          </AppButton>
        </header>

        <template v-if="activeSection === 'messages' && hasActiveConversation">
          <section v-if="activeConversation.panel.auditTrail?.length" class="context-block">
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
                <b>{{ statusText(record.status) }}</b>
              </span>
            </div>
          </section>

          <section v-if="activeConversation.panel.agentEvidence?.length" class="context-block">
            <h3>AI 预审依据</h3>
            <div class="stack-list">
              <span v-for="item in activeConversation.panel.agentEvidence" :key="item.label">
                <small>{{ item.label }}</small>
                <strong>{{ item.value }}</strong>
              </span>
            </div>
          </section>

          <section v-if="activeConversation.panel.attachments.length" class="context-block">
            <h3>相关材料</h3>
            <div class="stack-list">
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
                  <small>{{ file.size }} · {{ statusText(file.status) }}</small>
                </span>
                <ArrowUpRight :size="14" />
              </button>
            </div>
          </section>
          <p
            v-if="!activeConversation.panel.auditTrail?.length && !activeConversation.panel.agentEvidence?.length && !activeConversation.panel.attachments.length"
            class="empty-inline"
          >暂无详情</p>
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
        <button class="mobile-back" type="button" aria-label="关闭详情" @click="closeDetail">
          <ChevronLeft :size="22" />
        </button>
        <div class="mobile-detail-body">
          <h3>{{ activeConversation.title }}</h3>
          <section v-if="activeConversation.workflowSteps.length" class="mobile-detail-section">
            <h4>流程进度</h4>
            <div class="context-steps">
              <span v-for="step in activeConversation.workflowSteps" :key="step.label" :class="step.status">
                <strong>{{ step.label }}</strong>
                {{ step.detail }}
              </span>
            </div>
          </section>
          <section v-if="activeConversation.panel.attachments.length" class="mobile-detail-section">
            <h4>相关材料</h4>
            <div class="stack-list">
              <span v-for="file in activeConversation.panel.attachments" :key="file.id">
                <strong>{{ file.name }}</strong>
                <small>{{ file.size }} · {{ statusText(file.status) }}</small>
              </span>
            </div>
          </section>
          <p v-if="!activeConversation.workflowSteps.length && !activeConversation.panel.attachments.length" class="empty-inline">暂无详情</p>
        </div>
      </div>
    </div>

    <div v-if="toastMessage" class="toast-message" role="status" aria-live="polite">
      {{ toastMessage }}
    </div>

    <div
      v-if="conversationContextMenu"
      class="conversation-context-menu"
      :style="{ left: `${conversationContextMenu.x}px`, top: `${conversationContextMenu.y}px` }"
      @click.stop
    >
      <button type="button" :disabled="im.actionLoading" @click="toggleConversationPinnedFromMenu">
        <component :is="contextConversation?.pinned ? PinOff : Pin" :size="15" />
        <span>{{ contextConversation?.pinned ? '取消置顶' : '置顶会话' }}</span>
      </button>
      <button v-if="contextPrivateTargetContact" type="button" @click="openConversationRemarkFromMenu">
        <Pencil :size="15" />
        <span>设置备注</span>
      </button>
    </div>

    <AppDialog v-if="contactRemarkTarget">
      <form class="contact-remark-dialog" @submit.prevent="submitContactRemark">
        <header class="dialog-head dialog-head-row">
          <div>
            <h2>设置备注</h2>
            <p>{{ contactRemarkTarget.originalName || contactRemarkTarget.name }}</p>
          </div>
          <button class="field-icon-button" type="button" aria-label="关闭" @click="closeContactRemarkEditor">
            <X :size="15" />
          </button>
        </header>
        <label class="security-field">
          <span>备注名</span>
          <AppInput v-model="contactRemarkDraft" placeholder="留空则使用原名称" />
        </label>
        <p v-if="contactRemarkError" class="dialog-error">{{ contactRemarkError }}</p>
        <div class="dialog-actions">
          <AppButton variant="ghost" type="button" @click="closeContactRemarkEditor">取消</AppButton>
          <AppButton variant="primary" type="submit" @click="submitContactRemark">保存</AppButton>
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
              <AppAvatar :label="conv.title" :kind="conv.kind === 'agent' ? 'agent' : 'user'" :tone="toneOfConversation(conv.tone)" size="sm" />
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
            <p>当前格式暂不支持网页内预览</p>
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

    <AppDialog v-if="logoutConfirmOpen">
      <section class="dialog-head">
        <h2>退出登录？</h2>
      </section>
      <div class="dialog-actions settings-logout-actions">
        <AppButton variant="secondary" :disabled="logoutSubmitting" @click="logoutConfirmOpen = false">
          继续使用
        </AppButton>
        <AppButton variant="destructive" :disabled="logoutSubmitting" @click="confirmLogout">
          {{ logoutSubmitting ? '退出中' : '退出登录' }}
        </AppButton>
      </div>
    </AppDialog>
  </main>
</template>
