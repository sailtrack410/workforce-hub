const fs = require('node:fs');
const path = require('node:path');

function sanitize(value) {
  return String(value).replace(/(https?:\/\/[^\s?#]+)[?#][^\s]*/g, '$1?[redacted]')
    .replace(/((?:password|token|session|authorization|cookie|secret|bearer)\s*[:= ]\s*)[^\s,;]+/ig, '$1[redacted]')
    .replace(/[\r\n]+/g, ' ').slice(0, 2000);
}
class Diagnostics {
  constructor(directory, maxBytes = 2 * 1024 * 1024) { this.directory = directory; this.maxBytes = maxBytes; fs.mkdirSync(directory, { recursive: true }); }
  write(level, code, detail = '') {
    try {
      const file = path.join(this.directory, 'desktop.log');
      if (fs.existsSync(file) && fs.statSync(file).size >= this.maxBytes) {
        fs.rmSync(file + '.3', { force: true });
        for (let i = 2; i >= 1; i--) if (fs.existsSync(file + '.' + i)) fs.renameSync(file + '.' + i, file + '.' + (i + 1));
        fs.renameSync(file, file + '.1');
      }
      fs.appendFileSync(file, JSON.stringify({ time: new Date().toISOString(), level, code: sanitize(code), detail: sanitize(detail) }) + '\n', { mode: 0o600 });
    } catch { /* 日志失败不阻断业务 */ }
  }
  bytes() { return fs.readdirSync(this.directory).reduce((sum, file) => sum + fs.statSync(path.join(this.directory, file)).size, 0); }
  export(target) {
    const text = fs.readdirSync(this.directory).filter(file => /^desktop\.log(?:\.[1-3])?$/.test(file)).sort().map(file => fs.readFileSync(path.join(this.directory, file), 'utf8')).join('\n');
    fs.writeFileSync(target, text, { mode: 0o600 });
  }
}
module.exports = { Diagnostics, sanitize };
