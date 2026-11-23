<script setup lang="ts">
/**
 * 团队任务：按负责人 × 当前周的时间泳道，任务为横向色条；支持列表视图、新建与详情弹层。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { Plus, LayoutList, ChartNoAxesCombined, ChevronLeft, ChevronRight, ChevronDown, CalendarDays, UsersRound, Send, CircleCheck, Folder, PanelLeftClose, PanelLeftOpen, SlidersHorizontal, Search, X, UserRound } from 'lucide-vue-next';
import { teamTaskApi } from '@/api';
import { ApiError } from '@/api/http';
import type { TeamTaskResponse } from '@/api/types';
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
const filter = ref<'all' | 'mine' | 'created' | 'today' | 'done'>('all');
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
onMounted(() => Promise.all([load(), session.user ? Promise.resolve() : session.loadCurrentUser()]));
const assigneeName = (t: TeamTaskResponse) => t.assigneeName || '—';
const mineCount = computed(() => tasks.value.filter(t => String(t.assigneeId) === String(session.user?.userId) && t.status !== 'DONE').length);
const taskGroups = computed(() => [...new Set(tasks.value.filter(t => t.groupId != null).map(t => String(t.groupId)))].sort());
const assignees = computed(() => [...new Map(tasks.value.map(t => [String(t.assigneeId), assigneeName(t)])).entries()]);
const pageTitle = computed(() => groupFilter.value ? `清单 #${groupFilter.value}` : filter.value === 'today' ? '今日' : filter.value === 'mine' ? '我负责的' : tabs.find(t => t.key === filter.value)?.label || '全部任务');
const scopedTasks = computed(() => tasks.value.filter(t => {
  if (groupFilter.value && String(t.groupId) !== groupFilter.value) return false;
  if (filter.value === 'today') return t.status !== 'DONE' && !!t.startDate && !!t.endDate && t.startDate <= todayIso && t.endDate >= todayIso;
  if (filter.value === 'done') return t.status === 'DONE';
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
const form = ref({ title: '', description: '', assigneeId: '', startDate: todayIso, endDate: todayIso });
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
  else { discardChanges.value = false; returnFocus?.focus(); }
});
watch([discardChanges, confirmingBatchDelete], async ([discard, batch]) => {
  await nextTick();
  if (discard || batch) confirmPanel.value?.querySelector<HTMLButtonElement>('button')?.focus();
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
  editing.value = task; detail.value = null;
  form.value = { title: task?.title || '', description: task?.description || '', assigneeId: task ? String(task.assigneeId) : '', startDate: task?.startDate || todayIso, endDate: task?.endDate || todayIso };
  initialForm = JSON.stringify(form.value);
  formErrors.value = {};
  showCreate.value = true; void loadCandidates();
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
    const payload = { ...form.value, title: form.value.title.trim(), description: form.value.description.trim(), groupId: editing.value?.groupId == null ? (groupFilter.value || undefined) : String(editing.value.groupId) };
    if (editing.value) await teamTaskApi.update(editing.value.taskId, payload);
    else await teamTaskApi.create(payload);
    showToast(editing.value ? '任务已保存' : '任务已派发'); showCreate.value = false; await load();
  } catch (error) { await handleError(error); }
  finally { creating.value = false; }
}

/* 详情弹层 */
const detail = ref<TeamTaskResponse | null>(null);
watch(detail, async task => {
  await nextTick();
  if (task) detailPanel.value?.querySelector<HTMLButtonElement>('button')?.focus();
  else if (!showCreate.value) returnFocus?.focus();
});
const confirmingDelete = ref(false);
const busy = ref(false);
async function openDetail(t: TeamTaskResponse) {
  if (busy.value) return;
  if (typeof document !== 'undefined') returnFocus = document.activeElement as HTMLElement;
  busy.value = true; confirmingDelete.value = false;
  try { detail.value = await teamTaskApi.detail(t.taskId); }
  catch (error) { await handleError(error); }
  finally { busy.value = false; }
}
async function handleError(error: unknown) {
  showToast(error instanceof Error ? error.message : '操作失败');
  if (error instanceof ApiError && [403, 404, 409].includes(error.status || 0)) {
    detail.value = null; showCreate.value = false; confirmingDelete.value = false; await load();
  }
}
async function changeStatus(status: 'TODO' | 'IN_PROGRESS' | 'DONE') {
  const t = detail.value;
  if (!t || busy.value || !(status === 'DONE' ? t.canComplete : status === 'IN_PROGRESS' ? t.canStart : t.canReopen)) return;
  busy.value = true;
  try { await teamTaskApi.updateStatus(t.taskId, status); detail.value = null; showToast(status === 'DONE' ? '任务已完成' : status === 'TODO' ? '任务已重开' : '任务已开始'); await load(); }
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
</script>

<template>
  <div class="tk-workspace">
    <div class="tk-layout" :inert="!!detail || showCreate || confirmingBatchDelete">
      <aside v-if="!sidebarCollapsed" class="tk-sidebar" aria-label="任务导航">
        <header class="tk-sidebar-head"><strong>任务</strong><button class="tk-icon-button" type="button" aria-label="收起任务导航" @click="sidebarCollapsed = true"><PanelLeftClose :size="16" /></button></header>
        <div class="tk-sidebar-scroll">
          <button class="tk-nav-item" type="button" :class="{ active: filter === 'today' && !groupFilter }" :aria-current="filter === 'today' && !groupFilter ? 'page' : undefined" :disabled="batchBusy" @click="selectScope('today')"><CalendarDays :size="16" /><span>今日</span></button>
          <button class="tk-nav-item" type="button" :class="{ active: filter === 'mine' && !groupFilter }" :aria-current="filter === 'mine' && !groupFilter ? 'page' : undefined" :disabled="batchBusy" @click="selectScope('mine')"><UsersRound :size="16" /><span>我负责的</span><small v-if="!loading && !loadFailed">{{ mineCount }}</small></button>
          <div class="tk-nav-heading">快速访问</div>
          <button v-for="tab in tabs" :key="tab.key" class="tk-nav-item" type="button" :class="{ active: filter === tab.key && !groupFilter }" :aria-current="filter === tab.key && !groupFilter ? 'page' : undefined" :disabled="batchBusy" @click="selectScope(tab.key)"><component :is="tab.icon" :size="16" /><span>{{ tab.label }}</span></button>
          <template v-if="taskGroups.length">
            <div class="tk-nav-heading">任务清单</div>
            <button v-for="group in taskGroups" :key="group" class="tk-nav-item" type="button" :class="{ active: groupFilter === group }" :aria-current="groupFilter === group ? 'page' : undefined" :disabled="batchBusy" @click="selectScope('all', group)"><Folder :size="16" /><span>清单 #{{ group }}</span></button>
          </template>
        </div>
      </aside>
      <main class="tk-page">
        <header class="tk-head">
          <button v-if="sidebarCollapsed" class="tk-icon-button" type="button" aria-label="展开任务导航" @click="sidebarCollapsed = false"><PanelLeftOpen :size="18" /></button>
          <h1>{{ pageTitle }}</h1><span class="tk-head-spacer" />
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
          <div v-for="row in lanes" :key="row.id" class="tk-lane-row"><div class="tk-lane-name-cell">{{ row.name }}</div><div class="tk-lane-area" :style="{ height: `${row.maxLane * 52 + 12}px` }"><div v-for="d in weekDays" :key="d.iso" class="tk-lane-day" :class="{ today: d.iso === todayIso }" /><button v-for="bar in row.bars" :key="String(bar.task.taskId)" type="button" :class="['tk-task-bar', STATUS_CLASS[bar.task.status]]" :style="{ left: `${bar.startCol / 7 * 100}%`, width: `${bar.span / 7 * 100}%`, top: `${bar.lane * 52 + 6}px` }" @click="openDetail(bar.task)"><strong>{{ bar.task.title }}</strong><small>{{ STATUS_LABEL[bar.task.status] }}</small></button></div></div>
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
                  <td class="tk-title-cell"><button class="tk-title-button" type="button" :title="t.title" :disabled="busy || batchBusy" @click="openDetail(t)">{{ t.title }}</button></td>
                  <td v-if="columns.includes('creator')">{{ t.creatorName || '—' }}</td>
                  <td v-if="columns.includes('assignee')"><span class="tk-person"><span class="tk-person-avatar"><UserRound :size="12" aria-hidden="true" /></span>{{ assigneeName(t) }}</span></td>
                  <td v-if="columns.includes('start')" class="tk-date"><time v-if="t.startDate" :datetime="t.startDate" :title="t.startDate">{{ t.startDate.slice(5) }}</time><span v-else>—</span></td>
                  <td v-if="columns.includes('end')" class="tk-date"><time v-if="t.endDate" :datetime="t.endDate" :title="t.endDate">{{ t.endDate.slice(5) }}</time><span v-else>—</span></td>
                  <td v-if="columns.includes('status')"><span :class="['tk-status', STATUS_CLASS[t.status]]">{{ STATUS_LABEL[t.status] }}</span></td>
                  <td v-if="columns.includes('group')"><span v-if="t.groupId != null" class="tk-list-tag" :title="`清单 #${t.groupId}`">#{{ t.groupId }}</span><span v-else class="tk-date">—</span></td>
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
          <label>说明<textarea v-model="form.description" maxlength="1024" rows="4" /></label>
        </fieldset>
        <div class="tk-form-actions"><HubButton :disabled="creating" @click="closeCreate">取消</HubButton><HubButton variant="primary" type="submit" :disabled="creating || candidatesLoading || candidatesFailed || !form.assigneeId">{{ creating ? '保存中…' : editing ? '保存' : '派发' }}</HubButton></div>
      </form>
    </HubDialog>
    <HubDialog v-if="discardChanges" title-id="tk-discard-title" @close="discardChanges = false"><div ref="confirmPanel" class="tk-confirm-dialog" @keydown="trapFocus"><h2 id="tk-discard-title">放弃未保存的修改？</h2><div class="tk-form-actions"><HubButton @click="discardChanges = false">继续编辑</HubButton><HubButton variant="danger" @click="showCreate = false">放弃修改</HubButton></div></div></HubDialog>
    <HubDialog v-if="confirmingBatchDelete" :lock="batchBusy" title-id="tk-batch-delete-title" @close="confirmingBatchDelete = false"><div ref="confirmPanel" class="tk-confirm-dialog" @keydown="trapFocus"><h2 id="tk-batch-delete-title">删除选中的 {{ selectedTasks.length }} 项任务？</h2><div class="tk-form-actions"><HubButton :disabled="batchBusy" @click="confirmingBatchDelete = false">取消</HubButton><HubButton variant="danger" :disabled="batchBusy" @click="runBatch('delete')">{{ batchBusy ? '删除中…' : '确认删除' }}</HubButton></div></div></HubDialog>
    <div v-if="detail" class="tk-drawer-overlay" @click.self="closeDetail" @keydown.esc.stop.prevent="closeDetail">
      <aside ref="detailPanel" class="tk-detail" role="dialog" aria-modal="true" aria-labelledby="tk-detail-title" @keydown="trapFocus">
        <header class="tk-detail-head"><div><span :class="['tk-status', 'tk-detail-status', STATUS_CLASS[detail.status]]">{{ STATUS_LABEL[detail.status] }}</span><button class="tk-icon-button" type="button" aria-label="关闭任务详情" :disabled="busy" @click="closeDetail"><X :size="18" /></button></div><h2 id="tk-detail-title">{{ detail.title }}</h2></header>
        <div class="tk-detail-body"><dl><div><dt>派发人</dt><dd>{{ detail.creatorName || '—' }}</dd></div><div><dt>负责人</dt><dd>{{ assigneeName(detail) }}</dd></div><div><dt>起止日期</dt><dd>{{ detail.startDate || '—' }} 至 {{ detail.endDate || '—' }}</dd></div><div v-if="detail.groupId != null"><dt>所属清单</dt><dd><span class="tk-list-tag">#{{ detail.groupId }}</span></dd></div></dl><section v-if="detail.description" class="tk-description"><h3>说明</h3><p>{{ detail.description }}</p></section></div>
        <div v-if="confirmingDelete && detail.canDelete" class="tk-confirm"><span>确认删除该任务？</span><HubButton size="sm" :disabled="busy" @click="confirmingDelete = false">取消</HubButton><HubButton size="sm" variant="danger" :disabled="busy" @click="deleteTask">确认删除</HubButton></div>
        <footer class="tk-detail-actions"><HubButton v-if="detail.canDelete" variant="danger" :disabled="busy" @click="confirmingDelete = true">删除</HubButton><HubButton v-if="detail.canEdit" :disabled="busy" @click="openCreate(detail)">编辑</HubButton><span class="tk-head-spacer" /><HubButton v-if="detail.canReopen" :disabled="busy" @click="changeStatus('TODO')">重开任务</HubButton><HubButton v-if="detail.canStart" :disabled="busy" @click="changeStatus('IN_PROGRESS')">开始任务</HubButton><HubButton v-if="detail.canComplete" variant="primary" :disabled="busy" @click="changeStatus('DONE')">完成任务</HubButton></footer>
      </aside>
    </div>
  </div>
</template>
<style scoped src="../styles/tasks.css" />
