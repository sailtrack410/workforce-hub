<script setup lang="ts">
import {
  ArrowRight,
  Bookshelf,
  Brain,
  Caution,
  DatabaseAlert,
  DatabaseSearch,
  DatabaseSuccess,
  DatabaseSync,
  DocDetail,
  DocSearch,
  FileDoc,
  FileSearch,
  FileProtection,
  FileSuccessOne,
  FolderSearch,
  Lock,
  MagicWand,
  ManualGear,
  MindmapMap,
  ProcessLine,
  Refresh,
  Shield,
} from '@icon-park/vue-next';
import { computed } from 'vue';
import AppBadge from '../../ui/AppBadge.vue';
import AppButton from '../../ui/AppButton.vue';
import type {
  AgentResultCategory,
  AgentResultStatus,
  KnowledgeAnswerAction,
  KnowledgeAnswerRelatedRef,
  KnowledgeAnswerState,
  KnowledgeApiRef,
  KnowledgeCitation,
  KnowledgeRelatedFlow,
} from '@/types/knowledge';

const props = withDefaults(defineProps<{
  state: KnowledgeAnswerState;
  category?: AgentResultCategory;
  status?: AgentResultStatus;
  title: string;
  answer: string;
  confidence?: 'high' | 'medium' | 'low';
  citations?: KnowledgeCitation[];
  relatedFlows?: KnowledgeRelatedFlow[];
  missingReason?: string;
  suggestedActions?: KnowledgeAnswerAction[];
  relatedBusiness?: KnowledgeAnswerRelatedRef[];
  generatedAt?: string;
}>(), {
  category: undefined,
  status: undefined,
  confidence: undefined,
  citations: () => [],
  relatedFlows: () => [],
  missingReason: '',
  suggestedActions: () => [],
  relatedBusiness: () => [],
  generatedAt: '',
});

const emit = defineEmits<{
  action: [action: KnowledgeAnswerAction];
  citation: [citation: KnowledgeCitation];
  flow: [flow: KnowledgeRelatedFlow];
}>();

const iconPalettes: Record<string, string[]> = {
  answered: ['#064e3b', '#6ee7b7', '#ffffff', '#10b981'],
  noMatch: ['#78350f', '#fcd34d', '#ffffff', '#f59e0b'],
  lowConfidence: ['#7c2d12', '#fdba74', '#ffffff', '#f97316'],
  noPermission: ['#7f1d1d', '#fca5a5', '#ffffff', '#ef4444'],
  ingesting: ['#075985', '#7dd3fc', '#ffffff', '#0ea5e9'],
  failed: ['#7f1d1d', '#fecaca', '#ffffff', '#dc2626'],
  knowledge: ['#0f3d63', '#93c5fd', '#ffffff', '#2563eb'],
  policy: ['#14532d', '#86efac', '#ffffff', '#22c55e'],
  file: ['#78350f', '#fde68a', '#ffffff', '#f59e0b'],
  workflow: ['#134e4a', '#99f6e4', '#ffffff', '#14b8a6'],
  unknown: ['#334155', '#cbd5e1', '#ffffff', '#64748b'],
  action: ['#1e3a8a', '#bfdbfe', '#ffffff', '#3b82f6'],
};

const stateMeta = computed(() => {
  if (props.state === 'ANSWERED') {
    return {
      icon: DatabaseSuccess,
      fill: iconPalettes.answered,
      label: props.category ? categoryLabel(props.category) : '已回答',
      tone: 'success' as const,
      className: 'answered',
      helper: successHelper(),
    };
  }
  if (props.state === 'NO_MATCH') {
    return {
      icon: DocSearch,
      fill: iconPalettes.noMatch,
      label: '未命中',
      tone: 'warning' as const,
      className: 'no-match',
      helper: '未找到可引用内容',
    };
  }
  if (props.state === 'LOW_CONFIDENCE') {
    return {
      icon: Brain,
      fill: iconPalettes.lowConfidence,
      label: '低置信',
      tone: 'warning' as const,
      className: 'low-confidence',
      helper: '建议人工确认',
    };
  }
  if (props.state === 'NO_PERMISSION') {
    return {
      icon: FileProtection,
      fill: iconPalettes.noPermission,
      label: '无权限',
      tone: 'danger' as const,
      className: 'no-permission',
      helper: '依据不可见',
    };
  }
  if (props.state === 'INGESTING') {
    return {
      icon: DatabaseSync,
      fill: iconPalettes.ingesting,
      label: props.status === 'LOADING' ? '查询中' : '入库中',
      tone: 'primary' as const,
      className: 'ingesting',
      helper: props.status === 'LOADING' ? '正在检索数据' : '知识正在处理',
    };
  }
  return {
    icon: DatabaseAlert,
    fill: iconPalettes.failed,
    label: '检索失败',
    tone: 'danger' as const,
    className: 'search-failed',
    helper: '服务暂不可用',
  };
});

