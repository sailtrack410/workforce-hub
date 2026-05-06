const navItems = [
  { id: "messages", label: "消息", glyph: "息" },
  { id: "todos", label: "待办", glyph: "办" },
  { id: "docs", label: "文档", glyph: "文" },
  { id: "workbench", label: "工作台", glyph: "台" },
  { id: "profile", label: "我的", glyph: "我" },
];

const currentUser = {
  name: "林澈",
  shortName: "林",
  title: "运营专员",
  department: "运营中心 / 运营一部",
  manager: "周悦",
  status: "在职",
  workNo: "WFH-1027",
  location: "上海",
};

const conversations = [
  {
    id: "ops",
    title: "运营一部组织群",
    subtitle: "组织群 · 18 人 · HR 助手已接入",
    note: "王晨：我把补卡说明发到群文件了",
    time: "10:24",
    unread: 3,
    kind: "team",
    label: "组织群",
    members: [
      ["周悦", "部门负责人"],
      ["王晨", "运营专员"],
      ["许知夏", "运营专员"],
      ["HR 助手", "会话问答 Agent"],
    ],
    files: ["补卡说明.pdf", "五月排班表.xlsx", "员工手册.pdf"],
    messages: [
      {
        author: "周悦",
        initials: "周",
        text: "今天优先处理五月排班确认，异常打卡先在待办里补齐。",
      },
      {
        author: "王晨",
        initials: "王",
        text: "我把补卡说明发到群文件了，大家走工作台里的补卡申请入口就行。",
      },
      {
        author: "HR 助手",
        initials: "AI",
        agent: true,
        text: "我已根据组织规则整理本周待处理事项：3 条补卡、1 条请假审批、2 个文件待确认。",
        card: {
          title: "团队协同摘要",
          desc: "数据范围来自当前组织群与个人待办，不读取未授权会话内容。",
          stats: [
            ["3", "补卡待处理"],
            ["1", "审批待办"],
            ["2", "文件待确认"],
          ],
          action: "查看待办",
        },
      },
      {
        author: "林澈",
        initials: "林",
        own: true,
        text: "收到，我先处理自己的异常打卡，再看群文件。",
      },
    ],
  },
  {
    id: "manager",
    title: "周悦",
    subtitle: "直属上级 · 运营一部",
    note: "下午把请假申请提上来，我会一起看。",
    time: "09:18",
    unread: 0,
    kind: "person",
    label: "直属上级",
    members: [
      ["周悦", "部门负责人"],
      ["林澈", "运营专员"],
    ],
    files: ["请假交接清单.docx"],
    messages: [
      {
        author: "周悦",
        initials: "周",
        text: "下午把请假申请提上来，我会一起看。交接说明记得附上。",
      },
      {
        author: "林澈",
        initials: "林",
        own: true,
        text: "好的，我会从工作台发起，附件走文件中心。",
      },
    ],
  },
  {
    id: "agent",
    title: "HR 助手",
    subtitle: "会话问答 Agent · 已授权员工手册",
    note: "年假余额还剩 5 天，建议避开团队峰值。",
    time: "昨天",
    unread: 1,
    kind: "agent",
    label: "Agent",
    members: [
      ["HR 助手", "知识问答 Agent"],
      ["林澈", "当前用户"],
    ],
    files: ["员工手册.pdf", "假期规则.md"],
    messages: [
      {
        author: "林澈",
        initials: "林",
        own: true,
        text: "我下周想请两天年假，余额够吗？",
      },
      {
        author: "HR 助手",
        initials: "AI",
        agent: true,
        text: "你的年假余额为 5 天。若申请 2 天，预计剩余 3 天。提交前我会预审日期、交接说明和直属上级审批链。",
        card: {
          title: "请假预审",
          desc: "低风险建议可直接带入草稿，高风险动作仍需人工确认。",
          stats: [
            ["5 天", "当前余额"],
            ["2 天", "计划使用"],
            ["周悦", "首个审批人"],
          ],
          action: "生成草稿",
        },
      },
    ],
  },
  {
    id: "project",
    title: "客服流程优化项目",
    subtitle: "业务群 · 8 人",
    note: "许知夏：流程图更新了，等大家确认。",
    time: "周一",
    unread: 0,
    kind: "team",
    label: "业务群",
    members: [
      ["许知夏", "项目负责人"],
      ["林澈", "项目成员"],
      ["韩序", "流程顾问"],
    ],
    files: ["流程优化草案.md", "客服排班对照.xlsx"],
    messages: [
      {
        author: "许知夏",
        initials: "许",
        text: "流程图更新了，等大家确认。文档空间里保留了上一版。",
      },
      {
        author: "韩序",
        initials: "韩",
        text: "这版把异常升级路径补齐了，审批节点可以接 Agent 预审。",
      },
    ],
  },
];

