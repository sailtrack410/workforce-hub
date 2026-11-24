const { contextBridge, ipcRenderer } = require('electron');

// 渲染进程只能访问消息缓存能力，不暴露 Node、文件系统或任意 IPC。
contextBridge.exposeInMainWorld('workforceDesktop', {
  platform: process.platform,
  bindCacheAccount: () => ipcRenderer.invoke('cache:bind-account'),
  cacheContext: () => ipcRenderer.invoke('cache:context'),
  logoutCache: () => ipcRenderer.invoke('cache:logout'),
  cacheGet: (context, key) => ipcRenderer.invoke('cache:get', context, key),
  cachePut: (context, key, value) => ipcRenderer.invoke('cache:put', context, key, value),
  cacheInvalidate: (context, prefix) => ipcRenderer.invoke('cache:invalidate', context, prefix),
  saveDraft: (context, key, value) => ipcRenderer.invoke('cache:draft', context, key, value),
  readDraft: (context, key) => ipcRenderer.invoke('cache:read-draft', context, key),
  cacheStats: (context) => ipcRenderer.invoke('cache:stats', context),
  storageStats: (context) => ipcRenderer.invoke('storage:stats', context),
  clearCache: (context) => ipcRenderer.invoke('storage:clear', context),
  openDownloads: () => ipcRenderer.invoke('storage:open-downloads'),
  exportDiagnostics: () => ipcRenderer.invoke('diagnostics:export'),
  reportError: (kind) => ipcRenderer.invoke('diagnostics:error', kind),
  checkReminders: (context, offline) => ipcRenderer.invoke('reminders:check', context, offline),
  media: (context, key, url) => ipcRenderer.invoke('media:get', context, key, url),
  stageUpload: (context, id, bytes, metadata) => ipcRenderer.invoke('transfers:stage', context, id, bytes, metadata),
  uploads: (context) => ipcRenderer.invoke('transfers:list', context),
  readUpload: (context, id) => ipcRenderer.invoke('transfers:read', context, id),
  uploadStatus: (context, id, status) => ipcRenderer.invoke('transfers:status', context, id, status),
  discardUpload: (context, id) => ipcRenderer.invoke('transfers:discard', context, id),
  pendingOperations: (context) => ipcRenderer.invoke('cache:queue', context),
  enqueue: (context, operation) => ipcRenderer.invoke('cache:enqueue', context, operation),
  setOperationState: (context, id, state, error) => ipcRenderer.invoke('cache:queue-state', context, id, state, error),
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
  listMessages: (userId, conversationId, options, context) => ipcRenderer.invoke('messages:list', userId, conversationId, options, context),
  saveMessages: (userId, messages) => ipcRenderer.invoke('messages:save', userId, messages),
  replaceMessages: (context, conversationId, messages, range) => ipcRenderer.invoke('messages:replace', context, conversationId, messages, range),
  removeMessage: (context, messageId) => ipcRenderer.invoke('messages:remove', context, messageId),
  clearMessages: (context, conversationId) => ipcRenderer.invoke('messages:clear', context, conversationId),
  writeLocalUpload: (uploadPath, content) => ipcRenderer.invoke('files:write-local-upload', uploadPath, content),
  capture: process.env.HUB_DESKTOP_CAPTURE
    ? () => ipcRenderer.sendSync('desktop:capture') : undefined,
});
