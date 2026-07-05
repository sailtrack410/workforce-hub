import { computed, getCurrentInstance, onBeforeUnmount, watch } from 'vue';
import { employeeApi } from '@/api';
import type {
  EntityId,
  LeaveAccountResponse,
  LeaveTypeResponse,
  WorkflowRequestAttachmentResponse,
  WorkflowRequestCreateResponse,
  WorkflowRequestDetailResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTaskActionResponse,
} from '@/api';
import type { PersonalFileRecord, useShellStore } from '@/stores/shell';
import type { useSessionStore } from '@/stores/session';
import { useWorkflowStore } from '@/stores/workflow';
import { useAgentTasks } from './useAgentTasks';
import { thisMonthRange, useAttendance } from './useAttendance';
import { usePersonalFileTransfer } from './usePersonalFileTransfer';
import {
  bizTypeLabel,
  displayField,
  formatDateTime,
  minutesToDayLabel,
  punchTypeLabel,
  statusText,
  toApiDateTime,
} from '@/utils/employee-format';
import { isFileTrashed } from '@/utils/file-record';
import { ref } from 'vue';
import { useFilePreview } from './useFilePreview';

type FieldRows = Array<[string, string]>;

interface UseWorkbenchStateOptions {
  shell: ReturnType<typeof useShellStore>;
  session: ReturnType<typeof useSessionStore>;
  showToast: (message: string) => void;
}

const HALF_DAY_MINUTES = 240;

function overlapMinutes(rangeStart: Date, rangeEnd: Date, segmentStart: Date, segmentEnd: Date) {
  const start = Math.max(rangeStart.getTime(), segmentStart.getTime());
  const end = Math.min(rangeEnd.getTime(), segmentEnd.getTime());
  if (end <= start) return 0;
  return Math.round((end - start) / 60000);
}

// 按半天（4h）为最小单位计算请假时长，只计算工作时间段（9-12, 13-18）
function calculateLeaveDurationMinutes(startAt: string, endAt: string) {
  if (!startAt || !endAt) return 0;
  const start = new Date(startAt);
  const end = new Date(endAt);
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || end <= start) return 0;
  let minutes = 0;
  const cursor = new Date(start);
  while (cursor <= end) {
    const day = new Date(cursor);
    day.setHours(0, 0, 0, 0);
    const morningStart = new Date(day);
    morningStart.setHours(9, 0, 0, 0);
    const morningEnd = new Date(day);
    morningEnd.setHours(12, 0, 0, 0);
    const afternoonStart = new Date(day);
    afternoonStart.setHours(13, 0, 0, 0);
    const afternoonEnd = new Date(day);
    afternoonEnd.setHours(18, 0, 0, 0);
    minutes += overlapMinutes(start, end, morningStart, morningEnd);
    minutes += overlapMinutes(start, end, afternoonStart, afternoonEnd);
    cursor.setDate(cursor.getDate() + 1);
    cursor.setHours(0, 0, 0, 0);
  }
  if (minutes <= 0) return 0;
  // 向上取整到半天
  return Math.ceil(minutes / HALF_DAY_MINUTES) * HALF_DAY_MINUTES;
}

function accountAsLeaveType(account: LeaveAccountResponse): LeaveTypeResponse {
  return {
    leaveTypeId: account.leaveTypeId,
    leaveCode: account.leaveCode,
    leaveName: account.leaveName,
    balanceMode: account.balanceMode,
    status: account.status,
  };
}

function resolveSelectableLeaveTypes(types: LeaveTypeResponse[], accounts: LeaveAccountResponse[]) {
  if (types.length) return types;
  const seen = new Set<string>();
  return accounts.filter((account) => {
    const key = String(account.leaveTypeId);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  }).map(accountAsLeaveType);
}

