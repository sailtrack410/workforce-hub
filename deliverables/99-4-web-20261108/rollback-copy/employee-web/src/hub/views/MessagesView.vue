<script setup lang="ts">
/**
 * 员工端新消息页：对齐画布「员工端-新消息」3:2204。
 * 三栏：会话列表 344 / 聊天窗口 弹性 / 会话详情栏 300，挂 HubShell。
 * 全新实现：UI 全部使用 hub/components 新组件；页面逻辑（草稿/引用/撤回/菜单/建群）自含。
 * 数据通道：stores/im + useImSocket + usePersonalFileTransfer。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  AtSign, MessageSquare, Paperclip, Plus, Search, Send, UsersRound, X, Quote,
} from 'lucide-vue-next';
import HubShell from '../layouts/HubShell.vue';
import HubAvatar from '../components/HubAvatar.vue';
import HubDialog from '../components/HubDialog.vue';
import HubButton from '../components/HubButton.vue';
import HubEmpty from '../components/HubEmpty.vue';
import ImBubble from '../components/im/ImBubble.vue';
import ImKnowledgeCard from '../components/im/ImKnowledgeCard.vue';
import ImProcessCard from '../components/im/ImProcessCard.vue';
import { useShellStore } from '@/stores/shell';
import { useImStore } from '@/stores/im';
import { useSessionStore } from '@/stores/session';
import { useImSocket } from '@/composables/useImSocket';
import { usePersonalFileTransfer } from '@/composables/usePersonalFileTransfer';
import { employeeApi } from '@/api';
import type {
  AgentResponse,
  EntityId,
} from '@/api';
import type { KnowledgeAnswerAction } from '@/types/knowledge';
import type {
  ConversationAttachment,
  ConversationMessage,
  ConversationProcessCard,
  ConversationRecord,
  MessageMention,
  PersonalFileRecord,
} from '@/stores/shell';

const shell = useShellStore();
const im = useImStore();
const session = useSessionStore();
const route = useRoute();
const router = useRouter();

/* ---------- toast ---------- */
const toastMessage = ref('');
let toastTimer: number | undefined;
function showToast(message: string) {
  toastMessage.value = message;
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => { toastMessage.value = ''; }, 2600);
}

/* ---------- 头像与文件 ---------- */
const { resolveDownloadUrl, uploadImMessageAttachment } = usePersonalFileTransfer();

async function refreshContactAvatarUrls() {
  const contacts = shell.contacts.filter((item) => item.avatarAttachmentRefId && !item.avatarUrl);
  await Promise.all(contacts.map(async (contact) => {
    if (!contact.avatarAttachmentRefId) return;
    try {
      const ticket = await employeeApi.files.createDownloadTicket(contact.avatarAttachmentRefId, { downloadType: 'INLINE' });
      contact.avatarUrl = resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo));
    } catch {
      contact.avatarUrl = '';
    }
  }));
}

// 附件预览：hub 版先以新窗口打开下载/预览链接（预览面板属后续迭代）
async function openAttachmentUrl(_file: PersonalFileRecord, url: string) {
  window.open(url, '_blank', 'noopener');
}

/* ---------- 会话消息同步（拉消息+详情+已读回写） ---------- */
async function syncConversationMessages(id: string) {
  const conversationId = id.trim();
  if (!conversationId || !session.isAuthenticated) return;
  await Promise.all([
    im.loadMessages(conversationId, session.user?.userId),
    im.loadConversationDetail(conversationId, undefined, session.user?.userId).catch(() => undefined),
  ]);
  shell.mergeBackendConversations(im.conversations);
  shell.setColleagues(session.colleagues);
  shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
  void refreshContactAvatarUrls();
  const messages = im.messagesByConversation[conversationId];
  if (messages?.length) {
    shell.mergeBackendMessages(conversationId, messages);
    const lastSeq = im.lastMessageSeqByConversation[conversationId];
    const lastReadSeq = im.lastReadSeqByConversation[conversationId] ?? 0;
    if (typeof lastSeq === 'number' && lastSeq > lastReadSeq) {
      try {
        const updated = await im.updateReadPosition(conversationId, lastSeq);
        const conversation = shell.conversations.find((item) => item.id === conversationId);
        if (conversation) {
          conversation.unread = updated.unreadCount;
          conversation.unreadLabel = updated.unreadCount > 0 ? '未读消息' : '已读';
        }
      } catch {
        // 已读回写失败不阻断展示
      }
    }
  }
}

