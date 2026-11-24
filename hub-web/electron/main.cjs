const { app, BrowserWindow, ipcMain, shell, dialog, Notification, screen, Tray, Menu, nativeImage } = require('electron');
const path = require('node:path');
const fs = require('node:fs');
const { MessageStore } = require('./message-store.cjs');
const { writeLocalUpload } = require('./local-upload.cjs');
const { Preferences, isQuietTime, windowBounds, downloadPath } = require('./preferences.cjs');
const { startAppServer } = require('./app-server.cjs');
const { LocalStore } = require('./local-store.cjs');
const { createHash, randomUUID } = require('node:crypto');

// 更名后复用已有数据目录，保留消息缓存和登录会话。
const legacyUserData = path.join(app.getPath('appData'), 'hub-web');
if (fs.existsSync(legacyUserData)) app.setPath('userData', legacyUserData);
app.setName('workforce');
app.setAppUserModelId('com.workforce.desktop');
const iconPath = path.join(__dirname, 'icons', 'workforce.png');

let store;
let mainWindow;
let preferences;
let tray;
let quitting = false;
let saveWindowTimer;
let appServer;
let localStore;
let activeAccount;
let cacheContext;
let backendOrigin;
let binding;
let accountGeneration = 0;
const notifications = new Map();
const notified = new Set();
const downloadPaths = new Set();
const SESSION_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

async function bindCacheAccount() {
  if (binding) return binding;
  binding = (async () => {
    const generation = accountGeneration;
    try {
      const response = await require('electron').session.defaultSession.fetch(backendOrigin + '/api/employee/auth/me', { credentials: 'include', headers: { 'X-Workforce-Client': 'desktop' } });
      if (response.status === 401 || response.status === 403) {
        activeAccount = undefined; cacheContext = undefined; localStore.meta('account:' + backendOrigin, null); return null;
      }
      if (!response.ok) throw new Error('服务不可用');
      const envelope = JSON.parse((await response.text()).replace(/("userId"\s*:\s*)(\d{16,})/g, '$1"$2"'));
      const user = envelope.data;
      if (envelope.code !== 0 || user?.userId == null) throw new Error('会话无效');
      if (generation !== accountGeneration) return null;
      const scope = createHash('sha256').update(`${backendOrigin}\n${user.tenantId ?? 'default'}\n${user.userId}`).digest('hex');
      if (activeAccount?.scope !== scope) cacheContext = randomUUID();
      activeAccount = { scope, user, verifiedAt: Date.now() };
      localStore.meta('account:' + backendOrigin, activeAccount);
      return { user, offline: false };
    } catch {
      if (generation !== accountGeneration) return null;
      const saved = localStore.meta('account:' + backendOrigin);
      if (!saved || Date.now() - saved.verifiedAt > SESSION_MAX_AGE_SECONDS * 1000) return null;
      if (activeAccount?.scope !== saved.scope) cacheContext = randomUUID();
      activeAccount = saved;
      return { user: saved.user, offline: true };
    }
  })();
  try { return await binding; } finally { binding = undefined; }
}

function requireCache(context) {
  if (!context || cacheContext !== context || !activeAccount) throw new Error('本地账号已切换，请重新加载');
  return activeAccount.scope;
}

function showWindow() {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show(); mainWindow.focus();
}

function syncTray() {
  if (preferences.get().closeBehavior === 'background' && process.platform !== 'darwin') {
    if (tray) return;
    tray = new Tray(nativeImage.createFromPath(iconPath).resize({ width: 16, height: 16 }));
    tray.setToolTip('workforce');
    tray.setContextMenu(Menu.buildFromTemplate([{ label: '打开 workforce', click: showWindow }, { label: '退出', click: () => app.quit() }]));
    tray.on('double-click', showWindow);
  } else if (tray) { tray.destroy(); tray = undefined; }
}

function saveWindowState() {
  clearTimeout(saveWindowTimer);
  if (!mainWindow || mainWindow.isDestroyed()) return;
  try { preferences.update({ windowState: { ...mainWindow.getNormalBounds(), maximized: mainWindow.isMaximized() } }); }
  catch { console.error('窗口状态保存失败'); }
}

function settingsResponse() {
  return { ...preferences.get(), themeConfigured: preferences.themeConfigured, canLaunchAtLogin: app.isPackaged && ['darwin', 'win32'].includes(process.platform), notificationsSupported: Notification.isSupported() };
}

async function persistSessionCookies(webContents) {
  // 服务端 SESSION 默认仅存活到浏览器退出；桌面端按服务端 7 天滑动会话持久化。
  // 不在 SQLite 或业务存储中保存密码、Session ID 等认证信息。
  const cookies = await webContents.session.cookies.get({ name: 'SESSION' });
  const now = Date.now() / 1000;
  await Promise.all(cookies
    .map((cookie) => webContents.session.cookies.set({
      url: `${cookie.secure ? 'https' : 'http'}://${cookie.domain.replace(/^\./, '')}${cookie.path}`,
      name: cookie.name,
      value: cookie.value,
      domain: cookie.domain,
      path: cookie.path,
      secure: cookie.secure,
      httpOnly: cookie.httpOnly,
      sameSite: cookie.sameSite,
      expirationDate: now + SESSION_MAX_AGE_SECONDS,
    })));
}

