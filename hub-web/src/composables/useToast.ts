// hub-web 轻提示：模块级单例状态 + show 方法
import { ref } from 'vue';

const message = ref('');
let timer: number | undefined;

export function useToast() {
  function show(text: string) {
    message.value = text;
    if (timer) window.clearTimeout(timer);
    timer = window.setTimeout(() => { message.value = ''; }, 2600);
  }
  return { message, show };
}
