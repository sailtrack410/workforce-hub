<script setup lang="ts">
/**
 * HubShell：应用壳 = 顶部导航 64（弹性滑块胶囊）+ 左侧图标栏 64（次级导航，由页面注入）。
 * 画布规范：圆角 20 卡片式顶导、蓝色渐变图标栏。
 * 次级导航约定：页面通过 <template #rail> 注入自己的分区导航，未注入时回退到页面级默认分区。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  AtSign, Bell, CalendarDays, ChevronDown, Clock, FileUp, Inbox, LayoutDashboard,
  Mails, MessageSquare, Moon, Search, Settings, Sparkles, SunMedium, UserRound, UsersRound,
} from 'lucide-vue-next';
import { useSessionStore } from '@/stores/session';
import { notificationApi } from '@/api';

const session = useSessionStore();
const route = useRoute();
const router = useRouter();
const theme = ref<'light' | 'dark'>('light');

/** 通知中心未读数（#10 联调）：铃铛数字徽标，点击全部已读 */
const unreadCount = ref(0);
async function loadUnread() {
  try {
    unreadCount.value = await notificationApi.unreadCount();
  } catch { /* 未读数失败不阻断 */ }
}
async function onBell() {
  if (unreadCount.value > 0) {
    try {
      await notificationApi.markAllRead();
      unreadCount.value = 0;
    } catch { /* 忽略 */ }
  }
  if (route.path !== '/workbench') void router.push('/workbench');
}

onMounted(async () => {
  document.body.dataset.theme = theme.value;
  if (!session.initialized) {
    await session.loadCurrentUser();
    if (!session.isAuthenticated) void router.replace({ name: 'login' });
    else void session.loadOrgProfile();
  }
  void loadUnread();
});

/** 顶部主导航：滑块胶囊项 */
const primaryNav = [
  { id: 'workbench', label: '工作台', path: '/workbench' },
  { id: 'messages', label: '消息', path: '/messages' },
  { id: 'approval', label: '审批', path: '/approvals' },
  { id: 'attendance', label: '考勤', path: '/attendance' },
  { id: 'agent', label: 'Agent', path: '/agent' },
];

const activeId = computed(() => {
  const p = route.path;
  if (p.startsWith('/messages')) return 'messages';
  if (p.startsWith('/approvals')) return 'approval';
  if (p.startsWith('/workbench')) return 'workbench';
  if (p.startsWith('/attendance')) return 'attendance';
  if (p.startsWith('/agent')) return 'agent';
  return '';
});

const navEl = ref<HTMLElement | null>(null);
/** 滑块位置：基于激活按钮的 offsetLeft/offsetWidth 计算，切页与点击时平滑跟随 */
const thumb = ref({ left: 0, width: 0, ready: false });

function updateThumb() {
  const nav = navEl.value;
  if (!nav) return;
  const idx = primaryNav.findIndex((n) => n.id === activeId.value);
  if (idx < 0) {
    thumb.value = { ...thumb.value, ready: false };
    return;
  }
  // 注意：nav.children 含滑块自身，必须只取按钮
  const btn = nav.querySelectorAll<HTMLElement>('.hw-nav-item')[idx];
  if (!btn) return;
  thumb.value = { left: btn.offsetLeft, width: btn.offsetWidth, ready: true };
}

watch(activeId, () => requestAnimationFrame(updateThumb));
watch(navEl, () => requestAnimationFrame(updateThumb));
window.addEventListener('resize', updateThumb);

