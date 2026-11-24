<script setup lang="ts">
/**
 * 审批中心：待我审批 / 我发起的 / 抄送我的 三个 tab；
 * 支持发起审批（请假、补卡走专用契约，其余走通用表单）、审批、撤回、详情查看。
 */
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import { useLocalDraft } from '@/services/localData';
import { Download, Plus } from 'lucide-vue-next';
import { fileApi, leaveApi, workflowApi } from '@/api';
import type {
  EntityId,
  LeaveAccountResponse,
  WorkflowCcResponse,
  WorkflowTaskActionResponse,
  WorkflowRequestDetailResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTodoResponse,
} from '@/api/types';
import { useToast } from '@/composables/useToast';
import HubButton from '@/components/base/HubButton.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';
import HubToast from '@/components/base/HubToast.vue';

const { message: toastMessage, show: showToast } = useToast();

const tab = ref<'todo' | 'mine' | 'cc'>('todo');
const loading = ref(true);

/* ---------- 待我审批 ---------- */
const todos = ref<WorkflowTodoResponse[]>([]);
const rejecting = ref<WorkflowTodoResponse | null>(null);
const rejectComment = ref('');
const approvingId = ref<string | null>(null);

/* ---------- 我发起的 ---------- */
const requests = ref<WorkflowRequestSummaryResponse[]>([]);
const requestTotal = ref(0);
const requestPage = ref(1);
const detailOpen = ref(false);
const detail = ref<WorkflowRequestDetailResponse | null>(null);
const actions = ref<WorkflowTaskActionResponse[]>([]);
const withdrawing = ref(false);

/* ---------- 抄送我的 ---------- */
const ccList = ref<WorkflowCcResponse[]>([]);
const ccTotal = ref(0);
const ccPage = ref(1);
const PAGE_SIZE = 10;

/* ---------- 发起审批 ---------- */
const createOpen = ref(false);
const createStep = ref<'pick' | 'form'>('pick');
const submitting = ref(false);
const PROCESS_OPTIONS = [
  { code: 'LEAVE', label: '请假' },
  { code: 'CLOCK_CORRECTION', label: '补卡' },
  { code: 'EXPENSE', label: '费用报销' },
  { code: 'OVERTIME', label: '加班' },
  { code: 'SEAL', label: '用章' },
  { code: 'CERTIFICATE', label: '证明开具' },
  { code: 'BORROW', label: '备用金' },
  { code: 'PROJECT', label: '项目立项' },
] as const;
const selectedProcess = ref<string>('LEAVE');
const leaveAccounts = ref<LeaveAccountResponse[]>([]);
// 通用表单字段
const genericTitle = ref('');
const genericSummary = ref('');
const genericAmount = ref('');
const genericDate = ref('');
// 请假表单
const leaveTypeId = ref<EntityId | null>(null);
const leaveStart = ref('');
const leaveEnd = ref('');
const leaveReason = ref('');
const leaveDuration = ref<number | null>(null);
// 补卡表单
const ccTargetDate = ref('');
const ccPunchType = ref<'ON_DUTY' | 'OFF_DUTY'>('ON_DUTY');
const ccExpectedAt = ref('');
const ccReason = ref('');
const approvalFields = { selectedProcess, genericTitle, genericSummary, genericAmount, genericDate, leaveTypeId, leaveStart, leaveEnd, leaveReason, leaveDuration, ccTargetDate, ccPunchType, ccExpectedAt, ccReason };
const approvalDraft = useLocalDraft('approval-form', computed({
  get: () => Object.fromEntries(Object.entries(approvalFields).map(([key, value]) => [key, value.value])),
  set: (value) => { for (const [key, field] of Object.entries(approvalFields)) if (key in value) (field as { value: unknown }).value = value[key]; },
}));
onMounted(() => { void approvalDraft.restore(); });
onBeforeUnmount(approvalDraft.stop);

const isLeave = computed(() => selectedProcess.value === 'LEAVE');
const isClock = computed(() => selectedProcess.value === 'CLOCK_CORRECTION');
const needAmount = computed(() => ['EXPENSE', 'BORROW'].includes(selectedProcess.value));
const selectedLabel = computed(() => PROCESS_OPTIONS.find((o) => o.code === selectedProcess.value)?.label ?? '');

function fmt(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—';
}
function statusClass(status?: string | null) {
  const s = (status ?? '').toUpperCase();
  if (s.includes('APPROVED') || s.includes('DONE') || s.includes('PASS')) return 'success';
  if (s.includes('REJECT') || s.includes('WITHDRAW')) return 'danger';
  if (s.includes('PENDING') || s.includes('REVIEW')) return 'warn';
  return 'info';
}

