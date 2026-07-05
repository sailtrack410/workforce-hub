import { ref } from 'vue';
import { employeeApi } from '@/api';
import type { AgentResponse, AgentTaskResponse } from '@/api';
import type { useSessionStore } from '@/stores/session';
import { useAgentDirectory } from './useAgentDirectory';

interface UseAgentTasksOptions {
  session: ReturnType<typeof useSessionStore>;
  showToast: (message: string) => void;
}

// Agent 任务 composable：创建任务、确认/拒绝、取消
export function useAgentTasks({ session, showToast }: UseAgentTasksOptions) {
  const agentTaskForm = ref({
    agentId: '',
    inputText: '',
    taskId: '',
    comment: '',
    cancelReason: '',
  });
  const agentTaskSubmitting = ref(false);
  const agentTaskResult = ref<AgentTaskResponse | null>(null);
  const agentTaskError = ref('');
  const {
    agents,
    agentsLoading,
    loadAgents: loadAgentDirectory,
  } = useAgentDirectory({ showToast });

  async function loadAgents() {
    await loadAgentDirectory();
    if (!agentTaskForm.value.agentId && agents.value[0]) {
      agentTaskForm.value.agentId = String(agents.value[0].agentId);
    }
  }

  async function submitAgentTask() {
    const agentId = agentTaskForm.value.agentId.trim();
    const inputText = agentTaskForm.value.inputText.trim();
    if (!agentId || agentTaskSubmitting.value) return;
    agentTaskSubmitting.value = true;
    agentTaskError.value = '';
    agentTaskResult.value = null;
    try {
      agentTaskResult.value = await employeeApi.agents.createTask(agentId, {
        triggerType: 'MANUAL',
        contextType: 'USER_QUERY',
        contextId: session.user?.userId,
        inputPayloadJson: JSON.stringify({ query: inputText }),
      });
      if (agentTaskResult.value.taskId) {
        agentTaskForm.value.taskId = String(agentTaskResult.value.taskId);
      }
      showToast('AI 任务已创建');
    } catch (error) {
      agentTaskError.value = error instanceof Error ? error.message : '任务创建失败';
    } finally {
      agentTaskSubmitting.value = false;
    }
  }

  async function confirmAgentTask(approved: boolean) {
    const taskId = agentTaskForm.value.taskId.trim();
    if (!taskId || agentTaskSubmitting.value) return;
    agentTaskSubmitting.value = true;
    agentTaskError.value = '';
    try {
      agentTaskResult.value = await employeeApi.agentTasks.confirm(taskId, {
        approved,
        comment: agentTaskForm.value.comment.trim() || undefined,
      });
      showToast(approved ? 'AI 任务已确认' : 'AI 任务已拒绝');
    } catch (error) {
      agentTaskError.value = error instanceof Error ? error.message : '操作失败';
    } finally {
      agentTaskSubmitting.value = false;
    }
  }

  async function cancelAgentTask() {
    const taskId = agentTaskForm.value.taskId.trim();
    const reason = agentTaskForm.value.cancelReason.trim();
    if (!taskId || !reason || agentTaskSubmitting.value) return;
    agentTaskSubmitting.value = true;
    agentTaskError.value = '';
    try {
      agentTaskResult.value = await employeeApi.agentTasks.cancel(taskId, { reason });
      showToast('AI 任务已取消');
    } catch (error) {
      agentTaskError.value = error instanceof Error ? error.message : '取消失败';
    } finally {
      agentTaskSubmitting.value = false;
    }
  }

  return {
    agentTaskForm,
    agentTaskSubmitting,
    agentTaskResult,
    agentTaskError,
    agents,
    agentsLoading,
    loadAgents,
    submitAgentTask,
    confirmAgentTask,
    cancelAgentTask,
  };
}
