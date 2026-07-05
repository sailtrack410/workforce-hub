<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Plus, RefreshCcw, Save } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type {
  AttendanceCalendarResponse,
  AttendanceDailyResultResponse,
  AttendanceGroupMemberResponse,
  AttendanceGroupResponse,
  AttendanceScheduleResponse,
  AttendanceShiftResponse,
  EntityId,
} from '@/api/types';
import { errorMessage, formatDateTime, formatId, formatMinutes, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';

const toast = useToast();

const activeTab = ref<'shifts' | 'groups' | 'schedules' | 'results'>('shifts');
const loading = ref(false);
const submitting = ref(false);

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
  shiftCode: '',
  shiftName: '',
  startTime: '09:00:00',
  endTime: '18:00:00',
  onDutyPunchStart: '08:00:00',
  onDutyPunchEnd: '10:00:00',
  offDutyPunchStart: '17:00:00',
  offDutyPunchEnd: '19:00:00',
  status: 'ACTIVE',
});
const calendarForm = reactive({ calendarCode: '', calendarName: '', status: 'ACTIVE' });
const groupForm = reactive({
  groupCode: '',
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

const shiftColumns: TableColumn[] = [
  { key: 'shiftCode', label: '编码' },
  { key: 'shiftName', label: '名称' },
  { key: 'workTime', label: '班段' },
  { key: 'punchTime', label: '打卡窗口' },
  { key: 'status', label: '状态' },
];
const calendarColumns: TableColumn[] = [
  { key: 'calendarCode', label: '编码' },
  { key: 'calendarName', label: '名称' },
  { key: 'status', label: '状态' },
];
const groupColumns: TableColumn[] = [
  { key: 'groupCode', label: '编码' },
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

function localDate(date: Date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function sourceLabel(value?: string | null) {
  const labels: Record<string, string> = { MANUAL: '手动', AUTO: '自动', TEMPLATE: '模板' };
  return value ? labels[value] ?? value : '-';
}

function resultLabel(value?: string | null) {
  const labels: Record<string, string> = { NORMAL: '正常', ABNORMAL: '异常', NO_RULE: '未排班', LEAVE: '请假' };
  return value ? labels[value] ?? value : '-';
}

function shiftName(shiftId?: EntityId | null) {
  return shifts.value.find((item) => String(item.shiftId) === String(shiftId))?.shiftName ?? formatId(shiftId);
}

function calendarName(calendarId?: EntityId | null) {
  return calendars.value.find((item) => String(item.calendarId) === String(calendarId))?.calendarName ?? formatId(calendarId);
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
      calendarCode: calendarForm.calendarCode,
      calendarName: calendarForm.calendarName,
      calendarType: 'STANDARD',
      status: calendarForm.status,
    });
    calendarForm.calendarCode = '';
    calendarForm.calendarName = '';
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
    await adminApi.attendance.createShift({ ...shiftForm });
    shiftForm.shiftCode = '';
    shiftForm.shiftName = '';
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
      groupCode: groupForm.groupCode,
      groupName: groupForm.groupName,
      calendarId: groupForm.calendarId,
      defaultShiftId: groupForm.defaultShiftId,
      lateGraceMinutes: Number(groupForm.lateGraceMinutes),
      earlyLeaveGraceMinutes: Number(groupForm.earlyLeaveGraceMinutes),
      status: groupForm.status,
    });
    groupForm.groupCode = '';
    groupForm.groupName = '';
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
  scheduleForm.workDate = row.workDate;
  scheduleForm.shiftId = String(row.shiftId);
  scheduleForm.calendarId = row.calendarId == null ? '' : String(row.calendarId);
  scheduleForm.scheduleSource = row.scheduleSource;
  scheduleForm.status = row.status;
}

function clearScheduleSelection() {
  selectedScheduleId.value = null;
  scheduleForm.userId = '';
  scheduleForm.workDate = today;
  scheduleForm.shiftId = '';
  scheduleForm.calendarId = '';
  scheduleForm.scheduleSource = 'MANUAL';
  scheduleForm.status = 'ACTIVE';
}

