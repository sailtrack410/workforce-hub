import { defineStore } from 'pinia';
import type { EntityId } from '@/api/types';

export const useLeaveTypeStore = defineStore('leave-type', {
  state: () => ({
    selectedLeaveTypeId: null as EntityId | null,
  }),
});
