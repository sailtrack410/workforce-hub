<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  Check,
  ChevronLeft,
  Download,
  Eye,
  ExternalLink,
  Folder,
  Home,
  Search,
  X,
} from 'lucide-vue-next';
import { employeeApi, type AgentResponse, type EmployeeFileFolderResponse, type EmployeeFileResponse, type EntityId, type LeaveAccountResponse } from '@/api';
import { useWorkbenchSectionContext } from '../employee-shell-context';
import { useWorkbenchState } from '@/composables/useWorkbenchState';
import { usePersonalFileTransfer } from '@/composables/usePersonalFileTransfer';
import {
  actionTypeLabel,
  bizTypeLabel,
  displayField,
  formatDateTime,
  humanizeText,
  minutesToDayLabel,
  punchTypeLabel,
  statusText,
  toneOfStatus,
} from '@/utils/employee-format';
import { fileIconOf, fileToneOf } from '@/utils/file-record';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import type { PersonalFileRecord } from '@/stores/shell';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppButton from '../../components/ui/AppButton.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppErrorState from '../../components/ui/AppErrorState.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppLoadingState from '../../components/ui/AppLoadingState.vue';
import AppTag from '../../components/ui/AppTag.vue';

const {
  shell,
  session,
  showToast,
} = useWorkbenchSectionContext();

const {
  leaveAccounts,
  attendanceExceptions,
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
  workbenchLoading,
  workbenchError,
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
  activeFile,
  activeWorkbench,
  workbenchHighlights,
  selectedLeaveAccount,
  activeWorkbenchTitle,
  selectedRequest,
  workflowDetailRows,
  attendanceSummary,
  teamAttendanceRows,
  submitLeaveRequest,
  refreshActiveWorkbench,
  loadWorkflowRequests,
  loadWorkflowRequestDetail,
  previewWorkflowAttachment,
  downloadWorkflowAttachment,
  withdrawWorkflowRequest,
  refreshAttendancePanel,
  punchAttendance,
  submitClockCorrectionRequest,
  loadAgents,
  submitAgentTask,
  confirmAgentTask,
  cancelAgentTask,
  workflowFilePreview,
  workflowFilePreviewLoading,
  workflowFilePreviewTitle,
  workflowFilePreviewType,
  closeWorkflowFilePreview,
  openWorkflowPreviewExternal,
} = useWorkbenchState({
  shell,
  session,
  showToast,
});

function backToList() {
  shell.setMobileView('list');
}

type AttachmentPickerTarget = 'leave' | 'clock';

const attachmentPickerOpen = ref(false);
const attachmentPickerTarget = ref<AttachmentPickerTarget>('leave');
const attachmentPickerFolderId = ref<EntityId | null>(null);
const attachmentPickerKeyword = ref('');
const attachmentPickerType = ref('all');
const attachmentPickerLoading = ref(false);
const attachmentPickerError = ref('');
const attachmentPickerFolders = ref<EmployeeFileFolderResponse[]>([]);
const attachmentPickerFiles = ref<PersonalFileRecord[]>([]);
const attachmentPickerBreadcrumbs = ref<EmployeeFileFolderResponse[]>([]);
let attachmentSearchTimer: number | undefined;
const agentAvatarUrls = ref<Record<string, string>>({});
const { resolveDownloadUrl } = usePersonalFileTransfer();

const currentAttachmentIds = computed(() => (
  attachmentPickerTarget.value === 'leave' ? leaveAttachmentIds.value : clockCorrectionAttachmentIds.value
));
const currentAttachmentIdSet = computed(() => new Set(currentAttachmentIds.value.map((item) => String(item))));
const leaveAvailableMinutes = computed(() => leaveAccounts.value.reduce((sum, account) => sum + Math.max(0, account.availableMinutes ?? 0), 0));
const leaveFrozenMinutes = computed(() => leaveAccounts.value.reduce((sum, account) => sum + Math.max(0, account.frozenMinutes ?? 0), 0));
const activeLeaveAccountCount = computed(() => leaveAccounts.value.filter((account) => account.status === 'ACTIVE').length);

function leaveAccountTone(account: LeaveAccountResponse) {
  if (account.status !== 'ACTIVE') return 'neutral';
  if ((account.frozenMinutes ?? 0) > 0) return 'warning';
  return 'success';
}

function leaveAccountMeta(account: LeaveAccountResponse) {
  if (account.lastChangeMinutes === undefined || account.lastChangeMinutes === null) return '暂无近期变动';
  const prefix = account.lastChangeMinutes > 0 ? '+' : '';
  return `${account.lastTxnType ?? '最近变动'} ${prefix}${minutesToDayLabel(Math.abs(account.lastChangeMinutes))}`;
}