onMounted(loadPage);
</script>

<template>
  <main class="page-grid">
    <AdminPanel :span="12" title="考勤管理">
      <template #actions>
        <button class="secondary-button" :disabled="loading" @click="loadPage">
          <RefreshCcw :size="16" />
          <span>刷新</span>
        </button>
      </template>
      <div class="attendance-tabs" role="tablist">
        <button :class="{ active: activeTab === 'shifts' }" type="button" @click="activeTab = 'shifts'">班次</button>
        <button :class="{ active: activeTab === 'groups' }" type="button" @click="activeTab = 'groups'">考勤组</button>
        <button :class="{ active: activeTab === 'schedules' }" type="button" @click="activeTab = 'schedules'">排班</button>
        <button :class="{ active: activeTab === 'results' }" type="button" @click="activeTab = 'results'">日结果</button>
      </div>
    </AdminPanel>

    <template v-if="activeTab === 'shifts'">
      <AdminPanel :span="5" title="新建班次">
        <form class="attendance-form" @submit.prevent="createShift">
          <label><span>编码</span><input v-model="shiftForm.shiftCode" required maxlength="64" /></label>
          <label><span>名称</span><input v-model="shiftForm.shiftName" required maxlength="128" /></label>
          <label><span>上班</span><input v-model="shiftForm.startTime" required type="time" step="1" /></label>
          <label><span>下班</span><input v-model="shiftForm.endTime" required type="time" step="1" /></label>
          <label><span>上班打卡开始</span><input v-model="shiftForm.onDutyPunchStart" type="time" step="1" /></label>
          <label><span>上班打卡结束</span><input v-model="shiftForm.onDutyPunchEnd" type="time" step="1" /></label>
          <label><span>下班打卡开始</span><input v-model="shiftForm.offDutyPunchStart" type="time" step="1" /></label>
          <label><span>下班打卡结束</span><input v-model="shiftForm.offDutyPunchEnd" type="time" step="1" /></label>
          <label>
            <span>状态</span>
            <select v-model="shiftForm.status">
              <option value="ACTIVE">启用</option>
              <option value="INACTIVE">停用</option>
            </select>
          </label>
          <button class="primary-button" :disabled="submitting" type="submit">
            <Plus :size="16" />
            <span>新建班次</span>
          </button>
        </form>
      </AdminPanel>

      <AdminPanel :span="7" :title="`班次 (${shifts.length})`">
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
      <AdminPanel :span="5" title="工作日历">
        <form class="attendance-form" @submit.prevent="createCalendar">
          <label><span>编码</span><input v-model="calendarForm.calendarCode" required maxlength="64" /></label>
          <label><span>名称</span><input v-model="calendarForm.calendarName" required maxlength="128" /></label>
          <label>
            <span>状态</span>
            <select v-model="calendarForm.status">
              <option value="ACTIVE">启用</option>
              <option value="INACTIVE">停用</option>
            </select>
          </label>
          <button class="primary-button" :disabled="submitting" type="submit">
            <Plus :size="16" />
            <span>新建日历</span>
          </button>
        </form>
        <AdminTable :columns="calendarColumns" :rows="calendars" :loading="loading" row-key="calendarId">
          <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
        </AdminTable>
      </AdminPanel>

      <AdminPanel :span="7" title="考勤组">
        <form class="attendance-form group-form" @submit.prevent="createGroup">
          <label><span>编码</span><input v-model="groupForm.groupCode" required maxlength="64" /></label>
          <label><span>名称</span><input v-model="groupForm.groupName" required maxlength="128" /></label>
          <label>
            <span>日历</span>
            <select v-model="groupForm.calendarId" required>
              <option value="">请选择</option>
              <option v-for="calendar in activeCalendars" :key="formatId(calendar.calendarId)" :value="String(calendar.calendarId)">
                {{ calendar.calendarName }}
              </option>
            </select>
          </label>
          <label>
            <span>默认班次</span>
            <select v-model="groupForm.defaultShiftId" required>
              <option value="">请选择</option>
              <option v-for="shift in activeShifts" :key="formatId(shift.shiftId)" :value="String(shift.shiftId)">
                {{ shift.shiftName }}
              </option>
            </select>
          </label>
          <label><span>迟到宽限</span><input v-model.number="groupForm.lateGraceMinutes" min="0" type="number" /></label>
          <label><span>早退宽限</span><input v-model.number="groupForm.earlyLeaveGraceMinutes" min="0" type="number" /></label>
          <label>
            <span>状态</span>
            <select v-model="groupForm.status">
              <option value="ACTIVE">启用</option>
              <option value="INACTIVE">停用</option>
            </select>
          </label>
          <button class="primary-button" :disabled="submitting" type="submit">
            <Plus :size="16" />
            <span>新建考勤组</span>
          </button>
        </form>
        <AdminTable
          :columns="groupColumns"
          :rows="groups"
          :loading="loading"
          :selected-id="selectedGroupId"
          row-key="groupId"
          @row-click="selectGroup"
        >
          <template #cell-calendarId="{ row }">{{ calendarName(row.calendarId) }}</template>
          <template #cell-defaultShiftId="{ row }">{{ shiftName(row.defaultShiftId) }}</template>
          <template #cell-grace="{ row }">{{ row.lateGraceMinutes }} / {{ row.earlyLeaveGraceMinutes }}</template>
          <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
        </AdminTable>
      </AdminPanel>

      <AdminPanel :span="12" title="考勤组成员">
        <form class="attendance-form member-form" @submit.prevent="addGroupMember">
          <label><span>员工 ID</span><input v-model="memberForm.userId" required /></label>
          <label><span>开始日</span><input v-model="memberForm.effectiveFrom" required type="date" /></label>
          <label><span>结束日</span><input v-model="memberForm.effectiveTo" type="date" /></label>
          <label>
            <span>来源</span>
            <select v-model="memberForm.sourceType">
              <option value="MANUAL">手动</option>
            </select>
          </label>
          <button class="primary-button" :disabled="submitting || !selectedGroupId" type="submit">
            <Plus :size="16" />
            <span>添加成员</span>
          </button>
        </form>
        <AdminTable :columns="memberColumns" :rows="groupMembers" :loading="loading" row-key="memberId">
          <template #cell-userId="{ row }">{{ formatId(row.userId) }}</template>
          <template #cell-effectiveTo="{ row }">{{ row.effectiveTo || '-' }}</template>
          <template #cell-sourceType="{ row }">{{ sourceLabel(row.sourceType) }}</template>
          <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
        </AdminTable>
      </AdminPanel>
    </template>

    <template v-else-if="activeTab === 'schedules'">
      <AdminPanel :span="12" title="排班筛选">
        <AdminFilterBar @search="loadSchedules">
          <input v-model="scheduleFilters.userId" placeholder="员工 ID" />
          <input v-model="scheduleFilters.startDate" required type="date" />
          <input v-model="scheduleFilters.endDate" required type="date" />
        </AdminFilterBar>
      </AdminPanel>

      <AdminPanel :span="5" :title="selectedScheduleId ? '修改排班' : '新建排班'">
        <form class="attendance-form" @submit.prevent="submitSchedule">
          <label><span>员工 ID</span><input v-model="scheduleForm.userId" :disabled="!!selectedScheduleId" required /></label>
          <label><span>日期</span><input v-model="scheduleForm.workDate" :disabled="!!selectedScheduleId" required type="date" /></label>
          <label>
            <span>班次</span>
            <select v-model="scheduleForm.shiftId" required>
              <option value="">请选择</option>
              <option v-for="shift in activeShifts" :key="formatId(shift.shiftId)" :value="String(shift.shiftId)">
                {{ shift.shiftName }}
              </option>
            </select>
          </label>
          <label>
            <span>日历</span>
            <select v-model="scheduleForm.calendarId">
              <option value="">无</option>
              <option v-for="calendar in activeCalendars" :key="formatId(calendar.calendarId)" :value="String(calendar.calendarId)">
                {{ calendar.calendarName }}
              </option>
            </select>
          </label>
          <label>
            <span>来源</span>
            <select v-model="scheduleForm.scheduleSource">
              <option value="MANUAL">手动</option>
              <option value="AUTO">自动</option>
              <option value="TEMPLATE">模板</option>
            </select>
          </label>
          <label>
            <span>状态</span>
            <select v-model="scheduleForm.status">
              <option value="ACTIVE">启用</option>
              <option value="INACTIVE">停用</option>
            </select>
          </label>
          <div class="button-row">
            <button class="primary-button" :disabled="submitting" type="submit">
              <Save :size="16" />
              <span>{{ selectedScheduleId ? '保存排班' : '新建排班' }}</span>
            </button>
            <button class="secondary-button" type="button" @click="clearScheduleSelection">清空</button>
          </div>
        </form>
      </AdminPanel>

      <AdminPanel :span="7" :title="`排班 (${schedules.length})`">
        <AdminTable
          :columns="scheduleColumns"
          :rows="schedules"
          :loading="loading"
          :selected-id="selectedScheduleId"
          row-key="scheduleId"
          @row-click="selectSchedule"
        >
          <template #cell-userId="{ row }">{{ formatId(row.userId) }}</template>
          <template #cell-shiftId="{ row }">{{ shiftName(row.shiftId) }}</template>
          <template #cell-scheduleSource="{ row }">{{ sourceLabel(row.scheduleSource) }}</template>
          <template #cell-status="{ row }"><span class="status-pill">{{ statusLabel(row.status) }}</span></template>
        </AdminTable>
      </AdminPanel>
    </template>

    <template v-else>
      <AdminPanel :span="12" title="日结果筛选">
        <AdminFilterBar @search="loadResults">
          <input v-model="resultFilters.userId" placeholder="员工 ID" />
          <input v-model="resultFilters.startDate" required type="date" />
          <input v-model="resultFilters.endDate" required type="date" />
        </AdminFilterBar>
      </AdminPanel>

      <AdminPanel :span="12" :title="`日结果 (${dailyResults.length})`">
        <AdminTable :columns="resultColumns" :rows="dailyResults" :loading="loading" row-key="dailyResultId">
          <template #cell-userId="{ row }">{{ formatId(row.userId) }}</template>
          <template #cell-resultStatus="{ row }"><span class="status-pill">{{ resultLabel(row.resultStatus) }}</span></template>
          <template #cell-minutes="{ row }">
            <strong>工作 {{ formatMinutes(row.workMinutes) }}</strong>
            <span>迟到 {{ row.lateMinutes }} / 早退 {{ row.earlyLeaveMinutes }} / 缺勤 {{ row.absenceMinutes }}</span>
          </template>
          <template #cell-exceptionFlag="{ row }">{{ row.exceptionFlag ? '是' : '否' }}</template>
          <template #cell-calculatedAt="{ row }">{{ formatDateTime(row.calculatedAt) }}</template>
        </AdminTable>
      </AdminPanel>
    </template>
  </main>
</template>

<style scoped>
.attendance-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.attendance-tabs button {
  min-height: 34px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  padding: 0 var(--space-lg);
  background: var(--panel-strong);
  color: var(--ink);
  font-weight: 700;
}

.attendance-tabs button.active {
  border-color: var(--green);
  background: var(--green);
  color: var(--panel-strong);
}

.attendance-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
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

.group-form,
.member-form {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.button-row {
  display: flex;
  gap: var(--space-sm);
}

@media (max-width: 900px) {
  .attendance-form,
  .group-form,
  .member-form {
    grid-template-columns: 1fr;
  }
}
</style>
