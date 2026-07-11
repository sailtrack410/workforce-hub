<script setup lang="ts">
import {
  Camera,
  ChevronLeft,
  LogOut,
  MoonStar,
  SunMedium,
} from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { employeeApi } from '@/api';
import type {
  AccentColor,
  BubbleStyle,
  FontScale,
  OrgUserSummaryResponse,
  PresenceStatus,
  UpdateEmployeePreferenceRequest,
} from '@/api';
import { createEmailCodeCooldown } from '@/composables/emailCodeCooldown';
import { useCurrentUserAvatarUrl } from '@/composables/useCurrentUserAvatarUrl';
import { usePersonalFileTransfer } from '@/composables/usePersonalFileTransfer';
import { ensureBrowserNotificationPermission } from '@/services/notifications';
import { useSettingsSectionContext } from '../employee-shell-context';
import { displayField, employmentStatusLabel } from '@/utils/employee-format';
import { normalizeSettingsPanel, shellUser } from '@/stores/shell';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppButton from '../../components/ui/AppButton.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppErrorState from '../../components/ui/AppErrorState.vue';
import AppInput from '../../components/ui/AppInput.vue';

type FieldRows = Array<[string, string]>;
type NotificationPreferenceField = 'notifSound' | 'notifDesktop' | 'notifMentionOnly' | 'notifUnreadBadge';

interface RelationTree {
  department: string;
  managerName: string;
  ownName: string;
  subordinates: OrgUserSummaryResponse[];
}

const {
  shell,
  session,
  showToast,
} = useSettingsSectionContext();

const router = useRouter();
const bindEmail = ref('');
const bindEmailCode = ref('');
const bindEmailCodeSending = ref(false);
const bindEmailCodeCooldown = createEmailCodeCooldown();
const bindEmailSubmitting = ref(false);
const bindEmailEditing = ref(false);
const bindEmailNotice = ref('');
const bindEmailError = ref('');
const mobileDraft = ref('');
const mobileSubmitting = ref(false);
const mobileUpdateNotice = ref('');
const mobileUpdateError = ref('');
const mobileInput = ref<HTMLInputElement | null>(null);
const emailCard = ref<HTMLElement | null>(null);
const passwordCard = ref<HTMLElement | null>(null);
const passwordChangeForm = ref({ code: '', newPassword: '' });
const passwordChangeCodeSending = ref(false);
const passwordChangeCodeCooldown = createEmailCodeCooldown();
const passwordChangeSubmitting = ref(false);
const passwordChangeNotice = ref('');
const passwordChangeError = ref('');
const avatarUploading = ref(false);
const avatarUpdateError = ref('');
const avatarPickerInput = ref<HTMLInputElement | null>(null);
const logoutSubmitting = ref(false);
const logoutConfirmOpen = ref(false);
const preferencesHydrated = ref(false);
let preferencePushTimer: number | undefined;

const {
  uploadAvatarAttachment,
} = usePersonalFileTransfer();
const { refreshCurrentUserAvatarUrl } = useCurrentUserAvatarUrl(shell, session);

const presenceOptions: Array<{ value: PresenceStatus; label: string }> = [
  { value: 'ONLINE', label: '在线' },
  { value: 'BUSY', label: '忙碌' },
  { value: 'AWAY', label: '离开' },
  { value: 'DND', label: '免打扰' },
];

const accentColorOptions: Array<{ value: AccentColor; label: string }> = [
  { value: 'indigo', label: '靛蓝' },
  { value: 'violet', label: '紫罗兰' },
  { value: 'blue', label: '蓝色' },
  { value: 'teal', label: '青绿' },
  { value: 'amber', label: '琥珀' },
  { value: 'rose', label: '玫瑰' },
];

const fontScaleOptions: Array<{ value: FontScale; label: string }> = [
  { value: 'small', label: '小' },
  { value: 'medium', label: '标准' },
  { value: 'large', label: '大' },
];

