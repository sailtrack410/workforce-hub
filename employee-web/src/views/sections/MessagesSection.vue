<script setup lang="ts">
import {
  AtSign,
  Bell,
  ChevronLeft,
  Clock3,
  Copy,
  Download,
  Eye,
  FolderOpen,
  Forward,
  Info,
  Languages,
  Paperclip,
  Pin,
  Plus,
  Quote,
  RotateCcw,
  Search,
  Send,
  Sparkles,
  Star,
  Trash2,
  UserMinus,
  UsersRound,
  VolumeX,
  X,
} from 'lucide-vue-next';
import { computed, ref } from 'vue';
import { useMessagesSectionContext } from '../employee-shell-context';
import { useGroupFiles } from '@/composables/useGroupFiles';
import { useMessagesState } from '@/composables/useMessagesState';
import { agentTypeLabel, formatDateTime, humanizeText, statusText } from '@/utils/employee-format';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { fileIconOf, fileToneOf } from '@/utils/file-record';
import { employeeApi } from '@/api';
import AppButton from '../../components/ui/AppButton.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppDrawer from '../../components/ui/AppDrawer.vue';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppInput from '../../components/ui/AppInput.vue';
import AppSkeleton from '../../components/ui/AppSkeleton.vue';
import TodoCreateDialog from '../../components/todos/TodoCreateDialog.vue';
import ConversationProcessCard from '../../components/shell/ConversationProcessCard.vue';
import KnowledgeAnswerCard from '../../components/shell/cards/KnowledgeAnswerCard.vue';
import MessageBubble from '../../components/shell/MessageBubble.vue';
import { DEFAULT_CONVERSATION_AVATAR_URL } from '@/stores/im';
import type { ConversationMessage, MessageMention } from '@/stores/shell';

const {
  shell,
  im,
  session,
  wsConnected,
  showToast,
  openDetail,
  openUploadPanel,
  syncConversationMessages,
  navigateMessagesConversation,
  openFilePreviewFromUrl,
} = useMessagesSectionContext();

const {
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
  messageActionError,
  messageForwardOpen,
  messageForwardSearch,
  messageForwardNote,
  messageForwardSelectedIds,
  messageForwardConversations,
  quotedMessagePreview,
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
  openImAttachment,
  insertAgentMention,
  insertMemberMention,
  mentionForMessage,
  sendMessage,
  activeConversationId,
} = useMessagesState({
  shell,
  im,
  session,
  wsConnected,
  showToast,
  syncConversationMessages,
  navigateMessagesConversation,
  openFilePreviewFromUrl,
});

const {
  groupFiles,
  groupFilesLoading,
  groupFileUploading,
  groupFileInput,
  loadGroupFiles,
  openGroupFilePicker,
  handleGroupFileSelected,
  openGroupFile,
  trashGroupFile,
} = useGroupFiles({
  activeConversationId,
  openFilePreviewFromUrl,
  showToast,
});

const todoCreateOpen = ref(false);
const mentionProfile = ref<MessageMention | null>(null);
const mentionActionLoading = ref(false);
const draftMentionNames = computed(() => mentionsFromText(draft.value).map((item) => `@${item.originalName || item.name}`));
const selectableContacts = computed(() => shell.contacts.filter((item) => (
  item.relation !== '本人' && item.userId !== undefined && item.userId !== null
)));
const selectedCreateMemberIds = computed(() => new Set(
  createConversationForm.value.memberUserIds
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean),
));
const selectedGroupMemberIds = computed(() => new Set(
  groupProfileForm.value.memberUserIds
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean),
));

async function openGroupManagement() {
  await openGroupPanel();
  if (groupPanelOpen.value) void loadGroupFiles();
}

function contactSubtitle(contact: { role?: string; department?: string; relation?: string }) {
  return [contact.department, contact.role, contact.relation].filter(Boolean).join(' · ');
}

