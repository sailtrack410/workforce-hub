import { defineStore } from 'pinia';
import type { EntityId } from '@/api/types';

export const useDepartmentStore = defineStore('department', {
  state: () => ({
    selectedDepartmentId: null as EntityId | null,
  }),
});
