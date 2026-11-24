const test = require('node:test'); const assert = require('node:assert/strict');
const fs = require('node:fs'); const os = require('node:os'); const path = require('node:path');
const { Transfers } = require('./transfers.cjs');
test('上传暂存重启可恢复、按账号隔离，成功释放正文并保留记录', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'workforce-transfers-'));
  try {
    let files = new Transfers(dir); files.stage('a', 'id', [1, 2, 3], { fileName: 'test.txt', ownerType: 'PERSONAL_FILE' });
    files.status('a', 'id', 'failed'); files = new Transfers(dir);
    assert.equal(files.list('b').length, 0); assert.equal(files.list('a')[0].status, 'failed');
    assert.deepEqual(files.read('a', 'id').bytes, [1, 2, 3]); assert.equal(files.bytes('a'), 3);
    files.status('a', 'id', 'done'); assert.equal(files.bytes('a'), 0);
    assert.equal(files.list('a')[0].status, 'done');
    assert.throws(() => files.read('b', 'id')); files.discard('a', 'id'); assert.equal(files.list('a').length, 0);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
