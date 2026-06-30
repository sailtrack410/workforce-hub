import { computed, nextTick, ref, watch } from 'vue';
import { employeeApi } from '@/api';
import type { EntityId } from '@/api';
import type { ConversationMessage, PersonalFileRecord, useShellStore } from '@/stores/shell';
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
      draft.value = `> ${message.body}\n\n${draft.value}`;
      closeMessageAction();
      showToast('已引用到输入框');
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
      const sent = await im.sendMessage(conversationId, text, session.user?.userId, employeeApi.im, quotedMessageId.value ?? undefined);
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
      quotedMessageId.value = null;
      await scrollMessagesToLatest();
      if (isAgentConversation && sent?.seqNo !== undefined && !wsConnected.value) {
        void im.pollAgentReply(conversationId, sent.seqNo, session.user?.userId).then(() => {
          const refreshedMessages = im.messagesByConversation[conversationKey];
          if (refreshedMessages?.length) {
            shell.mergeBackendMessages(conversationKey, refreshedMessages.filter((item) => !item.id.startsWith('agent-loading-')));
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

  return {
    draft,
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
    messageActionCanRecall,
    activeConversation,
    activeConversationTitle,
    activeConversationCurrentStep,
    activeGroupProfile,
    activeConversationPreferenceRows,
    hasActiveConversation,
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
    sendMessage,
    activeConversationId,
  };
}
