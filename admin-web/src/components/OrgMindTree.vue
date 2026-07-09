<script setup lang="ts">
import { ChevronDown, ChevronRight, Plus, Trash2, Users } from 'lucide-vue-next';
import type { EntityId, OrgDepartmentTreeNodeResponse, OrgUserSummaryResponse } from '@/api/types';
import { statusLabel } from '@/utils/format';

const props = withDefaults(
  defineProps<{
    nodes: OrgDepartmentTreeNodeResponse[];
    usersByDept: ReadonlyMap<string, OrgUserSummaryResponse[]>;
    expandedDeptIds: string[];
    selectedUserId?: EntityId | null;
    selectedDeptId?: EntityId | null;
    virtualRootId?: EntityId | null;
    root?: boolean;
  }>(),
  { root: false },
);

const emit = defineEmits<{
  toggle: [deptId: EntityId];
  selectUser: [user: OrgUserSummaryResponse];
  addDepartment: [dept: OrgDepartmentTreeNodeResponse];
  deleteDepartment: [dept: OrgDepartmentTreeNodeResponse];
}>();

function sameId(left: EntityId | null | undefined, right: EntityId) {
  return left != null && String(left) === String(right);
}

function isExpanded(id: EntityId) {
  return props.expandedDeptIds.includes(String(id));
}

function deptUsers(id: EntityId) {
  return props.usersByDept.get(String(id)) ?? [];
}

function isVirtualRoot(id: EntityId) {
  return props.virtualRootId != null && String(props.virtualRootId) === String(id);
}

function nodeUserCount(node: OrgDepartmentTreeNodeResponse) {
  if (!isVirtualRoot(node.id)) return deptUsers(node.id).length;
  let total = 0;
  for (const users of props.usersByDept.values()) total += users.length;
  return total;
}

function hasVisibleChildren(node: OrgDepartmentTreeNodeResponse) {
  return isExpanded(node.id) && Boolean(node.children?.length);
}

function hasVisiblePeople(node: OrgDepartmentTreeNodeResponse) {
  return isExpanded(node.id) && !isVirtualRoot(node.id) && deptUsers(node.id).length > 0;
}

function hasVisibleBranch(node: OrgDepartmentTreeNodeResponse) {
  return hasVisibleChildren(node) || hasVisiblePeople(node);
}

function employeeNodeId(deptId: EntityId, userId: EntityId) {
  return `user:${String(deptId)}:${String(userId)}`;
}
</script>

<template>
  <ul :class="['org-tree', { 'org-tree-root': root }]">
    <li v-for="node in nodes" :key="String(node.id)" class="org-tree-item">
      <div class="org-branch" :class="{ 'has-children': hasVisibleBranch(node) }">
        <div
          class="org-dept-node"
          :class="{ selected: sameId(selectedDeptId, node.id), expanded: isExpanded(node.id), virtual: isVirtualRoot(node.id) }"
          :data-org-node-id="String(node.id)"
        >
          <span class="org-link-port left" />
          <button
            type="button"
            class="org-dept-main"
            :aria-expanded="isExpanded(node.id)"
            @click="emit('toggle', node.id)"
          >
            <span class="org-node-toggle">
              <ChevronDown v-if="isExpanded(node.id)" :size="16" />
              <ChevronRight v-else :size="16" />
            </span>
            <span class="org-node-main">
              <strong>{{ node.deptName }}</strong>
            </span>
            <span class="org-node-count"><Users :size="14" />{{ nodeUserCount(node) }}</span>
          </button>
          <span class="org-node-actions">
            <button type="button" class="org-action-button" title="新增部门" @click.stop="emit('addDepartment', node)">
              <Plus :size="14" />
            </button>
            <button v-if="!isVirtualRoot(node.id)" type="button" class="org-action-button danger" title="删除部门" @click.stop="emit('deleteDepartment', node)">
              <Trash2 :size="14" />
            </button>
          </span>
          <span v-if="hasVisibleBranch(node)" class="org-link-port right" />
        </div>

        <div v-if="hasVisibleBranch(node)" class="org-children">
          <div v-if="hasVisiblePeople(node)" class="org-people-branch">
            <button
              v-for="user in deptUsers(node.id)"
              :key="String(user.userId)"
              type="button"
              class="org-employee-node"
              :class="{ selected: sameId(selectedUserId, user.userId) }"
              :data-org-node-id="employeeNodeId(node.id, user.userId)"
              :data-org-user-id="String(user.userId)"
              @click="emit('selectUser', user)"
            >
              <span class="org-link-port left" />
              <strong>{{ user.realName }}</strong>
              <span class="org-employee-meta">
                <span>{{ user.positionName ?? '-' }}</span>
                <span>{{ statusLabel(user.employmentStatus ?? '') }}</span>
              </span>
            </button>
          </div>
          <OrgMindTree
            v-if="hasVisibleChildren(node)"
            :nodes="node.children ?? []"
            :users-by-dept="usersByDept"
            :expanded-dept-ids="expandedDeptIds"
            :selected-user-id="selectedUserId"
            :selected-dept-id="selectedDeptId"
            :virtual-root-id="virtualRootId"
            @toggle="emit('toggle', $event)"
            @select-user="emit('selectUser', $event)"
            @add-department="emit('addDepartment', $event)"
            @delete-department="emit('deleteDepartment', $event)"
          />
        </div>
      </div>
    </li>
  </ul>
