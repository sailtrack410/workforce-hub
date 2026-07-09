import type { EntityId } from '@/api/types';

const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const fullDateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

export function formatDateTime(value?: string | null, fallback = '-') {
  if (!value) return fallback;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return fallback;
  return dateTimeFormatter.format(date);
}

export function formatFullDateTime(value?: string | null, fallback = '-') {
  if (!value) return fallback;
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return fallback;
  return fullDateTimeFormatter.format(date);
}

export function formatBytes(value?: number | null) {
  if (value === undefined || value === null) return '-';
  if (value < 1024) return `${value} B`;
  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = value / 1024;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size.toFixed(size >= 10 ? 1 : 2)} ${units[index]}`;
}

// 后端存分钟数，按 8h/天 换算，优先显示天数
export function formatMinutes(value?: number | null) {
  if (value === undefined || value === null) return '-';
  const abs = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (abs % 480 === 0) return `${sign}${abs / 480} 天`;
  if (abs >= 480) return `${sign}${(abs / 480).toFixed(1)} 天`;
  if (abs % 60 === 0) return `${sign}${abs / 60} 小时`;
  return `${value} 分钟`;
}

export function formatId(value?: EntityId | null) {
  if (value === undefined || value === null || value === '') return '-';
  return String(value);
}

// 雪花 ID 太长，列表页只显示后 8 位
export function shortId(value?: EntityId | null) {
  const text = formatId(value);
  if (text === '-') return text;
  return text.length > 8 ? text.slice(-8) : text;
}

export function auditActionLabel(value?: string | null) {
  const labels: Record<string, string> = {
    FILE_UPLOAD_TICKET_CREATED: '创建上传凭证',
    FILE_ADMIN_UPLOAD_TICKET_CREATED: '管理员创建上传凭证',
    FILE_UPLOAD_CONFIRMED: '确认文件上传',
    FILE_ADMIN_UPLOAD_CONFIRMED: '管理员确认文件上传',
    FILE_UPLOAD_DEDUP: '复用已上传文件',
    FILE_ADMIN_UPLOAD_DEDUP: '管理员复用已上传文件',
    FILE_DOWNLOAD_TICKET_CREATED: '创建下载凭证',
    FILE_ADMIN_DOWNLOAD_TICKET_CREATED: '管理员创建下载凭证',
    WORKFLOW_ATTACHMENT_DOWNLOAD_TICKET_CREATED: '创建审批附件下载凭证',
    IM_GROUP_FILE_DOWNLOAD_TICKET_CREATED: '创建群文件下载凭证',
    FILE_DOWNLOAD_EXECUTED: '下载文件',
    FILE_ADMIN_DOWNLOAD_EXECUTED: '管理员下载文件',
    WORKFLOW_ATTACHMENT_DOWNLOAD_EXECUTED: '下载审批附件',
    IM_GROUP_FILE_DOWNLOAD_EXECUTED: '下载群文件',
    FILE_FOLDER_CREATED: '新建文件夹',
    FILE_MOVED: '移动文件',
    FILE_COPIED: '复制文件',
    FILE_TRASHED: '移入回收站',
    FILE_RESTORED: '恢复文件',
    FILE_PURGED: '彻底删除文件',
    FILE_ADMIN_TRASHED: '管理员移入回收站',
    FILE_ADMIN_RESTORED: '管理员恢复文件',
    FILE_ADMIN_PURGED: '管理员彻底删除文件',
    FILE_QUOTA_POLICY_CREATED: '新建存储配额',
    FILE_QUOTA_POLICY_UPDATED: '修改存储配额',
    FILE_CLEANUP_JOB_RUN: '执行文件清理',
    NOTIFY_CHANNEL_ENABLED: '启用通知渠道',
    NOTIFY_CHANNEL_DISABLED: '停用通知渠道',
    NOTIFY_CHANNEL_UPDATED: '修改通知渠道',
    NOTIFY_CHANNEL_TEST: '测试通知渠道',
    OPS_ALERT_RESOLVE: '处理告警',
    OPS_ALERT_IGNORE: '忽略告警',
    TASK_CREATED: '创建 AI 任务',
    TASK_WAITING_HUMAN_CONFIRM: '等待人工确认',
    TASK_HUMAN_CONFIRMED: '人工确认',
    TASK_HUMAN_REJECTED: '人工拒绝',
    TASK_RUNNING: '开始执行 AI 任务',
    TASK_SUCCEEDED: 'AI 任务完成',
    TASK_FAILED: 'AI 任务失败',
    TASK_CANCELLED: '取消 AI 任务',
    TASK_TIMEOUT_CANCELLED: '超时取消 AI 任务',
    IM_OUTBOX_RETRY_REQUESTED: '重试消息投递',
    IM_OUTBOX_BATCH_RETRY_REQUESTED: '批量重试消息投递',
    IM_OUTBOX_DISPATCH_TRIGGERED: '触发消息投递',
    APPROVE: '审批通过',
    REJECT: '审批驳回',
    SUBMIT: '提交申请',
    REVOKE: '撤回申请',
  };
  if (!value) return '-';
  return labels[value] ?? '业务操作';
}

export function auditTargetLabel(value?: string | null) {
  const labels: Record<string, string> = {
    DOWNLOAD_TICKET: '下载凭证',
    UPLOAD_TICKET: '上传凭证',
    ATTACHMENT_REF: '文件',
    FILE_OBJECT: '文件',
    FILE_FOLDER: '文件夹',
    WORKFLOW_REQUEST: '审批申请',
    WORKFLOW_ATTACHMENT: '审批附件',
    AGENT_TASK: 'AI 任务',
    AGENT: 'AI 助手',
    NOTIFY_CHANNEL: '通知渠道',
    OPS_ALERT: '系统告警',
    CLEANUP_JOB: '清理任务',
    STORAGE_QUOTA_POLICY: '存储配额',
    IM_EVENT_OUTBOX: '消息投递',
    IM_EVENT_OUTBOX_DISPATCH: '消息投递',
    USER: '员工',
    ADMIN: '管理员',
    ADMIN_USER: '管理员',
    ROLE: '角色',
    PERMISSION: '权限',
  };
  if (!value) return '-';
  return labels[value] ?? '业务对象';
}

export function auditOperatorLabel(type?: string | null, id?: EntityId | null) {
  const labels: Record<string, string> = {
    USER: '员工',
    ADMIN: '管理员',
    ADMIN_USER: '管理员',
    SYSTEM: '系统',
    AGENT: 'AI助手',
    AI: 'AI助手',
  };
  void id;
  return type ? labels[type] ?? '操作人' : '-';
}

function humanizeAuditCode(value: string) {
  const labels: Record<string, string> = {
    ADMIN: '管理员',
    AGENT: 'AI助手',
    AI: 'AI助手',
    ALERT: '告警',
    APPROVE: '审批通过',
    BATCH: '批量',
    CANCELLED: '已取消',
    CHANNEL: '渠道',
    CLEANUP: '清理',
    CONFIRMED: '已确认',
    COPIED: '已复制',
    CREATED: '已创建',
    DEDUP: '复用',
    DISABLED: '已停用',
    DISPATCH: '投递',
    DOWNLOAD: '下载',
    ENABLED: '已启用',
    EXECUTED: '已执行',
    FAILED: '失败',
    FILE: '文件',
    FOLDER: '文件夹',
    HUMAN: '人工',
    IM: '消息',
    JOB: '任务',
    MOVED: '已移动',
    NOTIFY: '通知',
    OBJECT: '对象',
    OPS: '运维',
    OUTBOX: '待投递',
    PERMISSION: '权限',
    POLICY: '策略',
    PURGED: '已彻底删除',
    QUOTA: '配额',
    REJECT: '审批驳回',
    REJECTED: '已拒绝',
    REQUEST: '申请',
    REQUESTED: '已发起',
    RESOLVE: '处理',
    RESTORED: '已恢复',
    RETRY: '重试',
    REVOKE: '撤回',
    ROLE: '角色',
    RUN: '执行',
    RUNNING: '执行中',
    STORAGE: '存储',
    SUBMIT: '提交',
    SUCCEEDED: '完成',
    SYSTEM: '系统',
    TASK: '任务',
    TEST: '测试',
    TICKET: '凭证',
    TIMEOUT: '超时',
    TRASHED: '已移入回收站',
    UPDATED: '已修改',
    UPLOAD: '上传',
    USER: '员工',
    WAITING: '等待',
    WORKFLOW: '审批',
  };
  const text = value
    .split('_')
    .filter(Boolean)
    .map((part) => labels[part] ?? part)
    .join('');
  return text || value;
}

export function statusLabel(value?: string | null) {
  const labels: Record<string, string> = {
    ACTIVE: '启用',
    INACTIVE: '停用',
    LOCKED: '锁定',
    DISABLED: '禁用',
    PENDING: '待处理',
    PENDING_ONBOARD: '待入职',
    PROBATION: '试用',
    PENDING_OFFBOARD: '待离职',
    OFFBOARDED: '已离职',
    RESIGNED: '离职',
    RUNNING: '运行中',
    INDEXING: '向量化中',
    EMBEDDED: '已向量化',
    SUCCEEDED: '成功',
    SUCCESS: '成功',
    SKIPPED: '已跳过',
    FAILED: '失败',
    PARTIAL_FAILED: '部分失败',
    WAITING_HUMAN_CONFIRM: '待确认',
    WAITING_CONFIRM: '待确认',
    REJECTED: '已拒绝',
    CANCELLED: '已取消',
    SUBMITTED: '已提交',
    APPROVED: '已通过',
    REVOKED: '已撤回',
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
    DEPRECATED: '已弃用',
  };
  if (!value) return '-';
  return labels[value] ?? '未知状态';
}

export function workflowStatusLabel(value?: string | null) {
  const labels: Record<string, string> = {
    PENDING_REVIEW: '待预审',
    IN_APPROVAL: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回',
    WITHDRAWN: '已撤回',
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    ARCHIVED: '已归档',
    ACTIVE: '启用',
    INACTIVE: '停用',
  };
  if (!value) return '-';
  return labels[value] ?? statusLabel(value);
}

export function workflowBizLabel(value?: string | null) {
  const labels: Record<string, string> = {
    LEAVE: '请假',
    CLOCK_CORRECTION: '补卡',
    EXPENSE: '费用报销',
    BUSINESS_TRIP: '出差',
    OVERTIME: '加班',
    SEAL: '用章',
    PURCHASE: '采购',
  };
  if (!value) return '-';
  return labels[value] ?? '其他业务';
}

export function workflowActionLabel(value?: string | null) {
  const labels: Record<string, string> = {
    APPROVE: '通过',
    REJECT: '驳回',
    WITHDRAW: '撤回',
    AGENT_PRE_REVIEW: 'AI 预审',
  };
  if (!value) return '-';
  return labels[value] ?? '其他动作';
}

export function punchTypeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    ON_DUTY: '上班卡',
    OFF_DUTY: '下班卡',
  };
  if (!value) return '-';
  return labels[value] ?? '其他补卡类型';
}

export function workflowStepLabel(value?: string | null) {
  const labels: Record<string, string> = {
    manager_approval: '直属上级审批',
    agent_pre_review: 'AI 预审',
  };
  if (!value) return '-';
  return labels[value] ?? '审批节点';
}

export function workflowDefinitionLabel(value?: string | null) {
  const labels: Record<string, string> = {
    leave_manager_approval: '请假直属上级审批',
    clock_correction_manager_approval: '补卡直属上级审批',
    expense_manager_approval: '费用报销直属上级审批',
    business_trip_manager_approval: '出差申请直属上级审批',
    overtime_manager_approval: '加班申请直属上级审批',
    seal_manager_approval: '用章申请直属上级审批',
    purchase_manager_approval: '采购申请直属上级审批',
    LEAVE_MANAGER_APPROVAL: '请假直属上级审批',
    CLOCK_CORRECTION_MANAGER_APPROVAL: '补卡直属上级审批',
    EXPENSE_MANAGER_APPROVAL: '费用报销直属上级审批',
    BUSINESS_TRIP_MANAGER_APPROVAL: '出差申请直属上级审批',
    OVERTIME_MANAGER_APPROVAL: '加班申请直属上级审批',
    SEAL_MANAGER_APPROVAL: '用章申请直属上级审批',
    PURCHASE_MANAGER_APPROVAL: '采购申请直属上级审批',
  };
  if (!value) return '-';
  return labels[value] ?? '已配置流程';
}

export function fileScanStatusLabel(value?: string | null) {
  const labels: Record<string, string> = {
    PENDING: '待扫描',
    CLEAN: '已通过',
    FAILED: '扫描失败',
  };
  if (!value) return '-';
  return labels[value] ?? '待确认';
}

export function providerLabel(value?: string | null) {
  const labels: Record<string, string> = {
    openai: 'OpenAI（国际模型）',
    deepseek: 'DeepSeek（深度求索）',
    anthropic: 'Anthropic（Claude）',
    qwen: '通义千问',
    doubao: '豆包',
    'openai-compatible': 'OpenAI 兼容接口',
  };
  if (!value) return '-';
  return labels[value] ?? '自定义模型';
}

export function leaveBalanceModeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    DEDUCT: '扣减余额',
    NO_DEDUCT: '不扣减余额',
  };
  if (!value) return '-';
  return labels[value] ?? '未知模式';
}

export function agentContextLabel(value?: string | null) {
  const labels: Record<string, string> = {
    USER_QUERY: '员工咨询',
    WF_REQUEST: '审批申请',
    IM_GROUP: '群聊消息',
    IM_CONVERSATION: '消息会话',
    CONVERSATION: '会话',
  };
  if (!value) return '-';
  return labels[value] ?? '业务场景';
}

export function actorTypeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    USER: '员工',
    ADMIN: '管理员',
    ADMIN_USER: '管理员',
    SYSTEM: '系统',
    AGENT: 'AI 助手',
    WORKFLOW: '审批',
    TIMER: '定时任务',
    EVENT: '事件',
  };
  if (!value) return '-';
  return labels[value] ?? '系统来源';
}

export function attendanceSourceLabel(value?: string | null) {
  const labels: Record<string, string> = {
    APP: '移动端',
    PC: '电脑端',
    MANUAL: '手动',
    AUTO: '自动',
    TEMPLATE: '模板',
    ONBOARD: '入职同步',
    TRANSFER: '调岗同步',
  };
  if (!value) return '-';
  return labels[value] ?? '其他来源';
}

export function attendanceResultLabel(value?: string | null) {
  const labels: Record<string, string> = {
    NORMAL: '正常',
    ABNORMAL: '异常',
    NO_RULE: '未排班',
    LEAVE: '请假',
  };
  if (!value) return '-';
  return labels[value] ?? '其他结果';
}

export function departmentTypeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    ROOT: '组织',
    COMPANY: '公司',
    BRANCH: '分支机构',
    DEPARTMENT: '部门',
    TEAM: '团队',
  };
  if (!value) return '-';
  return labels[value] ?? '其他组织';
}

export function positionCategoryLabel(value?: string | null) {
  const labels: Record<string, string> = {
    ENGINEER: '专业岗',
    MANAGER: '管理岗',
  };
  if (!value) return '-';
  return labels[value] ?? '其他分类';
}

export function genderLabel(value?: string | null) {
  const labels: Record<string, string> = {
    MALE: '男',
    FEMALE: '女',
    UNKNOWN: '未知',
  };
  if (!value) return '-';
  return labels[value] ?? '未知';
}

export function taskTriggerLabel(value?: string | null) {
  const labels: Record<string, string> = {
    MANUAL: '手动',
    CONVERSATION: '会话',
    WORKFLOW: '审批',
    TIMER: '定时',
    EVENT: '事件',
    SERVICE: '服务',
    SCHEDULED: '定时任务',
  };
  if (!value) return '-';
  return labels[value] ?? '其他触发';
}

export function taskContextLabel(value?: string | null) {
  return agentContextLabel(value);
}

export function cleanupJobTypeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    ALL_EXPIRED: '全部到期清理',
    EXPIRED_UPLOAD_TICKET: '过期上传票据',
    EXPIRED_DOWNLOAD_TICKET: '过期下载票据',
    EXPIRED_RECYCLE_REF: '到期回收站引用',
    UNREFERENCED_FILE_OBJECT: '无引用物理对象',
    FAILED_PHYSICAL_OBJECT: '失败物理对象重试',
  };
  if (!value) return '-';
  return labels[value] ?? '其他清理任务';
}

export function permissionModuleLabel(value?: string | null) {
  const labels: Record<string, string> = {
    RBAC: '角色权限',
    ORG: '组织人员',
    ATTENDANCE: '考勤',
    LEAVE: '假期',
    WORKFLOW: '审批',
    AGENT: 'AI 助手',
    AGENT_TASK: 'AI 任务',
    FILE: '文件',
    RAGFLOW: '知识库',
    NOTIFICATION: '通知',
  };
  if (!value) return '-';
  return labels[value] ?? '其他模块';
}

export function permissionActionLabel(value?: string | null) {
  const labels: Record<string, string> = {
    MANAGE: '管理',
    READ: '查看',
    WRITE: '编辑',
    CREATE: '新建',
    UPDATE: '修改',
    DELETE: '删除',
  };
  if (!value) return '-';
  return labels[value] ?? '其他动作';
}

export function roleCodeLabel(value?: string | null) {
  const labels: Record<string, string> = {
    ADMIN: '系统管理员',
    HR_MANAGER: '人事管理员',
    DEPT_MANAGER: '部门主管',
    EMPLOYEE: '普通员工',
    SUPER_ADMIN: '超级管理员',
  };
  if (!value) return '-';
  return labels[value] ?? '自定义角色';
}

// 后端返回的术语对用户不友好，统一替换成前端展示用语
export function humanizeText(value?: string | null) {
  if (!value) return '';
  return value
    .replace(/Agent预审/g, 'AI 预审')
    .replace(/Agent任务/g, 'AI 任务')
    .replace(/Agent会话/g, 'AI 会话')
    .replace(/Agent 不存在/g, 'AI 助手不存在')
    .replace(/Agent不存在/g, 'AI 助手不存在')
    .replace(/IM会话/g, '消息会话')
    .replace(/IM 会话/g, '消息会话')
    .replace(/Agent/g, 'AI 助手');
}

export function safeBusinessMessage(value?: string | null, fallback = '处理失败，请检查配置') {
  const text = humanizeText(value).trim();
  if (!text) return fallback;
  if (/api[_ -]?key|token|secret|authorization/i.test(text)) return '密钥配置异常，请检查配置';
  if (/https?:\/\//i.test(text) || text.includes('/api/')) return fallback;
  if (/\bWF-[A-Z0-9-]*\d[A-Z0-9-]*\b/i.test(text)) return fallback;
  if (/\b\d{16,}\b/.test(text)) return fallback;
  if (/^[A-Z][A-Z0-9_]{2,}$/.test(text)) return fallback;
  return text;
}

export function permissionDisplayName(value?: string | null) {
  const text = humanizeText(value).trim();
  if (!text) return '-';
  return text
    .replace(/AI 助手配置管理/g, 'AI 助手配置')
    .replace(/AI 任务管理/g, 'AI 任务管理')
    .replace(/RAGFlow管理/g, '知识库管理')
    .replace(/RAGFlow 管理/g, '知识库管理')
    .replace(/RAGFlow/g, '知识库');
}

// 统一的错误消息提取，顺便做术语替换
export function errorMessage(error: unknown, fallback = '请求失败') {
  if (error instanceof Error && error.message) return safeBusinessMessage(error.message, fallback);
  return fallback;
}
