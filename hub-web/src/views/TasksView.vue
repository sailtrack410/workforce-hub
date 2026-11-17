<script setup lang="ts">
/**
 * 团队任务：按负责人 × 当前周的时间泳道，任务为横向色条；支持列表视图、新建与详情弹层。
 */
import { computed, onMounted, ref } from 'vue';
import { CalendarPlus, LayoutList, Rows3 } from 'lucide-vue-next';
import { orgApi, teamTaskApi } from '@/api';
import type { OrgUserSummaryResponse, TeamTaskResponse } from '@/api/types';
import { useToast } from '@/composables/useToast';
import { useSessionStore } from '@/stores/session';
import HubButton from '@/components/base/HubButton.vue';
import HubDialog from '@/components/base/HubDialog.vue';
import HubEmpty from '@/components/base/HubEmpty.vue';
import HubToast from '@/components/base/HubToast.vue';

const session = useSessionStore();
const { message: toastMessage, show: showToast } = useToast();

const tasks = ref<TeamTaskResponse[]>([]);
const colleagues = ref<OrgUserSummaryResponse[]>([]);
const loading = ref(true);
const loadFailed = ref(false);
const filter = ref<'all' | 'mine' | 'created' | 'done'>('all');
const viewMode = ref<'lane' | 'list'>('lane');

const tabs = [
  { key: 'all', label: '全部任务' },
  { key: 'mine', label: '我负责的' },
  { key: 'created', label: '我创建的' },
  { key: 'done', label: '已完成' },
] as const;

/* 当前周（周一到周日） */
const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日'];
const monday = computed(() => {
  const d = new Date();
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
  d.setHours(0, 0, 0, 0);
  return d;
});
const weekDays = computed(() => Array.from({ length: 7 }, (_, i) => {
  const d = new Date(monday.value);
  d.setDate(d.getDate() + i);
  return {
    date: d,
    iso: toIso(d),
    label: `${d.getDate()} ${WEEK_LABELS[i]}`,
  };
}));
const todayIso = toIso(new Date());

function toIso(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

async function load() {
  loading.value = true;
  loadFailed.value = false;
  try {
    tasks.value = await teamTaskApi.list();
  } catch (error) {
    loadFailed.value = true;
    showToast(error instanceof Error ? error.message : '任务加载失败');
  } finally {
    loading.value = false;
  }
}

onMounted(async () => {
  await Promise.all([load(), session.user ? Promise.resolve() : session.loadCurrentUser()]);
  colleagues.value = await orgApi.colleagues().catch(() => []);
});

const assigneeName = (t: TeamTaskResponse) => t.assigneeName || colleagues.value.find((c) => String(c.userId) === String(t.assigneeId))?.realName || `成员 ${t.assigneeId}`;

const visibleTasks = computed(() => tasks.value.filter((t) => {
  if (filter.value === 'done') return t.status === 'DONE';
  if (filter.value === 'mine') return String(t.assigneeId) === String(session.user?.userId);
  if (filter.value === 'created') return String(t.creatorId ?? '') === String(session.user?.userId);
  return true;
}));

const STATUS_LABEL: Record<string, string> = { TODO: '待开始', IN_PROGRESS: '进行中', IN_REVIEW: '评审中', DONE: '已完成' };
const STATUS_CLASS: Record<string, string> = { TODO: 'todo', IN_PROGRESS: 'progress', IN_REVIEW: 'review', DONE: 'done' };

/* 泳道数据：每位负责人一行，任务按日期切到本周列内，纵向按重叠分行 */
interface LaneBar {
  task: TeamTaskResponse;
  startCol: number;
  span: number;
  lane: number;
}
interface LaneRow {
  name: string;
  bars: LaneBar[];
  maxLane: number;
}
const lanes = computed<LaneRow[]>(() => {
  const map = new Map<string, { name: string; items: TeamTaskResponse[] }>();
  for (const t of visibleTasks.value) {
    const key = String(t.assigneeId);
    if (!map.has(key)) map.set(key, { name: assigneeName(t), items: [] });
    map.get(key)!.items.push(t);
  }
  const rows: LaneRow[] = [];
  for (const { name, items } of map.values()) {
    const ends: number[] = [];
    const bars: LaneBar[] = [];
    for (const t of items.slice().sort((a, b) => a.startDate.localeCompare(b.startDate))) {
      const startCol = weekDays.value.findIndex((d) => d.iso >= t.startDate);
      const endCol = weekDays.value.findIndex((d) => d.iso >= t.endDate);
      const col = startCol < 0 ? (t.endDate >= weekDays.value[6].iso ? 0 : -1) : startCol;
      if (col < 0) continue;
      const span = Math.max(1, Math.min(7 - col, (endCol < 0 ? 6 : endCol) - col + 1));
      const lane = ends.findIndex((end) => end < col);
      const laneIdx = lane < 0 ? ends.length : lane;
      ends[laneIdx] = col + span;
      bars.push({ task: t, startCol: col, span, lane: laneIdx });
    }
    rows.push({ name, bars, maxLane: Math.max(1, ends.length) });
  }
  return rows;
});

/* 新建弹层 */
const showCreate = ref(false);
const creating = ref(false);
const form = ref({ title: '', description: '', assigneeId: '', startDate: todayIso, endDate: todayIso });

function openCreate() {
  form.value = { title: '', description: '', assigneeId: colleagues.value[0] ? String(colleagues.value[0].userId) : '', startDate: todayIso, endDate: todayIso };
  showCreate.value = true;
}

async function submitCreate() {
  if (!form.value.title.trim()) { showToast('请填写任务标题'); return; }
  if (!form.value.assigneeId) { showToast('请选择负责人'); return; }
  creating.value = true;
  try {
    await teamTaskApi.create({
      title: form.value.title.trim(),
      description: form.value.description.trim(),
      assigneeId: form.value.assigneeId,
      startDate: form.value.startDate,
      endDate: form.value.endDate,
    });
    showToast('任务已创建');
    showCreate.value = false;
    await load();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '创建失败');
  } finally {
    creating.value = false;
  }
}

