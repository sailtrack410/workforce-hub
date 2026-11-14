/// <reference types="vite/client" />

interface WorkforceDesktopBridge {
  platform: string;
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