const bubbleStyleOptions: Array<{ value: BubbleStyle; label: string }> = [
  { value: 'rounded', label: '圆润' },
  { value: 'sharp', label: '利落' },
];

const activeProfile = computed(() => shell.activeProfile);
const selectedProfileId = computed(() => activeProfile.value.id);
const currentUserName = computed(() => session.displayName || shellUser.name);
const currentUserShortName = computed(() => session.shortName || shellUser.shortName);
const profileStatusLabel = computed(() => employmentStatusLabel(session.profile?.employmentStatus ?? session.user?.employmentStatus));
const currentEmail = computed(() => session.user?.email?.trim() ?? '');
const themeLabel = computed(() => (shell.theme === 'light' ? '浅色' : '深色'));
const presenceStatusLabel = computed(() => (
  presenceOptions.find((item) => item.value === shell.presenceStatus)?.label ?? '在线'
));
const messageDensityLabel = computed(() => (shell.messageDensity === 'compact' ? '紧凑' : '宽松'));
const bindEmailCodeButtonText = computed(() => (
  bindEmailCodeSending.value ? '发送中' : bindEmailCodeCooldown.label.value
));
const passwordChangeCodeButtonText = computed(() => (
  passwordChangeCodeSending.value ? '发送中' : passwordChangeCodeCooldown.label.value
));
const profileRows = computed<FieldRows>(() => [
  ['岗位', displayField(session.profile?.positionName)],
  ['入职日期', displayField(session.profile?.hireDate)],
]);
const accountRows = computed<FieldRows>(() => [
  ['登录名', displayField(session.user?.loginName)],
  ['手机号', displayField(session.profile?.mobile)],
  ['绑定邮箱', currentEmail.value || '未绑定'],
  ['账号状态', session.user ? '正常' : '未登录'],
]);
const relationTree = computed<RelationTree>(() => {
  const ownName = session.displayName || currentUserName.value;
  return {
    department: session.departmentName || '组织',
    managerName: session.manager?.realName ?? '',
    ownName,
    subordinates: session.subordinates,
  };
});
const organizationRows = computed<FieldRows>(() => [
  ['所属部门', relationTree.value.department],
  ['岗位', displayField(session.profile?.positionName)],
  ['直属上级', displayField(relationTree.value.managerName || session.profile?.managerName)],
  ['直属下级', `${relationTree.value.subordinates.length} 人`],
]);
const colleaguesLoading = computed(() => session.profileLoading);
const colleaguesError = computed(() => session.profileError);
const preferenceDisabled = computed(() => !preferencesHydrated.value);

void hydrateUserPreferences();
void refreshCurrentUserAvatarUrl();

async function hydrateUserPreferences() {
  try {
    const prefs = await employeeApi.me.preferences();
    shell.hydratePreferencesFromServer(prefs);
  } catch {
    return;
  } finally {
    preferencesHydrated.value = true;
  }
}

async function pushPreferencesNow(patch: UpdateEmployeePreferenceRequest = {}) {
  if (!preferencesHydrated.value) return;
  if (preferencePushTimer) {
    window.clearTimeout(preferencePushTimer);
    preferencePushTimer = undefined;
  }
  await employeeApi.me.updatePreferences({
    ...shell.currentPreferencePayload(),
    ...patch,
  });
}

function schedulePreferencePush() {
  if (!preferencesHydrated.value) return;
  if (preferencePushTimer) window.clearTimeout(preferencePushTimer);
  preferencePushTimer = window.setTimeout(async () => {
    preferencePushTimer = undefined;
    try {
      await employeeApi.me.updatePreferences(shell.currentPreferencePayload());
    } catch {
      // 远端更新失败不影响本地体验，下次自动重试。
    }
  }, 600);
}

async function openMobileReset() {
  mobileDraft.value = session.profile?.mobile ?? '';
  mobileUpdateNotice.value = '';
  mobileUpdateError.value = '';
  selectSettingsItem('security');
  await nextTick();
  mobileInput.value?.focus();
}

