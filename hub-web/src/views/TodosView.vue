<script setup lang="ts">
/**
 * 我的待办：分类 × 本周泳道 / 月历 / 列表三种视图；完成、重开、删除与新建。
 */
import { computed, onMounted, ref } from 'vue';
import { CalendarPlus, LayoutList, Rows3, CalendarDays } from 'lucide-vue-next';
import { personalTodoApi } from '@/api';
import type { PersonalTodoResponse } from '@/api/types';
import { useToast } from '@/composables/useToast';
import HubButton from '@/components/base/HubButton.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';
import HubToast from '@/components/base/HubToast.vue';

const { message: toastMessage, show: showToast } = useToast();

const todos = ref<PersonalTodoResponse[]>([]);
const loading = ref(true);
const loadFailed = ref(false);
const filter = ref<'today' | 'week' | 'later' | 'done'>('today');
const viewMode = ref<'lane' | 'month' | 'list'>('lane');

const tabs = [
  { key: 'today', label: '今日安排' },
  { key: 'week', label: '本周安排' },
  { key: 'later', label: '稍后处理' },
  { key: 'done', label: '已完成' },
] as const;
const CATEGORY_LABEL: Record<string, string> = { APPROVAL: '审批与申请', MEETING: '会议与协作', TASK: '个人任务', REMINDER: '提醒与杂项' };
const CATEGORY_CLASS: Record<string, string> = { APPROVAL: 'approval', MEETING: 'meeting', TASK: 'task', REMINDER: 'reminder' };
const CATEGORIES = ['APPROVAL', 'MEETING', 'TASK', 'REMINDER'] as const;

const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日'];

function toIso(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function dayOf(t: PersonalTodoResponse) {
  return (t.dueAt || '').slice(0, 10);
}

const monday = computed(() => {
  const d = new Date();
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  d.setHours(0, 0, 0, 0);
  return d;
});
const weekDays = computed(() => Array.from({ length: 7 }, (_, i) => {
  const d = new Date(monday.value);
  d.setDate(d.getDate() + i);
  return { iso: toIso(d), label: `${d.getDate()} ${WEEK_LABELS[i]}` };
}));
const todayIso = toIso(new Date());

async function load() {
  loading.value = true;
  loadFailed.value = false;
  try {
    todos.value = await personalTodoApi.list();
  } catch (error) {
    loadFailed.value = true;
    showToast(error instanceof Error ? error.message : '待办加载失败');
  } finally {
    loading.value = false;
  }
}
onMounted(load);

const visibleTodos = computed(() => todos.value.filter((t) => {
  const day = dayOf(t);
  if (filter.value === 'done') return t.status === 'DONE';
  if (filter.value === 'today') return t.status === 'PENDING' && day === todayIso;
  if (filter.value === 'week') return t.status === 'PENDING' && day >= weekDays.value[0].iso && day <= weekDays.value[6].iso;
  return t.status === 'PENDING' && (day > todayIso || !day);
}));

/* 泳道：按分类行 × 日期列 */
const laneTodos = computed(() => {
  if (filter.value === 'done') return todos.value.filter((t) => t.status === 'DONE');
  if (filter.value === 'today') return visibleTodos.value;
  if (filter.value === 'week') return visibleTodos.value;
  return visibleTodos.value; // 稍后处理：无日期的排到第一列
});

/* 月历：当月网格 */
const monthCells = computed(() => {
  const base = new Date();
  const first = new Date(base.getFullYear(), base.getMonth(), 1);
  const lead = (first.getDay() + 6) % 7;
  const daysInMonth = new Date(base.getFullYear(), base.getMonth() + 1, 0).getDate();
  const cells: Array<{ iso: string | null; num: string | null }> = Array.from({ length: lead }, () => ({ iso: null, num: null }));
  for (let i = 1; i <= daysInMonth; i += 1) cells.push({ iso: toIso(new Date(base.getFullYear(), base.getMonth(), i)), num: String(i) });
  return cells;
});
const todosByDay = computed(() => {
  const map = new Map<string, PersonalTodoResponse[]>();
  for (const t of todos.value) {
    const day = dayOf(t);
    if (!day) continue;
    if (!map.has(day)) map.set(day, []);
    map.get(day)!.push(t);
  }
  return map;
});

/* 列表：按分类分组 */
const groupedTodos = computed(() => CATEGORIES.map((c) => ({
  category: c,
  items: todos.value.filter((t) => t.category === c && (filter.value === 'done' ? t.status === 'DONE' : t.status === 'PENDING')),
})));

const timeText = (t: PersonalTodoResponse) => (t.dueAt ? t.dueAt.slice(5, 16).replace('T', ' ') : '未安排时间');

/* 新建弹层 */
const showCreate = ref(false);
const creating = ref(false);
const form = ref({ title: '', category: 'TASK' as PersonalTodoResponse['category'], dueAt: '', note: '' });

function openCreate() {
  form.value = { title: '', category: 'TASK', dueAt: '', note: '' };
  showCreate.value = true;
}

async function submitCreate() {
  if (!form.value.title.trim()) { showToast('请填写待办标题'); return; }
  creating.value = true;
  try {
    await personalTodoApi.create({
      title: form.value.title.trim(),
      category: form.value.category,
      dueAt: form.value.dueAt || undefined,
      note: form.value.note.trim() || undefined,
    });
    showToast('待办已创建');
    showCreate.value = false;
    await load();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '创建失败');
  } finally {
    creating.value = false;
  }
}

