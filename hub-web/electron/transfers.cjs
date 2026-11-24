const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');
class Transfers {
  constructor(directory) { this.directory = directory; fs.mkdirSync(directory, { recursive: true }); }
  file(scope, id) { return path.join(this.directory, createHash('sha256').update(scope + '\n' + id).digest('hex')); }
  stage(scope, id, bytes, metadata) {
    if (!Array.isArray(bytes) || bytes.length > 128 * 1024 * 1024 || bytes.some(n => !Number.isInteger(n) || n < 0 || n > 255)) throw new Error('本机暂存文件最大 128 MB');
    if (!metadata || typeof metadata.fileName !== 'string' || !['PERSONAL_FILE', 'IM_MESSAGE_PENDING'].includes(metadata.ownerType)) throw new Error('上传信息无效');
    const file = this.file(scope, id);
    fs.writeFileSync(file + '.bin', Buffer.from(bytes), { mode: 0o600 });
    this.save(scope, id, { id, ...metadata, fileSize: bytes.length, status: 'pending', createdAt: Date.now() });
  }
  save(scope, id, metadata) { const file = this.file(scope, id); fs.writeFileSync(file + '.json.tmp', JSON.stringify({ scope, metadata }), { mode: 0o600 }); fs.renameSync(file + '.json.tmp', file + '.json'); }
  list(scope) {
    return fs.readdirSync(this.directory).filter(file => /^[a-f0-9]{64}\.json$/.test(file)).flatMap(file => {
      try { const row = JSON.parse(fs.readFileSync(path.join(this.directory, file), 'utf8')); return row.scope === scope ? [row.metadata] : []; } catch { return []; }
    }).sort((a, b) => b.createdAt - a.createdAt);
  }
  read(scope, id) {
    const file = this.file(scope, id), entry = JSON.parse(fs.readFileSync(file + '.json', 'utf8'));
    if (entry.scope !== scope) throw new Error('上传账号不匹配');
    return { ...entry.metadata, bytes: [...fs.readFileSync(file + '.bin')] };
  }
  status(scope, id, status) {
    const file = this.file(scope, id), entry = JSON.parse(fs.readFileSync(file + '.json', 'utf8'));
    if (entry.scope !== scope || !['failed', 'done', 'pending'].includes(status)) throw new Error('上传状态无效');
    this.save(scope, id, { ...entry.metadata, status });
    if (status === 'done') {
      fs.rmSync(file + '.bin', { force: true });
      for (const old of this.list(scope).filter(item => item.status === 'done').slice(100)) fs.rmSync(this.file(scope, old.id) + '.json', { force: true });
    }
  }
  discard(scope, id) { const file = this.file(scope, id); for (const suffix of ['.bin', '.json']) fs.rmSync(file + suffix, { force: true }); }
  bytes(scope) { return this.list(scope).filter(row => row.status !== 'done').reduce((sum, row) => sum + row.fileSize, 0); }
}
module.exports = { Transfers };
