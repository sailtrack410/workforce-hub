import { http, ApiError } from '@/api/http';
import { currentContext, localStatus } from './localData';
import type { PersonalTodoResponse } from '@/api/types';

export interface SyncOperation {
  id: string;
  method: 'POST' | 'PUT' | 'DELETE';
  path: string;
  body?: Record<string, unknown>;
  entityKey?: string;
  version?: number;
  snapshot?: unknown;
  state?: string;
  error?: string;
}
let syncing = false;
const todoBase = '/api/employee/personal-todos';

export interface TodoConflict {
  context: string;
  operation: SyncOperation;
  operations: SyncOperation[];
  local: PersonalTodoResponse;
  remote: PersonalTodoResponse | null;
}

export async function readTodoConflict(id: string): Promise<TodoConflict> {
  const context = await currentContext();
  if (!context || localStatus.suspended) throw new Error('登录状态已变化');
  const queue = await window.workforceDesktop!.pendingOperations(context);
  const operation = queue.find(op => op.id === id);
  if (!operation || !['conflict', 'error'].includes(operation.state ?? '') || !operation.entityKey || !/^\/api\/employee\/personal-todos\/\d+(?:\/(?:complete|reopen))?$/.test(operation.path)) throw new Error('该操作无法进行版本对比');
  const operations = queue.filter(op => op.entityKey === operation.entityKey && op.path.startsWith(todoBase + '/'));
  const local = operations[operations.length - 1].snapshot as PersonalTodoResponse;
  if (!local) throw new Error('本地修改不存在');
  let remote: PersonalTodoResponse | null;
  try { remote = await http.get<PersonalTodoResponse>(`${todoBase}/${operation.entityKey}`, undefined, { cache: false }); }
  catch (error) { if (error instanceof ApiError && error.status === 404) remote = null; else throw error; }
  if (await currentContext() !== context) throw new Error('登录状态已变化');
  return { context, operation, operations, local, remote };
}

export async function resolveTodoConflict(conflict: TodoConflict, choice: 'remote' | 'local' | 'merge', merged?: PersonalTodoResponse) {
  if (syncing || localStatus.suspended) throw new Error('同步处理中，请稍后重试');
  syncing = true;
  try {
    const fresh = await readTodoConflict(conflict.operation.id);
    if (fresh.context !== conflict.context) throw new Error('登录状态已变化');
    if (fresh.remote?.version !== conflict.remote?.version || Boolean(fresh.remote) !== Boolean(conflict.remote)
      || JSON.stringify(fresh.operations) !== JSON.stringify(conflict.operations)) throw new Error('版本已变化，请重新查看');
    let value = fresh.remote;
    if (choice !== 'remote') {
      const local = choice === 'merge' ? merged : fresh.local;
      if (!local?.title?.trim()) throw new Error('请填写待办名称');
      const last = fresh.operations[fresh.operations.length - 1];
      const path = `${todoBase}/${fresh.operation.entityKey}`;
      if (last.method === 'DELETE') {
        if (fresh.remote) await http.delete(`${path}?expectedVersion=${fresh.remote.version}`);
        value = null;
      } else {
        const body = { title: local.title.trim(), category: local.category, note: local.note ?? '', startAt: local.startAt || undefined,
          dueAt: local.dueAt || undefined, allDay: Boolean(local.allDay), reminderMinutes: typeof local.reminderMinutes === 'number' ? local.reminderMinutes : null };
        value = fresh.remote
          ? await http.put<PersonalTodoResponse>(path, { ...body, expectedVersion: fresh.remote.version })
          : await http.post<PersonalTodoResponse>(todoBase, { ...body, clientRequestId: `resolve-${fresh.operation.id}` });
        if (await currentContext() !== fresh.context) throw new Error('登录状态已变化');
        if (value.status !== local.status) value = await http.put<PersonalTodoResponse>(`${todoBase}/${value.todoId}/${local.status === 'DONE' ? 'complete' : 'reopen'}?expectedVersion=${value.version}`);
      }
    }
    if (await currentContext() !== fresh.context) throw new Error('登录状态已变化');
    await window.workforceDesktop!.discardOperations(fresh.context, fresh.operations.map(op => op.id));
    await window.workforceDesktop!.cacheInvalidate(fresh.context, todoBase);
    await pendingOperations();
    window.dispatchEvent(new CustomEvent('workforce:sync-result', { detail: { operation: fresh.operation, value } }));
  } finally { syncing = false; }
}
export async function pendingOperations() {
  const context = await currentContext();
  const queue = context ? await window.workforceDesktop!.pendingOperations(context) : [];
  localStatus.pending = queue.length; localStatus.conflicts = queue.filter((op) => op.state === 'conflict' || op.state === 'error').length;
  return queue;
}
async function execute<T>(op: SyncOperation) {
  const path = op.path + (op.version != null && !op.body ? `?expectedVersion=${op.version}` : '');
  if (op.method === 'POST') return http.post<T>(path, op.body);
  if (op.method === 'PUT') return http.put<T>(path, op.body);
  return http.delete<T>(path);
}
export async function durableRequest<T>(op: SyncOperation): Promise<{ queued: boolean; value?: T }> {
  const context = await currentContext();
  if (!context) return { queued: false, value: await execute<T>(op) };
  const desktop = window.workforceDesktop!;
  const earlier = await desktop.pendingOperations(context);
  await desktop.enqueue(context, op);
  if (op.entityKey && earlier.some((item) => item.entityKey === op.entityKey)) { await pendingOperations(); return { queued: true }; }
  try {
    const value = await execute<T>(op);
    await desktop.setOperationState(context, op.id, 'done');
    await pendingOperations();
    return { queued: false, value };
  } catch (error) {
    if (error instanceof TypeError || error instanceof ApiError && (error.status ?? error.code ?? 0) >= 500) {
      localStatus.offline = true; await pendingOperations(); return { queued: true };
    }
    await desktop.setOperationState(context, op.id, error instanceof ApiError && (error.status === 409 || error.code === 409) ? 'conflict' : 'error', error instanceof Error ? error.message : '同步失败');
    await pendingOperations(); throw error;
  }
}

