<script setup lang="ts">
import {
  Camera,
  ChevronLeft,
  ChevronRight,
  MoonStar,
  ShieldCheck,
  SlidersHorizontal,
  SunMedium,
  PencilLine,
  UsersRound,
} from 'lucide-vue-next';
import { useEmployeeShellContext } from '../employee-shell-context';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppButton from '../../components/ui/AppButton.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppInput from '../../components/ui/AppInput.vue';

const {
  shell,
  session,
  bindEmail,
  bindEmailCode,
  bindEmailCodeSending,
  bindEmailSubmitting,
  bindEmailEditing,
  bindEmailNotice,
  bindEmailError,
  mobileDraft,
  mobileSubmitting,
  mobileUpdateNotice,
  mobileUpdateError,
  mobileInput,
  passwordChangeForm,
  passwordChangeCodeSending,
  passwordChangeSubmitting,
  passwordChangeNotice,
  passwordChangeError,
  avatarUploading,
  avatarUpdateError,
  avatarPickerInput,
  colleagues,
  colleaguesLoading,
  colleaguesError,
  currentUserName,
  currentUserShortName,
  currentAvatarUrl,
  selectedProfileId,
  profileStatusLabel,
  profileIdentityRows,
  accountSecurityRows,
  currentEmail,
  organizationRows,
  preferenceRows,
  activeProfile,
  relationTree,
  themeLabel,
  backToList,
  displayField,
  selectSettingsItem,
  openMobileReset,
  sendBindEmailCode,
  submitBindEmail,
  startBindEmailEditing,
  cancelBindEmailEditing,
  submitMobileUpdate,
  sendPasswordChangeCode,
  submitPasswordChange,
  openAvatarPicker,
  handleAvatarSelected,
  showToast,
} = useEmployeeShellContext();
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
          <AppAvatar :label="currentUserShortName" :image="currentAvatarUrl" tone="primary" size="lg" status="online" />
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
            <span>主题 · 会话密度（云端同步）</span>
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
            <AppButton variant="secondary" size="sm" :disabled="bindEmailCodeSending || !bindEmail.trim()" @click="sendBindEmailCode">
              {{ bindEmailCodeSending ? '发送中' : '发送验证码' }}
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
            <AppButton variant="secondary" size="sm" :disabled="!currentEmail || passwordChangeCodeSending" @click="sendPasswordChangeCode">
              {{ passwordChangeCodeSending ? '发送中' : '发送验证码' }}
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
      <p class="settings-hint">主题、密度、列表宽度会实时同步到云端，换设备登录会保持一致。本机会同步缓存一份避免离线时丢失。</p>
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
          <h3>会话密度</h3>
        </header>
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
      </article>
    </section>
  </section>
</div>
</template>
