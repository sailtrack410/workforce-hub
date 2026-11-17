import { defineStore } from 'pinia';
import type { EntityId } from '@/api/types';

export const useUserViewStore = defineStore('user-view', {
  state: () => ({
    filters: {
      keyword: '',
      deptId: undefined as EntityId | undefined,
      employmentStatus: '',
    },
    selectedUserId: null as EntityId | null,
    pagination: {
      pageNo: 1,
      pageSize: 20,
      total: 0,
    },
  }),
});
