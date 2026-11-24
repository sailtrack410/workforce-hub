import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TasksView from './TasksView.vue';
import TodosView from './TodosView.vue';
import { teamTaskApi, personalTodoApi } from '@/api';
import { ApiError } from '@/api/http';

vi.mock('vue', async importOriginal => ({ ...await importOriginal<typeof import('vue')>(), onMounted: vi.fn(), useSSRContext: () => ({ modules: new Set() }) }));
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ user: { userId: '9007199254740993' } }) }));
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ message: '', show: vi.fn() }) }));
vi.mock('@/api', () => ({
  teamTaskApi: { list: vi.fn(), assignees: vi.fn(), detail: vi.fn(), create: vi.fn(), update: vi.fn(), updateStatus: vi.fn(), remove: vi.fn() },
  personalTodoApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), complete: vi.fn(), reopen: vi.fn(), remove: vi.fn() },
  orgApi: { colleagues: vi.fn() },
}));

// Exercise the actual script setup so permissions, date boundaries and request bodies stay tied to the views.
function setup(component: unknown): any {
  return (component as { setup: Function }).setup({}, { expose: vi.fn() });
}
const me = '9007199254740993';
const task = (extra = {}) => ({ taskId: '9007199254740995', title: '任务', assigneeId: me, creatorId: '9007199254740994', status: 'TODO', startDate: '2026-11-16', endDate: '2026-11-16', canEdit: false, canDelete: false, canStart: true, canComplete: true, canReopen: false, ...extra });
const todo = (extra = {}) => ({ todoId: '9007199254740996', title: '待办', category: 'TASK', status: 'PENDING', dueAt: null, ...extra });
afterEach(() => vi.useRealTimers());
beforeEach(() => {
  vi.resetAllMocks();
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-11-19T12:00:00'));
  vi.mocked(teamTaskApi.list).mockResolvedValue([]);
  vi.mocked(teamTaskApi.assignees).mockResolvedValue([]);
  vi.mocked(personalTodoApi.list).mockResolvedValue([]);
});

