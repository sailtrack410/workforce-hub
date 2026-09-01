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
import AdminMetricStrip from '@/components/AdminMetricStrip.vue';

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
const totalAvailableMinutes = computed(() => accounts.value.reduce((sum, item) => sum + item.availableMinutes, 0));
const totalFrozenMinutes = computed(() => accounts.value.reduce((sum, item) => sum + item.frozenMinutes, 0));
const frozenAccountCount = computed(() => accounts.value.filter((item) => item.frozenMinutes > 0).length);
const inactiveAccountCount = computed(() => accounts.value.filter((item) => item.status !== 'ACTIVE').length);
const accountMetrics = computed(() => [
  { label: '账户总数', value: accounts.value.length },
  { label: '启用假种', value: activeLeaveTypes.value.length },
  { label: '存在冻结', value: frozenAccountCount.value },
  { label: '停用账户', value: inactiveAccountCount.value },
]);
const adjustmentDirection = computed(() => adjustment.changeMinutes > 0 ? '增加余额' : adjustment.changeMinutes < 0 ? '扣减余额' : '无余额变化');

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
  <main class="page-grid leave-governance-page">
    <AdminPanel :span="12" title="假期余额运行态势" subtitle="汇总当前筛选范围内的可用、冻结与异常账户，余额变更需留存原因">
      <div class="balance-command">
        <div class="balance-focus">
          <span class="balance-kicker">组织可支配余额</span>
          <strong>{{ formatMinutes(totalAvailableMinutes) }}</strong>
          <div class="balance-risk">
            <span>冻结 {{ formatMinutes(totalFrozenMinutes) }}</span>
            <span>{{ frozenAccountCount }} 个账户待关注</span>
          </div>
          <span class="balance-breakout" aria-hidden="true">余额</span>
        </div>
        <AdminMetricStrip :cards="accountMetrics" />
      </div>
    </AdminPanel>

    <AdminPanel :span="7" :title="`员工假期账户 (${accounts.length})`" subtitle="点击账户可直接带入右侧余额调整">
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
        <template #cell-userId="{ row }">
          <strong>{{ employeeLabel(row) }}</strong>
          <span>{{ row.employeeNo || '未设置工号' }}</span>
        </template>
        <template #cell-leaveName="{ row }">
          <strong>{{ row.leaveName }}</strong>
        </template>
        <template #cell-availableMinutes="{ row }">{{ formatMinutes(row.availableMinutes) }}</template>
        <template #cell-frozenMinutes="{ row }">{{ formatMinutes(row.frozenMinutes) }}</template>
        <template #cell-status="{ row }">
          <span class="status-pill" :class="{ 'risk-status': row.status !== 'ACTIVE' || row.frozenMinutes > 0 }">{{ statusLabel(row.status) }}</span>
        </template>
      </AdminTable>
    </AdminPanel>

    <AdminPanel :span="5" title="余额调整" subtitle="正数增加、负数扣减；提交前将再次确认">
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
        <div class="adjustment-impact" aria-live="polite">
          <span>本次影响</span>
          <strong>{{ adjustmentDirection }} · {{ formatMinutes(Math.abs(adjustment.changeMinutes || 0)) }}</strong>
          <small>{{ adjustment.userId && adjustment.leaveTypeId ? '账户已就绪，可提交审核确认' : '请选择员工与假种' }}</small>
        </div>
        <button class="danger-button" :disabled="submitting" type="submit">
          <Save :size="16" />
          <span>提交调整</span>
        </button>
      </form>
    </AdminPanel>

    <AdminPanel :span="12" title="假种政策覆盖" subtitle="快速核对余额扣减规则与启停状态">
      <div class="card-grid">
        <article v-for="type in leaveTypes" :key="formatId(type.leaveTypeId)" class="mini-card" :class="{ inactive: type.status !== 'ACTIVE' }">
          <strong>{{ type.leaveName }}</strong>
          <span>{{ leaveBalanceModeLabel(type.balanceMode) }}</span>
          <span class="policy-status">{{ statusLabel(type.status) }}</span>
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
.leave-governance-page { letter-spacing: 0; }

.balance-command {
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(0, 2.2fr);
  gap: var(--space-base);
  align-items: stretch;
  padding-top: var(--space-sm);
}

.balance-focus {
  position: relative;
  display: grid;
  align-content: center;
  gap: var(--space-sm);
  min-height: 118px;
  padding: var(--space-base);
  border: 1px solid var(--green);
  border-radius: var(--radius-lg);
  background: var(--primary-soft);
  overflow: visible;
}

.balance-kicker,
.balance-risk,
.adjustment-impact span,
.adjustment-impact small {
  color: var(--muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.balance-focus > strong {
  color: var(--green-deep);
  font-size: var(--text-xl);
  font-variant-numeric: tabular-nums;
}

.balance-risk {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.balance-breakout {
  position: absolute;
  top: calc(var(--space-base) * -1);
  right: var(--space-base);
  display: grid;
  place-items: center;
  min-width: 48px;
  min-height: 32px;
  border: 4px solid var(--panel);
  border-radius: var(--radius-md);
  background: var(--green);
  color: var(--panel-strong);
  font-size: var(--text-xs);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.adjustment-impact {
  display: grid;
  gap: var(--space-xs);
  padding: var(--space-md);
  border-left: 3px solid var(--amber);
  background: var(--surface-subtle);
}

.adjustment-impact strong { color: var(--ink); font-size: var(--text-sm); }

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-md);
}

.mini-card {
  position: relative;
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

.mini-card.inactive { background: var(--surface-subtle); }

.policy-status {
  position: absolute;
  top: var(--space-md);
  right: var(--space-md);
  color: var(--green-dark) !important;
}

.mini-card.inactive .policy-status,
.risk-status { color: var(--red-dark) !important; }

@media (max-width: 900px) {
  .balance-command { grid-template-columns: 1fr; }
}

@media (max-width: 480px) {
  .balance-risk { display: grid; }
  .balance-breakout { right: var(--space-sm); }
}
</style>
