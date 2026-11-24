const test = require('node:test');
const assert = require('node:assert/strict');
const { MessageStore } = require('./message-store.cjs');
test('服务端快照替换同步删除与撤回，单条删除后不会离线复现，分页有上限', () => {
  const store = new MessageStore(':memory:');
  const message = (id) => ({ messageId: String(id), conversationId: '9', seqNo: id, contentText: 'message' });
  store.save('scope-a', Array.from({ length: 250 }, (_, i) => message(i + 1)));
  assert.equal(store.list('scope-a', '9').length, 200);
  store.replace('scope-a', '9', [{ ...message(250), status: 'RECALLED' }]);
  assert.equal(store.list('scope-a', '9').length, 1);
  assert.equal(store.list('scope-a', '9')[0].status, 'RECALLED');
  store.remove('scope-a', '250'); assert.equal(store.list('scope-a', '9').length, 0);
  store.save('scope-a', [message(251)]); store.clear('scope-a', '9');
  assert.equal(store.list('scope-a', '9').length, 0); store.close();
});

test('消息按用户和会话隔离，并按 ID 幂等更新', () => {
  const store = new MessageStore(':memory:');
  const original = { messageId: '10000000000000001', conversationId: '9', seqNo: 1, contentText: '旧内容' };
  store.save('u1', [original]);
  store.save('u1', [{ ...original, contentText: '已撤回' }]);
  store.save('u2', [{ ...original, contentText: '其他用户' }]);
  assert.equal(store.list('u1', '9')[0].contentText, '已撤回');
  assert.equal(store.list('u1', '9').length, 1);
  assert.equal(store.list('u1', 'missing').length, 0);
  assert.equal(store.list('u2', '9')[0].contentText, '其他用户');
  store.close();
});

test('无效消息不写入缓存', () => {
  const store = new MessageStore(':memory:');
  assert.throws(() => store.save('u1', [{ messageId: '1' }]), /必要字段/);
  assert.equal(store.list('u1', '9').length, 0);
  store.close();
});
