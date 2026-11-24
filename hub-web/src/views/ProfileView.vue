<script setup lang="ts">
/**
 * 个人中心：对齐画布「员工端-个人中心-桌面端1280」。
 * 身份摘要（含更换头像）→ 个人资料 → 个性化 → 账号安全。
 */
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { Clock, LogOut, Moon, SunMedium, UserRound } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import { useSessionStore } from '@/stores/session';
import HubAvatar from '@/components/base/HubAvatar.vue';
import HubToast from '@/components/base/HubToast.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import { useToast } from '@/composables/useToast';
import { authApi, orgApi, fileApi } from '@/api';
import { uploadEmployeeFile } from '@/composables/useUpload';
import { useEmailCode } from '@/composables/useEmailCode';
import { localMedia } from '@/services/media';
import { resolvedTheme, savePreferences } from '@/services/preferences';

const session = useSessionStore();
const router = useRouter();
const { message: toastMessage, show: showToast } = useToast();
const theme = resolvedTheme;
const dialog = ref<'' | 'contact' | 'password' | 'logout' | 'name'>('');
const contactForm = ref({ mobile: '', email: '', code: '' });
const passwordForm = ref({ current: '', next: '', confirm: '' });
const saving = ref(false), avatarInput = ref<HTMLInputElement | null>(null), avatarUrl = ref(''), nameDraft = ref('');
const emailCode = useEmailCode(() => contactForm.value.email, 'BIND');
let avatarGeneration = 0;

watch(() => session.profile?.avatarAttachmentRefId, async (attachmentId) => {
  const generation = ++avatarGeneration;
  avatarUrl.value = '';
  if (!attachmentId) return;
  try {
    const key = `avatar:${attachmentId}`;
    let image = await localMedia(key);
    if (!image) { const ticket = await fileApi.createDownloadTicket(attachmentId, 'INLINE'); image = await localMedia(key, fileApi.downloadUrl(ticket.ticketNo)); }
    if (generation === avatarGeneration) avatarUrl.value = image || '';
  } catch { /* 头像不可用时保留默认头像 */ }
}, { immediate: true });
onBeforeUnmount(() => { avatarGeneration++; });

function openContact() {
  contactForm.value = { mobile: session.profile?.mobile || '', email: session.user?.email || '', code: '' };
  dialog.value = 'contact';
}
function openPassword() {
  passwordForm.value = { current: '', next: '', confirm: '' };
  dialog.value = 'password';
}
async function submitContact() {
  if (saving.value || !session.profile) return;
  saving.value = true;
  try { session.profile = await orgApi.updateProfile({ mobile: contactForm.value.mobile.trim(), avatarAttachmentRefId: session.profile.avatarAttachmentRefId ?? null }); showToast('手机号已保存'); }
  catch (error) { showToast(error instanceof Error ? error.message : '保存失败'); }
  finally { saving.value = false; }
}
async function bindEmail() {
  if (saving.value) return;
  if (!contactForm.value.code.trim()) { showToast('请输入邮箱验证码'); return; }
  saving.value = true;
  try { await authApi.bindEmail(contactForm.value.email.trim(), contactForm.value.code.trim()); if (session.user) session.user.email = contactForm.value.email.trim(); contactForm.value.code = ''; showToast('邮箱已绑定'); }
  catch (error) { showToast(error instanceof Error ? error.message : '邮箱绑定失败'); }
  finally { saving.value = false; }
}
async function sendEmailCode() {
  try { await emailCode.send(); } catch (error) { showToast(error instanceof Error ? error.message : '验证码发送失败'); }
}
async function submitPassword() {
  if (saving.value) return;
  if (!passwordForm.value.current || !passwordForm.value.next) { showToast('请填写完整'); return; }
  if (passwordForm.value.next !== passwordForm.value.confirm) { showToast('两次输入的密码不一致'); return; }
  if (passwordForm.value.next.length < 8 || passwordForm.value.next.length > 64) { showToast('密码长度需在 8-64 之间'); return; }
  saving.value = true;
  try { await authApi.changePassword(passwordForm.value.current, passwordForm.value.next); passwordForm.value = { current: '', next: '', confirm: '' }; await logout(); }
  catch (error) { showToast(error instanceof Error ? error.message : '密码修改失败'); }
  finally { saving.value = false; }
}
async function saveName() {
  if (saving.value) return;
  saving.value = true;
  try { session.personalization = await orgApi.updatePreferences({ displayName: nameDraft.value.trim() }); dialog.value = ''; showToast('显示名称已保存'); }
  catch (error) { showToast(error instanceof Error ? error.message : '保存失败'); }
  finally { saving.value = false; }
}
async function uploadAvatar(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0];
  if (!file || saving.value || !session.profile) return;
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type) || file.size > 5 * 1024 * 1024) { showToast('请选择 5 MB 以内的 PNG、JPEG 或 WebP 图片'); input.value = ''; return; }
  saving.value = true;
  try { const result = await uploadEmployeeFile({ file, ownerType: 'USER_AVATAR' }); if (result.profile) session.profile = result.profile; showToast('头像已更新'); }
  catch (error) { showToast(error instanceof Error ? error.message : '头像上传失败'); }
  finally { saving.value = false; input.value = ''; }
}

