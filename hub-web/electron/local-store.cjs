const { DatabaseSync } = require('node:sqlite');
const fs = require('node:fs');

class LocalStore {
  constructor(filename) {
    this.db = new DatabaseSync(filename);
    try { this.db.prepare('PRAGMA quick_check').get(); }
    catch (error) {
      if (filename === ':memory:' || ![11, 26].includes(error.errcode)) throw error;
      this.db.close();
      const preserved = `${filename}.corrupt-${Date.now()}`;
      for (const suffix of ['', '-wal', '-shm']) if (fs.existsSync(filename + suffix)) fs.renameSync(filename + suffix, preserved + suffix);
      this.db = new DatabaseSync(filename); this.recovered = true;
    }
    if (this.db.prepare('PRAGMA user_version').get().user_version > 1) { this.db.close(); throw new Error('本地数据库版本较新，请升级应用'); }
    this.db.exec(`
      PRAGMA journal_mode=WAL;
      PRAGMA busy_timeout=5000;
      CREATE TABLE IF NOT EXISTS cache_entries (scope TEXT NOT NULL, key TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, accessed_at INTEGER NOT NULL, PRIMARY KEY(scope,key));
      CREATE TABLE IF NOT EXISTS drafts (scope TEXT NOT NULL, key TEXT NOT NULL, payload TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(scope,key));
      CREATE TABLE IF NOT EXISTS outbox (sequence INTEGER PRIMARY KEY AUTOINCREMENT, scope TEXT NOT NULL, id TEXT NOT NULL, payload TEXT NOT NULL, state TEXT NOT NULL DEFAULT 'pending', error TEXT, created_at INTEGER NOT NULL, UNIQUE(scope,id));
      CREATE TABLE IF NOT EXISTS local_meta (key TEXT PRIMARY KEY, payload TEXT NOT NULL);
      PRAGMA user_version=1;
    `);
  }
  put(scope, key, value, ttl = 7 * 86400000) {
    const now = Date.now(), payload = JSON.stringify(value);
    if (Buffer.byteLength(payload) > 5 * 1024 * 1024) return;
    this.db.prepare('INSERT INTO cache_entries VALUES(?,?,?,?,?,?) ON CONFLICT(scope,key) DO UPDATE SET payload=excluded.payload,updated_at=excluded.updated_at,expires_at=excluded.expires_at,accessed_at=excluded.accessed_at').run(scope, key, payload, now, now + ttl, now);
    this.db.prepare('DELETE FROM cache_entries WHERE expires_at < ?').run(now);
    let size = this.db.prepare('SELECT COALESCE(SUM(length(CAST(payload AS BLOB))),0) AS size FROM cache_entries').get().size;
    if (size > 100 * 1024 * 1024) {
      for (const row of this.db.prepare('SELECT scope,key,length(CAST(payload AS BLOB)) AS size FROM cache_entries ORDER BY accessed_at').all()) {
        this.db.prepare('DELETE FROM cache_entries WHERE scope=? AND key=?').run(row.scope, row.key); size -= row.size;
        if (size <= 80 * 1024 * 1024) break;
      }
    }
  }
  get(scope, key) {
    const row = this.db.prepare('SELECT payload,updated_at,expires_at FROM cache_entries WHERE scope=? AND key=?').get(scope, key);
    if (!row || row.expires_at < Date.now()) return null;
    this.db.prepare('UPDATE cache_entries SET accessed_at=? WHERE scope=? AND key=?').run(Date.now(), scope, key);
    return { value: JSON.parse(row.payload), updatedAt: row.updated_at };
  }
  invalidate(scope, prefix) {
    this.db.prepare('DELETE FROM cache_entries WHERE scope=? AND substr(key,1,?)=?').run(scope, prefix.length, prefix);
  }
  clearCache(scope) { this.db.prepare('DELETE FROM cache_entries WHERE scope=?').run(scope); }
  draft(scope, key, value) {
    if (value == null) this.db.prepare('DELETE FROM drafts WHERE scope=? AND key=?').run(scope, key);
    else this.db.prepare('INSERT INTO drafts VALUES(?,?,?,?) ON CONFLICT(scope,key) DO UPDATE SET payload=excluded.payload,updated_at=excluded.updated_at').run(scope, key, JSON.stringify(value), Date.now());
  }
  readDraft(scope, key) {
    const row = this.db.prepare('SELECT payload FROM drafts WHERE scope=? AND key=?').get(scope, key);
    return row ? JSON.parse(row.payload) : null;
  }
  enqueue(scope, operation) {
    this.db.prepare('INSERT INTO outbox(scope,id,payload,created_at) VALUES(?,?,?,?) ON CONFLICT(scope,id) DO UPDATE SET payload=excluded.payload').run(scope, operation.id, JSON.stringify(operation), Date.now());
  }
  queue(scope) { return this.db.prepare('SELECT id,payload,state,error FROM outbox WHERE scope=? ORDER BY sequence').all(scope).map((row) => ({ ...JSON.parse(row.payload), state: row.state, error: row.error })); }
  queueState(scope, id, state, error = null) {
    if (state === 'done') this.db.prepare('DELETE FROM outbox WHERE scope=? AND id=?').run(scope, id);
    else this.db.prepare('UPDATE outbox SET state=?,error=? WHERE scope=? AND id=?').run(state, error, scope, id);
  }
  meta(key, value) {
    if (arguments.length === 2) {
      if (value == null) this.db.prepare('DELETE FROM local_meta WHERE key=?').run(key);
      else this.db.prepare('INSERT INTO local_meta VALUES(?,?) ON CONFLICT(key) DO UPDATE SET payload=excluded.payload').run(key, JSON.stringify(value));
    }
    const row = this.db.prepare('SELECT payload FROM local_meta WHERE key=?').get(key);
    return row ? JSON.parse(row.payload) : null;
  }
  stats(scope) {
    const cache = this.db.prepare('SELECT count(*) AS entries, COALESCE(SUM(length(CAST(payload AS BLOB))),0) AS bytes FROM cache_entries WHERE scope=?').get(scope);
    return { ...cache, drafts: this.db.prepare('SELECT count(*) AS n FROM drafts WHERE scope=?').get(scope).n, pending: this.db.prepare('SELECT count(*) AS n FROM outbox WHERE scope=?').get(scope).n };
  }
  close() { this.db.exec('PRAGMA wal_checkpoint(TRUNCATE)'); this.db.close(); }
}
module.exports = { LocalStore };
