import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  define: {
    'process.env.NODE_ENV': JSON.stringify('production'),
  },
  plugins: [vue()],
  build: {
    outDir: 'file-dist',
    emptyOutDir: true,
    cssCodeSplit: false,
    lib: {
      entry: 'src/main.ts',
      name: 'EmployeeWeb',
      formats: ['iife'],
      fileName: () => 'employee-web.js',
    },
  },
});
