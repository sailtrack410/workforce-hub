import { defineStore } from 'pinia';

export type SectionId = 'messages' | 'todos' | 'files' | 'workbench' | 'profile';
export type ConversationTone = 'blue' | 'green' | 'amber' | 'red' | 'gray';
export type SectionFilter = '全部' | '未处理' | 'Agent' | '组织群' | '私聊';
export type ApiContractStatus = 'implemented' | 'planned' | 'mockOnly';

export interface ApiContractHint {
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  path: string;
  status: ApiContractStatus;
  note: string;
}

export interface SectionAction {
  label: string;
  action: 'switch-section' | 'switch-item' | 'open-workbench' | 'toast';
  section?: SectionId;
  item?: string;
  app?: string;
  message?: string;
}

export interface SectionMeta {
  kicker: string;
  title: string;
  subtitle: string;
  search: string;
  primaryAction?: SectionAction;
  secondaryAction?: SectionAction;
}

export interface ConversationMessage {
  id: string;
  type?: 'text' | 'process-card';
  author: string;
  role: string;
  body: string;
  side?: 'left' | 'right' | 'system';
  time?: string;
}

export interface ConversationCard {
  label: string;
  value: string;
  hint: string;
}

export interface ConversationStep {
  label: string;
  status: 'done' | 'current' | 'pending';
  detail: string;
}

export interface ConversationAttachment {
  id: string;
  name: string;
  type: string;
  size: string;
  status: string;
}

export interface ConversationPanel {
  members: Array<{ name: string; role: string }>;
  tasks: string[];
  attachments: ConversationAttachment[];
}

export interface ConversationRecord {
  id: string;
  kind: 'approval' | 'team' | 'agent' | 'attendance' | 'file';
  tone: ConversationTone;
  badge: string;
  title: string;
  subtitle: string;
  memberCount?: number;
  meta: string;
  tag: string;
  unread: number;
  flow: string;
  status: string;
  unreadLabel: string;
  summaryTitle: string;
  summaryBody: string;
  cards: ConversationCard[];
  workflowSteps: ConversationStep[];
  messages: ConversationMessage[];
  panel: ConversationPanel;
}

export interface TodoRecord {
  id: string;
  title: string;
  subtitle: string;
  note: string;
  time: string;
  status: string;
  priority: string;
  requestNo: string;
  taskId: string;
  currentStepName: string;
  summary: string;
  fields: Array<[string, string]>;
  timeline: string[];
  apis: ApiContractHint[];
  actions: SectionAction[];
}

export interface FileSpaceRecord {
  id: string;
  title: string;
  subtitle: string;
  note: string;
  usage: number;
  label: string;
  summary: Array<[string, string]>;
  files: Array<[string, string, string, string, string]>;
}

export interface WorkbenchRecord {
  id: string;
  title: string;
  label: string;
  subtitle: string;
  note: string;
  tone: ConversationTone;
  status: string;
  fields: Array<[string, string]>;
  stats: Array<[string, string]>;
  steps: string[];
  disabled?: boolean;
}

export interface ProfileRecord {
  id: string;
  title: string;
  subtitle: string;
  note: string;
  tone: ConversationTone;
  stats: Array<[string, string]>;
  fields: Array<[string, string]>;
}

export const sectionMeta: Record<SectionId, SectionMeta> = {
  messages: {
    kicker: '',
    title: '消息',
    subtitle: '',
    search: '搜索会话、待办、附件、状态',
    secondaryAction: {
      label: '查看待办',
      action: 'switch-section',
      section: 'todos',
    },
  },
  todos: {
    kicker: '统一处理',
    title: '待办',
    subtitle: '审批、附件、考勤和文件提醒会汇聚到这里',
    search: '搜索待办、申请号、处理人',
    primaryAction: {
      label: '刷新列表',
      action: 'toast',
      message: '已刷新待办列表。',
    },
    secondaryAction: {
      label: '打开申请',
      action: 'open-workbench',
      app: 'requests',
    },
  },
  files: {
    kicker: '文件中心',
    title: '文档',
    subtitle: '个人文件、审批附件、下载票据和回收站',
    search: '搜索文件、票据、状态',
    primaryAction: {
      label: '申请上传',
      action: 'toast',
      message: '上传流程是申请票据、直传对象、确认上传。',
    },
    secondaryAction: {
      label: '回收站',
      action: 'switch-item',
      item: 'recycle',
    },
  },
  workbench: {
    kicker: '业务办理',
    title: '工作台',
    subtitle: '请假、考勤、假期、文件和申请入口都从这里展开',
    search: '搜索请假、考勤、假期、文件',
    primaryAction: {
      label: '请假申请',
      action: 'switch-item',
      item: 'leave',
    },
    secondaryAction: {
      label: '我的考勤',
      action: 'switch-item',
      item: 'attendance',
    },
  },
  profile: {
    kicker: '个人中心',
    title: '我的',
    subtitle: '身份、组织、账号安全和可访问范围',
    search: '搜索资料、安全、权限',
    primaryAction: {
      label: '绑定邮箱',
      action: 'toast',
      message: '员工端邮箱绑定和验证码入口已按认证文档预留。',
    },
    secondaryAction: {
      label: '重置密码',
      action: 'toast',
      message: '密码重置入口已按认证文档预留。',
    },
  },
};

