<script setup lang="ts">
/**
 * WorkbenchView：工作台（对齐画布「员工端-新工作台」3:1285，批次⑪ 重做）。
 * 画布结构：问候区（标题30px+副标题，右侧指标卡×4 高88 r18）→
 * 行A 290（今日考勤[打卡块+操作按钮+考勤备注] + 本周出勤[柱状图 周一~周日 今天高亮]）→
 * 行B 240（假期余额[进度条] + 审批进行中[状态标签]）→
 * 行C fill（今日日程[时间+事件]）；
 * 右栏 336（Agent卡 hug + 通知卡弹性 + 快捷入口 104 钉底）。
 * 布局契约：1440×900/760 无页面滚动，行C 弹性吸收剩余高度。
 */
import { computed, onMounted, ref } from 'vue';
import {
  CalendarCheck, CalendarDays, ChevronRight, Clock, Inbox, Palmtree,
  Receipt, Send, Sparkles, Timer, Wrench, Stamp,
} from 'lucide-vue-next';
import { useSessionStore } from '@/stores/session';
import { useImStore } from '@/stores/im';
import { attendanceApi, leaveApi, notificationApi, workflowApi } from '@/api';
import type {
  AttendanceDailyResultResponse,
  AttendanceScheduleResponse,
  LeaveAccountResponse,
  SysNotificationResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTodoResponse,
} from '@/api/types';
import { useToast } from '@/composables/useToast';
import HubToast from '@/components/base/HubToast.vue';

const session = useSessionStore();
const im = useImStore();
const { message: toastMessage, show: showToast } = useToast();

/* ---------- 日期工具 ---------- */
const pad = (n: number) => String(n).padStart(2, '0');
const iso = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const today = new Date();
const todayIso = iso(today);
const weekStart = (() => {
  const d = new Date(today);
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  return d;
})();
const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日'];

/* ---------- 数据 ---------- */
const dailyResults = ref<AttendanceDailyResultResponse[]>([]);
const schedules = ref<AttendanceScheduleResponse[]>([]);
const leaveAccounts = ref<LeaveAccountResponse[]>([]);
const myRequests = ref<WorkflowRequestSummaryResponse[]>([]);
const todos = ref<WorkflowTodoResponse[]>([]);
const notifications = ref<SysNotificationResponse[]>([]);
const loading = ref(true);

async function loadAll() {
  const [daily, scheduleList, leave, requests, todoList, notifPage, convs] = await Promise.allSettled([
    attendanceApi.dailyResults({ startDate: iso(weekStart), endDate: todayIso }),
    attendanceApi.schedules({ startDate: todayIso, endDate: todayIso }),
    leaveApi.accounts(),
    workflowApi.requests({ pageNo: 1, pageSize: 5 }),
    workflowApi.todos(),
    notificationApi.list({ pageNo: 1, pageSize: 5 }),
    im.loadConversations(),
  ]);
  if (daily.status === 'fulfilled') dailyResults.value = daily.value;
  if (scheduleList.status === 'fulfilled') schedules.value = scheduleList.value;
  if (leave.status === 'fulfilled') leaveAccounts.value = leave.value;
  if (requests.status === 'fulfilled') myRequests.value = requests.value.list ?? [];
  if (todoList.status === 'fulfilled') todos.value = todoList.value;
  if (notifPage.status === 'fulfilled') {
    // 兼容 PageResult 与裸数组两种返回
    const raw = notifPage.value;
    notifications.value = Array.isArray(raw) ? raw : (raw.list ?? []);
  }
  loading.value = false;
}
onMounted(loadAll);

/* ---------- 问候区（画布：标题30px + 副标题含待办数） ---------- */
const greeting = computed(() => {
  const h = today.getHours();
  const part = h < 6 ? '夜深了' : h < 12 ? '早上好' : h < 14 ? '中午好' : h < 18 ? '下午好' : '晚上好';
  return `${part}，${session.displayName || '同事'}！`;
});
const dateSub = computed(() => {
  const base = `星期${WEEK_LABELS[(today.getDay() + 6) % 7]}，${today.getMonth() + 1}月${today.getDate()}日`;
  const n = todos.value.length;
  return n ? `${base} · 今天有 ${n} 项审批待你处理` : `${base} · 今天没有待处理的审批`;
});

