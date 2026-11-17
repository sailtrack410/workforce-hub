import {
  FileArchive,
  FileAudio,
  FileCode,
  FileImage,
  FileJson,
  FileSpreadsheet,
  FileText,
  FileType,
  FileVideo,
} from 'lucide-vue-next';
import type { WorkflowRequestAttachmentResponse } from '@/api';
import type { PersonalFileRecord } from '@/stores/shell';
import { fileSizeLabel } from './file-format';

export type FileLikeRecord = PersonalFileRecord | WorkflowRequestAttachmentResponse;

// 文件上传前计算 SHA-256，用于秒传校验和完整性验证
export async function sha256Hex(file: File) {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) throw new Error('当前环境不支持安全文件校验，请使用 HTTPS 或 localhost');
  const digest = await subtle.digest('SHA-256', await file.arrayBuffer());
  return [...new Uint8Array(digest)]
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

export function fileNameOf(file: FileLikeRecord) {
  return 'displayName' in file ? file.displayName : file.name;
}

export function fileSizeOf(file: FileLikeRecord) {
  return 'fileSize' in file ? fileSizeLabel(file.fileSize) : file.size;
}

export function fileMimeOf(file: FileLikeRecord) {
  return file.mimeType ?? '';
}

// 根据文件名和 MIME 类型返回对应的图标组件，优先用 MIME 判断
export function fileIconOf(file: FileLikeRecord) {
  const name = fileNameOf(file).toLowerCase();
  const mimeType = fileMimeOf(file).toLowerCase();
  if (mimeType.startsWith('image/') || /\.(png|jpe?g|gif|webp|svg)$/i.test(name)) return FileImage;
  if (mimeType.startsWith('audio/') || /\.(mp3|wav|aac|flac|m4a)$/i.test(name)) return FileAudio;
  if (mimeType.startsWith('video/') || /\.(mp4|mov|webm|avi|mkv)$/i.test(name)) return FileVideo;
  if (/\.(xls|xlsx|csv)$/i.test(name)) return FileSpreadsheet;
  if (/\.(json|jsonl)$/i.test(name) || mimeType.includes('json')) return FileJson;
  if (/\.(js|ts|tsx|vue|java|sql|xml|html|css|scss|yml|yaml)$/i.test(name)) return FileCode;
  if (/\.(zip|rar|7z|tar|gz)$/i.test(name)) return FileArchive;
  if (/\.(doc|docx|pdf|txt|md)$/i.test(name)) return FileText;
  return FileType;
}

// 文件类型对应的色系，用于列表项的左侧色条
export function fileToneOf(file: FileLikeRecord) {
  const name = fileNameOf(file).toLowerCase();
  const mimeType = fileMimeOf(file).toLowerCase();
  if (mimeType.startsWith('image/') || /\.(png|jpe?g|gif|webp|svg)$/i.test(name)) return 'image';
  if (mimeType.startsWith('audio/') || /\.(mp3|wav|aac|flac|m4a)$/i.test(name)) return 'audio';
  if (mimeType.startsWith('video/') || /\.(mp4|mov|webm|avi|mkv)$/i.test(name)) return 'video';
  if (/\.(xls|xlsx|csv)$/i.test(name)) return 'sheet';
  if (/\.(json|jsonl|js|ts|tsx|vue|java|sql|xml|html|css|scss|yml|yaml)$/i.test(name) || mimeType.includes('json')) return 'code';
  if (/\.(zip|rar|7z|tar|gz)$/i.test(name)) return 'archive';
  if (/\.(pdf|doc|docx|txt|md)$/i.test(name)) return 'document';
  return 'default';
}

export function canInlinePreview(file: FileLikeRecord) {
  const name = fileNameOf(file).toLowerCase();
  const mimeType = fileMimeOf(file).toLowerCase();
  return (
    mimeType.startsWith('image/')
    || mimeType === 'application/pdf'
    || mimeType.startsWith('text/')
    || mimeType.includes('json')
    || /\.(png|jpe?g|gif|webp|svg|pdf|txt|md|json|csv|log)$/i.test(name)
  );
}

export function isFileTrashed(file: PersonalFileRecord) {
  return file.status === '回收站' || file.status === '已删除';
}
