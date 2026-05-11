const navItems = [
  { id: "messages", label: "消息", glyph: "息" },
  { id: "todos", label: "待办", glyph: "办" },
  { id: "files", label: "文档", glyph: "档" },
  { id: "workbench", label: "工作台", glyph: "台" },
  { id: "profile", label: "我的", glyph: "我" },
];

const sectionMeta = {
  messages: {
    kicker: "默认入口",
    title: "消息",
    subtitle: "工作流消息流、协同消息和附件提醒都先从这里进入",
    search: "搜索会话、待办、附件、状态",
    primaryAction: {
      label: "请假申请",
      action: "open-workbench",
      app: "leave",
    },
    secondaryAction: {
      label: "查看待办",
      action: "switch-section",
      section: "todos",
    },
  },
  todos: {
    kicker: "统一处理",
    title: "待办",
    subtitle: "审批、附件、考勤和文件提醒会汇聚到这里",
    search: "搜索待办、申请号、处理人",
    primaryAction: {
      label: "刷新列表",
      action: "toast",
      message: "已刷新待办列表。",
    },
    secondaryAction: {
      label: "打开申请",
      action: "open-workbench",
      app: "requests",
    },
  },
  files: {
    kicker: "文件中心",
    title: "文档",
    subtitle: "个人文件、审批附件、下载票据和回收站",
    search: "搜索文件、票据、状态",
    primaryAction: {
      label: "申请上传",
      action: "toast",
      message: "上传流程是申请票据、直传对象、确认上传。",
    },
    secondaryAction: {
      label: "回收站",
      action: "switch-item",
      item: "recycle",
    },
  },
  workbench: {
    kicker: "业务办理",
    title: "工作台",
    subtitle: "请假、考勤、假期、文件和申请入口都从这里展开",
    search: "搜索请假、考勤、假期、文件",
    primaryAction: {
      label: "请假申请",
      action: "switch-item",
      item: "leave",
    },
    secondaryAction: {
      label: "我的考勤",
      action: "switch-item",
      item: "attendance",
    },
  },
  profile: {
    kicker: "个人中心",
    title: "我的",
    subtitle: "身份、组织、账号安全和可访问范围",
    search: "搜索资料、安全、权限",
    primaryAction: {
      label: "绑定邮箱",
      action: "toast",
      message: "员工端邮箱绑定和验证码入口已按认证文档预留。",
    },
    secondaryAction: {
      label: "重置密码",
      action: "toast",
      message: "密码重置入口已按认证文档预留。",
    },
  },
};

const currentUser = {
  name: "林澈",
  shortName: "林",
  loginName: "lin.che",
  email: "lin.che@workforce.local",
  title: "运营专员",
  department: "运营中心 / 运营一部",
  manager: "周悦",
  status: "在职",
  workNo: "WFH-1027",
  location: "上海",
  phone: "138-0000-1027",
};

const messageThreads = [
  {
    id: "wf-approval",
    title: "王晨年假申请",
    subtitle: "工作流消息 · 待审批 · 直属上级周悦",
    note: "请假 2 天，余额预审通过，等待审批确认",
    time: "10:24",
    unread: 2,
    tone: "blue",
    label: "审批",
    status: "PENDING",
    participants: [
      ["王晨", "申请人"],
      ["周悦", "审批人"],
      ["HR 助手", "预审 Agent"],
    ],
    relatedApis: [
      "GET /api/employee/workflow/todos",
      "GET /api/employee/workflow/requests/{requestId}",
      "POST /api/employee/workflow/tasks/{taskId}/approve",
    ],
    messages: [
      {
        kind: "system",
        text: "请假申请已写入业务申请头，待办已投递给周悦。",
      },
      {
        kind: "card",
        card: {
          tone: "blue",
          title: "申请概览",
          desc: "余额预审通过，审批链将先进入直属上级。",
          stats: [
            ["WF-LEAVE-20260506-01", "申请号"],
            ["PENDING", "待办状态"],
            ["高", "优先级"],
          ],
          actionLabel: "打开待办",
          action: { type: "switch-section", section: "todos" },
        },
      },
      {
        kind: "text",
        author: "周悦",
        initials: "周",
        text: "下午我会集中处理，请把交接清单再确认一次。",
      },
      {
        kind: "text",
        author: "林澈",
        initials: "林",
        own: true,
        text: "附件已上传，补充说明放到文件中心了。",
      },
    ],
  },
  {
    id: "ops-group",
    title: "运营一部组织群",
    subtitle: "组织群 · 18 人 · 附件与待办提醒已接入",
    note: "五月排班和补卡说明在群里同步了",
    time: "09:42",
    unread: 3,
    tone: "gold",
    label: "组织群",
    status: "ACTIVE",
    participants: [
      ["周悦", "部门负责人"],
      ["王晨", "运营专员"],
      ["许知夏", "运营专员"],
      ["HR 助手", "会话问答 Agent"],
    ],
    relatedApis: [
      "GET /api/employee/me/profile",
      "GET /api/employee/workflow/todos",
      "GET /api/employee/files/quota",
    ],
    messages: [
      {
        kind: "text",
        author: "周悦",
        initials: "周",
        text: "今天先处理五月排班确认，异常打卡先在待办里统一看。",
      },
      {
        kind: "text",
        author: "王晨",
        initials: "王",
        text: "补卡说明已经发到群文件了，大家走工作台里的申请入口。",
      },
      {
        kind: "card",
        card: {
          tone: "amber",
          title: "团队协同摘要",
          desc: "这里只展示当前组织群与个人待办的交叉信息。",
          stats: [
            ["3", "补卡待处理"],
            ["1", "审批待办"],
            ["2", "文件待确认"],
          ],
          actionLabel: "查看待办",
          action: { type: "switch-section", section: "todos" },
        },
      },
    ],
  },
  {
    id: "hr-agent",
    title: "HR 助手",
    subtitle: "会话问答 Agent · 已授权员工手册",
    note: "年假余额还剩 5 天，建议避开团队峰值",
    time: "昨天",
    unread: 1,
    tone: "green",
    label: "Agent",
    status: "READY",
    participants: [
      ["HR 助手", "知识问答 Agent"],
      ["林澈", "当前用户"],
    ],
    relatedApis: [
      "GET /api/employee/leave/accounts",
      "GET /api/employee/workflow/requests",
      "GET /api/employee/attendance/daily-results",
    ],
    messages: [
      {
        kind: "text",
        author: "林澈",
        initials: "林",
        own: true,
        text: "我下周想请两天年假，余额够吗？",
      },
      {
        kind: "card",
        card: {
          tone: "green",
          title: "请假预审",
          desc: "低风险建议可直接带入草稿，高风险动作仍需人工确认。",
          stats: [
            ["5 天", "当前余额"],
            ["2 天", "计划使用"],
            ["周悦", "首个审批人"],
          ],
          actionLabel: "生成草稿",
          action: { type: "switch-item", section: "workbench", item: "leave" },
        },
      },
    ],
  },
  {
    id: "attendance",
    title: "考勤提醒",
    subtitle: "个人考勤 · 我的日结果与异常提示",
    note: "5 月 3 日存在签退缺卡，结果会进入异常列表",
    time: "周三",
    unread: 1,
    tone: "amber",
    label: "考勤",
    status: "ABNORMAL",
    participants: [
      ["林澈", "本人"],
      ["考勤系统", "结果计算"],
      ["周悦", "团队查看"],
    ],
    relatedApis: [
      "POST /api/employee/attendance/punch",
      "GET /api/employee/attendance/daily-results",
      "GET /api/employee/attendance/exceptions",
    ],
    messages: [
      {
        kind: "text",
        author: "系统",
        initials: "系",
        text: "5 月 3 日签退缺卡，日结果已标记为异常。",
      },
      {
        kind: "card",
        card: {
          tone: "amber",
          title: "考勤结果",
          desc: "员工端只看自己的结果，团队结果仅领导可见。",
          stats: [
            ["ABNORMAL", "结果状态"],
            ["20 分钟", "缺失分钟"],
            ["1 条", "异常记录"],
          ],
          actionLabel: "查看考勤",
          action: { type: "switch-item", section: "workbench", item: "attendance" },
        },
      },
    ],
  },
  {
    id: "files",
    title: "文件中心通知",
    subtitle: "个人文件 · 下载票据与回收站提醒",
    note: "员工手册下载票据还剩 8 分钟",
    time: "昨天",
    unread: 0,
    tone: "slate",
    label: "文件",
    status: "ACTIVE",
    participants: [
      ["林澈", "文件拥有者"],
      ["文件服务", "上传确认"],
    ],
    relatedApis: [
      "POST /api/employee/files/upload-tickets",
      "POST /api/employee/files/confirm-upload",
      "GET /api/employee/files/download/{ticketNo}",
    ],
    messages: [
      {
        kind: "system",
        text: "个人文件空间已更新，下载票据会在短期内过期。",
      },
      {
        kind: "card",
        card: {
          tone: "slate",
          title: "下载票据",
          desc: "票据只允许一次性使用，过期后可重新申请。",
          stats: [
            ["ACTIVE", "当前状态"],
            ["8 分钟", "剩余时间"],
            ["PERSONAL_FILE", "对象归属"],
          ],
          actionLabel: "查看文件",
          action: { type: "switch-section", section: "files" },
        },
      },
    ],
  },
];

