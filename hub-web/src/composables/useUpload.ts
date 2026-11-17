// hub-web 上传工具：申请票据 → PUT 对象存储 → 确认上传
import { fileApi } from '@/api';
import type { EntityId } from '@/api/types';

async function sha256Hex(file: File): Promise<string> {
  const buffer = await file.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(digest)).map((b) => b.toString(16).padStart(2, '0')).join('');
}

async function putObject(ticket: Awaited<ReturnType<typeof fileApi.createUploadTicket>>, file: File): Promise<void> {
  const headers: Record<string, string> = { ...(ticket.headers ?? {}) };
  if (!headers['Content-Type'] && !headers['content-type']) {
    headers['Content-Type'] = file.type || 'application/octet-stream';
  }
  let response: Response;
  if (/^https?:\/\//i.test(ticket.uploadUrl!)) {
    response = await fetch(ticket.uploadUrl!, { method: 'PUT', headers, body: file });
  } else {
    if (window.workforceDesktop) {
      // LOCAL 文件由 Electron 写入票据指定目录，再由服务端确认大小与哈希。
      await window.workforceDesktop.writeLocalUpload(ticket.uploadUrl!, Array.from(new Uint8Array(await file.arrayBuffer())));
      response = Response.json({ ok: true });
    } else {
      response = await fetch('/__employee-local-upload', {
        method: 'PUT',
        credentials: 'include',
        headers: { ...headers, 'X-Upload-Path': ticket.uploadUrl! },
        body: file,
      });
    }
  }
  if (!response.ok) {
    throw new Error(await response.text().catch(() => '') || '文件上传失败');
  }
}

// 通用员工端上传（云空间/IM 共用流程）
export async function uploadEmployeeFile(request: {
  file: File;
  ownerType: string;
  ownerId?: EntityId | null;
  folderId?: EntityId | null;
}): Promise<{ attachmentRefId: EntityId }> {
  const { file, ownerType, ownerId = null, folderId = null } = request;
  const contentSha256 = await sha256Hex(file);
  const ticket = await fileApi.createUploadTicket({
    ownerType,
    ownerId,
    folderId,
    fileName: file.name,
    mimeType: file.type || 'application/octet-stream',
    fileSize: file.size,
    contentSha256,
    clientRequestId: `hw-${ownerType.toLowerCase()}-${Date.now()}-${file.name}`,
  });

  // 秒传命中
  if (ticket.deduplicated && ticket.attachmentRefId) {
    return { attachmentRefId: ticket.attachmentRefId };
  }
  if (!ticket.uploadTicketId || !ticket.objectKey || !ticket.uploadUrl) {
    throw new Error('上传地址不可用');
  }

  await putObject(ticket, file);

  const confirmed = await fileApi.confirmUpload({
    uploadTicketId: ticket.uploadTicketId,
    objectKey: ticket.objectKey,
    contentSha256,
    fileSize: file.size,
    ownerType,
    ownerId,
    displayName: file.name,
  });
  return { attachmentRefId: confirmed.attachmentRefId };
}

export async function uploadImAttachment(file: File): Promise<{ attachmentRefId: EntityId }> {
  return uploadEmployeeFile({ file, ownerType: 'IM_MESSAGE_PENDING' });
}
