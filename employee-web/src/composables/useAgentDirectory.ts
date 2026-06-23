import { ref } from 'vue';
import { employeeApi } from '@/api';
import type { AgentResponse } from '@/api';

interface AgentConversationForm {
  agentId: string;
}

interface UseAgentDirectoryOptions {
  createConversationForm?: { value: AgentConversationForm };
  showToast: (message: string) => void;
}

export function useAgentDirectory({
  createConversationForm,
  showToast,
}: UseAgentDirectoryOptions) {
  const agents = ref<AgentResponse[]>([]);
  const agentsLoading = ref(false);
  const agentKeyword = ref('');

  async function loadAgents(keyword = agentKeyword.value) {
    if (agentsLoading.value) return;
    agentsLoading.value = true;
    try {
      agentKeyword.value = keyword;
      agents.value = await employeeApi.agents.list({ keyword: keyword.trim() || undefined });
      if (createConversationForm && !createConversationForm.value.agentId && agents.value[0]) {
        createConversationForm.value.agentId = String(agents.value[0].agentId);
      }
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'AI 助手加载失败');
    } finally {
      agentsLoading.value = false;
    }
  }

  return {
    agents,
    agentsLoading,
    agentKeyword,
    loadAgents,
  };
}
