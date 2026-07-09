<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { LogIn } from 'lucide-vue-next';
import { useSessionStore } from '@/stores/session';
import { errorMessage } from '@/utils/format';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const form = reactive({
  loginName: '',
  password: '',
});
const submitting = ref(false);
const error = ref('');

const redirectTarget = computed(() => {
  const redirect = route.query.redirect;
  return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : '/agent-tasks';
});

async function submitLogin() {
  if (!form.loginName.trim() || !form.password) {
    error.value = '登录名和密码不能为空';
    return;
  }
  submitting.value = true;
  error.value = '';
  try {
    await session.login({
      loginName: form.loginName.trim(),
      password: form.password,
    });
    await router.replace(redirectTarget.value);
  } catch (loginError) {
    error.value = errorMessage(loginError, '登录失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div>
        <p class="eyebrow">workforce hub</p>
        <h1>后台运营入口</h1>
      </div>

      <form class="login-form" @submit.prevent="submitLogin">
        <label>
          <span>登录名</span>
          <input v-model="form.loginName" autocomplete="username" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="form.password" type="password" autocomplete="current-password" />
        </label>
        <p v-if="error" class="form-error">{{ error }}</p>
        <button class="primary-button" :disabled="submitting || session.loading">
          <LogIn :size="18" />
          <span>{{ submitting ? '登录中' : '登录' }}</span>
        </button>
      </form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--space-3xl);
  background: var(--surface);
}

.login-panel {
  width: min(430px, 100%);
  display: grid;
  gap: var(--space-xl);
  padding: var(--space-3xl);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel);
  box-shadow: var(--shadow-sm);
}

.eyebrow {
  margin: 0 0 var(--space-sm);
  color: var(--green);
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0;
  text-transform: uppercase;
}

.login-panel h1 {
  margin: 0;
  letter-spacing: 0;
  font-size: var(--text-2xl);
}

.login-form {
  display: grid;
  gap: var(--space-base);
}

.login-form label {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

@media (max-width: 640px) {
  .login-page { padding: var(--space-lg); }
}
</style>
