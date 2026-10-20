<script setup lang="ts">
/**
 * hub-web 消息页：对齐画布「员工端-新消息」3:2204 及其全部状态帧。
 * 三栏：会话列表 344 / 聊天窗口 弹性 / 会话详情栏 300。
 * 弹层与细节：新建会话、群管理（资料/公告/成员增删/退出解散）、@提及面板、附件菜单、消息操作菜单。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  AtSign, BellOff, BookUser, Check, ChevronRight, Cloud, Download, FileText,
  FolderOpen, Image, MessageSquare, PanelRightClose, PanelRightOpen, Paperclip,
  Pin, Plus, Search, Send, UsersRound, X, Quote, Zap,
} from 'lucide-vue-next';
import HubAvatar from '@/components/base/HubAvatar.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import HubButton from '@/components/base/HubButton.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';
import HubToast from '@/components/base/HubToast.vue';
import { useToast } from '@/composables/useToast';
import ImBubble from '@/components/im/ImBubble.vue';
import ImKnowledgeCard from '@/components/im/ImKnowledgeCard.vue';
import ImProcessCard from '@/components/im/ImProcessCard.vue';
import { useSessionStore } from '@/stores/session';
import { useImStore, kindOf, lastMessageText } from '@/stores/im';
import { useImSocket } from '@/composables/useImSocket';
import { uploadImAttachment } from '@/composables/useUpload';
import { agentApi, contactsApi, fileApi, imApi } from '@/api';
import type { AgentResponse, EmployeeFileResponse, EntityId, ImConversationSummaryResponse, ImGroupFileResponse, ImGroupNoticeResponse, ImMessageAttachment, ImMessageResponse } from '@/api/types';
import { toMessageView, friendlyTime, TIME_DIVIDER_GAP, type MessageView } from '@/utils/message';

const session = useSessionStore();
const im = useImStore();
const route = useRoute();
const router = useRouter();
const { message: toastMessage, show: showToast } = useToast();

/* ---------- 当前会话 ---------- */
const activeId = computed(() => String(route.params.conversationId ?? ''));
const activeConversation = computed<ImConversationSummaryResponse | null>(() =>
  im.conversations.find((c) => String(c.conversationId) === activeId.value) ?? null);
const activeKind = computed(() => (activeConversation.value ? kindOf(activeConversation.value) : 'system'));
const activeDetail = computed(() => (activeId.value ? im.detailsByConversation[activeId.value] ?? null : null));
const groupMembers = computed(() => activeDetail.value?.members ?? []);
const activeMessages = computed<MessageView[]>(() =>
  (activeId.value ? im.messagesByConversation[activeId.value] ?? [] : []).map((m) =>
    toMessageView(m, session.user?.userId)),
);
const activeTitle = computed(() => {
  const c = activeConversation.value;
  if (!c) return '';
  // #1 联调：优先用会话摘要的 memberCount，detail 成员数为兜底
  const count = c.memberCount ?? activeDetail.value?.members?.length;
  return count ? `${c.title}（${count}）` : c.title;
});
const activeSubtitle = computed(() => {
  const c = activeConversation.value;
  if (!c) return '';
  const kindText = { team: '群聊', agent: 'AI 助手', private: '私聊', system: '系统会话' }[activeKind.value];
  return `${kindText} · ${(c.lastMessageAt || '').slice(5, 10) || ''}`;
});

async function syncConversation(id: string) {
  if (!id || !session.isAuthenticated) return;
  await Promise.all([
    im.loadMessages(id),
    im.loadDetail(id).catch(() => undefined),
  ]);
  void im.markRead(id);
}

function selectConversation(id: string) {
  if (id === activeId.value) return;
  void router.push({ name: 'messages-detail', params: { conversationId: id } });
}

/* ---------- 实时 ---------- */
const { connected: wsConnected, connect: connectSocket, close: closeSocket } = useImSocket({
  onMessage: (conversationId) => {
    if (conversationId === activeId.value) {
      scrollToBottom();
      void im.markRead(conversationId);
    } else {
      void im.loadConversations().catch(() => undefined);
    }
  },
});

/* ---------- 聊天头：记录搜索 + 面板切换（画布 3:2303） ---------- */
const recordSearchOpen = ref(false);
const recordKeyword = ref('');
const detailOpen = ref(true);

const visibleMessages = computed<MessageView[]>(() => {
  const kw = recordKeyword.value.trim();
  if (!kw) return activeMessages.value;
  return activeMessages.value.filter((m) =>
    `${m.body}${m.author}`.includes(kw));
});

/* 时间分隔条（批次⑨）：首条必显示，后续间隔 >5 分钟才显示 */
type StreamItem =
  | { type: 'time'; key: string; label: string }
  | { type: 'msg'; key: string; message: MessageView };
const streamItems = computed<StreamItem[]>(() => {
  const items: StreamItem[] = [];
  let lastTime = 0;
  for (const m of visibleMessages.value) {
    const t = m.sentAt ? new Date(m.sentAt).getTime() : 0;
    if (!items.length || (t && t - lastTime > TIME_DIVIDER_GAP)) {
      const label = friendlyTime(m.sentAt);
      if (label) items.push({ type: 'time', key: `time-${m.id}`, label });
    }
    if (t) lastTime = t;
    items.push({ type: 'msg', key: m.id, message: m });
  }
  return items;
});

/* ---------- 会话列表 ---------- */
type ListFilter = '全部' | '@我' | '单聊' | '群组' | 'AI';
const LIST_FILTERS: ListFilter[] = ['全部', '@我', '单聊', '群组', 'AI'];
const validFilters: ListFilter[] = ['全部', '@我', '单聊', '群组', 'AI'];
const activeFilter = ref<ListFilter>(
  validFilters.includes(String(route.query.section) as ListFilter)
    ? (String(route.query.section) as ListFilter)
    : '全部',
);
watch(() => route.query.section, (s) => {
  if (typeof s === 'string' && validFilters.includes(s as ListFilter)) {
    activeFilter.value = s as ListFilter;
  }
});
const keyword = ref('');

const filteredConversations = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return im.conversations
    .filter((c) => {
      const kind = kindOf(c);
      if (activeFilter.value === '@我' && !c.hasMentionMe) return false;
      if (activeFilter.value === '群组' && kind !== 'team') return false;
      if (activeFilter.value === 'AI' && kind !== 'agent') return false;
      if (activeFilter.value === '单聊' && !(kind === 'private' || kind === 'system')) return false;
      if (kw && !(`${c.title}${lastMessageText(c)}`.toLowerCase().includes(kw))) return false;
      return true;
    })
    // 置顶优先，其次按最后消息时间倒序
    .sort((a, b) => {
      if (Boolean(a.pin) !== Boolean(b.pin)) return a.pin ? -1 : 1;
      return (b.lastMessageAt || '').localeCompare(a.lastMessageAt || '');
    });
});

