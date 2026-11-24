import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import TodosView from './TodosView.vue';
import { personalTodoApi } from '@/api';

vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onMounted: vi.fn(), onBeforeUnmount: vi.fn(), useSSRContext: () => ({ modules: new Set() }) }));
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }));
vi.mock('@/api', () => ({ personalTodoApi: { list: vi.fn(), create: vi.fn(), update: vi.fn(), complete: vi.fn(), reopen: vi.fn(), remove: vi.fn() } }));
const setup = (): any => (TodosView as { setup: Function }).setup({}, { expose: vi.fn() });
const todo = (extra = {}) => ({ todoId: '9007199254740993', title: '事项', status: 'PENDING', category: 'TASK', ...extra });
beforeEach(() => { vi.resetAllMocks(); vi.useFakeTimers(); vi.setSystemTime(new Date('2026-11-25T13:00:00')); });
afterEach(() => vi.useRealTimers());

describe('新版待办排程表单', () => {
  it('全天请求保留日期范围，备注修剪并携带站内提醒设置', () => {
    const v = setup();
    const result = v.payload({ title: '  制度校对  ', category: 'TASK', startAt: '2026-11-25', dueAt: '2026-11-26', note: '  核对  ', allDay: true, reminderMinutes: 10 });
    expect(result).toEqual({ title: '制度校对', category: 'TASK', startAt: '2026-11-25 00:00:00', dueAt: '2026-11-26 23:59:59', note: '核对', allDay: true, reminderMinutes: 10 });
  });
  it('提醒缺少日期、反向时间均不发请求，错误保留草稿', async () => {
    const v = setup(); v.quickForm.value.title = '事项'; v.quickForm.value.reminderMinutes = 10;
    await v.submitQuick(); expect(personalTodoApi.create).not.toHaveBeenCalled(); expect(v.quickError.value).toBe('请先选择日期与时间');
    v.quickForm.value.startAt = '2026-11-25T14:00'; v.quickForm.value.dueAt = '2026-11-25T13:00';
    await v.submitQuick(); expect(personalTodoApi.create).not.toHaveBeenCalled(); expect(v.quickTitle.value).toBe('事项');
  });
  it('有效起止时间与分类直接提交，失败重试不丢失设置', async () => {
    const v = setup(); v.quickForm.value = { title: '会议', category: 'MEETING', startAt: '2026-11-25T14:00', dueAt: '2026-11-25T15:00', allDay: false, note: '材料', reminderMinutes: 10 };
    vi.mocked(personalTodoApi.create).mockRejectedValueOnce(new Error('离线')); await v.submitQuick();
    expect(v.quickForm.value.reminderMinutes).toBe(10);
    vi.mocked(personalTodoApi.create).mockResolvedValueOnce(todo({ category: 'MEETING' }) as any); await v.submitQuick();
    expect(personalTodoApi.create).toHaveBeenLastCalledWith(expect.objectContaining({ startAt: '2026-11-25 14:00:00', dueAt: '2026-11-25 15:00:00', reminderMinutes: 10, category: 'MEETING' }));
    expect(v.todos.value).toHaveLength(1); expect(v.quickOpen.value).toBe(false);
  });
  it('小时与全天格预填准确，23点不会生成24:00非法请求', () => {
    const v = setup(); v.openSlot('2026-11-25', 23);
    expect(v.form.value.dueAt).toBe('2026-11-25T23:59');
    v.openSlot('2026-11-26', 0, true); expect(v.payload(v.form.value = { ...v.form.value, title: '事项' })).toMatchObject({ allDay: true, startAt: '2026-11-26 00:00:00', dueAt: '2026-11-26 23:59:59' });
  });
  it('星期日跳转仍定位到所在周，今天线随周切换隐藏', () => {
    const v = setup(); expect(v.nowVisible.value).toBe(true);
    v.jumpToDate('2026-11-29'); expect(v.weekDays.value[0].iso).toBe('2026-11-23');
    v.jumpToDate('2026-12-06'); expect(v.weekDays.value[0].iso).toBe('2026-11-30'); expect(v.nowVisible.value).toBe(false);
  });
  it('分类计数取真实待办，搜索同时过滤时间块和全天区', () => {
    const v = setup(); v.todos.value = [todo({startAt:'2026-11-25 09:00:00'}),todo({todoId:'2',title:'会议',category:'MEETING',startAt:'2026-11-25 00:00:00',dueAt:'2026-11-25 23:59:59',allDay:true}),todo({todoId:'3',status:'DONE'})];
    expect(v.categoryCounts.value.TASK).toBe(1);
    v.selectCategory('MEETING'); expect(v.schedule.value[2].allDay).toHaveLength(1); expect(v.schedule.value[2].blocks).toEqual([]);
    v.search.value = '不存在'; expect(v.schedule.value.every((d: any) => !d.allDay.length && !d.blocks.length)).toBe(true);
  });
});
