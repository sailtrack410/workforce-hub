import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import {
  navItems,
  normalizeWorkbenchApp,
  normalizeSettingsPanel,
  resolveWorkbenchRoute,
  sectionMeta,
  todoLifecycle,
  todoSectionFilters,
  useShellStore,
  type ConversationMessage,
  type ConversationRecord,
  type TodoRecord,
} from './shell';

const storage = new Map<string, string>();
Object.defineProperty(globalThis, 'localStorage', {
  value: {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
    clear: () => storage.clear(),
  },
  configurable: true,
});

const shellDemoConversations: ConversationRecord[] = [
  {
    id: 'leave-approval',
    kind: 'approval',
    tone: 'blue',
    badge: '王晨',
    title: '王晨年假申请',
    subtitle: '请假 2 天，等待审批确认',
    meta: '10:24',
    tag: '审批',
    unread: 2,
    flow: '私聊',
    status: 'PENDING',
    unreadLabel: '未读消息',
    summaryTitle: '王晨年假申请',
    summaryBody: '请假 2 天，等待审批确认',
    cards: [],
    workflowSteps: [
      { label: '提交申请', status: 'done', detail: '已提交' },
      { label: '余额校验', status: 'done', detail: '已通过' },
      { label: '直属上级审批', status: 'current', detail: '待处理' },
      { label: '完成', status: 'pending', detail: '待完成' },
    ],
    messages: [
      {
        id: 'm-1',
        type: 'process-card',
        author: 'HR 助手',
        role: 'Agent',
        body: '王晨年假申请待确认',
        processCard: {
          title: '王晨年假申请待确认',
          status: '待处理',
          statusTone: 'warning',
          body: '待处理',
          actionLabel: '查看',
        },
      },
      {
        id: 'm-2',
        type: 'process-card',
        author: 'HR 助手',
        role: 'Agent',
        body: 'Agent 预审已通过',
        processCard: {
          title: 'Agent 预审已通过',
          status: '已完成',
          statusTone: 'success',
          body: '已完成',
          actionLabel: '查看',
        },
      },
      {
        id: 'm-3',
        type: 'process-card',
        author: 'HR 助手',
        role: 'Agent',
        body: '文件已确认',
        processCard: {
          title: '文件已确认',
          status: '已完成',
          statusTone: 'success',
          body: '已完成',
          actionLabel: '查看',
        },
      },
    ],
    panel: {
      members: [{ name: 'HR 助手', role: 'Agent' }],
      tasks: [],
      attachments: [],
    },
  },
  {
    id: 'operation-team',
    kind: 'team',
    tone: 'green',
    badge: '运营',
    title: '运营一部群',
    subtitle: '五月排班已同步',
    memberCount: 12,
    meta: '09:42',
    tag: '群聊',
    unread: 0,
    flow: '群聊',
    status: 'ACTIVE',
    unreadLabel: '已读',
    summaryTitle: '运营一部群',
    summaryBody: '五月排班已同步',
    cards: [],
    workflowSteps: [],
    messages: [],
    panel: {
      members: [{ name: '周悦', role: '成员' }],
      tasks: [],
      attachments: [],
    },
  },
  {
    id: 'hr-agent',
    kind: 'agent',
    tone: 'green',
    badge: 'HR',
    title: 'HR 助手',
    subtitle: '假期余额',
    meta: '09:20',
    tag: 'Agent',
    unread: 0,
    flow: 'Agent 会话',
    status: 'ACTIVE',
    unreadLabel: '已读',
    summaryTitle: 'HR 助手',
    summaryBody: '假期余额',
    cards: [],
    workflowSteps: [],
    messages: [],
    panel: {
      members: [{ name: 'HR 助手', role: 'Agent' }],
      tasks: [],
      attachments: [],
    },
  },
  {
    id: 'file-center',
    kind: 'file',
    tone: 'gray',
    badge: '文档',
    title: '文件助手',
    subtitle: '材料已确认',
    meta: '08:40',
    tag: '文件',
    unread: 0,
    flow: '私聊',
    status: 'ACTIVE',
    unreadLabel: '已读',
    summaryTitle: '文件助手',
    summaryBody: '材料已确认',
    cards: [],
    workflowSteps: [],
    messages: [],
    panel: {
      members: [{ name: 'HR 助手', role: 'Agent' }],
      tasks: [],
      attachments: [{ id: 'f-1', name: '材料.pdf', type: 'PDF', size: '1KB', status: '已确认' }],
    },
  },
];

