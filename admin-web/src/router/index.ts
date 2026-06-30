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
const RoleView = () => import('@/views/RoleView.vue');
const AttendanceView = () => import('@/views/AttendanceView.vue');
const DepartmentView = () => import('@/views/DepartmentView.vue');
const PositionView = () => import('@/views/PositionView.vue');
const UserView = () => import('@/views/UserView.vue');
const FileCleanupView = () => import('@/views/FileCleanupView.vue');
const RagflowView = () => import('@/views/RagflowView.vue');
const SettingsView = () => import('@/views/SettingsView.vue');
const SystemGovernanceView = () => import('@/views/SystemGovernanceView.vue');

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
      { path: 'agent-tasks', name: 'admin-agent-tasks', component: AgentTasksView, meta: { permission: 'ADMIN_AGENT_TASK_MANAGE' } },
      { path: 'agent-config', name: 'admin-agent-config', component: AgentConfigView, meta: { permission: 'ADMIN_AGENT_MANAGE' } },
      { path: 'workflow', name: 'admin-workflow', component: WorkflowGovernanceView, meta: { permission: 'ADMIN_WORKFLOW_MANAGE' } },
      { path: 'leave', name: 'admin-leave', component: LeaveGovernanceView, meta: { permission: 'ADMIN_LEAVE_MANAGE' } },
      { path: 'leave-types', name: 'admin-leave-types', component: LeaveTypeView, meta: { permission: 'ADMIN_LEAVE_MANAGE' } },
      { path: 'roles', name: 'admin-roles', component: RoleView, meta: { permission: 'ADMIN_RBAC_MANAGE' } },
      { path: 'attendance', name: 'admin-attendance', component: AttendanceView, meta: { permission: 'ADMIN_ATTENDANCE_MANAGE' } },
      { path: 'departments', name: 'admin-departments', component: DepartmentView, meta: { permission: 'ADMIN_ORG_MANAGE' } },
      { path: 'positions', name: 'admin-positions', component: PositionView, meta: { permission: 'ADMIN_ORG_MANAGE' } },
      { path: 'users', name: 'admin-users', component: UserView, meta: { permission: 'ADMIN_ORG_MANAGE' } },
      { path: 'files', name: 'admin-files', component: FileCleanupView, meta: { permission: 'ADMIN_FILE_MANAGE' } },
      { path: 'ragflow', name: 'admin-ragflow', component: RagflowView, meta: { permission: 'ADMIN_RAGFLOW_MANAGE' } },
      { path: 'system', name: 'admin-system', component: SystemGovernanceView },
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
  const permission = typeof to.meta.permission === 'string' ? to.meta.permission : '';
  if (permission && !session.hasPermission(permission)) return '/dashboard';
  return true;
});

export default router;
