<script setup lang="ts">
import { computed } from 'vue';
import { CalendarCheck2, ClipboardList, ListTodo, MessageSquareText, ArrowRight } from 'lucide-vue-next';
import type { AttendanceDailyResultResponse, LeaveAccountResponse, WorkflowRequestSummaryResponse } from '@/api/types';
import type { TodoRecord, ConversationRecord } from '@/stores/shell';
import { statusText, toneOfStatus, minutesToDayLabel, formatDateTime } from '@/utils/employee-format';
import AppEmptyState from './ui/AppEmptyState.vue';
import AppTag from './ui/AppTag.vue';

const props = defineProps<{
  displayName: string;
  hireDate?: string | null;
  todayResult?: AttendanceDailyResultResponse | null;
  attendanceDailyResults: AttendanceDailyResultResponse[];
  workflowRequests: WorkflowRequestSummaryResponse[];
  leaveAccounts: LeaveAccountResponse[];
  todos: TodoRecord[];
  conversations: ConversationRecord[];
}>();

const emit = defineEmits<{
  (e: 'open-workbench', id: string): void;
  (e: 'open-section', id: string): void;
}>();

// ---------- 问候条 ----------
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜深了';
  if (hour < 9) return '早上好';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const todayLabel = computed(() => {
  const now = new Date();
  const weekDays = ['日', '一', '二', '三', '四', '五', '六'];
  return `${now.getMonth() + 1}月${now.getDate()}日 星期${weekDays[now.getDay()]}`;
});

const hireDays = computed(() => {
  if (!props.hireDate) return null;
  const hire = new Date(props.hireDate);
  if (Number.isNaN(hire.getTime())) return null;
  const days = Math.floor((Date.now() - hire.getTime()) / 86400000);
  return days >= 0 ? days : null;
});

// ---------- 状态卡带 ----------
const todayAttendance = computed(() => {
  const result = props.todayResult;
  if (!result) return { label: '未排班', tone: 'neutral' as const, desc: '今日无排班' };
  const status = result.resultStatus;
  if (status === 'NORMAL') return { label: '正常', tone: 'success' as const, desc: '今日考勤正常' };
  if (result.exceptionFlag) return { label: '异常', tone: 'warning' as const, desc: '有考勤异常待处理' };
  return { label: statusText(status), tone: 'neutral' as const, desc: '今日考勤' };
});

const pendingApprovalCount = computed(() =>
  props.workflowRequests.filter((r) => ['PENDING', 'IN_APPROVAL', 'OPEN'].includes(r.status)).length);

const pendingTodoCount = computed(() => props.todos.filter((t) => !t.completed).length);

const unreadMessageCount = computed(() =>
  props.conversations.reduce((sum, c) => sum + (c.unread ?? 0), 0));

// ---------- 我的申请（最近 5 条，带进度） ----------
const recentRequests = computed(() =>
  [...props.workflowRequests]
    .sort((a, b) => (b.submittedAt ?? '').localeCompare(a.submittedAt ?? ''))
    .slice(0, 5));

function requestProgress(status: string): number {
  if (['APPROVED', 'DONE', 'FINISHED'].includes(status)) return 100;
  if (['REJECTED', 'CANCELLED', 'WITHDRAWN'].includes(status)) return 100;
  if (['IN_APPROVAL', 'OPEN'].includes(status)) return 50;
  return 25; // PENDING / 待预审
}

// ---------- 本月考勤热力图 ----------
interface HeatDay {
  date: number;
  status: 'normal' | 'late' | 'absent' | 'none' | 'future' | 'weekend';
}

const heatmapDays = computed<HeatDay[]>(() => {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth();
  const daysInMonth = new Date(year, month + 1, 0).getDate();
  const resultMap = new Map<string, AttendanceDailyResultResponse>();
  props.attendanceDailyResults.forEach((r) => {
    if (r.workDate) resultMap.set(r.workDate, r);
  });
  const days: HeatDay[] = [];
  for (let d = 1; d <= daysInMonth; d += 1) {
    const date = new Date(year, month, d);
    const key = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    const dayOfWeek = date.getDay();
    if (date > now) {
      days.push({ date: d, status: 'future' });
    } else if (dayOfWeek === 0 || dayOfWeek === 6) {
      days.push({ date: d, status: 'weekend' });
    } else {
      const record = resultMap.get(key);
      if (!record) {
        days.push({ date: d, status: 'none' });
      } else if (record.exceptionFlag || (record.lateMinutes ?? 0) > 0 || (record.earlyLeaveMinutes ?? 0) > 0) {
        days.push({ date: d, status: 'late' });
      } else if ((record.absenceMinutes ?? 0) > 0) {
        days.push({ date: d, status: 'absent' });
      } else {
        days.push({ date: d, status: 'normal' });
      }
    }
  }
  return days;
});

