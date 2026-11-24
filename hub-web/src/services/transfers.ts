import { currentContext } from './localData';
import { uploadEmployeeFile } from '@/composables/useUpload';
export interface UploadRecord {
  id: string; fileName: string; mimeType: string; fileSize: number; ownerType: string;
  ownerId?: string | number | null; folderId?: string | number | null; taskId?: string; status?: string; createdAt?: number;
}
export async function uploads() { const context = await currentContext(); return context ? window.workforceDesktop!.uploads(context) : []; }
export async function retryUpload(id: string) {
  const context = await currentContext(); if (!context) return;
  const record = await window.workforceDesktop!.readUpload(context, id);
  const file = new File([new Uint8Array(record.bytes)], record.fileName, { type: record.mimeType });
  return uploadEmployeeFile({ file, ownerType: record.ownerType, ownerId: record.ownerId, folderId: record.folderId, transferId: id, taskId: record.taskId });
}
export async function discardUpload(id: string) { const context = await currentContext(); if (context) await window.workforceDesktop!.discardUpload(context, id); }
