import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ProfileView from './ProfileView.vue';
import { authApi, orgApi } from '@/api';
const { session, router, showToast } = vi.hoisted(() => ({
  session: { user: { userId: '1', email: 'fixture@example.test' }, profile: { mobile: 'old', avatarAttachmentRefId: '9007199254740993' }, personalization: { displayName: '' }, logout: vi.fn(), loadOrgProfile: vi.fn(), displayName: 'fixture' },
  router: { replace: vi.fn() }, showToast: vi.fn(),
}));
vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onMounted: vi.fn(), onBeforeUnmount: vi.fn(), useSSRContext: () => ({ modules: new Set() }) }));
vi.mock('vue-router', () => ({ useRouter: () => router }));
vi.mock('@/stores/session', () => ({ useSessionStore: () => session }));
vi.mock('@/composables/useToast', () => ({ useToast: () => ({ show: showToast }) }));
vi.mock('@/services/media', () => ({ localMedia: vi.fn(async () => null) }));
vi.mock('@/api', () => ({ authApi: { changePassword: vi.fn(), bindEmail: vi.fn(), sendEmailCode: vi.fn() }, orgApi: { updateProfile: vi.fn(), updatePreferences: vi.fn() }, fileApi: { createDownloadTicket: vi.fn() } }));
const setup = (): any => (ProfileView as unknown as { setup: Function }).setup({}, { expose: vi.fn() });
beforeEach(() => { vi.clearAllMocks(); session.profile = { mobile: 'old', avatarAttachmentRefId: '9007199254740993' }; });
afterEach(() => vi.unstubAllGlobals());
it('保存手机号保留现有头像，失败时保留输入和对话框', async () => {
  const view = setup(); view.openContact(); view.contactForm.value.mobile = 'new';
  vi.mocked(orgApi.updateProfile).mockRejectedValueOnce(new Error('failed'));
  await view.submitContact(); expect(view.contactForm.value.mobile).toBe('new'); expect(view.dialog.value).toBe('contact');
  vi.mocked(orgApi.updateProfile).mockResolvedValueOnce({ mobile: 'new', avatarAttachmentRefId: '9007199254740993' });
  await view.submitContact(); expect(orgApi.updateProfile).toHaveBeenLastCalledWith({ mobile: 'new', avatarAttachmentRefId: '9007199254740993' });
  expect(session.profile.mobile).toBe('new');
});
it('修改密码成功后退出旧登录，不一致密码不提交', async () => {
  const view = setup(); view.passwordForm.value = { current: 'Fixture@2026', next: 'Updated@2026', confirm: 'different' };
  await view.submitPassword(); expect(authApi.changePassword).not.toHaveBeenCalled();
  view.passwordForm.value.confirm = 'Updated@2026'; await view.submitPassword();
  expect(authApi.changePassword).toHaveBeenCalledWith('Fixture@2026', 'Updated@2026'); expect(session.logout).toHaveBeenCalledOnce(); expect(router.replace).toHaveBeenCalledWith({ name: 'login' });
});
it('邮箱绑定必须提交验证码；显示名称保存云端偏好', async () => {
  const view = setup(); view.openContact(); await view.bindEmail(); expect(authApi.bindEmail).not.toHaveBeenCalled();
  view.contactForm.value.code = 'fixture'; await view.bindEmail(); expect(authApi.bindEmail).toHaveBeenCalledWith('fixture@example.test', 'fixture');
  vi.mocked(orgApi.updatePreferences).mockResolvedValue({ displayName: '昵称' }); view.nameDraft.value = ' 昵称 '; await view.saveName();
  expect(orgApi.updatePreferences).toHaveBeenCalledWith({ displayName: '昵称' }); expect(session.personalization.displayName).toBe('昵称');
});
