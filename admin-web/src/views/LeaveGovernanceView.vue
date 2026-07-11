<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { RefreshCcw, Save } from 'lucide-vue-next';
import { adminApi } from '@/api/admin';
import type { EntityId, LeaveAccountResponse, LeaveTypeResponse, OrgUserSummaryResponse } from '@/api/types';
import { errorMessage, formatId, formatMinutes, leaveBalanceModeLabel, statusLabel } from '@/utils/format';
import { useLeaveGovernanceStore } from '@/stores/leaveGovernance';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminFilterBar from '@/components/AdminFilterBar.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';
import AdminUserPicker from '@/components/AdminUserPicker.vue';

const store = useLeaveGovernanceStore();
const toast = useToast();
const adjustment = reactive({ userId: '', leaveTypeId: '', changeMinutes: 480, reason: '' });

const leaveTypes = ref<LeaveTypeResponse[]>([]);
const accounts = ref<LeaveAccountResponse[]>([]);
const loading = ref(false);
const submitting = ref(false);
const confirmVisible = ref(false);
const filterUserId = ref<EntityId | null>(store.filters.userId || null);
const adjustmentUserId = ref<EntityId | null>(null);

const activeLeaveTypes = computed(() => leaveTypes.value.filter((t) => t.status === 'ACTIVE'));

const accountColumns: TableColumn[] = [
  { key: 'userId', label: '员工' },
  { key: 'leaveName', label: '假种' },
  { key: 'availableMinutes', label: '可用' },
  { key: 'frozenMinutes', label: '冻结' },
  { key: 'status', label: '状态' },
];

onMounted(() => { void loadPage(); });

async function loadPage() {
  loading.value = true;
  try {
    const [typeRows, accountRows] = await Promise.all([
      adminApi.leave.types(),
      adminApi.leave.accounts({ userId: filterUserId.value || undefined, leaveTypeId: store.filters.leaveTypeId }),
    ]);
    leaveTypes.value = typeRows;
    accounts.value = accountRows;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function fillAdjustment(account: LeaveAccountResponse) {
  adjustment.userId = String(account.userId);
  adjustmentUserId.value = account.userId;
  adjustment.leaveTypeId = String(account.leaveTypeId);
}

function employeeLabel(row: { realName?: string | null; employeeNo?: string | null }) {
  return row.realName || '未命名员工';
}

function onFilterUser(user: OrgUserSummaryResponse | null) {
  void user;
  store.filters.userId = filterUserId.value ? String(filterUserId.value) : '';
}

function onAdjustmentUser(user: OrgUserSummaryResponse | null) {
  void user;
  adjustment.userId = adjustmentUserId.value ? String(adjustmentUserId.value) : '';
}

async function submitAdjustment() {
  if (!adjustment.userId || !adjustment.leaveTypeId || !adjustment.changeMinutes) {
    toast.error('员工、假种和调整分钟数不能为空');
    return;
  }
  submitting.value = true;
  try {
    const result = await adminApi.leave.adjustAccount({
      userId: adjustment.userId,
      leaveTypeId: adjustment.leaveTypeId,
      changeMinutes: Number(adjustment.changeMinutes),
      reason: adjustment.reason || null,
      sourceTxnKey: `ADMIN_ADJUST:${Date.now()}`,
    });
    toast.success(`调整完成，当前可用 ${formatMinutes(result.availableMinutes)}`);
    await loadPage();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <main class="page-grid">
    <AdminPanel :span="7" :title="`员工假期账户 (${accounts.length})`">
      <template #actions>
        <button class="secondary-button" :disabled="loading" @click="loadPage">
          <RefreshCcw :size="16" />
          <span>刷新</span>
        </button>
      </template>
      <AdminFilterBar @search="loadPage">
        <AdminUserPicker v-model="filterUserId" label="员工" @select="onFilterUser" />
        <select v-model="store.filters.leaveTypeId">
          <option value="">全部假种</option>
          <option v-for="type in leaveTypes" :key="formatId(type.leaveTypeId)" :value="String(type.leaveTypeId)">
            {{ type.leaveName }}
          </option>
        </select>
      </AdminFilterBar>
      <AdminTable interactive :columns="accountColumns" :rows="accounts" :loading="loading" row-key="accountId" @select="fillAdjustment">
        <template #cell-userId="{ row }">{{ employeeLabel(row) }}</template>
        <template #cell-leaveName="{ row }">
          <strong>{{ row.leaveName }}</strong>
        </template>
        <template #cell-availableMinutes="{ row }">{{ formatMinutes(row.availableMinutes) }}</template>
        <template #cell-frozenMinutes="{ row }">{{ formatMinutes(row.frozenMinutes) }}</template>
        <template #cell-status="{ row }">
          <span class="status-pill">{{ statusLabel(row.status) }}</span>
        </template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="5" title="余额调整">
      <form class="stack-form" @submit.prevent="confirmVisible = true">
        <AdminUserPicker v-model="adjustmentUserId" label="员工" @select="onAdjustmentUser" />
        <label>
          <span>假种</span>
          <select v-model="adjustment.leaveTypeId">
            <option value="">请选择</option>
            <option v-for="type in activeLeaveTypes" :key="formatId(type.leaveTypeId)" :value="String(type.leaveTypeId)">
              {{ type.leaveName }}
            </option>
          </select>
        </label>
        <label>
          <span>调整分钟数</span>
          <input v-model.number="adjustment.changeMinutes" type="number" />
        </label>
        <label>
          <span>原因</span>
          <textarea v-model="adjustment.reason" maxlength="512" />
        </label>
        <button class="danger-button" :disabled="submitting" type="submit">
          <Save :size="16" />
          <span>提交调整</span>
        </button>
      </form>
    </AdminPanel>

    <AdminPanel :span="12" title="假种">
      <div class="card-grid">
        <article v-for="type in leaveTypes" :key="formatId(type.leaveTypeId)" class="mini-card">
          <strong>{{ type.leaveName }}</strong>
          <span>{{ leaveBalanceModeLabel(type.balanceMode) }} · {{ statusLabel(type.status) }}</span>
        </article>
        <div v-if="leaveTypes.length === 0" class="empty-state">无数据</div>
      </div>
    </AdminPanel>

    <AdminConfirmDialog
      v-model:visible="confirmVisible"
      title="确认调整假期余额"
      message="确认调整员工假期余额？"
      confirm-label="提交调整"
      :danger="true"
      @confirm="submitAdjustment"
    />
  </main>
</template>

<style scoped>
.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-md);
}

.mini-card {
  display: grid;
  gap: var(--space-xs);
  min-height: 76px;
  padding: var(--space-md);
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--panel-strong);
  box-shadow: var(--shadow-sm);
}

.mini-card span {
  color: var(--muted);
  font-size: var(--text-xs);
}
</style>
