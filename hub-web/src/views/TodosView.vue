<script setup lang="ts">
/**
 * 我的待办：分类 × 本周泳道 / 月历 / 列表三种视图；完成、重开、删除与新建。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Plus, LayoutList, CalendarDays, ChevronLeft, ChevronRight, ChevronDown, Check, Search, X, Pencil, LoaderCircle, Inbox, PanelLeftClose, PanelLeftOpen, SlidersHorizontal, Clock3, Bell } from 'lucide-vue-next';
import { personalTodoApi } from '@/api';
import { ApiError } from '@/api/http';
import type { PersonalTodoResponse } from '@/api/types';
import { useToast } from '@/composables/useToast';
import HubButton from '@/components/base/HubButton.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';
import TodoEditor, { type TodoDraft } from './TodoEditor.vue';

const { show: showToast } = useToast();
const todos = ref<PersonalTodoResponse[]>([]);
const loading = ref(true);
const loadFailed = ref(false);
const filter = ref<'all' | 'overdue' | 'today' | 'later' | 'unscheduled' | 'done'>('today');
const viewMode = ref<'week' | 'list'>('list');
const categoryFilter = ref('');
const search = ref('');
const searchOpen = ref(false);
const searchInput = ref<HTMLInputElement | null>(null);
const sidebarCollapsed = ref(false);
const collapsedGroups = ref(new Set<string>());
const showCompleted = ref(false);
const sort = ref('time');
const pendingIds = ref(new Set<string>());
const categories = [{ key: 'TASK', label: '任务', color: 'task' }, { key: 'MEETING', label: '会议', color: 'meeting' }, { key: 'REMINDER', label: '提醒', color: 'reminder' }, { key: 'APPROVAL', label: '审批', color: 'approval' }] as const;
const categoryName = (category: string) => categories.find(c => c.key === category)?.label || '任务';
const categoryClass = (category: string) => categories.find(c => c.key === category)?.color || 'task';
const tabs = [{ key: 'all', label: '全部待办' }, { key: 'overdue', label: '逾期' }, { key: 'today', label: '今天' }, { key: 'later', label: '稍后' }, { key: 'unscheduled', label: '待安排' }, { key: 'done', label: '已完成' }] as const;
const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日'];
const now = ref(new Date());
let clock: ReturnType<typeof setInterval> | undefined;
function toIso(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function dayOf(t: PersonalTodoResponse) { return (t.dueAt || t.startAt || '').slice(0, 10); }
const todayIso = computed(() => toIso(now.value));
const todayLabel = computed(() => `${now.value.getMonth() + 1}月${now.value.getDate()}日 · 星期${WEEK_LABELS[(now.value.getDay() + 6) % 7]}`);
async function load() {
  loading.value = true; loadFailed.value = false;
  try { todos.value = await personalTodoApi.list(); }
  catch (error) { todos.value = []; loadFailed.value = true; showToast(error instanceof Error ? error.message : '待办加载失败'); }
  finally { loading.value = false; }
}
function openLinkedTodo() {
  const id = new URLSearchParams(location.hash.split('?')[1] || '').get('todoId');
  if (!id) return;
  const todo = todos.value.find(t => String(t.todoId) === id);
  if (todo) openDetail(todo);
  else showToast('待办不存在或已删除');
}
onMounted(async () => {
  await load(); openLinkedTodo();
  clock = setInterval(() => { now.value = new Date(); }, 60000);
  window.addEventListener('hashchange', openLinkedTodo);
});
onBeforeUnmount(() => { if (clock) clearInterval(clock); window.removeEventListener('hashchange', openLinkedTodo); });
function groupOf(t: PersonalTodoResponse) {
  if (t.status === 'DONE') return 'done';
  const day = dayOf(t);
  if (!day) return 'unscheduled';
  const start = (t.startAt || t.dueAt || '').slice(0, 10);
  return day < todayIso.value ? 'overdue' : start <= todayIso.value ? 'today' : 'later';
}
const matchedTodos = computed(() => todos.value.filter(t => (!categoryFilter.value || t.category === categoryFilter.value)
  && `${t.title}\n${t.note || ''}`.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase()))
  .sort((a, b) => sort.value === 'title' ? a.title.localeCompare(b.title, 'zh-CN') : (a.startAt || a.dueAt || '9999').localeCompare(b.startAt || b.dueAt || '9999') || String(a.todoId).localeCompare(String(b.todoId))));
const visibleTodos = computed(() => matchedTodos.value.filter(t => filter.value === 'done' ? t.status === 'DONE'
  : t.status === 'PENDING' && (filter.value === 'all' || (filter.value === 'today' ? ['today', 'overdue', 'unscheduled'].includes(groupOf(t)) : groupOf(t) === filter.value))));
const counts = computed(() => Object.fromEntries(tabs.map(t => [t.key, todos.value.filter(item => t.key === 'all' ? item.status === 'PENDING' : groupOf(item) === t.key).length])));
const categoryCounts = computed(() => Object.fromEntries(categories.map(c => [c.key, todos.value.filter(t => t.category === c.key && t.status === 'PENDING').length])));
const pageTitle = computed(() => viewMode.value === 'week' ? '预览' : categoryFilter.value ? categoryName(categoryFilter.value) : filter.value === 'today' ? '今天' : filter.value === 'unscheduled' ? '收集箱' : tabs.find(t => t.key === filter.value)?.label || '全部待办');
function selectScope(scope: typeof filter.value, mode: typeof viewMode.value = 'list') { filter.value = scope; viewMode.value = mode; categoryFilter.value = ''; }
function selectCategory(category: string) { categoryFilter.value = categoryFilter.value === category ? '' : category; filter.value = 'all'; }
async function openSearch() { searchOpen.value = true; await nextTick(); searchInput.value?.focus(); }

/* 泳道：按分类行 × 日期列 */
/* 列表：按分类分组 */
const groupedTodos = computed(() => tabs.filter(t => t.key !== 'all').map(t => ({ key: t.key, label: t.key === 'today' ? '已安排' : t.key === 'unscheduled' ? '待安排' : t.label,
  items: visibleTodos.value.filter(item => groupOf(item) === t.key) })).filter(g => g.items.length || !search.value.trim() && filter.value === 'today' && ['today', 'unscheduled'].includes(g.key)));
