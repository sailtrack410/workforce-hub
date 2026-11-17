<template>
  <div class="auth-page" :data-theme="theme">
    <main class="auth-card">
      <!-- 品牌区：440 宽满高，135° 蓝渐变(#1677ff→#124da3)，内边距 48，gap 18 -->
      <aside class="brand-pane">
        <div class="brand-logo">
          <div class="brand-avatar">W</div>
          <strong>Workforce Hub</strong>
        </div>

        <h1 class="brand-slogan">智能人事协同<br>让工作更简单</h1>
        <p class="brand-subtitle">考勤 · 假期 · 审批 · 消息 · AI Agent，一个入口全部搞定</p>

        <div class="brand-spacer" />

        <ul class="brand-features">
          <li v-for="f in features" :key="f.name" class="feature-row">
            <span class="feature-icon" aria-hidden="true">
              <Sparkles :size="20" :stroke-width="2" />
            </span>
            <span class="feature-copy">
              <strong>{{ f.name }}</strong>
              <small>{{ f.desc }}</small>
            </span>
          </li>
        </ul>

        <p class="brand-foot">私有化部署 · 数据自主可控</p>
      </aside>

      <!-- 表单区：内边距 56，gap 16 -->
      <section class="form-pane">
        <!-- 登录视图 -->
        <template v-if="view === 'login' || view === 'code'">
          <h2 class="pane-title">欢迎回来</h2>
          <p class="pane-subtitle">登录 Workforce Hub 员工端</p>

          <!-- 登录方式：胶囊 Tab，激活=深底白字(pill-dark #181D23)，未激活=bg-chip -->
          <div class="mode-tabs" role="tablist">
            <button type="button" role="tab" class="mode-tab" :class="{ active: view === 'login' }" :aria-selected="view === 'login'" @click="switchLoginMode('login')">密码登录</button>
            <button type="button" role="tab" class="mode-tab" :class="{ active: view === 'code' }" :aria-selected="view === 'code'" @click="switchLoginMode('code')">验证码登录</button>
          </div>

          <form class="pane-form" :class="{ 'pane-form-code': view === 'code' }" @submit.prevent="submitLogin">
            <!-- 密码登录：工号/邮箱 + 密码；验证码登录：工号/邮箱 + 验证码(接口预留 sendLoginCode) -->
            <div class="field">
              <label class="field-label" for="login-identifier">工号 / 邮箱</label>
              <div class="field-input input-48" :class="{ focused: focusedField === 'identifier' }">
                <input id="login-identifier" ref="identifierEl" v-model.trim="loginForm.identifier" type="text" name="username" autocomplete="username" placeholder="请输入工号或企业邮箱" :disabled="status === 'loading'" @focus="focusedField = 'identifier'" @blur="focusedField = ''">
              </div>
            </div>

            <template v-if="view === 'login'">
              <div class="field">
                <label class="field-label" for="login-password">密码</label>
                <div class="field-input input-48" :class="{ focused: focusedField === 'password' }">
                  <input id="login-password" ref="passwordEl" v-model="loginForm.password" :type="showPassword ? 'text' : 'password'" name="password" autocomplete="current-password" placeholder="请输入密码" :disabled="status === 'loading'" @focus="focusedField = 'password'" @blur="focusedField = ''">
                  <button type="button" class="field-eye" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                    <Eye v-if="showPassword" :size="16" :stroke-width="2" />
                    <EyeOff v-else :size="16" :stroke-width="2" />
                  </button>
                </div>
              </div>
              <button type="button" class="link-right" @click="switchView('reset')">忘记密码？</button>
            </template>

            <template v-else>
              <div class="field">
                <label class="field-label" for="login-email-code">邮箱验证码</label>
                <div class="field-input input-48" :class="{ focused: focusedField === 'emailCode' }">
                  <input id="login-email-code" v-model.trim="loginForm.emailCode" type="text" inputmode="numeric" maxlength="6" placeholder="请输入 6 位验证码" :disabled="status === 'loading'" @focus="focusedField = 'emailCode'" @blur="focusedField = ''">
                </div>
              </div>
              <button type="button" class="link-right" :disabled="codeCooldown > 0" @click="sendLoginEmailCode">
                {{ codeCooldown > 0 ? `${codeCooldown}s 后重发` : '发送验证码' }}
              </button>
            </template>

            <Transition name="msg-pop">
              <p v-if="status === 'error' && errorMessage" class="form-error" role="alert">{{ errorMessage }}</p>
            </Transition>

            <!-- 主提交按钮：高度 44，圆角 12；morph 圆 → spinner → 对勾/叉 -->
            <button ref="submitBtnEl" type="submit" class="submit-btn" :class="[`submit-${status}`, { round: status !== 'idle' }]" :disabled="status !== 'idle'">
              <span class="submit-label">{{ submitLabel }}</span>
              <span v-if="status === 'loading'" class="submit-spinner" aria-hidden="true" />
              <svg v-if="status === 'success'" class="submit-mark" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path class="mark-path" d="M5 12.5l4.5 4.5L19 7.5" />
              </svg>
              <svg v-if="status === 'error'" class="submit-mark mark-error" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path class="mark-path" d="M7 7l10 10M17 7L7 17" />
              </svg>
            </button>
          </form>

          <!-- 分隔区：1px 线(bg-track) + 12px 文字 + 线 -->
          <div class="divider">
            <span class="divider-line" />
            <span class="divider-text">其他登录方式</span>
            <span class="divider-line" />
          </div>

          <!-- 微信登录：描边按钮(bg-card + #d6dee8)，高度 50 圆角 12（接口预留） -->
          <button type="button" class="wechat-btn" @click="onWechatLogin">
            <svg class="wechat-icon" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d="M8.69 3.5C5.1 3.5 2.2 5.99 2.2 9.06c0 1.77 1 3.35 2.55 4.38l-.64 1.93 2.24-1.12c.68.19 1.42.3 2.19.31a5.05 5.05 0 0 1-.13-1.1c0-2.9 2.79-5.25 6.23-5.25.2 0 .4.01.6.03C14.61 5.5 11.93 3.5 8.69 3.5Zm-2.2 3.06a.86.86 0 1 1 0 1.72.86.86 0 0 1 0-1.72Zm4.4 0a.86.86 0 1 1 0 1.72.86.86 0 0 1 0-1.72Zm2.5 2.94c-3.02 0-5.47 1.99-5.47 4.44 0 2.45 2.45 4.44 5.47 4.44.6 0 1.18-.08 1.72-.23l1.94.97-.55-1.67c1.34-.81 2.2-2.08 2.2-3.51 0-2.45-2.29-4.44-5.31-4.44Zm-1.86 2.23a.74.74 0 1 1 0 1.48.74.74 0 0 1 0-1.48Zm3.72 0a.74.74 0 1 1 0 1.48.74.74 0 0 1 0-1.48Z"/>
            </svg>
            微信扫码登录
          </button>

          <div class="switch-guide">
            <span>还没有账号？</span>
            <button type="button" class="guide-link" @click="switchView('register')">立即注册</button>
          </div>
        </template>

        <!-- 注册视图：姓名/手机号/短信验证码/设置密码/企业邀请码 + 协议（接口预留） -->
        <template v-else-if="view === 'register'">
          <h2 class="pane-title">创建账号</h2>
          <p class="pane-subtitle">注册后即可加入你的团队</p>

          <form class="pane-form pane-form-register" @submit.prevent="submitRegister">
            <div class="field">
              <label class="field-label" for="reg-name">姓名</label>
              <div class="field-input input-44" :class="{ focused: focusedField === 'name' }">
                <input id="reg-name" v-model.trim="registerForm.name" type="text" autocomplete="name" placeholder="请输入真实姓名" :disabled="registerStatus === 'submitting'" @focus="focusedField = 'name'" @blur="focusedField = ''">
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="reg-mobile">手机号</label>
              <div class="field-input input-44" :class="{ focused: focusedField === 'mobile' }">
                <input id="reg-mobile" v-model.trim="registerForm.mobile" type="tel" inputmode="numeric" maxlength="11" autocomplete="tel" placeholder="请输入手机号" :disabled="registerStatus === 'submitting'" @focus="focusedField = 'mobile'" @blur="focusedField = ''">
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="reg-sms">短信验证码</label>
              <div class="sms-row">
                <div class="field-input input-44 sms-input" :class="{ focused: focusedField === 'smsCode' }">
                  <input id="reg-sms" v-model.trim="registerForm.smsCode" type="text" inputmode="numeric" maxlength="6" placeholder="请输入 6 位验证码" :disabled="registerStatus === 'submitting'" @focus="focusedField = 'smsCode'" @blur="focusedField = ''">
                </div>
                <button type="button" class="sms-send" :disabled="smsCooldown > 0" @click="sendSmsCode">{{ smsCooldown > 0 ? `${smsCooldown}s` : '发送验证码' }}</button>
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="reg-password">设置密码</label>
              <div class="field-input input-44" :class="{ focused: focusedField === 'regPassword' }">
                <input id="reg-password" v-model="registerForm.password" type="password" autocomplete="new-password" placeholder="8 位以上，含字母和数字" :disabled="registerStatus === 'submitting'" @focus="focusedField = 'regPassword'" @blur="focusedField = ''">
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="reg-invite">企业邀请码</label>
              <div class="field-input input-44" :class="{ focused: focusedField === 'inviteCode' }">
                <input id="reg-invite" v-model.trim="registerForm.inviteCode" type="text" placeholder="请向管理员索取邀请码" :disabled="registerStatus === 'submitting'" @focus="focusedField = 'inviteCode'" @blur="focusedField = ''">
              </div>
            </div>

            <label class="agreement">
              <input v-model="registerForm.agreed" type="checkbox" class="agreement-check">
              <span>已阅读并同意<button type="button" class="agreement-link" @click.stop>《用户协议》</button>和<button type="button" class="agreement-link" @click.stop>《隐私政策》</button></span>
            </label>

            <Transition name="msg-pop">
              <p v-if="registerMessage" class="form-error" :class="{ 'form-ok': registerStatus === 'sent' }" role="alert">{{ registerMessage }}</p>
            </Transition>

            <button type="submit" class="submit-btn" :class="[`submit-${registerStatus === 'submitting' ? 'loading' : registerStatus === 'done' ? 'success' : registerStatus === 'failed' ? 'error' : 'idle'}`, { round: registerStatus === 'submitting' || registerStatus === 'done' }]" :disabled="registerStatus === 'submitting' || registerStatus === 'done'">
              <span class="submit-label">{{ registerSubmitLabel }}</span>
              <span v-if="registerStatus === 'submitting'" class="submit-spinner" aria-hidden="true" />
              <svg v-if="registerStatus === 'done'" class="submit-mark" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path class="mark-path" d="M5 12.5l4.5 4.5L19 7.5" />
              </svg>
            </button>
          </form>

          <div class="switch-guide">
            <span>已有账号？</span>
            <button type="button" class="guide-link" @click="switchView('login')">直接登录</button>
          </div>
        </template>

        <!-- 找回密码视图（画布外补充，沿用密码登录视觉参数） -->
        <template v-else>
          <h2 class="pane-title">找回登录密码</h2>
          <p class="pane-subtitle">输入绑定邮箱完成验证后重设</p>

          <form class="pane-form" @submit.prevent="submitReset">
            <div class="field">
              <label class="field-label" for="reset-email">绑定邮箱</label>
              <div class="field-input input-48" :class="{ focused: focusedField === 'email' }">
                <input id="reset-email" v-model.trim="resetForm.email" type="email" autocomplete="email" placeholder="请输入企业邮箱" :disabled="resetStatus === 'sending' || resetStatus === 'submitting'" @focus="focusedField = 'email'" @blur="focusedField = ''">
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="reset-code">邮箱验证码</label>
              <div class="sms-row">
                <div class="field-input input-48 sms-input" :class="{ focused: focusedField === 'resetCode' }">
                  <input id="reset-code" v-model.trim="resetForm.code" type="text" inputmode="numeric" maxlength="6" placeholder="请输入 6 位验证码" :disabled="resetStatus === 'sending' || resetStatus === 'submitting'" @focus="focusedField = 'resetCode'" @blur="focusedField = ''">
                </div>
                <button type="button" class="sms-send" :disabled="resetCooldown > 0 || resetStatus === 'sending'" @click="sendResetCode">{{ resetCooldown > 0 ? `${resetCooldown}s` : '发送验证码' }}</button>
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="reset-new-password">新密码</label>
              <div class="field-input input-48" :class="{ focused: focusedField === 'newPassword' }">
                <input id="reset-new-password" v-model="resetForm.newPassword" type="password" autocomplete="new-password" placeholder="8 位以上，含字母和数字" :disabled="resetStatus === 'sending' || resetStatus === 'submitting'" @focus="focusedField = 'newPassword'" @blur="focusedField = ''">
              </div>
            </div>

            <Transition name="msg-pop">
              <p v-if="resetMessage" class="form-error" :class="{ 'form-ok': resetStatus === 'sent' }" role="alert">{{ resetMessage }}</p>
            </Transition>

            <button type="submit" class="submit-btn" :class="[`submit-${resetStatus === 'submitting' ? 'loading' : resetStatus === 'done' ? 'success' : resetStatus === 'failed' ? 'error' : 'idle'}`, { round: resetStatus === 'submitting' || resetStatus === 'done' }]" :disabled="resetStatus === 'sending' || resetStatus === 'submitting' || resetStatus === 'done'">
              <span class="submit-label">{{ resetSubmitLabel }}</span>
              <span v-if="resetStatus === 'submitting'" class="submit-spinner" aria-hidden="true" />
              <svg v-if="resetStatus === 'done'" class="submit-mark" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path class="mark-path" d="M5 12.5l4.5 4.5L19 7.5" />
              </svg>
            </button>
          </form>

          <div class="switch-guide">
            <span>想起密码了？</span>
            <button type="button" class="guide-link" @click="switchView('login')">返回登录</button>
          </div>
        </template>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { Eye, EyeOff, Sparkles } from 'lucide-vue-next';
import { employeeApi } from '@/api';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/session';

defineProps<{
  /** 主题继承自 shell store，登录页仅透传给根节点 */
  theme?: string;
}>();

const session = useSessionStore();
const router = useRouter();

/* 品牌区特性：文案来自画布「员工端-登录注册」品牌区特性行 */
const features = [
  { name: 'AI 预审', desc: '审批提交秒级预审，硬规则自动校验' },
  { name: '@Agent 问答', desc: '制度、假期、流程，随时问随时答' },
  { name: '主动提醒', desc: '待办、异常、截止日，Agent 替你盯着' },
];

/* ---------- 视图状态：login=密码登录 / code=验证码登录 / register / reset ---------- */
type AuthView = 'login' | 'code' | 'register' | 'reset';
const view = ref<AuthView>('login');
const focusedField = ref('');
const showPassword = ref(false);
const identifierEl = ref<HTMLInputElement | null>(null);
const passwordEl = ref<HTMLInputElement | null>(null);
const submitBtnEl = ref<HTMLButtonElement | null>(null);

/**
 * 收缩动画前置：width:auto→44px 无法产生 CSS 过渡，
 * 提交瞬间先把当前渲染宽度锁成显式 px，下一帧再切到圆形态。
 */
function morphToRound() {
  const btn = submitBtnEl.value;
  if (!btn) return;
  // 先锁起始宽度(显式 px,transition 才有可插值起点)
  btn.style.width = `${btn.getBoundingClientRect().width}px`;
  void btn.offsetWidth;
  status.value = 'loading';
  // 下一帧把目标宽度也设为显式 44px(内联优先于 class,必须显式覆盖才能触发 548→44 过渡)
  requestAnimationFrame(() => {
    btn.style.width = '44px';
  });
}

function switchView(next: AuthView) {
  view.value = next;
  status.value = 'idle';
  errorMessage.value = '';
  registerMessage.value = '';
  registerStatus.value = 'idle';
  resetMessage.value = '';
  resetStatus.value = 'idle';
}

function switchLoginMode(next: 'login' | 'code') {
  view.value = next;
  status.value = 'idle';
  errorMessage.value = '';
}

/* ---------- 密码 / 验证码登录（接口：POST /api/employee/auth/login） ---------- */
const status = ref<'idle' | 'loading' | 'success' | 'error'>('idle');
const errorMessage = ref('');
const codeCooldown = ref(0);
const loginForm = ref({ identifier: '', password: '', emailCode: '' });
let codeTimer: number | undefined;

const submitLabel = computed(() => {
  if (status.value !== 'idle') return '';
  return '登 录';
});

// 回到 idle 时清除锁定宽度，按钮才能过渡回全宽
watch(status, (s) => {
  if (s === 'idle') {
    const btn = submitBtnEl.value;
    if (btn) btn.style.width = '';
  }
});

async function sendLoginEmailCode() {
  if (!loginForm.value.identifier) {
    errorMessage.value = '请先输入工号或企业邮箱';
    window.setTimeout(() => { errorMessage.value = ''; }, 1800);
    return;
  }
  try {
    // 接口预留：POST /api/employee/auth/login/send-code
    await employeeApi.auth.sendLoginCode({ identifier: loginForm.value.identifier });
    codeCooldown.value = 60;
    codeTimer = window.setInterval(() => {
      codeCooldown.value -= 1;
      if (codeCooldown.value <= 0) window.clearInterval(codeTimer);
    }, 1000);
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '验证码发送失败，请稍后重试';
    window.setTimeout(() => { errorMessage.value = ''; }, 1800);
  }
}

async function submitLogin() {
  if (status.value === 'loading' || status.value === 'success') return;
  if (!loginForm.value.identifier) {
    errorMessage.value = '请输入工号或企业邮箱';
    window.setTimeout(() => { errorMessage.value = ''; }, 1800);
    identifierEl.value?.focus();
    return;
  }
  if (view.value === 'login' && !loginForm.value.password) {
    errorMessage.value = '请输入密码';
    window.setTimeout(() => { errorMessage.value = ''; }, 1800);
    passwordEl.value?.focus();
    return;
  }
  if (view.value === 'code' && !loginForm.value.emailCode) {
    errorMessage.value = '请输入邮箱验证码';
    window.setTimeout(() => { errorMessage.value = ''; }, 1800);
    return;
  }
  morphToRound();
  errorMessage.value = '';
  try {
    // 验证码登录暂复用密码登录通道，emailCode 随请求下发；服务端支持后无需改前端
    const result = await session.login({
      loginName: loginForm.value.identifier,
      password: loginForm.value.password || `email-code:${loginForm.value.emailCode}`,
      emailCode: loginForm.value.emailCode || undefined,
    });
    if (result.requiresEmailCode) {
      switchLoginMode('code');
      status.value = 'idle';
      return;
    }
    status.value = 'success';
    window.setTimeout(() => router.push('/messages'), 650);
  } catch (err) {
    status.value = 'error';
    errorMessage.value = err instanceof Error ? err.message : '登录失败，请检查账号或密码';
    // 圆内画叉停留 900ms 让失败反馈看清楚，再弹回全宽恢复按钮
    window.setTimeout(() => {
      status.value = 'idle';
      errorMessage.value = '';
    }, 900);
  }
}

/* 微信扫码登录：后端未提供微信 OAuth，占位提示 */
function onWechatLogin() {
  status.value = 'error';
  errorMessage.value = '微信扫码登录暂未开放，请使用密码登录';
  window.setTimeout(() => {
    status.value = 'idle';
    errorMessage.value = '';
  }, 900);
}

/* ---------- 注册（接口预留：后端无注册接口，提交时占位提示） ---------- */
const registerStatus = ref<'idle' | 'sending' | 'submitting' | 'sent' | 'done' | 'failed'>('idle');
const registerMessage = ref('');
const smsCooldown = ref(0);
const registerForm = ref({ name: '', mobile: '', smsCode: '', password: '', inviteCode: '', agreed: false });
let smsTimer: number | undefined;

const registerSubmitLabel = computed(() => {
  if (registerStatus.value === 'submitting' || registerStatus.value === 'done') return '';
  return '注 册';
});

function sendSmsCode() {
  if (!/^1\d{10}$/.test(registerForm.value.mobile)) {
    registerStatus.value = 'failed';
    registerMessage.value = '请先输入正确的手机号';
    return;
  }
  registerStatus.value = 'sent';
  registerMessage.value = '验证码已发送（演示环境短信通道未接入）';
  smsCooldown.value = 60;
  smsTimer = window.setInterval(() => {
    smsCooldown.value -= 1;
    if (smsCooldown.value <= 0) window.clearInterval(smsTimer);
  }, 1000);
}

async function submitRegister() {
  if (registerStatus.value === 'submitting' || registerStatus.value === 'done') return;
  const f = registerForm.value;
  if (!f.name || !/^1\d{10}$/.test(f.mobile) || !f.smsCode || f.password.length < 8 || !f.inviteCode) {
    registerStatus.value = 'failed';
    registerMessage.value = '请完整填写各项信息（密码至少 8 位）';
    return;
  }
  if (!f.agreed) {
    registerStatus.value = 'failed';
    registerMessage.value = '请先勾选同意《用户协议》和《隐私政策》';
    return;
  }
  // 接口预留：后端注册接口未开放，先走提交动效后提示，避免假成功
  registerStatus.value = 'submitting';
  registerMessage.value = '';
  await new Promise((r) => window.setTimeout(r, 900));
  registerStatus.value = 'failed';
  registerMessage.value = '注册通道暂未开放，请联系管理员在后台开通账号';
}

/* ---------- 找回密码（接口：email/send-code + password/reset） ---------- */
const resetStatus = ref<'idle' | 'sending' | 'submitting' | 'sent' | 'done' | 'failed'>('idle');
const resetMessage = ref('');
const resetCooldown = ref(0);
const resetForm = ref({ email: '', code: '', newPassword: '' });
let resetTimer: number | undefined;

const resetSubmitLabel = computed(() => {
  if (resetStatus.value === 'submitting' || resetStatus.value === 'done') return '';
  return '重设密码';
});

async function sendResetCode() {
  if (!resetForm.value.email) {
    resetStatus.value = 'failed';
    resetMessage.value = '请先输入绑定邮箱';
    return;
  }
  resetStatus.value = 'sending';
  resetMessage.value = '';
  try {
    await employeeApi.auth.sendLoginCode({ identifier: resetForm.value.email });
    resetStatus.value = 'sent';
    startResetCooldown();
    resetMessage.value = '验证码已发送，请查收邮箱';
  } catch (err) {
    resetStatus.value = 'failed';
    resetMessage.value = err instanceof Error ? err.message : '验证码发送失败，请稍后重试';
  }
}

function startResetCooldown() {
  resetCooldown.value = 60;
  resetTimer = window.setInterval(() => {
    resetCooldown.value -= 1;
    if (resetCooldown.value <= 0) window.clearInterval(resetTimer);
  }, 1000);
}

async function submitReset() {
  if (resetStatus.value === 'submitting' || resetStatus.value === 'done') return;
  const f = resetForm.value;
  if (!f.email || !f.code || f.newPassword.length < 8) {
    resetStatus.value = 'failed';
    resetMessage.value = '请完整填写邮箱、验证码与新密码（至少 8 位）';
    return;
  }
  resetStatus.value = 'submitting';
  resetMessage.value = '';
  try {
    await session.resetPassword({ email: f.email, code: f.code, newPassword: f.newPassword });
    resetStatus.value = 'done';
    window.setTimeout(() => switchView('login'), 900);
  } catch (err) {
    resetStatus.value = 'failed';
    resetMessage.value = err instanceof Error ? err.message : '密码重设失败，请检查验证码';
  }
}

onBeforeUnmount(() => {
  window.clearInterval(codeTimer);
  window.clearInterval(smsTimer);
  window.clearInterval(resetTimer);
});
</script>

<style scoped src="../styles/auth-page.css" />
