<script setup lang="ts">
/**
 * ImKnowledgeCard：AI 知识回答卡（画布 3:2315：灰底卡 + 标题/正文/来源 chips/操作）。
 */
import { computed } from 'vue';
import { BookOpen, FileText } from 'lucide-vue-next';
import HubAvatar from '../base/HubAvatar.vue';
import type { KnowledgeCardData } from '@/utils/message';

const props = defineProps<{
  card: KnowledgeCardData;
  avatarUrl?: string;
  avatarLabel?: string;
}>();

const emit = defineEmits<{
  (e: 'action', action: { label: string; action?: string }): void;
}>();

const loading = computed(() => props.card.loading);
</script>

<template>
  <div class="im-kc-row">
    <HubAvatar :label="avatarLabel || 'AI'" :image="avatarUrl" size="md" agent />
    <div class="im-kc">
      <header class="im-kc-head">
        <BookOpen :size="16" :stroke-width="2" />
        <strong>{{ card.title }}</strong>
      </header>
      <p v-if="loading" class="im-kc-loading">
        <span class="im-kc-dot" /><span class="im-kc-dot" /><span class="im-kc-dot" />
        正在查询
      </p>
      <template v-else>
        <p class="im-kc-answer">{{ card.answer }}</p>
        <div v-if="card.citations.length" class="im-kc-sources">
          <small>来源引用</small>
          <div class="im-kc-sources-list">
            <span v-for="c in card.citations" :key="c.id" class="im-kc-source">
              <FileText :size="14" :stroke-width="2" />
              {{ c.title }}
            </span>
          </div>
        </div>
        <div v-if="card.actions.length" class="im-kc-actions">
          <button
            v-for="(a, i) in card.actions" :key="a.action || i" type="button"
            class="im-kc-action" :class="{ primary: i === 0 }"
            @click="emit('action', a)"
          >{{ a.label }}</button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.im-kc-row { display: flex; align-items: flex-start; gap: 10px; }
.im-kc {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(520px, 100%);
  padding: 16px;
  border-radius: 16px;
  background: var(--hw-inner);
}
.im-kc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--hw-primary);
}
.im-kc-head strong { font-size: 14px; font-weight: 500; color: var(--hw-ink); }
.im-kc-answer {
  font-size: 13px;
  line-height: 21px;
  color: var(--hw-secondary);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.im-kc-loading {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--hw-muted);
}
.im-kc-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--hw-primary);
  animation: im-kc-bounce 1s infinite ease-in-out;
}
.im-kc-dot:nth-child(2) { animation-delay: 0.15s; }
.im-kc-dot:nth-child(3) { animation-delay: 0.3s; }
@keyframes im-kc-bounce {
  0%, 100% { opacity: 0.3; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-3px); }
}
.im-kc-sources { display: flex; flex-direction: column; gap: 6px; }
.im-kc-sources > small { font-size: 12px; font-weight: 500; color: var(--hw-muted); }
.im-kc-sources-list { display: flex; flex-wrap: wrap; gap: 8px; }
.im-kc-source {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border: 1px solid var(--hw-track);
  border-radius: 8px;
  background: var(--hw-card);
  font-size: 12px;
  color: var(--hw-primary);
}
.im-kc-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.im-kc-action {
  padding: 7px 16px;
  border: 1px solid var(--hw-track);
  border-radius: 999px;
  background: var(--hw-card);
  color: var(--hw-secondary);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}
.im-kc-action:hover { background: var(--hw-chip); }
.im-kc-action.primary {
  border: 0;
  background: var(--hw-primary);
  color: #fff;
}
.im-kc-action.primary:hover { background: var(--hw-primary-deep); }
</style>