const completedTodos = computed(() => matchedTodos.value.filter(t => t.status === 'DONE'));
function toggleGroup(key: string) {
  if (collapsedGroups.value.has(key)) collapsedGroups.value.delete(key);
  else collapsedGroups.value.add(key);
}
const emptyDraft = (): TodoDraft => ({ title: '', category: (categoryFilter.value || 'TASK') as PersonalTodoResponse['category'], startAt: '', dueAt: '', note: '', allDay: false, reminderMinutes: null });
const quickOpen = ref(false);
const quickGroup = ref('unscheduled');
const quickForm = ref<TodoDraft>(emptyDraft());
const quickTitle = computed({ get: () => quickForm.value.title, set: (value: string) => { quickForm.value.title = value; } });
const quickCreating = ref(false);
const quickError = ref('');
const quickDiscarding = ref(false);
let quickInitial = '';
async function openQuick(group = 'unscheduled') {
  viewMode.value = 'list';
  if (filter.value === 'done') filter.value = 'today';
  if (!quickOpen.value) {
    quickForm.value = emptyDraft(); quickError.value = ''; quickGroup.value = group;
    if (group === 'today') {
      const start = new Date(now.value); start.setMinutes(Math.min(59, start.getMinutes() + 1), 0, 0);
      quickForm.value.startAt = `${toIso(start)}T${String(start.getHours()).padStart(2, '0')}:${String(start.getMinutes()).padStart(2, '0')}`;
    }
    quickInitial = JSON.stringify(quickForm.value); quickOpen.value = true;
  }
  collapsedGroups.value.delete(quickGroup.value);
  await nextTick();
  document.querySelector<HTMLInputElement>('.td-quick-form input')?.focus();
}
function closeQuick() {
  if (quickCreating.value) return;
  if (JSON.stringify(quickForm.value) !== quickInitial && (quickForm.value.title || quickForm.value.note)) quickDiscarding.value = true;
  else quickOpen.value = false;
}
function payload(draft: TodoDraft) {
  if (!draft.title.trim()) throw new Error('请填写待办标题');
  if (draft.startAt && draft.dueAt && draft.startAt > draft.dueAt) throw new Error('开始时间不能晚于截止时间');
  if ((draft.allDay || draft.reminderMinutes != null) && !draft.startAt && !draft.dueAt) throw new Error('请先选择日期与时间');
  // datetime-local 值为分钟精度（yyyy-MM-ddTHH:mm），后端 LocalDateTime 反序列化要求带秒，补 ":00"。
  const date = (value: string, end: boolean) => !value ? undefined : draft.allDay ? value.slice(0, 10) + (end ? ' 23:59:59' : ' 00:00:00') : value.slice(0, 16).replace('T', ' ') + ':00';
  return { title: draft.title.trim(), category: draft.category, startAt: date(draft.startAt, false), dueAt: date(draft.dueAt, true), note: draft.note.trim() || undefined,
    allDay: draft.allDay || undefined, reminderMinutes: draft.reminderMinutes ?? undefined };
}
async function submitQuick() {
  if (quickCreating.value || !quickTitle.value.trim()) return;
  quickCreating.value = true; quickError.value = '';
  try {
    const created = await personalTodoApi.create(payload(quickForm.value));
    todos.value.push(created); quickForm.value = emptyDraft(); quickOpen.value = false; quickDiscarding.value = false;
    if (filter.value !== 'all' && !['today', 'unscheduled', 'overdue'].includes(groupOf(created))) filter.value = 'all';
    showToast('待办已添加');
  } catch (error) { quickError.value = error instanceof Error ? error.message : '添加失败'; }
  finally { quickCreating.value = false; }
}

