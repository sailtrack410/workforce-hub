import { computed, ref } from 'vue';
import type { WorkflowRequestAttachmentResponse } from '@/api';
import type { PersonalFileRecord } from '@/stores/shell';
import { canInlinePreview, fileMimeOf, fileNameOf } from '@/utils/file-record';

export type FilePreviewTarget = PersonalFileRecord | WorkflowRequestAttachmentResponse;

export function useFilePreview() {
  const filePreview = ref<{ file: FilePreviewTarget; url: string; inline: boolean; objectUrl?: string } | null>(null);
  const filePreviewLoading = ref(false);
  const filePreviewTitle = computed(() => (filePreview.value ? fileNameOf(filePreview.value.file) : ''));
  const filePreviewType = computed(() => (filePreview.value ? fileMimeOf(filePreview.value.file) : ''));

  // 可内联预览的文件（图片、PDF、文本）先 fetch 到内存，避开下载接口的安全响应头
  async function openFilePreviewFromUrl(file: FilePreviewTarget, url: string) {
    closeFilePreview();
    const inline = canInlinePreview(file);
    if (!inline) {
      filePreview.value = { file, url, inline: false };
      return;
    }
    filePreviewLoading.value = true;
    try {
      const response = await fetch(url, { credentials: 'include' });
      if (!response.ok) {
        let message = '文件预览失败';
        try {
          if ((response.headers.get('Content-Type') ?? '').includes('application/json')) {
            const payload = await response.json() as { message?: string };
            message = payload.message || message;
          } else {
            message = await response.text() || message;
          }
        } catch {
          message = '文件预览失败';
        }
        throw new Error(message);
      }
      const blob = await response.blob();
      const objectUrl = URL.createObjectURL(blob);
      filePreview.value = { file, url: objectUrl, inline: true, objectUrl };
    } catch (error) {
      closeFilePreview();
      throw error;
    } finally {
      filePreviewLoading.value = false;
    }
  }

  // 关闭预览时释放 objectUrl，防止内存泄漏
  function closeFilePreview() {
    if (filePreview.value?.objectUrl) {
      URL.revokeObjectURL(filePreview.value.objectUrl);
    }
    filePreview.value = null;
  }

  function openPreviewExternal() {
    if (!filePreview.value?.url) return;
    window.open(filePreview.value.url, '_blank', 'noopener');
  }

  return {
    filePreview,
    filePreviewLoading,
    filePreviewTitle,
    filePreviewType,
    openFilePreviewFromUrl,
    closeFilePreview,
    openPreviewExternal,
  };
}
