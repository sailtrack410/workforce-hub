// hub-web 路由
import { createRouter, createWebHashHistory } from 'vue-router';

const AuthView = () => import('@/views/AuthView.vue');
const PlaceholderView = () => import('@/views/PlaceholderView.vue');
const WorkbenchView = () => import('@/views/WorkbenchView.vue');
const MessagesView = () => import('@/views/MessagesView.vue');

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: AuthView },
    { path: '/register', name: 'register', component: AuthView },
    { path: '/workbench', name: 'workbench', component: WorkbenchView },
    { path: '/messages', name: 'messages', component: MessagesView },
    { path: '/messages/:conversationId', name: 'messages-detail', component: MessagesView },
    { path: '/approvals', name: 'approvals', component: PlaceholderView },
    { path: '/attendance', name: 'attendance', component: PlaceholderView },
    { path: '/agent', name: 'agent', component: PlaceholderView },
    { path: '/', redirect: '/workbench' },
    { path: '/:pathMatch(.*)*', redirect: '/workbench' },
  ],
});
