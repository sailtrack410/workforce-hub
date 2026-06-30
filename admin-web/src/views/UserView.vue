<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { Crosshair, Plus, RefreshCcw, X } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { EntityId, OrgUserSummaryResponse, OrgUserDetailResponse, OrgDepartmentTreeNodeResponse, OrgPositionResponse, OrgUserListQuery } from '@/api/types';
import { errorMessage, formatDateTime, statusLabel } from '@/utils/format';
import { useUserViewStore } from '@/stores/userView';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminDetailList, { type DetailRow } from '@/components/AdminDetailList.vue';
import AdminSkeleton from '@/components/AdminSkeleton.vue';
import AdminPagination from '@/components/AdminPagination.vue';
import OrgMindTree from '@/components/OrgMindTree.vue';

const store = useUserViewStore();
const toast = useToast();

const loading = ref(false);
const orgLoading = ref(false);
const detailLoading = ref(false);
const items = ref<OrgUserSummaryResponse[]>([]);
const orgUsers = ref<OrgUserSummaryResponse[]>([]);
const selectedDetail = ref<OrgUserDetailResponse | null>(null);
const depts = ref<OrgDepartmentTreeNodeResponse[]>([]);
const positions = ref<OrgPositionResponse[]>([]);
const creating = ref(false);
const createForm = reactive({ employeeNo: '', realName: '', gender: 'MALE', mobile: '', primaryDeptId: undefined as number | undefined, positionId: undefined as number | undefined, employmentStatus: 'ACTIVE', status: 'ACTIVE' });
const departmentCreating = ref(false);
const departmentFormOpen = ref(false);
const departmentForm = reactive({ parentId: undefined as EntityId | undefined, deptCode: '', deptName: '' });
const ORG_ROOT_ID = '__org_root__';
const orgViewportRef = ref<HTMLElement | null>(null);
const orgCanvasRef = ref<HTMLElement | null>(null);
const detailDialogOpen = ref(false);
const orgLinks = ref<OrgLink[]>([]);
const orgCanvasSize = reactive({ width: 0, height: 0 });
const orgPanState = { active: false, moved: false, pointerId: 0, x: 0, y: 0, left: 0, top: 0 };
let orgLinkFrame = 0;
let orgPanClearTimer = 0;
let orgResizeObserver: ResizeObserver | null = null;

interface OrgLink {
  key: string;
  path: string;
}

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
    { label: '邮箱', value: r.email ?? '-' },
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

const orgRootNodes = computed<OrgDepartmentTreeNodeResponse[]>(() => {
  if (!depts.value.length) return [];
  return [{
    id: ORG_ROOT_ID,
    parentId: null,
    treePath: null,
    deptCode: '全部部门',
    deptName: '组织架构',
    deptType: 'ROOT',
    deptLevel: -1,
    status: 'ACTIVE',
    children: depts.value,
  }];
});

const usersByDept = computed(() => {
  const map = new Map<string, OrgUserSummaryResponse[]>();
  for (const user of orgUsers.value) {
    const key = user.primaryDeptId == null ? '' : String(user.primaryDeptId);
    const group = map.get(key) ?? [];
    group.push(user);
    map.set(key, group);
  }
  return map;
});

const unassignedUsers = computed(() => usersByDept.value.get('') ?? []);
const expandedDeptIds = ref<string[]>([]);

const orgVisibleEdges = computed(() => {
  const edges: { sourceId: string; targetId: string }[] = [];
  function walk(nodes: OrgDepartmentTreeNodeResponse[]) {
    for (const node of nodes) {
      if (!isDeptExpanded(node.id)) continue;
      if (String(node.id) !== ORG_ROOT_ID) {
        for (const user of usersByDept.value.get(String(node.id)) ?? []) {
          edges.push({ sourceId: String(node.id), targetId: `user:${String(node.id)}:${String(user.userId)}` });
        }
      }
      if (node.children?.length) {
        for (const child of node.children) {
          edges.push({ sourceId: String(node.id), targetId: String(child.id) });
        }
        walk(node.children);
      }
    }
  }
  walk(orgRootNodes.value);
  return edges;
});

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
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    detailLoading.value = false;
  }
}

async function loadMeta() {
  orgLoading.value = true;
  try {
    const [d, p, u] = await Promise.all([adminApi.org.departmentTree(), adminApi.org.positions(), adminApi.org.orgChartUsers()]);
    depts.value = d;
    positions.value = p;
    orgUsers.value = u;
    expandedDeptIds.value = [...new Set([ORG_ROOT_ID, ...expandedDeptIds.value])];
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    orgLoading.value = false;
  }
}

