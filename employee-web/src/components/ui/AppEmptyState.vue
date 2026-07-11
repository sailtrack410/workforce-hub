<script setup lang="ts">
const props = defineProps<{
  title: string;
  description?: string;
  icon?: 'messages' | 'todos' | 'files' | 'contacts' | 'workbench' | 'default';
}>();
</script>

<template>
  <div class="ui-empty-state">
    <div class="empty-mark" :class="props.icon ?? 'default'" aria-hidden="true">
      <span></span>
      <span></span>
      <i></i>
    </div>
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot />
  </div>
</template>

<style scoped>
.ui-empty-state {
  display: grid;
  align-content: center;
  align-items: center;
  justify-items: center;
  gap: var(--space-12);
  padding: var(--space-32) var(--space-20);
  border: 0;
  border-radius: var(--radius-8);
  background: var(--surface-panel);
  text-align: center;
}

.empty-mark {
  position: relative;
  display: grid;
  width: 4rem;
  height: 3.25rem;
  place-items: center;
  border-radius: var(--radius-16);
  background: var(--primary-50);
  box-shadow: inset 0 0 0 1px var(--primary-100);
}

.empty-mark span {
  position: absolute;
  left: 0.9rem;
  right: 0.9rem;
  height: 0.35rem;
  border-radius: 999px;
  background: var(--primary-300);
}

.empty-mark span:first-child {
  top: 1rem;
}

.empty-mark span:nth-child(2) {
  top: 1.75rem;
  right: 1.45rem;
}

.empty-mark i {
  position: absolute;
  right: -0.2rem;
  bottom: 0.25rem;
  width: 1.15rem;
  height: 1.15rem;
  border: 2px solid var(--surface-panel);
  border-radius: 999px;
  background: var(--primary-500);
}

.empty-mark.todos {
  background: var(--warning-50);
  box-shadow: inset 0 0 0 1px var(--warning-100);
}

.empty-mark.todos span,
.empty-mark.todos i {
  background: var(--warning-500);
}

.empty-mark.files {
  background: var(--success-50);
  box-shadow: inset 0 0 0 1px var(--success-100);
}

.empty-mark.files span,
.empty-mark.files i {
  background: var(--success-500);
}

.empty-mark.contacts {
  background: var(--surface-panel-muted);
  box-shadow: inset 0 0 0 1px var(--primary-100);
}

.empty-mark.contacts span,
.empty-mark.contacts i {
  background: var(--primary-400);
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