/* ---------- 指标卡（画布：出勤率/可用年假/待办审批/未读消息） ---------- */
const workedDays = computed(() => dailyResults.value.filter((r) => (r.workMinutes ?? 0) > 0).length);
const shouldWorkDays = computed(() => {
  // 本周至今的工作日数（周一~周五，含今天）
  let count = 0;
  for (let i = 0; i < 7; i += 1) {
    const d = new Date(weekStart);
    d.setDate(d.getDate() + i);
    if (iso(d) > todayIso) break;
    if (d.getDay() >= 1 && d.getDay() <= 5) count += 1;
  }
  return Math.max(count, 1);
});
const attendanceRate = computed(() => Math.min(100, Math.round((workedDays.value / shouldWorkDays.value) * 100)));
const annualLeave = computed(() => {
  const acc = leaveAccounts.value.find((a) => a.leaveCode === 'ANNUAL' || a.leaveName.includes('年假'));
  return acc ? (acc.availableMinutes / 480).toFixed(1) : '—';
});
const unreadTotal = computed(() =>
  im.conversations.reduce((sum, c) => sum + (c.mute ? 0 : c.unreadCount), 0));
const metrics = computed(() => [
  { id: 'rate', label: '本周出勤率', value: `${attendanceRate.value}`, unit: '%', icon: CalendarCheck },
  { id: 'annual', label: '可用年假', value: annualLeave.value, unit: '天', icon: Palmtree },
  { id: 'todos', label: '待办审批', value: String(todos.value.length), unit: '项', icon: Inbox },
  { id: 'unread', label: '未读消息', value: String(unreadTotal.value), unit: '条', icon: Clock },
]);

/* ---------- 行A：今日考勤 ---------- */
const todayResult = computed(() => dailyResults.value.find((r) => r.workDate === todayIso) ?? null);
const todayHours = computed(() => ((todayResult.value?.workMinutes ?? 0) / 60).toFixed(1));
const punchTime = (isoText?: string | null) => (isoText ? isoText.slice(11, 16) : '--:--');
const firstPunch = computed(() => punchTime(todayResult.value?.firstPunchAt));
const lastPunch = computed(() => punchTime(todayResult.value?.lastPunchAt));
const todayLabel = computed(() =>
  `${today.getMonth() + 1}月${today.getDate()}日 周${WEEK_LABELS[(today.getDay() + 6) % 7]}`);
const exceptionCount = computed(() => dailyResults.value.filter((r) => r.exceptionFlag).length);
const punching = ref(false);
async function punchOut() {
  if (punching.value) return;
  punching.value = true;
  try {
    await attendanceApi.punch('OUT');
    showToast('下班打卡成功');
    await loadAll();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '打卡失败');
  } finally {
    punching.value = false;
  }
}

/* ---------- 行A：本周出勤柱状图（画布：周一~周日 bars，今天高亮） ---------- */
const weekBars = computed(() => {
  const map = new Map(dailyResults.value.map((r) => [r.workDate, r]));
  const cells = WEEK_LABELS.map((label, i) => {
    const d = new Date(weekStart);
    d.setDate(d.getDate() + i);
    const key = iso(d);
    const hours = (map.get(key)?.workMinutes ?? 0) / 60;
    return { label: `周${label}`, key, hours, isToday: key === todayIso, future: key > todayIso };
  });
  const max = Math.max(...cells.map((c) => c.hours), 1);
  return cells.map((c) => ({ ...c, pct: Math.round((c.hours / max) * 100) }));
});
const avgHours = computed(() => {
  const worked = dailyResults.value.filter((r) => (r.workMinutes ?? 0) > 0);
  if (!worked.length) return '—';
  return (worked.reduce((s, r) => s + (r.workMinutes ?? 0), 0) / worked.length / 60).toFixed(1);
});

