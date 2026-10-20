<script setup lang="ts">
/**
 * ImProcessCard：流程卡（画布 3:2340：白底蓝描边 + 标题状态/字段行/操作）。
 */
import { computed } from 'vue';
import HubAvatar from '../base/HubAvatar.vue';
import type { ProcessCardData } from '@/utils/message';

const props = defineProps<{
  card: ProcessCardData;
  avatarUrl?: string;
  avatarLabel?: string;
}>();

const emit = defineEmits<{
  (e: 'action'): void;
}>();

const statusClass = computed(() => `is-${props.card.statusTone}`);
</script>

<template>
  <div class="im-pc-row">
    <HubAvatar :label="avatarLabel || 'AI'" :image="avatarUrl" size="md" agent />
    <div class="im-pc">
      <header class="im-pc-head">
        <strong>{{ card.title }}</strong>
        <span v-if="card.status" class="im-pc-status" :class="statusClass">{{ card.status }}</span>
      </header>
      <p v-if="card.body" class="im-pc-body">{{ card.body }}</p>
      <div v-if="card.fields.length" class="im-pc-fields">
        <div v-for="[k, v] in card.fields" :key="k" class="im-pc-field">
          <small>{{ k }}</small>
          <span>{{ v }}</span>
        </div>
      </div>
      <div v-if="card.actionLabel" class="im-pc-actions">
        <button type="button" class="im-pc-action" @click="emit('action')">{{ card.actionLabel }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.im-pc-row { display: flex; align-items: flex-start; gap: 10px; }
.im-pc {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: min(520px, 100%);
  padding: 16px;
  border: 1px solid var(--hw-sky-strong);
  border-radius: 16px;
  background: var(--hw-card);
}
.im-pc-head { display: flex; align-items: center; gap: 8px; }
.im-pc-head strong { font-size: 14px; font-weight: 500; color: var(--hw-ink); }
.im-pc-status {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}
.im-pc-status.is-ok { background: var(--hw-green-bg); color: var(--hw-green); }
.im-pc-status.is-warn { background: var(--hw-amber-bg); color: var(--hw-amber-strong); }
.im-pc-status.is-info { background: var(--hw-sky); color: var(--hw-primary); }
.im-pc-status.is-neutral { background: var(--hw-chip); color: var(--hw-secondary); }
.im-pc-body {
  font-size: 13px;
  line-height: 20px;
  color: var(--hw-secondary);
  white-space: pre-wrap;
}
.im-pc-fields { display: flex; flex-direction: column; gap: 8px; }
.im-pc-field {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}
.im-pc-field small { font-size: 12px; color: var(--hw-tertiary); flex: none; }
.im-pc-field span { font-size: 12px; font-weight: 500; color: var(--hw-ink); text-align: right; }
.im-pc-actions { display: flex; gap: 10px; }
.im-pc-action {
  padding: 7px 16px;
  border: 0;
  border-radius: 999px;
  background: var(--hw-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s;
}
.im-pc-action:hover { background: var(--hw-primary-deep); }
</style>