async function handleCreate() {
  creating.value = true;
  try {
    await adminApi.org.createUser(createForm);
    createForm.employeeNo = '';
    createForm.realName = '';
    createForm.mobile = '';
    await Promise.all([loadList(), loadMeta()]);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    creating.value = false;
  }
}

function startCreateDepartment(parent?: OrgDepartmentTreeNodeResponse | null) {
  departmentForm.parentId = parent && String(parent.id) !== ORG_ROOT_ID ? parent.id : undefined;
  departmentForm.deptCode = '';
  departmentForm.deptName = '';
  departmentFormOpen.value = true;
}

async function handleCreateDepartment() {
  departmentCreating.value = true;
  try {
    const parentId = departmentForm.parentId;
    const deptId = await adminApi.org.createDepartment({
      parentId: parentId ?? null,
      deptCode: departmentForm.deptCode.trim(),
      deptName: departmentForm.deptName.trim(),
      deptType: 'TEAM',
      status: 'ACTIVE',
    });
    departmentForm.parentId = undefined;
    departmentForm.deptCode = '';
    departmentForm.deptName = '';
    departmentFormOpen.value = false;
    expandedDeptIds.value = [...new Set([
      ORG_ROOT_ID,
      ...expandedDeptIds.value,
      ...(parentId == null ? [] : [String(parentId)]),
      String(deptId),
    ])];
    await loadMeta();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    departmentCreating.value = false;
  }
}