async function loadTodos() {
  todos.value = await workflowApi.todos();
}
async function loadRequests() {
  const page = await workflowApi.requests({ pageNo: requestPage.value, pageSize: PAGE_SIZE });
  requests.value = page.list;
  requestTotal.value = page.total;
}
async function loadCc() {
  const page = await workflowApi.ccList({ pageNo: ccPage.value, pageSize: PAGE_SIZE });
  ccList.value = page.list;
  ccTotal.value = page.total;
}
async function loadCurrent() {
  loading.value = true;
  try {
    if (tab.value === 'todo') await loadTodos();
    else if (tab.value === 'mine') await loadRequests();
    else await loadCc();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '加载失败');
  }
  loading.value = false;
}
onMounted(loadCurrent);

async function switchTab(key: 'todo' | 'mine' | 'cc') {
  tab.value = key;
  await loadCurrent();
}

async function approve(todo: WorkflowTodoResponse) {
  approvingId.value = todo.taskId;
  try {
    await workflowApi.approveTask(todo.taskId, '同意');
    showToast('已同意');
    await loadTodos();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  }
  approvingId.value = null;
}
async function submitReject() {
  if (!rejecting.value || !rejectComment.value.trim()) return;
  try {
    await workflowApi.rejectTask(rejecting.value.taskId, rejectComment.value.trim());
    showToast('已驳回');
    rejecting.value = null;
    rejectComment.value = '';
    await loadTodos();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  }
}

async function openDetail(item: { requestId: EntityId }) {
  try {
    detail.value = await workflowApi.requestDetail(item.requestId);
    actions.value = await workflowApi.requestActions(item.requestId);
    detailOpen.value = true;
  } catch (error) {
    showToast(error instanceof Error ? error.message : '加载详情失败');
  }
}
async function withdraw() {
  if (!detail.value || withdrawing.value) return;
  withdrawing.value = true;
  try {
    await workflowApi.withdrawRequest(detail.value.requestId, '撤回申请');
    showToast('已撤回');
    detailOpen.value = false;
    await loadRequests();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '撤回失败');
  }
  withdrawing.value = false;
}
const canWithdraw = computed(() => {
  const s = (detail.value?.status ?? '').toUpperCase();
  return s.includes('PENDING') || s.includes('REVIEW') || s.includes('APPROVING');
});
async function downloadAttachment(attachmentRefId: EntityId) {
  if (!detail.value) return;
  try {
    const ticket = await workflowApi.attachmentDownloadTicket(detail.value.requestId, attachmentRefId, 'ATTACHMENT');
    const url = ticket.downloadUrl || workflowApi.attachmentDownloadUrl(ticket.ticketNo);
    const res = await fetch(url, { credentials: 'include' });
    const blob = await res.blob();
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = detail.value.attachments.find((a2) => a2.attachmentRefId === attachmentRefId)?.displayName || '附件';
    a.click();
    URL.revokeObjectURL(a.href);
  } catch {
    showToast('附件下载失败');
  }
}

async function markRead(cc: WorkflowCcResponse) {
  if (cc.read) return;
  try {
    await workflowApi.markCcRead(cc.requestId);
    cc.read = true;
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  }
}

