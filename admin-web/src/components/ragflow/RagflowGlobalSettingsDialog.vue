<script setup lang="ts">
import { computed } from 'vue';
import type { LlmModelResponse } from '@/api/types';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const props = defineProps<{
  embeddingModelOptions: LlmModelResponse[];
  form: {
    ocrBaseUrl: string;
    ocrApiKey: string;
    ocrModel: string;
    ocrModelId: string;
    embeddingBaseUrl: string;
    embeddingApiKey: string;
    embeddingModel: string;
    embeddingModelId: string;
    embeddingDimension: string;
    rerankEnabled: boolean;
    rerankBaseUrl: string;
    rerankApiKey: string;
    rerankModel: string;
    rerankModelId: string;
    chunkSize: string;
    chunkOverlap: string;
    retrievalTopK: string;
    scoreThreshold: string;
  };
  ocrModelOptions: LlmModelResponse[];
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
  <AdminConfirmDialog v-model:visible="opened" title="知识库模型设置" message="" confirm-label="保存" :close-on-confirm="false" @confirm="emit('confirm')">
    <div class="ragflow-dialog-form">
      <h3 class="full-field form-subtitle">OCR</h3>
      <label>
        <span>文字识别模型</span>
        <select v-model="form.ocrModelId">
          <option value="">未指定</option>
          <option v-for="model in ocrModelOptions" :key="String(model.modelId)" :value="String(model.modelId)">{{ model.displayName }}</option>
        </select>
      </label>

      <h3 class="full-field form-subtitle">嵌入</h3>
      <label>
        <span>嵌入模型</span>
        <select v-model="form.embeddingModelId">
          <option value="">未指定</option>
          <option v-for="model in embeddingModelOptions" :key="String(model.modelId)" :value="String(model.modelId)">{{ model.displayName }}</option>
        </select>
      </label>
      <label>
        <span>检测维度</span>
        <input :value="form.embeddingDimension || '保存后自动检测'" disabled />
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

      <h3 class="full-field form-subtitle">重排序</h3>
      <label>
        <span>重排序模型</span>
        <select v-model="form.rerankModelId">
          <option value="">未指定</option>
          <option v-for="model in rerankModelOptions" :key="String(model.modelId)" :value="String(model.modelId)">{{ model.displayName }}</option>
        </select>
      </label>
    </div>
  </AdminConfirmDialog>
</template>
