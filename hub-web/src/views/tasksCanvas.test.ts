import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import TasksView from './TasksView.vue';
import { teamTaskApi } from '@/api';

vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onMounted: vi.fn(), useSSRContext: () => ({ modules: new Set() }) }));
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ user: { userId: '9007199254740993' } }) }));
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }));
vi.mock('@/api', () => ({ teamTaskApi: { list: vi.fn(), assignees: vi.fn(), detail: vi.fn(), create: vi.fn(), update: vi.fn(), updateStatus: vi.fn(), remove: vi.fn() } }));

const me = '9007199254740993';
const task = (extra = {}) => ({ taskId: '1', title: '任务', assigneeId: me, creatorId: 'other', status: 'TODO', startDate: '2026-11-23', endDate: '2026-11-26', canEdit: false, canDelete: false, canStart: true, canComplete: true, canReopen: false, ...extra });
const setup = (): any => (TasksView as unknown as { setup: Function }).setup({}, { expose: vi.fn() });
beforeEach(() => {
  vi.resetAllMocks();
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-11-23T12:00:00'));
  vi.mocked(teamTaskApi.list).mockResolvedValue([]);
});
afterEach(() => vi.useRealTimers());

describe('团队任务列表画布交互', () => {
  it('默认全部列表；今日包含跨日未完成任务，不包含已完成和空日期', () => {
    const v = setup();
    expect(v.filter.value).toBe('all'); expect(v.viewMode.value).toBe('list');
    v.tasks.value = [task(), task({ taskId: '2', status: 'DONE' }), task({ taskId: '3', startDate: null }), task({ taskId: '4', startDate: '2026-11-24' })];
    v.selectScope('today'); expect(v.visibleTasks.value.map((t: any) => t.taskId)).toEqual(['1']);
  });
  it('负责人和派发人使用完整 ID 区分，清单来自真实 groupId', () => {
    const v = setup();
    v.tasks.value = [task({ groupId: '9007199254740993' }), task({ taskId: '2', assigneeId: '9007199254740992', creatorId: me, groupId: '9007199254740992' })];
    v.selectScope('mine'); expect(v.visibleTasks.value.map((t: any) => t.taskId)).toEqual(['1']);
    v.selectScope('created'); expect(v.visibleTasks.value.map((t: any) => t.taskId)).toEqual(['2']);
    expect(v.taskGroups.value).toEqual(['9007199254740992', '9007199254740993']);
    v.selectScope('all', '9007199254740993'); expect(v.visibleTasks.value.map((t: any) => t.taskId)).toEqual(['1']);
  });
  it('按截止时间排序，空日期在末尾；搜索、负责人和状态组合过滤', () => {
    const v = setup();
    v.tasks.value = [task({ taskId: '1', endDate: null }), task({ taskId: '2', title: 'API 回归', endDate: '2026-11-25' }), task({ taskId: '3', title: 'API 发布', endDate: '2026-11-24', status: 'DONE' })];
    expect(v.visibleTasks.value.map((t: any) => t.taskId)).toEqual(['3', '2', '1']);
    v.keyword.value = 'api'; v.assigneeFilter.value = me; v.statusFilter.value = 'TODO';
    expect(v.visibleTasks.value.map((t: any) => t.taskId)).toEqual(['2']);
  });
  it('分页只选择本页有能力的任务；筛选变化清除选择并回到第一页', async () => {
    const v = setup();
    v.tasks.value = Array.from({ length: 25 }, (_, i) => task({ taskId: String(i), canComplete: i !== 0 }));
    await nextTick(); expect(v.pageCount.value).toBe(3); v.togglePage();
    expect(v.selectedTasks.value).toHaveLength(11); expect(v.allSelected.value).toBe(true);
    v.page.value = 3; expect(v.pageTasks.value).toHaveLength(1);
    v.keyword.value = '不存在'; await nextTick();
    expect(v.page.value).toBe(1); expect(v.selectedIds.value).toEqual([]); expect(v.pageCount.value).toBe(1);
  });
  it('分组按状态或负责人组织当前页，不能丢失任务', () => {
    const v = setup(); v.tasks.value = [task(), task({ taskId: '2', status: 'DONE' })];
    v.groupBy.value = 'status'; expect(v.groupedTasks.value.map((g: any) => g.label)).toEqual(['待开始', '已完成']);
    v.groupBy.value = 'assignee'; expect(v.groupedTasks.value[0].items).toHaveLength(2);
  });
  it('权限混合时禁止批量完成和删除；不可操作的任务不能勾选', async () => {
    const v = setup();
    v.tasks.value = [task(), task({ taskId: '2', canComplete: false, canDelete: true }), task({ taskId: '3', canComplete: false })];
    v.toggleTask(v.tasks.value[2]); expect(v.selectedIds.value).toEqual([]);
    v.togglePage(); expect(v.canBatchComplete.value).toBe(false); expect(v.canBatchDelete.value).toBe(false);
    await v.runBatch('complete'); v.confirmingBatchDelete.value = true; await v.runBatch('delete');
    expect(teamTaskApi.updateStatus).not.toHaveBeenCalled(); expect(teamTaskApi.remove).not.toHaveBeenCalled();
  });
  it('批量部分失败后保留失败项和原筛选，并刷新接口数据', async () => {
    const v = setup(); const rows = [task(), task({ taskId: '2' })];
    v.tasks.value = rows; await nextTick(); v.togglePage();
    vi.mocked(teamTaskApi.updateStatus).mockResolvedValueOnce(task({ status: 'DONE' }) as any).mockRejectedValueOnce(new Error('权限已变更'));
    vi.mocked(teamTaskApi.list).mockResolvedValue([task({ status: 'DONE', canComplete: false }), rows[1]] as any);
    await v.runBatch('complete');
    expect(teamTaskApi.updateStatus).toHaveBeenCalledTimes(2); expect(v.selectedIds.value).toEqual(['2']);
    expect(v.batchBusy.value).toBe(false); expect(v.filter.value).toBe('all'); expect(teamTaskApi.list).toHaveBeenCalledOnce();
  });
  it('批量删除必须确认，提交中禁止重复操作', async () => {
    const v = setup(); v.tasks.value = [task({ canDelete: true })]; await nextTick(); v.togglePage();
    await v.runBatch('delete'); expect(teamTaskApi.remove).not.toHaveBeenCalled();
    let release!: () => void;
    vi.mocked(teamTaskApi.remove).mockImplementation(() => new Promise(resolve => { release = resolve; }));
    v.confirmingBatchDelete.value = true; const pending = v.runBatch('delete');
    await v.runBatch('delete'); v.selectScope('mine');
    expect(teamTaskApi.remove).toHaveBeenCalledOnce(); expect(v.filter.value).toBe('all');
    release(); await pending; expect(v.confirmingBatchDelete.value).toBe(false);
  });
  it('未保存的表单关闭时先确认；原始表单直接关闭', () => {
    const v = setup(); vi.mocked(teamTaskApi.assignees).mockResolvedValue([]);
    v.openCreate(); v.closeCreate(); expect(v.showCreate.value).toBe(false);
    v.openCreate(); v.form.value.title = '未保存'; v.closeCreate();
    expect(v.showCreate.value).toBe(true); expect(v.discardChanges.value).toBe(true);
  });
});
