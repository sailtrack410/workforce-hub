<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Bot, Building2 } from 'lucide-vue-next';

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

// 用户头像用 label 首字 + 稳定哈希取色，避免所有人共用同一个剪影图标。
const PALETTES = [
  ['#eef2ff', '#4f46e5'],
  ['#ecfeff', '#0891b2'],
  ['#f0fdf4', '#16a34a'],
  ['#fff7ed', '#ea580c'],
  ['#fdf2f8', '#db2777'],
  ['#fefce8', '#a16207'],
  ['#f5f3ff', '#7c3aed'],
  ['#eff6ff', '#2563eb'],
] as const;

function hashLabel(value: string): number {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) >>> 0;
  }
  return hash;
}

const initial = computed(() => {
  const text = props.label.trim();
  return text ? Array.from(text)[0].toUpperCase() : '?';
});

const palette = computed(() => PALETTES[hashLabel(props.label) % PALETTES.length]);

const isGlyphKind = computed(() => props.kind !== 'user');
const fallbackIcon = computed(() => (props.kind === 'agent' ? Bot : Building2));

const imageFailed = ref(false);
// 内置占位剪影与首字头像效果重复，视为无图，走首字回退。
const PLACEHOLDER_IMAGES = new Set([
  '/default-avatar.svg',
  '/default-user-avatar.svg',
  '/default-group-avatar.svg',
  '/default-agent-avatar.svg',
]);
const showImage = computed(() => Boolean(props.image) && !PLACEHOLDER_IMAGES.has(props.image ?? '') && !imageFailed.value);

watch(() => props.image, () => {
  imageFailed.value = false;
});
</script>

<template>
  <span class="ui-avatar-wrap">
    <span
      class="ui-avatar"
      :class="[props.tone, props.size, { 'has-image': showImage, 'is-initial': !isGlyphKind && !showImage }]"
      :style="!isGlyphKind && !showImage
        ? { background: palette[0], color: palette[1] }
        : undefined"
    >
      <img v-if="showImage" :src="props.image" :alt="props.label" @error="imageFailed = true" />
      <span v-else-if="!isGlyphKind" class="ui-avatar-initial" aria-hidden="true">{{ initial }}</span>
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

.ui-avatar.is-initial {
  font-weight: 700;
}

.ui-avatar-initial {
  line-height: 1;
  transform: translateY(0.02em);
  user-select: none;
}

.ui-avatar.sm .ui-avatar-initial {
  font-size: 0.6875rem;
}

.ui-avatar.md .ui-avatar-initial {
  font-size: 0.8125rem;
}

.ui-avatar.lg .ui-avatar-initial {
  font-size: 1.0625rem;
}

.ui-avatar.xl .ui-avatar-initial {
  font-size: 1.75rem;
}

.ui-avatar.primary {
  background: var(--primary-100);
  color: var(--primary-600);
}

.ui-avatar.success {
  background: var(--success-100);
  color: var(--success-600);
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
  background: var(--surface-panel-strong);
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
