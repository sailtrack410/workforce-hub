<script setup lang="ts">
/**
 * hub-web 登录/注册页：对齐画布「员工端-登录」10:6、「员工端-注册」10:10。
 * 居中白卡 1100×720 r24 = 左 440 渐变品牌区 + 右 660 表单区。
 */
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { MessageSquare, Send, ShieldCheck, Sparkles, Zap } from 'lucide-vue-next';
import HubButton from '@/components/base/HubButton.vue';
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
  { icon: Zap, title: '主动提醒', desc: '待办、异常、截止日，Agent 替你盯着' },
];

/* ---------- 登录 ---------- */
const loginTab = ref<'password' | 'code'>('password');
const loginName = ref('');
const password = ref('');
const loginError = ref('');
const loginSubmitting = ref(false);

/**
 * 登录按钮状态机：idle → loading(收缩成圆+spinner) → success(对勾)/error(叉) → idle。
 * width:auto 不可过渡，提交瞬间先锁显式宽度再切圆形态（morphToRound 双段内联宽度）。
 */
const loginBtnEl = ref<HTMLButtonElement | null>(null);
const loginState = ref<'idle' | 'loading' | 'success' | 'error'>('idle');

function morphToRound() {
  const btn = loginBtnEl.value;
  if (!btn) {
    loginState.value = 'loading';
    return;
  }
  btn.style.width = `${btn.getBoundingClientRect().width}px`;
  void btn.offsetWidth;
  loginState.value = 'loading';
  requestAnimationFrame(() => {
    btn.style.width = '44px';
  });
}

watch(loginState, (s) => {
  if (s === 'idle') {
    const btn = loginBtnEl.value;
    if (btn) btn.style.width = '';
  }
});

async function submitLogin() {
  if (loginState.value !== 'idle') return;
  if (loginTab.value === 'code') {
    showToast('验证码登录即将上线');
    return;
  }
  if (!loginName.value.trim() || !password.value) {
    loginError.value = '请输入工号和密码';
    return;
  }
  loginError.value = '';
  morphToRound();
  try {
    await session.login(loginName.value.trim(), password.value);
    await session.loadOrgProfile().catch(() => undefined);
    loginState.value = 'success';
    window.setTimeout(() => void router.replace('/messages'), 650);
  } catch (e) {
    loginState.value = 'error';
    loginError.value = e instanceof Error ? e.message : '登录失败';
    window.setTimeout(() => { loginState.value = 'idle'; }, 900);
  }
}

