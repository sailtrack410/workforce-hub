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
.detail-list { display: grid; gap: 9px; }

.detail-list div {
  display: flex;
  justify-content: space-between;
  gap: var(--space-lg);
  border-bottom: 1px solid var(--line);
  padding-bottom: var(--space-sm);
  font-size: var(--text-sm);
}

.detail-list span { color: var(--muted); }

.detail-list strong {
  min-width: 0;
  text-align: right;
  overflow-wrap: anywhere;
}
</style>
