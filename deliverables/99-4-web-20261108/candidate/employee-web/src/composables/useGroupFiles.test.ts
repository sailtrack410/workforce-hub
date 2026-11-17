import { beforeEach, describe, expect, it, vi } from 'vitest';
import { employeeApi } from '@/api';
import { useGroupFiles } from './useGroupFiles';
import type { ImGroupFileResponse } from '@/api';
import type { PersonalFileRecord } from '@/stores/shell';

function groupFile(overrides: Partial<ImGroupFileResponse> = {}): ImGroupFileResponse {
  return {
    attachmentRefId: '501',
    conversationId: '7001',
    displayName: '群文件.txt',
    mimeType: 'text/plain',
    fileSize: 24,
    contentSha256: 'a'.repeat(64),
    scanStatus: 'PASSED',
    uploaderId: '2002',
    uploaderName: '李四',
    createdAt: '2026-05-16T09:30:00',
    canDelete: false,
    ...overrides,
  };
}

describe('useGroupFiles', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('loads files for the active group and opens previews through group-file tickets', async () => {
    vi.spyOn(employeeApi.im, 'groupFiles').mockResolvedValue([groupFile()]);
    vi.spyOn(employeeApi.im, 'createGroupFileDownloadTicket').mockResolvedValue({
      downloadTicketId: 'DL-1',
      ticketNo: 'IMDL-1',
      downloadUrl: '/api/employee/im/attachments/download/IMDL-1',
      fileName: '群文件.txt',
      fileSize: 24,
    });
    const openFilePreviewFromUrl = vi.fn();
    const state = useGroupFiles({
      activeConversationId: () => '7001',
      openFilePreviewFromUrl,
      showToast: vi.fn(),
    });

    await state.loadGroupFiles();
    await state.openGroupFile(state.groupFiles.value[0], 'preview');

    expect(employeeApi.im.groupFiles).toHaveBeenCalledWith('7001');
    expect(employeeApi.im.createGroupFileDownloadTicket).toHaveBeenCalledWith('7001', '501', {
      downloadType: 'SINGLE',
      reason: '预览群文件',
    });
    expect(openFilePreviewFromUrl).toHaveBeenCalledWith(
      expect.objectContaining<Partial<PersonalFileRecord>>({
        attachmentRefId: '501',
        name: '群文件.txt',
        type: 'TXT',
        size: '24B',
        status: '已通过',
      }),
      '/api/employee/im/attachments/download/IMDL-1',
    );
  });

  it('uploads group files with IM_GROUP_FILE owner and reloads the group list', async () => {
    const loaded = groupFile({ attachmentRefId: '502', displayName: '上传后.txt' });
    vi.spyOn(employeeApi.im, 'groupFiles').mockResolvedValueOnce([]).mockResolvedValueOnce([loaded]);
    vi.spyOn(employeeApi.files, 'createUploadTicket').mockResolvedValue({
      uploadTicketId: 'UP-1',
      ticketNo: 'UP-1',
      deduplicated: true,
      attachmentRefId: '502',
      storageProvider: 'LOCAL',
      headers: {},
    });
    const state = useGroupFiles({
      activeConversationId: () => '7001',
      openFilePreviewFromUrl: vi.fn(),
      showToast: vi.fn(),
    });

    await state.loadGroupFiles();
    await state.uploadGroupFile(new File(['abc'], '上传后.txt', { type: 'text/plain' }));

    expect(employeeApi.files.createUploadTicket).toHaveBeenCalledWith(expect.objectContaining({
      ownerType: 'IM_GROUP_FILE',
      ownerId: '7001',
      fileName: '上传后.txt',
    }));
    expect(employeeApi.im.groupFiles).toHaveBeenCalledTimes(2);
    expect(state.groupFiles.value).toEqual([loaded]);
  });
});
