import { computed, nextTick, ref, watch } from 'vue';
import { employeeApi } from '@/api';
import type { EntityId } from '@/api';
import type { ConversationMessage, MessageMention, PersonalFileRecord, useShellStore } from '@/stores/shell';
import type { useImStore } from '@/stores/im';
import type { useSessionStore } from '@/stores/session';
import { useAgentDirectory } from './useAgentDirectory';
import { usePersonalFileTransfer } from './usePersonalFileTransfer';
import { entityIdListFromText } from '@/utils/employee-format';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { createClientId } from '@/utils/request-id';
import { scrollElementToBottomAfterLayout } from '@/utils/scroll';

type FieldRows = Array<[string, string]>;
export type MessageMenuAction = 'copy' | 'translate' | 'forward' | 'favorite' | 'remind' | 'quote' | 'delete-or-recall';
export interface MessageQuotePreview {
  id: string;
  author: string;
  body: string;
  visible: boolean;
}

// 只能撤回自己发的、2 分钟内的消息
export function canRecallMessage(message: ConversationMessage, now = new Date()) {
  if (message.side !== 'right' || message.status === 'RECALLED' || !message.time) return false;
  const sentAt = new Date(message.time.replace(' ', 'T'));
  if (Number.isNaN(sentAt.getTime())) return false;
  return now.getTime() - sentAt.getTime() <= 2 * 60 * 1000;
}

interface UseMessagesStateOptions {
  shell: ReturnType<typeof useShellStore>;
  im: ReturnType<typeof useImStore>;
  session: ReturnType<typeof useSessionStore>;
  wsConnected: { value: boolean };
  showToast: (message: string) => void;
  navigateMessagesConversation: (id: string) => void;
  syncConversationMessages: (id: string) => Promise<void>;
  openFilePreviewFromUrl: (file: PersonalFileRecord, url: string) => Promise<void>;
}

