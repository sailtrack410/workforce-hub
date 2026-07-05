import { computed, ref } from 'vue';
import { employeeApi } from '@/api';
import type {
  AttendanceDailyResultResponse,
  AttendanceExceptionResponse,
  PunchRecordResponse,
  WorkflowRequestCreateResponse,
} from '@/api';
import type { useSessionStore } from '@/stores/session';
import type { useShellStore } from '@/stores/shell';
import type { useWorkflowStore } from '@/stores/workflow';
import { punchTypeLabel, toApiDateTime } from '@/utils/employee-format';

interface UseAttendanceOptions {
  session: ReturnType<typeof useSessionStore>;
  shell: ReturnType<typeof useShellStore>;
  workflow: ReturnType<typeof useWorkflowStore>;
  loadWorkflowRequests: () => Promise<void>;
  showToast: (message: string) => void;
}

// 获取本月日期范围，用于考勤查询
export function thisMonthRange() {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  return {
    startDate: start.toISOString().slice(0, 10),
    endDate: end.toISOString().slice(0, 10),
  };
}

export function useAttendance({
  session,
  shell,
  workflow,
  loadWorkflowRequests,
  showToast,
}: UseAttendanceOptions) {
  const attendanceDailyResults = ref<AttendanceDailyResultResponse[]>([]);
  const teamAttendanceDailyResults = ref<AttendanceDailyResultResponse[]>([]);
  const attendanceExceptions = ref<AttendanceExceptionResponse[]>([]);
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

  const attendanceSummary = computed<Array<[string, string]>>(() => [
    ['日结果', `${attendanceDailyResults.value.length} 条`],
    ['异常', `${attendanceExceptions.value.length} 条`],
    ['团队考勤', `${teamAttendanceDailyResults.value.length} 条`],
    ['本月范围', `${thisMonthRange().startDate} 至 ${thisMonthRange().endDate}`],
  ]);
  const teamAttendanceRows = computed(() => teamAttendanceDailyResults.value.slice(0, 12));

  // 并行加载本月考勤日结果、异常、团队考勤
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
      // 团队考勤：逐个下属查询，任一失败不影响其他
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

  // 提交补卡申请后刷新待办和申请列表
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

  return {
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
  };
}