const confidenceLabel = computed(() => {
  if (props.confidence === 'high') return '高置信';
  if (props.confidence === 'medium') return '中置信';
  if (props.confidence === 'low') return '低置信';
  return '';
});

const confidenceTone = computed(() => {
  if (props.confidence === 'high') return 'success';
  if (props.confidence === 'medium') return 'primary';
  if (props.confidence === 'low') return 'warning';
  return 'neutral';
});

function visibleBusinessText(value?: string) {
  const text = value?.trim();
  if (!text) return '';
  if (/https?:\/\//i.test(text) || text.includes('/api/')) return '';
  if (/\bWF-[A-Z0-9-]*\d[A-Z0-9-]*\b/i.test(text)) return '';
  if (/\b\d{16,}\b/.test(text)) return '';
  if (/^[A-Z][A-Z0-9_]{2,}$/.test(text)) return '';
  return text;
}

function visibleFlowFields(flow: KnowledgeRelatedFlow) {
  return (flow.fields ?? [])
    .map(([key, value]) => [key, visibleBusinessText(value)] as [string, string])
      .filter(([, value]) => value);
}

function visibleRelatedBusiness() {
  return props.relatedBusiness
    .map((item) => ({ ...item, value: visibleBusinessText(item.value) }))
    .filter((item) => item.value);
}

function categoryLabel(category?: AgentResultCategory) {
  if (category === 'OA') return 'OA';
  if (category === 'QA') return 'QA';
  if (category === 'KNOWLEDGE') return '知识库';
  return '已回答';
}

function successHelper() {
  if (props.category === 'OA') return '已基于业务数据生成';
  if (props.category === 'QA') return '已生成回答';
  return '已基于可访问知识生成';
}

function scoreLabel(score?: number) {
  if (typeof score !== 'number') return '';
  return `${Math.round(score * 100)}%`;
}

function sourceTypeLabel(sourceType?: KnowledgeCitation['sourceType']) {
  if (sourceType === 'DOC') return '文档';
  if (sourceType === 'FILE') return '文件';
  if (sourceType === 'WORKFLOW') return '流程';
  if (sourceType === 'POLICY') return '制度';
  return '知识';
}

function citationIcon(sourceType?: KnowledgeCitation['sourceType']) {
  if (sourceType === 'POLICY') return FileProtection;
  if (sourceType === 'DOC') return DocDetail;
  if (sourceType === 'FILE') return FolderSearch;
  if (sourceType === 'WORKFLOW') return MindmapMap;
  return FileSearch;
}

function citationIconFill(sourceType?: KnowledgeCitation['sourceType']) {
  if (sourceType === 'POLICY') return iconPalettes.policy;
  if (sourceType === 'DOC') return iconPalettes.knowledge;
  if (sourceType === 'FILE') return iconPalettes.file;
  if (sourceType === 'WORKFLOW') return iconPalettes.workflow;
  return iconPalettes.unknown;
}

function actionIcon(action: KnowledgeAnswerAction) {
  if (action.action.includes('OPEN_FLOW')) return ProcessLine;
  if (action.action.includes('open_flow')) return ProcessLine;
  if (action.action.includes('retry')) return Refresh;
  if (action.action.includes('manual')) return ManualGear;
  if (action.action.includes('agent')) return MagicWand;
  return ArrowRight;
}

function apiRefLabel(apiRef?: KnowledgeApiRef) {
  if (!apiRef) return '';
  if (apiRef.status === 'available') return '已接接口';
  if (apiRef.status === 'planned') return '接口预留';
  return '不可用';
}

function fallbackReason() {
  if (props.missingReason) return props.missingReason;
  if (props.state === 'NO_MATCH') return '当前问题没有命中可引用知识';
  if (props.state === 'NO_PERMISSION') return '存在相关内容，但当前身份不能查看依据';
  if (props.state === 'INGESTING') return '文件已接收，切片与向量化尚未完成';
  if (props.state === 'SEARCH_FAILED') return '知识检索服务返回异常';
  return '';
}

function shouldShowEmptyCitation() {
  return props.category === 'KNOWLEDGE' && props.state === 'ANSWERED';
}
</script>

<template>
  <article class="knowledge-answer-card" :class="stateMeta.className">
    <header class="card-head">
      <div class="state-mark">
        <component :is="stateMeta.icon" theme="multi-color" :fill="stateMeta.fill" :size="36" />
      </div>
      <div class="head-copy">
        <div class="title-row">
          <h3>{{ props.title }}</h3>
          <AppBadge :tone="stateMeta.tone">{{ stateMeta.label }}</AppBadge>
          <AppBadge v-if="confidenceLabel" :tone="confidenceTone">{{ confidenceLabel }}</AppBadge>
        </div>
        <p>{{ stateMeta.helper }}<span v-if="props.generatedAt"> · {{ props.generatedAt }}</span></p>
      </div>
    </header>

    <section class="answer-panel" :class="{ loading: props.status === 'LOADING' }">
      <p v-if="props.answer">{{ props.answer }}</p>
      <div v-else-if="props.status === 'LOADING'" class="loading-row">
        <span></span>
        <span></span>
        <span></span>
      </div>
      <div v-if="fallbackReason()" class="reason-row">
        <DatabaseSearch theme="multi-color" :fill="iconPalettes.knowledge" :size="19" />
        <span>{{ fallbackReason() }}</span>
      </div>
    </section>

    <section v-if="visibleRelatedBusiness().length" class="business-strip" aria-label="关联字段">
      <span
        v-for="item in visibleRelatedBusiness()"
        :key="`${item.label}-${item.value}`"
        :class="item.tone ? `tone-${item.tone}` : ''"
      >
        <small>{{ item.label }}</small>
        <b>{{ item.value }}</b>
      </span>
    </section>

    <section v-if="props.relatedFlows.length" class="flow-section">
      <div class="section-head">
        <span>
          <ProcessLine theme="multi-color" :fill="iconPalettes.workflow" :size="17" />
          相关流程
        </span>
        <small>{{ props.relatedFlows.length }} 条</small>
      </div>

      <button
        v-for="flow in props.relatedFlows"
        :key="flow.id"
        class="flow-row"
        type="button"
        @click="emit('flow', flow)"
      >
        <span class="flow-mark">
          <MindmapMap theme="multi-color" :fill="iconPalettes.workflow" :size="29" />
        </span>
        <span class="flow-copy">
          <strong>{{ visibleBusinessText(flow.title) || '相关流程' }}</strong>
          <small v-if="visibleBusinessText(flow.summary)">{{ visibleBusinessText(flow.summary) }}</small>
          <span v-if="visibleFlowFields(flow).length" class="flow-fields">
            <i v-for="field in visibleFlowFields(flow)" :key="field[0]">{{ field[0] }}：{{ field[1] }}</i>
          </span>
        </span>
        <span class="flow-meta">
          <AppBadge :tone="flow.statusTone ?? 'neutral'">{{ visibleBusinessText(flow.status) || '待处理' }}</AppBadge>
          <small v-if="apiRefLabel(flow.apiRef)">{{ apiRefLabel(flow.apiRef) }}</small>
        </span>
      </button>
    </section>

    <section v-if="props.citations.length" class="citation-section">
      <div class="section-head">
        <span>
          <Shield theme="multi-color" :fill="iconPalettes.policy" :size="17" />
          引用依据
        </span>
        <small>{{ props.citations.length }} 条</small>
      </div>

      <button
        v-for="citation in props.citations"
        :key="citation.id || citation.title"
        class="citation-row"
        type="button"
        @click="emit('citation', citation)"
      >
        <span class="file-mark">
          <component
            :is="citationIcon(citation.sourceType)"
            theme="multi-color"
            :fill="citationIconFill(citation.sourceType)"
            :size="29"
          />
        </span>
        <span class="citation-copy">
          <strong>{{ citation.title }}</strong>
          <small v-if="citation.snippet">{{ citation.snippet }}</small>
        </span>
        <span class="citation-meta">
          <b>{{ sourceTypeLabel(citation.sourceType) }}</b>
          <small v-if="scoreLabel(citation.score)">{{ scoreLabel(citation.score) }}</small>
          <small v-if="apiRefLabel(citation.apiRef)" class="api-ref">{{ apiRefLabel(citation.apiRef) }}</small>
        </span>
      </button>
    </section>

    <section v-else-if="shouldShowEmptyCitation()" class="empty-citation">
      <div>
        <FileSuccessOne theme="multi-color" :fill="iconPalettes.noMatch" :size="23" />
        <strong>暂无可展示依据</strong>
      </div>
      <span>本次回复不会伪造引用</span>
    </section>

    <footer v-if="props.suggestedActions.length" class="card-actions">
      <AppButton
        v-for="action in props.suggestedActions"
        :key="action.action"
        :variant="action.disabled ? 'secondary' : 'primary'"
        size="sm"
        :disabled="action.disabled"
        @click="emit('action', action)"
      >
        <component :is="actionIcon(action)" theme="multi-color" :fill="iconPalettes.action" :size="16" />
        {{ action.label }}
      </AppButton>
    </footer>
  </article>
</template>

<style scoped>
.knowledge-answer-card {
  display: grid;
  gap: var(--space-14, 0.875rem);
  width: min(100%, 43rem);
  padding: var(--space-16);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-panel);
  box-shadow: var(--shadow-sm);
}