function toggleCreateMember(userId?: string | number) {
  if (userId === undefined || userId === null) return;
  const id = String(userId);
  const next = new Set(selectedCreateMemberIds.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  createConversationForm.value.memberUserIds = Array.from(next).join(',');
}

function toggleGroupMember(userId?: string | number) {
  if (userId === undefined || userId === null) return;
  const id = String(userId);
  const next = new Set(selectedGroupMemberIds.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  groupProfileForm.value.memberUserIds = Array.from(next).join(',');
}

function messageAvatarUrl(message: ConversationMessage) {
  if (message.side === 'system') return '';
  if (message.side === 'right') return shell.avatarUrl || DEFAULT_CONVERSATION_AVATAR_URL;
  if (message.senderType === 'AGENT') return activeConversation.value.avatarUrl || DEFAULT_CONVERSATION_AVATAR_URL;
  if (message.senderId !== undefined && message.senderId !== null) {
    const contact = shell.contacts.find((item) => (
      item.userId !== undefined && item.userId !== null && String(item.userId) === String(message.senderId)
    ));
    if (contact?.avatarUrl) return contact.avatarUrl;
  }
  return DEFAULT_CONVERSATION_AVATAR_URL;
}

const mentionProfileContact = computed(() => {
  const profile = mentionProfile.value;
  if (!profile) return null;
  return shell.contacts.find((item) => (
    item.userId !== undefined && item.userId !== null && String(item.userId) === String(profile.userId)
  )) ?? null;
});

const mentionProfileIsFriend = computed(() => {
  const profile = mentionProfile.value;
  return profile ? shell.friendUserIds.has(String(profile.userId)) : false;
});

function openMentionProfile(mention: MessageMention) {
  mentionProfile.value = mention;
}

function closeMentionProfile() {
  if (mentionActionLoading.value) return;
  mentionProfile.value = null;
}

async function addMentionFriend() {
  const profile = mentionProfile.value;
  if (!profile || mentionActionLoading.value || mentionProfileIsFriend.value) return;
  mentionActionLoading.value = true;
  try {
    await employeeApi.contacts.addFriend(profile.userId);
    const friends = await employeeApi.contacts.friends();
    shell.setFriends(friends);
    shell.syncSessionProfile(session.user, session.profile, session.manager, session.subordinates);
    showToast('已发送好友申请');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '好友申请失败');
  } finally {
    mentionActionLoading.value = false;
  }
}

async function sendMentionPrivateMessage() {
  const profile = mentionProfile.value;
  if (!profile || mentionActionLoading.value) return;
  mentionActionLoading.value = true;
  try {
    const conversation = await im.startPrivateConversation(profile.userId);
    shell.mergeBackendConversations(im.conversations);
    mentionProfile.value = null;
    navigateMessagesConversation(conversation.id);
    await syncConversationMessages(conversation.id);
    showToast('已打开会话');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '私聊创建失败');
  } finally {
    mentionActionLoading.value = false;
  }
}
</script>

