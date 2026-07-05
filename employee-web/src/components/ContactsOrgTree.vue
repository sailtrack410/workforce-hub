<script setup lang="ts">
import { ref, watch } from 'vue';
import { ChevronDown, ChevronRight, Folder, GripVertical, UserRound } from 'lucide-vue-next';
import type { EntityId, ContactRecord, OrgDepartmentTreeNodeResponse } from '@/stores/shell';
import type { ContactTreeNode } from './contact-category';

defineOptions({ name: 'ContactsOrgTree' });

const props = defineProps<{
  nodes: ContactTreeNode[];
  activeDeptId?: string | null;
}>();

const emit = defineEmits<{
  select: [node: OrgDepartmentTreeNodeResponse];
  contact: [contact: ContactRecord];
}>();

const expandedIds = ref<Set<string>>(new Set());

function isExpanded(id: EntityId) {
  return expandedIds.value.has(String(id));
}

function toggleExpand(id: EntityId, event: Event) {
  event.stopPropagation();
  const key = String(id);
  const next = new Set(expandedIds.value);
  if (next.has(key)) next.delete(key);
  else next.add(key);
  expandedIds.value = next;
}

function selectNode(node: ContactTreeNode) {
  if (node.kind === 'contact') {
    if (node.contact) emit('contact', node.contact);
    return;
  }
  if (!node.dept) return;
  const key = String(node.dept.id);
  if (node.children?.length) {
    const next = new Set(expandedIds.value);
    next.add(key);
    expandedIds.value = next;
  }
  emit('select', node.dept);
}

watch(() => props.nodes, (nodes) => {
  const ids = new Set<string>();
  function collect(rows: ContactTreeNode[]) {
    rows.forEach((node) => {
      if (node.kind === 'dept' && node.dept?.id != null && node.children.length) {
        ids.add(String(node.dept.id));
        collect(node.children);
      }
    });
  }
  collect(nodes);
  expandedIds.value = ids;
}, { immediate: true });
</script>

<template>
  <ul class="org-tree-level">
    <li
      v-for="node in nodes"
      :key="String(node.id)"
      class="org-tree-node-wrap"
      :class="{ 'has-open-children': node.kind === 'dept' && node.children?.length && isExpanded(node.dept?.id ?? node.id) }"
    >
      <div class="org-tree-branch">
        <button
          v-if="node.kind === 'dept' && node.children?.length"
          type="button"
          class="org-tree-expand"
          @click="toggleExpand(node.dept?.id ?? node.id, $event)"
        >
          <component :is="isExpanded(node.dept?.id ?? node.id) ? ChevronDown : ChevronRight" :size="13" />
        </button>
        <span v-else class="org-tree-expand-spacer" />
        <button
          type="button"
          class="org-tree-card"
          :class="{ active: node.kind === 'dept' && activeDeptId === String(node.dept?.id), 'is-contact': node.kind === 'contact' }"
          @click="selectNode(node)"
        >
          <component :is="node.kind === 'contact' ? UserRound : Folder" :size="16" />
          <span>{{ node.label }}</span>
          <small v-if="node.subLabel">{{ node.subLabel }}</small>
        </button>
        <div class="org-tree-drag-handle">
          <GripVertical :size="12" />
        </div>
      </div>
      <div v-if="node.kind === 'dept' && node.children?.length && isExpanded(node.dept?.id ?? node.id)" class="org-tree-children">
        <ContactsOrgTree
          :nodes="node.children"
          :active-dept-id="activeDeptId"
          @select="$emit('select', $event)"
          @contact="$emit('contact', $event)"
        />
      </div>
    </li>
  </ul>
</template>
