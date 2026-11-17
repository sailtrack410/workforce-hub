import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const require = createRequire(path.join(root, 'employee-web/package.json'));
const { build } = require('esbuild');
await build({
  entryPoints: [path.join(here, 'main.ts')], outfile: path.join(here, 'bundle.js'),
  bundle: true, platform: 'browser', format: 'iife', target: 'es2022',
  nodePaths: [path.join(root, 'employee-web/node_modules')],
  alias: { '@': path.join(root, 'employee-web/src'), '@shared': path.join(root, 'shared-web') },
  define: { 'import.meta.env': '{}', 'process.env.NODE_ENV': '"production"' },
  logLevel: 'info',
});
