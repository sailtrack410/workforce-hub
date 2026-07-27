/// <reference types="vite/client" />

import type { WorkforceHostSdk } from './core/host-sdk';

declare global {
  interface Window {
    __WORKFORCE_HOST__?: WorkforceHostSdk;
  }
}
