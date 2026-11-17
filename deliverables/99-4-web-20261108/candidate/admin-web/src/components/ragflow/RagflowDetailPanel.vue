<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ArrowLeft, ChevronDown, ChevronUp, Download, RefreshCcw, Search, Settings, Trash2, Upload } from 'lucide-vue-next';
import type { EntityId, RagflowDocumentResponse, RagflowSearchTestResponse, RagflowSpaceResponse } from '@/api/types';
import { formatBytes, formatDateTime, safeBusinessMessage, statusLabel } from '@/utils/format';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';

const props = defineProps<{
  currentTitle: string;
  deletingId: EntityId | null;
  deletingSpace: boolean;
  docLoading: boolean;
  documentColumns: TableColumn[];
  documents: RagflowDocumentResponse[];
  reingestingId: EntityId | null;
  searchLoading: boolean;
  searchQuery: string;
  searchResult: RagflowSearchTestResponse | null;
  selected: RagflowSpaceResponse | null;
}>();

const emit = defineEmits<{
  'update:searchQuery': [value: string];
  back: [];
  delete: [value: RagflowDocumentResponse];
  'delete-space': [value: RagflowSpaceResponse];
  download: [value: RagflowDocumentResponse];
  'load-documents': [];
  'open-settings': [value: RagflowSpaceResponse];
  'open-upload': [];
  reingest: [value: RagflowDocumentResponse];
  search: [];
}>();

const searchValue = computed({
  get: () => props.searchQuery,
  set: (value: string) => emit('update:searchQuery', value),
});

type SearchHit = RagflowSearchTestResponse['hits'][number];

const expandedHitIds = ref(new Set<string>());

watch(
  () => props.searchResult,
  () => {
    expandedHitIds.value = new Set();
  },
);

function hitKey(hit: SearchHit) {
  return String(hit.chunkId);
}

function hitText(hit: SearchHit) {
  return hit.contentText?.trim() || '-';
}

function isLongHit(hit: SearchHit) {
  return (hit.contentText?.trim().length ?? 0) > 180;
}

function isHitExpanded(hit: SearchHit) {
  return expandedHitIds.value.has(hitKey(hit));
}

function toggleHit(hit: SearchHit) {
  const key = hitKey(hit);
  const next = new Set(expandedHitIds.value);
  if (next.has(key)) {
    next.delete(key);
  } else {
    next.add(key);
  }
  expandedHitIds.value = next;
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

function parserTypeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    TEXT: '文本',
    WORD: '文档',
    DOCUMENT: '文档',
    PDF: 'PDF',
    PPT: '演示文稿',
    TABLE: '表格',
    OCR: '图片 OCR',
  };
  return value ? labels[value.toUpperCase()] ?? '未知解析方式' : '-';
}
</script>

<template>
  <AdminPanel :span="12">
    <div class="kb-detail-head">
      <button class="icon-button" title="返回知识库列表" @click="emit('back')">
        <ArrowLeft :size="16" />
      </button>
      <div class="kb-detail-title">
        <h2>{{ currentTitle }}</h2>
        <span>文件、向量化进度、知识检索</span>
      </div>
      <div class="kb-detail-actions">
        <button v-if="selected" class="secondary-button" @click="emit('open-settings', selected)">
          <Settings :size="16" />
          <span>设置</span>
        </button>
        <button v-if="selected" class="danger-button" :disabled="deletingSpace" @click="emit('delete-space', selected)">
          <Trash2 :size="16" />
          <span>删除</span>
        </button>
        <button class="secondary-button" title="刷新文件列表" :disabled="docLoading" @click="emit('load-documents')">
          <RefreshCcw :size="16" />
          <span>刷新</span>
        </button>
      </div>
    </div>

    <div class="detail-summary">
      <span>文件 {{ selected?.documentCount ?? 0 }}</span>
      <span>切片 {{ selected?.chunkCount ?? 0 }}</span>
      <span>{{ visibilityLabel(selected?.visibility) }}</span>
    </div>
  </AdminPanel>

  <section class="ragflow-detail-layout">
    <AdminPanel title="文件列表">
      <template #actions>
        <button class="primary-button" type="button" @click="emit('open-upload')">
          <Upload :size="16" />
          <span>上传文件</span>
        </button>
      </template>

      <AdminTable :columns="documentColumns" :rows="documents" :loading="docLoading" row-key="documentId">
        <template #cell-title="{ row }">
          <strong>{{ row.title }}</strong>
          <span>{{ formatBytes(row.fileSize) }}</span>
        </template>
        <template #cell-parserType="{ row }">
          {{ row.parserTypeName || parserTypeLabel(row.parserType) }}
        </template>
        <template #cell-ingestStatus="{ row }">
          <span class="status-pill">{{ row.ingestStatusName || statusLabel(row.ingestStatus) }}</span>
          <small v-if="row.errorMessage" class="cell-note error-text">{{ safeBusinessMessage(row.errorMessage) }}</small>
        </template>
        <template #cell-vectorStatus="{ row }">
          <span class="status-pill">{{ row.vectorStatusName || statusLabel(row.vectorStatus || 'PENDING') }}</span>
        </template>
        <template #cell-createdAt="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
        <template #cell-_actions="{ row }">
          <button class="secondary-button action-btn" title="重新入库" aria-label="重新入库" :disabled="reingestingId === row.documentId" @click.stop="emit('reingest', row)">
            <RefreshCcw :size="14" />
          </button>
          <button class="secondary-button action-btn" title="下载文件" aria-label="下载文件" :disabled="!row.attachmentRefId" @click.stop="emit('download', row)">
            <Download :size="14" />
          </button>
          <button class="danger-button action-btn" title="删除文件" aria-label="删除文件" :disabled="deletingId === row.documentId" @click.stop="emit('delete', row)">
            <Trash2 :size="14" />
          </button>
        </template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel title="检索测试">
      <form class="search-test" @submit.prevent="emit('search')">
        <input v-model="searchValue" placeholder="输入问题或关键词" />
        <button class="secondary-button" :disabled="searchLoading" type="submit">
          <Search :size="16" />
          <span>{{ searchLoading ? '检索中' : '测试检索' }}</span>
        </button>
      </form>

      <div v-if="searchLoading" class="empty-state">检索中…</div>
      <div v-else-if="searchResult?.hits.length" class="hit-list">
        <article v-for="hit in searchResult.hits" :key="String(hit.chunkId)" class="hit-item">
          <strong>{{ hit.documentTitle || '-' }}</strong>
          <span class="hit-meta">切片 {{ hit.chunkNo ?? '-' }}{{ hit.pageNo ? ` · 第 ${hit.pageNo} 页` : '' }}{{ hit.score == null ? '' : ` · ${(hit.score * 100).toFixed(1)}%` }}</span>
          <p :class="['hit-content', { expanded: isHitExpanded(hit) }]">{{ hitText(hit) }}</p>
          <button
            v-if="isLongHit(hit)"
            class="hit-toggle"
            type="button"
            :aria-expanded="isHitExpanded(hit)"
            @click="toggleHit(hit)"
          >
            <component :is="isHitExpanded(hit) ? ChevronUp : ChevronDown" :size="14" />
            <span>{{ isHitExpanded(hit) ? '收起' : '展开' }}</span>
          </button>
        </article>
      </div>
      <div v-else class="empty-state">暂无结果</div>
    </AdminPanel>
  </section>
</template>
