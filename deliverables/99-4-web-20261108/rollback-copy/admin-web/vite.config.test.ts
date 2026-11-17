import os from 'node:os';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { resolveLocalUploadTargets } from './vite.config';

describe('admin vite local upload bridge', () => {
  it('allows backend local file storage target', () => {
    const uploadPath = path.join(os.homedir(), '.workforce-hub', 'files', 'uploads', 'test.txt');

    expect(resolveLocalUploadTargets(uploadPath)).toEqual([uploadPath]);
  });
});
