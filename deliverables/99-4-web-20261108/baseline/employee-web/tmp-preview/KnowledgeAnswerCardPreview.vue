<script setup lang="ts">
import { computed, ref } from 'vue';
import KnowledgeAnswerCard, {
  type KnowledgeAnswerAction,
  type KnowledgeCitation,
  type KnowledgeRelatedFlow,
} from '../src/components/shell/cards/KnowledgeAnswerCard.vue';

type DetailPayload = KnowledgeCitation | KnowledgeRelatedFlow | KnowledgeAnswerAction;

const selectedTitle = ref('引用依据');
const selectedPayload = ref<DetailPayload>({
  id: 'policy-annual-leave',
  title: '员工年假制度',
  sourceType: 'POLICY',
  score: 0.87,
  snippet: '年假申请通过后，系统按审批单时间扣减余额，并同步生成请假考勤结果。',
  apiRef: {
    status: 'planned',
    method: 'GET',
    path: '/api/employee/knowledge/spaces/{spaceId}/documents/{documentId}',
    description: '打开知识库原文与命中片段',
  },
});

const answeredCard = {
  state: 'ANSWERED' as const,
  title: '年假与考勤影响',
  answer: '可以申请。审批通过后系统会按请假时间扣减年假余额，并将对应日期的考勤日结果标记为请假，不再按缺卡异常处理。',
  confidence: 'high' as const,
  generatedAt: '15:24',
  missingReason: '命中 3 条制度片段，最高相似度 87%',
  relatedBusiness: [
    { label: '当前余额', value: '6.5 天', tone: 'green' as const },
    { label: '申请时长', value: '2 天', tone: 'blue' as const },
    { label: '审批路径', value: '直属上级', tone: 'amber' as const },
  ],
  relatedFlows: [
    {
      id: 'WF-20260514-001',
      title: '请假申请 WF-20260514-001',
      status: '审批中',
      statusTone: 'primary' as const,
      summary: '员工端审批详情与轨迹接口可展示申请字段、附件和动作记录。',
      fields: [
        ['状态', '审批中'],
        ['当前节点', '直属上级'],
        ['接口', '/api/employee/workflow/requests/{requestId}'],
      ],
      apiRef: {
        status: 'available' as const,
        method: 'GET' as const,
        path: '/api/employee/workflow/requests/{requestId}',
        description: '流程详情',
      },
    },
  ],
  citations: [
    {
      id: 'policy-annual-leave',
      title: '员工年假制度',
      sourceType: 'POLICY' as const,
      score: 0.87,
      snippet: '年假申请通过后，系统按审批单时间扣减余额，并同步生成请假考勤结果。',
      apiRef: {
        status: 'planned' as const,
        method: 'GET' as const,
        path: '/api/employee/knowledge/spaces/{spaceId}/documents/{documentId}',
        description: '打开知识库原文与命中片段',
      },
    },
    {
      id: 'leave-attachment',
      title: '请假申请附件',
      sourceType: 'FILE' as const,
      score: 0.81,
      snippet: '如果引用来自审批附件，点击后走 workflow 附件下载票据接口。',
      apiRef: {
        status: 'available' as const,
        method: 'POST' as const,
        path: '/api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets',
        description: '审批附件下载票据',
      },
    },
  ],
  suggestedActions: [
    {
      label: '打开申请',
      action: 'open_flow',
      apiRef: {
        status: 'available' as const,
        method: 'GET' as const,
        path: '/api/employee/workflow/requests/{requestId}',
      },
    },
    {
      label: '查看引用',
      action: 'open_citation',
      apiRef: {
        status: 'planned' as const,
        method: 'GET' as const,
        path: '/api/employee/knowledge/chunks/{chunkId}',
      },
    },
  ],
};

const noMatchCard = {
  state: 'NO_MATCH' as const,
  title: '未找到相关制度',
  answer: '当前知识库没有找到与该问题直接相关的内容。',
  missingReason: '未命中可引用的制度文件',
  suggestedActions: [
    {
      label: '上传制度文件',
      action: 'upload_policy',
      apiRef: {
        status: 'planned' as const,
        method: 'POST' as const,
        path: '/api/employee/knowledge/ingest-tasks',
      },
    },
    {
      label: '转人工确认',
      action: 'manual_confirm',
      apiRef: {
        status: 'planned' as const,
        method: 'POST' as const,
        path: '/api/employee/agent/handoffs',
      },
    },
  ],
};

