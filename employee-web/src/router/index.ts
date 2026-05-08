import { createRouter, createWebHashHistory } from 'vue-router';
import MessagesView from '../views/MessagesView.vue';

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      name: 'messages',
      component: MessagesView,
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'messages-fallback',
      component: MessagesView,
    },
  ],
});

export default router;