export const navItems: Array<{ id: SectionId; label: string; glyph: string }> = [
  { id: 'messages', label: '消息', glyph: '息' },
  { id: 'todos', label: '待办', glyph: '办' },
  { id: 'files', label: '文档', glyph: '档' },
  { id: 'workbench', label: '工作台', glyph: '台' },
  { id: 'profile', label: '我的', glyph: '我' },
];

export const sectionFilters: SectionFilter[] = ['全部', '未处理', 'Agent', '组织群', '私聊'];

const currentUser = {
  name: '林澈',
  shortName: '林',
  loginName: 'lin.che',
  email: 'lin.che@workforce.local',
  title: '运营专员',
  department: '运营中心 / 运营一部',
  manager: '周悦',
  status: '在职',
  workNo: 'WFH-1027',
  location: '上海',
  phone: '138-0000-1027',
};

const conversations: ConversationRecord[] = [
  {
    id: 'leave-approval',
    kind: 'approval',
    tone: 'blue',
    badge: '王晨',
    title: '王晨年假申请',
    subtitle: '请假 2 天，余额预审通过，等待审批确认',
    meta: '10:24',
    tag: '审批 · PENDING',
    unread: 2,
    flow: '审批',
    status: 'PENDING',
    unreadLabel: '未读消息',
    summaryTitle: '余额预审通过，审批链将进入直属上级。',
    summaryBody: '请假时长、附件引用、假期账户余额都已完成第一轮校验。',
    cards: [
      { label: '申请号', value: 'WF-LEAVE-20260506-01', hint: '申请号' },
      { label: '当前状态', value: 'PENDING', hint: '待办状态' },
      { label: '优先级', value: '高', hint: '优先级' },
    ],
    workflowSteps: [
      { label: '提交申请', status: 'done', detail: '王晨已提交年假申请。' },
      { label: '余额预审', status: 'done', detail: '年假余额、请假时长和附件引用已校验。' },
      { label: '直属上级审批', status: 'current', detail: '当前等待周悦确认交接安排与排班冲突。' },
      { label: 'HR归档', status: 'pending', detail: '审批通过后写入假期台账并同步考勤。' },
    ],
    messages: [
      { id: 'system-1', author: '系统', role: '工作流消息', body: '请假申请已写入业务申请头，待办已投递给直属上级。', side: 'system' },
      { id: 'agent-card', author: 'HR 助手', role: '预审 Agent', body: '余额预审通过。申请链路将先进入直属上级审批，附件与请假时长已完成一致性检查。', side: 'left' },
      { id: 'manager-1', author: '周悦', role: '审批人', body: '下午我会集中处理，请把交接清单再确认一次。', side: 'left' },
      { id: 'me-1', author: '林澈', role: '申请人', body: '附件已上传，补充说明放到文件中心了。', side: 'right' },
    ],
    panel: {
      members: [
        { name: '王晨', role: '申请人' },
        { name: '周悦', role: '审批人' },
        { name: 'HR 助手', role: '预审 Agent' },
      ],
      tasks: ['待办已投递给直属上级', '请假余额扣减等待审批通过', '审批附件下载授权已就绪'],
      attachments: [
        { id: 'leave-handover', name: '请假交接清单.docx', type: 'DOCX', size: '84KB', status: '已上传' },
        { id: 'leave-note', name: '年假说明.pdf', type: 'PDF', size: '640KB', status: '审批附件' },
      ],
    },
  },
  {
    id: 'operation-team',
    kind: 'team',
    tone: 'green',
    badge: '运营',
    title: '运营一部组织群',
    subtitle: '五月排班和补卡说明已同步到群文件',
    memberCount: 12,
    meta: '09:42',
    tag: '组织群 · ACTIVE',
    unread: 3,
    flow: '组织协作',
    status: 'ACTIVE',
    unreadLabel: '未读消息',
    summaryTitle: '排班同步已发布，群内待确认事项进入跟进。',
    summaryBody: '本周排班、补卡规则与群文件下载已完成归档，后续将持续更新。',
    cards: [
      { label: '群状态', value: 'ACTIVE', hint: '群聊状态' },
      { label: '待确认', value: '3 项', hint: '待跟进任务' },
      { label: '文件数', value: '12', hint: '群文件' },
    ],
    workflowSteps: [
      { label: '群文件', status: 'done', detail: '五月排班与补卡说明已同步。' },
      { label: '成员确认', status: 'current', detail: '当前等待组内成员确认排班。' },
      { label: '跟进归档', status: 'pending', detail: '确认完成后归档到组织群文件。' },
    ],
    messages: [
      { id: 'system-2', author: '系统', role: '群消息', body: '五月排班与补卡说明已同步到群文件。', side: 'system' },
      { id: 'ops-1', author: '运营小组', role: '群主', body: '今晚 18:00 前确认排班，若有冲突直接在这里回复。', side: 'left' },
      { id: 'me-2', author: '林澈', role: '成员', body: '收到，我会在群文件里确认替班。', side: 'right' },
    ],
    panel: {
      members: [
        { name: '运营小组', role: '群主' },
        { name: '周悦', role: '协同人' },
        { name: '林澈', role: '成员' },
      ],
      tasks: ['排班确认', '补卡说明归档', '群文件同步'],
      attachments: [
        { id: 'ops-schedule', name: '五月排班表.xlsx', type: 'XLSX', size: '148KB', status: '群文件' },
        { id: 'ops-rule', name: '补卡说明.pdf', type: 'PDF', size: '310KB', status: '已同步' },
      ],
    },
  },
  {
    id: 'hr-agent',
    kind: 'agent',
    tone: 'green',
    badge: 'HR',
    title: 'HR 助手',
    subtitle: '年假余额还剩 5 天，建议避开团队峰值',
    meta: '昨天',
    tag: 'Agent · READY',
    unread: 1,
    flow: 'Agent 建议',
    status: 'READY',
    unreadLabel: '未读消息',
    summaryTitle: '可执行建议已整理，支持直接投递待办。',
    summaryBody: 'Agent 已基于余额、团队峰值和历史审批节奏给出建议，可继续追问或一键生成申请。',
    cards: [
      { label: '建议数', value: '3', hint: '建议条目' },
      { label: '可用余额', value: '5 天', hint: '年假余额' },
      { label: '响应级别', value: 'READY', hint: 'Agent 状态' },
    ],
    workflowSteps: [
      { label: '问题识别', status: 'done', detail: '识别年假余额与团队峰值。' },
      { label: '建议生成', status: 'current', detail: '已给出请假时段建议，可继续追问。' },
      { label: '生成申请', status: 'pending', detail: '确认后进入工作台申请草稿。' },
    ],
    messages: [
      { id: 'system-3', author: '系统', role: 'Agent 结果', body: '可直接在当前会话发起年假申请，已规避团队峰值时段。', side: 'system' },
      { id: 'agent-1', author: 'HR 助手', role: '预审 Agent', body: '建议优先选择 5 月中旬后的两个工作日，审批通过率更高。', side: 'left' },
      { id: 'me-3', author: '林澈', role: '申请人', body: '那帮我按建议生成一版申请。', side: 'right' },
    ],
    panel: {
      members: [
        { name: 'HR 助手', role: 'Agent' },
        { name: '林澈', role: '申请人' },
        { name: '周悦', role: '审批人' },
      ],
      tasks: ['建议生成申请', '余额校验', '审批链预览'],
      attachments: [],
    },
  },
  {
    id: 'attendance-alert',
    kind: 'attendance',
    tone: 'amber',
    badge: '考勤',
    title: '考勤提醒',
    subtitle: '5 月 3 日存在签退缺卡，结果会进入异常列表',
    meta: '周三',
    tag: '考勤 · ABNORMAL',
    unread: 1,
    flow: '考勤异常',
    status: 'ABNORMAL',
    unreadLabel: '未读消息',
    summaryTitle: '缺卡已记录，进入异常待处理流程。',
    summaryBody: '异常原因、打卡时段和审批路径已经串联，后续可直接发起补卡说明。',
    cards: [
      { label: '异常类型', value: '签退缺卡', hint: '考勤异常' },
      { label: '发生日期', value: '5 月 3 日', hint: '异常日期' },
      { label: '处理状态', value: '待确认', hint: '待处理' },
    ],
    workflowSteps: [
      { label: '异常生成', status: 'done', detail: '系统已记录 5 月 3 日签退缺卡。' },
      { label: '本人确认', status: 'current', detail: '当前等待补充缺卡说明。' },
      { label: '异常归档', status: 'pending', detail: '处理完成后同步日结果。' },
    ],
    messages: [
      { id: 'system-4', author: '系统', role: '考勤消息', body: '5 月 3 日存在签退缺卡，结果将进入异常列表。', side: 'system' },
      { id: 'hr-2', author: '考勤小组', role: '处理人', body: '请补充说明原因，必要时同步直属主管。', side: 'left' },
      { id: 'me-4', author: '林澈', role: '申请人', body: '我晚点补交说明，先确认当天排班。', side: 'right' },
    ],
    panel: {
      members: [
        { name: '考勤小组', role: '处理人' },
        { name: '林澈', role: '申请人' },
        { name: '周悦', role: '审批人' },
      ],
      tasks: ['缺卡说明补交', '排班核对', '异常处理归档'],
      attachments: [],
    },
  },
  {
    id: 'file-center',
    kind: 'file',
    tone: 'gray',
    badge: '文件',
    title: '文件中心通知',
    subtitle: '员工手册下载票据还剩 8 分钟',
    meta: '昨天',
    tag: '文件 · ACTIVE',
    unread: 0,
    flow: '文件流转',
    status: 'ACTIVE',
    unreadLabel: '未读消息',
    summaryTitle: '文件下载票据已下发，接收端可继续查看。',
    summaryBody: '员工手册、票据和读取授权都已进入文件流转记录。',
    cards: [
      { label: '文件状态', value: 'ACTIVE', hint: '文件可访问' },
      { label: '剩余时间', value: '8 分钟', hint: '下载票据' },
      { label: '授权', value: '已就绪', hint: '访问授权' },
    ],
    workflowSteps: [
      { label: '票据生成', status: 'done', detail: '员工手册下载票据已生成。' },
      { label: '等待下载', status: 'current', detail: '票据剩余 8 分钟，请在有效期内使用。' },
      { label: '审计写入', status: 'pending', detail: '下载完成后记录消费审计。' },
    ],
    messages: [
      { id: 'system-5', author: '系统', role: '文件消息', body: '员工手册下载票据已生成，请在有效期内完成下载。', side: 'system' },
      { id: 'file-1', author: '文件中心', role: '服务', body: '可继续查看员工手册版本说明和附件清单。', side: 'left' },
    ],
    panel: {
      members: [
        { name: '文件中心', role: '服务' },
        { name: '林澈', role: '接收人' },
        { name: 'HR 助手', role: '校验 Agent' },
      ],
      tasks: ['手册下载', '票据校验', '附件访问'],
      attachments: [
        { id: 'employee-handbook', name: '员工手册.pdf', type: 'PDF', size: '2.1MB', status: '票据有效' },
      ],
    },
  },
];

