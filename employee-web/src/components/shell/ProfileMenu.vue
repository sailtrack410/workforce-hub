<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ListTodo, LogOut, MoonStar, Settings, SunMedium, UserRound } from 'lucide-vue-next';
import type { PresenceStatus } from '@/api';
import { useSessionStore } from '@/stores/session';
import { useShellStore } from '@/stores/shell';
import AppAvatar from '../ui/AppAvatar.vue';

const emit = defineEmits<{
  (e: 'logout'): void;
}>();

const shell = useShellStore();
const session = useSessionStore();
const router = useRouter();

const open = ref(false);
const rootRef = ref<HTMLElement | null>(null);

const presenceOptions: Array<{ value: PresenceStatus; label: string; tone: string }> = [
  { value: 'ONLINE', label: '在线', tone: 'var(--success-500, #22c55e)' },
  { value: 'BUSY', label: '忙碌', tone: 'var(--danger-500, #ef4444)' },
  { value: 'AWAY', label: '离开', tone: 'var(--warning-500, #f59e0b)' },
  { value: 'DND', label: '免打扰', tone: 'var(--neutral-400, #94a3b8)' },
];

const currentPresenceLabel = computed(() =>
  presenceOptions.find((o) => o.value === shell.presenceStatus)?.label ?? '在线'
);

const themeLabel = computed(() => (shell.theme === 'light' ? '浅色' : '深色'));

function toggle() {
  open.value = !open.value;
}

function close() {
  open.value = false;
}

function onDocClick(e: MouseEvent) {
  if (rootRef.value && !rootRef.value.contains(e.target as Node)) close();
}

function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') close();
}

onMounted(() => {
  document.addEventListener('click', onDocClick, true);
  document.addEventListener('keydown', onEsc);
});

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick, true);
  document.removeEventListener('keydown', onEsc);
});

function setPresence(status: PresenceStatus) {
  shell.setPresenceStatus(status);
}

function goSettings() {
  close();
  shell.setSection('settings');
  shell.selectItem('settings', 'profile');
  router.push('/settings/profile');
}

function goTodos() {
  close();
  shell.setSection('todos');
  router.push('/todos');
}

function onLogout() {
  close();
  emit('logout');
}
</script>

<template>
  <div ref="rootRef" class="profile-menu-root">
    <!-- 触发按钮：仅头像（适配窄导航栏） -->
    <button class="profile-button" type="button" :aria-expanded="open" aria-label="个人菜单" @click.stop="toggle">
      <AppAvatar :label="session.shortName || '我'" :image="shell.avatarUrl" tone="primary" size="lg" status="online" />
    </button>

    <!-- 弹出菜单 -->
    <Transition name="menu-pop">
      <div v-if="open" class="profile-menu" role="menu" @click.stop>
        <!-- 用户信息头部 -->
        <div class="profile-menu-head">
          <AppAvatar :label="session.shortName || '我'" :image="shell.avatarUrl" tone="primary" size="md" status="online" />
          <div class="profile-menu-head-copy">
            <strong>{{ session.displayName || '我' }}</strong>
            <small>{{ [session.departmentName, session.positionName].filter(Boolean).join(' · ') }}</small>
            <span class="profile-menu-presence">
              <i class="presence-dot" :style="{ background: presenceOptions.find(o => o.value === shell.presenceStatus)?.tone }" />
              {{ currentPresenceLabel }}
            </span>
          </div>
        </div>

        <!-- 在线状态四态切换 -->
        <div class="profile-menu-section">
          <div class="profile-menu-section-title">在线状态</div>
          <div class="presence-switch">
            <button
              v-for="opt in presenceOptions"
              :key="opt.value"
              type="button"
              class="presence-option"
              :class="{ active: shell.presenceStatus === opt.value }"
              @click="setPresence(opt.value)"
            >
              <i class="presence-dot" :style="{ background: opt.tone }" />
              {{ opt.label }}
            </button>
          </div>
        </div>

        <!-- 快捷入口 -->
        <div class="profile-menu-section">
          <button type="button" class="profile-menu-item" role="menuitem" @click="goSettings">
            <UserRound :size="16" />
            <span>个人中心</span>
          </button>
          <button type="button" class="profile-menu-item" role="menuitem" @click="goTodos">
            <ListTodo :size="16" />
            <span>我的待办</span>
          </button>
          <button type="button" class="profile-menu-item" role="menuitem" @click="shell.toggleTheme()">
            <component :is="shell.theme === 'light' ? MoonStar : SunMedium" :size="16" />
            <span>主题</span>
            <span class="profile-menu-item-value">{{ themeLabel }}</span>
          </button>
          <button type="button" class="profile-menu-item" role="menuitem" @click="goSettings">
            <Settings :size="16" />
            <span>偏好设置</span>
          </button>
        </div>

        <!-- 退出登录 -->
        <div class="profile-menu-section">
          <button type="button" class="profile-menu-item profile-menu-danger" role="menuitem" @click="onLogout">
            <LogOut :size="16" />
            <span>退出登录</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.profile-menu-root {
  position: relative;
}