<template>
<div class="workspace" @click="closeMessageAction">
  <header v-if="hasActiveConversation" class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">{{ activeConversationTitle }}</h2>
        <div class="workspace-meta-line">
          <span>{{ activeConversation.meta }}</span>
          <span v-if="activeConversationCurrentStep">{{ activeConversationCurrentStep.label }}</span>
        </div>
      </div>
    </div>
    <div class="head-actions">
      <AppButton variant="secondary" size="sm" aria-label="添加待办" @click="todoCreateOpen = true">
        <Clock3 :size="16" />
        添加待办
      </AppButton>
      <AppButton variant="secondary" size="sm" aria-label="详情" @click="openDetail">
        <Info :size="16" />
        详情
      </AppButton>
      <AppButton v-if="activeConversation.kind === 'team'" variant="secondary" size="sm" aria-label="群管理" @click="openGroupManagement">
        <UsersRound :size="16" />
        群管理
      </AppButton>
    </div>
  </header>

  <section v-if="hasActiveConversation" ref="messageStream" class="message-stream">
    <div v-if="im.messageLoading" class="stream-placeholder">
      <AppSkeleton />
      <AppSkeleton />
      <AppSkeleton />
    </div>

    <template v-else>
      <article
        v-for="message in activeConversation.messages"
        :key="message.id"
        class="stream-row"
        :class="[message.type === 'process-card' || message.type === 'knowledge-card' ? 'is-process' : '', message.side]"
        :data-message-id="message.id"
      >
        <div v-if="message.side === 'system'" class="system-line">
          <strong>{{ message.author }} · {{ message.role }}</strong>
          <span>{{ message.body }}</span>
        </div>
        <ConversationProcessCard
          v-else-if="message.type === 'process-card' && message.processCard"
          :message="message"
          @open="openDetail"
        />
        <KnowledgeAnswerCard
          v-else-if="message.type === 'knowledge-card' && message.knowledgeCard"
          v-bind="message.knowledgeCard"
          @citation="openDetail"
          @flow="openDetail"
          @action="openDetail"
        />
        <MessageBubble
          v-else
          :message="message"
          :agent="message.role.includes('AI') || message.author.includes('助手')"
          :avatar-url="messageAvatarUrl(message)"
          :quote="resolveQuotedMessage(message.quotedMessageId)"
          :sender-mention="mentionForMessage(message)"
          @action="openMessageAction"
          @attachment="openImAttachment"
          @quote="jumpToQuotedMessage"
          @mention="openMentionProfile"
          @insert-mention="insertMemberMention"
        />
      </article>
    </template>
  </section>

  <section v-else class="content-stack">
    <AppEmptyState title="暂无会话">
      <div class="head-actions">
        <AppButton variant="secondary" size="sm" @click="openCreateConversation('private')">
          私聊
        </AppButton>
        <AppButton variant="secondary" size="sm" @click="openCreateConversation('group')">
          群聊
        </AppButton>
        <AppButton variant="secondary" size="sm" @click="openCreateConversation('agent')">
          <Sparkles :size="16" />
          AI
        </AppButton>
      </div>
    </AppEmptyState>
  </section>

  <footer v-if="hasActiveConversation" class="composer">
    <div v-if="quotedMessagePreview" class="composer-quote-bar">
      <button
        class="composer-quote-card"
        type="button"
        :disabled="!quotedMessagePreview.visible"
        @click="jumpToQuotedMessage(quotedMessagePreview.id)"
      >
        <Quote :size="14" />
        <span>
          <strong>{{ quotedMessagePreview.author }}</strong>
          <em>{{ quotedMessagePreview.body }}</em>
        </span>
      </button>
      <button class="field-icon-button" type="button" aria-label="取消引用" @click="cancelQuote">
        <X :size="15" />
      </button>
    </div>
    <div class="composer-tools" aria-label="消息工具">
      <button class="composer-tool-button" type="button" @click="openUploadPanel">
        <Paperclip :size="16" />
        上传
      </button>
      <button class="composer-tool-button" type="button" @click="() => openDetail()">
        <FolderOpen :size="16" />
        材料
      </button>
      <button class="composer-tool-button" type="button" @click="insertAgentMention">
        <AtSign :size="16" />
        AI
      </button>
      <button class="composer-tool-button" type="button" @click="openCreateConversation('agent')">
        <Sparkles :size="16" />
        AI 会话
      </button>
    </div>
    <div class="composer-main-row">
      <div class="composer-input composer-mention-input">
        <div v-if="draftMentionNames.length" class="composer-mention-preview" aria-hidden="true">
          <span v-for="name in draftMentionNames" :key="name">{{ name }}</span>
        </div>
        <AppInput v-model="draft" placeholder="输入消息，支持 @AI" :disabled="im.messageSending" @keydown.enter.prevent="sendMessage" />
      </div>
      <AppButton variant="primary" :disabled="im.messageSending" @click="sendMessage">
        <Send :size="18" />
        发送
      </AppButton>
    </div>
  </footer>

  <AppDialog v-if="createConversationOpen">
    <form class="conversation-dialog" @submit.prevent="submitCreateConversation">
      <header class="dialog-head dialog-head-row">
        <div>
          <h2>{{ createConversationMode === 'private' ? '新建私聊' : createConversationMode === 'group' ? '新建群聊' : 'AI 会话' }}</h2>
        </div>
        <button class="field-icon-button" type="button" :disabled="createConversationSubmitting" aria-label="关闭" @click="closeCreateConversation">
          <X :size="15" />
        </button>
      </header>

      <div class="conversation-mode-row">
        <button type="button" :class="{ active: createConversationMode === 'private' }" @click="createConversationMode = 'private'">私聊</button>
        <button type="button" :class="{ active: createConversationMode === 'group' }" @click="createConversationMode = 'group'">群聊</button>
        <button type="button" :class="{ active: createConversationMode === 'agent' }" @click="openCreateConversation('agent')">AI</button>
      </div>

      <section v-if="createConversationMode === 'private'" class="security-field">
        <span>选择成员</span>
        <div class="member-picker-list">
          <button
            v-for="contact in selectableContacts"
            :key="`private-${contact.id}`"
            type="button"
            class="member-picker-row"
            :class="{ active: createConversationForm.targetUserId === String(contact.userId) }"
            @click="createConversationForm.targetUserId = String(contact.userId)"
          >
            <AppAvatar :label="contact.name" :image="contact.avatarUrl" tone="primary" size="sm" />
            <span>
              <strong>{{ contact.name }}</strong>
              <small>{{ contactSubtitle(contact) }}</small>
            </span>
          </button>
          <p v-if="!selectableContacts.length" class="agent-empty">暂无可选成员</p>
        </div>
      </section>

      <template v-else-if="createConversationMode === 'group'">
        <label class="security-field">
          <span>群名称</span>
          <input v-model="createConversationForm.groupTitle" class="ui-inline-input" />
        </label>
        <section class="security-field">
          <span>选择成员</span>
          <div class="member-picker-list">
            <button
              v-for="contact in selectableContacts"
              :key="`group-${contact.id}`"
              type="button"
              class="member-picker-row"
              :class="{ active: selectedCreateMemberIds.has(String(contact.userId)) }"
              @click="toggleCreateMember(contact.userId)"
            >
              <AppAvatar :label="contact.name" :image="contact.avatarUrl" tone="primary" size="sm" />
              <span>
                <strong>{{ contact.name }}</strong>
                <small>{{ contactSubtitle(contact) }}</small>
              </span>
            </button>
            <p v-if="!selectableContacts.length" class="agent-empty">暂无可选成员</p>
          </div>
        </section>
      </template>

      <template v-else>
        <label class="security-field">
          <span>搜索 AI 助手</span>
          <div class="agent-search-row">
            <input v-model="createConversationForm.agentKeyword" class="ui-inline-input" @keydown.enter.prevent="searchAgents" />
            <AppButton variant="secondary" type="button" :disabled="agentsLoading" @click="searchAgents">
              {{ agentsLoading ? '搜索中' : '搜索' }}
            </AppButton>
          </div>
        </label>
        <div class="agent-result-list">
          <button
            v-for="agent in agents"
            :key="agent.agentId"
            type="button"
            class="agent-result-row"
            :class="{ active: createConversationForm.agentId === String(agent.agentId) }"
            @click="createConversationForm.agentId = String(agent.agentId)"
          >
            <strong>{{ humanizeText(agent.agentName) }}</strong>
            <span>{{ agent.agentTypeName || agentTypeLabel(agent.agentType) }}</span>
          </button>
          <p v-if="!agentsLoading && agentKeyword && !agents.length" class="agent-empty">未找到 AI 助手</p>
        </div>
      </template>

      <p v-if="createConversationError" class="dialog-error">{{ createConversationError }}</p>
      <div class="dialog-actions">
        <AppButton variant="ghost" type="button" :disabled="createConversationSubmitting" @click="closeCreateConversation">
          取消
        </AppButton>
        <AppButton variant="primary" type="submit" :disabled="createConversationSubmitting">
          {{ createConversationSubmitting ? '创建中' : '创建' }}
        </AppButton>
      </div>
    </form>
  </AppDialog>

  <AppDialog v-if="mentionProfile">
    <section class="mention-profile-dialog" @click.stop>
      <header class="dialog-head dialog-head-row">
        <div class="mention-profile-head">
          <AppAvatar
            :label="mentionProfile.originalName || mentionProfile.name"
            :image="mentionProfile.avatarUrl || mentionProfileContact?.avatarUrl"
            tone="primary"
            size="lg"
          />
          <span>
            <h2>{{ mentionProfile.originalName || mentionProfile.name }}</h2>
            <p>{{ [mentionProfile.department || mentionProfileContact?.department, mentionProfile.role || mentionProfileContact?.role].filter(Boolean).join(' · ') }}</p>
          </span>
        </div>
        <button class="field-icon-button" type="button" :disabled="mentionActionLoading" aria-label="关闭" @click="closeMentionProfile">
          <X :size="15" />
        </button>
      </header>
      <div class="dialog-actions">
        <AppButton
          variant="secondary"
          type="button"
          :disabled="mentionActionLoading || mentionProfileIsFriend"
          @click="addMentionFriend"
        >
          {{ mentionProfileIsFriend ? '已是好友' : '加为好友' }}
        </AppButton>
        <AppButton variant="primary" type="button" :disabled="mentionActionLoading" @click="sendMentionPrivateMessage">
          发消息
        </AppButton>
      </div>
    </section>
  </AppDialog>

  <TodoCreateDialog :open="todoCreateOpen" @close="todoCreateOpen = false" />

  <!-- 群管理侧边抽屉 -->
  <Teleport to="body">
    <div v-if="groupPanelOpen" class="group-drawer-overlay" @click="closeGroupPanel" />
    <aside class="group-drawer" :class="{ open: groupPanelOpen }">
      <AppDrawer>
        <input ref="groupFileInput" class="visually-hidden-input" type="file" @change="handleGroupFileSelected" />
        <!-- 头部：群资料卡片 -->
        <header class="group-drawer-head">
          <div class="group-head-top">
            <AppAvatar
              :label="activeGroupProfile?.title || activeConversation.title"
              tone="primary"
              size="lg"
            />
            <div class="group-head-info">
              <h2>{{ activeGroupProfile?.title || activeConversation.title }}</h2>
              <span class="group-member-count">
                <UsersRound :size="13" />
                {{ activeConversation.panel.members.length }}人
              </span>
            </div>
            <button class="field-icon-button" type="button" :disabled="im.actionLoading" aria-label="关闭" @click="closeGroupPanel">
              <X :size="16" />
            </button>
          </div>
          <div v-if="groupProfileForm.noticeText || activeGroupProfile?.noticeText" class="group-notice-preview">
            <span>群公告：</span>
            <p>{{ activeGroupProfile?.noticeText || groupProfileForm.noticeText }}</p>
          </div>
        </header>

        <!-- 成员区 -->
        <section class="group-drawer-section">
          <div class="group-section-header">
            <h3>成员</h3>
            <AppButton variant="ghost" size="sm" @click="toggleGroupMemberAdding">
              <Plus :size="14" />
              添加
            </AppButton>
          </div>

          <!-- 添加成员内联输入 -->
          <div v-if="groupMemberAdding" class="group-add-member-inline">
            <section class="security-field">
              <span>选择成员</span>
              <div class="member-picker-list">
                <button
                  v-for="contact in selectableContacts"
                  :key="`group-add-${contact.id}`"
                  type="button"
                  class="member-picker-row"
                  :class="{ active: selectedGroupMemberIds.has(String(contact.userId)) }"
                  @click="toggleGroupMember(contact.userId)"
                >
                  <AppAvatar :label="contact.name" :image="contact.avatarUrl" tone="primary" size="sm" />
                  <span>
                    <strong>{{ contact.name }}</strong>
                    <small>{{ contactSubtitle(contact) }}</small>
                  </span>
                </button>
                <p v-if="!selectableContacts.length" class="agent-empty">暂无可选成员</p>
              </div>
            </section>
            <div class="form-actions">
              <AppButton variant="secondary" size="sm" :disabled="im.actionLoading || !groupProfileForm.memberUserIds.trim()" @click="addGroupMembers">
                确认添加
              </AppButton>
              <AppButton variant="ghost" size="sm" @click="groupMemberAdding = false">取消</AppButton>
            </div>
          </div>

          <!-- 成员卡片网格 -->
          <div class="group-member-grid">
            <div v-for="member in activeConversation.panel.members" :key="`${member.name}-${member.role}`" class="group-member-card">
              <AppAvatar :label="member.name" tone="primary" size="md" />
              <span class="group-member-name">{{ member.name }}</span>
              <span v-if="member.role === '群主'" class="group-role-badge owner">群主</span>
              <button
                v-else-if="activeGroupProfile?.currentMemberRole === 'OWNER'"
                class="group-member-remove"
                type="button"
                aria-label="移除成员"
                :disabled="im.actionLoading"
                @click="groupProfileForm.removeMemberUserIds = String(member.userId || ''); removeGroupMembers()"
              >
                <UserMinus :size="13" />
              </button>
            </div>
          </div>
        </section>

        <!-- 群文件 -->
        <section class="group-drawer-section">
          <div class="group-section-header">
            <h3>群文件</h3>
            <AppButton variant="ghost" size="sm" :disabled="groupFileUploading" @click="openGroupFilePicker">
              <Plus :size="14" />
              {{ groupFileUploading ? '上传中' : '上传' }}
            </AppButton>
          </div>
          <div v-if="groupFilesLoading" class="group-file-loading">
            <AppSkeleton />
          </div>
          <p v-else-if="!groupFiles.length" class="group-file-empty">暂无文件</p>
          <div v-else class="file-list group-file-list">
            <div v-for="file in groupFiles" :key="file.attachmentRefId" class="file-row group-file-row">
              <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
                <component :is="fileIconOf(file)" :size="18" />
              </span>
              <div>
                <strong>{{ file.displayName }}</strong>
                <small>
                  {{ fileTypeLabelFromName(file.displayName, file.mimeType) }} · {{ fileSizeLabel(file.fileSize) }} · {{ file.uploaderName || '成员' }} · {{ formatDateTime(file.createdAt) }} · {{ file.scanStatusName || statusText(file.scanStatus) }}
                </small>
              </div>
              <div class="file-actions">
                <button type="button" @click="openGroupFile(file, 'preview')">
                  <Eye :size="15" />
                  预览
                </button>
                <button type="button" @click="openGroupFile(file, 'download')">
                  <Download :size="15" />
                  下载
                </button>
                <button v-if="file.canDelete" type="button" @click="trashGroupFile(file)">
                  <Trash2 :size="15" />
                  删除
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- 群资料编辑（可折叠） -->
        <section class="group-drawer-section">
          <div class="group-section-header">
            <h3>群资料</h3>
            <AppButton variant="primary" size="sm" :disabled="im.actionLoading" @click="saveGroupProfile">
              保存
            </AppButton>
          </div>
          <label class="security-field">
            <span>群名称</span>
            <input v-model="groupProfileForm.title" class="ui-inline-input" />
          </label>
          <label class="security-field">
            <span>群公告</span>
            <textarea v-model="groupProfileForm.noticeText" class="ui-textarea compact-textarea" rows="2" />
          </label>
        </section>

        <!-- 会话设置 -->
        <section class="group-drawer-section">
          <h3>会话设置</h3>
          <div class="group-setting-list">
            <button class="group-setting-row" type="button" :disabled="im.actionLoading" @click="toggleActiveConversationPreference('pin')">
              <Pin :size="16" />
              <span>置顶</span>
              <span class="group-setting-value" :class="{ active: activeConversationPreferenceRows[0]?.[1] === '已开启' }">
                {{ activeConversationPreferenceRows[0]?.[1] || '未开启' }}
              </span>
            </button>
            <button class="group-setting-row" type="button" :disabled="im.actionLoading" @click="toggleActiveConversationPreference('mute')">
              <VolumeX :size="16" />
              <span>免打扰</span>
              <span class="group-setting-value" :class="{ active: activeConversationPreferenceRows[1]?.[1] === '已开启' }">
                {{ activeConversationPreferenceRows[1]?.[1] || '未开启' }}
              </span>
            </button>
          </div>
        </section>

        <!-- 底部操作 -->
        <footer class="group-drawer-footer">
          <p v-if="groupActionError" class="dialog-error">{{ groupActionError }}</p>
          <AppButton variant="ghost" type="button" :disabled="im.actionLoading" block @click="leaveActiveGroup">
            退出群聊
          </AppButton>
          <AppButton v-if="activeGroupProfile?.currentMemberRole === 'OWNER'" variant="ghost" type="button" :disabled="im.actionLoading" block class="destructive-text" @click="dismissActiveGroup">
            解散群聊
          </AppButton>
        </footer>
      </AppDrawer>
    </aside>
  </Teleport>

  <div
    v-if="messageActionTarget && messageActionMenu"
    class="message-context-menu"
    :style="{ left: `${messageActionMenu.x}px`, top: `${messageActionMenu.y}px` }"
    @click.stop
  >
    <button type="button" @click="runMessageMenuAction('copy')">
      <Copy :size="15" />
      <span>复制</span>
    </button>
    <button type="button" @click="runMessageMenuAction('translate')">
      <Languages :size="15" />
      <span>翻译</span>
    </button>
    <button type="button" @click="runMessageMenuAction('forward')">
      <Forward :size="15" />
      <span>转发</span>
    </button>
    <button type="button" @click="runMessageMenuAction('favorite')">
      <Star :size="15" />
      <span>收藏</span>
    </button>
    <button type="button" @click="runMessageMenuAction('remind')">
      <Bell :size="15" />
      <span>提醒</span>
    </button>
    <button type="button" @click="runMessageMenuAction('quote')">
      <Quote :size="15" />
      <span>引用</span>
    </button>
    <button type="button" class="danger" :disabled="im.actionLoading" @click="runMessageMenuAction('delete-or-recall')">
      <component :is="messageActionCanRecall ? RotateCcw : Trash2" :size="15" />
      <span>{{ messageActionCanRecall ? '撤回' : '删除' }}</span>
    </button>
    <p v-if="messageActionError" class="message-context-error">{{ messageActionError }}</p>
  </div>

  <AppDialog v-if="messageForwardOpen" flush>
    <section class="forward-dialog" @click.stop>
      <aside class="forward-picker">
        <label class="forward-search">
          <Search :size="16" />
          <input v-model="messageForwardSearch" type="search" placeholder="搜索" autofocus />
        </label>
        <div class="forward-list-head">
          <strong>最近聊天</strong>
          <span>{{ messageForwardSelectedIds.length }} 已选</span>
        </div>
        <div class="forward-conversation-list">
          <button
            v-for="conversation in messageForwardConversations"
            :key="conversation.id"
            type="button"
            class="forward-conversation-row"
            :class="{ selected: messageForwardSelectedIds.includes(conversation.id) }"
            @click="toggleMessageForwardTarget(conversation.id)"
          >
            <span class="forward-check" aria-hidden="true">
              <span v-if="messageForwardSelectedIds.includes(conversation.id)" />
            </span>
            <AppAvatar :label="conversation.title" :image="conversation.avatarUrl" tone="primary" size="sm" />
            <span class="forward-row-text">
              <strong>{{ conversation.title }}</strong>
              <small>{{ conversation.summaryBody || conversation.flow }}</small>
            </span>
          </button>
          <p v-if="!messageForwardConversations.length" class="forward-empty">没有匹配会话</p>
        </div>
      </aside>

      <main class="forward-preview">
        <header class="forward-preview-head">
          <h2>发送给</h2>
          <button class="field-icon-button" type="button" :disabled="im.actionLoading" aria-label="关闭" @click="closeMessageForward">
            <X :size="15" />
          </button>
        </header>
        <section class="forward-content-preview">
          <p v-if="messageActionTarget?.body" class="forward-text-preview">{{ messageActionTarget.body }}</p>
          <div v-if="messageActionTarget?.attachments?.length" class="forward-attachment-preview">
            <span v-for="file in messageActionTarget.attachments" :key="file.id">
              {{ file.name }}
            </span>
          </div>
        </section>
        <label class="forward-note">
          <input v-model="messageForwardNote" placeholder="给朋友留言" />
        </label>
        <p v-if="messageActionError" class="message-context-error">{{ messageActionError }}</p>
        <footer class="forward-actions">
          <AppButton variant="ghost" type="button" :disabled="im.actionLoading" @click="closeMessageForward">取消</AppButton>
          <AppButton variant="primary" type="button" :disabled="im.actionLoading || !messageForwardSelectedIds.length" @click="submitMessageForward">
            发送
          </AppButton>
        </footer>
      </main>
    </section>
  </AppDialog>
