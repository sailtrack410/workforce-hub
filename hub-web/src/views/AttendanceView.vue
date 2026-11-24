<script setup lang="ts">
/** 我的考勤：今日打卡优先，综合记录、假期和班次作为辅助信息。 */
import { computed, onMounted, ref } from 'vue';
import { CalendarDays, ChevronRight, Clock, Wrench } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import { attendanceApi, leaveApi } from '@/api';
import type { AttendanceDailyResultResponse, AttendanceScheduleResponse, LeaveAccountResponse } from '@/api/types';
import { useToast } from '@/composables/useToast';
import HubToast from '@/components/base/HubToast.vue';

const router = useRouter();
const { message: toastMessage, show: showToast } = useToast();
const dailyResults = ref<AttendanceDailyResultResponse[]>([]);
const schedules = ref<AttendanceScheduleResponse[]>([]);
const leaveAccounts = ref<LeaveAccountResponse[]>([]);
const loading = ref(true);
const punching = ref(false);
const filter = ref('全部');
const filters = ['全部', '出勤', '请假', '出差', '加班'];
const today = new Date();
const todayIso = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;

async function loadAll() {
  const start = new Date(today.getFullYear(), today.getMonth(), 1);
  const startIso = `${start.getFullYear()}-${String(start.getMonth() + 1).padStart(2, '0')}-01`;
  const [daily, scheduleList, leave] = await Promise.allSettled([
    attendanceApi.dailyResults({ startDate: startIso, endDate: todayIso }),
    attendanceApi.schedules({ startDate: todayIso, endDate: todayIso }),
    leaveApi.accounts(),
  ]);
  if (daily.status === 'fulfilled') dailyResults.value = daily.value;
  if (scheduleList.status === 'fulfilled') schedules.value = scheduleList.value;
  if (leave.status === 'fulfilled') leaveAccounts.value = leave.value;
  loading.value = false;
}
onMounted(loadAll);

const todayResult = computed(() => dailyResults.value.find((r) => r.workDate === todayIso) ?? null);
const punchTime = (value?: string | null) => value ? value.slice(11, 16) : '--:--';
const workedDays = computed(() => dailyResults.value.filter((r) => (r.workMinutes ?? 0) > 0).length);
const exceptionCount = computed(() => dailyResults.value.filter((r) => r.exceptionFlag).length);
const overtimeHours = computed(() => dailyResults.value.filter((r) => r.resultStatus?.toUpperCase().includes('OVERTIME')).reduce((sum, r) => sum + (r.workMinutes ?? 0), 0) / 60);
const currentSchedule = computed(() => schedules.value.find((s) => s.workDate === todayIso) ?? null);

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

const records = computed(() => dailyResults.value.slice().sort((a, b) => b.workDate.localeCompare(a.workDate)).map((r) => ({
  id: String(r.dailyResultId),
  type: r.resultStatus?.toUpperCase().includes('OVERTIME') ? '加班' : '出勤',
  title: `${r.workDate === todayIso ? '今天' : r.workDate.slice(5)} · ${r.resultStatus === 'NORMAL' ? '标准工作日' : '考勤记录'}`,
  detail: `上班 ${punchTime(r.firstPunchAt)} · 下班 ${punchTime(r.lastPunchAt)}`,
  status: r.exceptionFlag ? '异常' : r.workDate === todayIso ? '进行中' : '正常',
})));
const visibleRecords = computed(() => records.value.filter((r) => filter.value === '全部' || r.type === filter.value));
const leaveRows = computed(() => leaveAccounts.value.slice(0, 3).map((a) => {
  const days = a.availableMinutes / 480;
  const total = a.entitlementMinutes ? a.entitlementMinutes / 480 : null;
  return {
    id: String(a.accountId), name: a.leaveName,
    text: total ? `${days.toFixed(1)} / ${total.toFixed(1)} 天` : `${days.toFixed(1)} 天`,
    pct: total ? Math.min(100, Math.round(days / total * 100)) : 100,
    annual: a.leaveCode === 'ANNUAL' || a.leaveName.includes('年假'),
  };
}));
</script>