/* ---------- 发起审批 ---------- */
async function openCreate() {
  createOpen.value = true;
  createStep.value = 'pick';
  if (!leaveAccounts.value.length) {
    try {
      leaveAccounts.value = await leaveApi.accounts();
    } catch { /* 假期账户加载失败时仅影响请假类型选项 */ }
  }
}
function pickProcess(code: string) {
  selectedProcess.value = code;
  createStep.value = 'form';
}
function resetForm() {
  genericTitle.value = '';
  genericSummary.value = '';
  genericAmount.value = '';
  genericDate.value = '';
  leaveTypeId.value = leaveAccounts.value[0]?.accountId ?? null;
  leaveStart.value = '';
  leaveEnd.value = '';
  leaveReason.value = '';
  leaveDuration.value = null;
  ccTargetDate.value = '';
  ccPunchType.value = 'ON_DUTY';
  ccExpectedAt.value = '';
  ccReason.value = '';
}
async function previewDuration() {
  if (!leaveStart.value || !leaveEnd.value) return;
  try {
    const result = await workflowApi.previewLeaveDuration(leaveStart.value, leaveEnd.value);
    leaveDuration.value = result.durationMinutes;
  } catch (error) {
    leaveDuration.value = null;
    showToast(error instanceof Error ? error.message : '时长计算失败');
  }
}
async function submitCreate() {
  if (submitting.value) return;
  submitting.value = true;
  try {
    if (isLeave.value) {
      if (!leaveTypeId.value || !leaveStart.value || !leaveEnd.value || !leaveReason.value.trim() || !leaveDuration.value) {
        showToast('请完整填写请假信息'); submitting.value = false; return;
      }
      await workflowApi.submitLeaveRequest({
        leaveTypeId: leaveTypeId.value,
        startAt: leaveStart.value,
        endAt: leaveEnd.value,
        durationMinutes: leaveDuration.value,
        reason: leaveReason.value.trim(),
      });
    } else if (isClock.value) {
      if (!ccTargetDate.value || !ccExpectedAt.value || !ccReason.value.trim()) {
        showToast('请完整填写补卡信息'); submitting.value = false; return;
      }
      await workflowApi.submitClockCorrectionRequest({
        targetDate: ccTargetDate.value,
        missingPunchType: ccPunchType.value,
        expectedPunchAt: ccExpectedAt.value,
        reason: ccReason.value.trim(),
      });
    } else {
      if (!genericSummary.value.trim()) {
        showToast('请填写事由'); submitting.value = false; return;
      }
      const formPayload: Record<string, unknown> = { 事由: genericSummary.value.trim() };
      if (needAmount.value && genericAmount.value) formPayload['金额'] = Number(genericAmount.value);
      if (genericDate.value) formPayload['日期'] = genericDate.value;
      await workflowApi.submitGenericRequest({
        processCode: selectedProcess.value,
        title: genericTitle.value.trim() || undefined,
        summary: genericSummary.value.trim(),
        formPayload,
      });
    }
    showToast('提交成功，已进入审批流程');
    createOpen.value = false;
    resetForm();
    await approvalDraft.clear();
    tab.value = 'mine';
    requestPage.value = 1;
    await loadRequests();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '提交失败');
  }
  submitting.value = false;
}
const requestPages = computed(() => Math.max(1, Math.ceil(requestTotal.value / PAGE_SIZE)));
const ccPages = computed(() => Math.max(1, Math.ceil(ccTotal.value / PAGE_SIZE)));
async function pageRequests(delta: number) {
  requestPage.value = Math.min(requestPages.value, Math.max(1, requestPage.value + delta));
  await loadRequests();
}
async function pageCc(delta: number) {
  ccPage.value = Math.min(ccPages.value, Math.max(1, ccPage.value + delta));
  await loadCc();
}
</script>

