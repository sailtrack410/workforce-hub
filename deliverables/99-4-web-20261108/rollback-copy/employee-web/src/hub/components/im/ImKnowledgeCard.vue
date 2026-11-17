<script setup lang="ts">
/**
 * ImKnowledgeCard：AI 知识回答卡，对齐画布 3:2315。
 * 灰底卡（--hub-inner）r16 pad 16 w≤520：图标+标题 / 正文 / 来源引用 chips / 操作按钮。
 */
import { computed } from 'vue';
import { BookOpen, FileText } from 'lucide-vue-next';
import HubAvatar from '../HubAvatar.vue';
import type { KnowledgeAnswerAction, KnowledgeAnswerCard } from '@/types/knowledge';

const props = defineProps<{
  card: KnowledgeAnswerCard;
  avatarUrl?: string;
}>();

const emit = defineEmits<{
  (e: 'action', action: KnowledgeAnswerAction): void;
}>();

const loading = computed(() => props.card.state === 'INGESTING' || !props.card.answer);
const citations = computed(() => props.card.citations ?? []);
const actions = computed(() => (props.card.suggestedActions ?? []).filter((a) => !a.disabled));
</script>

<template>
  <div class="im-kc-row">
    <HubAvatar label="AI" :image="avatarUrl || ''" size="md" agent />
    <div class="im-kc">
      <header class="im-kc-head">
        <BookOpen :size="16" :stroke-width="2" />
        <strong>{{ card.title || '知识回答' }}</strong>
      </header>

      <p v-if="loading" class="im-kc-loading">
        <span class="im-kc-dot" /><span class="im-kc-dot" /><span class="im-kc-dot" />
        正在查询
      </p>
      <template v-else>
        <p class="im-kc-answer">{{ card.answer }}</p>

        <div v-if="citations.length" class="im-kc-sources">
          <small class="im-kc-sources-title">来源引用</small>
          <div class="im-kc-sources-list">
            <span v-for="(c, i) in citations" :key="c.id || i" class="im-kc-source">
              <FileText :size="14" :stroke-width="2" />
              {{ c.title }}
            </span>
          </div>
        </div>

        <div v-if="actions.length" class="im-kc-actions">
          <button
            v-for="(a, i) in actions" :key="a.action || i" type="button"
            class="im-kc-action" :class="{ primary: i === 0 }"
            @click="emit('action', a)"
          >{{ a.label }}</button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.im-kc-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.im-kc {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(520px, 100%);
  padding: 16px;
  border-radius: 16px;
  background: var(--hub-inner, #f6f8fb);
  box-sizing: border-box;
}
.im-kc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--hub-primary, #1677ff);
}
.im-kc-head strong {
  font-size: 14px;
  font-weight: 500;
  color: var(--hub-ink, #181d23);
}
.im-kc-answer {
  margin: 0;
  font-size: 13px;
  line-height: 21px;
  color: var(--hub-secondary, #424b56);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.im-kc-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 13px;
  color: var(--hub-muted, #9ba6b4);
}
.im-kc-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--hub-primary, #1677ff);
  animation: im-kc-bounce 1s infinite ease-in-out;
}
.im-kc-dot:nth-child(2) { animation-delay: 0.15s; }
.im-kc-dot:nth-child(3) { animation-delay: 0.3s; }
@keyframes im-kc-bounce {
  0%, 100% { opacity: 0.3; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-3px); }
}
.im-kc-sources {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.im-kc-sources-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--hub-muted, #9ba6b4);
}
.im-kc-sources-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.im-kc-source {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--hub-track, #eaeff5);
  border-radius: 8px;
  background: var(--hub-card, #fff);
  font-size: 12px;
  color: var(--hub-primary, #1677ff);
}
.im-kc-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.im-kc-action {
  padding: 7px 16px;
  border: 1px solid var(--hub-track, #eaeff5);
  border-radius: 999px;
  background: var(--hub-card, #fff);
  color: var(--hub-secondary, #424b56);
  font: 500 12px/16px "Noto Sans SC", sans-serif;
  cursor: pointer;
  transition: background 0.2s;
}
.im-kc-action:hover { background: var(--hub-chip, #f2f5f9); }
.im-kc-action.primary {
  border: 0;
  background: var(--hub-primary, #1677ff);
  color: #fff;
}
.im-kc-action.primary:hover { background: var(--hub-primary-deep, #124da3); }
</style>
