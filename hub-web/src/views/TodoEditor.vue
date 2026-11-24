<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { CalendarDays, Bell, Tag } from 'lucide-vue-next';
import HubButton from '@/components/base/HubButton.vue';
import type { PersonalTodoResponse } from '@/api/types';

export interface TodoDraft {
  title: string; category: PersonalTodoResponse['category']; startAt: string; dueAt: string;
  note: string; allDay: boolean; reminderMinutes: number | null;
}
const model = defineModel<TodoDraft>({ required: true });
defineProps<{ saving: boolean; error: string; editing?: boolean }>();
const emit = defineEmits<{ save: []; cancel: [] }>();
const titleInput = ref<HTMLInputElement | null>(null);
onMounted(() => titleInput.value?.focus());
function setAllDay(event: Event) {
  model.value.allDay = (event.target as HTMLInputElement).checked;
  for (const field of ['startAt', 'dueAt'] as const) {
    if (model.value[field]) model.value[field] = model.value.allDay ? model.value[field].slice(0, 10) : model.value[field].slice(0, 10) + 'T09:00';
  }
}
</script>

<template>
  <form class="td-editor" @submit.prevent="emit('save')" @keydown.esc.stop.prevent="emit('cancel')">
    <fieldset :disabled="saving">
      <label class="td-editor-name"><span class="td-sr-only">待办名称</span><input ref="titleInput" v-model="model.title" aria-label="待办名称" placeholder="待办名称" maxlength="128" required /></label>
      <label class="td-editor-note"><span class="td-sr-only">备注</span><textarea v-model="model.note" aria-label="备注" placeholder="备注" maxlength="1024" rows="1" /></label>
      <div class="td-editor-chips">
        <details class="td-date-picker">
          <summary :class="{ scheduled: model.startAt || model.dueAt }"><CalendarDays :size="14" />{{ model.startAt || model.dueAt ? `${(model.startAt || model.dueAt).slice(5, 10)}${model.allDay ? ' 全天' : ' ' + (model.startAt || model.dueAt).slice(11, 16)}` : '日期与时间' }}</summary>
          <div class="td-date-popover">
            <label class="td-all-day-toggle"><input type="checkbox" :checked="model.allDay" @change="setAllDay" />全天</label>
            <label>开始时间<input v-model="model.startAt" :type="model.allDay ? 'date' : 'datetime-local'" aria-label="开始时间" /></label>
            <label>截止时间<input v-model="model.dueAt" :type="model.allDay ? 'date' : 'datetime-local'" aria-label="截止时间" :min="model.startAt || undefined" /></label>
            <HubButton size="sm" variant="ghost" @click="model.startAt = ''; model.dueAt = ''; model.allDay = false; model.reminderMinutes = null">清除时间</HubButton>
          </div>
        </details>
        <label class="td-editor-select"><Tag :size="14" /><select v-model="model.category" aria-label="待办分类"><option value="TASK">任务</option><option value="MEETING">会议</option><option value="REMINDER">提醒</option><option value="APPROVAL">审批</option></select></label>
        <label class="td-editor-select"><Bell :size="14" /><select v-model="model.reminderMinutes" aria-label="提前提醒"><option :value="null">不提醒</option><option :value="0">准时提醒</option><option v-for="minute in [5, 10, 15, 30, 60, 1440]" :key="minute" :value="minute">{{ minute === 1440 ? '提前 1 天' : `提前 ${minute} 分钟` }}</option></select></label>
      </div>
    </fieldset>
    <p v-if="error" class="td-field-error" role="alert">{{ error }}</p>
    <footer class="td-editor-actions"><HubButton size="sm" variant="ghost" :disabled="saving" @click="emit('cancel')">取消</HubButton><HubButton size="sm" variant="primary" type="submit" :disabled="saving || !model.title.trim()">{{ saving ? '保存中…' : editing ? '保存' : '添加待办' }}</HubButton></footer>
  </form>
</template>

<style scoped>
.td-editor { border: 1px solid var(--hw-control-border); border-radius: var(--hw-radius-panel); padding: 14px; background: var(--hw-card); }
fieldset { display: flex; flex-direction: column; gap: 6px; border: 0; min-width: 0; }
input, textarea, select { color: var(--hw-ink); background: transparent; font-size: 12px; }
.td-editor-name input { width: 100%; min-width: 0; border: 0; font-size: 15px; font-weight: 500; line-height: 24px; }
.td-editor-note textarea { width: 100%; min-height: 24px; max-height: 120px; border: 0; resize: vertical; line-height: 20px; }
.td-editor-name input:focus, .td-editor-note textarea:focus { outline: none; }
.td-editor-chips { display: flex; flex-wrap: wrap; gap: 8px; margin: 4px 0 12px; }
summary, .td-editor-select { display: flex; align-items: center; gap: 6px; min-height: 28px; padding: 0 8px; border: 1px solid var(--hw-border); border-radius: 5px; color: var(--hw-tertiary); font-size: 12px; cursor: pointer; }
summary { list-style: none; }
summary::-webkit-details-marker { display: none; }
summary.scheduled { color: var(--hw-green); background: var(--hw-green-bg); }
.td-editor-select select { min-width: 0; border: 0; max-width: 140px; }
.td-date-picker { position: relative; }
.td-date-picker[open] summary { border-color: var(--hw-primary); }
.td-date-popover { position: absolute; top: calc(100% + 6px); left: 0; z-index: 12; display: grid; gap: 12px; width: min(290px, calc(100vw - 64px)); padding: 16px; border: 1px solid var(--hw-border); border-radius: 8px; background: var(--hw-card); box-shadow: var(--hw-shadow-menu); }
.td-date-popover label { display: grid; gap: 6px; color: var(--hw-secondary); font-size: 12px; }
.td-date-popover input:not([type='checkbox']) { width: 100%; min-width: 0; border: 1px solid var(--hw-control-border); border-radius: 5px; padding: 5px; }
.td-date-popover label.td-all-day-toggle { display: flex; align-items: center; gap: 8px; }
.td-editor-actions { display: flex; justify-content: flex-end; gap: 8px; border-top: 1px solid var(--hw-border); padding-top: 10px; }
.td-field-error { color: var(--hw-red); font-size: 12px; padding-bottom: 8px; }
.td-sr-only { position: absolute; width: 1px; height: 1px; padding: 0; overflow: hidden; clip-path: inset(50%); }
</style>
