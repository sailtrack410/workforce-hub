import { computed, onBeforeUnmount, ref } from 'vue';
import { authApi } from '@/api';

export function useEmailCode(email: () => string, purpose: 'BIND' | 'RESET_PASSWORD') {
  const sending = ref(false), nextSend = ref(0), now = ref(Date.now()), sentTo = ref('');
  let timer: ReturnType<typeof setInterval> | undefined;
  const remaining = computed(() => email().trim() === sentTo.value ? Math.max(0, Math.ceil((nextSend.value - now.value) / 1000)) : 0);
  async function send() {
    if (sending.value || remaining.value) return;
    const address = email().trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(address)) throw new Error('请输入有效邮箱');
    sending.value = true;
    try {
      await authApi.sendEmailCode(address, purpose);
      sentTo.value = address; now.value = Date.now(); nextSend.value = now.value + 60000;
      if (timer) clearInterval(timer);
      timer = setInterval(() => { now.value = Date.now(); if (now.value >= nextSend.value) { clearInterval(timer); timer = undefined; } }, 1000);
    } finally { sending.value = false; }
  }
  onBeforeUnmount(() => clearInterval(timer));
  return { sending, remaining, send };
}
