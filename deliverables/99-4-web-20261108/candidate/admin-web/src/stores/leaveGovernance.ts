import { defineStore } from 'pinia';

export const useLeaveGovernanceStore = defineStore('leave-governance', {
  state: () => ({
    filters: {
      userId: '',
      leaveTypeId: '',
    },
  }),
});
