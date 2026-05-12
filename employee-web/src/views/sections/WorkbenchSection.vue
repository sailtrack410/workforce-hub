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

const activeUploadableFiles = () => activeFile.value.files.filter((item: { attachmentRefId?: unknown }) => item.attachmentRefId && !isFileTrashed(item));
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">{{ activeWorkbenchTitle }}</h2>
        <div class="workspace-meta-line">
          <span>{{ activeWorkbench.subtitle }}</span>
        </div>
      </div>
    </div>
    <div class="head-actions">
      <AppButton v-if="activeWorkbench.id === 'requests'" variant="secondary" size="sm" :disabled="workflowRequestsLoading" @click="loadWorkflowRequests">
        {{ workflowRequestsLoading ? '刷新中' : '刷新' }}
      </AppButton>
      <AppButton v-if="activeWorkbench.id === 'attendance'" variant="secondary" size="sm" :disabled="attendanceLoading" @click="refreshAttendancePanel">
        {{ attendanceLoading ? '刷新中' : '刷新' }}
      </AppButton>
    </div>
  </header>

  <section class="content-stack">
    <template v-if="activeWorkbench.id === 'leave'">
      <section class="highlight-grid attendance-highlight-grid">
        <article v-for="item in workbenchHighlights" :key="item.label" class="highlight-card">
          <small>{{ item.label }}</small>
          <strong>{{ item.value }}</strong>
          <span>{{ item.note }}</span>
        </article>
      </section>

      <section class="detail-grid">
        <article class="detail-card">
          <h3>请假申请</h3>
          <form class="workbench-form" @submit.prevent="submitLeaveRequest">
            <label class="security-field">
              <span>假期类型</span>
              <select v-model="leaveForm.leaveTypeId" class="ui-inline-input">
                <option v-for="account in leaveAccounts" :key="account.leaveTypeId" :value="String(account.leaveTypeId)">
                  {{ account.leaveName }}
                </option>
              </select>
            </label>
            <label class="security-field">
              <span>开始时间</span>
              <input v-model="leaveForm.startAt" class="ui-inline-input" type="datetime-local" />
            </label>
            <label class="security-field">
              <span>结束时间</span>
              <input v-model="leaveForm.endAt" class="ui-inline-input" type="datetime-local" />
            </label>
            <label class="security-field">
              <span>请假分钟</span>
              <input v-model="leaveForm.durationMinutes" class="ui-inline-input" type="number" min="1" step="1" />
            </label>
            <label class="security-field form-full">
              <span>请假原因</span>
              <textarea v-model="leaveForm.reason" class="ui-textarea compact-textarea" rows="4" />
            </label>
            <label v-if="activeFile.files.length" class="security-field form-full">
              <span>申请附件</span>
              <select v-model="leaveAttachmentIds" class="ui-inline-input" multiple>
                <option
                  v-for="file in activeUploadableFiles()"
                  :key="file.id"
                  :value="file.attachmentRefId"
                >
                  {{ file.name }}
                </option>
              </select>
            </label>
            <p v-if="leaveError" class="dialog-error form-full">{{ leaveError }}</p>
            <p v-if="leaveResult" class="form-result form-full">申请编号：{{ leaveResult.requestNo }} · {{ statusText(leaveResult.status) }}</p>
            <div class="form-actions form-full">
              <AppButton variant="primary" type="submit" :disabled="leaveSubmitting || !leaveAccounts.length">
                {{ leaveSubmitting ? '提交中' : '提交申请' }}
              </AppButton>
            </div>
          </form>
        </article>

        <article class="detail-card">
          <h3>假期账户</h3>
          <dl class="field-grid">
            <div>
              <dt>假期类型</dt>
              <dd>{{ selectedLeaveAccount?.leaveName ?? '暂无' }}</dd>
            </div>
            <div>
              <dt>可用余额</dt>
              <dd>{{ minutesToDayLabel(selectedLeaveAccount?.availableMinutes) }}</dd>
            </div>
            <div>
              <dt>冻结余额</dt>
              <dd>{{ minutesToDayLabel(selectedLeaveAccount?.frozenMinutes) }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ statusText(selectedLeaveAccount?.status ?? '') }}</dd>
            </div>
          </dl>
        </article>
      </section>
    </template>

    <template v-else-if="activeWorkbench.id === 'requests'">
      <AppErrorState
        v-if="workflowRequestsError"
        title="申请加载失败"
        :description="workflowRequestsError"
        action-label="重试"
        @retry="loadWorkflowRequests"
      />
      <section class="detail-grid workbench-split">
        <article class="detail-card">
          <div class="title-line">
            <h3>我的申请</h3>
            <span class="meta-time">{{ workflowRequestsTotal }} 条</span>
          </div>
          <AppLoadingState v-if="workflowRequestsLoading" />
          <AppEmptyState v-else-if="!workflowRequests.length" title="暂无申请" description="暂无申请" />
          <div v-else class="request-list">
            <button
              v-for="request in workflowRequests"
              :key="request.requestId"
              type="button"
              :class="{ active: request.requestId === selectedRequestId }"
              @click="loadWorkflowRequestDetail(request.requestId)"
            >
              <strong>{{ request.title }}</strong>
              <span>{{ request.requestNo }}</span>
              <small>{{ bizTypeLabel(request.bizType) }} · {{ statusText(request.status) }} · {{ formatDateTime(request.submittedAt) }}</small>
            </button>
          </div>
        </article>

        <article class="detail-card">
          <div class="title-line">
            <h3>申请详情</h3>
            <AppTag v-if="selectedRequest" :tone="toneOfStatus(selectedRequest.status)">{{ statusText(selectedRequest.status) }}</AppTag>
          </div>
          <AppLoadingState v-if="workflowDetailLoading" />
          <AppEmptyState v-else-if="!workflowDetail" title="暂无详情" description="暂无详情" />
          <div v-else class="request-detail-stack">
            <dl class="field-grid">
              <div v-for="row in workflowDetailRows" :key="row[0]">
                <dt>{{ row[0] }}</dt>
                <dd>{{ row[1] }}</dd>
              </div>
            </dl>
            <div v-if="workflowDetail.attachments.length" class="file-list">
              <div v-for="file in workflowDetail.attachments" :key="file.attachmentRefId" class="file-row">
                <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
                  <component :is="fileIconOf(file)" :size="18" />
                </span>
                <div>
                  <strong>{{ file.displayName }}</strong>
                  <small>{{ fileTypeLabelFromName(file.displayName, file.mimeType) }} · {{ fileSizeLabel(file.fileSize) }} · {{ statusText(file.scanStatus) }}</small>
                </div>
                <div class="file-actions">
                  <button type="button" @click="previewWorkflowAttachment(file)">
                    <Eye :size="15" />
                    预览
                  </button>
                  <button type="button" @click="downloadWorkflowAttachment(file)">
                    <Download :size="15" />
                    下载
                  </button>
                </div>
              </div>
            </div>
            <div class="timeline-list">
              <span v-for="action in workflowActions" :key="action.actionId">
                <strong>{{ actionTypeLabel(action.actionType) }}</strong>
                <small>{{ displayField(action.stepName) }} · {{ formatDateTime(action.actedAt) }}</small>
                <em v-if="action.comment">{{ action.comment }}</em>
              </span>
            </div>
            <div v-if="workflowDetail.status === 'IN_APPROVAL'" class="withdraw-box">
              <textarea v-model="withdrawComment" class="ui-textarea compact-textarea" placeholder="撤回原因" rows="3" />
              <AppButton variant="secondary" size="sm" :disabled="withdrawSubmitting" @click="withdrawWorkflowRequest">
                {{ withdrawSubmitting ? '撤回中' : '撤回申请' }}
              </AppButton>
            </div>
            <p v-if="workflowDetailError" class="dialog-error">{{ workflowDetailError }}</p>
          </div>
        </article>
      </section>
    </template>

    <template v-else-if="activeWorkbench.id === 'attendance'">
      <section class="highlight-grid">
        <article v-for="row in attendanceSummary" :key="row[0]" class="highlight-card">
          <small>{{ row[0] }}</small>
          <strong>{{ row[1] }}</strong>
        </article>
      </section>
      <section class="detail-grid">
        <article class="detail-card">
          <h3>打卡</h3>
          <div class="punch-actions">
            <AppButton variant="primary" :disabled="Boolean(punchLoading)" @click="punchAttendance('ON_DUTY')">
              {{ punchLoading === 'ON_DUTY' ? '打卡中' : '上班打卡' }}
            </AppButton>
            <AppButton variant="secondary" :disabled="Boolean(punchLoading)" @click="punchAttendance('OFF_DUTY')">
              {{ punchLoading === 'OFF_DUTY' ? '打卡中' : '下班打卡' }}
            </AppButton>
          </div>
          <p v-if="punchResult" class="form-result">
            {{ punchTypeLabel(punchResult.punchType) }} · {{ formatDateTime(punchResult.punchAt) }} · {{ statusText(punchResult.status) }}
          </p>
          <p v-if="attendanceError" class="dialog-error">{{ attendanceError }}</p>
        </article>

        <article class="detail-card">
          <h3>补卡申请</h3>
          <form class="workbench-form single-column" @submit.prevent="submitClockCorrectionRequest">
            <label class="security-field">
              <span>考勤日期</span>
              <input v-model="clockCorrectionForm.targetDate" class="ui-inline-input" type="date" />
            </label>
            <label class="security-field">
              <span>缺卡类型</span>
              <select v-model="clockCorrectionForm.missingPunchType" class="ui-inline-input">
                <option value="ON_DUTY">上班</option>
                <option value="OFF_DUTY">下班</option>
              </select>
            </label>
            <label class="security-field">
              <span>补卡时间</span>
              <input v-model="clockCorrectionForm.expectedPunchAt" class="ui-inline-input" type="datetime-local" />
            </label>
            <label class="security-field">
              <span>补卡原因</span>
              <textarea v-model="clockCorrectionForm.reason" class="ui-textarea compact-textarea" rows="3" />
            </label>
            <label v-if="activeFile.files.length" class="security-field">
              <span>补卡附件</span>
              <select v-model="clockCorrectionAttachmentIds" class="ui-inline-input" multiple>
                <option
                  v-for="file in activeUploadableFiles()"
                  :key="file.id"
                  :value="file.attachmentRefId"
                >
                  {{ file.name }}
                </option>
              </select>
            </label>
            <p v-if="clockCorrectionError" class="dialog-error">{{ clockCorrectionError }}</p>
            <p v-if="clockCorrectionResult" class="form-result">申请编号：{{ clockCorrectionResult.requestNo }} · {{ statusText(clockCorrectionResult.status) }}</p>
            <div class="form-actions">
              <AppButton variant="primary" type="submit" :disabled="clockCorrectionSubmitting">
                {{ clockCorrectionSubmitting ? '提交中' : '提交补卡' }}
              </AppButton>
            </div>
          </form>
        </article>
      </section>
      <section class="detail-grid">
        <article class="detail-card">
          <h3>日结果</h3>
          <AppLoadingState v-if="attendanceLoading" />
          <AppEmptyState v-else-if="!attendanceDailyResults.length" title="暂无日结果" description="暂无日结果" />
          <div v-else class="compact-table">
            <div v-for="item in attendanceDailyResults" :key="item.dailyResultId">
              <strong>{{ item.workDate }}</strong>
              <span>{{ statusText(item.resultStatus) }}</span>
              <small>迟到 {{ item.lateMinutes ?? 0 }} · 早退 {{ item.earlyLeaveMinutes ?? 0 }} · 缺勤 {{ item.absenceMinutes ?? 0 }}</small>
            </div>
          </div>
        </article>
        <article class="detail-card">
          <h3>团队考勤</h3>
          <AppLoadingState v-if="attendanceLoading" />
          <AppEmptyState v-else-if="!teamAttendanceRows.length" title="暂无团队考勤" description="暂无团队考勤" />
          <div v-else class="compact-table">
            <div v-for="item in teamAttendanceRows" :key="`team-${item.userId}-${item.workDate}`">
              <strong>{{ item.workDate }}</strong>
              <span>{{ statusText(item.resultStatus) }}</span>
              <small>员工 {{ item.userId }} · 迟到 {{ item.lateMinutes ?? 0 }} · 早退 {{ item.earlyLeaveMinutes ?? 0 }}</small>
            </div>
          </div>
        </article>
        <article class="detail-card">
          <h3>异常</h3>
          <AppEmptyState v-if="!attendanceExceptions.length" title="暂无异常" description="暂无异常" />
          <div v-else class="compact-table">
            <div v-for="item in attendanceExceptions" :key="item.exceptionId">
              <strong>{{ item.workDate }}</strong>
              <span>{{ statusText(item.status) }}</span>
              <small>{{ item.exceptionDesc || item.exceptionType }}</small>
            </div>
          </div>
        </article>
      </section>
    </template>

    <template v-else-if="activeWorkbench.id === 'balance'">
      <section class="leave-account-grid">
        <article v-for="account in leaveAccounts" :key="account.accountId" class="detail-card leave-account-card">
          <small>{{ account.leaveCode }}</small>
          <h3>{{ account.leaveName }}</h3>
          <strong>{{ minutesToDayLabel(account.availableMinutes) }}</strong>
          <dl class="field-grid">
            <div>
              <dt>冻结</dt>
              <dd>{{ minutesToDayLabel(account.frozenMinutes) }}</dd>
            </div>
            <div>
              <dt>状态</dt>
              <dd>{{ statusText(account.status) }}</dd>
            </div>
          </dl>
        </article>
        <AppEmptyState v-if="!leaveAccounts.length" title="暂无假期账户" description="暂无假期账户" />
      </section>
    </template>

    <template v-else-if="activeWorkbench.id === 'agent'">
      <section class="detail-grid">
        <article class="detail-card">
          <div class="title-line">
            <h3>可用Agent</h3>
            <AppButton variant="secondary" size="sm" :disabled="agentsLoading" @click="loadAgents">
              {{ agentsLoading ? '加载中' : '刷新' }}
            </AppButton>
          </div>
          <AppLoadingState v-if="agentsLoading" />
          <AppEmptyState v-else-if="!agents.length" title="暂无Agent" description="暂无Agent" />
          <div v-else class="compact-table">
            <div v-for="agent in agents" :key="agent.agentId">
              <strong>{{ agent.agentName }}</strong>
              <span>{{ agent.agentType }}</span>
              <small>{{ agent.agentCode }} · {{ statusText(agent.status) }}</small>
            </div>
          </div>
        </article>

        <article class="detail-card">
          <h3>Agent任务</h3>
          <form class="workbench-form single-column" @submit.prevent="submitAgentTask">
            <label class="security-field">
              <span>Agent</span>
              <select v-model="agentTaskForm.agentId" class="ui-inline-input">
                <option v-for="agent in agents" :key="agent.agentId" :value="String(agent.agentId)">
                  {{ agent.agentName }}
                </option>
              </select>
            </label>
            <label class="security-field">
              <span>输入</span>
              <textarea v-model="agentTaskForm.inputText" class="ui-textarea compact-textarea" rows="3" />
            </label>
            <div class="form-actions">
              <AppButton variant="primary" type="submit" :disabled="agentTaskSubmitting || !agentTaskForm.agentId">
                {{ agentTaskSubmitting ? '提交中' : '创建任务' }}
              </AppButton>
            </div>
          </form>
          <p v-if="agentTaskError" class="dialog-error">{{ agentTaskError }}</p>
          <p v-if="agentTaskResult" class="form-result">任务：{{ agentTaskResult.taskId }} · {{ statusText(agentTaskResult.status) }}</p>
        </article>
      </section>

      <section class="detail-grid">
        <article class="detail-card">
          <h3>任务确认</h3>
          <div class="security-form">
            <label class="security-field">
              <span>任务ID</span>
              <input v-model="agentTaskForm.taskId" class="ui-inline-input" inputmode="numeric" />
            </label>
            <label class="security-field">
              <span>意见</span>
              <textarea v-model="agentTaskForm.comment" class="ui-textarea compact-textarea" rows="3" />
            </label>
            <div class="form-actions">
              <AppButton variant="primary" size="sm" :disabled="agentTaskSubmitting || !agentTaskForm.taskId" @click="confirmAgentTask(true)">
                确认继续
              </AppButton>
              <AppButton variant="secondary" size="sm" :disabled="agentTaskSubmitting || !agentTaskForm.taskId" @click="confirmAgentTask(false)">
                拒绝继续
              </AppButton>
            </div>
          </div>
        </article>

        <article class="detail-card">
          <h3>取消任务</h3>
          <div class="security-form">
            <label class="security-field">
              <span>任务ID</span>
              <input v-model="agentTaskForm.taskId" class="ui-inline-input" inputmode="numeric" />
            </label>
            <label class="security-field">
              <span>原因</span>
              <textarea v-model="agentTaskForm.cancelReason" class="ui-textarea compact-textarea" rows="3" />
            </label>
            <div class="form-actions">
              <AppButton variant="destructive" size="sm" :disabled="agentTaskSubmitting || !agentTaskForm.taskId || !agentTaskForm.cancelReason.trim()" @click="cancelAgentTask">
                取消任务
              </AppButton>
            </div>
          </div>
        </article>
      </section>
    </template>
  </section>
</div>
</template>