/* ---------- 注册（UI 对齐画布，接口待接 #12） ---------- */
const regName = ref('');
const regPhone = ref('');
const regCode = ref('');
const regPassword = ref('');
const regInvite = ref('');
const regAgree = ref(false);
const regSubmitting = ref(false);

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
  window.setTimeout(() => {
    regSubmitting.value = false;
    showToast('自助注册暂未开放，请联系管理员开通（接口需求 #12）');
  }, 400);
}
function sendSmsCode() {
  showToast('短信验证码发送即将上线');
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <!-- 品牌区 440 渐变 -->
      <aside class="auth-brand">
        <div class="auth-logo">
          <span class="auth-logo-mark">W</span>
          <strong>Workforce Hub</strong>
        </div>
        <h1 class="auth-slogan">智能人事协同<br />让工作更简单</h1>
        <p class="auth-slogan-sub">考勤 · 假期 · 审批 · 消息 · AI Agent，一个入口全部搞定</p>
        <span class="auth-brand-spacer" />
        <ul class="auth-features">
          <li v-for="f in FEATURES" :key="f.title">
            <span class="auth-feature-icon"><component :is="f.icon" :size="18" :stroke-width="2" /></span>
            <span class="auth-feature-copy">
              <strong>{{ f.title }}</strong>
              <small>{{ f.desc }}</small>
            </span>
          </li>
        </ul>
        <p class="auth-foot">私有化部署 · 数据自主可控</p>
      </aside>

      <!-- 表单区 660 -->
      <section class="auth-form">
        <!-- 登录 -->
        <template v-if="mode === 'login'">
          <h2>欢迎回来</h2>
          <p class="auth-form-sub">登录 Workforce Hub 员工端</p>

          <div class="auth-tabs">
            <button
              type="button" :class="{ active: loginTab === 'password' }"
              @click="loginTab = 'password'"
            >密码登录</button>
            <button
              type="button" :class="{ active: loginTab === 'code' }"
              @click="loginTab = 'code'"
            >验证码登录</button>
          </div>

          <template v-if="loginTab === 'password'">
            <form class="auth-fields" @submit.prevent="submitLogin">
              <label class="auth-field">
                工号 / 邮箱
                <input v-model="loginName" type="text" placeholder="请输入工号或邮箱" autocomplete="username" />
              </label>
              <label class="auth-field">
                密码
                <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
              </label>
              <div class="auth-forgot-row">
                <button type="button" class="auth-link" @click="showToast('找回密码即将上线')">忘记密码？</button>
              </div>
              <p v-if="loginError" class="auth-error">{{ loginError }}</p>
              <button
                ref="loginBtnEl"
                type="submit"
                class="auth-morph-btn"
                :class="[`auth-morph-${loginState}`, { round: loginState !== 'idle' }]"
                :disabled="loginState !== 'idle'"
              >
                <span class="auth-morph-label">登 录</span>
                <span v-if="loginState === 'loading'" class="auth-morph-spinner" aria-hidden="true" />
                <svg v-if="loginState === 'success'" class="auth-morph-mark" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path class="mark-path" d="M5 12.5l4.5 4.5L19 7.5" />
                </svg>
                <svg v-if="loginState === 'error'" class="auth-morph-mark mark-error" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path class="mark-path" d="M7 7l10 10M17 7L7 17" />
                </svg>
              </button>
            </form>
          </template>
          <div v-else class="auth-code-panel">
            <label class="auth-field">
              手机号 / 邮箱
              <input type="text" placeholder="请输入手机号或邮箱" disabled />
            </label>
            <label class="auth-field">
              验证码
              <input type="text" placeholder="验证码登录即将上线" disabled />
            </label>
            <HubButton variant="primary" disabled>登 录</HubButton>
          </div>

          <div class="auth-divider">
            <i />
            <small>其他登录方式</small>
            <i />
          </div>
          <button type="button" class="auth-wechat" @click="showToast('微信扫码登录即将上线')">
            <!-- 画布 icon-wechat-green：微信气泡双点图形 -->
            <svg class="auth-wechat-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M8.69 3.5C5.1 3.5 2.2 5.99 2.2 9.06c0 1.77 1 3.35 2.55 4.38l-.64 1.93 2.24-1.12c.68.19 1.42.3 2.19.31a5.05 5.05 0 0 1-.13-1.1c0-2.9 2.79-5.25 6.23-5.25.2 0 .4.01.6.03C14.61 5.5 11.93 3.5 8.69 3.5Zm-2.2 3.06a.86.86 0 1 1 0 1.72.86.86 0 0 1 0-1.72Zm4.4 0a.86.86 0 1 1 0 1.72.86.86 0 0 1 0-1.72Zm2.5 2.94c-3.02 0-5.47 1.99-5.47 4.44 0 2.45 2.45 4.44 5.47 4.44.6 0 1.18-.08 1.72-.23l1.94.97-.55-1.67c1.34-.81 2.2-2.08 2.2-3.51 0-2.45-2.29-4.44-5.31-4.44Zm-1.86 2.23a.74.74 0 1 1 0 1.48.74.74 0 0 1 0-1.48Zm3.72 0a.74.74 0 1 1 0 1.48.74.74 0 0 1 0-1.48Z" />
            </svg>
            微信扫码登录
          </button>

          <span class="auth-form-spacer" />
          <p class="auth-guide">
            还没有账号？
            <button type="button" class="auth-link" @click="router.push({ name: 'register' })">立即注册</button>
          </p>
        </template>

        <!-- 注册 -->
        <template v-else>
          <h2>创建账号</h2>
          <p class="auth-form-sub">注册后即可加入你的团队</p>

          <form class="auth-fields" @submit.prevent="submitRegister">
            <label class="auth-field">
              姓名
              <input v-model="regName" type="text" placeholder="请输入姓名" />
            </label>
            <label class="auth-field">
              手机号
              <input v-model="regPhone" type="tel" placeholder="请输入手机号" />
            </label>
            <label class="auth-field auth-field-code">
              短信验证码
              <span class="auth-code-row">
                <input v-model="regCode" type="text" placeholder="请输入验证码" />
                <button type="button" class="auth-link" @click="sendSmsCode">获取验证码</button>
              </span>
            </label>
            <label class="auth-field">
              设置密码
              <input v-model="regPassword" type="password" placeholder="请设置登录密码" autocomplete="new-password" />
            </label>
            <label class="auth-field">
              企业邀请码
              <input v-model="regInvite" type="text" placeholder="请输入企业邀请码（选填）" />
            </label>
            <label class="auth-agree">
              <input v-model="regAgree" type="checkbox" />
              <small>已阅读并同意《用户协议》和《隐私政策》</small>
            </label>
            <HubButton variant="primary" type="submit" :disabled="regSubmitting">
              {{ regSubmitting ? '注册中…' : '注 册' }}
            </HubButton>
          </form>

          <span class="auth-form-spacer" />
          <p class="auth-guide">
            已有账号？
            <button type="button" class="auth-link" @click="router.push({ name: 'login' })">去登录</button>
          </p>
        </template>
      </section>
    </div>
    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped>
.auth-page {
  display: grid;
  place-items: center;
  min-height: 100vh;
  background: var(--hw-canvas);
  padding: 32px;
}
.auth-card {
  display: flex;
  width: min(1100px, 100%);
  height: min(720px, calc(100vh - 64px));
  min-height: 560px;
  border-radius: 24px;
  background: var(--hw-card);
  overflow: hidden;
  box-shadow: 0 24px 64px rgba(15, 30, 55, 0.12);
}

/* 品牌区 440 */
.auth-brand {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 440px;
  flex: none;
  padding: 48px;
  background: linear-gradient(135deg, #1677ff 0%, #124da3 100%);
  color: #fff;
}
.auth-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}
.auth-logo-mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 16px;
  font-weight: 700;
}
.auth-logo strong { font-size: 20px; font-weight: 700; }
.auth-slogan {
  font-size: 30px;
  line-height: 42px;
  font-weight: 700;
}
.auth-slogan-sub {
  font-size: 13px;
  line-height: 20px;
  opacity: 0.85;
}
.auth-brand-spacer { flex: 0 0 24px; }
.auth-features {
  display: flex;
  flex-direction: column;
  gap: 18px;
  list-style: none;
}
.auth-features li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.auth-feature-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  flex: none;
}
.auth-feature-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.auth-feature-copy strong { font-size: 14px; }
.auth-feature-copy small { font-size: 12px; opacity: 0.75; }
.auth-foot {
  margin-top: auto;
  font-size: 12px;
  opacity: 0.6;
}

