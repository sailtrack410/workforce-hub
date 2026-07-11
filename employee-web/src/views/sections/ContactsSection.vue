<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  ChevronLeft,
  ChevronDown,
  ChevronRight,
  BadgeCheck,
  Pencil,
  MessageCircle,
  MessageSquareText,
  UserRoundCheck,
  UserPlus,
  UserMinus,
  X,
  List as ListIcon,
  Network,
  GripVertical,
} from 'lucide-vue-next';
import type { ContactRecord, EntityId, OrgDepartmentTreeNodeResponse } from '@/stores/shell';
import { employeeApi } from '@/api';
import type { AgentResponse, OrgUserSummaryResponse } from '@/api';
import { useContactsSectionContext } from '../employee-shell-context';
import { agentTypeLabel, displayField, employmentStatusLabel } from '@/utils/employee-format';
import { humanizeText } from '@/utils/employee-format';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppButton from '../../components/ui/AppButton.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppTag from '../../components/ui/AppTag.vue';
import type { ContactCategoryItem, ContactTreeNode } from '../../components/contact-category';
import { contactsTreeNodesForView } from '../../components/contact-category';
import ContactsOrgTree from '../../components/ContactsOrgTree.vue';

const {
  shell,
  activeContact,
  backToList,
  startPrivateConversationFromContact,
  openContactRemarkEditor,
} = useContactsSectionContext();

const router = useRouter();

const profileContact = ref<ContactRecord | null>(null);
const friendActionLoading = ref(false);
const localToast = ref('');
let localToastTimer: number | undefined;

// AI 助手
const agents = ref<AgentResponse[]>([]);
const agentsLoading = ref(false);
const agentAvatarUrls = ref<Record<string, string>>({});
const friendRequests = ref<OrgUserSummaryResponse[]>([]);
const friendRequestsLoading = ref(false);

// 树状图画布拖动
const treeCanvasRef = ref<HTMLElement | null>(null);
const isPanning = ref(false);
const panStart = ref({ x: 0, y: 0, scrollLeft: 0, scrollTop: 0 });
const profileDialogOffset = ref({ x: 0, y: 0 });
const profileDialogDrag = ref<{ pointerId: number; x: number; y: number; startX: number; startY: number } | null>(null);

function onTreeCanvasPointerDown(e: PointerEvent) {
  const target = e.target as HTMLElement;
  if (target.closest('button') || target.closest('.org-tree-card')) return;
  const canvas = treeCanvasRef.value;
  if (!canvas) return;
  isPanning.value = true;
  panStart.value = {
    x: e.clientX,
    y: e.clientY,
    scrollLeft: canvas.scrollLeft,
    scrollTop: canvas.scrollTop,
  };
  canvas.setPointerCapture(e.pointerId);
}

function onTreeCanvasPointerMove(e: PointerEvent) {
  if (!isPanning.value) return;
  const canvas = treeCanvasRef.value;
  if (!canvas) return;
  const dx = e.clientX - panStart.value.x;
  const dy = e.clientY - panStart.value.y;
  canvas.scrollLeft = panStart.value.scrollLeft - dx;
  canvas.scrollTop = panStart.value.scrollTop - dy;
}

function onTreeCanvasPointerUp(e: PointerEvent) {
  if (!isPanning.value) return;
  isPanning.value = false;
  treeCanvasRef.value?.releasePointerCapture(e.pointerId);
}

function onProfileDialogPointerDown(e: PointerEvent) {
  const target = e.target as HTMLElement;
  if (target.closest('button, a, input, textarea, select')) return;
  e.preventDefault();
  profileDialogDrag.value = {
    pointerId: e.pointerId,
    x: e.clientX,
    y: e.clientY,
    startX: profileDialogOffset.value.x,
    startY: profileDialogOffset.value.y,
  };
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
}

function onProfileDialogPointerMove(e: PointerEvent) {
  const drag = profileDialogDrag.value;
  if (!drag) return;
  profileDialogOffset.value = {
    x: drag.startX + e.clientX - drag.x,
    y: drag.startY + e.clientY - drag.y,
  };
}

function onProfileDialogPointerUp(e: PointerEvent) {
  if (!profileDialogDrag.value) return;
  profileDialogDrag.value = null;
  (e.currentTarget as HTMLElement).releasePointerCapture(e.pointerId);
}