function leaveAccountToneClass(index: number) {
  return ['blue', 'green', 'amber', 'gray'][index % 4];
}

function mapPickerFile(file: EmployeeFileResponse): PersonalFileRecord {
  const name = file.displayName || file.originalFileName || '未命名文件';
  return {
    id: `personal-file-${file.attachmentRefId}`,
    name,
    type: fileTypeLabelFromName(name, file.mimeType ?? ''),
    size: fileSizeLabel(file.fileSize),
    createdAt: formatDateTime(file.createdAt),
    status: statusText(file.scanStatus),
    attachmentRefId: file.attachmentRefId,
    folderId: file.folderId ?? null,
    mimeType: file.mimeType ?? undefined,
  };
}

async function refreshAgentAvatarUrls(items: AgentResponse[]) {
  const entries = await Promise.all(items.map(async (agent) => {
    if (!agent.avatarAttachmentRefId) return [String(agent.agentId), ''] as const;
    try {
      const ticket = await employeeApi.files.createDownloadTicket(agent.avatarAttachmentRefId, { downloadType: 'INLINE' });
      return [String(agent.agentId), resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo))] as const;
    } catch {
      return [String(agent.agentId), ''] as const;
    }
  }));
  agentAvatarUrls.value = Object.fromEntries(entries);
}

function attachmentIdsFor(target: AttachmentPickerTarget) {
  return target === 'leave' ? leaveAttachmentIds.value : clockCorrectionAttachmentIds.value;
}

function setAttachmentIdsFor(target: AttachmentPickerTarget, ids: Array<string | number>) {
  if (target === 'leave') {
    leaveAttachmentIds.value = ids;
  } else {
    clockCorrectionAttachmentIds.value = ids;
  }
}

function selectedAttachmentFiles(target: AttachmentPickerTarget) {
  const ids = attachmentIdsFor(target);
  return ids.map((id) => (
    activeFile.value.files.find((file) => String(file.attachmentRefId) === String(id))
    ?? {
      id: `selected-${id}`,
      name: String(id),
      type: '文件',
      size: '',
      createdAt: '',
      status: '',
      attachmentRefId: id,
    }
  ));
}

async function loadAttachmentPickerFiles() {
  attachmentPickerLoading.value = true;
  attachmentPickerError.value = '';
  try {
    const browser = await employeeApi.files.browser({
      folderId: attachmentPickerFolderId.value ?? undefined,
      keyword: attachmentPickerKeyword.value.trim() || undefined,
      fileType: attachmentPickerType.value,
    });
    attachmentPickerFolders.value = browser.folders;
    attachmentPickerFiles.value = browser.files.map(mapPickerFile);
    attachmentPickerBreadcrumbs.value = browser.breadcrumbs;
  } catch (error) {
    attachmentPickerError.value = error instanceof Error ? error.message : '附件加载失败';
  } finally {
    attachmentPickerLoading.value = false;
  }
}

function openAttachmentPicker(target: AttachmentPickerTarget) {
  attachmentPickerTarget.value = target;
  attachmentPickerOpen.value = true;
  void loadAttachmentPickerFiles();
}

function closeAttachmentPicker() {
  attachmentPickerOpen.value = false;
}

function openAttachmentFolder(folderId: EntityId | null) {
  attachmentPickerFolderId.value = folderId;
}

function toggleAttachment(file: PersonalFileRecord) {
  if (!file.attachmentRefId) return;
  const target = attachmentPickerTarget.value;
  const ids = attachmentIdsFor(target);
  const key = String(file.attachmentRefId);
  if (ids.some((item) => String(item) === key)) {
    setAttachmentIdsFor(target, ids.filter((item) => String(item) !== key));
  } else {
    setAttachmentIdsFor(target, [...ids, file.attachmentRefId]);
  }
}

function removeAttachment(target: AttachmentPickerTarget, attachmentRefId?: EntityId) {
  if (!attachmentRefId) return;
  setAttachmentIdsFor(target, attachmentIdsFor(target).filter((item) => String(item) !== String(attachmentRefId)));
}

watch([attachmentPickerFolderId, attachmentPickerType], () => {
  if (attachmentPickerOpen.value) void loadAttachmentPickerFiles();
});

watch(attachmentPickerKeyword, () => {
  if (!attachmentPickerOpen.value) return;
  if (attachmentSearchTimer) window.clearTimeout(attachmentSearchTimer);
  attachmentSearchTimer = window.setTimeout(() => {
    void loadAttachmentPickerFiles();
  }, 240);
});

