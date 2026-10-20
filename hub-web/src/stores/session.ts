// hub-web 会话与组织档案 store
import { defineStore } from 'pinia';
import { authApi, orgApi } from '@/api';
import type { CurrentUserResponse, EntityId, OrgUserDetailResponse, OrgUserSummaryResponse } from '@/api/types';

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
      } catch {
        this.user = null;
      } finally {
        this.initialized = true;
      }
    },
    async login(loginName: string, password: string) {
      this.user = await authApi.login(loginName, password);
      this.initialized = true;
    },
    async logout() {
      await authApi.logout().catch(() => undefined);
      this.user = null;
      this.profile = null;
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