// 当前选中的部门节点（用于面包屑和列表过滤）
const currentDeptNode = ref<OrgDepartmentTreeNodeResponse | null>(null);

const currentCategory = computed<ContactCategoryItem | undefined>(() => {
  function find(items: ContactCategoryItem[]): ContactCategoryItem | undefined {
    for (const item of items) {
      if (item.id === shell.contactsCategoryId) return item;
      if (item.children) {
        const found = find(item.children);
        if (found) return found;
      }
    }
    return undefined;
  }
  return find(shell.contactsCategoryItems);
});

// 面包屑路径
const breadcrumbItems = computed(() => {
  const current = currentCategory.value;
  if (!current) return ['通讯录'];
  if (current.type === 'dept' && currentDeptNode.value) {
    return buildBreadcrumb(currentDeptNode.value);
  }
  if (current.type === 'category') return ['通讯录', current.label];
  if (current.type === 'ai') return ['通讯录', 'AI 助手'];
  if (current.type === 'service') return ['通讯录', '企业服务', current.label];
  return ['通讯录'];
});

function buildBreadcrumb(node: OrgDepartmentTreeNodeResponse): string[] {
  const path: string[] = ['通讯录'];
  const parent = findParentDept(node.id, shell.deptTree);
  if (parent) {
    path.push(...buildBreadcrumb(parent).slice(1));
  }
  path.push(node.deptName);
  return path;
}

function findParentDept(targetId: EntityId, nodes: OrgDepartmentTreeNodeResponse[]): OrgDepartmentTreeNodeResponse | null {
  for (const node of nodes) {
    if (node.children?.some((child) => String(child.id) === String(targetId))) return node;
    const found = findParentDept(targetId, node.children ?? []);
    if (found) return found;
  }
  return null;
}

// 当前分类/部门的成员
const currentMembers = computed<ContactRecord[]>(() => {
  const current = currentCategory.value;
  if (!current) return [];
  if (current.id === 'friends') {
    return shell.filteredContacts.filter((c) => c.relation === '好友');
  }
  if (current.id === 'new-friends') {
    return friendRequests.value.map((user) => ({
      id: `request-${user.userId}`,
      name: user.realName,
      originalName: user.realName,
      role: user.positionName || '未设置岗位',
      department: user.primaryDeptName || '未设置部门',
      primaryDeptId: user.primaryDeptId,
      relation: '待同意',
      source: '好友申请',
      userId: user.userId,
      avatarAttachmentRefId: user.avatarAttachmentRefId,
      employeeNo: user.employeeNo,
      status: user.status,
      employmentStatus: user.employmentStatus,
      managerName: user.managerName,
      signature: user.signature || '',
      presenceStatus: user.presenceStatus as ContactRecord['presenceStatus'],
    }));
  }
  if (current.type === 'dept' && currentDeptNode.value) {
    const deptId = String(currentDeptNode.value.id);
    return shell.filteredContacts.filter((c) =>
      c.relation !== '本人' && String(c.primaryDeptId) === deptId
    );
  }
  if (current.id === 'ai') return [];
  // 其他固定分类暂无数据
  return [];
});

const isAiCategory = computed(() => currentCategory.value?.type === 'ai');
const canToggleTreeView = computed(() => currentCategory.value?.type === 'dept');

// 当前部门子部门（用于树状视图以某个部门为根显示）
const currentDeptChildren = computed<ContactTreeNode[]>(() => {
  const currentUserDeptId = shell.contacts.find((contact) => contact.relation === '本人')?.primaryDeptId;
  return contactsTreeNodesForView(shell.deptTree, shell.filteredContacts, currentUserDeptId);
});

function centerTreeCanvas() {
  void nextTick(() => {
    const canvas = treeCanvasRef.value;
    if (!canvas) return;
    canvas.scrollLeft = Math.max(0, (canvas.scrollWidth - canvas.clientWidth) / 2);
    canvas.scrollTop = Math.max(0, (canvas.scrollHeight - canvas.clientHeight) / 2);
  });
}

// 是否好友关系
const isCurrentFriend = computed(() => {
  const c = profileContact.value;
  if (!c?.userId) return false;
  return shell.friendUserIds.has(String(c.userId));
});

