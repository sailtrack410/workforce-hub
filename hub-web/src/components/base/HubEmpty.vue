<script setup lang="ts">
/**
 * HubEmpty：空态（圆形图标底 + 标题 + 说明 + 动作区）。
 */
import type { Component } from 'vue';
import { Inbox } from 'lucide-vue-next';

withDefaults(defineProps<{
  title?: string;
  note?: string;
  icon?: Component;
}>(), {
  title: '暂无数据',
  note: '',
  icon: Inbox,
});
</script>

<template>
  <div class="hw-empty">
    <span class="hw-empty-icon"><component :is="icon" :size="20" :stroke-width="2" /></span>
    <strong>{{ title }}</strong>
    <small v-if="note">{{ note }}</small>
    <div v-if="$slots.default" class="hw-empty-actions"><slot /></div>
  </div>
</template>

<style scoped>
.hw-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 16px;
  flex: 1;
  text-align: center;
}
.hw-empty-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  background: var(--hw-chip);
  color: var(--hw-tertiary);
  margin-bottom: 4px;
}
.hw-empty strong { font-size: 13px; font-weight: 500; color: var(--hw-secondary); }
.hw-empty small { font-size: 12px; color: var(--hw-muted); }
.hw-empty-actions { display: flex; gap: 8px; margin-top: 8px; }
</style>
