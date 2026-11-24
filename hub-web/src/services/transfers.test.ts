import { expect, it, vi, afterEach } from 'vitest';
import { retryUpload } from './transfers';
import { uploadEmployeeFile } from '@/composables/useUpload';
vi.mock('@/composables/useUpload', () => ({ uploadEmployeeFile: vi.fn().mockResolvedValue({ attachmentRefId: '123' }) }));
afterEach(() => vi.unstubAllGlobals());
it('恢复上传复用原幂等标识、文件正文和关联任务', async () => {
  vi.stubGlobal('File', class { name: string; constructor(_bytes: unknown, name: string) { this.name = name; } });
  vi.stubGlobal('window', { workforceDesktop: { cacheContext: async () => 'scope', readUpload: async () => ({ id: 'upload1', fileName: 'a.txt', mimeType: 'text/plain', bytes: [1, 2], ownerType: 'PERSONAL_FILE', taskId: '99' }) } });
  await retryUpload('upload1');
  expect(uploadEmployeeFile).toHaveBeenCalledWith(expect.objectContaining({ transferId: 'upload1', taskId: '99', file: expect.objectContaining({ name: 'a.txt' }) }));
});