<template>
  <div class="at-page">
    <header class="at-head">
      <div><h1>我的考勤</h1><p>出勤、假期、出差与加班记录统一查看</p></div>
      <span class="at-head-spacer" />
      <button type="button" class="at-btn-secondary" @click="router.push({ path: '/approvals', query: { create: 'CLOCK_CORRECTION', from: 'attendance' } })"><Wrench :size="18" />补卡申请</button>
      <button type="button" class="at-btn-primary" @click="router.push({ path: '/approvals', query: { create: 'LEAVE', from: 'attendance' } })"><CalendarDays :size="18" />申请请假</button>
    </header>

    <section class="at-card at-today">
      <div class="at-card-head"><h2>今日考勤</h2><em class="at-status success">{{ todayResult?.exceptionFlag ? '存在异常' : '出勤正常' }}</em></div>
      <div class="at-today-row">
        <div class="at-punch-times">
          <div class="at-punch-block"><small>上班打卡</small><strong>{{ punchTime(todayResult?.firstPunchAt) }}</strong><span :class="todayResult?.firstPunchAt ? 'success-text' : ''">{{ todayResult?.firstPunchAt ? '已打卡 · 正常' : '尚未打卡' }}</span></div>
          <div class="at-punch-block"><small>下班打卡</small><strong class="muted">{{ punchTime(todayResult?.lastPunchAt) }}</strong><span>{{ currentSchedule?.endTime ? `计划 ${currentSchedule.endTime} 下班` : '待打卡' }}</span></div>
        </div>
        <button type="button" class="at-btn-primary at-punch-btn" :disabled="punching" @click="punchOut"><Clock :size="18" />{{ punching ? '打卡中…' : '下班打卡' }}</button>
      </div>
    </section>

    <div class="at-summary"><strong>本月</strong><span>出勤 {{ workedDays }} 天</span><span>加班 {{ overtimeHours.toFixed(0) }} 小时</span><span class="success-text">异常 {{ exceptionCount }} 次</span></div>

    <section class="at-main">
      <div class="at-card at-records">
        <div class="at-card-head"><h2>综合记录</h2><button type="button" class="at-link" @click="router.push('/approvals')">相关申请<ChevronRight :size="12" /></button></div>
        <div class="at-filters"><button v-for="f in filters" :key="f" type="button" :class="{ active: filter === f }" @click="filter = f">{{ f }}</button></div>
        <div class="at-record-list">
          <div v-for="r in visibleRecords" :key="r.id" class="at-record"><div><strong>{{ r.title }}</strong><small>{{ r.detail }}</small></div><em :class="['at-status', r.status === '正常' ? '' : 'info']">{{ r.status }}</em></div>
          <p v-if="!visibleRecords.length" class="at-empty">{{ loading ? '加载中…' : '当前分类暂无记录' }}</p>
        </div>
      </div>
      <aside class="at-side">
        <div class="at-card at-leave"><div class="at-card-head"><h2>假期余额</h2></div><div class="at-leave-list"><div v-for="l in leaveRows" :key="l.id" class="at-leave-item"><div><span>{{ l.name }}</span><strong>{{ l.text }}</strong></div><div class="at-track"><i :class="{ amber: !l.annual }" :style="{ width: `${l.pct}%` }" /></div></div><p v-if="!leaveRows.length" class="at-empty">暂无假期账户数据</p></div></div>
        <div class="at-card at-shift"><div class="at-card-head"><h2>当前班次</h2></div><div class="at-shift-info"><strong>{{ currentSchedule?.shiftName || '标准工作日' }}</strong><span>{{ currentSchedule?.startTime ? `${currentSchedule.startTime}–${currentSchedule.endTime || '待定'}` : '按排班执行' }}</span></div></div>
      </aside>
    </section>
    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped>
