<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Bot, Building2, UserRound } from 'lucide-vue-next';

const props = withDefaults(defineProps<{
  label: string;
  image?: string;
  kind?: 'user' | 'agent' | 'org';
  tone?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
  size?: 'sm' | 'md' | 'lg' | 'xl';
  status?: 'online' | 'busy' | 'away' | 'offline' | '';
}>(), {
  image: '',
  kind: 'user',
  tone: 'primary',
  size: 'md',
  status: '',
});

const fallbackIcon = computed(() => {
  if (props.kind === 'agent') return Bot;
  if (props.kind === 'org') return Building2;
  return UserRound;
});

const imageFailed = ref(false);
const showImage = computed(() => Boolean(props.image) && !imageFailed.value);

watch(() => props.image, () => {
  imageFailed.value = false;
});
</script>

<template>
  <span class="ui-avatar-wrap">
    <span class="ui-avatar" :class="[props.tone, props.size, { 'has-image': showImage }]">
      <img v-if="showImage" :src="props.image" :alt="props.label" @error="imageFailed = true" />
      <component :is="fallbackIcon" v-else :size="props.size === 'sm' ? 15 : props.size === 'xl' ? 34 : 20" aria-hidden="true" />
    </span>
    <span v-if="props.status" class="ui-avatar-status" :class="props.status" />
  </span>
</template>

<style scoped>
.ui-avatar-wrap {
  position: relative;
  display: inline-grid;
}

.ui-avatar {
  display: grid;
  place-items: center;
  border-radius: 999px;
  font-size: var(--text-caption-md);
  font-weight: 750;
  overflow: hidden;
}

.ui-avatar svg {
  stroke-width: 2;
}

.ui-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ui-avatar.sm {
  width: 1.75rem;
  height: 1.75rem;
  border-radius: 999px;
}

.ui-avatar.md {
  width: 2.25rem;
  height: 2.25rem;
}

.ui-avatar.lg {
  width: 2.75rem;
  height: 2.75rem;
  border-radius: 999px;
}

.ui-avatar.xl {
  width: 4.75rem;
  height: 4.75rem;
  border-radius: 999px;
}

.ui-avatar.primary {
  background: #e8f3ff;
  color: #1677ff;
}

.ui-avatar.success {
  background: #e8f8f0;
  color: #1fad66;
}

.ui-avatar.warning {
  background: var(--warning-100);
  color: var(--warning-700);
}

.ui-avatar.danger {
  background: var(--danger-100);
  color: var(--danger-700);
}

.ui-avatar.neutral {
  background: #e6eaf0;
  color: var(--text-secondary);
}

.ui-avatar-status {
  position: absolute;
  right: -0.0625rem;
  bottom: -0.0625rem;
  width: 0.625rem;
  height: 0.625rem;
  border: 2px solid var(--surface-panel);
  border-radius: 999px;
}

.ui-avatar-status.online {
  background: var(--success-500);
}

.ui-avatar-status.busy {
  background: var(--danger-500);
}

.ui-avatar-status.away {
  background: var(--warning-500);
}

.ui-avatar-status.offline {
  background: var(--neutral-400);
}
</style>