const { wsConnected, closeImSocket, connectImSocket } = useImSocket({
  im,
  session,
  shell,
  syncConversationMessages,
});

/* ---------- 当前会话与切换 ---------- */
const activeConversation = computed<ConversationRecord>(() =>
  shell.conversations.find((item) => item.id === shell.active.messages) ?? ({} as ConversationRecord));
const hasActiveConversation = computed(() => Boolean(activeConversation.value.id));
const activeTitle = computed(() => {
  const c = activeConversation.value;
  return c.memberCount ? `${c.title}（${c.memberCount}）` : c.title;
});

function navigateMessagesConversation(id: string) {
  shell.selectItem('messages', id);
  if (route.params.conversationId !== id) {
    void router.push({ name: 'hub-messages-detail', params: { conversationId: id } });
  }
}
function selectConversation(id: string) {
  navigateMessagesConversation(id);
  void syncConversationMessages(id).then(scrollToBottom);
}

/* ---------- 会话列表：筛选 + 搜索 ---------- */
type ListFilter = '全部' | '@我' | '单聊' | '群组' | 'AI';
const LIST_FILTERS: ListFilter[] = ['全部', '@我', '单聊', '群组', 'AI'];
const activeFilter = ref<ListFilter>('全部');
const keyword = ref('');

const filteredConversations = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return shell.conversations.filter((item) => {
    if (activeFilter.value === '@我' && !item.hasMentionMe) return false;
    if (activeFilter.value === '群组' && item.kind !== 'team') return false;
    if (activeFilter.value === 'AI' && item.kind !== 'agent') return false;
    if (activeFilter.value === '单聊' && !(item.kind === 'private' || item.kind === 'approval' || item.kind === 'attendance' || item.kind === 'file')) return false;
    if (kw && !(item.title.toLowerCase().includes(kw) || item.subtitle.toLowerCase().includes(kw))) return false;
    return true;
  });
});

function conversationAvatar(item: ConversationRecord) {
  return item.avatarUrl || '';
}
function messageAvatarUrl(message: ConversationMessage) {
  // 无真实头像时回退空串，由 HubAvatar 显示首字+彩色底
  if (message.side === 'system') return '';
  if (message.side === 'right') return shell.avatarUrl || '';
  if (message.senderType === 'AGENT') return activeConversation.value.avatarUrl || '';
  if (message.senderId !== undefined && message.senderId !== null) {
    const contact = shell.contacts.find((item) => (
      item.userId !== undefined && item.userId !== null && String(item.userId) === String(message.senderId)
    ));
    if (contact?.avatarUrl) return contact.avatarUrl;
  }
  return '';
}