const todos = [
  {
    id: "todo-approval",
    title: "审批：王晨年假申请",
    kind: "审批待办",
    owner: "王晨",
    due: "今天 14:00",
    priority: "高",
    status: "待处理",
    note: "请假 2 天，已通过 Agent 余额预审",
    desc: "员工提交年假申请，直属上级需要确认交接安排与团队排班冲突。",
    timeline: ["王晨提交申请", "HR 助手完成余额预审", "等待周悦审批", "通过后写入假期台账"],
    fields: [
      ["申请类型", "年假"],
      ["起止时间", "2026-05-11 至 2026-05-12"],
      ["预审结果", "余额充足，未发现硬规则冲突"],
    ],
  },
  {
    id: "todo-clock",
    title: "补卡：5 月 3 日签退异常",
    kind: "个人待办",
    owner: "林澈",
    due: "今天 18:00",
    priority: "中",
    status: "待提交",
    note: "缺少签退记录，需要补充原因",
    desc: "考勤异常会进入统一待办，员工从待办详情或工作台补卡入口继续处理。",
    timeline: ["系统生成异常", "投递个人待办", "填写补卡原因", "提交直属上级审批"],
    fields: [
      ["异常日期", "2026-05-03"],
      ["异常类型", "缺签退"],
      ["建议动作", "发起补卡申请"],
    ],
  },
  {
    id: "todo-file",
    title: "文件：员工手册下载票据即将过期",
    kind: "文件提醒",
    owner: "林澈",
    due: "剩余 8 分钟",
    priority: "低",
    status: "待确认",
    note: "短期下载入口不会长期暴露对象地址",
    desc: "文件中心采用下载票据，短期有效。过期后可重新申请，不影响原文件引用。",
    timeline: ["申请下载票据", "生成短期下载入口", "票据即将过期", "必要时重新申请"],
    fields: [
      ["文件名", "员工手册.pdf"],
      ["票据状态", "ACTIVE"],
      ["安全策略", "短期入口，可审计"],
    ],
  },
  {
    id: "todo-agent",
    title: "Agent：请假草稿需要人工确认",
    kind: "Agent 任务",
    owner: "HR 助手",
    due: "明天",
    priority: "中",
    status: "待确认",
    note: "Agent 只生成草稿，不自动提交",
    desc: "高风险或正式提交动作需要人工确认，Agent 提供建议和预填内容。",
    timeline: ["用户询问假期余额", "Agent 生成请假草稿", "等待人工确认", "确认后进入申请页"],
    fields: [
      ["任务状态", "WAITING_CONFIRM"],
      ["风险等级", "低风险写入前确认"],
      ["下一步", "进入申请表单"],
    ],
  },
];

const docSpaces = [
  {
    id: "space-personal",
    title: "我的云空间",
    subtitle: "个人文件 · 3.2GB / 10GB",
    note: "已接入两段式上传、配额、回收站",
    quota: 32,
    label: "个人",
    files: [
      ["请假交接清单.docx", "DOCX", "84KB", "今天", "可下载"],
      ["员工手册.pdf", "PDF", "2.1MB", "昨天", "短期票据"],
      ["报销凭证.zip", "ZIP", "16.4MB", "周一", "已确认"],
    ],
  },
  {
    id: "space-team",
    title: "运营共享资料",
    subtitle: "团队文档 · 授权空间",
    note: "用于群文件、业务文档和知识问答来源",
    quota: 58,
    label: "团队",
    files: [
      ["五月排班表.xlsx", "XLSX", "128KB", "今天", "团队可见"],
      ["补卡说明.pdf", "PDF", "640KB", "今天", "组织群"],
      ["客服流程优化草案.md", "MD", "44KB", "周一", "业务群"],
    ],
  },
  {
    id: "space-knowledge",
    title: "知识问答空间",
    subtitle: "授权知识 · Agent 可检索",
    note: "RAG 入库状态会影响 Agent 回答",
    quota: 46,
    label: "知识",
    files: [
      ["假期规则.md", "MD", "36KB", "昨天", "READY"],
      ["员工手册.pdf", "PDF", "2.1MB", "昨天", "READY"],
      ["流程制度.docx", "DOCX", "612KB", "周一", "PARSING"],
    ],
  },
];

