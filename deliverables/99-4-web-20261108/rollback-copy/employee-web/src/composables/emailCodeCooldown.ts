import { computed, ref } from 'vue';

const DEFAULT_COOLDOWN_SECONDS = 60;

export function createEmailCodeCooldown(defaultLabel = '发送验证码') {
  const remainingSeconds = ref(0);
  let timer: ReturnType<typeof setInterval> | undefined;

  const stopTimer = () => {
    if (timer) {
      clearInterval(timer);
      timer = undefined;
    }
  };

  const reset = () => {
    stopTimer();
    remainingSeconds.value = 0;
  };

  const tick = () => {
    remainingSeconds.value = Math.max(0, remainingSeconds.value - 1);
    if (remainingSeconds.value === 0) stopTimer();
  };

  const start = (seconds = DEFAULT_COOLDOWN_SECONDS) => {
    reset();
    remainingSeconds.value = Math.max(0, Math.ceil(seconds));
    if (remainingSeconds.value > 0) {
      timer = setInterval(tick, 1000);
    }
  };

  const isActive = computed(() => remainingSeconds.value > 0);
  const label = computed(() => (isActive.value ? `${remainingSeconds.value} 秒后重发` : defaultLabel));

  return {
    remainingSeconds,
    isActive,
    label,
    start,
    reset,
  };
}
