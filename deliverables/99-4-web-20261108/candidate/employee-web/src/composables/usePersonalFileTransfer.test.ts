import { afterEach, describe, expect, it, vi } from 'vitest';
import { usePersonalFileTransfer } from './usePersonalFileTransfer';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('usePersonalFileTransfer', () => {
  it('uploads to remote object storage without host credentials or csrf', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    vi.stubGlobal('fetch', fetcher);
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' });
    const { uploadToTicketTarget } = usePersonalFileTransfer();

    await uploadToTicketTarget('https://objects.example/upload', file, {
      'Content-Type': 'text/plain',
      'X-Object-Header': 'signed-value',
      'x-xsrf-token': 'lowercase-token',
    });

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][0]).toBe('https://objects.example/upload');
    expect(fetcher.mock.calls[0][1]).toMatchObject({
      method: 'PUT',
      body: file,
      credentials: 'omit',
      headers: {
        'Content-Type': 'text/plain',
        'X-Object-Header': 'signed-value',
      },
    });
    expect(new Headers(fetcher.mock.calls[0][1]?.headers).has('x-xsrf-token')).toBe(false);
  });

  it('uploads through the Vite bridge without host credentials or csrf', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    vi.stubGlobal('fetch', fetcher);
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' });
    const { uploadToTicketTarget } = usePersonalFileTransfer();

    await uploadToTicketTarget('target/uploads/hello.txt', file, {
      'Content-Type': 'text/plain',
      'X-Object-Header': 'signed-value',
      'X-xSrF-ToKeN': 'mixed-case-token',
    });

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][0]).toBe('/__employee-local-upload');
    expect(fetcher.mock.calls[0][1]).toMatchObject({
      method: 'PUT',
      body: file,
      credentials: 'omit',
      headers: {
        'Content-Type': 'text/plain',
        'X-Upload-Path': 'target/uploads/hello.txt',
      },
    });
    expect(new Headers(fetcher.mock.calls[0][1]?.headers).has('X-XsRf-ToKeN')).toBe(false);
  });
});