const heatmapSummary = computed(() => {
  const normal = heatmapDays.value.filter((d) => d.status === 'normal').length;
  const late = heatmapDays.value.filter((d) => d.status === 'late').length;
  const absent = heatmapDays.value.filter((d) => d.status === 'absent').length;
  return { normal, late, absent };
});

// ---------- 假期余额环 ----------
const leaveRings = computed(() => {
  return props.leaveAccounts
    .filter((a) => a.status === 'ACTIVE')
    .slice(0, 4)
    .map((a) => {
      const available = Math.max(0, a.availableMinutes ?? 0);
      const frozen = Math.max(0, a.frozenMinutes ?? 0);
      const total = available + frozen;
      const pct = total > 0 ? Math.round((available / total) * 100) : 100;
      return {
        name: a.leaveName,
        label: minutesToDayLabel(available),
        pct,
        frozen: frozen > 0,
      };
    });
});

const ringCircumference = 2 * Math.PI * 26;
</script>

<template>
  <div class="today-view">
    <!-- 问候条 -->
    <section class="today-hero">
      <div class="today-hero-copy">
        <h2>{{ greeting }}，{{ displayName || '同学' }}</h2>
        <p>
          {{ todayLabel }}
          <span v-if="hireDays !== null">· 今天是你入职的第 {{ hireDays + 1 }} 天</span>
        </p>
      </div>
      <img src="../assets/brand/empty-workbench.jpg" alt="" aria-hidden="true" class="today-hero-art" />
    </section>

    <!-- 状态卡带 -->
    <section class="today-strip">
      <button type="button" class="strip-card tone-success" @click="emit('open-workbench', 'attendance')">
        <span class="strip-icon tone-success"><CalendarCheck2 :size="18" /></span>
        <span class="strip-copy">
          <small>今日考勤</small>
          <strong>{{ todayAttendance.label }}</strong>
          <em>{{ todayAttendance.desc }}</em>
        </span>
        <ArrowRight :size="15" class="strip-arrow" />
      </button>
      <button type="button" class="strip-card tone-primary" @click="emit('open-workbench', 'oa')">
        <span class="strip-icon tone-primary"><ClipboardList :size="18" /></span>
        <span class="strip-copy">
          <small>进行中申请</small>
          <strong>{{ pendingApprovalCount }} 件</strong>
          <em>审批进度实时同步</em>
        </span>
        <ArrowRight :size="15" class="strip-arrow" />
      </button>
      <button type="button" class="strip-card tone-warning" @click="emit('open-section', 'todos')">
        <span class="strip-icon tone-warning"><ListTodo :size="18" /></span>
        <span class="strip-copy">
          <small>我的待办</small>
          <strong>{{ pendingTodoCount }} 项</strong>
          <em>优先处理当日未完成事项</em>
        </span>
        <ArrowRight :size="15" class="strip-arrow" />
      </button>
      <button type="button" class="strip-card tone-info" @click="emit('open-section', 'messages')">
        <span class="strip-icon tone-info"><MessageSquareText :size="18" /></span>
        <span class="strip-copy">
          <small>未读消息</small>
          <strong>{{ unreadMessageCount }} 条</strong>
          <em>保持与团队同步</em>
        </span>
        <ArrowRight :size="15" class="strip-arrow" />
      </button>
    </section>

    <div class="today-grid">
      <!-- 我的申请 -->
      <section class="today-card">
        <header class="today-card-head">
          <h3>我的申请</h3>
          <button type="button" class="today-card-link" @click="emit('open-workbench', 'oa')">查看全部</button>
        </header>
        <AppEmptyState v-if="!recentRequests.length" title="暂无申请" description="发起申请后，审批进度会实时同步到这里。" icon="workbench" />
        <ul v-else class="today-request-list">
          <li v-for="request in recentRequests" :key="request.requestId">
            <div class="request-row">
              <div class="request-copy">
                <strong>{{ request.title }}</strong>
                <small>{{ request.bizTypeName || request.bizType }} · {{ formatDateTime(request.submittedAt) }}</small>
              </div>
              <AppTag :tone="toneOfStatus(request.status)">{{ request.statusName || statusText(request.status) }}</AppTag>
            </div>
            <div class="request-progress">
              <span class="request-progress-bar" :style="{ width: requestProgress(request.status) + '%' }" />
            </div>
          </li>
        </ul>
      </section>

      <!-- 本月考勤热力图 -->
      <section class="today-card">
        <header class="today-card-head">
          <h3>本月考勤</h3>
          <button type="button" class="today-card-link" @click="emit('open-workbench', 'attendance')">查看详情</button>
        </header>
        <div class="heatmap" role="img" aria-label="本月考勤热力图">
          <span
            v-for="day in heatmapDays"
            :key="day.date"
            class="heat-cell"
            :class="`heat-${day.status}`"
            :title="`${day.date}日`"
          >{{ day.date }}</span>
        </div>
        <footer class="heatmap-legend">
          <span><i class="heat-cell heat-normal" />正常 {{ heatmapSummary.normal }}</span>
          <span><i class="heat-cell heat-late" />异常 {{ heatmapSummary.late }}</span>
          <span><i class="heat-cell heat-absent" />缺卡 {{ heatmapSummary.absent }}</span>
          <span><i class="heat-cell heat-weekend" />休息</span>
        </footer>
      </section>

      <!-- 假期余额环 -->
      <section class="today-card">
        <header class="today-card-head">
          <h3>假期余额</h3>
          <button type="button" class="today-card-link" @click="emit('open-workbench', 'balance')">查看详情</button>
        </header>
        <AppEmptyState v-if="!leaveRings.length" title="暂无假期账户" icon="workbench" />
        <div v-else class="ring-grid">
          <div v-for="ring in leaveRings" :key="ring.name" class="ring-item">
            <svg viewBox="0 0 64 64" class="ring-svg">
              <circle cx="32" cy="32" r="26" class="ring-track" />
              <circle
                cx="32" cy="32" r="26"
                class="ring-value"
                :stroke-dasharray="`${(ring.pct / 100) * ringCircumference} ${ringCircumference}`"
                :class="{ frozen: ring.frozen }"
              />
              <text x="32" y="36" text-anchor="middle" class="ring-text">{{ ring.pct }}%</text>
            </svg>
            <strong>{{ ring.label }}</strong>
            <small>{{ ring.name }}</small>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.today-view {
  display: grid;
  gap: var(--space-16);
  align-content: start;
}

