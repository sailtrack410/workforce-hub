import { afterEach, describe, expect, it, vi } from 'vitest';
import { uploadToTicketTarget } from './upload-target';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('uploadToTicketTarget', () => {
  it('uses credential-free fetch for Vite bridge uploads without any host csrf header casing', async () => {
    const file = new File(['handbook'], 'handbook.pdf', { type: 'application/pdf' });
    const response = new Response('', { status: 200 });
    const fetchMock = vi.fn().mockResolvedValue(response);
    const onProgress = vi.fn();
    vi.stubGlobal('window', { location: { origin: 'https://admin.example.com' } });
    vi.stubGlobal('fetch', fetchMock);

    await expect(uploadToTicketTarget('target/uploads/handbook.pdf', file, {
      'Content-Type': 'application/pdf',
      'x-xsrf-token': 'lowercase-token',
      'X-xSrF-ToKeN': 'mixed-case-token',
    }, onProgress)).resolves.toBe(response);

    expect(fetchMock).toHaveBeenCalledWith('/__admin-local-upload', {
      method: 'PUT',
      body: file,
      headers: {
        'Content-Type': 'application/pdf',
        'X-Upload-Path': encodeURIComponent('target/uploads/handbook.pdf'),
      },
      credentials: 'omit',
    });
    expect(onProgress).toHaveBeenCalledTimes(1);
    expect(onProgress).toHaveBeenCalledWith(100);
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('uses credential-free fetch for same-origin object storage without any host csrf header casing', async () => {
    const file = new File(['handbook'], 'handbook.pdf', { type: 'application/pdf' });
    const response = new Response('', { status: 200 });
    const fetchMock = vi.fn().mockResolvedValue(response);
    const onProgress = vi.fn();
    const uploadUrl = 'https://admin.example.com/uploads/handbook.pdf';
    const headers = {
      'Content-Type': 'application/pdf',
      'X-Upload-Ticket': 'ticket-1',
      'X-XsRf-ToKeN': 'mixed-case-token',
    };
    vi.stubGlobal('window', { location: { origin: 'https://admin.example.com' } });
    vi.stubGlobal('fetch', fetchMock);

    await expect(uploadToTicketTarget(uploadUrl, file, headers, onProgress)).resolves.toBe(response);

    expect(fetchMock).toHaveBeenCalledWith(uploadUrl, {
      method: 'PUT',
      body: file,
      headers: {
        'Content-Type': 'application/pdf',
        'X-Upload-Ticket': 'ticket-1',
      },
      credentials: 'omit',
    });
    expect(onProgress).toHaveBeenCalledWith(100);
    expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).has('x-xsrf-token')).toBe(false);
  });

  it('uses credential-free XHR with progress for cross-origin uploads without any host csrf header casing', async () => {
    const file = new File(['handbook'], 'handbook.pdf', { type: 'application/pdf' });
    const uploadUrl = 'https://storage.example.com/uploads/handbook.pdf';
    const headers = {
      'Content-Type': 'application/pdf',
      'X-Upload-Ticket': 'ticket-1',
      'x-XsRf-ToKeN': 'mixed-case-token',
    };
    const onProgress = vi.fn();
    const fetchMock = vi.fn();
    const xhr = {
      upload: {} as XMLHttpRequestUpload,
      open: vi.fn(),
      setRequestHeader: vi.fn(),
      send: vi.fn(),
      withCredentials: true,
      responseText: 'uploaded',
      status: 201,
      statusText: 'Created',
      onload: null as XMLHttpRequest['onload'],
      onerror: null as XMLHttpRequest['onerror'],
      onabort: null as XMLHttpRequest['onabort'],
    };
    xhr.send.mockImplementation(() => {
      const onUploadProgress = xhr.upload.onprogress as ((event: ProgressEvent) => void) | null;
      const onLoad = xhr.onload as ((event: ProgressEvent) => void) | null;
      onUploadProgress?.({ lengthComputable: true, loaded: 1, total: 4 } as ProgressEvent);
      onUploadProgress?.({ lengthComputable: true, loaded: 3, total: 4 } as ProgressEvent);
      onLoad?.({} as ProgressEvent);
    });
    const XMLHttpRequestMock = vi.fn(function XMLHttpRequestMock() {
      return xhr;
    });
    vi.stubGlobal('window', { location: { origin: 'https://admin.example.com' } });
    vi.stubGlobal('fetch', fetchMock);
    vi.stubGlobal('XMLHttpRequest', XMLHttpRequestMock);

    const response = await uploadToTicketTarget(uploadUrl, file, headers, onProgress);

    expect(fetchMock).not.toHaveBeenCalled();
    expect(XMLHttpRequestMock).toHaveBeenCalledTimes(1);
    expect(xhr.open).toHaveBeenCalledWith('PUT', uploadUrl);
    expect(xhr.withCredentials).toBe(false);
    expect(xhr.setRequestHeader.mock.calls).toEqual([
      ['Content-Type', 'application/pdf'],
      ['X-Upload-Ticket', 'ticket-1'],
    ]);
    expect(xhr.setRequestHeader.mock.calls.some(([key]) => key.toLowerCase() === 'x-xsrf-token')).toBe(false);
    expect(xhr.send).toHaveBeenCalledWith(file);
    expect(onProgress.mock.calls).toEqual([[25], [75]]);
    expect(response.status).toBe(201);
    await expect(response.text()).resolves.toBe('uploaded');
  });
});
