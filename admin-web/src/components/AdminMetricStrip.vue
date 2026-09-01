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
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 150px), 1fr));
  gap: var(--space-md);
  width: 100%;
}

.metric-card {
  position: relative;
  display: grid;
  align-content: center;
  gap: var(--space-xs);
  min-height: 76px;
  padding: var(--space-md) var(--space-base);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface-subtle);
  overflow: hidden;
}

.metric-card::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--green);
}

.metric-card span {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.metric-card strong {
  font-size: var(--text-lg);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 820px) {
  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 420px) {
  .metric-strip { grid-template-columns: 1fr; }
}
</style>