const todoItems = [
  {
    id: "todo-approval",
    title: "王晨年假申请",
    subtitle: "审批待办 · 周悦待处理",
    note: "请假 2 天，余额预审通过",
    time: "10:24",
    status: "PENDING",
    priority: "高",
    requestNo: "WF-LEAVE-20260506-01",
    taskId: "TASK-20481",
    currentStepName: "直属上级审批",
    summary: "员工提交年假申请，待审批人确认交接安排与团队排班冲突。",
    fields: [
      ["申请人", "王晨"],
      ["请假类型", "年假"],
      ["起止时间", "2026-05-11 09:00 至 2026-05-12 18:00"],
      ["预审结果", "余额充足"],
    ],
    timeline: ["王晨提交申请", "HR 助手完成余额预审", "等待周悦审批", "通过后写入假期台账"],
    apis: [
      "GET /api/employee/workflow/todos",
      "POST /api/employee/workflow/tasks/{taskId}/approve",
      "POST /api/employee/workflow/tasks/{taskId}/reject",
    ],
    actions: [
      { label: "审批通过", action: "toast", message: "待办处理会在后端通过 workflow 接口写入审批动作。" },
      { label: "驳回", action: "toast", message: "驳回动作会通过 /tasks/{taskId}/reject 完成。" },
    ],
  },
  {
    id: "todo-ticket",
    title: "员工手册下载票据",
    subtitle: "文件提醒 · 仅本人可用",
    note: "短期票据剩余 8 分钟",
    time: "剩余 8 分钟",
    status: "ACTIVE",
    priority: "中",
    requestNo: "FILE-TICKET-20260507-02",
    taskId: "FILE-TICKET-02",
    currentStepName: "文件下载票据",
    summary: "文件下载采用短期票据，过期后可重新申请，不暴露固定对象地址。",
    fields: [
      ["文件名", "员工手册.pdf"],
      ["票据状态", "ACTIVE"],
      ["ownerType", "PERSONAL_FILE"],
      ["安全策略", "一次性下载、可审计"],
    ],
    timeline: ["申请下载票据", "生成短期下载入口", "票据即将过期", "必要时重新申请"],
    apis: [
      "POST /api/employee/files/{attachmentRefId}/download-tickets",
      "GET /api/employee/files/download/{ticketNo}",
    ],
    actions: [
      { label: "重新申请", action: "toast", message: "下载票据到期后可以重新申请，不影响原文件引用。" },
      { label: "查看文件", action: "switch-section", section: "files" },
    ],
  },
  {
    id: "todo-attendance",
    title: "5 月 3 日考勤缺卡",
    subtitle: "个人待办 · 异常提醒",
    note: "签退缺失，需要在工作台继续处理",
    time: "今天 18:00",
    status: "OPEN",
    priority: "中",
    requestNo: "ATT-20260503-01",
    taskId: "ATT-20260503-01",
    currentStepName: "考勤异常处理",
    summary: "考勤异常会进入统一待办和个人考勤入口，员工查看自己的结果和异常。",
    fields: [
      ["异常日期", "2026-05-03"],
      ["异常类型", "MISS_PUNCH"],
      ["日结果", "ABNORMAL"],
      ["建议动作", "查看个人考勤"],
    ],
    timeline: ["系统生成异常", "写入日结果", "投递个人待办", "在工作台继续查看"],
    apis: [
      "POST /api/employee/attendance/punch",
      "GET /api/employee/attendance/daily-results",
      "GET /api/employee/attendance/exceptions",
    ],
    actions: [
      { label: "查看考勤", action: "switch-item", section: "workbench", item: "attendance" },
      { label: "查看详情", action: "toast", message: "考勤详情已按员工端接口口径保留。" },
    ],
  },
  {
    id: "todo-request",
    title: "我的申请状态更新",
    subtitle: "流程提醒 · 申请已进入审批中",
    note: "请假申请状态已从草稿切到审批中",
    time: "周一",
    status: "IN_APPROVAL",
    priority: "低",
    requestNo: "WF-LEAVE-20260506-01",
    taskId: "REQ-2051",
    currentStepName: "申请详情同步",
    summary: "申请列表用于查看自己的申请、当前节点和审批结果。",
    fields: [
      ["申请编号", "WF-LEAVE-20260506-01"],
      ["当前状态", "IN_APPROVAL"],
      ["当前节点", "直属上级审批"],
      ["列表来源", "GET /api/employee/workflow/requests"],
    ],
    timeline: ["员工提交申请", "流程进入审批中", "我的申请列表同步", "待审批结果回写"],
    apis: [
      "GET /api/employee/workflow/requests",
      "GET /api/employee/workflow/requests/{requestId}",
      "GET /api/employee/workflow/requests/{requestId}/actions",
    ],
    actions: [
      { label: "打开申请", action: "switch-item", section: "workbench", item: "requests" },
      { label: "查看消息", action: "switch-section", section: "messages" },
    ],
  },
];

