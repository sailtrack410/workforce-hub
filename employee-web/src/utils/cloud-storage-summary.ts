import type { PersonalFileRecord } from '@/stores/shell';

export type StorageSegmentKey = 'document' | 'image' | 'video' | 'other';

export interface StorageSegment {
  key: StorageSegmentKey;
  label: string;
  color: string;
  value: number;
  percent: number;
}

export function buildStorageOverview(files: PersonalFileRecord[], usedPercent: number) {
  const totals = files.reduce<Record<StorageSegmentKey, number>>((next, file) => {
    next[storageTypeOf(file)] += file.fileSizeBytes ?? parseSizeBytes(file.size);
    return next;
  }, {
    document: 0,
    image: 0,
    video: 0,
    other: 0,
  });
  const total = Math.max(0, Object.values(totals).reduce((sum, value) => sum + value, 0));
  return {
    usedPercent: Math.max(0, Math.min(100, usedPercent)),
    segments: [
      { key: 'document', label: '文档', color: '#ef4444', value: totals.document },
      { key: 'image', label: '图像', color: '#2f80ed', value: totals.image },
      { key: 'video', label: '视频', color: '#f4c20d', value: totals.video },
      { key: 'other', label: '其他', color: '#22c55e', value: totals.other },
    ].map((item) => ({
      ...item,
      percent: total > 0 ? (item.value / total) * 100 : 0,
    })),
  };
}

function parseSizeBytes(value?: string) {
  const match = String(value ?? '').trim().match(/^([\d.]+)\s*(B|KB|MB|GB|TB)$/i);
  if (!match) return 0;
  const size = Number(match[1]);
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const unitIndex = units.indexOf(match[2].toUpperCase());
  return Number.isFinite(size) && unitIndex >= 0 ? size * (1024 ** unitIndex) : 0;
}

function storageTypeOf(file: PersonalFileRecord): StorageSegmentKey {
  const mime = file.mimeType?.toLowerCase() ?? '';
  const type = file.type.toLowerCase();
  const name = file.name.toLowerCase();
  if (mime.startsWith('image/') || ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].some((ext) => type.includes(ext) || name.endsWith(`.${ext}`))) return 'image';
  if (mime.startsWith('video/') || ['mp4', 'mov', 'avi', 'mkv', 'webm'].some((ext) => type.includes(ext) || name.endsWith(`.${ext}`))) return 'video';
  if (
    mime.includes('pdf')
    || mime.includes('document')
    || mime.includes('spreadsheet')
    || mime.includes('presentation')
    || ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'csv'].some((ext) => type.includes(ext) || name.endsWith(`.${ext}`))
  ) return 'document';
  return 'other';
}
