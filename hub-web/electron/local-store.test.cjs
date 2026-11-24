const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { LocalStore } = require('./local-store.cjs');
test('损坏数据库隔离原件并恢复空缓存，不直接删除原有文件', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'workforce-corrupt-'));
  try {
    const file = path.join(dir, 'cache.sqlite'); const pristine = Buffer.from('invalid sqlite fixture');
    fs.writeFileSync(file, pristine); const store = new LocalStore(file);
    assert.equal(store.recovered, true);
    const preserved = fs.readdirSync(dir).find(name => name.startsWith('cache.sqlite.corrupt-'));
    assert.deepEqual(fs.readFileSync(path.join(dir, preserved)), pristine);
    assert.equal(store.stats('account').entries, 0); store.close();
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('SQLite 重启后按服务器和账号隔离缓存、草稿与待同步操作，清理不损失未提交内容', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'workforce-local-'));
  try {
    const file = path.join(dir, 'local.sqlite'); let store = new LocalStore(file);
    store.put('server1:user1', '/tasks', [{ id: '1' }]);
    store.put('server2:user1', '/tasks', [{ id: '2' }]);
    store.put('server1:user1', '/expired', [], -100);
    store.draft('server1:user1', 'todo', { title: 'unfinished' });
    store.enqueue('server1:user1', { id: 'operation1', body: { title: 'pending' } });
    store.close(); store = new LocalStore(file);
    assert.equal(store.get('server1:user1', '/tasks').value[0].id, '1');
    assert.equal(store.get('server2:user1', '/tasks').value[0].id, '2');
    assert.equal(store.get('server1:user2', '/tasks'), null);
    assert.equal(store.get('server1:user1', '/expired'), null);
    assert.equal(store.readDraft('server1:user2', 'todo'), null);
    assert.equal(store.readDraft('server1:user1', 'todo').title, 'unfinished');
    assert.equal(store.queue('server1:user1')[0].id, 'operation1');
    store.clearCache('server1:user1');
    assert.equal(store.get('server1:user1', '/tasks'), null);
    assert.equal(store.stats('server1:user1').drafts, 1); assert.equal(store.stats('server1:user1').pending, 1);
    store.queueState('server1:user1', 'operation1', 'conflict', 'changed');
    assert.equal(store.queue('server1:user1')[0].state, 'conflict');
    store.queueState('server1:user1', 'operation1', 'done');
    assert.equal(store.queue('server1:user1').length, 0); store.close();
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
