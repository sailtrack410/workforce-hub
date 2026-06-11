import { describe, expect, it } from 'vitest';
import { fileSizeLabel, fileTypeLabelFromName } from './file-format';

describe('file-format utils', () => {
  it('formats file sizes with compact binary units', () => {
    expect(fileSizeLabel(0)).toBe('0B');
    expect(fileSizeLabel(512)).toBe('512B');
    expect(fileSizeLabel(1536)).toBe('1.5KB');
    expect(fileSizeLabel(12 * 1024)).toBe('12KB');
    expect(fileSizeLabel(2.5 * 1024 * 1024)).toBe('2.5MB');
    expect(fileSizeLabel(1024 * 1024 * 1024)).toBe('1.0GB');
  });

  it('derives file type labels from extension or mime type', () => {
    expect(fileTypeLabelFromName('考勤.xlsx')).toBe('XLSX');
    expect(fileTypeLabelFromName('archive.longextension')).toBe('LONGEXTE');
    expect(fileTypeLabelFromName('', 'application/pdf')).toBe('PDF');
    expect(fileTypeLabelFromName('无扩展名')).toBe('文件');
  });
});
