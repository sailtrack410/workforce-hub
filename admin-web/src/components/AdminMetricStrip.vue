<script setup lang="ts">
interface MetricCard {
  label: string;
  value: number | string;
}

defineProps<{
  cards: MetricCard[];
}>();
</script>

<template>
  <div class="metric-strip">
    <div v-for="card in cards" :key="card.label" class="metric-card">
      <span>{{ card.label }}</span>
      <strong>{{ card.value }}</strong>
    </div>
  </div>
</template>

<style scoped>
.metric-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: var(--space-md);
  width: 100%;
}

.metric-card {
  display: grid;
  gap: var(--space-xs);
  min-height: 76px;
  padding: 13px;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
}

.metric-card span {
  color: var(--muted);
  font-size: var(--text-xs);
}

.metric-card strong { font-size: var(--text-lg); }

@media (max-width: 820px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }
}
</style>