export function useMessagesState({
  shell,
  im,
  session,
  wsConnected,
  showToast,
  navigateMessagesConversation,
  syncConversationMessages,
  openFilePreviewFromUrl,
}: UseMessagesStateOptions) {
  const { resolveDownloadUrl } = usePersonalFileTransfer();

  const draft = ref('');
  const draftMentions = ref<MessageMention[]>([]);
  const messageStream = ref<HTMLElement | null>(null);
  const createConversationOpen = ref(false);
  const createConversationMode = ref<'private' | 'group' | 'agent'>('private');
  const createConversationForm = ref({
    targetUserId: '',
    groupTitle: '',
    memberUserIds: '',
    agentId: '',
    agentKeyword: '',
  });
  const createConversationSubmitting = ref(false);
  const createConversationError = ref('');
  const groupPanelOpen = ref(false);
  const groupProfileForm = ref({
    title: '',
    noticeText: '',
    memberUserIds: '',
    removeMemberUserIds: '',
  });
  const groupActionError = ref('');
  const groupMemberAdding = ref(false);
  const messageActionTarget = ref<ConversationMessage | null>(null);
  const messageActionMenu = ref<{ x: number; y: number } | null>(null);
  const messageEditText = ref('');
  const messageActionError = ref('');
  const messageForwardOpen = ref(false);
  const messageForwardSearch = ref('');
  const messageForwardNote = ref('');
  const messageForwardSelectedIds = ref<string[]>([]);
  const quotedMessageId = ref<EntityId | null>(null);

  const {
    agents,
    agentsLoading,
    agentKeyword,
    loadAgents,
  } = useAgentDirectory({
    createConversationForm,
    showToast,
  });

  const activeConversation = computed(() => shell.activeConversation);
  const activeConversationTitle = computed(() => {
    const conversation = activeConversation.value;
    return conversation.memberCount ? `${conversation.title}（${conversation.memberCount}）` : conversation.title;
  });
  const activeConversationCurrentStep = computed(() => (
    activeConversation.value.workflowSteps.find((step) => step.status === 'current')
  ));
  const mentionableMembers = computed<MessageMention[]>(() => {
    const entries = new Map<string, MessageMention>();
    activeConversation.value.panel.members.forEach((member) => {
      if (member.userId === undefined || member.userId === null) return;
      const contact = shell.contacts.find((item) => (
        item.userId !== undefined && item.userId !== null && String(item.userId) === String(member.userId)
      ));
      const name = member.originalName || contact?.originalName || member.name;
      entries.set(String(member.userId), {
        userId: member.userId,
        name,
        originalName: name,
        role: contact?.role || member.role,
        department: contact?.department,
        avatarUrl: contact?.avatarUrl,
      });
    });
    shell.contacts.forEach((contact) => {
      if (contact.userId === undefined || contact.userId === null || contact.relation === '本人') return;
      const key = String(contact.userId);
      if (entries.has(key)) return;
      const name = contact.originalName || contact.name;
      entries.set(key, {
        userId: contact.userId,
        name,
        originalName: name,
        role: contact.role,
        department: contact.department,
        avatarUrl: contact.avatarUrl,
      });
    });
    return Array.from(entries.values());
  });
  const activeConversationDetail = computed(() => im.detailsByConversation[shell.active.messages] ?? null);
  const activeGroupProfile = computed(() => activeConversationDetail.value?.groupProfile ?? null);
  const activeConversationPreferenceRows = computed<FieldRows>(() => {
    const conversation = im.conversations.find((item) => item.id === shell.active.messages);
    return [
      ['置顶', conversation?.pinned ? '已开启' : '未开启'],
      ['免打扰', conversation?.muted ? '已开启' : '未开启'],
    ];
  });
  const hasActiveConversation = computed(() => Boolean(activeConversation.value.id));
  const quotedMessagePreview = computed(() => resolveQuotedMessage(quotedMessageId.value));
  const messageForwardConversations = computed(() => {
    const keyword = messageForwardSearch.value.trim().toLowerCase();
    return shell.conversations
      .filter((item) => item.id !== activeConversationId())
      .filter((item) => {
        if (!keyword) return true;
        return `${item.title} ${item.summaryTitle} ${item.summaryBody}`.toLowerCase().includes(keyword);
      });
  });

  function activeConversationId() {
    return shell.active.messages.trim();
  }

  function backToList() {
    shell.setMobileView('list');
  }

  function isCurrentUserMessage(message: ConversationMessage) {
    return message.side === 'right' && message.status !== 'RECALLED';
  }

  function resolveQuotedMessage(messageId?: EntityId | string | null): MessageQuotePreview | null {
    if (messageId === undefined || messageId === null) return null;
    const id = String(messageId);
    const message = activeConversation.value.messages.find((item) => item.id === id);
    if (!message) {
      return {
        id,
        author: '引用消息',
        body: '原消息不可见',
        visible: false,
      };
    }
    return {
      id,
      author: message.author || '成员',
      body: message.status === 'RECALLED' ? '消息已撤回' : (message.body || '非文本消息'),
      visible: true,
    };
  }

  function cancelQuote() {
    quotedMessageId.value = null;
  }

  async function jumpToQuotedMessage(messageId?: EntityId | string | null) {
    if (messageId === undefined || messageId === null) return;
    await nextTick();
    const id = String(messageId);
    const target = Array.from(messageStream.value?.querySelectorAll<HTMLElement>('[data-message-id]') ?? [])
      .find((item) => item.dataset.messageId === id);
    if (!target) {
      showToast('原消息不可见');
      return;
    }
    target.scrollIntoView({ block: 'center', behavior: 'smooth' });
    target.classList.remove('quote-target');
    void target.offsetWidth;
    target.classList.add('quote-target');
    window.setTimeout(() => target.classList.remove('quote-target'), 1400);
  }

  function openCreateConversation(mode: 'private' | 'group' | 'agent') {
    createConversationMode.value = mode;
    createConversationOpen.value = true;
    createConversationError.value = '';
    if (mode === 'agent') void loadAgents(createConversationForm.value.agentKeyword);
  }

  function closeCreateConversation() {
    if (createConversationSubmitting.value) return;
    createConversationOpen.value = false;
    createConversationError.value = '';
  }

  // 创建会话：私聊/群聊/AI 会话三种模式复用同一个提交函数
  async function submitCreateConversation() {
    if (createConversationSubmitting.value) return;
    createConversationSubmitting.value = true;
    createConversationError.value = '';
    try {
      let conversation;
      if (createConversationMode.value === 'private') {
        const targetUserId = createConversationForm.value.targetUserId.trim();
        if (!targetUserId) throw new Error('请选择成员');
        conversation = await im.startPrivateConversation(targetUserId);
      } else if (createConversationMode.value === 'group') {
        const title = createConversationForm.value.groupTitle.trim();
        const memberUserIds = entityIdListFromText(createConversationForm.value.memberUserIds);
        if (!title || !memberUserIds.length) throw new Error('请填写群名称和成员');
        conversation = await im.createGroupConversation(title, memberUserIds);
      } else {
        const agentId = createConversationForm.value.agentId.trim();
        if (!agentId) throw new Error('请选择 AI 助手');
        conversation = await im.startAgentConversation(agentId);
      }
      shell.mergeBackendConversations(im.conversations);
      navigateMessagesConversation(conversation.id);
      await syncConversationMessages(conversation.id);
      closeCreateConversation();
      showToast('会话已创建');
    } catch (error) {
      createConversationError.value = error instanceof Error ? error.message : '会话创建失败';
    } finally {
      createConversationSubmitting.value = false;
    }
  }

  async function searchAgents() {
    createConversationForm.value.agentId = '';
    await loadAgents(createConversationForm.value.agentKeyword);
  }

  async function openGroupPanel() {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    try {
      const detail = await im.loadConversationDetail(conversationId);
      groupProfileForm.value.title = detail.groupProfile?.title ?? activeConversation.value.title;
      groupProfileForm.value.noticeText = detail.groupProfile?.noticeText ?? '';
      groupProfileForm.value.memberUserIds = '';
      groupProfileForm.value.removeMemberUserIds = '';
      groupActionError.value = '';
      groupPanelOpen.value = true;
    } catch (error) {
      showToast(error instanceof Error ? error.message : '群资料加载失败');
    }
  }

  function closeGroupPanel() {
    if (im.actionLoading) return;
    groupPanelOpen.value = false;
    groupActionError.value = '';
    groupMemberAdding.value = false;
  }

  function toggleGroupMemberAdding() {
    groupMemberAdding.value = !groupMemberAdding.value;
    groupProfileForm.value.memberUserIds = '';
  }

  async function saveGroupProfile() {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    groupActionError.value = '';
    try {
      await im.updateGroupProfile(conversationId, {
        title: groupProfileForm.value.title.trim() || undefined,
        noticeText: groupProfileForm.value.noticeText.trim() || undefined,
      });
      shell.mergeBackendConversations(im.conversations);
      showToast('群资料已保存');
    } catch (error) {
      groupActionError.value = error instanceof Error ? error.message : '保存失败';
    }
  }

  async function addGroupMembers() {
    const conversationId = activeConversationId();
    const memberUserIds = entityIdListFromText(groupProfileForm.value.memberUserIds);
    if (!conversationId || !memberUserIds.length) return;
    groupActionError.value = '';
    try {
      await im.addGroupMembers(conversationId, memberUserIds);
      groupProfileForm.value.memberUserIds = '';
      shell.setColleagues(session.colleagues);
      shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
      showToast('成员已添加');
    } catch (error) {
      groupActionError.value = error instanceof Error ? error.message : '添加失败';
    }
  }

  async function removeGroupMembers() {
    const conversationId = activeConversationId();
    const memberUserIds = entityIdListFromText(groupProfileForm.value.removeMemberUserIds);
    if (!conversationId || !memberUserIds.length) return;
    groupActionError.value = '';
    try {
      await im.removeGroupMembers(conversationId, memberUserIds);
      groupProfileForm.value.removeMemberUserIds = '';
      shell.setColleagues(session.colleagues);
      shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
      showToast('成员已移除');
    } catch (error) {
      groupActionError.value = error instanceof Error ? error.message : '移除失败';
    }
  }

  async function leaveActiveGroup() {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    try {
      await im.leaveGroup(conversationId);
      await im.loadConversations();
      shell.mergeBackendConversations(im.conversations);
      closeGroupPanel();
      showToast('已退出群聊');
    } catch (error) {
      groupActionError.value = error instanceof Error ? error.message : '退群失败';
    }
  }

  async function dismissActiveGroup() {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    try {
      await im.dismissGroup(conversationId);
      shell.mergeBackendConversations(im.conversations);
      closeGroupPanel();
      showToast('群聊已解散');
    } catch (error) {
      groupActionError.value = error instanceof Error ? error.message : '解散失败';
    }
  }

  async function toggleActiveConversationPreference(field: 'pin' | 'mute') {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    const current = im.conversations.find((item) => item.id === String(conversationId));
    if (!current) return;
    try {
      await im.updatePreference(conversationId, {
        pin: field === 'pin' ? !current.pinned : current.pinned,
        mute: field === 'mute' ? !current.muted : current.muted,
      });
      shell.mergeBackendConversations(im.conversations);
      showToast('会话设置已保存');
    } catch (error) {
      showToast(error instanceof Error ? error.message : '保存失败');
    }
  }

  const messageActionCanRecall = computed(() => (
    messageActionTarget.value ? canRecallMessage(messageActionTarget.value) : false
  ));

  function openMessageAction(message: ConversationMessage, event?: MouseEvent) {
    if (message.status === 'RECALLED') return;
    messageActionTarget.value = message;
    messageEditText.value = message.body;
    messageActionError.value = '';
    if (event) {
      const menuWidth = 180;
      const menuHeight = 320;
      messageActionMenu.value = {
        x: Math.max(8, Math.min(event.clientX, window.innerWidth - menuWidth - 8)),
        y: Math.max(8, Math.min(event.clientY, window.innerHeight - menuHeight - 8)),
      };
    }
  }

  function closeMessageAction() {
    if (im.actionLoading) return;
    if (messageForwardOpen.value) return;
    messageActionTarget.value = null;
    messageActionMenu.value = null;
    messageEditText.value = '';
    messageActionError.value = '';
  }

  async function runMessageMenuAction(action: MessageMenuAction) {
    const message = messageActionTarget.value;
    if (!message) return;
    if (action === 'copy') {
      await navigator.clipboard?.writeText(message.body);
      closeMessageAction();
      showToast('已复制');
      return;
    }
    if (action === 'translate') {
      await submitMessageTranslate(message);
      return;
    }
    if (action === 'forward') {
      messageForwardOpen.value = true;
      messageActionMenu.value = null;
      messageActionError.value = '';
      messageForwardSearch.value = '';
      messageForwardNote.value = '';
      messageForwardSelectedIds.value = [];
      return;
    }
    if (action === 'favorite') {
      await submitMessageFavorite(message);
      return;
    }
    if (action === 'remind') {
      await submitMessageReminder(message);
      return;
    }
    if (action === 'quote') {
      quotedMessageId.value = message.id;
      closeMessageAction();
      showToast('已引用');
      return;
    }
    if (action === 'delete-or-recall') {
      if (messageActionCanRecall.value) {
        await submitMessageRecall();
        return;
      }
      await submitMessageDelete(message);
      return;
    }
  }

  function removeMessageLocally(message: ConversationMessage) {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    const currentMessages = im.messagesByConversation[String(conversationId)] ?? activeConversation.value.messages;
    const nextMessages = currentMessages.filter((item) => item.id !== message.id);
    im.messagesByConversation[String(conversationId)] = nextMessages;
    shell.mergeBackendMessages(String(conversationId), nextMessages);
  }

  async function submitMessageTranslate(message: ConversationMessage) {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    messageActionError.value = '';
    try {
      const translated = await employeeApi.im.translateMessage(conversationId, message.id);
      const key = String(conversationId);
      const currentMessages = im.messagesByConversation[key] ?? activeConversation.value.messages;
      const nextMessages = currentMessages.map((item) => (
        item.id === message.id ? { ...item, translatedText: translated.translatedText } : item
      ));
      im.messagesByConversation[key] = nextMessages;
      shell.mergeBackendMessages(key, nextMessages);
      closeMessageAction();
      showToast('已翻译');
    } catch (error) {
      messageActionError.value = error instanceof Error ? error.message : '翻译失败';
    }
  }

  function closeMessageForward() {
    if (im.actionLoading) return;
    messageForwardOpen.value = false;
    messageActionTarget.value = null;
    messageActionMenu.value = null;
    messageActionError.value = '';
    messageForwardSearch.value = '';
    messageForwardNote.value = '';
    messageForwardSelectedIds.value = [];
  }

  function toggleMessageForwardTarget(conversationId: EntityId) {
    const id = String(conversationId);
    messageForwardSelectedIds.value = messageForwardSelectedIds.value.includes(id)
      ? messageForwardSelectedIds.value.filter((item) => item !== id)
      : [...messageForwardSelectedIds.value, id];
  }

  async function submitMessageForward() {
    const message = messageActionTarget.value;
    const conversationId = activeConversationId();
    if (!message || !conversationId) return;
    if (!messageForwardSelectedIds.value.length) {
      messageActionError.value = '请选择会话';
      return;
    }
    messageActionError.value = '';
    try {
      const targetCount = messageForwardSelectedIds.value.length;
      const note = messageForwardNote.value.trim();
      await Promise.all(messageForwardSelectedIds.value.map(async (targetConversationId) => {
        await employeeApi.im.forwardMessage(conversationId, message.id, { targetConversationId });
        if (note) {
          await employeeApi.im.sendMessage(targetConversationId, {
            messageType: 'TEXT',
            text: note,
            clientMessageId: `forward-note-${targetConversationId}-${message.id}-${Date.now()}`,
          });
        }
      }));
      closeMessageForward();
      showToast(`已转发给 ${targetCount} 个会话`);
    } catch (error) {
      messageActionError.value = error instanceof Error ? error.message : '转发失败';
    }
  }

  async function submitMessageFavorite(message: ConversationMessage) {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    messageActionError.value = '';
    try {
      await employeeApi.im.favoriteMessage(conversationId, message.id);
      closeMessageAction();
      showToast('已收藏');
    } catch (error) {
      messageActionError.value = error instanceof Error ? error.message : '收藏失败';
    }
  }

  async function submitMessageReminder(message: ConversationMessage) {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    messageActionError.value = '';
    try {
      await employeeApi.im.remindMessage(conversationId, message.id, { note: message.body.slice(0, 200) });
      closeMessageAction();
      showToast('已添加提醒');
    } catch (error) {
      messageActionError.value = error instanceof Error ? error.message : '提醒失败';
    }
  }

  async function submitMessageDelete(message: ConversationMessage) {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    messageActionError.value = '';
    try {
      await im.deleteMessage(conversationId, message.id);
      removeMessageLocally(message);
      closeMessageAction();
      showToast('已删除');
    } catch (error) {
      messageActionError.value = error instanceof Error ? error.message : '删除失败';
    }
  }

  async function submitMessageEdit() {
    const message = messageActionTarget.value;
    const conversationId = activeConversationId();
    if (!message || !conversationId) return;
    try {
      await im.editMessage(conversationId, message.id, messageEditText.value, session.user?.userId);
      shell.mergeBackendMessages(String(conversationId), im.messagesByConversation[String(conversationId)] ?? []);
      closeMessageAction();
      showToast('消息已更新');
    } catch (error) {
      messageActionError.value = error instanceof Error ? error.message : '编辑失败';
    }
  }

  async function submitMessageRecall() {
    const message = messageActionTarget.value;
    const conversationId = activeConversationId();
    if (!message || !conversationId) return;
    try {
      await im.recallMessage(conversationId, message.id, session.user?.userId);
      shell.mergeBackendMessages(String(conversationId), im.messagesByConversation[String(conversationId)] ?? []);
      closeMessageAction();
      showToast('消息已撤回');
    } catch (error) {
      messageActionError.value = error instanceof Error ? error.message : '撤回失败';
    }
  }

  async function openImAttachment(message: ConversationMessage, attachmentRefId: EntityId, action: 'preview' | 'download') {
    const conversationId = activeConversationId();
    if (!conversationId) return;
    try {
      const ticket = await employeeApi.im.createMessageAttachmentDownloadTicket(conversationId, message.id, attachmentRefId, {
        downloadType: 'SINGLE',
        reason: action === 'preview' ? '预览附件' : '下载附件',
      });
      const attachment = message.attachments?.find((item) => item.attachmentRefId === attachmentRefId);
      const file: PersonalFileRecord = {
        id: attachment?.id ?? `im-attachment-${attachmentRefId}`,
        name: attachment?.name ?? ticket.fileName,
        type: attachment?.type ?? fileTypeLabelFromName(ticket.fileName),
        size: attachment?.size ?? fileSizeLabel(ticket.fileSize),
        createdAt: '',
        status: attachment?.status ?? '',
        attachmentRefId,
        mimeType: attachment?.mimeType,
      };
      const url = resolveDownloadUrl(ticket, employeeApi.im.downloadAttachmentUrl(ticket.ticketNo));
      if (action === 'download') {
        window.open(url, '_blank', 'noopener');
        return;
      }
      await openFilePreviewFromUrl(file, url);
    } catch (error) {
      showToast(error instanceof Error ? error.message : '附件打开失败');
    }
  }

  function insertAgentMention() {
    if (!draft.value.includes('@AI')) {
      draft.value = draft.value ? `${draft.value} @AI ` : '@AI ';
    }
  }

  function insertMemberMention(member: MessageMention) {
    const token = `@${member.originalName || member.name} `;
    draft.value = draft.value ? `${draft.value.trimEnd()} ${token}` : token;
    if (!draftMentions.value.some((item) => String(item.userId) === String(member.userId))) {
      draftMentions.value = [...draftMentions.value, member];
    }
  }

  function mentionForMessage(message: ConversationMessage): MessageMention | null {
    if (message.senderType !== 'USER' || message.senderId === undefined || message.senderId === null || message.side === 'right') return null;
    return mentionableMembers.value.find((member) => String(member.userId) === String(message.senderId)) ?? {
      userId: message.senderId,
      name: message.originalAuthor || message.author,
      originalName: message.originalAuthor || message.author,
      role: message.role,
    };
  }

  function escapeRegExp(value: string) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function hasMentionToken(text: string, name: string) {
    return new RegExp(`(^|[\\s，。,.!?！？、:：；;])@${escapeRegExp(name)}(?=$|[\\s，。,.!?！？、:：；;])`).test(text);
  }

  function mentionsFromText(text: string, members = mentionableMembers.value) {
    const normalized = text.trim();
    if (!normalized) return [];
    return members.filter((member) => (
      hasMentionToken(normalized, member.originalName || member.name)
    ));
  }

  async function scrollMessagesToLatest(behavior: ScrollBehavior = 'smooth') {
    await nextTick();
    await scrollElementToBottomAfterLayout(() => messageStream.value, { behavior });
  }

  async function sendMessage() {
    const text = draft.value.trim();
    if (!text || im.messageSending) return;
    if (!session.isAuthenticated) {
      showToast('未登录');
      return;
    }
    const conversationId = activeConversationId();
    if (!conversationId) {
      showToast('不可发送');
      return;
    }
    const conversationKey = String(conversationId);
    const agentLoadingId = createClientId('agent-loading');
    const isAgentConversation = activeConversation.value.kind === 'agent';
    const loadingMessage = {
      id: agentLoadingId,
      type: 'knowledge-card' as const,
      author: 'AI 助手',
      role: 'AI 助手',
      body: '正在查询',
      side: 'left' as const,
      knowledgeCard: {
        state: 'INGESTING' as const,
        category: 'QA' as const,
        status: 'LOADING' as const,
        title: '正在查询',
        answer: '',
      },
    };
    if (isAgentConversation) {
      im.upsertMessage(conversationKey, loadingMessage);
      shell.mergeBackendMessages(conversationKey, im.messagesByConversation[conversationKey] ?? []);
      void scrollMessagesToLatest();
    }
    try {
      const parsedMentions = mentionsFromText(text);
      const mentions = [
        ...draftMentions.value.filter((member) => hasMentionToken(text, member.originalName || member.name)),
        ...parsedMentions,
      ].filter((member, index, rows) => (
        rows.findIndex((item) => String(item.userId) === String(member.userId)) === index
      ));
      const sent = await im.sendMessage(
        conversationId,
        text,
        session.user?.userId,
        employeeApi.im,
        quotedMessageId.value ?? undefined,
        mentions.map((mention) => mention.userId),
      );
      if (sent && mentions.length) {
        const currentMessages = im.messagesByConversation[conversationKey] ?? [];
        im.messagesByConversation[conversationKey] = currentMessages.map((message) => (
          message.id === sent.id ? { ...message, mentions } : message
        ));
      }
      const messages = im.messagesByConversation[conversationKey];
      if (messages?.length) {
        shell.mergeBackendMessages(conversationKey, messages);
      }
      if (isAgentConversation) {
        const currentMessages = im.messagesByConversation[conversationKey] ?? [];
        if (currentMessages[currentMessages.length - 1]?.id !== agentLoadingId) {
          im.messagesByConversation[conversationKey] = [
            ...currentMessages.filter((item) => item.id !== agentLoadingId),
            loadingMessage,
          ];
          shell.mergeBackendMessages(conversationKey, im.messagesByConversation[conversationKey]);
        }
      }
      draft.value = '';
      draftMentions.value = [];
      quotedMessageId.value = null;
      await scrollMessagesToLatest();
      if (isAgentConversation && sent?.seqNo !== undefined && !wsConnected.value) {
        void im.pollAgentReply(conversationId, sent.seqNo, session.user?.userId).then(() => {
          const refreshedMessages = im.messagesByConversation[conversationKey];
          if (refreshedMessages?.length) {
            const refreshedMembers = mentionableMembers.value;
            const parsedMessages = refreshedMessages
              .filter((item) => !item.id.startsWith('agent-loading-'))
              .map((message) => ({
                ...message,
                mentions: message.mentions ?? mentionsFromText(message.body, refreshedMembers),
              }));
            im.messagesByConversation[conversationKey] = parsedMessages;
            shell.mergeBackendMessages(conversationKey, parsedMessages);
            void scrollMessagesToLatest();
          }
        }).catch(() => undefined);
      }
    } catch (error) {
      if (isAgentConversation) {
        const messages = im.messagesByConversation[conversationKey]?.filter((item) => item.id !== agentLoadingId) ?? [];
        im.messagesByConversation[conversationKey] = messages;
        if (messages.length) {
          shell.mergeBackendMessages(conversationKey, messages);
        } else {
          const conversation = shell.conversations.find((item) => item.id === conversationKey);
          if (conversation) conversation.messages = [];
        }
      }
      showToast(error instanceof Error ? error.message : '消息发送失败');
    }
  }

  watch(
    [
      () => shell.active.messages,
      () => shell.activeConversation.messages.length,
      () => im.messageLoading,
    ],
    () => {
      if (!hasActiveConversation.value) return;
      void scrollMessagesToLatest('auto');
    },
    { flush: 'post' },
  );

  watch(
    () => shell.active.messages,
    () => {
      quotedMessageId.value = null;
      draftMentions.value = [];
    },
  );

  return {
    draft,
    draftMentions,
    messageStream,
    createConversationOpen,
    createConversationMode,
    createConversationForm,
    createConversationSubmitting,
    createConversationError,
    agents,
    agentsLoading,
    agentKeyword,
    groupPanelOpen,
    groupProfileForm,
    groupActionError,
    groupMemberAdding,
    messageActionTarget,
    messageActionMenu,
    messageEditText,
    messageActionError,
    messageForwardOpen,
    messageForwardSearch,
    messageForwardNote,
    messageForwardSelectedIds,
    messageForwardConversations,
    quotedMessageId,
    quotedMessagePreview,
    messageActionCanRecall,
    activeConversation,
    activeConversationTitle,
    activeConversationCurrentStep,
    activeGroupProfile,
    activeConversationPreferenceRows,
    hasActiveConversation,
    mentionableMembers,
    backToList,
    openCreateConversation,
    closeCreateConversation,
    submitCreateConversation,
    searchAgents,
    openGroupPanel,
    closeGroupPanel,
    saveGroupProfile,
    toggleGroupMemberAdding,
    addGroupMembers,
    removeGroupMembers,
    leaveActiveGroup,
    dismissActiveGroup,
    toggleActiveConversationPreference,
    resolveQuotedMessage,
    cancelQuote,
    jumpToQuotedMessage,
    mentionsFromText,
    openMessageAction,
    closeMessageAction,
    runMessageMenuAction,
    closeMessageForward,
    toggleMessageForwardTarget,
    submitMessageForward,
    submitMessageEdit,
    submitMessageRecall,
    openImAttachment,
    insertAgentMention,
    insertMemberMention,
    mentionForMessage,
    sendMessage,
    activeConversationId,
  };
}
