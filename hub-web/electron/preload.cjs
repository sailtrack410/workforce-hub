const { contextBridge, ipcRenderer } = require('electron');

// 渲染进程只能访问消息缓存能力，不暴露 Node、文件系统或任意 IPC。
contextBridge.exposeInMainWorld('workforceDesktop', {
  platform: process.platform,
  getSettings: () => ipcRenderer.invoke('settings:get'),
  updateSettings: (patch) => ipcRenderer.invoke('settings:update', patch),
  chooseDownloadDirectory: () => ipcRenderer.invoke('settings:choose-download-directory'),
  notify: (payload) => ipcRenderer.invoke('desktop:notify', payload),
  dismissNotifications: () => ipcRenderer.invoke('desktop:dismiss-notifications'),
  download: (url) => ipcRenderer.invoke('desktop:download', url),
  onNavigate: (callback) => {
    const listener = (_event, route) => callback(route);
    ipcRenderer.on('desktop:navigate', listener);
    return () => ipcRenderer.removeListener('desktop:navigate', listener);
  },
  listMessages: (userId, conversationId) => ipcRenderer.invoke('messages:list', userId, conversationId),
  saveMessages: (userId, messages) => ipcRenderer.invoke('messages:save', userId, messages),
  writeLocalUpload: (uploadPath, content) => ipcRenderer.invoke('files:write-local-upload', uploadPath, content),
  capture: process.env.HUB_DESKTOP_CAPTURE
    ? () => ipcRenderer.sendSync('desktop:capture') : undefined,
});
