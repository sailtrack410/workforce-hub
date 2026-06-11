<script setup lang="ts">
import { reactive, ref } from 'vue';
import { Play } from 'lucide-vue-next';
import type {
  AdminAgentTaskFailureSummaryResponse,
  AdminAgentTaskTimeoutRunResponse,
  PageResult,
} from '@/api/types';
import { formatDateTime, statusLabel } from '@/utils/format';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminCompactList from '@/components/AdminCompactList.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

defineProps<{
  failures: AdminAgentTaskFailureSummaryResponse[];
  timeoutRuns: PageResult<AdminAgentTaskTimeoutRunResponse>;
  loading: boolean;
}>();

const emit = defineEmits<{
  scan: [batchSize: number];
}>();

const scanForm = reactive({ batchSize: 100 });
const scanResult = ref('');
const confirmVisible = ref(false);

function onConfirm() {
  scanResult.value = '';
  emit('scan', Number(scanForm.batchSize) || 100);
}

defineExpose({ setScanResult: (msg: string) => { scanResult.value = msg; } });
</script>

<template>
  <AdminPanel :span="6" title="失败聚合">
    <AdminCompactList :empty="failures.length === 0">
      <article v-for="failure in failures" :key="failure.errorMessage || 'empty'">
        <strong>{{ failure.errorMessage || '-' }}</strong>
        <span>任务 {{ failure.taskCount }} · 工具调用 {{ failure.toolCallCount }} · {{ formatDateTime(failure.lastOccurredAt) }}</span>
      </article>
    </AdminCompactList>
  </AdminPanel>

  <AdminPanel :span="6" title="超时扫描">
    <form class="inline-form" @submit.prevent="confirmVisible = true">
      <input v-model.number="scanForm.batchSize" type="number" min="1" max="500" />
      <button class="danger-button" :disabled="loading" type="submit">
        <Play :size="16" />
        <span>执行扫描</span>
      </button>
    </form>
    <p v-if="scanResult" class="inline-success">{{ scanResult }}</p>
    <AdminCompactList :empty="timeoutRuns.list.length === 0">
      <article v-for="run in timeoutRuns.list" :key="run.jobRunId">
        <strong>{{ run.jobRunId }}</strong>
        <span>{{ statusLabel(run.status) }} · 扫描 {{ run.scannedCount ?? 0 }} · {{ formatDateTime(run.startedAt) }}</span>
      </article>
    </AdminCompactList>
  </AdminPanel>

  <AdminConfirmDialog
    v-model:visible="confirmVisible"
    title="确认执行超时扫描"
    message="确认执行 Agent 超时扫描？"
    confirm-label="执行"
    :danger="true"
    @confirm="onConfirm"
  />
</template>