async function createWindow() {
  const saved = preferences.get().windowState;
  mainWindow = new BrowserWindow({
    ...windowBounds(saved, screen.getAllDisplays().map((display) => display.workArea)),
    minWidth: 960,
    minHeight: 640,
    title: 'workforce',
    icon: iconPath,
    backgroundColor: '#edf1f7',
    show: false,
    // macOS 使用系统绘制的内嵌红绿灯，业务页面不提供仿造控件。
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'default',
    trafficLightPosition: { x: 16, y: 18 },
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  mainWindow.once('ready-to-show', () => {
    if (saved?.maximized) mainWindow.maximize();
    mainWindow.show();
  });
  for (const event of ['move', 'resize', 'maximize', 'unmaximize']) {
    mainWindow.on(event, () => { clearTimeout(saveWindowTimer); saveWindowTimer = setTimeout(saveWindowState, 250); });
  }
  mainWindow.on('close', (event) => {
    saveWindowState();
    if (quitting) return;
    event.preventDefault();
    if (preferences.get().closeBehavior === 'background') mainWindow.hide();
    else app.quit();
  });
  const renewSession = () => {
    void persistSessionCookies(mainWindow.webContents).catch(() => undefined);
  };
  mainWindow.webContents.on('did-finish-load', renewSession);
  mainWindow.webContents.session.webRequest.onCompleted(
    { urls: ['http://127.0.0.1/*', 'http://localhost/*'] }, renewSession);
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (new URL(url).origin === new URL(mainWindow.webContents.getURL()).origin && /\/api\/employee\/(files|im\/attachments|workflow\/attachments)\/download\//.test(new URL(url).pathname)) {
      mainWindow.webContents.downloadURL(url, { headers: { 'X-Workforce-Client': 'desktop' } });
      return { action: 'deny' };
    }
    void shell.openExternal(url);
    return { action: 'deny' };
  });
  // 默认通过同一 HTTP 源加载，沿用服务端 Cookie、CSRF 与 WebSocket 鉴权。
  await mainWindow.loadURL(appServer?.origin || process.env.HUB_WEB_URL || 'http://127.0.0.1:5174');
}

