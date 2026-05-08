import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { ApiClientError } from '../api';
import type { CurrentUserResponse, LoginRequest, LoginResponse } from '../api';
import { useSessionStore, type EmployeeAuthApi } from './session';

const currentUser: CurrentUserResponse = {
  userId: 1001,
  userType: 'USER',
  displayName: '林澈',
  loginName: 'lin.che',
  primaryDeptId: 10,
  positionId: 20,
  managerUserId: 30,
  employmentStatus: 'ACTIVE',
};

function createAuthApi(overrides: Partial<EmployeeAuthApi> = {}): EmployeeAuthApi {
  return {
    me: () => Promise.resolve(currentUser),
    login: (_request: LoginRequest) => Promise.resolve(currentUser as LoginResponse),
    logout: () => Promise.resolve(),
    ...overrides,
  };
}

describe('useSessionStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('loads current session user', async () => {
    const session = useSessionStore();

    await session.loadCurrentUser(createAuthApi());

    expect(session.isAuthenticated).toBe(true);
    expect(session.user?.displayName).toBe('林澈');
    expect(session.loading).toBe(false);
  });

  it('clears current user when backend returns unauthorized', async () => {
    const session = useSessionStore();
    session.user = currentUser;

    await session.loadCurrentUser(createAuthApi({
      me: () => Promise.reject(new ApiClientError('未登录', { code: 401 })),
    }));

    expect(session.isAuthenticated).toBe(false);
    expect(session.user).toBeNull();
    expect(session.error).toBe('未登录');
  });

  it('stores login result and then refreshes current user', async () => {
    const session = useSessionStore();

    await session.login({ loginName: 'lin.che', password: 'secret' }, createAuthApi());

    expect(session.isAuthenticated).toBe(true);
    expect(session.user?.loginName).toBe('lin.che');
  });

  it('clears user after logout', async () => {
    const session = useSessionStore();
    session.user = currentUser;

    await session.logout(createAuthApi());

    expect(session.user).toBeNull();
    expect(session.isAuthenticated).toBe(false);
  });
});
