<script setup lang="ts">
/**
 * PlaceholderView：考勤/Agent 页面占位（HubShell 由 App.vue 常驻提供，页面不自包壳）。
 */
import { useRoute } from 'vue-router';

const route = useRoute();
const TITLES: Record<string, string> = {
  attendance: '我的考勤',
  agent: 'Agent',
};
const title = TITLES[String(route.name) || ''] || '页面';
</script>

<template>
  <div class="placeholder">
    <h2>{{ title }}</h2>
    <p>该页面正在按画布实现中，敬请期待</p>
  </div>
</template>

<style scoped>
.placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 20px;
  background: var(--hw-card);
  min-height: 0;
}
.placeholder h2 {
  margin: 0;
  font-size: 20px;
  color: var(--hw-ink);
}
.placeholder p {
  margin: 0;
  font-size: 13px;
  color: var(--hw-tertiary);
}
</style>
