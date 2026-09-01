<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue';
import { useRouter } from 'vue-router';
import {
  BriefcaseBusiness,
  CalendarDays,
  Check,
  ChevronLeft,
  Clock3,
  CreditCard,
  Download,
  Eye,
  ExternalLink,
  FileCheck2,
  Folder,
  Home,
  Plane,
  ReceiptText,
  Search,
  ShoppingCart,
  Stamp,
  Timer,
  Upload,
  X,
} from 'lucide-vue-next';
import { employeeApi, type AttendanceExceptionResponse, type EmployeeFileFolderResponse, type EmployeeFileResponse, type EntityId, type LeaveAccountResponse } from '@/api';
import { usePluginsStore } from '@/stores/plugins';
import { useWorkbenchSectionContext } from '../employee-shell-context';
import { useWorkbenchState } from '@/composables/useWorkbenchState';
import { usePersonalFileTransfer } from '@/composables/usePersonalFileTransfer';
import {
  actionTypeLabel,
  bizTypeLabel,
  displayField,
  formatDateTime,
  humanizeText,
  minutesToDayLabel,
  parseAgentPreReviewDecision,
  punchTypeLabel,
  reviewModeLabel,
  riskLevelLabel,
  statusText,
  toneOfStatus,
} from '@/utils/employee-format';
import { fileIconOf, fileToneOf } from '@/utils/file-record';
import { fileSizeLabel, fileTypeLabelFromName } from '@/utils/file-format';
import { trimFormValue } from '@/utils/form-values';
import type { PersonalFileRecord } from '@/stores/shell';
import AppButton from '../../components/ui/AppButton.vue';
import AppEmptyState from '../../components/ui/AppEmptyState.vue';
import AppErrorState from '../../components/ui/AppErrorState.vue';
import AppDialog from '../../components/ui/AppDialog.vue';
import AppLoadingState from '../../components/ui/AppLoadingState.vue';
import AppSkeleton from '../../components/ui/AppSkeleton.vue';
import AppTag from '../../components/ui/AppTag.vue';
import WorkbenchToday from '../../components/WorkbenchToday.vue';
import PluginWorkbenchHost from '../../components/plugins/PluginWorkbenchHost.vue';

const {
  shell,
  session,
  showToast,
} = useWorkbenchSectionContext();
const router = useRouter();
const plugins = usePluginsStore();

const {
  leaveAccounts,
  selectableLeaveTypes,
  attendanceExceptions,
  leaveForm,
  leaveAttachmentIds,
  leaveSubmitting,
  leaveResult,
  leaveError,
  workbenchLoading,
  workbenchError,
  workflowRequests,
  workflowRequestsLoading,
  workflowRequestsError,
  selectedRequestId,
  workflowDetail,
  workflowActions,
  workflowDetailLoading,
  workflowDetailError,
  withdrawComment,
  withdrawSubmitting,
  attendanceDailyResults,
  attendanceLoading,
  attendanceError,
  punchLoading,
  punchResult,
  todayKey,
  todayResult,
  todayExceptions,
  latestDailyResult,
  clockCorrectionForm,
  clockCorrectionAttachmentIds,
  clockCorrectionSubmitting,
  clockCorrectionResult,
  clockCorrectionError,
  activeFile,
  activeWorkbench,
  canSubmitLeave,
  activeWorkbenchTitle,
  workflowDetailRows,
  attendanceSummary,
  attendanceRiskSummary,
  teamAttendanceRows,
  submitLeaveRequest,
  refreshActiveWorkbench,
  loadWorkflowRequests,
  loadWorkflowRequestDetail,
  previewWorkflowAttachment,
  downloadWorkflowAttachment,
  withdrawWorkflowRequest,
  refreshAttendancePanel,
  punchAttendance,
  openClockCorrectionFromException,
  submitClockCorrectionRequest,
  workflowFilePreview,
  workflowFilePreviewLoading,
  workflowFilePreviewTitle,
  workflowFilePreviewType,
  closeWorkflowFilePreview,
  openWorkflowPreviewExternal,
} = useWorkbenchState({
  shell,
  session,
  showToast,
});

function backToList() {
  shell.setMobileView('list');
}

type AttachmentPickerTarget = 'leave' | 'clock' | 'generic';
type WorkflowBucketKey = 'pending' | 'active' | 'finished';
type OaProcessAvailability = 'enabled' | 'planned';
type OaProcessTone = 'primary' | 'success' | 'warning' | 'neutral';
type GenericOaFieldType = 'text' | 'date' | 'datetime' | 'time' | 'money' | 'number' | 'select' | 'textarea' | 'attachment';

interface GenericOaFieldOption {
  value: string;
  label: string;
}

interface GenericOaField {
  key: string;
  label: string;
  type: GenericOaFieldType;
  required?: boolean;
  options?: GenericOaFieldOption[];
  placeholder?: string;
}

interface OaProcessDefinition {
  key: string;
  processCode?: string;
  title: string;
  statusLabel: string;
  statusTone: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
  availability: OaProcessAvailability;
  tone: OaProcessTone;
  icon: Component;
  formFields?: GenericOaField[];
}

const attachmentPickerOpen = ref(false);
const attachmentPickerTarget = ref<AttachmentPickerTarget>('leave');
const attachmentPickerFolderId = ref<EntityId | null>(null);
const attachmentPickerKeyword = ref('');
const attachmentPickerType = ref('all');
const attachmentPickerLoading = ref(false);
const attachmentPickerError = ref('');
const attachmentPickerFolders = ref<EmployeeFileFolderResponse[]>([]);
const attachmentPickerFiles = ref<PersonalFileRecord[]>([]);
const attachmentPickerBreadcrumbs = ref<EmployeeFileFolderResponse[]>([]);
const attachmentUploadInput = ref<HTMLInputElement | null>(null);
const attachmentUploading = ref(false);
const attachmentUploadError = ref('');
const leaveDialogOpen = ref(false);
const clockCorrectionDialogOpen = ref(false);
const genericOaDialogOpen = ref(false);
const activeGenericProcess = ref<OaProcessDefinition | null>(null);
const genericOaForm = ref<Record<string, string | number>>({});
const genericOaAttachmentIds = ref<Array<number | string>>([]);
const genericOaSubmitting = ref(false);
const genericOaError = ref('');
const genericOaResult = ref('');
const processCatalogOpen = ref(false);
const workflowBucket = ref<WorkflowBucketKey>('active');
const workflowBucketInitialized = ref(false);
let attachmentSearchTimer: number | undefined;
const { resolveDownloadUrl, uploadPersonalAttachment } = usePersonalFileTransfer();

