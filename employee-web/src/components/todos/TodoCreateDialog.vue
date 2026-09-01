<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Plus, X } from 'lucide-vue-next';
import type { TodoRecord, TodoTagColor } from '@/stores/shell';
import { useShellStore } from '@/stores/shell';
import AppButton from '../ui/AppButton.vue';
import AppDialog from '../ui/AppDialog.vue';

const props = defineProps<{
  open: boolean;
  // 传入则为编辑模式，否则为新建
  todo?: TodoRecord | null;
}>();

const emit = defineEmits<{
  close: [];
}>();

const shell = useShellStore();
const tagOptions: Array<{ name: string; color: TodoTagColor; label: string }> = [
  { name: '工作', color: 'blue', label: '蓝' },
  { name: '个人', color: 'green', label: '绿' },
  { name: '紧急', color: 'rose', label: '红' },
  { name: '学习', color: 'amber', label: '橙' },
  { name: '协作', color: 'teal', label: '青' },
  { name: '其他', color: 'gray', label: '灰' },
];

const isEdit = computed(() => Boolean(props.todo?.id));

const formError = ref('');
const draft = ref({
  title: '',
  note: '',
  startAt: toDateTimeMinuteKey(new Date()),
  endAt: toDateTimeMinuteKey(addMinutes(new Date(), 60)),
  tagName: tagOptions[0].name,
  tagColor: tagOptions[0].color,
});

// 打开弹窗时：编辑模式回填，新建模式重置
watch(() => props.open, (open) => {
  if (!open) return;
  formError.value = '';
  if (props.todo) {
    draft.value = {
      title: props.todo.title,
      note: props.todo.note || '',
      startAt: props.todo.startAt || toDateTimeMinuteKey(new Date()),
      endAt: props.todo.endAt || toDateTimeMinuteKey(addMinutes(new Date(), 60)),
      tagName: props.todo.tagName || tagOptions[0].name,
      tagColor: props.todo.tagColor || tagOptions[0].color,
    };
  } else {
    draft.value = {
      title: '',
      note: '',
      startAt: toDateTimeMinuteKey(new Date()),
      endAt: toDateTimeMinuteKey(addMinutes(new Date(), 60)),
      tagName: tagOptions[0].name,
      tagColor: tagOptions[0].color,
    };
  }
});

function toDateKey(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function toDateTimeMinuteKey(date: Date) {
  return `${toDateKey(date)}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
}

function addMinutes(date: Date, amount: number) {
  const result = new Date(date);
  result.setMinutes(result.getMinutes() + amount);
  return result;
}

function selectTag(option: { name: string; color: TodoTagColor }) {
  draft.value.tagName = option.name;
  draft.value.tagColor = option.color;
}

function closeCreate() {
  formError.value = '';
  emit('close');
}

function isMinuteDateTime(value: string) {
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value);
}

function submitTodo() {
  const title = draft.value.title.trim();
  const note = draft.value.note.trim();
  const tagName = draft.value.tagName.trim();
  formError.value = '';
  if (!title) {
    formError.value = '标题不能为空';
    return;
  }
  if (!draft.value.startAt || !draft.value.endAt) {
    formError.value = '开始和结束时间不能为空';
    return;
  }
  if (!isMinuteDateTime(draft.value.startAt) || !isMinuteDateTime(draft.value.endAt)) {
    formError.value = '时间格式不正确';
    return;
  }
  if (draft.value.endAt < draft.value.startAt) {
    formError.value = '结束时间不能早于开始时间';
    return;
  }
  if (!tagName) {
    formError.value = '标签不能为空';
    return;
  }
  if (isEdit.value && props.todo) {
    shell.updateTodoTask(props.todo.id, {
      title,
      note,
      startAt: draft.value.startAt,
      endAt: draft.value.endAt,
      tagName,
      tagColor: draft.value.tagColor,
    });
  } else {
    shell.addTodoTask({
      title,
      note,
      startAt: draft.value.startAt,
      endAt: draft.value.endAt,
      tagName,
      tagColor: draft.value.tagColor,
    });
  }
  draft.value.title = '';
  draft.value.note = '';
  closeCreate();
}
</script>

<template>
  <AppDialog v-if="props.open">
    <form class="conversation-dialog todo-create-dialog" @submit.prevent="submitTodo">
      <header class="dialog-head dialog-head-row">
        <div>
          <h2>{{ isEdit ? '编辑待办' : '添加待办' }}</h2>
        </div>
        <button class="field-icon-button" type="button" aria-label="关闭" @click="closeCreate">
          <X :size="15" />
        </button>
      </header>

      <label class="security-field">
        <span>标题</span>
        <input v-model="draft.title" class="ui-inline-input todo-create-title-input" maxlength="60" placeholder="待办标题" />
      </label>

      <div class="todo-date-row">
        <label class="security-field">
          <span>开始</span>
          <input v-model="draft.startAt" class="ui-inline-input" type="datetime-local" step="60" />
        </label>
        <label class="security-field">
          <span>结束</span>
          <input v-model="draft.endAt" class="ui-inline-input" type="datetime-local" step="60" />
        </label>
      </div>

      <label class="security-field">
        <span>备注</span>
        <textarea v-model="draft.note" class="ui-textarea compact-textarea todo-create-textarea" maxlength="300" rows="3" placeholder="备注" />
      </label>

      <div class="security-field">
        <span>标签</span>
        <div class="todo-color-picker">
          <button
            v-for="option in tagOptions"
            :key="`${option.name}-${option.color}`"
            type="button"
            :class="['todo-color-choice', `todo-color-${option.color}`, { active: draft.tagColor === option.color && draft.tagName === option.name }]"
            @click="selectTag(option)"
          >
            <i />
            {{ option.name }}
          </button>
        </div>
        <input v-model="draft.tagName" class="ui-inline-input" maxlength="12" placeholder="标签名称" />
      </div>

      <p v-if="formError" class="dialog-error">{{ formError }}</p>

      <div class="dialog-actions">
        <AppButton variant="ghost" type="button" @click="closeCreate">取消</AppButton>
        <AppButton variant="primary" type="submit">
          <Plus :size="15" />
          {{ isEdit ? '保存' : '添加' }}
        </AppButton>
      </div>
    </form>
  </AppDialog>
</template>

<style scoped>
.todo-create-dialog {
  gap: var(--space-16);
}

.todo-create-title-input {
  font-size: var(--text-body-md);
  font-weight: 600;
}

.todo-create-textarea {
  min-height: 6rem;
}
</style>
