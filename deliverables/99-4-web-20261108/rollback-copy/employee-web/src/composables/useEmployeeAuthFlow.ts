import { computed, ref } from 'vue';
import { employeeApi } from '@/api';
import type { useSessionStore } from '@/stores/session';
import { createEmailCodeCooldown } from './emailCodeCooldown';
import { loginEmailCodeSubtitle } from '@/utils/auth-login-ui';

interface UseEmployeeAuthFlowOptions {
  session: ReturnType<typeof useSessionStore>;
  loadAuthenticatedWorkspace: () => Promise<void>;
}

export function useEmployeeAuthFlow({ session, loadAuthenticatedWorkspace }: UseEmployeeAuthFlowOptions) {
  const authMode = ref<'login' | 'reset'>('login');
  const loginName = ref('');
  const password = ref('');
  const loginEmailCode = ref('');
  const loginRequiresEmailCode = ref(false);
  const loginRequiresEmailBinding = ref(false);
  const loginBindEmail = ref('');
  const loginMaskedEmail = ref('');
  const loginCodeSending = ref(false);
  const loginCodeCooldown = createEmailCodeCooldown();
  const resetEmail = ref('');
  const resetCode = ref('');
  const resetPasswordValue = ref('');
  const resetCodeSending = ref(false);
  const resetCodeCooldown = createEmailCodeCooldown();
  const authNotice = ref('');

  const authModeTitle = computed(() => {
    if (authMode.value === 'reset') return '找回密码';
    if (loginRequiresEmailBinding.value) return '绑定邮箱';
    if (loginRequiresEmailCode.value) return '邮箱验证';
    return '登录';
  });
  const authModeSubtitle = computed(() => {
    if (authMode.value === 'reset') return '';
    if (loginRequiresEmailBinding.value) return '首次登录需要绑定邮箱';
    if (loginRequiresEmailCode.value) return loginEmailCodeSubtitle({
      requiresEmailCode: loginRequiresEmailCode.value,
      maskedEmail: loginMaskedEmail.value,
      sending: loginCodeSending.value,
      cooldownActive: loginCodeCooldown.isActive.value,
    });
    return '';
  });
  const loginCodeButtonText = computed(() => {
    if (loginCodeSending.value) return '发送中';
    if (loginCodeCooldown.isActive.value) return loginCodeCooldown.label.value;
    return loginRequiresEmailBinding.value ? '发送验证码' : '重新发送';
  });
  const resetCodeButtonText = computed(() => (resetCodeSending.value ? '发送中' : resetCodeCooldown.label.value));

  function setAuthMode(mode: 'login' | 'reset') {
    authMode.value = mode;
    session.error = '';
    authNotice.value = '';
    if (mode === 'login') {
      resetCodeCooldown.reset();
      loginRequiresEmailCode.value = false;
      loginRequiresEmailBinding.value = false;
      loginEmailCode.value = '';
      loginBindEmail.value = '';
      loginMaskedEmail.value = '';
    } else {
      loginCodeCooldown.reset();
    }
  }

  // 登录流程是多步的：先密码验证，再根据后端返回判断是否需要邮箱二验或绑定
  async function submitLogin() {
    authNotice.value = '';
    try {
      const result = await session.login({
        loginName: loginName.value.trim(),
        password: password.value,
        emailCode: loginRequiresEmailCode.value || loginRequiresEmailBinding.value ? loginEmailCode.value.trim() : undefined,
        bindEmail: loginRequiresEmailBinding.value ? loginBindEmail.value.trim() : undefined,
      });
      // 后端返回 requiresEmailBinding 说明未绑定邮箱，需要先完成邮箱绑定
      if (result?.requiresEmailBinding) {
        loginRequiresEmailBinding.value = true;
        loginRequiresEmailCode.value = false;
        loginMaskedEmail.value = '';
        authNotice.value = '请输入邮箱并获取验证码';
        return;
      }
      // 后端返回 requiresEmailCode 说明已绑定邮箱，需要输入验证码
      if (result?.requiresEmailCode) {
        loginRequiresEmailCode.value = true;
        loginRequiresEmailBinding.value = false;
        loginMaskedEmail.value = result.maskedEmail ?? '';
        await sendLoginCode();
        return;
      }
      // 登录成功，清空敏感字段并加载工作区
      password.value = '';
      loginEmailCode.value = '';
      loginRequiresEmailCode.value = false;
      loginRequiresEmailBinding.value = false;
      loginBindEmail.value = '';
      loginMaskedEmail.value = '';
      loginCodeCooldown.reset();
      await loadAuthenticatedWorkspace();
    } catch {
      // session.login() 内部已赋值 session.error 并 re-throw；此处兜底：若 session.error 为空则设置通用提示
      if (!session.error) session.error = '登录失败，请稍后重试';
    }
  }

  async function sendLoginCode() {
    const identifier = loginRequiresEmailBinding.value ? loginBindEmail.value.trim() : loginName.value.trim();
    if (!identifier || loginCodeSending.value || loginCodeCooldown.isActive.value) return;
    loginCodeSending.value = true;
    authNotice.value = '';
    try {
      if (loginRequiresEmailBinding.value) {
        await employeeApi.auth.sendEmailCode({ email: identifier, purpose: 'BIND' });
        loginCodeCooldown.start();
        authNotice.value = '验证码已发送，60 秒后可再次发送';
        return;
      }
      const result = await session.sendLoginCode(identifier);
      if (!result.maskedEmail) {
        loginRequiresEmailCode.value = false;
        loginMaskedEmail.value = '';
        authNotice.value = '当前账号未绑定邮箱';
        return;
      }
      loginCodeCooldown.start();
      loginRequiresEmailCode.value = true;
      loginMaskedEmail.value = result.maskedEmail;
      authNotice.value = `验证码已发送至 ${result.maskedEmail}，60 秒后可再次发送`;
    } catch (error) {
      if (!session.error && error instanceof Error) session.error = error.message;
    } finally {
      loginCodeSending.value = false;
    }
  }

  async function sendResetCode() {
    const email = resetEmail.value.trim();
    if (!email || resetCodeSending.value || resetCodeCooldown.isActive.value) return;
    resetCodeSending.value = true;
    authNotice.value = '';
    try {
      await session.sendPasswordResetCode(email);
      resetCodeCooldown.start();
      authNotice.value = '验证码已发送，60 秒后可再次发送';
    } catch (error) {
      if (!session.error && error instanceof Error) session.error = error.message;
    } finally {
      resetCodeSending.value = false;
    }
  }

  async function submitPasswordReset() {
    authNotice.value = '';
    try {
      await session.resetPassword({
        email: resetEmail.value.trim(),
        code: resetCode.value.trim(),
        newPassword: resetPasswordValue.value,
      });
      resetCode.value = '';
      resetPasswordValue.value = '';
      authNotice.value = '密码已重置';
      authMode.value = 'login';
    } catch {
      // session.resetPassword() 内部已赋值 session.error 并 re-throw；此处兜底
      if (!session.error) session.error = '密码重置失败，请稍后重试';
    }
  }

  function resetAuthCooldowns() {
    loginCodeCooldown.reset();
    resetCodeCooldown.reset();
  }

  return {
    authMode,
    loginName,
    password,
    loginEmailCode,
    loginRequiresEmailCode,
    loginRequiresEmailBinding,
    loginBindEmail,
    loginMaskedEmail,
    loginCodeSending,
    loginCodeCooldown,
    resetEmail,
    resetCode,
    resetPasswordValue,
    resetCodeSending,
    resetCodeCooldown,
    authNotice,
    authModeTitle,
    authModeSubtitle,
    loginCodeButtonText,
    resetCodeButtonText,
    setAuthMode,
    submitLogin,
    sendLoginCode,
    sendResetCode,
    submitPasswordReset,
    resetAuthCooldowns,
  };
}