describe('团队任务', () => {
  it('默认展示全部任务，按角色和状态组合筛选', () => {
    const v = setup(TasksView);
    expect(v.filter.value).toBe('all'); v.filter.value = 'mine';
    v.tasks.value = [task(), task({ taskId: '2', assigneeId: 'other', creatorId: me, status: 'DONE' })];
    expect(v.visibleTasks.value.map((t: any) => t.taskId)).toEqual(['9007199254740995']);
    v.filter.value = 'created'; expect(v.visibleTasks.value.map((t: any) => t.taskId)).toEqual(['2']);
    v.filter.value = 'all'; v.statusFilter.value = 'DONE'; expect(v.visibleTasks.value.map((t: any) => t.taskId)).toEqual(['2']);
  });
  it.each([[-7, -1, 0], [7, 9, 0], [-2, 0, 1], [0, 6, 1], [-3, 9, 1], [2, 2, 1]])('周边界 %s 到 %s', (start, end, bars) => {
    const v = setup(TasksView);
    const date = (offset: number) => { const d = new Date('2026-11-16T00:00:00'); d.setDate(d.getDate() + offset); return v.toIso(d); };
    v.tasks.value = [task({ startDate: date(start), endDate: date(end) })];
    expect(v.lanes.value.reduce((n: number, r: any) => n + r.bars.length, 0)).toBe(bars);
  });
  it('历史空日期不崩溃，相邻任务共享行，重叠任务换行', () => {
    const v = setup(TasksView);
    v.tasks.value = [task({ startDate: null, endDate: null }), task(), task({ taskId: '2', startDate: '2026-11-17', endDate: '2026-11-17' })];
    expect(v.lanes.value[0].maxLane).toBe(1);
    v.tasks.value.push(task({ taskId: '3' })); expect(v.lanes.value[0].maxLane).toBe(2);
  });
  it('使用独立派发候选接口', async () => {
    const v = setup(TasksView); await v.loadCandidates();
    expect(teamTaskApi.assignees).toHaveBeenCalledOnce();
  });
  it.each(['TODO', 'IN_PROGRESS', 'DONE'])('未授权时禁止状态请求 %s', async status => {
    const v = setup(TasksView); v.detail.value = task({ canStart: false, canComplete: false, canReopen: false });
    await v.changeStatus(status); expect(teamTaskApi.updateStatus).not.toHaveBeenCalled();
  });
  it.each([['IN_PROGRESS', 'canStart'], ['DONE', 'canComplete'], ['TODO', 'canReopen']])('能力 %s 发出正确请求', async (status, capability) => {
    const v = setup(TasksView); v.detail.value = task({ [capability]: true });
    await v.changeStatus(status); expect(teamTaskApi.updateStatus).toHaveBeenCalledWith('9007199254740995', status);
  });
  it('无删除能力或未确认时禁止删除', async () => {
    const v = setup(TasksView); v.detail.value = task(); v.confirmingDelete.value = true;
    await v.deleteTask(); expect(teamTaskApi.remove).not.toHaveBeenCalled();
    v.detail.value = task({ canDelete: true }); v.confirmingDelete.value = false;
    await v.deleteTask(); expect(teamTaskApi.remove).not.toHaveBeenCalled();
    v.confirmingDelete.value = true; await v.deleteTask(); expect(teamTaskApi.remove).toHaveBeenCalledWith('9007199254740995');
  });
  it('反向日期阻止派发', async () => {
    const v = setup(TasksView); v.form.value = { title: '任务', assigneeId: 'other', startDate: '2026-11-20', endDate: '2026-11-19', description: '' };
    await v.submitCreate(); expect(teamTaskApi.create).not.toHaveBeenCalled();
  });
  it('编辑派发使用 PUT 并保留分组', async () => {
    const v = setup(TasksView); v.editing.value = task({ canEdit: true, groupId: '9007199254740997' });
    v.form.value = { title: ' 修改 ', assigneeId: '9007199254740998', startDate: '2026-11-19', endDate: '2026-11-20', description: ' 说明 ' };
    await v.submitCreate(); expect(teamTaskApi.update).toHaveBeenCalledWith('9007199254740995', { title: '修改', assigneeId: '9007199254740998', startDate: '2026-11-19', endDate: '2026-11-20', description: '说明', groupId: '9007199254740997' });
    expect(teamTaskApi.create).not.toHaveBeenCalled();
  });
  it('冲突后关闭旧详情并刷新', async () => {
    const v = setup(TasksView); v.detail.value = task();
    vi.mocked(teamTaskApi.updateStatus).mockRejectedValue(new ApiError('状态已变化', { status: 409 }));
    await v.changeStatus('DONE'); expect(v.detail.value).toBeNull(); expect(teamTaskApi.list).toHaveBeenCalled(); expect(v.busy.value).toBe(false);
  });
  it('失败的候选请求不可提交，重新加载后恢复', async () => {
    const v = setup(TasksView); vi.mocked(teamTaskApi.assignees).mockRejectedValueOnce(new Error('离线'));
    await v.loadCandidates(); expect(v.candidatesFailed.value).toBe(true);
    await v.submitCreate(); expect(teamTaskApi.create).not.toHaveBeenCalled();
    await v.loadCandidates(); expect(v.candidatesFailed.value).toBe(false);
  });
  it('重复点击完成只发送一次', async () => {
    const v = setup(TasksView); v.detail.value = task();
    let release!: () => void;
    vi.mocked(teamTaskApi.updateStatus).mockImplementation(() => new Promise(resolve => { release = () => resolve(task() as any); }));
    const first = v.changeStatus('DONE'); await v.changeStatus('DONE'); expect(teamTaskApi.updateStatus).toHaveBeenCalledOnce(); release(); await first;
  });
});