const fileSpaces = [
  {
    id: "personal",
    title: "我的云空间",
    subtitle: "PERSONAL_FILE · 3.2GB / 10GB",
    note: "上传票据、确认上传、下载票据、回收站都从这里串起来",
    usage: 32,
    label: "个人",
    summary: [
      ["3.2GB", "已用空间"],
      ["10GB", "个人配额"],
      ["2 次", "近 7 天上传"],
    ],
    files: [
      ["请假交接清单.docx", "DOCX", "84KB", "今天 09:14", "已确认"],
      ["员工手册.pdf", "PDF", "2.1MB", "昨天 17:08", "短期票据"],
      ["报销凭证.zip", "ZIP", "16.4MB", "周一", "已确认"],
    ],
  },
  {
    id: "workflow",
    title: "审批附件",
    subtitle: "WF_REQUEST · 2 个申请引用",
    note: "审批参与人可以申请下载票据，下载后自动消费",
    usage: 54,
    label: "业务",
    summary: [
      ["2 个", "申请引用"],
      ["1 次", "票据使用"],
      ["可追溯", "审计留痕"],
    ],
    files: [
      ["请假交接清单.docx", "DOCX", "84KB", "今天 10:03", "申请引用"],
      ["年假说明.pdf", "PDF", "640KB", "昨天 16:22", "审批附件"],
      ["审批备注.txt", "TXT", "8KB", "昨天", "只读"],
    ],
  },
  {
    id: "tickets",
    title: "下载票据",
    subtitle: "ACTIVE / USED / EXPIRED",
    note: "短期有效、一次性消费，不直接暴露对象地址",
    usage: 18,
    label: "票据",
    summary: [
      ["ACTIVE", "当前票据"],
      ["USED", "已消费"],
      ["EXPIRED", "过期状态"],
    ],
    files: [
      ["ticket-20260507-01", "TICKET", "ACTIVE", "剩余 8 分钟", "可下载"],
      ["ticket-20260506-11", "TICKET", "USED", "已消费", "审计已写入"],
      ["ticket-20260505-02", "TICKET", "EXPIRED", "已过期", "需重申"],
    ],
  },
  {
    id: "recycle",
    title: "回收站",
    subtitle: "30 天保留",
    note: "恢复、彻底删除和容量释放都从这里理解",
    usage: 7,
    label: "回收",
    summary: [
      ["30 天", "保留期"],
      ["2 项", "可恢复"],
      ["1 项", "待彻底删除"],
    ],
    files: [
      ["旧请假说明.docx", "DOCX", "132KB", "两天前", "可恢复"],
      ["旧排班表.xlsx", "XLSX", "148KB", "上周", "可恢复"],
      ["临时截图.png", "PNG", "64KB", "上周", "待清理"],
    ],
  },
];

const workbenchItems = [
  {
    id: "leave",
    title: "请假申请",
    label: "workflow",
    subtitle: "POST /api/employee/workflow/leave-requests",
    note: "请假单会先走余额校验，再进入审批链。",
    tone: "blue",
    status: "可用",
    fields: [
      ["leaveTypeId", "ANNUAL_LEAVE"],
      ["startAt", "2026-05-11 09:00:00"],
      ["endAt", "2026-05-12 18:00:00"],
      ["durationMinutes", "960"],
      ["reason", "家中有事请假两天"],
      ["attachmentRefIds", "[9601]"],
    ],
    stats: [
      ["余额", "先校验"],
      ["附件", "可选"],
      ["结果", "IN_APPROVAL"],
    ],
    steps: [
      "先查 /api/employee/leave/accounts",
      "再提交 /api/employee/workflow/leave-requests",
      "最后在我的申请里看状态",
    ],
  },
  {
    id: "requests",
    title: "我的申请",
    label: "workflow",
    subtitle: "GET /api/employee/workflow/requests",
    note: "查看自己发起的申请、当前节点和审批结果。",
    tone: "slate",
    status: "已开放",
    fields: [
      ["requestId", "2051896265423310851"],
      ["requestNo", "WF-LEAVE-20260506132606"],
      ["status", "IN_APPROVAL / APPROVED / REJECTED"],
      ["currentStepName", "直属上级审批"],
    ],
    stats: [
      ["列表", "分页查询"],
      ["详情", "申请状态"],
      ["轨迹", "动作流水"],
    ],
    steps: [
      "从消息或待办跳转进入",
      "按 requestId 查看申请详情",
      "按 actions 查看审批轨迹",
    ],
  },
  {
    id: "attendance",
    title: "我的考勤",
    label: "attendance",
    subtitle: "GET /api/employee/attendance/daily-results",
    note: "查看自己的日结果、异常和团队维度（领导可见）。",
    tone: "amber",
    status: "已开放",
    fields: [
      ["workDate", "2026-05-07"],
      ["resultStatus", "ABNORMAL"],
      ["lateMinutes", "0"],
      ["earlyLeaveMinutes", "0"],
      ["absenceMinutes", "20"],
      ["exceptionFlag", "true"],
    ],
    stats: [
      ["日结果", "NORMAL / ABNORMAL"],
      ["异常", "迟到 / 早退 / 缺卡"],
      ["团队", "仅领导可见"],
    ],
    steps: [
      "打卡结果先写入考勤服务",
      "请假审批通过后触发重算",
      "异常页只看自己的结果",
    ],
  },
  {
    id: "balance",
    title: "我的假期",
    label: "leave",
    subtitle: "GET /api/employee/leave/accounts",
    note: "余额查询只返回当前员工自己的账户。",
    tone: "green",
    status: "已开放",
    fields: [
      ["leaveCode", "ANNUAL_LEAVE"],
      ["availableMinutes", "960"],
      ["frozenMinutes", "0"],
      ["status", "ACTIVE"],
      ["lastTxnType", "ADJUST"],
    ],
    stats: [
      ["余额", "分钟级"],
      ["扣减", "审批通过"],
      ["流水", "可追溯"],
    ],
    steps: [
      "余额先查账户",
      "申请时先校验再提交",
      "审批通过后扣减并重算考勤",
    ],
  },
  {
    id: "files",
    title: "文件中心",
    label: "file",
    subtitle: "POST /api/employee/files/upload-tickets",
    note: "上传票据、确认上传、下载票据和回收站都在文件中心。",
    tone: "blue",
    status: "已开放",
    fields: [
      ["ownerType", "PERSONAL_FILE"],
      ["uploadTicket", "CREATED / UPLOADED / CONFIRMED"],
      ["downloadTicket", "ACTIVE / USED / EXPIRED"],
      ["trash", "可恢复 / 可彻底删除"],
    ],
    stats: [
      ["上传", "两段式"],
      ["下载", "短期票据"],
      ["安全", "可审计"],
    ],
    steps: [
      "先申请上传票据",
      "再直传对象存储",
      "最后确认上传生成引用",
    ],
  },
  {
    id: "agent",
    title: "Agent 问答",
    label: "future",
    subtitle: "module-agent 待后续接入",
    note: "先保留入口，不把它做成假完成。",
    tone: "slate",
    status: "待后续",
    fields: [
      ["触发器", "SPI 回调"],
      ["可见范围", "授权上下文"],
      ["执行结果", "审计可追溯"],
    ],
    stats: [
      ["现在", "只保留入口"],
      ["后续", "Agent 闭环"],
      ["规则", "可审计"],
    ],
    steps: [
      "当前先不做伪接口",
      "等 module-agent 落地后再接",
      "入口保留在工作台即可",
    ],
    disabled: true,
  },
];

