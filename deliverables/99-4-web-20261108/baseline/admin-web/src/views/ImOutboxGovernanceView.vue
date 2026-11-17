<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { RefreshCcw, RotateCcw, Send } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type {
  AdminImOutboxAuditResponse,
  AdminImOutboxEventResponse,
  AdminImOutboxFailureSummaryResponse,
  AdminImOutboxSummaryResponse,
  EntityId,
  PageResult,
} from '@/api/types';
import { errorMessage, formatDateTime, formatId, safeBusinessMessage, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminPagination from '@/components/AdminPagination.vue';
import AdminCompactList from '@/components/AdminCompactList.vue';
import AdminDialog from '@/components/AdminDialog.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const toast = useToast();
const loading = ref(false);
const operating = ref(false);
const detailVisible = ref(false);
const confirmVisible = ref(false);
const operation = ref<'single' | 'batch' | 'dispatch'>('dispatch');
const summary = ref<AdminImOutboxSummaryResponse | null>(null);
const failureSummary = ref<AdminImOutboxFailureSummaryResponse[]>([]);
const events = ref<PageResult<AdminImOutboxEventResponse>>({ pageNo: 1, pageSize: 20, total: 0, list: [] });
const selectedEvent = ref<AdminImOutboxEventResponse | null>(null);
const audits = ref<AdminImOutboxAuditResponse[]>([]);
const selectedIds = ref<EntityId[]>([]);
const dispatchBatchSize = ref(50);
const filters = reactive({ status: 'FAILED', eventType: '', conversationId: '', messageId: '' });

const columns: TableColumn[] = [
  { key: 'selection', label: '选择', width: '64px' },
  { key: 'eventType', label: '事件' },
  { key: 'conversationId', label: '会话' },
  { key: 'status', label: '状态' },
  { key: 'retryCount', label: '重试' },
  { key: 'lastError', label: '最近错误' },
  { key: 'nextRetryAt', label: '下次重试' },
];

const statusCards = computed(() => summary.value?.statusCounts ?? []);
const sentCount = computed(() => statusCards.value.find((item) => item.status === 'SENT')?.count ?? 0);
const failedCount = computed(() => statusCards.value.find((item) => item.status === 'FAILED')?.count ?? 0);
const deadCount = computed(() => statusCards.value.find((item) => item.status === 'DEAD')?.count ?? 0);
const deliveryRiskCount = computed(() => failedCount.value + deadCount.value + Number(summary.value?.staleSendingCount ?? 0));
const sentShare = computed(() => {
  const total = Number(summary.value?.totalCount ?? 0);
  return total ? `${Math.round((sentCount.value / total) * 1000) / 10}%` : '-';
});
const deliveryHealthLabel = computed(() => deliveryRiskCount.value ? '投递受阻' : '链路稳定');
const retryableRows = computed(() => events.value.list.filter((item) => ['FAILED', 'DEAD'].includes(item.status)));
const allRetryableSelected = computed(() => retryableRows.value.length > 0 && retryableRows.value.every((row) => selectedIds.value.some((id) => String(id) === String(row.eventId))));
const confirmTitle = computed(() => operation.value === 'dispatch' ? '派发到期事件' : operation.value === 'batch' ? '批量重试事件' : '重试投递事件');
const confirmLabel = computed(() => operation.value === 'dispatch' ? '确认派发' : '确认重试');

onMounted(loadPage);

async function loadPage() {
  loading.value = true;
  try {
    const [summaryResult, failureResult, eventPage] = await Promise.all([
      adminApi.imOutbox.summary(),
      adminApi.imOutbox.failureSummary(),
      adminApi.imOutbox.list({
        pageNo: events.value.pageNo,
        pageSize: events.value.pageSize,
        status: filters.status || undefined,
        eventType: filters.eventType || undefined,
        conversationId: filters.conversationId || undefined,
        messageId: filters.messageId || undefined,
      }),
    ]);
    summary.value = summaryResult;
    failureSummary.value = failureResult;
    events.value = eventPage;
    selectedIds.value = selectedIds.value.filter((id) => eventPage.list.some((item) => String(item.eventId) === String(id) && ['FAILED', 'DEAD'].includes(item.status)));
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function search() {
  events.value.pageNo = 1;
  void loadPage();
}

function filterByStatus(status: string) {
  filters.status = status;
  search();
}

function toggleSelection(eventId: EntityId) {
  const index = selectedIds.value.findIndex((id) => String(id) === String(eventId));
  if (index >= 0) selectedIds.value.splice(index, 1);
  else if (selectedIds.value.length < 100) selectedIds.value.push(eventId);
}

function toggleAllRetryable() {
  selectedIds.value = allRetryableSelected.value ? [] : retryableRows.value.slice(0, 100).map((item) => item.eventId);
}

async function openDetail(row: AdminImOutboxEventResponse) {
  loading.value = true;
  try {
    const [detail, auditPage] = await Promise.all([
      adminApi.imOutbox.detail(row.eventId),
      adminApi.imOutbox.audits({ pageNo: 1, pageSize: 50, eventId: row.eventId }),
    ]);
    selectedEvent.value = detail;
    audits.value = auditPage.list;
    detailVisible.value = true;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function requestSingleRetry() {
  if (!selectedEvent.value || !['FAILED', 'DEAD'].includes(selectedEvent.value.status)) return;
  operation.value = 'single';
  confirmVisible.value = true;
}

function requestBatchRetry() {
  if (!selectedIds.value.length) return;
  operation.value = 'batch';
  confirmVisible.value = true;
}

function requestDispatch() {
  operation.value = 'dispatch';
  confirmVisible.value = true;
}

async function confirmOperation() {
  operating.value = true;
  try {
    if (operation.value === 'single' && selectedEvent.value) {
      selectedEvent.value = await adminApi.imOutbox.retry(selectedEvent.value.eventId);
      toast.success('事件已重置为待发送');
    } else if (operation.value === 'batch') {
      const result = await adminApi.imOutbox.batchRetry(selectedIds.value);
      toast.success(`重试成功 ${result.succeededCount} 条，跳过 ${result.skippedCount} 条`);
      selectedIds.value = [];
    } else {
      const result = await adminApi.imOutbox.dispatch(dispatchBatchSize.value);
      toast.success(`已派发 ${result.dispatchedCount} 条到期事件`);
    }
    confirmVisible.value = false;
    await loadPage();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    operating.value = false;
  }
}

function prettyJson(value?: string | null) {
  if (!value) return '-';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
</script>

<template>
  <main class="page-grid">
    <AdminPanel :span="12" title="投递总览" subtitle="WebSocket 可靠投递状态与当前待处置事件">
      <template #actions>
        <button class="secondary-button" :disabled="loading" @click="loadPage"><RefreshCcw :size="16" />刷新</button>
        <button class="primary-button" type="button" :disabled="operating" @click="requestDispatch"><Send :size="16" />派发到期事件</button>
      </template>
      <div class="delivery-command-grid">
        <article :class="['delivery-health', { attention: deliveryRiskCount }]">
          <span>当前投递健康</span>
          <strong>{{ deliveryHealthLabel }}</strong>
          <p>{{ deliveryRiskCount ? `${deliveryRiskCount} 条失败、终止或停滞事件待处置` : '未发现失败与停滞事件' }}</p>
          <small>已发送占比 {{ sentShare }}</small>
        </article>
        <div class="delivery-queue-grid">
          <button type="button" @click="requestDispatch"><span>到期可派发</span><strong>{{ summary?.dueDispatchCount ?? 0 }}</strong><small>立即进入派发确认</small></button>
          <button :class="{ attention: failedCount }" type="button" @click="filterByStatus('FAILED')"><span>投递失败</span><strong>{{ failedCount }}</strong><small>可选择后批量重试</small></button>
          <button :class="{ attention: deadCount }" type="button" @click="filterByStatus('DEAD')"><span>已终止</span><strong>{{ deadCount }}</strong><small>需人工核对后重试</small></button>
          <button :class="{ attention: summary?.staleSendingCount }" type="button" @click="filterByStatus('SENDING')"><span>发送中停滞</span><strong>{{ summary?.staleSendingCount ?? 0 }}</strong><small>检查锁定与超时事件</small></button>
        </div>
      </div>
      <div class="status-breakdown">
        <span>状态分布</span>
        <div class="summary-grid">
          <button v-for="card in statusCards" :key="card.status" type="button" :class="['summary-card', { active: filters.status === card.status }]" @click="filterByStatus(card.status)">
            <span>{{ card.statusName || statusLabel(card.status) }}</span>
            <strong>{{ card.count }}</strong>
          </button>
        </div>
      </div>
    </AdminPanel>

    <AdminPanel :span="12" :title="`投递事件 (${events.total})`">
      <template #actions>
        <button class="secondary-button" type="button" :disabled="selectedIds.length === 0 || operating" @click="requestBatchRetry"><RotateCcw :size="16" />重试已选 {{ selectedIds.length ? `(${selectedIds.length})` : '' }}</button>
      </template>
      <AdminFilterBar @search="search">
        <select v-model="filters.status"><option value="">全部状态</option><option value="PENDING">待发送</option><option value="SENDING">发送中</option><option value="SENT">已发送</option><option value="FAILED">失败</option><option value="DEAD">终止</option></select>
        <select v-model="filters.eventType"><option value="">全部事件</option><option value="IM_MESSAGE_CREATED">消息创建</option><option value="IM_MESSAGE_EDITED">消息编辑</option><option value="IM_MESSAGE_RECALLED">消息撤回</option><option value="IM_READ_POSITION_UPDATED">已读位置更新</option><option value="IM_CONVERSATION_PREFERENCE_UPDATED">会话偏好更新</option><option value="IM_GROUP_PROFILE_UPDATED">群资料更新</option><option value="IM_GROUP_MEMBERS_ADDED">群成员加入</option><option value="IM_GROUP_MEMBERS_REMOVED">群成员移除</option><option value="IM_GROUP_MEMBER_LEFT">成员退群</option><option value="IM_GROUP_DISMISSED">群解散</option><option value="IM_AGENT_MESSAGE_CREATED">AI 消息创建</option><option value="IM_AGENT_REPLY_FAILED">AI 回复失败</option></select>
        <input v-model.trim="filters.conversationId" inputmode="numeric" placeholder="会话 ID" />
        <input v-model.trim="filters.messageId" inputmode="numeric" placeholder="消息 ID" />
      </AdminFilterBar>
      <AdminTable interactive :columns="columns" :rows="events.list" :loading="loading" row-key="eventId" @row-click="openDetail">
        <template #cell-selection="{ row }">
          <input v-if="['FAILED', 'DEAD'].includes(row.status)" class="row-check" type="checkbox" :checked="selectedIds.some((id) => String(id) === String(row.eventId))" :aria-label="`选择事件 ${formatId(row.eventId)}`" @click.stop @change="toggleSelection(row.eventId)" />
        </template>
        <template #cell-eventType="{ row }"><strong>{{ row.eventTypeName || row.eventType }}</strong><span>{{ formatId(row.eventId) }}</span></template>
        <template #cell-conversationId="{ row }">{{ formatId(row.conversationId) }}</template>
        <template #cell-status="{ row }"><span class="status-pill">{{ row.statusName || statusLabel(row.status) }}</span></template>
        <template #cell-lastError="{ row }">{{ row.lastError ? safeBusinessMessage(row.lastError) : '-' }}</template>
        <template #cell-nextRetryAt="{ row }">{{ formatDateTime(row.nextRetryAt) }}</template>
      </AdminTable>
      <div v-if="retryableRows.length" class="table-selection">
        <label><input type="checkbox" :checked="allRetryableSelected" @change="toggleAllRetryable" />选择本页可重试事件</label>
        <span>单次最多 100 条</span>
      </div>
      <AdminPagination :page-no="events.pageNo" :page-size="events.pageSize" :total="events.total" @update:page-no="(value) => { events.pageNo = value; loadPage(); }" @update:page-size="(value) => { events.pageSize = value; events.pageNo = 1; loadPage(); }" />
    </AdminPanel>

    <AdminPanel :span="12" title="失败原因聚合">
      <AdminCompactList :empty="failureSummary.length === 0">
        <article v-for="item in failureSummary" :key="`${item.status}-${item.lastError}`">
          <strong>{{ item.statusName || statusLabel(item.status) }} · {{ item.eventCount }} 条</strong>
          <span>{{ safeBusinessMessage(item.lastError) }}</span>
          <span>最高重试 {{ item.maxRetryCount }} 次 · 最近发生 {{ formatDateTime(item.lastOccurredAt) }}</span>
        </article>
      </AdminCompactList>
    </AdminPanel>

    <AdminDialog v-model:visible="detailVisible" title="投递事件详情" width="min(820px, 94vw)">
      <div v-if="selectedEvent" class="event-detail">
        <div class="detail-head"><div><strong>{{ selectedEvent.eventTypeName || selectedEvent.eventType }}</strong><span>事件 {{ formatId(selectedEvent.eventId) }} · 会话 {{ formatId(selectedEvent.conversationId) }}</span></div><button v-if="['FAILED', 'DEAD'].includes(selectedEvent.status)" class="primary-button" :disabled="operating" @click="requestSingleRetry"><RotateCcw :size="16" />重试</button></div>
        <dl><div><dt>状态</dt><dd>{{ selectedEvent.statusName || statusLabel(selectedEvent.status) }}</dd></div><div><dt>重试次数</dt><dd>{{ selectedEvent.retryCount }}</dd></div><div><dt>创建时间</dt><dd>{{ formatDateTime(selectedEvent.createdAt) }}</dd></div><div><dt>下次重试</dt><dd>{{ formatDateTime(selectedEvent.nextRetryAt) }}</dd></div></dl>
        <p v-if="selectedEvent.lastError" class="detail-error">{{ safeBusinessMessage(selectedEvent.lastError) }}</p>
        <h3>事件载荷</h3><pre>{{ prettyJson(selectedEvent.payloadJson) }}</pre>
        <h3>操作审计</h3>
        <AdminCompactList :empty="audits.length === 0"><article v-for="audit in audits" :key="formatId(audit.auditId)"><strong>{{ audit.actionTypeName || audit.actionType }} · {{ audit.resultName || audit.result }}</strong><span>{{ audit.operatorName || audit.operatorTypeName }} · {{ formatDateTime(audit.occurredAt) }}</span></article></AdminCompactList>
      </div>
    </AdminDialog>

    <AdminConfirmDialog v-model:visible="confirmVisible" :title="confirmTitle" :confirm-label="confirmLabel" :close-on-confirm="false" :confirm-disabled="operating" :cancel-disabled="operating" @confirm="confirmOperation">
      <div class="operation-impact">
        <p v-if="operation === 'single'">将事件 {{ formatId(selectedEvent?.eventId) }} 从“{{ selectedEvent?.statusName || statusLabel(selectedEvent?.status) }}”重置为待发送。</p>
        <p v-else-if="operation === 'batch'">将重试已选择的 {{ selectedIds.length }} 条失败或终止事件，不符合条件的事件会被跳过。</p>
        <template v-else><p>将立即扫描并派发当前到期事件。</p><label><span>本次派发上限</span><input v-model.number="dispatchBatchSize" type="number" min="1" max="500" /></label><span>当前到期可派发 {{ summary?.dueDispatchCount ?? 0 }} 条</span></template>
      </div>
    </AdminConfirmDialog>
  </main>
</template>

<style scoped>
.delivery-command-grid { display: grid; grid-template-columns: minmax(260px, 1.1fr) minmax(0, 2.2fr); gap: var(--space-md); }
.delivery-health, .delivery-queue-grid button { border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--panel-strong); color: var(--ink); letter-spacing: 0; text-align: left; }
.delivery-health { display: grid; align-content: center; gap: var(--space-sm); min-height: 138px; padding: var(--space-lg); border-color: var(--success-border); background: var(--success-bg); }
.delivery-health.attention, .delivery-queue-grid button.attention { border-color: var(--error-border); background: var(--error-bg); }
.delivery-health > span, .delivery-queue-grid span, .delivery-queue-grid small { color: var(--muted); font-size: var(--text-xs); font-weight: 700; }
.delivery-health strong { color: var(--success); font-size: var(--text-xl); }
.delivery-health.attention strong, .delivery-queue-grid button.attention strong { color: var(--red-dark); }
.delivery-health p { margin: 0; color: var(--muted); font-size: var(--text-sm); line-height: 1.5; }
.delivery-health small { color: var(--muted); font-size: var(--text-xs); font-weight: 700; }
.delivery-queue-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-md); }
.delivery-queue-grid button { display: grid; align-content: center; gap: var(--space-sm); min-height: 138px; padding: var(--space-base); }
.delivery-queue-grid button:hover { border-color: var(--green); background: var(--hover-bg); }
.delivery-queue-grid strong { font-size: 25px; line-height: 1; font-variant-numeric: tabular-nums; }
.status-breakdown { display: grid; gap: var(--space-sm); margin-top: var(--space-lg); padding-top: var(--space-base); border-top: 1px solid var(--line); }
.status-breakdown > span { color: var(--muted); font-size: var(--text-xs); font-weight: 800; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: var(--space-sm); }
.summary-card { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); min-height: 50px; padding: var(--space-sm) var(--space-md); border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--panel-strong); color: var(--ink); letter-spacing: 0; text-align: left; }
.summary-card:hover, .summary-card.active { border-color: var(--green); background: var(--hover-bg); }
.summary-card span { color: var(--muted); font-size: var(--text-xs); font-weight: 700; }
.summary-card strong { font-size: var(--text-lg); }
.row-check { width: 20px; min-height: 20px; }
.table-selection { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); margin-top: var(--space-md); color: var(--muted); font-size: var(--text-sm); }
.table-selection label { display: flex; align-items: center; gap: var(--space-sm); min-height: 44px; }
.table-selection input { width: 20px; min-height: 20px; }
.event-detail { display: grid; gap: var(--space-base); }
.detail-head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-md); }
.detail-head > div { display: grid; gap: var(--space-xs); }
.detail-head span { color: var(--muted); font-size: var(--text-sm); }
.event-detail dl { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-sm); margin: 0; }
.event-detail dl div { display: grid; gap: var(--space-xs); padding: var(--space-md); border: 1px solid var(--line); border-radius: var(--radius-sm); }
.event-detail dt { color: var(--muted); font-size: var(--text-xs); }
.event-detail dd { margin: 0; font-weight: 700; }
.event-detail h3 { margin: var(--space-sm) 0 0; }
.event-detail pre { max-height: 280px; overflow: auto; margin: 0; padding: var(--space-base); border-radius: var(--radius-sm); background: var(--table-head-bg); color: var(--ink); white-space: pre-wrap; word-break: break-word; }
.detail-error { margin: 0; padding: var(--space-md); border: 1px solid var(--error-border); border-radius: var(--radius-sm); background: var(--error-bg); color: var(--red-dark); }
.operation-impact { display: grid; gap: var(--space-md); }
.operation-impact p { margin: 0; color: var(--muted); line-height: 1.5; }
.operation-impact label { display: grid; gap: var(--space-xs); color: var(--muted); font-size: var(--text-sm); font-weight: 700; }
.operation-impact > span { color: var(--muted); font-size: var(--text-sm); }
@media (max-width: 1050px) { .delivery-command-grid { grid-template-columns: 1fr; } .delivery-queue-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 700px) { .delivery-queue-grid { grid-template-columns: 1fr; } .delivery-health, .delivery-queue-grid button { min-height: 104px; } .event-detail dl { grid-template-columns: repeat(2, minmax(0, 1fr)); } .detail-head { align-items: stretch; flex-direction: column; } }
</style>
