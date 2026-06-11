<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Pencil, PlayCircle, Plus, PowerOff, RefreshCcw, Search, Send } from 'lucide-vue-next';
import { useRouter } from 'vue-router';
import { adminApi } from '@/api/admin';
import type { AgentResponse } from '@/api/types';
import { errorMessage, formatDateTime, statusLabel } from '@/utils/format';
import { useToast } from '@/composables/useToast';
import AdminPanel from '@/components/AdminPanel.vue';
import AdminTable, { type TableColumn } from '@/components/AdminTable.vue';
import AdminConfirmDialog from '@/components/AdminConfirmDialog.vue';

const router = useRouter();
const toast = useToast();

const loading = ref(false);
const items = ref<AgentResponse[]>([]);
const keyword = ref('');

const createVisible = ref(false);
const createForm = reactive({ agentCode: '', agentName: '' });
const creating = ref(false);
const disableTarget = ref<AgentResponse | null>(null);
const disableVisible = ref(false);
const disabling = ref(false);
const enableTarget = ref<AgentResponse | null>(null);
const enableVisible = ref(false);
const enabling = ref(false);

const columns: TableColumn[] = [
  { key: 'agentCode', label: '编码' },
  { key: 'agentName', label: '名称' },
  { key: 'agentType', label: '类型' },
  { key: 'status', label: '状态' },
  { key: 'publishedVersionId', label: '发布版本' },
  { key: 'createdAt', label: '创建时间' },
  { key: '_actions', label: '操作', width: '230px' },
];

async function loadList() {
  loading.value = true;
  try {
    const res = await adminApi.agentConfig.list(1, 100, keyword.value.trim() || undefined);
    items.value = res.list;
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  void loadList();
}

function openCreate() {
  createForm.agentCode = '';
  createForm.agentName = '';
  createVisible.value = true;
}

async function handleCreate() {
  if (!createForm.agentCode.trim() || !createForm.agentName.trim()) return;
  creating.value = true;
  try {
    await adminApi.agentConfig.create({
      agentCode: createForm.agentCode.trim(),
      agentName: createForm.agentName.trim(),
      agentType: 'QA',
      identityMode: 'SYSTEM_IDENTITY',
    });
    createVisible.value = false;
    toast.success('Agent 创建成功');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    creating.value = false;
  }
}

function openDagEditor(agent: AgentResponse) {
  const resolved = router.resolve({ name: 'admin-agent-dag', params: { agentId: agent.agentId } });
  window.open(resolved.href, '_blank');
}

function askDisable(agent: AgentResponse) {
  disableTarget.value = agent;
  disableVisible.value = true;
}

function askEnable(agent: AgentResponse) {
  enableTarget.value = agent;
  enableVisible.value = true;
}

async function handleDisable() {
  if (!disableTarget.value || disabling.value) return;
  disabling.value = true;
  try {
    await adminApi.agentConfig.disable(disableTarget.value.agentId);
    disableVisible.value = false;
    toast.success('已停用');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    disabling.value = false;
  }
}

async function handleEnable() {
  if (!enableTarget.value || enabling.value) return;
  enabling.value = true;
  try {
    await adminApi.agentConfig.enable(enableTarget.value.agentId);
    enableVisible.value = false;
    toast.success('已启用');
    await loadList();
  } catch (e) {
    toast.error(errorMessage(e));
  } finally {
    enabling.value = false;
  }
}

onMounted(loadList);
</script>

<template>
  <AdminPanel :span="12" title="Agent 管理">
    <template #actions>
      <form class="agent-search" @submit.prevent="handleSearch">
        <input v-model="keyword" placeholder="搜索名称 / 编码" />
        <button class="secondary-button" title="搜索" type="submit">
          <Search :size="16" />
        </button>
      </form>
      <button class="secondary-button" title="刷新" @click="loadList">
        <RefreshCcw :size="16" />
      </button>
      <button class="primary-button" @click="openCreate">
        <Plus :size="16" />
        <span>新建 Agent</span>
      </button>
    </template>

    <AdminTable
      :columns="columns"
      :rows="items"
      :loading="loading"
      row-key="agentId"
    >
      <template #cell-status="{ row }">
        <span class="status-pill">{{ statusLabel(row.status) }}</span>
      </template>
      <template #cell-publishedVersionId="{ row }">
        {{ row.publishedVersionId ? `V${row.publishedVersionLabel || row.publishedVersionNo}` : '未发布' }}
      </template>
      <template #cell-createdAt="{ row }">
        {{ formatDateTime(row.createdAt) }}
      </template>
      <template #cell-_actions="{ row }">
        <div class="row-actions">
          <button class="secondary-button action-btn" title="编辑 DAG" @click.stop="openDagEditor(row)">
            <Pencil :size="14" />
            <span>编辑</span>
          </button>
          <button class="primary-button action-btn" title="发布" @click.stop="openDagEditor(row)">
            <Send :size="14" />
            <span>发布</span>
          </button>
          <button
            v-if="row.status === 'INACTIVE'"
            class="primary-button action-btn"
            title="启用"
            @click.stop="askEnable(row)"
          >
            <PlayCircle :size="14" />
            <span>启用</span>
          </button>
          <button
            v-else
            class="danger-button action-btn"
            title="停用"
            :disabled="row.status !== 'ACTIVE'"
            @click.stop="askDisable(row)"
          >
            <PowerOff :size="14" />
            <span>停用</span>
          </button>
        </div>
      </template>
    </AdminTable>
  </AdminPanel>

  <AdminConfirmDialog
    v-model:visible="createVisible"
    title="新建 Agent"
    message=""
    confirm-label="创建"
    :danger="false"
    @confirm="handleCreate"
  >
    <template #default>
      <div class="create-dialog-form">
        <label>
          <span>Agent 编码</span>
          <input v-model="createForm.agentCode" placeholder="如 ANNUAL_LEAVE_QA" autofocus />
        </label>
        <label>
          <span>Agent 名称</span>
          <input v-model="createForm.agentName" placeholder="如 年假问答助手" />
        </label>
      </div>
    </template>
  </AdminConfirmDialog>

  <AdminConfirmDialog
    v-model:visible="disableVisible"
    title="停用 Agent"
    :message="disableTarget ? `停用后员工端将不可检索和调用 ${disableTarget.agentName || disableTarget.agentCode}` : ''"
    confirm-label="停用"
    :danger="true"
    @confirm="handleDisable"
  />

  <AdminConfirmDialog
    v-model:visible="enableVisible"
    title="启用 Agent"
    :message="enableTarget ? `启用后员工端可按权限检索和调用 ${enableTarget.agentName || enableTarget.agentCode}` : ''"
    confirm-label="启用"
    :danger="false"
    @confirm="handleEnable"
  />
</template>

<style scoped>
.agent-search {
  display: flex;
  align-items: center;
  gap: 6px;
}

.agent-search input {
  width: 220px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--panel-strong);
  color: var(--ink);
  font-size: 13px;
}

.row-actions {
  display: flex;
  gap: 6px;
  flex-wrap: nowrap;
  align-items: center;
}

.action-btn {
  min-height: 30px;
  padding: 0 8px;
  font-size: 12px;
  white-space: nowrap;
}

.create-dialog-form {
  display: grid;
  gap: var(--space-lg);
  margin-top: var(--space-md);
}

.create-dialog-form label {
  display: grid;
  gap: var(--space-xs);
  color: var(--muted);
  font-size: var(--text-sm);
  font-weight: 700;
}
</style>
