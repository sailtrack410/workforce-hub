import { http, ApiError } from '@/api/http';
import { currentContext, localStatus } from './localData';

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
