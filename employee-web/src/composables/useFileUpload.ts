import { ref } from 'vue';
import { employeeApi } from '@/api';
import type { EntityId } from '@/api';
import type { FileSpaceRecord, PersonalFileRecord, SectionId, useShellStore } from '@/stores/shell';
import { fileSizeLabel, fileTypeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { formatDateTime, statusText } from '@/utils/employee-format';
import {
  fileNameOf,
  isFileTrashed,
} from '@/utils/file-record';
import { useFilePreview } from './useFilePreview';
import { usePersonalFileTransfer } from './usePersonalFileTransfer';

interface UseFileUploadOptions {
  shell: ReturnType<typeof useShellStore>;
  activeFile: { value: FileSpaceRecord };
  activeSection: { value: SectionId };
  uploadFolderId: { value: EntityId | null };
  hasActiveConversation: { value: boolean };
  showToast: (message: string) => void;
  sendSelectedFileToConversation: (file: PersonalFileRecord) => Promise<void>;
}

// 文件上传 composable：统一处理个人文件上传、IM 附件发送、预览/下载/删除等操作
export function useFileUpload({
  shell,
  activeFile,
  activeSection,
  uploadFolderId,
  hasActiveConversation,
  showToast,
  sendSelectedFileToConversation,
}: UseFileUploadOptions) {
  const {
    uploadImMessageAttachment,
    uploadPersonalAttachment,
    resolveDownloadUrl,
  } = usePersonalFileTransfer();
  const personalFileInput = ref<HTMLInputElement | null>(null);
  const fileUploading = ref(false);
  const uploadPanelOpen = ref(false);
  const uploadDraftFile = ref<File | null>(null);
  const uploadDragActive = ref(false);
  const uploadResult = ref<{ status: 'success' | 'error'; text: string } | null>(null);
  const fileActionLoading = ref<Record<string, boolean>>({});
  const {
    filePreview,
    filePreviewLoading,
    filePreviewTitle,
    filePreviewType,
    openFilePreviewFromUrl,
    closeFilePreview,
    openPreviewExternal,
  } = useFilePreview();

  async function refreshFileQuota() {
    try {
      shell.syncFileQuota(await employeeApi.files.quota());
    } catch {
      return;
    }
  }

  async function refreshPersonalFiles() {
    try {
      const [activeFiles, recycleBinFiles] = await Promise.all([
        employeeApi.files.list({ pageNo: 1, pageSize: 100 }),
        employeeApi.files.recycleBin({ pageNo: 1, pageSize: 100 }),
      ]);
      shell.mergeBackendPersonalFiles(activeFiles.list, recycleBinFiles.list);
    } catch {
      return;
    }
  }

  async function refreshFileCenter() {
    await Promise.all([refreshFileQuota(), refreshPersonalFiles()]);
  }

  async function handlePersonalFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    uploadDraftFile.value = file;
    uploadResult.value = null;
    uploadPanelOpen.value = true;
    uploadDragActive.value = false;
  }

  function openUploadPanel() {
    uploadPanelOpen.value = true;
    uploadResult.value = null;
    uploadDragActive.value = false;
  }

  function closeUploadPanel() {
    if (fileUploading.value) return;
    uploadPanelOpen.value = false;
    uploadDraftFile.value = null;
    uploadResult.value = null;
    uploadDragActive.value = false;
  }

  function openFilePicker() {
    personalFileInput.value?.click();
  }

  function handleUploadDrop(event: DragEvent) {
    uploadDragActive.value = false;
    const file = event.dataTransfer?.files?.[0];
    if (!file) return;
    uploadDraftFile.value = file;
    uploadResult.value = null;
  }

  async function submitPersonalFileUpload() {
    const file = uploadDraftFile.value;
    if (!file || fileUploading.value) return;
    const attachmentRefId = await uploadPersonalFile(file);
    if (attachmentRefId) {
      uploadDraftFile.value = null;
    }
  }

  function uploadedPersonalFile(attachmentRefId: EntityId) {
    return activeFile.value.files.find((item) => item.attachmentRefId === attachmentRefId);
  }

  // 上传流程：当前在消息页且有活跃会话时走 IM 附件通道，否则走个人文件通道
  async function uploadPersonalFile(file: File): Promise<EntityId | null> {
    fileUploading.value = true;
    uploadResult.value = null;
    try {
      if (activeSection.value === 'messages' && hasActiveConversation.value) {
        const attachment = await uploadImMessageAttachment(file);
        uploadResult.value = { status: 'success', text: '上传成功' };
        await sendSelectedFileToConversation({
          id: `im-upload-${attachment.attachmentRefId}`,
          name: attachment.displayName || file.name,
          type: fileTypeLabelFromName(attachment.displayName || file.name, attachment.mimeType || file.type),
          size: fileSizeLabel(attachment.fileSize || file.size),
          createdAt: attachment.createdAt ? formatDateTime(attachment.createdAt) : '',
          status: attachment.scanStatus ? statusText(attachment.scanStatus) : '已确认',
          attachmentRefId: attachment.attachmentRefId,
          mimeType: attachment.mimeType || file.type,
        });
        return attachment.attachmentRefId;
      }

      const attachment = await uploadPersonalAttachment(file, 'personal', uploadFolderId.value);
      if (attachment.deduplicated) {
        const attachmentRefId = attachment.attachmentRefId;
        shell.addPersonalFile({
          name: file.name,
          type: fileTypeLabel(file),
          size: fileSizeLabel(file.size),
          status: '已确认',
          attachmentRefId,
          folderId: uploadFolderId.value,
          mimeType: file.type,
        });
        uploadResult.value = { status: 'success', text: '上传成功' };
        await refreshFileCenter();
        const uploaded = uploadedPersonalFile(attachmentRefId);
        if (uploaded && activeSection.value === 'messages' && hasActiveConversation.value) await sendSelectedFileToConversation(uploaded);
        return attachmentRefId;
      }

      shell.addPersonalFile({
        name: attachment.displayName || file.name,
        type: fileTypeLabelFromName(attachment.displayName || file.name, attachment.mimeType),
        size: fileSizeLabel(attachment.fileSize || file.size),
        status: attachment.scanStatus ? statusText(attachment.scanStatus) : '已确认',
        attachmentRefId: attachment.attachmentRefId,
        folderId: uploadFolderId.value,
        mimeType: attachment.mimeType || file.type,
        createdAt: formatDateTime(attachment.createdAt),
      });
      uploadResult.value = { status: 'success', text: '上传成功' };
      await refreshFileCenter();
      const uploaded = uploadedPersonalFile(attachment.attachmentRefId);
      if (uploaded && activeSection.value === 'messages' && hasActiveConversation.value) await sendSelectedFileToConversation(uploaded);
      return attachment.attachmentRefId;
    } catch (error) {
      uploadResult.value = { status: 'error', text: error instanceof Error ? error.message : '上传失败' };
      return null;
    } finally {
      fileUploading.value = false;
    }
  }

  async function createPersonalDownloadTicket(file: PersonalFileRecord) {
    if (!file.attachmentRefId) throw new Error('文件不可下载');
    return employeeApi.files.createDownloadTicket(file.attachmentRefId, { downloadType: 'SINGLE' });
  }

  // 文件预览：先获取下载凭证，再通过 objectUrl 内联展示
  async function openPersonalFilePreview(file: PersonalFileRecord) {
    if (isFileTrashed(file)) return;
    const actionKey = `preview-${file.id}`;
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
    try {
      let url = file.downloadUrl;
      if (!url) {
        const ticket = await createPersonalDownloadTicket(file);
        url = resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo));
      }
      await openFilePreviewFromUrl(file, url);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '预览失败');
    } finally {
      fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
    }
  }

  async function downloadPersonalFile(file: PersonalFileRecord) {
    if (isFileTrashed(file)) return;
    const actionKey = `download-${file.id}`;
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
    try {
      const ticket = await createPersonalDownloadTicket(file);
      window.open(resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo)), '_blank', 'noopener');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '下载失败');
    } finally {
      fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
    }
  }

  async function trashPersonalFile(file: PersonalFileRecord) {
    if (!file.attachmentRefId) return;
    const actionKey = `trash-${file.id}`;
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
    try {
      await employeeApi.files.trash(file.attachmentRefId, { reason: '删除' });
      shell.updatePersonalFile(file.attachmentRefId, { status: '回收站' });
      await refreshFileCenter();
      showToast('已移入回收站');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '删除失败');
    } finally {
      fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
    }
  }

  async function restorePersonalFile(file: PersonalFileRecord) {
    if (!file.attachmentRefId) return;
    const actionKey = `restore-${file.id}`;
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
    try {
      await employeeApi.files.restore(file.attachmentRefId, { reason: '恢复' });
      shell.updatePersonalFile(file.attachmentRefId, { status: '已恢复' });
      await refreshFileCenter();
      showToast('文件已恢复');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '恢复失败');
    } finally {
      fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
    }
  }

  async function purgePersonalFile(file: PersonalFileRecord) {
    if (!file.attachmentRefId) return;
    const actionKey = `purge-${file.id}`;
    fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: true };
    try {
      await employeeApi.files.purge(file.attachmentRefId);
      shell.removePersonalFile(file.attachmentRefId);
      await refreshFileCenter();
      showToast('文件已彻底删除');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '彻底删除失败');
    } finally {
      fileActionLoading.value = { ...fileActionLoading.value, [actionKey]: false };
    }
  }

  return {
    personalFileInput,
    fileUploading,
    uploadPanelOpen,
    uploadDraftFile,
    uploadDragActive,
    uploadResult,
    filePreview,
    filePreviewLoading,
    fileActionLoading,
    filePreviewTitle,
    filePreviewType,
    refreshFileQuota,
    refreshPersonalFiles,
    refreshFileCenter,
    handlePersonalFileSelected,
    openUploadPanel,
    closeUploadPanel,
    openFilePicker,
    handleUploadDrop,
    submitPersonalFileUpload,
    uploadedPersonalFile,
    uploadPersonalFile,
    resolveDownloadUrl,
    createPersonalDownloadTicket,
    openPersonalFilePreview,
    openFilePreviewFromUrl,
    downloadPersonalFile,
    trashPersonalFile,
    restorePersonalFile,
    purgePersonalFile,
    closeFilePreview,
    openPreviewExternal,
  };
}