/* ---------- 消息流滚动 ---------- */
const messageStream = ref<HTMLElement | null>(null);
function scrollToBottom() {
  void nextTick(() => {
    const el = messageStream.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}
watch(
  () => activeConversation.value.messages?.length,
  () => scrollToBottom(),
);

/* ---------- 草稿与发送 ---------- */
const draft = ref('');
const sending = computed(() => im.messageSending);

async function sendMessage() {
  const text = draft.value.trim();
  if (!text || !activeConversation.value.id || sending.value) return;
  try {
    const mentionIds = parseMentionIds(text);
    const record = await im.sendMessage(
      activeConversation.value.id,
      text,
      session.user?.userId,
      undefined,
      quotedMessage.value ? quotedMessage.value.id : undefined,
      mentionIds,
    );
    if (record) {
      const messages = im.messagesByConversation[activeConversation.value.id];
      if (messages?.length) shell.mergeBackendMessages(activeConversation.value.id, messages);
    }
    draft.value = '';
    quotedMessage.value = null;
    scrollToBottom();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '消息发送失败');
  }
}

// 从文本解析 @名字 → userId（命中联系人）
function parseMentionIds(text: string): EntityId[] {
  const ids: EntityId[] = [];
  for (const contact of shell.contacts) {
    const names = [contact.name, contact.originalName].filter(Boolean) as string[];
    if (names.some((n) => text.includes(`@${n}`)) && contact.userId !== undefined && contact.userId !== null) {
      ids.push(contact.userId);
    }
  }
  return ids;
}
const draftMentionNames = computed(() =>
  shell.contacts
    .filter((c) => [c.name, c.originalName].some((n) => n && draft.value.includes(`@${n}`)))
    .map((c) => `@${c.originalName || c.name}`),
);

function insertAgentMention() {
  draft.value = `${draft.value}@`.replace(/@@+$/, '@');
}

/* ---------- 引用 ---------- */
const quotedMessage = ref<ConversationMessage | null>(null);
const quotedPreview = computed(() => {
  const m = quotedMessage.value;
  if (!m) return null;
  return { id: m.id, author: m.author, body: m.body || '[附件]', visible: true };
});
function resolveQuote(id?: EntityId | null) {
  if (id === undefined || id === null) return null;
  const found = (activeConversation.value.messages ?? []).find((m) => String(m.id) === String(id));
  return found ? { id: found.id, author: found.author, body: found.body || '[附件]', visible: true } : null;
}
function jumpToQuoted(id: string) {
  const el = messageStream.value?.querySelector(`[data-message-id="${id}"]`);
  if (el instanceof HTMLElement) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

/* ---------- 消息操作菜单 ---------- */
const menuTarget = ref<ConversationMessage | null>(null);
const menuPos = ref<{ x: number; y: number } | null>(null);
const menuCanRecall = computed(() => {
  const m = menuTarget.value;
  if (!m || m.side !== 'right' || m.status === 'RECALLED' || !m.time) return false;
  const sentAt = new Date(m.time.replace(' ', 'T')).getTime();
  return !Number.isNaN(sentAt) && Date.now() - sentAt <= 2 * 60 * 1000;
});

function openMessageMenu(message: ConversationMessage, event: MouseEvent) {
  menuTarget.value = message;
  menuPos.value = {
    x: Math.max(8, Math.min(event.clientX, window.innerWidth - 140)),
    y: Math.max(8, Math.min(event.clientY, window.innerHeight - 140)),
  };
}
function closeMessageMenu() {
  menuTarget.value = null;
  menuPos.value = null;
}
async function runMenuAction(action: 'copy' | 'quote' | 'delete-or-recall') {
  const m = menuTarget.value;
  if (!m) return;
  closeMessageMenu();
  if (action === 'copy') {
    try {
      await navigator.clipboard.writeText(m.body || '');
      showToast('已复制');
    } catch {
      showToast('复制失败');
    }
    return;
  }
  if (action === 'quote') {
    quotedMessage.value = m;
    return;
  }
  try {
    if (menuCanRecall.value) {
      await im.recallMessage(activeConversation.value.id, m.id, session.user?.userId);
      showToast('已撤回');
    } else {
      await im.deleteMessage(activeConversation.value.id, m.id);
      showToast('已删除');
    }
    const messages = im.messagesByConversation[activeConversation.value.id];
    if (messages) shell.mergeBackendMessages(activeConversation.value.id, messages);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  }
}

/* ---------- 附件发送 ---------- */
const attachmentInput = ref<HTMLInputElement | null>(null);
const attachmentSending = ref(false);
function openAttachmentPicker() {
  attachmentInput.value?.click();
}
async function handleAttachmentSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !activeConversation.value.id || attachmentSending.value) return;
  attachmentSending.value = true;
  try {
    const uploaded = await uploadImMessageAttachment(file);
    await im.sendFileMessage(activeConversation.value.id, uploaded.attachmentRefId, '', session.user?.userId);
    const messages = im.messagesByConversation[activeConversation.value.id];
    if (messages?.length) shell.mergeBackendMessages(activeConversation.value.id, messages);
    showToast('文件已发送');
    scrollToBottom();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '文件发送失败');
  } finally {
    attachmentSending.value = false;
  }
}
async function openAttachment(message: ConversationMessage, file: ConversationAttachment) {
  if (!file.attachmentRefId) return;
  try {
    const ticket = await employeeApi.files.createDownloadTicket(file.attachmentRefId, { downloadType: 'INLINE' });
    window.open(resolveDownloadUrl(ticket, employeeApi.files.downloadUrl(ticket.ticketNo)), '_blank', 'noopener');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '附件打开失败');
  }
}