/** 各页面次级导航定义：route 前缀 → 分区列表（图标语义唯一，不重复） */
const secondaryByPage: Record<string, Array<{ id: string; label: string; icon: unknown }>> = {
  '/messages': [
    { id: 'all', label: '全部会话', icon: MessageSquare },
    { id: '@我', label: '@我的消息', icon: AtSign },
    { id: '单聊', label: '单聊', icon: UserRound },
    { id: '群组', label: '群组', icon: UsersRound },
    { id: 'AI', label: 'AI 助手', icon: Sparkles },
  ],
  '/workbench': [
    { id: 'overview', label: '概览', icon: LayoutDashboard },
    { id: 'attendance', label: '考勤速览', icon: Clock },
    { id: 'leave', label: '假期余额', icon: CalendarDays },
  ],
  '/approvals': [
    { id: 'todo', label: '待我审批', icon: Inbox },
    { id: 'mine', label: '我发起的', icon: FileUp },
    { id: 'cc', label: '抄送我', icon: Mails },
  ],
};

const secondaryItems = computed(() => {
  const prefix = Object.keys(secondaryByPage).find((k) => route.path.startsWith(k));
  return prefix ? secondaryByPage[prefix] : [];
});

/** 页面通过 defineExpose 或 provide 接收次级导航选择；这里用 route query 兜底 */
const secondaryActive = computed(() => String(route.query.section || secondaryItems.value[0]?.id || ''));

function onSecondary(id: string) {
  void router.push({ path: route.path, query: { ...route.query, section: id } });
}

const displayName = computed(() => session.displayName || '同事');
const avatarChar = computed(() => displayName.value.slice(0, 1));

function goto(path?: string) {
  if (path) void router.push(path);
}
function toggleTheme() {
  theme.value = theme.value === 'light' ? 'dark' : 'light';
  document.body.dataset.theme = theme.value;
}
</script>

<template>
  <div class="hw-shell">
    <header class="hw-topbar">
      <div class="hw-brand" @click="goto('/workbench')">
        <span class="hw-brand-avatar">W</span>
        <strong>Workforce Hub</strong>
      </div>
      <nav ref="navEl" class="hw-nav" aria-label="主功能">
        <span
          v-if="thumb.ready"
          class="hw-nav-thumb"
          :style="{ transform: `translateX(${thumb.left}px)`, width: `${thumb.width}px` }"
        />
        <button
          v-for="item in primaryNav" :key="item.id" type="button"
          class="hw-nav-item" :class="{ active: activeId === item.id }"
          @click="goto(item.path)"
        >{{ item.label }}</button>
      </nav>
      <div class="hw-topbar-actions">
        <div class="hw-search">
          <Search :size="16" :stroke-width="2" />
          <input type="text" placeholder="搜索同事、申请、知识…" disabled />
        </div>
        <button type="button" class="hw-icon-btn" aria-label="通知" @click="onBell">
          <Bell :size="18" :stroke-width="2" />
          <span v-if="unreadCount > 0" class="hw-badge-num">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
        </button>
        <button type="button" class="hw-icon-btn" aria-label="设置">
          <Settings :size="18" :stroke-width="2" />
        </button>
        <button type="button" class="hw-user" :aria-label="displayName">
          <span class="hw-user-avatar">{{ avatarChar }}</span>
          <ChevronDown :size="14" :stroke-width="2" />
        </button>
      </div>
    </header>

    <div class="hw-body">
      <!-- 左栏：次级导航（页面分区），非顶部复刻 -->
      <aside class="hw-rail" aria-label="次级导航">
        <template v-if="secondaryItems.length">
          <button
            v-for="item in secondaryItems" :key="item.id" type="button"
            class="hw-rail-btn" :class="{ active: secondaryActive === item.id }"
            :data-label="item.label" :aria-label="item.label"
            @click="onSecondary(item.id)"
          >
            <component :is="item.icon" :size="20" :stroke-width="2" />
          </button>
        </template>
        <span class="hw-rail-spacer" />
        <button
          type="button" class="hw-rail-btn dim"
          :data-label="theme === 'light' ? '深色模式' : '浅色模式'"
          aria-label="切换主题" @click="toggleTheme"
        >
          <Moon v-if="theme === 'light'" :size="20" :stroke-width="2" />
          <SunMedium v-else :size="20" :stroke-width="2" />
        </button>
      </aside>
      <main class="hw-content">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.hw-shell {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100vh;
  padding: 16px 20px 20px;
  background: var(--hw-canvas);
}

