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
        <h2 class="workspace-title">{{ activeFile.title }}</h2>
        <div class="workspace-meta-line">
          <span>{{ activeFile.subtitle }}</span>
          <span>{{ activeFile.note }}</span>
        </div>
      </div>
    </div>
    <div class="head-actions">
      <AppButton variant="primary" size="sm" :disabled="fileUploading" @click="runAction(currentMeta.primaryAction)">
        {{ fileUploading ? '上传中' : currentMeta.primaryAction?.label }}
      </AppButton>
    </div>
  </header>

  <section class="content-stack">
    <article class="hero-panel">
      <div>
        <h3>{{ activeFile.subtitle }}</h3>
        <p>{{ activeFile.note }}</p>
      </div>
      <div class="progress-ring">
        <svg viewBox="0 0 120 120" aria-hidden="true">
          <circle class="progress-ring-track" cx="60" cy="60" r="52" />
          <circle
            class="progress-ring-value"
            cx="60"
            cy="60"
            r="52"
            :stroke-dasharray="fileUsageCircumference"
            :stroke-dashoffset="activeFileUsageOffset"
          />
        </svg>
        <div class="progress-ring-text">
          <strong>{{ activeFileUsage }}%</strong>
          <span>已用空间</span>
        </div>
      </div>
    </article>

    <section class="detail-grid">
      <article class="detail-card">
        <h3>关键指标</h3>
        <dl class="field-grid">
          <div v-for="item in activeFile.summary" :key="item[0]">
            <dt>{{ item[1] }}</dt>
            <dd>{{ item[0] }}</dd>
          </div>
        </dl>
      </article>
      <article class="detail-card">
        <h3>最近文件</h3>
        <AppEmptyState v-if="!activeFile.files.length" title="暂无文件" description="暂无文件" />
        <div class="file-list">
          <div v-for="file in activeFile.files" :key="file.id" class="file-row">
            <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
              <component :is="fileIconOf(file)" :size="18" />
            </span>
            <div>
              <strong>{{ file.name }}</strong>
              <small>{{ file.type }} · {{ file.size }} · {{ file.createdAt }} · {{ file.status }}</small>
            </div>
            <div class="file-actions">
              <button type="button" :disabled="!file.attachmentRefId || im.messageSending || isFileTrashed(file)" @click="sendSelectedFileToConversation(file)">
                <Send :size="15" />
                发送
              </button>
              <button type="button" :disabled="!file.attachmentRefId || isFileTrashed(file)" @click="openPersonalFilePreview(file)">
                <Eye :size="15" />
                预览
              </button>
              <button type="button" :disabled="!file.attachmentRefId || isFileTrashed(file)" @click="downloadPersonalFile(file)">
                <Download :size="15" />
                下载
              </button>
              <button type="button" :disabled="!file.attachmentRefId || isFileTrashed(file)" @click="trashPersonalFile(file)">
                <Trash2 :size="15" />
                删除
              </button>
            </div>
          </div>
        </div>
      </article>
    </section>
  </section>
</div>
</template>
