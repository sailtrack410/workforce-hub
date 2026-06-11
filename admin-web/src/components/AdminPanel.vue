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
    <div v-if="title || $slots.actions" class="panel-title">
      <div>
        <h2>{{ title }}</h2>
        <span v-if="subtitle" class="panel-subtitle">{{ subtitle }}</span>
      </div>
      <slot name="actions" />
    </div>
    <slot />
  </section>
</template>

<style scoped>
.panel-subtitle {
  color: var(--muted);
  font-size: 13px;
}
</style>
