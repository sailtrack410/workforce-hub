<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  BarChart3,
  Bot,
  FileArchive,
  LibraryBig,
  GitPullRequestArrow,
  LayoutGrid,
  LogOut,
  MapPin,
  PlaneTakeoff,
  Settings,
  Tag,
  TreePine,
  Users,
} from 'lucide-vue-next';
import { useSessionStore } from '@/stores/session';
import AdminToast from '@/components/AdminToast.vue';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const navItems = [
  { path: '/dashboard', label: '概览', icon: BarChart3 },
  { path: '/agent-tasks', label: 'Agent 任务', icon: Bot },
  { path: '/agent-config', label: 'Agent 配置', icon: LayoutGrid },
  { path: '/workflow', label: '审批治理', icon: GitPullRequestArrow },
  { path: '/leave', label: '假期余额', icon: PlaneTakeoff },
  { path: '/leave-types', label: '假种管理', icon: Tag },
  { path: '/departments', label: '部门管理', icon: TreePine },
  { path: '/positions', label: '岗位管理', icon: MapPin },
  { path: '/users', label: '人员管理', icon: Users },
  { path: '/files', label: '文件清理', icon: FileArchive },
  { path: '/ragflow', label: 'RAGFlow', icon: LibraryBig },
  { path: '/settings', label: '设置', icon: Settings },
];

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
        <span class="brand-mark">W</span>
        <div>
          <strong>Workforce Hub</strong>
          <span>Admin</span>
        </div>
      </div>

      <nav class="admin-nav" aria-label="后台运营导航">
        <RouterLink
          v-for="item in navItems"
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
          <span class="admin-avatar">{{ session.shortName }}</span>
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
  grid-template-columns: 196px minmax(0, 1fr);
}

.admin-sidebar {
  position: sticky;
  top: 0;
  height: 100vh;
  padding: 20px var(--space-base);
  border-right: 1px solid rgba(0, 80, 180, 0.12);
  background: var(--sidebar-bg);
  color: var(--sidebar-brand-text);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: var(--space-base);
  padding: var(--space-xs) var(--space-sm) 24px;
}

.brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-lg);
  background: var(--brand-mark-bg);
  color: var(--green);
  font-weight: 900;
}

.brand-block div { display: grid; gap: 3px; }

.brand-block span:not(.brand-mark) {
  color: var(--muted-light);
  font-size: var(--text-xs);
}

.admin-nav { display: grid; gap: var(--space-xs); }

.admin-nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  min-height: 40px;
  padding: 0 var(--space-sm);
  border-radius: var(--radius-sm);
  color: var(--sidebar-text);
  text-decoration: none;
  font-size: var(--text-sm);
}

.admin-nav-item:hover,
.admin-nav-item.router-link-active {
  background: var(--sidebar-hover);
  color: var(--sidebar-text-active);
}

.admin-main { min-width: 0; padding: var(--space-2xl); }

.admin-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 58px;
  margin-bottom: var(--space-xl);
}

.admin-topbar h1 { font-size: var(--text-xl); margin: 0; letter-spacing: 0; }

.admin-user {
  display: inline-flex;
  align-items: center;
  gap: var(--space-md);
  color: var(--muted);
}

.admin-avatar {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-circle);
  background: var(--avatar-bg);
  color: var(--green);
  font-weight: 800;
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
    border-bottom: 1px solid rgba(0, 80, 180, 0.12);
  }
  .brand-block { padding-bottom: var(--space-sm); }
  .brand-block div { display: none; }
  .admin-nav {
    display: flex;
    overflow-x: auto;
    gap: 2px;
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
  .admin-main { padding: var(--space-lg); }
  .admin-topbar { align-items: stretch; flex-direction: column; }
  .admin-nav-item span { display: inline; }
  .admin-nav { flex-wrap: wrap; }
}
</style>
