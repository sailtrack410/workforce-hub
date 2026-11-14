const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('node:path');
const { MessageStore } = require('./message-store.cjs');

let store;
let mainWindow;

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
