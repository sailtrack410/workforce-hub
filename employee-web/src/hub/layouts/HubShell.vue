<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Bell, Search, Settings, ChevronDown, Home, MessageSquare, FileCheck,
  Clock, Sparkles, Users, Moon, SunMedium,
} from 'lucide-vue-next';
import { useShellStore } from '@/stores/shell';
import { useSessionStore } from '@/stores/session';

/**
 * Hub 新壳布局：对齐画布「员工端-新工作台」3:1285（1440×900 版）。
 * 仅顶部导航(64 高圆角20,导航居中)，页面容器 padding 20/16、区块 gap 16。
 */
const shell = useShellStore();
const session = useSessionStore();
const route = useRoute();
const router = useRouter();

onMounted(async () => {
  document.body.dataset.theme = shell.theme;
  if (!session.initialized) {
    await session.loadCurrentUser().catch(() => undefined);
    if (!session.isAuthenticated) {
      void router.replace({ name: 'employee-login' });
    }
  }
});

const primaryNav = [
  { id: 'workbench', label: '工作台', icon: Home, path: '/hub/workbench' },
  { id: 'messages', label: '消息', icon: MessageSquare, path: '/hub/messages' },
  { id: 'approval', label: '审批', icon: FileCheck, path: '/hub/approvals' },
  { id: 'attendance', label: '考勤', icon: Clock, path: '/hub/messages' },
  { id: 'agent', label: 'Agent', icon: Sparkles, path: '/hub/messages' },
];

const railNav = [
  { id: 'workbench', label: '工作台', icon: Home, path: '/hub/workbench' },
  { id: 'messages', label: '消息', icon: MessageSquare, path: '/hub/messages' },
  { id: 'approval', label: '审批', icon: FileCheck, path: '/hub/approvals' },
  { id: 'attendance', label: '考勤', icon: Clock, path: '/hub/messages' },
  { id: 'agent', label: 'Agent', icon: Sparkles, path: '/hub/messages' },
  { id: 'sep', label: '', icon: null, path: '' },
  { id: 'files', label: '文件', icon: FileCheck, path: '/files' },
  { id: 'contacts', label: '通讯录', icon: Users, path: '/contacts' },
];

const activeNavId = computed(() => {
  const p = route.path;
  if (p.startsWith('/hub/messages')) return 'messages';
  if (p.startsWith('/hub/approvals')) return 'approval';
  if (p.startsWith('/hub/workbench')) return 'workbench';
  const hit = primaryNav.find((n) => n.path && !n.path.startsWith('/hub') && p.startsWith(n.path));
  return hit?.id ?? '';
});

const displayName = computed(() => session.displayName || session.user?.displayName || '');
const avatarChar = computed(() => displayName.value.slice(0, 1) || '我');

function goto(item: { path: string }) {
  if (item.path) router.push(item.path).catch(() => undefined);
}
</script>

<template>
  <div class="hub-shell" :data-theme="shell.theme">
    <!-- 顶部导航：高64 圆角20,品牌左/导航中/操作右 -->
    <header class="hub-topbar">
      <div class="hub-brand" @click="goto(primaryNav[0])">
        <span class="hub-brand-avatar">W</span>
        <strong class="hub-brand-name">Workforce Hub</strong>
      </div>

      <nav class="hub-nav" aria-label="主功能">
        <button
          v-for="item in primaryNav"
          :key="item.id"
          type="button"
          class="hub-nav-item"
          :class="{ active: activeNavId === item.id }"
          @click="goto(item)"
        >{{ item.label }}</button>
      </nav>

      <div class="hub-topbar-actions">
        <div class="hub-search">
          <Search :size="16" :stroke-width="2" class="hub-search-icon" />
          <input type="text" placeholder="搜索同事、申请、知识…" disabled>
        </div>
        <button type="button" class="hub-icon-btn" aria-label="通知">
          <Bell :size="18" :stroke-width="2" />
          <span class="hub-badge-dot" />
        </button>
        <button type="button" class="hub-icon-btn" aria-label="设置">
          <Settings :size="18" :stroke-width="2" />
        </button>
        <button type="button" class="hub-avatar" :aria-label="displayName || '用户'">
          <span class="hub-avatar-img">{{ avatarChar }}</span>
          <ChevronDown :size="14" :stroke-width="2" class="hub-avatar-caret" />
        </button>
      </div>
    </header>

    <div class="hub-body">
      <!-- 左侧图标栏：64 宽 蓝渐变,20×20 线性图标 + 底部主题切换 -->
      <aside class="hub-rail" aria-label="快捷功能">
        <template v-for="item in railNav" :key="item.id">
          <span v-if="item.id === 'sep'" class="hub-rail-sep" />
          <button
            v-else
            type="button"
            class="hub-rail-btn"
            :class="{ active: activeNavId === item.id }"
            :title="item.label"
            :aria-label="item.label"
            @click="goto(item)"
          >
            <component :is="item.icon" :size="20" :stroke-width="2" />
          </button>
        </template>
        <span class="hub-rail-spacer" />
        <button
          type="button"
          class="hub-rail-btn dim"
          :aria-label="shell.theme === 'light' ? '切换深色' : '切换浅色'"
          @click="shell.toggleTheme()"
        >
          <Moon v-if="shell.theme === 'light'" :size="20" :stroke-width="2" />
          <SunMedium v-else :size="20" :stroke-width="2" />
        </button>
      </aside>

      <!-- 页面内容 -->
      <main class="hub-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped src="../styles/hub-shell.css" />
