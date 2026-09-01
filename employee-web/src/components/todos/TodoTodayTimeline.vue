<script setup lang="ts">
import { computed } from 'vue';
import type { TodoRecord } from '@/stores/shell';
import AppEmptyState from '../ui/AppEmptyState.vue';

const props = defineProps<{
  todos: TodoRecord[];
}>();

const emit = defineEmits<{
  (e: 'select', todo: TodoRecord): void;
  (e: 'create'): void;
}>();

// 时间轴覆盖全天 0:00 - 24:00，定位全部用百分比，轨道高度跟随卡片
const AXIS_START = 0;
const AXIS_END = 24;
const AXIS_SPAN = AXIS_END - AXIS_START;

interface TimelineBlock {
  todo: TodoRecord;
  top: number; // 百分比
  height: number; // 百分比
  startLabel: string;
  endLabel: string;
}

function toDateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

const todayKey = toDateKey(new Date());

function parseTime(value?: string): number | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) return null;
  const hour = Number(value.slice(11, 13));
  const minute = Number(value.slice(14, 16));
  return hour + minute / 60;
}

const todayTodos = computed(() =>
  props.todos.filter((todo) => (
    todo.startDate && todo.endDate && todo.startDate <= todayKey && todo.endDate >= todayKey
  )));

const timelineBlocks = computed<TimelineBlock[]>(() => {
  return todayTodos.value
    .map((todo) => {
      const start = parseTime(todo.startAt) ?? AXIS_START;
      const end = parseTime(todo.endAt) ?? Math.min(start + 1, AXIS_END);
      const clampedStart = Math.max(AXIS_START, Math.min(start, AXIS_END));
      const clampedEnd = Math.max(clampedStart + 0.25, Math.min(end, AXIS_END));
      return {
        todo,
        top: ((clampedStart - AXIS_START) / AXIS_SPAN) * 100,
        height: Math.max(4, ((clampedEnd - clampedStart) / AXIS_SPAN) * 100),
        startLabel: todo.startAt?.slice(11, 16) ?? '',
        endLabel: todo.endAt?.slice(11, 16) ?? '',
      };
    })
    .sort((a, b) => a.top - b.top);
});

const hours = computed(() => Array.from({ length: AXIS_SPAN + 1 }, (_, i) => AXIS_START + i));

// 当前时间红线
const nowIndicatorTop = computed(() => {
  const now = new Date();
  const pos = now.getHours() + now.getMinutes() / 60;
  if (pos < AXIS_START || pos > AXIS_END) return null;
  return ((pos - AXIS_START) / AXIS_SPAN) * 100;
});

const nowLabel = computed(() => {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
});

const todayDoneCount = computed(() => todayTodos.value.filter((t) => t.completed).length);
</script>

<template>
  <section class="todo-timeline-card">
    <header class="timeline-head">
      <div>
        <h3>今日时间轴</h3>
        <p>{{ todayTodos.length }} 项安排 · 已完成 {{ todayDoneCount }} 项</p>
      </div>
      <button type="button" class="timeline-add" @click="emit('create')">+ 添加</button>
    </header>

    <AppEmptyState
      v-if="!timelineBlocks.length"
      icon="todos"
      title="今日无安排"
      description="今天还没有安排任务，点右上角添加一个吧。"
      action-label="添加待办"
      @action="emit('create')"
    />

    <div v-else class="timeline-body">
      <div class="timeline-axis">
        <div v-for="hour in hours" :key="hour" class="timeline-hour" :style="{ top: ((hour - AXIS_START) / AXIS_SPAN) * 100 + '%' }">
          <span>{{ String(hour).padStart(2, '0') }}:00</span>
        </div>
      </div>
      <div class="timeline-track">
        <div v-for="hour in hours" :key="`line-${hour}`" class="timeline-gridline" :style="{ top: ((hour - AXIS_START) / AXIS_SPAN) * 100 + '%' }" />

        <div v-if="nowIndicatorTop !== null" class="timeline-now" :style="{ top: nowIndicatorTop + '%' }">
          <i />{{ nowLabel }}
        </div>

        <button
          v-for="block in timelineBlocks"
          :key="block.todo.id"
          type="button"
          class="timeline-block"
          :class="[`todo-color-${block.todo.tagColor ?? 'blue'}`, { completed: block.todo.completed }]"
          :style="{ top: block.top + '%', height: block.height + '%' }"
          @click="emit('select', block.todo)"
        >
          <strong>{{ block.todo.title }}</strong>
          <small>{{ block.startLabel }}<template v-if="block.endLabel"> - {{ block.endLabel }}</template></small>
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.todo-timeline-card {
  padding: var(--space-16) var(--space-18);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-10, 0.625rem);
  background: var(--surface-panel);
}