</div>
</template>

<style scoped>
.message-context-menu {
  position: fixed;
  z-index: 1200;
  width: 11.25rem;
  padding: var(--space-6, 0.375rem);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-8, 0.5rem);
  background: var(--surface-panel);
  box-shadow: var(--shadow-lg, 0 18px 40px rgba(15, 23, 42, 0.16));
}

.mention-profile-dialog {
  display: grid;
  gap: var(--space-18, 1.125rem);
  min-width: min(22rem, calc(100vw - 2rem));
}

.mention-profile-head {
  display: flex;
  align-items: center;
  gap: var(--space-12, 0.75rem);
  min-width: 0;
}

.mention-profile-head span {
  display: grid;
  gap: var(--space-4, 0.25rem);
  min-width: 0;
}

.mention-profile-head h2,
.mention-profile-head p {
  margin: 0;
}

.mention-profile-head p {
  color: var(--text-tertiary);
  font-size: var(--text-body-sm);
}

.message-context-menu button {
  display: flex;
  align-items: center;
  gap: var(--space-8, 0.5rem);
  width: 100%;
  min-height: 2.25rem;
  padding: 0 var(--space-10, 0.625rem);
  border: 0;
  border-radius: var(--radius-6, 0.375rem);
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: 0.875rem;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}

