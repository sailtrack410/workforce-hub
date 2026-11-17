<script setup lang="ts">
/**
 * 员工端审批中心：对齐画布「员工端-审批中心」8:34。
 * 页头 + 指标卡×4 + 列表(页签/筛选/分页) + 右栏(AI预审概览/审批效率)。
 * 数据：workflow.todos（待我审批）、workflow.requests（我发起的，分页）。
 * 抄送我、AI预审统计暂无接口，做空态/占位并在文档标注。
 */
import { computed, onMounted, ref } from 'vue';
import {
  CalendarCheck, Check, ChevronDown, ChevronLeft, ChevronRight, FileCheck,
  Plus, Receipt, Search, Sparkles, Stamp, Timer, X,
} from 'lucide-vue-next';
import HubShell from '../layouts/HubShell.vue';
import HubDialog from '../components/HubDialog.vue';
import HubButton from '../components/HubButton.vue';
import HubEmpty from '../components/HubEmpty.vue';
import { employeeApi } from '@/api';
import type {
  PageResult,
  WorkflowRequestSummaryResponse,
  WorkflowTodoResponse,
} from '@/api/types';

/* ---------- 类型映射（图标与语义一一对应） ---------- */
const TYPE_META: Record<string, { label: string; icon: typeof CalendarCheck; warm?: boolean }> = {
  LEAVE: { label: '请假申请', icon: CalendarCheck },
  EXPENSE: { label: '费用报销', icon: Receipt },
  OVERTIME: { label: '加班申请', icon: Timer },
  SEAL: { label: '用印申请', icon: Stamp, warm: true },
  CLOCK_CORRECTION: { label: '补卡申请', icon: Timer },
  GENERIC: { label: '通用申请', icon: FileCheck },
};
function typeMeta(bizType?: string | null, title?: string) {
  if (bizType && TYPE_META[bizType]) return TYPE_META[bizType];
  const t = title || '';
  if (t.includes('请假') || t.includes('年假') || t.includes('调休')) return TYPE_META.LEAVE;
  if (t.includes('报销')) return TYPE_META.EXPENSE;
  if (t.includes('加班')) return TYPE_META.OVERTIME;
  if (t.includes('用印')) return TYPE_META.SEAL;
  if (t.includes('补卡')) return TYPE_META.CLOCK_CORRECTION;
  return TYPE_META.GENERIC;
}

/* ---------- toast ---------- */
const toastMessage = ref('');
let toastTimer: number | undefined;
function showToast(message: string) {
  toastMessage.value = message;
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => { toastMessage.value = ''; }, 2600);
}

/* ---------- 数据加载 ---------- */
const loading = ref(true);
const todos = ref<WorkflowTodoResponse[]>([]);
const myRequests = ref<PageResult<WorkflowRequestSummaryResponse> | null>(null);

type TabKey = 'todos' | 'mine' | 'cc';
const activeTab = ref<TabKey>('todos');
const TABS: Array<{ key: TabKey; label: string }> = [
  { key: 'todos', label: '待我审批' },
  { key: 'mine', label: '我发起的' },
  { key: 'cc', label: '抄送我' },
];

async function loadTodos() {
  todos.value = await employeeApi.workflow.todos().catch(() => []);
}
const requestPageNo = ref(1);
const REQUEST_PAGE_SIZE = 8;
async function loadMyRequests() {
  myRequests.value = await employeeApi.workflow.requests({
    pageNo: requestPageNo.value,
    pageSize: REQUEST_PAGE_SIZE,
  }).catch(() => null);
}

onMounted(async () => {
  loading.value = true;
  await Promise.allSettled([loadTodos(), loadMyRequests()]);
  loading.value = false;
});

/* ---------- 指标卡 ---------- */
const inFlightRequests = computed(() =>
  (myRequests.value?.list ?? []).filter((r) => !r.finishedAt));
