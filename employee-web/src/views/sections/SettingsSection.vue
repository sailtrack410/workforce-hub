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
        <h2 class="workspace-title">{{ activeProfile.title }}</h2>
        <div class="workspace-meta-line">
          <span>{{ currentUserName }}</span>
          <span v-if="session.departmentName">{{ session.departmentName }}</span>
        </div>
      </div>
    </div>
  </header>

  <section class="content-stack settings-dashboard">
    <section v-if="selectedProfileId === 'center'" class="settings-card-grid">
      <article class="settings-card settings-profile-card">
        <div class="settings-profile-main">
          <AppAvatar :label="currentUserShortName" tone="primary" size="lg" status="online" />
          <div>
            <h3>{{ currentUserName }}</h3>
            <span>{{ session.positionName || session.departmentName || profileStatusLabel }}</span>
          </div>
        </div>
        <dl class="settings-list compact">
          <div>
            <dt>工号</dt>
            <dd>{{ displayField(session.profile?.employeeNo) }}</dd>
          </div>
          <div>
            <dt>登录名</dt>
            <dd>{{ displayField(session.user?.loginName) }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>{{ profileStatusLabel }}</dd>
          </div>
        </dl>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>账号安全</h3>
        </header>
        <dl class="settings-list compact">
          <div v-for="row in accountSecurityRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>

      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>工作概览</h3>
        </header>
        <div class="settings-metric-grid">
          <div
            v-for="metric in settingsMetrics"
            :key="metric.label"
            class="settings-metric-card"
            :style="{ '--metric-level': `${metric.level}%` }"
          >
            <small>{{ metric.label }}</small>
            <strong>{{ metric.value }}</strong>
            <span>{{ metric.detail }}</span>
            <i />
          </div>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>快捷操作</h3>
        </header>
        <div class="settings-action-list">
          <button type="button" @click="shell.selectItem('settings', 'profile')">个人资料</button>
          <button type="button" @click="shell.selectItem('settings', 'security')">账号安全</button>
          <button type="button" @click="shell.selectItem('settings', 'preferences')">偏好设置</button>
          <button type="button" @click="shell.selectItem('settings', 'storage')">文件空间</button>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>组织关系</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in organizationRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'profile'" class="settings-card-grid">
      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>个人资料</h3>
        </header>
        <dl class="settings-list two-column">
          <div class="settings-list-row is-editable">
            <dt>手机号</dt>
            <dd>
              <span>{{ displayField(session.profile?.mobile) }}</span>
              <button class="field-icon-button" type="button" aria-label="修改手机号" @click="openMobileReset">
                <PencilLine :size="14" />
              </button>
            </dd>
          </div>
          <div v-for="row in profileIdentityRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'organization'" class="settings-card-grid">
      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>组织架构</h3>
        </header>
        <div class="settings-org-chart">
          <div class="settings-org-node department-node">{{ relationTree.department }}</div>
          <div class="settings-org-level">
            <div v-if="relationTree.managerName" class="settings-org-node">直属上级：{{ relationTree.managerName }}</div>
            <div class="settings-org-node current-user-node">{{ relationTree.ownName }}</div>
          </div>
          <div v-if="relationTree.subordinates.length" class="settings-org-level child-level">
            <div v-for="user in relationTree.subordinates" :key="user.userId" class="settings-org-node">
              {{ user.realName }}
            </div>
          </div>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>组织信息</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in organizationRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>

      <article v-if="relationTree.conversationMembers.length" class="settings-card">
        <header class="settings-card-head">
          <h3>会话成员</h3>
        </header>
        <div class="settings-member-list">
          <span v-for="contact in relationTree.conversationMembers" :key="contact.id">
            {{ contact.name }}
          </span>
        </div>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'security'" class="settings-card-grid">
      <article class="settings-card">
        <header class="settings-card-head">
          <h3>账号安全</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in accountSecurityRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>手机号</h3>
        </header>
        <div class="security-form">
          <label class="security-field">
            <span>手机号</span>
            <input
              ref="mobileInput"
              v-model="mobileDraft"
              class="ui-inline-input"
              type="tel"
              inputmode="tel"
              autocomplete="tel"
            />
          </label>
          <div class="form-actions">
            <AppButton variant="primary" size="sm" :disabled="mobileSubmitting" @click="submitMobileUpdate">
              {{ mobileSubmitting ? '保存中' : '保存手机号' }}
            </AppButton>
          </div>
          <p v-if="mobileUpdateNotice" class="form-result">{{ mobileUpdateNotice }}</p>
          <p v-if="mobileUpdateError" class="dialog-error">{{ mobileUpdateError }}</p>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>邮箱绑定</h3>
        </header>
        <div class="security-form">
          <div v-if="currentEmail && !bindEmailEditing" class="bound-email-row">
            <div>
              <span>当前邮箱</span>
              <strong>{{ currentEmail }}</strong>
            </div>
            <AppButton variant="secondary" size="sm" @click="startBindEmailEditing">
              换绑
            </AppButton>
          </div>
          <label class="security-field">
            <span>{{ currentEmail ? '新邮箱' : '邮箱地址' }}</span>
            <AppInput v-model="bindEmail" type="email" placeholder="邮箱地址" />
          </label>
          <label v-if="!currentEmail || bindEmailEditing" class="security-field">
            <span>验证码</span>
            <AppInput v-model="bindEmailCode" placeholder="验证码" />
          </label>
          <div v-if="!currentEmail || bindEmailEditing" class="form-actions">
            <AppButton v-if="currentEmail" variant="ghost" size="sm" @click="cancelBindEmailEditing">
              取消
            </AppButton>
            <AppButton variant="secondary" size="sm" :disabled="bindEmailCodeSending || !bindEmail.trim()" @click="sendBindEmailCode">
              {{ bindEmailCodeSending ? '发送中' : '发送验证码' }}
            </AppButton>
            <AppButton variant="primary" size="sm" :disabled="bindEmailSubmitting || !bindEmail.trim() || !bindEmailCode.trim()" @click="submitBindEmail">
              {{ bindEmailSubmitting ? '提交中' : currentEmail ? '确认换绑' : '绑定邮箱' }}
            </AppButton>
          </div>
          <p v-if="bindEmailNotice" class="form-result">{{ bindEmailNotice }}</p>
          <p v-if="bindEmailError" class="dialog-error">{{ bindEmailError }}</p>
        </div>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'preferences'" class="settings-card-grid">
      <article class="settings-card">
        <header class="settings-card-head">
          <h3>显示设置</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in preferenceRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
        <div class="settings-control-row">
          <AppButton variant="secondary" size="sm" @click="shell.toggleTheme()">
            <component :is="shell.theme === 'light' ? MoonStar : SunMedium" :size="14" />
            切换到{{ shell.theme === 'light' ? '深色' : '浅色' }}
          </AppButton>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>会话密度</h3>
        </header>
        <div class="settings-density-row">
          <button
            type="button"
            :class="{ active: shell.messageDensity === 'cozy' }"
            @click="shell.setMessageDensity('cozy')"
          >
            宽松
          </button>
          <button
            type="button"
            :class="{ active: shell.messageDensity === 'compact' }"
            @click="shell.setMessageDensity('compact')"
          >
            紧凑
          </button>
        </div>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'storage'" class="settings-card-grid">
      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>文件空间</h3>
          <AppButton variant="primary" size="sm" :disabled="fileUploading" @click="runAction({ action: 'upload-personal-file', label: '上传文件' })">
            {{ fileUploading ? '上传中' : '上传文件' }}
          </AppButton>
        </header>
        <div class="settings-storage-layout">
          <div class="settings-storage-chart" :style="{ '--storage-level': `${activeFileUsage}%` }">
            <strong>{{ activeFileUsage }}%</strong>
            <span>已用</span>
          </div>
          <dl class="settings-list">
            <div v-for="row in storageRows" :key="row[0]">
              <dt>{{ row[0] }}</dt>
              <dd>{{ row[1] }}</dd>
            </div>
          </dl>
        </div>
      </article>

      <article v-if="recentFileRows.length" class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>最近文件</h3>
        </header>
        <div class="file-list">
          <div v-for="file in recentFileRows" :key="file.id" class="file-row">
            <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
              <component :is="fileIconOf(file)" :size="18" />
            </span>
            <div>
              <strong>{{ file.name }}</strong>
              <small>{{ file.type }} · {{ file.size }} · {{ file.createdAt }} · {{ file.status }}</small>
            </div>
            <div class="file-actions">
              <button type="button" :disabled="!file.attachmentRefId || isFileTrashed(file)" @click="openPersonalFilePreview(file)">
                <Eye :size="15" />
                预览
              </button>
              <button type="button" :disabled="!file.attachmentRefId || isFileTrashed(file)" @click="downloadPersonalFile(file)">
                <Download :size="15" />
                下载
              </button>
            </div>
          </div>
        </div>
      </article>
    </section>
  </section>
</div>
</template>
