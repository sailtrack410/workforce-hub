<script setup lang="ts">
export interface DetailRow {
  label: string;
  value: string;
}

withDefaults(
  defineProps<{
    rows: DetailRow[];
    error?: string;
  }>(),
  { error: '' },
);
</script>

<template>
  <div v-if="rows.length > 0" class="detail-list">
    <div v-for="row in rows" :key="row.label">
      <span>{{ row.label }}</span>
      <strong>{{ row.value }}</strong>
    </div>
  </div>
  <div v-else class="empty-state">无数据</div>
  <p v-if="error" class="inline-error">{{ error }}</p>
</template>

<style scoped>
.detail-list {
  display: grid;
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--surface-subtle);
  overflow: hidden;
}

.detail-list div {
  display: grid;
  grid-template-columns: minmax(112px, 0.4fr) minmax(0, 1fr);
  align-items: start;
  gap: var(--space-lg);
  border-bottom: 1px solid var(--line);
  padding: var(--space-sm) var(--space-md);
  font-size: var(--text-sm);
  line-height: 1.5;
}

.detail-list div:last-child { border-bottom: 0; }

.detail-list span { color: var(--muted); }

.detail-list strong {
  min-width: 0;
  text-align: right;
  overflow-wrap: anywhere;
}

@media (max-width: 480px) {
  .detail-list div { grid-template-columns: minmax(0, 1fr); gap: 2px; }
  .detail-list strong { text-align: left; }
}
</style>
