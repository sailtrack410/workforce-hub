<script setup lang="ts">
import { RefreshCcw } from 'lucide-vue-next';
import type { AdminAgentTaskMetricsResponse } from '@/api/types';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminMetricStrip from '@/components/AdminMetricStrip.vue';

defineProps<{
  metrics: AdminAgentTaskMetricsResponse | null;
  loading: boolean;
}>();

defineEmits<{
  refresh: [];
}>();
</script>

<template>
  <AdminPanel :span="12" title="指标概览">
    <template #actions>
      <button class="secondary-button" :disabled="loading" @click="$emit('refresh')">
        <RefreshCcw :size="16" />
        <span>刷新</span>
      </button>
    </template>
    <AdminMetricStrip :cards="[
      { label: '任务总数', value: metrics?.totalTaskCount ?? 0 },
      { label: '待确认', value: metrics?.waitingHumanConfirmTaskCount ?? 0 },
      { label: '失败任务', value: metrics?.failedTaskCount ?? 0 },
      { label: '失败工具调用', value: metrics?.failedToolCallCount ?? 0 },
    ]" />
  </AdminPanel>
</template>