/* ---------- 消息流滚动 ---------- */
const messageStream = ref<HTMLElement | null>(null);
function scrollToBottom() {
  void nextTick(() => {
    const el = messageStream.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}
watch(() => activeMessages.value.length, scrollToBottom);

/* ---------- 头像 ---------- */
function conversationAvatar(c: ImConversationSummaryResponse) {
  return '';
}
function senderAvatar(m: MessageView) {
  if (m.side === 'right') return '';
  return '';
}

/* ---------- 引用 ---------- */
const quotedMessage = ref<MessageView | null>(null);
function resolveQuote(id?: EntityId | null) {
  if (id === undefined || id === null) return null;
  const found = activeMessages.value.find((m) => String(m.id) === String(id));
  return found ? { id: found.id, author: found.author, body: found.body || '[附件]' } : null;
}
function jumpToQuoted(id: string) {
  const el = messageStream.value?.querySelector(`[data-message-id="${id}"]`);
  if (el instanceof HTMLElement) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

/* ---------- 发送 ---------- */
const draft = ref('');
const sending = computed(() => im.sending);

async function sendMessage() {
  const text = draft.value.trim();
  if (!text || !activeId.value || sending.value) return;
  try {
    await im.sendText(activeId.value, text, {
      quotedMessageId: quotedMessage.value?.id,
      mentionUserIds: mentionedUserIds.value,
    });
    draft.value = '';
    quotedMessage.value = null;
    mentionPanelUserIds.value = new Set();
    scrollToBottom();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '消息发送失败');
  }
}

/* ---------- @提及：文本解析 + 面板 ---------- */
const mentionPanelOpen = ref(false);
const mentionPanelUserIds = ref<Set<string>>(new Set());
const mentionCandidates = computed(() =>
  groupMembers.value.filter((m) => m.memberType !== 'AGENT'),
);
const mentionedUserIds = computed<EntityId[]>(() => {
  const ids: EntityId[] = [...mentionPanelUserIds.value];
  for (const contact of session.contacts) {
    const names = [contact.name, contact.originalName].filter(Boolean) as string[];
    if (names.some((n) => draft.value.includes(`@${n}`))) ids.push(contact.userId);
  }
  return Array.from(new Set(ids));
});
// 供气泡高亮使用的全部可提及名字
const allMentionNames = computed(() =>
  session.contacts.flatMap((c) => [c.name, c.originalName].filter(Boolean) as string[]),
);
const draftMentionNames = computed(() =>
  session.contacts
    .filter((c) => [c.name, c.originalName].some((n) => n && draft.value.includes(`@${n}`)))
    .map((c) => `@${c.originalName || c.name}`),
);

function onDraftInput(event: Event) {
  const value = (event.target as HTMLInputElement).value;
  // 光标前刚输入 @ 且当前是群聊 → 弹出成员面板
  mentionPanelOpen.value = activeKind.value === 'team' && /@$/.test(value);
}
function insertMention(member: { memberId: EntityId; displayName: string }) {
  draft.value = `${draft.value}${member.displayName} `;
  const next = new Set(mentionPanelUserIds.value);
  next.add(String(member.memberId));
  mentionPanelUserIds.value = next;
  mentionPanelOpen.value = false;
}
function insertAtSymbol() {
  draft.value = `${draft.value}@`;
  mentionPanelOpen.value = activeKind.value === 'team';
}

/* ---------- 附件菜单 ---------- */
const attachMenuOpen = ref(false);
const attachInput = ref<HTMLInputElement | null>(null);
const attachSending = ref(false);
function openAttachMenu() {
  attachMenuOpen.value = !attachMenuOpen.value;
}
function pickAttachFile() {
  attachMenuOpen.value = false;
  attachInput.value?.click();
}
async function handleAttachSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !activeId.value || attachSending.value) return;
  attachSending.value = true;
  try {
    const uploaded = await uploadImAttachment(file);
    await im.sendFile(activeId.value, uploaded.attachmentRefId);
    showToast('文件已发送');
    scrollToBottom();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '文件发送失败');
  } finally {
    attachSending.value = false;
  }
}
async function openAttachment(file: ImMessageAttachment) {
  try {
    const ticket = await fileApi.createDownloadTicket(file.attachmentRefId, 'INLINE');
    window.open(ticket.downloadUrl || ticket.url || fileApi.downloadUrl(ticket.ticketNo), '_blank', 'noopener');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '附件打开失败');
  }
}

/* ---------- 消息操作菜单 ---------- */
const menuTarget = ref<MessageView | null>(null);
const menuPos = ref<{ x: number; y: number } | null>(null);
const menuCanRecall = computed(() => {
  const m = menuTarget.value;
  if (!m || m.side !== 'right' || m.recalled || !m.time) return false;
  return Date.now() - new Date(m.time.replace(' ', 'T')).getTime() <= 2 * 60 * 1000;
});
function openMessageMenu(message: MessageView, event: MouseEvent) {
  menuTarget.value = message;
  menuPos.value = {
    x: Math.max(8, Math.min(event.clientX, window.innerWidth - 140)),
    y: Math.max(8, Math.min(event.clientY, window.innerHeight - 170)),
  };
}
function closeMessageMenu() {
  menuTarget.value = null;
  menuPos.value = null;
  mentionPanelOpen.value = false;
  attachMenuOpen.value = false;
}
async function runMenuAction(action: 'copy' | 'quote' | 'forward' | 'delete-or-recall') {
  const m = menuTarget.value;
  if (!m) return;
  closeMessageMenu();
  if (action === 'copy') {
    await navigator.clipboard.writeText(m.body || '').then(
      () => showToast('已复制'),
      () => showToast('复制失败'),
    );
    return;
  }
  if (action === 'quote') {
    quotedMessage.value = m;
    return;
  }
  if (action === 'forward') {
    openForward(m);
    return;
  }
  try {
    if (menuCanRecall.value) {
      await im.recall(activeId.value, m.id);
      showToast('已撤回');
    } else {
      await im.remove(activeId.value, m.id);
      showToast('已删除');
    }
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  }
}

/* ---------- 卡片动作 ---------- */
function handleCardAction(label?: string) {
  showToast(`「${label || '操作'}」即将上线`);
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

const selectableContacts = computed(() =>
  session.contacts.filter((c) => String(c.userId) !== String(session.user?.userId ?? '')),
);
function contactSubtitle(contact: { role?: string | null; department?: string | null; relation?: string | null }) {
  return [contact.department, contact.role, contact.relation].filter(Boolean).join(' · ');
}
function toggleId(set: Set<string>, id: string) {
  const next = new Set(set);
  if (next.has(id)) next.delete(id); else next.add(id);
  return next;
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
    agentResults.value = await agentApi.list(agentKeyword.value.trim() || undefined);
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
    let conversation: ImConversationSummaryResponse | undefined;
    if (createMode.value === 'private') {
      if (!createTargetUserId.value) { showToast('请选择成员'); return; }
      conversation = await imApi.startPrivateConversation(createTargetUserId.value);
    } else if (createMode.value === 'group') {
      if (!createGroupTitle.value.trim()) { showToast('请输入群名称'); return; }
      if (!createMemberIds.value.size) { showToast('请选择成员'); return; }
      conversation = await imApi.createGroupConversation(createGroupTitle.value.trim(), [...createMemberIds.value]);
    } else {
      if (!createAgentId.value) { showToast('请选择 AI 助手'); return; }
      conversation = await imApi.startAgentConversation(createAgentId.value);
    }
    await im.loadConversations().catch(() => undefined);
    closeCreate();
    if (conversation?.conversationId) selectConversation(String(conversation.conversationId));
    showToast('会话已创建');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '创建失败');
  } finally {
    createSubmitting.value = false;
  }
}

/* ---------- 群管理（完整版） ---------- */
const groupPanelOpen = ref(false);
const groupTab = ref<'profile' | 'members' | 'notice'>('profile');
const groupTitle = ref('');
const groupNotice = ref('');
const groupSubmitting = ref(false);