.timeline-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--space-14, 0.875rem);
}

.timeline-head h3 {
  margin: 0;
  font-size: var(--text-body-lg);
  font-weight: 700;
}

.timeline-head p {
  margin: var(--space-4) 0 0;
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.timeline-add {
  padding: var(--space-6, 0.375rem) var(--space-12);
  border: 0;
  border-radius: var(--radius-8);
  background: var(--primary-600);
  color: #fff;
  font-size: var(--text-caption-md);
  font-weight: 600;
  cursor: pointer;
}

.timeline-body {
  display: grid;
  grid-template-columns: 3rem minmax(0, 1fr);
  gap: var(--space-8);
  flex: 1;
  min-height: 20rem;
  overflow-y: auto;
  scrollbar-width: thin;
}

.timeline-axis {
  position: relative;
}

.timeline-hour {
  position: absolute;
  right: 0;
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding-right: var(--space-8);
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
  font-variant-numeric: tabular-nums;
}

.timeline-hour span {
  transform: translateY(-50%);
}

/* 首尾整点标签不向上/下溢出轨道 */
.timeline-hour:first-child span {
  transform: none;
}

.timeline-hour:last-child span {
  transform: translateY(-100%);
}

.timeline-track {
  position: relative;
  height: 100%;
  min-height: 20rem;
  border-left: 1px solid var(--border-subtle);
}

.timeline-gridline {
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: var(--border-subtle);
  opacity: 0.5;
}

.timeline-now {
  position: absolute;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  gap: var(--space-4);
  color: var(--danger-500, #ef4444);
  font-size: 0.625rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  z-index: 3;
  pointer-events: none;
  transform: translateY(-50%);
}

.timeline-now i {
  flex: 1;
  height: 2px;
  background: var(--danger-500, #ef4444);
  border-radius: 999px;
}

.timeline-now::before {
  content: '';
  width: 0.5rem;
  height: 0.5rem;
  margin-left: -0.25rem;
  border-radius: 999px;
  background: var(--danger-500, #ef4444);
  flex: 0 0 auto;
}

.timeline-block {
  position: absolute;
  left: var(--space-8);
  right: var(--space-8);
  display: grid;
  align-content: center;
  gap: 1px;
  padding: var(--space-4) var(--space-10);
  border: 1px solid transparent;
  border-radius: var(--radius-8);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  z-index: 2;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.timeline-block:hover {
  transform: translateX(2px);
  box-shadow: var(--shadow-sm);
}

.timeline-block strong {
  font-size: var(--text-body-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-block small {
  font-size: 0.625rem;
  opacity: 0.75;
  font-variant-numeric: tabular-nums;
}

.timeline-block.todo-color-blue { background: var(--primary-50, #eef2ff); border-color: var(--primary-200); color: var(--primary-700); }
.timeline-block.todo-color-green { background: var(--success-50, #ecfdf5); border-color: var(--success-200, #a7f3d0); color: var(--success-700); }
.timeline-block.todo-color-amber { background: var(--warning-50, #fffbeb); border-color: var(--warning-200, #fde68a); color: var(--warning-700, #b45309); }
.timeline-block.todo-color-red { background: var(--danger-50, #fef2f2); border-color: var(--danger-200, #fecaca); color: var(--danger-700, #b91c1c); }

.timeline-block.completed {
  opacity: 0.55;
}

.timeline-block.completed strong {
  text-decoration: line-through;
}
</style>
