<script setup lang="ts">
import { computed } from 'vue';
import type { LlmModelResponse } from '@/api/types';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const props = defineProps<{
  embeddingModelOptions: LlmModelResponse[];
  form: {
    spaceName: string;
    spaceCode: string;
    description: string;
    visibility: string;
    embeddingModelId: string;
    rerankEnabled: boolean;
    rerankModelId: string;
    chunkSize: string;
    chunkOverlap: string;
    retrievalTopK: string;
    scoreThreshold: string;
  };
  rerankModelOptions: LlmModelResponse[];
  scoreThresholdOptions: { value: string; label: string }[];
  visible: boolean;
}>();

const emit = defineEmits<{
  confirm: [];
  'update:visible': [value: boolean];
}>();

const opened = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
});
</script>

<template>
  <AdminConfirmDialog v-model:visible="opened" title="新建知识库" message="" confirm-label="创建" :close-on-confirm="false" @confirm="emit('confirm')">
    <div class="ragflow-create-dialog">
      <section class="ragflow-create-panel">
        <h3>基础信息</h3>
        <div class="ragflow-create-fields">
          <label>
            <span>名称</span>
            <input v-model="form.spaceName" placeholder="如 公司制度知识库" autofocus />
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
          <label class="full-field">
            <span>描述</span>
            <textarea v-model="form.description" rows="7" />
          </label>
        </div>
      </section>

      <section class="ragflow-create-panel">
        <h3>参数配置</h3>
        <div class="ragflow-create-fields">
          <label class="full-field">
            <span>嵌入模型</span>
            <select v-model="form.embeddingModelId">
              <option value="">使用全局默认</option>
              <option v-for="model in embeddingModelOptions" :key="String(model.modelId)" :value="String(model.modelId)">{{ model.displayName }}</option>
            </select>
          </label>
          <label>
            <span>切片大小</span>
            <input v-model="form.chunkSize" type="number" min="100" />
          </label>
          <label>
            <span>切片重叠</span>
            <input v-model="form.chunkOverlap" type="number" min="0" />
          </label>
          <label>
            <span>召回数量</span>
            <input v-model="form.retrievalTopK" type="number" min="1" />
          </label>
          <label>
            <span>分数阈值</span>
            <select v-model="form.scoreThreshold">
              <option v-for="option in scoreThresholdOptions" :key="option.value || 'default'" :value="option.value">{{ option.label }}</option>
              <option v-if="form.scoreThreshold && !scoreThresholdOptions.some((option) => option.value === form.scoreThreshold)" :value="form.scoreThreshold">自定义阈值</option>
            </select>
          </label>
          <label class="check-row">
            <input v-model="form.rerankEnabled" type="checkbox" />
            <span>启用重排序</span>
          </label>
          <label class="full-field">
            <span>重排序模型</span>
            <select v-model="form.rerankModelId">
              <option value="">未指定</option>
              <option v-for="model in rerankModelOptions" :key="String(model.modelId)" :value="String(model.modelId)">{{ model.displayName }}</option>
            </select>
          </label>
        </div>
      </section>
    </div>
  </AdminConfirmDialog>
</template>