async function openGroupPanel() {
  if (!activeId.value) return;
  try {
    const detail = await im.loadDetail(activeId.value);
    groupTitle.value = detail.groupProfile?.title ?? activeConversation.value?.title ?? '';
    groupNotice.value = detail.groupProfile?.noticeText ?? '';
    groupTab.value = 'profile';
    groupPanelOpen.value = true;
  } catch (error) {
    showToast(error instanceof Error ? error.message : '群资料加载失败');
  }
}
async function saveGroupPanel() {
  if (groupSubmitting.value || !activeId.value) return;
  groupSubmitting.value = true;
  try {
    const profile = activeDetail.value?.groupProfile;
    if (
      groupTitle.value.trim() !== (profile?.title ?? '') ||
      groupNotice.value.trim() !== (profile?.noticeText ?? '')
    ) {
      await imApi.updateGroupProfile(activeId.value, {
        title: groupTitle.value.trim() || undefined,
        noticeText: groupNotice.value.trim() || undefined,
      });
    }
    await Promise.all([
      im.loadDetail(activeId.value),
      im.loadConversations(),
    ]);
    groupPanelOpen.value = false;
    showToast('已保存');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '保存失败');
  } finally {
    groupSubmitting.value = false;
  }
}
async function leaveActiveGroup() {
  if (!activeId.value || leaveConfirming.value) return;
  leaveConfirming.value = true;
  try {
    await imApi.leaveGroup(activeId.value);
    await im.loadConversations();
    leaveConfirmOpen.value = false;
    showToast('已退出群聊');
    const first = im.conversations[0];
    if (first) selectConversation(String(first.conversationId));
    else void router.push({ name: 'messages' });
  } catch (error) {
    showToast(error instanceof Error ? error.message : '退出失败');
  } finally {
    leaveConfirming.value = false;
  }
}

/* ---------- 退群确认（画布 8:5409） ---------- */
const leaveConfirmOpen = ref(false);
const leaveConfirming = ref(false);

/* ---------- 添加群成员弹窗（画布 8:4881） ---------- */
const addMembersOpen = ref(false);
const addKeyword = ref('');
const addDept = ref('全部');
const addSelected = ref<Set<string>>(new Set());
const addSubmitting = ref(false);

const deptOptions = computed(() => {
  const depts = selectableContacts.value.map((c) => c.department).filter(Boolean) as string[];
  return ['全部', ...Array.from(new Set(depts))];
});
const addCandidates = computed(() => {
  const memberIds = new Set(groupMembers.value.map((m) => String(m.memberId)));
  const kw = addKeyword.value.trim();
  return selectableContacts.value.filter((c) => {
    if (memberIds.has(String(c.userId))) return false;
    if (addDept.value !== '全部' && c.department !== addDept.value) return false;
    if (kw && !(`${c.name}${c.remarkName ?? ''}`.includes(kw))) return false;
    return true;
  });
});

function openAddMembers() {
  addKeyword.value = '';
  addDept.value = '全部';
  addSelected.value = new Set();
  addMembersOpen.value = true;
}
async function submitAddMembers() {
  if (addSubmitting.value || !activeId.value) return;
  if (!addSelected.value.size) { showToast('请选择成员'); return; }
  addSubmitting.value = true;
  try {
    await imApi.addGroupMembers(activeId.value, [...addSelected.value]);
    await Promise.all([im.loadDetail(activeId.value), im.loadConversations()]);
    addMembersOpen.value = false;
    showToast(`已添加 ${addSelected.value.size} 名成员`);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '添加失败');
  } finally {
    addSubmitting.value = false;
  }
}

/* ---------- 移除群成员弹窗（画布 8:6233） ---------- */
const removeMembersOpen = ref(false);
const removeKeyword = ref('');
const removeDept = ref('全部');
const removingId = ref('');

const removeDeptOptions = computed(() => {
  const depts = groupMembers.value
    .map((m) => session.contacts.find((c) => String(c.userId) === String(m.memberId))?.department)
    .filter(Boolean) as string[];
  return ['全部', ...Array.from(new Set(depts))];
});
const removeCandidates = computed(() => {
  const kw = removeKeyword.value.trim();
  return groupMembers.value.filter((m) => {
    const contact = session.contacts.find((c) => String(c.userId) === String(m.memberId));
    if (removeDept.value !== '全部' && contact?.department !== removeDept.value) return false;
    if (kw && !(`${m.displayName}${contact?.remarkName ?? ''}`.includes(kw))) return false;
    return true;
  });
});

function openRemoveMembers() {
  removeKeyword.value = '';
  removeDept.value = '全部';
  removeMembersOpen.value = true;
}
async function removeOneMember(member: { memberId: EntityId; displayName: string }) {
  if (removingId.value || !activeId.value) return;
  removingId.value = String(member.memberId);
  try {
    await imApi.removeGroupMembers(activeId.value, [member.memberId]);
    await Promise.all([im.loadDetail(activeId.value), im.loadConversations()]);
    showToast(`已移除 ${member.displayName}`);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '移除失败');
  } finally {
    removingId.value = '';
  }
}

/* ---------- 批次③+⑫：偏好开关（四枚全通） ---------- */
const prefUpdating = ref(false);
type PrefKey = 'pin' | 'mute' | 'saveToContacts' | 'showMemberNickname';
const PREF_TOAST: Record<PrefKey, [string, string]> = {
  pin: ['已置顶', '已取消置顶'],
  mute: ['已开启免打扰', '已关闭免打扰'],
  saveToContacts: ['已保存到通讯录', '已从通讯录移除'],
  showMemberNickname: ['显示成员昵称', '显示真实姓名'],
};
async function togglePreference(key: PrefKey) {
  const c = activeConversation.value;
  if (!c || prefUpdating.value) return;
  prefUpdating.value = true;
  try {
    const updated = await imApi.updatePreference(c.conversationId, { [key]: !c[key] });
    im.upsertConversation(updated);
    const [onText, offText] = PREF_TOAST[key];
    showToast(updated[key] ? onText : offText);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '设置失败');
  } finally {
    prefUpdating.value = false;
  }
}

/* 群文件（仅群聊）：进入群会话时加载 */
const groupFiles = ref<ImGroupFileResponse[]>([]);
const groupFilesLoading = ref(false);
async function loadGroupFiles() {
  if (!activeId.value || activeKind.value !== 'team') {
    groupFiles.value = [];
    return;
  }
  groupFilesLoading.value = true;
  try {
    groupFiles.value = await imApi.groupFiles(activeId.value);
  } catch {
    groupFiles.value = [];
  } finally {
    groupFilesLoading.value = false;
  }
}
watch([activeId, activeKind], loadGroupFiles);

async function downloadGroupFile(file: ImGroupFileResponse) {
  try {
    const ticket = await imApi.createGroupFileDownloadTicket(file.conversationId, file.attachmentRefId);
    const url = ticket.downloadUrl || ticket.url || imApi.attachmentDownloadUrl(ticket.ticketNo);
    window.open(url, '_blank');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '下载失败');
  }
}
function formatSize(bytes?: number | null) {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

/* ---------- 批次④：查看全部成员 ---------- */
const allMembersOpen = ref(false);

/* ---------- 批次④：备注编辑弹窗（私聊） ---------- */
const remarkOpen = ref(false);
const remarkName = ref('');
const remarkSaving = ref(false);
const peerMember = computed(() => {
  if (activeKind.value !== 'private') return null;
  const myId = String(session.user?.userId ?? '');
  return groupMembers.value.find((m) => String(m.memberId) !== myId) ?? null;
});
function openRemark() {
  remarkName.value = String(peerMember.value?.displayName ?? activeConversation.value?.title ?? '');
  remarkOpen.value = true;
}
async function saveRemark() {
  if (remarkSaving.value || !peerMember.value) return;
  remarkSaving.value = true;
  try {
    await contactsApi.updateFriendRemark(peerMember.value.memberId, remarkName.value.trim());
    await Promise.all([im.loadConversations(), activeId.value ? im.loadDetail(activeId.value) : Promise.resolve()]);
    remarkOpen.value = false;
    showToast('备注已保存');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '保存失败（需先互为联系人）');
  } finally {
    remarkSaving.value = false;
  }
}

/* ---------- 批次④：群公告详情弹窗 ---------- */
const noticeDetailOpen = ref(false);

