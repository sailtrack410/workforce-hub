<script setup lang="ts">
import { computed } from 'vue';
import { Save } from 'lucide-vue-next';
import type { RagflowAclResponse, RagflowSpaceResponse } from '@/api/types';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';

const props = defineProps<{
  aclColumns: TableColumn[];
  aclForm: {
    subjectType: string;
    subjectId: string;
    permCode: string;
  };
  aclItems: RagflowAclResponse[];
  aclLoading: boolean;
  aclSubjectName: (row: RagflowAclResponse) => string;
  aclSubjectOptions: { id: string | number; name: string }[];
  form: {
    spaceName: string;
    spaceCode: string;
    description: string;
    visibility: string;
    status: string;
    embeddingModelId: string;
    embeddingDimension: string;
    rerankEnabled: boolean;
    rerankModelId: string;
    chunkSize: string;
    chunkOverlap: string;
    retrievalTopK: string;
    scoreThreshold: string;
  };
  saving: boolean;
  settingsSpace: RagflowSpaceResponse | null;
  visible: boolean;
}>();

const emit = defineEmits<{
  'add-acl': [];
  confirm: [];
  'remove-acl': [value: RagflowAclResponse];
  'save-acl': [];
  'update:visible': [value: boolean];
}>();

const opened = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});

</script>

<template>
  <AdminConfirmDialog v-model:visible="opened" title="知识库设置" message="" confirm-label="保存" :close-on-confirm="false" @confirm="emit('confirm')">
    <div class="settings-dialog">
      <section class="dialog-form">
        <label>
          <span>名称</span>
          <input v-model="form.spaceName" />
        </label>
        <label class="full-field">
          <span>描述</span>
          <textarea v-model="form.description" rows="3" />
        </label>
        <label>
          <span>查阅范围</span>
          <select v-model="form.visibility">
            <option value="PRIVATE">私有</option>
            <option value="ROLE">按角色</option>
            <option value="DEPARTMENT">按部门</option>
            <option value="POSITION">按岗位</option>
            <option value="PUBLIC">全员可查阅</option>
          </select>
        </label>
        <label>
          <span>状态</span>
          <select v-model="form.status">
            <option value="ACTIVE">启用</option>
            <option value="INACTIVE">停用</option>
          </select>
        </label>
      </section>

      <section class="dialog-section">
        <div class="section-head">
          <h3>查阅权限</h3>
          <button class="secondary-button" :disabled="!settingsSpace || saving" @click.stop="emit('save-acl')">
            <Save :size="16" />
            <span>保存权限</span>
          </button>
        </div>
        <form class="acl-form" @submit.prevent="emit('add-acl')">
          <select v-model="aclForm.subjectType">
            <option value="ROLE">角色</option>
            <option value="DEPARTMENT">部门</option>
            <option value="POSITION">岗位</option>
          </select>
          <select v-model="aclForm.subjectId">
            <option value="">请选择对象</option>
            <option v-for="item in aclSubjectOptions" :key="String(item.id)" :value="String(item.id)">
              {{ item.name }}
            </option>
          </select>
          <select v-model="aclForm.permCode">
            <option value="read">可查阅</option>
            <option value="manage">可管理</option>
          </select>
          <button class="secondary-button" type="submit">添加</button>
        </form>
        <AdminTable :columns="aclColumns" :rows="aclItems" :loading="aclLoading" row-key="aclId">
          <template #cell-subjectType="{ row }">
            {{ row.subjectType === 'ROLE' ? '角色' : row.subjectType === 'DEPARTMENT' ? '部门' : '岗位' }}
          </template>
          <template #cell-subjectId="{ row }">
            {{ aclSubjectName(row) }}
          </template>
          <template #cell-permCode="{ row }">
            {{ row.permCode === 'manage' ? '可管理' : '可查阅' }}
          </template>
          <template #cell-_actions="{ row }">
            <button class="danger-button action-btn" @click.stop="emit('remove-acl', row)">移除</button>
          </template>
        </AdminTable>
      </section>
    </div>
  </AdminConfirmDialog>
</template>