onMounted(() => {
  if (!session.profile) void session.loadOrgProfile();
});

const displayName = computed(() => session.displayName || '同事');
const positionName = computed(() => session.profile?.positionName || '未设置职位');
const departmentName = computed(() => session.profile?.primaryDeptName || '未分配部门');
const email = computed(() => session.user?.email || '未绑定邮箱');

async function toggleTheme() {
  try { await savePreferences({ theme: theme.value === 'light' ? 'dark' : 'light' }); } catch { /* 隐私模式不阻断 */ showToast('主题保存失败'); }
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
      <HubAvatar :label="displayName" :image="avatarUrl" size="xl" />
      <div class="pc-identity-copy">
        <strong>{{ displayName }}</strong>
        <span>{{ positionName }} · {{ departmentName }}</span>
        <small>{{ session.user?.loginName || '' }}</small>
      </div>
      <input ref="avatarInput" type="file" accept="image/png,image/jpeg,image/webp" hidden @change="uploadAvatar" />
      <button type="button" class="pc-btn-secondary" :disabled="saving || !session.profile" @click="avatarInput?.click()">
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
            <button type="button" class="pc-link" @click="nameDraft = session.personalization?.displayName || ''; dialog = 'name'">修改</button>
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
          <div class="pc-row-copy"><strong>登录密码</strong><small>修改后重新登录</small></div>
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

    <HubDialog v-if="dialog === 'name'" :lock="saving" @close="dialog = ''"><form class="pc-dialog" @submit.prevent="saveName"><h2>修改显示名称</h2><label class="pc-dialog-field">显示名称<input v-model="nameDraft" maxlength="64" :disabled="saving" /></label><div class="pc-dialog-actions"><button type="button" class="pc-btn-secondary" :disabled="saving" @click="dialog = ''">取消</button><button class="pc-btn-primary" :disabled="saving">保存</button></div></form></HubDialog>
    <HubDialog v-if="dialog === 'contact'" :lock="saving" @close="dialog = ''">
      <div class="pc-dialog">
        <h2>修改联系方式</h2>
        <form class="pc-contact-form" @submit.prevent="submitContact"><label class="pc-dialog-field"><span>手机号</span><input v-model="contactForm.mobile" type="tel" maxlength="32" :disabled="saving" placeholder="请输入手机号"></label><button class="pc-btn-primary" :disabled="saving">保存手机号</button></form>
        <form class="pc-contact-form" @submit.prevent="bindEmail"><label class="pc-dialog-field"><span>邮箱</span><input v-model="contactForm.email" type="email" required :disabled="saving || emailCode.sending.value" placeholder="请输入邮箱"></label><div class="pc-code-row"><label class="pc-dialog-field"><span>验证码</span><input v-model="contactForm.code" required autocomplete="one-time-code" :disabled="saving" /></label><button type="button" class="pc-btn-secondary" :disabled="saving || emailCode.sending.value || emailCode.remaining.value > 0" @click="sendEmailCode">{{ emailCode.remaining.value ? `${emailCode.remaining.value} 秒` : '获取验证码' }}</button></div><button class="pc-btn-primary" :disabled="saving">绑定邮箱</button></form>
        <p class="pc-dialog-note">联系方式仅用于账号通知与组织协作</p>
        <div class="pc-dialog-actions">
          <button type="button" class="pc-btn-secondary" :disabled="saving" @click="dialog = ''">关闭</button>
        </div>
      </div>
    </HubDialog>

    <HubDialog v-if="dialog === 'password'" :lock="saving" @close="dialog = ''">
      <div class="pc-dialog">
        <h2>修改密码</h2>
        <label class="pc-dialog-field"><span>当前密码</span><input v-model="passwordForm.current" type="password" autocomplete="current-password" :disabled="saving" placeholder="请输入当前密码"></label>
        <label class="pc-dialog-field"><span>新密码</span><input v-model="passwordForm.next" type="password" autocomplete="new-password" maxlength="64" :disabled="saving" placeholder="8–64 位"></label>
        <label class="pc-dialog-field"><span>确认新密码</span><input v-model="passwordForm.confirm" type="password" autocomplete="new-password" :disabled="saving" placeholder="请再次输入新密码"></label>
        <div class="pc-dialog-actions">
          <button type="button" class="pc-btn-secondary" :disabled="saving" @click="dialog = ''">取消</button>
          <button type="button" class="pc-btn-primary" :disabled="saving" @click="submitPassword">确认修改</button>
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
