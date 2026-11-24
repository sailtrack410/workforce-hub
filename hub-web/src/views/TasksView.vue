<script setup lang="ts">
/**
 * 团队任务：按负责人 × 当前周的时间泳道，任务为横向色条；支持列表视图、新建与详情弹层。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useLocalDraft } from '@/services/localData';
import { Plus, LayoutList, ChartNoAxesCombined, ChevronLeft, ChevronRight, ChevronDown, CalendarDays, UsersRound, Send, CircleCheck, Folder, PanelLeftClose, PanelLeftOpen, SlidersHorizontal, Search, X, UserRound, Bookmark, Pencil, Trash2, Link, Check, ListTree, MessageSquare, Clock3, Maximize2, Paperclip, Download, FileText } from 'lucide-vue-next';
import { teamTaskApi } from '@/api';
import { ApiError } from '@/api/http';
import type { TeamTaskResponse, TeamTaskListResponse, TeamTaskSubtaskResponse, TeamTaskAttachmentResponse } from '@/api/types';
import { uploadEmployeeFile } from '@/composables/useUpload';
import { useToast } from '@/composables/useToast';
import { useSessionStore } from '@/stores/session';
import HubButton from '@/components/base/HubButton.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';

const session = useSessionStore();
const { show: showToast } = useToast();
const tasks = ref<TeamTaskResponse[]>([]);
const colleagues = ref<Awaited<ReturnType<typeof teamTaskApi.assignees>>>([]);
const loading = ref(true);
const loadFailed = ref(false);
const filter = ref<'all' | 'mine' | 'created' | 'today' | 'done' | 'watched'>('all');
const statusFilter = ref('');
const viewMode = ref<'lane' | 'list'>('list');
const tabs = [{ key: 'all', label: '全部任务', icon: LayoutList }, { key: 'created', label: '我派发的', icon: Send }, { key: 'done', label: '已完成', icon: CircleCheck }] as const;
const STATUS_LABEL: Record<string, string> = { TODO: '待开始', IN_PROGRESS: '进行中', IN_REVIEW: '评审中', DONE: '已完成' };
const STATUS_CLASS: Record<string, string> = { TODO: 'todo', IN_PROGRESS: 'progress', IN_REVIEW: 'review', DONE: 'done' };
const sidebarCollapsed = ref(false);
const keyword = ref('');
const assigneeFilter = ref('');
const groupFilter = ref('');
const sortBy = ref<'endDate' | 'startDate' | 'title'>('endDate');
const groupBy = ref<'none' | 'status' | 'assignee'>('none');
const fields = [{ key: 'creator', label: '派发人' }, { key: 'assignee', label: '负责人' }, { key: 'start', label: '开始时间' }, { key: 'end', label: '截止时间' }, { key: 'status', label: '状态' }, { key: 'group', label: '所属清单' }] as const;
const columns = ref<string[]>(fields.map(f => f.key));
const page = ref(1);
const PAGE_SIZE = 12;
const selectedIds = ref<string[]>([]);
const batchBusy = ref(false);
const confirmingBatchDelete = ref(false);
const taskLists = ref<TeamTaskListResponse[]>([]);
const listsFailed = ref(false);
const showListForm = ref(false);
const listEditing = ref<TeamTaskListResponse | null>(null);
const listNameInput = ref('');
const listBusy = ref(false);
const listError = ref('');
const listDeleting = ref(false);
const listPanel = ref<HTMLFormElement | null>(null);
const showSubtasks = ref(true);
const subtaskTitle = ref('');
const editingSubtask = ref<TeamTaskSubtaskResponse | null>(null);
const removingSubtask = ref<TeamTaskSubtaskResponse | null>(null);
const commentText = ref('');
const activityTab = ref<'all' | 'comments' | 'history'>('all');
const detailsError = ref('');
const detailExpanded = ref(false);
const resumeDetail = ref<TeamTaskResponse | null>(null);
const attachments = ref<TeamTaskAttachmentResponse[]>([]);
const attachmentsLoading = ref(false);
const attachmentsFailed = ref(false);
const attachmentInput = ref<HTMLInputElement | null>(null);
const removingAttachment = ref<TeamTaskAttachmentResponse | null>(null);

/* 当前周（周一到周日） */
const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日'];
const weekOffset = ref(0);
const todayIso = toIso(new Date());
function toIso(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
const monday = computed(() => {
  const d = new Date();
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7) + weekOffset.value * 7);
  d.setHours(0, 0, 0, 0);
  return d;
});
const weekDays = computed(() => Array.from({ length: 7 }, (_, i) => {
  const d = new Date(monday.value); d.setDate(d.getDate() + i);
  return { iso: toIso(d), label: `${d.getDate()} ${WEEK_LABELS[i]}` };
}));
async function load() {
  loading.value = true; loadFailed.value = false;
  try { tasks.value = await teamTaskApi.list({ scope: 'ALL' }); }
  catch (error) { tasks.value = []; loadFailed.value = true; showToast(error instanceof Error ? error.message : '任务加载失败'); }
  finally { loading.value = false; }
}
async function loadLists() {
  listsFailed.value = false;
  try { taskLists.value = await teamTaskApi.lists(); }
  catch { listsFailed.value = true; }
}
onMounted(async () => {
  window.addEventListener('hashchange', openLinkedTask);
  await Promise.all([load(), loadLists(), session.user ? Promise.resolve() : session.loadCurrentUser()]);
  await openLinkedTask();
});
onBeforeUnmount(() => window.removeEventListener('hashchange', openLinkedTask));
async function openLinkedTask() {
  const taskId = new URLSearchParams(location.hash.split('?')[1] || '').get('taskId');
  if (taskId && taskId !== String(detail.value?.taskId)) await openDetail({ taskId } as TeamTaskResponse);
}
const assigneeName = (t: TeamTaskResponse) => t.assigneeName || '—';
const mineCount = computed(() => tasks.value.filter(t => String(t.assigneeId) === String(session.user?.userId) && t.status !== 'DONE').length);
const taskGroups = computed(() => [...new Set([...taskLists.value.map(l => String(l.listId)), ...tasks.value.filter(t => t.groupId != null).map(t => String(t.groupId)).sort()])]);
const groupName = (id: string) => taskLists.value.find(l => String(l.listId) === id)?.name || tasks.value.find(t => String(t.groupId) === id)?.groupName || `清单 #${id}`;
const currentList = computed(() => taskLists.value.find(l => String(l.listId) === groupFilter.value));
const assignees = computed(() => [...new Map(tasks.value.map(t => [String(t.assigneeId), assigneeName(t)])).entries()]);
const pageTitle = computed(() => groupFilter.value ? groupName(groupFilter.value) : filter.value === 'today' ? '今日' : filter.value === 'mine' ? '我负责的' : filter.value === 'watched' ? '我关注的' : tabs.find(t => t.key === filter.value)?.label || '全部任务');
const scopedTasks = computed(() => tasks.value.filter(t => {
  if (groupFilter.value && String(t.groupId) !== groupFilter.value) return false;
  if (filter.value === 'today') return t.status !== 'DONE' && !!t.startDate && !!t.endDate && t.startDate <= todayIso && t.endDate >= todayIso;
  if (filter.value === 'done') return t.status === 'DONE';
  if (filter.value === 'watched') return !!t.watching;
  return filter.value === 'all' || String(filter.value === 'mine' ? t.assigneeId : t.creatorId) === String(session.user?.userId);
}));
const visibleTasks = computed(() => scopedTasks.value.filter(t => (!statusFilter.value || t.status === statusFilter.value)
  && (!assigneeFilter.value || String(t.assigneeId) === assigneeFilter.value)
  && (!keyword.value.trim() || t.title.toLocaleLowerCase().includes(keyword.value.trim().toLocaleLowerCase())))
  .sort((a, b) => {
    if (sortBy.value === 'title') return a.title.localeCompare(b.title, 'zh-CN');
    const left = a[sortBy.value]; const right = b[sortBy.value];
    return left && right ? left.localeCompare(right) : left ? -1 : right ? 1 : 0;
  }));
