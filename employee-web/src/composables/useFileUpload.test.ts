import { describe, expect, it, vi } from 'vitest';
import { employeeApi } from '@/api';
import { useFileUpload } from './useFileUpload';
import type { FileSpaceRecord } from '@/stores/shell';

function fileSpace(files: FileSpaceRecord['files']): FileSpaceRecord {
  return {
    id: 'personal',
    title: '云空间',
    subtitle: '',
    note: '',
    usage: 0,
    label: '',
    summary: [],
    files,
  };
}

describe('useFileUpload', () => {
  it('previews personal files with single-use download tickets and object URLs', async () => {
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview');
    const revokeObjectURL = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(new Blob(['hello'], { type: 'text/plain' }))));
    vi.spyOn(employeeApi.files, 'createDownloadTicket').mockResolvedValue({
      downloadTicketId: 'DL-1',
      ticketNo: 'DL-1',
      downloadUrl: '/api/employee/files/download/DL-1',
      fileName: '说明.txt',
      fileSize: 5,
    });

    const state = useFileUpload({
      shell: {
        syncFileQuota: vi.fn(),
        mergeBackendPersonalFiles: vi.fn(),
        upsertPersonalFile: vi.fn(),
        markPersonalFileTrashed: vi.fn(),
        restorePersonalFile: vi.fn(),
        purgePersonalFile: vi.fn(),
      } as never,
      activeFile: { value: fileSpace([]) },
      activeSection: { value: 'files' },
      uploadFolderId: { value: null },
      hasActiveConversation: { value: false },
      showToast: vi.fn(),
      sendSelectedFileToConversation: vi.fn(),
    });

    await state.openPersonalFilePreview({
      id: 'file-1',
      name: '说明.txt',
      type: 'TXT',
      size: '5B',
      createdAt: '',
      status: '已上传',
      attachmentRefId: '501',
      mimeType: 'text/plain',
      downloadUrl: '/api/employee/files/download/DL-OLD',
    });

    expect(employeeApi.files.createDownloadTicket).toHaveBeenCalledWith('501', {
      downloadType: 'INLINE',
      reason: '预览',
    });
    expect(fetch).toHaveBeenCalledWith('/api/employee/files/download/DL-1', { credentials: 'include' });
    expect(state.filePreview.value).toMatchObject({ url: 'blob:preview', inline: true });

    state.closeFilePreview();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:preview');
    createObjectURL.mockRestore();
    revokeObjectURL.mockRestore();
  });
});
