<script setup lang="ts">
import { computed } from 'vue';

defineOptions({ name: 'AppSkeleton' });

const props = withDefaults(defineProps<{
  variant?: 'text' | 'list' | 'grid' | 'chat' | 'block';
  count?: number;
  blockHeight?: string;
}>(), {
  variant: 'text',
  count: 1,
  blockHeight: '5rem',
});

const items = computed(() => Array.from({ length: Math.max(1, Math.floor(props.count)) }, (_, i) => i));
</script>

<template>
  <div class="app-skeleton" :class="`is-${variant}`" role="status" aria-label="内容加载中">
    <template v-if="variant === 'list'">
      <div v-for="i in items" :key="i" class="sk-row">
        <span class="sk sk-avatar" />
        <span class="sk-lines">
          <span class="sk sk-line w-60" />
          <span class="sk sk-line w-40" />
        </span>
      </div>
    </template>

    <template v-else-if="variant === 'grid'">
      <div v-for="i in items" :key="i" class="sk-card">
        <span class="sk sk-thumb" />
        <span class="sk sk-line w-70" />
        <span class="sk sk-line w-45" />
      </div>
    </template>

    <template v-else-if="variant === 'chat'">
      <div v-for="i in items" :key="i" class="sk-bubble-row">
        <span class="sk sk-bubble" />
      </div>
    </template>

    <template v-else-if="variant === 'block'">
      <span v-for="i in items" :key="i" class="sk sk-block" :style="{ height: blockHeight }" />
    </template>

    <template v-else>
      <span v-for="i in items" :key="i" class="sk sk-line" :class="i % 2 ? 'w-55' : ''" />
    </template>
  </div>
</template>

<style scoped>
.app-skeleton {
  display: grid;
  gap: var(--space-12);
  width: 100%;
}

.app-skeleton.is-text {
  gap: var(--space-8);
}

.app-skeleton.is-grid {
  grid-template-columns: repeat(auto-fill, minmax(9.5rem, 1fr));
}

.sk {
  position: relative;
  overflow: hidden;
  display: block;
  border-radius: var(--radius-4);
  background: var(--surface-panel-strong);
}

.sk::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent,
    color-mix(in srgb, var(--surface-panel) 72%, transparent),
    transparent
  );
  transform: translateX(-100%);
  animation: sk-sweep 1.4s ease-in-out infinite;
}

.sk-line {
  width: 100%;
  height: 0.7rem;
}

.is-text > .sk-line {
  height: 1rem;
  border-radius: var(--radius-6);
}

.sk-row {
  display: flex;
  align-items: center;
  gap: var(--space-12);
  padding: var(--space-12);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-panel);
}

.sk-avatar {
  width: 2.75rem;
  height: 2.75rem;
  flex: none;
  border-radius: 999px;
}

.sk-lines {
  display: grid;
  flex: 1;
  gap: var(--space-8);
  min-width: 0;
}

.sk-card {
  display: grid;
  gap: var(--space-8);
  padding: var(--space-12);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: var(--surface-panel);
}

.sk-thumb {
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-6);
}

.sk-bubble-row {
  display: flex;
}

.sk-bubble-row:nth-child(odd) {
  justify-content: flex-start;
}

.sk-bubble-row:nth-child(even) {
  justify-content: flex-end;
}

.sk-bubble {
  width: 56%;
  height: 2.6rem;
  border-radius: var(--radius-8);
}

.sk-bubble-row:nth-child(3n) .sk-bubble {
  width: 38%;
}

.sk-block {
  width: 100%;
  border-radius: var(--radius-8);
}

.w-40 { width: 40%; }
.w-45 { width: 45%; }
.w-55 { width: 55%; }
.w-60 { width: 60%; }
.w-70 { width: 70%; }

@media (prefers-reduced-motion: reduce) {
  .sk::after {
    animation: none;
  }
}
</style>