async function retryOrgProfile() {
  await session.loadOrgProfile();
  shell.setColleagues(session.colleagues);
  shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
}

function backToList() {
  shell.setMobileView('list');
}

function selectSettingsItem(id: string) {
  const canonical = normalizeSettingsPanel(id);
  shell.selectItem('settings', canonical);
  void router.push({ name: 'employee-settings-detail', params: { panel: canonical } });
}

async function openEmailBinding() {
  startBindEmailEditing();
  selectSettingsItem('security');
  await nextTick();
  emailCard.value?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  (emailCard.value?.querySelector('input') as HTMLInputElement | null)?.focus();
}

async function openPasswordChange() {
  selectSettingsItem('security');
  await nextTick();
  passwordCard.value?.scrollIntoView({ block: 'center', behavior: 'smooth' });
  (passwordCard.value?.querySelector('input:not(:disabled)') as HTMLInputElement | null)?.focus();
}

async function submitMobileUpdate() {
  const mobile = mobileDraft.value.trim();
  if (mobileSubmitting.value) return;
  mobileUpdateNotice.value = '';
  mobileUpdateError.value = '';
  mobileSubmitting.value = true;
  try {
    await employeeApi.me.updateProfile({
      mobile: mobile || null,
      avatarAttachmentRefId: session.profile?.avatarAttachmentRefId ?? null,
    });
    await session.loadCurrentUser();
    await session.loadOrgProfile();
    shell.setColleagues(session.colleagues);
    shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    mobileDraft.value = session.profile?.mobile ?? '';
    mobileUpdateNotice.value = '手机号已更新';
    showToast('手机号已更新');
  } catch (error) {
    mobileUpdateError.value = error instanceof Error ? error.message : '手机号更新失败';
    showToast(mobileUpdateError.value);
  } finally {
    mobileSubmitting.value = false;
  }
}

async function sendBindEmailCode() {
  const email = bindEmail.value.trim();
  if (!email || bindEmailCodeSending.value || bindEmailCodeCooldown.isActive.value) return;
  bindEmailCodeSending.value = true;
  bindEmailNotice.value = '';
  bindEmailError.value = '';
  try {
    await employeeApi.auth.sendEmailCode({ email, purpose: 'BIND' });
    bindEmailCodeCooldown.start();
    bindEmailNotice.value = '验证码已发送';
    showToast(bindEmailNotice.value);
  } catch (error) {
    bindEmailError.value = error instanceof Error ? error.message : '验证码发送失败';
    showToast(bindEmailError.value);
  } finally {
    bindEmailCodeSending.value = false;
  }
}

async function submitBindEmail() {
  const email = bindEmail.value.trim();
  const code = bindEmailCode.value.trim();
  if (!email || !code || bindEmailSubmitting.value) return;
  bindEmailSubmitting.value = true;
  bindEmailNotice.value = '';
  bindEmailError.value = '';
  try {
    const hadEmail = Boolean(currentEmail.value);
    await employeeApi.auth.bindEmail({ email, code });
    bindEmail.value = '';
    bindEmailCode.value = '';
    bindEmailEditing.value = false;
    bindEmailCodeCooldown.reset();
    await session.loadCurrentUser();
    await session.loadOrgProfile();
    shell.setColleagues(session.colleagues);
    shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    bindEmailNotice.value = hadEmail ? '邮箱已换绑' : '邮箱已绑定';
    showToast(bindEmailNotice.value);
  } catch (error) {
    bindEmailError.value = error instanceof Error ? error.message : '邮箱绑定失败';
    showToast(bindEmailError.value);
  } finally {
    bindEmailSubmitting.value = false;
  }
}

function startBindEmailEditing() {
  bindEmailEditing.value = true;
  bindEmail.value = '';
  bindEmailCode.value = '';
  bindEmailNotice.value = '';
  bindEmailError.value = '';
  bindEmailCodeCooldown.reset();
}

