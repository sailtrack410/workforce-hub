import { notificationApi } from '@/api';
import type { SysNotificationResponse } from '@/api/types';

export function useDesktopNotifications() {
  let previous: Set<string> | null = null;
  let checking = false;
  let stopped = false;
  return {
    stop() { stopped = true; },
    async poll() {
      if (!window.workforceDesktop || checking || stopped) return;
      checking = true;
      try {
        const response = await notificationApi.list({ pageNo: 1, pageSize: 50 });
        if (stopped) return;
        const list: SysNotificationResponse[] = Array.isArray(response) ? response : response.list;
        if (previous) {
          for (const n of list) {
            if (n.read || previous.has(String(n.notificationId))) continue;
            let body = n.content || '';
            try { const parsed = JSON.parse(body); body = typeof parsed === 'string' ? parsed : String(parsed.text || n.title); } catch { /* 纯文本通知直接展示 */ }
            await window.workforceDesktop.notify({ id: `notification:${n.notificationId}`, title: n.title, body,
              route: n.linkType === 'PERSONAL_TODO' && n.linkId ? `/todos?todoId=${n.linkId}` : '/notifications' });
          }
        }
        previous = new Set(list.map((n) => String(n.notificationId)));
      } catch { /* 通知拉取失败保留上次快照，下次轮询重试 */ }
      finally { checking = false; }
    },
  };
}
