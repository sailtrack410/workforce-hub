<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  BarChart3,
  Bot,
  CalendarCheck,
  FileArchive,
  Cpu,
  LibraryBig,
  GitPullRequestArrow,
  LayoutGrid,
  ListTree,
  LogOut,
  MapPin,
  Menu,
  MessageSquareWarning,
  PlaneTakeoff,
  ShieldCheck,
  Settings,
  Tag,
  TreePine,
  Users,
  X,
} from 'lucide-vue-next';
import { useSessionStore } from '@/stores/session';
import AdminToast from '@/components/AdminToast.vue';
import AdminIconAvatar from '@/components/AdminIconAvatar.vue';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const mobileMenuOpen = ref(false);

const navGroups = [
  {
    label: '工作台',
    items: [
      { path: '/dashboard', label: '运营概览', icon: BarChart3 },
    ],
  },
  {
    label: '智能能力',
    items: [
      { path: '/agent-tasks', label: 'AI 任务', icon: Bot, permission: 'ADMIN_AGENT_TASK_MANAGE' },
      { path: '/agent-config', label: 'AI 助手', icon: LayoutGrid, permission: 'ADMIN_AGENT_MANAGE' },
      { path: '/ragflow', label: '知识库', icon: LibraryBig, permission: 'ADMIN_RAGFLOW_MANAGE' },
      { path: '/ai-models', label: 'AI 基座', icon: Cpu, permission: 'ADMIN_AGENT_MANAGE' },
    ],
  },
  {
    label: '人事业务',
    items: [
      { path: '/workflow', label: '审批治理', icon: GitPullRequestArrow, permission: 'ADMIN_WORKFLOW_MANAGE' },
      { path: '/attendance', label: '考勤管理', icon: CalendarCheck, permission: 'ADMIN_ATTENDANCE_MANAGE' },
      { path: '/leave', label: '假期余额', icon: PlaneTakeoff, permission: 'ADMIN_LEAVE_MANAGE' },
      { path: '/leave-types', label: '假种管理', icon: Tag, permission: 'ADMIN_LEAVE_MANAGE' },
      { path: '/departments', label: '部门管理', icon: TreePine, permission: 'ADMIN_ORG_MANAGE' },
      { path: '/positions', label: '岗位管理', icon: MapPin, permission: 'ADMIN_ORG_MANAGE' },
      { path: '/users', label: '人员管理', icon: Users, permission: 'ADMIN_ORG_MANAGE' },
    ],
  },
  {
    label: '系统治理',
    items: [
      { path: '/roles', label: '角色权限', icon: ShieldCheck, permission: 'ADMIN_RBAC_MANAGE' },
      { path: '/permissions', label: '权限点', icon: ShieldCheck, permission: 'ADMIN_RBAC_MANAGE' },
      { path: '/files', label: '文件空间', icon: FileArchive, permission: 'ADMIN_FILE_MANAGE' },
      { path: '/message-delivery', label: '消息投递', icon: MessageSquareWarning },
      { path: '/audit-logs', label: '审计日志', icon: ShieldCheck },
      { path: '/dicts', label: '数据字典', icon: ListTree, permission: 'ADMIN_DICT_MANAGE' },
      { path: '/settings', label: '设置', icon: Settings, permission: 'ADMIN_NOTIFICATION_CHANNEL_MANAGE' },
    ],
  },
];

const navItems = navGroups.flatMap((group) => group.items);
const visibleNavGroups = computed(() => navGroups.map((group) => ({
  ...group,
  items: group.items.filter((item) => session.hasPermission(item.permission)),
})).filter((group) => group.items.length));
const activeTitle = computed(() => navItems.find((item) => (
  route.path === item.path || route.path.startsWith(`${item.path}/`)
))?.label ?? '后台运营');

watch(() => route.fullPath, () => { mobileMenuOpen.value = false; });

async function logout() {
  await session.logout();
  await router.replace('/login');
}
</script>

<template>
  <div class="admin-shell">
    <aside id="admin-navigation" :class="['admin-sidebar', { open: mobileMenuOpen }]">
      <div class="brand-block">
        <AdminIconAvatar label="企业" kind="org" size="md" />
        <div>
          <strong>Workforce Hub</strong>
          <span>企业管理控制台</span>
        </div>
        <button class="icon-button mobile-close" type="button" aria-label="关闭导航菜单" @click="mobileMenuOpen = false">
          <X :size="20" />
        </button>
      </div>

      <nav class="admin-nav" aria-label="后台运营导航">
        <section v-for="group in visibleNavGroups" :key="group.label" class="admin-nav-group">
          <h2>{{ group.label }}</h2>
          <RouterLink
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="admin-nav-item"
          >
            <component :is="item.icon" :size="18" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </section>
      </nav>
    </aside>

    <section class="admin-main">
      <header class="admin-topbar">
        <div class="topbar-title">
          <button
            class="icon-button mobile-menu-button"
            type="button"
            aria-label="打开导航菜单"
            aria-controls="admin-navigation"
            :aria-expanded="mobileMenuOpen"
            @click="mobileMenuOpen = true"
          >
            <Menu :size="20" />
          </button>
          <h1>{{ activeTitle }}</h1>
        </div>
        <div class="admin-user">
          <AdminIconAvatar :label="session.displayName || '管理员'" kind="user" size="md" />
          <div class="admin-user-copy">
            <strong>{{ session.displayName || '管理员' }}</strong>
            <span>管理员</span>
          </div>
          <button class="icon-button" title="退出登录" @click="logout">
            <LogOut :size="18" />
          </button>
        </div>
      </header>

      <RouterView />
    </section>
    <button v-if="mobileMenuOpen" class="nav-backdrop" type="button" aria-label="关闭导航菜单" @click="mobileMenuOpen = false" />
  </div>
  <AdminToast />
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  background: var(--surface);
}

