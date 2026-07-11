<script setup lang="ts">
import { computed } from 'vue';
import { Plus, RefreshCcw, Search, Settings, Trash2 } from 'lucide-vue-next';
import type { LlmModelResponse, RagflowGlobalSettingResponse, RagflowSpaceResponse } from '@/api/types';
import { formatDateTime, statusLabel } from '@/utils/format';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';

const props = defineProps<{
  columns: TableColumn[];
  globalSetting: RagflowGlobalSettingResponse | null;
  items: RagflowSpaceResponse[];
  keyword: string;
  llmModels: LlmModelResponse[];
  loading: boolean;
}>();

const emit = defineEmits<{
  'update:keyword': [value: string];
  create: [];
  'delete-space': [value: RagflowSpaceResponse];
  load: [];
  'open-global-settings': [];
  'open-settings': [value: RagflowSpaceResponse];
  'open-space': [value: RagflowSpaceResponse];
}>();

const keywordValue = computed({
  get: () => props.keyword,
  set: (value: string) => emit('update:keyword', value),
});

function modelLabel(modelId?: string | number | null, fallback?: string | null) {
  if (modelId != null && modelId !== '') {
    return props.llmModels.find((item) => String(item.modelId) === String(modelId))?.displayName || '已配置';
  }
  return fallback ? '已配置' : '-';
}

function visibilityLabel(value?: string | null) {
  const labels: Record<string, string> = {
    PRIVATE: '私有',
    ROLE: '按角色',
    DEPARTMENT: '按部门',
    POSITION: '按岗位',
    PUBLIC: '全员可查阅',
  };
  return value ? labels[value] ?? '未知范围' : '-';
}
</script>

<template>
  <AdminPanel :span="12" title="知识库模型设置">
    <template #actions>
      <button class="secondary-button" type="button" @click="emit('open-global-settings')">
        <Settings :size="16" />
        <span>设置</span>
      </button>
    </template>
    <div class="model-setting-grid">
      <div>
        <span>OCR</span>
        <strong>{{ modelLabel(globalSetting?.ocrModelId, globalSetting?.ocrModel) }}</strong>
        <small>{{ globalSetting?.ocrModelId ? '基座模型' : '未选择' }}</small>
      </div>
      <div>
        <span>嵌入</span>
        <strong>{{ modelLabel(globalSetting?.embeddingModelId, globalSetting?.embeddingModel) }}</strong>
        <small>{{ globalSetting?.embeddingDimension ? `维度 ${globalSetting.embeddingDimension}` : '默认维度' }}</small>
      </div>
      <div>
        <span>重排</span>
        <strong>{{ globalSetting?.rerankEnabled ? modelLabel(globalSetting?.rerankModelId, globalSetting?.rerankModel) : '未启用' }}</strong>
        <small>{{ globalSetting?.rerankEnabled ? '已启用' : '-' }}</small>
      </div>
      <div>
        <span>检索</span>
        <strong>召回 {{ globalSetting?.retrievalTopK ?? 5 }} 条</strong>
        <small>{{ globalSetting?.chunkSize ?? 800 }} / {{ globalSetting?.chunkOverlap ?? 100 }}</small>
      </div>
    </div>
  </AdminPanel>

  <AdminPanel :span="12" title="知识库">
    <template #actions>
      <form class="ragflow-search" @submit.prevent="emit('load')">
        <input v-model="keywordValue" placeholder="搜索名称" />
        <button class="secondary-button" title="搜索" type="submit">
          <Search :size="16" />
        </button>
      </form>
      <button class="secondary-button" title="刷新" type="button" :disabled="loading" @click="emit('load')">
        <RefreshCcw :size="16" />
        <span>刷新</span>
      </button>
      <button class="primary-button" type="button" @click="emit('create')">
        <Plus :size="16" />
        <span>新建知识库</span>
      </button>
    </template>

    <AdminTable interactive :columns="columns" :rows="items" :loading="loading" row-key="spaceId" @row-click="emit('open-space', $event)">
      <template #cell-spaceName="{ row }">
        <strong>{{ row.spaceName }}</strong>
      </template>
      <template #cell-visibility="{ row }">
        {{ visibilityLabel(row.visibility) }}
      </template>
      <template #cell-status="{ row }">
        <span class="status-pill">{{ row.statusName || statusLabel(row.status) }}</span>
      </template>
      <template #cell-updatedAt="{ row }">
        {{ formatDateTime(row.updatedAt) }}
      </template>
      <template #cell-_actions="{ row }">
        <div class="row-actions">
          <button class="secondary-button action-btn" type="button" @click.stop="emit('open-space', row)">进入</button>
          <button class="secondary-button action-btn" type="button" @click.stop="emit('open-settings', row)">
            <Settings :size="14" />
            <span>设置</span>
          </button>
          <button class="danger-button action-btn" title="删除知识库" type="button" @click.stop="emit('delete-space', row)">
            <Trash2 :size="14" />
            <span>删除</span>
          </button>
        </div>
      </template>
    </AdminTable>
  </AdminPanel>
</template>