/* 月历：当月网格 */
const weekOffset = ref(0);
const weekDays = computed(() => {
  const monday = new Date(now.value); monday.setDate(monday.getDate() - (monday.getDay() + 6) % 7 + weekOffset.value * 7); monday.setHours(0, 0, 0, 0);
  return Array.from({ length: 7 }, (_, i) => { const d = new Date(monday); d.setDate(d.getDate() + i); return { iso: toIso(d), num: d.getDate(), label: '周' + WEEK_LABELS[i] }; });
});
const monthLabel = computed(() => `${weekDays.value[0].iso.slice(0, 4)}年${Number(weekDays.value[0].iso.slice(5, 7))}月`);
function jumpToDate(value: string) {
  const target = new Date(value + 'T12:00:00');
  if (!Number.isFinite(+target)) return;
  target.setDate(target.getDate() - (target.getDay() + 6) % 7);
  weekOffset.value += Math.round((+target - +new Date(weekDays.value[0].iso + 'T12:00:00')) / (7 * 86400000));
}
const calendarTodos = computed(() => matchedTodos.value.filter(t => filter.value === 'done' ? t.status === 'DONE' : t.status === 'PENDING' || showCompleted.value));
interface TimeBlock { todo: PersonalTodoResponse; start: number; end: number; column: number; columns: number }
const schedule = computed(() => weekDays.value.map(day => {
  const allDay = calendarTodos.value.filter(t => t.allDay && !!dayOf(t) && (t.startAt || t.dueAt)!.slice(0, 10) <= day.iso && (t.dueAt || t.startAt)!.slice(0, 10) >= day.iso);
  const blocks: TimeBlock[] = [];
  for (const todo of calendarTodos.value.filter(t => !t.allDay)) {
    const from = todo.startAt || todo.dueAt;
    if (!from) continue;
    const start = new Date(from.replace(' ', 'T'));
    let end = todo.startAt && todo.dueAt ? new Date(todo.dueAt.replace(' ', 'T')) : new Date(start.getTime() + 30 * 60000);
    if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end < start) continue;
    if (!todo.startAt || !todo.dueAt || +end === +start) {
      end = new Date(+start + 30 * 60000);
      const boundary = new Date(start); boundary.setHours(24, 0, 0, 0);
      if (end > boundary) end = boundary;
    }
    const midnight = new Date(day.iso + 'T00:00:00'); const next = new Date(midnight); next.setDate(next.getDate() + 1);
    if (start >= next || end <= midnight) continue;
    const top = start < midnight ? 0 : start.getHours() * 60 + start.getMinutes();
    const bottom = end >= next ? 1440 : end.getHours() * 60 + end.getMinutes();
    const visualStart = Math.min(top, 1410);
    blocks.push({ todo, start: visualStart, end: Math.min(1440, Math.max(bottom, visualStart + 30)), column: 0, columns: 1 });
  }
  blocks.sort((a, b) => a.start - b.start || b.end - a.end || String(a.todo.todoId).localeCompare(String(b.todo.todoId)));
  let cluster: TimeBlock[] = [], until = 0;
  const place = () => { const lanes: number[] = []; for (const b of cluster) { let lane = lanes.findIndex(end => end <= b.start); if (lane < 0) lane = lanes.length; b.column = lane; lanes[lane] = b.end; } cluster.forEach(b => { b.columns = lanes.length; }); };
  for (const b of blocks) { if (cluster.length && b.start >= until) { place(); cluster = []; } cluster.push(b); until = Math.max(cluster.length === 1 ? 0 : until, b.end); }
  place();
  return { ...day, allDay, blocks };
}));
const startHour = computed(() => Math.min(8, ...schedule.value.flatMap(d => d.blocks.map(b => Math.floor(b.start / 60)))));
const endHour = computed(() => Math.max(18, ...schedule.value.flatMap(d => d.blocks.map(b => Math.ceil(b.end / 60)))));
const hours = computed(() => Array.from({ length: endHour.value - startHour.value }, (_, i) => startHour.value + i));
const nowPosition = computed(() => (now.value.getHours() * 60 + now.value.getMinutes() - startHour.value * 60) / 60 * 64);
const nowVisible = computed(() => weekDays.value.some(d => d.iso === todayIso.value) && nowPosition.value >= 0 && nowPosition.value < hours.value.length * 64);
const nowLabel = computed(() => `${String(now.value.getHours()).padStart(2, '0')}:${String(now.value.getMinutes()).padStart(2, '0')}`);
const unscheduledTodos = computed(() => visibleTodos.value.filter(t => !dayOf(t))); // 稍后处理：无日期的排到第一列
const formatTime = (value?: string | null) => {
  if (!value) return '未设置';
  const date = value.slice(0, 10) === todayIso.value ? '今天' : `${Number(value.slice(5, 7))}月${Number(value.slice(8, 10))}日`;
  return `${date} ${value.slice(11, 16)}`;
};
const timeText = (t: PersonalTodoResponse) => {
  if (t.allDay) return `${(t.startAt || t.dueAt || '').slice(5, 10)}${t.startAt && t.dueAt && t.startAt.slice(0, 10) !== t.dueAt.slice(0, 10) ? ' – ' + t.dueAt.slice(5, 10) : ''} 全天`;
  if (t.startAt && t.dueAt && t.startAt.slice(0, 10) === t.dueAt.slice(0, 10)) return `${t.startAt.slice(11, 16)} – ${t.dueAt.slice(11, 16)}`;
  if (t.startAt && t.dueAt) return `${formatTime(t.startAt)} – ${formatTime(t.dueAt)}`;
  return formatTime(t.startAt || t.dueAt);
};

