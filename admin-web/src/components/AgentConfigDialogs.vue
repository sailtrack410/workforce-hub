<script setup lang="ts">
import { computed } from 'vue';
import type { AgentResponse } from '@/api/types';
import { humanizeText } from '@/utils/format';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const props = defineProps<{
  createVisible: boolean;
  editVisible: boolean;
  disableVisible: boolean;
  enableVisible: boolean;
  deleteVisible: boolean;
  agentOptions: { value: string; label: string }[];
  createForm: { agentCode: string; agentName: string };
  editForm: { agentName: string };
  disableTarget: AgentResponse | null;
  enableTarget: AgentResponse | null;
  deleteTarget: AgentResponse | null;
}>();

const emit = defineEmits<{
  'update:createVisible': [value: boolean];
  'update:editVisible': [value: boolean];
  'update:disableVisible': [value: boolean];
  'update:enableVisible': [value: boolean];
  'update:deleteVisible': [value: boolean];
  create: [];
  edit: [];
  disable: [];
  enable: [];
  delete: [];
}>();

const disableMessage = computed(() =>
  props.disableTarget ? `停用后员工端将不可检索和调用 ${humanizeText(props.disableTarget.agentName) || 'AI 助手'}` : '',
);

const enableMessage = computed(() =>
  props.enableTarget ? `启用后员工端可按权限检索和调用 ${humanizeText(props.enableTarget.agentName) || 'AI 助手'}` : '',
);

const deleteMessage = computed(() =>
  props.deleteTarget
    ? `删除后员工端不再可见，已有会话无法继续提问，${humanizeText(props.deleteTarget.agentName) || 'AI 助手'} 的历史配置和任务记录仍保留。`
    : '',
);

function selectAgentCode() {
  props.createForm.agentName = props.agentOptions.find((item) => item.value === props.createForm.agentCode)?.label ?? props.createForm.agentName;
}
</script>

<template>
  <AdminConfirmDialog
    :visible="createVisible"
    title="新建 AI 助手"
    message=""
    confirm-label="创建"
    @update:visible="emit('update:createVisible', $event)"
    @confirm="emit('create')"
  >
    <div class="create-dialog-form">
      <label>
        <span>助手用途</span>
        <select v-model="createForm.agentCode" autofocus @change="selectAgentCode">
          <option v-for="item in agentOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
      </label>
      <label>
        <span>助手名称</span>
        <input v-model="createForm.agentName" placeholder="如 假期助手" />
      </label>
    </div>
  </AdminConfirmDialog>

  <AdminConfirmDialog
    :visible="editVisible"
    title="编辑 AI 助手"
    message=""
    confirm-label="保存"
    @update:visible="emit('update:editVisible', $event)"
    @confirm="emit('edit')"
  >
    <div class="create-dialog-form">
      <label>
        <span>助手名称</span>
        <input v-model="editForm.agentName" placeholder="如 假期助手" autofocus />
      </label>
    </div>
  </AdminConfirmDialog>

  <AdminConfirmDialog
    :visible="disableVisible"
    title="停用 AI 助手"
    :message="disableMessage"
    confirm-label="停用"
    :danger="true"
    @update:visible="emit('update:disableVisible', $event)"
    @confirm="emit('disable')"
  />

  <AdminConfirmDialog
    :visible="enableVisible"
    title="启用 AI 助手"
    :message="enableMessage"
    confirm-label="启用"
    @update:visible="emit('update:enableVisible', $event)"
    @confirm="emit('enable')"
  />

  <AdminConfirmDialog
    :visible="deleteVisible"
    title="删除 AI 助手"
    :message="deleteMessage"
    confirm-label="删除"
    :danger="true"
    @update:visible="emit('update:deleteVisible', $event)"
    @confirm="emit('delete')"
  />
</template>

<style scoped>
.create-dialog-form {
  display: grid;
  gap: var(--space-lg);
  margin-top: var(--space-md);
}

.create-dialog-form label {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}
</style>
