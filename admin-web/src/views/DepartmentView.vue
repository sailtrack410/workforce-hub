<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { OrgDepartmentTreeNodeResponse, OrgDepartmentSaveRequest } from '@/api/types';
import { departmentTypeLabel, errorMessage, statusLabel } from '@/utils/format';
import { useDepartmentStore } from '@/stores/department';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminCompactList from '@/components/AdminCompactList.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminSkeleton from '@/components/AdminSkeleton.vue';
import { maxLength, requiredText } from '@/utils/validation';

const store = useDepartmentStore();
const toast = useToast();

const loading = ref(false);
const tree = ref<OrgDepartmentTreeNodeResponse[]>([]);

const createForm = reactive<OrgDepartmentSaveRequest>({
  parentId: null,
  deptCode: '',
  deptName: '',
  deptType: 'DEPARTMENT',
  status: 'ACTIVE',
});
const creating = ref(false);
const createNameTouched = ref(false);
const createNameError = computed(() => requiredText(createForm.deptName, '部门名称') || maxLength(createForm.deptName, 128, '部门名称'));

const selected = computed(() => {
  if (!store.selectedDepartmentId) return null;
  const flat = flatten(tree.value);
  return flat.find((f) => f.node.id === store.selectedDepartmentId)?.node ?? null;
});

async function loadTree() {
  loading.value = true;

  try {
    tree.value = await adminApi.org.departmentTree();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function selectNode(node: OrgDepartmentTreeNodeResponse) {
  store.selectedDepartmentId = node.id;
  createForm.parentId = node.id;
}

async function handleCreate() {
  if (createNameError.value) return;
  creating.value = true;
  try {
    await adminApi.org.createDepartment(createForm);
    createForm.deptCode = '';
    createForm.deptName = '';
    createNameTouched.value = false;
    await loadTree();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    creating.value = false;
  }
}

async function handleUpdate() {
  if (!selected.value) return;
  try {
    await adminApi.org.updateDepartment(selected.value.id, {
      deptCode: selected.value.deptCode,
      deptName: selected.value.deptName,
      deptType: selected.value.deptType,
      leaderUserId: selected.value.leaderUserId,
      status: selected.value.status,
    });
    await loadTree();
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

function flatten(nodes: OrgDepartmentTreeNodeResponse[], depth = 0): { node: OrgDepartmentTreeNodeResponse; depth: number }[] {
  return nodes.flatMap(n => [{ node: n, depth }, ...flatten(n.children ?? [], depth + 1)]);
}

const flatList = computed(() => flatten(tree.value));

const detailRows = computed<DetailRow[]>(() => {
  const s = selected.value;
  if (!s) return [];
  return [
    { label: '名称', value: s.deptName },
    { label: '类型', value: departmentTypeLabel(s.deptType) },
    { label: '层级', value: String(s.deptLevel ?? '-') },
    { label: '负责人', value: s.leaderName ?? '-' },
    { label: '状态', value: statusLabel(s.status) },
  ];
});

onMounted(loadTree);
</script>

<template>
  <div class="page-grid">
    <AdminPanel :span="selected ? 7 : 12" title="部门树">
      <template #actions>
        <button class="secondary-button" title="刷新" @click="loadTree"><RefreshCcw :size="16" />刷新</button>
      </template>

      <AdminFilterBar :show-search="false">
        <label class="filter-field">
          <input v-model="createForm.deptName" :aria-invalid="createNameTouched && Boolean(createNameError)" maxlength="128" placeholder="部门名称" @blur="createNameTouched = true" />
          <small v-if="createNameTouched && createNameError" class="field-error">{{ createNameError }}</small>
        </label>
        <select v-model="createForm.parentId">
          <option :value="null">根节点</option>
          <option v-for="f in flatList" :key="f.node.id" :value="f.node.id">{{ '&nbsp;'.repeat(f.depth * 4) }}{{ f.node.deptName }}</option>
        </select>
        <button class="primary-button" :disabled="creating || Boolean(createNameError)" @click="handleCreate">
          <Plus :size="16" /> 新建
        </button>
      </AdminFilterBar>

      <AdminSkeleton v-if="loading" :lines="6" />
      <AdminCompactList v-else>
        <article
          v-for="f in flatList"
          :key="f.node.id"
          class="clickable"
          :class="{ selected: store.selectedDepartmentId === f.node.id }"
          :style="{ paddingLeft: 10 + f.depth * 20 + 'px' }"
          role="button"
          tabindex="0"
          @click="selectNode(f.node)"
          @keydown.enter.prevent="selectNode(f.node)"
          @keydown.space.prevent="selectNode(f.node)"
        >
          <strong>{{ f.node.deptName }}</strong>
          <span>{{ statusLabel(f.node.status) }}</span>
        </article>
      </AdminCompactList>
    </AdminPanel>

    <AdminPanel v-if="selected" :span="5" title="部门详情">
      <AdminDetailList :rows="detailRows" />
      <div class="detail-actions">
        <button class="secondary-button" @click="handleUpdate">保存修改</button>
      </div>
    </AdminPanel>
  </div>
</template>

<style scoped>
.detail-actions {
  display: flex;
  margin-top: var(--space-base);
}

.filter-field { display: grid; gap: var(--space-xs); }
</style>
