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
      ['申请编号', `REQ-${todo.requestId}`],
      ['任务状态', todo.status],
      ['优先级', normalizePriority(todo.priority)],
      ['当前处理', '待处理'],
    ],
    timeline: ['待办已同步', '等待员工处理', '处理完成后更新审批状态'],
    apis: [],
    actions: [
      { label: '审批通过', action: 'toast', message: '审批通过会通过 workflow 接口写入动作。' },
      { label: '驳回', action: 'toast', message: '驳回会通过 workflow 接口写入动作。' },
    ],
  };
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
