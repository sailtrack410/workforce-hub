import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const source = readFileSync(fileURLToPath(new URL('./RagflowView.vue', import.meta.url)), 'utf8');

describe('RagflowView', () => {
  it('shows upload progress while a document is uploading', () => {
    expect(source).toContain('const uploadProgress = ref(0)');
    expect(source).toContain('const uploadStatus = ref');
    expect(source).toContain('class="upload-progress"');
    expect(source).toContain(':value="uploadProgress"');
  });

  it('keeps upload feedback visible after a fast result or an error', () => {
    expect(source).toContain('v-if="uploading || uploadStatus"');
    expect(source).toContain('uploadStatus.value = errorMessage(e)');
    expect(source).toContain('window.setTimeout(resolve, 600)');
  });

  it('uses browser upload progress events for ticket target uploads', () => {
    expect(source).toContain('XMLHttpRequest');
    expect(source).toContain('xhr.upload.onprogress');
    expect(source).toContain('onProgress(Math.round');
  });

  it('uploads ragflow documents through ragflow-scoped endpoints', () => {
    expect(source).toContain('adminApi.ragflow.createUploadTicket');
    expect(source).toContain('adminApi.ragflow.confirmUpload');
    expect(source).not.toContain('adminApi.files.createUploadTicket({');
    expect(source).not.toContain('adminApi.files.confirmUpload({');
  });
});
