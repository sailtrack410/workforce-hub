<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ArrowRight, Bot, FileArchive, Tag, TriangleAlert } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type {
  AdminAgentTaskMetricsResponse,
  LeaveTypeResponse,
  StorageSummaryResponse,
} from '@/api/types';
import { errorMessage, formatBytes, formatMinutes } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminMetricStrip from '@/components/AdminMetricStrip.vue';
import AdminSkeleton from '@/components/AdminSkeleton.vue';

const toast = useToast();
const loading = ref(true);
const taskMetrics = ref<AdminAgentTaskMetricsResponse | null>(null);
const leaveTypes = ref<LeaveTypeResponse[]>([]);
const storage = ref<StorageSummaryResponse | null>(null);

async function load() {
  loading.value = true;
  try {
    const [tm, lt, st] = await Promise.all([
      adminApi.agentTasks.metrics(),
      adminApi.leave.types(),
      adminApi.files.storageSummary(),
    ]);
    taskMetrics.value = tm;
    leaveTypes.value = lt;
    storage.value = st;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

onMounted(load);

const taskCards = computed(() => {
  const v = taskMetrics.value;
  return [
    { label: '任务总数', value: v?.totalTaskCount ?? 0 },
    { label: '运行中', value: v?.runningTaskCount ?? 0 },
    { label: '待确认', value: v?.waitingHumanConfirmTaskCount ?? 0 },
    { label: '失败', value: v?.failedTaskCount ?? 0 },
  ];
});

const storageCards = computed(() => {
  const v = storage.value;
  return [
    { label: '已用空间', value: formatBytes(v?.orgPoolUsedBytes ?? 0) },
    { label: '配额', value: formatBytes(v?.orgPoolQuotaBytes ?? 0) },
    { label: '文件数', value: v?.fileObjectCount ?? 0 },
    { label: '回收站', value: v?.recycleBinCount ?? 0 },
  ];
});

const exceptionActions = computed(() => [
  {
    label: '待人工确认',
    value: taskMetrics.value?.waitingHumanConfirmTaskCount ?? 0,
    hint: '进入任务列表完成确认',
    to: { path: '/agent-tasks', query: { status: 'WAITING_HUMAN_CONFIRM' } },
    icon: Bot,
  },
  {
    label: '执行失败',
    value: taskMetrics.value?.failedTaskCount ?? 0,
    hint: '定位失败任务与原因',
    to: { path: '/agent-tasks', query: { status: 'FAILED' } },
    icon: TriangleAlert,
  },
  {
    label: '回收站文件',
    value: storage.value?.recycleBinCount ?? 0,
    hint: '检查存储并执行清理',
    to: '/files',
    icon: FileArchive,
  },
]);
</script>

<template>
  <AdminSkeleton v-if="loading" :lines="4" />
  <main v-else class="page-grid">
    <AdminPanel :span="12" title="待处理异常" subtitle="从异常指标直接进入对应处置页面">
      <div class="action-grid">
        <RouterLink v-for="action in exceptionActions" :key="action.label" :to="action.to" class="action-card">
          <component :is="action.icon" :size="20" />
          <span>{{ action.label }}</span>
          <strong>{{ action.value }}</strong>
          <small>{{ action.hint }}</small>
          <ArrowRight class="action-arrow" :size="18" />
        </RouterLink>
      </div>
    </AdminPanel>

    <AdminPanel :span="12" title="AI 任务概览">
      <AdminMetricStrip :cards="taskCards" />
    </AdminPanel>

    <AdminPanel :span="6" title="假种数量">
      <RouterLink to="/leave-types" class="summary-link">
        <Tag :size="20" />
        <strong class="leave-count">{{ leaveTypes.length }}</strong>
        <span>管理假种</span>
        <ArrowRight :size="18" />
      </RouterLink>
    </AdminPanel>

    <AdminPanel :span="6" title="存储概览">
      <AdminMetricStrip :cards="storageCards" />
      <RouterLink to="/files" class="panel-link">进入文件空间 <ArrowRight :size="16" /></RouterLink>
    </AdminPanel>
  </main>
</template>

<style scoped>
.leave-count {
  display: inline-flex;
  align-items: center;
  min-height: 56px;
  color: var(--green-dark);
  font-size: 28px;
  line-height: 1;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-base);
}

.action-card {
  position: relative;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: var(--space-sm) var(--space-md);
  min-height: 112px;
  padding: var(--space-base);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  color: var(--ink);
  text-decoration: none;
  transition: border-color 200ms ease, box-shadow 200ms ease;
}

.action-card:hover { border-color: var(--green-light); box-shadow: var(--shadow); }
.action-card:focus-visible { outline: none; box-shadow: 0 0 0 4px var(--focus-ring); }
.action-card > svg:first-child { color: var(--green-dark); }
.action-card span { font-size: var(--text-sm); font-weight: 700; }
.action-card strong { font-size: var(--text-xl); }
.action-card small { grid-column: 2 / 4; color: var(--muted); font-size: var(--text-xs); }
.action-arrow { color: var(--muted); }

.summary-link {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  gap: var(--space-md);
  min-height: 84px;
  padding: 0 var(--space-base);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  color: var(--ink);
  text-decoration: none;
}

.summary-link > svg:first-child { color: var(--green-dark); }
.summary-link span,
.panel-link { color: var(--muted); font-size: var(--text-sm); font-weight: 700; }
.summary-link:focus-visible,
.panel-link:focus-visible { outline: none; box-shadow: 0 0 0 4px var(--focus-ring); }

.panel-link {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-xs);
  min-height: 44px;
  margin-top: var(--space-sm);
  color: var(--green-dark);
  text-decoration: none;
}

@media (max-width: 820px) {
  .action-grid { grid-template-columns: 1fr; }
}
</style>
