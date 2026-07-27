import { defineConfig } from 'vite';

export function createPluginConfig(outDir?: string) {
  return defineConfig({
    build: {
      outDir: outDir ?? 'dist/plugins/hello-plugin',
      emptyOutDir: true,
      copyPublicDir: false,
      cssCodeSplit: false,
      sourcemap: false,
      lib: {
        entry: '../plugins/hello-plugin/frontend/src/index.ts',
        name: 'HelloPlugin',
        formats: ['iife'],
        fileName: () => 'index.js',
      },
    },
  });
}

export default createPluginConfig();