const oaProcessCatalog: OaProcessDefinition[] = [
  {
    key: 'leave',
    title: '请假申请',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'primary',
    icon: CalendarDays,
  },
  {
    key: 'clockCorrection',
    title: '补卡申请',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'warning',
    icon: Clock3,
  },
  {
    key: 'expense',
    processCode: 'EXPENSE',
    title: '费用报销',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'success',
    icon: ReceiptText,
    formFields: [
      {
        key: 'expenseCategory',
        label: '费用类型',
        type: 'select',
        required: true,
        options: [
          { value: 'TRAVEL', label: '差旅费' },
          { value: 'TRANSPORT', label: '交通费' },
          { value: 'ENTERTAINMENT', label: '招待费' },
          { value: 'OFFICE', label: '办公费' },
          { value: 'OTHER', label: '其他' },
        ],
      },
      { key: 'amount', label: '报销金额', type: 'money', required: true },
      { key: 'occurredAt', label: '发生日期', type: 'date', required: true },
      { key: 'reason', label: '报销事由', type: 'textarea', required: true },
      { key: 'invoiceAttachments', label: '发票/凭证', type: 'attachment', required: true },
    ],
  },
  {
    key: 'businessTrip',
    processCode: 'BUSINESS_TRIP',
    title: '出差申请',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'primary',
    icon: Plane,
    formFields: [
      { key: 'destination', label: '出差地点', type: 'text', required: true },
      { key: 'startAt', label: '开始时间', type: 'datetime', required: true },
      { key: 'endAt', label: '结束时间', type: 'datetime', required: true },
      { key: 'budgetAmount', label: '预计预算', type: 'money' },
      { key: 'reason', label: '出差事由', type: 'textarea', required: true },
    ],
  },
  {
    key: 'overtime',
    processCode: 'OVERTIME',
    title: '加班申请',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'warning',
    icon: Timer,
    formFields: [
      { key: 'overtimeDate', label: '加班日期', type: 'date', required: true },
      { key: 'startAt', label: '开始时间', type: 'time', required: true },
      { key: 'endAt', label: '结束时间', type: 'time', required: true },
      {
        key: 'compensationMode',
        label: '补偿方式',
        type: 'select',
        required: true,
        options: [
          { value: 'TIME_OFF', label: '调休' },
          { value: 'PAY', label: '加班费' },
          { value: 'ATTENDANCE_ONLY', label: '仅计入考勤' },
        ],
      },
      { key: 'reason', label: '加班原因', type: 'textarea', required: true },
    ],
  },
  {
    key: 'seal',
    processCode: 'SEAL',
    title: '用章申请',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'neutral',
    icon: Stamp,
    formFields: [
      {
        key: 'sealType',
        label: '印章类型',
        type: 'select',
        required: true,
        options: [
          { value: 'COMPANY', label: '公章' },
          { value: 'CONTRACT', label: '合同章' },
          { value: 'LEGAL_REPRESENTATIVE', label: '法人章' },
          { value: 'FINANCE', label: '财务章' },
          { value: 'DEPARTMENT', label: '部门章' },
        ],
      },
      { key: 'documentName', label: '文件名称', type: 'text', required: true },
      { key: 'counterparty', label: '相对方', type: 'text' },
      { key: 'purpose', label: '用章用途', type: 'textarea', required: true },
      { key: 'documentAttachments', label: '用章文件', type: 'attachment', required: true },
    ],
  },
  {
    key: 'purchase',
    processCode: 'PURCHASE',
    title: '采购申请',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'success',
    icon: ShoppingCart,
    formFields: [
      { key: 'itemName', label: '采购内容', type: 'text', required: true },
      { key: 'quantity', label: '数量', type: 'number', required: true },
      { key: 'budgetAmount', label: '预算金额', type: 'money', required: true },
      { key: 'supplier', label: '建议供应商', type: 'text' },
      { key: 'reason', label: '采购原因', type: 'textarea', required: true },
    ],
  },
  {
    key: 'certificate',
    processCode: 'CERTIFICATE',
    title: '证明开具',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'neutral',
    icon: FileCheck2,
    formFields: [
      {
        key: 'certificateType',
        label: '证明类型',
        type: 'select',
        required: true,
        options: [
          { value: 'EMPLOYMENT', label: '在职证明' },
          { value: 'INCOME', label: '收入证明' },
          { value: 'INTERNSHIP', label: '实习证明' },
          { value: 'OTHER', label: '其他证明' },
        ],
      },
      { key: 'recipient', label: '接收方', type: 'text' },
      {
        key: 'deliveryMode',
        label: '交付方式',
        type: 'select',
        required: true,
        options: [
          { value: 'ELECTRONIC', label: '电子版' },
          { value: 'PAPER', label: '纸质版' },
        ],
      },
      { key: 'purpose', label: '申请用途', type: 'textarea', required: true },
      { key: 'supportingAttachments', label: '补充材料', type: 'attachment' },
    ],
  },
  {
    key: 'borrow',
    processCode: 'BORROW',
    title: '备用金借款',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'success',
    icon: CreditCard,
    formFields: [
      { key: 'amount', label: '借款金额', type: 'money', required: true },
      { key: 'useDate', label: '使用日期', type: 'date', required: true },
      { key: 'expectedRepayAt', label: '预计归还日期', type: 'date', required: true },
      { key: 'purpose', label: '借款用途', type: 'textarea', required: true },
      { key: 'supportingAttachments', label: '预算/说明附件', type: 'attachment' },
    ],
  },
  {
    key: 'project',
    processCode: 'PROJECT',
    title: '项目立项',
    statusLabel: '可提交',
    statusTone: 'success',
    availability: 'enabled',
    tone: 'primary',
    icon: BriefcaseBusiness,
    formFields: [
      { key: 'projectName', label: '项目名称', type: 'text', required: true },
      { key: 'startAt', label: '开始日期', type: 'date', required: true },
      { key: 'endAt', label: '结束日期', type: 'date', required: true },
      { key: 'budgetAmount', label: '项目预算', type: 'money', required: true },
      { key: 'goal', label: '立项目标', type: 'textarea', required: true },
      { key: 'projectAttachments', label: '立项材料', type: 'attachment' },
    ],
  },
];

const BUILT_IN_WORKBENCH_IDS = new Set(['oa', 'attendance', 'balance']);

const activePlugin = computed(() => {
  const id = shell.active.workbench;
  if (BUILT_IN_WORKBENCH_IDS.has(id)) return null;
  const entry = plugins.manifest.find((p) => p.id === id);
  if (!entry || !plugins.loadedIds.includes(id)) return null;
  return entry;
});

const currentAttachmentIds = computed(() => attachmentIdsFor(attachmentPickerTarget.value));
const currentAttachmentIdSet = computed(() => new Set(currentAttachmentIds.value.map((item) => String(item))));
const activeGenericFields = computed(() => activeGenericProcess.value?.formFields ?? []);
const genericOaSubmitDisabled = computed(() => genericOaSubmitting.value || !activeGenericProcess.value);
const leaveAvailableMinutes = computed(() => leaveAccounts.value.reduce((sum, account) => sum + Math.max(0, account.availableMinutes ?? 0), 0));
const leaveFrozenMinutes = computed(() => leaveAccounts.value.reduce((sum, account) => sum + Math.max(0, account.frozenMinutes ?? 0), 0));
const activeLeaveAccountCount = computed(() => leaveAccounts.value.filter((account) => account.status === 'ACTIVE').length);
const frozenLeaveAccountCount = computed(() => leaveAccounts.value.filter((account) => (account.frozenMinutes ?? 0) > 0).length);
const recentLeaveAccounts = computed(() => leaveAccounts.value.filter((account) => account.lastChangeMinutes !== undefined && account.lastChangeMinutes !== null));
const workflowBuckets = computed(() => [
  { key: 'pending' as const, label: '待处理', count: workflowRequests.value.filter((request) => workflowBucketOf(request.status) === 'pending').length },
  { key: 'active' as const, label: '进行中', count: workflowRequests.value.filter((request) => workflowBucketOf(request.status) === 'active').length },
  { key: 'finished' as const, label: '已结束', count: workflowRequests.value.filter((request) => workflowBucketOf(request.status) === 'finished').length },
]);
const activeWorkflowBucket = computed(() => workflowBuckets.value.find((item) => item.key === workflowBucket.value) ?? workflowBuckets.value[1]);
const filteredWorkflowRequests = computed(() => workflowRequests.value.filter((request) => workflowBucketOf(request.status) === workflowBucket.value));
const activeWorkflowDetail = computed(() => (
  workflowDetail.value && workflowBucketOf(workflowDetail.value.status) === workflowBucket.value ? workflowDetail.value : null
));
const activeWorkflowAttachments = computed(() => activeWorkflowDetail.value?.attachments ?? []);
const activeWorkflowCanWithdraw = computed(() => activeWorkflowDetail.value?.status === 'IN_APPROVAL');

