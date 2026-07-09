import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick, reactive, type Ref } from 'vue';
import { employeeApi } from '@/api';
import { useWorkbenchState } from './useWorkbenchState';
import type {
  AttendanceDailyResultResponse,
  AttendanceExceptionResponse,
  LeaveAccountResponse,
  WorkflowRequestDetailResponse,
  WorkflowTaskActionResponse,
} from '@/api';
import type { useSessionStore } from '@/stores/session';
import type { useShellStore } from '@/stores/shell';

type Deferred<T> = {
  promise: Promise<T>;
  resolve: (value: T) => void;
};

function createDeferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((next) => {
    resolve = next;
  });
  return { promise, resolve };
}

function createShell() {
  return reactive({
    active: { workbench: 'oa' },
    activeWorkbench: { id: 'oa', title: 'OA 申请', subtitle: '' },
    activeFile: { files: [] },
    pendingTodoCount: 0,
    mergeBackendTodos: vi.fn(),
  }) as unknown as ReturnType<typeof useShellStore>;
}

function createSession() {
  return reactive({
    isAuthenticated: false,
    user: { userId: '1001' },
    subordinates: [],
  }) as unknown as ReturnType<typeof useSessionStore>;
}

const leaveAccount: LeaveAccountResponse = {
  accountId: 'A-1',
  userId: '1001',
  leaveTypeId: 'L-1',
  leaveCode: 'ANNUAL',
  leaveName: '年假',
  balanceMode: 'MINUTES',
  availableMinutes: 480,
  frozenMinutes: 0,
  status: 'ACTIVE',
};

const sickLeaveAccount: LeaveAccountResponse = {
  accountId: 'A-2',
  userId: '1001',
  leaveTypeId: 'L-2',
  leaveCode: 'SICK',
  leaveName: '病假',
  balanceMode: 'NO_DEDUCT',
  availableMinutes: 0,
  frozenMinutes: 0,
  status: 'ACTIVE',
};

function workflowDetail(requestId: string): WorkflowRequestDetailResponse {
  return {
    requestId,
    requestNo: `REQ-${requestId}`,
    bizType: 'LEAVE',
    bizId: `BIZ-${requestId}`,
    applicantUserId: '1001',
    title: `申请 ${requestId}`,
    status: 'IN_APPROVAL',
    currentStepName: '直属上级审批',
    submittedAt: '2026-05-15T09:30:00',
    finishedAt: null,
    leave: null,
    clockCorrection: null,
    attachments: [],
  };
}