const todoItems: TodoRecord[] = [
  {
    id: 'todo-approval',
    title: '王晨年假申请',
    subtitle: '审批待办 · 周悦待处理',
    note: '请假 2 天，余额预审通过',
    time: '10:24',
    status: 'PENDING',
    priority: '高',
    requestNo: 'WF-LEAVE-20260506-01',
    taskId: 'TASK-20481',
    currentStepName: '直属上级审批',
    summary: '员工提交年假申请，待审批人确认交接安排与团队排班冲突。',
    fields: [
      ['申请人', '王晨'],
      ['请假类型', '年假'],
      ['起止时间', '2026-05-11 09:00 至 2026-05-12 18:00'],
      ['预审结果', '余额充足'],
    ],
    timeline: ['王晨提交申请', 'HR 助手完成余额预审', '等待周悦审批', '通过后写入假期台账'],
    apis: [
      api('GET', '/api/employee/workflow/todos', 'implemented', '员工端待办列表已由后端开放'),
      api('POST', '/api/employee/workflow/tasks/{taskId}/approve', 'implemented', '审批通过动作已由后端开放'),
      api('POST', '/api/employee/workflow/tasks/{taskId}/reject', 'implemented', '审批驳回动作已由后端开放'),
    ],
    actions: [
      { label: '审批通过', action: 'toast', message: '待办处理会在后端通过 workflow 接口写入审批动作。' },
      { label: '驳回', action: 'toast', message: '驳回动作会通过 /tasks/{taskId}/reject 完成。' },
    ],
  },
  {
    id: 'todo-ticket',
    title: '员工手册下载票据',
    subtitle: '文件提醒 · 仅本人可用',
    note: '短期票据剩余 8 分钟',
    time: '剩余 8 分钟',
    status: 'ACTIVE',
    priority: '中',
    requestNo: 'FILE-TICKET-20260507-02',
    taskId: 'FILE-TICKET-02',
    currentStepName: '文件下载票据',
    summary: '文件下载采用短期票据，过期后可重新申请，不暴露固定对象地址。',
    fields: [
      ['文件名', '员工手册.pdf'],
      ['票据状态', 'ACTIVE'],
      ['ownerType', 'PERSONAL_FILE'],
      ['安全策略', '一次性下载、可审计'],
    ],
    timeline: ['申请下载票据', '生成短期下载入口', '票据即将过期', '必要时重新申请'],
    apis: [
      api('POST', '/api/employee/files/{attachmentRefId}/download-tickets', 'implemented', '员工端文件下载票据已由后端开放'),
      api('GET', '/api/employee/files/download/{ticketNo}', 'implemented', '短期票据下载入口已由后端开放'),
    ],
    actions: [
      { label: '重新申请', action: 'toast', message: '下载票据到期后可以重新申请，不影响原文件引用。' },
      { label: '查看文件', action: 'switch-section', section: 'files' },
    ],
  },
  {
    id: 'todo-attendance',
    title: '5 月 3 日考勤缺卡',
    subtitle: '个人待办 · 异常提醒',
    note: '签退缺失，需要在工作台继续处理',
    time: '今天 18:00',
    status: 'OPEN',
    priority: '中',
    requestNo: 'ATT-20260503-01',
    taskId: 'ATT-20260503-01',
    currentStepName: '考勤异常处理',
    summary: '考勤异常会进入统一待办和个人考勤入口，员工查看自己的结果和异常。',
    fields: [
      ['异常日期', '2026-05-03'],
      ['异常类型', 'MISS_PUNCH'],
      ['日结果', 'ABNORMAL'],
      ['建议动作', '查看个人考勤'],
    ],
    timeline: ['系统生成异常', '写入日结果', '投递个人待办', '在工作台继续查看'],
    apis: [
      api('POST', '/api/employee/attendance/punch', 'implemented', '员工端打卡接口已由后端开放'),
      api('GET', '/api/employee/attendance/daily-results', 'implemented', '员工端日结果接口已由后端开放'),
      api('GET', '/api/employee/attendance/exceptions', 'implemented', '员工端异常列表接口已由后端开放'),
    ],
    actions: [
      { label: '查看考勤', action: 'switch-item', section: 'workbench', item: 'attendance' },
      { label: '查看详情', action: 'toast', message: '考勤详情已按员工端接口口径保留。' },
    ],
  },
  {
    id: 'todo-request',
    title: '我的申请状态更新',
    subtitle: '流程提醒 · 申请已进入审批中',
    note: '请假申请状态已从草稿切到审批中',
    time: '周一',
    status: 'IN_APPROVAL',
    priority: '低',
    requestNo: 'WF-LEAVE-20260506-01',
    taskId: 'REQ-2051',
    currentStepName: '申请详情同步',
    summary: '申请列表用于查看自己的申请、当前节点和审批结果。',
    fields: [
      ['申请编号', 'WF-LEAVE-20260506-01'],
      ['当前状态', 'IN_APPROVAL'],
      ['当前节点', '直属上级审批'],
      ['列表来源', 'GET /api/employee/workflow/requests'],
    ],
    timeline: ['员工提交申请', '流程进入审批中', '我的申请列表同步', '待审批结果回写'],
    apis: [
      api('GET', '/api/employee/workflow/requests', 'implemented', '员工端申请分页列表已由后端开放'),
      api('GET', '/api/employee/workflow/requests/{requestId}', 'implemented', '员工端申请详情已由后端开放'),
      api('GET', '/api/employee/workflow/requests/{requestId}/actions', 'implemented', '员工端申请动作流水已由后端开放'),
    ],
    actions: [
      { label: '打开申请', action: 'switch-item', section: 'workbench', item: 'requests' },
      { label: '查看消息', action: 'switch-section', section: 'messages' },
    ],
  },
];

