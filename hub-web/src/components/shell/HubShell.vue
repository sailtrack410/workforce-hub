<script setup lang="ts">
/** 桌面应用壳：56px 顶导 + 可拖拽调宽的一级导航，页面负责自己的工作区。 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  Bell, BookOpen, BookUser, CalendarDays, CheckSquare, ChevronDown,
  ChevronRight, Cloud, LayoutDashboard, LogOut, MessageSquare, Moon,
  Search, Settings, Stamp, SunMedium, UserRound,
} from 'lucide-vue-next';
import HubAvatar from '@/components/base/HubAvatar.vue';
import HubToast from '@/components/base/HubToast.vue';
import { useSessionStore } from '@/stores/session';
import { useImStore } from '@/stores/im';
import { useToast } from '@/composables/useToast';
import { NAV_MAX_WIDTH, NAV_MIN_WIDTH, navigationWidth } from '@/utils/imLayout';

const session = useSessionStore();
const im = useImStore();
const route = useRoute();
const router = useRouter();
const { message: toastMessage, show: showToast } = useToast();
const accountOpen = ref(false);
const navWidth = ref(180);
const navResizing = ref(false);
const theme = ref<'light' | 'dark'>('light');
const searchKeyword = ref('');
let resizeCleanup: (() => void) | null = null;

const primaryNav = [
  { id: 'messages', label: '消息', path: '/messages', icon: MessageSquare },
  { id: 'workbench', label: '工作台', path: '/workbench', icon: LayoutDashboard },
  { id: 'tasks', label: '任务', path: '/tasks', icon: CheckSquare },
  { id: 'todos', label: '待办', path: '/todos', icon: Bell },
  { id: 'approvals', label: '审批', path: '/approvals', icon: Stamp },
  { id: 'attendance', label: '考勤', path: '/attendance', icon: CalendarDays },
  { id: 'knowledge', label: '知识库', path: '/knowledge', icon: BookOpen },
  { id: 'drive', label: '云空间', path: '/drive', icon: Cloud },
  { id: 'contacts', label: '通讯录', path: '/contacts', icon: BookUser },
];
const activeId = computed(() => primaryNav.find((item) => route.path.startsWith(item.path))?.id ?? '');
const unreadTotal = computed(() => im.conversations.reduce((sum, item) => sum + (item.mute ? 0 : item.unreadCount), 0));
const displayName = computed(() => session.displayName || '同事');

function readPreference(key: string) {
  try { return localStorage.getItem(key); } catch { return null; }
}
function saveNavWidth() {
  try { localStorage.setItem('hw-nav-width', String(navWidth.value)); } catch { /* 隐私模式不阻断 */ }
}
function startNavResize(event: PointerEvent) {
  if (event.button !== 0) return;
  event.preventDefault();
  const startX = event.clientX;
  const startWidth = navWidth.value;
  const available = window.innerWidth - 680;
  navResizing.value = true;
  const onMove = (move: PointerEvent) => {
    navWidth.value = navigationWidth(startWidth, move.clientX - startX, available);
  };
  const onUp = () => {
    navResizing.value = false;
    saveNavWidth();
    resizeCleanup?.();
  };
  resizeCleanup = () => {
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', onUp);
    resizeCleanup = null;
  };
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', onUp, { once: true });
}
function resizeNavByKeyboard(delta: number) {
  navWidth.value = navigationWidth(navWidth.value, delta, window.innerWidth - 680);
  saveNavWidth();
}
function toggleTheme() {
  theme.value = theme.value === 'light' ? 'dark' : 'light';
  document.body.dataset.theme = theme.value;
  try { localStorage.setItem('hw-theme', theme.value); } catch { /* 隐私模式不阻断 */ }
}
function goto(path: string) {
  accountOpen.value = false;
  // 返回消息模块时恢复上次停留会话，而不是无参路由触发首项选择。
  if (path === '/messages' && im.activeConversationId) {
    const target = `/messages/${im.activeConversationId}`;
    if (route.path !== target) void router.push(target);
    return;
  }
  if (route.path !== path) void router.push(path);
}
function globalSearch() {
  const keyword = searchKeyword.value.trim();
  if (keyword) void router.push({ path: '/messages', query: { keyword } });
}
function comingSoon(label: string) {
  accountOpen.value = false;
  showToast(`${label}正在按画布实现中`);
}
const loggingOut = ref(false);
async function logout() {
  if (loggingOut.value) return;
  loggingOut.value = true;
  accountOpen.value = false;
  await session.logout();
  await router.replace({ name: 'login' });
}
function onDocumentClick(event: MouseEvent) {
  if (!(event.target as Element | null)?.closest('.hw-account-wrap')) accountOpen.value = false;
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') accountOpen.value = false;
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault();
    document.querySelector<HTMLInputElement>('.hw-global-search input')?.focus();
  }
}

