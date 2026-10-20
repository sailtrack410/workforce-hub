<script setup lang="ts">
/**
 * HubToast：轻提示（底部居中 pill，2.6s 自动消失）。
 * 配合 composables/useToast 使用。
 */
withDefaults(defineProps<{ message?: string }>(), { message: '' });
</script>

<template>
  <transition name="hw-toast">
    <div v-if="message" class="hw-toast">{{ message }}</div>
  </transition>
</template>

<style scoped>
.hw-toast {
  position: fixed;
  left: 50%;
  bottom: 40px;
  transform: translateX(-50%);
  z-index: 70;
  padding: 10px 18px;
  border-radius: 999px;
  background: var(--hw-pill-dark);
  color: #fff;
  font-size: 13px;
  box-shadow: 0 12px 32px rgba(15, 30, 55, 0.2);
}
.hw-toast-enter-active, .hw-toast-leave-active { transition: opacity 0.25s var(--hw-ease), transform 0.25s var(--hw-ease); }
.hw-toast-enter-from, .hw-toast-leave-to { opacity: 0; transform: translate(-50%, 8px); }
</style>