function leaveAccountTone(account: LeaveAccountResponse) {
  if (account.status !== 'ACTIVE') return 'neutral';
  if ((account.frozenMinutes ?? 0) > 0) return 'warning';
  return 'success';
}

function leaveAccountToneClass(index: number) {
  return ['blue', 'green', 'amber', 'gray'][index % 4];
}

function leaveAccountApplyText(account: LeaveAccountResponse) {
  if (account.status !== 'ACTIVE') return statusText(account.status);
  if (account.balanceMode === 'NO_DEDUCT') return '可申请';
  return (account.availableMinutes ?? 0) > 0 ? '可申请' : '余额不足';
}

function leaveAccountChangeTone(account: LeaveAccountResponse) {
  if ((account.lastChangeMinutes ?? 0) > 0) return 'positive';
  if ((account.lastChangeMinutes ?? 0) < 0) return 'negative';
  return 'neutral';
}

function leaveBalanceModeText(account: LeaveAccountResponse) {
  return account.balanceMode === 'NO_DEDUCT' ? '不扣余额' : '按时长扣减';
}

async function openLeaveInOa() {
  shell.selectItem('workbench', 'oa');
  await router.push({ name: 'employee-workbench-detail', params: { app: 'oa' } });
  await openLeaveDialog();
}

function openProcessCatalog() {
  processCatalogOpen.value = true;
}

function closeProcessCatalog() {
  processCatalogOpen.value = false;
}

function handleOaProcess(process: OaProcessDefinition) {
  if (process.key === 'leave') {
    processCatalogOpen.value = false;
    void openLeaveDialog();
    return;
  }
  if (process.key === 'clockCorrection') {
    processCatalogOpen.value = false;
    openClockCorrectionDialog();
    return;
  }
  if (process.processCode && process.formFields?.length) {
    openGenericOaDialog(process);
    return;
  }
  showToast(`${process.title}流程模板待发布`);
}

function workflowBucketOf(status: string): WorkflowBucketKey {
  if (status === 'DRAFT' || status === 'PENDING') return 'pending';
  if (status === 'PENDING_REVIEW' || status === 'IN_APPROVAL') return 'active';
  return 'finished';
}

function setWorkflowBucket(bucket: WorkflowBucketKey) {
  workflowBucket.value = bucket;
  const firstRequest = workflowRequests.value.find((request) => workflowBucketOf(request.status) === bucket);
  if (firstRequest) void loadWorkflowRequestDetail(firstRequest.requestId);
}

function initializeWorkflowBucket() {
  if (workflowBucketInitialized.value || !workflowRequests.value.length) return;
  workflowBucketInitialized.value = true;
  const bucket = workflowBuckets.value.find((item) => item.count > 0)?.key ?? 'active';
  workflowBucket.value = bucket;
  const firstRequest = workflowRequests.value.find((request) => workflowBucketOf(request.status) === bucket);
  if (firstRequest && String(firstRequest.requestId) !== String(selectedRequestId.value ?? '')) {
    void loadWorkflowRequestDetail(firstRequest.requestId);
  }
}

function mapPickerFile(file: EmployeeFileResponse): PersonalFileRecord {
  const name = file.displayName || file.originalFileName || '未命名文件';
  return {
    id: `personal-file-${file.attachmentRefId}`,
    name,
    type: fileTypeLabelFromName(name, file.mimeType ?? ''),
    size: fileSizeLabel(file.fileSize),
    createdAt: formatDateTime(file.createdAt),
    status: statusText(file.scanStatus),
    attachmentRefId: file.attachmentRefId,
    folderId: file.folderId ?? null,
    mimeType: file.mimeType ?? undefined,
  };
}

function attachmentIdsFor(target: AttachmentPickerTarget) {
  if (target === 'leave') return leaveAttachmentIds.value;
  if (target === 'clock') return clockCorrectionAttachmentIds.value;
  return genericOaAttachmentIds.value;
}

function setAttachmentIdsFor(target: AttachmentPickerTarget, ids: Array<string | number>) {
  if (target === 'leave') {
    leaveAttachmentIds.value = ids;
  } else if (target === 'clock') {
    clockCorrectionAttachmentIds.value = ids;
  } else {
    genericOaAttachmentIds.value = ids;
  }
}

function selectedAttachmentFiles(target: AttachmentPickerTarget) {
  const ids = attachmentIdsFor(target);
  return ids.map((id) => (
    activeFile.value.files.find((file) => String(file.attachmentRefId) === String(id))
    ?? attachmentPickerFiles.value.find((file) => String(file.attachmentRefId) === String(id))
    ?? {
      id: `selected-${id}`,
      name: '已选附件',
      type: '文件',
      size: '',
      createdAt: '',
      status: '',
      attachmentRefId: id,
    }
  ));
}

const workflowProgressSteps = computed(() => {
  const detail = workflowDetail.value;
  const hasAgentAction = workflowActions.value.some((action) => action.actionType === 'AGENT_PRE_REVIEW');
  const currentStepKey = detail?.currentStepKey ?? '';
  const terminal = detail ? ['APPROVED', 'REJECTED', 'WITHDRAWN'].includes(detail.status) : false;
  return [
    { key: 'submitted', label: '提交申请', note: detail ? '已提交' : '待提交', state: detail ? 'done' : 'todo' },
    {
      key: 'agent_pre_review',
      label: 'AI 预审',
      note: currentStepKey === 'agent_pre_review' ? '当前节点' : (hasAgentAction ? '已通过' : '未触发'),
      state: currentStepKey === 'agent_pre_review' ? 'active' : (hasAgentAction ? 'done' : 'muted'),
    },
    {
      key: 'manager_approval',
      label: '直属上级审批',
      note: currentStepKey === 'manager_approval' ? '当前节点' : (terminal ? '已处理' : '待进入'),
      state: currentStepKey === 'manager_approval' ? 'active' : (terminal ? 'done' : 'todo'),
    },
    { key: 'finished', label: terminal ? statusText(detail?.status ?? '') : '完成', note: terminal ? '已结束' : '未完成', state: terminal ? 'done' : 'todo' },
  ];
});

const workflowActionItems = computed(() => workflowActions.value.map((action) => {
  const actionLabel = actionTypeLabel(action.actionType);
  const stepLabel = humanizeText(displayField(action.stepName));
  const sameLabel = actionLabel.replace(/\s+/g, '') === stepLabel.replace(/\s+/g, '');
  return {
    action,
    meta: sameLabel ? formatDateTime(action.actedAt) : `${stepLabel} · ${formatDateTime(action.actedAt)}`,
    agentDecision: action.actionType === 'AGENT_PRE_REVIEW' ? parseAgentPreReviewDecision(action.agentDecisionJson) : null,
  };
}));

function agentDecisionCheckValue(result: string, status: string) {
  if (!result) return statusText(status);
  return /^\d{4}-\d{2}-\d{2}T/.test(result) ? formatDateTime(result) : displayField(result);
}

async function loadAttachmentPickerFiles() {
  attachmentPickerLoading.value = true;
  attachmentPickerError.value = '';
  try {
    const browser = await employeeApi.files.browser({
      folderId: attachmentPickerFolderId.value ?? undefined,
      keyword: attachmentPickerKeyword.value.trim() || undefined,
      fileType: attachmentPickerType.value,
    });
    attachmentPickerFolders.value = browser.folders;
    attachmentPickerFiles.value = browser.files.map(mapPickerFile);
    attachmentPickerBreadcrumbs.value = browser.breadcrumbs;
  } catch (error) {
    attachmentPickerError.value = error instanceof Error ? error.message : '附件加载失败';
  } finally {
    attachmentPickerLoading.value = false;
  }
}