app.whenReady().then(async () => {
  if (process.platform === 'darwin') app.dock.setIcon(iconPath);
  preferences = new Preferences(path.join(app.getPath('userData'), 'preferences.json'), app.getPath('downloads'));
  backendOrigin = new URL(preferences.get().serverUrl).origin;
  fs.mkdirSync(app.getPath('userData'), { recursive: true });
  localStore = new LocalStore(path.join(app.getPath('userData'), 'local.sqlite'));
  ipcMain.handle('cache:bind-account', () => bindCacheAccount());
  ipcMain.handle('cache:context', async () => { if (!activeAccount) await bindCacheAccount(); return cacheContext ?? null; });
  ipcMain.handle('cache:logout', async () => {
    accountGeneration++;
    activeAccount = undefined; cacheContext = undefined; localStore.meta('account:' + backendOrigin, null);
    for (const n of notifications.values()) n.close(); notifications.clear(); notified.clear();
    const cookies = require('electron').session.defaultSession.cookies;
    for (const cookie of await cookies.get({ name: 'SESSION' })) {
      await cookies.remove(`${cookie.secure ? 'https' : 'http'}://${cookie.domain.replace(/^\./, '')}${cookie.path}`, 'SESSION');
    }
  });
  ipcMain.handle('cache:get', (_event, context, key) => localStore.get(requireCache(context), String(key)));
  ipcMain.handle('cache:put', (_event, context, key, value) => {
    if (typeof key !== 'string' || !key.startsWith('/api/employee/') || /auth|token|ticket|download/i.test(key)) throw new Error('不允许缓存此接口');
    localStore.put(requireCache(context), key, value);
  });
  ipcMain.handle('cache:invalidate', (_event, context, prefix) => localStore.invalidate(requireCache(context), String(prefix)));
  ipcMain.handle('cache:draft', (_event, context, key, value) => localStore.draft(requireCache(context), String(key), value));
  ipcMain.handle('cache:read-draft', (_event, context, key) => localStore.readDraft(requireCache(context), String(key)));
  ipcMain.handle('cache:stats', (_event, context) => localStore.stats(requireCache(context)));
  if (app.isPackaged) appServer = await startAppServer({ root: path.join(__dirname, '..', 'dist'), backend: preferences.get().serverUrl });
  syncTray();
  const session = require('electron').session.defaultSession;
  session.on('will-download', (_event, item) => {
    try {
      const target = downloadPath(preferences.get().downloadDirectory, item.getFilename(), downloadPaths);
      downloadPaths.add(target);
      item.once('done', () => downloadPaths.delete(target));
      item.setSavePath(target);
    }
    catch { item.cancel(); dialog.showErrorBox('下载失败', '下载目录不可写，请在设置中更改目录'); }
  });
  ipcMain.handle('settings:get', () => settingsResponse());
  ipcMain.handle('settings:update', (_event, patch) => {
    if (!patch || Object.keys(patch).some((key) => ['windowState', 'downloadDirectory', 'canLaunchAtLogin', 'notificationsSupported', 'themeConfigured'].includes(key))) throw new Error('配置字段不可修改');
    if ('launchAtLogin' in patch && !settingsResponse().canLaunchAtLogin) throw new Error('开机启动仅支持安装版');
    const previous = preferences.get();
    preferences.update(patch);
    try {
      if ('launchAtLogin' in patch) app.setLoginItemSettings({ openAtLogin: patch.launchAtLogin });
    } catch (error) { preferences.update(previous); throw error; }
    syncTray();
    if (!preferences.get().desktopNotifications || isQuietTime(preferences.get()) || !preferences.get().messagePreview) for (const n of notifications.values()) n.close();
    return settingsResponse();
  });
  ipcMain.handle('settings:choose-download-directory', async () => {
    const result = await dialog.showOpenDialog(mainWindow, { title: '下载文件保存到', defaultPath: preferences.get().downloadDirectory, properties: ['openDirectory', 'createDirectory'] });
    if (!result.canceled && result.filePaths[0]) {
      fs.accessSync(result.filePaths[0], fs.constants.W_OK);
      preferences.update({ downloadDirectory: result.filePaths[0] });
    }
    return settingsResponse();
  });
  ipcMain.handle('desktop:dismiss-notifications', () => {
    for (const n of notifications.values()) n.close();
    notifications.clear(); notified.clear();
  });
  ipcMain.handle('desktop:notify', (_event, payload) => {
    const settings = preferences.get();
    if (!settings.desktopNotifications || isQuietTime(settings) || !Notification.isSupported() || mainWindow.isFocused()) return false;
    if (!payload || typeof payload.id !== 'string' || typeof payload.title !== 'string' || typeof payload.body !== 'string' || typeof payload.route !== 'string') throw new Error('通知格式无效');
    if (!/^\/(messages\/\d+|notifications|todos(?:\?todoId=\d+)?)$/.test(payload.route)) throw new Error('通知跳转无效');
    if (notified.has(payload.id)) return false;
    const notification = new Notification({ title: settings.messagePreview ? payload.title.slice(0, 100) : 'workforce', body: settings.messagePreview ? payload.body.slice(0, 300) : '你收到一条新通知', silent: !settings.notificationSound, icon: iconPath });
    notification.on('click', () => { showWindow(); mainWindow.webContents.send('desktop:navigate', payload.route); });
    notification.on('close', () => notifications.delete(payload.id));
    notification.on('failed', () => notifications.delete(payload.id));
    notified.add(payload.id);
    if (notified.size > 500) notified.delete(notified.values().next().value);
    notifications.set(payload.id, notification);
    notification.show();
    return true;
  });
  ipcMain.handle('desktop:download', (event, rawUrl) => {
    const url = new URL(rawUrl, mainWindow.webContents.getURL());
    const origin = new URL(mainWindow.webContents.getURL()).origin;
    if (!['http:', 'https:'].includes(url.protocol) || url.origin !== origin) throw new Error('下载地址无效');
    event.sender.downloadURL(url.href, { headers: { 'X-Workforce-Client': 'desktop' } });
  });
  store = new MessageStore(path.join(app.getPath('userData'), 'messages.sqlite'));
  ipcMain.handle('messages:list', (_event, userId, conversationId) =>
    String(userId) === String(activeAccount?.user.userId) ? store.list(activeAccount.scope, String(conversationId)) : []);
  ipcMain.handle('messages:save', (_event, userId, messages) =>
    String(userId) === String(activeAccount?.user.userId) ? store.save(activeAccount.scope, messages) : undefined);
  ipcMain.handle('files:write-local-upload', (_event, uploadPath, content) =>
    writeLocalUpload(uploadPath, content));
  // 仅本机显式指定输出路径时生成验收截图，不对渲染进程暴露此能力。
  if (process.env.HUB_DESKTOP_CAPTURE) {
    ipcMain.on('desktop:capture', async (event) => {
      const image = await mainWindow.webContents.capturePage();
      event.returnValue = image.toPNG().toString('base64');
    });
  }
  await createWindow();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) void createWindow();
    else showWindow();
  });
}).catch((error) => {
  console.error('桌面应用启动失败', error);
  app.quit();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
app.on('before-quit', () => {
  quitting = true;
  saveWindowState();
  for (const n of notifications.values()) n.close();
});
app.on('will-quit', () => { appServer?.close(); localStore?.close(); store?.close(); });
