<script setup lang="ts">
import {
  BadgeCheck,
  Bell,
  CalendarDays,
  Camera,
  ChevronLeft,
  ChevronRight,
  KeyRound,
  LogOut,
  Mail,
  MoonStar,
  Palette,
  Phone,
  ShieldCheck,
  SunMedium,
  UsersRound,
  Zap,
} from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
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

// ---------- 个人中心头卡与统计卡数据 ----------
const hireDays = computed(() => {
  const hireDate = session.profile?.hireDate;
  if (!hireDate) return null;
  const hire = new Date(hireDate);
  if (Number.isNaN(hire.getTime())) return null;
  const days = Math.floor((Date.now() - hire.getTime()) / 86400000);
  return days >= 0 ? days : null;
});

// 本月出勤天数（正常/异常均算出勤）
const monthAttendanceDays = ref<number | null>(null);
// 假期余额（可用分钟数转天）
const leaveBalanceLabel = ref('—');
// 待办未完成数
const pendingTodoCount = computed(() => shell.todos.filter((t) => !t.completed).length);

async function loadPersonalStats() {
  if (!session.isAuthenticated) return;
  try {
    const now = new Date();
    const from = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
    const to = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()).padStart(2, '0')}`;
    const [dailyResults, leaveAccounts] = await Promise.all([
      employeeApi.attendance.dailyResults({ startDate: from, endDate: to }),
      employeeApi.leave.accounts(),
    ]);
    monthAttendanceDays.value = dailyResults.filter((r) => r.resultStatus === 'NORMAL' || r.workMinutes).length;
    const totalAvailable = leaveAccounts
      .filter((a) => a.status === 'ACTIVE')
      .reduce((sum, a) => sum + Math.max(0, a.availableMinutes ?? 0), 0);
    leaveBalanceLabel.value = totalAvailable > 0 ? `${(totalAvailable / 480).toFixed(1)} 天` : '0 天';
  } catch {
    // 统计失败不阻塞页面，保持占位符
  }
}

onMounted(() => {
  void loadPersonalStats();
});
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
  } catch (error) {
    showToast(error instanceof Error ? error.message : '退出登录失败');
  } finally {
    window.location.reload();
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
      <!-- App 式头卡：头像 + 姓名 + 岗位 + 组织路径 + 入职天数 -->
      <article class="personal-hero-card">
        <div class="personal-hero-main">
          <AppAvatar :label="currentUserShortName" :image="shell.avatarUrl" tone="primary" size="xl" status="online" />
          <div class="personal-hero-copy">
            <h3>{{ currentUserName }}</h3>
            <p>
              <span v-if="session.departmentName">{{ session.departmentName }}</span>
              <span v-if="session.positionName"> · {{ session.positionName }}</span>
            </p>
            <small v-if="hireDays !== null">入职第 {{ hireDays + 1 }} 天 · {{ profileStatusLabel }}</small>
          </div>
        </div>
        <input
          ref="avatarPickerInput"
          type="file"
          accept="image/*"
          hidden
          @change="handleAvatarSelected"
        />
        <div class="personal-hero-actions">
          <AppButton variant="secondary" size="sm" :disabled="avatarUploading" @click="openAvatarPicker">
            <Camera :size="15" />
            {{ avatarUploading ? '上传中' : '更换头像' }}
          </AppButton>
        </div>
        <p v-if="avatarUpdateError" class="dialog-error personal-center-error">{{ avatarUpdateError }}</p>
      </article>

      <!-- 三张统计小卡：本月出勤 / 假期余额 / 待办 -->
      <section class="personal-stat-strip" aria-label="个人统计">
        <article class="personal-stat-card">
          <small>本月出勤</small>
          <strong>{{ monthAttendanceDays === null ? '—' : `${monthAttendanceDays} 天` }}</strong>
          <span>工作日打卡记录</span>
        </article>
        <article class="personal-stat-card">
          <small>假期余额</small>
          <strong>{{ leaveBalanceLabel }}</strong>
          <span>可用假期</span>
        </article>
        <article class="personal-stat-card">
          <small>待办</small>
          <strong>{{ pendingTodoCount }} 项</strong>
          <span>未完成事项</span>
        </article>
      </section>

      <!-- App 式分组列表 -->
      <div class="settings-group-list">
        <!-- 身份信息组 -->
        <section class="settings-group">
          <header class="settings-group-title">身份信息</header>
          <div class="settings-group-body">
            <div class="settings-row">
              <span class="settings-row-icon tone-primary"><BadgeCheck :size="16" /></span>
              <span class="settings-row-label">任职状态</span>
              <span class="settings-row-value">{{ profileStatusLabel }}</span>
            </div>
            <div class="settings-row">
              <span class="settings-row-icon tone-info"><CalendarDays :size="16" /></span>
              <span class="settings-row-label">入职日期</span>
              <span class="settings-row-value">{{ displayField(session.profile?.hireDate) }}</span>
            </div>
            <div class="settings-row">
              <span class="settings-row-icon tone-warning"><BadgeCheck :size="16" /></span>
              <span class="settings-row-label">岗位</span>
              <span class="settings-row-value">{{ displayField(session.profile?.positionName) }}</span>
            </div>
          </div>
        </section>

        <!-- 组织归属组 -->
        <section class="settings-group">
          <header class="settings-group-title">组织归属</header>
          <div class="settings-group-body">
            <div class="settings-row">
              <span class="settings-row-icon tone-success"><UsersRound :size="16" /></span>
              <span class="settings-row-label">所属部门</span>
              <span class="settings-row-value">{{ displayField(session.departmentName) }}</span>
            </div>
            <div class="settings-row">
              <span class="settings-row-icon tone-primary"><UsersRound :size="16" /></span>
              <span class="settings-row-label">直属上级</span>
              <span class="settings-row-value">{{ displayField(relationTree.managerName || session.profile?.managerName) }}</span>
            </div>
            <button type="button" class="settings-row settings-row-link" @click="selectSettingsItem('organization')">
              <span class="settings-row-icon tone-info"><UsersRound :size="16" /></span>
              <span class="settings-row-label">组织关系</span>
              <span class="settings-row-value">{{ relationTree.subordinates.length }} 名下级</span>
              <span class="settings-row-chevron">›</span>
            </button>
          </div>
        </section>

        <!-- 账号安全组 -->
        <section class="settings-group">
          <header class="settings-group-title">账号安全</header>
          <div class="settings-group-body">
            <button type="button" class="settings-row settings-row-link" @click="openMobileReset">
              <span class="settings-row-icon tone-primary"><Phone :size="16" /></span>
              <span class="settings-row-label">手机号</span>
              <span class="settings-row-value">{{ displayField(session.profile?.mobile) }}</span>
              <span class="settings-row-chevron">›</span>
            </button>
            <button type="button" class="settings-row settings-row-link" @click="openEmailBinding">
              <span class="settings-row-icon tone-success"><Mail :size="16" /></span>
              <span class="settings-row-label">邮箱</span>
              <span class="settings-row-value">{{ currentEmail ? '已绑定' : '未绑定' }}</span>
              <span class="settings-row-chevron">›</span>
            </button>
            <button type="button" class="settings-row settings-row-link" @click="openPasswordChange">
              <span class="settings-row-icon tone-warning"><KeyRound :size="16" /></span>
              <span class="settings-row-label">修改密码</span>
              <span class="settings-row-value"></span>
              <span class="settings-row-chevron">›</span>
            </button>
          </div>
        </section>

        <!-- 偏好设置组 -->
        <section class="settings-group">
          <header class="settings-group-title">偏好设置</header>
          <div class="settings-group-body">
            <button type="button" class="settings-row settings-row-link" @click="selectSettingsItem('preferences')">
              <span class="settings-row-icon tone-primary"><Palette :size="16" /></span>
              <span class="settings-row-label">主题</span>
              <span class="settings-row-value">{{ themeLabel }}</span>
              <span class="settings-row-chevron">›</span>
            </button>
            <button type="button" class="settings-row settings-row-link" @click="selectSettingsItem('preferences')">
              <span class="settings-row-icon tone-info"><Bell :size="16" /></span>
              <span class="settings-row-label">消息密度</span>
              <span class="settings-row-value">{{ messageDensityLabel }}</span>
              <span class="settings-row-chevron">›</span>
            </button>
            <button type="button" class="settings-row settings-row-link" @click="selectSettingsItem('preferences')">
              <span class="settings-row-icon tone-success"><Zap :size="16" /></span>
              <span class="settings-row-label">在线状态</span>
              <span class="settings-row-value">{{ presenceStatusLabel }}</span>
              <span class="settings-row-chevron">›</span>
            </button>
          </div>
        </section>
      </div>
    </section>

    <section v-else-if="selectedProfileId === 'organization'" class="settings-group-list">
      <section class="settings-group">
        <header class="settings-group-title">组织信息</header>
        <div class="settings-group-body">
          <div class="settings-row" v-for="row in organizationRows" :key="row[0]">
            <span class="settings-row-icon tone-primary"><UsersRound :size="16" /></span>
            <span class="settings-row-label">{{ row[0] }}</span>
            <span class="settings-row-value">{{ row[1] }}</span>
          </div>
        </div>
      </section>

      <section class="settings-group">
        <header class="settings-group-title">组织链路</header>
        <div class="settings-group-body settings-group-padded">
          <div class="settings-org-chart compact-org-chart">
            <div class="settings-org-level">
              <span class="settings-org-node department-node">{{ relationTree.department }}</span>
            </div>
            <div class="settings-org-level">
              <span class="settings-org-node">{{ relationTree.managerName || '暂无上级' }}</span>
              <span class="settings-org-node current-user-node">{{ relationTree.ownName }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="settings-group">
        <header class="settings-group-title">直属下级</header>
        <div class="settings-group-body settings-group-padded">
          <p v-if="colleaguesError" class="dialog-error">{{ colleaguesError }}</p>
          <p v-else-if="colleaguesLoading" class="form-hint">加载中</p>
          <div v-else-if="relationTree.subordinates.length" class="settings-member-list compact-member-list">
            <span v-for="user in relationTree.subordinates" :key="user.userId">
              {{ user.realName }}
            </span>
          </div>
          <p v-else class="empty-inline">暂无下属</p>
        </div>
      </section>
    </section>

    <section v-else-if="selectedProfileId === 'security'" class="settings-group-list">
      <!-- 账号信息组 -->
      <section class="settings-group">
        <header class="settings-group-title">账号信息</header>
        <div class="settings-group-body">
          <div class="settings-row">
            <span class="settings-row-icon tone-primary"><BadgeCheck :size="16" /></span>
            <span class="settings-row-label">登录名</span>
            <span class="settings-row-value">{{ displayField(session.user?.loginName) }}</span>
          </div>
          <div class="settings-row">
            <span class="settings-row-icon tone-info"><Phone :size="16" /></span>
            <span class="settings-row-label">手机号</span>
            <span class="settings-row-value">{{ displayField(session.profile?.mobile) }}</span>
          </div>
          <div class="settings-row">
            <span class="settings-row-icon tone-success"><Mail :size="16" /></span>
            <span class="settings-row-label">绑定邮箱</span>
            <span class="settings-row-value">{{ currentEmail || '未绑定' }}</span>
          </div>
          <div class="settings-row">
            <span class="settings-row-icon tone-warning"><ShieldCheck :size="16" /></span>
            <span class="settings-row-label">账号状态</span>
            <span class="settings-row-value">{{ session.user ? '正常' : '未登录' }}</span>
          </div>
        </div>
      </section>

      <!-- 手机号组 -->
      <section class="settings-group">
        <header class="settings-group-title">手机号</header>
        <div class="settings-group-body settings-group-padded">
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
        </div>
      </section>

      <!-- 邮箱绑定组 -->
      <section class="settings-group">
        <header class="settings-group-title">邮箱绑定</header>
        <div class="settings-group-body settings-group-padded">
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
        </div>
      </section>

      <!-- 修改密码组 -->
      <section ref="passwordCard" class="settings-group">
        <header class="settings-group-title">修改密码</header>
        <div class="settings-group-body settings-group-padded">
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
        </div>
      </section>

      <!-- 退出登录组 -->
      <section class="settings-group">
        <div class="settings-group-body">
          <button type="button" class="settings-row settings-row-link settings-row-danger" :disabled="logoutSubmitting" @click="handleLogout">
            <span class="settings-row-icon tone-danger"><LogOut :size="16" /></span>
            <span class="settings-row-label">{{ logoutSubmitting ? '退出中' : '退出登录' }}</span>
            <span class="settings-row-chevron">›</span>
          </button>
        </div>
      </section>
    </section>

    <section v-else-if="selectedProfileId === 'preferences'" class="settings-group-list">
      <!-- 工作状态组 -->
      <section class="settings-group">
        <header class="settings-group-title">工作状态</header>
        <div class="settings-group-body settings-group-padded">
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
        </div>
      </section>

      <!-- 界面设置组 -->
      <section class="settings-group">
        <header class="settings-group-title">界面设置</header>
        <div class="settings-group-body">
          <div class="settings-row">
            <span class="settings-row-icon tone-primary"><Palette :size="16" /></span>
            <span class="settings-row-label">主题</span>
            <span class="settings-row-value">
              <AppButton variant="secondary" size="sm" :disabled="preferenceDisabled" @click="shell.toggleTheme()">
                <component :is="shell.theme === 'light' ? MoonStar : SunMedium" :size="14" />
                {{ themeLabel }}
              </AppButton>
            </span>
          </div>
          <div class="settings-row settings-row-stacked">
            <span class="settings-row-icon tone-info"><Bell :size="16" /></span>
            <span class="settings-row-label">消息密度</span>
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
          </div>
          <div class="settings-row settings-row-stacked">
            <span class="settings-row-icon tone-warning"><Zap :size="16" /></span>
            <span class="settings-row-label">字号</span>
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
          </div>
        </div>
      </section>

      <!-- 消息样式组 -->
      <section class="settings-group">
        <header class="settings-group-title">消息样式</header>
        <div class="settings-group-body">
          <div class="settings-row settings-row-stacked">
            <span class="settings-row-icon tone-success"><Mail :size="16" /></span>
            <span class="settings-row-label">气泡样式</span>
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
          </div>
          <div class="settings-row settings-row-stacked">
            <span class="settings-row-icon tone-primary"><Palette :size="16" /></span>
            <span class="settings-row-label">主题色</span>
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
          </div>
        </div>
      </section>

      <!-- 通知设置组 -->
      <section class="settings-group">
        <header class="settings-group-title">通知设置</header>
        <div class="settings-group-body">
          <label class="settings-row settings-row-toggle">
            <span class="settings-row-icon tone-primary"><Bell :size="16" /></span>
            <span class="settings-row-label">声音提醒</span>
            <input
              type="checkbox"
              :checked="shell.notifSound"
              :disabled="preferenceDisabled"
              @change="updateNotificationPreference('notifSound', ($event.target as HTMLInputElement).checked)"
            />
          </label>
          <label class="settings-row settings-row-toggle">
            <span class="settings-row-icon tone-info"><Bell :size="16" /></span>
            <span class="settings-row-label">桌面通知</span>
            <input
              type="checkbox"
              :checked="shell.notifDesktop"
              :disabled="preferenceDisabled"
              @change="updateNotificationPreference('notifDesktop', ($event.target as HTMLInputElement).checked)"
            />
          </label>
          <label class="settings-row settings-row-toggle">
            <span class="settings-row-icon tone-warning"><Bell :size="16" /></span>
            <span class="settings-row-label">仅 @ 我提醒</span>
            <input
              type="checkbox"
              :checked="shell.notifMentionOnly"
              :disabled="preferenceDisabled"
              @change="updateNotificationPreference('notifMentionOnly', ($event.target as HTMLInputElement).checked)"
            />
          </label>
          <label class="settings-row settings-row-toggle">
            <span class="settings-row-icon tone-success"><Bell :size="16" /></span>
            <span class="settings-row-label">未读角标</span>
            <input
              type="checkbox"
              :checked="shell.notifUnreadBadge"
              :disabled="preferenceDisabled"
              @change="updateNotificationPreference('notifUnreadBadge', ($event.target as HTMLInputElement).checked)"
            />
          </label>
        </div>
      </section>
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