/* 表单区 660 */
.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
  padding: 56px 64px 40px;
  overflow-y: auto;
}
.auth-form h2 { font-size: 24px; font-weight: 700; }
.auth-form-sub {
  font-size: 13px;
  color: var(--hw-tertiary);
  margin-top: -8px;
}
.auth-tabs {
  display: flex;
  gap: 8px;
  margin: 8px 0 4px;
}
.auth-tabs button {
  padding: 8px 16px;
  border: 0;
  border-radius: 999px;
  background: var(--hw-chip);
  color: var(--hw-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.auth-tabs button.active {
  background: var(--hw-pill-dark);
  color: #fff;
}
.auth-fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.auth-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: var(--hw-secondary);
}
.auth-field input {
  height: 46px;
  padding: 0 16px;
  border: 0;
  border-radius: 12px;
  outline: none;
  background: var(--hw-inner);
  font-size: 14px;
  color: var(--hw-ink);
  transition: box-shadow 0.2s;
}
.auth-field input:focus { box-shadow: 0 0 0 2px var(--hw-sky-strong); }
.auth-field input::placeholder { color: var(--hw-muted); }
.auth-field input:disabled { color: var(--hw-muted); cursor: not-allowed; }
.auth-code-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.auth-code-row input { flex: 1; }
.auth-forgot-row {
  display: flex;
  justify-content: flex-end;
  margin-top: -6px;
}
.auth-link {
  border: 0;
  background: transparent;
  color: var(--hw-primary);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
}
.auth-link:hover { opacity: 0.8; }
.auth-error { font-size: 12px; color: var(--hw-red); }
.auth-code-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.auth-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0;
}
.auth-divider i {
  flex: 1;
  height: 1px;
  background: var(--hw-track);
}
.auth-divider small {
  font-size: 12px;
  color: var(--hw-muted);
}
.auth-wechat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 46px;
  border: 1px solid var(--hw-track);
  border-radius: 12px;
  background: var(--hw-card);
  color: var(--hw-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: background 0.2s;
}
.auth-wechat:hover { background: var(--hw-chip); }
.auth-form-spacer { flex: 1; min-height: 12px; }
.auth-guide {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  color: var(--hw-tertiary);
}
.auth-agree {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.auth-agree small { font-size: 12px; color: var(--hw-tertiary); }

@media (max-width: 960px) {
  .auth-brand { display: none; }
  .auth-card { width: min(480px, 100%); height: auto; }
}

/* 登录按钮收缩 morph：胶囊→圆→spinner→对勾/叉 */
.auth-morph-btn {
  position: relative;
  display: grid;
  place-items: center;
  height: 44px;
  border: 0;
  border-radius: 12px;
  background: var(--hw-primary);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  overflow: hidden;
  transition:
    width 0.42s var(--hw-morph, cubic-bezier(0.65, 0, 0.35, 1)),
    border-radius 0.42s var(--hw-morph, cubic-bezier(0.65, 0, 0.35, 1)),
    background 0.3s var(--hw-ease),
    box-shadow 0.3s var(--hw-ease);
}
.auth-morph-btn:hover:enabled:not(.round) {
  background: var(--hw-primary-deep);
  box-shadow: 0 8px 20px rgba(22, 119, 255, 0.32);
}
.auth-morph-btn.round {
  margin-inline: auto;
  border-radius: 999px;
  cursor: wait;
  box-shadow: 0 8px 20px rgba(22, 119, 255, 0.30);
}
.auth-morph-label {
  white-space: nowrap;
  transition: opacity 0.22s var(--hw-ease);
}
.auth-morph-btn.round .auth-morph-label {
  opacity: 0;
  width: 0;
  overflow: hidden;
}
.auth-morph-spinner {
  position: absolute;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2.5px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  opacity: 0;
  animation: auth-spin 0.7s linear infinite, auth-spin-in 0.3s var(--hw-ease) 0.25s forwards;
}
.auth-morph-mark {
  position: absolute;
  width: 22px;
  height: 22px;
}
.auth-morph-mark .mark-path {
  stroke: #fff;
  stroke-width: 2.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 30;
  stroke-dashoffset: 30;
  animation: auth-mark-draw 0.32s var(--hw-ease) 0.05s forwards;
}
.mark-error .mark-path {
  stroke-dasharray: 15;
  stroke-dashoffset: 15;
}
.auth-morph-success {
  background: #22a05c;
  transition: background 0.3s var(--hw-ease) 0.3s;
  animation: auth-mark-bounce 0.45s var(--hw-spring) 0.55s;
}
.auth-morph-error {
  background: var(--hw-red);
  transition: background 0.3s var(--hw-ease) 0.3s;
  animation: auth-mark-bounce 0.45s var(--hw-spring) 0.55s;
}
.auth-morph-error .auth-morph-spinner {
  opacity: 0;
  animation: none;
}
@keyframes auth-spin { to { transform: rotate(360deg); } }
@keyframes auth-spin-in { to { opacity: 1; } }
@keyframes auth-mark-draw { to { stroke-dashoffset: 0; } }
@keyframes auth-mark-bounce {
  0% { transform: scale(1); }
  45% { transform: scale(1.12); }
  100% { transform: scale(1); }
}

/* 微信图标：画布 wechat 绿 */
.auth-wechat-icon {
  width: 18px;
  height: 18px;
  color: #22c05e;
  flex: none;
}
</style>
