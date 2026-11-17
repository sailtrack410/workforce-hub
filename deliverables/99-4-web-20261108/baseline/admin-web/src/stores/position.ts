import { defineStore } from 'pinia';
import type { EntityId } from '@/api/types';

export const usePositionStore = defineStore('position', {
  state: () => ({
    selectedPositionId: null as EntityId | null,
  }),
});
