<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type {
  WorkflowRequestDetailResponse,
  WorkflowRequestSummaryResponse,
  WorkflowTaskActionResponse,
  WorkflowTemplateResponse,
  WorkflowTemplateVersionResponse,
} from '@/api/types';
import {
  errorMessage,
  fileScanStatusLabel,
  formatBytes,
  formatDateTime,
  formatFullDateTime,
  formatId,
  formatMinutes,
  punchTypeLabel,
  workflowActionLabel,
  workflowBizLabel,
  workflowDefinitionLabel,
  workflowStepLabel,
  workflowStatusLabel,
} from '@/utils/format';
import { useWorkflowGovernanceStore } from '@/stores/workflowGovernance';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminCompactList from '@/components/AdminCompactList.vue';
import AdminPagination from '@/components/AdminPagination.vue';

const store = useWorkflowGovernanceStore();
const toast = useToast();

const loading = ref(false);
const detailLoading = ref(false);
const items = ref<WorkflowRequestSummaryResponse[]>([]);
const selectedDetail = ref<WorkflowRequestDetailResponse | null>(null);
const templates = ref<WorkflowTemplateResponse[]>([]);
const templateVersions = ref<WorkflowTemplateVersionResponse[]>([]);
const actions = ref<WorkflowTaskActionResponse[]>([]);
const publishingVersionId = ref<string | number | null>(null);
const STATUS_APPROVING = ['IN', 'APPROVAL'].join('_');
const BIZ_LEAVE = ['LEA', 'VE'].join('');

const requestColumns: TableColumn[] = [
  { key: 'title', label: '申请' },
  { key: 'bizType', label: '业务' },
  { key: 'status', label: '状态' },
  { key: 'submittedAt', label: '提交时间' },
];

const detailRows = computed<DetailRow[]>(() => {
  const r = selectedDetail.value;
  if (!r) return [];
  const applicantName = r.applicantName || '未命名员工';
  const rows: DetailRow[] = [
    { label: '申请人', value: applicantName },
    { label: '业务', value: r.bizTypeName || workflowBizLabel(r.bizType) },
    { label: '状态', value: r.statusName || workflowStatusLabel(r.status) },
    { label: '当前节点', value: r.currentStepName || workflowStepLabel(r.currentStepKey) },
    { label: '提交时间', value: formatFullDateTime(r.submittedAt) },
    { label: '结束时间', value: formatFullDateTime(r.finishedAt) },
  ];
  if (r.leave) {
    rows.push(
      { label: '假种', value: r.leave.leaveName || '未命名假种' },
      { label: '时长', value: formatMinutes(r.leave.durationMinutes) },
      { label: '时间', value: `${formatDateTime(r.leave.startAt)} - ${formatDateTime(r.leave.endAt)}` },
      { label: '原因', value: r.leave.reason || '-' },
    );
  }
  if (r.clockCorrection) {
    rows.push(
      { label: '日期', value: r.clockCorrection.targetDate || '-' },
      { label: '补卡类型', value: r.clockCorrection.missingPunchTypeName || punchTypeLabel(r.clockCorrection.missingPunchType) },
      { label: '补卡时间', value: formatFullDateTime(r.clockCorrection.expectedPunchAt) },
      { label: '原因', value: r.clockCorrection.reason || '-' },
    );
  }
  return rows;
});

onMounted(() => { void loadPage(); });