const profileItems = [
  {
    id: "overview",
    title: "账号概览",
    subtitle: "SESSION 会话 / 员工身份 / 组织归属",
    note: "默认进入 employee-web，默认能访问消息、待办、文档、工作台和我的。",
    tone: "blue",
    stats: [
      ["林澈", "当前用户"],
      ["在职", "状态"],
      ["上海", "地点"],
    ],
    fields: [
      ["loginName", currentUser.loginName],
      ["email", currentUser.email],
      ["workNo", currentUser.workNo],
      ["phone", currentUser.phone],
    ],
  },
  {
    id: "relations",
    title: "组织关系",
    subtitle: "主部门 / 直属上级 / 直属下级",
    note: "领导不是后台管理员，是员工能力上的增强身份。",
    tone: "green",
    stats: [
      ["运营中心 / 运营一部", "主部门"],
      ["周悦", "直属上级"],
      ["2 人", "直属下级"],
    ],
    fields: [
      ["部门", currentUser.department],
      ["直属上级", currentUser.manager],
      ["身份", "员工 / 领导（视组织关系而定）"],
    ],
  },
  {
    id: "security",
    title: "账号安全",
    subtitle: "登录、邮箱、密码重置与会话策略",
    note: "认证使用服务端 Session，不是纯 JWT。",
    tone: "amber",
    stats: [
      ["登录", "账号密码"],
      ["邮箱", "可绑定"],
      ["会话", "SESSION"],
    ],
    fields: [
      ["登录入口", "/api/employee/auth/login"],
      ["邮箱验证码", "/api/employee/auth/email/send-code"],
      ["绑定邮箱", "/api/employee/auth/email/bind"],
      ["密码重置", "/api/employee/auth/password/reset"],
    ],
  },
];

const app = document.querySelector("#app");
const savedListWidth = Number(localStorage.getItem("employeePrototypeListWidth"));

const state = {
  section: "messages",
  detailOpen: false,
  mobileView: "list",
  listWidth: Number.isFinite(savedListWidth) && savedListWidth >= 260 && savedListWidth <= 480 ? savedListWidth : 336,
  active: {
    messages: "wf-approval",
    todos: "todo-approval",
    files: "personal",
    workbench: "leave",
    profile: "overview",
  },
  search: {
    messages: "",
    todos: "",
    files: "",
    workbench: "",
    profile: "",
  },
  pendingSearchFocus: null,
};

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function byId(list, id) {
  return list.find((item) => item.id === id) || list[0];
}

