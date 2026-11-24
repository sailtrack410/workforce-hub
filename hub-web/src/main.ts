import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import { router } from './router';
import './styles/tokens.css';
import { loadPreferences } from '@/services/preferences';

if (window.workforceDesktop?.platform) {
  document.body.dataset.desktopPlatform = window.workforceDesktop.platform;
}

void loadPreferences().catch(() => undefined).finally(() => {
  createApp(App).use(createPinia()).use(router).mount('#app');
});
