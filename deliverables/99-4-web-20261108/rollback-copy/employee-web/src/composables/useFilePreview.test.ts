import { describe, expect, it, vi } from 'vitest';
import { useFilePreview } from './useFilePreview';

describe('useFilePreview', () => {
  it('opens inline previews through object URLs and revokes them on close', async () => {
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview');
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(new Blob(['hello'], { type: 'text/plain' }))));
    const state = useFilePreview();

    await state.openFilePreviewFromUrl({
      id: 'file-1',
      name: '说明.txt',
      type: 'TXT',
      size: '5B',
      createdAt: '',
      status: '已确认',
      mimeType: 'text/plain',
    }, '/api/files/download/T1');

    expect(state.filePreview.value).toMatchObject({
      url: 'blob:preview',
      inline: true,
      objectUrl: 'blob:preview',
    });
    expect(state.filePreviewTitle.value).toBe('说明.txt');
    state.closeFilePreview();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview');
    expect(state.filePreview.value).toBeNull();
    createObjectURL.mockRestore();
    revokeObjectURL.mockRestore();
  });

  it('uses backend preview error messages when inline downloads fail', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      code: 100404,
      message: '文件对象不存在',
    }), {
      status: 404,
      headers: { 'Content-Type': 'application/json' },
    })));
    const state = useFilePreview();

    await expect(state.openFilePreviewFromUrl({
      id: 'file-1',
      name: '说明.txt',
      type: 'TXT',
      size: '5B',
      createdAt: '',
      status: '已确认',
      mimeType: 'text/plain',
    }, '/api/files/download/T1')).rejects.toThrow('文件对象不存在');
    expect(state.filePreview.value).toBeNull();
  });
});