function isMobileLayout() {
  return window.matchMedia("(max-width: 640px)").matches;
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function activeMessage() {
  return byId(messageThreads, state.active.messages);
}

function activeTodo() {
  return byId(todoItems, state.active.todos);
}

function activeFileSpace() {
  return byId(fileSpaces, state.active.files);
}

function activeWorkbenchItem() {
  return byId(workbenchItems, state.active.workbench);
}

function activeProfileItem() {
  return byId(profileItems, state.active.profile);
}

function sectionItems(section) {
  switch (section) {
    case "messages":
      return messageThreads;
    case "todos":
      return todoItems;
    case "files":
      return fileSpaces;
    case "workbench":
      return workbenchItems;
    case "profile":
      return profileItems;
    default:
      return [];
  }
}

function searchValue(section) {
  return state.search[section] || "";
}

function filterItems(section, items) {
  const query = searchValue(section).trim().toLowerCase();
  if (!query) return items;
  return items.filter((item) => {
    const haystack = [
      item.title,
      item.subtitle,
      item.note,
      item.time,
      item.label,
      item.status,
      Array.isArray(item.fields) ? item.fields.flat().join(" ") : "",
      Array.isArray(item.summary) ? item.summary.flat().join(" ") : "",
    ]
      .join(" ")
      .toLowerCase();
    return haystack.includes(query);
  });
}

function toneClass(tone) {
  if (tone === "green") return "green";
  if (tone === "amber") return "amber";
  if (tone === "blue") return "blue";
  if (tone === "slate") return "slate";
  return "slate";
}

function render() {
  app.innerHTML = `
    <div class="app-shell ${state.detailOpen ? "detail-open" : ""} ${state.mobileView === "detail" ? "mobile-detail" : "mobile-list"}" style="--list-width: ${state.listWidth}px">
      ${renderRail()}
      ${renderListPane()}
      <div class="resize-handle" data-action="resize-list" role="separator" aria-orientation="vertical" aria-label="调整列表宽度" tabindex="0"></div>
      <main class="main-pane">${renderMainPane()}</main>
      <aside class="detail-panel">${renderDetailPanel()}</aside>
    </div>
    <div class="toast" role="status" aria-live="polite"></div>
  `;

  restoreSearchFocus();
}

function renderRail() {
  const buttons = navItems
    .map(
      (item) => `
        <button class="rail-button ${state.section === item.id ? "is-active" : ""}" data-nav="${item.id}" aria-label="${escapeHtml(item.label)}">
          <span class="rail-glyph">${escapeHtml(item.glyph)}</span>
          <span class="rail-label">${escapeHtml(item.label)}</span>
        </button>
      `,
    )
    .join("");

  return `
    <nav class="rail" aria-label="员工端一级功能">
      <div class="brand-mark">WH</div>
      <div class="rail-nav">${buttons}</div>
      <button class="rail-user" data-nav="profile" aria-label="我的">${currentUser.shortName}</button>
    </nav>
  `;
}

function renderListPane() {
  const meta = sectionMeta[state.section];
  const items = filterItems(state.section, sectionItems(state.section));
  return `
    <section class="list-pane">
      <header class="pane-head">
        <div class="pane-title-row">
          <div>
            <p class="pane-kicker">${escapeHtml(meta.kicker)}</p>
            <h1 class="pane-title">${escapeHtml(meta.title)}</h1>
            <p class="pane-subtitle">${escapeHtml(meta.subtitle)}</p>
          </div>
          <div class="pane-actions">
            ${renderSectionAction(meta.primaryAction, "primary")}
            ${renderSectionAction(meta.secondaryAction, "secondary")}
          </div>
        </div>
        <label class="search-wrap">
          <span class="sr-only">搜索</span>
          <input class="search-input" type="search" placeholder="${escapeHtml(meta.search)}" value="${escapeHtml(searchValue(state.section))}" data-search="${state.section}" />
        </label>
      </header>
      <div class="list-items">${renderListItems(items)}</div>
    </section>
  `;
}

function renderSectionAction(action, variant) {
  if (!action) return "";
  const cls = variant === "primary" ? "section-action primary" : "section-action secondary";
  const attrs = action.action === "switch-section"
    ? `data-action="${action.action}" data-section="${escapeHtml(action.section)}"`
    : action.action === "switch-item"
      ? `data-action="${action.action}" data-section="${escapeHtml(action.section)}" data-item="${escapeHtml(action.item)}"`
      : action.action === "open-workbench"
        ? `data-action="${action.action}" data-app="${escapeHtml(action.app)}"`
        : `data-action="${escapeHtml(action.action)}" data-message="${escapeHtml(action.message || "")}"`;
  return `<button class="${cls}" ${attrs}>${escapeHtml(action.label)}</button>`;
}

function renderListItems(items) {
  if (!items.length) {
    return `
      <div class="empty-state">
        <strong>没有匹配结果</strong>
        <p>换个关键词试试，或者清空搜索重新看。</p>
      </div>
    `;
  }

  return items
    .map((item) => {
      if (state.section === "messages") return renderMessageThreadItem(item);
      if (state.section === "todos") return renderTodoItem(item);
      if (state.section === "files") return renderFileSpaceItem(item);
      if (state.section === "workbench") return renderWorkbenchItem(item);
      return renderProfileItem(item);
    })
    .join("");
}

function renderMessageThreadItem(item) {
  return `
    <button class="list-item message-list-item ${state.active.messages === item.id ? "is-active" : ""}" data-select="${escapeHtml(item.id)}">
      <span class="avatar-wrap">
        <span class="avatar ${toneClass(item.tone)}">${escapeHtml(item.title.slice(0, 2))}</span>
        ${item.unread ? `<span class="badge unread-badge" aria-label="${item.unread} 条未读">${item.unread}</span>` : ""}
      </span>
      <span class="item-main">
        <span class="item-row">
          <strong class="item-title">${escapeHtml(item.title)}</strong>
          <span class="item-time">${escapeHtml(item.time)}</span>
        </span>
        <span class="item-note">${escapeHtml(item.note)}</span>
        <span class="item-meta">${escapeHtml(item.label)} · ${escapeHtml(item.status)}</span>
      </span>
    </button>
  `;
}

function renderTodoItem(item) {
  return `
    <button class="list-item ${state.active.todos === item.id ? "is-active" : ""}" data-select="${escapeHtml(item.id)}">
      <span class="avatar ${priorityTone(item.priority)}">${escapeHtml(item.priority.slice(0, 1))}</span>
      <span class="item-main">
        <span class="item-row">
          <strong class="item-title">${escapeHtml(item.title)}</strong>
          <span class="pill ${priorityTone(item.priority)}">${escapeHtml(item.priority)}</span>
        </span>
        <span class="item-note">${escapeHtml(item.note)}</span>
        <span class="item-meta">${escapeHtml(item.subtitle)} · ${escapeHtml(item.time)}</span>
      </span>
    </button>
  `;
}

function renderFileSpaceItem(item) {
  return `
    <button class="list-item ${state.active.files === item.id ? "is-active" : ""}" data-select="${escapeHtml(item.id)}">
      <span class="file-icon">${escapeHtml(item.label.slice(0, 2))}</span>
      <span class="item-main">
        <span class="item-row">
          <strong class="item-title">${escapeHtml(item.title)}</strong>
          <span class="item-time">${escapeHtml(item.usage)}%</span>
        </span>
        <span class="item-note">${escapeHtml(item.note)}</span>
        <span class="item-meta">${escapeHtml(item.subtitle)}</span>
      </span>
    </button>
  `;
}

function renderWorkbenchItem(item) {
  return `
    <button class="list-item ${state.active.workbench === item.id ? "is-active" : ""} ${item.disabled ? "is-disabled" : ""}" data-select="${escapeHtml(item.id)}" ${item.disabled ? "aria-disabled='true'" : ""}>
      <span class="app-icon">${escapeHtml(item.label.slice(0, 2))}</span>
      <span class="item-main">
        <span class="item-row">
          <strong class="item-title">${escapeHtml(item.title)}</strong>
          <span class="pill ${toneClass(item.tone)}">${escapeHtml(item.status)}</span>
        </span>
        <span class="item-note">${escapeHtml(item.note)}</span>
        <span class="item-meta">${escapeHtml(item.subtitle)}</span>
      </span>
    </button>
  `;
}

function renderProfileItem(item) {
  return `
    <button class="list-item ${state.active.profile === item.id ? "is-active" : ""}" data-select="${escapeHtml(item.id)}">
      <span class="avatar ${toneClass(item.tone)}">${escapeHtml(item.title.slice(0, 1))}</span>
      <span class="item-main">
        <span class="item-row">
          <strong class="item-title">${escapeHtml(item.title)}</strong>
          <span class="pill ${toneClass(item.tone)}">${escapeHtml(item.stats[0][0])}</span>
        </span>
        <span class="item-note">${escapeHtml(item.note)}</span>
        <span class="item-meta">${escapeHtml(item.subtitle)}</span>
      </span>
    </button>
  `;
}

function renderMainPane() {
  if (state.section === "messages") return renderMessages();
  if (state.section === "todos") return renderTodoDetail();
  if (state.section === "files") return renderFileCenter();
  if (state.section === "workbench") return renderWorkbench();
  return renderProfile();
}

function renderHeader(title, subtitle, actionsHtml = "") {
  return `
    <header class="workspace-head">
      <div class="workspace-title-group">
        <button class="text-button mobile-back" data-action="back-list" aria-label="返回列表">&lt;</button>
        <div class="workspace-head-text">
          <h2 class="workspace-title">${escapeHtml(title)}</h2>
          <p class="workspace-subtitle">${escapeHtml(subtitle)}</p>
        </div>
      </div>
      <div class="head-actions">
        ${actionsHtml}
        <button class="icon-button action-icon-detail" data-action="toggle-detail" aria-label="${state.detailOpen ? "收起详情" : "查看详情"}">
          <span class="desktop-action-label">${state.detailOpen ? "收起详情" : "详情"}</span>
          <span class="mobile-action-glyph" aria-hidden="true">i</span>
        </button>
      </div>
    </header>
  `;
}

function renderMessages() {
  const item = activeMessage();
  const messageCards = item.messages.map(renderMessageBlock).join("");
  return `
    <article class="workspace">
      ${renderHeader(item.title, item.subtitle, `
        <button class="text-button" data-action="switch-section" data-section="todos">查看待办</button>
        <button class="text-button" data-action="open-workbench" data-app="leave">请假申请</button>
      `)}
      <div class="workspace-body">
        <div class="message-summary">
          <div class="summary-card">
            <strong>${item.label}</strong>
            <span>当前消息流</span>
          </div>
          <div class="summary-card">
            <strong>${item.status}</strong>
            <span>当前状态</span>
          </div>
          <div class="summary-card">
            <strong>${item.unread}</strong>
            <span>未读消息</span>
          </div>
        </div>
        <div class="message-stream">${messageCards}</div>
      </div>
      <footer class="composer">
        <button class="attach-button" data-action="toast" data-message="附件会先申请上传票据，再确认生成引用。" aria-label="添加附件">＋</button>
        <textarea id="messageInput" class="composer-input" rows="1" placeholder="输入消息，支持 @Agent"></textarea>
        <button class="primary-button" data-action="send-message">发送</button>
      </footer>
    </article>
  `;
}

function renderMessageBlock(block) {
  if (block.kind === "system") {
    return `
      <div class="message system">
        <div class="bubble system-bubble">${escapeHtml(block.text)}</div>
      </div>
    `;
  }

  if (block.kind === "card") {
    return `
      <div class="message card-message">
        <div class="bubble-wrap">
          ${renderInfoCard(block.card)}
        </div>
      </div>
    `;
  }

  return `
    <div class="message ${block.own ? "own" : ""}">
      <span class="avatar ${block.author === "HR 助手" ? "blue" : block.author === "系统" ? "amber" : ""}">${escapeHtml(block.initials)}</span>
      <div class="bubble-wrap">
        <span class="message-name">${escapeHtml(block.author)}</span>
        <div class="bubble">${escapeHtml(block.text)}</div>
      </div>
    </div>
  `;
}

function renderInfoCard(card) {
  return `
    <section class="info-card ${toneClass(card.tone)}">
      <div class="info-card-head">
        <div>
          <h3 class="info-card-title">${escapeHtml(card.title)}</h3>
          <p class="info-card-desc">${escapeHtml(card.desc)}</p>
        </div>
        <span class="pill ${toneClass(card.tone)}">${escapeHtml(card.tone === "green" ? "可用" : card.tone === "amber" ? "待处理" : "信息")}</span>
      </div>
      <div class="mini-grid">
        ${card.stats
          .map(
            ([value, label]) => `
              <div class="mini-stat">
                <strong>${escapeHtml(value)}</strong>
                <span>${escapeHtml(label)}</span>
              </div>
            `,
          )
          .join("")}
      </div>
      <div class="card-actions">
        ${renderCardAction(card.actionLabel, card.action)}
      </div>
    </section>
  `;
}

function renderCardAction(label, action) {
  if (!action) return "";
  if (action.type === "switch-section") {
    return `<button class="text-button" data-action="switch-section" data-section="${escapeHtml(action.section)}">${escapeHtml(label)}</button>`;
  }
  if (action.type === "switch-item") {
    return `<button class="text-button" data-action="switch-item" data-section="${escapeHtml(action.section)}" data-item="${escapeHtml(action.item)}">${escapeHtml(label)}</button>`;
  }
  if (action.type === "open-workbench") {
    return `<button class="text-button" data-action="open-workbench" data-app="${escapeHtml(action.app)}">${escapeHtml(label)}</button>`;
  }
  return `<button class="text-button" data-action="toast" data-message="${escapeHtml(action.message || label)}">${escapeHtml(label)}</button>`;
}

function renderTodoDetail() {
  const item = activeTodo();
  return `
    <article class="workspace">
      ${renderHeader(item.title, `${item.subtitle} · ${item.requestNo}`, `
        <button class="primary-button" data-action="toast" data-message="审批通过动作会走 /api/employee/workflow/tasks/{taskId}/approve。">${escapeHtml(item.actions[0].label)}</button>
        <button class="text-button" data-action="toast" data-message="驳回动作会走 /api/employee/workflow/tasks/{taskId}/reject。">${escapeHtml(item.actions[1].label)}</button>
      `)}
      <div class="workspace-body todo-detail">
        <section class="panel span-12">
          <div class="panel-top">
            <span class="pill ${priorityTone(item.priority)}">${escapeHtml(item.priority)}优先级</span>
            <span class="pill slate">${escapeHtml(item.currentStepName)}</span>
          </div>
          <h3 class="panel-title">处理说明</h3>
          <p class="panel-note">${escapeHtml(item.summary)}</p>
        </section>
        <div class="dashboard-grid">
          <section class="panel span-7">
            <h3 class="panel-title">关键字段</h3>
            ${item.fields.map(([key, value]) => keyValueRow(key, value)).join("")}
          </section>
          <section class="panel span-5">
            <h3 class="panel-title">流转轨迹</h3>
            <ul class="timeline">${item.timeline.map((step) => `<li><strong>${escapeHtml(step)}</strong><span>${escapeHtml(item.status)}</span></li>`).join("")}</ul>
          </section>
          <section class="panel span-12">
            <h3 class="panel-title">接口对应</h3>
            <div class="api-list">${item.apis.map((api) => `<span class="api-pill">${escapeHtml(api)}</span>`).join("")}</div>
          </section>
        </div>
      </div>
    </article>
  `;
}

function renderFileCenter() {
  const space = activeFileSpace();
  return `
    <article class="workspace">
      ${renderHeader(space.title, `${space.subtitle} · ${space.note}`, `
        <button class="text-button" data-action="toast" data-message="上传流程是申请票据、直传对象、确认上传。">申请上传</button>
        <button class="text-button" data-action="switch-item" data-section="files" data-item="tickets">下载票据</button>
      `)}
      <div class="workspace-body">
        <div class="dashboard-grid">
          <section class="panel span-4">
            <h3 class="panel-title">空间用量</h3>
            <p class="panel-note">${escapeHtml(space.note)}</p>
            <div class="quota-bar"><div class="quota-fill" style="width:${space.usage}%"></div></div>
            <div class="mini-grid">
              ${space.summary
                .map(
                  ([value, label]) => `
                    <div class="mini-stat">
                      <strong>${escapeHtml(value)}</strong>
                      <span>${escapeHtml(label)}</span>
                    </div>
                  `,
                )
                .join("")}
            </div>
          </section>
          <section class="panel span-8">
            <h3 class="panel-title">文件列表</h3>
            <table class="file-table">
              <thead>
                <tr>
                  <th>文件</th>
                  <th>类型</th>
                  <th>大小</th>
                  <th>更新</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                ${space.files
                  .map(
                    ([name, type, size, time, status]) => `
                      <tr>
                        <td data-label="文件"><strong>${escapeHtml(name)}</strong></td>
                        <td data-label="类型">${escapeHtml(type)}</td>
                        <td data-label="大小">${escapeHtml(size)}</td>
                        <td data-label="更新">${escapeHtml(time)}</td>
                        <td data-label="状态"><span class="pill blue">${escapeHtml(status)}</span></td>
                      </tr>
                    `,
                  )
                  .join("")}
              </tbody>
            </table>
          </section>
          <section class="panel span-12">
            <h3 class="panel-title">上传链路</h3>
            <div class="mini-grid">
              <div class="mini-stat"><strong>1</strong><span>申请上传票据</span></div>
              <div class="mini-stat"><strong>2</strong><span>客户端直传对象</span></div>
              <div class="mini-stat"><strong>3</strong><span>确认上传并生成引用</span></div>
            </div>
          </section>
        </div>
      </div>
    </article>
  `;
}

function renderWorkbench() {
  const item = activeWorkbenchItem();
  return `
    <article class="workspace">
      ${renderHeader(item.title, `${item.subtitle} · ${item.note}`, `
        <button class="text-button" data-action="switch-section" data-section="messages">回到消息</button>
        <button class="text-button" data-action="switch-section" data-section="todos">查看待办</button>
      `)}
      <div class="workspace-body">
        <div class="dashboard-grid">
          <section class="panel span-5">
            <h3 class="panel-title">业务入口</h3>
            <div class="app-grid">
              ${workbenchItems
                .map(
                  (entry) => `
                    <button class="app-tile ${state.active.workbench === entry.id ? "is-active" : ""} ${entry.disabled ? "is-disabled" : ""}" data-app="${escapeHtml(entry.id)}" ${entry.disabled ? "aria-disabled='true'" : ""}>
                      <span class="app-icon ${toneClass(entry.tone)}">${escapeHtml(entry.label.slice(0, 2))}</span>
                      <strong>${escapeHtml(entry.title)}</strong>
                      <p>${escapeHtml(entry.note)}</p>
                    </button>
                  `,
                )
                .join("")}
            </div>
          </section>
          <section class="panel span-7">
            <h3 class="panel-title">表单与接口</h3>
            <div class="form-grid">
              ${item.fields
                .map(
                  ([key, value], index) => `
                    <label class="field ${index === item.fields.length - 1 ? "span-2" : ""}">
                      <span>${escapeHtml(key)}</span>
                      ${index === item.fields.length - 1 ? `<textarea>${escapeHtml(value)}</textarea>` : `<input value="${escapeHtml(value)}" />`}
                    </label>
                  `,
                )
                .join("")}
            </div>
          </section>
          <section class="panel span-6">
            <h3 class="panel-title">当前能力</h3>
            <div class="mini-grid">
              ${item.stats
                .map(
                  ([value, label]) => `
                    <div class="mini-stat">
                      <strong>${escapeHtml(value)}</strong>
                      <span>${escapeHtml(label)}</span>
                    </div>
                  `,
                )
                .join("")}
            </div>
          </section>
          <section class="panel span-6">
            <h3 class="panel-title">处理顺序</h3>
            <ul class="timeline">${item.steps.map((step) => `<li><strong>${escapeHtml(step)}</strong><span>${escapeHtml(item.status)}</span></li>`).join("")}</ul>
          </section>
        </div>
      </div>
    </article>
  `;
}

function renderProfile() {
  const item = activeProfileItem();
  return `
    <article class="workspace">
      ${renderHeader(currentUser.name, `${currentUser.department} · ${currentUser.title}`, `
        <button class="text-button" data-action="toast" data-message="编辑资料会走员工端白名单字段。">编辑资料</button>
        <button class="text-button" data-action="toast" data-message="退出登录会清理当前 Session。">退出登录</button>
      `)}
      <div class="workspace-body">
        <div class="dashboard-grid">
          <section class="panel span-7">
            <div class="profile-card">
              <span class="profile-avatar">${currentUser.shortName}</span>
              <div>
                <h3 class="profile-name">${escapeHtml(currentUser.name)}</h3>
                <p class="profile-meta">${escapeHtml(currentUser.workNo)} · ${escapeHtml(currentUser.status)} · ${escapeHtml(currentUser.location)}</p>
              </div>
            </div>
          </section>
          <section class="panel span-5">
            <h3 class="panel-title">组织关系</h3>
            ${keyValueRow("主部门", currentUser.department)}
            ${keyValueRow("直属上级", currentUser.manager)}
            ${keyValueRow("手机号", currentUser.phone)}
          </section>
          <section class="panel span-6">
            <h3 class="panel-title">可访问入口</h3>
            <div class="mini-grid">
              <div class="mini-stat"><strong>消息</strong><span>默认入口</span></div>
              <div class="mini-stat"><strong>待办</strong><span>统一处理</span></div>
              <div class="mini-stat"><strong>文档</strong><span>文件中心</span></div>
              <div class="mini-stat"><strong>工作台</strong><span>发起申请</span></div>
            </div>
          </section>
          <section class="panel span-6">
            <h3 class="panel-title">账号安全</h3>
            <div class="mini-grid">
              ${item.stats
                .map(
                  ([value, label]) => `
                    <div class="mini-stat">
                      <strong>${escapeHtml(value)}</strong>
                      <span>${escapeHtml(label)}</span>
                    </div>
                  `,
                )
                .join("")}
            </div>
            ${keyValueRow("登录入口", "/api/employee/auth/login")}
            ${keyValueRow("邮箱绑定", "/api/employee/auth/email/bind")}
            ${keyValueRow("密码重置", "/api/employee/auth/password/reset")}
          </section>
          <section class="panel span-12">
            <h3 class="panel-title">${escapeHtml(item.title)}</h3>
            <p class="panel-note">${escapeHtml(item.note)}</p>
            <div class="api-list">${item.fields.map(([key, value]) => `<span class="api-pill">${escapeHtml(key)} · ${escapeHtml(value)}</span>`).join("")}</div>
          </section>
        </div>
      </div>
    </article>
  `;
}

function renderDetailPanel() {
  if (state.section === "messages") {
    const item = activeMessage();
    return `
      <div class="detail-inner">
        ${detailHead("会话详情")}
        <section class="panel">
          <h3 class="panel-title">${escapeHtml(item.title)}</h3>
          <p class="panel-note">${escapeHtml(item.subtitle)}</p>
          <div class="api-list">
            <span class="api-pill">${escapeHtml(item.label)}</span>
            <span class="api-pill">${escapeHtml(item.status)}</span>
          </div>
        </section>
        <section class="panel">
          <h3 class="panel-title">成员</h3>
          <div class="member-list">${item.participants.map(([name, role]) => memberRow(name, role)).join("")}</div>
        </section>
        <section class="panel">
          <h3 class="panel-title">相关接口</h3>
          <div class="api-list">${item.relatedApis.map((api) => `<span class="api-pill">${escapeHtml(api)}</span>`).join("")}</div>
        </section>
      </div>
    `;
  }

  if (state.section === "todos") {
    const item = activeTodo();
    return `
      <div class="detail-inner">
        ${detailHead("待办上下文")}
        <section class="panel">
          <h3 class="panel-title">${escapeHtml(item.title)}</h3>
          <p class="panel-note">${escapeHtml(item.summary)}</p>
          <div class="api-list">
            <span class="api-pill">${escapeHtml(item.requestNo)}</span>
            <span class="api-pill">${escapeHtml(item.taskId)}</span>
            <span class="api-pill">${escapeHtml(item.currentStepName)}</span>
          </div>
        </section>
        <section class="panel">
          <h3 class="panel-title">权限口径</h3>
          ${keyValueRow("查看范围", "仅申请人、审批人或参与者")}
          ${keyValueRow("动作范围", "通过 / 驳回 / 撤回")}
          ${keyValueRow("状态", item.status)}
        </section>
        <section class="panel">
          <h3 class="panel-title">相关接口</h3>
          <div class="api-list">${item.apis.map((api) => `<span class="api-pill">${escapeHtml(api)}</span>`).join("")}</div>
        </section>
      </div>
    `;
  }

  if (state.section === "files") {
    const space = activeFileSpace();
    return `
      <div class="detail-inner">
        ${detailHead("文件上下文")}
        <section class="panel">
          <h3 class="panel-title">${escapeHtml(space.title)}</h3>
          <p class="panel-note">${escapeHtml(space.note)}</p>
          <div class="quota-bar"><div class="quota-fill" style="width:${space.usage}%"></div></div>
        </section>
        <section class="panel">
          <h3 class="panel-title">文件策略</h3>
          ${keyValueRow("上传方式", "两段式：票据 + 确认上传")}
          ${keyValueRow("下载方式", "短期票据、一次性使用")}
          ${keyValueRow("归属", space.subtitle)}
        </section>
      </div>
    `;
  }

  if (state.section === "workbench") {
    const item = activeWorkbenchItem();
    return `
      <div class="detail-inner">
        ${detailHead("工作台上下文")}
        <section class="panel">
          <h3 class="panel-title">${escapeHtml(item.title)}</h3>
          <p class="panel-note">${escapeHtml(item.note)}</p>
          <div class="api-list">${item.fields.map(([key, value]) => `<span class="api-pill">${escapeHtml(key)} = ${escapeHtml(value)}</span>`).join("")}</div>
        </section>
        <section class="panel">
          <h3 class="panel-title">能力边界</h3>
          ${keyValueRow("状态", item.status)}
          ${keyValueRow("接口", item.subtitle)}
          ${keyValueRow("用途", item.label)}
        </section>
      </div>
    `;
  }

  const item = activeProfileItem();
  return `
    <div class="detail-inner">
      ${detailHead("个人上下文")}
      <section class="panel">
        <h3 class="panel-title">${escapeHtml(item.title)}</h3>
        <p class="panel-note">${escapeHtml(item.note)}</p>
        <div class="api-list">${item.stats.map(([value, label]) => `<span class="api-pill">${escapeHtml(value)} · ${escapeHtml(label)}</span>`).join("")}</div>
      </section>
      <section class="panel">
        <h3 class="panel-title">账号字段</h3>
        ${item.fields.map(([key, value]) => keyValueRow(key, value)).join("")}
      </section>
    </div>
  `;
}

function detailHead(title) {
  return `
    <div class="detail-head">
      <h2 class="detail-title">${escapeHtml(title)}</h2>
      <button class="icon-button" data-action="close-detail" aria-label="关闭详情">×</button>
    </div>
  `;
}

function memberRow(name, role) {
  return `
    <div class="member">
      <span class="avatar ${name === "HR 助手" ? "blue" : name === "系统" ? "amber" : "slate"}">${escapeHtml(name.slice(0, 2))}</span>
      <span>
        <strong>${escapeHtml(name)}</strong>
        <span>${escapeHtml(role)}</span>
      </span>
    </div>
  `;
}

function keyValueRow(key, value) {
  return `
    <div class="key-value">
      <span>${escapeHtml(key)}</span>
      <strong>${escapeHtml(value)}</strong>
    </div>
  `;
}

function priorityTone(priority) {
  if (priority === "高") return "rust";
  if (priority === "中") return "amber";
  return "green";
}

function showToast(message) {
  const toast = document.querySelector(".toast");
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add("show");
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 2200);
}

