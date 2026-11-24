import { reactive, watch, type Ref } from 'vue';

export const localStatus = reactive({ offline: false, cachedAt: 0, pending: 0, conflicts: 0, error: '', suspended: false });

export function cacheable(path: string) {
  return path.startsWith('/api/employee/') && !/auth|token|ticket|download/i.test(path);
}
export async function currentContext() {
  if (typeof window === 'undefined') return null;
  return await window.workforceDesktop?.cacheContext?.().catch(() => null) ?? null;
}
export async function cachedResponse<T>(context: string | null, path: string): Promise<T | undefined> {
  if (!context) return undefined;
  const cached = await window.workforceDesktop?.cacheGet(context, path).catch(() => null);
  if (!cached) return undefined;
  localStatus.offline = true; localStatus.cachedAt = cached.updatedAt;
  return cached.value as T;
}
export async function storeResponse(context: string | null, path: string, value: unknown) {
  if (context) await window.workforceDesktop?.cachePut(context, path, value).catch(() => { localStatus.error = '本地缓存写入失败'; });
}
export async function invalidateResponse(context: string | null, path: string) {
  const prefix = path.split('?')[0].split('/').slice(0, 4).join('/');
  if (context) await window.workforceDesktop?.cacheInvalidate(context, prefix).catch(() => undefined);
}

export function useLocalDraft<T>(key: string, model: Ref<T>) {
  let context: string | null = null;
  let restoring = false;
  let saving = Promise.resolve();
  const stop = watch(model, (value) => {
    if (!context || restoring) return;
    const snapshot = JSON.parse(JSON.stringify(value));
    saving = saving.then(() => window.workforceDesktop?.saveDraft(context!, key, snapshot)).catch(() => { localStatus.error = '草稿保存失败'; });
  }, { deep: true, flush: 'sync' });
  return {
    async restore() {
      context = await currentContext();
      if (!context) return;
      const value = await window.workforceDesktop?.readDraft(context, key).catch(() => null);
      if (value != null) { restoring = true; model.value = value as T; restoring = false; }
    },
    async clear() { await saving; if (context) await window.workforceDesktop?.saveDraft(context, key, null); },
    stop,
  };
}
