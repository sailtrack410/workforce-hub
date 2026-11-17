<script setup lang="ts">
withDefaults(
  defineProps<{
    lines?: number;
    height?: string;
  }>(),
  { lines: 3, height: '16px' },
);
</script>

<template>
  <div class="skeleton-wrap">
    <div
      v-for="i in lines"
      :key="i"
      class="skeleton-line"
      :style="{ height }"
    />
  </div>
</template>

<style scoped>
.skeleton-wrap {
  display: grid;
  gap: 10px;
}

.skeleton-line {
  border-radius: var(--radius-sm);
  background: var(--line);
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

@keyframes skeleton-pulse {
  50% { opacity: 0.42; }
}
</style>