function sendMessage() {
  const input = document.querySelector("#messageInput");
  const text = input ? input.value.trim() : "";
  if (!text) {
    showToast("先输入一条消息。");
    return;
  }

  activeMessage().messages.push({
    kind: "text",
    author: currentUser.name,
    initials: currentUser.shortName,
    own: true,
    text,
  });
  render();
  showToast("消息已加入当前原型会话。");
}

function setSection(section) {
  state.section = section;
  state.mobileView = "list";
  if (section === "messages" && isMobileLayout()) {
    state.detailOpen = false;
  }
  render();
}

function setActive(section, id) {
  state.active[section] = id;
  if (isMobileLayout()) {
    state.mobileView = "detail";
    state.detailOpen = false;
  }
  render();
}

function applySectionAction(actionButton) {
  const action = actionButton.dataset.action;
  if (action === "switch-section") {
    setSection(actionButton.dataset.section);
    return;
  }
  if (action === "switch-item") {
    const section = actionButton.dataset.section;
    const item = actionButton.dataset.item;
    if (section && item) {
      state.section = section;
      state.active[section] = item;
      state.mobileView = "list";
      render();
    }
    return;
  }
  if (action === "open-workbench") {
    state.section = "workbench";
    state.active.workbench = actionButton.dataset.app || "leave";
    state.mobileView = "list";
    render();
    return;
  }
  if (action === "toast") {
    showToast(actionButton.dataset.message || "已触发原型动作。");
  }
}

