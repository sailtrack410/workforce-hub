// hub-web 会话与组织档案 store
import { defineStore } from 'pinia';
import { authApi, orgApi } from '@/api';
import type { CurrentUserResponse, EntityId, OrgUserDetailResponse, OrgUserSummaryResponse } from '@/api/types';
import { ApiError } from '@/api/http';
import { localStatus } from '@/services/localData';
import { useImStore } from '@/stores/im';

// 视图层统一的联系人形态（选择器/提及/副标题使用）
export interface ContactView {
  id: string;
  userId: EntityId;
  name: string;
  originalName?: string;
  remarkName?: string | null;
  role?: string | null;
  department?: string | null;
  relation?: string | null;
  avatarAttachmentRefId?: EntityId | null;
  avatarUrl?: string;
}

export const useSessionStore = defineStore('hw-session', {
  state: () => ({
    user: null as CurrentUserResponse | null,
    profile: null as OrgUserDetailResponse | null,
    manager: null as OrgUserSummaryResponse | null,
    subordinates: [] as OrgUserSummaryResponse[],
    colleagues: [] as OrgUserSummaryResponse[],
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state) => state.user !== null,
    displayName: (state) => state.profile?.realName ?? state.user?.displayName ?? '',
    contacts(state): ContactView[] {
      const map = new Map<string, ContactView>();
      const push = (u: OrgUserSummaryResponse, relation: string) => {
        const key = String(u.userId);
        if (map.has(key)) return;
        map.set(key, {
          id: key,
          userId: u.userId,
          name: u.realName,
          originalName: u.realName,
          remarkName: null,
          role: u.positionName,
          department: u.primaryDeptName,
          relation,
          avatarAttachmentRefId: u.avatarAttachmentRefId,
        });
      };
      if (state.manager) push(state.manager, '领导');
      for (const u of state.subordinates) push(u, '下属');
      for (const u of state.colleagues) push(u, '同事');
      return [...map.values()];
    },
  },
  actions: {
    async loadCurrentUser() {
      try {
        this.user = await authApi.me();
        const account = await window.workforceDesktop?.bindCacheAccount?.();
        if (account && !account.offline && String(account.user.userId) === String(this.user.userId)) this.user = account.user;
        else if (account) await window.workforceDesktop?.logoutCache?.();
      } catch (error) {
        if (error instanceof TypeError || error instanceof ApiError && (error.status ?? 0) >= 500) {
          const cached = await window.workforceDesktop?.bindCacheAccount?.().catch(() => null);
          this.user = cached?.user ?? null; localStatus.offline = Boolean(cached?.offline);
        } else {
          this.user = null;
          await window.workforceDesktop?.logoutCache?.().catch(() => undefined);
        }
      } finally {
        this.initialized = true;
      }
    },
    async login(loginName: string, password: string) {
      this.user = await authApi.login(loginName, password);
      const account = await window.workforceDesktop?.bindCacheAccount?.();
      if (account && !account.offline && String(account.user.userId) === String(this.user.userId)) this.user = account.user;
      else if (account) await window.workforceDesktop?.logoutCache?.();
      this.initialized = true;
    },
    async logout() {
      await authApi.logout().catch(() => undefined);
      await window.workforceDesktop?.logoutCache?.().catch(() => undefined);
      useImStore().$reset();
      this.user = null;
      this.profile = null;
      this.manager = null; this.subordinates = []; this.colleagues = [];
      Object.assign(localStatus, { offline: false, cachedAt: 0, pending: 0, conflicts: 0, error: '' });
    },
    async loadOrgProfile() {
      if (!this.user) return;
      const [profile, manager, subordinates, colleagues] = await Promise.all([
        orgApi.profile().catch(() => null),
        orgApi.manager().catch(() => null),
        orgApi.subordinates().catch(() => []),
        orgApi.colleagues().catch(() => []),
      ]);
      this.profile = profile;
      this.manager = manager;
      this.subordinates = subordinates;
      this.colleagues = colleagues;
    },
  },
});
