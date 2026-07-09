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

  it('denies permission when legacy session omits permissions', async () => {
    const session = useSessionStore();

    await session.loadCurrentUser(authApi());

    expect(session.hasPermission('ADMIN_ATTENDANCE_MANAGE')).toBe(false);
  });

  it('denies permission when session returns empty permissions', async () => {
    const session = useSessionStore();

    await session.loadCurrentUser(authApi({
      me: async () => ({ ...user, permissionCodes: [] }),
    }));

    expect(session.hasPermission('ADMIN_ATTENDANCE_MANAGE')).toBe(false);
  });

  it('grants permissions for super admin role', async () => {
    const session = useSessionStore();

    await session.loadCurrentUser(authApi({
      me: async () => ({ ...user, roleCodes: ['SUPER_ADMIN'], permissionCodes: [] }),
    }));

    expect(session.hasPermission('ADMIN_ATTENDANCE_MANAGE')).toBe(true);
  });

  it('clears local session when logout request is already expired', async () => {
    const session = useSessionStore();
    await session.loadCurrentUser(authApi());

    await expect(session.logout(authApi({
      logout: async () => {
        throw new Error('Unauthorized');
      },
    }))).resolves.toBeUndefined();

    expect(session.isAuthenticated).toBe(false);
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