const fileSpaces: FileSpaceRecord[] = [
  {
    id: 'personal',
    title: '我的云空间',
    subtitle: 'PERSONAL_FILE · 3.2GB / 10GB',
    note: '上传票据、确认上传、下载票据、回收站都从这里串起来',
    usage: 32,
    label: '个人',
    summary: [['3.2GB', '已用空间'], ['10GB', '个人配额'], ['2 次', '近 7 天上传']],
    files: [
      ['请假交接清单.docx', 'DOCX', '84KB', '今天 09:14', '已确认'],
      ['员工手册.pdf', 'PDF', '2.1MB', '昨天 17:08', '短期票据'],
      ['报销凭证.zip', 'ZIP', '16.4MB', '周一', '已确认'],
    ],
  },
  {
    id: 'workflow',
    title: '审批附件',
    subtitle: 'WF_REQUEST · 2 个申请引用',
    note: '审批参与人可以申请下载票据，下载后自动消费',
    usage: 54,
    label: '业务',
    summary: [['2 个', '申请引用'], ['1 次', '票据使用'], ['可追溯', '审计留痕']],
    files: [
      ['请假交接清单.docx', 'DOCX', '84KB', '今天 10:03', '申请引用'],
      ['年假说明.pdf', 'PDF', '640KB', '昨天 16:22', '审批附件'],
      ['审批备注.txt', 'TXT', '8KB', '昨天', '只读'],
    ],
  },
  {
    id: 'tickets',
    title: '下载票据',
    subtitle: 'ACTIVE / USED / EXPIRED',
    note: '短期有效、一次性消费，不直接暴露对象地址',
    usage: 18,
    label: '票据',
    summary: [['ACTIVE', '当前票据'], ['USED', '已消费'], ['EXPIRED', '过期状态']],
    files: [
      ['ticket-20260507-01', 'TICKET', 'ACTIVE', '剩余 8 分钟', '可下载'],
      ['ticket-20260506-11', 'TICKET', 'USED', '已消费', '审计已写入'],
      ['ticket-20260505-02', 'TICKET', 'EXPIRED', '已过期', '需重申'],
    ],
  },
  {
    id: 'recycle',
    title: '回收站',
    subtitle: '30 天保留',
    note: '恢复、彻底删除和容量释放都从这里理解',
    usage: 7,
    label: '回收',
    summary: [['30 天', '保留期'], ['2 项', '可恢复'], ['1 项', '待彻底删除']],
    files: [
      ['旧请假说明.docx', 'DOCX', '132KB', '两天前', '可恢复'],
      ['旧排班表.xlsx', 'XLSX', '148KB', '上周', '可恢复'],
      ['临时截图.png', 'PNG', '64KB', '上周', '待清理'],
    ],
  },
];