const counts = computed(() => ({ pending: scopedTasks.value.filter(t => t.status !== 'DONE').length,
  progress: scopedTasks.value.filter(t => t.status === 'IN_PROGRESS').length,
  done: scopedTasks.value.filter(t => t.status === 'DONE').length }));
const pageCount = computed(() => Math.max(1, Math.ceil(visibleTasks.value.length / PAGE_SIZE)));
const pageTasks = computed(() => visibleTasks.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE));
const groupedTasks = computed(() => {
  const groups = new Map<string, { key: string; label: string; items: TeamTaskResponse[] }>();
  for (const task of pageTasks.value) {
    const key = groupBy.value === 'status' ? task.status : groupBy.value === 'assignee' ? String(task.assigneeId) : '';
    if (!groups.has(key)) groups.set(key, { key, label: groupBy.value === 'status' ? STATUS_LABEL[task.status] : groupBy.value === 'assignee' ? assigneeName(task) : '', items: [] });
    groups.get(key)!.items.push(task);
  }
  return [...groups.values()];
});
const selectableTasks = computed(() => pageTasks.value.filter(t => t.canComplete || t.canDelete));
const selectedTasks = computed(() => visibleTasks.value.filter(t => selectedIds.value.includes(String(t.taskId))));
const allSelected = computed(() => selectableTasks.value.length > 0 && selectableTasks.value.every(t => selectedIds.value.includes(String(t.taskId))));
const someSelected = computed(() => selectableTasks.value.some(t => selectedIds.value.includes(String(t.taskId))));
const canBatchComplete = computed(() => selectedTasks.value.length > 0 && selectedTasks.value.every(t => t.canComplete));
const canBatchDelete = computed(() => selectedTasks.value.length > 0 && selectedTasks.value.every(t => t.canDelete));
watch([filter, statusFilter, assigneeFilter, keyword, groupFilter, sortBy, groupBy, viewMode], () => { page.value = 1; selectedIds.value = []; confirmingBatchDelete.value = false; });
watch(visibleTasks, rows => {
  page.value = Math.min(page.value, pageCount.value);
  selectedIds.value = selectedIds.value.filter(id => rows.some(t => String(t.taskId) === id && (t.canComplete || t.canDelete)));
});
function selectScope(scope: typeof filter.value, group = '') {
  if (batchBusy.value) return;
  filter.value = scope; groupFilter.value = group; statusFilter.value = ''; assigneeFilter.value = ''; keyword.value = '';
}
function openListForm(list: TeamTaskListResponse | null = null) {
  if (list && !list.canEdit) return;
  listEditing.value = list; listNameInput.value = list?.name || ''; listError.value = ''; showListForm.value = true;
  void nextTick(() => listPanel.value?.querySelector('input')?.focus());
}
async function saveList() {
  if (listBusy.value) return;
  const name = listNameInput.value.trim();
  if (!name || name.length > 64) { listError.value = '清单名称须为1至64个字符'; return; }
  listBusy.value = true; listError.value = '';
  try {
    const list = listEditing.value ? await teamTaskApi.renameList(listEditing.value.listId, name) : await teamTaskApi.createList(name);
    await loadLists(); await load(); showListForm.value = false; selectScope('all', String(list.listId));
  } catch (error) { listError.value = error instanceof Error ? error.message : '清单保存失败'; }
  finally { listBusy.value = false; }
}
async function deleteList() {
  if (listBusy.value || !currentList.value?.canEdit || !listDeleting.value) return;
  listBusy.value = true; listError.value = '';
  try { await teamTaskApi.removeList(currentList.value.listId); listDeleting.value = false; selectScope('all'); await loadLists(); }
  catch (error) { listError.value = error instanceof Error ? error.message : '清单删除失败'; }
  finally { listBusy.value = false; }
}
function toggleTask(task: TeamTaskResponse) {
  if (batchBusy.value || !(task.canComplete || task.canDelete)) return;
  const id = String(task.taskId);
  selectedIds.value = selectedIds.value.includes(id) ? selectedIds.value.filter(item => item !== id) : [...selectedIds.value, id];
}
function togglePage() {
  if (batchBusy.value) return;
  const ids = selectableTasks.value.map(t => String(t.taskId));
  selectedIds.value = allSelected.value ? selectedIds.value.filter(id => !ids.includes(id)) : [...new Set([...selectedIds.value, ...ids])];
}
async function runBatch(action: 'complete' | 'delete') {
  if (batchBusy.value || !(action === 'complete' ? canBatchComplete.value : canBatchDelete.value) || (action === 'delete' && !confirmingBatchDelete.value)) return;
  batchBusy.value = true;
  const targets = [...selectedTasks.value];
  try {
    const results = await Promise.allSettled(targets.map(t => action === 'complete' ? teamTaskApi.updateStatus(t.taskId, 'DONE') : teamTaskApi.remove(t.taskId)));
    const failed = targets.filter((_, index) => results[index].status === 'rejected');
    selectedIds.value = failed.map(t => String(t.taskId));
    confirmingBatchDelete.value = false;
    const label = action === 'complete' ? '完成' : '删除';
    showToast(failed.length ? `已${label} ${targets.length - failed.length} 项，${failed.length} 项失败` : `已${label} ${targets.length} 项任务`);
    await load();
  } finally { batchBusy.value = false; }
}

/* 泳道数据：每位负责人一行，任务按日期切到本周列内，纵向按重叠分行 */
interface LaneBar { task: TeamTaskResponse; startCol: number; span: number; lane: number }
interface LaneRow { id: string; name: string; bars: LaneBar[]; maxLane: number }
const lanes = computed<LaneRow[]>(() => {
  const map = new Map<string, LaneRow>();
  const first = weekDays.value[0].iso; const last = weekDays.value[6].iso;
  for (const t of visibleTasks.value.filter(t => t.startDate && t.endDate && t.startDate <= t.endDate && t.startDate <= last && t.endDate >= first)
    .sort((a, b) => a.startDate!.localeCompare(b.startDate!))) {
    const id = String(t.assigneeId);
    if (!map.has(id)) map.set(id, { id, name: assigneeName(t), bars: [], maxLane: 1 });
    const row = map.get(id)!;
    const startCol = t.startDate! <= first ? 0 : weekDays.value.findIndex(d => d.iso === t.startDate);
    const endCol = t.endDate! >= last ? 6 : weekDays.value.findIndex(d => d.iso === t.endDate);
    if (startCol < 0 || endCol < startCol) continue;
    let lane = 0;
    while (row.bars.some(b => b.lane === lane && b.startCol + b.span > startCol)) lane++;
    row.bars.push({ task: t, startCol, span: endCol - startCol + 1, lane });
    row.maxLane = Math.max(row.maxLane, lane + 1);
  }
  return [...map.values()];
});

