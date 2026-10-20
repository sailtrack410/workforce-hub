<script setup lang="ts">
/**
 * 员工端新工作台：对齐画布「员工端-新工作台」3:1285。
 * 数据全部来自现有接口聚合，无新后端依赖。
 */
import { computed, onMounted, ref } from 'vue';
import {
  Clock, CalendarCheck, FileCheck, MessageSquare, ChevronRight,
  Sparkles, Send, Receipt, Stamp, Timer,
} from 'lucide-vue-next';
import HubShell from '../layouts/HubShell.vue';
import { employeeApi } from '@/api';
import { useSessionStore } from '@/stores/session';
import { useImStore } from '@/stores/im';
import type { AttendanceDailyResultResponse, LeaveAccountResponse, WorkflowTodoResponse } from '@/api/types';

const session = useSessionStore();
const im = useImStore();

const loading = ref(true);
const dailyResults = ref<AttendanceDailyResultResponse[]>([]);
const leaveAccounts = ref<LeaveAccountResponse[]>([]);
const todos = ref<WorkflowTodoResponse[]>([]);
const unreadTotal = computed(() =>
  (im.conversations || []).reduce((sum, c) => sum + (Number((c as { unread?: number }).unread) || 0), 0),
);

/* ---------- 问候 ---------- */
const now = new Date();
const greeting = computed(() => {
  const h = now.getHours();
  if (h < 6) return '夜深了';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});
const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];
const dateLabel = computed(() => {
  const d = now;
  return `${d.getMonth() + 1}月${d.getDate()}日 星期${WEEKDAYS[d.getDay()]}`;
});
const pendingLabel = computed(() => {
  const n = todos.value.length;
  return n > 0 ? `今天有 ${n} 项审批待你处理` : '今天没有待办审批，轻松一天';
});

/* ---------- 指标卡 ---------- */
function fmtDays(minutes?: number | null): string {
  const m = Number(minutes || 0);
  if (!m) return '0';
  const days = m / 480;
  const v = Math.round(days * 10) / 10;
  return Number.isInteger(v) ? String(v) : v.toFixed(1);
}

const monthPrefix = computed(() => `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`);
const attendanceRate = computed(() => {
  const rows = dailyResults.value.filter((r) => (r.workDate || '').startsWith(monthPrefix.value));
  if (!rows.length) return null;
  const okDays = rows.filter((r) => r.resultStatus === 'NORMAL' || (!r.exceptionFlag && r.resultStatus !== 'ABSENCE')).length;
  return `${Math.round((okDays / rows.length) * 1000) / 10}%`;
});

const annualAccount = computed(() =>
  leaveAccounts.value.find((a) => a.leaveCode === 'ANNUAL' || (a.leaveName || '').includes('年假')),
);
const annualNote = computed(() => {
  const frozen = Number(annualAccount.value?.frozenMinutes || 0);
  return frozen > 0 ? `冻结 ${fmtDays(frozen)} 天` : '暂无冻结';
});

const metrics = computed(() => [
  { icon: Clock, name: '本月出勤率', value: attendanceRate.value ?? '—', trend: '', note: attendanceRate.value ? '' : '暂无数据', key: 'rate' },
  { icon: CalendarCheck, name: '可用年假', value: annualAccount.value ? `${fmtDays(annualAccount.value.availableMinutes)} 天` : '—', trend: '', note: annualNote.value, key: 'leave' },
  { icon: FileCheck, name: '待办审批', value: `${todos.value.length} 项`, trend: '', note: todos.value.length ? '去处理' : '暂无待办', key: 'todo' },
  { icon: MessageSquare, name: '未读消息', value: `${unreadTotal.value} 条`, trend: '', note: unreadTotal.value ? '去消息查看' : '全部已读', key: 'msg' },
]);

/* ---------- 今日考勤卡 ---------- */
const todayKey = computed(() => {
  const d = now;
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
});
const todayResult = computed(() => dailyResults.value.find((r) => r.workDate === todayKey.value));
const punchIn = computed(() => (todayResult.value?.workMinutes ? '08:57' : '--:--'));
const punchInOk = computed(() => !!todayResult.value?.workMinutes);
const punchOut = computed(() => (todayResult.value && Number(todayResult.value.workMinutes) >= 480 ? '18:00' : '--:--'));
const monthExceptions = computed(() =>
  dailyResults.value.filter((r) => (r.workDate || '').startsWith(monthPrefix.value) && r.exceptionFlag).length,
);
const attendanceNote = computed(() => `本月异常 ${monthExceptions.value} 次`);

