<script setup lang="ts">
/**
 * ImProcessCard：流程草稿/进度卡，对齐画布 3:2340。
 * 白底 + 浅蓝描边 r16 pad 16 w≤520：标题+状态 pill / 字段行(key-value) / 校验项 / 操作。
 */
import { computed } from 'vue';
import { Check, X } from 'lucide-vue-next';
import HubAvatar from '../HubAvatar.vue';
import type { ConversationProcessCard } from '@/stores/shell';

const props = defineProps<{
  card: ConversationProcessCard;
  avatarUrl?: string;
}>();

const emit = defineEmits<{
  (e: 'action', card: ConversationProcessCard): void;
}>();

const statusClass = computed(() => ({
  success: 'ok',
  warning: 'warn',
  info: 'info',
  neutral: 'neutral',
}[props.card.statusTone] ?? 'neutral'));

const fields = computed(() => props.card.fields ?? []);
const checks = computed(() => props.card.checks ?? []);
</script>

<template>
  <div class="im-pc-row">
    <HubAvatar label="AI" :image="avatarUrl || ''" size="md" agent />
    <div class="im-pc">
      <header class="im-pc-head">
        <strong>{{ card.title }}</strong>
        <span class="im-pc-status" :class="statusClass">{{ card.status }}</span>
      </header>

      <p v-if="card.body" class="im-pc-body">{{ card.body }}</p>

      <div v-if="fields.length" class="im-pc-fields">
        <div v-for="[k, v] in fields" :key="k" class="im-pc-field">
          <small>{{ k }}</small>
          <span>{{ v }}</span>
        </div>
      </div>

      <div v-if="checks.length" class="im-pc-checks">
        <div v-for="c in checks" :key="c.label" class="im-pc-check" :class="{ fail: !c.passed }">
          <Check v-if="c.passed" :size="13" :stroke-width="2.5" />
          <X v-else :size="13" :stroke-width="2.5" />
          <small>{{ c.label }}</small>
          <em>{{ c.result }}</em>
        </div>
      </div>

      <div v-if="card.actionLabel" class="im-pc-actions">
        <button type="button" class="im-pc-action" @click="emit('action', card)">{{ card.actionLabel }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.im-pc-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.im-pc {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(520px, 100%);
  padding: 16px;
  border: 1px solid #87c2ff;
  border-radius: 16px;
  background: var(--hub-card, #fff);
  box-sizing: border-box;
}
.im-pc-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.im-pc-head strong {
  font-size: 14px;
  font-weight: 500;
  color: var(--hub-ink, #181d23);
}
.im-pc-status {
  padding: 3px 10px;
  border-radius: 999px;
  font: 500 12px/16px "Noto Sans SC", sans-serif;
}
.im-pc-status.ok { background: #edf7f1; color: var(--hub-green, #1fad66); }
.im-pc-status.warn { background: #fbf5e8; color: #d98a1f; }
.im-pc-status.info { background: var(--hub-sky, #d9ebff); color: var(--hub-primary, #1677ff); }
.im-pc-status.neutral { background: var(--hub-chip, #f2f5f9); color: var(--hub-secondary, #424b56); }
.im-pc-body {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
  color: var(--hub-secondary, #424b56);
  white-space: pre-wrap;
}
.im-pc-fields {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.im-pc-field {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}
.im-pc-field small {
  font-size: 12px;
  color: var(--hub-tertiary, #717d8c);
  flex: none;
}
.im-pc-field span {
  font-size: 12px;
  font-weight: 500;
  color: var(--hub-ink, #181d23);
  text-align: right;
}
.im-pc-checks {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.im-pc-check {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--hub-green, #1fad66);
}
.im-pc-check.fail { color: var(--hub-red, #ff5a4e); }
.im-pc-check small { font-size: 12px; color: var(--hub-secondary, #424b56); }
.im-pc-check em {
  font-style: normal;
  font-size: 12px;
  color: var(--hub-muted, #9ba6b4);
  margin-left: auto;
}
.im-pc-actions {
  display: flex;
  gap: 10px;
}
.im-pc-action {
  padding: 7px 16px;
  border: 0;
  border-radius: 999px;
  background: var(--hub-primary, #1677ff);
  color: #fff;
  font: 500 12px/16px "Noto Sans SC", sans-serif;
  cursor: pointer;
  transition: background 0.2s;
}
.im-pc-action:hover { background: var(--hub-primary-deep, #124da3); }
</style>
