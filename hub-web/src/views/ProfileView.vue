<script setup lang="ts">
/**
 * 个人中心：对齐画布「员工端-个人中心-桌面端1280」。
 * 身份摘要（含更换头像）→ 个人资料 → 个性化 → 账号安全。
 */
import { computed, onMounted, ref } from 'vue';
import { Clock, LogOut, Moon, SunMedium, UserRound } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/session';
import HubAvatar from '@/components/base/HubAvatar.vue';
import HubToast from '@/components/base/HubToast.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import { useToast } from '@/composables/useToast';

const session = useSessionStore();
const router = useRouter();
const { message: toastMessage, show: showToast } = useToast();
const theme = ref<'light' | 'dark'>(document.body.dataset.theme === 'dark' ? 'dark' : 'light');
const dialog = ref<'' | 'contact' | 'password' | 'logout'>('');
const contactForm = ref({ mobile: '', email: '' });
const passwordForm = ref({ current: '', next: '', confirm: '' });

function openContact() {
  contactForm.value = { mobile: '', email: session.user?.email || '' };
  dialog.value = 'contact';
}
function openPassword() {
  passwordForm.value = { current: '', next: '', confirm: '' };
  dialog.value = 'password';
}
function submitContact() {
  showToast('保存联系方式即将上线');
  dialog.value = '';
}
function submitPassword() {
  if (!passwordForm.value.current || !passwordForm.value.next) { showToast('请填写完整'); return; }
  if (passwordForm.value.next !== passwordForm.value.confirm) { showToast('两次输入的密码不一致'); return; }
  showToast('密码修改即将上线');
  dialog.value = '';
}

onMounted(() => {
  if (!session.profile) void session.loadOrgProfile();
});

const displayName = computed(() => session.displayName || '同事');
const positionName = computed(() => session.profile?.positionName || '未设置职位');
const departmentName = computed(() => session.profile?.primaryDeptName || '未分配部门');
const email = computed(() => session.user?.email || '未绑定邮箱');

function toggleTheme() {
  theme.value = theme.value === 'light' ? 'dark' : 'light';
  document.body.dataset.theme = theme.value;
  try { localStorage.setItem('hw-theme', theme.value); } catch { /* 隐私模式不阻断 */ }
}

async function logout() {
  await session.logout();
  await router.replace({ name: 'login' });
}
</script>