/* ---------- 批次④：转发弹窗 ---------- */
const forwardOpen = ref(false);
const forwardTarget = ref<MessageView | null>(null);
const forwardKeyword = ref('');
const forwardConversationId = ref('');
const forwardSending = ref(false);
const forwardCandidates = computed(() => {
  const kw = forwardKeyword.value.trim().toLowerCase();
  return im.conversations
    .filter((c) => String(c.conversationId) !== activeId.value)
    .filter((c) => !kw || c.title.toLowerCase().includes(kw))
    .slice(0, 20);
});
function openForward(message: MessageView) {
  forwardTarget.value = message;
  forwardKeyword.value = '';
  forwardConversationId.value = '';
  forwardOpen.value = true;
}
async function submitForward() {
  const target = forwardTarget.value;
  if (!target || !forwardConversationId.value || forwardSending.value) return;
  forwardSending.value = true;
  try {
    if (target.attachments.length) {
      for (const file of target.attachments) {
        await im.sendFile(forwardConversationId.value, file.attachmentRefId);
      }
    }
    if (target.body.trim()) {
      await im.sendText(forwardConversationId.value, target.body);
    }
    forwardOpen.value = false;
    showToast('已转发');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '转发失败');
  } finally {
    forwardSending.value = false;
  }
}

/* ---------- 批次④：云空间文件选择面板 ---------- */
const cloudOpen = ref(false);
const cloudFiles = ref<EmployeeFileResponse[]>([]);
const cloudLoading = ref(false);
const cloudKeyword = ref('');
const cloudSending = ref(false);
const filteredCloudFiles = computed(() => {
  const kw = cloudKeyword.value.trim().toLowerCase();
  if (!kw) return cloudFiles.value;
  return cloudFiles.value.filter((f) => f.displayName.toLowerCase().includes(kw));
});
async function openCloudPicker() {
  attachMenuOpen.value = false;
  cloudOpen.value = true;
  cloudKeyword.value = '';
  cloudLoading.value = true;
  try {
    const page = await fileApi.list({ pageNo: 1, pageSize: 50 });
    cloudFiles.value = page.list ?? [];
  } catch {
    cloudFiles.value = [];
  } finally {
    cloudLoading.value = false;
  }
}
async function sendCloudFile(file: EmployeeFileResponse) {
  if (cloudSending.value || !activeId.value) return;
  cloudSending.value = true;
  try {
    await im.sendFile(activeId.value, file.attachmentRefId);
    cloudOpen.value = false;
    scrollToBottom();
    showToast('已发送');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '发送失败');
  } finally {
    cloudSending.value = false;
  }
}

/* ---------- 批次⑫：本群昵称 / 清空记录 / 多公告 ---------- */
const myGroupNickname = computed(() => {
  const myId = String(session.user?.userId ?? '');
  const me = groupMembers.value.find((m) => String(m.memberId) === myId);
  return (me as { groupNickname?: string } | undefined)?.groupNickname ?? '';
});
const nicknameOpen = ref(false);
const nicknameDraft = ref('');
const nicknameSaving = ref(false);
function openNickname() {
  nicknameDraft.value = myGroupNickname.value;
  nicknameOpen.value = true;
}
async function saveNickname() {
  if (nicknameSaving.value || !activeId.value) return;
  nicknameSaving.value = true;
  try {
    await imApi.setMyNickname(activeId.value, nicknameDraft.value.trim());
    await im.loadDetail(activeId.value);
    nicknameOpen.value = false;
    showToast(nicknameDraft.value.trim() ? '群昵称已设置' : '群昵称已清除');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '设置失败');
  } finally {
    nicknameSaving.value = false;
  }
}

const clearConfirmOpen = ref(false);
const clearing = ref(false);
async function confirmClearMessages() {
  if (clearing.value || !activeId.value) return;
  clearing.value = true;
  try {
    await imApi.clearMessages(activeId.value);
    im.messagesByConversation[activeId.value] = [];
    clearConfirmOpen.value = false;
    showToast('聊天记录已清空');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '清空失败');
  } finally {
    clearing.value = false;
  }
}

/* 多公告（#5） */
const notices = ref<ImGroupNoticeResponse[]>([]);
const noticesLoading = ref(false);
const noticePublishOpen = ref(false);
const noticeTitle = ref('');
const noticeContent = ref('');
const noticePublishing = ref(false);
const canPublishNotice = computed(() => {
  const role = activeDetail.value?.groupProfile?.currentMemberRole ?? '';
  return role === 'OWNER' || role === 'ADMIN';
});
async function openNoticeDetail() {
  noticeDetailOpen.value = true;
  if (!activeId.value) return;
  noticesLoading.value = true;
  try {
    notices.value = await imApi.groupNotices(activeId.value);
  } catch {
    notices.value = [];
  } finally {
    noticesLoading.value = false;
  }
}
async function publishNotice() {
  if (noticePublishing.value || !activeId.value || !noticeContent.value.trim()) return;
  noticePublishing.value = true;
  try {
    const created = await imApi.publishGroupNotice(activeId.value, {
      title: noticeTitle.value.trim() || undefined,
      content: noticeContent.value.trim(),
    });
    notices.value = [created, ...notices.value];
    noticePublishOpen.value = false;
    noticeTitle.value = '';
    noticeContent.value = '';
    void im.loadDetail(activeId.value);
    showToast('公告已发布');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '发布失败');
  } finally {
    noticePublishing.value = false;
  }
}
function noticeTime(isoText: string) {
  return (isoText || '').slice(0, 16).replace('T', ' ');
}

/* ---------- 生命周期 ---------- */
onMounted(async () => {
  if (!session.isAuthenticated) {
    void router.replace({ name: 'login' });
    return;
  }
  await Promise.allSettled([im.loadConversations(), session.loadOrgProfile()]);
  connectSocket();
  if (activeId.value) {
    await syncConversation(activeId.value);
    scrollToBottom();
  } else if (im.conversations.length) {
    selectConversation(String(im.conversations[0].conversationId));
  }
});

watch(activeId, async (id) => {
  if (id) {
    await syncConversation(id);
    scrollToBottom();
  }
});

onBeforeUnmount(() => {
  closeSocket();
});
</script>

