const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('node:path');
const { MessageStore } = require('./message-store.cjs');

let store;
let mainWindow;
const SESSION_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

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
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 900,
    minWidth: 960,
    minHeight: 640,
    title: 'Workforce Hub',
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
  mainWindow.once('ready-to-show', () => mainWindow.show());
  const renewSession = () => {
    void persistSessionCookies(mainWindow.webContents).catch(() => undefined);
  };
  mainWindow.webContents.on('did-finish-load', renewSession);
  mainWindow.webContents.session.webRequest.onCompleted(
    { urls: ['http://127.0.0.1/*', 'http://localhost/*'] }, renewSession);
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url);
    return { action: 'deny' };
  });
  // 默认通过同一 HTTP 源加载，沿用服务端 Cookie、CSRF 与 WebSocket 鉴权。
  await mainWindow.loadURL(process.env.HUB_WEB_URL || 'http://127.0.0.1:5174');
}

app.whenReady().then(async () => {
  store = new MessageStore(path.join(app.getPath('userData'), 'messages.sqlite'));
  ipcMain.handle('messages:list', (_event, userId, conversationId) =>
    store.list(String(userId), String(conversationId)));
  ipcMain.handle('messages:save', (_event, userId, messages) =>
    store.save(String(userId), messages));
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
  });
}).catch((error) => {
  console.error('桌面应用启动失败', error);
  app.quit();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
app.on('before-quit', () => store?.close());
