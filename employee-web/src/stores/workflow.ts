import { defineStore } from 'pinia';
import { employeeApi } from '../api';
import type { WorkflowTodoResponse } from '../api';
import type { TodoRecord } from './shell';

export interface EmployeeWorkflowApi {
  todos(): Promise<WorkflowTodoResponse[]>;
}

export const useWorkflowStore = defineStore('employee-workflow', {
  state: () => ({
    todos: [] as TodoRecord[],
    loading: false,
    error: '',
    loadedAt: null as string | null,
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
  },
});

export function mapWorkflowTodoToRecord(todo: WorkflowTodoResponse): TodoRecord {
  return {
    id: `workflow-todo-${todo.todoId}`,
    title: todo.title,
    subtitle: `流程待办 · ${todo.status}`,
    note: todo.summary,
    time: formatTodoTime(todo.createdAt),
    status: todo.status,
    priority: normalizePriority(todo.priority),
    requestNo: `REQ-${todo.requestId}`,
    taskId: todo.taskId,
    currentStepName: '待处理',
    summary: todo.summary,
    fields: [
      ['requestId', String(todo.requestId)],
      ['taskId', todo.taskId],
      ['status', todo.status],
      ['priority', todo.priority ?? 'NORMAL'],
    ],
    timeline: ['后端待办已加载', '等待员工处理', '处理后回写审批动作'],
    apis: [
      api('GET', '/api/employee/workflow/todos', 'implemented', '员工端待办列表已由后端开放'),
      api('POST', '/api/employee/workflow/tasks/{taskId}/approve', 'implemented', '审批通过动作已由后端开放'),
      api('POST', '/api/employee/workflow/tasks/{taskId}/reject', 'implemented', '审批驳回动作已由后端开放'),
    ],
    actions: [
      { label: '审批通过', action: 'toast', message: '审批通过会通过 workflow 接口写入动作。' },
      { label: '驳回', action: 'toast', message: '驳回会通过 workflow 接口写入动作。' },
    ],
  };
}

function api(method: 'GET' | 'POST', path: string, status: 'implemented', note: string) {
  return { method, path, status, note };
}

function normalizePriority(priority?: string | null) {
  if (priority === 'HIGH') return '高';
  if (priority === 'LOW') return '低';
  return '中';
}

function formatTodoTime(value?: string | null) {
  if (!value) return '刚刚';
  return value.replace('T', ' ').slice(0, 16);
}