/* ---------- 卡片动作（知识卡/流程卡） ---------- */
function handleKnowledgeAction(action: KnowledgeAnswerAction) {
  showToast(`「${action.label}」即将上线`);
}
function handleProcessAction(_card: ConversationProcessCard) {
  showToast('流程操作即将上线');
}

/* ---------- 新建会话 ---------- */
const createOpen = ref(false);
const createMode = ref<'private' | 'group' | 'agent'>('private');
const createTargetUserId = ref('');
const createGroupTitle = ref('');
const createMemberIds = ref<Set<string>>(new Set());
const createAgentId = ref('');
const agentKeyword = ref('');
const agentResults = ref<AgentResponse[]>([]);
const agentSearching = ref(false);
const createSubmitting = ref(false);

const selectableContacts = computed(() => shell.contacts.filter((item) => (
  item.relation !== '本人' && item.userId !== undefined && item.userId !== null
)));
function contactSubtitle(contact: { role?: string; department?: string; relation?: string }) {
  return [contact.department, contact.role, contact.relation].filter(Boolean).join(' · ');
}
function toggleCreateMember(userId: EntityId) {
  const id = String(userId);
  const next = new Set(createMemberIds.value);
  if (next.has(id)) next.delete(id); else next.add(id);
  createMemberIds.value = next;
}
function openCreate(mode: 'private' | 'group' | 'agent') {
  createMode.value = mode;
  createOpen.value = true;
  if (mode === 'agent' && !agentResults.value.length) void searchAgents();
}
function closeCreate() {
  createOpen.value = false;
  createTargetUserId.value = '';
  createGroupTitle.value = '';
  createMemberIds.value = new Set();
  createAgentId.value = '';
  agentKeyword.value = '';
}
async function searchAgents() {
  agentSearching.value = true;
  try {
    agentResults.value = await employeeApi.agents.list({ keyword: agentKeyword.value.trim() || undefined });
  } catch {
    agentResults.value = [];
  } finally {
    agentSearching.value = false;
  }
}
async function submitCreate() {
  if (createSubmitting.value) return;
  createSubmitting.value = true;
  try {
    let conversation: ConversationRecord | undefined;
    if (createMode.value === 'private') {
      if (!createTargetUserId.value) { showToast('请选择成员'); return; }
      conversation = await im.startPrivateConversation(createTargetUserId.value);
    } else if (createMode.value === 'group') {
      if (!createGroupTitle.value.trim()) { showToast('请输入群名称'); return; }
      if (!createMemberIds.value.size) { showToast('请选择成员'); return; }
      conversation = await im.createGroupConversation(createGroupTitle.value.trim(), Array.from(createMemberIds.value));
    } else {
      if (!createAgentId.value) { showToast('请选择 AI 助手'); return; }
      conversation = await im.startAgentConversation(createAgentId.value);
    }
    shell.mergeBackendConversations(im.conversations);
    closeCreate();
    if (conversation?.id) selectConversation(conversation.id);
    showToast('会话已创建');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '创建失败');
  } finally {
    createSubmitting.value = false;
  }
}

/* ---------- 群管理 ---------- */
const groupPanelOpen = ref(false);
const groupTitle = ref('');
const groupAddIds = ref<Set<string>>(new Set());
const groupSubmitting = ref(false);
const conversationDetail = computed(() => im.detailsByConversation[activeConversation.value.id] ?? null);
const groupMembers = computed(() => conversationDetail.value?.members ?? []);