describe('useWorkbenchState', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.restoreAllMocks();
  });

  it('reports a workbench loading error when every summary API fails', async () => {
    vi.spyOn(employeeApi.leave, 'accounts').mockRejectedValue(new Error('leave down'));
    vi.spyOn(employeeApi.attendance, 'exceptions').mockRejectedValue(new Error('attendance down'));
    vi.spyOn(employeeApi.attendance, 'dailyResults').mockRejectedValue(new Error('daily down'));
    const state = useWorkbenchState({
      shell: createShell(),
      session: createSession(),
      showToast: vi.fn(),
    }) as ReturnType<typeof useWorkbenchState> & { workbenchError?: Ref<string> };

    await state.refreshWorkbenchData();

    expect(state.leaveAccounts.value).toEqual([]);
    expect(state.attendanceExceptions.value).toEqual([]);
    expect(state.attendanceDailyResults.value).toEqual([]);
    expect(state.workbenchError?.value).toBe('仪表盘数据加载失败');
  });

  it('keeps the latest workflow detail when an older request resolves later', async () => {
    const detailA = createDeferred<WorkflowRequestDetailResponse>();
    const detailB = createDeferred<WorkflowRequestDetailResponse>();
    vi.spyOn(employeeApi.workflow, 'requestDetail')
      .mockImplementationOnce(() => detailA.promise)
      .mockImplementationOnce(() => detailB.promise);
    vi.spyOn(employeeApi.workflow, 'requestActions').mockResolvedValue([] as WorkflowTaskActionResponse[]);
    const state = useWorkbenchState({
      shell: createShell(),
      session: createSession(),
      showToast: vi.fn(),
    });

    const first = state.loadWorkflowRequestDetail('A');
    const second = state.loadWorkflowRequestDetail('B');
    detailB.resolve(workflowDetail('B'));
    await second;
    await nextTick();
    detailA.resolve(workflowDetail('A'));
    await first;
    await nextTick();

    expect(state.selectedRequestId.value).toBe('B');
    expect(state.workflowDetail.value?.requestId).toBe('B');
    expect(state.workflowDetailLoading.value).toBe(false);
  });

  it('loads workflow requests for oa workbench', async () => {
    vi.spyOn(employeeApi.workflow, 'requests').mockResolvedValue({
      pageNo: 1,
      pageSize: 20,
      total: 1,
      list: [{
        requestId: 'REQ-1',
        requestNo: 'REQ-1',
        bizType: 'LEAVE',
        bizId: 1,
        title: '申请 REQ-1',
        status: 'IN_APPROVAL',
        currentStepKey: 'manager_approval',
        currentStepName: '直属上级审批',
        submittedAt: '2026-05-15T09:30:00',
        finishedAt: null,
      }],
    });
    vi.spyOn(employeeApi.workflow, 'requestDetail').mockResolvedValue(workflowDetail('REQ-1'));
    vi.spyOn(employeeApi.workflow, 'requestActions').mockResolvedValue([] as WorkflowTaskActionResponse[]);
    const state = useWorkbenchState({
      shell: createShell(),
      session: { ...createSession(), isAuthenticated: true },
      showToast: vi.fn(),
    });

    await state.refreshActiveWorkbench();

    expect(state.workflowRequests.value).toHaveLength(1);
    expect(state.workflowRequests.value[0]?.requestId).toBe('REQ-1');
  });

  it('computes leave minutes from datetime range and rounds up to half day', async () => {
    vi.spyOn(employeeApi.leave, 'types').mockResolvedValue([
      { leaveTypeId: 'L-1', leaveCode: 'ANNUAL', leaveName: '年假', balanceMode: 'DEDUCT', status: 'ACTIVE' },
      { leaveTypeId: 'L-2', leaveCode: 'SICK', leaveName: '病假', balanceMode: 'NO_DEDUCT', status: 'ACTIVE' },
    ]);
    vi.spyOn(employeeApi.leave, 'accounts').mockResolvedValue([leaveAccount, sickLeaveAccount]);
    vi.spyOn(employeeApi.attendance, 'exceptions').mockResolvedValue([]);
    vi.spyOn(employeeApi.attendance, 'dailyResults').mockResolvedValue([]);
    const state = useWorkbenchState({
      shell: createShell(),
      session: createSession(),
      showToast: vi.fn(),
    });

    await state.refreshWorkbenchData();
    state.leaveForm.value.startAt = '2026-05-07T17:00';
    state.leaveForm.value.endAt = '2026-05-08T10:00';
    await nextTick();

    expect(state.leaveForm.value.durationMinutes).toBe('240');
  });

  it('keeps no-deduct leave types selectable without annual leave default bias', async () => {
    vi.spyOn(employeeApi.leave, 'types').mockResolvedValue([
      { leaveTypeId: 'L-2', leaveCode: 'SICK', leaveName: '病假', balanceMode: 'NO_DEDUCT', status: 'ACTIVE' },
      { leaveTypeId: 'L-1', leaveCode: 'ANNUAL', leaveName: '年假', balanceMode: 'DEDUCT', status: 'ACTIVE' },
    ]);
    vi.spyOn(employeeApi.leave, 'accounts').mockResolvedValue([sickLeaveAccount, leaveAccount]);
    vi.spyOn(employeeApi.attendance, 'exceptions').mockResolvedValue([]);
    vi.spyOn(employeeApi.attendance, 'dailyResults').mockResolvedValue([]);
    const state = useWorkbenchState({
      shell: createShell(),
      session: createSession(),
      showToast: vi.fn(),
    });

    await state.refreshWorkbenchData();

    expect(state.leaveAccounts.value.map((item) => item.leaveName)).toEqual(['病假', '年假']);
    expect(state.leaveForm.value.leaveTypeId).toBe('L-2');
  });

  it('falls back to leave accounts when leave type list is unavailable', async () => {
    vi.spyOn(employeeApi.leave, 'types').mockResolvedValue([]);
    vi.spyOn(employeeApi.leave, 'accounts').mockResolvedValue([sickLeaveAccount, leaveAccount]);
    vi.spyOn(employeeApi.attendance, 'exceptions').mockResolvedValue([]);
    vi.spyOn(employeeApi.attendance, 'dailyResults').mockResolvedValue([]);
    const state = useWorkbenchState({
      shell: createShell(),
      session: createSession(),
      showToast: vi.fn(),
    });

    await state.refreshWorkbenchData();

    expect(state.selectableLeaveTypes.value.map((item) => item.leaveName)).toEqual(['病假', '年假']);
    expect(state.leaveForm.value.leaveTypeId).toBe('L-2');
    expect(state.canSubmitLeave.value).toBe(true);
  });

  it('builds attendance risk data without loading team results for regular employees', async () => {
    const teamDailyResults = vi.spyOn(employeeApi.attendance, 'teamDailyResults').mockResolvedValue([]);
    vi.spyOn(employeeApi.attendance, 'dailyResults').mockResolvedValue([
      {
        dailyResultId: 'D-1',
        userId: '1001',
        workDate: '2026-05-06',
        resultStatus: 'NORMAL',
        lateMinutes: 0,
        earlyLeaveMinutes: 0,
        absenceMinutes: 0,
        exceptionFlag: false,
      },
      {
        dailyResultId: 'D-2',
        userId: '1001',
        workDate: '2026-05-07',
        resultStatus: 'ABNORMAL',
        lateMinutes: 0,
        earlyLeaveMinutes: 0,
        absenceMinutes: 480,
        exceptionFlag: true,
      },
    ] as AttendanceDailyResultResponse[]);
    vi.spyOn(employeeApi.attendance, 'exceptions').mockResolvedValue([
      {
        exceptionId: 'E-1',
        dailyResultId: 'D-2',
        userId: '1001',
        workDate: '2026-05-07',
        exceptionType: 'MISS_PUNCH',
        exceptionDesc: '缺卡',
        status: 'OPEN',
      },
    ] as AttendanceExceptionResponse[]);
    const shell = createShell();
    shell.active.workbench = 'attendance';
    const state = useWorkbenchState({
      shell,
      session: { ...createSession(), isAuthenticated: true },
      showToast: vi.fn(),
    });

    await state.refreshActiveWorkbench();

    expect(teamDailyResults).not.toHaveBeenCalled();
    expect(state.attendanceSummary.value).toEqual([
      ['出勤天数', '1 天'],
      ['异常次数', '1 次'],
      ['缺卡次数', '1 次'],
      ['异常天数', '1 天'],
    ]);
    expect(state.teamAttendanceRows.value).toEqual([]);
  });

  it('maps team attendance rows to subordinate names', async () => {
    vi.spyOn(employeeApi.attendance, 'dailyResults').mockResolvedValue([]);
    vi.spyOn(employeeApi.attendance, 'exceptions').mockResolvedValue([]);
    vi.spyOn(employeeApi.attendance, 'teamDailyResults')
      .mockResolvedValueOnce([
        {
          dailyResultId: 'TD-1',
          userId: '2001',
          workDate: '2026-05-07',
          resultStatus: 'ABNORMAL',
          lateMinutes: 12,
          earlyLeaveMinutes: 0,
          absenceMinutes: 0,
          exceptionFlag: true,
        },
      ] as AttendanceDailyResultResponse[])
      .mockResolvedValueOnce([]);
    const shell = createShell();
    shell.active.workbench = 'attendance';
    const session = reactive({
      isAuthenticated: false,
      user: { userId: '1001' },
      subordinates: [
        { userId: '2001', employeeNo: 'E2001', realName: '李四' },
        { userId: '2002', employeeNo: 'E2002', realName: '王五' },
      ],
    }) as unknown as ReturnType<typeof useSessionStore>;
    const state = useWorkbenchState({
      shell,
      session,
      showToast: vi.fn(),
    });

    await state.refreshAttendancePanel();
    await nextTick();

    expect(employeeApi.attendance.teamDailyResults).toHaveBeenCalledTimes(2);
    expect(state.teamAttendanceRows.value[0]?.userName).toBe('李四');
  });

  it('validates clock correction date against expected punch time', async () => {
    const submitClock = vi.spyOn(employeeApi.workflow, 'submitClockCorrectionRequest').mockResolvedValue({
      requestId: 'REQ-CLOCK',
      requestNo: 'REQ-CLOCK',
      status: 'IN_APPROVAL',
    });
    const state = useWorkbenchState({
      shell: createShell(),
      session: createSession(),
      showToast: vi.fn(),
    });

    state.clockCorrectionForm.value = {
      targetDate: '2026-05-07',
      missingPunchType: 'OFF_DUTY',
      expectedPunchAt: '2026-05-08T18:00',
      reason: '忘记打卡',
    };
    await state.submitClockCorrectionRequest();

    expect(submitClock).not.toHaveBeenCalled();
    expect(state.clockCorrectionError.value).toBe('补卡时间需与考勤日期一致');
  });
});
