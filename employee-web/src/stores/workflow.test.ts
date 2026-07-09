import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import type { WorkflowTodoResponse } from '../api';
import { mapWorkflowTodoToRecord, useWorkflowStore, type EmployeeWorkflowApi } from './workflow';

const todo: WorkflowTodoResponse = {
  todoId: 10,
  requestId: 20,
  taskId: 'TASK-10',
  title: '王晨年假申请',
  summary: '请假 2 天，等待直属上级审批',
  status: 'PENDING',
  priority: 'HIGH',
  createdAt: '2026-05-09T10:24:00',
};

function createWorkflowApi(todos: WorkflowTodoResponse[] = [todo]): EmployeeWorkflowApi {
  return {
    todos: () => Promise.resolve(todos),
    approveTask: () => Promise.resolve({ requestId: 20, status: 'APPROVED' }),
    rejectTask: () => Promise.resolve({ requestId: 20, status: 'REJECTED' }),
  };
}

describe('workflow store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
  });

  it('maps backend todo response into shell todo record', () => {
    const record = mapWorkflowTodoToRecord(todo);

    expect(record).toMatchObject({
      id: 'workflow-todo-10',
      title: '王晨年假申请',
      status: 'PENDING',
      priority: '高',
      requestNo: 'REQ-20',
      taskId: 'TASK-10',
      currentStepName: '待处理',
    });
    expect(record.actions).toEqual([{ label: '查看流程', action: 'open-workbench', app: 'oa' }]);
  });

  it('loads workflow todos from employee API', async () => {
    const workflow = useWorkflowStore();

    await workflow.loadTodos(createWorkflowApi());

    expect(workflow.loading).toBe(false);
    expect(workflow.todos.map((item) => item.id)).toEqual(['workflow-todo-10']);
  });
});
