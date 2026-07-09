import { defineStore } from 'pinia';
import { employeeApi } from '../api';
import type {
  WorkflowRequestStatusResponse,
  WorkflowTaskApproveRequest,
  WorkflowTaskRejectRequest,
  WorkflowTodoResponse,
} from '../api';
import type { TodoRecord } from './shell';

export interface EmployeeWorkflowApi {
  todos(): Promise<WorkflowTodoResponse[]>;
  approveTask(taskId: string, request: WorkflowTaskApproveRequest): Promise<WorkflowRequestStatusResponse>;
  rejectTask(taskId: string, request: WorkflowTaskRejectRequest): Promise<WorkflowRequestStatusResponse>;
}

// 审批待办 Store：加载待办列表、审批通过/驳回后自动刷新
export const useWorkflowStore = defineStore('employee-workflow', {
  state: () => ({
    todos: [] as TodoRecord[],
    loading: false,
    error: '',
    loadedAt: null as string | null,
    actionLoading: false,
  }),
  actions: {
    async loadTodos(workflowApi: EmployeeWorkflowApi = employeeApi.workflow) {
      this.loading = true;
      this.error = '';
      try {
        const todos = await workflowApi.todos();
        this.todos = todos.map(mapWorkflowTodoToRecord);
        this.loadedAt = new Date().toISOString();
      } catch (error) {
        this.error = error instanceof Error ? error.message : '待办加载失败';
      } finally {
        this.loading = false;
      }
    },
    async approveTask(taskId: string, comment = '', workflowApi: EmployeeWorkflowApi = employeeApi.workflow) {
      this.actionLoading = true;
      this.error = '';
      try {
        const result = await workflowApi.approveTask(taskId, { comment });
        await this.loadTodos(workflowApi);
        return result;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '审批失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
    async rejectTask(taskId: string, comment: string, workflowApi: EmployeeWorkflowApi = employeeApi.workflow) {
      this.actionLoading = true;
      this.error = '';
      try {
        const result = await workflowApi.rejectTask(taskId, { comment });
        await this.loadTodos(workflowApi);
        return result;
      } catch (error) {
        this.error = error instanceof Error ? error.message : '驳回失败';
        throw error;
      } finally {
        this.actionLoading = false;
      }
    },
  },
});

export function mapWorkflowTodoToRecord(todo: WorkflowTodoResponse): TodoRecord {
  const statusText = todo.statusName || statusLabel(todo.status);
  const priorityText = todo.priorityName || normalizePriority(todo.priority);
  return {
    id: `workflow-todo-${todo.todoId}`,
    title: todo.title,
    subtitle: statusText,
    note: todo.summary,
    time: formatTodoTime(todo.createdAt),
    status: todo.status,
    priority: priorityText,
    requestNo: '',
    taskId: todo.taskId,
    currentStepName: statusText,
    summary: todo.summary,
    fields: [
      ['任务状态', statusText],
      ['优先级', priorityText],
      ['当前状态', statusText],
    ],
    timeline: [],
    actions: [
      { label: '查看流程', action: 'open-workbench', app: 'oa' },
    ],
  };
}

function normalizePriority(priority?: string | null) {
  if (priority === 'HIGH') return '高';
  if (priority === 'LOW') return '低';
  return '中';
}

function statusLabel(status: string) {
  if (status === 'PENDING') return '待处理';
  if (status === 'OPEN') return '待处理';
  if (status === 'IN_APPROVAL') return '审批中';
  if (status === 'APPROVED') return '已通过';
  if (status === 'REJECTED') return '已驳回';
  return status ? '其他状态' : '-';
}

function formatTodoTime(value?: string | null) {
  if (!value) return '刚刚';
  return value.replace('T', ' ').slice(0, 16);
}
