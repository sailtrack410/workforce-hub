const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');

class AssetCache {
  constructor(directory, maxBytes = 200 * 1024 * 1024) { this.directory = directory; this.maxBytes = maxBytes; fs.mkdirSync(directory, { recursive: true }); }
  filename(scope, key) { return path.join(this.directory, createHash('sha256').update(scope + '\n' + key).digest('hex')); }
  get(scope, key) {
    const file = this.filename(scope, key);
    try {
      const stat = fs.statSync(file);
      if (Date.now() - stat.mtimeMs > 7 * 86400000) { fs.unlinkSync(file); return null; }
      const entry = JSON.parse(fs.readFileSync(file, 'utf8'));
      fs.utimesSync(file, new Date(), stat.mtime);
      return entry;
    } catch { return null; }
  }
  put(scope, key, content, mime) {
    if (content.length > 15 * 1024 * 1024 || !/^image\/(png|jpeg|webp|gif|avif)$/.test(mime)) throw new Error('仅支持缓存图片预览');
    const entry = { mime, base64: Buffer.from(content).toString('base64') };
    fs.writeFileSync(this.filename(scope, key), JSON.stringify(entry), { mode: 0o600 });
    const files = fs.readdirSync(this.directory).filter(name => /^[a-f0-9]{64}$/.test(name)).map(name => ({ name, ...fs.statSync(path.join(this.directory, name)) })).sort((a, b) => a.atimeMs - b.atimeMs);
    let bytes = files.reduce((sum, file) => sum + file.size, 0);
    for (const file of files) {
      if (bytes <= this.maxBytes && Date.now() - file.mtimeMs <= 7 * 86400000) continue;
      fs.unlinkSync(path.join(this.directory, file.name)); bytes -= file.size;
    }
    return entry;
  }
  remove(scope, key) { fs.rmSync(this.filename(scope, key), { force: true }); }
  bytes() { return fs.readdirSync(this.directory).filter(name => /^[a-f0-9]{64}$/.test(name)).reduce((sum, name) => sum + fs.statSync(path.join(this.directory, name)).size, 0); }
  clear() { for (const name of fs.readdirSync(this.directory)) if (/^[a-f0-9]{64}$/.test(name)) fs.rmSync(path.join(this.directory, name)); }
}
module.exports = { AssetCache };
