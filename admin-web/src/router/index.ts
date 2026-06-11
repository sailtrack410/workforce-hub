import { createRouter, createWebHashHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useSessionStore } from '@/stores/session';

const LoginView = () => import('@/views/LoginView.vue');
const AdminShell = () => import('@/views/AdminShell.vue');
const DashboardView = () => import('@/views/DashboardView.vue');
const AgentTasksView = () => import('@/views/AgentTasksView.vue');
const AgentConfigView = () => import('@/views/AgentConfigView.vue');
const AgentDagView = () => import('@/views/AgentDagView.vue');
const WorkflowGovernanceView = () => import('@/views/WorkflowGovernanceView.vue');
const LeaveGovernanceView = () => import('@/views/LeaveGovernanceView.vue');
const LeaveTypeView = () => import('@/views/LeaveTypeView.vue');
const DepartmentView = () => import('@/views/DepartmentView.vue');
const PositionView = () => import('@/views/PositionView.vue');
const UserView = () => import('@/views/UserView.vue');
const FileCleanupView = () => import('@/views/FileCleanupView.vue');
const RagflowView = () => import('@/views/RagflowView.vue');
const SettingsView = () => import('@/views/SettingsView.vue');

export const adminRoutes = [
  {
    path: '/login',
    name: 'admin-login',
    component: LoginView,
    meta: { public: true },
  },
  {
    path: '/agent-config/:agentId/dag',
    name: 'admin-agent-dag',
    component: AgentDagView,
  },
  {
    path: '/',
    component: AdminShell,
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', name: 'admin-dashboard', component: DashboardView },
      { path: 'agent-tasks', name: 'admin-agent-tasks', component: AgentTasksView },
      { path: 'agent-config', name: 'admin-agent-config', component: AgentConfigView },
      { path: 'workflow', name: 'admin-workflow', component: WorkflowGovernanceView },
      { path: 'leave', name: 'admin-leave', component: LeaveGovernanceView },
      { path: 'leave-types', name: 'admin-leave-types', component: LeaveTypeView },
      { path: 'departments', name: 'admin-departments', component: DepartmentView },
      { path: 'positions', name: 'admin-positions', component: PositionView },
      { path: 'users', name: 'admin-users', component: UserView },
      { path: 'files', name: 'admin-files', component: FileCleanupView },
      { path: 'ragflow', name: 'admin-ragflow', component: RagflowView },
      { path: 'settings', name: 'admin-settings', component: SettingsView },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
] satisfies RouteRecordRaw[];

const router = createRouter({
  history: createWebHashHistory(),
  routes: adminRoutes,
});

router.beforeEach(async (to) => {
  const session = useSessionStore();
  if (!session.initialized) {
    await session.loadCurrentUser();
  }

  if (to.meta.public) {
    if (session.isAuthenticated && to.name === 'admin-login') return '/dashboard';
    return true;
  }

  if (!session.isAuthenticated) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    };
  }
  return true;
});

export default router;