<template>
  <div class="im-page" @click="closeMessageMenu">
    <!-- 会话列表 344 -->
    <aside class="im-list">
      <header class="im-list-head">
        <h3>消息</h3>
        <button type="button" class="im-icon-btn" aria-label="从通讯录发起会话" title="从通讯录发起会话" @click="openCreate('private')">
          <BookUser :size="16" :stroke-width="2" />
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
          v-for="c in filteredConversations" :key="String(c.conversationId)" type="button"
          class="im-conv" :class="{ active: String(c.conversationId) === activeId }"
          @click="selectConversation(String(c.conversationId))"
        >
          <HubAvatar :label="c.title" :image="conversationAvatar(c)" size="lg" :agent="kindOf(c) === 'agent'" />
          <span class="im-conv-copy">
            <strong>{{ c.title }}<em v-if="c.memberCount" class="im-conv-count">{{ c.memberCount }}人</em><Pin v-if="c.pin" :size="12" :stroke-width="2.5" class="im-pin-icon" /></strong>
            <small>{{ lastMessageText(c) }}</small>
          </span>
          <span class="im-conv-side">
            <small>{{ (c.lastMessageAt || '').slice(5, 10) }}</small>
            <BellOff v-if="c.mute" :size="12" :stroke-width="2" class="im-mute-icon" />
            <i v-if="c.unreadCount > 0" class="im-unread" :class="{ muted: c.mute }">{{ c.unreadCount > 99 ? '99+' : c.unreadCount }}</i>
          </span>
        </button>
        <HubEmpty v-if="!filteredConversations.length" title="没有匹配的会话" :icon="MessageSquare" />
      </div>
    </aside>

    <!-- 聊天窗口 弹性 -->
    <section v-if="activeConversation" class="im-chat">
      <header class="im-chat-head">
        <div class="im-chat-title">
          <h3>{{ activeTitle }}</h3>
          <small>{{ activeSubtitle }}<template v-if="wsConnected === false"> · 连接已断开</template></small>
        </div>
        <div class="im-chat-actions">
          <button
            type="button" class="im-icon-btn im-head-icon"
            :class="{ active: recordSearchOpen }" aria-label="记录搜索"
            @click="recordSearchOpen = !recordSearchOpen"
          >
            <Search :size="16" :stroke-width="2" />
          </button>
          <button
            type="button" class="im-icon-btn im-head-icon"
            :class="{ active: detailOpen }" aria-label="面板切换"
            @click="detailOpen = !detailOpen"
          >
            <PanelRightClose v-if="detailOpen" :size="16" :stroke-width="2" />
            <PanelRightOpen v-else :size="16" :stroke-width="2" />
          </button>
        </div>
      </header>

      <div v-if="recordSearchOpen" class="im-record-search">
        <Search :size="14" :stroke-width="2" />
        <input
          v-model="recordKeyword" type="text"
          placeholder="搜索当前会话的消息记录"
          @keydown.esc="recordSearchOpen = false; recordKeyword = ''"
        />
        <button
          type="button" class="im-icon-btn" aria-label="关闭搜索"
          @click="recordSearchOpen = false; recordKeyword = ''"
        >
          <X :size="14" />
        </button>
      </div>

      <div ref="messageStream" class="im-stream">
        <template v-if="streamItems.length">
          <template v-for="item in streamItems" :key="item.key">
            <!-- 微信式时间分隔条：间隔 >5 分钟的第一条消息前插入 -->
            <div v-if="item.type === 'time'" class="im-time-divider">{{ item.label }}</div>
            <article
              v-else
              class="im-row" :class="[item.message.side]"
              :data-message-id="item.message.id"
            >
              <div v-if="item.message.side === 'system'" class="im-system-line">
                <strong>{{ item.message.author }}</strong>
                <span>{{ item.message.body }}</span>
              </div>
              <ImKnowledgeCard
                v-else-if="item.message.knowledgeCard"
                :card="item.message.knowledgeCard"
                :avatar-label="item.message.author"
                @action="(a) => handleCardAction(a.label)"
              />
              <ImProcessCard
                v-else-if="item.message.processCard"
                :card="item.message.processCard"
                :avatar-label="item.message.author"
                @action="handleCardAction(item.message.processCard.actionLabel)"
              />
              <ImBubble
                v-else
                :message="item.message"
                :avatar-url="senderAvatar(item.message)"
                :show-author="activeKind === 'team' && item.message.side !== 'right'"
                :quote="resolveQuote(item.message.quotedMessageId)"
                :mention-names="allMentionNames"
                @action="openMessageMenu"
                @attachment="openAttachment"
                @quote="jumpToQuoted"
              />
            </article>
          </template>
        </template>
        <HubEmpty v-else :title="recordKeyword ? `没有包含「${recordKeyword}」的消息` : '暂无消息，说点什么吧'" :icon="MessageSquare" />
      </div>

      <footer class="im-composer">
        <div v-if="quotedMessage" class="im-quote-bar">
          <button class="im-quote-card" type="button" @click="jumpToQuoted(quotedMessage.id)">
            <Quote :size="14" />
            <span>
              <strong>{{ quotedMessage.author }}</strong>
              <em>{{ quotedMessage.body || '[附件]' }}</em>
            </span>
          </button>
          <button class="im-icon-btn" type="button" aria-label="取消引用" @click="quotedMessage = null">
            <X :size="15" />
          </button>
        </div>

        <div class="im-composer-row">
          <div class="im-attach-wrap">
            <button
              type="button" class="im-icon-btn" aria-label="附件"
              :disabled="attachSending" @click.stop="openAttachMenu"
            >
              <Paperclip :size="16" :stroke-width="2" />
            </button>
            <div v-if="attachMenuOpen" class="im-attach-menu" @click.stop>
              <button type="button" @click="pickAttachFile">
                <Image :size="16" :stroke-width="2" />图片
              </button>
              <button type="button" @click="pickAttachFile">
                <FileText :size="16" :stroke-width="2" />文件
              </button>
              <button type="button" @click="openCloudPicker">
                <Cloud :size="16" :stroke-width="2" />云空间
              </button>
              <button type="button" @click="attachMenuOpen = false; showToast('快捷审批即将上线')">
                <Zap :size="16" :stroke-width="2" />快捷审批
              </button>
            </div>
          </div>
          <input ref="attachInput" type="file" hidden @change="handleAttachSelected" />

          <div class="im-input-wrap">
            <div v-if="draftMentionNames.length" class="im-mention-preview" aria-hidden="true">
              <span v-for="name in draftMentionNames" :key="name">{{ name }}</span>
            </div>
            <input
              v-model="draft" type="text" placeholder="输入消息，群聊支持 @同事"
              :disabled="sending"
              @input="onDraftInput"
              @keydown.enter.prevent="sendMessage"
            />
            <div v-if="mentionPanelOpen" class="im-mention-panel" @click.stop>
              <button
                v-for="m in mentionCandidates" :key="String(m.memberId)" type="button"
                @click="insertMention(m)"
              >
                <HubAvatar :label="m.displayName" size="xs" />
                {{ m.displayName }}
              </button>
              <p v-if="!mentionCandidates.length" class="im-mention-empty">没有可提及的成员</p>
            </div>
          </div>

          <button type="button" class="im-mention-btn" aria-label="@提及" @click.stop="insertAtSymbol">
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
    <aside v-if="activeConversation && detailOpen" class="im-detail">
      <div class="im-profile">
        <HubAvatar :label="activeConversation.title" size="xl" :agent="activeKind === 'agent'" />
        <strong>{{ activeConversation.title }}</strong>
        <small>{{ activeSubtitle }}</small>
      </div>

      <!-- 群聊详情 -->
      <template v-if="activeKind === 'team'">
        <h4 class="im-detail-title">
          群成员<em>{{ groupMembers.length }} 人</em>
          <button
            v-if="groupMembers.length > 12" type="button" class="im-detail-more"
            @click="allMembersOpen = true"
          >查看全部</button>
        </h4>
        <div class="im-members">
          <span v-for="m in groupMembers.slice(0, 12)" :key="String(m.memberId)" class="im-member" :title="m.displayName">
            <HubAvatar :label="m.displayName" size="sm" :agent="m.memberType === 'AGENT'" />
          </span>
          <button type="button" class="im-member-add" aria-label="添加成员" @click="openAddMembers">
            <Plus :size="14" :stroke-width="2" />
          </button>
        </div>
        <h4 class="im-detail-title">
          群公告
          <button type="button" class="im-detail-more" @click="openNoticeDetail">详情</button>
        </h4>
        <p class="im-detail-note im-notice-preview" @click="openNoticeDetail">{{ activeDetail?.groupProfile?.noticeText || '暂无公告' }}</p>
        <div class="im-detail-rows">
          <div class="im-detail-row static">
            <UsersRound :size="16" :stroke-width="2" />
            <span>群聊名称</span>
            <em class="im-detail-row-value">{{ activeConversation.title }}</em>
          </div>
          <button type="button" class="im-detail-row" @click="openGroupPanel">
            <FileText :size="16" :stroke-width="2" />
            <span>群公告</span>
            <em class="im-detail-row-value">{{ activeDetail?.groupProfile?.noticeText ? '已发布' : '未设置' }}</em>
            <ChevronRight :size="14" :stroke-width="2" class="im-row-arrow" />
          </button>
          <button type="button" class="im-detail-row" @click="openGroupPanel">
            <UsersRound :size="16" :stroke-width="2" />
            <span>群管理</span>
            <ChevronRight :size="14" :stroke-width="2" class="im-row-arrow" />
          </button>
          <button type="button" class="im-detail-row" @click="recordSearchOpen = true">
            <Search :size="16" :stroke-width="2" />
            <span>查找聊天记录</span>
            <ChevronRight :size="14" :stroke-width="2" class="im-row-arrow" />
          </button>
          <button type="button" class="im-detail-row" @click="openNickname">
            <AtSign :size="16" :stroke-width="2" />
            <span>本群昵称</span>
            <em class="im-detail-row-value">{{ myGroupNickname || '未设置' }}</em>
            <ChevronRight :size="14" :stroke-width="2" class="im-row-arrow" />
          </button>
        </div>
        <div class="im-detail-switches">
          <div class="im-switch-row">
            <span>消息免打扰</span>
            <button
              type="button" class="im-switch" :class="{ on: activeConversation.mute }"
              role="switch" :aria-checked="Boolean(activeConversation.mute)"
              :disabled="prefUpdating" @click="togglePreference('mute')"
            ><i /></button>
          </div>
          <div class="im-switch-row">
            <span>置顶聊天</span>
            <button
              type="button" class="im-switch" :class="{ on: activeConversation.pin }"
              role="switch" :aria-checked="Boolean(activeConversation.pin)"
              :disabled="prefUpdating" @click="togglePreference('pin')"
            ><i /></button>
          </div>
          <div class="im-switch-row">
            <span>保存到通讯录</span>
            <button
              type="button" class="im-switch" :class="{ on: activeConversation.saveToContacts }"
              role="switch" :aria-checked="Boolean(activeConversation.saveToContacts)"
              :disabled="prefUpdating" @click="togglePreference('saveToContacts')"
            ><i /></button>
          </div>
          <div class="im-switch-row">
            <span>显示成员昵称</span>
            <button
              type="button" class="im-switch" :class="{ on: activeConversation.showMemberNickname !== false }"
              role="switch" :aria-checked="activeConversation.showMemberNickname !== false"
              :disabled="prefUpdating" @click="togglePreference('showMemberNickname')"
            ><i /></button>
          </div>
        </div>
        <h4 class="im-detail-title">群文件<em v-if="groupFiles.length">{{ groupFiles.length }}</em></h4>
        <div class="im-files">
          <p v-if="groupFilesLoading" class="im-detail-note">加载中…</p>
          <p v-else-if="!groupFiles.length" class="im-detail-note">暂无群文件</p>
          <button
            v-for="f in groupFiles" :key="String(f.attachmentRefId)" type="button"
            class="im-file-item" @click="downloadGroupFile(f)"
          >
            <span class="im-file-icon"><FileText :size="15" :stroke-width="2" /></span>
            <span class="im-file-copy">
              <strong>{{ f.displayName }}</strong>
              <small>{{ formatSize(f.fileSize) }}<template v-if="f.uploaderName"> · {{ f.uploaderName }}</template></small>
            </span>
            <Download :size="14" :stroke-width="2" class="im-file-dl" />
          </button>
        </div>
        <div class="im-detail-danger">
          <button type="button" class="im-danger-link" @click="clearConfirmOpen = true">清空聊天记录</button>
          <button type="button" class="im-danger-link" @click="leaveConfirmOpen = true">退出群聊</button>
        </div>
      </template>

      <!-- AI 会话详情 -->
      <template v-else-if="activeKind === 'agent'">
        <h4 class="im-detail-title">快捷提问</h4>
        <button
          v-for="q in ['我的年假还剩几天？', '差旅报销制度怎么查？', '帮我发起请假申请']" :key="q"
          type="button" class="im-quick-q" @click="draft = q"
        >{{ q }}</button>
        <p class="im-detail-note">向 TA 提问制度、假期、流程问题，回答可引用知识库出处。</p>
      </template>

      <!-- 私聊/系统详情 -->
      <template v-else>
        <h4 class="im-detail-title">会话信息</h4>
        <p class="im-detail-note">{{ lastMessageText(activeConversation) }}</p>
        <div v-if="activeKind === 'private'" class="im-detail-rows">
          <button type="button" class="im-detail-row" @click="openRemark">
            <FileText :size="16" :stroke-width="2" />
            <span>备注</span>
            <em class="im-detail-row-value">{{ peerMember?.displayName || '未设置' }}</em>
            <ChevronRight :size="14" :stroke-width="2" class="im-row-arrow" />
          </button>
        </div>
        <div v-if="activeKind !== 'system'" class="im-detail-switches">
          <div class="im-switch-row">
            <span>消息免打扰</span>
            <button
              type="button" class="im-switch" :class="{ on: activeConversation.mute }"
              role="switch" :aria-checked="Boolean(activeConversation.mute)"
              :disabled="prefUpdating" @click="togglePreference('mute')"
            ><i /></button>
          </div>
          <div class="im-switch-row">
            <span>置顶聊天</span>
            <button
              type="button" class="im-switch" :class="{ on: activeConversation.pin }"
              role="switch" :aria-checked="Boolean(activeConversation.pin)"
              :disabled="prefUpdating" @click="togglePreference('pin')"
            ><i /></button>
          </div>
        </div>
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
              <HubAvatar :label="contact.name" size="sm" />
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
              @click="createMemberIds = toggleId(createMemberIds, String(contact.userId))"
            >
              <HubAvatar :label="contact.name" size="sm" />
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
              v-for="agent in agentResults" :key="String(agent.agentId)" type="button"
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

    <!-- 群管理弹窗（完整版） -->
    <HubDialog v-if="groupPanelOpen" @close="groupPanelOpen = false">
      <form class="im-dialog im-group-dialog" @submit.prevent="saveGroupPanel">
        <header class="im-dialog-head">
          <h2>群管理</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="groupPanelOpen = false">
            <X :size="15" />
          </button>
        </header>
        <div class="im-mode-row">
          <button type="button" :class="{ active: groupTab === 'profile' }" @click="groupTab = 'profile'">群资料</button>
          <button type="button" :class="{ active: groupTab === 'members' }" @click="groupTab = 'members'">成员（{{ groupMembers.length }}）</button>
          <button type="button" :class="{ active: groupTab === 'notice' }" @click="groupTab = 'notice'">群公告</button>
        </div>

        <section v-if="groupTab === 'profile'" class="im-dialog-body">
          <label class="im-field-label">群名称
            <input v-model="groupTitle" type="text" placeholder="输入群名称" />
          </label>
          <p class="im-dialog-note">群头像与更多设置即将上线</p>
        </section>

        <section v-else-if="groupTab === 'members'" class="im-dialog-body">
          <span class="im-field-label">当前成员（{{ groupMembers.length }}）</span>
          <div class="im-member-picker">
            <div v-for="m in groupMembers" :key="`member-${m.memberId}`" class="im-member-row static">
              <HubAvatar :label="m.displayName" size="sm" :agent="m.memberType === 'AGENT'" />
              <span><strong>{{ m.displayName }}</strong><small>{{ m.memberTypeName || m.memberType }}</small></span>
            </div>
          </div>
          <div class="im-member-actions">
            <HubButton variant="secondary" @click="openAddMembers">添加成员</HubButton>
            <HubButton variant="secondary" @click="openRemoveMembers">移除成员</HubButton>
          </div>
        </section>

        <section v-else class="im-dialog-body">
          <label class="im-field-label">群公告
            <textarea v-model="groupNotice" rows="4" placeholder="输入群公告，成员进入群聊时可见" />
          </label>
        </section>

        <footer class="im-dialog-foot">
          <HubButton variant="secondary" :disabled="groupSubmitting" @click="groupPanelOpen = false">取消</HubButton>
          <HubButton variant="primary" :disabled="groupSubmitting" @click="saveGroupPanel">
            {{ groupSubmitting ? '保存中…' : '保存' }}
          </HubButton>
        </footer>
      </form>
    </HubDialog>

    <!-- 添加群成员弹窗（画布 8:4881） -->
    <HubDialog v-if="addMembersOpen" @close="addMembersOpen = false">
      <div class="im-dialog im-group-dialog">
        <header class="im-dialog-head">
          <h2>添加群成员</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="addMembersOpen = false">
            <X :size="15" />
          </button>
        </header>
        <label class="im-search im-dialog-search">
          <Search :size="16" :stroke-width="2" />
          <input v-model="addKeyword" type="text" placeholder="搜索成员" />
        </label>
        <div class="im-dept-row">
          <small class="im-dept-label">部门</small>
          <div class="im-dept-chips">
            <button
              v-for="d in deptOptions" :key="d" type="button"
              class="im-filter-chip" :class="{ active: addDept === d }"
              @click="addDept = d"
            >{{ d }}</button>
          </div>
        </div>
        <div class="im-member-picker im-member-picker-tall">
          <button
            v-for="contact in addCandidates" :key="`add-${contact.id}`" type="button"
            class="im-member-row" :class="{ active: addSelected.has(String(contact.userId)) }"
            @click="addSelected = toggleId(addSelected, String(contact.userId))"
          >
            <HubAvatar :label="contact.name" size="sm" />
            <span><strong>{{ contact.remarkName || contact.name }}</strong><small>{{ contactSubtitle(contact) }}</small></span>
            <i class="im-check-circle" :class="{ on: addSelected.has(String(contact.userId)) }">
              <Check v-if="addSelected.has(String(contact.userId))" :size="12" :stroke-width="3" />
            </i>
          </button>
          <p v-if="!addCandidates.length" class="im-dialog-note">没有匹配的成员</p>
        </div>
        <footer class="im-dialog-foot im-selected-foot">
          <small class="im-selected-count">已选 {{ addSelected.size }} 人</small>
          <span class="im-dialog-foot-btns">
            <HubButton variant="secondary" :disabled="addSubmitting" @click="addMembersOpen = false">取消</HubButton>
            <HubButton variant="primary" :disabled="addSubmitting || !addSelected.size" @click="submitAddMembers">
              {{ addSubmitting ? '添加中…' : '确定' }}
            </HubButton>
          </span>
        </footer>
      </div>
    </HubDialog>

    <!-- 移除群成员弹窗（画布 8:6233） -->
    <HubDialog v-if="removeMembersOpen" @close="removeMembersOpen = false">
      <div class="im-dialog im-group-dialog">
        <header class="im-dialog-head">
          <h2>移除群成员</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="removeMembersOpen = false">
            <X :size="15" />
          </button>
        </header>
        <p class="im-confirm-text">移除后，该成员将不再接收本群消息。</p>
        <label class="im-search im-dialog-search">
          <Search :size="16" :stroke-width="2" />
          <input v-model="removeKeyword" type="text" placeholder="搜索成员" />
        </label>
        <div class="im-dept-row">
          <small class="im-dept-label">部门</small>
          <div class="im-dept-chips">
            <button
              v-for="d in removeDeptOptions" :key="d" type="button"
              class="im-filter-chip" :class="{ active: removeDept === d }"
              @click="removeDept = d"
            >{{ d }}</button>
          </div>
        </div>
        <div class="im-member-picker im-member-picker-tall">
          <div v-for="m in removeCandidates" :key="`rm-${m.memberId}`" class="im-member-row static">
            <HubAvatar :label="m.displayName" size="sm" :agent="m.memberType === 'AGENT'" />
            <span><strong>{{ m.displayName }}</strong><small>{{ m.memberTypeName || m.memberType }}</small></span>
            <button
              type="button" class="im-remove-mini"
              :disabled="removingId === String(m.memberId)"
              @click="removeOneMember(m)"
            >{{ removingId === String(m.memberId) ? '移除中…' : '移除' }}</button>
          </div>
          <p v-if="!removeCandidates.length" class="im-dialog-note">没有匹配的成员</p>
        </div>
        <footer class="im-dialog-foot">
          <HubButton variant="secondary" @click="removeMembersOpen = false">关闭</HubButton>
        </footer>
      </div>
    </HubDialog>

    <!-- 退群确认弹窗（画布 8:5409） -->
    <HubDialog v-if="leaveConfirmOpen" @close="leaveConfirmOpen = false">
      <div class="im-dialog">
        <header class="im-dialog-head">
          <h2>退出群聊</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="leaveConfirmOpen = false">
            <X :size="15" />
          </button>
        </header>
        <p class="im-confirm-text">
          退出「{{ activeConversation?.title }}」后，你将不再接收该群的消息。确定退出吗？
        </p>
        <footer class="im-dialog-foot">
          <HubButton variant="secondary" :disabled="leaveConfirming" @click="leaveConfirmOpen = false">取消</HubButton>
          <HubButton variant="danger" :disabled="leaveConfirming" @click="leaveActiveGroup">
            {{ leaveConfirming ? '退出中…' : '确认退出' }}
          </HubButton>
        </footer>
      </div>
    </HubDialog>

    <!-- 查看全部成员弹窗 -->
    <HubDialog v-if="allMembersOpen" @close="allMembersOpen = false">
      <div class="im-dialog">
        <header class="im-dialog-head">
          <h2>群成员（{{ groupMembers.length }}）</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="allMembersOpen = false">
            <X :size="15" />
          </button>
        </header>
        <div class="im-member-picker">
          <div v-for="m in groupMembers" :key="`all-${m.memberId}`" class="im-member-row static">
            <HubAvatar :label="m.displayName" size="sm" :agent="m.memberType === 'AGENT'" />
            <span>
              <strong>{{ m.displayName }}</strong>
              <small>{{ m.memberTypeName || (m.memberType === 'AGENT' ? 'AI 助手' : '成员') }}</small>
            </span>
          </div>
        </div>
      </div>
    </HubDialog>

    <!-- 备注编辑弹窗（画布 8:5172） -->
    <HubDialog v-if="remarkOpen" @close="remarkOpen = false">
      <form class="im-dialog" @submit.prevent="saveRemark">
        <header class="im-dialog-head">
          <h2>编辑备注</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="remarkOpen = false">
            <X :size="15" />
          </button>
        </header>
        <section class="im-dialog-body">
          <label class="im-field-label">备注名
            <input v-model="remarkName" type="text" placeholder="给对方设置备注名" maxlength="20" />
          </label>
          <p class="im-dialog-note">备注仅自己可见，保存后会话列表同步显示</p>
        </section>
        <footer class="im-dialog-foot">
          <div class="im-dialog-foot-btns">
            <HubButton variant="secondary" type="button" :disabled="remarkSaving" @click="remarkOpen = false">取消</HubButton>
            <HubButton variant="primary" type="submit" :disabled="remarkSaving">保存</HubButton>
          </div>
        </footer>
      </form>
    </HubDialog>

    <!-- 群公告详情弹窗（#5 联调：多公告列表 + 群主/管理员可发布） -->
    <HubDialog v-if="noticeDetailOpen" @close="noticeDetailOpen = false">
      <div class="im-dialog">
        <header class="im-dialog-head">
          <h2>群公告</h2>
          <div class="im-dialog-head-actions">
            <button
              v-if="canPublishNotice" type="button" class="im-publish-btn"
              @click="noticePublishOpen = true"
            >发布公告</button>
            <button class="im-icon-btn" type="button" aria-label="关闭" @click="noticeDetailOpen = false">
              <X :size="15" />
            </button>
          </div>
        </header>
        <section class="im-dialog-body">
          <p v-if="noticesLoading" class="im-dialog-note">加载中…</p>
          <template v-else-if="notices.length">
            <div v-for="n in notices" :key="String(n.noticeId)" class="im-notice-card">
              <strong v-if="n.title">{{ n.title }}</strong>
              <p>{{ n.content }}</p>
              <small>{{ n.publisherName }} · {{ noticeTime(n.publishedAt) }}</small>
            </div>
          </template>
          <p v-else class="im-dialog-note">暂无公告</p>
        </section>
      </div>
    </HubDialog>

    <!-- 发布公告弹窗 -->
    <HubDialog v-if="noticePublishOpen" @close="noticePublishOpen = false">
      <form class="im-dialog" @submit.prevent="publishNotice">
        <header class="im-dialog-head">
          <h2>发布公告</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="noticePublishOpen = false">
            <X :size="15" />
          </button>
        </header>
        <section class="im-dialog-body">
          <label class="im-field-label">标题（可选）
            <input v-model="noticeTitle" type="text" placeholder="公告标题" maxlength="50" />
          </label>
          <label class="im-field-label">内容
            <textarea v-model="noticeContent" rows="4" placeholder="输入公告内容" />
          </label>
        </section>
        <footer class="im-dialog-foot">
          <div class="im-dialog-foot-btns">
            <HubButton variant="secondary" type="button" :disabled="noticePublishing" @click="noticePublishOpen = false">取消</HubButton>
            <HubButton variant="primary" type="submit" :disabled="!noticeContent.trim() || noticePublishing">发布</HubButton>
          </div>
        </footer>
      </form>
    </HubDialog>

    <!-- 本群昵称设置弹窗（#3 联调） -->
    <HubDialog v-if="nicknameOpen" @close="nicknameOpen = false">
      <form class="im-dialog" @submit.prevent="saveNickname">
        <header class="im-dialog-head">
          <h2>本群昵称</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="nicknameOpen = false">
            <X :size="15" />
          </button>
        </header>
        <section class="im-dialog-body">
          <label class="im-field-label">我在本群的昵称
            <input v-model="nicknameDraft" type="text" placeholder="留空则显示真实姓名" maxlength="20" />
          </label>
          <p class="im-dialog-note">昵称仅在本群对内显示，清空后恢复真实姓名</p>
        </section>
        <footer class="im-dialog-foot">
          <div class="im-dialog-foot-btns">
            <HubButton variant="secondary" type="button" :disabled="nicknameSaving" @click="nicknameOpen = false">取消</HubButton>
            <HubButton variant="primary" type="submit" :disabled="nicknameSaving">保存</HubButton>
          </div>
        </footer>
      </form>
    </HubDialog>

    <!-- 清空聊天记录确认弹窗（#4 联调） -->
    <HubDialog v-if="clearConfirmOpen" @close="clearConfirmOpen = false">
      <div class="im-dialog">
        <header class="im-dialog-head">
          <h2>清空聊天记录</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="clearConfirmOpen = false">
            <X :size="15" />
          </button>
        </header>
        <section class="im-dialog-body">
          <p class="im-confirm-text">清空后仅删除你视角下的消息记录，不影响其他群成员，且不可恢复。确定清空吗？</p>
        </section>
        <footer class="im-dialog-foot">
          <div class="im-dialog-foot-btns">
            <HubButton variant="secondary" type="button" :disabled="clearing" @click="clearConfirmOpen = false">取消</HubButton>
            <HubButton variant="primary" :disabled="clearing" @click="confirmClearMessages">确认清空</HubButton>
          </div>
        </footer>
      </div>
    </HubDialog>

    <!-- 转发弹窗 -->
    <HubDialog v-if="forwardOpen" @close="forwardOpen = false">
      <div class="im-dialog">
        <header class="im-dialog-head">
          <h2>转发消息</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="forwardOpen = false">
            <X :size="15" />
          </button>
        </header>
        <section class="im-dialog-body">
          <div v-if="forwardTarget" class="im-forward-preview">
            <strong>{{ forwardTarget.author }}</strong>
            <em>{{ forwardTarget.body || `[文件] ${forwardTarget.attachments[0]?.displayName ?? ''}` }}</em>
          </div>
          <label class="im-dialog-search">
            <Search :size="14" :stroke-width="2" />
            <input v-model="forwardKeyword" type="text" placeholder="搜索会话" />
          </label>
          <div class="im-member-picker">
            <button
              v-for="c in forwardCandidates" :key="`fw-${c.conversationId}`" type="button"
              class="im-member-row" :class="{ active: forwardConversationId === String(c.conversationId) }"
              @click="forwardConversationId = String(c.conversationId)"
            >
              <HubAvatar :label="c.title" size="sm" :agent="kindOf(c) === 'agent'" />
              <span><strong>{{ c.title }}</strong><small>{{ lastMessageText(c) }}</small></span>
              <Check v-if="forwardConversationId === String(c.conversationId)" :size="15" :stroke-width="2.5" class="im-check" />
            </button>
            <p v-if="!forwardCandidates.length" class="im-dialog-note">没有可转发的会话</p>
          </div>
        </section>
        <footer class="im-dialog-foot">
          <div class="im-dialog-foot-btns">
            <HubButton variant="secondary" type="button" :disabled="forwardSending" @click="forwardOpen = false">取消</HubButton>
            <HubButton variant="primary" :disabled="!forwardConversationId || forwardSending" @click="submitForward">发送</HubButton>
          </div>
        </footer>
      </div>
    </HubDialog>

    <!-- 云空间文件选择面板 -->
    <HubDialog v-if="cloudOpen" @close="cloudOpen = false">
      <div class="im-dialog">
        <header class="im-dialog-head">
          <h2>从云空间发送</h2>
          <button class="im-icon-btn" type="button" aria-label="关闭" @click="cloudOpen = false">
            <X :size="15" />
          </button>
        </header>
        <section class="im-dialog-body">
          <label class="im-dialog-search">
            <Search :size="14" :stroke-width="2" />
            <input v-model="cloudKeyword" type="text" placeholder="搜索文件" />
          </label>
          <div class="im-member-picker">
            <p v-if="cloudLoading" class="im-dialog-note">加载中…</p>
            <button
              v-for="f in filteredCloudFiles" :key="`cloud-${f.attachmentRefId}`" type="button"
              class="im-file-item" :disabled="cloudSending" @click="sendCloudFile(f)"
            >
              <span class="im-file-icon"><FileText :size="15" :stroke-width="2" /></span>
              <span class="im-file-copy">
                <strong>{{ f.displayName }}</strong>
                <small>{{ formatSize(f.fileSize) }}</small>
              </span>
              <Send :size="14" :stroke-width="2" class="im-file-dl" />
            </button>
            <p v-if="!cloudLoading && !filteredCloudFiles.length" class="im-dialog-note">云空间暂无文件</p>
          </div>
        </section>
      </div>
    </HubDialog>

    <!-- 消息操作菜单 -->
    <div
      v-if="menuPos && menuTarget" class="im-msg-menu"
      :style="{ left: `${menuPos.x}px`, top: `${menuPos.y}px` }"
      @click.stop
    >
      <button type="button" @click="runMenuAction('copy')">复制</button>
      <button type="button" @click="runMenuAction('quote')">引用</button>
      <button type="button" @click="runMenuAction('forward')">转发</button>
      <button type="button" class="danger" @click="runMenuAction('delete-or-recall')">
        {{ menuCanRecall ? '撤回' : '删除' }}
      </button>
    </div>

    <HubToast :message="toastMessage" />
  </div>
</template>

<style>
@import '@/styles/messages.css';
</style>
