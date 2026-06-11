import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick, reactive, type Ref } from 'vue';
import { employeeApi } from '@/api';
import { useWorkbenchState } from './useWorkbenchState';
import type {
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
    active: { workbench: 'leave' },
    activeWorkbench: { id: 'leave', title: '请假', subtitle: '' },
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
    expect(state.workbenchError?.value).toBe('工作台数据加载失败');
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
});