const ingestingCard = {
  state: 'INGESTING' as const,
  title: '知识正在入库',
  answer: '相关文件已上传，但当前还不能作为可靠依据参与回答。',
  missingReason: '等待入库任务完成后可重新检索',
  suggestedActions: [
    {
      label: '重新检索',
      action: 'retry_search',
      apiRef: {
        status: 'planned' as const,
        method: 'POST' as const,
        path: '/api/employee/knowledge/search',
      },
    },
    {
      label: '查看入库任务',
      action: 'open_ingest_task',
      apiRef: {
        status: 'planned' as const,
        method: 'GET' as const,
        path: '/api/employee/knowledge/ingest-tasks/{taskId}',
      },
    },
  ],
};

const selectedJson = computed(() => JSON.stringify(selectedPayload.value, null, 2));

function selectDetail(title: string, payload: DetailPayload) {
  selectedTitle.value = title;
  selectedPayload.value = payload;
}
</script>

<template>
  <main class="preview-page">
    <header class="preview-head">
      <div>
        <h1>KnowledgeAnswerCard</h1>
        <p>知识库问答响应、流程联动和引用字段查看。</p>
      </div>
      <div class="status-tabs">
        <span>已回答</span>
        <span>未命中</span>
        <span>入库中</span>
      </div>
    </header>

    <section class="preview-board">
      <KnowledgeAnswerCard
        v-bind="answeredCard"
        @citation="selectDetail('引用依据', $event)"
        @flow="selectDetail('相关流程', $event)"
        @action="selectDetail('建议动作', $event)"
      />

      <aside class="detail-panel">
        <div class="detail-head">
          <strong>{{ selectedTitle }}</strong>
          <span>点击卡片字段后更新</span>
        </div>
        <pre>{{ selectedJson }}</pre>
      </aside>

      <KnowledgeAnswerCard
        v-bind="noMatchCard"
        @action="selectDetail('建议动作', $event)"
      />

      <KnowledgeAnswerCard
        v-bind="ingestingCard"
        @action="selectDetail('建议动作', $event)"
      />
    </section>
  </main>
</template>

<style scoped>
.preview-page {
  min-height: 100vh;
  padding: 2rem clamp(1.25rem, 3vw, 3rem) 3rem;
  background:
    radial-gradient(circle at 12% 10%, rgba(79, 127, 179, 0.12), transparent 22rem),
    radial-gradient(circle at 90% 94%, rgba(77, 133, 98, 0.1), transparent 24rem),
    var(--surface-canvas);
}

.preview-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  width: min(100%, 76rem);
  margin: 0 auto 1.25rem;
}

.preview-head h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.75rem;
  line-height: 1.2;
}

.preview-head p {
  margin-top: 0.35rem;
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
}

.status-tabs {
  display: inline-flex;
  gap: 0.25rem;
  padding: 0.25rem;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: color-mix(in srgb, var(--surface-panel) 78%, transparent);
}

.status-tabs span {
  min-height: 1.875rem;
  padding: 0.45rem 0.65rem;
  border-radius: var(--radius-6);
  color: var(--text-secondary);
  font-size: var(--text-caption-md);
  font-weight: 800;
}

.status-tabs span:first-child {
  background: var(--surface-accent);
  color: var(--text-accent);
}

.preview-board {
  display: grid;
  grid-template-columns: minmax(0, 43rem) minmax(20rem, 1fr);
  gap: 1rem;
  align-items: start;
  width: min(100%, 76rem);
  margin: 0 auto;
}

.preview-board > :deep(.knowledge-answer-card:nth-of-type(3)),
.preview-board > :deep(.knowledge-answer-card:nth-of-type(4)) {
  width: 100%;
}

.detail-panel {
  position: sticky;
  top: 1rem;
  display: grid;
  gap: 0.75rem;
  min-height: 22rem;
  padding: 1rem;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-panel);
  box-shadow: var(--shadow-sm);
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.detail-head strong {
  color: var(--text-primary);
  font-size: var(--text-subtitle-sm);
}

.detail-head span {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.detail-panel pre {
  overflow: auto;
  min-height: 17rem;
  margin: 0;
  padding: 0.75rem;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-panel-muted);
  color: var(--text-secondary);
  font-size: 0.75rem;
  line-height: 1.55;
  white-space: pre-wrap;
}

@media (max-width: 980px) {
  .preview-head,
  .preview-board {
    grid-template-columns: 1fr;
  }

  .preview-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .detail-panel {
    position: static;
  }
}
</style>
