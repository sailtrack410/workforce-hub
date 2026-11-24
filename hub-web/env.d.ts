/// <reference types="vite/client" />

interface WorkforceDesktopBridge {
  platform: string;
  getSettings(): Promise<import('./src/services/preferences').DesktopSettings>;
  updateSettings(patch: Partial<import('./src/services/preferences').LocalPreferences>): Promise<import('./src/services/preferences').DesktopSettings>;
  chooseDownloadDirectory(): Promise<import('./src/services/preferences').DesktopSettings>;
  notify(payload: { id: string; title: string; body: string; route: string }): Promise<boolean>;
  dismissNotifications(): Promise<void>;
  download(url: string): Promise<void>;
  onNavigate(callback: (route: string) => void): () => void;
  listMessages(userId: string, conversationId: string): Promise<import('./src/api/types').ImMessageResponse[]>;
  saveMessages(userId: string, messages: import('./src/api/types').ImMessageResponse[]): Promise<void>;
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
