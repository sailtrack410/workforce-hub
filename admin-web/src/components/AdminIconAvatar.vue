<script setup lang="ts">
import { computed } from 'vue';
import { Bot, Building2, UserRound } from 'lucide-vue-next';

const props = withDefaults(defineProps<{
  label: string;
  image?: string | null;
  kind?: 'user' | 'agent' | 'org';
  size?: 'sm' | 'md' | 'lg';
}>(), {
  image: '',
  kind: 'user',
  size: 'md',
});

const icon = computed(() => {
  if (props.kind === 'agent') return Bot;
  if (props.kind === 'org') return Building2;
  return UserRound;
});
</script>

<template>
  <span class="admin-icon-avatar" :class="[kind, size]">
    <img v-if="image" :src="image" :alt="label" />
    <component :is="icon" v-else :size="size === 'sm' ? 15 : size === 'lg' ? 22 : 18" aria-hidden="true" />
  </span>
</template>

<style scoped>
.admin-icon-avatar {
  display: inline-grid;
  place-items: center;
  overflow: hidden;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--avatar-bg);
  color: var(--green);
}

.admin-icon-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.admin-icon-avatar.sm {
  width: 28px;
  height: 28px;
}

.admin-icon-avatar.md {
  width: 34px;
  height: 34px;
}

.admin-icon-avatar.lg {
  width: 42px;
  height: 42px;
}

.admin-icon-avatar.agent {
  background: #e8f8f0;
  color: var(--success);
}

.admin-icon-avatar.org {
  border-radius: var(--radius-lg);
  background: #e8f3ff;
  color: var(--green);
}
</style>
