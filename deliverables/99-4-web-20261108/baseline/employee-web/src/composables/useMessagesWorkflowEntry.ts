import { ref } from 'vue';
import type { SectionAction } from '@/stores/shell';

type WorkflowEntryKey = 'todos' | 'leave' | 'requests' | 'attendance';

interface WorkflowEntryAction {
  key: WorkflowEntryKey;
  label: string;
  action: SectionAction;
}

interface UseMessagesWorkflowEntryOptions {
  runAction: (action?: SectionAction) => void;
}

// 消息面板快捷入口：待办/请假/申请/考勤，点击后切换到对应 section
const workflowEntryActions: WorkflowEntryAction[] = [
  {
    key: 'todos',
    label: '查看待办',
    action: {
      label: '查看待办',
      action: 'switch-section',
      section: 'todos',
    },
  },
  {
    key: 'leave',
    label: '发起请假',
    action: {
      label: '发起请假',
      action: 'open-workbench',
      app: 'leave',
    },
  },
  {
    key: 'requests',
    label: '我的申请',
    action: {
      label: '我的申请',
      action: 'open-workbench',
      app: 'requests',
    },
  },
  {
    key: 'attendance',
    label: '考勤处理',
    action: {
      label: '考勤处理',
      action: 'open-workbench',
      app: 'attendance',
    },
  },
];

export function useMessagesWorkflowEntry({ runAction }: UseMessagesWorkflowEntryOptions) {
  const workflowEntryOpen = ref(false);

  function openWorkflowEntry() {
    workflowEntryOpen.value = true;
  }

  function closeWorkflowEntry() {
    workflowEntryOpen.value = false;
  }

  function triggerWorkflowEntry(key: WorkflowEntryKey) {
    const entry = workflowEntryActions.find((item) => item.key === key);
    if (!entry) return;
    workflowEntryOpen.value = false;
    runAction(entry.action);
  }

  return {
    workflowEntryOpen,
    workflowEntryActions,
    openWorkflowEntry,
    closeWorkflowEntry,
    triggerWorkflowEntry,
  };
}
