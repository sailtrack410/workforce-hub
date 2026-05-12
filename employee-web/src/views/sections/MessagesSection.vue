<script setup lang="ts">
import {
  AtSign,
  ChevronLeft,
  Clock3,
  Download,
  Eye,
  FolderOpen,
  Info,
  Paperclip,
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
<div class="workspace">
  <header v-if="hasActiveConversation" class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">{{ activeConversationTitle }}</h2>
        <div class="workspace-meta-line">
          <span>{{ activeConversation.meta }}</span>
          <span v-if="activeConversationCurrentStep">{{ activeConversationCurrentStep.label }}</span>
        </div>
      </div>
    </div>
    <div class="head-actions">
      <AppButton variant="secondary" size="sm" @click="runAction({ action: 'switch-section', section: 'todos', label: '查看待办' })">
        <Clock3 :size="16" />
        待办
      </AppButton>
      <AppButton variant="secondary" size="sm" @click="openDetail">
        <Info :size="16" />
        详情
      </AppButton>
      <AppButton v-if="activeConversation.kind === 'team'" variant="secondary" size="sm" @click="openGroupPanel">
        <UsersRound :size="16" />
        群管理
      </AppButton>
    </div>
  </header>

  <section v-if="hasActiveConversation" ref="messageStream" class="message-stream">
    <div v-if="im.messageLoading" class="stream-placeholder">
      <AppSkeleton />
      <AppSkeleton />
      <AppSkeleton />
    </div>

    <template v-else>
      <article
        v-for="message in activeConversation.messages"
        :key="message.id"
        class="stream-row"
        :class="[message.type === 'process-card' ? 'is-process' : '', message.side]"
      >
        <div v-if="message.side === 'system'" class="system-line">
          <strong>{{ message.author }} · {{ message.role }}</strong>
          <span>{{ message.body }}</span>
        </div>
        <ConversationProcessCard
          v-else-if="message.type === 'process-card' && message.processCard"
          :message="message"
          @open="openDetail"
        />
        <MessageBubble
          v-else
          :message="message"
          :agent="message.role.includes('Agent') || message.author.includes('助手')"
          @action="openMessageAction"
          @attachment="openImAttachment"
        />
      </article>
    </template>
  </section>

  <section class="content-stack">
    <AppEmptyState title="暂无会话" description="暂无消息" />
  </section>

  <footer v-if="hasActiveConversation" class="composer">
    <div class="composer-tools" aria-label="消息工具">
      <button class="composer-tool-button" type="button" @click="openUploadPanel">
        <Paperclip :size="16" />
        上传
      </button>
      <button class="composer-tool-button" type="button" @click="openDetail">
        <FolderOpen :size="16" />
        材料
      </button>
      <button class="composer-tool-button" type="button" @click="insertAgentMention">
        <AtSign :size="16" />
        Agent
      </button>
      <button class="composer-tool-button" type="button" @click="openCreateConversation('agent')">
        <Sparkles :size="16" />
        Agent会话
      </button>
    </div>
    <div class="composer-main-row">
      <div class="composer-input">
        <AppInput v-model="draft" placeholder="输入消息，支持 @Agent" :disabled="im.messageSending" @keydown.enter.prevent="sendMessage" />
      </div>
      <AppButton variant="primary" :disabled="im.messageSending" @click="sendMessage">
        <Send :size="18" />
        发送
      </AppButton>
    </div>
  </footer>
</div>
</template>
