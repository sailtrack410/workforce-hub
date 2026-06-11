<script setup lang="ts">
import {
  Camera,
  ChevronLeft,
  ChevronRight,
  MoonStar,
  ShieldCheck,
  SlidersHorizontal,
  SunMedium,
  UploadCloud,
  PencilLine,
  UsersRound,
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
import { syncShellSessionProfile } from '@/composables/useShellSessionProfileSync';
import { useSettingsSectionContext } from '../employee-shell-context';
import { displayField, employmentStatusLabel } from '@/utils/employee-format';
import { normalizeSettingsPanel, shellUser } from '@/stores/shell';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppButton from '../../components/ui/AppButton.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
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
const passwordChangeForm = ref({ code: '', newPassword: '' });
const passwordChangeCodeSending = ref(false);
const passwordChangeCodeCooldown = createEmailCodeCooldown();
const passwordChangeSubmitting = ref(false);
const passwordChangeNotice = ref('');
const passwordChangeError = ref('');
const avatarUploading = ref(false);
const avatarUpdateError = ref('');
const avatarPickerInput = ref<HTMLInputElement | null>(null);
const signatureDraft = ref('');
const signatureSaving = ref(false);
const signatureSaveError = ref('');
const signatureSaveNotice = ref('');
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
const activeFile = computed(() => shell.activeFile);
const selectedProfileId = computed(() => activeProfile.value.id);
const currentUserName = computed(() => session.displayName || shellUser.name);
const currentUserShortName = computed(() => session.shortName || shellUser.shortName);
const profileStatusLabel = computed(() => employmentStatusLabel(session.profile?.employmentStatus ?? session.user?.employmentStatus));
const currentEmail = computed(() => session.user?.email?.trim() ?? '');
const themeLabel = computed(() => (shell.theme === 'light' ? '浅色' : '深色'));
const bindEmailCodeButtonText = computed(() => (
  bindEmailCodeSending.value ? '发送中' : bindEmailCodeCooldown.label.value
));
const passwordChangeCodeButtonText = computed(() => (
  passwordChangeCodeSending.value ? '发送中' : passwordChangeCodeCooldown.label.value
));
const profileIdentityRows = computed<FieldRows>(() => [
  ['姓名', displayField(session.profile?.realName || session.user?.displayName)],
  ['登录名', displayField(session.user?.loginName)],
  ['工号', displayField(session.profile?.employeeNo)],
  ['部门', displayField(session.profile?.primaryDeptName)],
  ['岗位', displayField(session.profile?.positionName)],
  ['工作地点', displayField(session.profile?.workLocation)],
  ['入职日期', displayField(session.profile?.hireDate)],
]);
const accountSecurityRows = computed<FieldRows>(() => [
  ['用户类型', displayField(session.user?.userType)],
  ['会话状态', session.user ? '已登录' : '未登录'],
  ['登录名', displayField(session.user?.loginName)],
  ['邮箱', displayField(session.user?.email)],
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
  ['部门', relationTree.value.department],
  ['岗位', displayField(session.profile?.positionName)],
  ['直属上级', displayField(relationTree.value.managerName || session.profile?.managerName)],
  ['直属下级', `${relationTree.value.subordinates.length} 人`],
]);
const preferenceRows = computed<FieldRows>(() => [
  ['颜色模式', themeLabel.value],
  ['消息密度', shell.messageDensity === 'cozy' ? '宽松' : '紧凑'],
  ['列表宽度', `${shell.listWidth}px`],
]);
const storageRows = computed<FieldRows>(() => activeFile.value.summary.map(([value, label]) => [label, value]));
const recentFileRows = computed(() => activeFile.value.files.slice(0, 5));
const personalizationRows = computed<FieldRows>(() => [
  ['在线状态', presenceOptions.find((item) => item.value === shell.presenceStatus)?.label ?? shell.presenceStatus],
  ['强调色', accentColorOptions.find((item) => item.value === shell.accentColor)?.label ?? shell.accentColor],
  ['字号', fontScaleOptions.find((item) => item.value === shell.fontScale)?.label ?? shell.fontScale],
  ['气泡样式', bubbleStyleOptions.find((item) => item.value === shell.bubbleStyle)?.label ?? shell.bubbleStyle],
]);
const notificationRows = computed<FieldRows>(() => [
  ['声音提醒', shell.notifSound ? '开启' : '关闭'],
  ['桌面通知', shell.notifDesktop ? '开启' : '关闭'],
  ['仅 @ 我提醒', shell.notifMentionOnly ? '开启' : '关闭'],
  ['未读角标', shell.notifUnreadBadge ? '开启' : '关闭'],
]);
const colleagues = computed(() => session.colleagues);
const colleaguesLoading = computed(() => session.profileLoading);
const colleaguesError = computed(() => session.profileError);

void hydrateUserPreferences();
void refreshCurrentUserAvatarUrl();

async function hydrateUserPreferences() {
  try {
    const prefs = await employeeApi.me.preferences();
    shell.hydratePreferencesFromServer(prefs);
  } catch {
    return;
  } finally {
    signatureDraft.value = shell.signature;
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

function backToList() {
  shell.setMobileView('list');
}

function selectSettingsItem(id: string) {
  const canonical = normalizeSettingsPanel(id);
  shell.selectItem('settings', canonical);
  void router.push({ name: 'employee-settings-detail', params: { panel: canonical } });
}

function goPersonalFiles() {
  shell.selectItem('files', 'personal');
  shell.setSection('files');
  void router.push({ name: 'employee-files' });
}

async function submitMobileUpdate() {
  const mobile = mobileDraft.value.trim();
  if (mobileSubmitting.value) return;
  mobileUpdateNotice.value = '';
  mobileUpdateError.value = '';
  mobileSubmitting.value = true;
  try {
    await employeeApi.me.updateProfile({ mobile: mobile || null });
    await session.loadCurrentUser();
    await session.loadOrgProfile();
    syncShellSessionProfile(shell, session);
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
    bindEmailNotice.value = '验证码已发送，60 秒后可再次发送';
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
    syncShellSessionProfile(shell, session);
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
    passwordChangeNotice.value = '验证码已发送至当前邮箱，60 秒后可再次发送';
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
    passwordChangeNotice.value = '密码已更新，下次登录请使用新密码';
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
    await employeeApi.me.updateProfile({ avatarAttachmentRefId: attachment.attachmentRefId });
    await session.loadOrgProfile();
    syncShellSessionProfile(shell, session);
    await refreshCurrentUserAvatarUrl();
    showToast('头像已更新');
  } catch (error) {
    avatarUpdateError.value = error instanceof Error ? error.message : '头像更新失败';
    showToast(avatarUpdateError.value);
  } finally {
    avatarUploading.value = false;
  }
}

async function saveSignature() {
  if (signatureSaving.value) return;
  signatureSaving.value = true;
  signatureSaveError.value = '';
  signatureSaveNotice.value = '';
  const signature = signatureDraft.value.trim().slice(0, 140);
  try {
    shell.setSignature(signature);
    await pushPreferencesNow({ signature });
    signatureDraft.value = shell.signature;
    signatureSaveNotice.value = '签名已保存';
    showToast(signatureSaveNotice.value);
  } catch (error) {
    signatureSaveError.value = error instanceof Error ? error.message : '签名保存失败';
    showToast(signatureSaveError.value);
  } finally {
    signatureSaving.value = false;
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

watch(() => shell.signature, (signature) => {
  if (!signatureSaving.value) signatureDraft.value = signature;
}, { immediate: true });

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
        <div class="workspace-meta-line">
          <span>{{ currentUserName }}</span>
          <span v-if="session.departmentName">{{ session.departmentName }}</span>
        </div>
      </div>
    </div>
  </header>

  <section class="content-stack settings-dashboard">
    <section v-if="selectedProfileId === 'profile'" class="personal-center">
      <article class="personal-hero">
        <button
          class="personal-avatar"
          type="button"
          :aria-label="avatarUploading ? '头像上传中' : '更换头像'"
          :disabled="avatarUploading"
          @click="openAvatarPicker"
        >
          <AppAvatar :label="currentUserShortName" :image="shell.avatarUrl" tone="primary" size="lg" status="online" />
          <span class="personal-avatar-overlay">
            <Camera :size="14" />
          </span>
        </button>
        <input
          ref="avatarPickerInput"
          type="file"
          accept="image/*"
          hidden
          @change="handleAvatarSelected"
        />
        <div class="personal-hero-copy">
          <h3>{{ currentUserName }}</h3>
          <p v-if="shell.signature" class="personal-signature">{{ shell.signature }}</p>
          <div class="personal-hero-meta">
            <span>{{ session.positionName || '未设置岗位' }}</span>
            <span v-if="session.departmentName">{{ session.departmentName }}</span>
            <span class="personal-hero-status">{{ profileStatusLabel }}</span>
          </div>
          <dl class="personal-hero-grid">
            <div>
              <dt>工号</dt>
              <dd>{{ displayField(session.profile?.employeeNo) }}</dd>
            </div>
            <div>
              <dt>登录名</dt>
              <dd>{{ displayField(session.user?.loginName) }}</dd>
            </div>
            <div>
              <dt>手机号</dt>
              <dd>
                {{ displayField(session.profile?.mobile) }}
                <button class="field-icon-button" type="button" aria-label="修改手机号" @click="openMobileReset">
                  <PencilLine :size="13" />
                </button>
              </dd>
            </div>
            <div>
              <dt>邮箱</dt>
              <dd>{{ displayField(currentEmail) }}</dd>
            </div>
          </dl>
          <p v-if="avatarUpdateError" class="dialog-error">{{ avatarUpdateError }}</p>
        </div>
      </article>

      <nav class="personal-nav" aria-label="个人中心快捷">
        <button type="button" class="personal-nav-item" @click="selectSettingsItem('organization')">
          <span class="personal-nav-icon"><UsersRound :size="18" /></span>
          <div>
            <strong>组织关系</strong>
            <span>{{ session.departmentName || '未分配部门' }} · 同事 / 上下级</span>
          </div>
          <ChevronRight :size="16" />
        </button>
        <button type="button" class="personal-nav-item" @click="selectSettingsItem('security')">
          <span class="personal-nav-icon"><ShieldCheck :size="18" /></span>
          <div>
            <strong>账号与安全</strong>
            <span>手机号 · 邮箱 · 密码</span>
          </div>
          <ChevronRight :size="16" />
        </button>
        <button type="button" class="personal-nav-item" @click="selectSettingsItem('preferences')">
          <span class="personal-nav-icon"><SlidersHorizontal :size="18" /></span>
          <div>
            <strong>偏好设置</strong>
            <span>状态 · 通知 · 外观</span>
          </div>
          <ChevronRight :size="16" />
        </button>
        <button type="button" class="personal-nav-item" @click="selectSettingsItem('storage')">
          <span class="personal-nav-icon"><UploadCloud :size="18" /></span>
          <div>
            <strong>文件空间</strong>
            <span>{{ activeProfile.id === 'storage' ? '当前' : shell.activeFile.subtitle || '个人文件' }}</span>
          </div>
          <ChevronRight :size="16" />
        </button>
      </nav>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>身份资料</h3>
        </header>
        <dl class="settings-list two-column">
          <div v-for="row in profileIdentityRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'organization'" class="settings-card-grid">
      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>所在部门</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in organizationRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>

      <article v-if="relationTree.managerName || relationTree.subordinates.length" class="settings-card">
        <header class="settings-card-head">
          <h3>上下级</h3>
        </header>
        <dl class="settings-list compact">
          <div>
            <dt>直属上级</dt>
            <dd>{{ relationTree.managerName || '暂无' }}</dd>
          </div>
          <div>
            <dt>直属下级</dt>
            <dd>{{ relationTree.subordinates.length ? `${relationTree.subordinates.length} 人` : '暂无' }}</dd>
          </div>
        </dl>
        <div v-if="relationTree.subordinates.length" class="settings-member-list">
          <span v-for="user in relationTree.subordinates" :key="user.userId">
            {{ user.realName }}
          </span>
        </div>
      </article>

      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>同部门同事 <small v-if="colleagues.length">（{{ colleagues.length }} 人）</small></h3>
        </header>
        <p v-if="colleaguesError" class="dialog-error">{{ colleaguesError }}</p>
        <AppEmptyState v-else-if="!colleaguesLoading && !colleagues.length" title="暂无同事" description="当前部门只有你一个人" />
        <p v-else-if="colleaguesLoading" class="form-hint">加载中…</p>
        <div v-else class="colleague-list">
          <div v-for="user in colleagues" :key="user.userId" class="colleague-row">
            <AppAvatar :label="(user.realName || '').slice(0, 2)" tone="primary" size="sm" />
            <div>
              <strong>{{ user.realName }}</strong>
              <small>{{ user.positionName || '未设置岗位' }}<span v-if="user.managerName"> · 直属上级 {{ user.managerName }}</span></small>
            </div>
          </div>
        </div>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'security'" class="settings-card-grid">
      <article class="settings-card">
        <header class="settings-card-head">
          <h3>账号概览</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in accountSecurityRows" :key="row[0]">
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

      <article class="settings-card">
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
          <label class="security-field">
            <span>{{ currentEmail ? '新邮箱' : '邮箱地址' }}</span>
            <AppInput v-model="bindEmail" type="email" placeholder="邮箱地址" />
          </label>
          <label v-if="!currentEmail || bindEmailEditing" class="security-field">
            <span>验证码</span>
            <AppInput v-model="bindEmailCode" placeholder="验证码" />
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

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>修改密码</h3>
        </header>
        <div class="security-form">
          <p v-if="!currentEmail" class="form-hint">请先在上方绑定邮箱以接收验证码。</p>
          <label class="security-field">
            <span>邮箱</span>
            <AppInput :model-value="currentEmail" disabled placeholder="未绑定邮箱" />
          </label>
          <label class="security-field">
            <span>验证码</span>
            <AppInput v-model="passwordChangeForm.code" placeholder="邮箱验证码" />
          </label>
          <label class="security-field">
            <span>新密码</span>
            <AppInput v-model="passwordChangeForm.newPassword" type="password" placeholder="8-64 位新密码" />
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
    </section>

    <section v-else-if="selectedProfileId === 'preferences'" class="settings-card-grid">
      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>个人状态</h3>
        </header>
        <div class="signature-editor">
          <label class="security-field">
            <span>个性签名</span>
            <textarea
              v-model="signatureDraft"
              class="ui-textarea compact-textarea"
              maxlength="140"
              rows="3"
            />
          </label>
          <div class="settings-control-row">
            <AppButton variant="primary" size="sm" :disabled="signatureSaving" @click="saveSignature">
              {{ signatureSaving ? '保存中' : '保存签名' }}
            </AppButton>
            <span class="settings-inline-count">{{ signatureDraft.length }}/140</span>
          </div>
          <p v-if="signatureSaveNotice" class="form-result">{{ signatureSaveNotice }}</p>
          <p v-if="signatureSaveError" class="dialog-error">{{ signatureSaveError }}</p>
        </div>
        <div class="settings-density-row">
          <button
            v-for="item in presenceOptions"
            :key="item.value"
            type="button"
            :class="{ active: shell.presenceStatus === item.value }"
            @click="updatePresenceStatus(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>显示设置</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in preferenceRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
        <div class="settings-control-row">
          <AppButton variant="secondary" size="sm" @click="shell.toggleTheme()">
            <component :is="shell.theme === 'light' ? MoonStar : SunMedium" :size="14" />
            切换到{{ shell.theme === 'light' ? '深色' : '浅色' }}
          </AppButton>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>会话显示</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in personalizationRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
        <div class="settings-density-row">
          <button
            type="button"
            :class="{ active: shell.messageDensity === 'cozy' }"
            @click="shell.setMessageDensity('cozy')"
          >
            宽松
          </button>
          <button
            type="button"
            :class="{ active: shell.messageDensity === 'compact' }"
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
            @click="updateFontScale(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
        <div class="settings-density-row">
          <button
            v-for="item in bubbleStyleOptions"
            :key="item.value"
            type="button"
            :class="{ active: shell.bubbleStyle === item.value }"
            @click="updateBubbleStyle(item.value)"
          >
            {{ item.label }}
          </button>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>强调色</h3>
        </header>
        <div class="accent-swatch-grid">
          <button
            v-for="item in accentColorOptions"
            :key="item.value"
            type="button"
            :class="['accent-swatch', `accent-${item.value}`, { active: shell.accentColor === item.value }]"
            :aria-label="item.label"
            @click="updateAccentColor(item.value)"
          >
            <span />
            <strong>{{ item.label }}</strong>
          </button>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>通知提醒</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in notificationRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
        <div class="settings-toggle-list">
          <label>
            <input
              type="checkbox"
              :checked="shell.notifSound"
              @change="updateNotificationPreference('notifSound', ($event.target as HTMLInputElement).checked)"
            />
            <span>声音提醒</span>
          </label>
          <label>
            <input
              type="checkbox"
              :checked="shell.notifDesktop"
              @change="updateNotificationPreference('notifDesktop', ($event.target as HTMLInputElement).checked)"
            />
            <span>桌面通知</span>
          </label>
          <label>
            <input
              type="checkbox"
              :checked="shell.notifMentionOnly"
              @change="updateNotificationPreference('notifMentionOnly', ($event.target as HTMLInputElement).checked)"
            />
            <span>仅 @ 我提醒</span>
          </label>
          <label>
            <input
              type="checkbox"
              :checked="shell.notifUnreadBadge"
              @change="updateNotificationPreference('notifUnreadBadge', ($event.target as HTMLInputElement).checked)"
            />
            <span>未读角标</span>
          </label>
        </div>
      </article>
    </section>

    <section v-else-if="selectedProfileId === 'storage'" class="settings-card-grid">
      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>文件空间</h3>
          <AppButton variant="secondary" size="sm" @click="goPersonalFiles">
            打开文档
          </AppButton>
        </header>
        <div class="settings-storage-layout">
          <div class="settings-storage-chart" :style="{ '--storage-level': `${shell.activeFile.usage}%` }">
            <strong>{{ shell.activeFile.usage }}%</strong>
            <span>已用</span>
          </div>
          <dl class="settings-list">
            <div v-for="row in storageRows" :key="row[0]">
              <dt>{{ row[0] }}</dt>
              <dd>{{ row[1] }}</dd>
            </div>
          </dl>
        </div>
      </article>

      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>最近文件</h3>
          <AppButton variant="primary" size="sm" @click="goPersonalFiles">
            管理文件
          </AppButton>
        </header>
        <AppEmptyState v-if="!recentFileRows.length" title="暂无文件" description="暂无文件" />
        <div v-else class="settings-file-list">
          <div v-for="file in recentFileRows" :key="file.id">
            <strong>{{ file.name }}</strong>
            <span>{{ file.size }} · {{ file.status }} · {{ file.createdAt }}</span>
          </div>
        </div>
      </article>
    </section>
  </section>
</div>
</template>
