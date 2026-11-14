const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const { writeLocalUpload } = require('./local-upload.cjs');

test('仅写入本机上传目录，路径穿越会被拒绝', async () => {
  await assert.rejects(() => writeLocalUpload('/tmp/forbidden.txt', [1]), /上传路径/);
  await assert.rejects(() => writeLocalUpload(path.join(os.homedir(), '.workforce-hub/files/uploads/../escape.txt'), [1]), /上传路径/);
});

test('按上传票据写入文件内容', async () => {
  const target = path.join(os.homedir(), '.workforce-hub/files/uploads', `upload-test-${process.pid}.bin`);
  try {
    await writeLocalUpload(target, [1, 2, 3]);
    assert.deepEqual([...await fs.readFile(target)], [1, 2, 3]);
  } finally {
    await fs.unlink(target).catch(() => undefined);
  }
});
