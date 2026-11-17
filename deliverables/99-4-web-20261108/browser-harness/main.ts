import { createApp, h, reactive, ref } from 'vue';
import { createPinia } from 'pinia';
import { employeeApi } from '@/api';
import { useWorkbenchState } from '@/composables/useWorkbenchState';
import { statusText } from '@/utils/employee-format';
import { attendanceResultLabel } from '../../../admin-web/src/utils/format';
import type { useShellStore } from '@/stores/shell';
import type { useSessionStore } from '@/stores/session';

const requests = ref<Array<{ startAt: string; endAt: string }>>([]);
employeeApi.workflow.leaveDuration = async (query) => {
  requests.value.push({ ...query });
  return { durationMinutes: 180 };
};

createApp({
  setup() {
    const shell = reactive({ active: { workbench: 'oa' }, activeWorkbench: { id: 'oa', title: 'OA' }, activeFile: { files: [] }, pendingTodoCount: 0 }) as unknown as ReturnType<typeof useShellStore>;
    const session = reactive({ isAuthenticated: false, user: { userId: '1001' }, subordinates: [] }) as unknown as ReturnType<typeof useSessionStore>;
    const state = useWorkbenchState({ shell, session, showToast: () => undefined });
    const field = (name: 'startAt' | 'endAt', label: string) => h('label', [
      h('span', label),
      h('input', { id: name, type: 'datetime-local', value: state.leaveForm.value[name], onInput: (event: Event) => { state.leaveForm.value[name] = (event.target as HTMLInputElement).value; } }),
    ]);
    return () => h('main', [
      h('header', [h('small', '99-4 · 浏览器受控 API 测试页'), h('h1', '请假排班计时'), h('p', '真实前端 composable 与格式函数；预览接口受控返回 180 分钟，未连接业务后端。')]),
      h('section', [h('h2', '员工端请假预览'), field('startAt', '开始时间'), field('endAt', '结束时间'), h('label', [h('span', '请假时长'), h('output', { id: 'duration' }, state.leaveForm.value.durationMinutes ? `${state.leaveForm.value.durationMinutes} 分钟` : '—')]), h('p', { id: 'error', role: 'status' }, state.leaveError.value)]),
      h('section', [h('h2', '排班状态'), h('dl', [h('dt', '员工端 REST'), h('dd', { id: 'employee-rest' }, statusText('REST')), h('dt', '管理端 REST'), h('dd', { id: 'admin-rest' }, attendanceResultLabel('REST'))])]),
      h('section', { class: 'evidence' }, [h('h2', '受控接口记录'), h('code', 'GET /api/employee/workflow/leave-duration'), h('pre', { id: 'request' }, JSON.stringify(requests.value.at(-1) ?? null, null, 2)), h('code', '{ "durationMinutes": 180 }')]),
    ]);
  },
}).use(createPinia()).mount('#app');
