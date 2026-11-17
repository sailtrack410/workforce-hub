import { createApp } from 'vue';
import { createPinia } from 'pinia';
import { initHostSdk } from './core/host-sdk';
import App from './App.vue';
import router from './router';
import './styles/main.css';
import './styles/employee-shell.css';

initHostSdk();

createApp(App).use(createPinia()).use(router).mount('#app');