function restoreSearchFocus() {
  const pending = state.pendingSearchFocus;
  if (!pending) return;
  state.pendingSearchFocus = null;
  const input = app.querySelector(`[data-search="${pending.section}"]`);
  if (!input) return;
  input.focus();
  if (typeof pending.cursor === "number" && input.setSelectionRange) {
    input.setSelectionRange(pending.cursor, pending.cursor);
  }
}

app.addEventListener("click", (event) => {
  const navButton = event.target.closest("[data-nav]");
  if (navButton) {
    setSection(navButton.dataset.nav);
    return;
  }

  const selectButton = event.target.closest("[data-select]");
  if (selectButton) {
    setActive(state.section, selectButton.dataset.select);
    return;
  }

  const appButton = event.target.closest("[data-app]");
  if (appButton) {
    const appId = appButton.dataset.app;
    if (state.section === "workbench") {
      state.active.workbench = appId;
      render();
      return;
    }
    if (state.section === "messages") {
      state.section = "workbench";
      state.active.workbench = appId;
      render();
      return;
    }
  }

  const actionButton = event.target.closest("[data-action]");
  if (!actionButton) return;
  const action = actionButton.dataset.action;
  if (action === "toggle-detail") {
    state.detailOpen = !state.detailOpen;
    render();
    return;
  }
  if (action === "back-list") {
    state.mobileView = "list";
    state.detailOpen = false;
    render();
    return;
  }
  if (action === "close-detail") {
    state.detailOpen = false;
    render();
    return;
  }
  if (action === "send-message") {
    sendMessage();
    return;
  }
  applySectionAction(actionButton);
});