function showToast(message: string) {
  localToast.value = message;
  if (localToastTimer) window.clearTimeout(localToastTimer);
  localToastTimer = window.setTimeout(() => {
    localToast.value = '';
  }, 2200);
}

function presenceStatusLabel(status?: string | null) {
  if (status === 'ONLINE') return '在线';
  if (status === 'BUSY') return '忙碌';
  if (status === 'AWAY') return '离开';
  if (status === 'DND') return '勿扰';
  return '未设置';
}

function openContactProfile(contact: ContactRecord) {
  profileContact.value = contact;
  shell.selectItem('contacts', contact.id);
}

function closeContactProfile() {
  profileContact.value = null;
}

async function sendProfileMessage() {
  const contact = profileContact.value;
  if (!contact) return;
  await startPrivateConversationFromContact(contact);
  profileContact.value = null;
}

function editProfileRemark() {
  const contact = profileContact.value;
  if (!contact) return;
  openContactRemarkEditor(contact);
  profileContact.value = null;
}

async function toggleFriend() {
  const contact = profileContact.value;
  if (!contact?.userId || friendActionLoading.value) return;
  friendActionLoading.value = true;
  try {
    if (isCurrentFriend.value) {
      await employeeApi.contacts.removeFriend(contact.userId);
      showToast('已移除好友');
    } else {
      await employeeApi.contacts.addFriend(contact.userId);
      showToast('已发送好友申请');
    }
    const rows = await employeeApi.contacts.friends();
    shell.setFriends(rows);
    const isFriendNow = rows.some((r) => String(r.userId) === String(contact.userId));
    shell.contacts = shell.contacts.map((c) => (
      c.userId === contact.userId
        ? { ...c, relation: isFriendNow ? '好友' : (c.relation === '好友' ? '同部门' : c.relation) }
        : c
    ));
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  } finally {
    friendActionLoading.value = false;
  }
}

async function acceptFriendRequest(contact: ContactRecord) {
  if (!contact.userId || friendActionLoading.value) return;
  friendActionLoading.value = true;
  try {
    await employeeApi.contacts.acceptFriendRequest(contact.userId);
    showToast('已同意好友申请');
    friendRequests.value = await employeeApi.contacts.friendRequests();
    shell.setFriends(await employeeApi.contacts.friends());
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  } finally {
    friendActionLoading.value = false;
  }
}

function findDeptNode(id: EntityId | undefined, nodes: OrgDepartmentTreeNodeResponse[]): OrgDepartmentTreeNodeResponse | null {
  if (id === undefined) return null;
  const idStr = String(id);
  for (const node of nodes) {
    if (String(node.id) === idStr) return node;
    const found = findDeptNode(id, node.children ?? []);
    if (found) return found;
  }
  return null;
}

function handleTreeSelect(node: OrgDepartmentTreeNodeResponse) {
  shell.setContactsCategoryId(`dept-${node.id}`);
  currentDeptNode.value = node;
}

function syncCategoryState() {
  const category = currentCategory.value;
  if (category?.type === 'dept') {
    const dept = findDeptNode(category.deptId, shell.deptTree);
    currentDeptNode.value = dept;
    if (shell.contactsViewMode !== 'tree') {
      shell.setContactsViewMode('list');
    }
  } else if (category?.id === 'org-structure') {
    currentDeptNode.value = null;
    shell.setContactsViewMode('tree');
  } else if (category?.id === 'ai') {
    currentDeptNode.value = null;
    shell.setContactsViewMode('list');
    void loadAgents();
  } else if (category?.id === 'new-friends') {
    currentDeptNode.value = null;
    shell.setContactsViewMode('list');
    void loadFriendRequests();
  } else {
    currentDeptNode.value = null;
    shell.setContactsViewMode('list');
  }
}

async function loadFriendRequests() {
  if (friendRequestsLoading.value) return;
  friendRequestsLoading.value = true;
  try {
    friendRequests.value = await employeeApi.contacts.friendRequests();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '好友申请加载失败');
  } finally {
    friendRequestsLoading.value = false;
  }
}

async function loadAgents() {
  if (agentsLoading.value) return;
  agentsLoading.value = true;
  try {
    agents.value = await employeeApi.agents.list({});
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'AI 助手加载失败');
  } finally {
    agentsLoading.value = false;
  }
}