/* 问候条 */
.today-hero {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-16);
  padding: var(--space-20) var(--space-24);
  border-radius: var(--radius-12, 0.75rem);
  background: linear-gradient(120deg, var(--primary-600), var(--primary-500) 60%, var(--info-500));
  color: var(--neutral-0);
  overflow: hidden;
}

.today-hero-copy h2 {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 750;
}

.today-hero-copy p {
  margin: var(--space-4) 0 0;
  opacity: 0.88;
  font-size: var(--text-body-sm);
}

.today-hero-art {
  width: 8.5rem;
  height: 5.5rem;
  object-fit: cover;
  border-radius: var(--radius-8);
  opacity: 0.9;
  mix-blend-mode: luminosity;
}

/* 状态卡带 */
.today-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
  gap: var(--space-12);
}

.strip-card {
  display: flex;
  align-items: center;
  gap: var(--space-12);
  padding: var(--space-16) var(--space-16);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-10, 0.625rem);
  text-align: left;
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast), border-color var(--transition-fast);
}

.strip-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

/* 淡色渐变底，深浅色共用语义表面 token，深色模式自动转深 */
.strip-card.tone-success { background: linear-gradient(135deg, var(--surface-positive), var(--surface-panel) 80%); border-color: color-mix(in srgb, var(--success-500) 32%, var(--border-default)); }
.strip-card.tone-primary { background: linear-gradient(135deg, var(--surface-accent), var(--surface-panel) 80%); border-color: color-mix(in srgb, var(--primary-500) 32%, var(--border-default)); }
.strip-card.tone-warning { background: linear-gradient(135deg, var(--surface-warning), var(--surface-panel) 80%); border-color: color-mix(in srgb, var(--warning-500) 35%, var(--border-default)); }
.strip-card.tone-info { background: linear-gradient(135deg, var(--surface-info), var(--surface-panel) 80%); border-color: color-mix(in srgb, var(--info-500) 32%, var(--border-default)); }