app.addEventListener("input", (event) => {
  const input = event.target.closest("[data-search]");
  if (!input) return;
  const section = input.dataset.search;
  state.search[section] = input.value;
  state.pendingSearchFocus = {
    section,
    cursor: input.selectionStart ?? input.value.length,
  };
  render();
});

app.addEventListener("pointerdown", (event) => {
  const handle = event.target.closest("[data-action='resize-list']");
  if (!handle || isMobileLayout()) return;
  event.preventDefault();

  const shell = handle.closest(".app-shell");
  const rail = shell.querySelector(".rail").getBoundingClientRect();
  const minWidth = 260;
  const maxWidth = clamp(window.innerWidth - rail.right - 280, 300, 480);

  function resize(pointerEvent) {
    const nextWidth = clamp(pointerEvent.clientX - rail.right, minWidth, maxWidth);
    state.listWidth = Math.round(nextWidth);
    shell.style.setProperty("--list-width", `${state.listWidth}px`);
  }

  function finish() {
    localStorage.setItem("employeePrototypeListWidth", String(state.listWidth));
    window.removeEventListener("pointermove", resize);
    window.removeEventListener("pointerup", finish);
    window.removeEventListener("pointercancel", finish);
  }

  window.addEventListener("pointermove", resize);
  window.addEventListener("pointerup", finish);
  window.addEventListener("pointercancel", finish);
});

window.addEventListener("resize", () => {
  if (!isMobileLayout() && state.mobileView === "detail") {
    state.mobileView = "list";
    render();
  }
});

app.addEventListener("keydown", (event) => {
  if (event.target.matches("#messageInput") && event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    sendMessage();
  }

  if (event.target.matches(".resize-handle") && !isMobileLayout()) {
    const delta = event.key === "ArrowLeft" ? -16 : event.key === "ArrowRight" ? 16 : 0;
    if (!delta) return;
    event.preventDefault();
    state.listWidth = clamp(state.listWidth + delta, 260, 480);
    localStorage.setItem("employeePrototypeListWidth", String(state.listWidth));
    render();
  }
});

render();
