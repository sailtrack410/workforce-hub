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
  background: linear-gradient(
    90deg,
    var(--line) 25%,
    rgba(217, 222, 211, 0.4) 50%,
    var(--line) 75%
  );
  background-size: 200% 100%;
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

@keyframes skeleton-pulse {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