function cancelBindEmailEditing() {
  if (bindEmailSubmitting.value) return;
  bindEmailEditing.value = false;
  bindEmail.value = '';
  bindEmailCode.value = '';
  bindEmailError.value = '';
  bindEmailCodeCooldown.reset();
}

async function sendPasswordChangeCode() {
  const email = currentEmail.value;
  if (!email || passwordChangeCodeSending.value || passwordChangeCodeCooldown.isActive.value) return;
  passwordChangeCodeSending.value = true;
  passwordChangeNotice.value = '';
  passwordChangeError.value = '';
  try {
    await session.sendPasswordResetCode(email);
    passwordChangeCodeCooldown.start();
    passwordChangeNotice.value = '验证码已发送';
    showToast(passwordChangeNotice.value);
  } catch (error) {
    passwordChangeError.value = error instanceof Error ? error.message : '验证码发送失败';
    showToast(passwordChangeError.value);
  } finally {
    passwordChangeCodeSending.value = false;
  }
}

async function submitPasswordChange() {
  const email = currentEmail.value;
  const code = passwordChangeForm.value.code.trim();
  const newPassword = passwordChangeForm.value.newPassword;
  if (!email || !code || newPassword.length < 8 || passwordChangeSubmitting.value) return;
  passwordChangeSubmitting.value = true;
  passwordChangeNotice.value = '';
  passwordChangeError.value = '';
  try {
    await session.resetPassword({ email, code, newPassword });
    passwordChangeForm.value.code = '';
    passwordChangeForm.value.newPassword = '';
    passwordChangeNotice.value = '密码已更新';
    showToast(passwordChangeNotice.value);
  } catch (error) {
    passwordChangeError.value = error instanceof Error ? error.message : '密码修改失败';
    showToast(passwordChangeError.value);
  } finally {
    passwordChangeSubmitting.value = false;
  }
}

function openAvatarPicker() {
  if (avatarUploading.value) return;
  avatarUpdateError.value = '';
  avatarPickerInput.value?.click();
}

async function handleAvatarSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (!file.type.startsWith('image/')) {
    avatarUpdateError.value = '请选择图片文件';
    showToast(avatarUpdateError.value);
    return;
  }
  avatarUploading.value = true;
  avatarUpdateError.value = '';
  try {
    const attachment = await uploadAvatarAttachment(file);
    await employeeApi.me.updateProfile({
      mobile: session.profile?.mobile ?? null,
      avatarAttachmentRefId: attachment.attachmentRefId,
    });
    await session.loadOrgProfile();
    shell.setColleagues(session.colleagues);
    shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    await refreshCurrentUserAvatarUrl();
    showToast('头像已更新');
  } catch (error) {
    avatarUpdateError.value = error instanceof Error ? error.message : '头像更新失败';
    showToast(avatarUpdateError.value);
  } finally {
    avatarUploading.value = false;
  }
}

function handleLogout() {
  if (logoutSubmitting.value) return;
  logoutConfirmOpen.value = true;
}

async function confirmLogout() {
  if (logoutSubmitting.value) return;
  logoutSubmitting.value = true;
  try {
    await session.logout();
    logoutConfirmOpen.value = false;
    shell.setAvatarUrl('');
    showToast('已退出登录');
    void router.replace({ name: 'employee-messages' });
  } catch (error) {
    showToast(error instanceof Error ? error.message : '退出登录失败');
  } finally {
    logoutSubmitting.value = false;
  }
}

async function updatePresenceStatus(status: PresenceStatus) {
  if (shell.presenceStatus === status) return;
  shell.setPresenceStatus(status);
  try {
    await pushPreferencesNow({ presenceStatus: status });
    showToast('在线状态已更新');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '在线状态保存失败');
  }
}

async function updateAccentColor(color: AccentColor) {
  if (shell.accentColor === color) return;
  shell.setAccentColor(color);
  try {
    await pushPreferencesNow({ accentColor: color });
  } catch {
    return;
  }
}

