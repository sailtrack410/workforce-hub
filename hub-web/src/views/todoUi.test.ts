import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TodosView from './TodosView.vue';
import { personalTodoApi } from '@/api';

vi.mock('vue', async importOriginal => ({ ...await importOriginal<typeof import('vue')>(), onMounted: vi.fn(), useSSRContext: () => ({ modules: new Set() }) }));
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }));
vi.mock('@/api', () => ({ personalTodoApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), complete: vi.fn(), reopen: vi.fn(), remove: vi.fn() } }));

const todo = (extra = {}) => ({ todoId: '1', ownerId: '7', title: '阅读需求', category: 'TASK', status: 'PENDING', dueAt: null, note: '', ...extra });
const setup = (): any => (TodosView as { setup: Function }).setup({}, { expose: vi.fn() });
beforeEach(() => {
  vi.resetAllMocks();
  vi.useFakeTimers(); vi.setSystemTime(new Date('2026-11-19T12:00:00'));
  vi.mocked(personalTodoApi.list).mockResolvedValue([]);
});
afterEach(() => vi.useRealTimers());

describe('个人待办快捷清单', () => {
  it('搜索标题和备注，兼容大小写与首尾空格，保留分类总数', () => {
    const v = setup();
    v.todos.value = [todo(), todo({ todoId: '2', title: '核对接口', note: 'API 回归' })];
    v.search.value = ' api ';
    expect(v.visibleTodos.value.map((t: any) => t.todoId)).toEqual(['2']);
    expect(v.counts.value.all).toBe(2);
    v.search.value = '阅读'; expect(v.visibleTodos.value.map((t: any) => t.todoId)).toEqual(['1']);
    v.search.value = '不存在'; expect(v.groupedTodos.value).toEqual([]);
    v.search.value = ''; expect(v.visibleTodos.value).toHaveLength(2);
  });
  it('快捷录入不提交空标题，失败保留草稿，成功后追加并清空', async () => {
    const v = setup(); v.quickOpen.value = true; v.quickTitle.value = '   ';
    await v.submitQuick(); expect(personalTodoApi.create).not.toHaveBeenCalled();
    v.quickTitle.value = '  阅读需求  ';
    vi.mocked(personalTodoApi.create).mockRejectedValueOnce(new Error('网络不可用'));
    await v.submitQuick(); expect(v.quickTitle.value).toBe('  阅读需求  '); expect(v.quickOpen.value).toBe(true);
    vi.mocked(personalTodoApi.create).mockResolvedValue(todo() as any);
    await v.submitQuick();
    expect(personalTodoApi.create).toHaveBeenLastCalledWith({ title: '阅读需求', category: 'TASK' });
    expect(v.todos.value.map((t: any) => t.title)).toEqual(['阅读需求']);
    expect(v.quickTitle.value).toBe(''); expect(v.quickCreating.value).toBe(false);
  });
  it('快捷录入重复提交只创建一次', async () => {
    const v = setup(); v.quickTitle.value = '阅读需求';
    let release!: () => void;
    vi.mocked(personalTodoApi.create).mockImplementation(() => new Promise(resolve => { release = () => resolve(todo() as any); }));
    const first = v.submitQuick(); await v.submitQuick();
    expect(personalTodoApi.create).toHaveBeenCalledOnce(); release(); await first;
  });
  it('勾选更新当前项，保留其余清单且不触发全页重载', async () => {
    const v = setup(); v.loading.value = false;
    v.todos.value = [todo(), todo({ todoId: '2', title: '整理笔记' })];
    vi.mocked(personalTodoApi.complete).mockResolvedValue(todo({ status: 'DONE' }) as any);
    await v.toggleDone(v.todos.value[0], true);
    expect(v.todos.value.map((t: any) => t.status)).toEqual(['DONE', 'PENDING']);
    expect(v.visibleTodos.value.map((t: any) => t.todoId)).toEqual(['2']);
    expect(personalTodoApi.list).not.toHaveBeenCalled(); expect(v.loading.value).toBe(false);
  });
  it('同项完成防重复，其他项可同时操作，失败后恢复可操作', async () => {
    const v = setup(); const firstTodo = todo(); const secondTodo = todo({ todoId: '2' });
    v.todos.value = [firstTodo, secondTodo];
    let release!: () => void;
    vi.mocked(personalTodoApi.complete).mockImplementationOnce(() => new Promise(resolve => { release = () => resolve(todo({ status: 'DONE' }) as any); }))
      .mockRejectedValueOnce(new Error('网络不可用'));
    const first = v.toggleDone(firstTodo, true);
    await v.toggleDone(firstTodo, true); await v.toggleDone(secondTodo, true);
    expect(personalTodoApi.complete).toHaveBeenCalledTimes(2);
    expect(v.pendingIds.value.has('1')).toBe(true); expect(v.pendingIds.value.has('2')).toBe(false);
    release(); await first; expect(v.pendingIds.value.size).toBe(0);
    expect(v.todos.value[1].status).toBe('PENDING');
  });
  it('未修改直接关闭，未保存表单保留并请求确认，提交中禁止关闭', () => {
    const v = setup(); v.openCreate(); v.requestCloseCreate(); expect(v.showCreate.value).toBe(false);
    v.openCreate(todo()); v.form.value.title = '修改标题'; v.requestCloseCreate();
    expect(v.showCreate.value).toBe(true); expect(v.discarding.value).toBe(true); expect(v.form.value.title).toBe('修改标题');
    v.discarding.value = false; v.creating.value = true; v.requestCloseCreate();
    expect(v.showCreate.value).toBe(true); expect(v.discarding.value).toBe(false);
  });
});