onMounted(async () => {
  if (loggingOut.value) return;
  const savedWidth = Number(readPreference('hw-nav-width'));
  if (savedWidth >= NAV_MIN_WIDTH && savedWidth <= NAV_MAX_WIDTH) navWidth.value = savedWidth;
  theme.value = readPreference('hw-theme') === 'dark' ? 'dark' : 'light';
  document.body.dataset.theme = theme.value;
  document.addEventListener('click', onDocumentClick);
  document.addEventListener('keydown', onKeydown);
  if (!session.initialized) await session.loadCurrentUser();
  if (!session.isAuthenticated) {
    void router.replace({ name: 'login' });
    return;
  }
  void session.loadOrgProfile();
  void im.loadConversations().catch(() => undefined);
});
onBeforeUnmount(() => {
  resizeCleanup?.();
  document.removeEventListener('click', onDocumentClick);
  document.removeEventListener('keydown', onKeydown);
});
</script>

<template>
  <div class="hw-shell">
    <header class="hw-topbar">
      <button type="button" class="hw-brand" aria-label="返回工作台" @click="goto('/workbench')">
        <span class="hw-brand-mark">W</span>
        <strong>Workforce Hub</strong>
      </button>
      <form class="hw-global-search" role="search" @submit.prevent="globalSearch">
        <Search :size="17" :stroke-width="2" />
        <input v-model="searchKeyword" type="search" aria-label="全局搜索" placeholder="搜索同事、申请、知识…" />
        <kbd>⌘ K</kbd>
      </form>
    </header>

    <div class="hw-body" :class="{ 'nav-resizing': navResizing }" :style="{ '--hw-nav-width': `${navWidth}px` }">
      <aside class="hw-primary-nav" :class="{ compact: navWidth === NAV_MIN_WIDTH }" aria-label="一级导航">
        <nav class="hw-nav-list">
          <button
            v-for="item in primaryNav" :key="item.id" type="button"
            class="hw-nav-item" :class="{ active: activeId === item.id }"
            :aria-label="item.label" :title="item.label"
            :aria-current="activeId === item.id ? 'page' : undefined"
            @click="goto(item.path)"
          >
            <component :is="item.icon" :size="20" :stroke-width="2" />
            <span class="hw-nav-label">{{ item.label }}</span>
            <em v-if="item.id === 'messages' && unreadTotal" class="hw-nav-badge">{{ unreadTotal > 99 ? '99+' : unreadTotal }}</em>
          </button>
        </nav>

        <div class="hw-nav-bottom">
          <div class="hw-account-wrap">
            <Transition name="hw-menu">
              <div v-if="accountOpen" class="hw-account-menu">
                <button type="button" @click="goto('/profile')"><UserRound :size="17" />个人中心<ChevronRight :size="14" /></button>
                <button type="button" @click="goto('/settings')"><Settings :size="17" />设置<ChevronRight :size="14" /></button>
                <button type="button" @click="toggleTheme">
                  <SunMedium v-if="theme === 'dark'" :size="17" /><Moon v-else :size="17" />
                  {{ theme === 'light' ? '深色模式' : '浅色模式' }}<small>{{ theme === 'light' ? '关闭' : '开启' }}</small>
                </button>
                <button type="button" class="danger" @click="logout"><LogOut :size="17" />退出登录</button>
              </div>
            </Transition>
            <button
              type="button" class="hw-account" :aria-label="`${displayName}的账户菜单`"
              :aria-expanded="accountOpen" @click.stop="accountOpen = !accountOpen"
            >
              <HubAvatar :label="displayName" size="sm" />
              <span class="hw-nav-label">{{ displayName }}</span>
              <ChevronDown class="hw-nav-label" :size="14" />
            </button>
          </div>
        </div>
      </aside>
      <div
        class="hw-nav-resizer" role="separator" aria-label="调整一级导航宽度"
        aria-orientation="vertical" :aria-valuenow="navWidth" :aria-valuemin="NAV_MIN_WIDTH" :aria-valuemax="NAV_MAX_WIDTH"
        tabindex="0" @pointerdown="startNavResize"
        @keydown.left.prevent="resizeNavByKeyboard(-4)" @keydown.right.prevent="resizeNavByKeyboard(4)"
      />
      <main class="hw-content"><slot /></main>
    </div>
    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped>
