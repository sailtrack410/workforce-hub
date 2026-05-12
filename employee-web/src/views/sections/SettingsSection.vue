<script setup lang="ts">
import {
  ChevronLeft,
  MoonStar,
  SunMedium,
  PencilLine,
} from 'lucide-vue-next';
import { useEmployeeShellContext } from '../employee-shell-context';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppButton from '../../components/ui/AppButton.vue';
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
  currentUserName,
  currentUserShortName,
  selectedProfileId,
  profileStatusLabel,
  profileIdentityRows,
  accountSecurityRows,
  currentEmail,
  organizationRows,
  settingsMetrics,
  preferenceRows,
  activeProfile,
  relationTree,
  backToList,
  displayField,
  openMobileReset,
  sendBindEmailCode,
  submitBindEmail,
  startBindEmailEditing,
  cancelBindEmailEditing,
  submitMobileUpdate,
  sendPasswordChangeCode,
  submitPasswordChange,
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
    <section v-if="selectedProfileId === 'profile'" class="settings-card-grid">
      <article class="settings-card settings-profile-card">
        <div class="settings-profile-main">
          <AppAvatar :label="currentUserShortName" tone="primary" size="lg" status="online" />
          <div>
            <h3>{{ currentUserName }}</h3>
            <span>{{ session.positionName || session.departmentName || profileStatusLabel }}</span>
          </div>
        </div>
        <dl class="settings-list compact">
          <div>
            <dt>工号</dt>
            <dd>{{ displayField(session.profile?.employeeNo) }}</dd>
          </div>
          <div>
            <dt>登录名</dt>
            <dd>{{ displayField(session.user?.loginName) }}</dd>
          </div>
          <div>
            <dt>状态</dt>
            <dd>{{ profileStatusLabel }}</dd>
          </div>
        </dl>
      </article>

      <article class="settings-card settings-wide-card">
        <header class="settings-card-head">
          <h3>身份资料</h3>
        </header>
        <dl class="settings-list two-column">
          <div class="settings-list-row is-editable">
            <dt>手机号</dt>
            <dd>
              <span>{{ displayField(session.profile?.mobile) }}</span>
              <button class="field-icon-button" type="button" aria-label="修改手机号" @click="openMobileReset">
                <PencilLine :size="14" />
              </button>
            </dd>
          </div>
          <div v-for="row in profileIdentityRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>组织关系</h3>
        </header>
        <dl class="settings-list">
          <div v-for="row in organizationRows" :key="row[0]">
            <dt>{{ row[0] }}</dt>
            <dd>{{ row[1] }}</dd>
          </div>
        </dl>
        <div v-if="relationTree.subordinates.length" class="settings-member-list">
          <span v-for="user in relationTree.subordinates" :key="user.userId">
            {{ user.realName }}
          </span>
        </div>
      </article>

      <article class="settings-card">
        <header class="settings-card-head">
          <h3>工作概览</h3>
        </header>
        <div class="settings-metric-grid">
          <div
            v-for="metric in settingsMetrics"
            :key="metric.label"
            class="settings-metric-card"
            :style="{ '--metric-level': `${metric.level}%` }"
          >
            <small>{{ metric.label }}</small>
            <strong>{{ metric.value }}</strong>
            <span>{{ metric.detail }}</span>
            <i />
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
