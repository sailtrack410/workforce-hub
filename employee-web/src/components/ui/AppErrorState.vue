<script setup lang="ts">
import AppButton from './AppButton.vue';

defineProps<{
  title: string;
  description: string;
  actionLabel?: string;
}>();

const emit = defineEmits<{
  retry: [];
}>();
</script>

<template>
  <div class="ui-error-state" role="alert">
    <div class="error-mark">!</div>
    <div class="copy">
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
    </div>
    <AppButton v-if="actionLabel" variant="destructive" size="sm" @click="emit('retry')">
      {{ actionLabel }}
    </AppButton>
  </div>
</template>

<style scoped>
.ui-error-state {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-16);
  padding: var(--space-20);
  border: 1px solid color-mix(in srgb, var(--danger-300) 80%, transparent);
  border-radius: var(--radius-16);
  background: var(--danger-50);
}

.error-mark {
  display: grid;
  width: 2rem;
  height: 2rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 999px;
  background: var(--danger-500);
  color: var(--text-inverse);
  font-weight: 800;
}

.copy h3,
.copy p {
  margin: 0;
}

.copy h3 {
  font-size: var(--text-subtitle-sm);
}

.copy p {
  margin-top: var(--space-8);
  color: var(--text-secondary);
  font-size: var(--text-body-sm);
  line-height: 1.6;
}
</style>
