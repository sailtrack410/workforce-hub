// hub-web 上传工具：申请票据 → PUT 对象存储 → 确认上传
import { fileApi } from '@/api';
import type { EntityId } from '@/api/types';

async function sha256Hex(file: File): Promise<string> {
  const buffer = await file.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, '0')).join('');
}

export async function uploadImAttachment(file: File): Promise<{ attachmentRefId: EntityId }> {
  const contentSha256 = await sha256Hex(file);
  const ticket = await fileApi.createUploadTicket({
    ownerType: 'IM_MESSAGE_PENDING',
    ownerId: null,
    fileName: file.name,
    mimeType: file.type || 'application/octet-stream',
    fileSize: file.size,
    contentSha256,
    clientRequestId: `hw-im-${Date.now()}-${file.name}`,
  });

  // 秒传命中
  if (ticket.deduplicated && ticket.attachmentRefId) {
    return { attachmentRefId: ticket.attachmentRefId };
  }
  if (!ticket.uploadTicketId || !ticket.objectKey || !ticket.uploadUrl) {
    throw new Error('上传地址不可用');
  }

  const headers: Record<string, string> = { ...(ticket.headers ?? {}) };
  if (!headers['Content-Type'] && !headers['content-type']) {
    headers['Content-Type'] = file.type || 'application/octet-stream';
  }
  let response: Response;
  if (/^https?:\/\//i.test(ticket.uploadUrl)) {
    response = await fetch(ticket.uploadUrl, { method: 'PUT', headers, body: file });
  } else {
    response = await fetch('/__employee-local-upload', {
      method: 'PUT',
      headers: { ...headers, 'X-Upload-Path': ticket.uploadUrl },
      body: file,
    });
  }
  if (!response.ok) {
    throw new Error(await response.text().catch(() => '') || '文件上传失败');
  }

  const confirmed = await fileApi.confirmUpload({
    uploadTicketId: ticket.uploadTicketId,
    objectKey: ticket.objectKey,
    contentSha256,
    fileSize: file.size,
    ownerType: 'IM_MESSAGE_PENDING',
    ownerId: null,
    displayName: file.name,
  });
  return { attachmentRefId: confirmed.attachmentRefId };
}
