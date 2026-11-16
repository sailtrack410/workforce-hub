<script setup lang="ts">
/**
 * 移动端个人中心：对齐画布「员工端-个人中心-桌面端1280」结构。
 * 身份摘要 + 个人资料 + 个性化 + 账号安全。
 */
import { computed, onMounted, ref } from 'vue';
import { CalendarCheck, ChevronRight, Clock, LogOut, Moon, SunMedium, UserRound } from 'lucide-vue-next';
import HubShell from '../layouts/HubShell.vue';
import HubEmpty from '../components/HubEmpty.vue';
import HubAvatar from '../components/HubAvatar.vue';
import { useSessionStore } from '@/stores/session';
import { useShellStore } from '@/stores/shell';
import { useRouter } from 'vue-router';
const session = useSessionStore();
const shell = useShellStore();
const router = useRouter();
const toast = ref('');

function showToast(message: string) {
  toast.value = message;
  window.setTimeout(() => { toast.value = ''; }, 2500);
}

onMounted(() => {
  if (!session.initialized) {
    void session.loadCurrentUser().then(() => {
      if (session.isAuthenticated) void session.loadOrgProfile().catch(() => undefined);
      else void router.replace({ name: 'employee-login' });
    });
  } else if (session.isAuthenticated && !session.profile) {
    void session.loadOrgProfile().catch(() => undefined);
  }
});

const displayName = computed(() => session.displayName || '同事');
const positionName = computed(() => session.positionName || '未设置职位');
const departmentName = computed(() => session.departmentName || '未分配部门');
const employeeNo = computed(() => session.profile?.employeeNo || '—');
const mobile = computed(() => {
  const m = session.profile?.mobile;
  if (!m) return '未绑定';
  return `${m.slice(0, 3)}****${m.slice(-4)}`;
});
const hireDate = computed(() => (session.profile?.hireDate || '—').slice(0, 10));
const avatarUrl = computed(() => shell.avatarUrl || '');

async function logout() {
  if (!window.confirm('确认退出登录？本机消息缓存将保留。')) return;
  await session.logout().catch(() => undefined);
  void router.replace({ name: 'employee-login' });
}
</script>

<template>
  <HubShell>
  <div class="pc-page">
    <header class="pc-head">
      <div>
        <h1>个人中心</h1>
        <p>管理个人资料、联系方式与账号安全</p>
      </div>
    </header>

    <!-- 身份摘要 -->
    <section class="pc-card pc-identity">
      <HubAvatar :label="displayName" :image="avatarUrl" size="lg" />
      <div class="pc-identity-copy">
        <strong>{{ displayName }}</strong>
        <span>{{ positionName }} · {{ departmentName }}</span>
        <small>{{ employeeNo }}</small>
      </div>
      <button type="button" class="pc-btn-secondary" @click="showToast('头像上传即将上线')">
        <UserRound :size="16" :stroke-width="2" />更换头像
      </button>
    </section>

    <!-- 个人资料 -->
    <section class="pc-card">
      <div class="pc-card-head">
        <h2>个人资料</h2>
        <span class="pc-card-note">组织信息由管理员维护</span>
      </div>
      <div class="pc-fields">
        <div class="pc-field">
          <small>工号</small>
          <span class="pc-field-readonly">{{ employeeNo }}</span>
        </div>
        <div class="pc-field">
          <small>部门 / 职位</small>
          <span class="pc-field-readonly">{{ departmentName }} / {{ positionName }}</span>
        </div>
        <div class="pc-field">
          <small>手机号</small>
          <span class="pc-field-value">
            {{ mobile }}
            <button type="button" class="pc-link" @click="showToast('修改手机号即将上线')">修改</button>
          </span>
        </div>
        <div class="pc-field">
          <small>入职日期</small>
          <span class="pc-field-readonly">{{ hireDate }}</span>
        </div>
      </div>
    </section>

    <!-- 个性化 -->
    <section class="pc-card">
      <div class="pc-card-head">
        <h2>个性化</h2>
        <span class="pc-card-note">不影响组织档案</span>
      </div>
      <div class="pc-fields">
        <div class="pc-field">
          <small>显示名称</small>
          <span class="pc-field-value">
            {{ displayName }}
            <button type="button" class="pc-link" @click="showToast('显示名称修改即将上线')">修改</button>
          </span>
        </div>
        <div class="pc-field">
          <small>外观主题</small>
          <span class="pc-field-value">
            {{ shell.theme === 'light' ? '浅色' : '深色' }}
            <button type="button" class="pc-link" @click="shell.toggleTheme()">
              <SunMedium v-if="shell.theme === 'dark'" :size="14" :stroke-width="2" />
              <Moon v-else :size="14" :stroke-width="2" />
              切换
            </button>
          </span>
        </div>
      </div>
    </section>

    <!-- 账号安全 -->
    <section class="pc-card">
      <div class="pc-card-head">
        <h2>账号安全</h2>
        <span class="pc-card-note">管理登录密码与本机登录状态</span>
      </div>
      <div class="pc-rows">
        <div class="pc-row">
          <span class="pc-row-icon"><Clock :size="18" :stroke-width="2" /></span>
          <div class="pc-row-copy">
            <strong>登录密码</strong>
            <small>当前登录保持有效</small>
          </div>
          <button type="button" class="pc-btn-secondary" @click="showToast('修改密码即将上线')">修改密码</button>
        </div>
        <div class="pc-divider" />
        <div class="pc-row">
          <span class="pc-row-icon"><CalendarCheck :size="18" :stroke-width="2" /></span>
          <div class="pc-row-copy">
            <strong>我的考勤</strong>
            <small>查看出勤、假期与加班记录</small>
          </div>
          <button type="button" class="pc-btn-secondary" @click="router.push('/hub/attendance')">
            进入考勤 <ChevronRight :size="12" :stroke-width="2" />
          </button>
        </div>
        <div class="pc-divider" />
        <div class="pc-row">
          <span class="pc-row-icon danger"><LogOut :size="18" :stroke-width="2" /></span>
          <div class="pc-row-copy">
            <strong class="danger">退出当前账号</strong>
            <small>退出后保留本机消息缓存</small>
          </div>
          <button type="button" class="pc-btn-danger" @click="logout">退出登录</button>
        </div>
      </div>
    </section>

    <Transition name="pc-toast">
      <p v-if="toast" class="pc-toast">{{ toast }}</p>
    </Transition>
  </div>
  </HubShell>
</template>

<style scoped src="../styles/profile.css" />
