import { createRouter, createWebHashHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';

const MessagesView = () => import('@/views/MessagesView.vue');
const FilesSection = () => import('@/views/sections/FilesSection.vue');
const ContactsSection = () => import('@/views/sections/ContactsSection.vue');
const AgentsSection = () => import('@/views/sections/AgentsSection.vue');
const SettingsSection = () => import('@/views/sections/SettingsSection.vue');
// hub 新壳：登录页走画布版 AuthView，认证通过后再进 MessagesView 主壳
const HubAuthView = () => import('@/hub/views/AuthView.vue');
// hub 工作台：画布版新工作台，挂在 HubShell 布局壳下
const HubShell = () => import('@/hub/layouts/HubShell.vue');
const HubWorkbench = () => import('@/hub/views/WorkbenchView.vue');
// hub 消息页：画布版新消息（三栏），同样挂 HubShell
const HubMessages = () => import('@/hub/views/MessagesView.vue');
// hub 审批中心：画布版审批中心
const HubApprovals = () => import('@/hub/views/ApprovalsView.vue');

export const employeeRoutes = [
  {
    path: '/login',
    name: 'employee-login',
    component: HubAuthView,
    meta: { section: 'auth' },
  },
  {
    // 顶层精确路径，HubShell 作为包装布局
    path: '/hub/workbench',
    name: 'hub-workbench',
    component: HubWorkbench,
    meta: { section: 'workbench', hub: true },
  },
  {
    path: '/hub/messages',
    name: 'hub-messages',
    component: HubMessages,
    meta: { section: 'messages', hub: true },
  },
  {
    path: '/hub/messages/:conversationId',
    name: 'hub-messages-detail',
    component: HubMessages,
    meta: { section: 'messages', hub: true },
  },
  {
    path: '/hub/approvals',
    name: 'hub-approvals',
    component: HubApprovals,
    meta: { section: 'approval', hub: true },
  },
  {
    path: '/',
    redirect: '/hub/messages',
  },
  {
    path: '/',
    component: MessagesView,
    children: [
      {
        // 新消息页唯一入口在 /hub/messages，旧路由一律重定向过去
        path: 'messages',
        redirect: '/hub/messages',
      },
      {
        path: 'messages/:conversationId',
        redirect: (to) => `/hub/messages/${to.params.conversationId}`,
      },
      {
        // 审批中心唯一入口在 /hub/approvals，旧待办路由一律重定向过去
        path: 'todos',
        redirect: '/hub/approvals',
      },
      {
        path: 'todos/:todoId',
        redirect: '/hub/approvals',
      },
      {
        // 新工作台唯一入口在 /hub/workbench，旧路由一律重定向过去
        path: 'workbench',
        redirect: '/hub/workbench',
      },
      {
        path: 'workbench/:app',
        redirect: '/hub/workbench',
      },
      {
        path: 'files',
        name: 'employee-files',
        component: FilesSection,
        meta: { section: 'files' },
      },
      {
        path: 'contacts',
        name: 'employee-contacts',
        component: ContactsSection,
        meta: { section: 'contacts' },
      },
      {
        path: 'agents',
        name: 'employee-agents',
        component: AgentsSection,
        meta: { section: 'agents' },
      },
      {
        path: 'settings',
        name: 'employee-settings',
        component: SettingsSection,
        meta: { section: 'settings' },
      },
      {
        path: 'settings/:panel',
        name: 'employee-settings-detail',
        component: SettingsSection,
        meta: { section: 'settings' },
      },
      {
        path: 'processes',
        redirect: '/messages',
      },
      {
        path: 'reports',
        redirect: '/messages',
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/messages',
  },
] satisfies RouteRecordRaw[];

// employee-web 没有路由守卫，认证逻辑在 MessagesView.vue 的 onMounted 中处理
const router = createRouter({
  history: createWebHashHistory(),
  routes: employeeRoutes,
});

export default router;