function openAttachmentPicker(target: AttachmentPickerTarget) {
  attachmentPickerTarget.value = target;
  attachmentPickerOpen.value = true;
  attachmentUploadError.value = '';
  void loadAttachmentPickerFiles();
}

function closeAttachmentPicker() {
  attachmentPickerOpen.value = false;
}

function openAttachmentFolder(folderId: EntityId | null) {
  attachmentPickerFolderId.value = folderId;
}

function toggleAttachment(file: PersonalFileRecord) {
  if (!file.attachmentRefId) return;
  const target = attachmentPickerTarget.value;
  const ids = attachmentIdsFor(target);
  const key = String(file.attachmentRefId);
  if (ids.some((item) => String(item) === key)) {
    setAttachmentIdsFor(target, ids.filter((item) => String(item) !== key));
  } else {
    setAttachmentIdsFor(target, [...ids, file.attachmentRefId]);
  }
}

function removeAttachment(target: AttachmentPickerTarget, attachmentRefId?: EntityId) {
  if (!attachmentRefId) return;
  setAttachmentIdsFor(target, attachmentIdsFor(target).filter((item) => String(item) !== String(attachmentRefId)));
}

async function openLeaveDialog() {
  leaveError.value = '';
  if (!canSubmitLeave.value && !workbenchLoading.value) {
    await refreshActiveWorkbench();
  }
  leaveDialogOpen.value = true;
}

async function submitLeaveRequestFromDialog() {
  await submitLeaveRequest();
  if (!leaveError.value && leaveResult.value) {
    leaveDialogOpen.value = false;
  }
}

function openClockCorrectionDialog(exception?: AttendanceExceptionResponse) {
  openClockCorrectionFromException(exception);
  clockCorrectionDialogOpen.value = true;
}

async function submitClockCorrectionFromDialog() {
  await submitClockCorrectionRequest();
  if (!clockCorrectionError.value && clockCorrectionResult.value) {
    clockCorrectionDialogOpen.value = false;
  }
}

function openGenericOaDialog(process: OaProcessDefinition) {
  activeGenericProcess.value = process;
  genericOaForm.value = {};
  genericOaAttachmentIds.value = [];
  genericOaError.value = '';
  genericOaResult.value = '';
  for (const field of process.formFields ?? []) {
    if (field.type !== 'attachment') {
      genericOaForm.value[field.key] = field.options?.[0]?.value ?? '';
    }
  }
  processCatalogOpen.value = false;
  genericOaDialogOpen.value = true;
}

function closeGenericOaDialog() {
  if (genericOaSubmitting.value) return;
  genericOaDialogOpen.value = false;
  activeGenericProcess.value = null;
  genericOaError.value = '';
  genericOaResult.value = '';
}

function genericFieldDisplayValue(field: GenericOaField, value: string) {
  if (field.type === 'select') {
    return field.options?.find((item) => item.value === value)?.label ?? value;
  }
  return value;
}

function genericFormValue(key: string) {
  return trimFormValue(genericOaForm.value[key]);
}

function genericOaSummary(process: OaProcessDefinition) {
  const fields = process.formFields ?? [];
  const reasonField = fields.find((field) => ['reason', 'purpose'].includes(field.key));
  const primaryField = reasonField ?? fields.find((field) => field.type !== 'attachment');
  const value = primaryField ? genericFieldDisplayValue(primaryField, genericFormValue(primaryField.key)) : '';
  return value || process.title;
}

async function submitGenericOaRequestFromDialog() {
  const process = activeGenericProcess.value;
  if (!process?.processCode || genericOaSubmitting.value) return;
  genericOaError.value = '';
  genericOaResult.value = '';
  const fields = process.formFields ?? [];
  for (const field of fields) {
    if (!field.required) continue;
    if (field.type === 'attachment') {
      if (!genericOaAttachmentIds.value.length) {
        genericOaError.value = `请上传或选择${field.label}`;
        return;
      }
      continue;
    }
    if (!genericFormValue(field.key)) {
      genericOaError.value = `请填写${field.label}`;
      return;
    }
  }
  const formFields = fields
    .filter((field) => field.type !== 'attachment')
    .map((field) => {
      const value = genericFormValue(field.key);
      return {
        key: field.key,
        label: field.label,
        type: field.type,
        value,
        displayValue: genericFieldDisplayValue(field, value),
      };
    });
  genericOaSubmitting.value = true;
  try {
    const result = await employeeApi.workflow.submitGenericRequest({
      processCode: process.processCode,
      title: process.title,
      summary: genericOaSummary(process),
      formPayload: {
        processKey: process.key,
        processTitle: process.title,
        fields: formFields,
      },
      attachmentRefIds: genericOaAttachmentIds.value,
    });
    genericOaResult.value = result.statusName || statusText(result.status);
    genericOaDialogOpen.value = false;
    activeGenericProcess.value = null;
    genericOaForm.value = {};
    genericOaAttachmentIds.value = [];
    await loadWorkflowRequests();
    showToast(`${process.title}已提交`);
  } catch (error) {
    genericOaError.value = error instanceof Error ? error.message : '提交失败';
  } finally {
    genericOaSubmitting.value = false;
  }
}

async function selectWorkflowRequest(requestId: EntityId) {
  await loadWorkflowRequestDetail(requestId);
}

function openAttachmentUpload() {
  attachmentUploadInput.value?.click();
}

async function handleAttachmentUploadSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || attachmentUploading.value) return;
  attachmentUploading.value = true;
  attachmentUploadError.value = '';
  try {
    const attachment = await uploadPersonalAttachment(file, 'workflow-attachment', attachmentPickerFolderId.value);
    const uploadedFile: PersonalFileRecord = {
      id: `workflow-upload-${attachment.attachmentRefId}`,
      name: attachment.displayName || file.name,
      type: fileTypeLabelFromName(attachment.displayName || file.name, attachment.mimeType || file.type),
      size: fileSizeLabel(attachment.fileSize || file.size),
      createdAt: attachment.createdAt ? formatDateTime(attachment.createdAt) : '',
      status: attachment.scanStatus ? statusText(attachment.scanStatus) : '已确认',
      attachmentRefId: attachment.attachmentRefId,
      folderId: attachmentPickerFolderId.value,
      mimeType: attachment.mimeType || file.type,
    };
    attachmentPickerFiles.value = [
      uploadedFile,
      ...attachmentPickerFiles.value.filter((item) => String(item.attachmentRefId) !== String(attachment.attachmentRefId)),
    ];
    const ids = attachmentIdsFor(attachmentPickerTarget.value);
    if (!ids.some((id) => String(id) === String(attachment.attachmentRefId))) {
      setAttachmentIdsFor(attachmentPickerTarget.value, [...ids, attachment.attachmentRefId]);
    }
    await loadAttachmentPickerFiles();
    if (!attachmentPickerFiles.value.some((item) => String(item.attachmentRefId) === String(attachment.attachmentRefId))) {
      attachmentPickerFiles.value = [uploadedFile, ...attachmentPickerFiles.value];
    }
    showToast('附件已上传');
  } catch (error) {
    attachmentUploadError.value = error instanceof Error ? error.message : '附件上传失败';
  } finally {
    attachmentUploading.value = false;
  }
}

watch([attachmentPickerFolderId, attachmentPickerType], () => {
  if (attachmentPickerOpen.value) void loadAttachmentPickerFiles();
});

watch(attachmentPickerKeyword, () => {
  if (!attachmentPickerOpen.value) return;
  if (attachmentSearchTimer) window.clearTimeout(attachmentSearchTimer);
  attachmentSearchTimer = window.setTimeout(() => {
    void loadAttachmentPickerFiles();
  }, 240);
});

