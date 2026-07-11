<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Search, X } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { EntityId, OrgUserSummaryResponse } from '@/api/types';
import { errorMessage } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDialog from '@/components/AdminDialog.vue';

const props = withDefaults(
  defineProps<{
    modelValue?: EntityId | null;
    label?: string;
    disabled?: boolean;
  }>(),
  { modelValue: null, label: '员工', disabled: false },
);

const emit = defineEmits<{
  'update:modelValue': [value: EntityId | null];
  select: [user: OrgUserSummaryResponse | null];
}>();

const toast = useToast();
const opened = ref(false);
const loading = ref(false);
const keyword = ref('');
const users = ref<OrgUserSummaryResponse[]>([]);
const selectedUser = ref<OrgUserSummaryResponse | null>(null);

const columns: TableColumn[] = [
  { key: 'realName', label: '姓名' },
  { key: 'primaryDeptName', label: '部门' },
  { key: 'positionName', label: '岗位' },
];

const displayValue = computed(() => {
  if (!props.modelValue) return '请选择';
  if (selectedUser.value) return userLabel(selectedUser.value);
  return '已选择员工';
});

onMounted(loadUsers);

async function loadUsers() {
  loading.value = true;
  try {
    const res = await adminApi.org.users({ pageNo: 1, pageSize: 50, keyword: keyword.value.trim() || undefined });
    users.value = res.list;
    if (props.modelValue && !selectedUser.value) {
      selectedUser.value = users.value.find((item) => String(item.userId) === String(props.modelValue)) ?? null;
    }
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function userLabel(user: OrgUserSummaryResponse) {
  return user.realName;
}

function pickUser(user: OrgUserSummaryResponse) {
  selectedUser.value = user;
  emit('update:modelValue', user.userId);
  emit('select', user);
  opened.value = false;
}

function clearUser() {
  selectedUser.value = null;
  emit('update:modelValue', null);
  emit('select', null);
}
</script>

<template>
  <div class="user-picker">
    <span>{{ label }}</span>
    <div class="picker-row">
      <button class="secondary-button picker-button" type="button" :disabled="disabled" @click="opened = true">
        {{ displayValue }}
      </button>
      <button v-if="modelValue && !disabled" class="icon-button" type="button" title="清空" @click="clearUser">
        <X :size="16" />
      </button>
    </div>
    <AdminDialog v-model:visible="opened" title="选择员工" labelled-by="user-picker-title" width="min(820px, 92vw)">
      <form class="picker-search" @submit.prevent="loadUsers">
        <input v-model="keyword" placeholder="姓名" />
        <button class="secondary-button" type="submit" :disabled="loading">
          <Search :size="16" />
          <span>搜索</span>
        </button>
      </form>
      <AdminTable interactive :columns="columns" :rows="users" :loading="loading" row-key="userId" @row-click="pickUser">
        <template #cell-primaryDeptName="{ row }">{{ row.primaryDeptName || '-' }}</template>
        <template #cell-positionName="{ row }">{{ row.positionName || '-' }}</template>
      </AdminTable>
    </AdminDialog>
  </div>
</template>

<style scoped>
.user-picker,
.picker-row {
  display: grid;
  gap: var(--space-xs);
}

.picker-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.picker-button {
  justify-content: flex-start;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.picker-search {
  display: flex;
  gap: var(--space-sm);
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-md);
}

.picker-search input {
  flex: 1;
}
</style>
