const fs = require('node:fs');
const path = require('node:path');

const defaults = {
  theme: 'system', desktopNotifications: true, notificationSound: false, messagePreview: true,
  dndEnabled: false, dndStart: '22:00', dndEnd: '08:00', closeBehavior: 'quit', launchAtLogin: false,
};
const timePattern = /^([01]\d|2[0-3]):[0-5]\d$/;

function validate(patch) {
  if (!patch || typeof patch !== 'object' || Array.isArray(patch)) throw new Error('配置格式无效');
  for (const [key, value] of Object.entries(patch)) {
    if (typeof defaults[key] === 'boolean' && typeof value === 'boolean') continue;
    if (key === 'theme' && ['light', 'dark', 'system'].includes(value)) continue;
    if (key === 'closeBehavior' && ['quit', 'background'].includes(value)) continue;
    if (['dndStart', 'dndEnd'].includes(key) && typeof value === 'string' && timePattern.test(value)) continue;
    if (key === 'downloadDirectory' && typeof value === 'string' && path.isAbsolute(value) && !value.includes('\0')) continue;
    if (key === 'windowState' && value && ['x', 'y', 'width', 'height'].every((field) => Number.isSafeInteger(value[field])) && value.width >= 960 && value.height >= 640 && typeof value.maximized === 'boolean') continue;
    throw new Error(`配置字段无效：${key}`);
  }
}

class Preferences {
  constructor(filename, downloads) {
    this.filename = filename;
    this.themeConfigured = false;
    this.value = { ...defaults, downloadDirectory: path.join(downloads, 'workforce') };
    try {
      const saved = JSON.parse(fs.readFileSync(filename, 'utf8'));
      if (saved.version !== 1) throw new Error('配置版本无效');
      validate(saved.preferences);
      Object.assign(this.value, saved.preferences);
      this.themeConfigured = typeof saved.preferences.theme === 'string';
    } catch (error) {
      if (error.code !== 'ENOENT') console.error('本机配置读取失败，使用默认设置');
    }
  }
  get() { return structuredClone(this.value); }
  update(patch) {
    validate(patch);
    const next = { ...this.value, ...patch };
    fs.mkdirSync(path.dirname(this.filename), { recursive: true });
    const temporary = `${this.filename}.tmp`;
    try {
      fs.writeFileSync(temporary, JSON.stringify({ version: 1, preferences: next }, null, 2), { mode: 0o600 });
      fs.renameSync(temporary, this.filename);
    } finally { fs.rmSync(temporary, { force: true }); }
    this.value = next;
    if ('theme' in patch) this.themeConfigured = true;
    return this.get();
  }
}

function isQuietTime(settings, now = new Date()) {
  if (!settings.dndEnabled) return false;
  const time = now.getHours() * 60 + now.getMinutes();
  const minutes = (value) => Number(value.slice(0, 2)) * 60 + Number(value.slice(3));
  const start = minutes(settings.dndStart), end = minutes(settings.dndEnd);
  return start === end || (start < end ? time >= start && time < end : time >= start || time < end);
}

function windowBounds(saved, workAreas) {
  if (!saved) return { width: 1280, height: 900 };
  const area = workAreas.find((a) => saved.x < a.x + a.width && saved.x + saved.width > a.x && saved.y < a.y + a.height && saved.y + saved.height > a.y) ?? workAreas[0];
  const width = Math.max(960, Math.min(saved.width, area.width));
  const height = Math.max(640, Math.min(saved.height, area.height));
  return { width, height, x: Math.max(area.x, Math.min(saved.x, area.x + area.width - width)), y: Math.max(area.y, Math.min(saved.y, area.y + area.height - height)) };
}

function downloadPath(directory, filename, reserved = new Set()) {
  fs.mkdirSync(directory, { recursive: true });
  let safe = path.basename(filename.replace(/\\/g, '/')).replace(/[<>:"|?*\x00-\x1f]/g, '_').replace(/[. ]+$/, '') || '下载文件';
  if (/^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)/i.test(safe)) safe = `_${safe}`;
  const extension = path.extname(safe), stem = path.basename(safe, extension);
  let target = path.join(directory, safe), index = 1;
  while (fs.existsSync(target) || reserved.has(target)) target = path.join(directory, `${stem} (${index++})${extension}`);
  return target;
}

module.exports = { Preferences, isQuietTime, windowBounds, downloadPath };
