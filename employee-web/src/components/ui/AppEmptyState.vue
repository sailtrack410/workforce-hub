<script setup lang="ts">
const props = defineProps<{
  title: string;
  description: string;
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
    <p>{{ description }}</p>
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
  background:
    radial-gradient(circle at top, color-mix(in srgb, var(--primary-50) 42%, transparent), transparent 64%),
    color-mix(in srgb, var(--surface-panel) 72%, transparent);
  text-align: center;
}

.empty-mark {
  position: relative;
  display: grid;
  width: 4rem;
  height: 3.25rem;
  place-items: center;
  border-radius: var(--radius-16);
  background: linear-gradient(135deg, #eaf4ff, #f7fbff);
  box-shadow: inset 0 0 0 1px #dbeafe;
}

.empty-mark span {
  position: absolute;
  left: 0.9rem;
  right: 0.9rem;
  height: 0.35rem;
  border-radius: 999px;
  background: #93c5fd;
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
  background: #1677ff;
}

.empty-mark.todos {
  background: linear-gradient(135deg, #fff8e6, #fffdf5);
  box-shadow: inset 0 0 0 1px #f8e6b8;
}

.empty-mark.todos span,
.empty-mark.todos i {
  background: #d9a63a;
}

.empty-mark.files {
  background: linear-gradient(135deg, #f0fdf4, #fbfffd);
  box-shadow: inset 0 0 0 1px #cdebd8;
}

.empty-mark.files span,
.empty-mark.files i {
  background: #38a169;
}

.empty-mark.contacts {
  background: linear-gradient(135deg, #f5f3ff, #fbfaff);
  box-shadow: inset 0 0 0 1px #ddd6fe;
}

.empty-mark.contacts span,
.empty-mark.contacts i {
  background: #8b5cf6;
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