/* 触发按钮：仅头像，窄导航栏适配 */
.profile-button {
  display: grid;
  place-items: center;
  width: 100%;
  padding: var(--space-4);
  border: 0;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.profile-button:hover {
  transform: scale(1.05);
  box-shadow: 0 0 0 2px var(--primary-200);
}

.profile-copy {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.profile-copy strong {
  color: var(--text-primary);
  font-size: var(--text-body-sm);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-copy small {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 弹出菜单：从头像向上弹 */
.profile-menu {
  position: absolute;
  left: 0;
  bottom: calc(100% + var(--space-8));
  width: 17rem;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-12, 0.75rem);
  background: var(--surface-panel);
  box-shadow: var(--shadow-lg, 0 12px 32px rgba(15, 23, 42, 0.16));
  overflow: hidden;
  z-index: 50;
}

.menu-pop-enter-active,
.menu-pop-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.menu-pop-enter-from,
.menu-pop-leave-to {
  opacity: 0;
  transform: translateY(6px);
}

/* 用户信息头部 */
.profile-menu-head {
  display: flex;
  align-items: center;
  gap: var(--space-10);
  padding: var(--space-14) var(--space-16);
  border-bottom: 1px solid var(--border-subtle);
  background: var(--surface-panel-muted, var(--surface-accent));
}

.profile-menu-head-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.profile-menu-head-copy strong {
  font-size: var(--text-body-md);
  font-weight: 700;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-menu-head-copy small {
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-menu-presence {
  display: inline-flex;
  align-items: center;
  gap: var(--space-4);
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: var(--text-caption-md);
}

.presence-dot {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 999px;
  flex: 0 0 auto;
}

/* 分组 */
.profile-menu-section {
  padding: var(--space-8);
}

.profile-menu-section + .profile-menu-section {
  border-top: 1px solid var(--border-subtle);
}

.profile-menu-section-title {
  padding: 0 var(--space-8) var(--space-4);
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
  font-weight: 600;
}

/* 状态切换 */
.presence-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-4);
}

.presence-option {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  padding: var(--space-8) var(--space-10);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-8);
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--text-caption-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.presence-option:hover {
  border-color: var(--primary-300);
  background: var(--surface-accent);
}

.presence-option.active {
  border-color: var(--primary-500);
  background: var(--primary-50);
  color: var(--primary-700);
  font-weight: 600;
}

/* 菜单项 */
.profile-menu-item {
  display: flex;
  align-items: center;
  gap: var(--space-10);
  width: 100%;
  padding: var(--space-8) var(--space-10);
  border: 0;
  border-radius: var(--radius-8);
  background: transparent;
  color: var(--text-primary);
  font-size: var(--text-body-sm);
  text-align: left;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.profile-menu-item:hover {
  background: var(--surface-accent);
}

.profile-menu-item svg {
  color: var(--text-tertiary);
  flex: 0 0 auto;
}

.profile-menu-item-value {
  margin-left: auto;
  color: var(--text-tertiary);
  font-size: var(--text-caption-md);
}

.profile-menu-danger {
  color: var(--danger-600, #dc2626);
}

.profile-menu-danger svg {
  color: var(--danger-500, #ef4444);
}

.profile-menu-danger:hover {
  background: var(--danger-50, #fef2f2);
}
</style>
