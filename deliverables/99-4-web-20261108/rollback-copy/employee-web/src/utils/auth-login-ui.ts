export interface LoginEmailCodeSubtitleState {
  requiresEmailCode: boolean;
  maskedEmail?: string;
  sending: boolean;
  cooldownActive: boolean;
}

export function loginEmailCodeSubtitle(state: LoginEmailCodeSubtitleState) {
  if (!state.requiresEmailCode) return '';
  if (state.sending) return '发送中';
  if (state.cooldownActive && state.maskedEmail) return `验证码已发送至 ${state.maskedEmail}`;
  return '请输入邮箱验证码';
}
