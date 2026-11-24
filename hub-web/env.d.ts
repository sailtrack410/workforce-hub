/// <reference types="vite/client" />

interface WorkforceDesktopBridge {
  platform: string;
  bindCacheAccount(): Promise<{ user: import('./src/api/types').CurrentUserResponse; offline: boolean } | null>;
  cacheContext(): Promise<string | null>;
  logoutCache(): Promise<void>;
  cacheGet(context: string, key: string): Promise<{ value: unknown; updatedAt: number } | null>;
  cachePut(context: string, key: string, value: unknown): Promise<void>;
  cacheInvalidate(context: string, prefix: string): Promise<void>;
  saveDraft(context: string, key: string, value: unknown): Promise<void>;
  readDraft(context: string, key: string): Promise<unknown>;
  cacheStats(context: string): Promise<{ entries: number; bytes: number; drafts: number; pending: number }>;
  storageStats(context: string | null): Promise<{ entries: number; bytes: number; drafts: number; pending: number; databaseBytes: number; mediaBytes: number; logBytes: number; transferBytes: number; recovered: boolean }>;
  clearCache(context: string): Promise<void>;
  openDownloads(): Promise<string>;
  exportDiagnostics(): Promise<boolean>;
  reportError(kind: string): Promise<void>;
  checkReminders(context: string, offline: boolean): Promise<void>;
  media(context: string, key: string, url?: string): Promise<string | null>;
  stageUpload(context: string, id: string, bytes: number[], metadata: import('./src/services/transfers').UploadRecord): Promise<void>;
  uploads(context: string): Promise<import('./src/services/transfers').UploadRecord[]>;
  readUpload(context: string, id: string): Promise<import('./src/services/transfers').UploadRecord & { bytes: number[] }>;
  uploadStatus(context: string, id: string, status: string): Promise<void>;
  discardUpload(context: string, id: string): Promise<void>;
  pendingOperations(context: string): Promise<import('./src/services/sync').SyncOperation[]>;
  enqueue(context: string, operation: import('./src/services/sync').SyncOperation): Promise<void>;
  setOperationState(context: string, id: string, state: string, error?: string): Promise<void>;
  getSettings(): Promise<import('./src/services/preferences').DesktopSettings>;
  updateSettings(patch: Partial<import('./src/services/preferences').LocalPreferences>): Promise<import('./src/services/preferences').DesktopSettings>;
  chooseDownloadDirectory(): Promise<import('./src/services/preferences').DesktopSettings>;
  notify(payload: { id: string; title: string; body: string; route: string }): Promise<boolean>;
  dismissNotifications(): Promise<void>;
  download(url: string): Promise<void>;
  onNavigate(callback: (route: string) => void): () => void;
  listMessages(userId: string, conversationId: string, options?: { beforeSeq?: number; size?: number }, context?: string): Promise<import('./src/api/types').ImMessageResponse[]>;
  saveMessages(userId: string, messages: import('./src/api/types').ImMessageResponse[]): Promise<void>;
  replaceMessages(context: string, conversationId: string, messages: import('./src/api/types').ImMessageResponse[], range?: { fromSeq: number; toSeq: number }): Promise<void>;
  removeMessage(context: string, messageId: string): Promise<void>;
  clearMessages(context: string, conversationId: string): Promise<void>;
  writeLocalUpload(uploadPath: string, content: number[]): Promise<void>;
}

interface Window {
  workforceDesktop?: WorkforceDesktopBridge;
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<Record<string, never>, Record<string, never>, unknown>;
  export default component;
}
