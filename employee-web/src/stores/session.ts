import { defineStore } from 'pinia';
import { ApiClientError, employeeApi } from '../api';
import type {
  CurrentUserResponse,
  LoginSendCodeRequest,
  LoginSendCodeResponse,
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
  sendLoginCode(request: LoginSendCodeRequest): Promise<LoginSendCodeResponse>;
  logout(): Promise<void>;
  sendEmailCode(request: SendEmailCodeRequest): Promise<void>;
  resetPassword(request: ResetPasswordRequest): Promise<void>;
}

export interface EmployeeOrgApi {
  profile(): Promise<OrgUserDetailResponse>;
  manager(): Promise<OrgUserSummaryResponse | null>;
  subordinates(): Promise<OrgUserSummaryResponse[]>;
  colleagues(): Promise<OrgUserSummaryResponse[]>;
}

export const useSessionStore = defineStore('employee-session', {
  state: () => ({
    user: null as CurrentUserResponse | null,
    profile: null as OrgUserDetailResponse | null,
    manager: null as OrgUserSummaryResponse | null,
    subordinates: [] as OrgUserSummaryResponse[],
    colleagues: [] as OrgUserSummaryResponse[],
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
        if (loginResult.requiresEmailCode || loginResult.requiresEmailBinding) {
          this.user = null;
          return loginResult;
        }
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
    async sendLoginCode(identifier: string, authApi: EmployeeAuthApi = employeeApi.auth) {
      this.error = '';
      try {
        return await authApi.sendLoginCode({ identifier });
      } catch (error) {
        this.error = resolveSessionError(error);
        throw error;
      }
    },
    async loadOrgProfile(orgApi: EmployeeOrgApi = employeeApi.me) {
      if (!this.user) return;
      this.profileLoading = true;
      this.profileError = '';
      try {
        const [profile, manager, subordinates, colleagues] = await Promise.all([
          orgApi.profile(),
          orgApi.manager().catch(() => null),
          orgApi.subordinates().catch(() => []),
          orgApi.colleagues().catch(() => []),
        ]);
        this.profile = profile;
        this.manager = manager;
        this.subordinates = subordinates;
        this.colleagues = colleagues;
      } catch (error) {
        this.profile = null;
        this.manager = null;
        this.subordinates = [];
        this.colleagues = [];
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
        this.colleagues = [];
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
