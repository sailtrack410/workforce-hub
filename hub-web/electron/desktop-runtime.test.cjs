const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');

async function runtime() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'workforce-runtime-'));
  const handlers = new Map(), shown = [], downloads = [];
  const app = new EventEmitter();
  Object.assign(app, {
    isPackaged: false,
    getPath: (name) => path.join(dir, name), setPath() {}, setName() {}, setAppUserModelId() {},
    dock: { setIcon() {} }, quit: () => { app.emit('before-quit'); },
    whenReady: () => ({ then: (callback) => { app.ready = callback(); return app.ready; } }),
  });
  class BrowserWindow extends EventEmitter {
    constructor(options) {
      super(); BrowserWindow.instance = this; this.options = options; this.focused = false;
      this.webContents = new EventEmitter();
      Object.assign(this.webContents, { session: { webRequest: { onCompleted() {} } }, getURL: () => 'http://127.0.0.1:5174', setWindowOpenHandler() {}, downloadURL: (url) => downloads.push(url), send: (_event, route) => { this.route = route; } });
    }
    loadURL() {} show() { this.hidden = false; } hide() { this.hidden = true; } focus() { this.focused = true; }
    isDestroyed() { return false; } isMinimized() { return false; } isFocused() { return this.focused; }
    getNormalBounds() { return { x: 45, y: 50, width: 1100, height: 760 }; } isMaximized() { return false; }
  }
  class Notification extends EventEmitter {
    static isSupported() { return true; }
    constructor(options) { super(); this.options = options; }
    show() { shown.push(this); } close() { this.emit('close'); }
  }
  const session = new EventEmitter();
  const electron = { app, BrowserWindow, Notification, session: { defaultSession: session }, ipcMain: { handle: (key, callback) => handlers.set(key, callback), on() {} },
    screen: { getAllDisplays: () => [{ workArea: { x: 0, y: 0, width: 1440, height: 900 } }] },
    dialog: { showOpenDialog: async () => ({ canceled: false, filePaths: [dir] }), showErrorBox: () => assert.fail('Unexpected dialog') }, shell: {},
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, 'main.cjs'), 'utf8'), {
    __dirname, process: { platform: 'darwin', env: {} }, console, setTimeout, clearTimeout, URL,
    require: (name) => name === 'electron' ? electron : name === './message-store.cjs' ? { MessageStore: class { close() {} } } : require(name),
  });
  await app.ready;
  const win = BrowserWindow.instance;
  return { dir, win, shown, downloads, session, invoke: (key, value) => handlers.get(key)({ sender: win.webContents }, value),
    cleanup: () => { app.emit('before-quit'); fs.rmSync(dir, { recursive: true, force: true }); } };
}

test('主进程通知执行预览隐私、声音、免打扰、关闭与去重策略，点击可导航', async () => {
  const r = await runtime();
  try {
    const n = { id: 'm:1', title: '敏感标题', body: '敏感正文', route: '/messages/123' };
    r.invoke('settings:update', { messagePreview: false, notificationSound: true });
    assert.equal(r.invoke('desktop:notify', n), true);
    assert.equal(r.shown[0].options.title, 'workforce');
    assert.equal(r.shown[0].options.body, '你收到一条新通知');
    assert.equal(r.shown[0].options.silent, false);
    assert.equal(r.invoke('desktop:notify', n), false);
    r.invoke('settings:update', { dndEnabled: true, dndStart: '00:00', dndEnd: '00:00' });
    assert.equal(r.invoke('desktop:notify', { ...n, id: 'm:2' }), false);
    r.invoke('settings:update', { dndEnabled: false, desktopNotifications: false });
    assert.equal(r.invoke('desktop:notify', { ...n, id: 'm:3' }), false);
    r.invoke('settings:update', { desktopNotifications: true, messagePreview: true, notificationSound: false });
    assert.equal(r.invoke('desktop:notify', { ...n, id: 'm:4' }), true);
    assert.equal(r.shown[1].options.body, n.body);
    assert.equal(r.shown[1].options.silent, true);
    r.shown[1].emit('click');
    assert.equal(r.win.route, '/messages/123');
    assert.equal(r.invoke('desktop:notify', { ...n, id: 'm:5' }), false);
    r.win.focused = false;
    assert.throws(() => r.invoke('desktop:notify', { ...n, route: 'https://external.test' }), /跳转/);
    r.invoke('desktop:dismiss-notifications');
    assert.equal(r.invoke('desktop:notify', n), true);
  } finally { r.cleanup(); }
});

test('原生目录选择写入配置，实际下载事件使用该目录，后台关闭保留窗口', async () => {
  const r = await runtime();
  try {
    assert.equal((await r.invoke('settings:choose-download-directory')).downloadDirectory, r.dir);
    r.invoke('desktop:download', '/api/employee/files/download/fixture');
    assert.equal(r.downloads[0], 'http://127.0.0.1:5174/api/employee/files/download/fixture');
    assert.throws(() => r.invoke('desktop:download', 'file:///etc/passwd'), /下载地址/);
    assert.throws(() => r.invoke('settings:update', { windowState: {} }), /不可修改/);
    assert.throws(() => r.invoke('settings:update', { launchAtLogin: true }), /安装版/);
    const first = new EventEmitter(); Object.assign(first, { getFilename: () => 'report.txt', setSavePath: (value) => { first.target = value; } });
    const second = new EventEmitter(); Object.assign(second, { getFilename: () => 'report.txt', setSavePath: (value) => { second.target = value; } });
    r.session.emit('will-download', {}, first); r.session.emit('will-download', {}, second);
    assert.equal(first.target, path.join(r.dir, 'report.txt'));
    assert.equal(second.target, path.join(r.dir, 'report (1).txt'));
    r.invoke('settings:update', { closeBehavior: 'background' });
    let prevented = false;
    r.win.emit('close', { preventDefault: () => { prevented = true; } });
    assert.equal(prevented, true); assert.equal(r.win.hidden, true);
    assert.equal(r.invoke('settings:get').windowState.width, 1100);
  } finally { r.cleanup(); }
});