.message-context-menu button:hover:not(:disabled) {
  background: var(--surface-accent);
}

.message-context-menu button.danger {
  color: var(--danger-600, #dc2626);
}

.message-context-menu button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.message-context-error {
  margin: var(--space-6, 0.375rem) var(--space-10, 0.625rem) 0;
  color: var(--danger-600, #dc2626);
  font-size: 0.75rem;
}

.composer-mention-input {
  display: grid;
  gap: var(--space-6, 0.375rem);
}

.composer-mention-preview {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-6, 0.375rem);
  min-height: 1.375rem;
}

.composer-mention-preview span {
  color: var(--text-accent);
  font-size: var(--text-caption-md);
  font-weight: 700;
}

.composer-quote-bar {
  display: flex;
  align-items: center;
  gap: var(--space-10, 0.625rem);
}

.composer-quote-card {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-8, 0.5rem);
  flex: 1;
  min-width: 0;
  padding: var(--space-10, 0.625rem) var(--space-12, 0.75rem) var(--space-10, 0.625rem) var(--space-14, 0.875rem);
  border: 1px solid color-mix(in srgb, var(--primary-200) 46%, var(--border-subtle));
  border-radius: var(--radius-10, 0.625rem);
  background: color-mix(in srgb, var(--surface-panel-muted) 78%, var(--surface-panel));
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--surface-panel) 72%, transparent);
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.composer-quote-card::before {
  content: '';
  position: absolute;
  top: var(--space-10, 0.625rem);
  bottom: var(--space-10, 0.625rem);
  left: var(--space-8, 0.5rem);
  width: 0.1875rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--primary-500) 74%, var(--primary-300));
}

