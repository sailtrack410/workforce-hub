const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { Preferences, isQuietTime, windowBounds, downloadPath } = require('./preferences.cjs');

test('重启后恢复配置，非法修改不覆盖文件或当前值', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'workforce-preferences-'));
  try {
    const file = path.join(dir, 'preferences.json');
    const store = new Preferences(file, dir);
    store.update({ theme: 'dark', desktopNotifications: false, notificationSound: true, messagePreview: false, dndEnabled: true, dndStart: '21:30', dndEnd: '07:15', closeBehavior: 'background', downloadDirectory: path.join(dir, '下载'), windowState: { x: -1100, y: 20, width: 1100, height: 760, maximized: true } });
    assert.deepEqual(new Preferences(file, dir).get(), store.get());
    const saved = fs.readFileSync(file, 'utf8');
    for (const patch of [{ theme: 'unknown' }, { dndStart: '25:00' }, { dndEnd: '' }, { desktopNotifications: 'false' }, { downloadDirectory: '../escape' }, { arbitrary: true }, { windowState: { width: 1 } }]) assert.throws(() => store.update(patch), /配置/);
    assert.equal(fs.readFileSync(file, 'utf8'), saved);
    const returned = store.get(); returned.windowState.width = 1;
    assert.equal(store.get().windowState.width, 1100);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('免打扰支持跨午夜、同日、起止边界和全天，关闭后立即放行', () => {
  const settings = { dndEnabled: true, dndStart: '22:00', dndEnd: '08:00' };
  const at = (h, m = 0) => new Date(2026, 10, 25, h, m);
  assert.equal(isQuietTime(settings, at(21, 59)), false);
  assert.equal(isQuietTime(settings, at(22)), true);
  assert.equal(isQuietTime(settings, at(0)), true);
  assert.equal(isQuietTime(settings, at(7, 59)), true);
  assert.equal(isQuietTime(settings, at(8)), false);
  assert.equal(isQuietTime({ ...settings, dndStart: '09:00', dndEnd: '18:00' }, at(12)), true);
  assert.equal(isQuietTime({ ...settings, dndStart: '09:00', dndEnd: '18:00' }, at(20)), false);
  assert.equal(isQuietTime({ ...settings, dndEnd: '22:00' }, at(12)), true);
  assert.equal(isQuietTime({ ...settings, dndEnabled: false }, at(0)), false);
});

test('窗口保留副屏位置，移除副屏后落到可见区域', () => {
  const saved = { x: -1200, y: 40, width: 1200, height: 800, maximized: false };
  const primary = { x: 0, y: 25, width: 1440, height: 875 };
  const secondary = { x: -1440, y: 0, width: 1440, height: 900 };
  assert.deepEqual(windowBounds(saved, [primary, secondary]), { x: -1200, y: 40, width: 1200, height: 800 });
  assert.deepEqual(windowBounds(saved, [primary]), { x: 0, y: 40, width: 1200, height: 800 });
  assert.deepEqual(windowBounds({ ...saved, x: 5000, width: 2000 }, [primary]), { x: 0, y: 40, width: 1440, height: 800 });
});

test('下载路径使用选定目录，过滤目录穿越，并避让已有及正在下载的同名文件', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'workforce-download-'));
  try {
    const target = downloadPath(dir, '../../report.txt');
    assert.equal(target, path.join(dir, 'report.txt'));
    fs.writeFileSync(target, 'old');
    const second = downloadPath(dir, 'report.txt');
    assert.equal(second, path.join(dir, 'report (1).txt'));
    assert.equal(downloadPath(dir, 'report.txt', new Set([second])), path.join(dir, 'report (2).txt'));
    assert.equal(downloadPath(dir, '..\\report.txt'), second);
    assert.equal(fs.readFileSync(target, 'utf8'), 'old');
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});