async function refreshAgentAvatarUrls(rows: AgentResponse[]) {
  const entries = await Promise.all(rows.map(async (agent) => {
    if (!agent.avatarAttachmentRefId) return [String(agent.agentId), ''] as const;
    try {
      const ticket = await employeeApi.files.createDownloadTicket(agent.avatarAttachmentRefId, { downloadType: 'INLINE' });
      return [String(agent.agentId), employeeApi.files.downloadUrl(ticket.ticketNo)] as const;
    } catch {
      return [String(agent.agentId), ''] as const;
    }
  }));
  agentAvatarUrls.value = Object.fromEntries(entries);
}

watch(agents, (rows) => {
  void refreshAgentAvatarUrls(rows);
}, { immediate: true });

watch(profileContact, () => {
  profileDialogOffset.value = { x: 0, y: 0 };
  profileDialogDrag.value = null;
});

watch([() => shell.contactsViewMode, currentDeptChildren], ([mode]) => {
  if (mode === 'tree') centerTreeCanvas();
}, { immediate: true });

watch(() => shell.contactsCategoryId, syncCategoryState, { immediate: true });
</script>

<template>
<div class="workspace contacts-workspace">
  <main class="contacts-main">
    <header class="contacts-main-head">
      <nav class="contacts-breadcrumb" aria-label="通讯录路径">
        <span v-for="(item, index) in breadcrumbItems" :key="`${item}-${index}`">{{ item }}</span>
      </nav>
      <div v-if="canToggleTreeView" class="contacts-view-toggle">
        <button
          type="button"
          :class="{ active: shell.contactsViewMode === 'list' }"
          @click="shell.setContactsViewMode('list')"
        >
          <ListIcon :size="15" />
          列表
        </button>
        <button
          type="button"
          :class="{ active: shell.contactsViewMode === 'tree' }"
          @click="shell.setContactsViewMode('tree')"
        >
          <Network :size="15" />
          树状
        </button>
      </div>
    </header>

    <section class="contacts-content">
      <!-- 列表视图：成员/AI 助手 -->
      <template v-if="shell.contactsViewMode === 'list'">
        <!-- AI 助理列表 -->
        <template v-if="isAiCategory">
          <p v-if="agentsLoading" class="form-hint">加载中…</p>
          <AppEmptyState
            v-else-if="!agents.length"
            title="暂无 AI 助手"
            icon="workbench"
          />
          <div v-else class="contacts-member-list">
            <button
              v-for="agent in agents"
              :key="String(agent.agentId)"
              type="button"
              class="contacts-member-row"
              @click="router.push({ name: 'employee-agents' })"
            >
              <AppAvatar
                :label="humanizeText(agent.agentName)"
                :image="agentAvatarUrls[String(agent.agentId)]"
                kind="agent"
                tone="success"
                size="md"
              />
              <span>
                <strong>{{ humanizeText(agent.agentName) }}</strong>
                <small v-if="(agent.agentTypeName || agentTypeLabel(agent.agentType)) !== humanizeText(agent.agentName)">
                  {{ agent.agentTypeName || agentTypeLabel(agent.agentType) }}
                </small>
              </span>
            </button>
          </div>
        </template>

        <!-- 好友申请列表 -->
        <template v-else-if="currentCategory?.id === 'new-friends'">
          <p v-if="friendRequestsLoading" class="form-hint">加载中…</p>
          <AppEmptyState
            v-else-if="!currentMembers.length"
            title="暂无好友申请"
            icon="contacts"
          />
          <div v-else class="contacts-member-list">
            <div
              v-for="contact in currentMembers"
              :key="contact.id"
              class="contacts-member-row is-static"
            >
              <AppAvatar :label="contact.name" :image="contact.avatarUrl" tone="primary" size="md" />
              <span>
                <strong>{{ contact.name }}</strong>
                <small>{{ contact.department }}</small>
              </span>
              <AppButton size="sm" :disabled="friendActionLoading" @click="acceptFriendRequest(contact)">
                同意
              </AppButton>
            </div>
          </div>
        </template>

        <!-- 普通成员列表 -->
        <template v-else>
          <AppEmptyState
            v-if="!currentMembers.length"
            title="暂无成员"
            icon="contacts"
          />
          <div v-else class="contacts-member-list">
            <button
              v-for="contact in currentMembers"
              :key="contact.id"
              type="button"
              class="contacts-member-row"
              :class="{ active: activeContact?.id === contact.id }"
              @click="openContactProfile(contact)"
            >
              <AppAvatar :label="contact.name" :image="contact.avatarUrl" tone="primary" size="md" />
              <span>
                <strong>{{ contact.name }}</strong>
                <small>{{ contact.role || '未设置岗位' }}</small>
              </span>
              <AppTag :tone="contact.relation === '好友' ? 'primary' : 'neutral'">
                {{ contact.relation }}
              </AppTag>
            </button>
          </div>
        </template>
      </template>

      <!-- 树状视图：横向部门树 + 成员 -->
      <template v-else>
        <div class="contacts-tree-view">
          <div
            ref="treeCanvasRef"
            class="contacts-tree-canvas"
            @pointerdown="onTreeCanvasPointerDown"
            @pointermove="onTreeCanvasPointerMove"
            @pointerup="onTreeCanvasPointerUp"
            @pointerleave="onTreeCanvasPointerUp"
          >
            <ContactsOrgTree
              :nodes="currentDeptChildren"
              :active-dept-id="currentDeptNode ? String(currentDeptNode.id) : null"
              @select="handleTreeSelect"
              @contact="openContactProfile"
            />
          </div>
        </div>
      </template>
    </section>
  </main>

  <AppDialog v-if="profileContact" flush>
    <section
      class="contact-profile-dialog"
      :style="{ transform: `translate(${profileDialogOffset.x}px, ${profileDialogOffset.y}px)` }"
      @pointerdown="onProfileDialogPointerDown"
      @pointermove="onProfileDialogPointerMove"
      @pointerup="onProfileDialogPointerUp"
      @pointercancel="onProfileDialogPointerUp"
    >
      <button class="contact-profile-close" type="button" aria-label="关闭" @click="closeContactProfile">
        <X :size="17" />
      </button>
      <div class="contact-profile-identity">
        <AppAvatar :label="profileContact.name" :image="profileContact.avatarUrl" tone="primary" size="xl" />
        <h3>{{ profileContact.name }}</h3>
        <p>{{ profileContact.department }}</p>
        <div class="contact-profile-badges">
          <span>
            <UserRoundCheck :size="13" />
            {{ employmentStatusLabel(profileContact.employmentStatus) }}
          </span>
          <span>
            <BadgeCheck :size="13" />
            {{ presenceStatusLabel(profileContact.presenceStatus) }}
          </span>
        </div>
        <div class="contact-action-row">
          <AppButton v-if="profileContact.userId && profileContact.relation !== '本人'" variant="primary" size="sm" @click="sendProfileMessage">
            <MessageCircle :size="15" />
            发消息
          </AppButton>
          <AppButton v-if="profileContact.userId && profileContact.relation !== '本人'" :variant="isCurrentFriend ? 'destructive' : 'secondary'" size="sm" :disabled="friendActionLoading" @click="toggleFriend">
            <component :is="isCurrentFriend ? UserMinus : UserPlus" :size="15" />
            {{ friendActionLoading ? '处理中' : (isCurrentFriend ? '移除好友' : '加为好友') }}
          </AppButton>
          <AppButton v-if="profileContact.userId && profileContact.relation !== '本人'" variant="secondary" size="sm" @click="editProfileRemark">
            <Pencil :size="15" />
            备注
          </AppButton>
        </div>
      </div>
      <dl class="contact-profile-detail">
        <div>
          <dt>关系</dt>
          <dd>{{ displayField(profileContact.relation) }}</dd>
        </div>
        <div>
          <dt>职位</dt>
          <dd>{{ displayField(profileContact.role) }}</dd>
        </div>
        <div>
          <dt>直属上级</dt>
          <dd>{{ displayField(profileContact.managerName) }}</dd>
        </div>
      </dl>
      <p v-if="profileContact.signature" class="contact-signature">
        <MessageSquareText :size="15" />
        <span>{{ profileContact.signature }}</span>
      </p>
    </section>
  </AppDialog>

  <div v-if="localToast" class="toast-message" role="status" aria-live="polite">
    {{ localToast }}
  </div>
</div>
</template>
