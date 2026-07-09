<script setup lang="ts">
import { computed } from 'vue';
import type {
  AdminAgentTaskResponse,
  AdminAgentTaskAuditResponse,
  AdminAgentTaskToolCallResponse,
} from '@/api/types';
import { actorTypeLabel, agentContextLabel, auditActionLabel, formatDateTime, formatFullDateTime, formatId, humanizeText, safeBusinessMessage, statusLabel, taskTriggerLabel } from '@/utils/format';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';
import AdminCompactList from '@/components/AdminCompactList.vue';

const props = defineProps<{
  selected: AdminAgentTaskResponse | null;
  toolCalls: AdminAgentTaskToolCallResponse[];
  audits: AdminAgentTaskAuditResponse[];
  detailLoading: boolean;
}>();

const detailRows = computed<DetailRow[]>(() => {
  const t = props.selected;
  if (!t) return [];
	  return [
	    { label: '状态', value: t.statusName || statusLabel(t.status) },
	    { label: '触发方式', value: t.triggerTypeName || taskTriggerLabel(t.triggerType) },
	    { label: '触发来源', value: t.triggeredByName || t.triggeredByTypeName || actorTypeLabel(t.triggeredByType) },
	    { label: '业务场景', value: t.contextDisplayName || t.contextTypeName || agentContextLabel(t.contextType) },
    { label: '开始时间', value: formatFullDateTime(t.startedAt) },
    { label: '结束时间', value: formatFullDateTime(t.finishedAt) },
  ];
});

const executionTrace = computed(() => props.selected?.executionTraceJson ? '执行轨迹已生成' : '');

function toolDisplayName(value?: string | null) {
  const text = humanizeText(value).trim();
  if (!text) return '未命名工具';
  if (/^[A-Z][A-Z0-9_]{2,}$/.test(text)) return '业务工具';
  return text;
}
</script>

<template>
  <AdminPanel :span="5" title="任务详情">
    <AdminDetailList :rows="detailRows" :error="selected?.errorMessage ? safeBusinessMessage(selected.errorMessage) : ''" />

    <h3>工具调用</h3>
    <AdminCompactList :loading="detailLoading" :empty="toolCalls.length === 0">
      <article v-for="call in toolCalls" :key="formatId(call.logId)">
        <strong>{{ toolDisplayName(call.toolName) }}</strong>
        <span>{{ call.resultStatusName || statusLabel(call.resultStatus) }} · {{ call.approvalStatusName || statusLabel(call.approvalStatus) }}</span>
      </article>
    </AdminCompactList>

    <h3>执行轨迹</h3>
    <pre v-if="executionTrace" class="trace-block">{{ executionTrace }}</pre>
    <div v-else class="empty-state">无执行轨迹</div>

    <h3>审计轨迹</h3>
    <AdminCompactList :loading="detailLoading" :empty="audits.length === 0">
      <article v-for="audit in audits" :key="formatId(audit.auditId)">
        <strong>{{ audit.actionTypeName || auditActionLabel(audit.actionType) }}</strong>
        <span>{{ audit.targetTypeName || '任务对象' }} · {{ formatDateTime(audit.occurredAt) }}</span>
        <span>{{ audit.decisionSummary ? safeBusinessMessage(audit.decisionSummary, '已记录') : '-' }}</span>
      </article>
    </AdminCompactList>
  </AdminPanel>
</template>

<style scoped>
.trace-block {
  max-height: 260px;
  overflow: auto;
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  color: var(--ink);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
}
</style>