/* ---------- 行B：假期余额（#14 联调：有总额度用真比例，否则退回相对比例） ---------- */
const leaveRows = computed(() => {
  const rows = leaveAccounts.value.slice(0, 3).map((a) => ({
    id: String(a.accountId),
    name: a.leaveName,
    days: a.availableMinutes / 480,
    entitlement: a.entitlementMinutes ? a.entitlementMinutes / 480 : null,
  }));
  const max = Math.max(...rows.map((r) => r.days), 1);
  return rows.map((r) => ({
    ...r,
    text: r.entitlement ? `${r.days.toFixed(1)}/${r.entitlement.toFixed(1)}` : r.days.toFixed(1),
    pct: r.entitlement
      ? Math.min(100, Math.round((r.days / r.entitlement) * 100))
      : Math.round((r.days / max) * 100),
  }));
});

/* ---------- 行B：审批进行中（画布：状态标签） ---------- */
const STATUS_TEXT: Record<string, string> = {
  RUNNING: '审批中', APPROVED: '已通过', REJECTED: '已驳回',
  WITHDRAWN: '已撤回', CANCELED: '已撤销', DRAFT: '草稿',
};
const requestRows = computed(() =>
  myRequests.value.slice(0, 3).map((r) => ({
    id: String(r.requestId),
    title: r.title || r.bizTypeName || '申请',
    status: r.statusName || STATUS_TEXT[(r.status || '').toUpperCase()] || '审批中',
    running: (r.status || '').toUpperCase() === 'RUNNING',
    time: (r.submittedAt || '').slice(5, 16),
  })));

/* ---------- 行C：今日日程（#9 联调：排班班次；#13 日程事件后端未做） ---------- */
const todaySchedule = computed(() => schedules.value.find((s) => s.workDate === todayIso) ?? null);

/* ---------- 通知卡（#10 联调：通知中心，不再拿 todos 冒充） ---------- */
const NOTIF_TYPE: Record<string, string> = {
  APPROVAL: '审批', SYSTEM: '系统', ATTENDANCE: '考勤', ANNOUNCEMENT: '公告',
};
const notifyRows = computed(() =>
  notifications.value.slice(0, 3).map((n) => ({
    id: String(n.notificationId),
    title: n.title,
    tag: NOTIF_TYPE[n.type] ?? '通知',
    time: (n.createdAt || '').slice(5, 16).replace('T', ' '),
    read: n.read,
  })));
async function readAllNotifications() {
  try {
    await notificationApi.markAllRead();
    notifications.value = notifications.value.map((n) => ({ ...n, read: true }));
    showToast('全部已读');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  }
}

/* ---------- 快捷入口（画布：申请请假/费用报销/申请加班/申请用印） ---------- */
const quickEntries = [
  { id: 'leave', label: '申请请假', icon: CalendarDays },
  { id: 'expense', label: '费用报销', icon: Receipt },
  { id: 'overtime', label: '申请加班', icon: Timer },
  { id: 'seal', label: '申请用印', icon: Stamp },
];
function onQuick(id: string) {
  showToast(`「${quickEntries.find((q) => q.id === id)?.label}」表单即将上线（见接口需求表 #4）`);
}

/* ---------- Agent 卡 ---------- */
const agentDraft = ref('');
const agentQuestions = ['我的年假还剩几天？', '差旅报销制度怎么查？'];
function askAgent() {
  if (!agentDraft.value.trim()) return;
  showToast('Agent 会话接通中，请先在消息页与 AI 助手对话');
  agentDraft.value = '';
}
function askQuestion(q: string) {
  agentDraft.value = q;
}
</script>

