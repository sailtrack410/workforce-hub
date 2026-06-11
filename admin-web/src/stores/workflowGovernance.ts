import { defineStore } from 'pinia';
import type { EntityId } from '@/api/types';

export const useWorkflowGovernanceStore = defineStore('workflow-governance', {
  state: () => ({
    filters: {
      status: '',
      bizType: '',
      keyword: '',
    },
    pagination: {
      pageNo: 1,
      pageSize: 20,
      total: 0,
    },
    selectedRequestId: null as EntityId | null,
    selectedTemplateId: null as EntityId | null,
  }),
});
