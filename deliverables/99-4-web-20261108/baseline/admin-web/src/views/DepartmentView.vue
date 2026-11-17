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
import AdminMetricStrip from '@/components/AdminMetricStrip.vue';
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
const departmentMetrics = computed(() => [
  { label: '组织单元', value: flatList.value.length },
  { label: '已启用', value: flatList.value.filter((item) => item.node.status === 'ACTIVE').length },
  { label: '未设负责人', value: flatList.value.filter((item) => !item.node.leaderUserId).length },
  { label: '最大层级', value: flatList.value.length ? Math.max(...flatList.value.map((item) => item.depth + 1)) : 0 },
]);

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
  <div class="page-grid department-page">
    <AdminPanel :span="12" title="组织结构健康度" subtitle="负责人缺口与停用节点会影响审批归属和组织协同">
      <AdminMetricStrip :cards="departmentMetrics" />
    </AdminPanel>

    <AdminPanel :span="7" :title="`组织单元目录 (${flatList.length})`" subtitle="按层级维护部门，选择节点后在右侧核对主数据">
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
          <div class="department-identity">
            <strong>{{ f.node.deptName }}</strong>
            <span>{{ departmentTypeLabel(f.node.deptType) }} · L{{ f.depth + 1 }}</span>
          </div>
          <div class="department-state">
            <span :class="{ 'risk-text': !f.node.leaderUserId }">{{ f.node.leaderName || '未设负责人' }}</span>
            <span class="status-pill" :class="{ 'risk-status': f.node.status !== 'ACTIVE' }">{{ statusLabel(f.node.status) }}</span>
          </div>
        </article>
      </AdminCompactList>
    </AdminPanel>

    <AdminPanel :span="5" title="组织单元详情" :subtitle="selected ? `当前节点：${selected.deptName}` : '从左侧选择组织单元'">
      <template v-if="selected">
        <div v-if="!selected.leaderUserId || selected.status !== 'ACTIVE'" class="governance-notice">
          <strong>治理提示</strong>
          <span>{{ !selected.leaderUserId ? '当前节点未设置负责人' : '当前节点处于停用状态' }}</span>
        </div>
        <AdminDetailList :rows="detailRows" />
        <div class="detail-actions">
          <button class="primary-button" @click="handleUpdate">保存修改</button>
        </div>
      </template>
      <div v-else class="empty-state">选择节点后查看层级、负责人和状态</div>
    </AdminPanel>
  </div>
</template>

<style scoped>
.department-page { letter-spacing: 0; }

.department-page :deep(.compact-list article) {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-md);
}

.department-identity,
.department-state {
  display: grid;
  gap: var(--space-xs);
  min-width: 0;
}

.department-identity span,
.department-state > span:first-child {
  color: var(--muted);
  font-size: var(--text-xs);
}

.department-state { justify-items: end; text-align: right; }

.governance-notice {
  display: grid;
  gap: var(--space-xs);
  margin-bottom: var(--space-base);
  padding: var(--space-md);
  border-left: 3px solid var(--amber);
  background: var(--surface-subtle);
}

.governance-notice span { color: var(--muted); font-size: var(--text-xs); }

.detail-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-base);
}

.filter-field { display: grid; gap: var(--space-xs); }

.risk-status,
.risk-text { color: var(--red-dark) !important; }

@media (max-width: 520px) {
  .department-page :deep(.compact-list article) { grid-template-columns: 1fr; }
  .department-state { justify-items: start; text-align: left; }
}
</style>
