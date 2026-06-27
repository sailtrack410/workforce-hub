<script setup lang="ts">
import {
  ArrowUpRight,
  BarChart3,
  CalendarDays,
  ChevronLeft,
  CheckCircle2,
  CircleAlert,
  Clock3,
  ClipboardList,
  CloudUpload,
  FileText,
  FolderOpen,
  GitBranch,
  LayoutGrid,
  MessageCircle,
  MoonStar,
  PanelLeftClose,
  PanelLeftOpen,
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
import type { PersonalFileRecord, SectionAction, SectionFilter, SectionId } from '../stores/shell';
import {
  primaryNavItems,
  sectionFilters,
  shellUser,
  useShellStore,
  normalizeSettingsPanel,
  normalizeWorkbenchApp,
} from '../stores/shell';
import { useImStore } from '../stores/im';
import { useSessionStore } from '../stores/session';
import { useWorkflowStore } from '../stores/workflow';
import { useCurrentUserAvatarUrl } from '../composables/useCurrentUserAvatarUrl';
import { useEmployeeAuthFlow } from '../composables/useEmployeeAuthFlow';
import { useFileUpload } from '../composables/useFileUpload';
import { useImSocket } from '../composables/useImSocket';
import { fileSizeLabel, fileTypeLabel } from '@/utils/file-format';
import {
  statusText,
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
import AppTag from '../components/ui/AppTag.vue';
import ShellNavButton from '../components/shell/ShellNavButton.vue';
import { provideEmployeeShellContexts } from './employee-shell-context';
import type { EmployeeShellContexts } from './employee-shell-context';

const shell = useShellStore();
const im = useImStore();
const session = useSessionStore();
const workflow = useWorkflowStore();
const router = useRouter();
const route = useRoute();

const searchOpen = ref(true);
const resizing = ref(false);
const toastMessage = ref('');
const filterScroller = ref<HTMLElement | null>(null);
const filtersOverflow = ref(false);
const fileSendTarget = ref<{ file: PersonalFileRecord; caption: string; submitting: boolean } | null>(null);
const fileSendTargetQuery = ref('');
const fileSendTargetError = ref('');
const todoActionMode = ref<'approve' | 'reject' | null>(null);
const todoActionComment = ref('');
const todoActionError = ref('');
let toastTimer: number | undefined;

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
  storage: FolderOpen,
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
const searchAgentResults = ref<AgentResponse[]>([]);
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
const mustHandleTodos = computed(() => filteredTodos.value.filter((item) => item.priority === '高').slice(0, 5));
const approvalTodos = computed(() => filteredTodos.value.filter((item) => item.priority !== '低'));
const deferredTodos = computed(() => filteredTodos.value.filter((item) => item.priority === '低'));
const workbenchLaunchers = computed(() => filteredWorkbench.value);
const hasActiveConversation = computed(() => Boolean(activeConversation.value.id));
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
  hasActiveConversation,
  showToast,
  sendSelectedFileToConversation,
});
const { refreshCurrentUserAvatarUrl } = useCurrentUserAvatarUrl(shell, session);
const hasActiveTodo = computed(() => Boolean(activeTodo.value.id));
const businessEmptyTitle = computed(() => {
  if (activeSection.value === 'processes') return '暂无流程';
  if (activeSection.value === 'contacts') return '暂无联系人';
  if (activeSection.value === 'reports') return '暂无报表';
  return '暂无数据';
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

function showToast(message: string) {
  toastMessage.value = message;
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => {
    toastMessage.value = '';
    toastTimer = undefined;
  }, 2600);
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

async function loadAuthenticatedWorkspace() {
  if (!session.isAuthenticated) return;
  await Promise.all([
    session.loadOrgProfile().then(() => {
      shell.setColleagues(session.colleagues);
      shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    }),
    workflow.loadTodos().then(() => {
      shell.mergeBackendTodos(workflow.todos);
    }),
    im.loadConversations().then(() => {
      shell.mergeBackendConversations(im.conversations);
    }),
    refreshFileCenter(),
    hydrateUserPreferences(),
  ]);
  await im.loadVisibleConversationDetails();
  shell.mergeBackendConversations(im.conversations);
  shell.setColleagues(session.colleagues);
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
    return;
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
  shell.setColleagues(session.colleagues);
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
  navigateMessagesConversation(id);
  void syncConversationMessages(id);
}

async function startAgentConversationFromSearch(agent: AgentResponse) {
  try {
    const conversation = await im.startAgentConversation(agent.agentId);
    shell.mergeBackendConversations(im.conversations);
    navigateMessagesConversation(conversation.id);
    await syncConversationMessages(conversation.id);
    shell.search.messages = '';
    showToast('Agent会话已创建');
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Agent会话创建失败');
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
    if (target === 'requests') {
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
  if (searchAgentsTimer) window.clearTimeout(searchAgentsTimer);
  resetAuthCooldowns();
  closeImSocket();
});

watch(() => session.profile?.avatarAttachmentRefId, () => {
  void refreshCurrentUserAvatarUrl();
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
          if (requestSeq === searchAgentsRequestSeq) searchAgentResults.value = agents;
        })
        .catch((error) => {
          if (requestSeq !== searchAgentsRequestSeq) return;
          searchAgentResults.value = [];
          searchAgentsError.value = error instanceof Error ? error.message : 'Agent搜索失败';
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
      if (typeof app === 'string') {
        const canonical = normalizeWorkbenchApp(app);
        shell.selectItem('workbench', canonical);
        if (canonical !== app) {
          void router.replace({ name: 'employee-workbench-detail', params: { app: canonical } });
        }
      } else {
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
  }
});

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
    workflow,
    activeTodo,
    pendingTodoCount,
    mustHandleTodos,
    approvalTodos,
    deferredTodos,
    hasActiveTodo,
    backToList,
    runAction,
    retryTodos,
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
        <label v-if="loginRequiresEmailBinding" class="auth-field">
          <span>绑定邮箱</span>
          <AppInput v-model="loginBindEmail" type="email" placeholder="邮箱地址" />
        </label>
        <div v-if="loginRequiresEmailCode || loginRequiresEmailBinding" class="auth-code-row">
          <label class="auth-field">
            <span>邮箱验证码</span>
            <AppInput v-model="loginEmailCode" placeholder="邮箱验证码" />
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
            :disabled="resetCodeSending || resetCodeCooldown.isActive.value || !resetEmail.trim()"
            @click="sendResetCode"
          >
            {{ resetCodeButtonText }}
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
          <AppAvatar :label="currentUserShortName" :image="shell.avatarUrl" tone="primary" size="lg" status="online" />
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
            v-if="!filteredConversations.length && !filteredSearchAgentResults.length && !searchAgentsLoading && !searchAgentsError"
            title="会话暂时为空"
            :description="listEmptyText"
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
                      :label="agent.agentName.slice(0, 2)"
                      tone="success"
                      status="online"
                    />
                    <span class="conversation-kind-glyph kind-agent" aria-hidden="true">
                      <Sparkles :size="11" />
                    </span>
                  </div>
                  <div class="message-copy">
                    <div class="message-title-line">
                      <strong>{{ agent.agentName }}</strong>
                      <span class="conversation-kind-pill pill-agent">可添加</span>
                    </div>
                    <p>{{ agent.agentCode }} · {{ agent.agentType }}</p>
                  </div>
                </div>
                <div class="message-row-side">
                  <span class="meta-time">开始会话</span>
                </div>
              </div>
            </AppListItem>
            <div v-if="searchAgentsLoading" class="agent-search-hint">正在搜索可添加Agent</div>
            <div v-else-if="searchAgentsError" class="agent-search-hint error">{{ searchAgentsError }}</div>
            <AppListItem
              v-for="item in filteredConversations"
              :key="item.id"
              :active="item.id === activeConversation.id"
              @click="selectConversation(item.id)"
            >
              <div class="message-row" :class="`message-row-${item.kind === 'team' ? 'team' : item.kind === 'agent' ? 'agent' : 'private'}`">
                <div class="message-row-main">
                  <div class="message-avatar-wrap">
                    <AppAvatar
                      :label="item.title"
                      :image="item.avatarUrl"
                      :tone="toneOfConversation(item.tone)"
                      :status="item.kind === 'agent' ? 'online' : ''"
                    >
                      <UserRound :size="20" />
                    </AppAvatar>
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
          </template>
        </div>

        <div v-else-if="activeSection === 'todos'" class="list-items">
          <AppEmptyState v-if="!filteredTodos.length" title="待办已清空" :description="listEmptyText" icon="todos" />
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
          <AppEmptyState v-if="!filteredFiles.length" title="文件空间为空" :description="listEmptyText" icon="files" />
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
          <AppEmptyState v-if="!filteredContacts.length" title="暂无联系人" description="暂无联系人" icon="contacts" />
          <AppListItem
            v-for="item in filteredContacts"
            v-else
            :key="item.id"
            :active="activeContact?.id === item.id"
            @click="shell.selectItem('contacts', item.id)"
          >
            <div class="generic-row">
              <AppAvatar :label="item.name.slice(0, 1)" :image="item.avatarUrl" tone="primary" />
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
