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
  Send,
  Sparkles,
  Star,
  Trash2,
  UserMinus,
  UsersRound,
  VolumeX,
  X,
} from 'lucide-vue-next';
import { useMessagesSectionContext } from '../employee-shell-context';
import { useGroupFiles } from '@/composables/useGroupFiles';
import { useMessagesState } from '@/composables/useMessagesState';
import { useMessagesWorkflowEntry } from '@/composables/useMessagesWorkflowEntry';
import { formatDateTime, humanizeText, statusText } from '@/utils/employee-format';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { fileIconOf, fileToneOf } from '@/utils/file-record';
import AppButton from '../../components/ui/AppButton.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppDrawer from '../../components/ui/AppDrawer.vue';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppInput from '../../components/ui/AppInput.vue';
import AppSkeleton from '../../components/ui/AppSkeleton.vue';
import ConversationProcessCard from '../../components/shell/ConversationProcessCard.vue';
import KnowledgeAnswerCard from '../../components/shell/cards/KnowledgeAnswerCard.vue';
import MessageBubble from '../../components/shell/MessageBubble.vue';
import { DEFAULT_CONVERSATION_AVATAR_URL } from '@/stores/im';
import type { ConversationMessage } from '@/stores/shell';

const {
  shell,
  im,
  session,
  wsConnected,
  showToast,
  runAction,
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
  submitMessageForward,
  openImAttachment,
  insertAgentMention,
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

const {
  workflowEntryOpen,
  workflowEntryActions,
  openWorkflowEntry,
  closeWorkflowEntry,
  triggerWorkflowEntry,
} = useMessagesWorkflowEntry({
  runAction,
});

async function openGroupManagement() {
  await openGroupPanel();
  if (groupPanelOpen.value) void loadGroupFiles();
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
      <AppButton variant="secondary" size="sm" @click="openWorkflowEntry">
        <Clock3 :size="16" />
        待办
      </AppButton>
      <AppButton variant="secondary" size="sm" @click="openDetail">
        <Info :size="16" />
        详情
      </AppButton>
      <AppButton v-if="activeConversation.kind === 'team'" variant="secondary" size="sm" @click="openGroupManagement">
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
          @action="openMessageAction"
          @attachment="openImAttachment"
        />
      </article>
    </template>
  </section>

  <section v-else class="content-stack">
    <AppEmptyState title="暂无会话" description="暂无消息">
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
      <div class="composer-input">
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

      <label v-if="createConversationMode === 'private'" class="security-field">
        <span>成员ID</span>
        <input v-model="createConversationForm.targetUserId" class="ui-inline-input" inputmode="numeric" />
      </label>

      <template v-else-if="createConversationMode === 'group'">
        <label class="security-field">
          <span>群名称</span>
          <input v-model="createConversationForm.groupTitle" class="ui-inline-input" />
        </label>
        <label class="security-field">
          <span>成员ID</span>
          <textarea v-model="createConversationForm.memberUserIds" class="ui-textarea compact-textarea" rows="3" />
        </label>
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
            <span>{{ agent.agentCode }} · {{ agent.agentType }}</span>
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

  <AppDialog v-if="workflowEntryOpen">
    <section class="workflow-entry-dialog">
      <header class="dialog-head dialog-head-row">
        <div>
          <h2>流程入口</h2>
        </div>
        <button class="field-icon-button" type="button" aria-label="关闭" @click="closeWorkflowEntry">
          <X :size="15" />
        </button>
      </header>
      <div class="workflow-entry-grid">
        <button
          v-for="item in workflowEntryActions"
          :key="item.key"
          class="workflow-entry-button"
          type="button"
          @click="triggerWorkflowEntry(item.key)"
        >
          {{ item.label }}
        </button>
      </div>
    </section>
  </AppDialog>

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
            <label class="security-field">
              <span>输入成员ID（多个用逗号分隔）</span>
              <AppInput v-model="groupProfileForm.memberUserIds" placeholder="成员ID" />
            </label>
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
            <button class="group-member-card group-add-card" type="button" @click="toggleGroupMemberAdding">
              <span class="group-add-icon"><Plus :size="18" /></span>
              <span class="group-member-name">添加</span>
            </button>
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
                  {{ fileTypeLabelFromName(file.displayName, file.mimeType) }} · {{ fileSizeLabel(file.fileSize) }} · {{ file.uploaderName || '成员' }} · {{ formatDateTime(file.createdAt) }} · {{ statusText(file.scanStatus) }}
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
    <div v-if="messageForwardOpen" class="message-forward-panel">
      <button
        v-for="conversation in shell.conversations.filter((item) => item.id !== activeConversationId())"
        :key="conversation.id"
        type="button"
        :disabled="im.actionLoading"
        @click="submitMessageForward(conversation.id)"
      >
        <Forward :size="14" />
        <span>{{ conversation.title }}</span>
      </button>
    </div>
    <p v-if="messageActionError" class="message-context-error">{{ messageActionError }}</p>
  </div>
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

.message-forward-panel {
  display: grid;
  max-height: 10rem;
  margin-top: var(--space-6, 0.375rem);
  padding-top: var(--space-6, 0.375rem);
  border-top: 1px solid var(--border-subtle, #e2e8f0);
  overflow: auto;
}
</style>