.composer-quote-card:hover:not(:disabled),
.composer-quote-card:focus-visible {
  outline: none;
  border-color: color-mix(in srgb, var(--primary-400) 56%, var(--border-subtle));
  background: color-mix(in srgb, var(--surface-accent) 76%, var(--surface-panel));
  transform: translateY(-1px);
}

.composer-quote-card:disabled {
  cursor: default;
  opacity: 0.84;
}

.composer-quote-card span {
  display: grid;
  gap: var(--space-2, 0.125rem);
  min-width: 0;
}

.composer-quote-card strong,
.composer-quote-card em {
  overflow: hidden;
  text-overflow: ellipsis;
}

.composer-quote-card strong {
  color: var(--text-primary);
  font-size: var(--text-caption-md);
  font-style: normal;
  font-weight: 700;
  white-space: nowrap;
}

.composer-quote-card em {
  color: var(--text-secondary);
  font-size: var(--text-caption-md);
  font-style: normal;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.composer-quote-card :deep(svg) {
  color: var(--primary-600);
}

.stream-row.quote-target :deep(.bubble-content) {
  border-color: color-mix(in srgb, var(--primary-500) 58%, var(--border-subtle));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary-100) 82%, transparent);
}

@media (max-width: 768px) {
  .composer-quote-bar {
    align-items: stretch;
  }

  .composer-quote-card {
    min-width: 0;
  }
}

