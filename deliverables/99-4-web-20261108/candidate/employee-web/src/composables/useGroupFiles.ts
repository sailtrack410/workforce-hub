import { ref } from 'vue';
import { employeeApi } from '@/api';
import type { EntityId, ImGroupFileResponse } from '@/api';
import type { PersonalFileRecord } from '@/stores/shell';
import { formatDateTime, statusText } from '@/utils/employee-format';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { usePersonalFileTransfer } from './usePersonalFileTransfer';

interface UseGroupFilesOptions {
  activeConversationId: () => EntityId | '';
  openFilePreviewFromUrl: (file: PersonalFileRecord, url: string) => Promise<void>;
  showToast: (message: string) => void;
}

// 群文件 composable：群文件列表加载、上传、预览/下载、删除
export function useGroupFiles({
  activeConversationId,
  openFilePreviewFromUrl,
  showToast,
}: UseGroupFilesOptions) {
  const {
    resolveDownloadUrl,
    uploadGroupFileAttachment,
  } = usePersonalFileTransfer();

  const groupFiles = ref<ImGroupFileResponse[]>([]);
  const groupFilesLoading = ref(false);
  const groupFileUploading = ref(false);
  const groupFileInput = ref<HTMLInputElement | null>(null);

  async function loadGroupFiles() {
    const conversationId = activeConversationId();
    if (!conversationId) {
      groupFiles.value = [];
      return;
    }
    groupFilesLoading.value = true;
    try {
      groupFiles.value = await employeeApi.im.groupFiles(conversationId);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '群文件加载失败');
    } finally {
      groupFilesLoading.value = false;
    }
  }

  function openGroupFilePicker() {
    if (groupFileUploading.value) return;
    groupFileInput.value?.click();
  }

  async function handleGroupFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    await uploadGroupFile(file);
  }

  async function uploadGroupFile(file: File) {
    const conversationId = activeConversationId();
    if (!conversationId || groupFileUploading.value) return;
    groupFileUploading.value = true;
    try {
      await uploadGroupFileAttachment(file, conversationId);
      await loadGroupFiles();
      showToast('群文件已上传');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '群文件上传失败');
    } finally {
      groupFileUploading.value = false;
    }
  }

  // 打开群文件：先获取下载凭证，preview 走内联预览，download 走新窗口
  async function openGroupFile(file: ImGroupFileResponse, action: 'preview' | 'download') {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    try {
      const ticket = await employeeApi.im.createGroupFileDownloadTicket(conversationId, file.attachmentRefId, {
        downloadType: 'SINGLE',
        reason: action === 'preview' ? '预览群文件' : '下载群文件',
      });
      const url = resolveDownloadUrl(ticket, employeeApi.im.downloadAttachmentUrl(ticket.ticketNo));
      if (action === 'download') {
        window.open(url, '_blank', 'noopener');
        return;
      }
      await openFilePreviewFromUrl(toPersonalFileRecord(file), url);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '群文件打开失败');
    }
  }

  async function trashGroupFile(file: ImGroupFileResponse) {
    if (!file.canDelete) return;
    const conversationId = activeConversationId();
    if (!conversationId) return;
    try {
      await employeeApi.im.trashGroupFile(conversationId, file.attachmentRefId, { reason: '删除群文件' });
      groupFiles.value = groupFiles.value.filter((item) => item.attachmentRefId !== file.attachmentRefId);
      showToast('群文件已删除');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '群文件删除失败');
    }
  }

  return {
    groupFiles,
    groupFilesLoading,
    groupFileUploading,
    groupFileInput,
    loadGroupFiles,
    openGroupFilePicker,
    handleGroupFileSelected,
    uploadGroupFile,
    openGroupFile,
    trashGroupFile,
    toPersonalFileRecord,
  };
}

function toPersonalFileRecord(file: ImGroupFileResponse): PersonalFileRecord {
  return {
    id: `im-group-file-${file.attachmentRefId}`,
    name: file.displayName,
    type: fileTypeLabelFromName(file.displayName, file.mimeType),
    size: fileSizeLabel(file.fileSize),
    createdAt: formatDateTime(file.createdAt),
    status: statusText(file.scanStatus),
    attachmentRefId: file.attachmentRefId,
    mimeType: file.mimeType,
  };
}
