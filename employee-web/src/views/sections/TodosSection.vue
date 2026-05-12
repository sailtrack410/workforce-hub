<script setup lang="ts">
import {
  AtSign,
  ChevronLeft,
  Clock3,
  Download,
  Eye,
  FolderOpen,
  Info,
  Send,
  Sparkles,
  Trash2,
  UsersRound,
  MoonStar,
  SunMedium,
  PencilLine,
  LayoutGrid,
  Settings,
} from 'lucide-vue-next';
import { useEmployeeShellContext } from '../employee-shell-context';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppBadge from '../../components/ui/AppBadge.vue';
import AppButton from '../../components/ui/AppButton.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppErrorState from '../../components/ui/AppErrorState.vue';
import AppInput from '../../components/ui/AppInput.vue';
import AppLoadingState from '../../components/ui/AppLoadingState.vue';
import AppSkeleton from '../../components/ui/AppSkeleton.vue';
import AppTag from '../../components/ui/AppTag.vue';
import ConversationProcessCard from '../../components/shell/ConversationProcessCard.vue';
import MessageBubble from '../../components/shell/MessageBubble.vue';

const {
shell,
  im,
  session,
  workflow,
  draft,
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
  leaveAccounts,
  attendanceExceptions,
  todoActionComment,
  todoActionError,
  fileUploading,
  agentTaskForm,
  agentTaskSubmitting,
  agentTaskResult,
  agentTaskError,
  agents,
  agentsLoading,
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
  attendanceLoading,
  attendanceError,
  punchLoading,
  punchResult,
  clockCorrectionForm,
  clockCorrectionAttachmentIds,
  clockCorrectionSubmitting,
  clockCorrectionResult,
  clockCorrectionError,
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
  currentMeta,
  currentUserName,
  currentUserShortName,
  activeFilter,
  activeConversationTitle,
  activeConversationCurrentStep,
  mustHandleTodos,
  approvalTodos,
  deferredTodos,
  workbenchLaunchers,
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
  selectedRequest,
  workflowDetailRows,
  attendanceSummary,
  teamAttendanceRows,
  relationTree,
  hasActiveConversation,
  hasActiveTodo,
  businessEmptyTitle,
  backToList,
  runAction,
  openDetail,
  openGroupPanel,
  openUploadPanel,
  openCreateConversation,
  insertAgentMention,
  sendMessage,
  openMessageAction,
  openImAttachment,
  retryTodos,
  statusText,
  toneOfStatus,
  submitLeaveRequest,
  isFileTrashed,
  minutesToDayLabel,
  loadWorkflowRequests,
  loadWorkflowRequestDetail,
  bizTypeLabel,
  formatDateTime,
  fileToneOf,
  fileIconOf,
  fileTypeLabelFromName,
  fileSizeLabel,
  previewWorkflowAttachment,
  downloadWorkflowAttachment,
  displayField,
  actionTypeLabel,
  withdrawWorkflowRequest,
  refreshAttendancePanel,
  punchAttendance,
  punchTypeLabel,
  submitClockCorrectionRequest,
  selectWorkbenchItem,
  loadAgents,
  submitAgentTask,
  confirmAgentTask,
  cancelAgentTask,
  sendSelectedFileToConversation,
  openPersonalFilePreview,
  downloadPersonalFile,
  trashPersonalFile,
  restorePersonalFile,
  purgePersonalFile,
  toneOfConversation,
  submitMobileUpdate,
  openMobileReset,
  sendBindEmailCode,
  submitBindEmail,
  startBindEmailEditing,
  cancelBindEmailEditing,
} = useEmployeeShellContext();
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">待办</h2>
        <div class="workspace-meta-line">
          <span>今日 {{ mustHandleTodos.length }} 项</span>
          <span>待审批 {{ approvalTodos.length }} 项</span>
        </div>
      </div>
    </div>
    <div class="head-actions">
      <AppButton variant="secondary" size="sm" @click="retryTodos">刷新待办</AppButton>
      <AppBadge tone="danger" kind="count">{{ pendingTodoCount }}</AppBadge>
    </div>
  </header>

  <section v-if="hasActiveTodo" class="content-stack">
    <article class="hero-panel">
      <div>
        <h3>{{ mustHandleTodos.length }} 项焦点待办</h3>
        <p>{{ activeTodo.title }}</p>
      </div>
      <div class="hero-actions">
        <AppButton variant="primary" :disabled="workflow.actionLoading" @click="runAction(activeTodo.actions[0])">
          {{ activeTodo.actions[0]?.label ?? '处理待办' }}
        </AppButton>
        <AppButton variant="secondary" :disabled="workflow.actionLoading" @click="runAction(activeTodo.actions[1])">
          {{ activeTodo.actions[1]?.label ?? '查看详情' }}
        </AppButton>
      </div>
    </article>

    <section class="card-section">
      <div class="section-head">
        <h3>今天必须处理</h3>
        <span>{{ mustHandleTodos.length }} / 5</span>
      </div>
      <div class="todo-grid">
        <article v-for="item in mustHandleTodos" :key="item.id" class="todo-card focus">
          <div class="title-line">
            <strong>{{ item.title }}</strong>
            <AppTag tone="warning">{{ item.priority }}</AppTag>
          </div>
          <p>{{ item.summary }}</p>
          <div class="micro-line">{{ item.requestNo }} · {{ item.currentStepName }}</div>
        </article>
      </div>
    </section>

    <section class="card-section">
      <div class="section-head">
        <h3>等待你的审批</h3>
        <span>{{ approvalTodos.length }}</span>
      </div>
      <div class="todo-grid compact">
        <article v-for="item in approvalTodos" :key="item.id" class="todo-card compact-card">
          <div class="title-line">
            <strong>{{ item.title }}</strong>
            <span class="meta-time">{{ item.time }}</span>
          </div>
          <p>{{ item.subtitle }}</p>
        </article>
      </div>
    </section>

    <details class="deferred-panel">
      <summary>延期 / 已读 {{ deferredTodos.length }} 项</summary>
      <div class="deferred-list">
        <article v-for="item in deferredTodos" :key="item.id" class="todo-card compact-card">
          <strong>{{ item.title }}</strong>
          <p>{{ item.note }}</p>
        </article>
      </div>
    </details>
  </section>
  <section class="content-stack">
    <AppEmptyState title="暂无待办" description="暂无待办" />
  </section>
</div>
</template>
