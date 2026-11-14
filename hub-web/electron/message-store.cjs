const { DatabaseSync } = require('node:sqlite');

class MessageStore {
  constructor(filename) {
    this.db = new DatabaseSync(filename);
    this.db.exec(`
      PRAGMA journal_mode = WAL;
      PRAGMA foreign_keys = ON;
      CREATE TABLE IF NOT EXISTS im_messages (
        user_id TEXT NOT NULL,
        conversation_id TEXT NOT NULL,
        message_id TEXT NOT NULL,
        seq_no INTEGER NOT NULL,
        sent_at TEXT,
        payload TEXT NOT NULL,
        PRIMARY KEY (user_id, message_id)
      );
      CREATE INDEX IF NOT EXISTS idx_im_messages_conversation
        ON im_messages (user_id, conversation_id, seq_no);
    `);
  }

  list(userId, conversationId) {
    const rows = this.db.prepare(`
      SELECT payload FROM im_messages
      WHERE user_id = ? AND conversation_id = ? ORDER BY seq_no
    `).all(userId, conversationId);
    return rows.flatMap((row) => {
      try { return [JSON.parse(row.payload)]; } catch { return []; }
    });
  }

  save(userId, messages) {
    if (!Array.isArray(messages)) throw new TypeError('消息格式无效');
    this.db.exec('BEGIN IMMEDIATE');
    try {
      const statement = this.db.prepare(`
        INSERT INTO im_messages (user_id, conversation_id, message_id, seq_no, sent_at, payload)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(user_id, message_id) DO UPDATE SET
          conversation_id = excluded.conversation_id, seq_no = excluded.seq_no,
          sent_at = excluded.sent_at, payload = excluded.payload
      `);
      for (const message of messages) {
        if (!message || message.messageId == null || message.conversationId == null || !Number.isFinite(message.seqNo)) {
          throw new TypeError('消息缺少必要字段');
        }
        statement.run(userId, String(message.conversationId), String(message.messageId),
          message.seqNo, message.sentAt ?? null, JSON.stringify(message));
      }
      this.db.exec('COMMIT');
    } catch (error) {
      this.db.exec('ROLLBACK');
      throw error;
    }
  }

  close() {
    this.db.close();
  }
}

module.exports = { MessageStore };
