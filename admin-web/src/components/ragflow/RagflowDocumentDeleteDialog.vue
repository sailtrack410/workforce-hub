<script setup lang="ts">
import { computed } from 'vue';
import type { RagflowDocumentResponse } from '@/api/types';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const props = defineProps<{
  target: RagflowDocumentResponse | null;
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
    title="删除文件"
    :message="`删除文件「${target?.title || ''}」？`"
    confirm-label="删除"
    :danger="true"
    @confirm="emit('confirm')"
    @cancel="emit('cancel')"
  />
</template>