.forward-dialog {
  display: grid;
  grid-template-columns: minmax(17rem, 0.95fr) minmax(20rem, 1.25fr);
  width: min(50rem, calc(100vw - 2rem));
  height: min(34rem, calc(100vh - 4rem));
  overflow: hidden;
  border-radius: var(--radius-8, 0.5rem);
  background: var(--surface-panel);
}

.forward-picker {
  display: grid;
  grid-template-rows: auto auto 1fr;
  gap: var(--space-12, 0.75rem);
  min-width: 0;
  padding: var(--space-16, 1rem);
  border-right: 1px solid var(--border-subtle, #e2e8f0);
  background: var(--surface-muted, #f8fafc);
}

.forward-search {
  display: flex;
  align-items: center;
  gap: var(--space-8, 0.5rem);
  min-height: 2.25rem;
  padding: 0 var(--space-10, 0.625rem);
  border: 1px solid var(--border-subtle, #e2e8f0);
  border-radius: var(--radius-8, 0.5rem);
  background: var(--surface-panel);
  color: var(--text-tertiary);
}

.forward-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
}

.forward-list-head,
.forward-preview-head,
.forward-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-12, 0.75rem);
}

.forward-list-head {
  color: var(--text-secondary);
  font-size: 0.875rem;
}

.forward-list-head span {
  color: var(--text-tertiary);
  font-size: 0.75rem;
}