const workbenchItems: WorkbenchRecord[] = [
  {
    id: 'leave',
    title: '请假申请',
    label: 'workflow',
    subtitle: 'POST /api/employee/workflow/leave-requests',
    note: '请假单会先走余额校验，再进入审批链。',
    tone: 'blue',
    status: '可用',
    fields: [
      ['leaveTypeId', 'ANNUAL_LEAVE'],
      ['startAt', '2026-05-11 09:00:00'],
      ['endAt', '2026-05-12 18:00:00'],
      ['durationMinutes', '960'],
      ['reason', '家中有事请假两天'],
      ['attachmentRefIds', '[9601]'],
    ],
    stats: [['余额', '先校验'], ['附件', '可选'], ['结果', 'IN_APPROVAL']],
    steps: ['先查 /api/employee/leave/accounts', '再提交 /api/employee/workflow/leave-requests', '最后在我的申请里看状态'],
  },
  {
    id: 'requests',
    title: '我的申请',
    label: 'workflow',
    subtitle: 'GET /api/employee/workflow/requests',
    note: '查看自己发起的申请、当前节点和审批结果。',
    tone: 'gray',
    status: '已开放',
    fields: [
      ['requestId', '2051896265423310851'],
      ['requestNo', 'WF-LEAVE-20260506132606'],
      ['status', 'IN_APPROVAL / APPROVED / REJECTED'],
      ['currentStepName', '直属上级审批'],
    ],
    stats: [['列表', '分页查询'], ['详情', '申请状态'], ['轨迹', '动作流水']],
    steps: ['从消息或待办跳转进入', '按 requestId 查看申请详情', '按 actions 查看审批轨迹'],
  },
  {
    id: 'attendance',
    title: '我的考勤',
    label: 'attendance',
    subtitle: 'GET /api/employee/attendance/daily-results',
    note: '查看自己的日结果、异常和团队维度（领导可见）。',
    tone: 'amber',
    status: '已开放',
    fields: [
      ['workDate', '2026-05-07'],
      ['resultStatus', 'ABNORMAL'],
      ['lateMinutes', '0'],
      ['earlyLeaveMinutes', '0'],
      ['absenceMinutes', '20'],
      ['exceptionFlag', 'true'],
    ],
    stats: [['日结果', 'NORMAL / ABNORMAL'], ['异常', '迟到 / 早退 / 缺卡'], ['团队', '仅领导可见']],
    steps: ['打卡结果先写入考勤服务', '请假审批通过后触发重算', '异常页只看自己的结果'],
  },
  {
    id: 'balance',
    title: '我的假期',
    label: 'leave',
    subtitle: 'GET /api/employee/leave/accounts',
    note: '余额查询只返回当前员工自己的账户。',
    tone: 'green',
    status: '已开放',
    fields: [
      ['leaveCode', 'ANNUAL_LEAVE'],
      ['availableMinutes', '960'],
      ['frozenMinutes', '0'],
      ['status', 'ACTIVE'],
      ['lastTxnType', 'ADJUST'],
    ],
    stats: [['余额', '分钟级'], ['扣减', '审批通过'], ['流水', '可追溯']],
    steps: ['余额先查账户', '申请时先校验再提交', '审批通过后扣减并重算考勤'],
  },
  {
    id: 'files',
    title: '文件中心',
    label: 'file',
    subtitle: 'POST /api/employee/files/upload-tickets',
    note: '上传票据、确认上传、下载票据和回收站都在文件中心。',
    tone: 'blue',
    status: '已开放',
    fields: [
      ['ownerType', 'PERSONAL_FILE'],
      ['uploadTicket', 'CREATED / UPLOADED / CONFIRMED'],
      ['downloadTicket', 'ACTIVE / USED / EXPIRED'],
      ['trash', '可恢复 / 可彻底删除'],
    ],
    stats: [['上传', '两段式'], ['下载', '短期票据'], ['安全', '可审计']],
    steps: ['先申请上传票据', '再直传对象存储', '最后确认上传生成引用'],
  },
  {
    id: 'agent',
    title: 'Agent 问答',
    label: 'future',
    subtitle: 'module-agent 待后续接入',
    note: '先保留入口，不把它做成假完成。',
    tone: 'gray',
    status: '待后续',
    fields: [
      ['触发器', 'SPI 回调'],
      ['可见范围', '授权上下文'],
      ['执行结果', '审计可追溯'],
    ],
    stats: [['现在', '只保留入口'], ['后续', 'Agent 闭环'], ['规则', '可审计']],
    steps: ['当前先不做伪接口', '等 module-agent 落地后再接', '入口保留在工作台即可'],
    disabled: true,
  },
];