async function handleDeleteDepartment(dept: OrgDepartmentTreeNodeResponse) {
  if (String(dept.id) === ORG_ROOT_ID) return;
  if (!window.confirm('删除部门')) return;
  try {
    await adminApi.org.deleteDepartment(dept.id);
    expandedDeptIds.value = expandedDeptIds.value.filter((item) => item !== String(dept.id));
    if (store.filters.deptId != null && String(store.filters.deptId) === String(dept.id)) {
      store.filters.deptId = undefined;
    }
    await Promise.all([loadList(), loadMeta()]);
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

function onRowClick(row: OrgUserSummaryResponse) {
  openUserDetail(row);
}

async function openUserDetail(row: OrgUserSummaryResponse) {
  detailDialogOpen.value = true;
  await loadDetail(row);
}

function closeUserDetail() {
  detailDialogOpen.value = false;
}

function isDeptExpanded(id: unknown) {
  return expandedDeptIds.value.includes(String(id));
}

function toggleDept(id: unknown) {
  const key = String(id);
  expandedDeptIds.value = isDeptExpanded(id)
    ? expandedDeptIds.value.filter((item) => item !== key)
    : [...expandedDeptIds.value, key];
}

function scheduleOrgLinks() {
  if (orgLinkFrame) window.cancelAnimationFrame(orgLinkFrame);
  orgLinkFrame = window.requestAnimationFrame(() => {
    orgLinkFrame = 0;
    redrawOrgLinks();
  });
}

function redrawOrgLinks() {
  const canvas = orgCanvasRef.value;
  if (!canvas) return;
  const canvasRect = canvas.getBoundingClientRect();
  orgCanvasSize.width = Math.max(canvas.scrollWidth, canvas.offsetWidth);
  orgCanvasSize.height = Math.max(canvas.scrollHeight, canvas.offsetHeight);
  const nodeMap = new Map<string, HTMLElement>();
  canvas.querySelectorAll<HTMLElement>('[data-org-node-id]').forEach((el) => {
    if (el.dataset.orgNodeId) nodeMap.set(el.dataset.orgNodeId, el);
  });
  orgLinks.value = orgVisibleEdges.value.flatMap((edge) => {
    const parent = nodeMap.get(edge.sourceId);
    const child = nodeMap.get(edge.targetId);
    if (!parent || !child) return [];
    const p = parent.getBoundingClientRect();
    const c = child.getBoundingClientRect();
    const x1 = p.right - canvasRect.left;
    const y1 = p.top - canvasRect.top + 33;
    const x2 = c.left - canvasRect.left;
    const y2 = c.top - canvasRect.top + 33;
    const midX = x1 + Math.max(24, (x2 - x1) / 2);
    return [{
      key: `${edge.sourceId}-${edge.targetId}`,
      path: `M ${x1} ${y1} H ${midX} V ${y2} H ${x2}`,
    }];
  });
}

function resetOrgPan() {
  const viewport = orgViewportRef.value;
  if (!viewport) return;
  viewport.scrollTo({ left: 0, top: 0, behavior: 'smooth' });
  scheduleOrgLinks();
}

function startOrgPan(event: PointerEvent) {
  const viewport = orgViewportRef.value;
  if (!viewport) return;
  if ((event.target as Element | null)?.closest('button,input,select,textarea,a,summary')) return;
  orgPanState.active = true;
  orgPanState.moved = false;
  orgPanState.pointerId = event.pointerId;
  orgPanState.x = event.clientX;
  orgPanState.y = event.clientY;
  orgPanState.left = viewport.scrollLeft;
  orgPanState.top = viewport.scrollTop;
  viewport.setPointerCapture(event.pointerId);
}

function moveOrgPan(event: PointerEvent) {
  const viewport = orgViewportRef.value;
  if (!viewport || !orgPanState.active || orgPanState.pointerId !== event.pointerId) return;
  const dx = event.clientX - orgPanState.x;
  const dy = event.clientY - orgPanState.y;
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) orgPanState.moved = true;
  viewport.scrollLeft = orgPanState.left - dx;
  viewport.scrollTop = orgPanState.top - dy;
}

function endOrgPan(event: PointerEvent) {
  const viewport = orgViewportRef.value;
  if (!viewport || orgPanState.pointerId !== event.pointerId) return;
  orgPanState.active = false;
  viewport.releasePointerCapture(event.pointerId);
  if (orgPanClearTimer) window.clearTimeout(orgPanClearTimer);
  orgPanClearTimer = window.setTimeout(() => {
    orgPanState.moved = false;
    orgPanClearTimer = 0;
  }, 0);
}

function preventOrgClickAfterPan(event: MouseEvent) {
  if (!orgPanState.moved) return;
  event.preventDefault();
  event.stopPropagation();
  orgPanState.moved = false;
}

watch([orgRootNodes, expandedDeptIds], () => nextTick(scheduleOrgLinks), { deep: true });

onMounted(() => {
  loadList();
  loadMeta();
  nextTick(() => {
    if (orgCanvasRef.value) {
      orgResizeObserver = new ResizeObserver(scheduleOrgLinks);
      orgResizeObserver.observe(orgCanvasRef.value);
    }
    scheduleOrgLinks();
  });
});

onBeforeUnmount(() => {
  if (orgLinkFrame) window.cancelAnimationFrame(orgLinkFrame);
  if (orgPanClearTimer) window.clearTimeout(orgPanClearTimer);
  orgResizeObserver?.disconnect();
});
</script>

<template>
  <div class="page-grid">
    <AdminPanel :span="12" title="组织架构">
      <template #actions>
        <button class="icon-button" title="新建部门" @click="startCreateDepartment(null)"><Plus :size="16" /></button>
        <button class="icon-button" title="重置视图" @click="resetOrgPan"><Crosshair :size="16" /></button>
        <button class="icon-button" title="刷新" @click="loadMeta"><RefreshCcw :size="16" /></button>
      </template>

      <div v-if="departmentFormOpen" class="department-form stack-form">
        <label>上级部门
          <select v-model="departmentForm.parentId">
            <option :value="undefined">顶级部门</option>
            <option v-for="f in flatDepts" :key="f.node.id" :value="f.node.id">{{ f.node.deptName }}</option>
          </select>
        </label>
        <label>部门编码 <input v-model="departmentForm.deptCode" /></label>
        <label>部门名称 <input v-model="departmentForm.deptName" /></label>
        <div class="department-actions">
          <button class="secondary-button" @click="departmentFormOpen = false">取消</button>
          <button class="primary-button" :disabled="departmentCreating || !departmentForm.deptCode.trim() || !departmentForm.deptName.trim()" @click="handleCreateDepartment">
            <Plus :size="16" /> 创建
          </button>
        </div>
      </div>

      <AdminSkeleton v-if="orgLoading" :lines="5" />
      <div
        v-else
        ref="orgViewportRef"
        class="org-chart"
        @pointerdown="startOrgPan"
        @pointermove="moveOrgPan"
        @pointerup="endOrgPan"
        @pointercancel="endOrgPan"
        @click.capture="preventOrgClickAfterPan"
        @scroll="scheduleOrgLinks"
      >
        <div ref="orgCanvasRef" class="org-canvas">
          <svg
            class="org-link-layer"
            :width="orgCanvasSize.width"
            :height="orgCanvasSize.height"
            :viewBox="`0 0 ${orgCanvasSize.width} ${orgCanvasSize.height}`"
            aria-hidden="true"
          >
            <path
              v-for="link in orgLinks"
              :key="link.key"
              class="org-link-path"
              :d="link.path"
            />
          </svg>
          <OrgMindTree
            v-if="orgRootNodes.length"
            :nodes="orgRootNodes"
            :users-by-dept="usersByDept"
            :expanded-dept-ids="expandedDeptIds"
            :selected-user-id="store.selectedUserId"
            :selected-dept-id="store.filters.deptId"
            :virtual-root-id="ORG_ROOT_ID"
            root
            @toggle="toggleDept"
            @select-user="openUserDetail"
            @add-department="startCreateDepartment"
            @delete-department="handleDeleteDepartment"
          />
          <div v-else class="empty-state">无部门</div>
        </div>

        <div v-if="unassignedUsers.length" class="org-users unassigned">
          <span class="org-empty">未分配部门</span>
          <button
            v-for="user in unassignedUsers"
            :key="user.userId"
            type="button"
            class="org-user"
            :class="{ selected: store.selectedUserId === user.userId }"
            @click="openUserDetail(user)"
          >
            <strong>{{ user.realName }}</strong>
            <span>{{ user.positionName ?? '-' }}</span>
            <span>{{ statusLabel(user.employmentStatus ?? '') }}</span>
          </button>
        </div>
      </div>
    </AdminPanel>

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

    <Teleport to="body">
      <div v-if="detailDialogOpen" class="user-detail-overlay" @click.self="closeUserDetail">
        <section class="user-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="user-detail-title">
          <header class="user-detail-header">
            <h3 id="user-detail-title">员工详情</h3>
            <button type="button" class="icon-button" title="关闭" @click="closeUserDetail"><X :size="16" /></button>
          </header>
          <AdminSkeleton v-if="detailLoading" :lines="5" />
          <AdminDetailList v-else-if="selectedDetail" :rows="detailRows" />
          <div v-else class="empty-state">无数据</div>
        </section>
      </div>
    </Teleport>
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

.department-form {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}

.department-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
}

