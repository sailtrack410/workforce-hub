import { computed, reactive, ref } from 'vue';

export interface LocalPreferences {
  theme: 'light' | 'dark' | 'system';
  desktopNotifications: boolean;
  notificationSound: boolean;
  messagePreview: boolean;
  dndEnabled: boolean;
  dndStart: string;
  dndEnd: string;
  downloadDirectory: string;
  closeBehavior: 'quit' | 'background';
  launchAtLogin: boolean;
}
export interface DesktopSettings extends LocalPreferences {
  themeConfigured?: boolean;
  canLaunchAtLogin: boolean;
  notificationsSupported: boolean;
}

export const preferences = reactive<DesktopSettings>({
  theme: 'system', desktopNotifications: true, notificationSound: false, messagePreview: true,
  dndEnabled: false, dndStart: '22:00', dndEnd: '08:00', downloadDirectory: '',
  closeBehavior: 'quit', launchAtLogin: false, canLaunchAtLogin: false, notificationsSupported: false,
});
const media = typeof window !== 'undefined' ? window.matchMedia?.('(prefers-color-scheme: dark)') : undefined;
const systemDark = ref(media?.matches ?? false);
export const resolvedTheme = computed(() => preferences.theme === 'system' ? (systemDark.value ? 'dark' : 'light') : preferences.theme);

function applyTheme() {
  document.body.dataset.theme = resolvedTheme.value;
}
media?.addEventListener('change', () => { systemDark.value = media.matches; if (preferences.theme === 'system') applyTheme(); });

export async function loadPreferences() {
  if (window.workforceDesktop) {
    let saved = await window.workforceDesktop.getSettings();
    let legacy: string | null = null;
    try { legacy = localStorage.getItem('hw-theme'); } catch { /* 隐私模式不阻断 */ }
    if (saved.themeConfigured === false && ['light', 'dark', 'system'].includes(legacy || '')) {
      saved = await window.workforceDesktop.updateSettings({ theme: legacy as LocalPreferences['theme'] });
    }
    Object.assign(preferences, saved);
  }
  else {
    try {
      const saved = JSON.parse(localStorage.getItem('hw-preferences') || '{}');
      for (const key of Object.keys(preferences) as Array<keyof DesktopSettings>) {
        if (typeof saved[key] === typeof preferences[key]) Object.assign(preferences, { [key]: saved[key] });
      }
      const legacy = localStorage.getItem('hw-theme');
      if (!saved.theme && ['light', 'dark', 'system'].includes(legacy || '')) preferences.theme = legacy as LocalPreferences['theme'];
    } catch { /* 隐私模式不阻断 */ }
  }
  applyTheme();
}

export async function savePreferences(patch: Partial<LocalPreferences>) {
  const next = window.workforceDesktop ? await window.workforceDesktop.updateSettings(patch) : { ...preferences, ...patch };
  if (!window.workforceDesktop) localStorage.setItem('hw-preferences', JSON.stringify(next));
  Object.assign(preferences, next);
  applyTheme();
}

export async function chooseDownloadDirectory() {
  if (window.workforceDesktop) Object.assign(preferences, await window.workforceDesktop.chooseDownloadDirectory());
}
