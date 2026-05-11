<script setup lang="ts">
import {
  AtSign,
  ChevronLeft,
  Clock3,
  FolderOpen,
  FileText,
  Info,
  LayoutGrid,
  MessageCircle,
  Paperclip,
  Search,
  Send,
  Smile,
  UserRound,
  PanelLeftClose,
  PanelLeftOpen,
} from 'lucide-vue-next';
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import type { SectionAction, SectionFilter, SectionId } from '../stores/shell';
import { navItems, sectionFilters, shellUser, useShellStore } from '../stores/shell';
import { useImStore } from '../stores/im';
import { useSessionStore } from '../stores/session';
import { useWorkflowStore } from '../stores/workflow';

const shell = useShellStore();
const im = useImStore();
const session = useSessionStore();
const workflow = useWorkflowStore();
const draft = ref('');
const searchOpen = ref(true);
const resizing = ref(false);
const toastMessage = ref('');
const messageStream = ref<HTMLElement | null>(null);
let toastTimer: ReturnType<typeof window.setTimeout> | undefined;

const sectionIcons: Record<SectionId, typeof MessageCircle> = {
  messages: MessageCircle,
  todos: Clock3,
  files: FileText,
  workbench: LayoutGrid,
  profile: UserRound,
};

const activeSection = computed(() => shell.section);
const currentMeta = computed(() => shell.currentMeta);
const isMessages = computed(() => shell.section === 'messages');
const activeConversation = computed(() => shell.activeConversation);
const activeTodo = computed(() => shell.activeTodo);
const activeFile = computed(() => shell.activeFile);
const activeWorkbench = computed(() => shell.activeWorkbench);
const activeProfile = computed(() => shell.activeProfile);
const currentUserName = computed(() => session.displayName || shellUser.name);
const currentUserShortName = computed(() => session.shortName || shellUser.shortName);
const detailVisible = computed(() => shell.detailOpen);
const listWidth = computed(() => `${shell.listWidth}px`);
const mobileStage = computed(() => shell.mobileView);
const filterOptions = computed(() => {
  if (activeSection.value === 'messages') return sectionFilters;
  if (activeSection.value === 'todos') return sectionFilters.filter((item) => ['全部', '未处理'].includes(item));
  if (activeSection.value === 'workbench') return sectionFilters.filter((item) => ['全部', '未处理', 'Agent'].includes(item));
  return sectionFilters.filter((item) => item === '全部');
});
const activeFilter = computed(() => shell.filters[activeSection.value]);
const filteredConversations = computed(() => shell.filteredConversations);
const filteredTodos = computed(() => shell.filteredTodos);
const filteredFiles = computed(() => shell.filteredFiles);
const filteredWorkbench = computed(() => shell.filteredWorkbench);
const filteredProfile = computed(() => shell.filteredProfile);
const listEmptyText = computed(() => `没有找到匹配的${currentMeta.value.title}内容`);
const activeConversationTitle = computed(() => {
  const conversation = activeConversation.value;
  return conversation.memberCount ? `${conversation.title}（${conversation.memberCount}）` : conversation.title;
});
const activeConversationCurrentStep = computed(() => activeConversation.value.workflowSteps.find((step) => step.status === 'current'));

function showToast(message: string) {
  toastMessage.value = message;
  if (toastTimer) {
    window.clearTimeout(toastTimer);
  }
  toastTimer = window.setTimeout(() => {
    toastMessage.value = '';
    toastTimer = undefined;
  }, 2600);
}

function runAction(action?: SectionAction) {
  if (!action) return;
  if (action.action === 'switch-section' && action.section) {
    shell.setSection(action.section);
    shell.setMobileView('list');
    return;
  }
  if (action.action === 'switch-item' && action.item) {
    const section = action.section ?? activeSection.value;
    shell.setSection(section);
    shell.selectItem(section, action.item);
    return;
  }
  if (action.action === 'open-workbench' && action.app) {
    shell.setSection('workbench');
    shell.selectItem('workbench', action.app);
    return;
  }
  if (action.action === 'toast' && action.message) {
    showToast(action.message);
  }
}

