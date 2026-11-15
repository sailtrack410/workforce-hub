<script setup lang="ts">
/**
 * 移动端考勤页：对齐画布「员工端-考勤」桌面版重构后的结构。
 * 今日打卡优先 + 本月概览摘要 + 综合记录 + 假期余额 + 当前班次。
 */
import { computed, onMounted, ref } from 'vue';
import { CalendarCheck, ChevronRight, Clock, Wrench } from 'lucide-vue-next';
import HubShell from '../layouts/HubShell.vue';
import HubEmpty from '../components/HubEmpty.vue';
import { employeeApi } from '@/api';
import { useSessionStore } from '@/stores/session';
import { useImStore } from '@/stores/im';
import type { AttendanceDailyResultResponse, LeaveAccountResponse, WorkflowTodoResponse } from '@/api/types';

const session = useSessionStore();
const im = useImStore();

const loading = ref(true);
const punching = ref(false);
const filter = ref('全部');
const filters = ['全部', '出勤', '请假', '出差', '加班'];
const toast = ref('');

const dailyResults = ref<AttendanceDailyResultResponse[]>([]);
const leaveAccounts = ref<LeaveAccountResponse[]>([]);
const todos = ref<WorkflowTodoResponse[]>([]);
const unreadTotal = computed(() =>
  (im.conversations || []).reduce((sum, c) => sum + (Number((c as { unread?: number }).unread) || 0), 0),
);

