import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TodosView from './TodosView.vue';
import { personalTodoApi } from '@/api';

vi.mock('vue', async importOriginal => ({ ...await importOriginal<typeof import('vue')>(), onMounted: vi.fn(), useSSRContext: () => ({ modules: new Set() }) }));
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }));
vi.mock('@/api', () => ({ personalTodoApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), complete: vi.fn(), reopen: vi.fn(), remove: vi.fn() } }));
const setup = (): any => (TodosView as { setup: Function }).setup({}, { expose: vi.fn() });
const todo = (extra = {}) => ({ todoId: '1', title: '跨天待办', category: 'TASK', status: 'PENDING', startAt: '2026-11-18 09:00:00', dueAt: '2026-11-24 18:00:00', ...extra });
beforeEach(() => {
  vi.resetAllMocks(); vi.useFakeTimers(); vi.setSystemTime(new Date('2026-11-19T12:00:00'));
  vi.mocked(personalTodoApi.list).mockResolvedValue([]);
});
afterEach(() => vi.useRealTimers());

describe('待办起止时间和跨天月历', () => {
  it('编辑回填开始时间并提交两个端点，支持清除', async () => {
    const v = setup(); v.openCreate(todo());
    expect(v.form.value.startAt).toBe('2026-11-18T09:00');
    v.form.value.startAt = '2026-11-19T10:15'; await v.submitCreate();
    expect(personalTodoApi.update).toHaveBeenCalledWith('1', expect.objectContaining({ startAt: '2026-11-19 10:15:00', dueAt: '2026-11-24 18:00:00' }));
    v.openCreate(todo()); v.form.value.startAt = ''; v.form.value.dueAt = ''; await v.submitCreate();
    expect(personalTodoApi.update).toHaveBeenLastCalledWith('1', expect.objectContaining({ startAt: undefined, dueAt: undefined }));
  });
  it('反向起止时间不提交，同一时刻可以提交', async () => {
    const v = setup(); v.openCreate(); v.form.value.title = '安排';
    v.form.value.startAt = '2026-11-19T11:00'; v.form.value.dueAt = '2026-11-19T10:00';
    await v.submitCreate(); expect(personalTodoApi.create).not.toHaveBeenCalled();
    v.form.value.dueAt = v.form.value.startAt; await v.submitCreate(); expect(personalTodoApi.create).toHaveBeenCalledOnce();
  });
  it('跨过今天的待办归今天，开始点可单独安排，旧截止点仍可用', () => {
    const v = setup(); expect(v.groupOf(todo())).toBe('today');
    expect(v.groupOf(todo({ startAt: '2026-11-21 09:00:00', dueAt: null }))).toBe('later');
    expect(v.groupOf(todo({ startAt: null, dueAt: '2026-11-18 18:00:00' }))).toBe('overdue');
    expect(v.groupOf(todo({ startAt: null, dueAt: null }))).toBe('unscheduled');
    expect(v.groupOf(todo({ status: 'DONE' }))).toBe('done');
  });
  it('跨天按每日裁切，结束日零点不额外占一天', () => {
    const v = setup(); v.todos.value = [todo({ startAt: '2026-11-18 23:00:00', dueAt: '2026-11-20 00:00:00' })];
    expect(v.schedule.value.flatMap((d: any) => d.blocks.map((b: any) => [d.iso,b.start,b.end]))).toEqual([['2026-11-18',1380,1440],['2026-11-19',0,1440]]);
  });
  it('跨月跨年周定位包含闰日，全天下跨天不进入小时区', () => {
    const v = setup(); v.jumpToDate('2028-02-29');
    expect(v.weekDays.value.map((d: any) => d.iso)).toContain('2028-02-29');
    v.todos.value = [todo({ allDay: true, startAt: '2028-02-28 00:00:00', dueAt: '2028-03-01 23:59:59' })];
    expect(v.schedule.value.filter((d: any) => d.allDay.length).map((d: any) => d.iso)).toEqual(['2028-02-28','2028-02-29','2028-03-01']);
    expect(v.schedule.value.flatMap((d: any) => d.blocks)).toEqual([]);
  });
  it('单边时间按半小时显示，无日期留在待安排，分类搜索同步日历', () => {
    const v = setup(); v.selectScope('all', 'week');
    v.todos.value = [todo({ startAt: null, dueAt: '2026-11-19 10:00:00' }), todo({ todoId: '2', dueAt: null }), todo({ todoId: '3', startAt: null, dueAt: null }), todo({ todoId: '4', status: 'DONE' })];
    expect(v.schedule.value.flatMap((d: any) => d.blocks).map((b: any) => b.end-b.start)).toEqual([30,30]);
    expect(v.unscheduledTodos.value.map((t: any) => t.todoId)).toEqual(['3']);
    v.search.value = '无匹配'; expect(v.schedule.value.flatMap((d: any) => d.blocks)).toEqual([]);
  });
  it('重叠连通组并排排布，相邻时间和独立组复用整列', () => {
    const v = setup(); v.todos.value = [todo({ todoId:'a',startAt:'2026-11-19 09:00:00',dueAt:'2026-11-19 10:00:00' }),todo({todoId:'b',startAt:'2026-11-19 09:30:00',dueAt:'2026-11-19 10:30:00'}),todo({todoId:'c',startAt:'2026-11-19 10:00:00',dueAt:'2026-11-19 11:00:00'}),todo({todoId:'d',startAt:'2026-11-19 11:00:00',dueAt:'2026-11-19 12:00:00'})];
    expect(v.schedule.value[3].blocks.map((b: any) => [b.todo.todoId,b.column,b.columns])).toEqual([['a',0,2],['b',1,2],['c',0,2],['d',0,1]]);
  });
  it('凌晨、午夜与同刻事项不会溢出日列，当前时间随跨日更新', () => {
    const v = setup(); v.todos.value = [todo({ startAt:'2026-11-19 23:59:00',dueAt:null }),todo({todoId:'2',startAt:'2026-11-19 06:00:00',dueAt:'2026-11-19 06:00:00'})];
    expect(v.startHour.value).toBe(6); expect(v.endHour.value).toBe(24);
    expect(v.schedule.value[3].blocks.every((b: any) => b.start>=0 && b.end<=1440 && b.end>b.start)).toBe(true);
    v.now.value = new Date('2026-11-20T00:01:00'); expect(v.todayIso.value).toBe('2026-11-20');
  });
});
