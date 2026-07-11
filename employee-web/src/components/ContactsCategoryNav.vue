<script setup lang="ts">
import {
  ChevronDown,
  ChevronRight,
  UsersRound,
  UserPlus,
  Mail,
  Building2,
  FolderTree,
  Folder,
  Sparkles,
  Monitor,
  BriefcaseBusiness,
} from 'lucide-vue-next';
import type { ContactCategoryItem } from './contact-category';

defineOptions({ name: 'ContactsCategoryNav' });

const props = defineProps<{
  items: ContactCategoryItem[];
  activeId: string;
  expandedIds: Set<string>;
}>();

const emit = defineEmits<{
  select: [id: string];
  toggle: [id: string];
}>();

const iconMap: Record<string, typeof UsersRound> = {
  users: UsersRound,
  userPlus: UserPlus,
  mail: Mail,
  building: Building2,
  sitemap: FolderTree,
  folder: Folder,
  sparkles: Sparkles,
  monitor: Monitor,
  briefcase: BriefcaseBusiness,
};

function isExpanded(id: string) {
  return props.expandedIds.has(id);
}

function handleToggle(item: ContactCategoryItem, event: Event) {
  event.stopPropagation();
  emit('toggle', item.id);
}

function handleSelect(item: ContactCategoryItem) {
  emit('select', item.id);
}

function hasChildren(item: ContactCategoryItem) {
  return Boolean(item.children?.length);
}
</script>

<template>
  <ul class="contacts-category-nav">
    <li v-for="item in items" :key="item.id">
      <div
        class="contacts-category-item"
        :class="{ active: activeId === item.id, expandable: hasChildren(item) }"
        role="button"
        tabindex="0"
        :aria-current="activeId === item.id ? 'page' : undefined"
        @click="handleSelect(item)"
        @keydown.enter.prevent="handleSelect(item)"
        @keydown.space.prevent="handleSelect(item)"
      >
        <button
          v-if="hasChildren(item)"
          type="button"
          class="contacts-category-toggle"
          :aria-label="`${isExpanded(item.id) ? '收起' : '展开'}${item.label}`"
          @click="handleToggle(item, $event)"
        >
          <component :is="isExpanded(item.id) ? ChevronDown : ChevronRight" :size="14" />
        </button>
        <span v-else class="contacts-category-spacer" />
        <span class="contacts-category-icon">
          <component :is="iconMap[item.icon ?? 'folder'] ?? Folder" :size="16" />
        </span>
        <span class="contacts-category-label">{{ item.label }}</span>
        <span v-if="item.badge" class="contacts-category-badge">{{ item.badge }}</span>
      </div>
      <ContactsCategoryNav
        v-if="hasChildren(item) && isExpanded(item.id)"
        :items="item.children ?? []"
        :active-id="activeId"
        :expanded-ids="expandedIds"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
      />
    </li>
  </ul>
</template>
