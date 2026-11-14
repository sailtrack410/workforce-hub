const { contextBridge, ipcRenderer } = require('electron');

// 渲染进程只能访问消息缓存能力，不暴露 Node、文件系统或任意 IPC。
contextBridge.exposeInMainWorld('workforceDesktop', {
  platform: process.platform,
  listMessages: (userId, conversationId) => ipcRenderer.invoke('messages:list', userId, conversationId),
  saveMessages: (userId, messages) => ipcRenderer.invoke('messages:save', userId, messages),
  capture: process.env.HUB_DESKTOP_CAPTURE
    ? () => ipcRenderer.sendSync('desktop:capture') : undefined,
});
