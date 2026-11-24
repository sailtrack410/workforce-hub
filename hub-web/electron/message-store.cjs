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

  list(userId, conversationId, { beforeSeq, size = 200 } = {}) {
    if (beforeSeq != null && (!Number.isSafeInteger(beforeSeq) || beforeSeq <= 0)) throw new TypeError('消息游标无效');
    if (!Number.isSafeInteger(size) || size <= 0) throw new TypeError('消息分页大小无效');
    const rows = this.db.prepare(`
      SELECT payload FROM (SELECT payload,seq_no FROM im_messages
      WHERE user_id = ? AND conversation_id = ? AND (? IS NULL OR seq_no < ?)
        AND COALESCE(json_extract(payload, '$.status'), '') <> 'PENDING'
      ORDER BY seq_no DESC LIMIT ?) ORDER BY seq_no
    `).all(userId, conversationId, beforeSeq ?? null, beforeSeq ?? null, Math.min(size, 200));
    return rows.flatMap((row) => {
      try { return [JSON.parse(row.payload)]; } catch { return []; }
    });
  }

  replace(userId, conversationId, messages, range) {
    if (range && (!Number.isSafeInteger(range.fromSeq) || !Number.isSafeInteger(range.toSeq) || range.fromSeq < 0 || range.toSeq < range.fromSeq)) throw new TypeError('消息同步范围无效');
    this.db.exec('BEGIN IMMEDIATE');
    try {
      if (range) this.db.prepare('DELETE FROM im_messages WHERE user_id=? AND conversation_id=? AND seq_no BETWEEN ? AND ?').run(userId, conversationId, range.fromSeq, range.toSeq);
      else this.db.prepare('DELETE FROM im_messages WHERE user_id=? AND conversation_id=?').run(userId, conversationId);
      const statement = this.db.prepare('INSERT INTO im_messages(user_id,conversation_id,message_id,seq_no,sent_at,payload) VALUES(?,?,?,?,?,?) ON CONFLICT(user_id,message_id) DO UPDATE SET seq_no=excluded.seq_no,sent_at=excluded.sent_at,payload=excluded.payload');
      for (const message of messages) {
        if (String(message.conversationId) !== String(conversationId) || message.messageId == null || !Number.isFinite(message.seqNo)) throw new Error('消息格式无效');
        if (range && (message.seqNo < range.fromSeq || message.seqNo > range.toSeq)) throw new Error('消息超出同步范围');
        statement.run(userId, conversationId, String(message.messageId), message.seqNo, message.sentAt ?? null, JSON.stringify(message));
      }
      this.db.exec('COMMIT');
    } catch (error) { this.db.exec('ROLLBACK'); throw error; }
  }
  remove(userId, messageId) { this.db.prepare('DELETE FROM im_messages WHERE user_id=? AND message_id=?').run(userId, messageId); }
  clear(userId, conversationId) { this.db.prepare('DELETE FROM im_messages WHERE user_id=? AND conversation_id=?').run(userId, conversationId); }

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