/* 新建弹层 */
const showCreate = ref(false);
const creating = ref(false);
const candidatesLoading = ref(false);
const candidatesFailed = ref(false);
const editing = ref<TeamTaskResponse | null>(null);
const form = ref({ title: '', description: '', assigneeId: '', startDate: todayIso, endDate: todayIso, groupId: '' });
const taskDraft = useLocalDraft('task-create', form);
onBeforeUnmount(taskDraft.stop);
const formErrors = ref<Record<string, string>>({});
const formPanel = ref<HTMLFormElement | null>(null);
const detailPanel = ref<HTMLElement | null>(null);
const confirmPanel = ref<HTMLElement | null>(null);
const discardChanges = ref(false);
let initialForm = '';
let returnFocus: HTMLElement | null = null;
function closeCreate() {
  if (creating.value) return;
  if (JSON.stringify(form.value) !== initialForm) discardChanges.value = true;
  else showCreate.value = false;
}
function closeDetail() {
  if (busy.value) return;
  detail.value = null; confirmingDelete.value = false;
}
function trapFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab') return;
  const nodes = [...(event.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]')];
  const first = nodes[0]; const last = nodes[nodes.length - 1];
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
}
watch(showCreate, async open => {
  await nextTick();
  if (open) formPanel.value?.querySelector('input')?.focus();
  else {
    discardChanges.value = false;
    if (resumeDetail.value && !detail.value) detail.value = resumeDetail.value;
    resumeDetail.value = null;
    if (!detail.value) returnFocus?.focus();
  }
});
watch([discardChanges, confirmingBatchDelete, listDeleting], async ([discard, batch, listDelete]) => {
  await nextTick();
  if (discard || batch || listDelete) confirmPanel.value?.querySelector<HTMLButtonElement>('button')?.focus();
  else if (showCreate.value) formPanel.value?.querySelector('input')?.focus();
  else if (typeof document !== 'undefined') document.querySelector<HTMLButtonElement>('.tk-batch-bar button')?.focus();
});
async function loadCandidates() {
  candidatesLoading.value = true; candidatesFailed.value = false; colleagues.value = [];
  try { colleagues.value = await teamTaskApi.assignees(); }
  catch (error) { candidatesFailed.value = true; showToast(error instanceof Error ? error.message : '负责人加载失败'); }
  finally { candidatesLoading.value = false; }
}
function openCreate(task: TeamTaskResponse | null = null) {
  if (task && !task.canEdit) return;
  if (!task && typeof document !== 'undefined') returnFocus = document.activeElement as HTMLElement;
  resumeDetail.value = task;
  editing.value = task; detail.value = null;
  form.value = { title: task?.title || '', description: task?.description || '', assigneeId: task ? String(task.assigneeId) : '', startDate: task?.startDate || todayIso, endDate: task?.endDate || todayIso, groupId: task?.groupId == null ? groupFilter.value : String(task.groupId) };
  initialForm = JSON.stringify(form.value);
  formErrors.value = {};
  showCreate.value = true; void loadCandidates();
  if (!task) void taskDraft.restore();
}
async function submitCreate() {
  if (creating.value || candidatesLoading.value || candidatesFailed.value) return;
  formErrors.value = {};
  if (!form.value.title.trim()) formErrors.value.title = '请填写任务名称';
  if (!form.value.assigneeId) formErrors.value.assigneeId = '请选择负责人';
  if (!form.value.startDate) formErrors.value.startDate = '请选择开始日期';
  if (!form.value.endDate || form.value.startDate > form.value.endDate) formErrors.value.endDate = '结束日期不能早于开始日期';
  const invalid = Object.keys(formErrors.value)[0];
  if (invalid) { formPanel.value?.querySelector<HTMLElement>(`[name="${invalid}"]`)?.focus(); return; }
  creating.value = true;
  try {
    const payload = { ...form.value, title: form.value.title.trim(), description: form.value.description.trim(), groupId: form.value.groupId === undefined ? (editing.value?.groupId == null ? undefined : String(editing.value.groupId)) : form.value.groupId || undefined };
    const saved = editing.value ? await teamTaskApi.update(editing.value.taskId, payload) : await teamTaskApi.create(payload);
    if (editing.value && saved) detail.value = saved;
    await taskDraft.clear();
    showToast(editing.value ? '任务已保存' : '任务已派发'); showCreate.value = false; await Promise.all([load(), loadLists()]);
  } catch (error) { await handleError(error); }
  finally { creating.value = false; }
}