async function openGroupPanel() {
  if (!activeConversation.value.id) return;
  try {
    const detail = await im.loadConversationDetail(activeConversation.value.id);
    groupTitle.value = detail.groupProfile?.title ?? activeConversation.value.title;
    groupAddIds.value = new Set();
    groupPanelOpen.value = true;
  } catch (error) {
    showToast(error instanceof Error ? error.message : '群资料加载失败');
  }
}
function toggleGroupAdd(userId: EntityId) {
  const id = String(userId);
  const next = new Set(groupAddIds.value);
  if (next.has(id)) next.delete(id); else next.add(id);
  groupAddIds.value = next;
}
async function saveGroupPanel() {
  if (groupSubmitting.value || !activeConversation.value.id) return;
  groupSubmitting.value = true;
  try {
    if (groupTitle.value.trim() && groupTitle.value.trim() !== activeConversation.value.title) {
      await im.updateGroupProfile(activeConversation.value.id, { title: groupTitle.value.trim() });
    }
    if (groupAddIds.value.size) {
      await im.addGroupMembers(activeConversation.value.id, Array.from(groupAddIds.value));
    }
    await im.loadConversationDetail(activeConversation.value.id).catch(() => undefined);
    shell.mergeBackendConversations(im.conversations);
    groupPanelOpen.value = false;
    showToast('已保存');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '保存失败');
  } finally {
    groupSubmitting.value = false;
  }
}
async function leaveActiveGroup() {
  if (!activeConversation.value.id) return;
  try {
    await im.leaveGroup(activeConversation.value.id);
    shell.mergeBackendConversations(im.conversations);
    showToast('已退出群聊');
    if (shell.conversations.length) selectConversation(shell.conversations[0].id);
    else void router.push({ name: 'hub-messages' });
  } catch (error) {
    showToast(error instanceof Error ? error.message : '退出失败');
  }
}

/* ---------- 详情栏 ---------- */
const detailKind = computed(() => activeConversation.value.kind);
const agentQuickQuestions = computed(() => activeConversation.value.panel?.tasks ?? []);

/* ---------- 生命周期 ---------- */
onMounted(async () => {
  if (!session.isAuthenticated) {
    void router.replace('/login');
    return;
  }
  await Promise.allSettled([
    im.loadConversations(),
    session.loadOrgProfile(),
  ]);
  shell.mergeBackendConversations(im.conversations);
  shell.setColleagues(session.colleagues);
  shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
  void refreshContactAvatarUrls();
  connectImSocket();
  const routeConversationId = typeof route.params.conversationId === 'string' ? route.params.conversationId : '';
  if (routeConversationId) {
    shell.selectItem('messages', routeConversationId);
    void syncConversationMessages(routeConversationId).then(scrollToBottom);
  } else if (shell.conversations.length) {
    selectConversation(shell.conversations[0].id);
  }
});

watch(() => route.params.conversationId, (id) => {
  if (typeof id === 'string' && id && id !== activeConversation.value.id) {
    shell.selectItem('messages', id);
    void syncConversationMessages(id).then(scrollToBottom);
  }
});

onBeforeUnmount(() => {
  if (toastTimer) window.clearTimeout(toastTimer);
  closeImSocket();
});
</script>