/* 详情弹层 */
const detail = ref<TeamTaskResponse | null>(null);
const confirmingDelete = ref(false);

async function completeTask() {
  if (!detail.value) return;
  try {
    await teamTaskApi.updateStatus(detail.value.taskId, 'DONE');
    showToast('任务已完成');
    detail.value = null;
    await load();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '操作失败');
  }
}

async function deleteTask() {
  if (!detail.value) return;
  try {
    await teamTaskApi.remove(detail.value.taskId);
    showToast('任务已删除');
    detail.value = null;
    confirmingDelete.value = false;
    await load();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '删除失败');
  }
}
</script>

<template>
  <div class="tk-page">
    <header class="tk-head">
      <div><h1>团队任务</h1><p>按负责人和时间协调工作</p></div>
      <span class="tk-head-spacer" />
      <button type="button" class="tk-btn-primary" @click="openCreate"><CalendarPlus :size="16" />新建团队任务</button>
    </header>

    <div class="tk-toolbar">
      <div class="tk-tabs">
        <button v-for="t in tabs" :key="t.key" type="button" :class="{ active: filter === t.key }" @click="filter = t.key">{{ t.label }}</button>
      </div>
      <div class="tk-view-switch">
        <button type="button" :class="{ active: viewMode === 'lane' }" @click="viewMode = 'lane'"><Rows3 :size="14" />泳道视图</button>
        <button type="button" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'"><LayoutList :size="14" />列表视图</button>
      </div>
    </div>

    <section v-if="viewMode === 'lane'" class="tk-card">
      <div class="tk-lane">
        <div class="tk-lane-head-cell" />
        <div class="tk-lane-header">
          <div class="tk-lane-name-cell" />
          <div v-for="d in weekDays" :key="d.iso" class="tk-lane-head-cell" :class="{ today: d.iso === todayIso }">{{ d.label }}</div>
        </div>
        <div v-for="row in lanes" :key="row.name" class="tk-lane-row">
          <div class="tk-lane-name-cell">{{ row.name }}</div>
          <div class="tk-lane-area" :style="{ height: `${row.maxLane * 30 + 12}px` }">
            <div v-for="d in weekDays" :key="d.iso" class="tk-lane-day" :class="{ today: d.iso === todayIso }" />
            <button
              v-for="bar in row.bars" :key="String(bar.task.taskId)" type="button"
              :class="['tk-task-bar', STATUS_CLASS[bar.task.status]]"
              :style="{ left: `${bar.startCol / 7 * 100}%`, width: `${bar.span / 7 * 100}%`, top: `${bar.lane * 30 + 6}px` }"
              @click="detail = bar.task"
            >
              <strong>{{ bar.task.title }}</strong>
              <small>{{ bar.task.startDate.slice(5) }} ~ {{ bar.task.endDate.slice(5) }}</small>
            </button>
          </div>
        </div>
        <HubEmpty v-if="!lanes.length && !loading" :title="loadFailed ? '任务加载失败' : '本周暂无任务'" :note="loadFailed ? '请稍后重试' : '点击右上角新建团队任务'" />
      </div>
    </section>

    <section v-else class="tk-card">
      <table class="tk-table">
        <thead><tr><th>名称</th><th>负责人</th><th>起止</th><th>状态</th></tr></thead>
        <tbody>
          <tr v-for="t in visibleTasks" :key="String(t.taskId)" @click="detail = t">
            <td>{{ t.title }}</td>
            <td>{{ assigneeName(t) }}</td>
            <td>{{ t.startDate }} ~ {{ t.endDate }}</td>
            <td><em :class="['tk-status', STATUS_CLASS[t.status]]">{{ STATUS_LABEL[t.status] }}</em></td>
          </tr>
        </tbody>
      </table>
      <HubEmpty v-if="!visibleTasks.length && !loading" :title="loadFailed ? '任务加载失败' : '暂无任务'" />
    </section>

    <HubDialog v-if="showCreate" @close="showCreate = false">
      <form class="tk-form" @submit.prevent="submitCreate">
        <h2>新建团队任务</h2>
        <label>标题<input v-model="form.title" placeholder="任务标题" /></label>
        <label>说明<textarea v-model="form.description" placeholder="补充说明（可选）" /></label>
        <label>负责人
          <select v-model="form.assigneeId">
            <option v-for="c in colleagues" :key="String(c.userId)" :value="String(c.userId)">{{ c.realName }}</option>
          </select>
        </label>
        <div class="tk-form-row">
          <label>开始日期<input v-model="form.startDate" type="date" /></label>
          <label>结束日期<input v-model="form.endDate" type="date" /></label>
        </div>
        <div class="tk-form-actions">
          <HubButton type="button" @click="showCreate = false">取消</HubButton>
          <HubButton variant="primary" type="submit" :disabled="creating">创建</HubButton>
        </div>
      </form>
    </HubDialog>

    <HubDialog v-if="detail" @close="detail = null; confirmingDelete = false">
      <div class="tk-detail">
        <h2>{{ detail.title }}</h2>
        <dl>
          <div><dt>状态</dt><dd><em :class="['tk-status', STATUS_CLASS[detail.status]]">{{ STATUS_LABEL[detail.status] }}</em></dd></div>
          <div><dt>负责人</dt><dd>{{ assigneeName(detail) }}</dd></div>
          <div><dt>起止日期</dt><dd>{{ detail.startDate }} ~ {{ detail.endDate }}</dd></div>
          <div v-if="detail.description"><dt>说明</dt><dd>{{ detail.description }}</dd></div>
        </dl>
        <div v-if="confirmingDelete" class="tk-confirm">
          <span>确认删除该任务？</span>
          <button type="button" @click="deleteTask">删除</button>
        </div>
        <div class="tk-detail-actions">
          <HubButton variant="danger" @click="confirmingDelete = true">删除任务</HubButton>
          <HubButton variant="primary" :disabled="detail.status === 'DONE'" @click="completeTask">完成任务</HubButton>
        </div>
      </div>
    </HubDialog>

    <HubToast :message="toastMessage" />
  </div>
</template>

<style scoped src="../styles/tasks.css" />