async function updateFontScale(scale: FontScale) {
  if (shell.fontScale === scale) return;
  shell.setFontScale(scale);
  try {
    await pushPreferencesNow({ fontScale: scale });
  } catch {
    return;
  }
}

async function updateBubbleStyle(style: BubbleStyle) {
  if (shell.bubbleStyle === style) return;
  shell.setBubbleStyle(style);
  try {
    await pushPreferencesNow({ bubbleStyle: style });
  } catch {
    return;
  }
}

async function updateNotificationPreference(field: NotificationPreferenceField, value: boolean) {
  if (field === 'notifDesktop' && value && !await ensureBrowserNotificationPermission()) {
    shell.setNotificationPreference(field, false);
    showToast('桌面通知未授权');
    return;
  }
  shell.setNotificationPreference(field, value);
  try {
    await pushPreferencesNow({ [field]: value });
  } catch {
    return;
  }
}

watch(
  () => [shell.theme, shell.messageDensity, shell.listWidth] as const,
  () => {
    schedulePreferencePush();
  },
);

watch(() => session.profile?.avatarAttachmentRefId, () => {
  void refreshCurrentUserAvatarUrl();
});

watch(() => session.profile?.mobile, (mobile) => {
  if (!mobileSubmitting.value) {
    mobileDraft.value = mobile ?? '';
  }
}, { immediate: true });

