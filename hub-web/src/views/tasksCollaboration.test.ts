import { beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import TasksView from './TasksView.vue';
import { teamTaskApi } from '@/api';

vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onMounted: vi.fn(), useSSRContext: () => ({ modules: new Set() }) }));
vi.mock('@/stores/session', () => ({ useSessionStore: () => ({ user: { userId: '9007199254740993' } }) }));
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ show: vi.fn() }) }));
vi.mock('@/api', () => ({ teamTaskApi: { list: vi.fn(), lists: vi.fn(), createList: vi.fn(), renameList: vi.fn(), removeList: vi.fn(), assignees: vi.fn(), detail: vi.fn(), watch: vi.fn(), comment: vi.fn(), addSubtask: vi.fn(), updateSubtask: vi.fn(), removeSubtask: vi.fn(), update: vi.fn(), create: vi.fn() } }));

const task = (extra = {}) => ({ taskId: '9007199254740995', title: '联调', creatorId: 'other', assigneeId: '9007199254740993', status: 'TODO', canEdit: false, canDelete: false, canStart: true, canComplete: true, canReopen: false, startDate: '2026-11-23', endDate: '2026-11-25', subtasks: [], activities: [], ...extra });
const setup = (): any => (TasksView as unknown as { setup: Function }).setup({}, { expose: vi.fn() });
beforeEach(() => { vi.resetAllMocks(); vi.mocked(teamTaskApi.list).mockResolvedValue([]); vi.mocked(teamTaskApi.lists).mockResolvedValue([]); });

