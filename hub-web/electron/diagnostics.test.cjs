const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { Diagnostics, sanitize } = require('./diagnostics.cjs');
test('诊断日志脱敏、轮转限额，并仅导出日志文件', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'workforce-logs-'));
  try {
    assert.equal(sanitize('token=fixture-secret https://example.test/path?ticket=fixture'), 'token=[redacted] https://example.test/path?[redacted]');
    const logs = new Diagnostics(dir, 100);
    for (let i = 0; i < 12; i++) logs.write('error', 'network', 'Authorization: fixture-secret');
    assert.ok(fs.readdirSync(dir).length <= 4);
    const exported = path.join(dir, 'export.txt'); logs.export(exported);
    assert.ok(!fs.readFileSync(exported, 'utf8').includes('fixture-secret'));
    assert.ok(logs.bytes() > 0);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