watch(agents, (items) => {
  void refreshAgentAvatarUrls(items);
}, { immediate: true });
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
      <AppErrorState
        v-if="workbenchError"
        title="工作台数据加载失败"
        :description="workbenchError"
        action-label="重试"
        @retry="refreshActiveWorkbench"
      />
      <AppLoadingState v-else-if="workbenchLoading" />
      <template v-else>
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
              <div class="security-field form-full">
                <span>申请附件</span>
                <div class="attachment-picker-summary">
                  <button type="button" @click="openAttachmentPicker('leave')">
                    选择附件
                    <small>{{ leaveAttachmentIds.length }} 个</small>
                  </button>
                  <div v-if="selectedAttachmentFiles('leave').length" class="attachment-chip-list">
                    <span v-for="file in selectedAttachmentFiles('leave')" :key="file.id">
                      {{ file.name }}
                      <button type="button" aria-label="移除附件" @click="removeAttachment('leave', file.attachmentRefId)">
                        <X :size="13" />
                      </button>
                    </span>
                  </div>
                </div>
              </div>
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
            <div class="security-field">
              <span>补卡附件</span>
              <div class="attachment-picker-summary">
                <button type="button" @click="openAttachmentPicker('clock')">
                  选择附件
                  <small>{{ clockCorrectionAttachmentIds.length }} 个</small>
                </button>
                <div v-if="selectedAttachmentFiles('clock').length" class="attachment-chip-list">
                  <span v-for="file in selectedAttachmentFiles('clock')" :key="file.id">
                    {{ file.name }}
                    <button type="button" aria-label="移除附件" @click="removeAttachment('clock', file.attachmentRefId)">
                      <X :size="13" />
                    </button>
                  </span>
                </div>
              </div>
            </div>
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
      <section class="leave-balance-board">
        <article class="leave-balance-hero">
          <div>
            <small>我的假期</small>
            <h3>{{ minutesToDayLabel(leaveAvailableMinutes) }}</h3>
            <p>当前可用余额，提交请假时会自动冻结对应时长。</p>
          </div>
          <div class="leave-hero-metrics">
            <span>
              <strong>{{ leaveAccounts.length }}</strong>
              <small>假种</small>
            </span>
            <span>
              <strong>{{ activeLeaveAccountCount }}</strong>
              <small>可用</small>
            </span>
            <span>
              <strong>{{ minutesToDayLabel(leaveFrozenMinutes) }}</strong>
              <small>冻结</small>
            </span>
          </div>
        </article>

        <section class="leave-account-grid refined">
          <article
            v-for="(account, index) in leaveAccounts"
            :key="account.accountId"
            class="leave-balance-card"
            :class="`leave-tone-${leaveAccountToneClass(index)}`"
          >
            <div class="leave-card-head">
              <span class="leave-card-icon">
                <Check :size="18" />
              </span>
              <AppTag :tone="leaveAccountTone(account)">{{ statusText(account.status) }}</AppTag>
            </div>
            <div>
              <small>{{ account.leaveCode }}</small>
              <h3>{{ account.leaveName }}</h3>
            </div>
            <strong>{{ minutesToDayLabel(account.availableMinutes) }}</strong>
            <dl>
              <div>
                <dt>冻结</dt>
                <dd>{{ minutesToDayLabel(account.frozenMinutes) }}</dd>
              </div>
              <div>
                <dt>近期</dt>
                <dd>{{ leaveAccountMeta(account) }}</dd>
              </div>
            </dl>
          </article>
          <AppEmptyState v-if="!leaveAccounts.length" title="暂无假期账户" description="暂无假期账户" />
        </section>
      </section>
    </template>

    <template v-else-if="activeWorkbench.id === 'agent'">
      <section class="detail-grid">
        <article class="detail-card">
          <div class="title-line">
            <h3>可用 AI 助手</h3>
            <AppButton variant="secondary" size="sm" :disabled="agentsLoading" @click="loadAgents">
              {{ agentsLoading ? '加载中' : '刷新' }}
            </AppButton>
          </div>
          <AppLoadingState v-if="agentsLoading" />
          <AppEmptyState v-else-if="!agents.length" title="暂无 AI 助手" description="暂无 AI 助手" />
          <div v-else class="compact-table">
            <div v-for="agent in agents" :key="agent.agentId" class="agent-compact-row">
              <AppAvatar :label="humanizeText(agent.agentName)" :image="agentAvatarUrls[String(agent.agentId)]" kind="agent" tone="success" size="sm" />
              <div>
                <strong>{{ humanizeText(agent.agentName) }}</strong>
                <span>{{ agent.agentType }}</span>
                <small>{{ agent.agentCode }} · {{ statusText(agent.status) }}</small>
              </div>
            </div>
          </div>
        </article>

        <article class="detail-card">
          <h3>AI 任务</h3>
          <form class="workbench-form single-column" @submit.prevent="submitAgentTask">
            <label class="security-field">
              <span>AI 助手</span>
              <select v-model="agentTaskForm.agentId" class="ui-inline-input">
                <option v-for="agent in agents" :key="agent.agentId" :value="String(agent.agentId)">
                  {{ humanizeText(agent.agentName) }}
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