const profileItems: ProfileRecord[] = [
  {
    id: 'overview',
    title: '账号概览',
    subtitle: 'SESSION 会话 / 员工身份 / 组织归属',
    note: '默认进入 employee-web，默认能访问消息、待办、文档、工作台和我的。',
    tone: 'blue',
    stats: [['林澈', '当前用户'], ['在职', '状态'], ['上海', '地点']],
    fields: [['loginName', currentUser.loginName], ['email', currentUser.email], ['workNo', currentUser.workNo], ['phone', currentUser.phone]],
  },
  {
    id: 'relations',
    title: '组织关系',
    subtitle: '主部门 / 直属上级 / 直属下级',
    note: '领导不是后台管理员，是员工能力上的增强身份。',
    tone: 'green',
    stats: [['运营中心 / 运营一部', '主部门'], ['周悦', '直属上级'], ['2 人', '直属下级']],
    fields: [['部门', currentUser.department], ['直属上级', currentUser.manager], ['身份', '员工 / 领导（视组织关系而定）']],
  },
  {
    id: 'security',
    title: '账号安全',
    subtitle: '登录、邮箱、密码重置与会话策略',
    note: '认证使用服务端 Session，不是纯 JWT。',
    tone: 'amber',
    stats: [['登录', '账号密码'], ['邮箱', '可绑定'], ['会话', 'SESSION']],
    fields: [['登录入口', '/api/employee/auth/login'], ['邮箱验证码', '/api/employee/auth/email/send-code'], ['绑定邮箱', '/api/employee/auth/email/bind'], ['密码重置', '/api/employee/auth/password/reset']],
  },
];

