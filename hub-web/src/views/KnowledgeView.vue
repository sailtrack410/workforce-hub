<script setup lang="ts">
/**
 * 知识库：分类侧栏（纯前端 tab）+ 文档卡片列表。
 * 后端暂无员工端 ragflow 接口，列表显示空态，交互按钮 toast 占位。
 */
import { computed, ref } from 'vue';
import { BookOpen, Clock, Star, Building2, Users, Plus, Search } from 'lucide-vue-next';
import { useToast } from '@/composables/useToast';
import HubButton from '@/components/base/HubButton.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';
import HubToast from '@/components/base/HubToast.vue';

const { message: toastMessage, show: showToast } = useToast();

const keyword = ref('');
const category = ref('recent');

const categories = [
  { key: 'recent', label: '最近使用', icon: Clock },
  { key: 'favorite', label: '我的收藏', icon: Star },
  { key: 'company', label: '企业知识', icon: Building2 },
  { key: 'department', label: '部门空间', icon: Users },
] as const;

// 空态驱动：无员工端接口，文档列表恒为空
const documents = computed(() => {
  void keyword.value;
  void category.value;
  return [] as Array<{ title: string; summary: string; updatedAt: string; source: string }>;
});

const CATEGORY_LABEL: Record<string, string> = Object.fromEntries(categories.map((c) => [c.key, c.label]));

function comingSoon(action: string) {
  showToast(`功能开发中：${action}`);
}
function detail(title: string) {
  showToast(`功能开发中：暂无知识库接口，无法打开「${title}」`);
}
</script>

<template>
  <div class="kb-page">
    <header class="kb-head">
      <div><h1>知识库</h1><p>企业知识与部门文档的统一入口</p></div>
      <span class="kb-head-spacer" />
      <button type="button" class="kb-btn-primary" @click="comingSoon('新建文档')"><Plus :size="16" />新建文档</button>
    </header>

    <div class="kb-toolbar">
      <div class="kb-search">
        <Search :size="14" />
        <input v-model="keyword" placeholder="搜索知识文档" />
      </div>
    </div>

    <div class="kb-body">
      <aside class="kb-side">
        <button
          v-for="c in categories" :key="c.key" type="button"
          :class="{ active: category === c.key }"
          @click="category = c.key"
        >
          <component :is="c.icon" :size="15" />
          {{ c.label }}
        </button>
      </aside>

      <section class="kb-card">
        <template v-if="documents.length">
          <button v-for="d in documents" :key="d.title" type="button" class="kb-doc" @click="detail(d.title)">
            <strong>{{ d.title }}</strong>
            <small>{{ d.summary }}</small>
            <span>{{ CATEGORY_LABEL[category] }} · {{ d.updatedAt }} · {{ d.source }}</span>
          </button>
        </template>
        <HubEmpty
          v-else
          :icon="BookOpen"
          :title="keyword.trim() ? '未找到匹配文档' : '暂无知识库内容'"
          :note="keyword.trim() ? '换个关键词试试' : '知识库接口尚未开放，敬请期待'"
        />
      </section>
    </div>

    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped src="../styles/knowledge.css" />