/* 顶部导航 */
.hw-topbar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  height: 64px;
  padding: 0 16px 0 24px;
  border-radius: 20px;
  background: var(--hw-card);
  flex: none;
}
.hw-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  justify-self: start;
}
.hw-brand-avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #4fa1ff, #124da3);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}
.hw-brand strong { font-size: 15px; font-weight: 700; }

/* 弹性滑块导航 */
.hw-nav {
  position: relative;
  display: flex;
  gap: 6px;
  justify-self: center;
}
.hw-nav-thumb {
  position: absolute;
  top: 0;
  left: 0;
  height: 40px;
  border-radius: 999px;
  background: var(--hw-pill-dark);
  transition: transform 0.35s cubic-bezier(0.34, 1.3, 0.5, 1), width 0.35s cubic-bezier(0.34, 1.3, 0.5, 1);
  will-change: transform, width;
}
.hw-nav-item {
  position: relative;
  z-index: 1;
  height: 40px;
  padding: 0 20px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--hw-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: color 0.25s var(--hw-ease);
}
.hw-nav-item:hover:not(.active) { color: var(--hw-ink); }
.hw-nav-item.active {
  color: #fff;
  font-weight: 500;
}
.hw-topbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-self: end;
}
.hw-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 224px;
  height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--hw-chip);
  color: var(--hw-muted);
}
.hw-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  font-size: 13px;
  color: var(--hw-ink);
}
.hw-search input::placeholder { color: var(--hw-muted); }
.hw-icon-btn {
  position: relative;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 999px;
  background: var(--hw-chip);
  color: var(--hw-secondary);
  cursor: pointer;
  transition: background 0.2s;
}
.hw-icon-btn:hover { background: var(--hw-track); }
.hw-badge-dot {
  position: absolute;
  top: 9px;
  right: 10px;
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--hw-red);
}
.hw-badge-num {
  position: absolute;
  top: 4px;
  right: 4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--hw-red);
  color: #fff;
  font-size: 10px;
  line-height: 16px;
  text-align: center;
}
.hw-user {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 36px;
  padding: 0 4px 0 0;
  border: 0;
  background: transparent;
  color: var(--hw-secondary);
  cursor: pointer;
}
.hw-user-avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: var(--hw-sky);
  color: var(--hw-primary);
  font-size: 14px;
  font-weight: 500;
}

/* 主体：左栏=次级导航 */
.hw-body {
  display: flex;
  gap: 16px;
  flex: 1;
  min-height: 0;
}
.hw-rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 64px;
  padding: 16px 10px;
  border-radius: 20px;
  background: linear-gradient(180deg, var(--hw-primary) 0%, var(--hw-primary-deep) 100%);
  flex: none;
}
.hw-rail-btn {
  position: relative;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: rgba(255, 255, 255, 0.75);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.hw-rail-btn:hover { background: rgba(255, 255, 255, 0.14); color: #fff; }
.hw-rail-btn.active { background: rgba(255, 255, 255, 0.22); color: #fff; }
.hw-rail-btn.dim { color: rgba(255, 255, 255, 0.6); }
/* 悬停文字提示：自定义 tooltip（原生 title 延迟久且无样式） */
.hw-rail-btn[data-label]::after {
  content: attr(data-label);
  position: absolute;
  left: calc(100% + 10px);
  top: 50%;
  transform: translateY(-50%) translateX(-4px);
  padding: 5px 10px;
  border-radius: 8px;
  background: var(--hw-pill-dark);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.18s var(--hw-ease), transform 0.18s var(--hw-ease);
  z-index: 20;
}
.hw-rail-btn[data-label]:hover::after {
  opacity: 1;
  transform: translateY(-50%) translateX(0);
  transition-delay: 0.25s;
}
.hw-rail-spacer { flex: 1; }
.hw-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
</style>
