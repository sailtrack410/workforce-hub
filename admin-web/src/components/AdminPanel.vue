<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string;
    span?: number;
    subtitle?: string;
  }>(),
  { span: undefined },
);
</script>

<template>
  <section :class="['panel', span ? `span-${span}` : '']">
    <div v-if="title || subtitle || $slots.actions" :class="['panel-title', { 'actions-only': !title && !subtitle }]">
      <div v-if="title || subtitle">
        <h2 v-if="title">{{ title }}</h2>
        <span v-if="subtitle" class="panel-subtitle">{{ subtitle }}</span>
      </div>
      <slot name="actions" />
    </div>
    <slot />
  </section>
</template>

<style scoped>
.panel-title.actions-only {
  justify-content: flex-end;
}

.panel-subtitle {
  color: var(--muted);
  display: inline-block;
  margin-top: var(--space-xs);
  font-size: var(--text-sm);
  line-height: 1.45;
}
</style>
