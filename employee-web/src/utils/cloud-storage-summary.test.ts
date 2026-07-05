import { describe, expect, it } from 'vitest';
import { buildStorageOverview, parseStorageSizeBytes } from './cloud-storage-summary';
import type { PersonalFileRecord } from '@/stores/shell';

describe('cloud storage summary', () => {
  it('keeps the used bar proportional to total quota and splits only the used area by type', () => {
    const files: PersonalFileRecord[] = [
      { id: 'doc', name: 'doc.pdf', type: 'PDF', size: '600B', createdAt: '', status: '已确认', mimeType: 'application/pdf', fileSizeBytes: 600 },
      { id: 'image', name: 'image.png', type: 'PNG', size: '300B', createdAt: '', status: '已确认', mimeType: 'image/png', fileSizeBytes: 300 },
      { id: 'video', name: 'video.mp4', type: 'MP4', size: '100B', createdAt: '', status: '已确认', mimeType: 'video/mp4', fileSizeBytes: 100 },
    ];

    const overview = buildStorageOverview(files, 250, 1000);

    expect(overview.usedPercent).toBe(25);
    expect(overview.segments.map((item) => [item.key, item.percent])).toEqual([
      ['document', 60],
      ['image', 30],
      ['video', 10],
      ['other', 0],
    ]);
  });

  it('derives tiny quota usage from used and total labels', () => {
    const overview = buildStorageOverview([], parseStorageSizeBytes('1.4 MB'), parseStorageSizeBytes('10 GB'));

    expect(overview.usedPercent).toBeCloseTo(0.0137, 3);
  });
});
