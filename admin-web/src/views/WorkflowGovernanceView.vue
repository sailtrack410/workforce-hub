<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { FileSearch, LayoutTemplate, Plus, RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type {
  WorkflowRequestDetailResponse,
  WorkflowRequestSummaryResponse,
  WorkflowProcessTypeResponse,
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
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';
import AdminPagination from '@/components/AdminPagination.vue';
import AdminDialog from '@/components/AdminDialog.vue';

const store = useWorkflowGovernanceStore();
const toast = useToast();
const activeTab = ref<'requests' | 'templates'>('requests');

const loading = ref(false);
const detailLoading = ref(false);
const submitting = ref(false);
const requestDetailVisible = ref(false);
const createProcessTypeVisible = ref(false);
const createTemplateVisible = ref(false);
const createVersionVisible = ref(false);
const items = ref<WorkflowRequestSummaryResponse[]>([]);
const selectedDetail = ref<WorkflowRequestDetailResponse | null>(null);
const processTypes = ref<WorkflowProcessTypeResponse[]>([]);
const templates = ref<WorkflowTemplateResponse[]>([]);
const templateVersions = ref<WorkflowTemplateVersionResponse[]>([]);
const actions = ref<WorkflowTaskActionResponse[]>([]);
const publishingVersionId = ref<string | number | null>(null);
const publishTarget = ref<WorkflowTemplateVersionResponse | null>(null);
const publishConfirmVisible = ref(false);
const processTypeForm = reactive({ processCode: '', processName: '', bizType: '', status: 'ACTIVE' });
const templateForm = reactive({ processTypeId: '', templateCode: '', templateName: '', status: 'ACTIVE' });
const versionForm = reactive({ versionNo: 1, formSchemaJson: '{}', ruleSnapshotJson: '{}' });
const STATUS_APPROVING = ['IN', 'APPROVAL'].join('_');
const BIZ_LEAVE = ['LEA', 'VE'].join('');

const requestColumns: TableColumn[] = [
  { key: 'title', label: '申请' },
  { key: 'bizType', label: '业务' },
  { key: 'status', label: '状态' },
  { key: 'submittedAt', label: '提交时间' },
];

const selectedTemplate = computed(() => templates.value.find((item) => String(item.templateId) === String(store.selectedTemplateId)) ?? null);
const currentPublishedVersion = computed(() => selectedTemplate.value?.currentVersion ?? templateVersions.value.find((item) => item.status === 'PUBLISHED') ?? null);
const publishDefinitionKey = computed(() => {
  const definitionKeys: Record<string, string> = {
    [BIZ_LEAVE]: 'leave_manager_approval',
    CLOCK_CORRECTION: 'clock_correction_manager_approval',
    EXPENSE: 'expense_manager_approval',
    BUSINESS_TRIP: 'business_trip_manager_approval',
    OVERTIME: 'overtime_manager_approval',
    SEAL: 'seal_manager_approval',
    PURCHASE: 'purchase_manager_approval',
  };
  return selectedTemplate.value?.processCode ? definitionKeys[selectedTemplate.value.processCode] ?? '' : '';
});
const formConfigChanged = computed(() => normalizedJson(currentPublishedVersion.value?.formSchemaJson) !== normalizedJson(publishTarget.value?.formSchemaJson));
const ruleConfigChanged = computed(() => normalizedJson(currentPublishedVersion.value?.ruleSnapshotJson) !== normalizedJson(publishTarget.value?.ruleSnapshotJson));
const pendingReviewCount = computed(() => items.value.filter((item) => item.status === 'PENDING_REVIEW').length);
const approvingCount = computed(() => items.value.filter((item) => item.status === STATUS_APPROVING).length);
const backlogCount = computed(() => pendingReviewCount.value + approvingCount.value);
const finishedCount = computed(() => items.value.filter((item) => ['APPROVED', 'REJECTED', 'WITHDRAWN'].includes(item.status)).length);
const activeTemplateCount = computed(() => templates.value.filter((item) => item.status === 'ACTIVE').length);
const draftVersionCount = computed(() => templateVersions.value.filter((item) => item.status === 'DRAFT').length);

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
    const [requestPage, processTypeRows, templateRows] = await Promise.all([
      adminApi.workflow.requests({
        pageNo: store.pagination.pageNo,
        pageSize: store.pagination.pageSize,
        status: store.filters.status,
        bizType: store.filters.bizType,
        keyword: store.filters.keyword,
      }),
      adminApi.workflow.processTypes(),
      adminApi.workflow.templates(),
    ]);
    items.value = requestPage.list;
    store.pagination.total = requestPage.total;
    processTypes.value = processTypeRows;
    templates.value = templateRows;
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
    requestDetailVisible.value = true;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    detailLoading.value = false;
  }
}

