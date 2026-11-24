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

test('游标分页可以读取 200 条之前的本地历史，范围更新保留更早记录并同步删除', () => {
  const store = new MessageStore(':memory:');
  const message = seqNo => ({ messageId: String(seqNo), conversationId: '9', seqNo, status: 'NORMAL' });
  store.save('scope', Array.from({ length: 350 }, (_, index) => message(index + 1)));
  store.replace('scope', '9', [message(349), { ...message(350), status: 'RECALLED' }], { fromSeq: 348, toSeq: 350 });
  assert.equal(store.list('scope', '9', { beforeSeq: 151, size: 100 })[0].seqNo, 51);
  assert.equal(store.list('scope', '9', { beforeSeq: 51, size: 100 }).length, 50);
  const latest = store.list('scope', '9');
  assert.equal(latest.some(item => item.seqNo === 348), false);
  assert.equal(latest.at(-1).status, 'RECALLED');
  assert.throws(() => store.replace('scope', '9', [message(10)], { fromSeq: 348, toSeq: 350 }), /同步范围/);
  assert.equal(store.list('scope', '9').at(-1).status, 'RECALLED');
  store.close();
});

test('待发送消息由 outbox 恢复，不占用服务端历史的分页窗口', () => {
  const store = new MessageStore(':memory:');
  store.save('scope', [{ messageId: '1', conversationId: '9', seqNo: 1, status: 'NORMAL' },
    ...Array.from({ length: 250 }, (_, index) => ({ messageId: `local-${index}`, conversationId: '9', seqNo: 1000 + index, status: 'PENDING' }))]);
  assert.deepEqual(store.list('scope', '9').map(item => item.messageId), ['1']);
  store.close();
});
