const test = require('node:test');
const assert = require('node:assert/strict');
const { MessageStore } = require('./message-store.cjs');

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