.knowledge-answer-card.answered {
  border-color: color-mix(in srgb, var(--success-300) 48%, var(--border-subtle));
}

.knowledge-answer-card.low-confidence,
.knowledge-answer-card.no-match,
.knowledge-answer-card.ingesting {
  border-color: color-mix(in srgb, var(--warning-300) 44%, var(--border-subtle));
}

.knowledge-answer-card.no-permission,
.knowledge-answer-card.search-failed {
  border-color: color-mix(in srgb, var(--danger-300) 38%, var(--border-subtle));
}

.card-head {
  display: grid;
  grid-template-columns: 3.25rem minmax(0, 1fr);
  gap: var(--space-14, 0.875rem);
  align-items: start;
}

.state-mark {
  position: relative;
  display: inline-grid;
  place-items: center;
  overflow: hidden;
  width: 3.25rem;
  height: 3.25rem;
  border: 1px solid var(--success-200);
  border-radius: 1rem;
  background: var(--success-50);
  box-shadow: var(--shadow-sm);
}

.state-mark::after {
  content: none;
}

.no-match .state-mark {
  border-color: var(--warning-200);
  background: var(--warning-50);
  box-shadow: var(--shadow-sm);
}

.low-confidence .state-mark {
  border-color: var(--warning-300);
  background: var(--warning-50);
  box-shadow: var(--shadow-sm);
}