<template>
  <div class="wb-page">
    <!-- 问候区（画布：左问候文案 + 右指标卡×4 同一行） -->
    <header class="wb-greeting">
      <div class="wb-greeting-copy">
        <h1>{{ greeting }}</h1>
        <p>{{ dateSub }}</p>
      </div>
      <div class="wb-metrics">
        <div v-for="m in metrics" :key="m.id" class="wb-metric">
          <span class="wb-metric-icon"><component :is="m.icon" :size="18" :stroke-width="2" /></span>
          <span class="wb-metric-copy">
            <small>{{ m.label }}</small>
            <strong>{{ m.value }}<em>{{ m.unit }}</em></strong>
          </span>
        </div>
      </div>
    </header>

    <!-- 主区：左内容列 + 右栏 336 -->
    <div class="wb-main">
      <div class="wb-col">
        <!-- 行A 290：今日考勤 + 本周出勤 -->
        <section class="wb-row wb-row-a">
          <div class="wb-card wb-card-attendance">
            <div class="wb-card-head">
              <h3 class="wb-card-title">今日考勤</h3>
              <small class="wb-card-head-side">{{ todayLabel }}</small>
            </div>
            <div class="wb-punch-row">
              <div class="wb-punch">
                <small>上班打卡</small>
                <strong>{{ firstPunch }}</strong>
                <em>{{ todayResult?.firstPunchAt ? '首签' : '未打卡' }}</em>
              </div>
              <div class="wb-punch">
                <small>下班打卡</small>
                <strong>{{ lastPunch }}</strong>
                <em>{{ todayResult?.lastPunchAt ? '末签' : '未打卡' }}</em>
              </div>
            </div>
            <div class="wb-attend-actions">
              <button type="button" class="wb-btn-primary" :disabled="punching" @click="punchOut">
                {{ punching ? '打卡中…' : '下班打卡' }}
              </button>
              <button type="button" class="wb-btn-ghost" @click="onQuick('punch')">
                <Wrench :size="14" :stroke-width="2" />补卡申请
              </button>
            </div>
            <p class="wb-card-note">
              今日已计工时 <b>{{ todayHours }}h</b> · 本月异常 {{ exceptionCount }} 次
            </p>
          </div>
          <div class="wb-card wb-card-week">
            <div class="wb-card-head">
              <h3 class="wb-card-title">本周出勤</h3>
              <small class="wb-card-head-side">日均 {{ avgHours }}h</small>
            </div>
            <div class="wb-chart">
              <div
                v-for="b in weekBars" :key="b.key"
                class="wb-bar-col" :class="{ today: b.isToday, future: b.future }"
              >
                <span class="wb-bar-value">{{ b.hours > 0 ? `${b.hours.toFixed(1)}` : '' }}</span>
                <span class="wb-bar" :style="{ height: `${Math.max(b.pct, 4)}%` }" />
                <small>{{ b.label }}</small>
              </div>
            </div>
          </div>
        </section>

        <!-- 行B 240：假期余额 + 审批进行中 -->
        <section class="wb-row wb-row-b">
          <div class="wb-card wb-card-leave">
            <div class="wb-card-head">
              <h3 class="wb-card-title">我的假期</h3>
              <button type="button" class="wb-link" @click="onQuick('leave')">申请请假</button>
            </div>
            <div v-if="leaveRows.length" class="wb-leave-list">
              <div v-for="l in leaveRows" :key="l.id" class="wb-leave-item">
                <div class="wb-leave-label">
                  <span>{{ l.name }}</span>
                  <strong>{{ l.text }} 天</strong>
                </div>
                <div class="wb-leave-track"><i :style="{ width: `${l.pct}%` }" /></div>
              </div>
            </div>
            <p v-else class="wb-empty">暂无假期账户数据</p>
          </div>
          <div class="wb-card wb-card-requests">
            <div class="wb-card-head">
              <h3 class="wb-card-title">我的申请</h3>
              <button type="button" class="wb-link" @click="showToast('审批中心即将上线')">
                全部申请<ChevronRight :size="12" :stroke-width="2" />
              </button>
            </div>
            <div v-if="requestRows.length" class="wb-req-list">
              <div v-for="r in requestRows" :key="r.id" class="wb-req-item">
                <div class="wb-req-copy">
                  <strong>{{ r.title }}</strong>
                  <small>{{ r.time }}</small>
                </div>
                <em class="wb-status" :class="{ running: r.running }">{{ r.status }}</em>
              </div>
            </div>
            <p v-else class="wb-empty">暂无发起的申请</p>
          </div>
        </section>

        <!-- 行C fill：今日日程 -->
        <section class="wb-row wb-row-c">
          <div class="wb-card wb-card-schedule">
            <div class="wb-card-head">
              <h3 class="wb-card-title">今日日程</h3>
              <button type="button" class="wb-link" @click="showToast('考勤页即将上线')">查看考勤</button>
            </div>
            <div v-if="todaySchedule?.isWorkday && todaySchedule.startTime" class="wb-schedule-today">
              <div class="wb-shift-hero">
                <small>今日班次</small>
                <strong>{{ todaySchedule.shiftName || '标准班' }}</strong>
                <em>{{ todaySchedule.startTime }} ~ {{ todaySchedule.endTime || '?' }}</em>
              </div>
            </div>
            <div v-else class="wb-schedule-empty">
              <p class="wb-empty">今日{{ todaySchedule && !todaySchedule.isWorkday ? '休息，无班次安排' : '暂无日程安排' }}</p>
              <small>日程事件接口后端未做（需求表 #13），接入后此处按时间线展示会议</small>
            </div>
          </div>
        </section>
      </div>

      <!-- 右栏 336 -->
      <aside class="wb-side">
        <div class="wb-card wb-card-agent">
          <div class="wb-agent-head">
            <span class="wb-agent-icon"><Sparkles :size="16" :stroke-width="2" /></span>
            <h3 class="wb-card-title">Workforce Agent</h3>
            <em class="wb-agent-online">在线</em>
          </div>
          <p class="wb-agent-desc">我是你的智能工作助手，可以帮你查假期、问制度、跟进审批进度。</p>
          <div class="wb-agent-questions">
            <button
              v-for="q in agentQuestions" :key="q" type="button"
              class="wb-agent-q" @click="askQuestion(q)"
            >{{ q }}</button>
          </div>
          <div class="wb-agent-input">
            <input
              v-model="agentDraft" type="text" placeholder="向 Agent 提问…"
              @keydown.enter="askAgent"
            />
            <button type="button" aria-label="发送" @click="askAgent">
              <Send :size="14" :stroke-width="2" />
            </button>
          </div>
        </div>
        <div class="wb-card wb-card-notify">
          <div class="wb-card-head">
            <h3 class="wb-card-title">通知</h3>
            <small class="wb-card-head-side">实时</small>
          </div>
          <div v-if="notifyRows.length" class="wb-notify-list">
            <div v-for="n in notifyRows" :key="n.id" class="wb-notify-item">
              <span class="wb-notify-dot" :class="{ read: n.read }" />
              <div class="wb-notify-copy">
                <strong>{{ n.title }}<em class="wb-notify-tag">{{ n.tag }}</em></strong>
                <small>{{ n.time }}</small>
              </div>
            </div>
          </div>
          <p v-else class="wb-empty">暂无新通知</p>
          <button type="button" class="wb-link wb-notify-all" @click="readAllNotifications">
            全部已读<ChevronRight :size="12" :stroke-width="2" />
          </button>
        </div>
        <div class="wb-card wb-card-quick">
          <div class="wb-quick-grid">
            <button
              v-for="q in quickEntries" :key="q.id" type="button"
              class="wb-quick-item" @click="onQuick(q.id)"
            >
              <component :is="q.icon" :size="18" :stroke-width="2" />
              <span>{{ q.label }}</span>
            </button>
          </div>
        </div>
      </aside>
    </div>

    <HubToast :message="toastMessage" />
  </div>
</template>

<style>
@import '@/styles/workbench.css';
</style>
