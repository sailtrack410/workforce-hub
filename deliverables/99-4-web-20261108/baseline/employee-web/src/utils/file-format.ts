export function fileSizeLabel(bytes?: number | null) {
  if (!bytes || bytes <= 0) return '0B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let size = bytes;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  const precision = size >= 10 || unitIndex === 0 ? 0 : 1;
  return `${size.toFixed(precision)}${units[unitIndex]}`;
}

export function fileTypeLabelFromName(name?: string | null, mimeType = '') {
  const ext = name?.split('.').pop()?.trim();
  if (ext && ext !== name) return ext.slice(0, 8).toUpperCase();
  if (mimeType) return mimeType.split('/').pop()?.slice(0, 8).toUpperCase() || '文件';
  return '文件';
}

export function fileTypeLabel(file: File) {
  return fileTypeLabelFromName(file.name, file.type);
}