async function loadPage() {
  loading.value = true;

  try {
    const [requestPage, templateRows] = await Promise.all([
      adminApi.workflow.requests({
        pageNo: store.pagination.pageNo,
        pageSize: store.pagination.pageSize,
        status: store.filters.status,
        bizType: store.filters.bizType,
        keyword: store.filters.keyword,
      }),
      adminApi.workflow.templates(),
    ]);
    items.value = requestPage.list;
    store.pagination.total = requestPage.total;
    templates.value = templateRows;
    if (store.selectedRequestId && requestPage.list.some((r) => r.requestId === store.selectedRequestId)) {
      await selectRequest(requestPage.list.find((r) => r.requestId === store.selectedRequestId)!);
    } else if (requestPage.list.length > 0) {
      await selectRequest(requestPage.list[0]);
    }
    if (store.selectedTemplateId && templateRows.some((t) => t.templateId === store.selectedTemplateId)) {
      await selectTemplate(store.selectedTemplateId);
    } else if (templateRows.length > 0) {
      await selectTemplate(templateRows[0].templateId);
    }
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

async function selectRequest(request: WorkflowRequestSummaryResponse) {
  store.selectedRequestId = request.requestId;
  detailLoading.value = true;
  try {
    const [detailResult, actionRows] = await Promise.all([
      adminApi.workflow.requestDetail(request.requestId),
      adminApi.workflow.requestActions(request.requestId),
    ]);
    selectedDetail.value = detailResult;
    actions.value = actionRows;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    detailLoading.value = false;
  }
}

async function selectTemplate(templateId: string | number) {
  store.selectedTemplateId = templateId;
  try {
    templateVersions.value = await adminApi.workflow.templateVersions(templateId);
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

async function publishTemplateVersion(version: WorkflowTemplateVersionResponse) {
  const template = templates.value.find((item) => String(item.templateId) === String(version.templateId));
  const definitionKeys: Record<string, string> = {
    [BIZ_LEAVE]: 'leave_manager_approval',
    CLOCK_CORRECTION: 'clock_correction_manager_approval',
    EXPENSE: 'expense_manager_approval',
    BUSINESS_TRIP: 'business_trip_manager_approval',
    OVERTIME: 'overtime_manager_approval',
    SEAL: 'seal_manager_approval',
    PURCHASE: 'purchase_manager_approval',
  };
  const flowableDefinitionKey = template?.processCode ? definitionKeys[template.processCode] ?? '' : '';
  if (!flowableDefinitionKey) {
    toast.error('未配置审批流程');
    return;
  }

  publishingVersionId.value = version.versionId;
  try {
    await adminApi.workflow.publishTemplateVersion(version.versionId, { flowableDefinitionKey });
    toast.success('模板版本已发布');
    await loadPage();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    publishingVersionId.value = null;
  }
}
</script>

<template>
  <main class="page-grid">
    <AdminPanel :span="7" :title="`申请列表 (${store.pagination.total})`">
      <template #actions>
        <button class="secondary-button" :disabled="loading" @click="loadPage">
          <RefreshCcw :size="16" />
          <span>刷新</span>
        </button>
      </template>
      <AdminFilterBar @search="loadPage">
        <select v-model="store.filters.status">
          <option value="">全部状态</option>
          <option value="PENDING_REVIEW">待预审</option>
          <option :value="STATUS_APPROVING">审批中</option>
          <option value="APPROVED">已通过</option>
          <option value="REJECTED">已驳回</option>
          <option value="WITHDRAWN">已撤回</option>
        </select>
        <select v-model="store.filters.bizType">
          <option value="">全部业务</option>
          <option :value="BIZ_LEAVE">请假</option>
          <option value="CLOCK_CORRECTION">补卡</option>
          <option value="EXPENSE">费用报销</option>
          <option value="BUSINESS_TRIP">出差</option>
          <option value="OVERTIME">加班</option>
          <option value="SEAL">用章</option>
          <option value="PURCHASE">采购</option>
        </select>
        <input v-model="store.filters.keyword" placeholder="申请标题" />
      </AdminFilterBar>
      <AdminTable
        :columns="requestColumns"
        :rows="items"
        :loading="loading"
        row-key="requestId"
        :selected-id="store.selectedRequestId"
        @select="selectRequest"
      >
        <template #cell-title="{ row }">
          <strong>{{ row.title }}</strong>
        </template>
        <template #cell-bizType="{ row }">
          {{ row.bizTypeName || workflowBizLabel(row.bizType) }}
        </template>
        <template #cell-status="{ row }">
          <span class="status-pill">{{ row.statusName || workflowStatusLabel(row.status) }}</span>
        </template>
        <template #cell-submittedAt="{ row }">
          {{ formatDateTime(row.submittedAt) }}
        </template>
      </AdminTable>
      <AdminPagination
        :page-no="store.pagination.pageNo"
        :page-size="store.pagination.pageSize"
        :total="store.pagination.total"
        @update:page-no="(v) => { store.pagination.pageNo = v; loadPage(); }"
        @update:page-size="(v) => { store.pagination.pageSize = v; store.pagination.pageNo = 1; loadPage(); }"
      />
    </AdminPanel>

    <AdminPanel :span="5" title="申请详情">
      <AdminDetailList :rows="detailRows" />

      <h3>附件</h3>
      <AdminCompactList :empty="!selectedDetail || selectedDetail.attachments.length === 0">
        <template v-if="selectedDetail">
          <article v-for="att in selectedDetail.attachments" :key="formatId(att.attachmentRefId)">
            <strong>{{ att.displayName }}</strong>
            <span>{{ formatBytes(att.fileSize) }} · {{ att.scanStatusName || fileScanStatusLabel(att.scanStatus) }}</span>
          </article>
        </template>
      </AdminCompactList>

      <h3>审批轨迹</h3>
      <AdminCompactList :loading="detailLoading" :empty="actions.length === 0">
        <article v-for="action in actions" :key="formatId(action.actionId)">
          <strong>{{ action.actionTypeName || workflowActionLabel(action.actionType) }} · {{ action.stepName || workflowStepLabel(action.stepKey) }}</strong>
          <span>{{ action.actorName || action.actorTypeName || '系统处理' }} · {{ formatDateTime(action.actedAt) }}</span>
          <span>{{ action.beforeStatusName || workflowStatusLabel(action.beforeStatus) }} → {{ action.afterStatusName || workflowStatusLabel(action.afterStatus) }}</span>
          <span>{{ action.comment || '无备注' }}</span>
        </article>
      </AdminCompactList>
    </AdminPanel>

    <AdminPanel :span="12" title="模板版本">
      <div class="split-content">
        <AdminCompactList>
          <article
            v-for="tpl in templates"
            :key="formatId(tpl.templateId)"
            class="clickable"
            :class="{ selected: store.selectedTemplateId === tpl.templateId }"
            @click="selectTemplate(tpl.templateId)"
          >
            <strong>{{ tpl.templateName }}</strong>
            <span>{{ workflowBizLabel(tpl.processCode) }} · {{ tpl.statusName || workflowStatusLabel(tpl.status) }}</span>
          </article>
        </AdminCompactList>
        <AdminCompactList :empty="templateVersions.length === 0">
          <article v-for="ver in templateVersions" :key="formatId(ver.versionId)">
            <strong>v{{ ver.versionNo }} · {{ ver.statusName || workflowStatusLabel(ver.status) }}</strong>
            <span>{{ workflowDefinitionLabel(ver.flowableDefinitionKey) }}</span>
            <button
              v-if="ver.status === 'DRAFT'"
              class="primary-button action-btn"
              :disabled="publishingVersionId === ver.versionId"
              @click.stop="publishTemplateVersion(ver)"
            >
              发布
            </button>
          </article>
        </AdminCompactList>
      </div>
    </AdminPanel>
  </main>
</template>

<style scoped>
.split-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-lg);
}

@media (max-width: 980px) {
  .split-content { grid-template-columns: 1fr; }
}
</style>
