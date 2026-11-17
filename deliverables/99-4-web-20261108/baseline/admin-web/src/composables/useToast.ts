import { inject, type InjectionKey } from 'vue';

export interface ToastApi {
  error(message: string): void;
  success(message: string): void;
}

export const toastKey: InjectionKey<ToastApi> = Symbol('toast');

export function useToast(): ToastApi {
  return inject(toastKey, {
    error: (msg) => console.error('[toast]', msg),
    success: (msg) => console.info('[toast]', msg),
  });
}
