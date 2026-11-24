import { afterEach, describe, expect, it, vi } from 'vitest';

afterEach(() => { vi.unstubAllGlobals(); vi.resetModules(); });

describe('本地偏好', () => {
  it('首次桌面配置继承旧主题，已保存的桌面主题不被旧值覆盖', async () => {
    let saved = { theme: 'system', themeConfigured: false };
    const updateSettings = vi.fn(async () => { saved = { theme: 'dark', themeConfigured: true }; return saved; });
    vi.stubGlobal('window', { workforceDesktop: { getSettings: async () => saved, updateSettings }, matchMedia: () => ({ matches: false, addEventListener() {} }) });
    vi.stubGlobal('document', { body: { dataset: {} }, createElement: () => ({}) });
    vi.stubGlobal('localStorage', { getItem: () => 'dark' });
    const service = await import('./preferences');
    await service.loadPreferences();
    expect(service.preferences.theme).toBe('dark');
    await service.loadPreferences();
    expect(updateSettings).toHaveBeenCalledTimes(1);
  });
  it('桌面端等待磁盘确认，写入失败保留原设置', async () => {
    const updateSettings = vi.fn().mockRejectedValue(new Error('磁盘不可写'));
    vi.stubGlobal('window', { workforceDesktop: { getSettings: async () => ({ theme: 'dark', desktopNotifications: false }), updateSettings }, matchMedia: () => ({ matches: false, addEventListener() {} }) });
    vi.stubGlobal('document', { body: { dataset: {} }, createElement: () => ({}) });
    const service = await import('./preferences');
    await service.loadPreferences();
    expect(document.body.dataset.theme).toBe('dark');
    await expect(service.savePreferences({ desktopNotifications: true })).rejects.toThrow('磁盘不可写');
    expect(service.preferences.desktopNotifications).toBe(false);
    expect(updateSettings).toHaveBeenCalledWith({ desktopNotifications: true });
  });

  it('网页端保存并恢复外观，系统外观变化立即生效', async () => {
    const storage = new Map<string, string>();
    let changed: (() => void) | undefined;
    const media = { matches: false, addEventListener: (_event: string, callback: () => void) => { changed = callback; } };
    vi.stubGlobal('window', { matchMedia: () => media });
    vi.stubGlobal('document', { body: { dataset: {} }, createElement: () => ({}) });
    vi.stubGlobal('localStorage', { getItem: (key: string) => storage.get(key) ?? null, setItem: (key: string, value: string) => storage.set(key, value) });
    const service = await import('./preferences');
    await service.savePreferences({ theme: 'system', dndStart: '23:00' });
    media.matches = true; changed?.();
    expect(document.body.dataset.theme).toBe('dark');
    service.preferences.dndStart = '10:00';
    await service.loadPreferences();
    expect(service.preferences.dndStart).toBe('23:00');
    expect(service.resolvedTheme.value).toBe('dark');
  });
});
