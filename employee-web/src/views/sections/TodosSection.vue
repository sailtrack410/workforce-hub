<script setup lang="ts">
import {
  CalendarDays,
  ChevronLeft,
  ChevronRight,
  Plus,
} from 'lucide-vue-next';
import { computed, ref } from 'vue';
import { useTodosSectionContext } from '../employee-shell-context';
import type { TodoRecord } from '@/stores/shell';
import AppButton from '../../components/ui/AppButton.vue';
import TodoCreateDialog from '../../components/todos/TodoCreateDialog.vue';
import TodoTodayTimeline from '../../components/todos/TodoTodayTimeline.vue';

interface CalendarDay {
  key: string;
  label: string;
  outside: boolean;
  today: boolean;
  count: number;
}

interface CalendarSegment {
  task: TodoRecord;
  start: number;
  end: number;
  row: number;
}

interface CalendarWeek {
  key: string;
  days: CalendarDay[];
  segments: CalendarSegment[];
  rowCount: number;
}

const {
  shell,
  pendingTodoCount,
  backToList,
} = useTodosSectionContext();

const todayKey = toDateKey(new Date());
const visibleMonth = ref(startOfMonth(new Date()));
const showCreate = ref(false);
const editingTodo = ref<TodoRecord | null>(null);

function openCreate() {
  editingTodo.value = null;
  showCreate.value = true;
}

function openEdit(todo: TodoRecord) {
  editingTodo.value = todo;
  showCreate.value = true;
}

const compactMonthLabel = computed(() => `${visibleMonth.value.getMonth() + 1}月`);
const monthYearLabel = computed(() => `${visibleMonth.value.getFullYear()}`);
const weekLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
const monthStartKey = computed(() => toDateKey(visibleMonth.value));
const monthEndKey = computed(() => toDateKey(new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + 1, 0)));
const calendarStartKey = computed(() => toDateKey(startOfWeekMonday(visibleMonth.value)));
const calendarEndKey = computed(() => toDateKey(endOfWeekSunday(new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + 1, 0))));
const visibleTodos = computed(() => shell.filteredTodos.filter((todo) => (
  isTaskDated(todo) && todo.endDate >= calendarStartKey.value && todo.startDate <= calendarEndKey.value
)));
const monthTodoCount = computed(() => shell.todos.filter((todo) => (
  isTaskDated(todo) && todo.endDate >= monthStartKey.value && todo.startDate <= monthEndKey.value
)).length);
const todayPendingCount = computed(() => shell.todos.filter((todo) => (
  !todo.completed && isTaskDated(todo) && todo.startDate <= todayKey && todo.endDate >= todayKey
)).length);
const completedTodoCount = computed(() => shell.todos.filter((todo) => todo.completed).length);
const calendarWeeks = computed(() => {
  const weeks: CalendarWeek[] = [];
  const month = visibleMonth.value.getMonth();
  const end = endOfWeekSunday(new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + 1, 0));
  let cursor = startOfWeekMonday(visibleMonth.value);

  while (cursor <= end) {
    const days = Array.from({ length: 7 }, (_, index) => {
      const date = addDays(cursor, index);
      const key = toDateKey(date);
      const dayTodos = visibleTodos.value.filter((todo) => isTaskDated(todo) && todo.startDate <= key && todo.endDate >= key);
      return {
        key,
        label: String(date.getDate()),
        outside: date.getMonth() !== month,
        today: key === todayKey,
        count: dayTodos.length,
      };
    });
    const weekStart = days[0]?.key ?? toDateKey(cursor);
    const weekEnd = days[6]?.key ?? toDateKey(addDays(cursor, 6));
    const rawSegments = visibleTodos.value
      .filter((todo) => isTaskDated(todo) && todo.endDate >= weekStart && todo.startDate <= weekEnd)
      .map((todo) => {
        const startDate = todo.startDate ?? weekStart;
        const endDate = todo.endDate ?? weekEnd;
        return {
          task: todo,
          start: Math.max(1, daysBetween(weekStart, startDate) + 1),
          end: Math.min(8, daysBetween(weekStart, endDate) + 2),
        };
      })
      .sort((a, b) => a.start - b.start || a.end - b.end || a.task.title.localeCompare(b.task.title));
    const rowEnds: number[] = [];
    const segments = rawSegments.map((segment) => {
      let rowIndex = rowEnds.findIndex((rowEnd) => segment.start >= rowEnd);
      if (rowIndex === -1) rowIndex = rowEnds.length;
      rowEnds[rowIndex] = segment.end;
      return { ...segment, row: rowIndex + 1 };
    });
    weeks.push({ key: weekStart, days, segments, rowCount: Math.max(1, rowEnds.length) });
    cursor = addDays(cursor, 7);
  }

  return weeks;
});

function toDateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function toDateTimeMinuteKey(date: Date) {
  return `${toDateKey(date)}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function parseDateKey(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function isTaskDated(todo: TodoRecord): todo is TodoRecord & { startDate: string; endDate: string } {
  return /^\d{4}-\d{2}-\d{2}$/.test(todo.startDate ?? '') && /^\d{4}-\d{2}-\d{2}$/.test(todo.endDate ?? '');
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1);
}

function startOfWeekMonday(date: Date) {
  const result = new Date(date);
  const diff = (result.getDay() + 6) % 7;
  result.setDate(result.getDate() - diff);
  return result;
}

function endOfWeekSunday(date: Date) {
  const result = startOfWeekMonday(date);
  result.setDate(result.getDate() + 6);
  return result;
}

function addDays(date: Date, amount: number) {
  const result = new Date(date);
  result.setDate(result.getDate() + amount);
  return result;
}

function addMinutes(date: Date, amount: number) {
  const result = new Date(date);
  result.setMinutes(result.getMinutes() + amount);
  return result;
}

function daysBetween(start: string, end: string) {
  return Math.round((parseDateKey(end).getTime() - parseDateKey(start).getTime()) / 86400000);
}

function shiftMonth(amount: number) {
  visibleMonth.value = new Date(visibleMonth.value.getFullYear(), visibleMonth.value.getMonth() + amount, 1);
}

function goToday() {
  visibleMonth.value = startOfMonth(new Date());
}

function isMinuteDateTime(value: string) {
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value);
}

function segmentMeta(todo: TodoRecord) {
  return [todo.tagName, todo.subtitle].filter(Boolean).join(' · ');
}

function shortDateTime(value?: string) {
  if (!value || !isMinuteDateTime(value)) return '';
  const day = Number(value.slice(8, 10));
  return `${day}日 ${value.slice(11)}`;
}

function segmentTime(todo: TodoRecord) {
  if (!todo.startAt || !todo.endAt) return todo.time || '';
  if (todo.startDate === todo.endDate) return `${shortDateTime(todo.startAt)}-${todo.endAt.slice(11)}`;
  return `${shortDateTime(todo.startAt)} - ${shortDateTime(todo.endAt)}`;
}

function segmentTitle(todo: TodoRecord) {
  return [todo.title, segmentTime(todo), segmentMeta(todo), todo.note].filter(Boolean).join('\n');
}

function selectTodo(todo: TodoRecord) {
  shell.selectItem('todos', todo.id);
  openEdit(todo);
}
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head todo-calendar-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">待办</h2>
        <div class="workspace-meta-line">
          <span>未完成 {{ pendingTodoCount }} 项</span>
          <span>本月 {{ monthTodoCount }} 项</span>
        </div>
      </div>
    </div>
  </header>

  <section class="todo-calendar-layout">
    <div class="todo-split">
      <div class="todo-main-col">
        <section class="workspace-command-bar todo-command-bar" aria-label="待办摘要">
          <div class="workspace-command-lead">
            <span class="workspace-command-icon"><CalendarDays :size="20" /></span>
            <div>
              <span class="workspace-command-kicker">今日节奏</span>
              <strong>{{ todayPendingCount ? `${todayPendingCount} 项待处理` : '今日已清空' }}</strong>
              <p>围绕日期安排任务，优先处理当日未完成事项。</p>
            </div>
          </div>
          <dl class="workspace-command-metrics">
            <div>
              <dt>全部未完成</dt>
              <dd>{{ pendingTodoCount }}</dd>
            </div>
            <div>
              <dt>本月安排</dt>
              <dd>{{ monthTodoCount }}</dd>
            </div>
            <div>
              <dt>累计完成</dt>
              <dd>{{ completedTodoCount }}</dd>
            </div>
          </dl>
        </section>

        <section class="todo-calendar-card">
      <header class="todo-month-title">
        <div>
          <h3>{{ compactMonthLabel }}</h3>
          <span>{{ monthYearLabel }}</span>
        </div>
        <div class="todo-month-actions">
          <AppButton variant="secondary" size="sm" @click="shiftMonth(-1)">
            <ChevronLeft :size="15" />
            上月
          </AppButton>
          <AppButton variant="secondary" size="sm" @click="goToday">
            <CalendarDays :size="15" />
            今天
          </AppButton>
          <AppButton variant="secondary" size="sm" @click="shiftMonth(1)">
            下月
            <ChevronRight :size="15" />
          </AppButton>
          <AppButton variant="primary" size="sm" @click="openCreate">
            <Plus :size="15" />
            添加
          </AppButton>
        </div>
      </header>

      <div class="todo-calendar-body">
        <div class="todo-week-header">
          <span v-for="label in weekLabels" :key="label">{{ label }}</span>
        </div>
        <div class="todo-month-grid">
          <section
            v-for="week in calendarWeeks"
            :key="week.key"
            class="todo-week-row"
            :style="{ '--todo-week-rows': week.rowCount }"
          >
            <div class="todo-week-days">
              <div
                v-for="day in week.days"
                :key="day.key"
                class="todo-day-cell"
                :class="{ outside: day.outside, today: day.today }"
              >
                <span>{{ day.label }}</span>
                <em v-if="day.count">{{ day.count }}</em>
              </div>
            </div>
            <div class="todo-week-bars">
              <div
                v-for="segment in week.segments"
                :key="`${week.key}-${segment.task.id}`"
                role="button"
                tabindex="0"
                class="todo-calendar-bar"
                :class="[`todo-color-${segment.task.tagColor ?? 'blue'}`, { completed: segment.task.completed }]"
                :style="{ gridColumn: `${segment.start} / ${segment.end}`, gridRow: segment.row }"
                :title="segmentTitle(segment.task)"
                @click="selectTodo(segment.task)"
                @keydown.enter.prevent="selectTodo(segment.task)"
                @keydown.space.prevent="selectTodo(segment.task)"
              >
                <small>{{ segmentTime(segment.task) }}</small>
                <span>{{ segment.task.title }}</span>
                <em>{{ segment.task.tagName }}</em>
              </div>
            </div>
          </section>
        </div>
        <p v-if="!visibleTodos.length" class="todo-calendar-empty">暂无待办</p>
      </div>
    </section>
      </div>

    <TodoTodayTimeline
      :todos="shell.todos"
      @select="selectTodo"
      @create="openCreate"
    />
    </div>
  </section>

  <TodoCreateDialog :open="showCreate" :todo="editingTodo" @close="showCreate = false; editingTodo = null" />
</div>
</template>