const now = new Date();
const pad = (n: number) => String(n).padStart(2, '0');
const todayKey = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
const monthPrefix = `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
const monthStart = `${monthPrefix}-01`;

async function loadAll() {
  const results = await Promise.allSettled([
    employeeApi.attendance.dailyResults({ startDate: monthStart, endDate: todayKey }),
    employeeApi.leave.accounts(),
    employeeApi.workflow.todos(),
    im.loadConversations().catch(() => undefined),
  ]);
  if (results[0].status === 'fulfilled') dailyResults.value = results[0].value || [];
  if (results[1].status === 'fulfilled') leaveAccounts.value = results[1].value || [];
  if (results[2].status === 'fulfilled') todos.value = results[2].value || [];
  loading.value = false;
}
onMounted(loadAll);

function showToast(message: string) {
  toast.value = message;
  window.setTimeout(() => { toast.value = ''; }, 2500);
}

/* ---------- 今日打卡（打卡时间来自打卡记录，daily-result 未含首末签到时间） ---------- */
const todayResult = computed(() => dailyResults.value.find((r) => r.workDate === todayKey) ?? null);
const workedToday = computed(() => (todayResult.value?.workMinutes ?? 0) > 0);
const punchIn = computed(() => (workedToday.value ? '已出勤' : '--:--'));
const punchOut = computed(() => (todayResult.value && (todayResult.value.workMinutes ?? 0) >= 480 ? '已出勤' : '--:--'));
async function punch(type: 'IN' | 'OUT') {
  if (punching.value) return;
  punching.value = true;
  try {
    await employeeApi.attendance.punch({ punchType: type, sourceType: 'MOBILE' });
    showToast(type === 'IN' ? '上班打卡成功' : '下班打卡成功');
    await loadAll();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '打卡失败');
  } finally {
    punching.value = false;
  }
}

/* ---------- 本月概览摘要 ---------- */
const workedDays = computed(() => dailyResults.value.filter((r) => (r.workMinutes ?? 0) > 0).length);
const exceptionCount = computed(() =>
  dailyResults.value.filter((r) => (r.workDate || '').startsWith(monthPrefix) && r.exceptionFlag).length,
);
const overtimeHours = computed(() =>
  Math.round(dailyResults.value.reduce((sum, r) => sum + (r.workMinutes ?? 0), 0) / 60));

/* ---------- 综合记录 ---------- */
const records = computed(() => dailyResults.value
  .slice()
  .sort((a, b) => (b.workDate || '').localeCompare(a.workDate || ''))
  .map((r) => ({
    id: String(r.dailyResultId),
    type: (r.resultStatus || '').toUpperCase().includes('OVERTIME') ? '加班' : '出勤',
    title: `${r.workDate === todayKey ? '今天' : (r.workDate || '').slice(5)} · ${r.resultStatus === 'NORMAL' ? '标准工作日' : '考勤记录'}`,
    detail: `出勤 ${(r.workMinutes ?? 0) / 60 >= 1 ? `${Math.round(((r.workMinutes ?? 0) / 60) * 10) / 10} 小时` : '无记录'}`,
    status: r.exceptionFlag ? '异常' : r.workDate === todayKey ? '进行中' : '正常',
  })));
const visibleRecords = computed(() =>
  records.value.filter((r) => filter.value === '全部' || r.type === filter.value));

/* ---------- 假期余额 ---------- */
function fmtDays(minutes?: number | null): string {
  const m = Number(minutes || 0);
  if (!m) return '0';
  const days = m / 480;
  const v = Math.round(days * 10) / 10;
  return Number.isInteger(v) ? String(v) : v.toFixed(1);
}
const leaveRows = computed(() => leaveAccounts.value.slice(0, 3).map((a) => {
  const days = Number(a.availableMinutes) || 0;
  const total = days + (Number(a.frozenMinutes) || 0);
  return {
    id: String(a.accountId),
    name: a.leaveName || a.leaveCode,
    text: total ? `${fmtDays(days)} / ${fmtDays(total)} 天` : `${fmtDays(days)} 天`,
    pct: total ? Math.min(100, Math.round((days / total) * 100)) : 100,
    annual: a.leaveCode === 'ANNUAL' || (a.leaveName || '').includes('年假'),
  };
}));
</script>

<template>
  <HubShell>
  <div class="at-page" :class="{ loading }">
    <header class="at-head">
      <div>
        <h1>我的考勤</h1>
        <p>出勤、假期、出差与加班记录统一查看</p>
      </div>
      <div class="at-head-actions">
        <button type="button" class="at-btn-secondary" @click="showToast('补卡申请表单即将上线')">
          <Wrench :size="16" :stroke-width="2" />补卡申请
        </button>
        <button type="button" class="at-btn-primary" @click="showToast('请假申请表单即将上线')">
          <CalendarCheck :size="16" :stroke-width="2" />申请请假
        </button>
      </div>
    </header>

    <section class="at-card at-today">
      <div class="at-card-head">
        <h2>今日考勤</h2>
        <span class="at-status" :class="todayResult?.exceptionFlag ? 'warn' : 'success'">
          {{ todayResult?.exceptionFlag ? '存在异常' : '出勤正常' }}
        </span>
      </div>
      <div class="at-today-row">
        <div class="at-punch-times">
          <div class="at-punch-block">
            <small>上班打卡</small>
            <strong :class="{ ok: punchIn !== '--:--' }">{{ punchIn }}</strong>
            <span :class="{ ok: punchIn !== '--:--' }">{{ punchIn !== '--:--' ? '已打卡 · 正常' : '尚未打卡' }}</span>
          </div>
          <div class="at-punch-block">
            <small>下班打卡</small>
            <strong :class="{ ok: punchOut !== '--:--' }">{{ punchOut }}</strong>
            <span>{{ punchOut !== '--:--' ? '已打卡' : '待打卡' }}</span>
          </div>
        </div>
        <button
          type="button" class="at-btn-primary at-punch-btn" :disabled="punching"
          @click="punch(punchOut !== '--:--' ? 'IN' : 'OUT')"
        >
          <Clock :size="18" :stroke-width="2" />{{ punching ? '打卡中…' : punchOut !== '--:--' ? '上班打卡' : '下班打卡' }}
        </button>
      </div>
    </section>

    <div class="at-summary">
      <strong>本月</strong>
      <span>出勤 {{ workedDays }} 天</span>
      <span>加班 {{ overtimeHours }} 小时</span>
      <span class="ok">异常 {{ exceptionCount }} 次</span>
    </div>

    <section class="at-card at-records">
      <div class="at-card-head">
        <h2>综合记录</h2>
        <button type="button" class="at-link" @click="showToast('即将上线')">
          相关申请 <ChevronRight :size="12" :stroke-width="2" />
        </button>
      </div>
      <div class="at-filters">
        <button
          v-for="f in filters" :key="f" type="button"
          :class="{ active: filter === f }" @click="filter = f"
        >{{ f }}</button>
      </div>
      <div class="at-record-list">
        <div v-for="r in visibleRecords" :key="r.id" class="at-record">
          <div>
            <strong>{{ r.title }}</strong>
            <small>{{ r.detail }}</small>
          </div>
          <span class="at-status" :class="{ info: r.status !== '正常' }">{{ r.status }}</span>
        </div>
        <HubEmpty v-if="!visibleRecords.length" :title="loading ? '加载中…' : '当前分类暂无记录'" />
      </div>
    </section>

    <section class="at-card at-leave">
      <div class="at-card-head">
        <h2>假期余额</h2>
        <button type="button" class="at-link" @click="showToast('请假申请表单即将上线')">
          申请请假 <ChevronRight :size="12" :stroke-width="2" />
        </button>
      </div>
      <div v-if="leaveRows.length" class="at-leave-list">
        <div v-for="l in leaveRows" :key="l.id" class="at-leave-item">
          <div>
            <span>{{ l.name }}</span>
            <strong>{{ l.text }}</strong>
          </div>
          <div class="at-track"><i :class="{ warm: !l.annual }" :style="{ width: `${l.pct}%` }" /></div>
        </div>
      </div>
      <HubEmpty v-else :title="loading ? '加载中…' : '暂无假期账户数据'" />
    </section>

    <Transition name="at-toast">
      <p v-if="toast" class="at-toast">{{ toast }}</p>
    </Transition>
  </div>
  </HubShell>
</template>

<style scoped src="../styles/attendance.css" />