describe('飞书任务协作交互', () => {
  it('真实清单名称优先于旧 groupId，空清单也显示在导航', () => {
    const v = setup(); v.taskLists.value = [{ listId: '9007199254740997', name: '版本发布', canEdit: true }];
    expect(v.taskGroups.value).toEqual(['9007199254740997']);
    v.groupFilter.value = '9007199254740997'; expect(v.pageTitle.value).toBe('版本发布');
    v.tasks.value = [task({ groupId: 'legacy' })]; expect(v.groupName('legacy')).toBe('清单 #legacy');
  });
  it('关注更新详情与列表，取消关注后立即退出关注范围', async () => {
    const v = setup(); v.tasks.value = [task()]; v.detail.value = task();
    vi.mocked(teamTaskApi.watch).mockResolvedValueOnce(task({ watching: true }) as any);
    await v.toggleWatch(); expect(teamTaskApi.watch).toHaveBeenCalledWith('9007199254740995', true);
    v.selectScope('watched'); expect(v.visibleTasks.value).toHaveLength(1);
    vi.mocked(teamTaskApi.watch).mockResolvedValueOnce(task({ watching: false }) as any);
    await v.toggleWatch(); expect(v.visibleTasks.value).toHaveLength(0);
  });
  it('评论失败保留输入，成功后清空并显示返回记录', async () => {
    const v = setup(); v.detail.value = task(); v.commentText.value = '  请核对接口  ';
    vi.mocked(teamTaskApi.comment).mockRejectedValueOnce(new Error('离线'));
    await v.postComment(); expect(v.commentText.value).toBe('  请核对接口  '); expect(v.detailsError.value).toBe('离线');
    vi.mocked(teamTaskApi.comment).mockResolvedValueOnce(task({ activities: [{ activityId: '1', kind: 'COMMENT', content: '请核对接口' }] }) as any);
    await v.postComment(); expect(teamTaskApi.comment).toHaveBeenLastCalledWith('9007199254740995', '请核对接口');
    expect(v.commentText.value).toBe(''); expect(v.detailActivities.value).toHaveLength(1);
  });
  it('提交中禁止重复评论', async () => {
    const v = setup(); v.detail.value = task(); v.commentText.value = '检查';
    let finish!: (value: any) => void;
    vi.mocked(teamTaskApi.comment).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
    const pending = v.postComment(); await v.postComment(); expect(teamTaskApi.comment).toHaveBeenCalledOnce();
    finish(task()); await pending; expect(v.busy.value).toBe(false);
  });
  it('只有派发人能增加、改名、删除子任务，负责人才能勾选完成', async () => {
    const v = setup(); v.detail.value = task(); v.subtaskTitle.value = '子任务';
    await v.saveSubtask(); expect(teamTaskApi.addSubtask).not.toHaveBeenCalled();
    const child = { subtaskId: '9007199254740999', title: '子任务', completed: false };
    vi.mocked(teamTaskApi.updateSubtask).mockResolvedValue(task({ subtasks: [{ ...child, completed: true }] }) as any);
    await v.toggleSubtask(child);
    expect(teamTaskApi.updateSubtask).toHaveBeenCalledWith('9007199254740995', '9007199254740999', '子任务', true);
    expect(v.completedSubtasks.value).toBe(1);
    v.detail.value = task({ assigneeId: '9007199254740992' }); await v.toggleSubtask(child);
    expect(teamTaskApi.updateSubtask).toHaveBeenCalledTimes(1);
  });
  it('子任务编辑和删除保留父任务上下文', async () => {
    const v = setup(); const child = { subtaskId: '4', title: '原名称', completed: false };
    v.detail.value = task({ canEdit: true, subtasks: [child] }); v.editingSubtask.value = child; v.subtaskTitle.value = '修改名称';
    vi.mocked(teamTaskApi.updateSubtask).mockResolvedValue(task({ canEdit: true, subtasks: [{ ...child, title: '修改名称' }] }) as any);
    await v.saveSubtask(); expect(v.editingSubtask.value).toBeNull(); expect(v.detail.value.taskId).toBe('9007199254740995');
    v.removingSubtask.value = child; vi.mocked(teamTaskApi.removeSubtask).mockResolvedValue(task({ canEdit: true }) as any);
    await v.deleteSubtask(); expect(teamTaskApi.removeSubtask).toHaveBeenCalledWith('9007199254740995', '4'); expect(v.detailSubtasks.value).toEqual([]);
  });
  it('记录筛选只过滤评论类型，不丢失历史数据', () => {
    const v = setup(); v.detail.value = task({ activities: [{ kind: 'COMMENT' }, { kind: 'STATUS' }, { kind: 'SUBTASK' }] });
    v.activityTab.value = 'comments'; expect(v.detailActivities.value).toHaveLength(1);
    v.activityTab.value = 'history'; expect(v.detailActivities.value).toHaveLength(2);
    v.activityTab.value = 'all'; expect(v.detailActivities.value).toHaveLength(3);
  });
  it('清单创建会修剪名称，失败保留表单；加载失败可重试', async () => {
    const v = setup(); v.openListForm(); v.listNameInput.value = '  新版本  ';
    vi.mocked(teamTaskApi.createList).mockRejectedValueOnce(new Error('保存失败'));
    await v.saveList(); expect(v.showListForm.value).toBe(true); expect(v.listNameInput.value).toBe('  新版本  ');
    vi.mocked(teamTaskApi.createList).mockResolvedValueOnce({ listId: '8', name: '新版本', canEdit: true } as any);
    await v.saveList(); expect(teamTaskApi.createList).toHaveBeenLastCalledWith('新版本'); expect(v.groupFilter.value).toBe('8');
    vi.mocked(teamTaskApi.lists).mockRejectedValueOnce(new Error('离线')); await v.loadLists(); expect(v.listsFailed.value).toBe(true);
    await v.loadLists(); expect(v.listsFailed.value).toBe(false);
  });
  it('清单删除必须确认并具备拥有权限', async () => {
    const v = setup(); v.taskLists.value = [{ listId: '8', name: '清单', canEdit: true }]; v.groupFilter.value = '8';
    await v.deleteList(); expect(teamTaskApi.removeList).not.toHaveBeenCalled();
    v.listDeleting.value = true; await v.deleteList(); expect(teamTaskApi.removeList).toHaveBeenCalledWith('8'); expect(v.groupFilter.value).toBe('');
  });
});
