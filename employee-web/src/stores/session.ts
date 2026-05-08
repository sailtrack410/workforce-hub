import { defineStore } from 'pinia';
import { ApiClientError, employeeApi } from '../api';
import type { CurrentUserResponse, LoginRequest, LoginResponse } from '../api';

export interface EmployeeAuthApi {
  me(): Promise<CurrentUserResponse>;
  login(request: LoginRequest): Promise<LoginResponse>;
  logout(): Promise<void>;
}

export const useSessionStore = defineStore('employee-session', {
  state: () => ({
    user: null as CurrentUserResponse | null,
    loading: false,
    error: '',
    initialized: false,
  }),
  getters: {
    isAuthenticated(state): boolean {
      return state.user !== null;
    },
    displayName(state): string {
      return state.user?.displayName ?? '';
    },
    shortName(state): string {
      return state.user?.displayName?.slice(0, 1) ?? '';
    },
  },
  actions: {
    async loadCurrentUser(authApi: EmployeeAuthApi = employeeApi.auth) {
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
    async login(request: LoginRequest, authApi: EmployeeAuthApi = employeeApi.auth) {
      this.loading = true;
      this.error = '';
      try {
        const loginResult = await authApi.login(request);
        this.user = await authApi.me().catch(() => loginResult as CurrentUserResponse);
      } catch (error) {
        this.user = null;
        this.error = resolveSessionError(error);
        throw error;
      } finally {
        this.loading = false;
        this.initialized = true;
      }
    },
    async logout(authApi: EmployeeAuthApi = employeeApi.auth) {
      this.loading = true;
      this.error = '';
      try {
        await authApi.logout();
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
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '会话状态获取失败';
}
