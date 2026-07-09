<script setup lang="ts">
import { computed } from 'vue';
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
  LogOut,
  MapPin,
  PlaneTakeoff,
  ShieldCheck,
  Settings,
  Tag,
  TreePine,
  Users,
} from 'lucide-vue-next';
import { useSessionStore } from '@/stores/session';
import AdminToast from '@/components/AdminToast.vue';
import AdminIconAvatar from '@/components/AdminIconAvatar.vue';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const navItems = [
  { path: '/dashboard', label: '概览', icon: BarChart3 },
  { path: '/agent-tasks', label: 'AI 任务', icon: Bot, permission: 'ADMIN_AGENT_TASK_MANAGE' },
  { path: '/agent-config', label: 'AI 助手', icon: LayoutGrid, permission: 'ADMIN_AGENT_MANAGE' },
  { path: '/workflow', label: '审批治理', icon: GitPullRequestArrow, permission: 'ADMIN_WORKFLOW_MANAGE' },
  { path: '/leave', label: '假期余额', icon: PlaneTakeoff, permission: 'ADMIN_LEAVE_MANAGE' },
  { path: '/leave-types', label: '假种管理', icon: Tag, permission: 'ADMIN_LEAVE_MANAGE' },
  { path: '/roles', label: '角色权限', icon: ShieldCheck, permission: 'ADMIN_RBAC_MANAGE' },
  { path: '/attendance', label: '考勤管理', icon: CalendarCheck, permission: 'ADMIN_ATTENDANCE_MANAGE' },
  { path: '/departments', label: '部门管理', icon: TreePine, permission: 'ADMIN_ORG_MANAGE' },
  { path: '/positions', label: '岗位管理', icon: MapPin, permission: 'ADMIN_ORG_MANAGE' },
  { path: '/users', label: '人员管理', icon: Users, permission: 'ADMIN_ORG_MANAGE' },
  { path: '/files', label: '文件清理', icon: FileArchive, permission: 'ADMIN_FILE_MANAGE' },
  { path: '/ragflow', label: '知识库', icon: LibraryBig, permission: 'ADMIN_RAGFLOW_MANAGE' },
  { path: '/ai-models', label: 'AI 基座', icon: Cpu, permission: 'ADMIN_AGENT_MANAGE' },
  { path: '/system', label: '系统记录', icon: ShieldCheck },
  { path: '/settings', label: '设置', icon: Settings, permission: 'ADMIN_NOTIFICATION_CHANNEL_MANAGE' },
];

const visibleNavItems = computed(() => navItems.filter((item) => session.hasPermission(item.permission)));
const activeTitle = computed(() => navItems.find((item) => route.path.startsWith(item.path))?.label ?? '后台运营');

async function logout() {
  await session.logout();
  await router.replace('/login');
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="brand-block">
        <AdminIconAvatar label="企业" kind="org" size="md" />
        <div>
          <strong>Workforce Hub</strong>
          <span>Admin</span>
        </div>
      </div>

      <nav class="admin-nav" aria-label="后台运营导航">
        <RouterLink
          v-for="item in visibleNavItems"
          :key="item.path"
          :to="item.path"
          class="admin-nav-item"
        >
          <component :is="item.icon" :size="18" />
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </aside>

    <section class="admin-main">
      <header class="admin-topbar">
        <h1>{{ activeTitle }}</h1>
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
  </div>
  <AdminToast />
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
}

.brand-block {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-xs) var(--space-sm) var(--space-lg);
}

.brand-block div { display: grid; gap: 3px; }

.brand-block span {
  color: var(--muted);
  font-size: var(--text-xs);
}

.admin-nav { display: grid; gap: var(--space-xs); }

.admin-nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  min-height: 38px;
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

.admin-user {
  display: inline-flex;
  align-items: center;
  gap: var(--space-md);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

@media (max-width: 980px) {
  .admin-shell { grid-template-columns: 1fr; }
  .admin-sidebar {
    position: sticky;
    top: 0;
    height: auto;
    z-index: 100;
    padding: var(--space-sm) var(--space-base);
    border-right: none;
    border-bottom: 1px solid var(--line);
  }
  .brand-block { padding-bottom: var(--space-sm); }
  .brand-block div { display: none; }
  .admin-nav {
    display: flex;
    overflow-x: auto;
    gap: var(--space-xs);
    -webkit-overflow-scrolling: touch;
  }
  .admin-nav-item {
    white-space: nowrap;
    flex-shrink: 0;
    min-height: 40px;
    padding: 0 var(--space-md);
    font-size: var(--text-sm);
  }
  .admin-nav-item span { display: none; }
}

@media (max-width: 640px) {
  .admin-main { padding: var(--space-base); }
  .admin-topbar { align-items: stretch; flex-direction: column; }
  .admin-nav-item span { display: inline; }
  .admin-nav { flex-wrap: wrap; }
}
</style>