<template>
  <div class="ap-page">
    <header class="ap-head">
      <div><h1>审批中心</h1><p>待办审批、发起申请与抄送统一处理</p></div>
      <span class="ap-head-spacer" />
      <button type="button" class="ap-btn-primary" @click="openCreate"><Plus :size="16" />发起审批</button>
    </header>

    <section class="ap-card">
      <div class="ap-card-head">
        <div class="ap-tabs">
          <button type="button" :class="{ active: tab === 'todo' }" @click="switchTab('todo')">待我审批</button>
          <button type="button" :class="{ active: tab === 'mine' }" @click="switchTab('mine')">我发起的</button>
          <button type="button" :class="{ active: tab === 'cc' }" @click="switchTab('cc')">抄送我的</button>
        </div>
      </div>

      <!-- 待我审批 -->
      <div v-if="tab === 'todo'" class="ap-list">
        <div v-for="t in todos" :key="t.taskId" class="ap-row" @click="openDetail({ requestId: t.requestId })">
          <div class="ap-row-main">
            <strong>{{ t.title }}</strong>
            <small>{{ t.summary || '—' }} · 提交于 {{ fmt(t.createdAt) }}<template v-if="t.priorityName"> · 优先级 {{ t.priorityName }}</template><template v-if="t.aiVerdictName"> · AI预审 {{ t.aiVerdictName }}</template></small>
          </div>
          <em :class="['ap-status', t.status === 'PENDING' ? 'warn' : '']">{{ t.statusName || '待审批' }}</em>
          <div class="ap-row-actions">
            <button type="button" class="ap-btn-approve" :disabled="approvingId === t.taskId" @click.stop="approve(t)">同意</button>
            <button type="button" class="ap-btn-reject" @click.stop="rejecting = t; rejectComment = ''">驳回</button>
          </div>
        </div>
        <HubEmpty v-if="!loading && !todos.length" title="暂无待办审批" note="有需要你审批的申请时会出现在这里" />
      </div>

      <!-- 我发起的 -->
      <div v-else-if="tab === 'mine'" class="ap-list">
        <div v-for="r in requests" :key="r.requestId" class="ap-row" @click="openDetail(r)">
          <div class="ap-row-main">
            <strong>{{ r.title }}</strong>
            <small>{{ r.bizTypeName }} · {{ r.requestNo }} · 提交于 {{ fmt(r.submittedAt) }}</small>
          </div>
          <em :class="['ap-status', statusClass(r.status)]">{{ r.statusName || r.status }}</em>
          <small v-if="r.currentStepName" style="flex-shrink:0">{{ r.currentStepName }}</small>
        </div>
        <HubEmpty v-if="!loading && !requests.length" title="暂无申请记录" note="点击右上角「发起审批」提交你的第一份申请" />
        <div v-if="requestTotal > PAGE_SIZE" class="ap-pager">
          <button type="button" :disabled="requestPage <= 1" @click="pageRequests(-1)">上一页</button>
          <span>{{ requestPage }} / {{ requestPages }}</span>
          <button type="button" :disabled="requestPage >= requestPages" @click="pageRequests(1)">下一页</button>
        </div>
      </div>

      <!-- 抄送我的 -->
      <div v-else class="ap-list">
        <div v-for="c in ccList" :key="c.requestId" class="ap-row" @click="openDetail({ requestId: c.requestId }); markRead(c)">
          <div class="ap-row-main">
            <strong>{{ c.title }}</strong>
            <small>{{ c.bizTypeName }} · 发起人 {{ c.applicantName || '—' }} · 抄送于 {{ fmt(c.ccAt) }}</small>
          </div>
          <em :class="['ap-status', statusClass(c.status)]">{{ c.statusName || c.status }}</em>
          <em v-if="!c.read" class="ap-status warn">未读</em>
        </div>
        <HubEmpty v-if="!loading && !ccList.length" title="暂无抄送" note="与你相关的审批抄送会出现在这里" />
        <div v-if="ccTotal > PAGE_SIZE" class="ap-pager">
          <button type="button" :disabled="ccPage <= 1" @click="pageCc(-1)">上一页</button>
          <span>{{ ccPage }} / {{ ccPages }}</span>
          <button type="button" :disabled="ccPage >= ccPages" @click="pageCc(1)">下一页</button>
        </div>
      </div>
    </section>

    <!-- 驳回弹层 -->
    <HubDialog v-if="rejecting" @close="rejecting = null">
      <div class="ap-form">
        <h3 style="margin:0;font-size:15px">驳回「{{ rejecting.title }}」</h3>
        <label>驳回意见<textarea v-model="rejectComment" placeholder="请填写驳回原因（必填）" /></label>
        <div class="ap-form-foot">
          <HubButton variant="ghost" @click="rejecting = null">取消</HubButton>
          <HubButton variant="danger" @click="submitReject">确认驳回</HubButton>
        </div>
      </div>
    </HubDialog>

    <!-- 详情弹层 -->
    <HubDialog v-if="detailOpen && detail" @close="detailOpen = false">
      <div class="ap-detail">
        <div class="ap-detail-title">
          <h3>{{ detail.title }}</h3>
          <p>{{ detail.bizTypeName }} · {{ detail.requestNo }} · 提交于 {{ fmt(detail.submittedAt) }}</p>
        </div>
        <dl class="ap-meta">
          <dt>状态</dt><dd><em :class="['ap-status', statusClass(detail.status)]">{{ detail.statusName || detail.status }}</em></dd>
          <dt>当前节点</dt><dd>{{ detail.currentStepName || '—' }}</dd>
          <template v-if="detail.leave">
            <dt>请假类型</dt><dd>{{ detail.leave.leaveName || '—' }}</dd>
            <dt>起止时间</dt><dd>{{ fmt(detail.leave.startAt) }} ~ {{ fmt(detail.leave.endAt) }}</dd>
            <dt>时长</dt><dd>{{ ((detail.leave.durationMinutes ?? 0) / 60).toFixed(1) }} 小时</dd>
            <dt>事由</dt><dd>{{ detail.leave.reason || '—' }}</dd>
          </template>
          <template v-else-if="detail.clockCorrection">
            <dt>补卡日期</dt><dd>{{ detail.clockCorrection.targetDate || '—' }}</dd>
            <dt>补卡类型</dt><dd>{{ detail.clockCorrection.missingPunchTypeName || detail.clockCorrection.missingPunchType || '—' }}</dd>
            <dt>应打卡时间</dt><dd>{{ fmt(detail.clockCorrection.expectedPunchAt) }}</dd>
            <dt>事由</dt><dd>{{ detail.clockCorrection.reason || '—' }}</dd>
          </template>
          <template v-else-if="detail.generic">
            <dt>摘要</dt><dd>{{ detail.generic.summary || '—' }}</dd>
            <dt v-if="detail.generic.formPayload">表单</dt><dd v-if="detail.generic.formPayload">{{ JSON.stringify(detail.generic.formPayload, null, 2) }}</dd>
          </template>
        </dl>
        <div v-if="actions.length" class="ap-section">
          <h4>操作记录</h4>
          <div class="ap-timeline">
            <div v-for="a in actions" :key="a.actionId" class="ap-timeline-item">
              <small>{{ fmt(a.actedAt) }}</small>
              <span>{{ a.actorName || '系统' }} · {{ a.actionTypeName || a.actionType }} · {{ a.stepName || '' }}{{ a.comment ? `：${a.comment}` : '' }}</span>
            </div>
          </div>
        </div>
        <div v-if="detail.attachments.length" class="ap-section">
          <h4>附件</h4>
          <div class="ap-attachments">
            <button v-for="att in detail.attachments" :key="att.attachmentRefId" type="button" @click="downloadAttachment(att.attachmentRefId)">
              <Download :size="14" />{{ att.displayName }}<small v-if="att.fileSize">（{{ (att.fileSize / 1024).toFixed(0) }} KB）</small>
            </button>
          </div>
        </div>
        <div v-if="canWithdraw" class="ap-form-foot">
          <HubButton variant="ghost" @click="detailOpen = false">关闭</HubButton>
          <HubButton variant="danger" :disabled="withdrawing" @click="withdraw">撤回申请</HubButton>
        </div>
      </div>
    </HubDialog>

    <!-- 发起审批弹层 -->
    <HubDialog v-if="createOpen" @close="createOpen = false; resetForm()">
      <!-- 第一步：选择类型 -->
      <div v-if="createStep === 'pick'" class="ap-form">
        <h3 style="margin:0;font-size:15px">选择审批类型</h3>
        <div class="ap-type-grid">
          <button v-for="o in PROCESS_OPTIONS" :key="o.code" type="button" @click="pickProcess(o.code)">{{ o.label }}</button>
        </div>
      </div>

      <!-- 第二步：填写表单 -->
      <div v-else class="ap-form">
        <h3 style="margin:0;font-size:15px">发起{{ selectedLabel }}申请</h3>

        <!-- 请假 -->
        <template v-if="isLeave">
          <label>请假类型
            <select v-model.number="leaveTypeId">
              <option v-for="a in leaveAccounts" :key="a.accountId" :value="a.accountId">{{ a.leaveName }}</option>
            </select>
          </label>
          <label>开始时间<input v-model="leaveStart" type="datetime-local" @change="previewDuration" /></label>
          <label>结束时间<input v-model="leaveEnd" type="datetime-local" @change="previewDuration" /></label>
          <p v-if="leaveDuration !== null" class="ap-duration">预计时长：{{ (leaveDuration / 60).toFixed(1) }} 小时</p>
          <label>事由<textarea v-model="leaveReason" placeholder="请填写请假事由（必填）" /></label>
        </template>

        <!-- 补卡 -->
        <template v-else-if="isClock">
          <label>补卡日期<input v-model="ccTargetDate" type="date" /></label>
          <label>补卡类型
            <select v-model="ccPunchType">
              <option value="ON_DUTY">上班卡</option>
              <option value="OFF_DUTY">下班卡</option>
            </select>
          </label>
          <label>应打卡时间<input v-model="ccExpectedAt" type="datetime-local" /></label>
          <label>事由<textarea v-model="ccReason" placeholder="请填写补卡原因（必填）" /></label>
        </template>

        <!-- 通用 -->
        <template v-else>
          <label>标题（选填）<input v-model="genericTitle" placeholder="默认自动生成" /></label>
          <label>事由<textarea v-model="genericSummary" placeholder="请填写申请事由（必填）" /></label>
          <label v-if="needAmount">金额（元）<input v-model="genericAmount" type="number" min="0" step="0.01" placeholder="选填" /></label>
          <label>日期<input v-model="genericDate" type="date" /></label>
        </template>

        <div class="ap-form-foot">
          <HubButton variant="ghost" @click="createStep = 'pick'">上一步</HubButton>
          <HubButton variant="primary" :disabled="submitting" @click="submitCreate">{{ submitting ? '提交中…' : '提交申请' }}</HubButton>
        </div>
      </div>
    </HubDialog>

    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped src="../styles/approvals.css" />