/* 新建弹层 */
const showCreate = ref(false);
const creating = ref(false);
const editing = ref<PersonalTodoResponse | null>(null);
const form = ref<TodoDraft>(emptyDraft());
const formError = ref('');
const initialForm = ref('');
const discarding = ref(false);
function openCreate(t: PersonalTodoResponse | null = null) {
  editing.value = t; detail.value = null;
  form.value = { title: t?.title || '', category: t?.category || 'TASK', startAt: t?.startAt?.slice(0, t.allDay ? 10 : 16).replace(' ', 'T') || '', dueAt: t?.dueAt?.slice(0, t.allDay ? 10 : 16).replace(' ', 'T') || '', note: t?.note || '', allDay: !!t?.allDay, reminderMinutes: t?.reminderMinutes ?? null };
  initialForm.value = JSON.stringify(form.value); discarding.value = false; formError.value = ''; showCreate.value = true;
}
function openSlot(day: string, hour: number, allDay = false) {
  openCreate(); form.value.allDay = allDay;
  form.value.startAt = allDay ? day : `${day}T${String(hour).padStart(2, '0')}:00`;
  form.value.dueAt = allDay ? day : hour === 23 ? `${day}T23:59` : `${day}T${String(hour + 1).padStart(2, '0')}:00`;
  form.value.category = (categoryFilter.value || 'TASK') as PersonalTodoResponse['category'];
  initialForm.value = JSON.stringify(form.value);
}
function requestCloseCreate() {
  if (creating.value) return;
  if (JSON.stringify(form.value) !== initialForm.value) discarding.value = true;
  else showCreate.value = false;
}
async function submitCreate() {
  if (creating.value) return;
  creating.value = true; formError.value = '';
  try {
    const request = payload(form.value);
    const saved = editing.value ? await personalTodoApi.update(editing.value.todoId, request) : await personalTodoApi.create(request);
    todos.value = editing.value ? todos.value.map(t => String(t.todoId) === String(editing.value!.todoId) ? saved : t) : [...todos.value, saved];
    showToast(editing.value ? '待办已保存' : '待办已添加'); showCreate.value = false;
  } catch (error) { formError.value = error instanceof Error ? error.message : '保存失败'; }
  finally { creating.value = false; }
}

