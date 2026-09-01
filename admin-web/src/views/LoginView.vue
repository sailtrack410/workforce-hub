<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Building2, LockKeyhole, LogIn, ShieldCheck, UserRound } from 'lucide-vue-next';
import { useSessionStore } from '@/stores/session';
import { errorMessage } from '@/utils/format';
import AdminAssetPlaceholder from '@/components/AdminAssetPlaceholder.vue';

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
    <section class="login-visual" aria-labelledby="admin-product-name">
      <header class="auth-brand">
        <span class="auth-brand-mark"><Building2 :size="20" /></span>
        <div>
          <h1 id="admin-product-name">Workforce Hub</h1>
          <span>企业管理控制台</span>
        </div>
      </header>

      <div class="login-visual-content">
        <div class="login-visual-heading">
          <span>Enterprise Admin</span>
          <strong>企业管理控制台</strong>
        </div>
        <AdminAssetPlaceholder asset-id="WF-AUTH-ADMIN-01" />
      </div>

      <span class="visual-foot">Workforce Hub · Admin</span>
    </section>

    <section class="login-access" aria-labelledby="admin-login-title">
      <div class="login-panel">
        <header class="login-heading">
          <p class="eyebrow">管理端</p>
          <h2 id="admin-login-title">登录企业控制台</h2>
          <p class="login-subtitle">使用企业管理员账号继续</p>
        </header>

        <form class="login-form" @submit.prevent="submitLogin">
          <label>
            <span>登录名</span>
            <span class="login-input-shell">
              <UserRound :size="17" aria-hidden="true" />
              <input v-model="form.loginName" autocomplete="username" />
            </span>
          </label>
          <label>
            <span>密码</span>
            <span class="login-input-shell">
              <LockKeyhole :size="17" aria-hidden="true" />
              <input v-model="form.password" type="password" autocomplete="current-password" />
            </span>
          </label>
          <p v-if="error" class="form-error">{{ error }}</p>
          <button class="primary-button" :disabled="submitting || session.loading">
            <LogIn :size="18" />
            <span>{{ submitting ? '登录中' : '登录' }}</span>
          </button>
        </form>
      </div>

      <p class="login-security"><ShieldCheck :size="15" />Workforce Hub Admin</p>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(420px, 0.8fr);
  background: var(--panel);
}

.login-visual {
  min-width: 0;
  min-height: 100vh;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: var(--space-xl);
  padding: var(--space-xl) var(--space-3xl);
  background: var(--auth-bg);
  color: var(--auth-text);
  overflow: hidden;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}

.auth-brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 1px solid var(--auth-line);
  border-radius: var(--radius-md);
  background: var(--auth-accent-soft);
  color: var(--auth-accent);
}

.auth-brand div {
  display: grid;
  gap: 2px;
}

.auth-brand h1 {
  margin: 0;
  color: var(--auth-text);
  font-size: var(--text-md);
  line-height: 1.2;
}

.auth-brand span {
  color: var(--auth-muted);
  font-size: var(--text-xs);
}

.login-visual-content {
  width: 100%;
  max-width: 760px;
  align-self: center;
  display: grid;
  gap: var(--space-xl);
}

.login-visual-heading {
  display: grid;
  gap: var(--space-sm);
}

.login-visual-heading span,
.visual-foot {
  color: var(--auth-muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.login-visual-heading strong {
  color: var(--auth-text);
  font-size: var(--text-2xl);
  line-height: 1.2;
}

.visual-foot {
  justify-self: start;
  padding-top: var(--space-md);
  border-top: 1px solid var(--auth-line);
}

.login-access {
  min-height: 100vh;
  display: grid;
  grid-template-rows: 1fr auto;
  place-items: center;
  gap: var(--space-xl);
  padding: var(--space-3xl);
  background: var(--panel);
}

.login-panel {
  width: min(390px, 100%);
  display: grid;
  gap: var(--space-2xl);
  align-self: center;
}

.eyebrow {
  margin: 0 0 var(--space-sm);
  color: var(--green-dark);
  font-size: var(--text-xs);
  font-weight: 700;
  letter-spacing: 0;
}

.login-heading h2 {
  margin: 0;
  letter-spacing: 0;
  font-size: var(--text-2xl);
  line-height: 1.25;
}

.login-subtitle {
  margin: var(--space-sm) 0 0;
  color: var(--muted);
  font-size: var(--text-sm);
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

.login-input-shell {
  min-height: var(--control-h);
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-sm);
  padding-left: var(--space-md);
  border: 1px solid var(--control-border);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  color: var(--muted);
  transition: border-color 160ms ease, box-shadow 160ms ease;
}

.login-input-shell:hover { border-color: var(--line-strong); }

.login-input-shell:focus-within {
  border-color: var(--green);
  box-shadow: 0 0 0 2px var(--surface), 0 0 0 4px var(--focus-ring);
  color: var(--green-dark);
}

.login-input-shell input {
  min-height: calc(var(--control-h) - 2px);
  padding-left: 0;
  border: 0;
  box-shadow: none;
}

.login-input-shell input:focus,
.login-input-shell input:hover { border: 0; box-shadow: none; }

.login-form .primary-button { width: 100%; margin-top: var(--space-xs); }

.login-security {
  display: flex;
  align-items: center;
  gap: var(--space-xs);
  margin: 0;
  color: var(--muted-light);
  font-size: var(--text-xs);
}

@media (max-width: 900px) {
  .login-page { grid-template-columns: minmax(0, 1fr); }
  .login-visual {
    min-height: 340px;
    padding: var(--space-lg);
  }
  .login-visual-content {
    grid-template-columns: minmax(150px, 0.6fr) minmax(280px, 1.4fr);
    align-items: center;
  }
  .login-access { min-height: auto; padding: var(--space-3xl) var(--space-lg); }
}

@media (max-width: 640px) {
  .login-visual { min-height: 300px; }
  .login-visual-content { grid-template-columns: minmax(0, 1fr); gap: var(--space-base); }
  .login-visual-heading { display: none; }
  .visual-foot { display: none; }
  .login-access { padding: var(--space-3xl) var(--space-lg) var(--space-xl); }
}
</style>