const shellDemoTodos: TodoRecord[] = [
  {
    id: 'todo-approval',
    title: '王晨年假申请',
    subtitle: '流程待办',
    note: '待处理',
    time: '10:24',
    status: 'PENDING',
    priority: '高',
    requestNo: 'REQ-1',
    taskId: 'TASK-1',
    currentStepName: '直属上级审批',
    summary: '待处理',
    fields: [],
    timeline: [],
    actions: [],
  },
  {
    id: 'todo-attendance',
    title: '考勤异常确认',
    subtitle: '流程待办',
    note: '待处理',
    time: '09:00',
    status: 'PENDING',
    priority: '中',
    requestNo: 'ATT-1',
    taskId: 'TASK-2',
    currentStepName: '异常确认',
    summary: '考勤异常',
    fields: [],
    timeline: [],
    actions: [],
  },
];

describe('useShellStore', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-06T12:00:00'));
    localStorage.clear();
    setActivePinia(createPinia());
  });

  it('starts without simulated conversations or todos', () => {
    const shell = useShellStore();

    expect(shell.conversations).toEqual([]);
    expect(shell.todos).toEqual([]);
    expect(shell.activeConversation.id).toBe('');
    expect(shell.activeTodo.id).toBe('');
  });

  it('keeps oa workbench route instead of falling back to leave', () => {
    expect(normalizeWorkbenchApp('oa')).toBe('oa');
  });

  it('maps legacy leave and requests workbench routes into oa', () => {
    expect(normalizeWorkbenchApp('leave')).toBe('oa');
    expect(normalizeWorkbenchApp('requests')).toBe('oa');
    expect(normalizeWorkbenchApp('agent')).toBe('oa');
  });

  it('labels the workbench section consistently', () => {
    const workbenchNav = navItems.find((item) => item.id === 'workbench');

    expect(workbenchNav?.label).toBe('工作台');
    expect(sectionMeta.workbench.title).toBe('工作台');
  });

  it('labels the files section as cloud space', () => {
    const shell = useShellStore();
    const filesNav = navItems.find((item) => item.id === 'files');

    expect(filesNav?.label).toBe('云空间');
    expect(sectionMeta.files.title).toBe('云空间');
    expect(shell.activeFile.title).toBe('云空间');
  });

  it('filters message conversations by title and member keyword', () => {
    const shell = useShellStore();
    shell.mergeBackendConversations(shellDemoConversations);

    shell.setSearch('messages', 'HR 助手');

    expect(shell.filteredConversations.map((item) => item.id)).toEqual(['leave-approval', 'hr-agent', 'file-center']);
  });

  it('filters message conversations by chip', () => {
    const shell = useShellStore();
    shell.mergeBackendConversations(shellDemoConversations);

    shell.setFilter('messages', '群聊');

    expect(shell.filteredConversations.map((item) => item.id)).toEqual(['operation-team']);
  });

  it('shows pinned conversations first', () => {
    const shell = useShellStore();
    shell.mergeBackendConversations([
      shellDemoConversations[0],
      { ...shellDemoConversations[1], pinned: true },
      shellDemoConversations[2],
    ]);

    expect(shell.filteredConversations.map((item) => item.id)).toEqual(['operation-team', 'leave-approval', 'hr-agent']);
  });

  it('keeps approval progress data with one current step', () => {
    const shell = useShellStore();
    shell.mergeBackendConversations(shellDemoConversations);

    const steps = shell.activeConversation.workflowSteps;

    expect(steps).toHaveLength(4);
    expect(steps.filter((step) => step.status === 'current').map((step) => step.label)).toEqual(['直属上级审批']);
  });

  it('keeps approval workflow feedback as agent process messages', () => {
    const shell = useShellStore();
    shell.mergeBackendConversations(shellDemoConversations);

    const cards = shell.activeConversation.messages.filter((message) => message.type === 'process-card');

    expect(cards).toHaveLength(3);
    expect(cards.map((message) => message.processCard?.title)).toEqual([
      '王晨年假申请待确认',
      'Agent 预审已通过',
      '文件已确认',
    ]);
  });

  it('keeps user-facing conversation detail free from API route strings', () => {
    const shell = useShellStore();
    shell.mergeBackendConversations(shellDemoConversations);
    const panelText = JSON.stringify(shell.activeConversation.panel);

    expect(panelText).not.toContain('/api/');
    expect(panelText).not.toContain('GET ');
    expect(panelText).not.toContain('POST ');
  });

  it('keeps group member count for compact conversation titles', () => {
    const shell = useShellStore();
    shell.mergeBackendConversations(shellDemoConversations);

    const group = shell.conversations.find((item) => item.id === 'operation-team');

    expect(group?.memberCount).toBe(12);
  });

  it('uses personal todo status filters instead of workflow pending filters', () => {
    expect(todoSectionFilters).toEqual(['全部', '进行中', '已完成', '未开始']);
  });

  it('combines personal todo status filter and keyword search', () => {
    const shell = useShellStore();
    const activeTodo = shell.addTodoTask({
      title: '准备周会材料',
      note: '整理本周风险项',
      startAt: '2026-07-06T09:30',
      endAt: '2026-07-06T13:30',
      tagName: '工作',
      tagColor: 'blue',
    });
    const upcomingTodo = shell.addTodoTask({
      title: '明日客户回访',
      note: '确认合同时间',
      startAt: '2026-07-07T14:00',
      endAt: '2026-07-07T14:30',
      tagName: '协作',
      tagColor: 'teal',
    });
    const completedTodo = shell.addTodoTask({
      title: '归档会议纪要',
      note: '同步到云空间',
      startAt: '2026-07-06T10:00',
      endAt: '2026-07-06T10:30',
      tagName: '工作',
      tagColor: 'green',
    });
    shell.toggleTodoTask(completedTodo.id);

    expect(todoLifecycle(activeTodo)).toBe('进行中');
    expect(todoLifecycle(upcomingTodo)).toBe('未开始');
    expect(todoLifecycle(shell.todos.find((item) => item.id === completedTodo.id)!)).toBe('已完成');

    shell.setSearch('todos', '客户');
    shell.setFilter('todos', '未开始');

    expect(shell.filteredTodos.map((item) => item.id)).toEqual([upcomingTodo.id]);
  });

  it('creates personal todos with minute time, note and colored tag', () => {
    const shell = useShellStore();

    const todo = shell.addTodoTask({
      title: '准备周会材料',
      note: '整理本周风险项',
      startAt: '2026-07-06T09:30',
      endAt: '2026-07-07T10:45',
      tagName: '工作',
      tagColor: 'blue',
    });

    expect(todo).toMatchObject({
      title: '准备周会材料',
      note: '整理本周风险项',
      startDate: '2026-07-06',
      endDate: '2026-07-07',
      startAt: '2026-07-06T09:30',
      endAt: '2026-07-07T10:45',
      tagName: '工作',
      tagColor: 'blue',
      status: 'OPEN',
    });
    expect(todo.subtitle).toBe('2026-07-06 09:30 至 2026-07-07 10:45');
    expect(shell.filteredTodos.map((item) => item.id)).toEqual([todo.id]);
  });

  it('persists personal todo completion state', () => {
    const shell = useShellStore();
    const todo = shell.addTodoTask({
      title: '回访客户',
      note: '确认合同时间',
      startAt: '2026-07-06T14:00',
      endAt: '2026-07-06T14:30',
      tagName: '协作',
      tagColor: 'teal',
    });

    shell.toggleTodoTask(todo.id);
    setActivePinia(createPinia());
    const restored = useShellStore();

    expect(restored.todos[0]).toMatchObject({
      id: todo.id,
      completed: true,
      status: 'DONE',
      note: '确认合同时间',
      startAt: '2026-07-06T14:00',
      endAt: '2026-07-06T14:30',
    });
    expect(restored.pendingTodoCount).toBe(0);
  });

  it('uses loaded backend todos as the source of truth', () => {
    const shell = useShellStore();
    const backendTodo: TodoRecord = {
      ...shellDemoTodos[0],
      id: 'workflow-todo-10',
      title: '后端待办',
      status: 'PENDING',
      taskId: 'TASK-10',
    };

    shell.mergeBackendTodos([backendTodo]);

    expect(shell.todos[0]).toMatchObject({
      id: 'workflow-todo-10',
      title: '后端待办',
      taskId: 'TASK-10',
    });
    expect(shell.todos.some((item) => item.id === 'todo-approval')).toBe(false);
    expect(shell.active.todos).toBe('workflow-todo-10');
  });

  it('uses loaded backend conversations as the source of truth', () => {
    const shell = useShellStore();
    const backendConversation: ConversationRecord = {
      ...shellDemoConversations[0],
      id: '7001',
      title: '张三与李四',
      subtitle: '下午同步一下排班。',
      status: 'ACTIVE',
      flow: '私聊',
    };

    shell.mergeBackendConversations([backendConversation]);

    expect(shell.conversations[0]).toMatchObject({
      id: '7001',
      title: '张三与李四',
      flow: '私聊',
    });
    expect(shell.conversations.some((item) => item.id === 'leave-approval')).toBe(false);
    expect(shell.active.messages).toBe('7001');
  });

  it('builds contacts from organization people without mixing group member roles', () => {
    const shell = useShellStore();
    shell.mergeBackendConversations([{
      ...shellDemoConversations[1],
      panel: {
        members: [
          { name: '群主张', role: '群主', userId: 9001 },
          { name: '群成员李', role: '成员', userId: 9002 },
        ],
        tasks: [],
        attachments: [],
      },
    }]);
    shell.setColleagues([
      {
        userId: 3001,
        employeeNo: 'E3001',
        realName: '同部门王',
        primaryDeptId: 10,
        primaryDeptName: '研发部',
        positionId: 20,
        positionName: '前端工程师',
        managerUserId: 1001,
        managerName: '主管赵',
        employmentStatus: 'ACTIVE',
        status: 'ACTIVE',
      },
    ]);

    shell.syncSessionProfile(
      {
        userId: 1000,
        userType: 'EMPLOYEE',
        displayName: '当前员工',
        loginName: 'demo.employee2',
        email: null,
      },
      {
        userId: 1000,
        employeeNo: 'E1000',
        realName: '当前员工',
        primaryDeptId: 10,
        primaryDeptName: '研发部',
        positionId: 21,
        positionName: '产品经理',
        managerUserId: 1001,
        managerName: '主管赵',
        employmentStatus: 'ACTIVE',
        status: 'ACTIVE',
      },
      {
        userId: 1001,
        employeeNo: 'E1001',
        realName: '主管赵',
        primaryDeptId: 10,
        primaryDeptName: '研发部',
        positionId: 22,
        positionName: '部门主管',
        managerUserId: null,
        managerName: null,
        employmentStatus: 'ACTIVE',
        status: 'ACTIVE',
      },
      [],
    );

    expect(shell.contacts.map((item) => item.name)).toEqual(['当前员工', '主管赵', '同部门王']);
    expect(shell.contacts.map((item) => item.relation)).toEqual(['本人', '直属上级', '同部门']);
    expect(shell.contacts.some((item) => ['群主', '成员'].includes(item.role))).toBe(false);
  });

  it('persists contact remarks and uses them in contacts, conversations and messages', () => {
    const shell = useShellStore();
    shell.setColleagues([
      {
        userId: 3001,
        employeeNo: 'E3001',
        realName: '同部门王',
        primaryDeptId: 10,
        primaryDeptName: '研发部',
        positionId: 20,
        positionName: '前端工程师',
        managerUserId: 1001,
        managerName: '主管赵',
        employmentStatus: 'ACTIVE',
        status: 'ACTIVE',
      },
    ]);
    shell.syncSessionProfile(null, null, null, []);
    shell.setContactRemark(3001, '前端联系人');
    shell.mergeBackendConversations([{
      ...shellDemoConversations[0],
      id: 'private-3001',
      kind: 'private',
      title: '同部门王',
      panel: {
        members: [{ name: '同部门王', role: '成员', userId: 3001 }],
        tasks: [],
        attachments: [],
      },
      messages: [{
        id: 'm-3001',
        author: '用户 3001',
        role: '成员',
        body: '收到',
        side: 'left',
        senderType: 'USER',
        senderId: 3001,
      }],
    }, {
      ...shellDemoConversations[1],
      id: 'group-3001',
      kind: 'team',
      title: '项目群',
      panel: {
        members: [{ name: '同部门王', originalName: '同部门王', role: '成员', userId: 3001 }],
        tasks: [],
        attachments: [],
      },
      messages: [{
        id: 'g-3001',
        author: '用户 3001',
        role: '成员',
        body: '群内收到',
        side: 'left',
        senderType: 'USER',
        senderId: 3001,
      }],
    }]);

    expect(shell.contacts.find((item) => item.userId === 3001)?.name).toBe('前端联系人');
    expect(shell.conversations[0].title).toBe('前端联系人');
    expect(shell.conversations[1].messages[0].author).toBe('前端联系人 @ 同部门王');
    expect(shell.displayNameForUser(3001, '同部门王')).toBe('前端联系人');

    setActivePinia(createPinia());
    const restored = useShellStore();
    expect(restored.contactRemarkFor(3001)).toBe('前端联系人');
  });

  it('merges loaded backend messages into the active conversation', () => {
    const shell = useShellStore();
    const backendConversation: ConversationRecord = {
      ...shellDemoConversations[0],
      id: '7001',
      title: '张三与李四',
      messages: [],
    };
    const backendMessages: ConversationMessage[] = [
      {
        id: '7101',
        author: '用户 2003',
        role: '成员',
        body: '我上午可以处理补卡。',
        side: 'left',
        attachments: [
          {
            id: 'im-file-1',
            name: '群聊资料.pdf',
            type: 'PDF',
            size: '12KB',
            status: '已确认',
            attachmentRefId: 8801,
            mimeType: 'application/pdf',
          },
        ],
      },
    ];

    shell.mergeBackendConversations([backendConversation]);
    shell.mergeBackendMessages('7001', backendMessages);

    expect(shell.activeConversation.messages).toEqual([{ ...backendMessages[0], mentions: [] }]);
    expect(shell.activeConversation.subtitle).toBe('我上午可以处理补卡。');
    expect(shell.activeConversation.panel.attachments).toEqual(backendMessages[0].attachments);
  });

  it('returns empty filtered results without changing active item', () => {
    const shell = useShellStore();

    shell.setSearch('files', '不存在的文件');

    expect(shell.filteredFiles).toEqual([]);
    expect(shell.activeFile.id).toBe('personal');
  });

  it('persists display preferences across store instances', () => {
    const shell = useShellStore();
    shell.setTheme('dark');
    shell.setMessageDensity('compact');

    setActivePinia(createPinia());
    const restored = useShellStore();

    expect(restored.theme).toBe('dark');
    expect(restored.messageDensity).toBe('compact');
  });

  it('exposes profile center panels from the profile overview', () => {
    const shell = useShellStore();

    expect(shell.profile.map((item) => item.id)).toEqual(['profile', 'organization', 'security', 'preferences']);
    expect(shell.activeProfile.title).toBe('个人资料');
    expect(normalizeSettingsPanel('center')).toBe('profile');
    expect(normalizeSettingsPanel('profile')).toBe('profile');
    expect(normalizeSettingsPanel('storage')).toBe('profile');
  });

  it('finds profile center entries by real field and action keywords', () => {
    const shell = useShellStore();

    shell.setSearch('settings', '入职日期');
    expect(shell.filteredProfile.map((item) => item.id)).toContain('profile');

    shell.setSearch('settings', '修改密码');
    expect(shell.filteredProfile.map((item) => item.id)).toContain('security');

    shell.setSearch('settings', '直属上级');
    expect(shell.filteredProfile.map((item) => item.id)).toContain('organization');

    shell.setSearch('settings', '在线状态');
    expect(shell.filteredProfile.map((item) => item.id)).toContain('profile');
  });

  it('persists personalization preferences across store instances', () => {
    const shell = useShellStore();
    shell.setSignature('本周优先处理排班');
    shell.setPresenceStatus('BUSY');
    shell.setNotificationPreference('notifMentionOnly', true);
    shell.setAccentColor('teal');
    shell.setFontScale('large');
    shell.setBubbleStyle('sharp');

    setActivePinia(createPinia());
    const restored = useShellStore();

    expect(restored.signature).toBe('本周优先处理排班');
    expect(restored.presenceStatus).toBe('BUSY');
    expect(restored.notifMentionOnly).toBe(true);
    expect(restored.accentColor).toBe('teal');
    expect(restored.fontScale).toBe('large');
    expect(restored.bubbleStyle).toBe('sharp');
  });

  it('keeps uploaded personal file metadata for backend file actions', () => {
    const shell = useShellStore();

    shell.addPersonalFile({
      name: '考勤证明.pdf',
      type: 'PDF',
      size: '10KB',
      status: '已确认',
      attachmentRefId: 42,
      mimeType: 'application/pdf',
    });

    expect(shell.activeFile.files[0]).toMatchObject({
      id: 'personal-file-42',
      name: '考勤证明.pdf',
      attachmentRefId: 42,
      mimeType: 'application/pdf',
    });
    expect(shell.filteredFiles.map((item) => item.id)).toEqual(['personal']);
  });

  it('merges backend personal file list into the file center', () => {
    const shell = useShellStore();

    shell.mergeBackendPersonalFiles([
      {
        attachmentRefId: '2053928981082898444',
        fileObjectId: '2053928981082898555',
        ownerType: 'PERSONAL_FILE',
        ownerId: null,
        originalFileName: '入职材料.pdf',
        displayName: '入职材料.pdf',
        uploaderType: 'USER',
        uploaderId: '2003',
        mimeType: 'application/pdf',
        fileSize: 2048,
        scanStatus: 'PENDING',
        createdAt: '2026-05-12T09:30:00',
        updatedAt: '2026-05-12T09:30:00',
      },
    ], [
      {
        attachmentRefId: '2053928981082898666',
        fileObjectId: '2053928981082898777',
        ownerType: 'PERSONAL_FILE',
        ownerId: null,
        originalFileName: '旧证明.txt',
        displayName: '旧证明.txt',
        uploaderType: 'USER',
        uploaderId: '2003',
        mimeType: 'text/plain',
        fileSize: 12,
        scanStatus: 'PENDING',
        deletedAt: '2026-05-12T10:00:00',
        purgeAfter: '2026-06-11T10:00:00',
        createdAt: '2026-05-12T09:00:00',
        updatedAt: '2026-05-12T10:00:00',
      },
    ]);

    expect(shell.activeFile.files).toHaveLength(2);
    expect(shell.activeFile.files[0]).toMatchObject({
      id: 'personal-file-2053928981082898444',
      name: '入职材料.pdf',
      type: 'PDF',
      size: '2.0KB',
      status: '待处理',
      attachmentRefId: '2053928981082898444',
      mimeType: 'application/pdf',
    });
    expect(shell.activeFile.files[1]).toMatchObject({
      id: 'personal-file-2053928981082898666',
      name: '旧证明.txt',
      status: '回收站',
    });
  });

  it('rebuildWorkbench appends loaded plugins after built-in items in manifest order', () => {
    const shell = useShellStore();
    shell.rebuildWorkbench(
      [
        { id: 'p-b', name: 'Beta' },
        { id: 'p-a', name: 'Alpha' },
      ],
      ['p-a', 'p-b'],
    );

    expect(shell.workbench.map((item) => item.id)).toEqual(['today', 'oa', 'attendance', 'balance', 'p-b', 'p-a']);
    expect(shell.workbench[4]).toMatchObject({ id: 'p-b', title: 'Beta', tone: 'gray' });
    expect(shell.workbench[5]).toMatchObject({ id: 'p-a', title: 'Alpha', tone: 'gray' });
  });

  it('rebuildWorkbench skips plugins that failed to load', () => {
    const shell = useShellStore();
    shell.rebuildWorkbench(
      [
        { id: 'p-ok', name: 'OK' },
        { id: 'p-fail', name: 'Fail' },
      ],
      ['p-ok'],
    );

    expect(shell.workbench.map((item) => item.id)).toEqual(['today', 'oa', 'attendance', 'balance', 'p-ok']);
  });

  it('rebuildWorkbench rejects plugins with reserved or legacy alias IDs', () => {
    const shell = useShellStore();
    shell.rebuildWorkbench(
      [
        { id: 'oa', name: 'Fake OA' },
        { id: 'attendance', name: 'Fake Attendance' },
        { id: 'balance', name: 'Fake Balance' },
        { id: 'agent', name: 'Fake Agent' },
        { id: 'files', name: 'Fake Files' },
        { id: 'leave', name: 'Fake Leave' },
        { id: 'requests', name: 'Fake Requests' },
        { id: 'p-ok', name: 'OK' },
      ],
      ['oa', 'attendance', 'balance', 'agent', 'files', 'leave', 'requests', 'p-ok'],
    );

    const ids = shell.workbench.map((item) => item.id);
    expect(ids).toEqual(['today', 'oa', 'attendance', 'balance', 'p-ok']);
    expect(shell.workbench.find((item) => item.id === 'oa')?.title).toBe('OA 申请');
  });

  it('rebuildWorkbench deduplicates repeated plugin IDs', () => {
    const shell = useShellStore();
    shell.rebuildWorkbench(
      [
        { id: 'p-x', name: 'X1' },
        { id: 'p-x', name: 'X2' },
      ],
      ['p-x'],
    );

    expect(shell.workbench.filter((item) => item.id === 'p-x')).toHaveLength(1);
  });

  it('normalizeWorkbenchApp resolves dynamic plugin IDs', () => {
    const shell = useShellStore();
    shell.rebuildWorkbench(
      [{ id: 'my-plugin', name: 'My Plugin' }],
      ['my-plugin'],
    );

    expect(normalizeWorkbenchApp('my-plugin', shell.workbench)).toBe('my-plugin');
    expect(normalizeWorkbenchApp('unknown', shell.workbench)).toBe('today');
  });

  it('normalizeWorkbenchApp still maps legacy aliases to oa', () => {
    const shell = useShellStore();
    shell.rebuildWorkbench([], []);

    expect(normalizeWorkbenchApp('agent', shell.workbench)).toBe('oa');
    expect(normalizeWorkbenchApp('files', shell.workbench)).toBe('oa');
    expect(normalizeWorkbenchApp('leave', shell.workbench)).toBe('oa');
    expect(normalizeWorkbenchApp('requests', shell.workbench)).toBe('oa');
  });

  it('selectItem normalizes workbench using dynamic list', () => {
    const shell = useShellStore();
    shell.rebuildWorkbench(
      [{ id: 'my-plugin', name: 'My Plugin' }],
      ['my-plugin'],
    );

    shell.selectItem('workbench', 'my-plugin');
    expect(shell.active.workbench).toBe('my-plugin');

    shell.selectItem('workbench', 'nonexistent');
    expect(shell.active.workbench).toBe('today');
  });

  it('rebuildWorkbench resets active workbench if current selection disappears', () => {
    const shell = useShellStore();
    shell.rebuildWorkbench(
      [{ id: 'p-a', name: 'A' }],
      ['p-a'],
    );
    shell.selectItem('workbench', 'p-a');
    expect(shell.active.workbench).toBe('p-a');

    shell.rebuildWorkbench([], []);
    expect(shell.active.workbench).toBe('today');
  });

  describe('resolveWorkbenchRoute', () => {
    it('returns today without replace when no candidate', () => {
      expect(resolveWorkbenchRoute('', true, true, true, ['today'])).toEqual({ id: 'today', replace: false, pending: false });
      expect(resolveWorkbenchRoute(undefined, true, true, true, ['today'])).toEqual({ id: 'today', replace: false, pending: false });
    });

    it('preserves candidate as pending when session not initialized', () => {
      expect(resolveWorkbenchRoute('my-plugin', false, false, false, [])).toEqual({ id: 'my-plugin', replace: false, pending: true });
    });

    it('returns today with replace when initialized but not authenticated', () => {
      expect(resolveWorkbenchRoute('my-plugin', true, false, false, [])).toEqual({ id: 'today', replace: true, pending: false });
    });

    it('returns today without replace when not authenticated and candidate is today', () => {
      expect(resolveWorkbenchRoute('today', true, false, false, [])).toEqual({ id: 'today', replace: false, pending: false });
    });

    it('maps legacy aliases to oa with replace regardless of auth state', () => {
      for (const alias of ['agent', 'files', 'leave', 'requests']) {
        expect(resolveWorkbenchRoute(alias, true, true, false, [])).toEqual({ id: 'oa', replace: true, pending: false });
      }
    });

    it('selects built-in or valid plugin in workbenchIds immediately', () => {
      expect(resolveWorkbenchRoute('oa', true, true, false, ['oa', 'attendance', 'balance']))
        .toEqual({ id: 'oa', replace: false, pending: false });
      expect(resolveWorkbenchRoute('my-plugin', true, true, true, ['oa', 'attendance', 'balance', 'my-plugin']))
        .toEqual({ id: 'my-plugin', replace: false, pending: false });
    });

    it('preserves unknown candidate as pending when plugins not resolved', () => {
      expect(resolveWorkbenchRoute('my-plugin', true, true, false, ['oa', 'attendance', 'balance']))
        .toEqual({ id: 'my-plugin', replace: false, pending: true });
    });

    it('replaces unknown ID with today after plugins resolved', () => {
      expect(resolveWorkbenchRoute('unknown', true, true, true, ['today', 'oa', 'attendance', 'balance']))
        .toEqual({ id: 'today', replace: true, pending: false });
    });

    it('replaces failed plugin ID with today after resolution', () => {
      expect(resolveWorkbenchRoute('failed-plugin', true, true, true, ['today', 'oa', 'attendance', 'balance']))
        .toEqual({ id: 'today', replace: true, pending: false });
    });
  });
});
