<script setup lang="ts">
import { computed } from 'vue';
import type {
  AdminAgentTaskResponse,
  AdminAgentTaskAuditResponse,
  AdminAgentTaskToolCallResponse,
} from '@/api/types';
import { formatDateTime, formatFullDateTime, formatId, shortId, statusLabel } from '@/utils/format';
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
    { label: '任务 ID', value: formatId(t.taskId) },
    { label: '状态', value: statusLabel(t.status) },
    { label: '触发来源', value: t.triggerType || '-' },
    { label: '上下文', value: `${t.contextType || '-'} / ${formatId(t.contextId)}` },
    { label: '开始时间', value: formatFullDateTime(t.startedAt) },
    { label: '结束时间', value: formatFullDateTime(t.finishedAt) },
  ];
});

const executionTrace = computed(() => {
  const value = props.selected?.executionTraceJson;
  if (!value) return '';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
});
</script>

<template>
  <AdminPanel :span="5" title="任务详情">
    <AdminDetailList :rows="detailRows" :error="selected?.errorMessage ?? ''" />

    <h3>工具调用</h3>
    <AdminCompactList :loading="detailLoading" :empty="toolCalls.length === 0">
      <article v-for="call in toolCalls" :key="formatId(call.logId)">
        <strong>{{ call.toolName || call.toolCode || shortId(call.toolId) }}</strong>
        <span>{{ statusLabel(call.resultStatus) }} · {{ statusLabel(call.approvalStatus) }}</span>
      </article>
    </AdminCompactList>

    <h3>执行轨迹</h3>
    <pre v-if="executionTrace" class="trace-block">{{ executionTrace }}</pre>
    <div v-else class="empty-state">无执行轨迹</div>

    <h3>审计轨迹</h3>
    <AdminCompactList :loading="detailLoading" :empty="audits.length === 0">
      <article v-for="audit in audits" :key="formatId(audit.auditId)">
        <strong>{{ audit.actionType }}</strong>
        <span>{{ formatDateTime(audit.occurredAt) }} · {{ audit.decisionSummary || '-' }}</span>
      </article>
    </AdminCompactList>
  </AdminPanel>
</template>

<style scoped>
.trace-block {
  max-height: 260px;
  overflow: auto;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--panel-strong);
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
}
</style>