export function useWorkbenchState({
  shell,
  session,
  showToast,
}: UseWorkbenchStateOptions) {
  const workflow = useWorkflowStore();
  const { resolveDownloadUrl } = usePersonalFileTransfer();

  const activeWorkbench = computed(() => shell.activeWorkbench);
  const activeFile = computed(() => shell.activeFile);
  const activeWorkbenchTitle = computed(() => activeWorkbench.value.title || '工作台');
  const leaveAccounts = ref<LeaveAccountResponse[]>([]);
  const leaveTypes = ref<LeaveTypeResponse[]>([]);
  const workbenchLoading = ref(false);
  const workbenchError = ref('');
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
  const {
    filePreview: workflowFilePreview,
    filePreviewLoading: workflowFilePreviewLoading,
    filePreviewTitle: workflowFilePreviewTitle,
    filePreviewType: workflowFilePreviewType,
    openFilePreviewFromUrl: openWorkflowFilePreviewFromUrl,
    closeFilePreview: closeWorkflowFilePreview,
    openPreviewExternal: openWorkflowPreviewExternal,
  } = useFilePreview();
  let workflowDetailRequestSeq = 0;

  const selectedLeaveAccount = computed(() => (
    leaveAccounts.value.find((item) => String(item.leaveTypeId) === leaveForm.value.leaveTypeId)
  ));
  const selectableLeaveTypes = computed(() => (
    resolveSelectableLeaveTypes(leaveTypes.value, leaveAccounts.value)
  ));
  const selectedLeaveType = computed(() => (
    selectableLeaveTypes.value.find((item) => String(item.leaveTypeId) === leaveForm.value.leaveTypeId)
    ?? (selectedLeaveAccount.value ? accountAsLeaveType(selectedLeaveAccount.value) : null)
  ));
  const annualLeaveAccount = computed(() => (
    leaveAccounts.value.find((item) => item.leaveCode === 'ANNUAL' || item.leaveName.includes('年假')) ?? leaveAccounts.value[0]
  ));
  const canSubmitLeave = computed(() => {
    if (!leaveForm.value.leaveTypeId) return false;
    if (!selectedLeaveType.value) return false;
    if (selectedLeaveType.value.balanceMode === 'NO_DEDUCT') return true;
    return Boolean(selectedLeaveAccount.value);
  });
  const selectedRequest = computed(() => (
    workflowRequests.value.find((item) => item.requestId === selectedRequestId.value) ?? workflowRequests.value[0] ?? null
  ));
  const workbenchHighlights = computed(() => [
    {
      label: '待办',
      value: `${shell.pendingTodoCount}`,
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
  const computedLeaveDurationMinutes = computed(() => (
    calculateLeaveDurationMinutes(leaveForm.value.startAt, leaveForm.value.endAt)
  ));
  const workflowDetailRows = computed<FieldRows>(() => {
    const detail = workflowDetail.value;
    if (!detail) return [];
    const rows: FieldRows = [
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
  const {
    agentTaskForm,
    agentTaskSubmitting,
    agentTaskResult,
    agentTaskError,
    agents,
    agentsLoading,
    loadAgents,
    submitAgentTask,
    confirmAgentTask,
    cancelAgentTask,
  } = useAgentTasks({
    session,
    showToast,
  });

  const {
    attendanceDailyResults,
    teamAttendanceDailyResults,
    attendanceExceptions,
    attendanceLoading,
    attendanceError,
    punchLoading,
    punchResult,
    clockCorrectionForm,
    clockCorrectionAttachmentIds,
    clockCorrectionSubmitting,
    clockCorrectionResult,
    clockCorrectionError,
    attendanceSummary,
    teamAttendanceRows,
    refreshAttendancePanel,
    punchAttendance,
    submitClockCorrectionRequest,
  } = useAttendance({
    session,
    shell,
    workflow,
    loadWorkflowRequests,
    showToast,
  });

  // 用 Promise.allSettled 并行加载，任一接口失败不影响其他数据展示
  async function refreshWorkbenchData() {
    if (workbenchLoading.value) return;
    workbenchLoading.value = true;
    workbenchError.value = '';
    const dateRange = thisMonthRange();
    try {
      const [typesResult, accountsResult, exceptionsResult, dailyResultsResult] = await Promise.allSettled([
        employeeApi.leave.types(),
        employeeApi.leave.accounts(),
        employeeApi.attendance.exceptions(dateRange),
        employeeApi.attendance.dailyResults(dateRange),
      ]);
      const failedCount = [typesResult, accountsResult, exceptionsResult, dailyResultsResult]
        .filter((result) => result.status === 'rejected')
        .length;
      if (failedCount === 4) {
        workbenchError.value = '工作台数据加载失败';
      }
      const types = typesResult.status === 'fulfilled' ? typesResult.value : [];
      const accounts = accountsResult.status === 'fulfilled' ? accountsResult.value : [];
      const exceptions = exceptionsResult.status === 'fulfilled' ? exceptionsResult.value : [];
      const dailyResults = dailyResultsResult.status === 'fulfilled' ? dailyResultsResult.value : [];
      leaveTypes.value = types;
      leaveAccounts.value = accounts;
      attendanceExceptions.value = exceptions;
      attendanceDailyResults.value = dailyResults;
      // 初始化请假表单的假种选择
      const selectableTypes = resolveSelectableLeaveTypes(types, accounts);
      if (
        !leaveForm.value.leaveTypeId
        || !selectableTypes.some((item) => String(item.leaveTypeId) === leaveForm.value.leaveTypeId)
      ) {
        const initialLeaveTypeId = selectableTypes[0]?.leaveTypeId ?? accounts[0]?.leaveTypeId;
        if (initialLeaveTypeId !== undefined && initialLeaveTypeId !== null) {
          leaveForm.value.leaveTypeId = String(initialLeaveTypeId);
        }
      }
    } finally {
      workbenchLoading.value = false;
    }
  }

  async function submitLeaveRequest() {
    leaveError.value = '';
    leaveResult.value = null;
    const leaveTypeId = leaveForm.value.leaveTypeId.trim();
    const durationMinutes = computedLeaveDurationMinutes.value;
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

  watch(
    computedLeaveDurationMinutes,
    (minutes) => {
      leaveForm.value.durationMinutes = minutes > 0 ? String(minutes) : '';
    },
    { immediate: true },
  );

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

  // 用 requestSeq 防止快速切换时旧请求覆盖新数据
  async function loadWorkflowRequestDetail(requestId: EntityId) {
    const requestSeq = workflowDetailRequestSeq + 1;
    workflowDetailRequestSeq = requestSeq;
    selectedRequestId.value = requestId;
    workflowDetailLoading.value = true;
    workflowDetailError.value = '';
    try {
      const [detail, actions] = await Promise.all([
        employeeApi.workflow.requestDetail(requestId),
        employeeApi.workflow.requestActions(requestId).catch(() => []),
      ]);
      if (requestSeq !== workflowDetailRequestSeq) return;
      workflowDetail.value = detail;
      workflowActions.value = actions;
    } catch (error) {
      if (requestSeq !== workflowDetailRequestSeq) return;
      workflowDetailError.value = error instanceof Error ? error.message : '详情加载失败';
    } finally {
      if (requestSeq === workflowDetailRequestSeq) {
        workflowDetailLoading.value = false;
      }
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
    try {
      const ticket = await employeeApi.workflow.createAttachmentDownloadTicket(requestId, file.attachmentRefId, {
        downloadType: 'SINGLE',
        reason: '下载附件',
      });
      window.open(resolveDownloadUrl(ticket, employeeApi.workflow.downloadAttachmentUrl(ticket.ticketNo)), '_blank', 'noopener');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '下载失败');
    }
  }

  async function previewWorkflowAttachment(file: WorkflowRequestAttachmentResponse) {
    const requestId = selectedRequestId.value;
    if (!requestId) return;
    try {
      const ticket = await employeeApi.workflow.createAttachmentDownloadTicket(requestId, file.attachmentRefId, {
        downloadType: 'SINGLE',
        reason: '预览附件',
      });
      await openWorkflowFilePreviewFromUrl(file, resolveDownloadUrl(ticket, employeeApi.workflow.downloadAttachmentUrl(ticket.ticketNo)));
    } catch (error) {
      showToast(error instanceof Error ? error.message : '预览失败');
    }
  }

  function activeUploadableFiles() {
    return activeFile.value.files.filter((item: PersonalFileRecord) => item.attachmentRefId && !isFileTrashed(item));
  }

  async function refreshActiveWorkbench() {
    if (!session.isAuthenticated) return;
    const id = shell.active.workbench;
    if (id === 'oa') {
      await Promise.all([refreshWorkbenchData(), loadWorkflowRequests()]);
      return;
    }
    if (id === 'attendance') {
      await refreshAttendancePanel();
      return;
    }
    if (id === 'agent') {
      await loadAgents();
      return;
    }
    if (id === 'balance') {
      await refreshWorkbenchData();
    }
  }

  watch(
    [() => shell.active.workbench, () => session.isAuthenticated],
    () => {
      void refreshActiveWorkbench();
    },
    { immediate: true },
  );

  if (getCurrentInstance()) {
    onBeforeUnmount(() => {
      closeWorkflowFilePreview();
    });
  }

  return {
    activeWorkbench,
    activeFile,
    activeWorkbenchTitle,
    leaveAccounts,
    leaveTypes,
    workbenchLoading,
    workbenchError,
    leaveForm,
    leaveAttachmentIds,
    leaveSubmitting,
    leaveResult,
    leaveError,
    selectedLeaveAccount,
    selectableLeaveTypes,
    selectedLeaveType,
    canSubmitLeave,
    workflowRequests,
    workflowRequestsTotal,
    workflowRequestsLoading,
    workflowRequestsError,
    selectedRequestId,
    selectedRequest,
    workflowDetail,
    workflowActions,
    workflowDetailLoading,
    workflowDetailError,
    workflowDetailRows,
    withdrawComment,
    withdrawSubmitting,
    attendanceDailyResults,
    teamAttendanceDailyResults,
    teamAttendanceRows,
    attendanceExceptions,
    attendanceLoading,
    attendanceError,
    punchLoading,
    punchResult,
    clockCorrectionForm,
    clockCorrectionAttachmentIds,
    clockCorrectionSubmitting,
    clockCorrectionResult,
    clockCorrectionError,
    attendanceSummary,
    workbenchHighlights,
    agentTaskForm,
    agentTaskSubmitting,
    agentTaskResult,
    agentTaskError,
    agents,
    agentsLoading,
    workflowFilePreview,
    workflowFilePreviewLoading,
    workflowFilePreviewTitle,
    workflowFilePreviewType,
    activeUploadableFiles,
    refreshWorkbenchData,
    refreshActiveWorkbench,
    submitLeaveRequest,
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
    closeWorkflowFilePreview,
    openWorkflowPreviewExternal,
  };
}
