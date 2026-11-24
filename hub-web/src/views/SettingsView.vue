<script setup lang="ts">
/**
 * 设置：对齐画布「员工端-设置-桌面端1280」。
 * 左侧二级导航（通用/消息通知/存储/关于）+ 右侧分组表单。
 */
import { computed, onMounted, ref } from 'vue';
import { useSessionStore } from '@/stores/session';
import HubToast from '@/components/base/HubToast.vue';
import brandIcon from '@/assets/brand/workforce.png';
import { useToast } from '@/composables/useToast';
import { preferences, savePreferences, chooseDownloadDirectory, type LocalPreferences } from '@/services/preferences';

const session = useSessionStore();
const { message: toastMessage, show: showToast } = useToast();
const section = ref<'general' | 'notifications' | 'storage' | 'about'>('general');
const saving = ref(false);
const desktop = Boolean(window.workforceDesktop);

const SECTIONS = [
  { id: 'general', label: '通用' },
  { id: 'notifications', label: '消息通知' },
  { id: 'storage', label: '存储' },
  { id: 'about', label: '关于' },
] as const;

onMounted(() => {
  /* 隐私模式不阻断 */
  if (!session.profile) void session.loadOrgProfile();
});

const title = computed(() => SECTIONS.find((s) => s.id === section.value)?.label ?? '通用');
const subtitle = computed(() => ({
  general: '管理桌面端外观、通知与本机使用偏好',
  notifications: '设置新消息提醒、声音和内容预览',
  storage: '查看本机数据占用，按需清理临时文件',
  about: '应用版本、服务支持与许可信息',
}[section.value] ?? ''));

async function save(patch: Partial<LocalPreferences>) {
  if (saving.value) return;
  saving.value = true;
  try { await savePreferences(patch); }
  catch (error) { /* 隐私模式不阻断 */ showToast(error instanceof Error ? error.message : '设置保存失败'); }
  finally { saving.value = false; }
}
async function selectDirectory() {
  saving.value = true;
  try { await chooseDownloadDirectory(); }
  catch { showToast('下载目录设置失败'); }
  finally { saving.value = false; }
}
</script>