.admin-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  z-index: 80;
  padding: 0 var(--space-md) var(--space-lg);
  border-right: 1px solid var(--sidebar-border);
  background: var(--sidebar-bg);
  color: var(--sidebar-text);
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.admin-sidebar::-webkit-scrollbar { width: 0; height: 0; }

.brand-block {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  min-height: var(--topbar-height);
  margin-bottom: var(--space-md);
  padding: 0 var(--space-sm);
  border-bottom: 1px solid var(--sidebar-border);
}

.brand-block div { display: grid; gap: 3px; flex: 1; }

.brand-block strong {
  color: var(--sidebar-brand-text);
  font-size: var(--text-base);
  line-height: 1.2;
}

.brand-block span {
  color: var(--sidebar-muted);
  font-size: var(--text-xs);
}

.brand-block :deep(.admin-icon-avatar) {
  border: 1px solid var(--auth-line);
  box-shadow: var(--shadow-sm);
}

.admin-nav { display: grid; gap: var(--space-md); }

.admin-nav-group { display: grid; gap: var(--space-xs); }

.admin-nav-group h2 {
  margin: 0;
  padding: 0 var(--space-md) var(--space-xs);
  color: var(--sidebar-muted);
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0;
}

.admin-nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 0 var(--space-md);
  border-radius: var(--radius-sm);
  color: var(--sidebar-text);
  text-decoration: none;
  font-size: var(--text-sm);
  font-weight: 600;
  transition: color 150ms ease, background-color 150ms ease;
}

.admin-nav-item:hover {
  background: var(--sidebar-hover);
  color: var(--sidebar-text-active);
}

.admin-nav-item.router-link-active {
  background: var(--sidebar-active);
  color: var(--sidebar-text-active);
  font-weight: 700;
}

.admin-nav-item.router-link-active::before {
  content: '';
  position: absolute;
  top: 8px;
  bottom: 8px;
  left: 0;
  width: 3px;
  border-radius: var(--radius-pill);
  background: var(--auth-accent);
}

.admin-main {
  min-width: 0;
  min-height: 100vh;
  padding: 0 var(--page-gutter) var(--page-gutter);
}

.admin-topbar {
  position: sticky;
  top: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-base);
  min-height: var(--topbar-height);
  margin: 0 calc(var(--page-gutter) * -1) var(--space-base);
  padding: 0 var(--page-gutter);
  border-bottom: 1px solid var(--line);
  background: var(--topbar-bg);
  backdrop-filter: blur(10px);
}

.admin-topbar h1 {
  font-size: var(--text-lg);
  margin: 0;
  letter-spacing: 0;
  line-height: 1.2;
}

.topbar-title { display: flex; align-items: center; gap: var(--space-md); }
.mobile-menu-button,
.mobile-close,
.nav-backdrop { display: none; }

.admin-user {
  display: inline-flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: 44px;
  padding: var(--space-xs);
  padding-left: var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-md);
  background: var(--panel);
  color: var(--muted);
  font-size: var(--text-sm);
}

.admin-user-copy {
  display: grid;
  min-width: 92px;
  gap: 1px;
}

.admin-user-copy strong {
  color: var(--ink);
  font-size: var(--text-sm);
  line-height: 1.2;
}

.admin-user-copy span {
  color: var(--muted);
  font-size: var(--text-xs);
  line-height: 1.2;
}

.admin-user > .icon-button {
  width: 32px;
  min-height: 32px;
  border-color: var(--line);
}

@media (max-width: 980px) {
  .admin-shell { grid-template-columns: minmax(0, 1fr); }
  .admin-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    width: min(84vw, 320px);
    height: 100vh;
    z-index: 120;
    padding: 0 var(--space-md) var(--space-lg);
    border-right: 1px solid var(--sidebar-border);
    transform: translateX(-100%);
    transition: transform 200ms ease;
  }
  .admin-sidebar.open { transform: translateX(0); }
  .mobile-menu-button,
  .mobile-close { display: inline-flex; min-width: 44px; min-height: 44px; }
  .mobile-close {
    border-color: var(--sidebar-border);
    background: var(--sidebar-hover);
    color: var(--sidebar-text-active);
  }
  .nav-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 110;
    width: 100%;
    border: 0;
    background: var(--dialog-overlay-bg);
  }
}

@media (max-width: 640px) {
  .admin-main { padding: 0 var(--space-base) var(--space-base); }
  .admin-topbar {
    min-height: 60px;
    margin: 0 calc(var(--space-base) * -1) var(--space-md);
    padding: 0 var(--space-base);
  }
  .admin-user-copy { display: none; }
  .admin-user { min-height: 40px; padding-left: var(--space-xs); }
}

@media (prefers-reduced-motion: reduce) {
  .admin-sidebar { transition: none; }
}
</style>
