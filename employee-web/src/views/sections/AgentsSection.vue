<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { MessageCircle, ChevronLeft, X } from 'lucide-vue-next';
import type { AgentResponse } from '@/api';
import { employeeApi } from '@/api';
import { useAgentsSectionContext } from '../employee-shell-context';
import { agentTypeLabel, humanizeText, identityModeLabel, statusText } from '@/utils/employee-format';
import AppAvatar from '../../components/ui/AppAvatar.vue';
import AppButton from '../../components/ui/AppButton.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppTag from '../../components/ui/AppTag.vue';

const {
  shell,
  showToast,
  navigateMessagesConversation,
  startAgentConversationFromSearch,
} = useAgentsSectionContext();

const agents = ref<AgentResponse[]>([]);
const agentsLoading = ref(false);
const agentsError = ref('');
const agentAvatarUrls = ref<Record<string, string>>({});
const profileAgent = ref<AgentResponse | null>(null);

const keyword = computed(() => shell.search.agents.trim());
const filteredAgents = computed(() => {
  const kw = keyword.value.toLowerCase();
  if (!kw) return agents.value;
  return agents.value.filter((item) => (
    `${item.agentName} ${item.agentCode} ${item.agentType}`.toLowerCase().includes(kw)
  ));
});
// 已添加的 AI 助手 agentCode 集合（基于现有会话）
const addedAgentCodes = computed(() => new Set(
  shell.conversations
    .filter((item) => item.kind === 'agent')
    .map((item) => item.tag)
    .filter(Boolean),
));
// agentCode → conversationId 映射，用于点击已添加卡片跳转
const addedAgentConversationIds = computed(() => {
  const map = new Map<string, string>();
  shell.conversations.forEach((item) => {
    if (item.kind === 'agent' && item.tag) {
      map.set(item.tag, item.id);
    }
  });
  return map;
});
const addedAgents = computed(() => filteredAgents.value.filter((a) => addedAgentCodes.value.has(a.agentCode)));
const availableAgents = computed(() => filteredAgents.value.filter((a) => !addedAgentCodes.value.has(a.agentCode)));

async function loadAgents() {
  if (agentsLoading.value) return;
  agentsLoading.value = true;
  agentsError.value = '';
  try {
    agents.value = await employeeApi.agents.list({});
  } catch (error) {
    agentsError.value = error instanceof Error ? error.message : 'AI 助手加载失败';
    showToast(agentsError.value);
  } finally {
    agentsLoading.value = false;
  }
}

async function refreshAgentAvatarUrls(rows: AgentResponse[]) {
  const entries = await Promise.all(rows.map(async (agent) => {
    if (!agent.avatarAttachmentRefId) return [String(agent.agentId), ''] as const;
    try {
      const ticket = await employeeApi.files.createDownloadTicket(agent.avatarAttachmentRefId, { downloadType: 'INLINE' });
      const url = employeeApi.files.downloadUrl(ticket.ticketNo);
      return [String(agent.agentId), url] as const;
    } catch {
      return [String(agent.agentId), ''] as const;
    }
  }));
  agentAvatarUrls.value = Object.fromEntries(entries);
}

function openAgentProfile(agent: AgentResponse) {
  profileAgent.value = agent;
}

function closeAgentProfile() {
  profileAgent.value = null;
}

async function startConversation(agent: AgentResponse) {
  profileAgent.value = null;
  await startAgentConversationFromSearch(agent);
}

function openExistingConversation(agent: AgentResponse) {
  const conversationId = addedAgentConversationIds.value.get(agent.agentCode);
  profileAgent.value = null;
  if (conversationId) {
    navigateMessagesConversation(conversationId);
  }
}

function backToList() {
  shell.setMobileView('list');
}

function agentVisibleType(agent: AgentResponse) {
  return agent.agentTypeName || agentTypeLabel(agent.agentType);
}

onMounted(() => {
  void loadAgents();
});

