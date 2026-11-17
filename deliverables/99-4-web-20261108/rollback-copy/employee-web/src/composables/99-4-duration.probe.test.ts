import { expect, it, vi } from 'vitest';
import { stdout } from 'node:process';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick, reactive } from 'vue';
import { employeeApi } from '@/api';
import { useWorkbenchState } from '@/composables/useWorkbenchState';
import type { useShellStore } from '@/stores/shell';
import type { useSessionStore } from '@/stores/session';

it('uses 180 scheduled morning minutes for the same 09:00-12:00 input', async () => {
  setActivePinia(createPinia());
  Object.assign(employeeApi.workflow, { leaveDuration: vi.fn().mockResolvedValue({ durationMinutes: 180 }) });
  const shell = reactive({ active: { workbench: 'oa' }, activeWorkbench: { id: 'oa', title: 'OA' }, activeFile: { files: [] }, pendingTodoCount: 0 }) as unknown as ReturnType<typeof useShellStore>;
  const session = reactive({ isAuthenticated: false, user: { userId: '1001' }, subordinates: [] }) as unknown as ReturnType<typeof useSessionStore>;
  const state = useWorkbenchState({ shell, session, showToast: vi.fn() });
  state.leaveForm.value.startAt = '2026-05-07T09:00';
  state.leaveForm.value.endAt = '2026-05-07T12:00';
  await nextTick();
  stdout.write(`INPUT=2026-05-07T09:00/2026-05-07T12:00; SERVER_MINUTES=180; DISPLAY_MINUTES=${state.leaveForm.value.durationMinutes}\n`);
  expect(state.leaveForm.value.durationMinutes).toBe('180');
});