onBeforeUnmount(() => {
  if (preferencePushTimer) window.clearTimeout(preferencePushTimer);
  bindEmailCodeCooldown.reset();
  passwordChangeCodeCooldown.reset();
});
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">{{ activeProfile.title }}</h2>
      </div>
    </div>
  </header>

  <section class="content-stack settings-dashboard">
    <AppErrorState
      v-if="session.profileError"
      title="档案加载失败"
      :description="session.profileError"
      action-label="重试"
      @retry="retryOrgProfile"
    />

    <section v-if="selectedProfileId === 'profile'" class="personal-center">
      <article class="personal-center-head">
        <AppAvatar :label="currentUserShortName" :image="shell.avatarUrl" tone="primary" size="xl" status="online" />
        <div class="personal-center-title">
          <h3>{{ currentUserName }}</h3>
          <p>
            <span v-if="session.departmentName">{{ session.departmentName }}</span>
            <span v-if="session.positionName"> · {{ session.positionName }}</span>
          </p>
        </div>
        <input
          ref="avatarPickerInput"
          type="file"
          accept="image/*"
          hidden
          @change="handleAvatarSelected"
        />
        <div class="personal-center-actions">
          <AppButton variant="secondary" size="sm" :disabled="avatarUploading" @click="openAvatarPicker">
            <Camera :size="15" />
            {{ avatarUploading ? '上传中' : '更换头像' }}
          </AppButton>
        </div>
        <p v-if="avatarUpdateError" class="dialog-error personal-center-error">{{ avatarUpdateError }}</p>
      </article>

      <section class="personal-status-grid" aria-label="个人状态">
        <article class="personal-status-card">
          <header>
            <span>身份状态</span>
            <strong>{{ profileStatusLabel }}</strong>
          </header>
          <dl class="personal-status-list">
            <div v-for="row in profileRows" :key="row[0]">
              <dt>{{ row[0] }}</dt>
              <dd>{{ row[1] }}</dd>
            </div>
          </dl>
        </article>

        <article class="personal-status-card">
          <header>
            <span>组织状态</span>
            <strong>{{ displayField(session.departmentName) }}</strong>
          </header>
          <dl class="personal-status-list">
            <div>
              <dt>直属上级</dt>
              <dd>{{ displayField(relationTree.managerName || session.profile?.managerName) }}</dd>
            </div>
            <div>
              <dt>直属下级</dt>
              <dd>{{ relationTree.subordinates.length }} 人</dd>
            </div>
          </dl>
          <footer>
            <button type="button" @click="selectSettingsItem('organization')">查看组织关系</button>
          </footer>
        </article>

        <article class="personal-status-card">
          <header>
            <span>账号状态</span>
            <strong>{{ session.user ? '正常' : '未登录' }}</strong>
          </header>
          <dl class="personal-status-list">
            <div>
              <dt>手机号</dt>
              <dd>{{ displayField(session.profile?.mobile) }}</dd>
            </div>
            <div>
              <dt>邮箱</dt>
              <dd>{{ currentEmail ? '已绑定' : '未绑定' }}</dd>
            </div>
          </dl>
          <footer>
            <button type="button" @click="openMobileReset">修改手机号</button>
            <button type="button" @click="openEmailBinding">{{ currentEmail ? '换绑邮箱' : '绑定邮箱' }}</button>
            <button type="button" @click="openPasswordChange">修改密码</button>
          </footer>
        </article>

        <article class="personal-status-card">
          <header>
            <span>偏好状态</span>
            <strong>{{ presenceStatusLabel }}</strong>
          </header>
          <dl class="personal-status-list">
            <div>
              <dt>主题</dt>
              <dd>{{ themeLabel }}</dd>
            </div>
            <div>
              <dt>消息密度</dt>
              <dd>{{ messageDensityLabel }}</dd>
            </div>
          </dl>
          <footer>
            <button type="button" @click="selectSettingsItem('preferences')">设置偏好</button>
          </footer>
        </article>
      </section>
    </section>

    <section v-else-if="selectedProfileId === 'organization'" class="settings-card-grid">
      <article class="settings-card settings-wide-card">
        <dl class="settings-list">
          <div v-for="row in organizationRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>组织链路</h3>
        </header>
        <div class="settings-org-chart compact-org-chart">
          <div class="settings-org-level">
            <span class="settings-org-node department-node">{{ relationTree.department }}</span>
          </div>
          <div class="settings-org-level">
            <span class="settings-org-node">{{ relationTree.managerName || '暂无上级' }}</span>
            <span class="settings-org-node current-user-node">{{ relationTree.ownName }}</span>
          </div>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>直属下级</h3>
        </header>
        <p v-if="colleaguesError" class="dialog-error">{{ colleaguesError }}</p>
        <p v-else-if="colleaguesLoading" class="form-hint">加载中</p>
        <div v-else-if="relationTree.subordinates.length" class="settings-member-list compact-member-list">
          <span v-for="user in relationTree.subordinates" :key="user.userId">
            {{ user.realName }}
          </span>
        </div>
        <p v-else class="empty-inline">暂无下属</p>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'security'" class="settings-card-grid">
      <article class="settings-card settings-wide-card">
        <dl class="settings-list two-column">
          <div v-for="row in accountRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>手机号</h3>
        </header>
        <div class="security-form">
          <label class="security-field">
            <span>手机号</span>
            <input
              ref="mobileInput"
              v-model="mobileDraft"
              class="ui-inline-input"
              type="tel"
              inputmode="tel"
              autocomplete="tel"
            />
          </label>
          <div class="form-actions">
            <AppButton variant="primary" size="sm" :disabled="mobileSubmitting" @click="submitMobileUpdate">
              {{ mobileSubmitting ? '保存中' : '保存手机号' }}
            </AppButton>
          </div>
          <p v-if="mobileUpdateNotice" class="form-result">{{ mobileUpdateNotice }}</p>
          <p v-if="mobileUpdateError" class="dialog-error">{{ mobileUpdateError }}</p>
        </div>
      </article>

      <article ref="emailCard" class="settings-card">
        <header class="settings-card-head">
          <h3>邮箱绑定</h3>
        </header>
        <div class="security-form">
          <div v-if="currentEmail && !bindEmailEditing" class="bound-email-row">
            <div>
              <span>当前邮箱</span>
              <strong>{{ currentEmail }}</strong>
            </div>
            <AppButton variant="secondary" size="sm" @click="startBindEmailEditing">
              换绑
            </AppButton>
          </div>
          <label v-if="!currentEmail || bindEmailEditing" class="security-field">
            <span>{{ currentEmail ? '新邮箱' : '邮箱地址' }}</span>
            <AppInput v-model="bindEmail" type="email" autocomplete="email" placeholder="邮箱地址" />
          </label>
          <label v-if="!currentEmail || bindEmailEditing" class="security-field">
            <span>验证码</span>
            <AppInput v-model="bindEmailCode" autocomplete="one-time-code" placeholder="验证码" />
          </label>
          <div v-if="!currentEmail || bindEmailEditing" class="form-actions">
            <AppButton v-if="currentEmail" variant="ghost" size="sm" @click="cancelBindEmailEditing">
              取消
            </AppButton>
            <AppButton
              variant="secondary"
              size="sm"
              :disabled="bindEmailCodeSending || bindEmailCodeCooldown.isActive.value || !bindEmail.trim()"
              @click="sendBindEmailCode"
            >
              {{ bindEmailCodeButtonText }}
            </AppButton>
            <AppButton variant="primary" size="sm" :disabled="bindEmailSubmitting || !bindEmail.trim() || !bindEmailCode.trim()" @click="submitBindEmail">
              {{ bindEmailSubmitting ? '提交中' : currentEmail ? '确认换绑' : '绑定邮箱' }}
            </AppButton>
          </div>
          <p v-if="bindEmailNotice" class="form-result">{{ bindEmailNotice }}</p>
          <p v-if="bindEmailError" class="dialog-error">{{ bindEmailError }}</p>
        </div>
      </article>

      <article ref="passwordCard" class="settings-card">
        <header class="settings-card-head">
          <h3>修改密码</h3>
        </header>
        <div class="security-form">
          <div v-if="!currentEmail" class="bound-email-row">
            <div>
              <span>邮箱</span>
              <strong>未绑定</strong>
            </div>
            <AppButton variant="secondary" size="sm" @click="openEmailBinding">
              绑定邮箱
            </AppButton>
          </div>
          <label class="security-field">
            <span>邮箱</span>
            <AppInput :model-value="currentEmail" autocomplete="email" disabled placeholder="未绑定邮箱" />
          </label>
          <label class="security-field">
            <span>验证码</span>
            <AppInput v-model="passwordChangeForm.code" autocomplete="one-time-code" placeholder="邮箱验证码" />
          </label>
          <label class="security-field">
            <span>新密码</span>
            <AppInput v-model="passwordChangeForm.newPassword" type="password" autocomplete="new-password" placeholder="8-64 位新密码" />
          </label>
          <div class="form-actions">
            <AppButton
              variant="secondary"
              size="sm"
              :disabled="!currentEmail || passwordChangeCodeSending || passwordChangeCodeCooldown.isActive.value"
              @click="sendPasswordChangeCode"
            >
              {{ passwordChangeCodeButtonText }}
            </AppButton>
            <AppButton variant="primary" size="sm" :disabled="!currentEmail || passwordChangeSubmitting || !passwordChangeForm.code.trim() || passwordChangeForm.newPassword.length < 8" @click="submitPasswordChange">
              {{ passwordChangeSubmitting ? '提交中' : '修改密码' }}
            </AppButton>
          </div>
          <p v-if="passwordChangeNotice" class="form-result">{{ passwordChangeNotice }}</p>
          <p v-if="passwordChangeError" class="dialog-error">{{ passwordChangeError }}</p>
        </div>
      </article>

      <article class="settings-card settings-wide-card settings-danger-card">
        <header class="settings-card-head">
          <h3>退出登录</h3>
          <AppButton variant="destructive" size="sm" :disabled="logoutSubmitting" @click="handleLogout">
            <LogOut :size="15" />
            {{ logoutSubmitting ? '退出中' : '退出登录' }}
          </AppButton>
        </header>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'preferences'" class="settings-card-grid">
      <article class="settings-card">
        <header class="settings-card-head">
          <h3>工作状态</h3>
        </header>
        <div class="settings-density-row">
          <button
            v-for="item in presenceOptions"
            :key="item.value"
            type="button"
            :class="{ active: shell.presenceStatus === item.value }"
            :disabled="preferenceDisabled"
            @click="updatePresenceStatus(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>界面设置</h3>
        </header>
        <div class="settings-control-row">
          <AppButton variant="secondary" size="sm" :disabled="preferenceDisabled" @click="shell.toggleTheme()">
            <component :is="shell.theme === 'light' ? MoonStar : SunMedium" :size="14" />
            {{ themeLabel }}
          </AppButton>
        </div>
        <div class="settings-density-row">
          <button
            type="button"
            :class="{ active: shell.messageDensity === 'cozy' }"
            :disabled="preferenceDisabled"
            @click="shell.setMessageDensity('cozy')"
          >
            宽松
          </button>
          <button
            type="button"
            :class="{ active: shell.messageDensity === 'compact' }"
            :disabled="preferenceDisabled"
            @click="shell.setMessageDensity('compact')"
          >
            紧凑
          </button>
        </div>
        <div class="settings-density-row">
          <button
            v-for="item in fontScaleOptions"
            :key="item.value"
            type="button"
            :class="{ active: shell.fontScale === item.value }"
            :disabled="preferenceDisabled"
            @click="updateFontScale(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>消息样式</h3>
        </header>
        <div class="settings-density-row">
          <button
            v-for="item in bubbleStyleOptions"
            :key="item.value"
            type="button"
            :class="{ active: shell.bubbleStyle === item.value }"
            :disabled="preferenceDisabled"
            @click="updateBubbleStyle(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
        <div class="accent-swatch-grid compact-swatch-grid">
          <button
            v-for="item in accentColorOptions"
            :key="item.value"
            type="button"
            :class="['accent-swatch', `accent-${item.value}`, { active: shell.accentColor === item.value }]"
            :aria-label="item.label"
            :disabled="preferenceDisabled"
            @click="updateAccentColor(item.value)"
          >
            <span />
            <strong>{{ item.label }}</strong>
          </button>
        </div>
      </article>

      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>通知设置</h3>
        </header>
        <div class="settings-toggle-list">
          <label>
            <input
              type="checkbox"
              :checked="shell.notifSound"
              :disabled="preferenceDisabled"
              @change="updateNotificationPreference('notifSound', ($event.target as HTMLInputElement).checked)"
            />
            <span>声音提醒</span>
          </label>
          <label>
            <input
              type="checkbox"
              :checked="shell.notifDesktop"
              :disabled="preferenceDisabled"
              @change="updateNotificationPreference('notifDesktop', ($event.target as HTMLInputElement).checked)"
            />
            <span>桌面通知</span>
          </label>
          <label>
            <input
              type="checkbox"
              :checked="shell.notifMentionOnly"
              :disabled="preferenceDisabled"
              @change="updateNotificationPreference('notifMentionOnly', ($event.target as HTMLInputElement).checked)"
            />
            <span>仅 @ 我提醒</span>
          </label>
          <label>
            <input
              type="checkbox"
              :checked="shell.notifUnreadBadge"
              :disabled="preferenceDisabled"
              @change="updateNotificationPreference('notifUnreadBadge', ($event.target as HTMLInputElement).checked)"
            />
            <span>未读角标</span>
          </label>
        </div>
      </article>
    </section>

  </section>
</div>

<AppDialog v-if="logoutConfirmOpen">
  <section class="dialog-head">
    <h2>退出登录？</h2>
  </section>
  <div class="dialog-actions settings-logout-actions">
    <AppButton variant="secondary" :disabled="logoutSubmitting" @click="logoutConfirmOpen = false">
      继续使用
    </AppButton>
    <AppButton variant="destructive" :disabled="logoutSubmitting" @click="confirmLogout">
      {{ logoutSubmitting ? '退出中' : '退出登录' }}
    </AppButton>
  </div>
</AppDialog>
</template>