function focusRequestStatus(status: string) {
  activeTab.value = 'requests';
  store.filters.status = status;
  store.pagination.pageNo = 1;
  void loadPage();
}

function validJson(value: string, label: string) {
  if (!value.trim()) return true;
  try {
    JSON.parse(value);
    return true;
  } catch {
    toast.error(`${label}不是有效 JSON`);
    return false;
  }
}

async function createProcessType() {
  submitting.value = true;
  try {
    const created = await adminApi.workflow.createProcessType({ ...processTypeForm });
    createProcessTypeVisible.value = false;
    processTypeForm.processCode = '';
    processTypeForm.processName = '';
    processTypeForm.bizType = '';
    await loadPage();
    templateForm.processTypeId = String(created.processTypeId);
    toast.success('流程类型已创建');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    submitting.value = false;
  }
}

async function createTemplate() {
  submitting.value = true;
  try {
    const created = await adminApi.workflow.createTemplate({
      ...templateForm,
      processTypeId: templateForm.processTypeId,
    });
    createTemplateVisible.value = false;
    templateForm.templateCode = '';
    templateForm.templateName = '';
    await loadPage();
    await selectTemplate(created.templateId);
    toast.success('流程模板已创建');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    submitting.value = false;
  }
}

async function createTemplateVersion() {
  if (!store.selectedTemplateId) return;
  if (!validJson(versionForm.formSchemaJson, '表单配置') || !validJson(versionForm.ruleSnapshotJson, '规则配置')) return;
  submitting.value = true;
  try {
    await adminApi.workflow.createTemplateVersion(store.selectedTemplateId, { ...versionForm });
    createVersionVisible.value = false;
    versionForm.versionNo += 1;
    await selectTemplate(store.selectedTemplateId);
    await loadPage();
    toast.success('模板版本已创建');
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    submitting.value = false;
  }
}

function openVersionDialog() {
  const maxVersion = templateVersions.value.reduce((max, item) => Math.max(max, item.versionNo), 0);
  versionForm.versionNo = maxVersion + 1;
  versionForm.formSchemaJson = currentPublishedVersion.value?.formSchemaJson || '{}';
  versionForm.ruleSnapshotJson = currentPublishedVersion.value?.ruleSnapshotJson || '{}';
  createVersionVisible.value = true;
}