watch(workflowRequests, () => {
  initializeWorkflowBucket();
});
</script>

<template>
<div class="workspace generic-workspace">
  <header class="workspace-head">
    <div class="workspace-title-group">
      <button class="mobile-back" type="button" aria-label="返回列表" @click="backToList">
        <ChevronLeft :size="22" />
      </button>
      <div class="workspace-head-text">
        <h2 class="workspace-title">{{ activePlugin ? activePlugin.name : activeWorkbenchTitle }}</h2>
      </div>
    </div>
    <div class="head-actions">
      <AppButton v-if="!activePlugin && activeWorkbench.id === 'oa'" variant="secondary" size="sm" :disabled="workflowRequestsLoading" @click="loadWorkflowRequests">
        {{ workflowRequestsLoading ? '刷新中' : '刷新' }}
      </AppButton>
      <AppButton v-if="!activePlugin && activeWorkbench.id === 'attendance'" variant="secondary" size="sm" :disabled="attendanceLoading" @click="refreshAttendancePanel">
        {{ attendanceLoading ? '刷新中' : '刷新' }}
      </AppButton>
    </div>
  </header>

  <section class="content-stack">
    <PluginWorkbenchHost v-if="activePlugin" :plugin-id="activePlugin.id" />
    <template v-else-if="activeWorkbench.id === 'today'">
      <WorkbenchToday
        :display-name="session.displayName"
        :hire-date="session.profile?.hireDate"
        :today-result="todayResult"
        :attendance-daily-results="attendanceDailyResults"
        :workflow-requests="workflowRequests"
        :leave-accounts="leaveAccounts"
        :todos="shell.todos"
        :conversations="shell.conversations"
        @open-workbench="(id) => shell.selectItem('workbench', id)"
        @open-section="(id) => shell.setSection(id as any)"
      />
    </template>
    <template v-else-if="activeWorkbench.id === 'oa'">
      <AppErrorState
        v-if="workbenchError"
        title="工作台数据加载失败"
        :description="workbenchError"
        action-label="重试"
        @retry="refreshActiveWorkbench"
      />
      <AppSkeleton v-else-if="workbenchLoading" variant="list" :count="4" />
      <template v-else>
        <section class="oa-workbench">
          <aside class="oa-status-rail" aria-label="申请状态">
            <button
              v-for="bucket in workflowBuckets"
              :key="bucket.key"
              type="button"
              :class="{ active: bucket.key === workflowBucket }"
              :aria-pressed="bucket.key === workflowBucket"
              @click="setWorkflowBucket(bucket.key)"
            >
              <span>{{ bucket.label }}</span>
              <strong>{{ bucket.count }}</strong>
            </button>
          </aside>

          <section class="oa-request-panel">
            <header class="oa-panel-head">
              <div>
                <h3>我的申请</h3>
              </div>
              <AppButton variant="primary" size="sm" @click="openProcessCatalog">
                发起申请
              </AppButton>
            </header>
            <AppErrorState
              v-if="workflowRequestsError"
              title="申请加载失败"
              :description="workflowRequestsError"
              action-label="重试"
              @retry="loadWorkflowRequests"
            />
            <AppSkeleton v-else-if="workflowRequestsLoading" variant="list" :count="3" />
            <AppEmptyState v-else-if="!workflowRequests.length" title="暂无申请" description="发起你的第一个申请，审批进度会实时同步到这里。" icon="workbench" action-label="发起申请" @action="openProcessCatalog" />
            <AppEmptyState v-else-if="!filteredWorkflowRequests.length" :title="`暂无${activeWorkflowBucket.label}申请`" icon="workbench" />
            <div v-else class="oa-request-list">
              <button
                v-for="request in filteredWorkflowRequests"
                :key="request.requestId"
                type="button"
                :class="{ active: request.requestId === selectedRequestId }"
                :aria-current="request.requestId === selectedRequestId"
                @click="selectWorkflowRequest(request.requestId)"
              >
                <div class="oa-request-row-main">
                  <span>{{ request.bizTypeName || bizTypeLabel(request.bizType) }}</span>
                  <strong>{{ request.title }}</strong>
                  <AppTag :tone="toneOfStatus(request.status)">{{ request.statusName || statusText(request.status) }}</AppTag>
                </div>
                <div class="oa-request-row-meta">
                  <span>{{ displayField(request.currentStepName) }}</span>
                  <span>{{ formatDateTime(request.submittedAt) }}</span>
                  <span v-if="request.finishedAt">{{ formatDateTime(request.finishedAt) }}</span>
                </div>
              </button>
            </div>

          </section>

          <section class="oa-detail-panel">
            <header class="oa-detail-head">
              <div>
                <h3>{{ activeWorkflowDetail?.title ?? '申请详情' }}</h3>
                <span v-if="activeWorkflowDetail">{{ activeWorkflowDetail.bizTypeName || bizTypeLabel(activeWorkflowDetail.bizType) }} · {{ displayField(activeWorkflowDetail.currentStepName) }}</span>
              </div>
              <div class="request-panel-actions">
                <AppTag v-if="activeWorkflowDetail" :tone="toneOfStatus(activeWorkflowDetail.status)">{{ activeWorkflowDetail.statusName || statusText(activeWorkflowDetail.status) }}</AppTag>
              </div>
            </header>
            <AppLoadingState v-if="workflowDetailLoading" />
            <AppEmptyState v-else-if="!activeWorkflowDetail" title="选择一条申请查看详情" icon="workbench" />
            <div v-else class="request-detail-stack oa-detail-body">
              <div class="workflow-node-map" aria-label="流程进度">
                <span
                  v-for="step in workflowProgressSteps"
                  :key="step.key"
                  :class="`state-${step.state}`"
                >
                  <i />
                  <strong>{{ step.label }}</strong>
                  <small>{{ step.note }}</small>
                </span>
              </div>
              <dl class="field-grid">
                <div v-for="row in workflowDetailRows" :key="row[0]">
                  <dt>{{ row[0] }}</dt>
                  <dd>{{ row[1] }}</dd>
                </div>
              </dl>
              <div v-if="activeWorkflowAttachments.length" class="oa-attachment-list">
                <div v-for="file in activeWorkflowAttachments" :key="file.attachmentRefId" class="oa-attachment-row">
                  <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
                    <component :is="fileIconOf(file)" :size="18" />
                  </span>
                  <div class="oa-attachment-copy">
                    <strong>{{ file.displayName }}</strong>
                    <small>{{ fileTypeLabelFromName(file.displayName, file.mimeType) }} · {{ fileSizeLabel(file.fileSize) }} · {{ file.scanStatusName || statusText(file.scanStatus) }}</small>
                  </div>
                  <div class="oa-attachment-actions">
                    <button type="button" @click="previewWorkflowAttachment(file)">
                      <Eye :size="15" />
                      预览
                    </button>
                    <button type="button" @click="downloadWorkflowAttachment(file)">
                      <Download :size="15" />
                      下载
                    </button>
                  </div>
                </div>
              </div>
              <div class="timeline-list">
                <span v-for="item in workflowActionItems" :key="item.action.actionId">
                  <strong>{{ actionTypeLabel(item.action.actionType) }}</strong>
                  <small>{{ item.meta }}</small>
                  <em v-if="item.action.comment">{{ humanizeText(item.action.comment) }}</em>
                  <div v-if="item.agentDecision" class="agent-decision-panel">
                    <div class="agent-decision-head">
                      <strong>{{ item.agentDecision.conclusion || statusText(item.agentDecision.decision) }}</strong>
                      <small v-if="riskLevelLabel(item.agentDecision.riskLevel)">风险：{{ riskLevelLabel(item.agentDecision.riskLevel) }}</small>
                      <small v-if="reviewModeLabel(item.agentDecision.reviewMode)">{{ reviewModeLabel(item.agentDecision.reviewMode) }}</small>
                    </div>
                    <p v-if="item.agentDecision.reason">{{ humanizeText(item.agentDecision.reason) }}</p>
                    <p v-if="item.agentDecision.suggestion">建议：{{ humanizeText(item.agentDecision.suggestion) }}</p>
                    <ul v-if="item.agentDecision.checks.length">
                      <li v-for="check in item.agentDecision.checks" :key="`${check.name}-${check.result}`">
                        <span>{{ displayField(check.name) }}</span>
                        <em>{{ agentDecisionCheckValue(check.result, check.status) }}</em>
                      </li>
                    </ul>
                  </div>
                </span>
              </div>
              <div v-if="activeWorkflowCanWithdraw" class="withdraw-box">
                <textarea v-model="withdrawComment" class="ui-textarea compact-textarea" placeholder="撤回原因" rows="3" />
                <AppButton variant="secondary" size="sm" :disabled="withdrawSubmitting" @click="withdrawWorkflowRequest">
                  {{ withdrawSubmitting ? '撤回中' : '撤回申请' }}
                </AppButton>
              </div>
              <p v-if="workflowDetailError" class="dialog-error">{{ workflowDetailError }}</p>
            </div>
          </section>
        </section>
      </template>
    </template>

    <template v-else-if="activeWorkbench.id === 'attendance'">
      <section class="attendance-command-grid">
        <article class="detail-card attendance-today-card">
          <div class="attendance-card-head">
            <div>
              <h3>今日考勤</h3>
              <span>{{ todayKey }}</span>
            </div>
            <AppTag v-if="todayResult" :tone="todayExceptions.length ? 'warning' : 'success'">{{ statusText(todayResult.resultStatus) }}</AppTag>
          </div>
          <div class="attendance-status-panel">
            <strong>{{ todayExceptions.length ? '今日异常' : (todayResult ? '今日正常' : '等待打卡') }}</strong>
            <span v-if="todayResult">迟到 {{ todayResult.lateMinutes ?? 0 }} · 早退 {{ todayResult.earlyLeaveMinutes ?? 0 }} · 缺勤 {{ todayResult.absenceMinutes ?? 0 }}</span>
            <span v-else>最近记录：{{ latestDailyResult?.workDate ?? '暂无' }}</span>
          </div>
          <div class="punch-actions">
            <AppButton variant="primary" :disabled="Boolean(punchLoading)" @click="punchAttendance('ON_DUTY')">
              {{ punchLoading === 'ON_DUTY' ? '打卡中' : '上班打卡' }}
            </AppButton>
            <AppButton variant="secondary" :disabled="Boolean(punchLoading)" @click="punchAttendance('OFF_DUTY')">
              {{ punchLoading === 'OFF_DUTY' ? '打卡中' : '下班打卡' }}
            </AppButton>
          </div>
          <div v-if="punchResult" class="attendance-last-punch">
            <span>最近打卡</span>
            <strong>{{ punchTypeLabel(punchResult.punchType) }} · {{ formatDateTime(punchResult.punchAt) }}</strong>
            <small>{{ statusText(punchResult.status) }}</small>
          </div>
          <p v-if="attendanceError" class="dialog-error">{{ attendanceError }}</p>
        </article>

        <article class="detail-card exception-loop-card">
          <div class="attendance-card-head">
            <div>
              <h3>异常闭环</h3>
            </div>
            <AppButton variant="secondary" size="sm" @click="openClockCorrectionDialog()">
              补卡
            </AppButton>
          </div>
          <AppEmptyState v-if="!attendanceExceptions.length" title="今日无考勤异常" icon="workbench" />
          <div v-else class="attendance-action-list">
            <div v-for="item in attendanceExceptions.slice(0, 5)" :key="item.exceptionId" class="attendance-action-row">
              <div>
                <strong>{{ item.workDate }}</strong>
                <span>{{ item.exceptionDesc || item.exceptionType }}</span>
              </div>
              <AppTag :tone="item.status === 'DONE' ? 'success' : 'warning'">{{ statusText(item.status) }}</AppTag>
              <button type="button" @click="openClockCorrectionDialog(item)">发起补卡</button>
            </div>
          </div>
        </article>
      </section>

      <section class="highlight-grid attendance-metric-grid">
        <article v-for="row in attendanceSummary" :key="row[0]" class="highlight-card">
          <small>{{ row[0] }}</small>
          <strong>{{ row[1] }}</strong>
        </article>
      </section>

      <section class="detail-grid attendance-detail-grid">
        <article class="detail-card team-risk-card" v-if="session.subordinates.length">
          <div class="attendance-card-head">
            <div>
              <h3>团队风险</h3>
              <span>{{ attendanceRiskSummary.map((row) => `${row[0]} ${row[1]}`).join(' · ') }}</span>
            </div>
          </div>
          <AppSkeleton v-if="attendanceLoading" variant="text" :count="4" />
          <AppEmptyState v-else-if="!teamAttendanceRows.length" title="团队考勤正常" icon="workbench" />
          <div v-else class="attendance-action-list">
            <div v-for="item in teamAttendanceRows" :key="`team-${item.userId}-${item.workDate}`" class="attendance-action-row">
              <div>
                <strong>{{ item.userName }}</strong>
                <span>{{ item.workDate }} · {{ statusText(item.resultStatus) }}</span>
              </div>
              <small>迟到 {{ item.lateMinutes ?? 0 }} · 早退 {{ item.earlyLeaveMinutes ?? 0 }} · 缺勤 {{ item.absenceMinutes ?? 0 }}</small>
            </div>
          </div>
        </article>

        <article class="detail-card attendance-history-card">
          <h3>月度记录</h3>
          <AppSkeleton v-if="attendanceLoading" variant="text" :count="4" />
          <AppEmptyState v-else-if="!attendanceDailyResults.length" title="暂无月度记录" icon="workbench" />
          <div v-else class="compact-table">
            <div v-for="item in attendanceDailyResults" :key="item.dailyResultId">
              <strong>{{ item.workDate }}</strong>
              <span>{{ statusText(item.resultStatus) }}</span>
              <small>迟到 {{ item.lateMinutes ?? 0 }} · 早退 {{ item.earlyLeaveMinutes ?? 0 }} · 缺勤 {{ item.absenceMinutes ?? 0 }}</small>
            </div>
          </div>
        </article>
      </section>
    </template>

    <template v-else-if="activeWorkbench.id === 'balance'">
      <section class="leave-balance-board">
        <AppErrorState
          v-if="workbenchError"
          title="假期账户加载失败"
          :description="workbenchError"
          action-label="重试"
          @retry="refreshActiveWorkbench"
        />
        <AppSkeleton v-else-if="workbenchLoading" variant="block" :count="2" block-height="7rem" />
        <template v-else>
          <article class="leave-balance-hero">
            <div class="leave-balance-primary">
              <span>假期账户</span>
              <strong>{{ minutesToDayLabel(leaveAvailableMinutes) }}</strong>
              <small>可用余额</small>
            </div>
            <div class="leave-balance-side">
              <div class="leave-hero-metrics">
                <span>
                  <strong>{{ minutesToDayLabel(leaveFrozenMinutes) }}</strong>
                  <small>冻结余额</small>
                </span>
                <span>
                  <strong>{{ activeLeaveAccountCount }}</strong>
                  <small>可申请假种</small>
                </span>
                <span>
                  <strong>{{ leaveAccounts.length }}</strong>
                  <small>账户数</small>
                </span>
              </div>
              <AppButton variant="primary" size="sm" type="button" @click="openLeaveInOa">
                请假
              </AppButton>
            </div>
          </article>

          <div class="leave-balance-layout">
            <section class="leave-account-panel">
              <header class="leave-panel-head">
                <div>
                  <h3>假种账户</h3>
                  <span>{{ activeLeaveAccountCount }} 个可申请 · {{ frozenLeaveAccountCount }} 个冻结中</span>
                </div>
              </header>
              <section class="leave-account-grid refined">
                <article
                  v-for="(account, index) in leaveAccounts"
                  :key="account.accountId"
                  class="leave-balance-card"
                  :class="`leave-tone-${leaveAccountToneClass(index)}`"
                >
                  <div class="leave-card-head">
                    <div>
                      <small>{{ leaveBalanceModeText(account) }}</small>
                      <h3>{{ account.leaveName }}</h3>
                    </div>
                    <AppTag :tone="leaveAccountTone(account)">{{ leaveAccountApplyText(account) }}</AppTag>
                  </div>
                  <strong>{{ minutesToDayLabel(account.availableMinutes) }}</strong>
                  <dl>
                    <div>
                      <dt>冻结</dt>
                      <dd>{{ minutesToDayLabel(account.frozenMinutes) }}</dd>
                    </div>
                  </dl>
                </article>
                <AppEmptyState v-if="!leaveAccounts.length" class="leave-account-empty" title="暂无假期账户" icon="workbench" />
              </section>
            </section>

            <aside class="leave-change-panel">
              <header class="leave-panel-head">
                <div>
                  <h3>近期变动</h3>
                </div>
              </header>
              <div v-if="recentLeaveAccounts.length" class="leave-change-list">
                <div v-for="account in recentLeaveAccounts" :key="`change-${account.accountId}`">
                  <span>
                    <strong>{{ account.leaveName }}</strong>
                    <small>{{ account.lastTxnType ?? '最近变动' }}</small>
                  </span>
                  <em :class="`change-${leaveAccountChangeTone(account)}`">
                    {{ (account.lastChangeMinutes ?? 0) > 0 ? '+' : '' }}{{ minutesToDayLabel(Math.abs(account.lastChangeMinutes ?? 0)) }}
                  </em>
                </div>
              </div>
              <AppEmptyState v-else title="暂无近期变动" icon="workbench" />
            </aside>
          </div>
        </template>
      </section>
    </template>

  </section>