const workApps = [
  {
    id: "leave",
    title: "请假申请",
    label: "申请",
    desc: "余额校验、附件、审批链预览",
    fields: ["假种", "起止日期", "交接说明", "附件"],
  },
  {
    id: "clock",
    title: "补卡申请",
    label: "考勤",
    desc: "异常记录带入，直属上级审批",
    fields: ["异常日期", "补卡时间", "原因", "证明附件"],
  },
  {
    id: "attendance",
    title: "我的考勤",
    label: "统计",
    desc: "日历结果、异常、补卡入口",
    fields: ["本月出勤", "异常次数", "待补卡", "趋势"],
  },
  {
    id: "balance",
    title: "假期余额",
    label: "余额",
    desc: "年假、调休、发放扣减流水",
    fields: ["年假", "调休", "有效期", "流水"],
  },
  {
    id: "approval",
    title: "团队审批",
    label: "领导",
    desc: "领导身份增强能力，员工端内处理",
    fields: ["待审批", "已处理", "转审", "加签"],
  },
  {
    id: "agent-task",
    title: "Agent 问答",
    label: "智能",
    desc: "授权知识检索、任务结果确认",
    fields: ["选择 Agent", "问题", "引用来源", "人工确认"],
  },
];

const app = document.querySelector("#app");
const savedListWidth = Number(localStorage.getItem("employeePrototypeListWidth"));