</template>

<style scoped>
.org-tree {
  --org-node-width: 340px;
  --org-branch-gap: 58px;
  --org-line-y: 34px;
  min-width: max-content;
  display: grid;
  gap: var(--space-base);
  margin: 0;
  padding: 0;
  list-style: none;
}

.org-tree-item {
  position: relative;
}

.org-branch {
  position: relative;
  display: grid;
  grid-template-columns: var(--org-node-width) max-content;
  align-items: start;
  column-gap: var(--org-branch-gap);
}

.org-children { position: relative; }

.org-people-branch {
  display: grid;
  gap: var(--space-sm);
  margin-bottom: var(--space-base);
}

.org-dept-node {
  position: relative;
  width: 100%;
  min-height: 66px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-sm);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel-strong);
  color: var(--ink);
  padding: var(--space-sm);
  text-align: left;
  box-shadow: var(--shadow-sm);
}

.org-link-port {
  position: absolute;
  top: 33px;
  z-index: 2;
  width: 9px;
  height: 9px;
  border: 2px solid var(--green);
  border-radius: var(--radius-circle);
  background: var(--panel-strong);
  box-shadow: 0 0 0 3px var(--org-link-port-ring);
}

.org-link-port.left { left: -5px; }
.org-link-port.right { right: -5px; }

.org-dept-node:hover,
.org-dept-node.selected,
.org-dept-node.expanded {
  border-color: var(--green);
  background: var(--hover-bg);
}

.org-dept-node.virtual {
  background: var(--hover-bg);
}

.org-dept-main {
  min-width: 0;
  min-height: 40px;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-sm);
  border: 0;
  background: transparent;
  color: inherit;
  padding: 0;
  text-align: left;
}

.org-node-toggle,
.org-node-count,
.org-node-actions,
.org-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.org-node-main {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.org-node-main strong,
.org-employee-node strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-node-main span,
.org-employee-meta {
  color: var(--muted);
  font-size: var(--text-sm);
}

.org-node-count {
  gap: 4px;
  color: var(--green);
  font-weight: 700;
}

.org-node-actions {
  gap: 4px;
}

.org-action-button {
  width: 28px;
  height: 28px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--muted);
}

.org-action-button:hover {
  border-color: var(--green);
  color: var(--green);
  background: var(--pill-bg);
}

.org-action-button.danger:hover {
  border-color: var(--red);
  color: var(--red);
}

.org-employee-node {
  position: relative;
  min-height: 58px;
  width: var(--org-node-width);
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 2px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--ink);
  padding: var(--space-sm) var(--space-md);
  text-align: left;
}

.org-employee-meta {
  min-width: 0;
  display: flex;
  gap: var(--space-sm);
}

.org-employee-meta span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-employee-node:hover,
.org-employee-node.selected {
  border-color: var(--green);
  background: var(--pill-bg);
}

.org-empty-node {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

@media (max-width: 900px) {
  .org-tree {
    --org-node-width: 280px;
    --org-branch-gap: 42px;
  }
}
</style>
