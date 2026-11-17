import { promises as fs } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import type { Plugin } from 'vite';
import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

function localUploadBridgePlugin(): Plugin {
  return {
    name: 'admin-local-upload-bridge',
    apply: 'serve',
    configureServer(server) {
      server.middlewares.use('/__admin-local-upload', async (request, response) => {
        if (request.method !== 'PUT') {
          response.statusCode = 405;
          response.end('Method Not Allowed');
          return;
        }

        const target = request.headers['x-upload-path'];
        const rawUploadPath = Array.isArray(target) ? target[0] : target;
        const uploadPath = decodeUploadPath(rawUploadPath);
        if (!uploadPath || uploadPath.includes('\0')) {
          response.statusCode = 400;
          response.end('Missing upload target');
          return;
        }

        try {
          const chunks: Buffer[] = [];
          for await (const chunk of request) {
            chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
          }
          const payload = Buffer.concat(chunks);
          const targets = resolveLocalUploadTargets(uploadPath);
          if (!targets.length) {
            response.statusCode = 400;
            response.end('Invalid upload target');
            return;
          }
          await Promise.all(targets.map(async (targetPath) => {
            await fs.mkdir(path.dirname(targetPath), { recursive: true });
            await fs.writeFile(targetPath, payload);
          }));
          response.setHeader('Content-Type', 'application/json');
          response.end(JSON.stringify({ ok: true, paths: targets }));
        } catch (error) {
          response.statusCode = 500;
          response.end(error instanceof Error ? error.message : 'Upload failed');
        }
      });
    },
  };
}

function decodeUploadPath(uploadPath?: string) {
  if (!uploadPath) return '';
  try {
    return decodeURIComponent(uploadPath);
  } catch {
    return uploadPath;
  }
}

export function resolveLocalUploadTargets(uploadPath: string) {
  const workspaceRoot = path.resolve(process.cwd(), '..');
  const serverRoot = path.resolve(workspaceRoot, 'server');
  const localFileRoot = path.resolve(os.homedir(), '.workforce-hub', 'files');
  const allowedRoots = [workspaceRoot, serverRoot, localFileRoot].map((item) => path.normalize(item));
  if (path.isAbsolute(uploadPath)) {
    const normalized = path.normalize(uploadPath);
    return allowedRoots.some((root) => normalized.startsWith(root)) ? [normalized] : [];
  }

  const normalizedRelative = path.normalize(uploadPath);
  if (normalizedRelative.startsWith('..')) return [];
  const candidateRoots = normalizedRelative.startsWith(`target${path.sep}`)
    ? [workspaceRoot, serverRoot]
    : [workspaceRoot];
  return [...new Set(candidateRoots.map((root) => path.resolve(root, normalizedRelative)))];
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080';
  const nodeEnv = process.env.NODE_ENV ?? (mode === 'production' ? 'production' : 'development');

  return {
    base: './',
    define: {
      'process.env.NODE_ENV': JSON.stringify(nodeEnv),
    },
    plugins: [vue(), localUploadBridgePlugin()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
        '@shared': path.resolve(__dirname, '../shared-web'),
      },
    },
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            'vendor-vue': ['vue', 'vue-router', 'pinia'],
            'vendor-icons': ['lucide-vue-next'],
          },
        },
      },
    },
  };
});
