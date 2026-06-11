import { beforeEach, describe, expect, it } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import {
  normalizeSettingsPanel,
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

  it('combines pending filter and keyword search for todos', () => {
    const shell = useShellStore();
    shell.mergeBackendTodos(shellDemoTodos);

    shell.setSearch('todos', '考勤');
    shell.setFilter('todos', '未处理');

    expect(shell.filteredTodos.map((item) => item.id)).toEqual(['todo-attendance']);
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

    expect(shell.activeConversation.messages).toEqual(backendMessages);
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

  it('exposes personal center panels for profile, security, preferences and storage', () => {
    const shell = useShellStore();

    expect(shell.profile.map((item) => item.id)).toEqual(['profile', 'organization', 'security', 'preferences', 'storage']);
    expect(normalizeSettingsPanel('center')).toBe('profile');
    expect(normalizeSettingsPanel('storage')).toBe('storage');
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
});