/* 详情弹层 */
const detail = ref<PersonalTodoResponse | null>(null);
const confirmingDelete = ref(false);
const busy = ref(false);
let returnFocus: HTMLElement | null = null;
watch(quickDiscarding, async open => {
  await nextTick();
  if (open) document.querySelector<HTMLButtonElement>('[role="dialog"] button')?.focus();
  else document.querySelector<HTMLInputElement>('.td-quick-form input')?.focus();
});
watch([showCreate, detail], async ([formOpen, currentDetail], [wasFormOpen, previousDetail]) => {
  if (typeof document === 'undefined') return;
  if (formOpen || currentDetail) {
    if (!wasFormOpen && !previousDetail) returnFocus = document.activeElement as HTMLElement;
    await nextTick();
    document.querySelector<HTMLElement>(formOpen ? '.td-form input' : '.td-detail button')?.focus();
  } else {
    await nextTick();
    if (returnFocus?.isConnected) returnFocus.focus();
    else document.querySelector<HTMLElement>('.td-add')?.focus();
  }
}, { flush: 'post' });
function onDialogKeydown(event: KeyboardEvent) {
  if (event.key !== 'Tab' || !(event.target instanceof Element)) return;
  const dialog = event.target.closest('[role="dialog"]');
  if (!dialog) return;
  const controls = [...dialog.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), summary, [tabindex="0"]')].filter(el => el.getClientRects().length);
  const first = controls[0], last = controls[controls.length - 1];
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
}
function openDetail(t: PersonalTodoResponse) { detail.value = t; confirmingDelete.value = false; }
async function handleError(error: unknown) {
  showToast(error instanceof Error ? error.message : '操作失败');
  if (error instanceof ApiError && [403, 404, 409].includes(error.status || 0)) {
    detail.value = null; showCreate.value = false; confirmingDelete.value = false; await load();
  }
}
async function toggleDone(t: PersonalTodoResponse, done: boolean) {
  const id = String(t.todoId);
  if (busy.value || pendingIds.value.has(id)) return;
  pendingIds.value.add(id);
  try {
    const updated = done ? await personalTodoApi.complete(t.todoId) : await personalTodoApi.reopen(t.todoId);
    todos.value = todos.value.map(item => String(item.todoId) === id ? updated : item);
    showToast(done ? '已完成' : '已重开');
    if (detail.value && String(detail.value.todoId) === id) detail.value = null;
  } catch (error) { await handleError(error); }
  finally { pendingIds.value.delete(id); }
}
async function removeTodo() {
  if (!detail.value || !confirmingDelete.value || busy.value) return;
  busy.value = true;
  try { await personalTodoApi.remove(detail.value.todoId); todos.value = todos.value.filter(t => String(t.todoId) !== String(detail.value!.todoId)); detail.value = null; confirmingDelete.value = false; showToast('待办已删除'); }
  catch (error) { await handleError(error); }
  finally { busy.value = false; }
}
</script>