function selectNav(section: SectionId) {
  shell.setSection(section);
  shell.setMobileView('list');
}

async function syncConversationMessages(id: string) {
  const conversationId = Number(id);
  if (!Number.isFinite(conversationId)) return;
  if (!session.user && !session.loading) {
    await session.loadCurrentUser();
  }
  await im.loadMessages(conversationId, session.user?.userId);
  const messages = im.messagesByConversation[id];
  if (messages?.length) {
    shell.mergeBackendMessages(id, messages);
    const lastSeq = im.lastMessageSeqByConversation[id];
    const lastReadSeq = im.lastReadSeqByConversation[id] ?? 0;
    if (typeof lastSeq === 'number' && lastSeq > lastReadSeq) {
      try {
        const updated = await im.updateReadPosition(conversationId, lastSeq);
        const conversation = shell.conversations.find((item) => item.id === id);
        if (conversation) {
          conversation.unread = updated.unreadCount;
          conversation.unreadLabel = updated.unreadCount > 0 ? '未读消息' : '已读';
        }
      } catch {
        // 已读回写失败不阻断消息展示
      }
    }
    await scrollMessagesToLatest('auto');
  }
}

function selectConversation(id: string) {
  shell.selectItem('messages', id);
  void syncConversationMessages(id);
}

function backToList() {
  shell.setMobileView('list');
}

function openDetail() {
  shell.setDetailOpen(true);
}

function closeDetail() {
  shell.setDetailOpen(false);
}

async function scrollMessagesToLatest(behavior: ScrollBehavior = 'smooth') {
  await nextTick();
  const stream = messageStream.value;
  if (!stream) return;
  stream.scrollTo({
    top: stream.scrollHeight,
    behavior,
  });
}

async function sendMessage() {
  const text = draft.value.trim();
  if (!text) return;
  if (im.messageSending) return;
  const conversationId = Number(shell.active.messages);
  if (!Number.isSafeInteger(conversationId)) {
    showToast('当前会话暂未接入真实发送。');
    return;
  }
  try {
    if (!session.user && !session.loading) {
      await session.loadCurrentUser();
    }
    await im.sendMessage(conversationId, text, session.user?.userId);
    const conversationKey = String(conversationId);
    const messages = im.messagesByConversation[conversationKey];
    if (messages?.length) {
      shell.mergeBackendMessages(conversationKey, messages);
    }
    draft.value = '';
    await scrollMessagesToLatest();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '消息发送失败，请稍后重试。');
  }
}

function inputValue(event: Event) {
  return (event.target as HTMLInputElement | null)?.value ?? '';
}

function selectFilter(filter: SectionFilter) {
  shell.setFilter(activeSection.value, filter);
}

function startResize(event: PointerEvent) {
  resizing.value = true;
  const startX = event.clientX;
  const startWidth = shell.listWidth;
  const onMove = (moveEvent: PointerEvent) => {
    if (!resizing.value) return;
    shell.setListWidth(startWidth + (moveEvent.clientX - startX));
  };
  const stop = () => {
    resizing.value = false;
    window.removeEventListener('pointermove', onMove);
    window.removeEventListener('pointerup', stop);
    window.removeEventListener('pointercancel', stop);
  };
  window.addEventListener('pointermove', onMove);
  window.addEventListener('pointerup', stop);
  window.addEventListener('pointercancel', stop);
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && detailVisible.value) {
    closeDetail();
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown);
  const currentUserReady = session.initialized
    ? Promise.resolve()
    : session.loadCurrentUser().catch(() => undefined);
  void workflow.loadTodos().then(() => {
    if (workflow.todos.length) {
      shell.mergeBackendTodos(workflow.todos);
    }
  });
  void currentUserReady.then(() => im.loadConversations()).then(() => {
    if (im.conversations.length) {
      shell.mergeBackendConversations(im.conversations);
      void syncConversationMessages(shell.active.messages);
    }
  });
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown);
  if (toastTimer) {
    window.clearTimeout(toastTimer);
  }
});
</script>

