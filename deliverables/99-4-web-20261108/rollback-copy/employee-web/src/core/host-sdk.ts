import type { Component } from 'vue';
import * as Vue from 'vue';

export interface PluginRegistration {
  id: string;
  name: string;
  component: () => Promise<Component>;
}

export interface WorkforceHostSdk {
  readonly Vue: typeof Vue;
  register(id: string, registration: PluginRegistration): void;
  getPlugin(id: string): PluginRegistration | undefined;
  getAllPlugins(): ReadonlyMap<string, PluginRegistration>;
}

interface HostSdkState {
  plugins: Map<string, PluginRegistration>;
  sources: Map<string, Element | null>;
}

const PLUGIN_ALREADY_REGISTERED = '插件已注册';
const PLUGIN_ID_REQUIRED = '插件 id 不能为空';
const PLUGIN_ID_INVALID = '非法插件 id';
const PLUGIN_ID_MISMATCH = '注册 id 与 registration.id 不一致';
const PLUGIN_NAME_REQUIRED = '插件 name 不能为空';
const PLUGIN_COMPONENT_INVALID = '插件 component 必须是函数';
const PLUGIN_SCRIPT_REQUIRED = '插件必须在当前脚本执行期间注册';
const PLUGIN_SCRIPT_INVALIDATED = '插件 script 已失效';
const VALID_PLUGIN_ID = /^[a-z0-9-]{1,64}$/;
const hostSdkStates = new WeakMap<WorkforceHostSdk, HostSdkState>();
const invalidatedPluginScripts = new WeakSet<Element>();

function createSdk(): WorkforceHostSdk {
  const plugins = new Map<string, PluginRegistration>();
  const sources = new Map<string, Element | null>();

  const sdk: WorkforceHostSdk = {
    Vue,

    register(id: string, registration: PluginRegistration): void {
      const source = typeof document === 'undefined' ? null : document.currentScript ?? null;
      if (typeof document !== 'undefined' && !source) {
        throw new Error(PLUGIN_SCRIPT_REQUIRED);
      }
      if (source && invalidatedPluginScripts.has(source)) {
        throw new Error(PLUGIN_SCRIPT_INVALIDATED);
      }
      if (!id || !id.trim()) {
        throw new Error(PLUGIN_ID_REQUIRED);
      }
      if (!VALID_PLUGIN_ID.test(id)) {
        throw new Error(PLUGIN_ID_INVALID);
      }
      if (id !== registration.id) {
        throw new Error(PLUGIN_ID_MISMATCH);
      }
      if (!registration.name || !registration.name.trim()) {
        throw new Error(PLUGIN_NAME_REQUIRED);
      }
      if (typeof registration.component !== 'function') {
        throw new Error(PLUGIN_COMPONENT_INVALID);
      }
      if (plugins.has(id)) {
        throw new Error(`${PLUGIN_ALREADY_REGISTERED}: ${id}`);
      }
      plugins.set(id, { ...registration });
      sources.set(id, source);
    },

    getPlugin(id: string): PluginRegistration | undefined {
      const found = plugins.get(id);
      return found ? { ...found } : undefined;
    },

    getAllPlugins(): ReadonlyMap<string, PluginRegistration> {
      return new Map([...plugins.entries()].map(([k, v]) => [k, { ...v }]));
    },
  };

  hostSdkStates.set(sdk, { plugins, sources });
  return Object.freeze(sdk);
}

export function clearHostSdkPlugins(): void {
  if (typeof window === 'undefined') return;
  const host = window.__WORKFORCE_HOST__;
  if (!host) return;
  const state = hostSdkStates.get(host);
  state?.plugins.clear();
  state?.sources.clear();
}

export function isHostSdkPluginSource(host: WorkforceHostSdk, id: string, source: HTMLScriptElement): boolean {
  return hostSdkStates.get(host)?.sources.get(id) === source;
}

export function invalidateHostSdkPluginScript(script: HTMLScriptElement): void {
  invalidatedPluginScripts.add(script);
}

export function removeHostSdkPlugin(host: WorkforceHostSdk, id: string, source: HTMLScriptElement): void {
  const state = hostSdkStates.get(host);
  if (!state || state.sources.get(id) !== source) return;
  state.plugins.delete(id);
  state.sources.delete(id);
}

/**
 * 初始化 Host SDK（幂等）。
 * 以 window.__WORKFORCE_HOST__ 为唯一真相：存在则返回，不存在则创建并赋值。
 * 创建后冻结全局属性，防止后加载脚本覆盖。
 */
export function initHostSdk(): WorkforceHostSdk {
  const existing = window.__WORKFORCE_HOST__;
  if (existing) {
    return existing;
  }
  const sdk = createSdk();
  Object.defineProperty(window, '__WORKFORCE_HOST__', {
    value: sdk,
    writable: false,
    configurable: false,
  });
  return sdk;
}
