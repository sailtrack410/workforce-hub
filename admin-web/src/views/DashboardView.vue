<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
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
</script>

<template>
  <AdminSkeleton v-if="loading" :lines="4" />
  <template v-else>
    <AdminPanel :span="12" title="Agent 任务概览">
      <AdminMetricStrip :cards="taskCards" />
    </AdminPanel>

    <AdminPanel :span="6" title="假种数量">
      <strong class="leave-count">{{ leaveTypes.length }}</strong>
    </AdminPanel>

    <AdminPanel :span="6" title="存储概览">
      <AdminMetricStrip :cards="storageCards" />
    </AdminPanel>
  </template>
</template>

<style scoped>
.leave-count {
  font-size: 28px;
}
</style>
