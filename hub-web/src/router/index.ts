// hub-web 路由
import { createRouter, createWebHashHistory } from 'vue-router';

const AuthView = () => import('@/views/AuthView.vue');
const PlaceholderView = () => import('@/views/PlaceholderView.vue');
const WorkbenchView = () => import('@/views/WorkbenchView.vue');
const MessagesView = () => import('@/views/MessagesView.vue');
const AttendanceView = () => import('@/views/AttendanceView.vue');
const ProfileView = () => import('@/views/ProfileView.vue');
const SettingsView = () => import('@/views/SettingsView.vue');
const TasksView = () => import('@/views/TasksView.vue');
const TodosView = () => import('@/views/TodosView.vue');
const ContactsView = () => import('@/views/ContactsView.vue');
const NotificationsView = () => import('@/views/NotificationsView.vue');
const ApprovalsView = () => import('@/views/ApprovalsView.vue');
const KnowledgeView = () => import('@/views/KnowledgeView.vue');
const DriveView = () => import('@/views/DriveView.vue');

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: AuthView },
    { path: '/register', name: 'register', component: AuthView },
    { path: '/workbench', name: 'workbench', component: WorkbenchView },
    { path: '/messages', name: 'messages', component: MessagesView },
    { path: '/messages/:conversationId', name: 'messages-detail', component: MessagesView },
    { path: '/attendance', name: 'attendance', component: AttendanceView },
    { path: '/profile', name: 'profile', component: ProfileView },
    { path: '/settings', name: 'settings', component: SettingsView },
    { path: '/tasks', name: 'tasks', component: TasksView },
    { path: '/todos', name: 'todos', component: TodosView },
    { path: '/knowledge', name: 'knowledge', component: KnowledgeView },
    { path: '/drive', name: 'drive', component: DriveView },
    { path: '/contacts', name: 'contacts', component: ContactsView },
    { path: '/notifications', name: 'notifications', component: NotificationsView },
    { path: '/approvals', name: 'approvals', component: ApprovalsView },
    { path: '/agent', redirect: '/messages' },
    { path: '/', redirect: '/messages' },
    { path: '/:pathMatch(.*)*', redirect: '/messages' },
  ],
});
