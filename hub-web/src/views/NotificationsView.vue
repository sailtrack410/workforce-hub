<script setup lang="ts">
/**
 * 通知中心：分页列表、未读标记、全部已读与详情弹层。
 */
import { computed, onMounted, ref } from 'vue';
import { notificationApi } from '@/api';
import type { PageResult, SysNotificationResponse } from '@/api/types';
import { useToast } from '@/composables/useToast';
import { pendingOperations } from '@/services/sync';
import HubButton from '@/components/base/HubButton.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';
import HubToast from '@/components/base/HubToast.vue';

const { message: toastMessage, show: showToast } = useToast();

const PAGE_SIZE = 20;
const items = ref<SysNotificationResponse[]>([]);
const pageNo = ref(1);
const total = ref(0);
const loading = ref(true);
const loadFailed = ref(false);

const TYPE_LABEL: Record<string, string> = { APPROVAL: '审批', SYSTEM: '系统', ATTENDANCE: '考勤', ANNOUNCEMENT: '公告' };
const TYPE_CLASS: Record<string, string> = { APPROVAL: 'approval', SYSTEM: 'system', ATTENDANCE: 'attendance', ANNOUNCEMENT: 'announcement' };

async function load(reset: boolean) {
  loading.value = true;
  try {
    const res = await notificationApi.list({ pageNo: reset ? 1 : pageNo.value, pageSize: PAGE_SIZE });
    const page = Array.isArray(res) ? { list: res, total: res.length } : (res as PageResult<SysNotificationResponse>);
    items.value = reset ? page.list : [...items.value, ...page.list];
    const queue = await pendingOperations();
    const allRead = queue.some(op => op.path === '/api/employee/notifications/read-all');
    for (const n of items.value) if (allRead || queue.some(op => op.path === `/api/employee/notifications/${n.notificationId}/read`)) n.read = true;
    total.value = page.total;
    pageNo.value = reset ? 1 : pageNo.value + 1;
    loadFailed.value = false;
  } catch (error) {
    loadFailed.value = true;
    showToast(error instanceof Error ? error.message : '通知加载失败');
  } finally {
    loading.value = false;
  }
}
onMounted(() => { load(true); void refreshUnread(); });

const unreadCount = computed(() => items.value.filter((n) => !n.read).length);
const hasMore = computed(() => items.value.length < total.value);

const unreadTotal = ref(0);
async function refreshUnread() {
  try { unreadTotal.value = (await notificationApi.unreadCount())?.unreadCount ?? 0; } catch { /* 徽标失败不阻断 */ }
}

async function markAllRead() {
  if (!unreadCount.value) return;
  try {
    await notificationApi.markAllRead();
    for (const n of items.value) n.read = true;
    showToast('已全部标记为已读');
    void refreshUnread();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  }
}

/* 详情弹层：点击行即标记已读 */
const detail = ref<SysNotificationResponse | null>(null);

function contentText(n: SysNotificationResponse) {
  const raw = n.content || '';
  try {
    const parsed = JSON.parse(raw);
    if (typeof parsed === 'string') return parsed;
    if (parsed?.text) return String(parsed.text);
    return JSON.stringify(parsed);
  } catch {
    return raw;
  }
}

async function openDetail(n: SysNotificationResponse) {
  detail.value = n;
  if (!n.read) {
    try {
      await notificationApi.markRead(n.notificationId);
      n.read = true;
      void refreshUnread();
    } catch { /* 标记失败不影响查看 */ }
  }
}
</script>

<template>
  <div class="nt-page">
    <header class="nt-head">
      <div><h1>通知中心</h1><p>审批、系统、考勤与公告通知</p></div>
      <span class="nt-head-spacer" />
      <span v-if="unreadTotal" class="nt-unread-pill">未读 {{ unreadTotal }}</span>
      <HubButton variant="primary" :disabled="!unreadCount" @click="markAllRead">全部已读</HubButton>
    </header>

    <section class="nt-list">
      <HubEmpty v-if="!items.length && !loading" :title="loadFailed ? '通知加载失败' : '暂无通知'" :note="loadFailed ? '请稍后重试' : '有新通知时会在这里显示'" />
      <p v-else-if="loading && !items.length" class="nt-loading">加载中…</p>
      <button
        v-for="n in items" :key="String(n.notificationId)" type="button"
        class="nt-item" :class="{ unread: !n.read }" @click="openDetail(n)"
      >
        <span v-if="!n.read" class="nt-dot" aria-label="未读" />
        <strong class="nt-title">{{ n.title }}</strong>
        <em :class="['nt-type', TYPE_CLASS[n.type] || 'system']">{{ TYPE_LABEL[n.type] || '系统' }}</em>
        <small class="nt-time">{{ (n.createdAt || '').slice(0, 16).replace('T', ' ') }}</small>
      </button>
      <div v-if="hasMore" class="nt-more">
        <HubButton :disabled="loading" @click="load(false)">{{ loading ? '加载中…' : '加载更多' }}</HubButton>
      </div>
    </section>

    <HubDialog v-if="detail" @close="detail = null">
      <div class="nt-detail">
        <h2>{{ detail.title }}</h2>
        <p class="nt-detail-meta">
          <em :class="['nt-type', TYPE_CLASS[detail.type] || 'system']">{{ TYPE_LABEL[detail.type] || '系统' }}</em>
          <span>{{ (detail.createdAt || '').slice(0, 16).replace('T', ' ') }}</span>
        </p>
        <p class="nt-detail-body">{{ contentText(detail) || '（无正文）' }}</p>
        <div class="nt-detail-actions"><HubButton v-if="detail.linkType === 'PERSONAL_TODO' && detail.linkId" variant="primary" @click="$router.push({ path: '/todos', query: { todoId: String(detail.linkId) } })">查看待办</HubButton><HubButton @click="detail = null">关闭</HubButton></div>
      </div>
    </HubDialog>

    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped src="../styles/notifications.css" />