/* 详情弹层 */
const detail = ref<TeamTaskResponse | null>(null);
const detailSubtasks = computed(() => detail.value?.subtasks || []);
const completedSubtasks = computed(() => detailSubtasks.value.filter(t => t.completed).length);
const detailActivities = computed(() => (detail.value?.activities || []).filter(a => activityTab.value === 'all' || (activityTab.value === 'comments' ? a.kind === 'COMMENT' : a.kind !== 'COMMENT')));
watch(detail, async task => {
  await nextTick();
  if (task) {
    if (!detailPanel.value?.contains(document.activeElement)) detailPanel.value?.querySelector<HTMLButtonElement>('button')?.focus();
  }
  else if (!showCreate.value) returnFocus?.focus();
});
const confirmingDelete = ref(false);
const busy = ref(false);
async function openDetail(t: TeamTaskResponse) {
  if (busy.value) return;
  if (typeof document !== 'undefined') returnFocus = document.activeElement as HTMLElement;
  busy.value = true; confirmingDelete.value = false; subtaskTitle.value = ''; editingSubtask.value = null; removingSubtask.value = null; commentText.value = ''; detailsError.value = ''; activityTab.value = 'all';
  try { detail.value = await teamTaskApi.detail(t.taskId); attachments.value = []; removingAttachment.value = null; void loadAttachments(); }
  catch (error) { await handleError(error); }
  finally { busy.value = false; }
}
async function handleError(error: unknown) {
  showToast(error instanceof Error ? error.message : '操作失败');
  if (error instanceof ApiError && [403, 404, 409].includes(error.status || 0)) {
    resumeDetail.value = null; detail.value = null; showCreate.value = false; confirmingDelete.value = false; await load();
  }
}
async function changeStatus(status: 'TODO' | 'IN_PROGRESS' | 'DONE') {
  const t = detail.value;
  if (!t || busy.value || !(status === 'DONE' ? t.canComplete : status === 'IN_PROGRESS' ? t.canStart : t.canReopen)) return;
  busy.value = true;
  try { const updated = await teamTaskApi.updateStatus(t.taskId, status); if (updated) detail.value = updated; showToast(status === 'DONE' ? '任务已完成' : status === 'TODO' ? '任务已重开' : '任务已开始'); await load(); }
  catch (error) { await handleError(error); }
  finally { busy.value = false; }
}
async function deleteTask() {
  if (!detail.value?.canDelete || !confirmingDelete.value || busy.value) return;
  busy.value = true;
  try { await teamTaskApi.remove(detail.value.taskId); detail.value = null; confirmingDelete.value = false; showToast('任务已删除'); await load(); }
  catch (error) { await handleError(error); }
  finally { busy.value = false; }
}
function applyDetail(task: TeamTaskResponse) {
  detail.value = task;
  tasks.value = tasks.value.map(t => String(t.taskId) === String(task.taskId) ? task : t);
}
async function toggleWatch() {
  if (!detail.value || busy.value) return;
  busy.value = true; detailsError.value = '';
  try { applyDetail(await teamTaskApi.watch(detail.value.taskId, !detail.value.watching)); }
  catch (error) { await handleDetailError(error); }
  finally { busy.value = false; }
}
async function postComment() {
  const task = detail.value; const content = commentText.value.trim();
  if (!task || busy.value || !content) return;
  busy.value = true; detailsError.value = '';
  try { applyDetail(await teamTaskApi.comment(task.taskId, content)); commentText.value = ''; }
  catch (error) { await handleDetailError(error); }
  finally { busy.value = false; }
}
async function saveSubtask() {
  const task = detail.value; const title = subtaskTitle.value.trim();
  if (!task?.canEdit || busy.value || !title) return;
  busy.value = true; detailsError.value = '';
  try {
    const current = editingSubtask.value;
    applyDetail(current ? await teamTaskApi.updateSubtask(task.taskId, current.subtaskId, title, current.completed) : await teamTaskApi.addSubtask(task.taskId, title));
    subtaskTitle.value = ''; editingSubtask.value = null;
  } catch (error) { await handleDetailError(error); }
  finally { busy.value = false; }
}
function startSubtaskEdit(item: TeamTaskSubtaskResponse) {
  editingSubtask.value = item; subtaskTitle.value = item.title;
  void nextTick(() => detailPanel.value?.querySelector<HTMLInputElement>('.tk-subtask-form input')?.focus());
}
async function toggleSubtask(item: TeamTaskSubtaskResponse) {
  const task = detail.value;
  if (!task || String(task.assigneeId) !== String(session.user?.userId) || busy.value) return;
  busy.value = true; detailsError.value = '';
  try { applyDetail(await teamTaskApi.updateSubtask(task.taskId, item.subtaskId, item.title, !item.completed)); }
  catch (error) { await handleDetailError(error); }
  finally { busy.value = false; }
}
async function deleteSubtask() {
  const task = detail.value; const item = removingSubtask.value;
  if (!task?.canEdit || !item || busy.value) return;
  busy.value = true; detailsError.value = '';
  try { applyDetail(await teamTaskApi.removeSubtask(task.taskId, item.subtaskId)); removingSubtask.value = null; }
  catch (error) { await handleDetailError(error); }
  finally { busy.value = false; }
}
async function handleDetailError(error: unknown) {
  detailsError.value = error instanceof Error ? error.message : '操作失败';
  if (error instanceof ApiError && [403, 404, 409].includes(error.status || 0)) await handleError(error);
}
async function copyTaskLink() {
  if (!detail.value) return;
  try { await navigator.clipboard.writeText(`${location.origin}${location.pathname}#/tasks?taskId=${encodeURIComponent(String(detail.value.taskId))}`); showToast('任务链接已复制'); }
  catch { detailsError.value = '复制失败，请重试'; }
}
async function loadAttachments() {
  const taskId = detail.value?.taskId;
  if (taskId == null) return;
  attachmentsLoading.value = true; attachmentsFailed.value = false;
  try { const files = await teamTaskApi.attachments(taskId); if (String(detail.value?.taskId) === String(taskId)) attachments.value = files; }
  catch { if (String(detail.value?.taskId) === String(taskId)) attachmentsFailed.value = true; }
  finally { if (String(detail.value?.taskId) === String(taskId)) attachmentsLoading.value = false; }
}
async function addAttachment(event: Event) {
  const input = event.target as HTMLInputElement; const file = input.files?.[0]; const taskId = detail.value?.taskId;
  if (!file || taskId == null || busy.value) return;
  busy.value = true; detailsError.value = '';
  try {
    const uploaded = await uploadEmployeeFile({ file, ownerType: 'PERSONAL_FILE' });
    attachments.value = await teamTaskApi.attach(taskId, uploaded.attachmentRefId);
    attachmentsFailed.value = false;
  } catch (error) { await handleDetailError(error); }
  finally { busy.value = false; input.value = ''; }
}
async function removeAttachment() {
  const taskId = detail.value?.taskId; const file = removingAttachment.value;
  if (taskId == null || !file?.canRemove || busy.value) return;
  busy.value = true; detailsError.value = '';
  try { await teamTaskApi.removeAttachment(taskId, file.attachmentRefId); removingAttachment.value = null; await loadAttachments(); }
  catch (error) { await handleDetailError(error); }
  finally { busy.value = false; }
}
async function downloadAttachment(file: TeamTaskAttachmentResponse) {
  const taskId = detail.value?.taskId;
  if (taskId == null || busy.value || file.scanStatus !== 'CLEAN') return;
  busy.value = true; detailsError.value = '';
  try {
    const ticket = await teamTaskApi.attachmentTicket(taskId, file.attachmentRefId);
    if (!ticket.downloadUrl) throw new Error('附件下载地址不可用');
    const response = await fetch(ticket.downloadUrl, { credentials: 'include', headers: window.workforceDesktop ? { 'X-Workforce-Client': 'desktop' } : {} });
    if (!response.ok) throw new Error((await response.json().catch(() => null))?.message || '附件下载失败');
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement('a'); link.href = url; link.download = file.fileName; link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  catch (error) { await handleDetailError(error); }
  finally { busy.value = false; }
}
</script>

<template>
  <div class="tk-workspace">
    <div class="tk-layout" :inert="!!detail || showCreate || confirmingBatchDelete || showListForm || listDeleting">
      <aside v-if="!sidebarCollapsed" class="tk-sidebar" aria-label="任务导航">
        <header class="tk-sidebar-head"><strong>任务</strong><button class="tk-icon-button" type="button" aria-label="收起任务导航" @click="sidebarCollapsed = true"><PanelLeftClose :size="16" /></button></header>
        <div class="tk-sidebar-scroll">
          <button class="tk-nav-item" type="button" :class="{ active: filter === 'today' && !groupFilter }" :aria-current="filter === 'today' && !groupFilter ? 'page' : undefined" :disabled="batchBusy" @click="selectScope('today')"><CalendarDays :size="16" /><span>今日</span></button>
          <button class="tk-nav-item" type="button" :class="{ active: filter === 'mine' && !groupFilter }" :aria-current="filter === 'mine' && !groupFilter ? 'page' : undefined" :disabled="batchBusy" @click="selectScope('mine')"><UsersRound :size="16" /><span>我负责的</span><small v-if="!loading && !loadFailed">{{ mineCount }}</small></button>
          <button class="tk-nav-item" type="button" :class="{ active: filter === 'watched' && !groupFilter }" :aria-current="filter === 'watched' && !groupFilter ? 'page' : undefined" :disabled="batchBusy" @click="selectScope('watched')"><Bookmark :size="16" /><span>我关注的</span></button>
          <div class="tk-nav-heading">快速访问</div>
          <button v-for="tab in tabs" :key="tab.key" class="tk-nav-item" type="button" :class="{ active: filter === tab.key && !groupFilter }" :aria-current="filter === tab.key && !groupFilter ? 'page' : undefined" :disabled="batchBusy" @click="selectScope(tab.key)"><component :is="tab.icon" :size="16" /><span>{{ tab.label }}</span></button>
          <div class="tk-nav-heading tk-list-heading"><span>任务清单</span><button class="tk-icon-button" type="button" aria-label="新建任务清单" @click="openListForm()"><Plus :size="16" /></button></div>
          <div v-if="listsFailed" class="tk-list-error" role="alert">清单加载失败<button type="button" @click="loadLists">重试</button></div>
          <button v-for="group in taskGroups" :key="group" class="tk-nav-item" type="button" :class="{ active: groupFilter === group }" :aria-current="groupFilter === group ? 'page' : undefined" :disabled="batchBusy" @click="selectScope('all', group)"><Folder :size="16" /><span>{{ groupName(group) }}</span></button>
        </div>
      </aside>
      <main class="tk-page">
        <header class="tk-head">
          <button v-if="sidebarCollapsed" class="tk-icon-button" type="button" aria-label="展开任务导航" @click="sidebarCollapsed = false"><PanelLeftOpen :size="18" /></button>
          <h1>{{ pageTitle }}</h1><button v-if="currentList?.canEdit" class="tk-icon-button" type="button" aria-label="重命名清单" @click="openListForm(currentList)"><Pencil :size="16" /></button><button v-if="currentList?.canEdit" class="tk-icon-button" type="button" aria-label="删除清单" @click="listError = ''; listDeleting = true"><Trash2 :size="16" /></button><span class="tk-head-spacer" />
          <HubButton variant="primary" :disabled="batchBusy" @click="openCreate()"><Plus :size="16" />新建任务</HubButton>
        </header>
        <div class="tk-view-switch" aria-label="任务视图">
          <button type="button" :class="{ active: viewMode === 'list' }" :aria-pressed="viewMode === 'list'" :disabled="batchBusy" @click="viewMode = 'list'"><LayoutList :size="16" />列表</button>
          <button type="button" :class="{ active: viewMode === 'lane' }" :aria-pressed="viewMode === 'lane'" :disabled="batchBusy" @click="viewMode = 'lane'"><ChartNoAxesCombined :size="16" />仪表盘</button>
        </div>
        <fieldset class="tk-toolbar" :disabled="batchBusy">
          <span class="tk-scope-label">{{ pageTitle }}</span>
          <details class="tk-menu">
            <summary><SlidersHorizontal :size="14" />筛选<small v-if="statusFilter || assigneeFilter">{{ Number(!!statusFilter) + Number(!!assigneeFilter) }}</small></summary>
            <div class="tk-popover tk-filter-panel">
              <label>任务状态<select v-model="statusFilter" aria-label="任务状态"><option value="">全部状态</option><option v-for="(label, key) in STATUS_LABEL" :key="key" :value="key">{{ label }}</option></select></label>
              <label>负责人<select v-model="assigneeFilter" aria-label="负责人"><option value="">全部负责人</option><option v-for="[id, name] in assignees" :key="id" :value="id">{{ name }}</option></select></label>
              <HubButton size="sm" @click="statusFilter = ''; assigneeFilter = ''">重置</HubButton>
            </div>
          </details>
          <label class="tk-tool-select">排序：<select v-model="sortBy" aria-label="任务排序"><option value="endDate">截止时间</option><option value="startDate">开始时间</option><option value="title">任务标题</option></select></label>
          <label class="tk-tool-select">分组：<select v-model="groupBy" aria-label="任务分组"><option value="none">无分组</option><option value="status">状态</option><option value="assignee">负责人</option></select></label>
          <button v-if="viewMode === 'list'" class="tk-tool-toggle" type="button" :aria-pressed="showSubtasks" @click="showSubtasks = !showSubtasks"><ListTree :size="14" />子任务：{{ showSubtasks ? '展示' : '隐藏' }}</button>
          <details v-if="viewMode === 'list'" class="tk-menu tk-fields-menu"><summary>字段配置<ChevronDown :size="12" /></summary><div class="tk-popover"><label v-for="field in fields" :key="field.key" class="tk-field-option"><input v-model="columns" type="checkbox" :value="field.key" />{{ field.label }}</label></div></details>
          <label class="tk-search"><Search :size="14" /><input v-model="keyword" type="search" aria-label="搜索任务" placeholder="搜索任务" /></label>
        </fieldset>
    <section class="tk-card" :aria-busy="loading">
      <div v-if="loading" class="tk-feedback" role="status">加载中…</div>
      <div v-else-if="loadFailed" class="tk-feedback"><HubEmpty title="任务加载失败" /><HubButton @click="load">重试</HubButton></div>
      <template v-else-if="viewMode === 'lane'">
        <div class="tk-summary"><div><span>未完成</span><strong>{{ counts.pending }}</strong></div><div><span>进行中</span><strong>{{ counts.progress }}</strong></div><div><span>已完成</span><strong>{{ counts.done }}</strong></div></div>
        <div class="tk-week-nav"><HubButton size="sm" aria-label="上一周" @click="weekOffset--"><ChevronLeft :size="16" /></HubButton><strong>{{ weekDays[0].iso }} — {{ weekDays[6].iso }}</strong><HubButton size="sm" aria-label="下一周" @click="weekOffset++"><ChevronRight :size="16" /></HubButton><HubButton size="sm" @click="weekOffset = 0">本周</HubButton></div>
        <div class="tk-lane">
          <div class="tk-lane-header"><div class="tk-lane-name-cell">负责人</div><div v-for="d in weekDays" :key="d.iso" class="tk-lane-head-cell" :class="{ today: d.iso === todayIso }">{{ d.label }}</div></div>
          <div v-for="row in lanes" :key="row.id" class="tk-lane-row"><div class="tk-lane-name-cell">{{ row.name }}</div><div class="tk-lane-area" :style="{ height: `${row.maxLane * 52 + 12}px` }"><div v-for="d in weekDays" :key="d.iso" class="tk-lane-day" :class="{ today: d.iso === todayIso }" aria-hidden="true" /><button v-for="bar in row.bars" :key="String(bar.task.taskId)" type="button" :class="['tk-task-bar', STATUS_CLASS[bar.task.status]]" :title="`${bar.task.title} · ${bar.task.startDate} 至 ${bar.task.endDate}`" :style="{ left: `calc(${bar.startCol / 7 * 100}% + 6px)`, width: `calc(${bar.span / 7 * 100}% - 12px)`, top: `${bar.lane * 52 + 6}px` }" @click="openDetail(bar.task)"><strong>{{ bar.task.title }}</strong><small>{{ STATUS_LABEL[bar.task.status] }}<span v-if="bar.task.subtasks?.length"> · {{ bar.task.subtasks.filter(s => s.completed).length }}/{{ bar.task.subtasks.length }}</span></small></button></div></div>
        </div><HubEmpty v-if="!lanes.length" title="本周暂无任务" />
      </template>
      <template v-else>
        <div class="tk-table-wrap">
          <table class="tk-table" aria-label="团队任务列表">
            <thead><tr>
              <th class="tk-check-cell" scope="col"><input type="checkbox" aria-label="选择本页可操作任务" :checked="allSelected" :indeterminate="someSelected && !allSelected" :disabled="batchBusy || !selectableTasks.length" @change="togglePage" /></th>
              <th class="tk-title-cell" scope="col">任务标题</th>
              <th v-if="columns.includes('creator')" scope="col">派发人</th><th v-if="columns.includes('assignee')" scope="col">负责人</th>
              <th v-if="columns.includes('start')" scope="col">开始时间</th><th v-if="columns.includes('end')" scope="col">截止时间</th>
              <th v-if="columns.includes('status')" scope="col">状态</th><th v-if="columns.includes('group')" scope="col">所属清单</th>
            </tr></thead>
            <tbody>
              <template v-for="group in groupedTasks" :key="group.key">
                <tr v-if="group.label" class="tk-group-row"><th :colspan="columns.length + 2" scope="rowgroup">{{ group.label }}<span>{{ group.items.length }}</span></th></tr>
                <tr v-for="t in group.items" :key="String(t.taskId)" :class="{ selected: selectedIds.includes(String(t.taskId)), completed: t.status === 'DONE' }">
                  <td class="tk-check-cell"><input type="checkbox" :aria-label="`选择任务：${t.title}`" :checked="selectedIds.includes(String(t.taskId))" :disabled="batchBusy || !(t.canComplete || t.canDelete)" @change="toggleTask(t)" /></td>
                  <td class="tk-title-cell"><button class="tk-title-button" type="button" :title="t.title" :disabled="busy || batchBusy" @click="openDetail(t)"><Bookmark v-if="t.watching" :size="13" class="tk-watched-icon" />{{ t.title }}<small v-if="t.subtasks?.length"> {{ t.subtasks.filter(s => s.completed).length }}/{{ t.subtasks.length }}</small></button><div v-if="showSubtasks && t.subtasks?.length" class="tk-subtask-preview"><button v-for="item in t.subtasks" :key="String(item.subtaskId)" type="button" :class="{ completed: item.completed }" @click="openDetail(t)"><Check v-if="item.completed" :size="12" /><span v-else class="tk-subtask-dot" />{{ item.title }}</button></div></td>
                  <td v-if="columns.includes('creator')">{{ t.creatorName || '—' }}</td>
                  <td v-if="columns.includes('assignee')"><span class="tk-person"><span class="tk-person-avatar"><UserRound :size="12" aria-hidden="true" /></span>{{ assigneeName(t) }}</span></td>
                  <td v-if="columns.includes('start')" class="tk-date"><time v-if="t.startDate" :datetime="t.startDate" :title="t.startDate">{{ t.startDate.slice(5) }}</time><span v-else>—</span></td>
                  <td v-if="columns.includes('end')" class="tk-date"><time v-if="t.endDate" :datetime="t.endDate" :title="t.endDate">{{ t.endDate.slice(5) }}</time><span v-else>—</span></td>
                  <td v-if="columns.includes('status')"><span :class="['tk-status', STATUS_CLASS[t.status]]">{{ STATUS_LABEL[t.status] }}</span></td>
                  <td v-if="columns.includes('group')"><span v-if="t.groupId != null" class="tk-list-tag" :title="t.groupName || groupName(String(t.groupId))">{{ t.groupName || groupName(String(t.groupId)) }}</span><span v-else class="tk-date">—</span></td>
                </tr>
              </template>
            </tbody>
          </table>
          <HubEmpty v-if="!visibleTasks.length" title="暂无任务" />
        </div>
        <footer v-if="selectedTasks.length" class="tk-table-footer tk-batch-bar" aria-live="polite"><span>已选 {{ selectedTasks.length }} 项</span><div><HubButton size="sm" variant="ghost" :disabled="batchBusy || !canBatchComplete" @click="runBatch('complete')">完成</HubButton><HubButton size="sm" variant="ghost" class="tk-danger-text" :disabled="batchBusy || !canBatchDelete" @click="confirmingBatchDelete = true">删除</HubButton><HubButton size="sm" variant="ghost" :disabled="batchBusy" @click="selectedIds = []">取消</HubButton></div></footer>
        <footer v-else class="tk-table-footer"><span>共 {{ visibleTasks.length }} 条</span><nav v-if="pageCount > 1" class="tk-pagination" aria-label="任务分页"><HubButton size="sm" variant="ghost" aria-label="上一页" :disabled="page <= 1" @click="page--"><ChevronLeft :size="16" /></HubButton><span>{{ page }} / {{ pageCount }}</span><HubButton size="sm" variant="ghost" aria-label="下一页" :disabled="page >= pageCount" @click="page++"><ChevronRight :size="16" /></HubButton></nav></footer>
      </template>
    </section>
      </main>
    </div>
    <HubDialog v-if="showCreate" :lock="creating || discardChanges" title-id="tk-form-title" @close="closeCreate">
      <form ref="formPanel" class="tk-form" :inert="discardChanges" @submit.prevent="submitCreate" @keydown="trapFocus">
        <h2 id="tk-form-title">{{ editing ? '编辑任务' : '派发任务' }}</h2>
        <fieldset :disabled="creating">
          <label>任务名称<input v-model="form.title" name="title" maxlength="128" required :aria-invalid="!!formErrors.title" :aria-describedby="formErrors.title ? 'tk-title-error' : undefined" @input="delete formErrors.title" /><span v-if="formErrors.title" id="tk-title-error" class="tk-field-error" role="alert">{{ formErrors.title }}</span></label>
          <label>负责人<select v-model="form.assigneeId" name="assigneeId" aria-label="负责人" required :disabled="candidatesLoading || candidatesFailed" :aria-invalid="!!formErrors.assigneeId" :aria-describedby="formErrors.assigneeId ? 'tk-assignee-error' : undefined" @change="delete formErrors.assigneeId"><option value="" disabled>请选择负责人</option><option v-if="editing && !colleagues.some(c => String(c.userId) === String(editing?.assigneeId))" :value="String(editing.assigneeId)">{{ editing.assigneeName || '原负责人' }}</option><option v-for="c in colleagues" :key="String(c.userId)" :value="String(c.userId)">{{ c.realName }}</option></select><span v-if="formErrors.assigneeId" id="tk-assignee-error" class="tk-field-error" role="alert">{{ formErrors.assigneeId }}</span></label>
          <div v-if="candidatesLoading" class="tk-form-notice" role="status">负责人加载中…</div><div v-else-if="candidatesFailed" class="tk-form-notice" role="alert">负责人加载失败 <HubButton size="sm" @click="loadCandidates">重试</HubButton></div><div v-else-if="!colleagues.length" class="tk-form-notice">暂无可派发的负责人</div>
          <div class="tk-form-row"><label>开始日期<input v-model="form.startDate" name="startDate" type="date" required :aria-invalid="!!formErrors.startDate" @input="delete formErrors.startDate" /><span v-if="formErrors.startDate" class="tk-field-error" role="alert">{{ formErrors.startDate }}</span></label><label>结束日期<input v-model="form.endDate" name="endDate" type="date" :min="form.startDate" required :aria-invalid="!!formErrors.endDate" @input="delete formErrors.endDate" /><span v-if="formErrors.endDate" class="tk-field-error" role="alert">{{ formErrors.endDate }}</span></label></div>
          <label>所属清单<select v-model="form.groupId" aria-label="所属清单"><option value="">不添加到清单</option><option v-if="form.groupId && !taskLists.some(l => String(l.listId) === form.groupId && l.canEdit)" :value="form.groupId">{{ groupName(form.groupId) }}</option><option v-for="list in taskLists.filter(l => l.canEdit)" :key="String(list.listId)" :value="String(list.listId)">{{ list.name }}</option></select></label>
          <label>说明<textarea v-model="form.description" maxlength="1024" rows="4" /></label>
        </fieldset>
        <div class="tk-form-actions"><HubButton :disabled="creating" @click="closeCreate">取消</HubButton><HubButton variant="primary" type="submit" :disabled="creating || candidatesLoading || candidatesFailed || !form.assigneeId">{{ creating ? '保存中…' : editing ? '保存' : '派发' }}</HubButton></div>
      </form>
    </HubDialog>
    <HubDialog v-if="discardChanges" title-id="tk-discard-title" @close="discardChanges = false"><div ref="confirmPanel" class="tk-confirm-dialog" @keydown="trapFocus"><h2 id="tk-discard-title">放弃未保存的修改？</h2><div class="tk-form-actions"><HubButton @click="discardChanges = false">继续编辑</HubButton><HubButton variant="danger" @click="showCreate = false">放弃修改</HubButton></div></div></HubDialog>
    <HubDialog v-if="confirmingBatchDelete" :lock="batchBusy" title-id="tk-batch-delete-title" @close="confirmingBatchDelete = false"><div ref="confirmPanel" class="tk-confirm-dialog" @keydown="trapFocus"><h2 id="tk-batch-delete-title">删除选中的 {{ selectedTasks.length }} 项任务？</h2><div class="tk-form-actions"><HubButton :disabled="batchBusy" @click="confirmingBatchDelete = false">取消</HubButton><HubButton variant="danger" :disabled="batchBusy" @click="runBatch('delete')">{{ batchBusy ? '删除中…' : '确认删除' }}</HubButton></div></div></HubDialog>
    <HubDialog v-if="showListForm" :lock="listBusy" title-id="tk-list-title" @close="showListForm = false"><form ref="listPanel" class="tk-form" @submit.prevent="saveList" @keydown="trapFocus"><h2 id="tk-list-title">{{ listEditing ? '重命名清单' : '新建任务清单' }}</h2><label>清单名称<input v-model="listNameInput" maxlength="64" required :disabled="listBusy" /></label><p v-if="listError" class="tk-field-error" role="alert">{{ listError }}</p><div class="tk-form-actions"><HubButton :disabled="listBusy" @click="showListForm = false">取消</HubButton><HubButton variant="primary" type="submit" :disabled="listBusy">{{ listBusy ? '保存中…' : '保存' }}</HubButton></div></form></HubDialog>
    <HubDialog v-if="listDeleting" :lock="listBusy" title-id="tk-list-delete-title" @close="listDeleting = false"><div ref="confirmPanel" class="tk-confirm-dialog" @keydown="trapFocus"><h2 id="tk-list-delete-title">删除清单“{{ currentList?.name }}”？</h2><p v-if="listError" class="tk-field-error" role="alert">{{ listError }}</p><div class="tk-form-actions"><HubButton :disabled="listBusy" @click="listDeleting = false">取消</HubButton><HubButton variant="danger" :disabled="listBusy" @click="deleteList">确认删除</HubButton></div></div></HubDialog>
    <div v-if="detail" class="tk-drawer-overlay" @click.self="closeDetail" @keydown.esc.stop.prevent="closeDetail">
      <aside ref="detailPanel" class="tk-detail" :class="{ expanded: detailExpanded }" role="dialog" aria-modal="true" :aria-label="detail.title" @keydown="trapFocus">
        <header class="tk-detail-toolbar">
          <HubButton v-if="detail.canComplete" :disabled="busy" @click="changeStatus('DONE')"><Check :size="16" />完成任务</HubButton><HubButton v-else-if="detail.canReopen" :disabled="busy" @click="changeStatus('TODO')">重开任务</HubButton><span v-else :class="['tk-status', STATUS_CLASS[detail.status]]">{{ STATUS_LABEL[detail.status] }}</span>
          <span class="tk-head-spacer" /><button class="tk-icon-button" type="button" :aria-label="detail.watching ? '取消关注任务' : '关注任务'" :aria-pressed="!!detail.watching" :disabled="busy" @click="toggleWatch"><Bookmark :size="18" /></button><button class="tk-icon-button" type="button" aria-label="复制任务链接" @click="copyTaskLink"><Link :size="18" /></button><button class="tk-icon-button" type="button" :aria-label="detailExpanded ? '收起详情' : '展开详情'" :aria-pressed="detailExpanded" @click="detailExpanded = !detailExpanded"><Maximize2 :size="17" /></button><button class="tk-icon-button" type="button" aria-label="关闭任务详情" :disabled="busy" @click="closeDetail"><X :size="18" /></button>
        </header>
        <div class="tk-detail-body">
          <div class="tk-detail-title"><span :class="['tk-status', 'tk-detail-status', STATUS_CLASS[detail.status]]">{{ STATUS_LABEL[detail.status] }}</span><h2 id="tk-detail-title"><button v-if="detail.canEdit" type="button" class="tk-editable-title" :disabled="busy" aria-label="编辑任务标题" @click="openCreate(detail)">{{ detail.title }}</button><span v-else>{{ detail.title }}</span></h2></div>
          <dl class="tk-detail-properties"><div><dt><UserRound :size="15" />派发人</dt><dd>{{ detail.creatorName || '—' }}</dd></div><div><dt><UsersRound :size="15" />负责人</dt><dd><button v-if="detail.canEdit" class="tk-inline-edit" type="button" :disabled="busy" aria-label="编辑负责人" @click="openCreate(detail)"><span class="tk-person-avatar"><UserRound :size="12" /></span>{{ assigneeName(detail) }}<ChevronDown :size="12" /></button><span v-else class="tk-person"><span class="tk-person-avatar"><UserRound :size="12" /></span>{{ assigneeName(detail) }}</span></dd></div><div><dt><CalendarDays :size="15" />起止日期</dt><dd><button v-if="detail.canEdit" class="tk-inline-edit" type="button" :disabled="busy" aria-label="编辑起止日期" @click="openCreate(detail)">{{ detail.startDate || '—' }} 至 {{ detail.endDate || '—' }}<ChevronDown :size="12" /></button><span v-else>{{ detail.startDate || '—' }} 至 {{ detail.endDate || '—' }}</span></dd></div><div><dt><Folder :size="15" />所属清单</dt><dd><button v-if="detail.canEdit" class="tk-inline-edit" type="button" :disabled="busy" aria-label="编辑所属清单" @click="openCreate(detail)">{{ detail.groupId == null ? '添加至任务清单' : detail.groupName || groupName(String(detail.groupId)) }}<ChevronDown :size="12" /></button><span v-else>{{ detail.groupId == null ? '—' : detail.groupName || groupName(String(detail.groupId)) }}</span></dd></div></dl>
          <section class="tk-description"><header class="tk-section-head"><h3>描述</h3><button v-if="detail.canEdit" class="tk-icon-button" type="button" :disabled="busy" aria-label="编辑任务描述" @click="openCreate(detail)"><Pencil :size="14" /></button></header><p v-if="detail.description">{{ detail.description }}</p><button v-else-if="detail.canEdit" class="tk-add-action" type="button" :disabled="busy" @click="openCreate(detail)"><Plus :size="15" />添加描述</button><span v-else class="tk-muted">暂无描述</span></section>
          <section class="tk-detail-section"><header class="tk-section-head"><h3><ListTree :size="16" />子任务</h3><span class="tk-muted">{{ completedSubtasks }} / {{ detailSubtasks.length }} 已完成</span></header><progress v-if="detailSubtasks.length" class="tk-subtask-progress" :value="completedSubtasks" :max="detailSubtasks.length" :aria-label="`子任务完成进度：${completedSubtasks}/${detailSubtasks.length}`" />
            <ul class="tk-subtasks"><li v-for="item in detailSubtasks" :key="String(item.subtaskId)" :class="{ completed: item.completed }"><input type="checkbox" :checked="item.completed" :aria-label="`完成子任务：${item.title}`" :disabled="busy || String(detail.assigneeId) !== String(session.user?.userId)" @change="toggleSubtask(item)" /><span>{{ item.title }}</span><button v-if="detail.canEdit" class="tk-icon-button" type="button" :disabled="busy" :aria-label="`编辑子任务：${item.title}`" @click="startSubtaskEdit(item)"><Pencil :size="14" /></button><button v-if="detail.canEdit" class="tk-icon-button" type="button" :disabled="busy" :aria-label="`删除子任务：${item.title}`" @click="removingSubtask = item"><Trash2 :size="14" /></button></li></ul>
            <div v-if="removingSubtask" class="tk-confirm"><span>删除子任务“{{ removingSubtask.title }}”？</span><HubButton size="sm" :disabled="busy" @click="removingSubtask = null">取消</HubButton><HubButton size="sm" variant="danger" :disabled="busy" @click="deleteSubtask">确认删除</HubButton></div>
            <form v-if="detail.canEdit" class="tk-subtask-form" @submit.prevent="saveSubtask"><Plus :size="16" /><input v-model="subtaskTitle" aria-label="子任务标题" :placeholder="editingSubtask ? '编辑子任务' : '添加子任务'" maxlength="128" :disabled="busy" required /><HubButton v-if="editingSubtask" size="sm" variant="ghost" :disabled="busy" @click="editingSubtask = null; subtaskTitle = ''">取消</HubButton><HubButton size="sm" :disabled="busy || !subtaskTitle.trim()" type="submit">{{ editingSubtask ? '保存' : '添加' }}</HubButton></form><p v-else-if="!detailSubtasks.length" class="tk-muted">暂无子任务</p>
          </section>
          <section class="tk-detail-section"><header class="tk-section-head"><h3><Paperclip :size="16" />附件<span v-if="attachments.length">{{ attachments.length }}</span></h3><HubButton size="sm" variant="ghost" :disabled="busy" @click="attachmentInput?.click()"><Plus :size="14" />添加附件</HubButton><input ref="attachmentInput" type="file" class="tk-hidden-input" tabindex="-1" aria-label="上传任务附件" :disabled="busy" @change="addAttachment" /></header><p v-if="attachmentsLoading" class="tk-muted" role="status">附件加载中…</p><div v-else-if="attachmentsFailed" class="tk-attachment-error" role="alert">附件加载失败<HubButton size="sm" @click="loadAttachments">重试</HubButton></div><ul v-else class="tk-attachments"><li v-for="file in attachments" :key="String(file.attachmentRefId)"><FileText :size="18" /><div><strong>{{ file.fileName }}</strong><small>{{ (file.fileSize / 1024).toFixed(1) }} KB<span v-if="file.scanStatus !== 'CLEAN'"> · {{ file.scanStatus === 'FAILED' ? '安全检查未通过' : '安全检查中' }}</span></small></div><button class="tk-icon-button" type="button" :aria-label="`下载附件：${file.fileName}`" :disabled="busy || file.scanStatus !== 'CLEAN'" @click="downloadAttachment(file)"><Download :size="16" /></button><button v-if="file.canRemove" class="tk-icon-button" type="button" :aria-label="`移除附件：${file.fileName}`" :disabled="busy" @click="removingAttachment = file"><X :size="16" /></button></li></ul><span v-if="!attachmentsLoading && !attachmentsFailed && !attachments.length" class="tk-muted">暂无附件</span><div v-if="removingAttachment" class="tk-confirm"><span>从任务中移除“{{ removingAttachment.fileName }}”？</span><HubButton size="sm" :disabled="busy" @click="removingAttachment = null">取消</HubButton><HubButton size="sm" variant="danger" :disabled="busy" @click="removeAttachment">确认移除</HubButton></div></section>
          <section class="tk-detail-section"><header class="tk-section-head"><h3><Bookmark :size="16" />关注人</h3><button class="tk-text-action" type="button" :disabled="busy" @click="toggleWatch">{{ detail.watching ? '取消关注' : '关注任务' }}</button></header><div class="tk-watchers"><span v-for="(name, index) in detail.watcherNames || []" :key="index" class="tk-person"><span class="tk-person-avatar"><UserRound :size="12" /></span>{{ name }}</span><span v-if="!detail.watcherNames?.length" class="tk-muted">暂无关注人</span></div></section>
          <section class="tk-detail-section"><nav class="tk-activity-tabs" aria-label="任务记录类型"><button type="button" :class="{ active: activityTab === 'all' }" :aria-pressed="activityTab === 'all'" @click="activityTab = 'all'">全部记录</button><button type="button" :class="{ active: activityTab === 'comments' }" :aria-pressed="activityTab === 'comments'" @click="activityTab = 'comments'"><MessageSquare :size="14" />评论</button><button type="button" :class="{ active: activityTab === 'history' }" :aria-pressed="activityTab === 'history'" @click="activityTab = 'history'"><Clock3 :size="14" />历史记录</button></nav><ol class="tk-activity-list"><li v-for="item in detailActivities" :key="String(item.activityId)" :class="{ comment: item.kind === 'COMMENT' }"><span class="tk-activity-dot" /><div><header><strong>{{ item.actorName || '—' }}</strong><time :datetime="item.createdAt">{{ item.createdAt.replace('T', ' ').slice(0, 16) }}</time></header><p>{{ item.content }}</p></div></li><li v-if="!detail.activities?.length && detail.createdAt && activityTab !== 'comments'"><span class="tk-activity-dot" /><div><header><strong>{{ detail.creatorName || '—' }}</strong><time>{{ detail.createdAt.replace('T', ' ').slice(0, 16) }}</time></header><p>创建了任务</p></div></li></ol><p v-if="!detailActivities.length && (detail.activities?.length || !detail.createdAt || activityTab === 'comments')" class="tk-muted">{{ activityTab === 'comments' ? '暂无评论' : '暂无记录' }}</p></section>
        </div>
        <p v-if="detailsError" class="tk-detail-error tk-field-error" role="alert">{{ detailsError }}</p>
        <form class="tk-comment-form" @submit.prevent="postComment"><textarea v-model="commentText" aria-label="任务评论" placeholder="输入评论" rows="2" maxlength="2000" :disabled="busy" @keydown.ctrl.enter.prevent="postComment" @keydown.meta.enter.prevent="postComment" /><HubButton variant="primary" type="submit" :disabled="busy || !commentText.trim()"><Send :size="15" />发送</HubButton></form>
        <div v-if="confirmingDelete && detail.canDelete" class="tk-confirm"><span>确认删除该任务？</span><HubButton size="sm" :disabled="busy" @click="confirmingDelete = false">取消</HubButton><HubButton size="sm" variant="danger" :disabled="busy" @click="deleteTask">确认删除</HubButton></div>
        <footer v-if="detail.canEdit || detail.canDelete || detail.canStart" class="tk-detail-actions"><HubButton v-if="detail.canDelete" variant="ghost" class="tk-danger-text" :disabled="busy" @click="confirmingDelete = true"><Trash2 :size="15" />删除</HubButton><span class="tk-head-spacer" /><HubButton v-if="detail.canEdit" :disabled="busy" @click="openCreate(detail)"><Pencil :size="15" />编辑</HubButton><HubButton v-if="detail.canStart" :disabled="busy" @click="changeStatus('IN_PROGRESS')">开始任务</HubButton></footer>
      </aside>
    </div>
  </div>
</template>
<style scoped src="../styles/tasks.css" />