.no-permission .state-mark,
.search-failed .state-mark {
  border-color: var(--danger-200);
  background: var(--danger-50);
  box-shadow: var(--shadow-sm);
}

.ingesting .state-mark {
  border-color: var(--info-200);
  background: var(--info-50);
  box-shadow: var(--shadow-sm);
}

.state-mark :deep(.i-icon) {
  position: relative;
  z-index: 1;
  padding: 0.25rem;
  border-radius: 0.75rem;
  background: var(--surface-panel);
  filter: drop-shadow(0 3px 5px rgba(15, 23, 42, 0.18));
}

.ingesting .state-mark :deep(.i-icon) {
  animation: spin 1s linear infinite;
}

.head-copy {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

.title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-8);
}

.title-row h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-subtitle-sm);
  line-height: 1.35;
}

.head-copy p,
.answer-panel p,
.reason-row,
.empty-citation,
.citation-copy small,
.flow-copy small,
.citation-meta,
.flow-meta,
.business-strip small {
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
}

.head-copy p {
  margin: 0;
  color: var(--text-tertiary);
}

.answer-panel {
  display: grid;
  gap: var(--space-10, 0.625rem);
  padding: var(--space-14, 0.875rem);
  border: 1px solid color-mix(in srgb, var(--border-subtle) 74%, transparent);
  border-radius: var(--radius-8);
  background: color-mix(in srgb, var(--surface-panel-muted) 42%, var(--surface-panel));
}

.answer-panel p {
  margin: 0;
  color: var(--text-primary);
  line-height: 1.72;
}

.loading-row {
  display: grid;
  gap: var(--space-8);
}

.loading-row span {
  height: 0.75rem;
  border-radius: 999px;
  background: var(--surface-panel-muted);
  animation: loading-shimmer 1.1s linear infinite;
}

.loading-row span:nth-child(2) {
  width: 82%;
}

.loading-row span:nth-child(3) {
  width: 58%;
}

.reason-row {
  display: inline-flex;
  align-items: center;
  gap: var(--space-8);
  min-width: 0;
  padding-top: var(--space-8);
  border-top: 1px solid var(--border-subtle);
  color: var(--text-tertiary);
}

.business-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-8);
}