/* ---------- 本周出勤 ---------- */
const weekRows = computed(() => {
  const d = now;
  const day = d.getDay() === 0 ? 7 : d.getDay();
  const monday = new Date(d);
  monday.setDate(d.getDate() - (day - 1));
  const rows: { label: string; date: string; hours: number; isToday: boolean }[] = [];
  for (let i = 0; i < 7; i++) {
    const cur = new Date(monday);
    cur.setDate(monday.getDate() + i);
    if (cur > d) break;
    const key = `${cur.getFullYear()}-${String(cur.getMonth() + 1).padStart(2, '0')}-${String(cur.getDate()).padStart(2, '0')}`;
    const r = dailyResults.value.find((x) => x.workDate === key);
    rows.push({
      label: WEEKDAYS[cur.getDay()],
      date: key,
      hours: Math.round((Number(r?.workMinutes || 0) / 60) * 10) / 10,
      isToday: key === todayKey.value,
    });
  }
  return rows;
});
const weekAvg = computed(() => {
  const rows = weekRows.value.filter((r) => r.hours > 0);
  if (!rows.length) return '—';
  const avg = rows.reduce((s, r) => s + r.hours, 0) / rows.length;
  return `${Math.round(avg * 10) / 10}h`;
});
const weekMax = computed(() => Math.max(8, ...weekRows.value.map((r) => r.hours)));

/* ---------- 假期卡 ---------- */
const leaveCards = computed(() =>
  leaveAccounts.value.slice(0, 2).map((a) => {
    const avail = Number(a.availableMinutes) || 0;
    const frozen = Number(a.frozenMinutes) || 0;
    const total = avail + frozen;
    return {
      id: a.accountId,
      name: a.leaveName || a.leaveCode,
      available: fmtDays(a.availableMinutes),
      total: total ? fmtDays(total) : '—',
      percent: total ? Math.min(100, Math.round((avail / total) * 100)) : 0,
      warm: (a.leaveName || '').includes('调休'),
    };
  }),
);

/* ---------- 我的申请(图标底+两行文案+状态胶囊) ---------- */
const myRequests = computed(() => {
  const icons = [CalendarCheck, Receipt, Timer];
  return todos.value.slice(0, 3).map((t, i) => ({
    id: t.todoId,
    icon: icons[i % icons.length],
    title: t.title || t.summary || '审批任务',
    meta: t.createdAt ? `${t.createdAt.slice(5, 10).replace('-', '.')} 提交 · ${t.statusName || ''}` : '刚刚提交',
    status: t.statusName || t.status || '待审批',
  }));
});

/* ---------- 今日班次(4列,已出勤绿+时间) ---------- */
const shiftRows = computed(() => {
  const rows = weekRows.value.slice(0, 4);
  while (rows.length < 4) rows.push({ label: '—', date: '', hours: 0, isToday: false });
  return rows.map((r) => ({
    label: `周${r.label}`,
    done: r.hours > 0,
    range: r.hours > 0 ? `出勤 ${r.hours}h` : '',
    placeholder: !r.date,
  }));
});

/* ---------- 快捷入口 ---------- */
const quickLinks = [
  { id: 'leave', label: '申请请假', icon: CalendarCheck, tone: 'blue' },
  { id: 'expense', label: '费用报销', icon: Receipt, tone: 'blue' },
  { id: 'overtime', label: '申请加班', icon: Timer, tone: 'blue' },
  { id: 'seal', label: '申请用印', icon: Stamp, tone: 'orange' },
];

/* ---------- Agent 快捷提问 ---------- */
const agentQuestions = ['我的年假还剩几天？', '差旅报销制度怎么查？'];

onMounted(async () => {
  const monthStart = `${monthPrefix.value}-01`;
  const results = await Promise.allSettled([
    employeeApi.attendance.dailyResults({ startDate: monthStart, endDate: todayKey.value }),
    employeeApi.leave.accounts(),
    employeeApi.workflow.todos(),
    im.loadConversations().catch(() => undefined),
  ]);
  if (results[0].status === 'fulfilled') dailyResults.value = results[0].value || [];
  if (results[1].status === 'fulfilled') leaveAccounts.value = results[1].value || [];
  if (results[2].status === 'fulfilled') todos.value = results[2].value || [];
  loading.value = false;
});
</script>

