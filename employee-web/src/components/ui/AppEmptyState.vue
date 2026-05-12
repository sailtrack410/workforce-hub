<script setup lang="ts">
defineProps<{
  title: string;
  description: string;
}>();
</script>

<template>
  <div class="ui-empty-state">
    <div class="empty-mark" aria-hidden="true"></div>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.ui-empty-state {
  display: grid;
  justify-items: center;
  gap: var(--space-12);
  padding: var(--space-32) var(--space-20);
  border: 0;
  border-radius: var(--radius-8);
  background:
    radial-gradient(circle at top, color-mix(in srgb, var(--primary-50) 42%, transparent), transparent 64%),
    color-mix(in srgb, var(--surface-panel) 72%, transparent);
  text-align: center;
}

.empty-mark {
  display: grid;
  width: 3rem;
  height: 3rem;
  place-items: center;
  border-radius: 999px;
  background:
    linear-gradient(90deg, transparent 0 42%, var(--primary-500) 42% 58%, transparent 58% 100%),
    linear-gradient(0deg, transparent 0 42%, var(--primary-500) 42% 58%, transparent 58% 100%),
    var(--surface-accent);
  opacity: 0.72;
}

.ui-empty-state h3 {
  margin: 0;
  font-size: var(--text-subtitle-sm);
}

.ui-empty-state p {
  margin: 0;
  max-width: 22rem;
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
  line-height: 1.6;
}
</style>
