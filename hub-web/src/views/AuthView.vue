<script setup lang="ts">
/** 登录/注册：对齐画布 10:6 / 10:10；自助注册与短信能力仍等待后端接口。 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  BellRing, Check, Eye, EyeOff, LoaderCircle, MessageSquare, ShieldCheck, Sparkles, X,
} from 'lucide-vue-next';
import HubToast from '@/components/base/HubToast.vue';
import { useToast } from '@/composables/useToast';
import { useSessionStore } from '@/stores/session';

const session = useSessionStore();
const route = useRoute();
const router = useRouter();
const { message: toastMessage, show: showToast } = useToast();
const mode = computed(() => (route.name === 'register' ? 'register' : 'login'));
const FEATURES = [
  { icon: ShieldCheck, title: 'AI 预审', desc: '审批提交秒级预审，硬规则自动校验' },
  { icon: Sparkles, title: '@Agent 问答', desc: '制度、假期、流程，随时问随时答' },
  { icon: BellRing, title: '主动提醒', desc: '待办、异常、截止日，Agent 替你盯着' },
];

const loginTab = ref<'password' | 'code'>('password');
const loginName = ref('');
const password = ref('');
const showPassword = ref(false);
const loginError = ref('');
const loginState = ref<'idle' | 'loading' | 'success' | 'error'>('idle');
const restoring = ref(true);
let loginTimer = 0;

async function submitLogin() {
  if (loginState.value !== 'idle') return;
  if (!loginName.value.trim() || !password.value) {
    loginError.value = '请输入工号和密码';
    return;
  }
  loginError.value = '';
  loginState.value = 'loading';
  try {
    await session.login(loginName.value.trim(), password.value);
    void session.loadOrgProfile().catch(() => undefined);
    loginState.value = 'success';
    loginTimer = window.setTimeout(() => void router.replace('/messages'), 280);
  } catch (error) {
    loginState.value = 'error';
    loginError.value = error instanceof Error ? error.message : '登录失败';
    loginTimer = window.setTimeout(() => { loginState.value = 'idle'; }, 700);
  }
}

const regName = ref('');
const regPhone = ref('');
const regCode = ref('');
const regPassword = ref('');
const regInvite = ref('');
const regAgree = ref(false);
const regSubmitting = ref(false);
const showRegPassword = ref(false);

async function submitRegister() {
  if (regSubmitting.value) return;
  if (!regName.value.trim() || !regPhone.value.trim() || !regPassword.value) {
    showToast('请填写姓名、手机号和密码');
    return;
  }
  if (!regAgree.value) {
    showToast('请先阅读并同意《用户协议》和《隐私政策》');
    return;
  }
  regSubmitting.value = true;
  loginTimer = window.setTimeout(() => {
    regSubmitting.value = false;
    showToast('自助注册暂未开放，请联系管理员开通（接口需求 #12）');
  }, 240);
}
onMounted(async () => {
  if (session.isAuthenticated) {
    await router.replace('/messages');
    return;
  }
  await session.loadCurrentUser();
  if (session.isAuthenticated) await router.replace('/messages');
  restoring.value = false;
});
onBeforeUnmount(() => window.clearTimeout(loginTimer));
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <aside class="auth-brand">
        <div class="auth-logo"><span class="auth-logo-mark">W</span><strong>Workforce Hub</strong></div>
        <h1>智能人事协同<br />让工作更简单</h1>
        <p class="auth-slogan-sub">考勤 · 假期 · 审批 · 消息 · AI Agent，一个入口全部搞定</p>
        <ul class="auth-features">
          <li v-for="feature in FEATURES" :key="feature.title">
            <span class="auth-feature-icon"><component :is="feature.icon" :size="19" /></span>
            <span><strong>{{ feature.title }}</strong><small>{{ feature.desc }}</small></span>
          </li>
        </ul>
        <p class="auth-foot">私有化部署 · 数据自主可控</p>
      </aside>

      <section class="auth-form">
        <Transition name="auth-view" mode="out-in">
          <div v-if="mode === 'login'" key="login" class="auth-view">
            <p v-if="restoring" class="auth-dialog-note">正在恢复登录状态…</p>
            <template v-else>
            <header class="auth-heading"><h2>欢迎回来</h2><p>登录 Workforce Hub 员工端</p></header>
            <div class="auth-tabs" role="tablist" aria-label="登录方式">
              <button type="button" role="tab" :aria-selected="loginTab === 'password'" :class="{ active: loginTab === 'password' }" @click="loginTab = 'password'">密码登录</button>
              <button type="button" role="tab" :aria-selected="loginTab === 'code'" :class="{ active: loginTab === 'code' }" @click="loginTab = 'code'">验证码登录</button>
            </div>
            <form v-if="loginTab === 'password'" class="auth-fields login-fields" @submit.prevent="submitLogin">
              <label class="auth-field">工号 / 邮箱<input v-model="loginName" type="text" placeholder="请输入工号或企业邮箱" autocomplete="username" /></label>
              <label class="auth-field">密码
                <span class="auth-password"><input v-model="password" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" /><button type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="17" /><Eye v-else :size="17" /></button></span>
              </label>
              <div class="auth-forgot-row"><button type="button" class="auth-link" @click="showToast('找回密码即将上线')">忘记密码？</button></div>
              <p v-if="loginError" class="auth-error" role="alert">{{ loginError }}</p>
              <button type="submit" class="auth-submit" :class="`is-${loginState}`" :disabled="loginState !== 'idle'">
                <LoaderCircle v-if="loginState === 'loading'" class="auth-spin" :size="19" />
                <Check v-else-if="loginState === 'success'" :size="20" />
                <X v-else-if="loginState === 'error'" :size="20" />
                <span v-else>登 录</span>
              </button>
            </form>
            <div v-else class="auth-code-panel">
              <label class="auth-field">手机号 / 邮箱<input type="text" placeholder="请输入手机号或邮箱" disabled /></label>
              <label class="auth-field">验证码<input type="text" placeholder="验证码登录即将上线" disabled /></label>
              <button class="auth-submit" type="button" disabled>登 录</button>
            </div>
            <div class="auth-divider"><i /><small>其他登录方式</small><i /></div>
            <button type="button" class="auth-wechat" @click="showToast('微信扫码登录即将上线')"><MessageSquare :size="18" />微信扫码登录</button>
            <p class="auth-guide">还没有账号？<button type="button" class="auth-link" @click="router.push({ name: 'register' })">立即注册</button></p>
            </template>
          </div>

          <div v-else key="register" class="auth-view register-view">
            <header class="auth-heading"><h2>创建账号</h2><p>注册后即可加入你的团队</p></header>
            <form class="auth-fields" @submit.prevent="submitRegister">
              <label class="auth-field">姓名<input v-model="regName" type="text" placeholder="请输入真实姓名" autocomplete="name" /></label>
              <label class="auth-field">手机号<span class="auth-password"><input v-model="regPhone" type="tel" placeholder="请输入手机号" autocomplete="tel" /><button type="button" class="auth-link" @click="showToast('短信验证码发送即将上线')">获取验证码</button></span></label>
              <label class="auth-field">短信验证码<input v-model="regCode" type="text" inputmode="numeric" placeholder="请输入 6 位验证码" /></label>
              <label class="auth-field">设置密码<span class="auth-password"><input v-model="regPassword" :type="showRegPassword ? 'text' : 'password'" placeholder="8 位以上，含字母和数字" autocomplete="new-password" /><button type="button" :aria-label="showRegPassword ? '隐藏密码' : '显示密码'" @click="showRegPassword = !showRegPassword"><EyeOff v-if="showRegPassword" :size="17" /><Eye v-else :size="17" /></button></span></label>
              <label class="auth-field">企业邀请码<input v-model="regInvite" type="text" placeholder="请向管理员索取邀请码" /></label>
              <label class="auth-agree"><input v-model="regAgree" type="checkbox" /><small>已阅读并同意《用户协议》和《隐私政策》</small></label>
              <button type="submit" class="auth-submit" :disabled="regSubmitting"><LoaderCircle v-if="regSubmitting" class="auth-spin" :size="19" /><span v-else>注 册</span></button>
            </form>
            <p class="auth-guide">已有账号？<button type="button" class="auth-link" @click="router.push({ name: 'login' })">去登录</button></p>
          </div>
        </Transition>
      </section>
    </div>
    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped>
.auth-page { display: grid; place-items: center; min-height: 100dvh; padding: 32px; background: var(--hw-canvas); }
.auth-card { display: flex; width: min(1100px, 100%); height: min(720px, calc(100dvh - 64px)); min-height: 600px; border-radius: 24px; background: var(--hw-card); box-shadow: var(--hw-shadow-card); overflow: hidden; }
.auth-brand { display: flex; flex-direction: column; width: 440px; padding: 48px; background: linear-gradient(135deg, var(--hw-primary), var(--hw-primary-deep)); color: var(--hw-on-primary); flex: none; }
.auth-logo { display: flex; align-items: center; gap: 12px; margin-bottom: 32px; }
.auth-logo-mark { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 8px; background: var(--hw-nav-hover); font-size: 16px; font-weight: 600; }
.auth-logo strong { font-size: 20px; }
.auth-brand h1 { font-size: 30px; line-height: 42px; }
.auth-slogan-sub { margin-top: 20px; font-size: 13px; line-height: 22px; opacity: .82; }
.auth-features { display: flex; flex-direction: column; gap: 18px; margin-top: auto; list-style: none; }
.auth-features li { display: flex; align-items: center; gap: 12px; }
.auth-feature-icon { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 10px; background: var(--hw-nav-hover); flex: none; }
.auth-features li > span:last-child { display: flex; flex-direction: column; gap: 3px; }
.auth-features strong { font-size: 14px; font-weight: 500; }
.auth-features small { font-size: 12px; opacity: .78; }
.auth-foot { margin-top: 24px; font-size: 12px; opacity: .65; }
.auth-form { flex: 1; min-width: 0; padding: 52px 56px 36px; overflow-y: auto; }
.auth-view { display: flex; flex-direction: column; height: 100%; }
.auth-heading h2 { font-size: 24px; line-height: 32px; }
.auth-heading p { margin-top: 4px; font-size: 13px; color: var(--hw-tertiary); }
.auth-tabs { display: flex; gap: 8px; margin: 18px 0 20px; }
.auth-tabs button { height: 40px; padding: 0 16px; border: 0; border-radius: 999px; background: var(--hw-chip); color: var(--hw-secondary); font-size: 12px; cursor: pointer; transition: background var(--hw-duration-fast), color var(--hw-duration-fast); }
.auth-tabs button.active { background: var(--hw-pill-dark); color: var(--hw-on-primary); }
.auth-fields { display: flex; flex-direction: column; gap: 12px; }
.auth-field { display: flex; flex-direction: column; gap: 7px; color: var(--hw-secondary); font-size: 13px; }
.auth-field > input, .auth-password { width: 100%; height: 46px; border: 1px solid transparent; border-radius: 8px; background: var(--hw-inner); transition: border-color var(--hw-duration-fast), background var(--hw-duration-fast); }
.auth-field > input, .auth-password input { padding: 0 14px; border: 0; outline: 0; background: transparent; color: var(--hw-ink); font-size: 13px; }
.auth-field > input:focus, .auth-password:focus-within { border-color: var(--hw-sky-strong); background: var(--hw-card); }
.auth-field input::placeholder { color: var(--hw-muted); }
.auth-password { display: flex; align-items: center; }
.auth-password input { flex: 1; min-width: 0; height: 100%; }
.auth-password > button { display: grid; place-items: center; min-width: 42px; height: 40px; padding: 0 10px; border: 0; background: transparent; color: var(--hw-muted); cursor: pointer; }
.auth-password > button.auth-link { min-width: 90px; color: var(--hw-primary); font-size: 12px; }
.auth-forgot-row { display: flex; justify-content: flex-end; margin-top: -5px; }
.auth-link { padding: 0; border: 0; background: transparent; color: var(--hw-primary); font-size: 12px; cursor: pointer; }
.auth-error { margin-top: -4px; color: var(--hw-red); font-size: 12px; }
.auth-submit { display: inline-flex; align-items: center; justify-content: center; gap: 8px; width: 100%; height: 44px; margin-top: 10px; border: 0; border-radius: 8px; background: var(--hw-primary); color: var(--hw-on-primary); font-size: 14px; font-weight: 500; cursor: pointer; transition: background var(--hw-duration-fast), opacity var(--hw-duration-fast); }
.auth-submit:hover:not(:disabled) { background: var(--hw-primary-deep); }
.auth-submit:disabled { cursor: default; opacity: .8; }
.auth-submit.is-success { background: var(--hw-green); }
.auth-submit.is-error { background: var(--hw-red); }
.auth-spin { animation: auth-rotate .8s linear infinite; }
.auth-code-panel { display: flex; flex-direction: column; gap: 16px; }
.auth-code-panel input { width: 100%; }
.auth-divider { display: flex; align-items: center; gap: 12px; margin: 8px 0 16px; }
.login-fields + .auth-divider, .auth-code-panel + .auth-divider { margin-top: 18px; }
.auth-divider i { height: 1px; background: var(--hw-border); flex: 1; }
.auth-divider small { color: var(--hw-muted); font-size: 12px; }
.auth-wechat { display: flex; align-items: center; justify-content: center; gap: 8px; height: 48px; border: 1px solid var(--hw-border); border-radius: 8px; background: var(--hw-card); color: var(--hw-secondary); font-size: 13px; cursor: pointer; transition: background var(--hw-duration-fast); }
.auth-wechat:hover { background: var(--hw-chip); }
.auth-wechat svg { color: var(--hw-green); }
.auth-guide { display: flex; justify-content: center; gap: 6px; margin-top: auto; padding-top: 24px; color: var(--hw-tertiary); font-size: 12px; }
.auth-agree { display: flex; align-items: center; gap: 7px; color: var(--hw-tertiary); cursor: pointer; }
.auth-agree input { accent-color: var(--hw-primary); }
.auth-agree small { font-size: 12px; }
.register-view .auth-heading { margin-bottom: 18px; }
.register-view .auth-fields { gap: 11px; }
.register-view .auth-submit { margin-top: 0; }
.auth-view-enter-active { transition: opacity 160ms var(--hw-motion-enter), transform 160ms var(--hw-motion-enter); }
.auth-view-leave-active { transition: opacity 100ms var(--hw-motion-exit); }
.auth-view-enter-from { opacity: 0; transform: translateY(4px); }
.auth-view-leave-to { opacity: 0; }
@keyframes auth-rotate { to { transform: rotate(360deg); } }
@media (max-width: 960px) { .auth-brand { display: none; } .auth-card { width: min(560px, 100%); height: auto; min-height: 640px; } }
@media (max-width: 560px) { .auth-page { padding: 16px; } .auth-card { min-height: calc(100dvh - 32px); border-radius: 16px; } .auth-form { padding: 36px 24px 28px; } }
</style>