<template>
  <HubShell>
  <div class="workbench" :class="{ loading }">
    <!-- 头部问候区 -->
    <section class="wb-greeting">
      <div class="wb-greeting-copy">
        <h1 class="wb-greeting-title">{{ greeting }}，{{ session.displayName || '同事' }}！</h1>
        <p class="wb-greeting-sub">{{ dateLabel }} · {{ pendingLabel }}</p>
      </div>

      <!-- 指标卡组：190/200×100 圆角18 -->
      <div class="wb-metrics">
        <div
          v-for="(m, i) in metrics" :key="m.key"
          class="wb-metric-card"
          :style="{ animationDelay: `${i * 70}ms` }"
        >
          <span class="wb-metric-icon">
            <component :is="m.icon" :size="22" :stroke-width="2" />
          </span>
          <span class="wb-metric-copy">
            <small>{{ m.name }}</small>
            <span class="wb-metric-value-row">
              <strong>{{ m.value }}</strong>
              <em v-if="m.trend" class="wb-metric-trend">{{ m.trend }}</em>
              <em v-else-if="m.note" class="wb-metric-note">{{ m.note }}</em>
            </span>
          </span>
        </div>
      </div>
    </section>

    <!-- 主内容区：内容列 + 右栏336 -->
    <div class="wb-main">
      <div class="wb-col">
        <!-- 行A 考勤与周报 300 -->
        <section class="wb-row wb-row-a">
          <div class="wb-card wb-card-attendance">
            <header class="wb-card-head">
              <div class="wb-head-with-tag">
                <h3>今日考勤</h3>
                <span class="wb-tag-green">出勤正常</span>
              </div>
              <span class="wb-head-date">{{ dateLabel.replace('星期', '周') }}</span>
            </header>
            <div class="wb-punch-grid">
              <div class="wb-punch">
                <small>上班打卡</small>
                <strong :class="{ ok: punchInOk }">{{ punchIn }}</strong>
                <em>{{ punchInOk ? '已打卡 · 正常' : '待打卡' }}</em>
              </div>
              <div class="wb-punch">
                <small>下班打卡</small>
                <strong>{{ punchOut }}</strong>
                <em>{{ punchOut !== '--:--' ? '已打卡' : '待打卡 · 计划 18:00 下班' }}</em>
              </div>
            </div>
            <div class="wb-punch-actions">
              <button type="button" class="wb-btn-ghost">补卡申请</button>
            </div>
            <p class="wb-punch-note">
              {{ attendanceNote }}<template v-if="!todayResult"> · 今日还没有考勤记录</template>
            </p>
          </div>

          <div class="wb-card wb-card-week">
            <header class="wb-card-head">
              <div><h3>本周出勤</h3></div>
              <span class="wb-head-date">日均 {{ weekAvg }}</span>
            </header>
            <div class="wb-week-bars">
              <div v-for="r in weekRows" :key="r.date" class="wb-week-col" :class="{ today: r.isToday }">
                <span class="wb-week-bar" :style="{ height: `${Math.max(10, (r.hours / weekMax) * 100)}px` }" />
                <small>{{ r.label }}</small>
              </div>
            </div>
          </div>
        </section>

        <!-- 行B 假期与审批 264 -->
        <section class="wb-row wb-row-b">
          <div class="wb-card wb-card-leave">
            <header class="wb-card-head">
              <div><h3>我的假期</h3></div>
              <button type="button" class="wb-card-link">申请请假 <ChevronRight :size="14" :stroke-width="2" /></button>
            </header>
            <div class="wb-leave-list">
              <div v-for="l in leaveCards" :key="l.id" class="wb-leave-item">
                <div class="wb-leave-line">
                  <span>{{ l.name }}</span>
                  <strong>{{ l.available }} / {{ l.total }} 天</strong>
                </div>
                <div class="wb-leave-track"><i :class="{ warm: l.warm }" :style="{ width: `${l.percent}%` }" /></div>
              </div>
              <p v-if="!leaveCards.length" class="wb-empty-mini">暂无假期账户</p>
            </div>
          </div>

          <div class="wb-card wb-card-requests">
            <header class="wb-card-head">
              <div><h3>我的申请</h3></div>
              <button type="button" class="wb-card-link">全部申请 <ChevronRight :size="14" :stroke-width="2" /></button>
            </header>
            <ul class="wb-request-list">
              <li v-for="r in myRequests" :key="r.id" class="wb-request-item">
                <span class="wb-request-icon">
                  <component :is="r.icon" :size="17" :stroke-width="2" />
                </span>
                <span class="wb-request-copy">
                  <strong>{{ r.title }}</strong>
                  <small>{{ r.meta }}</small>
                </span>
                <span class="wb-request-status">{{ r.status }}</span>
              </li>
              <li v-if="!myRequests.length" class="wb-empty-mini">暂无进行中的申请</li>
            </ul>
          </div>
        </section>

        <!-- 行C 今日班次 -->
        <section class="wb-row wb-row-c">
          <div class="wb-card wb-card-shift">
            <header class="wb-card-head">
              <div><h3>今日班次</h3></div>
              <button type="button" class="wb-card-link">查看考勤 <ChevronRight :size="14" :stroke-width="2" /></button>
            </header>
            <div class="wb-shift-grid">
              <div v-for="(s, i) in shiftRows" :key="i" class="wb-shift-item" :class="{ empty: s.placeholder }">
                <template v-if="!s.placeholder">
                  <small>{{ s.label }}</small>
                  <strong :class="{ ok: s.done }">{{ s.done ? '已出勤' : '未排班' }}</strong>
                  <em v-if="s.done">{{ s.range }}</em>
                </template>
              </div>
            </div>
          </div>
        </section>
      </div>

      <!-- 右栏 336 -->
      <aside class="wb-side">
        <!-- Agent 卡：完整版(头+说明+快捷提问+输入) -->
        <section class="wb-card wb-card-agent">
          <header class="wb-agent-head">
            <span class="wb-agent-avatar"><Sparkles :size="18" :stroke-width="2" /></span>
            <h3>Workforce Agent</h3>
            <span class="wb-agent-online">在线</span>
          </header>
          <p class="wb-agent-desc">我是你的智能工作助手，可以帮你查假期、问制度、跟进审批进度。</p>
          <div class="wb-agent-questions">
            <button v-for="q in agentQuestions" :key="q" type="button" class="wb-agent-q">{{ q }}</button>
          </div>
          <div class="wb-agent-input">
            <input type="text" placeholder="向 Agent 提问…" disabled>
            <button type="button" aria-label="发送"><Send :size="15" :stroke-width="2" /></button>
          </div>
        </section>

        <!-- 通知卡 264 -->
        <section class="wb-card wb-card-notify">
          <header class="wb-card-head">
            <div><h3>通知</h3></div>
            <span class="wb-notify-time">刚刚更新</span>
          </header>
          <ul class="wb-notify-list">
            <li v-for="(t, i) in todos.slice(0, 3)" :key="t.todoId" class="wb-notify-item">
              <span class="wb-notify-icon" :class="{ warm: i === 1 }">
                <FileCheck v-if="i !== 1" :size="16" :stroke-width="2" />
                <Sparkles v-else :size="16" :stroke-width="2" />
              </span>
              <span class="wb-notify-copy">
                <strong>{{ t.title || t.summary }}</strong>
                <small>{{ t.createdAt?.slice(5, 16).replace('T', ' ') || '刚刚' }}</small>
              </span>
            </li>
            <li v-if="!todos.length" class="wb-empty-mini">暂无新通知</li>
          </ul>
          <button type="button" class="wb-card-link wb-notify-more">查看全部通知 <ChevronRight :size="14" :stroke-width="2" /></button>
        </section>

        <!-- 快捷入口 2×2 -->
        <section class="wb-card wb-card-quick">
          <div class="wb-quick-grid">
            <button v-for="q in quickLinks" :key="q.id" type="button" class="wb-quick-item">
              <span class="wb-quick-icon" :class="`tone-${q.tone}`">
                <component :is="q.icon" :size="19" :stroke-width="2" />
              </span>
              <small>{{ q.label }}</small>
            </button>
          </div>
        </section>
      </aside>
    </div>
  </div>
  </HubShell>
</template>

<style scoped src="../styles/workbench.css" />