<template>
  <main
    class="employee-shell"
    :class="[`section-${activeSection}`, { 'detail-open': detailVisible, 'mobile-detail-stage': mobileStage === 'detail' }]"
    :style="{ '--list-width': listWidth }"
  >
    <aside class="side-rail" aria-label="员工端功能">
      <div class="brand-block">
        <div class="brand-mark">WH</div>
        <div class="brand-copy">
          <strong>Workforce Hub</strong>
          <span>员工端</span>
        </div>
      </div>

      <nav class="rail-nav" aria-label="主功能">
        <button
          v-for="item in navItems"
          :key="item.id"
          class="rail-button"
          :class="{ active: item.id === activeSection }"
          type="button"
          @click="selectNav(item.id)"
        >
          <component :is="sectionIcons[item.id]" :size="19" stroke-width="2" />
          <span>{{ item.label }}</span>
        </button>
      </nav>

      <button class="rail-user" type="button">
        {{ currentUserShortName }}
      </button>
    </aside>

    <section class="list-pane" :class="{ 'mobile-visible': mobileStage === 'list' }">
      <header class="pane-head">
        <div class="pane-title-row">
          <div class="pane-head-copy">
            <h1 class="pane-title">{{ currentMeta.title }}</h1>
          </div>
          <div class="pane-actions">
            <button class="icon-button" type="button" :aria-label="searchOpen ? '收起搜索' : '展开搜索'" @click="searchOpen = !searchOpen">
              <Search :size="16" />
            </button>
          </div>
        </div>

        <div v-if="searchOpen" class="search-wrap">
          <input
            class="search-input"
            :value="shell.search[activeSection]"
            type="search"
            :placeholder="currentMeta.search"
            @input="shell.setSearch(activeSection, inputValue($event))"
          />
        </div>

        <div class="pane-filter-row" :aria-label="`${currentMeta.title} 筛选`">
          <button
            v-for="filter in filterOptions"
            :key="filter"
            class="filter-chip"
            type="button"
            :class="{ active: filter === activeFilter }"
            @click="selectFilter(filter)"
          >
            {{ filter }}
          </button>
        </div>
      </header>

      <div v-if="activeSection === 'messages'" class="list-items">
        <p v-if="!filteredConversations.length" class="empty-state">{{ listEmptyText }}</p>
        <button
          v-for="item in filteredConversations"
          :key="item.id"
          class="list-item message-list-item"
          :class="[item.id === activeConversation.id ? 'is-active' : '', `tone-${item.tone}`]"
          type="button"
          @click="selectConversation(item.id)"
        >
          <span class="avatar-wrap">
            <span class="avatar" :class="item.tone">{{ item.badge }}</span>
            <span v-if="item.unread" class="unread-badge">{{ item.unread }}</span>
          </span>
          <span class="item-main">
            <span class="item-row">
              <strong class="item-title">
                {{ item.title }}<span v-if="item.memberCount" class="item-title-count">（{{ item.memberCount }}）</span>
              </strong>
              <span class="item-meta-right">
                <span v-if="item.unread" class="item-unread">{{ item.unread }}</span>
                <span class="item-time">{{ item.meta }}</span>
              </span>
            </span>
            <span class="item-note">{{ item.subtitle }}</span>
          </span>
        </button>
      </div>

      <div v-else-if="activeSection === 'todos'" class="list-items">
        <p v-if="!filteredTodos.length" class="empty-state">{{ listEmptyText }}</p>
        <button
          v-for="item in filteredTodos"
          :key="item.id"
          class="list-item"
          :class="{ 'is-active': item.id === activeTodo.id }"
          type="button"
          @click="shell.selectItem('todos', item.id)"
        >
          <span class="avatar-wrap">
            <span class="avatar blue">{{ item.priority }}</span>
          </span>
          <span class="item-main">
            <span class="item-row">
              <strong class="item-title">{{ item.title }}</strong>
              <span class="item-time">{{ item.time }}</span>
            </span>
            <span class="item-note">{{ item.subtitle }}</span>
            <span class="pill">{{ item.status }}</span>
          </span>
        </button>
      </div>

      <div v-else-if="activeSection === 'files'" class="list-items">
        <p v-if="!filteredFiles.length" class="empty-state">{{ listEmptyText }}</p>
        <button
          v-for="item in filteredFiles"
          :key="item.id"
          class="list-item"
          :class="{ 'is-active': item.id === activeFile.id }"
          type="button"
          @click="shell.selectItem('files', item.id)"
        >
          <span class="avatar-wrap">
            <span class="avatar slate">{{ item.label }}</span>
          </span>
          <span class="item-main">
            <span class="item-row">
              <strong class="item-title">{{ item.title }}</strong>
              <span class="item-time">{{ item.subtitle }}</span>
            </span>
            <span class="item-note">{{ item.note }}</span>
            <span class="pill slate">{{ item.title }}</span>
          </span>
        </button>
      </div>

      <div v-else-if="activeSection === 'workbench'" class="list-items">
        <p v-if="!filteredWorkbench.length" class="empty-state">{{ listEmptyText }}</p>
        <button
          v-for="item in filteredWorkbench"
          :key="item.id"
          class="list-item"
          :class="{ 'is-active': item.id === activeWorkbench.id, disabled: item.disabled }"
          type="button"
          @click="!item.disabled && shell.selectItem('workbench', item.id)"
        >
          <span class="avatar-wrap">
            <span class="avatar" :class="item.tone">{{ item.label.slice(0, 1) }}</span>
          </span>
          <span class="item-main">
            <span class="item-row">
              <strong class="item-title">{{ item.title }}</strong>
              <span class="item-time">{{ item.status }}</span>
            </span>
            <span class="item-note">{{ item.subtitle }}</span>
            <span class="pill" :class="item.tone">{{ item.note }}</span>
          </span>
        </button>
      </div>

      <div v-else class="list-items">
        <p v-if="!filteredProfile.length" class="empty-state">{{ listEmptyText }}</p>
        <button
          v-for="item in filteredProfile"
          :key="item.id"
          class="list-item"
          :class="{ 'is-active': item.id === activeProfile.id }"
          type="button"
          @click="shell.selectItem('profile', item.id)"
        >
          <span class="avatar-wrap">
            <span class="avatar green">{{ item.title.slice(0, 1) }}</span>
          </span>
          <span class="item-main">
            <span class="item-row">
              <strong class="item-title">{{ item.title }}</strong>
              <span class="item-time">{{ item.subtitle }}</span>
            </span>
            <span class="item-note">{{ item.note }}</span>
            <span class="pill green">{{ item.tone }}</span>
          </span>
        </button>
      </div>
    </section>

    <div class="resize-handle" role="separator" aria-orientation="vertical" aria-label="调整列表宽度" @pointerdown.prevent="startResize" />

    <section class="main-pane" :class="{ 'mobile-visible': mobileStage === 'detail' }">
      <div v-if="activeSection === 'messages'" class="workspace">
        <header class="workspace-head">
          <div class="workspace-title-group">
            <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
              <ChevronLeft :size="22" />
            </button>
            <div class="workspace-head-text">
              <h2 class="workspace-title">{{ activeConversationTitle }}</h2>
              <p class="workspace-subtitle">{{ activeConversationCurrentStep?.label ?? activeConversation.flow }}</p>
            </div>
          </div>
          <div class="head-actions">
            <button class="icon-button" type="button" aria-label="查看待办" @click="runAction({ action: 'switch-section', section: 'todos', label: '查看待办' })">
              <Clock3 :size="17" />
            </button>
            <button class="icon-button" type="button" aria-label="会话详情" @click="openDetail">
              <Info :size="17" />
            </button>
          </div>
        </header>

        <section ref="messageStream" class="message-stream">
          <template
            v-for="message in activeConversation.messages"
            :key="message.id"
          >
            <article
              v-if="message.side === 'system'"
              class="system-line"
            >
              <strong>{{ message.author }} · {{ message.role }}</strong>
              <span>{{ message.body }}</span>
            </article>
            <article
              v-else-if="message.type === 'process-card' && message.processCard"
              class="process-card-message"
            >
              <div class="bubble-avatar agent-avatar">{{ message.author.slice(0, 1) }}</div>
              <div class="agent-process-wrap">
                <div class="bubble-meta">
                  <strong>{{ message.author }}</strong>
                  <span>{{ message.role }}</span>
                  <time v-if="message.time">{{ message.time }}</time>
                </div>
                <button class="agent-process-card" type="button" @click="openDetail">
                  <span class="process-card-head">
                    <strong>{{ message.processCard.title }}</strong>
                    <em :class="`tone-${message.processCard.statusTone}`">{{ message.processCard.status }}</em>
                  </span>
                  <span class="process-card-copy">{{ message.processCard.body }}</span>

                  <span v-if="message.processCard.fields?.length" class="process-fields">
                    <span v-for="field in message.processCard.fields" :key="field[0]">
                      <small>{{ field[0] }}</small>
                      <b>{{ field[1] }}</b>
                    </span>
                  </span>

                  <span v-if="message.processCard.checks?.length" class="process-checks">
                    <span
                      v-for="check in message.processCard.checks"
                      :key="check.label"
                      :class="{ passed: check.passed }"
                    >
                      <i aria-hidden="true"></i>
                      <span>
                        <strong>{{ check.label }}</strong>
                        <small>{{ check.result }}</small>
                      </span>
                    </span>
                  </span>

                  <span v-if="message.processCard.file" class="process-file">
                    <span class="attachment-icon">{{ message.processCard.file.type }}</span>
                    <span>
                      <strong>{{ message.processCard.file.name }}</strong>
                      <small>{{ message.processCard.file.size }} · {{ message.processCard.file.status }}</small>
                    </span>
                  </span>

                  <span class="process-card-foot">
                    <span>{{ message.body }}</span>
                    <b>{{ message.processCard.actionLabel }}</b>
                  </span>
                </button>
              </div>
            </article>
            <article
              v-else
              class="message-bubble"
              :class="message.side"
            >
              <div class="bubble-avatar">{{ message.author.slice(0, 1) }}</div>
              <div class="bubble-content">
                <div class="bubble-meta">
                  <strong>{{ message.author }}</strong>
                  <span>{{ message.role }}</span>
                </div>
                <p>{{ message.body }}</p>
              </div>
            </article>
          </template>
        </section>

        <footer class="composer">
          <div class="composer-tools" aria-label="消息工具">
            <button class="tool-button" type="button" @click="runAction({ action: 'switch-section', section: 'files', label: '上传附件' })">
              <Paperclip :size="16" />
              <span>上传</span>
            </button>
            <button class="tool-button" type="button" @click="openDetail">
              <FolderOpen :size="16" />
              <span>材料</span>
            </button>
            <button class="tool-button" type="button" @click="showToast('已准备 @Agent 输入。')">
              <AtSign :size="16" />
              <span>Agent</span>
            </button>
            <button class="tool-button" type="button" @click="showToast('表情面板后续接入。')">
              <Smile :size="16" />
              <span>表情</span>
            </button>
          </div>
          <div class="composer-input">
            <input
              v-model="draft"
              type="text"
              placeholder="输入消息，支持 @Agent"
              :disabled="im.messageSending"
              @keydown.enter.prevent="sendMessage"
            />
          </div>
          <button
            class="send-button"
            type="button"
            :disabled="im.messageSending"
            :aria-label="im.messageSending ? '发送中' : '发送'"
            @click="sendMessage"
          >
            <Send :size="18" />
          </button>
        </footer>
      </div>

      <div v-else class="workspace">
        <header class="workspace-head">
          <div class="workspace-title-group">
            <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
              <ChevronLeft :size="22" />
            </button>
            <div class="workspace-head-text">
              <p class="workspace-kicker">当前页面</p>
              <h2 class="workspace-title">{{ currentMeta.title }}</h2>
              <p class="workspace-subtitle">{{ currentMeta.subtitle }}</p>
            </div>
          </div>
          <div class="head-actions">
            <button class="text-button" type="button" @click="runAction(currentMeta.secondaryAction)">
              {{ currentMeta.secondaryAction?.label }}
            </button>
            <button class="text-button" type="button" @click="runAction(currentMeta.primaryAction)">
              {{ currentMeta.primaryAction?.label }}
            </button>
          </div>
        </header>

        <section class="content-grid">
          <article class="summary-panel">
            <p class="workspace-kicker">概览</p>
            <h3>{{ currentMeta.title }}入口已连接</h3>
            <p>{{ currentMeta.subtitle }}</p>
            <div class="summary-actions">
              <button class="primary-button" type="button" @click="runAction(currentMeta.primaryAction)">
                {{ currentMeta.primaryAction?.label }}
              </button>
              <button class="text-button" type="button" @click="runAction(currentMeta.secondaryAction)">
                {{ currentMeta.secondaryAction?.label }}
              </button>
            </div>
          </article>

          <article
            v-if="activeSection === 'todos'"
            class="detail-panel"
          >
            <h3>{{ activeTodo.title }}</h3>
            <p>{{ activeTodo.summary }}</p>
            <dl class="field-grid">
              <div v-for="field in activeTodo.fields" :key="field[0]">
                <dt>{{ field[0] }}</dt>
                <dd>{{ field[1] }}</dd>
              </div>
            </dl>
            <div class="timeline">
              <span v-for="step in activeTodo.timeline" :key="step">{{ step }}</span>
            </div>
          </article>

          <article
            v-else-if="activeSection === 'files'"
            class="detail-panel"
          >
            <h3>{{ activeFile.title }}</h3>
            <p>{{ activeFile.note }}</p>
            <dl class="field-grid">
              <div v-for="item in activeFile.summary" :key="item[0]">
                <dt>{{ item[1] }}</dt>
                <dd>{{ item[0] }}</dd>
              </div>
            </dl>
            <div class="timeline">
              <span v-for="row in activeFile.files" :key="row[0]">{{ row[0] }} · {{ row[4] }}</span>
            </div>
          </article>

          <article
            v-else-if="activeSection === 'workbench'"
            class="detail-panel"
          >
            <h3>{{ activeWorkbench.title }}</h3>
            <p>{{ activeWorkbench.note }}</p>
            <dl class="field-grid">
              <div v-for="field in activeWorkbench.fields" :key="field[0]">
                <dt>{{ field[0] }}</dt>
                <dd>{{ field[1] }}</dd>
              </div>
            </dl>
            <div class="timeline">
              <span v-for="step in activeWorkbench.steps" :key="step">{{ step }}</span>
            </div>
          </article>

          <article
            v-else-if="activeSection === 'profile'"
            class="detail-panel"
          >
            <h3>{{ activeProfile.title }}</h3>
            <p>{{ activeProfile.note }}</p>
            <dl class="field-grid">
              <div v-for="field in activeProfile.fields" :key="field[0]">
                <dt>{{ field[0] }}</dt>
                <dd>{{ field[1] }}</dd>
              </div>
            </dl>
            <div class="timeline">
              <span v-for="stat in activeProfile.stats" :key="stat[0]">{{ stat[0] }} · {{ stat[1] }}</span>
            </div>
          </article>

          <article
            v-else
            class="detail-panel"
          >
            <h3>暂无可展示详情</h3>
            <p>请选择左侧内容继续查看。</p>
          </article>
        </section>
      </div>
    </section>

    <aside class="context-panel" :class="{ open: detailVisible }">
      <header class="context-head">
        <div>
          <p class="pane-kicker">会话详情</p>
          <h2>{{ isMessages ? activeConversation.title : currentMeta.title }}</h2>
        </div>
        <button class="icon-button" type="button" :aria-label="detailVisible ? '收起详情' : '展开详情'" @click="shell.toggleDetail()">
          <component :is="detailVisible ? PanelLeftClose : PanelLeftOpen" :size="18" />
        </button>
      </header>

      <template v-if="isMessages">
        <section class="context-block audit-block">
          <h3>审核链路</h3>
          <div class="audit-chain">
            <span
              v-for="record in activeConversation.panel.auditTrail"
              :key="`${record.label}-${record.actor}`"
              class="audit-step"
              :class="record.statusTone"
            >
              <i aria-hidden="true"></i>
              <span class="audit-step-main">
                <strong>{{ record.label }}</strong>
                <em>{{ record.actor }} · {{ record.time }}</em>
                <small>{{ record.detail }}</small>
              </span>
              <b>{{ record.status }}</b>
            </span>
          </div>
        </section>

        <section class="context-block evidence-block">
          <h3>Agent 审核依据</h3>
          <div v-if="activeConversation.panel.agentEvidence?.length" class="evidence-list">
            <span v-for="item in activeConversation.panel.agentEvidence" :key="item.label">
              <small>{{ item.label }}</small>
              <strong>{{ item.value }}</strong>
            </span>
          </div>
          <p v-else class="empty-inline">暂无审核依据</p>
        </section>

        <section class="context-block materials-block">
          <h3>相关材料</h3>
          <div v-if="activeConversation.panel.attachments.length" class="attachment-list">
            <button
              v-for="file in activeConversation.panel.attachments"
              :key="file.id"
              class="attachment-item"
              type="button"
              @click="runAction({ action: 'switch-section', section: 'files', label: '查看材料' })"
            >
              <span class="attachment-icon">{{ file.type }}</span>
              <span>
                <strong>{{ file.name }}</strong>
                <em>{{ file.size }} · {{ file.status }}</em>
              </span>
            </button>
          </div>
          <p v-else class="empty-inline">无文件</p>
        </section>
      </template>

      <template v-else>
        <section class="context-block">
          <h3>入口说明</h3>
          <p class="context-copy">{{ currentMeta.subtitle }}</p>
        </section>

        <section class="context-block">
          <h3>可用操作</h3>
          <div class="task-list">
            <span v-if="currentMeta.primaryAction">{{ currentMeta.primaryAction.label }}</span>
            <span v-if="currentMeta.secondaryAction">{{ currentMeta.secondaryAction.label }}</span>
          </div>
        </section>
      </template>
    </aside>

    <nav class="mobile-rail" aria-label="底部导航">
      <button
        v-for="item in navItems"
        :key="item.id"
        type="button"
        class="mobile-rail-button"
        :class="{ active: item.id === activeSection }"
        @click="selectNav(item.id)"
      >
        <component :is="sectionIcons[item.id]" :size="18" />
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <div v-if="activeSection === 'messages' && mobileStage === 'detail' && detailVisible" class="mobile-detail-shell">
      <div class="mobile-detail-card">
        <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
          <ChevronLeft :size="22" />
        </button>
        <div class="mobile-detail-body">
          <p class="pane-kicker">会话详情</p>
          <h3>{{ activeConversation.title }}</h3>
          <section class="mobile-detail-section">
            <h4>流程进度</h4>
            <div class="context-steps">
              <span
                v-for="step in activeConversation.workflowSteps"
                :key="step.label"
                :class="step.status"
              >
                <strong>{{ step.label }}</strong>
                {{ step.detail }}
              </span>
            </div>
          </section>
          <section class="mobile-detail-section">
            <h4>相关材料</h4>
            <div v-if="activeConversation.panel.attachments.length" class="attachment-list">
              <button
                v-for="file in activeConversation.panel.attachments"
                :key="file.id"
                class="attachment-item"
                type="button"
                @click="runAction({ action: 'switch-section', section: 'files', label: '查看材料' })"
              >
                <span class="attachment-icon">{{ file.type }}</span>
                <span>
                  <strong>{{ file.name }}</strong>
                  <em>{{ file.size }} · {{ file.status }}</em>
                </span>
              </button>
            </div>
            <p v-else class="empty-inline">无文件</p>
          </section>
          <button class="text-button" type="button" @click="closeDetail">关闭</button>
        </div>
      </div>
    </div>

    <div v-if="toastMessage" class="toast-message" role="status" aria-live="polite">
      {{ toastMessage }}
    </div>
  </main>
</template>