</div>

<AppDialog v-if="processCatalogOpen" wide>
  <section class="oa-process-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>发起申请</h2>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="closeProcessCatalog">
        <X :size="15" />
      </button>
    </header>
    <div class="oa-process-grid">
      <button
        v-for="process in oaProcessCatalog"
        :key="process.key"
        type="button"
        class="oa-process-card"
        :class="[`tone-${process.tone}`, { planned: process.availability === 'planned' }]"
        @click="handleOaProcess(process)"
      >
        <span class="oa-process-icon" aria-hidden="true">
          <component :is="process.icon" :size="20" />
        </span>
        <span class="oa-process-copy">
          <span class="oa-process-title-row">
            <strong>{{ process.title }}</strong>
            <AppTag v-if="process.availability !== 'enabled'" :tone="process.statusTone">{{ process.statusLabel }}</AppTag>
          </span>
        </span>
      </button>
    </div>
  </section>
</AppDialog>

<AppDialog v-if="leaveDialogOpen">
  <section class="leave-request-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>请假申请</h2>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="leaveDialogOpen = false">
        <X :size="15" />
      </button>
    </header>
    <form class="workbench-form" @submit.prevent="submitLeaveRequestFromDialog">
      <label class="security-field">
        <span>假期类型</span>
        <select v-model="leaveForm.leaveTypeId" class="ui-inline-input">
          <option v-for="item in selectableLeaveTypes" :key="item.leaveTypeId" :value="String(item.leaveTypeId)">
            {{ item.leaveName }}
          </option>
        </select>
      </label>
      <label class="security-field">
        <span>开始时间</span>
        <input v-model="leaveForm.startAt" class="ui-inline-input" type="datetime-local" />
      </label>
      <label class="security-field">
        <span>结束时间</span>
        <input v-model="leaveForm.endAt" class="ui-inline-input" type="datetime-local" />
      </label>
      <label class="security-field">
        <span>请假时长</span>
        <input :value="leaveForm.durationMinutes ? minutesToDayLabel(Number(leaveForm.durationMinutes)) : ''" class="ui-inline-input" type="text" readonly />
      </label>
      <label class="security-field form-full">
        <span>请假原因</span>
        <textarea v-model="leaveForm.reason" class="ui-textarea compact-textarea" rows="4" />
      </label>
      <div class="security-field form-full">
        <span>申请附件</span>
        <div class="attachment-picker-summary">
          <button type="button" @click="openAttachmentPicker('leave')">
            选择附件
            <small>{{ leaveAttachmentIds.length }} 个</small>
          </button>
          <div v-if="selectedAttachmentFiles('leave').length" class="attachment-chip-list">
            <span v-for="file in selectedAttachmentFiles('leave')" :key="file.id">
              <span class="attachment-chip-name">{{ file.name }}</span>
              <button type="button" aria-label="移除附件" @click="removeAttachment('leave', file.attachmentRefId)">
                <X :size="13" />
              </button>
            </span>
          </div>
        </div>
      </div>
      <p v-if="leaveError" class="dialog-error form-full">{{ leaveError }}</p>
      <div class="form-actions form-full">
        <AppButton variant="secondary" type="button" :disabled="leaveSubmitting" @click="leaveDialogOpen = false">
          取消
        </AppButton>
        <AppButton variant="primary" type="submit" :disabled="leaveSubmitting || !canSubmitLeave">
          {{ leaveSubmitting ? '提交中' : '提交申请' }}
        </AppButton>
      </div>
    </form>
  </section>
