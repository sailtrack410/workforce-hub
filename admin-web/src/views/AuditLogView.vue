<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { AdminAuditLogResponse, PageResult } from '@/api/types';
import { auditActionLabel, auditOperatorLabel, auditTargetLabel, errorMessage, formatDateTime, formatId, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminPagination from '@/components/AdminPagination.vue';
import AdminDialog from '@/components/AdminDialog.vue';

const toast = useToast();
const loading = ref(false);
const page = ref<PageResult<AdminAuditLogResponse>>({ pageNo: 1, pageSize: 20, total: 0, list: [] });
const selected = ref<AdminAuditLogResponse | null>(null);
const detailVisible = ref(false);
const filters = reactive({ operatorType: '', actionType: '', targetType: '' });
const riskScope = ref<'ALL' | 'FAILED' | 'SENSITIVE' | 'AI'>('ALL');

const columns: TableColumn[] = [
  { key: 'occurredAt', label: '时间' },
  { key: 'operator', label: '操作人' },
  { key: 'actionType', label: '动作' },
  { key: 'target', label: '对象' },
  { key: 'result', label: '结果' },
];

const actionOptions = [
  ['TASK_CREATED', '创建 AI 任务'], ['TASK_SUCCEEDED', 'AI 任务完成'], ['TASK_FAILED', 'AI 任务失败'],
  ['APPROVE', '审批通过'], ['REJECT', '审批驳回'], ['SUBMIT', '提交申请'], ['REVOKE', '撤回申请'],
  ['FILE_DOWNLOAD_EXECUTED', '下载文件'], ['FILE_CLEANUP_JOB_RUN', '执行文件清理'],
  ['NOTIFY_CHANNEL_UPDATED', '修改通知渠道'], ['NOTIFY_CHANNEL_TEST', '测试通知渠道'],
];
const targetOptions = [
  ['WORKFLOW_REQUEST', '审批申请'], ['AGENT_TASK', 'AI 任务'], ['AGENT', 'AI 助手'], ['ATTACHMENT_REF', '文件'],
  ['NOTIFY_CHANNEL', '通知渠道'], ['LEAVE_TYPE', '假种'], ['LEAVE_ACCOUNT', '假期账户'],
  ['ATTENDANCE_CALENDAR', '工作日历'], ['ATTENDANCE_SHIFT', '班次'], ['ATTENDANCE_GROUP', '考勤组'],
];

const allRows = computed(() => page.value.list.map((row) => ({
  ...row,
  operator: row.operatorName || row.operatorAccount || row.operatorTypeName || auditOperatorLabel(row.operatorType, row.operatorId),
  target: row.targetDisplayName || row.targetNo || row.targetTypeName || auditTargetLabel(row.targetType),
})));
const failureCount = computed(() => allRows.value.filter(isFailureResult).length);
const sensitiveCount = computed(() => allRows.value.filter(isSensitiveAction).length);
const aiAutomationCount = computed(() => allRows.value.filter(isAiAutomation).length);
const rows = computed(() => {
  if (riskScope.value === 'FAILED') return allRows.value.filter(isFailureResult);
  if (riskScope.value === 'SENSITIVE') return allRows.value.filter(isSensitiveAction);
  if (riskScope.value === 'AI') return allRows.value.filter(isAiAutomation);
  return allRows.value;
});

function isFailureResult(row: AdminAuditLogResponse) {
  const result = String(row.result ?? '').toUpperCase();
  return Boolean(result && !['SUCCESS', 'SUCCEEDED', 'OK', 'PASSED'].includes(result));
}

function isSensitiveAction(row: AdminAuditLogResponse) {
  const action = row.actionType.toUpperCase();
  return ['DELETE', 'REMOVE', 'CLEANUP', 'DOWNLOAD', 'PUBLISH', 'PERMISSION', 'ROLE', 'CHANNEL_UPDATED']
    .some((marker) => action.includes(marker));
}

function isAiAutomation(row: AdminAuditLogResponse) {
  return row.operatorType === 'AGENT' || row.targetType === 'AGENT_TASK' || row.actionType.startsWith('TASK_');
}

async function loadPage() {
  loading.value = true;
  try {
    page.value = await adminApi.system.auditLogs({
      pageNo: page.value.pageNo,
      pageSize: page.value.pageSize,
      operatorType: filters.operatorType || undefined,
      actionType: filters.actionType || undefined,
      targetType: filters.targetType || undefined,
    });
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value.pageNo = 1;
  void loadPage();
}

function openDetail(row: AdminAuditLogResponse) {
  selected.value = row;
  detailVisible.value = true;
}

function prettyJson(value?: string | null) {
  if (!value) return '无明细';
  try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
}

onMounted(loadPage);
</script>

<template>
  <main class="page-grid">
    <AdminPanel :span="12" title="风险审计视图" subtitle="对当前页日志按失败、敏感操作和 AI 自动化快速聚焦">
      <div class="audit-risk-grid" role="group" aria-label="审计风险筛选">
        <button type="button" :class="{ active: riskScope === 'ALL' }" :aria-pressed="riskScope === 'ALL'" @click="riskScope = 'ALL'"><span>本页全部</span><strong>{{ allRows.length }}</strong><small>当前分页记录</small></button>
        <button type="button" :class="['risk-item', { active: riskScope === 'FAILED', attention: failureCount }]" :aria-pressed="riskScope === 'FAILED'" @click="riskScope = 'FAILED'"><span>执行失败</span><strong>{{ failureCount }}</strong><small>优先核对操作明细</small></button>
        <button type="button" :class="['risk-item', { active: riskScope === 'SENSITIVE', attention: sensitiveCount }]" :aria-pressed="riskScope === 'SENSITIVE'" @click="riskScope = 'SENSITIVE'"><span>敏感操作</span><strong>{{ sensitiveCount }}</strong><small>删除、发布、清理与下载</small></button>
        <button type="button" :class="{ active: riskScope === 'AI' }" :aria-pressed="riskScope === 'AI'" @click="riskScope = 'AI'"><span>AI 自动化</span><strong>{{ aiAutomationCount }}</strong><small>助手及任务执行记录</small></button>
      </div>
    </AdminPanel>

    <AdminPanel :span="12" :title="`审计日志 (${page.total})`" subtitle="点击记录查看操作对象和原始明细">
      <template #actions><button class="secondary-button" :disabled="loading" @click="loadPage"><RefreshCcw :size="16" />刷新</button></template>
      <AdminFilterBar @search="search">
        <select v-model="filters.operatorType"><option value="">全部操作人</option><option value="ADMIN">管理员</option><option value="USER">员工</option><option value="SYSTEM">系统</option><option value="AGENT">AI 助手</option></select>
        <select v-model="filters.actionType"><option value="">全部动作</option><option v-for="item in actionOptions" :key="item[0]" :value="item[0]">{{ item[1] }}</option></select>
        <select v-model="filters.targetType"><option value="">全部对象</option><option v-for="item in targetOptions" :key="item[0]" :value="item[0]">{{ item[1] }}</option></select>
      </AdminFilterBar>
      <AdminTable interactive :columns="columns" :rows="rows" :loading="loading" row-key="auditLogId" @row-click="openDetail">
        <template #cell-occurredAt="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
        <template #cell-actionType="{ row }">{{ row.actionTypeName || auditActionLabel(row.actionType) }}</template>
        <template #cell-target="{ row }"><strong>{{ row.target }}</strong><span v-if="row.targetId">{{ formatId(row.targetId) }}</span></template>
        <template #cell-result="{ row }"><span :class="['status-pill', { 'audit-risk-pill': isFailureResult(row) }]">{{ row.resultName || statusLabel(row.result) }}</span></template>
      </AdminTable>
      <p v-if="riskScope !== 'ALL'" class="audit-scope-note">当前风险视图展示本页 {{ rows.length }} 条记录，翻页后继续按同一规则筛选。</p>
      <AdminPagination :page-no="page.pageNo" :page-size="page.pageSize" :total="page.total" @update:page-no="(value) => { page.pageNo = value; loadPage(); }" @update:page-size="(value) => { page.pageSize = value; page.pageNo = 1; loadPage(); }" />
    </AdminPanel>

    <AdminDialog v-model:visible="detailVisible" title="审计记录详情" width="min(760px, 92vw)">
      <div v-if="selected" class="audit-detail">
        <dl>
          <div><dt>操作人</dt><dd>{{ selected.operatorName || selected.operatorAccount || selected.operatorTypeName }}</dd></div>
          <div><dt>动作</dt><dd>{{ selected.actionTypeName || auditActionLabel(selected.actionType) }}</dd></div>
          <div><dt>对象</dt><dd>{{ selected.targetDisplayName || selected.targetNo || selected.targetTypeName }}</dd></div>
          <div><dt>结果</dt><dd>{{ selected.resultName || statusLabel(selected.result) }}</dd></div>
          <div><dt>发生时间</dt><dd>{{ formatDateTime(selected.occurredAt) }}</dd></div>
          <div><dt>记录编号</dt><dd>{{ formatId(selected.auditLogId) }}</dd></div>
        </dl>
        <h3>操作明细</h3>
        <pre>{{ prettyJson(selected.detailJson) }}</pre>
      </div>
    </AdminDialog>
  </main>
</template>

<style scoped>
.audit-risk-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--space-md); }
.audit-risk-grid button { display: grid; align-content: center; gap: var(--space-sm); min-height: 116px; padding: var(--space-base); border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--panel-strong); color: var(--ink); letter-spacing: 0; text-align: left; }
.audit-risk-grid button:hover, .audit-risk-grid button.active { border-color: var(--green); background: var(--hover-bg); }
.audit-risk-grid button.attention { border-color: var(--error-border); background: var(--error-bg); }
.audit-risk-grid span, .audit-risk-grid small { color: var(--muted); font-size: var(--text-xs); font-weight: 700; }
.audit-risk-grid strong { font-size: 25px; line-height: 1; font-variant-numeric: tabular-nums; }
.audit-risk-grid button.attention strong { color: var(--red-dark); }
.audit-risk-pill { background: var(--error-bg); color: var(--red-dark); }
.audit-scope-note { margin: var(--space-md) 0 0; color: var(--muted); font-size: var(--text-xs); line-height: 1.5; }
.audit-detail { display: grid; gap: var(--space-base); }
.audit-detail dl { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-sm); margin: 0; }
.audit-detail dl div { display: grid; gap: var(--space-xs); padding: var(--space-md); border: 1px solid var(--line); border-radius: var(--radius-sm); }
.audit-detail dt { color: var(--muted); font-size: var(--text-xs); }
.audit-detail dd { margin: 0; font-weight: 700; }
.audit-detail h3 { margin: 0; }
.audit-detail pre { max-height: 320px; overflow: auto; margin: 0; padding: var(--space-base); border-radius: var(--radius-sm); background: var(--table-head-bg); white-space: pre-wrap; word-break: break-word; }
@media (max-width: 820px) { .audit-risk-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 640px) { .audit-risk-grid { grid-template-columns: 1fr; } .audit-risk-grid button { min-height: 96px; } .audit-detail dl { grid-template-columns: 1fr; } }
</style>
