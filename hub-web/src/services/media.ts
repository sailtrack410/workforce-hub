import { currentContext } from './localData';
export async function localMedia(key: string, url?: string) {
  const context = await currentContext();
  if (!context) return url ?? null;
  return await window.workforceDesktop!.media(context, key, url);
}
