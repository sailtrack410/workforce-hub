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
  listMessages(userId: string, conversationId: string): Promise<import('./src/api/types').ImMessageResponse[]>;
  saveMessages(userId: string, messages: import('./src/api/types').ImMessageResponse[]): Promise<void>;
  replaceMessages(context: string, conversationId: string, messages: import('./src/api/types').ImMessageResponse[]): Promise<void>;
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
