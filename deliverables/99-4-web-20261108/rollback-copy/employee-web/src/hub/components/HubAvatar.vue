<script setup lang="ts">
/**
 * HubAvatar：圆形头像，对齐画布头像规范（999 圆角）。
 * 无图时回退为首字符 + 按名字散列的确定性底色，全新实现，不依赖旧组件。
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
const FONT: Record<string, number> = { xs: 11, sm: 13, md: 16, lg: 19, xl: 26 };

// 与画布一致的蓝紫系散列色板（头像兜底）
const PALETTE = [
  ['#e3f0ff', '#1677ff'],
  ['#e7f6ef', '#1fad66'],
  ['#fbf5e8', '#d98a1f'],
  ['#f3e9ff', '#7c5ce0'],
  ['#e6f7fa', '#1595ad'],
  ['#fdeef0', '#d95d68'],
];

const px = computed(() => SIZE[props.size] ?? 40);
const fontSize = computed(() => FONT[props.size] ?? 16);
const initial = computed(() => (props.label || '').trim().slice(0, 1) || '·');
const tone = computed(() => {
  if (props.agent) return ['#e3f0ff', '#1677ff'];
  let hash = 0;
  for (const ch of props.label || '') hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return PALETTE[hash % PALETTE.length];
});
</script>

<template>
  <span
    class="hub-avatar"
    :class="{ agent }"
    :style="{ width: `${px}px`, height: `${px}px` }"
    role="img"
    :aria-label="label"
  >
    <img v-if="image" :src="image" :alt="label" loading="lazy" />
    <span
      v-else
      class="hub-avatar-fallback"
      :style="{ background: tone[0], color: tone[1], fontSize: `${fontSize}px` }"
    >{{ initial }}</span>
  </span>
</template>

<style scoped>
.hub-avatar {
  display: inline-grid;
  place-items: center;
  flex: none;
  border-radius: 999px;
  overflow: hidden;
}
.hub-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.hub-avatar-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  font-weight: 500;
  user-select: none;
}
.hub-avatar.agent .hub-avatar-fallback {
  background: linear-gradient(135deg, #4fa1ff 0%, #1677ff 100%);
  color: #fff;
}
</style>