describe('个人待办', () => {
  it('今天默认包含逾期、今天和待安排，全部入口包含稍后事项', () => {
    const v = setup(TodosView);
    v.todos.value = [todo({ todoId: '1', dueAt: '2026-11-18 10:00:00' }), todo({ todoId: '2', dueAt: '2026-11-19 10:00:00' }), todo({ todoId: '3', dueAt: '2026-11-20 10:00:00' }), todo({ todoId: '4' }), todo({ todoId: '5', status: 'DONE' })];
    expect(v.filter.value).toBe('today'); expect(v.viewMode.value).toBe('list');
    expect(v.visibleTodos.value.map((t: any) => t.todoId).sort()).toEqual(['1', '2', '4']); v.filter.value = 'all';
    expect(v.groupedTodos.value.map((g: any) => [g.key, g.items.length])).toEqual([['overdue', 1], ['today', 1], ['later', 1], ['unscheduled', 1]]);
    expect(v.counts.value.all).toBe(4);
  });
  it.each(['overdue', 'today', 'later', 'unscheduled', 'done'])('清单分组遵循范围筛选 %s', filter => {
    const v = setup(TodosView); v.filter.value = filter;
    v.todos.value = [todo({ todoId: '1', dueAt: '2026-11-18 10:00:00' }), todo({ todoId: '2', dueAt: '2026-11-19 10:00:00' }), todo({ todoId: '3', dueAt: '2026-11-20 10:00:00' }), todo({ todoId: '4' }), todo({ todoId: '5', status: 'DONE' })];
    const ids = (rows: any[]) => rows.map(t => t.todoId).sort();
    expect(ids(v.groupedTodos.value.flatMap((g: any) => g.items))).toEqual(ids(v.visibleTodos.value));
    if (filter === 'today') expect(ids(v.visibleTodos.value)).toEqual(['1', '2', '4']);
  });
  it('周日历支持跨年切换并覆盖七天', () => {
    const v = setup(TodosView); v.jumpToDate('2027-01-03');
    expect(v.weekDays.value.map((d: any) => d.iso)).toEqual(['2026-12-28','2026-12-29','2026-12-30','2026-12-31','2027-01-01','2027-01-02','2027-01-03']);
    v.weekOffset.value++; expect(v.weekDays.value[0].iso).toBe('2027-01-04');
  });
  it.each(['APPROVAL', 'MEETING', 'TASK', 'REMINDER'])('编辑保留历史标签 %s 并允许清除时间备注', async category => {
    const v = setup(TodosView); v.openCreate(todo({ category, dueAt: '2026-11-19 10:30:00', note: '旧备注' }));
    v.form.value.dueAt = ''; v.form.value.note = ''; await v.submitCreate();
    expect(personalTodoApi.update).toHaveBeenCalledWith('9007199254740996', { title: '待办', category, dueAt: undefined, note: undefined });
    expect(personalTodoApi.create).not.toHaveBeenCalled();
  });
  it('datetime-local 秒不会重复追加', async () => {
    const v = setup(TodosView); v.form.value = { title: '待办', category: 'TASK', dueAt: '2026-11-19T10:30:00', note: '' };
    await v.submitCreate(); expect(personalTodoApi.create).toHaveBeenCalledWith(expect.objectContaining({ dueAt: '2026-11-19 10:30:00' }));
  });
  it('完成重开走独立待办 API，删除需要确认', async () => {
    const v = setup(TodosView); await v.toggleDone(todo(), true); await v.toggleDone(todo({ status: 'DONE' }), false);
    expect(personalTodoApi.complete).toHaveBeenCalledWith('9007199254740996'); expect(personalTodoApi.reopen).toHaveBeenCalledWith('9007199254740996');
    v.detail.value = todo(); await v.removeTodo(); expect(personalTodoApi.remove).not.toHaveBeenCalled();
    v.confirmingDelete.value = true; await v.removeTodo(); expect(personalTodoApi.remove).toHaveBeenCalledWith('9007199254740996');
    expect(teamTaskApi.create).not.toHaveBeenCalled();
  });
});
