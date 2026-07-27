import { afterAll, describe, expect, it } from 'vitest';
import { build } from 'vite';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import vm from 'node:vm';
import { createPluginConfig } from '../../vite.plugin-poc.config';

describe('hello-plugin IIFE build', () => {
  const tmpDirs: string[] = [];

  async function buildPlugin(): Promise<string> {
    const dir = await fs.promises.mkdtemp(path.join(os.tmpdir(), 'plugin-poc-'));
    tmpDirs.push(dir);
    const cfg = createPluginConfig(dir);
    await build({ ...cfg, configFile: false });
    return path.join(dir, 'index.js');
  }

  afterAll(async () => {
    await Promise.all(tmpDirs.map((d) => fs.promises.rm(d, { recursive: true, force: true })));
  });

  it('产出仅含 index.js 单文件', async () => {
    const bundlePath = await buildPlugin();
    const dir = path.dirname(bundlePath);
    const files = await fs.promises.readdir(dir);
    expect(files).toEqual(['index.js']);
    expect(fs.existsSync(bundlePath)).toBe(true);
  });

  it('bundle 不含静态/动态 import', async () => {
    const bundlePath = await buildPlugin();
    const code = await fs.promises.readFile(bundlePath, 'utf-8');
    expect(code).not.toMatch(/\bimport\b/);
    expect(code).not.toMatch(/\bimport\(/);
  });

  it('已签名清单绑定实时构建摘要', async () => {
    const bundlePath = await buildPlugin();
    const digest = createHash('sha384')
      .update(await fs.promises.readFile(bundlePath))
      .digest('base64');
    const manifestPath = path.resolve(
      process.cwd(),
      '../plugins/hello-plugin/src/main/resources/plugin.json',
    );
    const manifest = JSON.parse(await fs.promises.readFile(manifestPath, 'utf-8')) as {
      frontendSha384?: string;
      type?: string;
      provider?: string;
    };

    expect(manifest.frontendSha384).toBe(`sha384-${digest}`);
    expect(manifest.type).toBe('UI');
    expect(manifest.provider).toBe('Workforce Hub');
  });

  it('vm 执行后恰好注册 hello-plugin，component() 返回组件', async () => {
    const bundlePath = await buildPlugin();
    const code = await fs.promises.readFile(bundlePath, 'utf-8');

    const calls: { id: string; reg: { id: string; name: string; component: () => Promise<unknown> } }[] = [];
    const defineComponent = (opts: Record<string, unknown>) => opts;
    const h = (...args: unknown[]) => args;

    const host = Object.freeze({
      Vue: Object.freeze({ defineComponent, h }),
      register(id: string, reg: { id: string; name: string; component: () => Promise<unknown> }) {
        calls.push({ id, reg });
      },
      getPlugin: () => undefined,
      getAllPlugins: () => new Map(),
    });

    const window = {} as Record<string, unknown>;
    Object.defineProperty(window, '__WORKFORCE_HOST__', {
      value: host,
      writable: false,
      configurable: false,
    });

    const sandbox = { window, self: window, globalThis: window, console };
    vm.createContext(sandbox);
    vm.runInContext(code, sandbox);

    expect(calls).toHaveLength(1);
    expect(calls[0].id).toBe('hello-plugin');
    expect(calls[0].reg.id).toBe('hello-plugin');
    expect(calls[0].reg.name).toBe('Hello Plugin');

    const comp = await calls[0].reg.component();
    expect(comp).toBeDefined();
    expect(typeof comp).toBe('object');
  });

  it('宿主不存在时抛错', async () => {
    const bundlePath = await buildPlugin();
    const code = await fs.promises.readFile(bundlePath, 'utf-8');

    const window = {} as Record<string, unknown>;
    const sandbox = { window, self: window, globalThis: window, console };
    vm.createContext(sandbox);

    expect(() => vm.runInContext(code, sandbox)).toThrow('宿主 SDK 未初始化');
  });
});