const finishedThisMonth = computed(() => {
  const prefix = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`;
  return (myRequests.value?.list ?? []).filter((r) => (r.finishedAt || '').startsWith(prefix)).length;
});
const avgHandleHours = computed(() => {
  const done = (myRequests.value?.list ?? []).filter((r) => r.submittedAt && r.finishedAt);
  if (!done.length) return '—';
  const hours = done.reduce((sum, r) => {
    const span = new Date(r.finishedAt!.replace(' ', 'T')).getTime() - new Date(r.submittedAt!.replace(' ', 'T')).getTime();
    return sum + Math.max(0, span / 3600000);
  }, 0) / done.length;
  return `${Math.round(hours * 10) / 10}h`;
});
const oldestTodoHours = computed(() => {
  const dated = todos.value.filter((t) => t.createdAt).map((t) => new Date(t.createdAt!.replace(' ', 'T')).getTime());
  if (!dated.length) return null;
  return Math.max(0, Math.round((Date.now() - Math.min(...dated)) / 3600000));
});

/* ---------- 筛选 ---------- */
const filterType = ref('全部类型');
const filterStatus = ref('全部状态');
const keyword = ref('');
const typeOptions = computed(() => {
  const source = activeTab.value === 'todos'
    ? todos.value.map((t) => typeMeta(null, t.title).label)
    : (myRequests.value?.list ?? []).map((r) => typeMeta(r.bizType, r.title).label);
  return ['全部类型', ...Array.from(new Set(source))];
});
const statusOptions = computed(() => {
  if (activeTab.value === 'todos') return ['全部状态'];
  const names = (myRequests.value?.list ?? []).map((r) => r.statusName || r.status).filter(Boolean) as string[];
  return ['全部状态', ...Array.from(new Set(names))];
});

const filteredTodos = computed(() => todos.value.filter((t) => {
  const meta = typeMeta(null, t.title);
  if (filterType.value !== '全部类型' && meta.label !== filterType.value) return false;
  const kw = keyword.value.trim();
  if (kw && !(`${t.title}${t.summary || ''}`.includes(kw))) return false;
  return true;
}));
const filteredRequests = computed(() => (myRequests.value?.list ?? []).filter((r) => {
  const meta = typeMeta(r.bizType, r.title);
  if (filterType.value !== '全部类型' && meta.label !== filterType.value) return false;
  if (filterStatus.value !== '全部状态' && (r.statusName || r.status) !== filterStatus.value) return false;
  const kw = keyword.value.trim();
  if (kw && !(`${r.title}${r.requestNo}`.includes(kw))) return false;
  return true;
}));

/* ---------- 待我审批分页（客户端） ---------- */
const TODO_PAGE_SIZE = 8;
const todoPageNo = ref(1);
const todoPageCount = computed(() => Math.max(1, Math.ceil(filteredTodos.value.length / TODO_PAGE_SIZE)));
const pagedTodos = computed(() => {
  const start = (todoPageNo.value - 1) * TODO_PAGE_SIZE;
  return filteredTodos.value.slice(start, start + TODO_PAGE_SIZE);
});
const requestPageCount = computed(() =>
  Math.max(1, Math.ceil((myRequests.value?.total ?? 0) / REQUEST_PAGE_SIZE)));

function switchTab(tab: TabKey) {
  activeTab.value = tab;
  filterType.value = '全部类型';
  filterStatus.value = '全部状态';
  keyword.value = '';
  todoPageNo.value = 1;
  requestPageNo.value = 1;
  void loadMyRequests();
}
function gotoPage(delta: number) {
  if (activeTab.value === 'todos') {
    todoPageNo.value = Math.min(todoPageCount.value, Math.max(1, todoPageNo.value + delta));
  } else if (activeTab.value === 'mine') {
    const next = Math.min(requestPageCount.value, Math.max(1, requestPageNo.value + delta));
    if (next !== requestPageNo.value) {
      requestPageNo.value = next;
      void loadMyRequests();
    }
  }
}

/* ---------- 审批动作 ---------- */
const actionSubmitting = ref(false);
async function approve(todo: WorkflowTodoResponse) {
  if (actionSubmitting.value) return;
  actionSubmitting.value = true;
  try {
    await employeeApi.workflow.approveTask(String(todo.taskId), {});
    todos.value = todos.value.filter((t) => t.todoId !== todo.todoId);
    showToast('已同意');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  } finally {
    actionSubmitting.value = false;
  }
}

const rejectTarget = ref<WorkflowTodoResponse | null>(null);
const rejectComment = ref('');
async function confirmReject() {
  const target = rejectTarget.value;
  if (!target || actionSubmitting.value) return;
  if (!rejectComment.value.trim()) {
    showToast('驳回必须填写原因');
    return;
  }
  actionSubmitting.value = true;
  try {
    await employeeApi.workflow.rejectTask(String(target.taskId), { comment: rejectComment.value.trim() });
    todos.value = todos.value.filter((t) => t.todoId !== target.todoId);
    rejectTarget.value = null;
    rejectComment.value = '';
    showToast('已驳回');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  } finally {
    actionSubmitting.value = false;
  }
}

const withdrawTarget = ref<WorkflowRequestSummaryResponse | null>(null);
const withdrawComment = ref('');
async function confirmWithdraw() {
  const target = withdrawTarget.value;
  if (!target || actionSubmitting.value) return;
  if (!withdrawComment.value.trim()) {
    showToast('撤回必须填写说明');
    return;
  }
  actionSubmitting.value = true;
  try {
    await employeeApi.workflow.withdrawRequest(target.requestId, { comment: withdrawComment.value.trim() });
    withdrawTarget.value = null;
    withdrawComment.value = '';
    showToast('已撤回');
    void loadMyRequests();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  } finally {
    actionSubmitting.value = false;
  }
}

/* ---------- 发起审批（类型选择，表单待接） ---------- */
const createOpen = ref(false);
const createTypes = [
  { key: 'leave', label: '请假申请', icon: CalendarCheck, desc: '年假 / 调休 / 病假等' },
  { key: 'expense', label: '费用报销', icon: Receipt, desc: '差旅、日常报销' },
  { key: 'overtime', label: '加班申请', icon: Timer, desc: '工作日 / 周末加班' },
  { key: 'seal', label: '用印申请', icon: Stamp, desc: '公章 / 合同章使用' },
];
function pickCreateType(label: string) {
  showToast(`${label}表单即将上线，敬请期待`);
}

function fmtTime(value?: string | null) {
  return value ? value.slice(5, 16).replace('T', ' ') : '';
}
const statusPillClass = (statusName?: string | null) => {
  const s = statusName || '';
  if (s.includes('通过') || s.includes('完成') || s.includes('同意')) return 'ok';
  if (s.includes('驳回') || s.includes('拒绝') || s.includes('撤销') || s.includes('撤回')) return 'danger';
  return 'pending';
};
</script>

<template>
  <HubShell>
  <div class="ap-page">
    <!-- 页头 -->
    <header class="ap-head">
      <div class="ap-head-copy">
        <h1>审批中心</h1>
        <p>待办、发起的流程统一在这里处理</p>
      </div>
      <button type="button" class="ap-create-btn" @click="createOpen = true">
        <Plus :size="18" :stroke-width="2" />
        发起审批
      </button>
    </header>

    <!-- 指标卡 ×4 -->
    <div class="ap-metrics">
      <div class="ap-metric">
        <span class="ap-metric-icon"><FileCheck :size="22" :stroke-width="2" /></span>
        <span class="ap-metric-copy">
          <small>待我审批</small>
          <span class="ap-metric-value"><strong>{{ todos.length }}</strong><em>{{ todos.length ? '去处理' : '全部已处理' }}</em></span>
        </span>
      </div>
      <div class="ap-metric">
        <span class="ap-metric-icon"><Timer :size="22" :stroke-width="2" /></span>
        <span class="ap-metric-copy">
          <small>我发起 · 进行中</small>
          <span class="ap-metric-value"><strong>{{ inFlightRequests.length }}</strong><em>{{ myRequests ? `共 ${myRequests.total} 条` : '—' }}</em></span>
        </span>
      </div>
      <div class="ap-metric">
        <span class="ap-metric-icon"><Check :size="22" :stroke-width="2" /></span>
        <span class="ap-metric-copy">
          <small>本月已审批</small>
          <span class="ap-metric-value"><strong>{{ finishedThisMonth }}</strong><em>按提交记录统计</em></span>
        </span>
      </div>
      <div class="ap-metric">
        <span class="ap-metric-icon"><Sparkles :size="22" :stroke-width="2" /></span>
        <span class="ap-metric-copy">
          <small>平均处理时长</small>
          <span class="ap-metric-value"><strong>{{ avgHandleHours }}</strong><em>{{ avgHandleHours === '—' ? '暂无完成记录' : '本页完成单' }}</em></span>
        </span>
      </div>
    </div>

    <div class="ap-main">
      <!-- 审批列表卡 -->
      <section class="ap-list-card">
        <div class="ap-list-bar">
          <div class="ap-tabs">
            <button
              v-for="t in TABS" :key="t.key" type="button"
              class="ap-tab" :class="{ active: activeTab === t.key }"
              @click="switchTab(t.key)"
            >
              {{ t.label }}
              <em v-if="t.key === 'todos' && todos.length">{{ todos.length }}</em>
            </button>
          </div>
          <div class="ap-filters">
            <label class="ap-select">
              <select v-model="filterType">
                <option v-for="o in typeOptions" :key="o">{{ o }}</option>
              </select>
              <ChevronDown :size="16" :stroke-width="2" />
            </label>
            <label v-if="activeTab !== 'todos'" class="ap-select">
              <select v-model="filterStatus">
                <option v-for="o in statusOptions" :key="o">{{ o }}</option>
              </select>
              <ChevronDown :size="16" :stroke-width="2" />
            </label>
            <label class="ap-search">
              <Search :size="16" :stroke-width="2" />
              <input v-model="keyword" type="text" :placeholder="activeTab === 'todos' ? '搜索标题 / 摘要' : '搜索标题 / 单号'" />
            </label>
          </div>
        </div>

        <div class="ap-list-scroll">
          <HubEmpty v-if="loading" title="加载中…" :icon="FileCheck" />
          <template v-else-if="activeTab === 'todos'">
            <article v-for="t in pagedTodos" :key="t.todoId" class="ap-item">
              <span class="ap-item-icon">
                <component :is="typeMeta(null, t.title).icon" :size="22" :stroke-width="2" />
              </span>
              <span class="ap-item-info">
                <strong>{{ t.title }}</strong>
                <small>{{ t.summary || '—' }}<template v-if="t.createdAt"> · {{ fmtTime(t.createdAt) }} 提交</template></small>
                <em v-if="t.priorityName && t.priorityName !== '普通'" class="ap-urgent">{{ t.priorityName }}</em>
              </span>
              <span class="ap-item-actions">
                <button type="button" class="ap-btn-reject" :disabled="actionSubmitting" @click="rejectTarget = t">驳回</button>
                <button type="button" class="ap-btn-approve" :disabled="actionSubmitting" @click="approve(t)">同意</button>
              </span>
            </article>
            <HubEmpty v-if="!pagedTodos.length" title="没有待审批的事项" :icon="FileCheck" />
          </template>

          <template v-else-if="activeTab === 'mine'">
            <article v-for="r in filteredRequests" :key="r.requestId" class="ap-item">
              <span class="ap-item-icon" :class="{ warm: typeMeta(r.bizType, r.title).warm }">
                <component :is="typeMeta(r.bizType, r.title).icon" :size="22" :stroke-width="2" />
              </span>
              <span class="ap-item-info">
                <strong>{{ r.title }}</strong>
                <small>{{ r.requestNo }}<template v-if="r.submittedAt"> · {{ fmtTime(r.submittedAt) }} 提交</template><template v-if="r.currentStepName"> · 当前节点：{{ r.currentStepName }}</template></small>
              </span>
              <span class="ap-item-actions">
                <i class="ap-status-pill" :class="statusPillClass(r.statusName || r.status)">{{ r.statusName || r.status }}</i>
                <button v-if="!r.finishedAt" type="button" class="ap-btn-withdraw" :disabled="actionSubmitting" @click="withdrawTarget = r">撤回</button>
              </span>
            </article>
            <HubEmpty v-if="!filteredRequests.length" title="还没有发起的申请" :icon="FileCheck" />
          </template>

          <template v-else>
            <HubEmpty title="抄送功能即将上线" :icon="FileCheck" />
          </template>
        </div>

        <footer v-if="activeTab !== 'cc'" class="ap-pager">
          <small v-if="activeTab === 'todos'">共 {{ filteredTodos.length }} 条记录 · 第 {{ todoPageNo }} / {{ todoPageCount }} 页</small>
          <small v-else>共 {{ myRequests?.total ?? 0 }} 条记录 · 第 {{ requestPageNo }} / {{ requestPageCount }} 页</small>
          <span class="ap-pager-btns">
            <button type="button" class="ap-page-btn" aria-label="上一页" @click="gotoPage(-1)"><ChevronLeft :size="16" :stroke-width="2" /></button>
            <button type="button" class="ap-page-btn" aria-label="下一页" @click="gotoPage(1)"><ChevronRight :size="16" :stroke-width="2" /></button>
          </span>
        </footer>
      </section>

      <!-- 右栏 336 -->
      <aside class="ap-side">
        <section class="ap-side-card">
          <header class="ap-side-head">
            <Sparkles :size="20" :stroke-width="2" />
            <h3>AI 预审概览</h3>
          </header>
          <div class="ap-data-row"><small>本周预审</small><strong>—</strong></div>
          <div class="ap-data-row"><small>预审通过率</small><strong class="ok">—</strong></div>
          <div class="ap-side-sep" />
          <div class="ap-data-row"><small>硬规则拦截</small><strong class="warn">—</strong></div>
          <p class="ap-side-note">预审统计接口接入后展示</p>
        </section>

        <section class="ap-side-card ap-side-grow">
          <h3 class="ap-side-title">审批效率</h3>
          <div class="ap-eff-item">
            <small>平均处理时长</small>
            <span class="ap-eff-value"><strong>{{ avgHandleHours }}</strong></span>
          </div>
          <div class="ap-eff-item">
            <small>最长等待</small>
            <span class="ap-eff-value"><strong :class="{ warn: oldestTodoHours !== null && oldestTodoHours >= 24 }">{{ oldestTodoHours === null ? '—' : `${oldestTodoHours} 小时` }}</strong></span>
          </div>
          <div class="ap-eff-item">
            <small>本周收到催办</small>
            <span class="ap-eff-value"><strong>—</strong></span>
          </div>
        </section>
      </aside>
    </div>

    <!-- 驳回原因弹窗 -->
    <HubDialog v-if="rejectTarget" @close="rejectTarget = null">
      <form class="ap-dialog" @submit.prevent="confirmReject">
        <header class="ap-dialog-head">
          <h2>驳回「{{ rejectTarget.title }}」</h2>
          <button class="ap-icon-btn" type="button" aria-label="关闭" @click="rejectTarget = null"><X :size="15" /></button>
        </header>
        <label class="ap-field">
          驳回原因（必填）
          <textarea v-model="rejectComment" rows="3" placeholder="请说明驳回原因，将通知申请人" />
        </label>
        <footer class="ap-dialog-foot">
          <HubButton variant="secondary" type="button" :disabled="actionSubmitting" @click="rejectTarget = null">取消</HubButton>
          <HubButton variant="primary" :disabled="actionSubmitting" @click="confirmReject">确认驳回</HubButton>
        </footer>
      </form>
    </HubDialog>

    <!-- 撤回说明弹窗 -->
    <HubDialog v-if="withdrawTarget" @close="withdrawTarget = null">
      <form class="ap-dialog" @submit.prevent="confirmWithdraw">
        <header class="ap-dialog-head">
          <h2>撤回「{{ withdrawTarget.title }}」</h2>
          <button class="ap-icon-btn" type="button" aria-label="关闭" @click="withdrawTarget = null"><X :size="15" /></button>
        </header>
        <label class="ap-field">
          撤回说明（必填）
          <textarea v-model="withdrawComment" rows="3" placeholder="请说明撤回原因" />
        </label>
        <footer class="ap-dialog-foot">
          <HubButton variant="secondary" type="button" :disabled="actionSubmitting" @click="withdrawTarget = null">取消</HubButton>
          <HubButton variant="primary" :disabled="actionSubmitting" @click="confirmWithdraw">确认撤回</HubButton>
        </footer>
      </form>
    </HubDialog>

    <!-- 发起审批类型选择 -->
    <HubDialog v-if="createOpen" @close="createOpen = false">
      <div class="ap-dialog">
        <header class="ap-dialog-head">
          <h2>发起审批</h2>
          <button class="ap-icon-btn" type="button" aria-label="关闭" @click="createOpen = false"><X :size="15" /></button>
        </header>
        <div class="ap-create-grid">
          <button
            v-for="t in createTypes" :key="t.key" type="button" class="ap-create-item"
            @click="pickCreateType(t.label)"
          >
            <span class="ap-item-icon" :class="{ warm: t.key === 'seal' }">
              <component :is="t.icon" :size="22" :stroke-width="2" />
            </span>
            <span class="ap-item-info">
              <strong>{{ t.label }}</strong>
              <small>{{ t.desc }}</small>
            </span>
          </button>
        </div>
      </div>
    </HubDialog>

    <transition name="ap-toast">
      <div v-if="toastMessage" class="ap-toast">{{ toastMessage }}</div>
    </transition>
  </div>
  </HubShell>
</template>

<style>
@import '../styles/approvals.css';
</style>
