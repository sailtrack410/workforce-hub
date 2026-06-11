import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useSessionStore, type AdminAuthApi } from './session';

const user = {
  userId: '9223372036854775807',
  userType: 'ADMIN',
  displayName: '系统管理员',
  loginName: 'admin.demo',
};

function authApi(overrides: Partial<AdminAuthApi> = {}): AdminAuthApi {
  return {
    me: async () => user,
    login: async () => user,
    logout: async () => undefined,
    ...overrides,
  };
}

describe('admin session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('loads current admin user', async () => {
    const session = useSessionStore();

    await session.loadCurrentUser(authApi());

    expect(session.isAuthenticated).toBe(true);
    expect(session.displayName).toBe('系统管理员');
    expect(session.initialized).toBe(true);
  });

  it('clears session when current user request fails', async () => {
    const session = useSessionStore();

    await session.loadCurrentUser(authApi({
      me: async () => {
        throw new Error('Unauthorized');
      },
    }));

    expect(session.isAuthenticated).toBe(false);
    expect(session.error).toBe('Unauthorized');
    expect(session.initialized).toBe(true);
  });
});
