import { beforeEach, expect, it, vi } from 'vitest';
import ApprovalsView from './ApprovalsView.vue';
import WorkbenchView from './WorkbenchView.vue';
import { leaveApi, workflowApi } from '@/api';
const { route, router, draft } = vi.hoisted(() => ({ route: { query: {} as Record<string, string> }, router: { push: vi.fn(), replace: vi.fn() }, draft: { restore: vi.fn(), stop: vi.fn(), clear: vi.fn() } }));
vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onMounted: vi.fn(), onBeforeUnmount: vi.fn(), useSSRContext: () => ({ modules: new Set() }) }));
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }));
vi.mock('@/services/localData', () => ({ useLocalDraft: () => draft }));
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ user: { userId: '1' }, displayName: 'fixture' }) }));
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }));
vi.mock('@/api', () => ({ leaveApi: { accounts: vi.fn() }, workflowApi: { submitLeaveRequest: vi.fn(), submitGenericRequest: vi.fn(), requests: vi.fn(), todos: vi.fn(), ccList: vi.fn() }, fileApi: {}, attendanceApi: {}, personalTodoApi: {}, teamTaskApi: {}, notificationApi: {} }));
const setup = (view: unknown): any => (view as { setup: Function }).setup({}, { expose: vi.fn() });
beforeEach(() => { vi.clearAllMocks(); route.query = {}; vi.mocked(leaveApi.accounts).mockResolvedValue([{ accountId: '9007199254740993', leaveTypeId: '9007199254740995', leaveName: '年假' }] as never); vi.mocked(workflowApi.requests).mockResolvedValue({ list: [], total: 0 } as never); });
it('工作台请假、报销、加班、用印及全部应用入口均指向真实审批路由', () => {
  const view = setup(WorkbenchView);
  for (const [id, code] of [['leave', 'LEAVE'], ['expense', 'EXPENSE'], ['overtime', 'OVERTIME'], ['seal', 'SEAL']]) {
    view.onQuick(id); expect(router.push).toHaveBeenLastCalledWith(`/approvals?create=${code}&from=workbench`);
  }
  view.onQuick('requests'); expect(router.push).toHaveBeenLastCalledWith('/approvals?tab=mine&from=workbench');
});
it('路由预选审批类型，请假提交使用假期类型 ID 而非账户 ID', async () => {
  route.query = { create: 'LEAVE', from: 'attendance' }; const view = setup(ApprovalsView);
  await view.applyRouteIntent(); expect(view.createOpen.value).toBe(true); expect(view.createStep.value).toBe('form');
  expect(view.returnPath.value).toBe('/attendance'); expect(view.leaveTypeId.value).toBe('9007199254740995');
  view.leaveStart.value = '2026-11-26T09:00'; view.leaveEnd.value = '2026-11-26T18:00'; view.leaveDuration.value = 480; view.leaveReason.value = '事由';
  await view.submitCreate(); expect(workflowApi.submitLeaveRequest).toHaveBeenCalledWith(expect.objectContaining({ leaveTypeId: '9007199254740995' })); expect(view.tab.value).toBe('mine');
});
it('非法类型不打开表单，已有草稿内容在切换入口时保留', async () => {
  const view = setup(ApprovalsView); view.genericSummary.value = '未提交事由'; route.query = { create: 'unknown' };
  await view.applyRouteIntent(); expect(view.createOpen.value).toBe(false);
  route.query = { create: 'EXPENSE' }; await view.applyRouteIntent(); expect(view.selectedProcess.value).toBe('EXPENSE'); expect(view.genericSummary.value).toBe('未提交事由');
});
it('旧草稿误存账户 ID 时恢复为有效假期类型，完整雪花 ID 不经过 Number 转换', async () => {
  const view = setup(ApprovalsView); view.leaveTypeId.value = '9007199254740993';
  await view.openCreate('LEAVE'); expect(view.leaveTypeId.value).toBe('9007199254740995');
});