.strip-icon {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border-radius: var(--radius-10, 0.625rem);
  flex: 0 0 auto;
  box-shadow: var(--shadow-xs);
}

.strip-icon.tone-success { background: var(--success-500); color: var(--neutral-0); }
.strip-icon.tone-primary { background: var(--primary-500); color: var(--neutral-0); }
.strip-icon.tone-warning { background: var(--warning-500); color: var(--neutral-0); }
.strip-icon.tone-info { background: var(--info-500); color: var(--neutral-0); }

.strip-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.strip-copy small {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.strip-copy strong {
  color: var(--text-primary);
  font-size: 1.25rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.strip-copy em {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
  font-style: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.strip-arrow {
  color: var(--text-tertiary);
  flex: 0 0 auto;
}

/* 三列卡片区 */
.today-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr));
  gap: var(--space-16);
  align-items: start;
}

.today-card {
  padding: var(--space-16) var(--space-18);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-10, 0.625rem);
  background: var(--surface-panel);
}

.today-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-12);
}

.today-card-head h3 {
  margin: 0;
  font-size: var(--text-body-lg);
  font-weight: 700;
}

.today-card-link {
  border: 0;
  background: transparent;
  color: var(--primary-600);
  font-size: var(--text-caption-md);
  cursor: pointer;
}

.today-card-link:hover {
  text-decoration: underline;
}

/* 我的申请 */
.today-request-list {
  display: grid;
  gap: var(--space-12);
  margin: 0;
  padding: 0;
  list-style: none;
}

.request-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-8);
}

.request-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.request-copy strong {
  color: var(--text-primary);
  font-size: var(--text-body-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request-copy small {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.request-progress {
  margin-top: var(--space-6, 0.375rem);
  height: 0.25rem;
  border-radius: 999px;
  background: var(--surface-panel-strong, var(--neutral-100, #f1f5f9));
  overflow: hidden;
}

.request-progress-bar {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--primary-500), var(--info-500, #0ea5e9));
  transition: width 0.4s ease;
}

/* 考勤热力图 */
.heatmap {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: var(--space-4);
}

.heat-cell {
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  border-radius: var(--radius-4, 0.25rem);
  font-size: 0.625rem;
  color: transparent;
}

.heat-normal { background: var(--success-500, #22c55e); }
.heat-late { background: var(--warning-500, #f59e0b); }
.heat-absent { background: var(--danger-500, #ef4444); }
.heat-weekend { background: var(--surface-panel-strong); }
.heat-none { background: var(--surface-panel-muted); outline: 1px dashed var(--border-default); }
.heat-future { background: transparent; outline: 1px dashed var(--border-subtle); }

.heatmap-legend {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-12);
  margin-top: var(--space-12);
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.heatmap-legend span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
}

.heatmap-legend .heat-cell {
  width: 0.625rem;
  height: 0.625rem;
  aspect-ratio: auto;
}

/* 假期余额环 */
.ring-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(6.5rem, 1fr));
  gap: var(--space-12);
}

.ring-item {
  display: grid;
  justify-items: center;
  gap: var(--space-4);
}

.ring-svg {
  width: 4rem;
  height: 4rem;
  transform: rotate(-90deg);
}

.ring-track {
  fill: none;
  stroke: var(--surface-panel-strong);
  stroke-width: 7;
}

.ring-value {
  fill: none;
  stroke: var(--primary-500);
  stroke-width: 7;
  stroke-linecap: round;
  transition: stroke-dasharray 0.5s ease;
}

.ring-value.frozen {
  stroke: var(--warning-500, #f59e0b);
}

.ring-text {
  fill: var(--text-primary);
  font-size: 0.9375rem;
  font-weight: 750;
  transform: rotate(90deg);
  transform-origin: center;
}

.ring-item strong {
  color: var(--text-primary);
  font-size: var(--text-body-sm);
}

.ring-item small {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}
</style>