const state = {
  section: "messages",
  detailOpen: false,
  listWidth: Number.isFinite(savedListWidth) && savedListWidth >= 260 && savedListWidth <= 460 ? savedListWidth : 330,
  mobileView: "list",
  active: {
    messages: "ops",
    todos: "todo-approval",
    docs: "space-personal",
    workbench: "leave",
    profile: "overview",
  },
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

function activeConversation() {
  return byId(conversations, state.active.messages);
}

function activeTodo() {
  return byId(todos, state.active.todos);
}

function activeDocSpace() {
  return byId(docSpaces, state.active.docs);
}

function activeWorkApp() {
  return byId(workApps, state.active.workbench);
}

function isMobileLayout() {
  return window.matchMedia("(max-width: 640px)").matches;
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
    <div class="toast" role="status"></div>
  `;
}

function renderRail() {
  const nav = navItems
    .map(
      (item) => `
        <button class="rail-button ${state.section === item.id ? "is-active" : ""}" data-nav="${item.id}" aria-label="${item.label}">
          <span class="rail-glyph">${item.glyph}</span>
          <span class="rail-label">${item.label}</span>
        </button>
      `,
    )
    .join("");

  return `
    <nav class="rail" aria-label="员工端一级功能">
      <div class="brand-mark">WH</div>
      <div class="rail-nav">${nav}</div>
      <button class="rail-user" data-nav="profile" aria-label="我的">${currentUser.shortName}</button>
    </nav>
  `;
}

function paneTitle() {
  const meta = {
    messages: ["默认入口", "消息"],
    todos: ["统一处理", "待办"],
    docs: ["文件与知识", "文档"],
    workbench: ["业务办理", "工作台"],
    profile: ["个人中心", "我的"],
  };
  return meta[state.section];
}

function renderListPane() {
  const [kicker, title] = paneTitle();
  return `
    <section class="list-pane">
      <header class="pane-head">
        <div class="pane-title-row">
          <div>
            <p class="pane-kicker">${kicker}</p>
            <h1 class="pane-title">${title}</h1>
          </div>
          <button class="new-action" data-action="quick-create" aria-label="新建">+</button>
        </div>
        <label class="search-wrap">
          <span class="sr-only">搜索</span>
          <input class="search-input" type="search" placeholder="搜索 ${title}" />
        </label>
      </header>
      <div class="list-items">${renderListItems()}</div>
    </section>
  `;
}

function renderListItems() {
  if (state.section === "messages") {
    return conversations
      .map(
        (item) => `
          <button class="list-item message-list-item ${state.active.messages === item.id ? "is-active" : ""}" data-select="${item.id}">
            <span class="avatar-wrap">
              <span class="avatar ${item.kind === "agent" ? "agent" : item.kind === "team" ? "team" : ""}">${item.title.slice(0, 2)}</span>
              ${item.unread ? `<span class="badge unread-badge" aria-label="${item.unread} 条未读">${item.unread}</span>` : ""}
            </span>
            <span class="item-main">
              <span class="item-row">
                <strong class="item-title">${escapeHtml(item.title)}</strong>
                <span class="item-time">${escapeHtml(item.time)}</span>
              </span>
              <span class="item-note">${escapeHtml(item.note)}</span>
              <span class="item-meta">${escapeHtml(item.label)}</span>
            </span>
          </button>
        `,
      )
      .join("");
  }

  if (state.section === "todos") {
    return todos
      .map(
        (item) => `
          <button class="list-item ${state.active.todos === item.id ? "is-active" : ""}" data-select="${item.id}">
            <span class="avatar">${item.kind.slice(0, 2)}</span>
            <span class="item-main">
              <span class="item-row">
                <strong class="item-title">${escapeHtml(item.title)}</strong>
                <span class="pill ${priorityTone(item.priority)}">${escapeHtml(item.priority)}</span>
              </span>
              <span class="item-note">${escapeHtml(item.note)}</span>
              <span class="item-meta">${escapeHtml(item.due)} · ${escapeHtml(item.status)}</span>
            </span>
            <span></span>
          </button>
        `,
      )
      .join("");
  }

  if (state.section === "docs") {
    return docSpaces
      .map(
        (item) => `
          <button class="list-item ${state.active.docs === item.id ? "is-active" : ""}" data-select="${item.id}">
            <span class="file-icon">${escapeHtml(item.label)}</span>
            <span class="item-main">
              <span class="item-row">
                <strong class="item-title">${escapeHtml(item.title)}</strong>
                <span class="item-time">${item.quota}%</span>
              </span>
              <span class="item-note">${escapeHtml(item.note)}</span>
              <span class="item-meta">${escapeHtml(item.subtitle)}</span>
            </span>
            <span></span>
          </button>
        `,
      )
      .join("");
  }

  if (state.section === "workbench") {
    return workApps
      .map(
        (item) => `
          <button class="list-item ${state.active.workbench === item.id ? "is-active" : ""}" data-select="${item.id}">
            <span class="app-icon">${escapeHtml(item.label)}</span>
            <span class="item-main">
              <span class="item-row">
                <strong class="item-title">${escapeHtml(item.title)}</strong>
              </span>
              <span class="item-note">${escapeHtml(item.desc)}</span>
              <span class="item-meta">${item.fields.slice(0, 2).join(" / ")}</span>
            </span>
            <span></span>
          </button>
        `,
      )
      .join("");
  }

  return `
    <button class="list-item is-active" data-select="overview">
      <span class="avatar">${currentUser.shortName}</span>
      <span class="item-main">
        <span class="item-row">
          <strong class="item-title">${currentUser.name}</strong>
          <span class="pill green">${currentUser.status}</span>
        </span>
        <span class="item-note">${currentUser.department}</span>
        <span class="item-meta">${currentUser.title} · ${currentUser.workNo}</span>
      </span>
      <span></span>
    </button>
    <button class="list-item" data-action="show-toast" data-message="邮箱绑定与密码重置已按认证文档保留入口。">
      <span class="avatar agent">安</span>
      <span class="item-main">
        <span class="item-row">
          <strong class="item-title">账号安全</strong>
        </span>
        <span class="item-note">邮箱绑定、验证码、密码重置</span>
        <span class="item-meta">SESSION 会话模式</span>
      </span>
      <span></span>
    </button>
  `;
}

function priorityTone(priority) {
  if (priority === "高") return "rust";
  if (priority === "中") return "gold";
  return "green";
}

function renderMainPane() {
  if (state.section === "messages") return renderConversation();
  if (state.section === "todos") return renderTodoDetail();
  if (state.section === "docs") return renderDocs();
  if (state.section === "workbench") return renderWorkbench();
  return renderProfile();
}

function renderHeader(title, subtitle, actions = "") {
  return `
    <header class="workspace-head">
      <div class="workspace-title-group">
        <button class="text-button mobile-back" data-action="back-list" aria-label="返回列表">&lt;</button>
        <div>
          <h2 class="workspace-title">${escapeHtml(title)}</h2>
          <p class="workspace-subtitle">${escapeHtml(subtitle)}</p>
        </div>
      </div>
      <div class="head-actions">
        ${actions}
        <button class="text-button action-icon-detail" data-action="toggle-detail" aria-label="${state.detailOpen ? "收起详情" : "查看详情"}">
          <span class="desktop-action-label">${state.detailOpen ? "收起详情" : "详情"}</span>
          <span class="mobile-action-glyph" aria-hidden="true">i</span>
        </button>
      </div>
    </header>
  `;
}

function renderConversation() {
  const item = activeConversation();
  const messages = item.messages.map(renderMessage).join("");
  return `
    <article class="workspace">
      ${renderHeader(item.title, item.subtitle, `
        <button class="icon-button" data-action="show-toast" data-message="已按会话权限规则检查可发起对象。" aria-label="发起会话">+</button>
        <button class="text-button action-icon-todos" data-nav="todos" aria-label="查看待办">
          <span class="desktop-action-label">查看待办</span>
          <span class="mobile-action-glyph" aria-hidden="true">✓</span>
        </button>
      `)}
      <div class="workspace-body">
        <div class="message-stream">${messages}</div>
      </div>
      <footer class="composer">
        <button class="text-button attach-button" data-action="show-toast" data-message="附件会先申请上传票据，再确认生成引用。" aria-label="添加附件">+</button>
        <textarea id="messageInput" class="composer-input" rows="1" placeholder="输入消息，支持 @Agent"></textarea>
        <button class="primary-button" data-action="send-message">发送</button>
      </footer>
    </article>
  `;
}

function renderMessage(message) {
  return `
    <div class="message ${message.own ? "own" : ""}">
      <span class="avatar ${message.agent ? "agent" : ""}">${escapeHtml(message.initials)}</span>
      <div class="bubble-wrap">
        <span class="message-name">${escapeHtml(message.author)}</span>
        <div class="bubble">${escapeHtml(message.text)}</div>
        ${message.card ? renderActionCard(message.card) : ""}
      </div>
    </div>
  `;
}

function renderActionCard(card) {
  return `
    <section class="action-card">
      <div class="action-card-head">
        <div>
          <h3 class="action-title">${escapeHtml(card.title)}</h3>
          <p class="action-desc">${escapeHtml(card.desc)}</p>
        </div>
        <span class="pill blue">Agent</span>
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
      <div>
        <button class="primary-button" data-nav="${card.action === "查看待办" ? "todos" : "workbench"}">${escapeHtml(card.action)}</button>
      </div>
    </section>
  `;
}

function renderTodoDetail() {
  const item = activeTodo();
  return `
    <article class="workspace">
      ${renderHeader(item.title, `${item.kind} · ${item.owner} · ${item.due}`, `
        <button class="primary-button" data-action="mark-done">${item.kind.includes("审批") ? "审批通过" : "处理完成"}</button>
        <button class="text-button" data-action="show-toast" data-message="已发起催办记录，实际接口会写入流程动作。">催办</button>
      `)}
      <div class="workspace-body todo-detail">
        <section class="panel span-12">
          <span class="pill ${priorityTone(item.priority)}">${escapeHtml(item.priority)}优先级</span>
          <h3 class="panel-title">处理说明</h3>
          <p class="panel-note">${escapeHtml(item.desc)}</p>
        </section>
        <div class="dashboard-grid">
          <section class="panel span-7">
            <h3 class="panel-title">关键字段</h3>
            ${item.fields.map(([key, value]) => `<div class="key-value"><span>${escapeHtml(key)}</span><strong>${escapeHtml(value)}</strong></div>`).join("")}
          </section>
          <section class="panel span-5">
            <h3 class="panel-title">流转轨迹</h3>
            <ul class="timeline">
              ${item.timeline.map((step) => `<li><strong>${escapeHtml(step)}</strong><span>${escapeHtml(item.status)}</span></li>`).join("")}
            </ul>
          </section>
        </div>
      </div>
    </article>
  `;
}

function renderDocs() {
  const space = activeDocSpace();
  return `
    <article class="workspace">
      ${renderHeader(space.title, space.subtitle, `
        <button class="primary-button" data-action="upload-demo">模拟上传</button>
        <button class="text-button" data-action="show-toast" data-message="下载会先申请短期票据，再走受控入口。">申请下载</button>
      `)}
      <div class="workspace-body">
        <div class="dashboard-grid">
          <section class="panel span-4">
            <h3 class="panel-title">空间用量</h3>
            <p class="panel-note">${escapeHtml(space.note)}</p>
            <div class="quota-bar"><div class="quota-fill" style="width:${space.quota}%"></div></div>
            <span class="pill green">${space.quota}% 已使用</span>
          </section>
          <section class="panel span-8">
            <h3 class="panel-title">文件列表</h3>
            <table class="file-table">
              <thead>
                <tr><th>文件</th><th>类型</th><th>大小</th><th>更新</th><th>状态</th></tr>
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
  const appItem = activeWorkApp();
  return `
    <article class="workspace">
      ${renderHeader(appItem.title, appItem.desc, `
        <button class="primary-button" data-action="submit-form">提交原型表单</button>
      `)}
      <div class="workspace-body">
        <div class="dashboard-grid">
          <section class="panel span-5">
            <h3 class="panel-title">业务入口</h3>
            <div class="app-grid">
              ${workApps
                .map(
                  (item) => `
                    <button class="app-tile ${state.active.workbench === item.id ? "is-active" : ""}" data-app="${item.id}">
                      <span class="app-icon">${escapeHtml(item.label)}</span>
                      <strong>${escapeHtml(item.title)}</strong>
                      <p>${escapeHtml(item.desc)}</p>
                    </button>
                  `,
                )
                .join("")}
            </div>
          </section>
          <section class="panel span-7">
            <h3 class="panel-title">${escapeHtml(appItem.title)}表单</h3>
            <div class="form-grid">
              ${appItem.fields
                .map(
                  (field, index) => `
                    <label class="field ${index === appItem.fields.length - 1 ? "span-2" : ""}">
                      <span>${escapeHtml(field)}</span>
                      ${index === appItem.fields.length - 1 ? `<textarea>${escapeHtml(defaultFieldValue(field))}</textarea>` : `<input value="${escapeHtml(defaultFieldValue(field))}" />`}
                    </label>
                  `,
                )
                .join("")}
            </div>
          </section>
          <section class="panel span-12">
            <h3 class="panel-title">审批链预览</h3>
            <ul class="timeline">
              <li><strong>发起人</strong><span>${currentUser.name}</span></li>
              <li><strong>直属上级</strong><span>${currentUser.manager}</span></li>
              <li><strong>部门领导层</strong><span>按规则动态解析</span></li>
              <li><strong>结果通知</strong><span>写入待办、消息和业务台账</span></li>
            </ul>
          </section>
        </div>
      </div>
    </article>
  `;
}

function defaultFieldValue(field) {
  const values = {
    假种: "年假",
    起止日期: "2026-05-11 至 2026-05-12",
    交接说明: "已同步运营一部组织群，并附交接清单。",
    附件: "请假交接清单.docx",
    异常日期: "2026-05-03",
    补卡时间: "18:32",
    原因: "外出客户现场，移动端签退失败。",
    证明附件: "客户拜访记录.pdf",
    本月出勤: "19 天",
    异常次数: "1 次",
    待补卡: "1 条",
    趋势: "本月正常率 96%",
    年假: "5 天",
    调休: "3.5 小时",
    有效期: "2026-12-31",
    流水: "本月无扣减",
    待审批: "1 条",
    已处理: "12 条",
    转审: "0 条",
    加签: "0 条",
    "选择 Agent": "HR 助手",
    问题: "下周请两天年假是否影响团队排班？",
    引用来源: "员工手册、假期规则",
    人工确认: "需要确认后生成申请草稿",
  };
  return values[field] || "";
}

function renderProfile() {
  return `
    <article class="workspace">
      ${renderHeader(currentUser.name, `${currentUser.department} · ${currentUser.title}`, `
        <button class="primary-button" data-action="show-toast" data-message="个人可编辑字段会按白名单提交。">编辑资料</button>
      `)}
      <div class="workspace-body">
        <div class="dashboard-grid">
          <section class="panel span-7">
            <div class="profile-card">
              <span class="profile-avatar">${currentUser.shortName}</span>
              <div>
                <h3 class="profile-name">${currentUser.name}</h3>
                <p class="profile-meta">${currentUser.workNo} · ${currentUser.status} · ${currentUser.location}</p>
              </div>
            </div>
          </section>
          <section class="panel span-5">
            <h3 class="panel-title">组织关系</h3>
            <div class="key-value"><span>主部门</span><strong>${currentUser.department}</strong></div>
            <div class="key-value"><span>直属上级</span><strong>${currentUser.manager}</strong></div>
            <div class="key-value"><span>直属下级</span><strong>2 人</strong></div>
          </section>
          <section class="panel span-6">
            <h3 class="panel-title">我的摘要</h3>
            <div class="mini-grid">
              <div class="mini-stat"><strong>5 天</strong><span>年假余额</span></div>
              <div class="mini-stat"><strong>1</strong><span>考勤异常</span></div>
              <div class="mini-stat"><strong>3.2GB</strong><span>云空间已用</span></div>
            </div>
          </section>
          <section class="panel span-6">
            <h3 class="panel-title">账号安全</h3>
            <div class="key-value"><span>登录方式</span><strong>账号密码</strong></div>
            <div class="key-value"><span>邮箱绑定</span><strong>lin.che@workforce.local</strong></div>
            <div class="key-value"><span>会话策略</span><strong>服务端 Session</strong></div>
          </section>
        </div>
      </div>
    </article>
  `;
}

function renderDetailPanel() {
  if (state.section === "messages") {
    const item = activeConversation();
    return `
      <div class="detail-inner">
        ${detailHead("会话详情")}
        <section class="panel">
          <h3 class="panel-title">${escapeHtml(item.title)}</h3>
          <p class="panel-note">${escapeHtml(item.subtitle)}</p>
          <span class="pill green">${escapeHtml(item.label)}</span>
        </section>
        <section class="panel">
          <h3 class="panel-title">成员</h3>
          <div class="member-list">
            ${item.members.map(([name, role]) => member(name, role)).join("")}
          </div>
        </section>
        <section class="panel">
          <h3 class="panel-title">相关文件</h3>
          ${item.files.map((file) => `<div class="key-value"><span>文件</span><strong>${escapeHtml(file)}</strong></div>`).join("")}
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
          <h3 class="panel-title">${escapeHtml(item.kind)}</h3>
          <p class="panel-note">${escapeHtml(item.desc)}</p>
          <span class="pill ${priorityTone(item.priority)}">${escapeHtml(item.status)}</span>
        </section>
        <section class="panel">
          <h3 class="panel-title">权限口径</h3>
          <div class="key-value"><span>主体</span><strong>${escapeHtml(item.owner)}</strong></div>
          <div class="key-value"><span>范围</span><strong>SELF / TEAM</strong></div>
          <div class="key-value"><span>状态约束</span><strong>${escapeHtml(item.status)}</strong></div>
        </section>
      </div>
    `;
  }

  if (state.section === "docs") {
    const space = activeDocSpace();
    return `
      <div class="detail-inner">
        ${detailHead("文档属性")}
        <section class="panel">
          <h3 class="panel-title">${escapeHtml(space.title)}</h3>
          <p class="panel-note">${escapeHtml(space.note)}</p>
          <div class="quota-bar"><div class="quota-fill" style="width:${space.quota}%"></div></div>
        </section>
        <section class="panel">
          <h3 class="panel-title">文件中心策略</h3>
          <div class="key-value"><span>上传上限</span><strong>100MB</strong></div>
          <div class="key-value"><span>去重口径</span><strong>SHA-256 + 大小</strong></div>
          <div class="key-value"><span>回收站</span><strong>30 天</strong></div>
        </section>
      </div>
    `;
  }

  if (state.section === "workbench") {
    const item = activeWorkApp();
    return `
      <div class="detail-inner">
        ${detailHead("业务上下文")}
        <section class="panel">
          <h3 class="panel-title">${escapeHtml(item.title)}</h3>
          <p class="panel-note">${escapeHtml(item.desc)}</p>
          <span class="pill blue">employee-web</span>
        </section>
        <section class="panel">
          <h3 class="panel-title">涉及字段</h3>
          ${item.fields.map((field) => `<div class="key-value"><span>字段</span><strong>${escapeHtml(field)}</strong></div>`).join("")}
        </section>
      </div>
    `;
  }

  return `
    <div class="detail-inner">
      ${detailHead("个人上下文")}
      <section class="panel">
        <h3 class="panel-title">身份摘要</h3>
        <div class="key-value"><span>用户类型</span><strong>USER</strong></div>
        <div class="key-value"><span>员工状态</span><strong>ACTIVE</strong></div>
        <div class="key-value"><span>登录准入</span><strong>允许</strong></div>
      </section>
      <section class="panel">
        <h3 class="panel-title">可访问入口</h3>
        <p class="panel-note">消息、待办、文档、工作台、我的；领导身份会增强团队审批与团队查看。</p>
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

function member(name, role) {
  return `
    <div class="member">
      <span class="avatar ${name.includes("助手") ? "agent" : ""}">${escapeHtml(name.slice(0, 2))}</span>
      <span><strong>${escapeHtml(name)}</strong><span>${escapeHtml(role)}</span></span>
    </div>
  `;
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
  activeConversation().messages.push({
    author: currentUser.name,
    initials: currentUser.shortName,
    own: true,
    text,
  });
  render();
  showToast("消息已加入当前原型会话。");
}

function handleSelect(id) {
  state.active[state.section] = id;
  if (isMobileLayout()) {
    state.mobileView = "detail";
    state.detailOpen = false;
  } else if (state.section !== "messages") {
    state.detailOpen = true;
  }
  render();
}

app.addEventListener("click", (event) => {
  const navButton = event.target.closest("[data-nav]");
  if (navButton) {
    state.section = navButton.dataset.nav;
    state.detailOpen = false;
    state.mobileView = "list";
    render();
    return;
  }

  const selectButton = event.target.closest("[data-select]");
  if (selectButton) {
    handleSelect(selectButton.dataset.select);
    return;
  }

  const appButton = event.target.closest("[data-app]");
  if (appButton) {
    state.active.workbench = appButton.dataset.app;
    render();
    return;
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
  if (action === "mark-done") {
    activeTodo().status = "已处理";
    render();
    showToast("待办状态已在原型中更新。");
    return;
  }
  if (action === "upload-demo") {
    activeDocSpace().files.unshift(["原型上传示例.pdf", "PDF", "256KB", "刚刚", "已确认"]);
    render();
    showToast("已模拟两段式上传确认。");
    return;
  }
  if (action === "submit-form") {
    render();
    showToast("表单已提交到原型流程，实际实现会调用员工端 API。");
    return;
  }
  if (action === "quick-create") {
    showToast("新建动作会根据当前一级入口打开对应表单。");
    return;
  }
  if (action === "show-toast") {
    showToast(actionButton.dataset.message || "已触发原型动作。");
  }
});

app.addEventListener("pointerdown", (event) => {
  const handle = event.target.closest("[data-action='resize-list']");
  if (!handle || isMobileLayout()) return;
  event.preventDefault();

  const shell = handle.closest(".app-shell");
  const rail = shell.querySelector(".rail").getBoundingClientRect();
  const minWidth = 260;
  const maxWidth = Math.min(460, Math.max(300, window.innerWidth - rail.width - 280));

  function resize(pointerEvent) {
    const nextWidth = Math.min(maxWidth, Math.max(minWidth, pointerEvent.clientX - rail.right));
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
    state.listWidth = Math.min(460, Math.max(260, state.listWidth + delta));
    localStorage.setItem("employeePrototypeListWidth", String(state.listWidth));
    render();
  }
});

render();
