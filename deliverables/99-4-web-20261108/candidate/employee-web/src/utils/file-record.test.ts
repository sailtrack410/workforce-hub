import { describe, expect, it } from 'vitest';
import {
  canInlinePreview,
  fileMimeOf,
  fileNameOf,
  fileSizeOf,
  fileToneOf,
  isFileTrashed,
  sha256Hex,
} from './file-record';
import type { PersonalFileRecord } from '@/stores/shell';
import type { WorkflowRequestAttachmentResponse } from '@/api';

describe('file record helpers', () => {
  it('normalizes personal and workflow file records', () => {
    const personal: PersonalFileRecord = {
      id: 'f1',
      name: 'report.pdf',
      type: 'PDF',
      size: '10 KB',
      createdAt: '',
      status: '已确认',
      mimeType: 'application/pdf',
    };
    const attachment: WorkflowRequestAttachmentResponse = {
      attachmentRefId: 10,
      displayName: 'data.json',
      fileSize: 256,
      mimeType: 'application/json',
      scanStatus: 'READY',
      contentSha256: 'hash',
    };

    expect(fileNameOf(personal)).toBe('report.pdf');
    expect(fileNameOf(attachment)).toBe('data.json');
    expect(fileSizeOf(personal)).toBe('10 KB');
    expect(fileSizeOf(attachment)).toBe('256B');
    expect(fileMimeOf(attachment)).toBe('application/json');
    expect(fileToneOf(attachment)).toBe('code');
    expect(canInlinePreview(personal)).toBe(true);
    expect(isFileTrashed({ ...personal, status: '回收站' })).toBe(true);
  });

  it('hashes file contents with SHA-256 hex output', async () => {
    const digest = await sha256Hex(new File(['abc'], 'sample.txt'));
    expect(digest).toBe('ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  });
});
