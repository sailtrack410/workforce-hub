import { employeeApi } from '@/api';
import type { DownloadTicketResponse, EntityId } from '@/api';
import { sha256Hex } from '@/utils/file-record';
import { createClientId } from '@/utils/request-id';

export interface PersonalAttachmentUploadResult {
  attachmentRefId: EntityId;
  displayName: string;
  mimeType: string;
  fileSize: number;
  scanStatus?: string | null;
  createdAt?: string | null;
  deduplicated: boolean;
}

export type EmployeeUploadOwnerType = 'PERSONAL_FILE' | 'USER_AVATAR' | 'IM_MESSAGE_PENDING' | 'IM_GROUP_FILE';

export interface EmployeeAttachmentUploadOptions {
  ownerType: EmployeeUploadOwnerType;
  ownerId?: EntityId | null;
  clientRequestPrefix?: string;
}

export function usePersonalFileTransfer() {
  async function uploadAttachment(file: File, options: EmployeeAttachmentUploadOptions): Promise<PersonalAttachmentUploadResult> {
    const ownerId = options.ownerId ?? null;
    const clientRequestPrefix = options.clientRequestPrefix ?? options.ownerType.toLowerCase();
    const contentSha256 = await sha256Hex(file);
    const ticket = await employeeApi.files.createUploadTicket({
      ownerType: options.ownerType,
      ownerId,
      fileName: file.name,
      mimeType: file.type || 'application/octet-stream',
      fileSize: file.size,
      contentSha256,
      clientRequestId: createClientId(`${clientRequestPrefix}-${file.name}`),
    });

    if (ticket.deduplicated && ticket.attachmentRefId) {
      return {
        attachmentRefId: ticket.attachmentRefId,
        displayName: file.name,
        mimeType: file.type || 'application/octet-stream',
        fileSize: file.size,
        scanStatus: null,
        createdAt: null,
        deduplicated: true,
      };
    }

    if (!ticket.uploadTicketId || !ticket.objectKey || !ticket.uploadUrl) {
      throw new Error('上传地址不可用');
    }

    const uploadHeaders = normalizeUploadHeaders(ticket.headers, file);
    const uploadResponse = await uploadToTicketTarget(ticket.uploadUrl, file, uploadHeaders);
    if (!uploadResponse.ok) {
      throw new Error(await uploadResponse.text().catch(() => '') || '文件上传失败');
    }

    const attachment = await employeeApi.files.confirmUpload({
      uploadTicketId: ticket.uploadTicketId,
      objectKey: ticket.objectKey,
      contentSha256,
      fileSize: file.size,
      ownerType: options.ownerType,
      ownerId,
      displayName: file.name,
    });

    return {
      attachmentRefId: attachment.attachmentRefId,
      displayName: attachment.displayName || file.name,
      mimeType: attachment.mimeType || file.type || 'application/octet-stream',
      fileSize: attachment.fileSize || file.size,
      scanStatus: attachment.scanStatus,
      createdAt: attachment.createdAt,
      deduplicated: attachment.deduplicated,
    };
  }

  async function uploadPersonalAttachment(file: File, clientRequestPrefix = 'personal'): Promise<PersonalAttachmentUploadResult> {
    return uploadAttachment(file, {
      ownerType: 'PERSONAL_FILE',
      ownerId: null,
      clientRequestPrefix,
    });
  }

  async function uploadAvatarAttachment(file: File): Promise<PersonalAttachmentUploadResult> {
    return uploadAttachment(file, {
      ownerType: 'USER_AVATAR',
      ownerId: null,
      clientRequestPrefix: 'avatar',
    });
  }

  async function uploadImMessageAttachment(file: File): Promise<PersonalAttachmentUploadResult> {
    return uploadAttachment(file, {
      ownerType: 'IM_MESSAGE_PENDING',
      ownerId: null,
      clientRequestPrefix: 'im-message',
    });
  }

  async function uploadGroupFileAttachment(file: File, conversationId: EntityId): Promise<PersonalAttachmentUploadResult> {
    return uploadAttachment(file, {
      ownerType: 'IM_GROUP_FILE',
      ownerId: conversationId,
      clientRequestPrefix: 'im-group-file',
    });
  }

  function uploadToTicketTarget(uploadUrl: string, file: File, uploadHeaders: Record<string, string>) {
    if (/^https?:\/\//i.test(uploadUrl)) {
      return fetch(uploadUrl, {
        method: 'PUT',
        body: file,
        headers: uploadHeaders,
      });
    }
    return fetch('/__employee-local-upload', {
      method: 'PUT',
      body: file,
      headers: {
        'Content-Type': uploadHeaders['Content-Type'] ?? (file.type || 'application/octet-stream'),
        'X-Upload-Path': uploadUrl,
      },
    });
  }

  function resolveDownloadUrl(ticket: DownloadTicketResponse, fallback: string) {
    const url = ticket.downloadUrl || fallback;
    if (/^https?:\/\//i.test(url)) return url;
    return url.startsWith('/') ? url : `/${url}`;
  }

  return {
    uploadAttachment,
    uploadPersonalAttachment,
    uploadAvatarAttachment,
    uploadImMessageAttachment,
    uploadGroupFileAttachment,
    uploadToTicketTarget,
    resolveDownloadUrl,
  };
}

function normalizeUploadHeaders(headers: Record<string, string>, file: File) {
  const uploadHeaders = { ...headers };
  if (uploadHeaders['content-type'] && !uploadHeaders['Content-Type']) {
    uploadHeaders['Content-Type'] = uploadHeaders['content-type'];
    delete uploadHeaders['content-type'];
  }
  if (uploadHeaders['Content-Type'] === 'application/octet-stream' && file.type) {
    uploadHeaders['Content-Type'] = file.type;
  }
  if (!uploadHeaders['Content-Type']) {
    uploadHeaders['Content-Type'] = file.type || 'application/octet-stream';
  }
  return uploadHeaders;
}