</AppDialog>

<AppDialog v-if="clockCorrectionDialogOpen">
  <section class="leave-request-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>补卡申请</h2>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="clockCorrectionDialogOpen = false">
        <X :size="15" />
      </button>
    </header>
    <form class="workbench-form single-column" @submit.prevent="submitClockCorrectionFromDialog">
      <label class="security-field">
        <span>考勤日期</span>
        <input v-model="clockCorrectionForm.targetDate" class="ui-inline-input" type="date" />
      </label>
      <label class="security-field">
        <span>缺卡类型</span>
        <select v-model="clockCorrectionForm.missingPunchType" class="ui-inline-input">
          <option value="ON_DUTY">上班</option>
          <option value="OFF_DUTY">下班</option>
        </select>
      </label>
      <label class="security-field">
        <span>补卡时间</span>
        <input v-model="clockCorrectionForm.expectedPunchAt" class="ui-inline-input" type="datetime-local" />
      </label>
      <label class="security-field">
        <span>补卡原因</span>
        <textarea v-model="clockCorrectionForm.reason" class="ui-textarea compact-textarea" rows="3" />
      </label>
      <div class="security-field">
        <span>补卡附件</span>
        <div class="attachment-picker-summary">
          <button type="button" @click="openAttachmentPicker('clock')">
            选择附件
            <small>{{ clockCorrectionAttachmentIds.length }} 个</small>
          </button>
          <div v-if="selectedAttachmentFiles('clock').length" class="attachment-chip-list">
            <span v-for="file in selectedAttachmentFiles('clock')" :key="file.id">
              <span class="attachment-chip-name">{{ file.name }}</span>
              <button type="button" aria-label="移除附件" @click="removeAttachment('clock', file.attachmentRefId)">
                <X :size="13" />
              </button>
            </span>
          </div>
        </div>
      </div>
      <p v-if="clockCorrectionError" class="dialog-error">{{ clockCorrectionError }}</p>
      <p v-if="clockCorrectionResult" class="form-result">{{ clockCorrectionResult.statusName || statusText(clockCorrectionResult.status) }}</p>
      <div class="form-actions">
        <AppButton variant="secondary" type="button" :disabled="clockCorrectionSubmitting" @click="clockCorrectionDialogOpen = false">
          取消
        </AppButton>
        <AppButton variant="primary" type="submit" :disabled="clockCorrectionSubmitting">
          {{ clockCorrectionSubmitting ? '提交中' : '提交补卡' }}
        </AppButton>
      </div>
    </form>
  </section>
