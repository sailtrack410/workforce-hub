const test = require('node:test'); const assert = require('node:assert/strict');
const { dueReminders } = require('./reminders.cjs');
test('本机提醒补发休眠期间到期事项，跳过已完成、未来、过期及已送达项', () => {
  const now = new Date(2026, 10, 25, 12);
  const todo = (id, time, status = 'PENDING') => ({ todoId: id, reminderAt: time, status });
  const items = [todo('1', '2026-11-25 11:59:00'), todo('2', '2026-11-25 12:01:00'), todo('3', '2026-11-25 11:00:00', 'DONE'), todo('4', '2026-11-23 12:00:00'), todo('5', '2026-11-25 11:00:00')];
  assert.deepEqual(dueReminders(items, new Set(['5:2026-11-25 11:00:00']), now).map(t => t.todoId), ['1']);
});
