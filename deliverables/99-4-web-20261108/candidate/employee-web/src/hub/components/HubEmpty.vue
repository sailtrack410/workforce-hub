<script setup lang="ts">
/**
 * HubEmpty：空态，画布规范（圆形图标底 + 标题 + 可选动作区）。
 * 全新实现，不依赖旧 AppEmptyState。
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
  <div class="hub-empty">
    <span class="hub-empty-icon">
      <component :is="icon" :size="20" :stroke-width="2" />
    </span>
    <strong class="hub-empty-title">{{ title }}</strong>
    <small v-if="note" class="hub-empty-note">{{ note }}</small>
    <div v-if="$slots.default" class="hub-empty-actions">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.hub-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 16px;
  flex: 1;
  text-align: center;
}
.hub-empty-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 999px;
  background: var(--hub-chip, #f2f5f9);
  color: var(--hub-tertiary, #717d8c);
  margin-bottom: 4px;
}
.hub-empty-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--hub-secondary, #424b56);
}
.hub-empty-note {
  font-size: 12px;
  color: var(--hub-muted, #9ba6b4);
}
.hub-empty-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
</style>