<template>
  <div class="pc-page">
    <header class="pc-head">
      <div><h1>个人中心</h1><p>管理个人资料、头像与账号安全</p></div>
    </header>

    <section class="pc-card pc-identity">
      <HubAvatar :label="displayName" size="xl" />
      <div class="pc-identity-copy">
        <strong>{{ displayName }}</strong>
        <span>{{ positionName }} · {{ departmentName }}</span>
        <small>{{ session.user?.loginName || '' }}</small>
      </div>
      <button type="button" class="pc-btn-secondary" @click="showToast('头像上传即将上线')">
        <UserRound :size="16" :stroke-width="2" />更换头像
      </button>
    </section>

    <section class="pc-card">
      <div class="pc-card-head"><h2>个人资料</h2><span class="pc-card-note">组织信息由管理员维护</span></div>
      <div class="pc-fields">
        <div class="pc-field"><small>账号</small><span class="pc-field-readonly">{{ session.user?.loginName || '—' }}</span></div>
        <div class="pc-field"><small>部门 / 职位</small><span class="pc-field-readonly">{{ departmentName }} / {{ positionName }}</span></div>
        <div class="pc-field">
          <small>邮箱</small>
          <span class="pc-field-value">{{ email }}
            <button type="button" class="pc-link" @click="openContact">修改</button>
          </span>
        </div>
      </div>
    </section>

    <section class="pc-card">
      <div class="pc-card-head"><h2>个性化</h2><span class="pc-card-note">不影响组织档案</span></div>
      <div class="pc-fields">
        <div class="pc-field">
          <small>显示名称</small>
          <span class="pc-field-value">{{ displayName }}
            <button type="button" class="pc-link" @click="showToast('显示名称修改即将上线')">修改</button>
          </span>
        </div>
        <div class="pc-field">
          <small>外观主题</small>
          <span class="pc-field-value">{{ theme === 'light' ? '浅色' : '深色' }}
            <button type="button" class="pc-link" @click="toggleTheme">
              <SunMedium v-if="theme === 'dark'" :size="14" :stroke-width="2" /><Moon v-else :size="14" :stroke-width="2" />切换
            </button>
          </span>
        </div>
      </div>
    </section>

    <section class="pc-card">
      <div class="pc-card-head"><h2>账号安全</h2><span class="pc-card-note">管理登录密码与本机登录状态</span></div>
      <div class="pc-rows">
        <div class="pc-row">
          <span class="pc-row-icon"><Clock :size="18" :stroke-width="2" /></span>
          <div class="pc-row-copy"><strong>登录密码</strong><small>当前登录保持有效</small></div>
          <button type="button" class="pc-btn-secondary" @click="openPassword">修改密码</button>
        </div>
        <div class="pc-divider" />
        <div class="pc-row">
          <span class="pc-row-icon danger"><LogOut :size="18" :stroke-width="2" /></span>
          <div class="pc-row-copy"><strong class="danger">退出当前账号</strong><small>退出后保留本机消息缓存</small></div>
          <button type="button" class="pc-btn-danger" @click="dialog = 'logout'">退出登录</button>
        </div>
      </div>
    </section>
    <HubToast :message="toastMessage" />

    <HubDialog v-if="dialog === 'contact'" @close="dialog = ''">
      <div class="pc-dialog">
        <h2>修改联系方式</h2>
        <label class="pc-dialog-field"><span>手机号</span><input v-model="contactForm.mobile" type="text" placeholder="请输入手机号"></label>
        <label class="pc-dialog-field"><span>邮箱</span><input v-model="contactForm.email" type="email" placeholder="请输入邮箱"></label>
        <p class="pc-dialog-note">联系方式仅用于账号通知与组织协作</p>
        <div class="pc-dialog-actions">
          <button type="button" class="pc-btn-secondary" @click="dialog = ''">取消</button>
          <button type="button" class="pc-btn-primary" @click="submitContact">保存</button>
        </div>
      </div>
    </HubDialog>

    <HubDialog v-if="dialog === 'password'" @close="dialog = ''">
      <div class="pc-dialog">
        <h2>修改密码</h2>
        <label class="pc-dialog-field"><span>当前密码</span><input v-model="passwordForm.current" type="password" placeholder="请输入当前密码"></label>
        <label class="pc-dialog-field"><span>新密码</span><input v-model="passwordForm.next" type="password" placeholder="至少 8 位，包含字母和数字"></label>
        <label class="pc-dialog-field"><span>确认新密码</span><input v-model="passwordForm.confirm" type="password" placeholder="请再次输入新密码"></label>
        <div class="pc-dialog-actions">
          <button type="button" class="pc-btn-secondary" @click="dialog = ''">取消</button>
          <button type="button" class="pc-btn-primary" @click="submitPassword">确认修改</button>
        </div>
      </div>
    </HubDialog>

    <HubDialog v-if="dialog === 'logout'" @close="dialog = ''">
      <div class="pc-dialog pc-dialog-confirm">
        <h2>确认退出登录？</h2>
        <p>退出后需要重新验证身份。本机消息缓存将保留。</p>
        <div class="pc-dialog-actions">
          <button type="button" class="pc-btn-secondary" @click="dialog = ''">取消</button>
          <button type="button" class="pc-btn-danger" @click="logout">退出登录</button>
        </div>
      </div>
    </HubDialog>
  </div>
</template>

<style scoped src="../styles/profile.css" />
