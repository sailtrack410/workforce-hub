<script setup lang="ts">
/**
 * WorkbenchView：个人任务首页。
 * 问候区 → 快捷应用 → 我的申请 / 我的待办 / 我的任务 → 近期安排。
 * 一级导航由 HubShell 提供；无独立二级侧栏，主内容按容器滚动。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  BookOpen, CalendarCheck, CalendarDays, ChevronRight, Clock, Cloud, Grid2X2, Inbox,
  ListTodo, Receipt, Sparkles, Stamp, Timer,
} from 'lucide-vue-next';
import { useSessionStore } from '@/stores/session';
import { attendanceApi, notificationApi, workflowApi } from '@/api';
import type {
  AttendanceScheduleResponse,
  SysNotificationResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTodoResponse,
} from '@/api/types';
import { useToast } from '@/composables/useToast';
import HubToast from '@/components/base/HubToast.vue';
import HubDialog from '@/components/base/HubDialog.vue';

const session = useSessionStore();
const router = useRouter();
const { message: toastMessage, show: showToast } = useToast();
const today = new Date();
const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日'];

const myRequests = ref<WorkflowRequestSummaryResponse[]>([]);
const todos = ref<WorkflowTodoResponse[]>([]);
const notifications = ref<SysNotificationResponse[]>([]);
const schedules = ref<AttendanceScheduleResponse[]>([]);
const loading = ref(true);

async function loadAll() {
  const [requests, todoList, notifPage, scheduleList] = await Promise.allSettled([
    workflowApi.requests({ pageNo: 1, pageSize: 5 }),
    workflowApi.todos(),
    notificationApi.list({ pageNo: 1, pageSize: 5 }),
    attendanceApi.schedules({ startDate: iso(today), endDate: iso(addDays(today, 14)) }),
  ]);
  if (requests.status === 'fulfilled') myRequests.value = requests.value.list ?? [];
  if (todoList.status === 'fulfilled') todos.value = todoList.value;
  if (notifPage.status === 'fulfilled') {
    // 兼容 PageResult 与裸数组两种返回
    const raw = notifPage.value;
    notifications.value = Array.isArray(raw) ? raw : (raw.list ?? []);
  }
  if (scheduleList.status === 'fulfilled') schedules.value = scheduleList.value;
  loading.value = false;
}
onMounted(loadAll);

function iso(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function addDays(d: Date, days: number) {
  const next = new Date(d);
  next.setDate(next.getDate() + days);
  return next;
}

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

const STATUS_TEXT: Record<string, string> = {
  RUNNING: '审批中', APPROVED: '已通过', REJECTED: '已驳回',
  WITHDRAWN: '已撤回', CANCELED: '已撤销', DRAFT: '草稿',
};
const requestRows = computed(() => myRequests.value.slice(0, 3).map((r) => ({
  id: String(r.requestId),
  title: r.title || r.bizTypeName || '申请',
  status: r.statusName || STATUS_TEXT[(r.status || '').toUpperCase()] || '审批中',
  running: (r.status || '').toUpperCase() === 'RUNNING',
  meta: `${(r.submittedAt || '').slice(5, 10)} 提交 · 当前节点：${r.currentStepName || '待处理'}`,
  icon: r.bizType?.toUpperCase().includes('LEAVE') ? CalendarDays : r.bizType?.toUpperCase().includes('OVERTIME') ? Timer : Receipt,
})));
const todoRows = computed(() => todos.value.slice(0, 3).map((t) => ({
  id: String(t.todoId),
  title: t.title || '待办事项',
  summary: t.summary || '请及时处理',
  time: (t.createdAt || '').slice(5, 10),
})));

const scheduleRows = computed(() => schedules.value.slice(0, 4).map((s) => ({
  id: String(s.scheduleId),
  day: s.workDate.slice(8),
  title: s.shiftName || (s.isWorkday ? '标准工作日' : '休息日'),
  detail: s.isWorkday && s.startTime ? `${s.workDate.slice(5)} · ${s.startTime}–${s.endTime || '待定'}` : `${s.workDate.slice(5)} · 休息`,
})));
const NOTIF_TYPE: Record<string, string> = {
  APPROVAL: '审批', SYSTEM: '系统', ATTENDANCE: '考勤', ANNOUNCEMENT: '公告',
};
const notifyRows = computed(() => notifications.value.slice(0, 4).map((n) => ({
  id: String(n.notificationId),
  day: (n.createdAt || '').slice(8, 10),
  title: n.title,
  detail: `${NOTIF_TYPE[n.type] ?? '通知'} · ${(n.createdAt || '').slice(5, 16).replace('T', ' ')}`,
})));

const quickEntries = [
  { id: 'leave', label: '请假', icon: CalendarDays, tone: 'amber', path: '' },
  { id: 'expense', label: '报销', icon: Receipt, tone: 'blue', path: '' },
  { id: 'overtime', label: '加班', icon: Timer, tone: 'blue', path: '' },
  { id: 'seal', label: '用印', icon: Stamp, tone: 'blue', path: '' },
  { id: 'tasks', label: '任务', icon: ListTodo, tone: 'blue', path: '/tasks' },
  { id: 'leave-balance', label: '假期', icon: CalendarCheck, tone: 'blue', path: '/attendance' },
  { id: 'requests', label: '申请', icon: Inbox, tone: 'blue', path: '' },
  { id: 'all', label: '全部', icon: Grid2X2, tone: 'blue', path: '' },
];
const allAppsOpen = ref(false);
function onQuick(id: string) {
  if (id === 'all') { allAppsOpen.value = true; return; }
  const item = quickEntries.find((q) => q.id === id);
  if (item?.path) { void router.push(item.path); return; }
  if (id === 'requests') { document.getElementById('wb-requests')?.scrollIntoView({ block: 'nearest' }); return; }
  showToast(`「${item?.label}」表单即将上线`);
}
function openApp(path: string) {
  allAppsOpen.value = false;
  if (path) void router.push(path);
}
</script>

<template>
  <div class="wb-page">
    <section class="wb-workspace">
      <header class="wb-greeting">
        <div class="wb-greeting-copy">
          <h1>{{ greeting }}</h1>
          <p>{{ dateSub }}</p>
        </div>
        <button type="button" class="wb-summary" @click="router.push('/todos')">
          待办审批 <strong>{{ todos.length }}</strong><span>项待处理</span>
        </button>
      </header>

      <section class="wb-card wb-card-quick" aria-labelledby="wb-quick-title">
        <div class="wb-card-head">
          <h2 id="wb-quick-title" class="wb-card-title">快捷应用</h2>
          <button type="button" class="wb-link" @click="onQuick('all')">全部应用</button>
        </div>
        <div class="wb-quick-grid">
          <button
            v-for="q in quickEntries" :key="q.id" type="button"
            class="wb-quick-item" @click="onQuick(q.id)"
          >
            <span class="wb-quick-icon" :class="q.tone"><component :is="q.icon" :size="18" :stroke-width="1.8" /></span>
            <span>{{ q.label }}</span>
          </button>
        </div>
      </section>

      <section class="wb-row wb-row-main" aria-label="个人工作">
        <div id="wb-requests" class="wb-card wb-card-requests">
          <div class="wb-card-head">
            <h2 class="wb-card-title">我的申请</h2>
            <button type="button" class="wb-link" @click="router.push('/approvals')">全部申请<ChevronRight :size="12" /></button>
          </div>
          <div v-if="requestRows.length" class="wb-req-list">
            <div v-for="r in requestRows" :key="r.id" class="wb-req-item">
              <span class="wb-req-icon"><component :is="r.icon" :size="18" :stroke-width="1.8" /></span>
              <div class="wb-req-copy"><strong>{{ r.title }}</strong><small>{{ r.meta }}</small></div>
              <em class="wb-status" :class="{ running: r.running }">{{ r.status }}</em>
            </div>
          </div>
          <p v-else class="wb-empty">{{ loading ? '加载中…' : '暂无发起的申请' }}</p>
        </div>

        <div class="wb-card wb-card-todos">
          <div class="wb-card-head">
            <h2 class="wb-card-title">我的待办</h2>
            <button type="button" class="wb-link" @click="router.push('/todos')">全部待办<ChevronRight :size="12" /></button>
          </div>
          <div v-if="todoRows.length" class="wb-todo-list">
            <button v-for="t in todoRows" :key="t.id" type="button" class="wb-todo-item" @click="router.push('/todos')">
              <i />
              <span><strong>{{ t.title }}</strong><small>{{ t.summary }}</small></span>
              <time>{{ t.time }}</time>
            </button>
          </div>
          <p v-else class="wb-empty">{{ loading ? '加载中…' : '暂无待办事项' }}</p>
        </div>

        <div class="wb-card wb-card-tasks">
          <div class="wb-card-head">
            <h2 class="wb-card-title">我的任务</h2>
            <button type="button" class="wb-link" @click="router.push('/tasks')">进入任务<ChevronRight :size="12" /></button>
          </div>
          <div class="wb-task-empty">
            <span><Sparkles :size="24" :stroke-width="1.5" /></span>
            <strong>暂无任务</strong>
            <small>任务创建后将在这里展示</small>
          </div>
        </div>
      </section>

      <section class="wb-card wb-card-schedule">
        <div class="wb-card-head">
          <h2 class="wb-card-title">近期安排</h2>
          <button type="button" class="wb-link" @click="router.push('/attendance')">查看全部<ChevronRight :size="12" /></button>
        </div>
        <div class="wb-schedule-list">
          <div v-for="s in scheduleRows" :key="`shift-${s.id}`" class="wb-schedule-item">
            <span class="wb-date">{{ s.day }}</span>
            <div><strong>{{ s.title }}</strong><small>{{ s.detail }}</small></div>
          </div>
          <div v-for="n in notifyRows" :key="`notice-${n.id}`" class="wb-schedule-item">
            <span class="wb-date amber">{{ n.day }}</span>
            <div><strong>{{ n.title }}</strong><small>{{ n.detail }}</small></div>
          </div>
          <p v-if="!scheduleRows.length && !notifyRows.length" class="wb-empty">{{ loading ? '加载中…' : '暂无近期安排' }}</p>
        </div>
      </section>
    </section>
    <HubToast :message="toastMessage" />

    <HubDialog v-if="allAppsOpen" @close="allAppsOpen = false">
      <div class="wb-apps-panel">
        <header class="wb-apps-head">
          <h2>全部应用</h2>
          <button type="button" class="wb-apps-close" aria-label="关闭" @click="allAppsOpen = false">×</button>
        </header>
        <div class="wb-apps-body">
          <nav class="wb-apps-cats">
            <button type="button" class="active">常用</button>
            <button type="button" @click="showToast('分类筛选即将上线')">人事</button>
            <button type="button" @click="showToast('分类筛选即将上线')">协同</button>
            <button type="button" @click="showToast('分类筛选即将上线')">知识与文件</button>
          </nav>
          <div class="wb-apps-grid">
            <button v-for="q in quickEntries.filter(x => x.id !== 'all')" :key="q.id" type="button" class="wb-apps-item" @click="openApp(q.path || `/workbench?app=${q.id}`)">
              <span class="wb-quick-icon" :class="q.tone"><component :is="q.icon" :size="18" :stroke-width="1.8" /></span>
              <span>{{ q.label }}</span>
            </button>
            <button type="button" class="wb-apps-item" @click="openApp('/attendance')"><span class="wb-quick-icon blue"><Clock :size="18" :stroke-width="1.8" /></span><span>考勤</span></button>
            <button type="button" class="wb-apps-item" @click="openApp('/knowledge')"><span class="wb-quick-icon blue"><BookOpen :size="18" :stroke-width="1.8" /></span><span>知识库</span></button>
            <button type="button" class="wb-apps-item" @click="openApp('/drive')"><span class="wb-quick-icon blue"><Cloud :size="18" :stroke-width="1.8" /></span><span>云空间</span></button>
          </div>
        </div>
      </div>
    </HubDialog>
  </div>
</template>

<style>
@import '@/styles/workbench.css';
</style>
