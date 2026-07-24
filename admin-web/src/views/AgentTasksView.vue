<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { adminApi } from '@/api/admin';
import type {
  AdminAgentTaskAuditResponse,
  AdminAgentTaskFailureSummaryResponse,
  AdminAgentTaskMetricsResponse,
  AdminAgentTaskResponse,
  AdminAgentTaskTimeoutRunResponse,
  AdminAgentTaskToolCallResponse,
  PageResult,
} from '@/api/types';
import { errorMessage } from '@/utils/format';
import { useAgentTasksStore } from '@/stores/agentTasks';
import { useToast } from '@/composables/useToast';
import TaskMetrics from './agent-tasks/TaskMetrics.vue';
import TaskList from './agent-tasks/TaskList.vue';
import TaskDetail from './agent-tasks/TaskDetail.vue';
import TaskScanPanel from './agent-tasks/TaskScanPanel.vue';

const store = useAgentTasksStore();
const toast = useToast();
const route = useRoute();

const loading = ref(false);
const detailLoading = ref(false);
const items = ref<AdminAgentTaskResponse[]>([]);
const metrics = ref<AdminAgentTaskMetricsResponse | null>(null);
const failures = ref<AdminAgentTaskFailureSummaryResponse[]>([]);
const toolCalls = ref<AdminAgentTaskToolCallResponse[]>([]);
const audits = ref<AdminAgentTaskAuditResponse[]>([]);
const timeoutRuns = ref<PageResult<AdminAgentTaskTimeoutRunResponse>>({ pageNo: 1, pageSize: 8, total: 0, list: [] });
const scanPanel = ref<InstanceType<typeof TaskScanPanel> | null>(null);

function getSelected() {
  return store.selectedTaskId ? items.value.find((i) => i.taskId === store.selectedTaskId) ?? null : null;
}

onMounted(() => {
  const status = typeof route.query.status === 'string' ? route.query.status : '';
  if (['PENDING', 'RUNNING', 'WAITING_HUMAN_CONFIRM', 'SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status)) {
    store.filters.status = status;
    store.pagination.pageNo = 1;
  }
  void loadPage();
});

  // 并行加载指标、失败汇总、任务列表、超时扫描记录
async function loadPage() {
  loading.value = true;
  try {
    const [metricsResult, failuresResult, tasksResult, timeoutRunsResult] = await Promise.all([
      adminApi.agentTasks.metrics(),
      adminApi.agentTasks.failureSummary(),
      adminApi.agentTasks.list({
        pageNo: store.pagination.pageNo,
        pageSize: store.pagination.pageSize,
        status: store.filters.status,
        contextType: store.filters.contextType,
        triggeredByType: store.filters.triggeredByType,
      }),
      adminApi.agentTasks.timeoutRuns({ pageNo: 1, pageSize: 8 }),
    ]);
    metrics.value = metricsResult;
    failures.value = failuresResult;
    items.value = tasksResult.list;
    store.pagination.total = tasksResult.total;
    timeoutRuns.value = timeoutRunsResult;
    // 保持之前选中的任务，如果还在列表中的话
    if (store.selectedTaskId && tasksResult.list.some((t) => t.taskId === store.selectedTaskId)) {
      await selectTask(tasksResult.list.find((t) => t.taskId === store.selectedTaskId)!);
    } else if (tasksResult.list.length > 0) {
      await selectTask(tasksResult.list[0]);
    }
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

async function selectTask(task: AdminAgentTaskResponse) {
  store.selectedTaskId = task.taskId;
  detailLoading.value = true;
  try {
    const [detail, taskToolCalls, taskAudits] = await Promise.all([
      adminApi.agentTasks.detail(task.taskId),
      adminApi.agentTasks.toolCalls(task.taskId),
      adminApi.agentTasks.audits(task.taskId),
    ]);
    const idx = items.value.findIndex((i) => i.taskId === task.taskId);
    if (idx >= 0) items.value[idx] = detail;
    toolCalls.value = taskToolCalls;
    audits.value = taskAudits;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    detailLoading.value = false;
  }
}

async function triggerScan(batchSize: number) {
  loading.value = true;
  try {
    const result = await adminApi.agentTasks.timeoutScan(batchSize);
    scanPanel.value?.setScanResult(`扫描 ${result.scannedCount}，取消 ${result.cancelledCount}，跳过 ${result.skippedCount}`);
    await loadPage();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="page-grid">
    <TaskMetrics :metrics="metrics" :loading="loading" @refresh="loadPage" />
    <TaskList :items="items" :loading="loading" @select="selectTask" @search="loadPage" />
    <TaskDetail
      :selected="getSelected()"
      :tool-calls="toolCalls"
      :audits="audits"
      :detail-loading="detailLoading"
    />
    <TaskScanPanel
      ref="scanPanel"
      :failures="failures"
      :timeout-runs="timeoutRuns"
      :loading="loading"
      @scan="triggerScan"
    />
  </main>
</template>