.forward-conversation-list {
  display: grid;
  align-content: start;
  gap: var(--space-6, 0.375rem);
  min-height: 0;
  overflow: auto;
}

.forward-conversation-row {
  display: grid;
  grid-template-columns: 1.25rem auto minmax(0, 1fr);
  align-items: center;
  gap: var(--space-10, 0.625rem);
  width: 100%;
  min-height: 3.25rem;
  padding: var(--space-6, 0.375rem);
  border: 0;
  border-radius: var(--radius-8, 0.5rem);
  background: transparent;
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}

.forward-conversation-row:hover,
.forward-conversation-row.selected {
  background: var(--surface-accent);
}

.forward-check {
  display: grid;
  place-items: center;
  width: 1rem;
  height: 1rem;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-panel);
}

.forward-check span {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 999px;
  background: var(--primary-600, #2563eb);
}

.forward-row-text {
  display: grid;
  gap: 0.125rem;
  min-width: 0;
}

.forward-row-text strong,
.forward-row-text small {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.forward-row-text small {
  color: var(--text-tertiary);
  font-size: 0.75rem;
}

.forward-preview {
  display: grid;
  grid-template-rows: auto 1fr auto auto auto;
  gap: var(--space-14, 0.875rem);
  min-width: 0;
  padding: var(--space-20, 1.25rem);
}

.forward-preview-head h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1rem;
}

.forward-content-preview {
  display: grid;
  place-items: center;
  min-height: 0;
  padding: var(--space-16, 1rem);
  border-bottom: 1px solid var(--border-subtle, #e2e8f0);
  overflow: auto;
}

.forward-text-preview {
  width: min(100%, 22rem);
  margin: 0;
  color: var(--text-secondary);
  font-size: 0.875rem;
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.forward-attachment-preview {
  display: grid;
  gap: var(--space-8, 0.5rem);
  width: min(100%, 22rem);
  margin-top: var(--space-12, 0.75rem);
}

.forward-attachment-preview span {
  padding: var(--space-8, 0.5rem);
  border: 1px solid var(--border-subtle, #e2e8f0);
  border-radius: var(--radius-8, 0.5rem);
  color: var(--text-secondary);
  font-size: 0.8125rem;
}

.forward-note input {
  width: 100%;
  min-height: 2.75rem;
  padding: 0 var(--space-12, 0.75rem);
  border: 0;
  border-radius: var(--radius-8, 0.5rem);
  background: var(--surface-muted, #f8fafc);
  color: var(--text-primary);
  font: inherit;
}

.forward-empty {
  margin: var(--space-12, 0.75rem) 0 0;
  color: var(--text-tertiary);
  font-size: 0.8125rem;
  text-align: center;
}

@media (max-width: 720px) {
  .forward-dialog {
    grid-template-columns: 1fr;
    height: min(42rem, calc(100vh - 2rem));
  }

  .forward-picker {
    min-height: 16rem;
    border-right: 0;
    border-bottom: 1px solid var(--border-subtle, #e2e8f0);
  }
}
</style>