<AppDialog v-if="attachmentPickerOpen">
  <section class="attachment-picker-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>选择附件</h2>
        <p>{{ currentAttachmentIds.length }} 个已选</p>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="closeAttachmentPicker">
        <X :size="15" />
      </button>
    </header>

    <div class="file-browser-toolbar">
      <label class="file-search-field">
        <Search :size="16" />
        <input v-model="attachmentPickerKeyword" type="search" placeholder="搜索附件" />
      </label>
      <select v-model="attachmentPickerType" class="ui-inline-input file-type-select">
        <option value="all">全部类型</option>
        <option value="document">文档</option>
        <option value="image">图片</option>
        <option value="sheet">表格</option>
        <option value="archive">压缩包</option>
        <option value="media">音视频</option>
        <option value="code">代码</option>
      </select>
    </div>

    <div class="file-breadcrumb">
      <button type="button" :class="{ active: attachmentPickerFolderId === null }" @click="openAttachmentFolder(null)">
        <Home :size="15" />
        根目录
      </button>
      <button
        v-for="folder in attachmentPickerBreadcrumbs"
        :key="folder.folderId"
        type="button"
        :class="{ active: String(attachmentPickerFolderId) === String(folder.folderId) }"
        @click="openAttachmentFolder(folder.folderId)"
      >
        {{ folder.folderName }}
      </button>
    </div>

    <AppLoadingState v-if="attachmentPickerLoading" />
    <p v-else-if="attachmentPickerError" class="dialog-error">{{ attachmentPickerError }}</p>
    <AppEmptyState
      v-else-if="!attachmentPickerFolders.length && !attachmentPickerFiles.length"
      title="暂无附件"
      description="暂无附件"
    />
    <div v-else class="attachment-picker-list">
      <button
        v-for="folder in attachmentPickerFolders"
        :key="folder.folderId"
        type="button"
        class="attachment-picker-row"
        @click="openAttachmentFolder(folder.folderId)"
      >
        <span class="file-icon folder-icon">
          <Folder :size="18" />
        </span>
        <strong>{{ folder.folderName }}</strong>
        <small>文件夹</small>
      </button>
      <button
        v-for="file in attachmentPickerFiles"
        :key="file.id"
        type="button"
        class="attachment-picker-row"
        :class="{ selected: currentAttachmentIdSet.has(String(file.attachmentRefId)) }"
        @click="toggleAttachment(file)"
      >
        <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
          <component :is="fileIconOf(file)" :size="18" />
        </span>
        <strong>{{ file.name }}</strong>
        <small>{{ file.type }} · {{ file.size }}</small>
        <Check v-if="currentAttachmentIdSet.has(String(file.attachmentRefId))" :size="17" />
      </button>
    </div>

    <div class="form-actions">
      <AppButton variant="primary" @click="closeAttachmentPicker">确定</AppButton>
    </div>
  </section>
</AppDialog>

<AppDialog v-if="workflowFilePreview">
  <section class="preview-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>{{ workflowFilePreviewTitle }}</h2>
        <p>{{ workflowFilePreviewType || '文件' }}</p>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="closeWorkflowFilePreview">
        <X :size="15" />
      </button>
    </header>
    <div class="preview-body">
      <AppLoadingState v-if="workflowFilePreviewLoading" />
      <img v-else-if="workflowFilePreview.inline && (workflowFilePreviewType || '').startsWith('image/')" :src="workflowFilePreview.url" :alt="workflowFilePreviewTitle" />
      <iframe v-else-if="workflowFilePreview.inline" :src="workflowFilePreview.url" title="文件预览" />
      <div v-else class="preview-fallback">
        <component :is="fileIconOf(workflowFilePreview.file)" :size="42" />
        <strong>{{ workflowFilePreviewTitle }}</strong>
        <AppButton variant="primary" size="sm" @click="openWorkflowPreviewExternal">
          <ExternalLink :size="15" />
          打开文件
        </AppButton>
      </div>
    </div>
  </section>
</AppDialog>
</template>