<template>
  <HubShell>
  <div class="im-page" @click="closeMessageMenu">
    <!-- 会话列表 344 -->
    <aside class="im-list">
      <header class="im-list-head">
        <h3>消息</h3>
        <button type="button" class="im-icon-btn" aria-label="新建会话" @click="openCreate('private')">
          <Plus :size="16" :stroke-width="2" />
        </button>
      </header>
      <div class="im-filters" role="tablist">
        <button
          v-for="f in LIST_FILTERS" :key="f" type="button" role="tab"
          class="im-filter-chip" :class="{ active: activeFilter === f }"
          @click="activeFilter = f"
        >{{ f }}</button>
      </div>
      <label class="im-search">
        <Search :size="16" :stroke-width="2" />
        <input v-model="keyword" type="text" placeholder="搜索会话" />
      </label>
      <div class="im-conversations">
        <button
          v-for="c in filteredConversations" :key="c.id" type="button"
          class="im-conv" :class="{ active: c.id === activeConversation.id }"
          @click="selectConversation(c.id)"
        >
          <HubAvatar :label="c.title" :image="conversationAvatar(c)" size="lg" :agent="c.kind === 'agent'" />
          <span class="im-conv-copy">
            <strong>{{ c.title }}<em v-if="c.memberCount" class="im-conv-count">{{ c.memberCount }}人</em></strong>
            <small>{{ c.subtitle || c.summaryBody || '暂无消息' }}</small>
          </span>
          <span class="im-conv-side">
            <small>{{ c.meta }}</small>
            <i v-if="c.unread > 0" class="im-unread">{{ c.unread > 99 ? '99+' : c.unread }}</i>
          </span>
        </button>
        <HubEmpty v-if="!filteredConversations.length" title="没有匹配的会话" :icon="MessageSquare" />
      </div>
    </aside>

    <!-- 聊天窗口 弹性 -->
    <section v-if="hasActiveConversation" class="im-chat">
      <header class="im-chat-head">
        <div class="im-chat-title">
          <h3>{{ activeTitle }}</h3>
          <small>{{ activeConversation.meta }}<template v-if="wsConnected === false"> · 连接已断开</template></small>
        </div>
        <div class="im-chat-actions">
          <button
            v-if="detailKind === 'team'" type="button" class="im-head-btn"
            @click="openGroupPanel"
          >
            <UsersRound :size="16" :stroke-width="2" />
            群管理
          </button>
        </div>
      </header>

      <div ref="messageStream" class="im-stream">
        <template v-if="activeConversation.messages?.length">
          <article
            v-for="message in activeConversation.messages" :key="message.id"
            class="im-row" :class="[message.side]"
            :data-message-id="message.id"
          >
            <div v-if="message.side === 'system'" class="im-system-line">
              <strong>{{ message.author }}<template v-if="message.role"> · {{ message.role }}</template></strong>
              <span>{{ message.body }}</span>
            </div>
            <ImProcessCard
              v-else-if="message.type === 'process-card' && message.processCard"
              :card="message.processCard"
              :avatar-url="activeConversation.avatarUrl || ''"
              @action="handleProcessAction"
            />
            <ImKnowledgeCard
              v-else-if="message.type === 'knowledge-card' && message.knowledgeCard"
              :card="message.knowledgeCard"
              :avatar-url="activeConversation.avatarUrl || ''"
              @action="handleKnowledgeAction"
            />
            <ImBubble
              v-else
              :message="message"
              :avatar-url="messageAvatarUrl(message)"
              :show-author="detailKind === 'team' && message.side !== 'right'"
              :quote="resolveQuote(message.quotedMessageId)"
              @action="openMessageMenu"
              @attachment="openAttachment"
              @quote="jumpToQuoted"
            />
          </article>
        </template>
        <HubEmpty v-else title="暂无消息，说点什么吧" :icon="MessageSquare" />
      </div>

      <footer class="im-composer">
        <div v-if="quotedPreview" class="im-quote-bar">
          <button class="im-quote-card" type="button" @click="jumpToQuoted(quotedPreview.id)">
            <Quote :size="14" />
            <span>
              <strong>{{ quotedPreview.author }}</strong>
              <em>{{ quotedPreview.body }}</em>
            </span>
          </button>
          <button class="im-icon-btn" type="button" aria-label="取消引用" @click="quotedMessage = null">
            <X :size="15" />
          </button>
        </div>
        <div class="im-composer-row">
          <button
            type="button" class="im-icon-btn" aria-label="发送附件"
            :disabled="attachmentSending" @click="openAttachmentPicker"
          >
            <Paperclip :size="16" :stroke-width="2" />
          </button>
          <input ref="attachmentInput" type="file" hidden @change="handleAttachmentSelected" />
          <div class="im-input-wrap">
            <div v-if="draftMentionNames.length" class="im-mention-preview" aria-hidden="true">
              <span v-for="name in draftMentionNames" :key="name">{{ name }}</span>
            </div>
            <input
              v-model="draft" type="text" placeholder="输入消息，支持 @同事"
              :disabled="sending" @keydown.enter.prevent="sendMessage"
            />
          </div>
          <button type="button" class="im-mention-btn" aria-label="@提及" @click="insertAgentMention">
            <AtSign :size="14" :stroke-width="2" />
          </button>
          <button
            type="button" class="im-send-btn"
            :disabled="sending || !draft.trim()" @click="sendMessage"
          >
            <Send :size="16" :stroke-width="2" />
            发送
          </button>
        </div>
      </footer>
    </section>

    <section v-else class="im-chat im-chat-empty">
      <HubEmpty title="选择一个会话开始聊天" :icon="MessageSquare">
        <HubButton variant="primary" size="sm" @click="openCreate('private')">新建会话</HubButton>
      </HubEmpty>
    </section>

    <!-- 会话详情栏 300 -->
    <aside v-if="hasActiveConversation" class="im-detail">
      <div class="im-profile">
        <HubAvatar :label="activeConversation.title" :image="activeConversation.avatarUrl || ''" size="xl" :agent="detailKind === 'agent'" />
        <strong>{{ activeConversation.title }}</strong>
        <small>{{ activeConversation.meta }}</small>
      </div>

      <template v-if="detailKind === 'team'">
        <h4 class="im-detail-title">群成员<em>{{ groupMembers.length }} 人</em></h4>
        <div class="im-members">
          <span v-for="m in groupMembers.slice(0, 12)" :key="String(m.memberId)" class="im-member" :title="m.displayName">
            <HubAvatar :label="m.displayName" size="sm" :agent="m.memberType === 'AGENT'" />
          </span>
        </div>
        <button type="button" class="im-danger-link" @click="leaveActiveGroup">退出群聊</button>
      </template>

      <template v-else-if="detailKind === 'agent'">
        <template v-if="agentQuickQuestions.length">
          <h4 class="im-detail-title">快捷提问</h4>
          <button
            v-for="q in agentQuickQuestions" :key="q" type="button" class="im-quick-q"
            @click="draft = q"
          >{{ q }}</button>
        </template>
        <p v-else class="im-detail-note">向 TA 提问制度、假期、流程问题，回答可引用知识库出处。</p>
      </template>

      <template v-else>
        <h4 class="im-detail-title">会话信息</h4>
        <p class="im-detail-note">{{ activeConversation.subtitle || activeConversation.summaryBody || '暂无更多信息' }}</p>
      </template>
    </aside>

    <!-- 新建会话弹窗 -->
    <HubDialog v-if="createOpen" @close="closeCreate">
      <form class="im-dialog" @submit.prevent="submitCreate">
        <header class="im-dialog-head">
          <h2>{{ createMode === 'private' ? '新建私聊' : createMode === 'group' ? '新建群聊' : 'AI 会话' }}</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="closeCreate">
            <X :size="15" />
          </button>
        </header>
        <div class="im-mode-row">
          <button type="button" :class="{ active: createMode === 'private' }" @click="createMode = 'private'">私聊</button>
          <button type="button" :class="{ active: createMode === 'group' }" @click="createMode = 'group'">群聊</button>
          <button type="button" :class="{ active: createMode === 'agent' }" @click="openCreate('agent')">AI</button>
        </div>

        <section v-if="createMode === 'private'" class="im-dialog-body">
          <span class="im-field-label">选择成员</span>
          <div class="im-member-picker">
            <button
              v-for="contact in selectableContacts" :key="`private-${contact.id}`" type="button"
              class="im-member-row" :class="{ active: createTargetUserId === String(contact.userId) }"
              @click="createTargetUserId = String(contact.userId)"
            >
              <HubAvatar :label="contact.name" :image="contact.avatarUrl" size="sm" />
              <span><strong>{{ contact.remarkName || contact.name }}</strong><small>{{ contactSubtitle(contact) }}</small></span>
            </button>
          </div>
        </section>

        <section v-else-if="createMode === 'group'" class="im-dialog-body">
          <label class="im-field-label">群名称
            <input v-model="createGroupTitle" type="text" placeholder="输入群名称" />
          </label>
          <span class="im-field-label">选择成员（{{ createMemberIds.size }}）</span>
          <div class="im-member-picker">
            <button
              v-for="contact in selectableContacts" :key="`group-${contact.id}`" type="button"
              class="im-member-row" :class="{ active: createMemberIds.has(String(contact.userId)) }"
              @click="toggleCreateMember(contact.userId!)"
            >
              <HubAvatar :label="contact.name" :image="contact.avatarUrl" size="sm" />
              <span><strong>{{ contact.remarkName || contact.name }}</strong><small>{{ contactSubtitle(contact) }}</small></span>
            </button>
          </div>
        </section>

        <section v-else class="im-dialog-body">
          <label class="im-field-label">搜索 AI 助手
            <input v-model="agentKeyword" type="text" placeholder="输入关键词" @keydown.enter.prevent="searchAgents" />
          </label>
          <div class="im-member-picker">
            <p v-if="agentSearching" class="im-dialog-note">搜索中…</p>
            <button
              v-for="agent in agentResults" :key="agent.agentId" type="button"
              class="im-member-row" :class="{ active: createAgentId === String(agent.agentId) }"
              @click="createAgentId = String(agent.agentId)"
            >
              <HubAvatar :label="agent.agentName" size="sm" agent />
              <span><strong>{{ agent.agentName }}</strong><small>{{ agent.agentTypeName || agent.agentType }}</small></span>
            </button>
            <p v-if="!agentSearching && !agentResults.length" class="im-dialog-note">暂无可用 AI 助手</p>
          </div>
        </section>

        <footer class="im-dialog-foot">
          <HubButton variant="secondary" :disabled="createSubmitting" @click="closeCreate">取消</HubButton>
          <HubButton variant="primary" :disabled="createSubmitting" @click="submitCreate">
            {{ createSubmitting ? '创建中…' : '创建' }}
          </HubButton>
        </footer>
      </form>
    </HubDialog>

    <!-- 群管理弹窗 -->
    <HubDialog v-if="groupPanelOpen" @close="groupPanelOpen = false">
      <form class="im-dialog" @submit.prevent="saveGroupPanel">
        <header class="im-dialog-head">
          <h2>群管理</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="groupPanelOpen = false">
            <X :size="15" />
          </button>
        </header>
        <section class="im-dialog-body">
          <label class="im-field-label">群名称
            <input v-model="groupTitle" type="text" placeholder="输入群名称" />
          </label>
          <span class="im-field-label">当前成员（{{ groupMembers.length }}）</span>
          <div class="im-member-picker">
            <div v-for="m in groupMembers" :key="`member-${m.memberId}`" class="im-member-row static">
              <HubAvatar :label="m.displayName" size="sm" :agent="m.memberType === 'AGENT'" />
              <span><strong>{{ m.displayName }}</strong><small>{{ m.memberTypeName || m.memberType }}</small></span>
            </div>
          </div>
          <span class="im-field-label">添加成员（{{ groupAddIds.size }}）</span>
          <div class="im-member-picker">
            <button
              v-for="contact in selectableContacts" :key="`add-${contact.id}`" type="button"
              class="im-member-row" :class="{ active: groupAddIds.has(String(contact.userId)) }"
              @click="toggleGroupAdd(contact.userId!)"
            >
              <HubAvatar :label="contact.name" :image="contact.avatarUrl" size="sm" />
              <span><strong>{{ contact.remarkName || contact.name }}</strong><small>{{ contactSubtitle(contact) }}</small></span>
            </button>
          </div>
        </section>
        <footer class="im-dialog-foot">
          <HubButton variant="secondary" :disabled="groupSubmitting" @click="groupPanelOpen = false">取消</HubButton>
          <HubButton variant="primary" :disabled="groupSubmitting" @click="saveGroupPanel">
            {{ groupSubmitting ? '保存中…' : '保存' }}
          </HubButton>
        </footer>
      </form>
    </HubDialog>

    <!-- 消息操作菜单 -->
    <div
      v-if="menuPos && menuTarget" class="im-msg-menu"
      :style="{ left: `${menuPos.x}px`, top: `${menuPos.y}px` }"
      @click.stop
    >
      <button type="button" @click="runMenuAction('copy')">复制</button>
      <button type="button" @click="runMenuAction('quote')">引用</button>
      <button type="button" class="danger" @click="runMenuAction('delete-or-recall')">
        {{ menuCanRecall ? '撤回' : '删除' }}
      </button>
    </div>

    <transition name="im-toast">
      <div v-if="toastMessage" class="im-toast">{{ toastMessage }}</div>
    </transition>
  </div>
  </HubShell>
</template>

<style>
@import '../styles/messages.css';
</style>
