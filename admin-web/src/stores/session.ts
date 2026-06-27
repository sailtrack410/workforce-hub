import { defineStore } from 'pinia';
import { ApiClientError } from '@/api/http';
import { adminApi } from '@/api/admin';
import type { CurrentUserResponse, LoginRequest, LoginResponse } from '@/api/types';

export interface AdminAuthApi {
  me(): Promise<CurrentUserResponse>;
  login(request: LoginRequest): Promise<LoginResponse>;
  logout(): Promise<void>;
}

export const useSessionStore = defineStore('admin-session', {
  state: () => ({
    user: null as CurrentUserResponse | null,
    loading: false,
    initialized: false,
    error: '',
  }),
  getters: {
    isAuthenticated(state): boolean {
      return state.user !== null;
    },
    displayName(state): string {
      return state.user?.displayName || state.user?.loginName || '';
    },
    shortName(state): string {
      return (state.user?.displayName || state.user?.loginName || 'A').slice(0, 1);
    },
    hasPermission(state): (permissionCode?: string) => boolean {
      return (permissionCode?: string) => {
        if (!permissionCode) return true;
        if (!state.user) return false;
        if (!state.user.permissionCodes?.length) return true;
        return state.user.permissionCodes.includes(permissionCode);
      };
    },
  },
  actions: {
    async loadCurrentUser(authApi: AdminAuthApi = adminApi.auth) {
      this.loading = true;
      this.error = '';
      try {
        this.user = await authApi.me();
      } catch (error) {
        this.user = null;
        this.error = resolveSessionError(error);
      } finally {
        this.loading = false;
        this.initialized = true;
      }
    },
    async login(request: LoginRequest, authApi: AdminAuthApi = adminApi.auth) {
      this.loading = true;
      this.error = '';
      try {
        const loginResult = await authApi.login(request);
        this.user = await authApi.me().catch(() => loginResult as CurrentUserResponse);
        return loginResult;
      } catch (error) {
        this.user = null;
        this.error = resolveSessionError(error);
        throw error;
      } finally {
        this.loading = false;
        this.initialized = true;
      }
    },
    async logout(authApi: AdminAuthApi = adminApi.auth) {
      this.loading = true;
      this.error = '';
      try {
        await authApi.logout();
      } catch (error) {
        this.error = resolveSessionError(error);
      } finally {
        this.user = null;
        this.loading = false;
        this.initialized = true;
      }
    },
  },
});

function resolveSessionError(error: unknown) {
  if (error instanceof ApiClientError) {
    if (error.status === 401 || error.status === 403) return '登录状态无效';
    if (error.status && error.status >= 500) return '认证服务暂不可用';
    return error.message;
  }
  if (error instanceof Error) {
    if (error.message === 'Failed to fetch') return '认证服务暂不可用';
    return error.message;
  }
  return '会话状态获取失败';
}
