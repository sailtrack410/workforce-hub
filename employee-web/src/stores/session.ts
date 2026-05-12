import { defineStore } from 'pinia';
import { ApiClientError, employeeApi } from '../api';
import type {
  CurrentUserResponse,
  LoginRequest,
  LoginResponse,
  OrgUserDetailResponse,
  OrgUserSummaryResponse,
  ResetPasswordRequest,
  SendEmailCodeRequest,
} from '../api';

export interface EmployeeAuthApi {
  me(): Promise<CurrentUserResponse>;
  login(request: LoginRequest): Promise<LoginResponse>;
  logout(): Promise<void>;
  sendEmailCode(request: SendEmailCodeRequest): Promise<void>;
  resetPassword(request: ResetPasswordRequest): Promise<void>;
}

export interface EmployeeOrgApi {
  profile(): Promise<OrgUserDetailResponse>;
  manager(): Promise<OrgUserSummaryResponse | null>;
  subordinates(): Promise<OrgUserSummaryResponse[]>;
}

export const useSessionStore = defineStore('employee-session', {
  state: () => ({
    user: null as CurrentUserResponse | null,
    profile: null as OrgUserDetailResponse | null,
    manager: null as OrgUserSummaryResponse | null,
    subordinates: [] as OrgUserSummaryResponse[],
    loading: false,
    profileLoading: false,
    error: '',
    profileError: '',
    initialized: false,
  }),
  getters: {
    isAuthenticated(state): boolean {
      return state.user !== null;
    },
    displayName(state): string {
      return state.profile?.realName ?? state.user?.displayName ?? '';
    },
    shortName(state): string {
      return (state.profile?.realName ?? state.user?.displayName ?? '').slice(0, 1);
    },
    positionName(state): string {
      return state.profile?.positionName ?? '';
    },
    departmentName(state): string {
      return state.profile?.primaryDeptName ?? '';
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
    async loadOrgProfile(orgApi: EmployeeOrgApi = employeeApi.me) {
      if (!this.user) return;
      this.profileLoading = true;
      this.profileError = '';
      try {
        const [profile, manager, subordinates] = await Promise.all([
          orgApi.profile(),
          orgApi.manager().catch(() => null),
          orgApi.subordinates().catch(() => []),
        ]);
        this.profile = profile;
        this.manager = manager;
        this.subordinates = subordinates;
      } catch (error) {
        this.profile = null;
        this.manager = null;
        this.subordinates = [];
        this.profileError = resolveSessionError(error);
      } finally {
        this.profileLoading = false;
      }
    },
    async logout(authApi: EmployeeAuthApi = employeeApi.auth) {
      this.loading = true;
      this.error = '';
      try {
        await authApi.logout();
      } finally {
        this.user = null;
        this.profile = null;
        this.manager = null;
        this.subordinates = [];
        this.loading = false;
        this.initialized = true;
      }
    },
    async sendPasswordResetCode(email: string, authApi: EmployeeAuthApi = employeeApi.auth) {
      this.error = '';
      try {
        await authApi.sendEmailCode({ email, purpose: 'RESET_PASSWORD' });
      } catch (error) {
        this.error = resolveSessionError(error);
        throw error;
      }
    },
    async resetPassword(request: ResetPasswordRequest, authApi: EmployeeAuthApi = employeeApi.auth) {
      this.loading = true;
      this.error = '';
      try {
        await authApi.resetPassword(request);
      } catch (error) {
        this.error = resolveSessionError(error);
        throw error;
      } finally {
        this.loading = false;
      }
    },
  },
});

function resolveSessionError(error: unknown) {
  if (error instanceof ApiClientError) {
    if (error.status && error.status >= 500 && error.message === 'Internal Server Error') {
      return '认证服务暂不可用';
    }
    return error.message;
  }
  if (error instanceof Error) {
    if (error.message === 'Failed to fetch') {
      return '认证服务暂不可用';
    }
    return error.message;
  }
  return '会话状态获取失败';
}
