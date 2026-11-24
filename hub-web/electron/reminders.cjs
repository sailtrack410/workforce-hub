function dueReminders(todos, delivered, now = new Date()) {
  return todos.filter(todo => {
    const time = Date.parse((todo.reminderAt || '').replace(' ', 'T'));
    return todo.status === 'PENDING' && Number.isFinite(time) && time <= +now && +now - time < 86400000 && !delivered.has(`${todo.todoId}:${todo.reminderAt}`);
  });
}
module.exports = { dueReminders };
