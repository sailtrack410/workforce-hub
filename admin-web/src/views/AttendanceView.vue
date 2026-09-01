<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw, Save, TriangleAlert } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type {
  AttendanceCalendarResponse,
  AttendanceDailyResultResponse,
  AttendanceGroupMemberResponse,
  AttendanceGroupResponse,
  AttendanceScheduleResponse,
  AttendanceShiftResponse,
  EntityId,
  OrgUserSummaryResponse,
} from '@/api/types';
import { attendanceResultLabel, attendanceSourceLabel, errorMessage, formatDateTime, formatId, formatMinutes, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminUserPicker from '@/components/AdminUserPicker.vue';
import AdminDialog from '@/components/AdminDialog.vue';
import AdminMetricStrip from '@/components/AdminMetricStrip.vue';

const toast = useToast();

const activeTab = ref<'shifts' | 'groups' | 'schedules' | 'results'>('shifts');
const loading = ref(false);
const submitting = ref(false);
const shiftDialogVisible = ref(false);
const calendarDialogVisible = ref(false);
const groupDialogVisible = ref(false);
const memberDialogVisible = ref(false);
const scheduleDialogVisible = ref(false);

const calendars = ref<AttendanceCalendarResponse[]>([]);
const shifts = ref<AttendanceShiftResponse[]>([]);
const groups = ref<AttendanceGroupResponse[]>([]);
const groupMembers = ref<AttendanceGroupMemberResponse[]>([]);
const schedules = ref<AttendanceScheduleResponse[]>([]);
const dailyResults = ref<AttendanceDailyResultResponse[]>([]);
const selectedGroupId = ref<EntityId | null>(null);
const selectedScheduleId = ref<EntityId | null>(null);

const today = localDate(new Date());
const firstDay = `${today.slice(0, 8)}01`;

const shiftForm = reactive({
  shiftName: '',
  startTime: '09:00:00',
  endTime: '18:00:00',
  onDutyPunchStart: '08:00:00',
  onDutyPunchEnd: '10:00:00',
  offDutyPunchStart: '17:00:00',
  offDutyPunchEnd: '19:00:00',
  status: 'ACTIVE',
});
const calendarForm = reactive({ calendarName: '', status: 'ACTIVE' });
const groupForm = reactive({
  groupName: '',
  calendarId: '',
  defaultShiftId: '',
  lateGraceMinutes: 0,
  earlyLeaveGraceMinutes: 0,
  status: 'ACTIVE',
});
const memberForm = reactive({ userId: '', effectiveFrom: today, effectiveTo: '', sourceType: 'MANUAL' });
const scheduleForm = reactive({
  userId: '',
  workDate: today,
  shiftId: '',
  calendarId: '',
  scheduleSource: 'MANUAL',
  status: 'ACTIVE',
});
const scheduleFilters = reactive({ userId: '', startDate: firstDay, endDate: today });
const resultFilters = reactive({ userId: '', startDate: firstDay, endDate: today });
const memberUserId = ref<EntityId | null>(null);
const scheduleUserId = ref<EntityId | null>(null);
const scheduleFilterUserId = ref<EntityId | null>(null);
const resultFilterUserId = ref<EntityId | null>(null);

const shiftColumns: TableColumn[] = [
  { key: 'shiftName', label: '名称' },
  { key: 'workTime', label: '班段' },
  { key: 'punchTime', label: '打卡窗口' },
  { key: 'status', label: '状态' },
];
const calendarColumns: TableColumn[] = [
  { key: 'calendarName', label: '名称' },
  { key: 'status', label: '状态' },
];
const groupColumns: TableColumn[] = [
  { key: 'groupName', label: '名称' },
  { key: 'calendarId', label: '日历' },
  { key: 'defaultShiftId', label: '默认班次' },
  { key: 'grace', label: '宽限' },
  { key: 'status', label: '状态' },
];
const memberColumns: TableColumn[] = [
  { key: 'userId', label: '员工' },
  { key: 'effectiveFrom', label: '开始日' },
  { key: 'effectiveTo', label: '结束日' },
  { key: 'sourceType', label: '来源' },
  { key: 'status', label: '状态' },
];
const scheduleColumns: TableColumn[] = [
  { key: 'userId', label: '员工' },
  { key: 'workDate', label: '日期' },
  { key: 'shiftId', label: '班次' },
  { key: 'scheduleSource', label: '来源' },
  { key: 'status', label: '状态' },
];
const resultColumns: TableColumn[] = [
  { key: 'userId', label: '员工' },
  { key: 'workDate', label: '日期' },
  { key: 'resultStatus', label: '结果' },
  { key: 'minutes', label: '分钟' },
  { key: 'exceptionFlag', label: '异常' },
  { key: 'calculatedAt', label: '计算时间' },
];

const activeCalendars = computed(() => calendars.value.filter((item) => item.status === 'ACTIVE'));
const activeShifts = computed(() => shifts.value.filter((item) => item.status === 'ACTIVE'));
const exceptionResults = computed(() => dailyResults.value.filter((item) => item.exceptionFlag));
const exceptionEmployeeCount = computed(() => new Set(exceptionResults.value.map((item) => String(item.userId))).size);
const attendanceMetrics = computed(() => [
  { label: '有效班次', value: `${activeShifts.value.length}/${shifts.value.length}` },
  { label: '考勤组', value: groups.value.length },
  { label: '当前排班', value: schedules.value.length },
  { label: '结果异常', value: exceptionResults.value.length },
]);
const exceptionRate = computed(() => dailyResults.value.length
  ? `${Math.round((exceptionResults.value.length / dailyResults.value.length) * 100)}%`
  : '0%');

function localDate(date: Date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function shiftName(shiftId?: EntityId | null) {
  return shifts.value.find((item) => String(item.shiftId) === String(shiftId))?.shiftName ?? '未命名班次';
}

function calendarName(calendarId?: EntityId | null) {
  return calendars.value.find((item) => String(item.calendarId) === String(calendarId))?.calendarName ?? '未命名日历';
}

function employeeLabel(row: { realName?: string | null; employeeNo?: string | null }) {
  return row.realName || '未命名员工';
}

function generatedCode(prefix: string, name: string) {
  const stamp = Date.now().toString(36).toUpperCase();
  const seed = encodeURIComponent(name.trim()).replace(/%/g, '').slice(0, 8).toUpperCase();
  return `${prefix}_${seed || stamp}_${stamp}`.slice(0, 64);
}

async function loadBase() {
  const [calendarRows, shiftRows, groupRows] = await Promise.all([
    adminApi.attendance.calendars(),
    adminApi.attendance.shifts(),
    adminApi.attendance.groups(),
  ]);
  calendars.value = calendarRows;
  shifts.value = shiftRows;
  groups.value = groupRows;
  if (!selectedGroupId.value && groupRows[0]) selectedGroupId.value = groupRows[0].groupId;
  if (selectedGroupId.value) {
    groupMembers.value = await adminApi.attendance.groupMembers(selectedGroupId.value);
  }
}

async function loadSchedules() {
  schedules.value = await adminApi.attendance.schedules({
    userId: scheduleFilters.userId || undefined,
    startDate: scheduleFilters.startDate,
    endDate: scheduleFilters.endDate,
  });
}

async function loadResults() {
  dailyResults.value = await adminApi.attendance.dailyResults({
    userId: resultFilters.userId || undefined,
    startDate: resultFilters.startDate,
    endDate: resultFilters.endDate,
  });
}

async function loadPage() {
  loading.value = true;
  try {
    await Promise.all([loadBase(), loadSchedules(), loadResults()]);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

async function createCalendar() {
  submitting.value = true;
  try {
    await adminApi.attendance.createCalendar({
      calendarCode: generatedCode('CAL', calendarForm.calendarName),
      calendarName: calendarForm.calendarName,
      calendarType: 'STANDARD',
      status: calendarForm.status,
    });
    calendarForm.calendarName = '';
    calendarDialogVisible.value = false;
    await loadBase();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    submitting.value = false;
  }
}

async function createShift() {
  submitting.value = true;
  try {
    await adminApi.attendance.createShift({
      ...shiftForm,
      shiftCode: generatedCode('SHIFT', shiftForm.shiftName),
    });
    shiftForm.shiftName = '';
    shiftDialogVisible.value = false;
    await loadBase();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    submitting.value = false;
  }
}

async function createGroup() {
  submitting.value = true;
  try {
    await adminApi.attendance.createGroup({
      groupCode: generatedCode('GROUP', groupForm.groupName),
      groupName: groupForm.groupName,
      calendarId: groupForm.calendarId,
      defaultShiftId: groupForm.defaultShiftId,
      lateGraceMinutes: Number(groupForm.lateGraceMinutes),
      earlyLeaveGraceMinutes: Number(groupForm.earlyLeaveGraceMinutes),
      status: groupForm.status,
    });
    groupForm.groupName = '';
    groupDialogVisible.value = false;
    await loadBase();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    submitting.value = false;
  }
}

async function selectGroup(row: AttendanceGroupResponse) {
  selectedGroupId.value = row.groupId;
  try {
    groupMembers.value = await adminApi.attendance.groupMembers(row.groupId);
  } catch (e) {
    toast.error(errorMessage(e));
  }
}

async function addGroupMember() {
  if (!selectedGroupId.value) return;
  submitting.value = true;
  try {
    await adminApi.attendance.addGroupMember(selectedGroupId.value, {
      userId: memberForm.userId,
      effectiveFrom: memberForm.effectiveFrom,
      effectiveTo: memberForm.effectiveTo || null,
      sourceType: memberForm.sourceType,
    });
    memberForm.userId = '';
    memberUserId.value = null;
    memberDialogVisible.value = false;
    groupMembers.value = await adminApi.attendance.groupMembers(selectedGroupId.value);
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    submitting.value = false;
  }
}

async function submitSchedule() {
  submitting.value = true;
  try {
    const payload = {
      shiftId: scheduleForm.shiftId,
      calendarId: scheduleForm.calendarId || null,
      scheduleSource: scheduleForm.scheduleSource,
      status: scheduleForm.status,
    };
    if (selectedScheduleId.value) {
      await adminApi.attendance.updateSchedule(selectedScheduleId.value, payload);
    } else {
      await adminApi.attendance.createSchedule({
        userId: scheduleForm.userId,
        workDate: scheduleForm.workDate,
        ...payload,
      });
    }
    clearScheduleSelection();
    scheduleDialogVisible.value = false;
    await loadSchedules();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    submitting.value = false;
  }
}

function selectSchedule(row: AttendanceScheduleResponse) {
  selectedScheduleId.value = row.scheduleId;
  scheduleForm.userId = String(row.userId);
  scheduleUserId.value = row.userId;
  scheduleForm.workDate = row.workDate;
  scheduleForm.shiftId = String(row.shiftId);
  scheduleForm.calendarId = row.calendarId == null ? '' : String(row.calendarId);
  scheduleForm.scheduleSource = row.scheduleSource;
  scheduleForm.status = row.status;
  scheduleDialogVisible.value = true;
}

function clearScheduleSelection() {
  selectedScheduleId.value = null;
  scheduleForm.userId = '';
  scheduleUserId.value = null;
  scheduleForm.workDate = today;
  scheduleForm.shiftId = '';
  scheduleForm.calendarId = '';
  scheduleForm.scheduleSource = 'MANUAL';
  scheduleForm.status = 'ACTIVE';
}

function newSchedule() {
  clearScheduleSelection();
  scheduleDialogVisible.value = true;
}

function onMemberUser(user: OrgUserSummaryResponse | null) {
  void user;
  memberForm.userId = memberUserId.value ? String(memberUserId.value) : '';
}

function onScheduleUser(user: OrgUserSummaryResponse | null) {
  void user;
  scheduleForm.userId = scheduleUserId.value ? String(scheduleUserId.value) : '';
}

function onScheduleFilterUser(user: OrgUserSummaryResponse | null) {
  void user;
  scheduleFilters.userId = scheduleFilterUserId.value ? String(scheduleFilterUserId.value) : '';
}

function onResultFilterUser(user: OrgUserSummaryResponse | null) {
  void user;
  resultFilters.userId = resultFilterUserId.value ? String(resultFilterUserId.value) : '';
}

onMounted(loadPage);
</script>

<template>
  <main class="page-grid attendance-page">
    <AdminPanel :span="12" title="考勤运行中心" subtitle="从规则、覆盖、执行到结果异常形成连续治理链路">
      <template #actions>
        <button class="secondary-button" :disabled="loading" @click="loadPage">
          <RefreshCcw :size="16" />
          <span>刷新</span>
        </button>
      </template>
      <div class="attendance-command">
        <button class="exception-focus" type="button" @click="activeTab = 'results'">
          <span>结果异常率</span>
          <strong>{{ exceptionRate }}</strong>
          <small>{{ exceptionEmployeeCount }} 名员工 · {{ exceptionResults.length }} 条异常</small>
          <span class="exception-breakout" aria-hidden="true"><TriangleAlert :size="20" /></span>
        </button>
        <AdminMetricStrip :cards="attendanceMetrics" />
      </div>
      <div class="attendance-tabs" role="tablist">
        <button :aria-selected="activeTab === 'shifts'" :class="{ active: activeTab === 'shifts' }" role="tab" type="button" @click="activeTab = 'shifts'">规则配置 <span>{{ shifts.length }}</span></button>
        <button :aria-selected="activeTab === 'groups'" :class="{ active: activeTab === 'groups' }" role="tab" type="button" @click="activeTab = 'groups'">组织覆盖 <span>{{ groups.length }}</span></button>
        <button :aria-selected="activeTab === 'schedules'" :class="{ active: activeTab === 'schedules' }" role="tab" type="button" @click="activeTab = 'schedules'">排班执行 <span>{{ schedules.length }}</span></button>
        <button :aria-selected="activeTab === 'results'" :class="{ active: activeTab === 'results' }" role="tab" type="button" @click="activeTab = 'results'">结果异常 <span>{{ exceptionResults.length }}</span></button>
      </div>
    </AdminPanel>

    <template v-if="activeTab === 'shifts'">
      <AdminPanel :span="12" :title="`班次规则 (${shifts.length})`" subtitle="集中核对工作时段、打卡窗口与启停状态">
        <template #actions><button class="primary-button" type="button" @click="shiftDialogVisible = true"><Plus :size="16" />新建班次</button></template>
        <AdminTable :columns="shiftColumns" :rows="shifts" :loading="loading" row-key="shiftId">
          <template #cell-workTime="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
          <template #cell-punchTime="{ row }">
            <strong>{{ row.onDutyPunchStart }} - {{ row.onDutyPunchEnd }}</strong>
            <span>{{ row.offDutyPunchStart }} - {{ row.offDutyPunchEnd }}</span>
          </template>
          <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
        </AdminTable>
      </AdminPanel>
    </template>

    <template v-else-if="activeTab === 'groups'">
      <AdminPanel :span="5" :title="`工作日历 (${calendars.length})`" subtitle="定义组织工作日基线">
        <template #actions><button class="primary-button" type="button" @click="calendarDialogVisible = true"><Plus :size="16" />新建日历</button></template>
        <AdminTable :columns="calendarColumns" :rows="calendars" :loading="loading" row-key="calendarId">
          <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
        </AdminTable>
      </AdminPanel>

      <AdminPanel :span="7" :title="`考勤组 (${groups.length})`" subtitle="选择考勤组后维护其成员覆盖">
        <template #actions><button class="primary-button" type="button" @click="groupDialogVisible = true"><Plus :size="16" />新建考勤组</button></template>
        <AdminTable
          interactive
          :columns="groupColumns"
          :rows="groups"
          :loading="loading"
          :selected-id="selectedGroupId"
          row-key="groupId"
          @row-click="selectGroup"
        >
          <template #cell-calendarId="{ row }">{{ row.calendarName || calendarName(row.calendarId) }}</template>
          <template #cell-defaultShiftId="{ row }">{{ row.defaultShiftName || shiftName(row.defaultShiftId) }}</template>
          <template #cell-grace="{ row }">{{ row.lateGraceMinutes }} / {{ row.earlyLeaveGraceMinutes }}</template>
          <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
        </AdminTable>
      </AdminPanel>

      <AdminPanel :span="12" :title="`考勤组成员 (${groupMembers.length})`" subtitle="成员生效区间决定规则实际覆盖范围">
        <template #actions><button class="primary-button" type="button" :disabled="!selectedGroupId" @click="memberDialogVisible = true"><Plus :size="16" />添加成员</button></template>
        <AdminTable :columns="memberColumns" :rows="groupMembers" :loading="loading" row-key="memberId">
          <template #cell-userId="{ row }">{{ employeeLabel(row) }}</template>
          <template #cell-effectiveTo="{ row }">{{ row.effectiveTo || '-' }}</template>
          <template #cell-sourceType="{ row }">{{ attendanceSourceLabel(row.sourceType) }}</template>
          <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
        </AdminTable>
      </AdminPanel>
    </template>

    <template v-else-if="activeTab === 'schedules'">
      <AdminPanel :span="12" :title="`排班执行 (${schedules.length})`" subtitle="按员工与日期窗口检查实际排班，点击记录可直接修改">
        <template #actions><button class="primary-button" type="button" @click="newSchedule"><Plus :size="16" />新建排班</button></template>
        <AdminFilterBar @search="loadSchedules">
          <AdminUserPicker v-model="scheduleFilterUserId" label="员工" @select="onScheduleFilterUser" />
          <input v-model="scheduleFilters.startDate" aria-label="排班开始日期" required type="date" />
          <input v-model="scheduleFilters.endDate" aria-label="排班结束日期" required type="date" />
        </AdminFilterBar>
        <AdminTable
          interactive
          :columns="scheduleColumns"
          :rows="schedules"
          :loading="loading"
          :selected-id="selectedScheduleId"
          row-key="scheduleId"
          @row-click="selectSchedule"
        >
          <template #cell-userId="{ row }">{{ employeeLabel(row) }}</template>
          <template #cell-shiftId="{ row }">{{ shiftName(row.shiftId) }}</template>
          <template #cell-scheduleSource="{ row }">{{ attendanceSourceLabel(row.scheduleSource) }}</template>
          <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
        </AdminTable>
      </AdminPanel>
    </template>

    <template v-else>
      <AdminPanel :span="12" :title="`日结果与异常 (${dailyResults.length})`" subtitle="异常优先展示迟到、早退与缺勤影响，按员工和日期范围复核">
        <AdminFilterBar @search="loadResults">
          <AdminUserPicker v-model="resultFilterUserId" label="员工" @select="onResultFilterUser" />
          <input v-model="resultFilters.startDate" aria-label="结果开始日期" required type="date" />
          <input v-model="resultFilters.endDate" aria-label="结果结束日期" required type="date" />
        </AdminFilterBar>
        <AdminTable :columns="resultColumns" :rows="dailyResults" :loading="loading" row-key="dailyResultId">
          <template #cell-userId="{ row }">{{ employeeLabel(row) }}</template>
          <template #cell-resultStatus="{ row }"><span class="status-pill" :class="{ 'risk-status': row.exceptionFlag }">{{ attendanceResultLabel(row.resultStatus) }}</span></template>
          <template #cell-minutes="{ row }">
            <strong>工作 {{ formatMinutes(row.workMinutes) }}</strong>
            <span>迟到 {{ row.lateMinutes }} / 早退 {{ row.earlyLeaveMinutes }} / 缺勤 {{ row.absenceMinutes }}</span>
          </template>
          <template #cell-exceptionFlag="{ row }"><strong :class="{ 'risk-text': row.exceptionFlag }">{{ row.exceptionFlag ? '待复核' : '正常' }}</strong></template>
          <template #cell-calculatedAt="{ row }">{{ formatDateTime(row.calculatedAt) }}</template>
        </AdminTable>
      </AdminPanel>
    </template>

    <AdminDialog v-model:visible="shiftDialogVisible" title="新建班次" width="min(680px, 92vw)">
      <form class="attendance-form" @submit.prevent="createShift">
        <label><span>名称</span><input v-model="shiftForm.shiftName" required maxlength="128" /></label>
        <label><span>上班</span><input v-model="shiftForm.startTime" required type="time" step="1" /></label>
        <label><span>下班</span><input v-model="shiftForm.endTime" required type="time" step="1" /></label>
        <label><span>上班打卡开始</span><input v-model="shiftForm.onDutyPunchStart" type="time" step="1" /></label>
        <label><span>上班打卡结束</span><input v-model="shiftForm.onDutyPunchEnd" type="time" step="1" /></label>
        <label><span>下班打卡开始</span><input v-model="shiftForm.offDutyPunchStart" type="time" step="1" /></label>
        <label><span>下班打卡结束</span><input v-model="shiftForm.offDutyPunchEnd" type="time" step="1" /></label>
        <label><span>状态</span><select v-model="shiftForm.status"><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
        <button class="primary-button" :disabled="submitting" type="submit"><Plus :size="16" />新建班次</button>
      </form>
    </AdminDialog>

    <AdminDialog v-model:visible="calendarDialogVisible" title="新建工作日历">
      <form class="attendance-form single-form" @submit.prevent="createCalendar">
        <label><span>名称</span><input v-model="calendarForm.calendarName" required maxlength="128" /></label>
        <label><span>状态</span><select v-model="calendarForm.status"><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
        <button class="primary-button" :disabled="submitting" type="submit"><Plus :size="16" />新建日历</button>
      </form>
    </AdminDialog>

    <AdminDialog v-model:visible="groupDialogVisible" title="新建考勤组" width="min(680px, 92vw)">
      <form class="attendance-form" @submit.prevent="createGroup">
        <label><span>名称</span><input v-model="groupForm.groupName" required maxlength="128" /></label>
        <label><span>日历</span><select v-model="groupForm.calendarId" required><option value="">请选择</option><option v-for="calendar in activeCalendars" :key="formatId(calendar.calendarId)" :value="String(calendar.calendarId)">{{ calendar.calendarName }}</option></select></label>
        <label><span>默认班次</span><select v-model="groupForm.defaultShiftId" required><option value="">请选择</option><option v-for="shift in activeShifts" :key="formatId(shift.shiftId)" :value="String(shift.shiftId)">{{ shift.shiftName }}</option></select></label>
        <label><span>迟到宽限</span><input v-model.number="groupForm.lateGraceMinutes" min="0" type="number" /></label>
        <label><span>早退宽限</span><input v-model.number="groupForm.earlyLeaveGraceMinutes" min="0" type="number" /></label>
        <label><span>状态</span><select v-model="groupForm.status"><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
        <button class="primary-button" :disabled="submitting" type="submit"><Plus :size="16" />新建考勤组</button>
      </form>
    </AdminDialog>

    <AdminDialog v-model:visible="memberDialogVisible" title="添加考勤组成员">
      <form class="attendance-form single-form" @submit.prevent="addGroupMember">
        <AdminUserPicker v-model="memberUserId" label="员工" @select="onMemberUser" />
        <label><span>开始日</span><input v-model="memberForm.effectiveFrom" required type="date" /></label>
        <label><span>结束日</span><input v-model="memberForm.effectiveTo" type="date" /></label>
        <label><span>来源</span><select v-model="memberForm.sourceType"><option value="MANUAL">手动</option></select></label>
        <button class="primary-button" :disabled="submitting || !selectedGroupId" type="submit"><Plus :size="16" />添加成员</button>
      </form>
    </AdminDialog>

    <AdminDialog v-model:visible="scheduleDialogVisible" :title="selectedScheduleId ? '修改排班' : '新建排班'" width="min(680px, 92vw)" @close="clearScheduleSelection">
      <form class="attendance-form" @submit.prevent="submitSchedule">
        <AdminUserPicker v-model="scheduleUserId" label="员工" :disabled="!!selectedScheduleId" @select="onScheduleUser" />
        <label><span>日期</span><input v-model="scheduleForm.workDate" :disabled="!!selectedScheduleId" required type="date" /></label>
        <label><span>班次</span><select v-model="scheduleForm.shiftId" required><option value="">请选择</option><option v-for="shift in activeShifts" :key="formatId(shift.shiftId)" :value="String(shift.shiftId)">{{ shift.shiftName }}</option></select></label>
        <label><span>日历</span><select v-model="scheduleForm.calendarId"><option value="">无</option><option v-for="calendar in activeCalendars" :key="formatId(calendar.calendarId)" :value="String(calendar.calendarId)">{{ calendar.calendarName }}</option></select></label>
        <label><span>来源</span><select v-model="scheduleForm.scheduleSource"><option value="MANUAL">手动</option><option value="AUTO">自动</option><option value="TEMPLATE">模板</option></select></label>
        <label><span>状态</span><select v-model="scheduleForm.status"><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
        <button class="primary-button" :disabled="submitting" type="submit"><Save :size="16" />{{ selectedScheduleId ? '保存排班' : '新建排班' }}</button>
      </form>
    </AdminDialog>
  </main>
</template>

<style scoped>
.attendance-page { letter-spacing: 0; }

.attendance-command {
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(0, 2.2fr);
  align-items: stretch;
  gap: var(--space-base);
  padding: var(--space-sm) 0 var(--space-base);
}

.exception-focus {
  position: relative;
  display: grid;
  align-content: center;
  gap: var(--space-xs);
  min-height: 118px;
  padding: var(--space-base);
  border: 1px solid var(--error-border);
  border-radius: var(--radius-lg);
  background: var(--error-bg);
  color: var(--red-dark);
  text-align: left;
  overflow: visible;
}

.exception-focus:hover { border-color: var(--red); }
.exception-focus:focus-visible { outline: none; box-shadow: 0 0 0 4px var(--focus-ring); }
.exception-focus > span:first-child,
.exception-focus small { font-size: var(--text-xs); font-weight: 700; }
.exception-focus strong { font-size: var(--text-xl); font-variant-numeric: tabular-nums; }

.exception-breakout {
  position: absolute;
  top: calc(var(--space-base) * -1);
  right: var(--space-base);
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 4px solid var(--panel);
  border-radius: var(--radius-md);
  background: var(--red);
  color: var(--panel-strong);
  box-shadow: var(--shadow-sm);
}

.attendance-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  padding-top: var(--space-base);
  border-top: 1px solid var(--line);
}

.attendance-tabs button {
  min-height: var(--control-h);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 0 var(--control-px);
  background: var(--panel-strong);
  color: var(--ink);
  font-weight: 700;
  transition: color 200ms ease, border-color 200ms ease, background-color 200ms ease;
}

.attendance-tabs button span {
  display: inline-grid;
  place-items: center;
  min-width: 22px;
  min-height: 20px;
  margin-left: var(--space-xs);
  border-radius: var(--radius-sm);
  background: var(--surface-subtle);
  color: var(--muted);
  font-size: var(--text-xs);
}

.attendance-tabs button:hover {
  border-color: var(--green-light);
  color: var(--green-dark);
}

.attendance-tabs button.active {
  border-color: var(--green);
  background: var(--green);
  color: var(--panel-strong);
}

.attendance-tabs button.active span { background: var(--panel-strong); color: var(--green-dark); }

.risk-status,
.risk-text { color: var(--red-dark) !important; }

.attendance-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-md);
  margin-bottom: 0;
}

.attendance-form label {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}

.attendance-form button,
.button-row {
  align-self: end;
}

.single-form { grid-template-columns: 1fr; }

.button-row {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .attendance-command { grid-template-columns: 1fr; }

  .attendance-form,
  .single-form {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .attendance-tabs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .attendance-tabs button { width: 100%; padding: 0 var(--space-sm); }
  .exception-breakout { right: var(--space-sm); }
}
</style>