.hw-shell { display: flex; flex-direction: column; height: 100dvh; min-width: 960px; background: var(--hw-canvas); overflow: hidden; }
.hw-topbar { display: flex; align-items: center; justify-content: space-between; height: 56px; padding: 0 20px 0 calc(20px + var(--hw-desktop-inset)); border-bottom: 1px solid var(--hw-border); background: var(--hw-card); flex: none; app-region: drag; -webkit-app-region: drag; }
.hw-topbar button, .hw-topbar input, .hw-topbar form { app-region: no-drag; -webkit-app-region: no-drag; }
.hw-brand { display: flex; align-items: center; gap: 12px; border: 0; background: transparent; color: var(--hw-ink); cursor: pointer; }
.hw-brand-mark { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 6px; background: var(--hw-primary); color: var(--hw-on-primary); font-size: 14px; font-weight: 600; }
.hw-brand strong { font-size: 16px; font-weight: 600; white-space: nowrap; }
.hw-global-search { display: flex; align-items: center; gap: 10px; width: 400px; height: 36px; padding: 0 12px; border: 1px solid transparent; border-radius: 6px; background: var(--hw-inner); color: var(--hw-muted); transition: border-color var(--hw-duration-fast) var(--hw-motion-enter), background var(--hw-duration-fast); }
.hw-global-search:focus-within { border-color: var(--hw-sky-strong); background: var(--hw-card); }
.hw-global-search input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--hw-ink); font-size: 12px; appearance: none; -webkit-appearance: none; }
.hw-global-search input:focus-visible { outline: none; }
.hw-global-search input::-webkit-search-cancel-button, .hw-global-search input::-webkit-search-decoration { display: none; }
.hw-global-search input::placeholder { color: var(--hw-muted); }
.hw-global-search kbd { font: 11px Inter, sans-serif; color: var(--hw-muted); white-space: nowrap; }
.hw-body { display: flex; flex: 1; min-height: 0; position: relative; }
.hw-primary-nav { display: flex; flex-direction: column; justify-content: space-between; width: var(--hw-nav-width, 180px); padding: 16px 8px 12px; background: var(--hw-nav-bg); color: var(--hw-nav-text); flex: none; overflow: visible; z-index: 10; }
.hw-nav-resizer { position: absolute; top: 0; bottom: 0; left: var(--hw-nav-width, 180px); width: 8px; margin-left: -4px; cursor: col-resize; touch-action: none; z-index: 20; }
.hw-nav-resizer::after { content: ''; position: absolute; inset: 0 3px; background: transparent; transition: background var(--hw-duration-fast); }
.hw-nav-resizer:hover::after, .hw-nav-resizer:focus-visible::after, .nav-resizing .hw-nav-resizer::after { background: var(--hw-primary); }
.nav-resizing { cursor: col-resize; user-select: none; }
.hw-nav-list { display: flex; flex-direction: column; gap: 4px; }
.hw-nav-item, .hw-account { position: relative; display: flex; align-items: center; gap: 8px; width: 100%; height: 40px; padding: 0 16px; border: 0; border-radius: 6px; background: transparent; color: inherit; font-size: 14px; text-align: left; cursor: pointer; white-space: nowrap; transition: background var(--hw-duration-fast), color var(--hw-duration-fast); }
.hw-nav-item:hover, .hw-account:hover { background: var(--hw-nav-hover); color: var(--hw-on-primary); }
.hw-nav-item.active { background: var(--hw-nav-active); color: var(--hw-on-primary); }
.hw-nav-item > svg, .hw-account > :first-child { flex: none; }
.hw-nav-label { overflow: hidden; text-overflow: ellipsis; }
.hw-nav-item .hw-nav-label, .hw-account .hw-nav-label:first-of-type { flex: 1; }
.hw-primary-nav.compact { padding-inline: 6px; }
.hw-primary-nav.compact .hw-nav-item, .hw-primary-nav.compact .hw-account { justify-content: center; padding: 0; }
.hw-primary-nav.compact .hw-nav-label { display: none; }
.hw-primary-nav.compact .hw-nav-badge { top: 3px; right: 3px; min-width: 8px; width: 8px; height: 8px; padding: 0; color: transparent; overflow: hidden; }
.hw-nav-badge { position: absolute; right: 12px; display: grid; place-items: center; min-width: 20px; height: 20px; padding: 0 5px; border-radius: 10px; background: var(--hw-red); color: var(--hw-on-primary); font: 500 11px/20px Inter, sans-serif; font-style: normal; }
.hw-nav-bottom { display: flex; flex-direction: column; gap: 8px; }
.hw-account-wrap { position: relative; }
.hw-account { height: 44px; padding: 0 12px; border: 1px solid var(--hw-nav-active); background: var(--hw-nav-hover); }
.hw-account-menu { position: absolute; left: 0; bottom: calc(100% + 10px); width: 188px; padding: 6px; border: 1px solid var(--hw-border); border-radius: 8px; background: var(--hw-card); box-shadow: var(--hw-shadow-menu); color: var(--hw-secondary); z-index: 30; }
.hw-account-menu button { display: flex; align-items: center; gap: 10px; width: 100%; height: 40px; padding: 0 10px; border: 0; border-radius: 6px; background: transparent; color: inherit; font-size: 13px; cursor: pointer; text-align: left; }
.hw-account-menu button:hover { background: var(--hw-chip); color: var(--hw-ink); }
.hw-account-menu button > :last-child:not(:first-child) { margin-left: auto; }
.hw-account-menu small { margin-left: auto; color: var(--hw-muted); }
.hw-account-menu .danger { color: var(--hw-red); border-top: 1px solid var(--hw-border); border-radius: 0 0 6px 6px; margin-top: 4px; }
.hw-menu-enter-active { transition: opacity 150ms var(--hw-motion-enter), transform 150ms var(--hw-motion-enter); }
.hw-menu-leave-active { transition: opacity 100ms var(--hw-motion-exit), transform 100ms var(--hw-motion-exit); }
.hw-menu-enter-from, .hw-menu-leave-to { opacity: 0; transform: translateY(4px); }
.hw-content { display: flex; flex: 1; min-width: 0; min-height: 0; overflow: hidden; }
button:focus-visible, input:focus-visible { outline: 2px solid var(--hw-focus); outline-offset: 2px; }
@media (max-width: 960px) { .hw-global-search { width: 280px; } .hw-global-search kbd { display: none; } }
@media (prefers-reduced-motion: reduce) { .hw-menu-enter-active, .hw-menu-leave-active { transition: none; } }
</style>