.business-strip span {
  min-width: 0;
  padding: var(--space-10, 0.625rem) var(--space-12);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-panel);
}

.business-strip small,
.business-strip b {
  display: block;
  overflow-wrap: anywhere;
}

.business-strip small {
  color: var(--text-tertiary);
}

.business-strip b {
  margin-top: var(--space-4);
  color: var(--text-primary);
  font-size: var(--text-body-sm);
}

.citation-section,
.flow-section {
  display: grid;
  gap: var(--space-8);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12);
}

.section-head span {
  display: inline-flex;
  align-items: center;
  gap: var(--space-8);
  color: var(--text-primary);
  font-size: var(--text-caption-md);
  font-weight: 800;
}

.section-head small {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.citation-row,
.flow-row {
  display: grid;
  gap: var(--space-10, 0.625rem);
  align-items: center;
  width: 100%;
  padding: var(--space-10, 0.625rem);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-panel);
  color: var(--text-primary);
  text-align: left;
}

.citation-row {
  grid-template-columns: 2.625rem minmax(0, 1fr) auto;
}

.flow-row {
  grid-template-columns: 2.625rem minmax(0, 1fr) auto;
}

.citation-row:hover,
.citation-row:focus-visible,
.flow-row:hover,
.flow-row:focus-visible {
  outline: none;
  border-color: var(--border-accent);
  background: var(--surface-accent);
}

.file-mark,
.flow-mark {
  position: relative;
  display: inline-grid;
  place-items: center;
  overflow: hidden;
  width: 2.625rem;
  height: 2.625rem;
  border: 1px solid var(--primary-200);
  border-radius: 0.875rem;
  background: var(--primary-50);
  box-shadow: var(--shadow-sm);
}

.flow-mark {
  border-color: var(--success-200);
  background: var(--success-50);
  box-shadow: var(--shadow-sm);
}

.file-mark::after,
.flow-mark::after {
  position: absolute;
  right: -0.45rem;
  bottom: -0.55rem;
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 999px;
  background: var(--surface-panel);
  content: '';
}

.file-mark :deep(.i-icon),
.flow-mark :deep(.i-icon) {
  position: relative;
  z-index: 1;
  padding: 0.2rem;
  border-radius: 0.625rem;
  background: var(--surface-panel);
  filter: drop-shadow(0 2px 4px rgba(15, 23, 42, 0.14));
}

.state-mark :deep(.i-icon),
.file-mark :deep(.i-icon),
.flow-mark :deep(.i-icon),
.section-head :deep(.i-icon),
.reason-row :deep(.i-icon),
.empty-citation :deep(.i-icon),
.card-actions :deep(.i-icon) {
  display: inline-flex;
  flex: 0 0 auto;
}

.citation-copy,
.flow-copy {
  display: grid;
  gap: var(--space-4);
  min-width: 0;
}

.citation-copy strong,
.flow-copy strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--text-body-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.citation-copy small,
.flow-copy small {
  display: -webkit-box;
  overflow: hidden;
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.flow-fields {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
}

.flow-fields i {
  padding: 0.125rem var(--space-8);
  border-radius: 999px;
  background: var(--surface-panel-muted);
  color: var(--text-secondary);
  font-size: var(--text-caption-md);
  font-style: normal;
  font-weight: 700;
}

.citation-meta,
.flow-meta {
  display: grid;
  gap: var(--space-4);
  justify-items: end;
  white-space: nowrap;
}

.citation-meta b {
  color: var(--text-secondary);
  font-size: var(--text-caption-md);
}

.citation-meta small,
.flow-meta small {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.api-ref {
  color: var(--text-accent) !important;
  font-weight: 700;
}

.empty-citation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-16);
  padding: var(--space-12);
  border: 1px dashed var(--border-default);
  border-radius: var(--radius-8);
  color: var(--text-tertiary);
}

.empty-citation div {
  display: inline-flex;
  align-items: center;
  gap: var(--space-8);
}

.empty-citation strong {
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
}

.empty-citation span {
  font-size: var(--text-caption-md);
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-8);
  padding-top: var(--space-4);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes loading-shimmer {
  to {
    background-position: -220% 0;
  }
}

@media (max-width: 720px) {
  .business-strip {
    grid-template-columns: 1fr;
  }

  .citation-row,
  .flow-row {
    grid-template-columns: 2.625rem minmax(0, 1fr);
  }

  .citation-meta,
  .flow-meta {
    grid-column: 2;
    justify-items: start;
  }

  .empty-citation {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
