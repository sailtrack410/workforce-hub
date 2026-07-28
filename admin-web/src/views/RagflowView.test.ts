import { afterEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { uploadToTicketTarget } from '../utils/upload-target';

const source = readFileSync(fileURLToPath(new URL('./RagflowView.vue', import.meta.url)), 'utf8');
const uploadDialogSource = readFileSync(fileURLToPath(new URL('../components/ragflow/RagflowUploadDialog.vue', import.meta.url)), 'utf8');
const settingsDialogSource = readFileSync(fileURLToPath(new URL('../components/ragflow/RagflowSettingsDialog.vue', import.meta.url)), 'utf8');
const uploadTargetSource = readFileSync(fileURLToPath(new URL('../utils/upload-target.ts', import.meta.url)), 'utf8');

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('RagflowView', () => {
  it('supports a multi-file upload queue', () => {
    expect(source).toContain('const uploadQueue = ref<UploadQueueItem[]>([])');
    expect(source).toContain('function setUploadFiles(files: File[])');
    expect(uploadDialogSource).toContain('type="file" multiple');
    expect(uploadDialogSource).toContain('v-for="item in items"');
  });

  it('closes the upload dialog after files are registered for background ingest', () => {
    expect(source).toContain('await adminApi.ragflow.addDocument(selected.value.spaceId, attachmentRefId)');
    expect(source).toContain('uploadVisible.value = false');
    expect(source).toContain('正在后台入库');
    expect(source).not.toContain('window.setTimeout(resolve, 600)');
  });

  it('uses browser upload progress events for ticket target uploads', () => {
    expect(uploadTargetSource).toContain('XMLHttpRequest');
    expect(uploadTargetSource).toContain('xhr.upload.onprogress');
    expect(uploadTargetSource).toContain('onProgress(Math.round');
  });

  it('uses credential-free fetch for Vite bridge uploads', async () => {
    const file = new File(['handbook'], 'handbook.pdf', { type: 'application/pdf' });
    const response = new Response('', { status: 200 });
    const fetchMock = vi.fn().mockResolvedValue(response);
    const onProgress = vi.fn();
    vi.stubGlobal('window', { location: { origin: 'https://admin.example.com' } });
    vi.stubGlobal('fetch', fetchMock);

    await expect(uploadToTicketTarget('target/uploads/handbook.pdf', file, {
      'Content-Type': 'application/pdf',
      'X-XSRF-TOKEN': 'must-not-leak',
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
    expect(fetchMock.mock.calls[0]?.[1]?.headers).not.toHaveProperty('X-XSRF-TOKEN');
  });

  it('uses credential-free fetch for same-origin uploads', async () => {
    const file = new File(['handbook'], 'handbook.pdf', { type: 'application/pdf' });
    const response = new Response('', { status: 200 });
    const fetchMock = vi.fn().mockResolvedValue(response);
    const onProgress = vi.fn();
    const uploadUrl = 'https://admin.example.com/uploads/handbook.pdf';
    const headers = { 'Content-Type': 'application/pdf', 'X-Upload-Ticket': 'ticket-1' };
    vi.stubGlobal('window', { location: { origin: 'https://admin.example.com' } });
    vi.stubGlobal('fetch', fetchMock);

    await expect(uploadToTicketTarget(uploadUrl, file, headers, onProgress)).resolves.toBe(response);

    expect(fetchMock).toHaveBeenCalledWith(uploadUrl, {
      method: 'PUT',
      body: file,
      headers,
      credentials: 'omit',
    });
    expect(onProgress).toHaveBeenCalledWith(100);
    expect(fetchMock.mock.calls[0]?.[1]?.headers).not.toHaveProperty('X-XSRF-TOKEN');
  });

  it('uses credential-free XHR with progress for cross-origin uploads', async () => {
    const file = new File(['handbook'], 'handbook.pdf', { type: 'application/pdf' });
    const uploadUrl = 'https://storage.example.com/uploads/handbook.pdf';
    const headers = { 'Content-Type': 'application/pdf', 'X-Upload-Ticket': 'ticket-1' };
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
    expect(xhr.setRequestHeader.mock.calls.flat()).not.toContain('X-XSRF-TOKEN');
    expect(xhr.send).toHaveBeenCalledWith(file);
    expect(onProgress.mock.calls).toEqual([[25], [75]]);
    expect(response.status).toBe(201);
    await expect(response.text()).resolves.toBe('uploaded');
  });

  it('uploads ragflow documents through ragflow-scoped endpoints', () => {
    expect(source).toContain('adminApi.ragflow.createUploadTicket');
    expect(source).toContain('adminApi.ragflow.confirmUpload');
    expect(source).not.toContain('adminApi.files.createUploadTicket({');
    expect(source).not.toContain('adminApi.files.confirmUpload({');
  });

  it('deletes knowledge spaces through a ragflow-scoped endpoint', () => {
    expect(source).toContain('RagflowSpaceDeleteDialog');
    expect(source).toContain('adminApi.ragflow.deleteSpace');
    expect(source).toContain('spaceDeleteVisible');
  });

  it('keeps uneditable model settings out of the knowledge space settings form', () => {
    expect(settingsDialogSource).not.toContain('const vectorSettingsLocked = computed');
    expect(settingsDialogSource).not.toContain(':disabled="vectorSettingsLocked"');
    expect(settingsDialogSource).not.toContain('模型设置');
  });
});
