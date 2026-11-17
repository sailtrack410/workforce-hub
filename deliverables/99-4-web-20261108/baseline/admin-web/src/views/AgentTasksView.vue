<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { AlertTriangle, Clock3, ShieldCheck, Wrench } from 'lucide-vue-next';
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
import AdminPanel from '@/components/AdminPanel.vue';
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
const attentionCount = computed(() => Number(metrics.value?.waitingHumanConfirmTaskCount ?? 0) + Number(metrics.value?.failedTaskCount ?? 0));
const taskHealthLabel = computed(() => {
  if (Number(metrics.value?.failedTaskCount ?? 0) > 0) return '存在失败任务';
  if (Number(metrics.value?.waitingHumanConfirmTaskCount ?? 0) > 0) return '等待人工确认';
  return '自动化运行正常';
});

function getSelected() {
  return store.selectedTaskId ? items.value.find((i) => i.taskId === store.selectedTaskId) ?? null : null;
}

function focusStatus(status: string) {
  store.filters.status = status;
  store.pagination.pageNo = 1;
  void loadPage();
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
    <AdminPanel :span="12" title="任务处置台" subtitle="先处理人工确认与失败任务，再检查运行中队列">
      <div class="task-command-grid">
        <button
          class="task-priority-card"
          type="button"
          data-priority="P0"
          @click="focusStatus('WAITING_HUMAN_CONFIRM')"
        >
          <span class="task-card-label"><AlertTriangle :size="17" />人工确认队列</span>
          <strong>{{ metrics?.waitingHumanConfirmTaskCount ?? 0 }}</strong>
          <small>{{ attentionCount ? `共 ${attentionCount} 项需优先处置` : taskHealthLabel }}</small>
          <span class="task-card-link">进入确认队列</span>
        </button>
        <div class="task-status-grid">
          <button type="button" @click="focusStatus('FAILED')">
            <span><ShieldCheck :size="16" />失败任务</span>
            <strong>{{ metrics?.failedTaskCount ?? 0 }}</strong>
            <small>需核对执行轨迹</small>
          </button>
          <article>
            <span><Wrench :size="16" />工具调用失败</span>
            <strong>{{ metrics?.failedToolCallCount ?? 0 }}</strong>
            <small>检查外部能力与参数</small>
          </article>
          <button type="button" @click="focusStatus('RUNNING')">
            <span><Clock3 :size="16" />运行中</span>
            <strong>{{ metrics?.runningTaskCount ?? 0 }}</strong>
            <small>持续观察超时队列</small>
          </button>
        </div>
      </div>
    </AdminPanel>
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

<style scoped>
.task-command-grid {
  display: grid;
  grid-template-columns: minmax(260px, 1.15fr) minmax(0, 2fr);
  gap: var(--space-md);
}

.task-priority-card,
.task-status-grid > * {
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  color: var(--ink);
  letter-spacing: 0;
  text-align: left;
}

.task-priority-card {
  position: relative;
  display: grid;
  align-content: center;
  gap: var(--space-sm);
  min-height: 150px;
  padding: var(--space-lg);
  border-color: var(--error-border);
  background: var(--error-bg);
}

.task-priority-card::after {
  content: attr(data-priority);
  position: absolute;
  top: -8px;
  right: var(--space-base);
  padding: 4px 9px;
  border: 1px solid var(--error-border);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  color: var(--red-dark);
  font-size: var(--text-xs);
  font-weight: 800;
  letter-spacing: 0;
}

.task-priority-card:hover,
.task-status-grid button:hover {
  border-color: var(--green);
  background: var(--hover-bg);
}

.task-card-label,
.task-status-grid span {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.task-priority-card strong {
  color: var(--red-dark);
  font-size: 34px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.task-priority-card small,
.task-status-grid small {
  color: var(--muted);
  line-height: 1.45;
}

.task-card-link {
  color: var(--red-dark);
  font-size: var(--text-sm);
  font-weight: 800;
}

.task-status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-md);
}

.task-status-grid > * {
  display: grid;
  align-content: center;
  gap: var(--space-sm);
  min-height: 150px;
  padding: var(--space-base);
}

.task-status-grid strong {
  font-size: 26px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 980px) {
  .task-command-grid { grid-template-columns: 1fr; }
}

@media (max-width: 640px) {
  .task-status-grid { grid-template-columns: 1fr; }
  .task-priority-card,
  .task-status-grid > * { min-height: 112px; }
}
</style>
