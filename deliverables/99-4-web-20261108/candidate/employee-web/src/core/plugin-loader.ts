import { clearHostSdkPlugins, invalidateHostSdkPluginScript, isHostSdkPluginSource, removeHostSdkPlugin, type WorkforceHostSdk } from './host-sdk';

const VALID_ID = /^[a-z0-9-]{1,64}$/;
const VALID_SRI = /^sha384-[A-Za-z0-9+/]{64}$/;
const SCRIPT_PREFIX = '/plugins/';
const PLUGIN_LOAD_TIMEOUT_MS = 10_000;

interface PendingPlugin {
  promise: Promise<void>;
  integrity: string;
  script: HTMLScriptElement;
  generation: number;
  reject: (reason: Error) => void;
}

const pending = new Map<string, PendingPlugin>();
const verifiedIntegrities = new Map<string, string>();
const loadedScripts = new Map<string, HTMLScriptElement>();
let runtimeGeneration = 0;

function validateId(pluginId: string): void {
  if (!pluginId || !VALID_ID.test(pluginId)) {
    throw new Error(`非法插件 id: ${pluginId}`);
  }
}

function validateIntegrity(integrity: string): void {
  if (!integrity || !VALID_SRI.test(integrity)) {
    throw new Error(`非法 integrity: ${integrity}`);
  }
}

export async function loadPlugin(pluginId: string, integrity: string): Promise<void> {
  validateId(pluginId);
  validateIntegrity(integrity);

  if (typeof window === 'undefined') {
    throw new Error('Host SDK 未初始化');
  }
  const host: WorkforceHostSdk | undefined = window.__WORKFORCE_HOST__;
  if (!host) {
    throw new Error('Host SDK 未初始化');
  }

  const existing = pending.get(pluginId);
  if (existing) {
    if (existing.integrity !== integrity) {
      throw new Error(`插件 ${pluginId} 正在加载，integrity 不匹配`);
    }
    return existing.promise;
  }

  if (host.getPlugin(pluginId)) {
    const verifiedIntegrity = verifiedIntegrities.get(pluginId);
    if (!verifiedIntegrity) {
      throw new Error(`插件 ${pluginId} 已注册但未经 loader 验证`);
    }
    if (verifiedIntegrity !== integrity) {
      throw new Error(`插件 ${pluginId} 已注册，integrity 不匹配`);
    }
    return;
  }

  const script = document.createElement('script');
  script.async = true;
  script.src = `${SCRIPT_PREFIX}${pluginId}/index.js`;
  script.integrity = integrity;

  let resolveLoad!: () => void;
  let rejectLoad!: (reason: Error) => void;
  const loadPromise = new Promise<void>((resolve, reject) => {
    resolveLoad = resolve;
    rejectLoad = reject;
  });
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  let entry!: PendingPlugin;
  const promise = loadPromise.finally(() => {
    if (timeoutId !== undefined) clearTimeout(timeoutId);
    if (pending.get(pluginId) === entry) {
      pending.delete(pluginId);
    }
  });
  entry = {
    promise,
    integrity,
    script,
    generation: runtimeGeneration,
    reject: rejectLoad,
  };

  script.onload = () => {
    if (entry.generation !== runtimeGeneration || pending.get(pluginId) !== entry) {
      removeHostSdkPlugin(host, pluginId, script);
      script.remove();
      rejectLoad(new Error(`插件 ${pluginId} 加载已取消`));
      return;
    }
    if (!host.getPlugin(pluginId)) {
      script.remove();
      rejectLoad(new Error(`插件 ${pluginId} 加载后未注册`));
      return;
    }
    if (!isHostSdkPluginSource(host, pluginId, script)) {
      script.remove();
      rejectLoad(new Error(`插件 ${pluginId} 未由当前 script 注册`));
      return;
    }
    verifiedIntegrities.set(pluginId, integrity);
    loadedScripts.set(pluginId, script);
    resolveLoad();
  };

  script.onerror = () => {
    if (entry.generation !== runtimeGeneration || pending.get(pluginId) !== entry) {
      removeHostSdkPlugin(host, pluginId, script);
      script.remove();
      rejectLoad(new Error(`插件 ${pluginId} 加载已取消`));
      return;
    }
    removeHostSdkPlugin(host, pluginId, script);
    script.remove();
    rejectLoad(new Error(`插件 ${pluginId} 加载失败`));
  };

  pending.set(pluginId, entry);
  timeoutId = setTimeout(() => {
    if (pending.get(pluginId) !== entry) return;
    pending.delete(pluginId);
    invalidateHostSdkPluginScript(script);
    removeHostSdkPlugin(host, pluginId, script);
    script.remove();
    rejectLoad(new Error(`插件 ${pluginId} 加载超时`));
  }, PLUGIN_LOAD_TIMEOUT_MS);
  try {
    document.head.appendChild(script);
  } catch (error) {
    if (pending.get(pluginId) === entry) {
      pending.delete(pluginId);
    }
    script.remove();
    rejectLoad(error instanceof Error ? error : new Error(String(error)));
  }

  return promise;
}

export function resetPluginRuntime(): void {
  runtimeGeneration++;
  clearHostSdkPlugins();
  verifiedIntegrities.clear();

  const pendingEntries = [...pending.entries()];
  const scripts = new Set([...loadedScripts.values(), ...pendingEntries.map(([, entry]) => entry.script)]);
  pending.clear();
  loadedScripts.clear();

  for (const script of scripts) {
    invalidateHostSdkPluginScript(script);
    script.remove();
  }
  for (const [pluginId, entry] of pendingEntries) {
    entry.reject(new Error(`插件 ${pluginId} 加载已取消`));
  }
}