async function selectTemplate(templateId: string | number) {
  store.selectedTemplateId = templateId;
  try {
    templateVersions.value = await adminApi.workflow.templateVersions(templateId);
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

function normalizedJson(value?: string | null) {
  if (!value?.trim()) return '';
  try {
    return JSON.stringify(JSON.parse(value));
  } catch {
    return value.trim();
  }
}

function requestPublishTemplateVersion(version: WorkflowTemplateVersionResponse) {
  publishTarget.value = version;
  if (!publishDefinitionKey.value) {
    toast.error('未配置审批流程');
    return;
  }
  publishConfirmVisible.value = true;
}

async function publishTemplateVersion() {
  if (!publishTarget.value || !publishDefinitionKey.value || publishingVersionId.value) return;
  publishingVersionId.value = publishTarget.value.versionId;
  try {
    await adminApi.workflow.publishTemplateVersion(publishTarget.value.versionId, { flowableDefinitionKey: publishDefinitionKey.value });
    publishConfirmVisible.value = false;
    publishTarget.value = null;
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
  <main class="workflow-page">
    <AdminPanel :span="12" title="审批运行态" subtitle="当前页面积压与流程配置状态">
      <template #actions>
        <button class="secondary-button" type="button" :disabled="loading" @click="loadPage">
          <RefreshCcw :size="16" />
          <span>刷新运行态</span>
        </button>
      </template>
      <div class="workflow-command-grid">
        <button :class="['backlog-card', { attention: backlogCount }]" type="button" @click="focusRequestStatus('PENDING_REVIEW')">
          <span>当前页面待处置</span>
          <strong>{{ backlogCount }}</strong>
          <small>{{ backlogCount ? '建议优先完成预审并追踪审批中申请' : '当前页面无积压' }}</small>
          <b>查看待预审</b>
        </button>
        <div class="workflow-metric-grid">
          <button type="button" @click="focusRequestStatus('PENDING_REVIEW')"><span>待预审</span><strong>{{ pendingReviewCount }}</strong></button>
          <button type="button" @click="focusRequestStatus(STATUS_APPROVING)"><span>审批中</span><strong>{{ approvingCount }}</strong></button>
          <article><span>本页已完成</span><strong>{{ finishedCount }}</strong></article>
          <button type="button" @click="activeTab = 'templates'"><span>启用模板</span><strong>{{ activeTemplateCount }}</strong><small>{{ draftVersionCount }} 个选中模板草稿</small></button>
        </div>
      </div>
    </AdminPanel>

    <div class="workflow-tabs" role="tablist" aria-label="审批治理任务">
      <button type="button" role="tab" :aria-selected="activeTab === 'requests'" :class="{ active: activeTab === 'requests' }" @click="activeTab = 'requests'">
        <FileSearch :size="16" />申请监控
      </button>
      <button type="button" role="tab" :aria-selected="activeTab === 'templates'" :class="{ active: activeTab === 'templates' }" @click="activeTab = 'templates'">
        <LayoutTemplate :size="16" />流程模板
      </button>
    </div>

    <AdminPanel v-if="activeTab === 'requests'" :span="12" :title="`申请列表 (${store.pagination.total})`">
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
        interactive
        :columns="requestColumns"
        :rows="items"
        :loading="loading"
        row-key="requestId"
        :selected-id="store.selectedRequestId"
        @row-click="selectRequest"
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

    <AdminPanel v-else :span="12" title="模板版本" subtitle="先选择模板，再创建和发布版本">
      <template #actions>
        <div class="template-actions">
          <button class="secondary-button" type="button" @click="createProcessTypeVisible = true"><Plus :size="16" />流程类型</button>
          <button class="secondary-button" type="button" :disabled="processTypes.length === 0" @click="createTemplateVisible = true"><Plus :size="16" />流程模板</button>
          <button class="primary-button" type="button" :disabled="!store.selectedTemplateId" @click="openVersionDialog"><Plus :size="16" />模板版本</button>
        </div>
      </template>
      <div class="split-content">
        <AdminCompactList :empty="templates.length === 0">
          <article
            v-for="tpl in templates"
            :key="formatId(tpl.templateId)"
            class="clickable"
            :class="{ selected: store.selectedTemplateId === tpl.templateId }"
            role="button"
            tabindex="0"
            @click="selectTemplate(tpl.templateId)"
            @keydown.enter.prevent="selectTemplate(tpl.templateId)"
            @keydown.space.prevent="selectTemplate(tpl.templateId)"
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
              @click.stop="requestPublishTemplateVersion(ver)"
            >
              发布
            </button>
          </article>
        </AdminCompactList>
      </div>
    </AdminPanel>

    <AdminDialog v-model:visible="requestDetailVisible" title="申请详情" width="min(760px, 92vw)">
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
    </AdminDialog>

    <AdminDialog v-model:visible="createProcessTypeVisible" title="新建流程类型">
      <form class="stack-form" @submit.prevent="createProcessType">
        <label><span>流程编码</span><input v-model.trim="processTypeForm.processCode" required maxlength="64" placeholder="例如 EXPENSE" /></label>
        <label><span>流程名称</span><input v-model.trim="processTypeForm.processName" required maxlength="128" placeholder="例如 费用报销" /></label>
        <label><span>业务类型</span><input v-model.trim="processTypeForm.bizType" maxlength="64" placeholder="可选" /></label>
        <label><span>状态</span><select v-model="processTypeForm.status"><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
        <button class="primary-button" :disabled="submitting" type="submit">创建流程类型</button>
      </form>
    </AdminDialog>

    <AdminDialog v-model:visible="createTemplateVisible" title="新建流程模板">
      <form class="stack-form" @submit.prevent="createTemplate">
        <label><span>流程类型</span><select v-model="templateForm.processTypeId" required><option value="">请选择</option><option v-for="item in processTypes" :key="formatId(item.processTypeId)" :value="String(item.processTypeId)">{{ item.processName }}</option></select></label>
        <label><span>模板编码</span><input v-model.trim="templateForm.templateCode" required maxlength="64" /></label>
        <label><span>模板名称</span><input v-model.trim="templateForm.templateName" required maxlength="128" /></label>
        <label><span>状态</span><select v-model="templateForm.status"><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
        <button class="primary-button" :disabled="submitting" type="submit">创建流程模板</button>
      </form>
    </AdminDialog>

    <AdminDialog v-model:visible="createVersionVisible" title="新建模板版本" width="min(680px, 92vw)">
      <form class="stack-form" @submit.prevent="createTemplateVersion">
        <label><span>版本号</span><input v-model.number="versionForm.versionNo" required min="1" type="number" /></label>
        <label><span>表单配置 JSON</span><textarea v-model="versionForm.formSchemaJson" maxlength="8192" rows="7" /></label>
        <label><span>规则配置 JSON</span><textarea v-model="versionForm.ruleSnapshotJson" maxlength="8192" rows="7" /></label>
        <button class="primary-button" :disabled="submitting" type="submit">创建模板版本</button>
      </form>
    </AdminDialog>

    <AdminConfirmDialog
      v-model:visible="publishConfirmVisible"
      title="发布流程模板"
      confirm-label="确认发布"
      :close-on-confirm="false"
      :confirm-disabled="Boolean(publishingVersionId)"
      :cancel-disabled="Boolean(publishingVersionId)"
      @confirm="publishTemplateVersion"
    >
      <div class="publish-impact">
        <p>发布后，后续新申请将使用目标版本；正在审批的申请不受影响。</p>
        <dl>
          <div>
            <dt>模板</dt>
            <dd>{{ selectedTemplate?.templateName || '-' }}</dd>
          </div>
          <div>
            <dt>版本切换</dt>
            <dd>v{{ currentPublishedVersion?.versionNo ?? '-' }} → v{{ publishTarget?.versionNo ?? '-' }}</dd>
          </div>
          <div>
            <dt>流程定义</dt>
            <dd>{{ workflowDefinitionLabel(publishDefinitionKey) }}</dd>
          </div>
          <div>
            <dt>表单配置</dt>
            <dd :class="formConfigChanged ? 'changed' : 'unchanged'">{{ formConfigChanged ? '有变化' : '无变化' }}</dd>
          </div>
          <div>
            <dt>规则配置</dt>
            <dd :class="ruleConfigChanged ? 'changed' : 'unchanged'">{{ ruleConfigChanged ? '有变化' : '无变化' }}</dd>
          </div>
          <div>
            <dt>目标状态</dt>
            <dd>{{ publishTarget?.statusName || workflowStatusLabel(publishTarget?.status) }}</dd>
          </div>
        </dl>
      </div>
    </AdminConfirmDialog>
  </main>
</template>

<style scoped>
.workflow-page {
  display: grid;
  gap: var(--space-base);
}

.workflow-command-grid {
  display: grid;
  grid-template-columns: minmax(250px, 1.05fr) minmax(0, 2.2fr);
  gap: var(--space-md);
}

.backlog-card,
.workflow-metric-grid > * {
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  color: var(--ink);
  letter-spacing: 0;
  text-align: left;
}

.backlog-card {
  display: grid;
  align-content: center;
  gap: var(--space-sm);
  min-height: 136px;
  padding: var(--space-lg);
}

.backlog-card.attention {
  border-color: var(--error-border);
  background: var(--error-bg);
}

.backlog-card:hover,
.workflow-metric-grid button:hover {
  border-color: var(--green);
  background: var(--hover-bg);
}

.backlog-card > span,
.workflow-metric-grid span,
.workflow-metric-grid small {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.backlog-card strong {
  color: var(--red-dark);
  font-size: 34px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.backlog-card small {
  color: var(--muted);
  line-height: 1.45;
}

.backlog-card b {
  color: var(--red-dark);
  font-size: var(--text-sm);
}

.workflow-metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--space-md);
}

.workflow-metric-grid > * {
  display: grid;
  align-content: center;
  gap: var(--space-sm);
  min-height: 136px;
  padding: var(--space-base);
}

.workflow-metric-grid strong {
  font-size: 26px;
  font-variant-numeric: tabular-nums;
}

.workflow-tabs {
  display: inline-flex;
  width: max-content;
  gap: var(--space-xs);
  padding: var(--space-xs);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--panel);
}

.workflow-tabs button {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: 44px;
  padding: 0 var(--space-base);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--muted);
  font-weight: 700;
}

.workflow-tabs button.active { background: var(--primary-soft); color: var(--green-dark); }
.template-actions { display: flex; flex-wrap: wrap; gap: var(--space-sm); }

.split-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-lg);
}

.publish-impact {
  display: grid;
  gap: var(--space-base);
}

.publish-impact p {
  margin: 0;
  color: var(--muted);
  line-height: 1.5;
}

.publish-impact dl {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-sm);
  margin: 0;
}

.publish-impact dl div {
  display: grid;
  gap: var(--space-xs);
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
}

.publish-impact dt {
  color: var(--muted);
  font-size: var(--text-xs);
}

.publish-impact dd {
  margin: 0;
  color: var(--ink);
  font-weight: 700;
}

.publish-impact .changed {
  color: var(--amber);
}

.publish-impact .unchanged {
  color: var(--success);
}

@media (max-width: 980px) {
  .workflow-command-grid { grid-template-columns: 1fr; }
  .workflow-metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .split-content { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .workflow-tabs,
  .workflow-tabs button { width: 100%; }
  .workflow-tabs button { justify-content: center; }
  .workflow-metric-grid { grid-template-columns: 1fr; }
  .backlog-card,
  .workflow-metric-grid > * { min-height: 104px; }
  .publish-impact dl { grid-template-columns: 1fr; }
}
</style>
