const test = require('node:test'); const assert = require('node:assert/strict');
const fs = require('node:fs'); const os = require('node:os'); const path = require('node:path');
const { AssetCache } = require('./asset-cache.cjs');
test('媒体缓存按账号隔离，限制类型及容量，清理不触碰目录外文件', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'workforce-media-'));
  try {
    const cache = new AssetCache(dir, 500);
    cache.put('a', 'image', Buffer.from('fixture'), 'image/png');
    assert.equal(cache.get('a', 'image').base64, Buffer.from('fixture').toString('base64'));
    assert.equal(cache.get('b', 'image'), null);
    assert.throws(() => cache.put('a', 'x', Buffer.from('html'), 'text/html'));
    for (let i = 0; i < 10; i++) cache.put('a', String(i), Buffer.alloc(100), 'image/png');
    assert.ok(cache.bytes() <= 500);
    fs.writeFileSync(path.join(dir, 'do-not-remove'), 'keep'); cache.clear();
    assert.equal(cache.bytes(), 0); assert.equal(fs.readFileSync(path.join(dir, 'do-not-remove'), 'utf8'), 'keep');
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
