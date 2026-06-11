import { createRouter, createWebHashHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';

const MessagesView = () => import('@/views/MessagesView.vue');
const MessagesSection = () => import('@/views/sections/MessagesSection.vue');
const TodosSection = () => import('@/views/sections/TodosSection.vue');
const WorkbenchSection = () => import('@/views/sections/WorkbenchSection.vue');
const FilesSection = () => import('@/views/sections/FilesSection.vue');
const ContactsSection = () => import('@/views/sections/ContactsSection.vue');
const SettingsSection = () => import('@/views/sections/SettingsSection.vue');

export const employeeRoutes = [
  {
    path: '/',
    redirect: '/messages',
  },
  {
    path: '/',
    component: MessagesView,
    children: [
      {
        path: 'messages',
        name: 'employee-messages',
        component: MessagesSection,
        meta: { section: 'messages' },
      },
      {
        path: 'messages/:conversationId',
        name: 'employee-message-detail',
        component: MessagesSection,
        meta: { section: 'messages' },
      },
      {
        path: 'todos',
        name: 'employee-todos',
        component: TodosSection,
        meta: { section: 'todos' },
      },
      {
        path: 'todos/:todoId',
        name: 'employee-todo-detail',
        component: TodosSection,
        meta: { section: 'todos' },
      },
      {
        path: 'workbench',
        name: 'employee-workbench',
        component: WorkbenchSection,
        meta: { section: 'workbench' },
      },
      {
        path: 'workbench/:app',
        name: 'employee-workbench-detail',
        component: WorkbenchSection,
        meta: { section: 'workbench' },
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

const router = createRouter({
  history: createWebHashHistory(),
  routes: employeeRoutes,
});

export default router;
