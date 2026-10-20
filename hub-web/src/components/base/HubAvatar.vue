<script setup lang="ts">
/**
 * HubAvatar：圆形头像（画布 999 圆角）。无图回退首字符 + 名字散列色。
 */
import { computed } from 'vue';

const props = withDefaults(defineProps<{
  label?: string;
  image?: string;
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  agent?: boolean;
}>(), {
  label: '',
  image: '',
  size: 'md',
  agent: false,
});

const SIZE: Record<string, number> = { xs: 24, sm: 32, md: 40, lg: 48, xl: 64 };
const FONT: Record<string, number> = { xs: 10, sm: 13, md: 16, lg: 19, xl: 26 };
const PALETTE = [
  ['#e3f0ff', '#1677ff'],
  ['#e7f6ef', '#1fad66'],
  ['#fbf5e8', '#d98a1f'],
  ['#f3e9ff', '#7c5ce0'],
  ['#e6f7fa', '#1595ad'],
  ['#fdeef0', '#d95d68'],
];

const px = computed(() => SIZE[props.size]);
const fontSize = computed(() => FONT[props.size]);
const initial = computed(() => (props.label || '').trim().slice(0, 1) || '·');
const tone = computed(() => {
  if (props.agent) return PALETTE[0];
  let hash = 0;
  for (const ch of props.label || '') hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return PALETTE[hash % PALETTE.length];
});
</script>

<template>
  <span class="hw-avatar" :class="{ agent }" :style="{ width: `${px}px`, height: `${px}px` }" role="img" :aria-label="label">
    <img v-if="image" :src="image" :alt="label" loading="lazy" />
    <span v-else class="hw-avatar-fallback" :style="{ background: tone[0], color: tone[1], fontSize: `${fontSize}px` }">{{ initial }}</span>
  </span>
</template>

<style scoped>
.hw-avatar {
  display: inline-grid;
  place-items: center;
  flex: none;
  border-radius: 999px;
  overflow: hidden;
}
.hw-avatar img { width: 100%; height: 100%; object-fit: cover; display: block; }
.hw-avatar-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  font-weight: 500;
  user-select: none;
}
.hw-avatar.agent .hw-avatar-fallback {
  background: linear-gradient(135deg, #4fa1ff 0%, #1677ff 100%);
  color: #fff;
}
</style>