</AppDialog>

<AppDialog v-if="genericOaDialogOpen && activeGenericProcess">
  <section class="leave-request-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>{{ activeGenericProcess.title }}</h2>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="closeGenericOaDialog">
        <X :size="15" />
      </button>
    </header>
    <form class="workbench-form" @submit.prevent="submitGenericOaRequestFromDialog">
      <template v-for="field in activeGenericFields" :key="field.key">
        <label v-if="field.type === 'select'" class="security-field">
          <span>{{ field.label }}</span>
          <select v-model="genericOaForm[field.key]" class="ui-inline-input">
            <option v-for="option in field.options" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label v-else-if="field.type === 'textarea'" class="security-field form-full">
          <span>{{ field.label }}</span>
          <textarea v-model="genericOaForm[field.key]" class="ui-textarea compact-textarea" rows="4" />
        </label>
        <div v-else-if="field.type === 'attachment'" class="security-field form-full">
          <span>{{ field.label }}</span>
          <div class="attachment-picker-summary">
            <button type="button" @click="openAttachmentPicker('generic')">
              选择附件
              <small>{{ genericOaAttachmentIds.length }} 个</small>
            </button>
            <div v-if="selectedAttachmentFiles('generic').length" class="attachment-chip-list">
              <span v-for="file in selectedAttachmentFiles('generic')" :key="file.id">
                <span class="attachment-chip-name">{{ file.name }}</span>
                <button type="button" aria-label="移除附件" @click="removeAttachment('generic', file.attachmentRefId)">
                  <X :size="13" />
                </button>
              </span>
            </div>
          </div>
        </div>
        <label v-else class="security-field">
          <span>{{ field.label }}</span>
          <input
            v-model="genericOaForm[field.key]"
            class="ui-inline-input"
            :type="field.type === 'datetime' ? 'datetime-local' : field.type === 'money' ? 'number' : field.type"
            :step="field.type === 'money' ? '0.01' : field.type === 'number' ? '1' : undefined"
            :placeholder="field.placeholder"
          />
        </label>
      </template>
      <p v-if="genericOaError" class="dialog-error form-full">{{ genericOaError }}</p>
      <p v-if="genericOaResult" class="form-result form-full">{{ genericOaResult }}</p>
      <div class="form-actions form-full">
        <AppButton variant="secondary" type="button" :disabled="genericOaSubmitting" @click="closeGenericOaDialog">
          取消
        </AppButton>
        <AppButton variant="primary" type="submit" :disabled="genericOaSubmitDisabled">
          {{ genericOaSubmitting ? '提交中' : '提交申请' }}
        </AppButton>
      </div>
    </form>
  </section>
</AppDialog>

<AppDialog v-if="attachmentPickerOpen" wide>
  <section class="attachment-picker-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>选择附件</h2>
        <p>{{ currentAttachmentIds.length }} 个已选</p>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="closeAttachmentPicker">
        <X :size="15" />
      </button>
    </header>

    <div class="file-browser-toolbar">
      <label class="file-search-field">
        <Search :size="16" />
        <input v-model="attachmentPickerKeyword" type="search" placeholder="搜索附件" />
      </label>
      <select v-model="attachmentPickerType" class="ui-inline-input file-type-select">
        <option value="all">全部类型</option>
        <option value="document">文档</option>
        <option value="image">图片</option>
        <option value="sheet">表格</option>
        <option value="archive">压缩包</option>
        <option value="media">音视频</option>
        <option value="code">代码</option>
      </select>
      <button class="attachment-upload-button" type="button" :disabled="attachmentUploading" @click="openAttachmentUpload">
        <Upload :size="16" />
        {{ attachmentUploading ? '上传中' : '上传附件' }}
      </button>
      <input ref="attachmentUploadInput" class="visually-hidden-input" type="file" @change="handleAttachmentUploadSelected" />
    </div>
    <p v-if="attachmentUploadError" class="dialog-error">{{ attachmentUploadError }}</p>

    <div class="file-breadcrumb">
      <button type="button" :class="{ active: attachmentPickerFolderId === null }" @click="openAttachmentFolder(null)">
        <Home :size="15" />
        根目录
      </button>
      <button
        v-for="folder in attachmentPickerBreadcrumbs"
        :key="folder.folderId"
        type="button"
        :class="{ active: String(attachmentPickerFolderId) === String(folder.folderId) }"
        @click="openAttachmentFolder(folder.folderId)"
      >
        {{ folder.folderName }}
      </button>
    </div>

    <AppLoadingState v-if="attachmentPickerLoading" />
    <p v-else-if="attachmentPickerError" class="dialog-error">{{ attachmentPickerError }}</p>
    <AppEmptyState
      v-else-if="!attachmentPickerFolders.length && !attachmentPickerFiles.length"
      title="暂无附件"
      icon="files"
    />
    <div v-else class="attachment-picker-list">
      <button
        v-for="folder in attachmentPickerFolders"
        :key="folder.folderId"
        type="button"
        class="attachment-picker-row"
        @click="openAttachmentFolder(folder.folderId)"
      >
        <span class="file-icon folder-icon">
          <Folder :size="18" />
        </span>
        <strong>{{ folder.folderName }}</strong>
        <small>文件夹</small>
      </button>
      <button
        v-for="file in attachmentPickerFiles"
        :key="file.id"
        type="button"
        class="attachment-picker-row"
        :class="{ selected: currentAttachmentIdSet.has(String(file.attachmentRefId)) }"
        @click="toggleAttachment(file)"
      >
        <span class="file-icon" :class="`file-tone-${fileToneOf(file)}`">
          <component :is="fileIconOf(file)" :size="18" />
        </span>
        <strong>{{ file.name }}</strong>
        <small>{{ file.type }} · {{ file.size }}</small>
        <Check v-if="currentAttachmentIdSet.has(String(file.attachmentRefId))" :size="17" />
      </button>
    </div>

    <div class="form-actions">
      <AppButton variant="primary" @click="closeAttachmentPicker">确定</AppButton>
    </div>
  </section>
</AppDialog>

<AppDialog v-if="workflowFilePreview">
  <section class="preview-dialog">
    <header class="dialog-head dialog-head-row">
      <div>
        <h2>{{ workflowFilePreviewTitle }}</h2>
        <p>{{ workflowFilePreviewType || '文件' }}</p>
      </div>
      <button class="field-icon-button" type="button" aria-label="关闭" @click="closeWorkflowFilePreview">
        <X :size="15" />
      </button>
    </header>
    <div class="preview-body">
      <AppLoadingState v-if="workflowFilePreviewLoading" />
      <img v-else-if="workflowFilePreview.inline && (workflowFilePreviewType || '').startsWith('image/')" :src="workflowFilePreview.url" :alt="workflowFilePreviewTitle" />
      <iframe v-else-if="workflowFilePreview.inline" :src="workflowFilePreview.url" title="文件预览" />
      <div v-else class="preview-fallback">
        <component :is="fileIconOf(workflowFilePreview.file)" :size="42" />
        <strong>{{ workflowFilePreviewTitle }}</strong>
        <AppButton variant="primary" size="sm" @click="openWorkflowPreviewExternal">
          <ExternalLink :size="15" />
          打开文件
        </AppButton>
      </div>
    </div>
  </section>
</AppDialog>
</template>