function findById<T extends { id: string }>(items: T[], id: string) {
  return items.find((item) => item.id === id) ?? items[0];
}

function api(
  method: ApiContractHint['method'],
  path: string,
  status: ApiContractStatus,
  note: string,
): ApiContractHint {
  return { method, path, status, note };
}

function includesKeyword(values: Array<string | number | undefined>, keyword: string) {
  if (!keyword) return true;
  const normalized = keyword.toLowerCase();
  return values.some((value) => String(value ?? '').toLowerCase().includes(normalized));
}

function isPendingStatus(status: string) {
  return ['PENDING', 'OPEN', 'IN_APPROVAL', 'ABNORMAL'].includes(status);
}

function matchesConversationFilter(item: ConversationRecord, filter: SectionFilter) {
  if (filter === '全部') return true;
  if (filter === '未处理') return item.unread > 0 || isPendingStatus(item.status);
  if (filter === 'Agent') return item.kind === 'agent' || item.title.includes('Agent') || item.tag.includes('Agent');
  if (filter === '组织群') return item.kind === 'team';
  if (filter === '私聊') return item.kind === 'approval' || item.kind === 'attendance' || item.kind === 'file';
  return true;
}

export const useShellStore = defineStore('employee-shell', {
  state: () => ({
    section: 'messages' as SectionId,
    detailOpen: false,
    mobileView: 'list' as 'list' | 'detail',
    listWidth: 336,
    search: {
      messages: '',
      todos: '',
      files: '',
      workbench: '',
      profile: '',
    },
    filters: {
      messages: '全部' as SectionFilter,
      todos: '全部' as SectionFilter,
      files: '全部' as SectionFilter,
      workbench: '全部' as SectionFilter,
      profile: '全部' as SectionFilter,
    },
    active: {
      messages: 'leave-approval',
      todos: 'todo-approval',
      files: 'personal',
      workbench: 'leave',
      profile: 'overview',
    },
    conversations,
    todos: todoItems,
    files: fileSpaces,
    workbench: workbenchItems,
    profile: profileItems,
  }),
  getters: {
    totalUnread(state): number {
      return state.conversations.reduce((sum, item) => sum + item.unread, 0);
    },
    activeConversation(state): ConversationRecord {
      return findById(state.conversations, state.active.messages);
    },
    activeTodo(state): TodoRecord {
      return findById(state.todos, state.active.todos);
    },
    activeFile(state): FileSpaceRecord {
      return findById(state.files, state.active.files);
    },
    activeWorkbench(state): WorkbenchRecord {
      return findById(state.workbench, state.active.workbench);
    },
    activeProfile(state): ProfileRecord {
      return findById(state.profile, state.active.profile);
    },
    currentMeta(state): SectionMeta {
      return sectionMeta[state.section];
    },
    filteredConversations(state): ConversationRecord[] {
      return state.conversations.filter((item) => matchesConversationFilter(item, state.filters.messages))
        .filter((item) => includesKeyword([
          item.title,
          item.subtitle,
          item.tag,
          item.flow,
          item.status,
        ], state.search.messages));
    },
    filteredTodos(state): TodoRecord[] {
      return state.todos
        .filter((item) => state.filters.todos !== '未处理' || isPendingStatus(item.status))
        .filter((item) => includesKeyword([
          item.title,
          item.subtitle,
          item.note,
          item.status,
          item.priority,
          item.requestNo,
          item.taskId,
          item.currentStepName,
        ], state.search.todos));
    },
    filteredFiles(state): FileSpaceRecord[] {
      return state.files.filter((item) => includesKeyword([
        item.title,
        item.subtitle,
        item.note,
        item.label,
        item.files.map((file) => file.join(' ')).join(' '),
      ], state.search.files));
    },
    filteredWorkbench(state): WorkbenchRecord[] {
      return state.workbench
        .filter((item) => state.filters.workbench !== '未处理' || item.status !== '已开放')
        .filter((item) => state.filters.workbench !== 'Agent' || item.id === 'agent')
        .filter((item) => includesKeyword([
          item.title,
          item.subtitle,
          item.note,
          item.status,
          item.label,
        ], state.search.workbench));
    },
    filteredProfile(state): ProfileRecord[] {
      return state.profile.filter((item) => includesKeyword([
        item.title,
        item.subtitle,
        item.note,
        item.tone,
        item.fields.map((field) => field.join(' ')).join(' '),
      ], state.search.profile));
    },
  },
  actions: {
    setSection(section: SectionId) {
      this.section = section;
    },
    setSearch(section: SectionId, value: string) {
      this.search[section] = value;
    },
    setFilter(section: SectionId, filter: SectionFilter) {
      this.filters[section] = filter;
    },
    selectItem(section: SectionId, id: string) {
      this.active[section] = id;
      this.mobileView = 'detail';
      if (section === 'messages') {
        const active = this.conversations.find((item) => item.id === id);
        if (active) active.unread = 0;
      }
    },
    setDetailOpen(open: boolean) {
      this.detailOpen = open;
    },
    toggleDetail() {
      this.detailOpen = !this.detailOpen;
    },
    setMobileView(view: 'list' | 'detail') {
      this.mobileView = view;
    },
    setListWidth(width: number) {
      this.listWidth = Math.max(260, Math.min(480, Math.round(width)));
    },
    mergeBackendConversations(backendConversations: ConversationRecord[]) {
      if (!backendConversations.length) return;
      const backendIds = new Set(backendConversations.map((item) => item.id));
      const fallbackConversations = conversations.filter((item) => !backendIds.has(item.id));
      this.conversations = [...backendConversations, ...fallbackConversations];
      if (!backendIds.has(this.active.messages)) {
        this.active.messages = this.conversations[0]?.id ?? this.active.messages;
      }
    },
    mergeBackendMessages(conversationId: string, backendMessages: ConversationMessage[]) {
      const conversation = this.conversations.find((item) => item.id === conversationId);
      if (!conversation || !backendMessages.length) return;
      conversation.messages = backendMessages;
      const latestMessage = backendMessages[backendMessages.length - 1];
      conversation.subtitle = latestMessage.body;
      conversation.meta = latestMessage.time || conversation.meta;
      conversation.unread = 0;
    },
    appendMessage(text: string) {
      const conversation = this.activeConversation;
      conversation.messages.push({
        id: `${conversation.id}-${Date.now()}`,
        author: currentUser.name,
        role: '申请人',
        body: text,
        side: 'right',
      });
      conversation.subtitle = text;
      conversation.meta = '刚刚';
      conversation.unread = 0;
    },
  },
});

export const shellUser = currentUser;
