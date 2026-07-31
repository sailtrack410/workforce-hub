<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  BarChart3,
  Bot,
  Blocks,
  CalendarCheck,
  FileArchive,
  Cpu,
  LibraryBig,
  GitPullRequestArrow,
  LayoutGrid,
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
      { path: '/plugins', label: '插件管理', icon: Blocks, permission: 'ADMIN_PLUGIN_MANAGE' },
      { path: '/message-delivery', label: '消息投递', icon: MessageSquareWarning },
      { path: '/audit-logs', label: '审计日志', icon: ShieldCheck },
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
          <span>Admin</span>
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
          <span>{{ session.displayName }}</span>
          <button class="icon-button" title="退出登录" @click="logout">
            <LogOut :size="18" />
          </button>
        </div>
      </header>

      <RouterView />
    </section>
    <button v-if="mobileMenuOpen" class="nav-backdrop" type="button" aria-label="关闭导航菜单" @click="mobileMenuOpen = false" />
  </div>
</template>

<style scoped>
.admin-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
}

.admin-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  padding: var(--space-base) var(--space-md);
  border-right: 1px solid var(--line);
  background: var(--sidebar-bg);
  color: var(--ink);
  overflow-y: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.admin-sidebar::-webkit-scrollbar { width: 0; height: 0; }

.brand-block {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-xs) var(--space-sm) var(--space-lg);
}

.brand-block div { display: grid; gap: 3px; flex: 1; }

.brand-block span {
  color: var(--muted);
  font-size: var(--text-xs);
}

.admin-nav { display: grid; gap: var(--space-base); }

.admin-nav-group { display: grid; gap: var(--space-xs); }

.admin-nav-group h2 {
  margin: 0;
  padding: 0 var(--space-md) var(--space-xs);
  color: var(--muted-light);
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0.08em;
}

.admin-nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  min-height: 44px;
  padding: 0 var(--space-md);
  border-radius: var(--radius-sm);
  color: var(--muted);
  text-decoration: none;
  font-size: var(--text-sm);
  font-weight: 600;
  transition: all 200ms ease;
}

.admin-nav-item:hover {
  background: var(--sidebar-hover);
  color: var(--green-dark);
}

.admin-nav-item.router-link-active {
  background: var(--sidebar-active);
  color: var(--green-dark);
  font-weight: 700;
}

.admin-main { min-width: 0; padding: var(--space-xl); }

.admin-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-base);
  min-height: 52px;
  margin-bottom: var(--space-base);
}

.admin-topbar h1 {
  font-size: 22px;
  margin: 0;
  letter-spacing: 0;
  line-height: 1.35;
}

.topbar-title { display: flex; align-items: center; gap: var(--space-md); }
.mobile-menu-button,
.mobile-close,
.nav-backdrop { display: none; }

.admin-user {
  display: inline-flex;
  align-items: center;
  gap: var(--space-md);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
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
    padding: var(--space-base);
    border-right: 1px solid var(--line);
    transform: translateX(-100%);
    transition: transform 200ms ease;
  }
  .admin-sidebar.open { transform: translateX(0); }
  .mobile-menu-button,
  .mobile-close { display: inline-flex; min-width: 44px; min-height: 44px; }
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
  .admin-main { padding: var(--space-base); }
  .admin-user > span { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  .admin-sidebar { transition: none; }
}
</style>
