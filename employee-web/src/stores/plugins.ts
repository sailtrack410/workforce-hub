import { defineStore } from 'pinia';
import { employeeApi } from '../api';
import type { EmployeePluginResponse } from '../api';
import { loadPlugin, resetPluginRuntime } from '../core/plugin-loader';

export const usePluginsStore = defineStore('employee-plugins', {
  state: () => ({
    manifest: [] as EmployeePluginResponse[],
    loadedIds: [] as string[],
    errors: {} as Record<string, string>,
    manifestError: '',
    loading: false,
    resolved: false,
    _generation: 0,
  }),
  actions: {
    async loadAccessiblePlugins() {
      const gen = ++this._generation;
      resetPluginRuntime();
      this.manifest = [];
      this.loadedIds = [];
      this.errors = {};
      this.manifestError = '';
      this.loading = true;
      this.resolved = false;

      let manifest: EmployeePluginResponse[];
      try {
        manifest = await employeeApi.plugins.mine();
      } catch (e) {
        if (gen !== this._generation) return;
        this.manifestError = e instanceof Error ? e.message : '插件清单加载失败';
        this.manifest = [];
        this.loadedIds = [];
        this.errors = {};
        this.loading = false;
        this.resolved = true;
        return;
      }

      if (gen !== this._generation) return;
      this.manifest = manifest;
      this.errors = {};

      const results = await Promise.allSettled(
        manifest.map(async (p) => {
          await loadPlugin(p.id, p.integrity);
        }),
      );

      if (gen !== this._generation) return;
      const loaded: string[] = [];
      const errors: Record<string, string> = {};
      for (let i = 0; i < results.length; i++) {
        const r = results[i];
        if (r.status === 'fulfilled') {
          loaded.push(manifest[i].id);
        } else {
          errors[manifest[i].id] = r.reason instanceof Error ? r.reason.message : String(r.reason);
        }
      }
      this.loadedIds = loaded;
      this.errors = errors;
      this.loading = false;
      this.resolved = true;
    },
    reset() {
      this._generation++;
      resetPluginRuntime();
      this.manifest = [];
      this.loadedIds = [];
      this.errors = {};
      this.manifestError = '';
      this.loading = false;
      this.resolved = false;
    },
  },
});
