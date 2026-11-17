<script setup lang="ts">
/**
 * 通讯录：左侧分类（全部同事/我的上级/我的下属/常用联系人）+ 右侧成员列表与详情弹层。
 */
import { computed, onMounted, ref } from 'vue';
import { Search } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import { contactsApi, orgApi } from '@/api';
import type { OrgUserSummaryResponse } from '@/api/types';
import { useToast } from '@/composables/useToast';
import HubAvatar from '@/components/base/HubAvatar.vue';
import HubButton from '@/components/base/HubButton.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';
import HubToast from '@/components/base/HubToast.vue';

const router = useRouter();
const { message: toastMessage, show: showToast } = useToast();

const keyword = ref('');
const category = ref<'all' | 'manager' | 'subordinates' | 'friends'>('all');
const colleagues = ref<OrgUserSummaryResponse[]>([]);
const manager = ref<OrgUserSummaryResponse | null>(null);
const subordinates = ref<OrgUserSummaryResponse[]>([]);
const friends = ref<OrgUserSummaryResponse[]>([]);
const loading = ref(true);
const loadFailed = ref(false);

const categories = [
  { key: 'all', label: '全部同事' },
  { key: 'manager', label: '我的上级' },
  { key: 'subordinates', label: '我的下属' },
  { key: 'friends', label: '常用联系人' },
] as const;

async function load() {
  loading.value = true;
  loadFailed.value = false;
  const [colleaguesRes, managerRes, subordinatesRes, friendsRes] = await Promise.allSettled([
    orgApi.colleagues(),
    orgApi.manager(),
    orgApi.subordinates(),
    fetchFriends(),
  ]);
  colleagues.value = colleaguesRes.status === 'fulfilled' ? colleaguesRes.value : [];
  manager.value = managerRes.status === 'fulfilled' ? managerRes.value : null;
  subordinates.value = subordinatesRes.status === 'fulfilled' ? subordinatesRes.value : [];
  friends.value = friendsRes.status === 'fulfilled' ? friendsRes.value : [];
  loadFailed.value = colleaguesRes.status === 'rejected';
  if (loadFailed.value) showToast('通讯录加载失败，请稍后重试');
  loading.value = false;
}
async function fetchFriends() {
  try {
    return await contactsApi.listFriends();
  } catch {
    return [];
  }
}
onMounted(load);

const members = computed(() => {
  const base = category.value === 'all' ? colleagues.value
    : category.value === 'manager' ? (manager.value ? [manager.value] : [])
    : category.value === 'subordinates' ? subordinates.value
    : friends.value;
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) return base;
  return base.filter((u) =>
    (u.realName || '').toLowerCase().includes(kw) || (u.primaryDeptName || '').toLowerCase().includes(kw));
});

/* 详情弹层与备注修改 */
const detail = ref<OrgUserSummaryResponse | null>(null);
const remarkDraft = ref('');
const savingRemark = ref(false);

function openDetail(user: OrgUserSummaryResponse) {
  detail.value = user;
  remarkDraft.value = '';
}
function sendMessage(user: OrgUserSummaryResponse) {
  void router.push({ path: '/messages', query: { keyword: user.realName } });
}
async function saveRemark() {
  if (!detail.value) return;
  savingRemark.value = true;
  try {
    const updated = await contactsApi.updateFriendRemark(detail.value.userId, remarkDraft.value.trim());
    const target = friends.value.find((u) => String(u.userId) === String(detail.value!.userId));
    if (target && updated) Object.assign(target, updated);
    showToast('备注已保存');
    detail.value = null;
  } catch (error) {
    showToast(error instanceof Error ? error.message : '备注保存失败（可能尚未互为联系人）');
  } finally {
    savingRemark.value = false;
  }
}
</script>

<template>
  <div class="ct-page">
    <header class="ct-head">
      <div><h1>通讯录</h1><p>同事、上下级与常用联系人</p></div>
      <label class="ct-search"><Search :size="16" /><input v-model="keyword" type="search" aria-label="搜索成员" placeholder="搜索姓名或部门" /></label>
    </header>

    <div class="ct-body">
      <aside class="ct-categories">
        <button
          v-for="c in categories" :key="c.key" type="button"
          class="ct-category" :class="{ active: category === c.key }"
          @click="category = c.key"
        >{{ c.label }}<em v-if="c.key === 'friends' && friends.length">{{ friends.length }}</em></button>
      </aside>

      <section class="ct-list">
        <HubEmpty v-if="!members.length && !loading" :title="loadFailed ? '通讯录加载失败' : '暂无成员'" :note="loadFailed ? '请稍后重试' : '换个分类或搜索条件试试'" />
        <p v-else-if="loading" class="ct-loading">加载中…</p>
        <button v-for="u in members" :key="String(u.userId)" type="button" class="ct-member" @click="openDetail(u)">
          <HubAvatar :label="u.realName" size="md" />
          <span class="ct-member-info">
            <strong>{{ u.realName }}</strong>
            <small>{{ [u.primaryDeptName, u.positionName].filter(Boolean).join(' · ') || '—' }}</small>
          </span>
          <span class="ct-member-actions" @click.stop>
            <HubButton size="sm" variant="primary" @click="sendMessage(u)">发消息</HubButton>
            <HubButton size="sm" @click="openDetail(u)">查看详情</HubButton>
          </span>
        </button>
      </section>
    </div>

    <HubDialog v-if="detail" @close="detail = null">
      <div class="ct-detail">
        <div class="ct-detail-head">
          <HubAvatar :label="detail.realName" size="xl" />
          <div>
            <h2>{{ detail.realName }}</h2>
            <p>{{ [detail.primaryDeptName, detail.positionName].filter(Boolean).join(' · ') || '—' }}</p>
          </div>
        </div>
        <dl>
          <div v-if="detail.employeeNo"><dt>工号</dt><dd>{{ detail.employeeNo }}</dd></div>
          <div v-if="detail.managerName"><dt>直属上级</dt><dd>{{ detail.managerName }}</dd></div>
        </dl>
        <label class="ct-remark">备注名<input v-model="remarkDraft" placeholder="仅互为联系人时可修改" maxlength="30" /></label>
        <div class="ct-detail-actions">
          <HubButton @click="detail = null">关闭</HubButton>
          <HubButton variant="primary" :disabled="savingRemark" @click="saveRemark">保存备注</HubButton>
        </div>
      </div>
    </HubDialog>

    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped src="../styles/contacts.css" />