.org-chart {
  height: min(68vh, 720px);
  overflow: auto;
  position: relative;
  padding: 18px 18px 24px;
  border: 1px solid rgba(0, 80, 180, 0.08);
  border-radius: var(--radius-sm);
  background:
    linear-gradient(rgba(208, 221, 239, 0.28) 1px, transparent 1px),
    linear-gradient(90deg, rgba(208, 221, 239, 0.28) 1px, transparent 1px),
    rgba(248, 250, 255, 0.62);
  background-size: 28px 28px;
  cursor: grab;
  overscroll-behavior: contain;
  scrollbar-width: none;
  user-select: none;
}

.org-chart::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.org-chart:active {
  cursor: grabbing;
}

.org-canvas {
  position: relative;
  min-width: 1900px;
  min-height: 860px;
  width: max-content;
  padding: 12px 120px 80px 12px;
}

.org-canvas :deep(.org-tree) {
  position: relative;
  z-index: 2;
}

.org-link-layer {
  position: absolute;
  inset: 0;
  z-index: 1;
  overflow: visible;
  pointer-events: none;
}

.org-link-path {
  fill: none;
  stroke: rgba(0, 137, 255, 0.42);
  stroke-width: 2;
  stroke-linecap: round;
}

.org-users {
  min-width: 420px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid var(--line);
}

.org-users.unassigned {
  align-items: center;
}

.org-user {
  min-width: 156px;
  max-width: 220px;
  min-height: 40px;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 2px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--ink);
  padding: 7px 10px;
  text-align: left;
}

.org-user:hover,
.org-user.selected {
  border-color: var(--green);
  background: var(--pill-bg);
}

.org-empty {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.user-detail-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(0, 20, 60, 0.4);
}

.user-detail-dialog {
  width: min(560px, 92vw);
  max-height: min(720px, 86vh);
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  box-shadow: var(--shadow);
  padding: 20px;
}

.user-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: 16px;
}

.user-detail-header h3 {
  margin: 0;
  font-size: 17px;
}
</style>