<template>
  <div class="st-page">
    <aside class="st-sidebar">
      <h1>设置</h1>
      <nav>
        <button
          v-for="s in SECTIONS" :key="s.id" type="button"
          :class="{ active: section === s.id }" @click="section = s.id"
        >{{ s.label }}</button>
      </nav>
    </aside>
    <main class="st-main">
      <header class="st-head"><h2>{{ title }}</h2><p>{{ subtitle }}</p></header>

      <template v-if="section === 'general'">
        <section class="st-card">
          <h3>外观</h3>
          <div class="st-row">
            <div class="st-copy"><strong>外观主题</strong><small>选择浅色、深色或跟随系统</small></div>
            <div class="st-seg">
              <button v-for="t in ['system', 'light', 'dark'] as const" :key="t" type="button" :class="{ active: preferences.theme === t }" :disabled="saving" @click="save({ theme: t })">
                {{ t === 'system' ? '跟随系统' : t === 'light' ? '浅色' : '深色' }}
              </button>
            </div>
          </div>
        </section>
        <section class="st-card">
          <h3>桌面端</h3>
          <div class="st-row">
            <div class="st-copy"><strong>开机时启动</strong><small>登录系统后自动打开 workforce</small></div>
            <span v-if="!preferences.canLaunchAtLogin" class="st-status">仅安装版可设置</span>
            <button type="button" class="st-toggle" :class="{ on: preferences.launchAtLogin }" role="switch" :aria-checked="preferences.launchAtLogin" aria-label="开机时启动" :disabled="saving || !preferences.canLaunchAtLogin" @click="save({ launchAtLogin: !preferences.launchAtLogin })"><i /></button>
          </div>
          <div class="st-row">
            <div class="st-copy"><strong>关闭窗口时</strong><small>选择关闭窗口后的应用行为</small></div>
            <select class="st-select" aria-label="关闭窗口时" :value="preferences.closeBehavior" :disabled="saving || !desktop" @change="save({ closeBehavior: ($event.target as HTMLSelectElement).value as LocalPreferences['closeBehavior'] })"><option value="quit">退出应用</option><option value="background">后台运行</option></select>
          </div>
          <div class="st-row">
            <div class="st-copy"><strong>窗口状态</strong></div><span class="st-status">{{ desktop ? '自动保存位置、大小与最大化状态' : '由浏览器管理' }}</span>
          </div>
        </section>
      </template>

      <template v-else-if="section === 'notifications'">
        <section class="st-card">
          <h3>消息通知</h3>
          <div class="st-row">
            <div class="st-copy"><strong>桌面通知</strong><small>收到新消息时在桌面显示提醒</small></div>
            <span v-if="!desktop || !preferences.notificationsSupported" class="st-status">当前环境不支持</span>
            <button type="button" class="st-toggle" :class="{ on: preferences.desktopNotifications }" role="switch" :aria-checked="preferences.desktopNotifications" aria-label="桌面通知" :disabled="saving || !desktop || !preferences.notificationsSupported" @click="save({ desktopNotifications: !preferences.desktopNotifications })"><i /></button>
          </div>
          <div class="st-row">
            <div class="st-copy"><strong>通知声音</strong><small>新消息提醒时播放提示音</small></div>
            <button type="button" class="st-toggle" :class="{ on: preferences.notificationSound }" role="switch" :aria-checked="preferences.notificationSound" aria-label="通知声音" :disabled="saving || !desktop || !preferences.desktopNotifications" @click="save({ notificationSound: !preferences.notificationSound })"><i /></button>
          </div>
          <div class="st-row">
            <div class="st-copy"><strong>显示消息预览</strong><small>关闭后仅提示收到新消息，不展示正文</small></div>
            <button type="button" class="st-toggle" :class="{ on: preferences.messagePreview }" role="switch" :aria-checked="preferences.messagePreview" aria-label="显示消息预览" :disabled="saving || !desktop || !preferences.desktopNotifications" @click="save({ messagePreview: !preferences.messagePreview })"><i /></button>
          </div>
        </section>
        <section class="st-card">
          <h3>免打扰</h3>
          <div class="st-row">
            <div class="st-copy"><strong>免打扰时段</strong><small>仅暂停本机提醒，不影响消息接收</small></div>
            <button type="button" class="st-toggle" :class="{ on: preferences.dndEnabled }" role="switch" :aria-checked="preferences.dndEnabled" aria-label="免打扰时段" :disabled="saving || !desktop" @click="save({ dndEnabled: !preferences.dndEnabled })"><i /></button>
          </div>
          <div v-if="preferences.dndEnabled" class="st-row st-time-range">
            <label>开始时间<input type="time" aria-label="免打扰开始时间" :value="preferences.dndStart" :disabled="saving" @change="save({ dndStart: ($event.target as HTMLInputElement).value })" /></label>
            <label>结束时间<input type="time" aria-label="免打扰结束时间" :value="preferences.dndEnd" :disabled="saving" @change="save({ dndEnd: ($event.target as HTMLInputElement).value })" /></label>
          </div>
        </section>
      </template>

      <template v-else-if="section === 'storage'">
        <section class="st-card">
          <h3>默认存储位置</h3>
          <div class="st-row">
            <div class="st-copy"><strong>下载文件保存到</strong><small>仅影响本机下载目录，不改变云端文件位置</small></div>
            <span class="st-path" :title="preferences.downloadDirectory">{{ desktop ? preferences.downloadDirectory : '由浏览器管理' }}</span>
            <button type="button" class="st-btn" :disabled="saving || !desktop" @click="selectDirectory">更改位置</button>
          </div>
        </section>
        <section class="st-card">
          <h3>本机数据</h3>
          <div class="st-row">
            <div class="st-copy"><strong class="st-big">256 MB</strong><small>离线消息与本机文件占用</small></div>
            <button type="button" class="st-btn" @click="showToast('文件管理即将上线')">管理文件</button>
          </div>
        </section>
        <section class="st-card">
          <h3>临时文件</h3>
          <div class="st-row">
            <div class="st-copy"><strong>清理临时文件</strong><small>不会删除离线消息或已保存的业务文件</small></div>
            <button type="button" class="st-btn" @click="showToast('清理临时文件即将上线')">清理</button>
          </div>
        </section>
      </template>

      <template v-else>
        <section class="st-card st-about">
          <img class="st-logo" :src="brandIcon" alt="" width="48" height="48" />
          <h3>workforce</h3>
          <small>版本 0.1.0 · 私有化部署</small>
          <p>面向组织协作、审批与知识服务的工作平台</p>
          <div class="st-links">
            <button type="button" @click="showToast('用户协议即将上线')">用户协议</button>
            <button type="button" @click="showToast('隐私政策即将上线')">隐私政策</button>
          </div>
        </section>
        <section class="st-card">
          <h3>支持</h3>
          <div class="st-row">
            <div class="st-copy"><strong>问题反馈</strong><small>反馈使用中遇到的问题，不包含消息正文</small></div>
            <button type="button" class="st-link" @click="showToast('问题反馈即将上线')">›</button>
          </div>
        </section>
      </template>
    </main>
    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped src="../styles/settings.css" />
