import os from 'node:os';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { resolveLocalUploadTargets } from './vite.config';

describe('employee vite local upload bridge', () => {
  it('allows backend local file storage target', () => {
    const uploadPath = path.join(os.homedir(), '.workforce-hub', 'files', 'uploads', 'avatar.png');

    expect(resolveLocalUploadTargets(uploadPath)).toEqual([uploadPath]);
  });
});
