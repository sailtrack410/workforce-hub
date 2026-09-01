<script setup lang="ts">
import { computed } from 'vue';
import emptyMessages from '../../assets/brand/empty-messages.jpg';
import emptyTodos from '../../assets/brand/empty-todos.jpg';
import emptyFiles from '../../assets/brand/empty-files.jpg';
import emptyContacts from '../../assets/brand/empty-contacts.jpg';
import emptyWorkbench from '../../assets/brand/empty-workbench.jpg';
import emptyGeneric from '../../assets/brand/empty-generic.jpg';

const props = defineProps<{
  title: string;
  description?: string;
  icon?: 'messages' | 'todos' | 'files' | 'contacts' | 'workbench' | 'default';
  actionLabel?: string;
}>();

const emit = defineEmits<{ (event: 'action'): void }>();

const emptyImage = computed(() => {
  if (props.icon === 'messages') return emptyMessages;
  if (props.icon === 'todos') return emptyTodos;
  if (props.icon === 'files') return emptyFiles;
  if (props.icon === 'contacts') return emptyContacts;
  if (props.icon === 'workbench') return emptyWorkbench;
  return emptyGeneric;
});
</script>

<template>
  <div class="ui-empty-state">
    <img class="empty-illustration" :src="emptyImage" alt="" aria-hidden="true" />
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <slot />
    <button v-if="actionLabel" class="empty-action" type="button" @click="emit('action')">
      {{ actionLabel }}
    </button>
  </div>
</template>

<style scoped>
.ui-empty-state {
  display: grid;
  align-content: center;
  align-items: center;
  justify-items: center;
  gap: var(--space-12);
  min-height: 11rem;
  padding: var(--space-24) var(--space-20);
  border: 0;
  border-radius: var(--radius-8);
  background: transparent;
  text-align: center;
}

.empty-illustration {
  width: 8.5rem;
  height: 8.5rem;
  object-fit: cover;
  border-radius: var(--radius-16, 1rem);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}

.empty-action {
  margin-top: var(--space-4);
  padding: var(--space-8) var(--space-20);
  border: 0;
  border-radius: var(--radius-8);
  background: var(--primary-600);
  color: #fff;
  font-size: var(--text-body-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease;
}

.empty-action:hover {
  background: var(--primary-700, var(--primary-600));
  transform: translateY(-1px);
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