.at-page { display: flex; flex: 1; flex-direction: column; gap: 16px; min-width: 0; min-height: 0; padding: 24px; background: var(--hw-canvas); overflow-y: auto; }
.at-head { display: flex; align-items: center; gap: 8px; flex: none; }
.at-head h1 { font-size: 22px; font-weight: 700; line-height: 32px; }
.at-head p { margin-top: 4px; color: var(--hw-tertiary); font-size: 12px; }
.at-head-spacer { flex: 1; }
.at-btn-primary, .at-btn-secondary { display: inline-flex; align-items: center; justify-content: center; gap: 8px; height: 36px; padding: 0 16px; border-radius: 6px; font-size: 14px; cursor: pointer; white-space: nowrap; }
.at-btn-primary { border: 1px solid var(--hw-primary); background: var(--hw-primary); color: var(--hw-on-primary); }
.at-btn-primary:hover:not(:disabled) { background: var(--hw-primary-deep); }
.at-btn-primary:disabled { opacity: 0.65; cursor: default; }
.at-btn-secondary { border: 1px solid var(--hw-border); background: var(--hw-card); color: var(--hw-secondary); }
.at-btn-secondary:hover { background: var(--hw-inner); }
.at-card { display: flex; flex-direction: column; gap: 12px; min-width: 0; padding: 16px; border: 1px solid var(--hw-border); border-radius: 8px; background: var(--hw-card); }
.at-card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.at-card-head h2 { font-size: 16px; font-weight: 700; line-height: 24px; }
.at-today { flex: none; }
.at-today-row { display: flex; align-items: center; gap: 16px; }
.at-punch-times { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; flex: 1; }
.at-punch-block { display: flex; flex-direction: column; gap: 4px; padding: 12px; border-radius: 6px; background: var(--hw-inner); }
.at-punch-block small, .at-punch-block span { color: var(--hw-tertiary); font-size: 12px; line-height: 20px; }
.at-punch-block strong { font-size: 22px; line-height: 28px; font-weight: 600; font-variant-numeric: tabular-nums; }
.at-punch-block strong.muted { color: var(--hw-muted); }
.at-punch-btn { width: 176px; flex: none; }
.success-text { color: var(--hw-green) !important; }
.at-status { flex: none; padding: 3px 8px; border-radius: 4px; background: var(--hw-chip); color: var(--hw-tertiary); font-size: 12px; font-style: normal; }
.at-status.success { background: var(--hw-green-bg); color: var(--hw-green); }
.at-status.info { background: var(--hw-sky); color: var(--hw-primary); }
.at-summary { display: flex; align-items: center; gap: 16px; min-height: 44px; padding: 0 16px; border: 1px solid var(--hw-border); border-radius: 8px; background: var(--hw-card); flex: none; }
.at-summary strong { font-size: 14px; font-weight: 500; }
.at-summary span { color: var(--hw-secondary); font-size: 12px; }
.at-main { display: grid; grid-template-columns: minmax(0, 1fr) 336px; align-items: start; gap: 16px; flex: none; }
.at-filters { display: flex; gap: 8px; }
.at-filters button { height: 32px; padding: 0 12px; border: 0; border-radius: 6px; background: transparent; color: var(--hw-tertiary); font-size: 12px; cursor: pointer; }
.at-filters button.active { background: var(--hw-sky); color: var(--hw-primary); font-weight: 500; }
.at-record-list { display: flex; flex-direction: column; }
.at-record { display: flex; align-items: center; gap: 12px; min-height: 56px; padding: 8px 12px; }
.at-record > div { display: flex; flex: 1; flex-direction: column; gap: 2px; min-width: 0; }
.at-record strong, .at-record small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.at-record strong { font-size: 14px; font-weight: 500; }
.at-record small { color: var(--hw-muted); font-size: 12px; }
.at-empty { padding: 20px; color: var(--hw-muted); font-size: 13px; text-align: center; }
.at-side { display: flex; flex-direction: column; gap: 16px; }
.at-leave-list { display: flex; flex-direction: column; gap: 16px; }
.at-leave-item { display: flex; flex-direction: column; gap: 8px; }
.at-leave-item > div:first-child { display: flex; justify-content: space-between; gap: 12px; font-size: 14px; }
.at-leave-item span { color: var(--hw-secondary); }
.at-leave-item strong { font-weight: 500; }
.at-track { height: 6px; border-radius: 999px; background: var(--hw-track); overflow: hidden; }
.at-track i { display: block; height: 100%; border-radius: inherit; background: var(--hw-primary); }
.at-track i.amber { background: var(--hw-amber); }
.at-link { display: inline-flex; align-items: center; border: 0; background: transparent; color: var(--hw-primary); font-size: 12px; cursor: pointer; }
.at-shift-info { display: flex; flex-direction: column; gap: 4px; padding: 12px; border-radius: 6px; background: var(--hw-inner); }
.at-shift-info strong { font-size: 14px; font-weight: 500; }
.at-shift-info span { color: var(--hw-muted); font-size: 12px; }
@media (max-width: 1100px) { .at-main { grid-template-columns: 1fr; } .at-side { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
