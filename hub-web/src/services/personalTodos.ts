import { http } from '@/api/http';
import type { EntityId, PersonalTodoResponse } from '@/api/types';
import { durableRequest, pendingOperations } from './sync';

const base = '/api/employee/personal-todos';
type Save = { title: string; category: PersonalTodoResponse['category']; startAt?: string; dueAt?: string; note?: string; allDay?: boolean; reminderMinutes?: number | null };
let current: PersonalTodoResponse[] = [];
export async function listPersonalTodos(query = {}) {
  let list: PersonalTodoResponse[];
  try { list = await http.get<PersonalTodoResponse[]>(base, query); }
  catch (error) { if (!(await pendingOperations()).some(op => op.path.startsWith(base))) throw error; list = []; }
  const map = new Map(list.map((t) => [String(t.todoId), t]));
  for (const op of await pendingOperations()) {
    if (!op.path.startsWith(base) || !op.entityKey) continue;
    if (op.method === 'DELETE') map.delete(op.entityKey);
    else if (op.snapshot) map.set(op.entityKey, { ...(op.snapshot as PersonalTodoResponse), syncState: op.state === 'conflict' || op.state === 'error' ? 'conflict' : 'pending' });
  }
  current = [...map.values()]; return current;
}
export async function savePersonalTodo(action: 'create' | 'update' | 'complete' | 'reopen' | 'delete', todoId?: EntityId, request?: Save) {
  if (!window.workforceDesktop) {
    if (action === 'create') return http.post<PersonalTodoResponse>(base, request);
    const path = `${base}/${todoId}`;
    if (action === 'update') return http.put<PersonalTodoResponse>(path, request);
    if (action === 'delete') { await http.delete(path); return undefined; }
    return http.put<PersonalTodoResponse>(`${path}/${action}`);
  }
  const id = crypto.randomUUID();
  const existing = current.find((t) => String(t.todoId) === String(todoId));
  const localId = action === 'create' ? `local-${id}` : String(todoId);
  const snapshot = { ...existing, ...request, todoId: localId, status: action === 'complete' ? 'DONE' : action === 'reopen' ? 'PENDING' : existing?.status || 'PENDING', syncState: 'pending' } as PersonalTodoResponse;
  const result = await durableRequest<PersonalTodoResponse>({ id, method: action === 'create' ? 'POST' : action === 'delete' ? 'DELETE' : 'PUT',
    path: action === 'create' ? base : `${base}/${todoId}${['complete', 'reopen'].includes(action) ? '/' + action : ''}`,
    body: request ? { ...request, ...(action === 'create' ? { clientRequestId: id } : existing?.version != null ? { expectedVersion: existing.version } : {}) } : undefined,
    entityKey: localId, version: existing?.version, snapshot });
  if (action === 'delete') { current = current.filter((t) => String(t.todoId) !== localId); return undefined; }
  const saved = result.queued ? snapshot : result.value!;
  current = [...current.filter((t) => String(t.todoId) !== localId), saved];
  return saved;
}