/* 详情弹层 */
const detail = ref<PersonalTodoResponse | null>(null);

async function toggleDone(t: PersonalTodoResponse, done: boolean) {
  try {
    if (done) await personalTodoApi.complete(t.todoId);
    else await personalTodoApi.reopen(t.todoId);
    showToast(done ? '已完成' : '已重开');
    detail.value = null;
    await load();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  }
}

async function removeTodo() {
  if (!detail.value) return;
  try {
    await personalTodoApi.remove(detail.value.todoId);
    showToast('待办已删除');
    detail.value = null;
    await load();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '删除失败');
  }
}

function laneBlockClass(t: PersonalTodoResponse) {
  return [CATEGORY_CLASS[t.category], t.status === 'DONE' ? 'done' : ''];
}
function laneCol(t: PersonalTodoResponse) {
  const day = dayOf(t);
  const idx = weekDays.value.findIndex((d) => d.iso === day);
  return idx < 0 ? 0 : idx;
}
function laneItems(col: number) {
  return laneTodos.value.filter((t) => laneCol(t) === col);
}
function itemsOf(category: string, col: number) {
  return laneItems(col).filter((t) => t.category === category);
}
</script>

<template>
  <div class="td-page">
    <header class="td-head">
      <div><h1>我的待办 · 时间泳道</h1><p>按个人安排与时间协调每日事项</p></div>
      <span class="td-head-spacer" />
      <button type="button" class="td-btn-primary" @click="openCreate"><CalendarPlus :size="16" />新建待办</button>
    </header>

    <div class="td-toolbar">
      <div class="td-tabs">
        <button v-for="t in tabs" :key="t.key" type="button" :class="{ active: filter === t.key }" @click="filter = t.key">{{ t.label }}</button>
      </div>
      <div class="td-view-switch">
        <button type="button" :class="{ active: viewMode === 'lane' }" @click="viewMode = 'lane'"><Rows3 :size="14" />泳道视图</button>
        <button type="button" :class="{ active: viewMode === 'month' }" @click="viewMode = 'month'"><CalendarDays :size="14" />月历视图</button>
        <button type="button" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'"><LayoutList :size="14" />列表</button>
      </div>
    </div>

    <section v-if="viewMode === 'lane'" class="td-card">
      <div class="td-lane-header">
        <div class="td-lane-name-cell" />
        <div v-for="d in weekDays" :key="d.iso" class="td-lane-head-cell" :class="{ today: d.iso === todayIso }">{{ d.label }}</div>
      </div>
      <div v-for="category in CATEGORIES" :key="category" class="td-lane-row">
        <div class="td-lane-name-cell">{{ CATEGORY_LABEL[category] }}</div>
        <div class="td-lane-area">
          <div v-for="d in weekDays" :key="d.iso" class="td-lane-day" :class="{ today: d.iso === todayIso }">
            <button
              v-for="t in itemsOf(category, weekDays.findIndex((w) => w.iso === d.iso))" :key="String(t.todoId)" type="button"
              :class="['td-todo-block', ...laneBlockClass(t)]"
              @click="detail = t"
            >
              <strong>{{ t.title }}</strong>
              <small>{{ timeText(t) }}</small>
            </button>
          </div>
        </div>
      </div>
      <HubEmpty v-if="!laneTodos.length && !loading" :title="loadFailed ? '待办加载失败' : '暂无待办'" :note="loadFailed ? '请稍后重试' : '点击右上角新建待办'" />
    </section>

    <section v-else-if="viewMode === 'month'" class="td-card">
      <div class="td-month-week"><span v-for="w in WEEK_LABELS" :key="w">{{ w }}</span></div>
      <div class="td-month">
        <div v-for="(cell, i) in monthCells" :key="i" class="td-month-cell" :class="{ blank: !cell.iso, today: cell.iso === todayIso }">
          <template v-if="cell.iso">
            <span class="num">{{ cell.num }}</span>
            <button v-for="t in (todosByDay.get(cell.iso) || [])" :key="String(t.todoId)" type="button" class="item" :class="{ done: t.status === 'DONE' }" @click="detail = t">{{ t.title }}</button>
          </template>
        </div>
      </div>
    </section>

    <section v-else class="td-card">
      <div v-for="group in groupedTodos" :key="group.category" class="td-group">
        <h3>{{ CATEGORY_LABEL[group.category] }}</h3>
        <button v-for="t in group.items" :key="String(t.todoId)" type="button" class="td-item" :class="{ done: t.status === 'DONE' }" @click="detail = t">
          <strong>{{ t.title }}</strong>
          <small>{{ timeText(t) }}</small>
          <span v-if="t.status === 'DONE'" class="done-mark">已完成</span>
        </button>
        <p v-if="!group.items.length" style="font-size: 12px; color: var(--hw-muted);">暂无</p>
      </div>
    </section>

    <HubDialog v-if="showCreate" @close="showCreate = false">
      <form class="td-form" @submit.prevent="submitCreate">
        <h2>新建待办</h2>
        <label>标题<input v-model="form.title" placeholder="待办标题" /></label>
        <label>分类
          <select v-model="form.category">
            <option v-for="c in CATEGORIES" :key="c" :value="c">{{ CATEGORY_LABEL[c] }}</option>
          </select>
        </label>
        <label>时间<input v-model="form.dueAt" type="datetime-local" /></label>
        <label>备注<textarea v-model="form.note" placeholder="补充说明（可选）" /></label>
        <div class="td-form-actions">
          <HubButton type="button" @click="showCreate = false">取消</HubButton>
          <HubButton variant="primary" type="submit" :disabled="creating">创建</HubButton>
        </div>
      </form>
    </HubDialog>

    <HubDialog v-if="detail" @close="detail = null">
      <div class="td-detail">
        <h2>{{ detail.title }}</h2>
        <dl>
          <div><dt>状态</dt><dd><em :class="['td-status', detail.status === 'DONE' ? 'done' : 'pending']">{{ detail.status === 'DONE' ? '已完成' : '待处理' }}</em></dd></div>
          <div><dt>分类</dt><dd>{{ CATEGORY_LABEL[detail.category] }}</dd></div>
          <div><dt>时间</dt><dd>{{ timeText(detail) }}</dd></div>
          <div v-if="detail.note"><dt>备注</dt><dd>{{ detail.note }}</dd></div>
        </dl>
        <div class="td-detail-actions">
          <HubButton variant="danger" @click="removeTodo">删除</HubButton>
          <HubButton variant="primary" @click="toggleDone(detail, detail.status !== 'DONE')">{{ detail.status === 'DONE' ? '重开' : '完成' }}</HubButton>
        </div>
      </div>
    </HubDialog>

    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped src="../styles/todos.css" />
