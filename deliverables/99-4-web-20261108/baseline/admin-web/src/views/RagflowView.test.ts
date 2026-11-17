import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const source = readFileSync(fileURLToPath(new URL('./RagflowView.vue', import.meta.url)), 'utf8');
const uploadDialogSource = readFileSync(fileURLToPath(new URL('../components/ragflow/RagflowUploadDialog.vue', import.meta.url)), 'utf8');
const settingsDialogSource = readFileSync(fileURLToPath(new URL('../components/ragflow/RagflowSettingsDialog.vue', import.meta.url)), 'utf8');

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