export async function syncPending() {
  if (syncing || localStatus.suspended || !window.workforceDesktop) return;
  syncing = true;
  try {
    const context = await currentContext(); if (!context) return;
    const desktop = window.workforceDesktop;
    const queue = await desktop.pendingOperations(context);
    const blocked = new Set<string>();
    for (const op of queue) {
      if (localStatus.suspended || await currentContext() !== context) break;
      if (op.state && op.state !== 'pending' || op.entityKey && blocked.has(op.entityKey)) { if (op.entityKey) blocked.add(op.entityKey); continue; }
      try {
        const value = await execute<Record<string, unknown>>(op);
        if (value && op.entityKey && value.todoId != null) {
          for (const later of queue.slice(queue.indexOf(op) + 1).filter((next) => next.entityKey === op.entityKey)) {
            const old = later.entityKey!;
            later.path = later.path.replace('/' + old, '/' + String(value.todoId));
            later.entityKey = String(value.todoId); later.version = value.version as number;
            if (later.snapshot) later.snapshot = { ...later.snapshot as Record<string, unknown>, todoId: value.todoId, version: value.version };
            if (later.body) later.body.expectedVersion = value.version;
            await desktop.enqueue(context, later);
          }
        }
        await desktop.setOperationState(context, op.id, 'done');
        window.dispatchEvent(new CustomEvent('workforce:sync-result', { detail: { operation: op, value } }));
      } catch (error) {
        if (error instanceof TypeError || error instanceof ApiError && (error.status ?? error.code ?? 0) >= 500) { localStatus.offline = true; break; }
        if (op.method === 'DELETE' && error instanceof ApiError && (error.status === 404 || error.code === 404)) { await desktop.setOperationState(context, op.id, 'done'); continue; }
        const message = error instanceof Error ? error.message : '同步失败';
        await desktop.setOperationState(context, op.id, error instanceof ApiError && (error.status === 409 || error.code === 409) ? 'conflict' : 'error', message);
        if (op.entityKey) blocked.add(op.entityKey);
      }
    }
    await pendingOperations();
  } finally { syncing = false; }
}

export async function discardOperation(id: string) {
  const context = await currentContext(); if (!context) return;
  await window.workforceDesktop!.setOperationState(context, id, 'done');
  await pendingOperations();
}
