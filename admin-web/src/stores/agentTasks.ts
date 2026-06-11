import { defineStore } from 'pinia';
import type { EntityId } from '@/api/types';

export const useAgentTasksStore = defineStore('agent-tasks', {
  state: () => ({
    filters: {
      status: '',
      contextType: '',
      triggeredByType: '',
    },
    pagination: {
      pageNo: 1,
      pageSize: 20,
      total: 0,
    },
    selectedTaskId: null as EntityId | null,
  }),
});
