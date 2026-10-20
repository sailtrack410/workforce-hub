<script setup lang="ts">
/**
 * hub-web 应用根组件。
 * 布局约定：HubShell 常驻于路由外层——切换页面时壳不卸载，
 * 顶部导航的弹性滑块才能跨页面平滑过渡；
 * 登录/注册页为无壳裸页。
 */
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import HubShell from '@/components/shell/HubShell.vue';

const route = useRoute();
const bare = computed(() => route.name === 'login' || route.name === 'register');
</script>

<template>
  <router-view v-if="bare" />
  <HubShell v-else>
    <router-view />
  </HubShell>
</template>
