<script setup lang="ts">
import { computed } from 'vue';
import type { RagflowSpaceResponse } from '@/api/types';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const props = defineProps<{
  target: RagflowSpaceResponse | null;
  visible: boolean;
}>();

const emit = defineEmits<{
  cancel: [];
  confirm: [];
  'update:visible': [value: boolean];
}>();

const opened = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});
</script>

<template>
  <AdminConfirmDialog
    v-model:visible="opened"
    title="删除知识库"
    confirm-label="删除"
    :danger="true"
    @confirm="emit('confirm')"
    @cancel="emit('cancel')"
  >
    <div class="space-delete-message">
      <p>删除知识库「{{ target?.spaceName || '' }}」？</p>
      <p>
        将停用该知识库下的文件、切片和向量。已关联它的 AI 助手会在列表中显示红色知识库异常，需要重新配置。
      </p>
    </div>
  </AdminConfirmDialog>
</template>

<style scoped>
.space-delete-message {
  display: grid;
  gap: var(--space-sm);
  color: var(--muted);
  font-size: var(--text-sm);
  line-height: 1.6;
}

.space-delete-message p {
  margin: 0;
}
</style>
