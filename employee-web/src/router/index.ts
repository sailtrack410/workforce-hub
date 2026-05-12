import { createRouter, createWebHashHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import MessagesView from '../views/MessagesView.vue';
import MessagesSection from '../views/sections/MessagesSection.vue';
import TodosSection from '../views/sections/TodosSection.vue';
import WorkbenchSection from '../views/sections/WorkbenchSection.vue';
import FilesSection from '../views/sections/FilesSection.vue';
import ContactsSection from '../views/sections/ContactsSection.vue';
import SettingsSection from '../views/sections/SettingsSection.vue';
import EmptySection from '../views/sections/EmptySection.vue';

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
        name: 'employee-processes',
        component: EmptySection,
        meta: { section: 'processes' },
      },
      {
        path: 'reports',
        name: 'employee-reports',
        component: EmptySection,
        meta: { section: 'reports' },
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
