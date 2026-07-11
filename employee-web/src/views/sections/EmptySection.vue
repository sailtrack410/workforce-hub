<script setup lang="ts">
import { ChevronLeft } from 'lucide-vue-next';
import { useEmptySectionContext } from '../employee-shell-context';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';

const {
  currentMeta,
  businessEmptyTitle,
  backToList,
} = useEmptySectionContext();
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">{{ currentMeta.title }}</h2>
      </div>
    </div>
  </header>
  <section class="content-stack">
    <AppEmptyState :title="businessEmptyTitle" />
  </section>
</div>
</template>