<template>
  <div class="td-page" @keydown="onDialogKeydown">
    <nav v-if="!sidebarCollapsed" class="td-sidebar" aria-label="个人待办导航">
      <header><h2>个人待办</h2><button class="td-icon" type="button" aria-label="收起待办导航" @click="sidebarCollapsed = true"><PanelLeftClose :size="17" /></button></header>
      <button class="td-nav td-add" type="button" @click="openQuick()"><Plus :size="18" />添加待办</button>
      <button class="td-nav" type="button" @click="openSearch"><Search :size="18" />搜索</button>
      <button class="td-nav" :class="{ active: filter === 'unscheduled' && viewMode === 'list' && !categoryFilter }" type="button" @click="selectScope('unscheduled')"><Inbox :size="18" />收集箱<span>{{ counts.unscheduled }}</span></button>
      <button class="td-nav" :class="{ active: filter === 'today' && viewMode === 'list' && !categoryFilter }" type="button" @click="selectScope('today')"><CalendarDays :size="18" />今天<span>{{ counts.today }}</span></button>
      <button class="td-nav" :class="{ active: viewMode === 'week' }" type="button" @click="selectScope('all', 'week')"><CalendarDays :size="18" />预览</button>
      <button class="td-nav" :class="{ active: filter === 'all' && viewMode === 'list' && !categoryFilter }" type="button" @click="selectScope('all')"><LayoutList :size="18" />全部待办<span>{{ counts.all }}</span></button>
      <button class="td-nav" :class="{ active: filter === 'done' && viewMode === 'list' }" type="button" @click="selectScope('done')"><Check :size="18" />已完成<span>{{ counts.done }}</span></button>
      <p class="td-category-heading">我的分类</p>
      <button v-for="category in categories" :key="category.key" class="td-nav td-category" :class="[category.color, { active: categoryFilter === category.key }]" type="button" :aria-pressed="categoryFilter === category.key" @click="selectCategory(category.key)"><i />{{ category.label }}<span>{{ categoryCounts[category.key] }}</span></button>
    </nav>
    <main class="td-main" :class="{ calendar: viewMode === 'week' }">
      <header class="td-head"><div class="td-heading"><button v-if="sidebarCollapsed" class="td-icon" type="button" aria-label="展开待办导航" @click="sidebarCollapsed = false"><PanelLeftOpen :size="18" /></button><div><h1>{{ pageTitle }}</h1><p v-if="filter === 'today' && viewMode === 'list'">{{ todayLabel }}</p></div></div>
        <details class="td-view-options"><summary><SlidersHorizontal :size="15" />查看</summary><div><label>排序<select v-model="sort" aria-label="待办排序"><option value="time">时间</option><option value="title">标题</option></select></label><label><input v-model="showCompleted" type="checkbox" />显示已完成</label></div></details>
      </header>
      <label v-if="searchOpen || search" class="td-search"><Search :size="16" /><input ref="searchInput" v-model="search" type="search" aria-label="搜索待办" /><button class="td-icon" type="button" aria-label="关闭搜索" @click="search = ''; searchOpen = false"><X :size="14" /></button></label>
      <div v-if="loading" class="td-feedback" role="status">加载中…</div>
      <div v-else-if="loadFailed" class="td-feedback"><HubEmpty title="待办加载失败" /><HubButton @click="load">重试</HubButton></div>
      <div v-else-if="viewMode === 'list'" class="td-list-scroll">
        <TodoEditor v-if="quickOpen && !groupedTodos.some(g => g.key === quickGroup)" v-model="quickForm" class="td-quick-form" :saving="quickCreating" :error="quickError" @save="submitQuick" @cancel="closeQuick" />
        <section v-for="group in groupedTodos" :key="group.key" class="td-group" :class="{ overdue: group.key === 'overdue' }">
          <h2><button type="button" :aria-expanded="!collapsedGroups.has(group.key)" @click="toggleGroup(group.key)">{{ group.label }}<span>{{ group.items.length }}</span><ChevronDown :size="13" :class="{ collapsed: collapsedGroups.has(group.key) }" /></button></h2>
          <div v-show="!collapsedGroups.has(group.key)">
            <div v-for="t in group.items" :key="String(t.todoId)" class="td-check-row" :class="[categoryClass(t.category), { done: t.status === 'DONE' }]">
              <button class="td-check" type="button" role="checkbox" :aria-checked="t.status === 'DONE'" :aria-label="`${t.status === 'DONE' ? '重开' : '完成'}：${t.title}`" :disabled="pendingIds.has(String(t.todoId))" @click="toggleDone(t, t.status !== 'DONE')"><LoaderCircle v-if="pendingIds.has(String(t.todoId))" :size="13" class="td-spinner" /><Check v-else :size="13" class="td-check-mark" /></button>
              <div class="td-item-info"><button class="td-item-body" type="button" @click="openDetail(t)">{{ t.title }}</button><span v-if="t.note" class="td-note-preview">{{ t.note }}</span><button v-if="t.startAt || t.dueAt" class="td-date" :class="{ overdue: group.key === 'overdue' }" type="button" :aria-label="`修改时间：${t.title}`" :disabled="pendingIds.has(String(t.todoId))" @click="openCreate(t)"><Clock3 :size="12" />{{ timeText(t) }}<Bell v-if="t.reminderMinutes != null" :size="12" /></button></div>
              <span class="td-item-category">{{ categoryName(t.category) }}</span><button class="td-icon td-row-edit" type="button" :aria-label="`编辑：${t.title}`" :disabled="pendingIds.has(String(t.todoId))" @click="openCreate(t)"><Pencil :size="14" /></button>
            </div>
            <TodoEditor v-if="quickOpen && quickGroup === group.key" v-model="quickForm" class="td-quick-form" :saving="quickCreating" :error="quickError" @save="submitQuick" @cancel="closeQuick" />
            <button v-else-if="group.key !== 'done'" class="td-quick-trigger" type="button" :aria-label="`添加${group.label}待办`" @click="openQuick(group.key)"><Plus :size="17" />添加待办</button>
          </div>
        </section>
        <HubEmpty v-if="!visibleTodos.length && search.trim()" title="无搜索结果" /><HubEmpty v-else-if="!groupedTodos.length && !quickOpen" :title="filter === 'done' ? '暂无已完成待办' : '暂无待办'" />
        <section v-if="filter !== 'done' && completedTodos.length" class="td-completed"><button type="button" @click="showCompleted = !showCompleted"><ChevronDown v-if="showCompleted" :size="14" /><ChevronRight v-else :size="14" />已完成 · {{ completedTodos.length }}</button><div v-if="showCompleted"><div v-for="t in completedTodos" :key="String(t.todoId)" class="td-check-row done"><button class="td-check" type="button" role="checkbox" aria-checked="true" :aria-label="`重开：${t.title}`" :disabled="pendingIds.has(String(t.todoId))" @click="toggleDone(t, false)"><Check :size="13" /></button><button class="td-item-body" type="button" @click="openDetail(t)">{{ t.title }}</button></div></div></section>
      </div>
      <template v-else>
        <div class="td-calendar-toolbar"><label class="td-month-picker"><span>{{ monthLabel }}</span><input type="date" aria-label="跳转到日期" :value="weekDays[0].iso" @change="jumpToDate(($event.target as HTMLInputElement).value)" /></label><span class="td-head-spacer" /><div class="td-week-controls"><button class="td-icon" type="button" aria-label="上一周" @click="weekOffset--"><ChevronLeft :size="16" /></button><button type="button" @click="weekOffset = 0">今天</button><button class="td-icon" type="button" aria-label="下一周" @click="weekOffset++"><ChevronRight :size="16" /></button></div><div class="td-view-switch"><button type="button" @click="viewMode = 'list'">列表</button><button type="button" class="active" aria-pressed="true">周日历</button></div></div>
        <div class="td-week-scroll"><div class="td-week-calendar">
          <header class="td-week-days"><span /><div v-for="day in weekDays" :key="day.iso" :class="{ today: day.iso === todayIso }"><small>{{ day.label }}</small><strong>{{ day.num }}</strong></div></header>
          <div class="td-all-day"><span>全天</span><div v-for="day in schedule" :key="day.iso"><div v-for="t in day.allDay" :key="String(t.todoId)" class="td-all-day-item" :class="[categoryClass(t.category), { done: t.status === 'DONE' }]"><button class="td-block-check" type="button" role="checkbox" :aria-checked="t.status === 'DONE'" :aria-label="`${t.status === 'DONE' ? '重开' : '完成'}：${t.title}`" :disabled="pendingIds.has(String(t.todoId))" @click="toggleDone(t, t.status !== 'DONE')"><Check v-if="t.status === 'DONE'" :size="9" /></button><button class="td-block-open" type="button" :title="t.title" @click="openDetail(t)">{{ t.title }}</button></div><button class="td-all-day-add" type="button" :aria-label="`${day.iso} 添加全天待办`" @click="openSlot(day.iso, 0, true)"><Plus :size="12" /></button></div></div>
          <div class="td-time-grid" :style="{ height: `${hours.length * 64}px` }"><div class="td-hour-axis"><span v-for="hour in hours" v-show="!nowVisible || Math.abs(hour * 60 - now.getHours() * 60 - now.getMinutes()) > 12" :key="hour" :style="{ top: `${(hour - startHour) * 64}px` }">{{ String(hour).padStart(2, '0') }}:00</span></div>
            <div v-for="day in schedule" :key="day.iso" class="td-day-column" :class="{ today: day.iso === todayIso }">
              <button v-for="hour in hours" :key="hour" class="td-hour-slot" type="button" :style="{ top: `${(hour - startHour) * 64}px` }" :aria-label="`${day.iso} ${String(hour).padStart(2, '0')}:00 添加待办`" @click="openSlot(day.iso, hour)" />
              <div v-for="block in day.blocks" :key="String(block.todo.todoId)" class="td-time-block" :class="[categoryClass(block.todo.category), { compact: block.end - block.start <= 30, done: block.todo.status === 'DONE' }]" :style="{ top: `${(block.start / 60 - startHour) * 64 + 2}px`, height: `${(block.end - block.start) / 60 * 64 - 4}px`, left: `calc(${block.column / block.columns * 100}% + 4px)`, width: `calc(${100 / block.columns}% - 8px)` }"><button class="td-block-check" type="button" role="checkbox" :aria-checked="block.todo.status === 'DONE'" :aria-label="`${block.todo.status === 'DONE' ? '重开' : '完成'}：${block.todo.title}`" :disabled="pendingIds.has(String(block.todo.todoId))" @click="toggleDone(block.todo, block.todo.status !== 'DONE')"><Check v-if="block.todo.status === 'DONE'" :size="9" /></button><button class="td-block-open" type="button" :title="`${block.todo.title} · ${timeText(block.todo)}`" :aria-label="`${block.todo.title}，${timeText(block.todo)}`" @click="openDetail(block.todo)"><span>{{ block.todo.title }}</span><small>{{ timeText(block.todo) }}</small></button></div>
              <span v-if="nowVisible && day.iso === todayIso" class="td-now-active" :style="{ top: `${nowPosition}px` }" />
            </div>
            <div v-if="nowVisible" class="td-now-line" :style="{ top: `${nowPosition}px` }"><span>{{ nowLabel }}</span></div>
          </div>
        </div><section v-if="unscheduledTodos.length" class="td-calendar-unscheduled"><h2>待安排 · {{ unscheduledTodos.length }}</h2><button v-for="t in unscheduledTodos" :key="String(t.todoId)" type="button" @click="openDetail(t)">{{ t.title }}</button></section></div>
      </template>
    </main>
    <HubDialog v-if="showCreate" :lock="creating" title-id="td-form-title" @close="requestCloseCreate"><div class="td-form"><header class="td-modal-head"><h2 id="td-form-title">{{ editing ? '编辑待办' : '添加待办' }}</h2><button class="td-icon" type="button" aria-label="关闭" :disabled="creating" @click="requestCloseCreate"><X :size="18" /></button></header><TodoEditor v-model="form" :saving="creating" :error="formError" :editing="!!editing" @save="submitCreate" @cancel="requestCloseCreate" /><div v-if="discarding" class="td-confirm" role="alert"><span>放弃未保存的修改？</span><HubButton size="sm" @click="discarding = false">继续编辑</HubButton><HubButton variant="danger" size="sm" @click="showCreate = false; discarding = false">放弃修改</HubButton></div></div></HubDialog>
    <HubDialog v-if="quickDiscarding" title-id="td-quick-discard" @close="quickDiscarding = false"><div class="td-discard"><h2 id="td-quick-discard">放弃未保存的待办？</h2><HubButton @click="quickDiscarding = false">继续编辑</HubButton><HubButton variant="danger" @click="quickDiscarding = false; quickOpen = false">放弃修改</HubButton></div></HubDialog>
    <HubDialog v-if="detail" :lock="busy || pendingIds.has(String(detail.todoId))" title-id="td-detail-title" @close="detail = null; confirmingDelete = false"><div class="td-detail"><header class="td-modal-head"><span class="td-status">{{ detail.status === 'DONE' ? '已完成' : '待处理' }}</span><button class="td-icon" type="button" aria-label="关闭" :disabled="busy || pendingIds.has(String(detail.todoId))" @click="detail = null"><X :size="18" /></button></header><div class="td-detail-scroll"><h2 id="td-detail-title">{{ detail.title }}</h2><dl><div><dt>分类</dt><dd>{{ categoryName(detail.category) }}</dd></div><div><dt>开始时间</dt><dd>{{ detail.allDay ? (detail.startAt || '').slice(0, 10) + ' 全天' : formatTime(detail.startAt) }}</dd></div><div><dt>截止时间</dt><dd>{{ detail.allDay ? (detail.dueAt || '').slice(0, 10) + ' 全天' : formatTime(detail.dueAt) }}</dd></div><div v-if="detail.reminderMinutes != null"><dt>提醒</dt><dd>{{ detail.reminderMinutes === 0 ? '准时提醒' : `提前 ${detail.reminderMinutes} 分钟` }}</dd></div><div v-if="detail.note"><dt>备注</dt><dd>{{ detail.note }}</dd></div></dl></div><div v-if="confirmingDelete" class="td-confirm"><span>确认删除“{{ detail.title }}”？</span><HubButton size="sm" :disabled="busy" @click="confirmingDelete = false">取消</HubButton><HubButton variant="danger" size="sm" :disabled="busy" @click="removeTodo">确认删除</HubButton></div><footer class="td-detail-actions"><HubButton variant="danger" :disabled="busy || pendingIds.has(String(detail.todoId))" @click="confirmingDelete = true">删除</HubButton><HubButton :disabled="busy || pendingIds.has(String(detail.todoId))" @click="openCreate(detail)">编辑</HubButton><span class="td-head-spacer" /><HubButton variant="primary" :disabled="busy || pendingIds.has(String(detail.todoId))" @click="toggleDone(detail, detail.status !== 'DONE')">{{ detail.status === 'DONE' ? '重开' : '完成' }}</HubButton></footer></div></HubDialog>
  </div>
</template>
<style scoped src="../styles/todos.css" />
