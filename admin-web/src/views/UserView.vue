<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { OrgUserSummaryResponse, OrgUserDetailResponse, OrgDepartmentTreeNodeResponse, OrgPositionResponse, OrgUserListQuery } from '@/api/types';
import { errorMessage, formatDateTime, statusLabel } from '@/utils/format';
import { useUserViewStore } from '@/stores/userView';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';
import AdminSkeleton from '@/components/AdminSkeleton.vue';
import AdminPagination from '@/components/AdminPagination.vue';

const store = useUserViewStore();
const toast = useToast();

const loading = ref(false);
const detailLoading = ref(false);
const items = ref<OrgUserSummaryResponse[]>([]);
const selectedDetail = ref<OrgUserDetailResponse | null>(null);
const depts = ref<OrgDepartmentTreeNodeResponse[]>([]);
const positions = ref<OrgPositionResponse[]>([]);
const creating = ref(false);
const createForm = reactive({ employeeNo: '', realName: '', gender: 'MALE', mobile: '', primaryDeptId: undefined as number | undefined, positionId: undefined as number | undefined, employmentStatus: 'ACTIVE', status: 'ACTIVE' });

const columns: TableColumn[] = [
  { key: 'employeeNo', label: '工号' },
  { key: 'realName', label: '姓名' },
  { key: 'primaryDeptName', label: '部门' },
  { key: 'positionName', label: '岗位' },
  { key: 'employmentStatus', label: '状态' },
];

const detailRows = computed<DetailRow[]>(() => {
  const r = selectedDetail.value;
  if (!r) return [];
  return [
    { label: '工号', value: r.employeeNo ?? '-' },
    { label: '姓名', value: r.realName },
    { label: '性别', value: r.gender ?? '-' },
    { label: '手机', value: r.mobile ?? '-' },
    { label: '部门', value: r.primaryDeptName ?? '-' },
    { label: '岗位', value: r.positionName ?? '-' },
    { label: '上级', value: r.managerName ?? '-' },
    { label: '入职日期', value: r.hireDate ?? '-' },
    { label: '在职状态', value: statusLabel(r.employmentStatus ?? '') },
    { label: '状态', value: statusLabel(r.status ?? '') },
  ];
});

function flattenDepts(nodes: OrgDepartmentTreeNodeResponse[], depth = 0): { node: OrgDepartmentTreeNodeResponse; depth: number }[] {
  return nodes.flatMap(n => [{ node: n, depth }, ...flattenDepts(n.children ?? [], depth + 1)]);
}

const flatDepts = computed(() => flattenDepts(depts.value));

async function loadList() {
  loading.value = true;
  try {
    const query: OrgUserListQuery = { pageNo: store.pagination.pageNo, pageSize: store.pagination.pageSize };
    if (store.filters.keyword) query.keyword = store.filters.keyword;
    if (store.filters.deptId) query.deptId = store.filters.deptId;
    if (store.filters.employmentStatus) query.employmentStatus = store.filters.employmentStatus;
    const res = await adminApi.org.users(query);
    items.value = res.list;
    store.pagination.total = res.total;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

async function loadDetail(row: OrgUserSummaryResponse) {
  store.selectedUserId = row.userId;
  selectedDetail.value = null;
  detailLoading.value = true;
  try {
    selectedDetail.value = await adminApi.org.userDetail(row.userId);
  } finally {
    detailLoading.value = false;
  }
}

async function loadMeta() {
  try {
    const [d, p] = await Promise.all([adminApi.org.departmentTree(), adminApi.org.positions()]);
    depts.value = d;
    positions.value = p;
  } catch { /* ignore meta load errors */ }
}

async function handleCreate() {
  creating.value = true;
  try {
    await adminApi.org.createUser(createForm);
    createForm.employeeNo = '';
    createForm.realName = '';
    createForm.mobile = '';
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    creating.value = false;
  }
}

function onRowClick(row: OrgUserSummaryResponse) {
  loadDetail(row);
}

onMounted(() => { loadList(); loadMeta(); });
</script>

<template>
  <div class="page-grid">
    <AdminPanel :span="7" title="人员列表">
      <template #actions>
        <button class="icon-button" title="刷新" @click="loadList"><RefreshCcw :size="16" /></button>
      </template>
      <AdminFilterBar>
        <input v-model="store.filters.keyword" placeholder="搜索工号/姓名" @keyup.enter="loadList" />
        <select v-model="store.filters.deptId" @change="loadList">
          <option :value="undefined">全部部门</option>
          <option v-for="f in flatDepts" :key="f.node.id" :value="f.node.id">{{ '&nbsp;'.repeat(f.depth * 4) }}{{ f.node.deptName }}</option>
        </select>
        <select v-model="store.filters.employmentStatus" @change="loadList">
          <option value="">全部状态</option>
          <option value="ACTIVE">在职</option>
          <option value="PROBATION">试用</option>
          <option value="RESIGNED">离职</option>
        </select>
        <button class="primary-button" @click="loadList">搜索</button>
      </AdminFilterBar>

      <details class="create-section">
        <summary class="create-summary">新建员工</summary>
        <div class="stack-form create-form">
          <label>工号 <input v-model="createForm.employeeNo" /></label>
          <label>姓名 <input v-model="createForm.realName" /></label>
          <label>手机 <input v-model="createForm.mobile" /></label>
          <label>部门
            <select v-model="createForm.primaryDeptId">
              <option :value="undefined">请选择</option>
              <option v-for="f in flatDepts" :key="f.node.id" :value="f.node.id">{{ f.node.deptName }}</option>
            </select>
          </label>
          <label>岗位
            <select v-model="createForm.positionId">
              <option :value="undefined">请选择</option>
              <option v-for="p in positions" :key="p.id" :value="p.id">{{ p.positionName }}</option>
            </select>
          </label>
          <button class="primary-button" :disabled="creating || !createForm.employeeNo || !createForm.realName" @click="handleCreate">
            <Plus :size="16" /> 创建
          </button>
        </div>
      </details>

      <AdminTable
        :columns="columns"
        :rows="items"
        :loading="loading"
        :selected-id="store.selectedUserId"
        row-key="userId"
        @row-click="onRowClick"
      />
      <AdminPagination
        :page-no="store.pagination.pageNo"
        :page-size="store.pagination.pageSize"
        :total="store.pagination.total"
        @update:page-no="(v) => { store.pagination.pageNo = v; loadList(); }"
        @update:page-size="(v) => { store.pagination.pageSize = v; store.pagination.pageNo = 1; loadList(); }"
      />
    </AdminPanel>

    <AdminPanel :span="5" title="员工详情">
      <template v-if="detailLoading"><AdminSkeleton :lines="5" /></template>
      <template v-else-if="selectedDetail">
        <AdminDetailList :rows="detailRows" />
      </template>
      <template v-else><div class="empty-state">选择左侧员工查看详情</div></template>
    </AdminPanel>
  </div>
</template>

<style scoped>
.create-section {
  margin-bottom: 12px;
}

.create-summary {
  cursor: pointer;
  color: var(--green);
  font-weight: 800;
  font-size: 13px;
}

.create-form {
  margin-top: 10px;
}
</style>