watch(agents, (rows) => {
  void refreshAgentAvatarUrls(rows);
}, { immediate: true });
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">AI 助手</h2>
      </div>
    </div>
  </header>

  <section class="content-stack agents-directory">
    <p v-if="agentsLoading" class="form-hint">加载中…</p>
    <p v-else-if="agentsError" class="dialog-error">{{ agentsError }}</p>
    <AppEmptyState
      v-else-if="!filteredAgents.length"
      :title="keyword ? '未匹配到 AI 助手' : '暂无可用 AI 助手'"
      icon="workbench"
    />

    <article v-if="addedAgents.length" class="agents-group">
      <header class="agents-group-head">
        <h3>已添加</h3>
        <p>{{ addedAgents.length }} 个</p>
      </header>
      <div class="agents-card-grid">
        <button
          v-for="agent in addedAgents"
          :key="`added-${agent.agentId}`"
          type="button"
          class="agent-card"
          @click="openAgentProfile(agent)"
        >
          <AppAvatar
            :label="humanizeText(agent.agentName)"
            :image="agentAvatarUrls[String(agent.agentId)]"
            kind="agent"
            tone="success"
            size="lg"
            status="online"
          />
          <div class="agent-card-copy">
            <strong>{{ humanizeText(agent.agentName) }}</strong>
            <small v-if="agentVisibleType(agent) !== humanizeText(agent.agentName)">{{ agentVisibleType(agent) }}</small>
          </div>
        </button>
      </div>
    </article>

    <article v-if="availableAgents.length" class="agents-group">
      <header class="agents-group-head">
        <h3>可添加</h3>
        <p>{{ availableAgents.length }} 个</p>
      </header>
      <div class="agents-card-grid">
        <button
          v-for="agent in availableAgents"
          :key="`avail-${agent.agentId}`"
          type="button"
          class="agent-card"
          @click="openAgentProfile(agent)"
        >
          <AppAvatar
            :label="humanizeText(agent.agentName)"
            :image="agentAvatarUrls[String(agent.agentId)]"
            kind="agent"
            tone="neutral"
            size="lg"
          />
          <div class="agent-card-copy">
            <strong>{{ humanizeText(agent.agentName) }}</strong>
            <small v-if="agentVisibleType(agent) !== humanizeText(agent.agentName)">{{ agentVisibleType(agent) }}</small>
          </div>
        </button>
      </div>
    </article>
  </section>

  <Teleport to="body">
    <div v-if="profileAgent" class="agent-profile-overlay" @click="closeAgentProfile" />
    <section v-if="profileAgent" class="agent-profile-dialog">
      <button class="agent-profile-close" type="button" aria-label="关闭" @click="closeAgentProfile">
        <X :size="17" />
      </button>
      <header class="agent-profile-head">
        <AppAvatar
          :label="humanizeText(profileAgent.agentName)"
          :image="agentAvatarUrls[String(profileAgent.agentId)]"
          kind="agent"
          tone="success"
          size="xl"
          status="online"
        />
        <h3>{{ humanizeText(profileAgent.agentName) }}</h3>
        <div class="agent-profile-badges">
          <AppTag tone="neutral">{{ agentVisibleType(profileAgent) }}</AppTag>
          <AppTag v-if="profileAgent.publishedVersionLabel" tone="primary">
            v{{ profileAgent.publishedVersionLabel }}
          </AppTag>
        </div>
      </header>
      <dl class="agent-profile-detail">
        <div>
          <dt>身份模式</dt>
          <dd>{{ profileAgent.identityModeName || identityModeLabel(profileAgent.identityMode) }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ profileAgent.statusName || statusText(profileAgent.status) }}</dd>
        </div>
      </dl>
      <div class="agent-profile-actions">
        <AppButton
          v-if="addedAgentCodes.has(profileAgent.agentCode)"
          variant="primary"
          size="sm"
          @click="openExistingConversation(profileAgent)"
        >
          <MessageCircle :size="15" />
          继续对话
        </AppButton>
        <AppButton
          v-else
          variant="primary"
          size="sm"
          @click="startConversation(profileAgent)"
        >
          <MessageCircle :size="15" />
          开始对话
        </AppButton>
        <AppButton variant="secondary" size="sm" @click="closeAgentProfile">
          关闭
        </AppButton>
      </div>
    </section>
  </Teleport>
</div>
</template>
