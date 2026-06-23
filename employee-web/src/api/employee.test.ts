import { describe, expect, it } from 'vitest';
import { createEmployeeApi } from './employee';
import type { ApiClient } from './http';

function createSpyClient() {
  const calls: Array<[string, string, unknown?]> = [];
  const client: ApiClient = {
    get: <T,>(path: string, options?: unknown) => {
      calls.push(['get', path, options]);
      return Promise.resolve(null as T);
    },
    post: <T,>(path: string, body?: unknown) => {
      calls.push(['post', path, body]);
      return Promise.resolve(null as T);
    },
    put: <T,>(path: string, body?: unknown) => {
      calls.push(['put', path, body]);
      return Promise.resolve(null as T);
    },
    delete: <T,>(path: string, options?: unknown) => {
      calls.push(['delete', path, options]);
      return Promise.resolve(null as T);
    },
  };
  return { client, calls };
}

describe('createEmployeeApi', () => {
  it('maps auth endpoints to backend employee auth controller', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.auth.login({ loginName: 'lin.che', password: 'secret' });
    await api.auth.me();
    await api.auth.sendEmailCode({ email: 'lin.che@example.com', purpose: 'RESET_PASSWORD' });
    await api.auth.resetPassword({ email: 'lin.che@example.com', code: '123456', newPassword: 'Employee@654321' });
    await api.auth.logout();

    expect(calls).toEqual([
      ['post', '/api/employee/auth/login', { loginName: 'lin.che', password: 'secret' }],
      ['get', '/api/employee/auth/me', undefined],
      ['post', '/api/employee/auth/email/send-code', { email: 'lin.che@example.com', purpose: 'RESET_PASSWORD' }],
      ['post', '/api/employee/auth/password/reset', { email: 'lin.che@example.com', code: '123456', newPassword: 'Employee@654321' }],
      ['post', '/api/employee/auth/logout', undefined],
    ]);
  });

  it('maps workflow query and task actions to backend workflow controller', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.workflow.submitLeaveRequest({
      leaveTypeId: 1,
      startAt: '2026-05-01T09:00:00',
      endAt: '2026-05-01T18:00:00',
      durationMinutes: 480,
      reason: '年假',
    });
    await api.workflow.submitClockCorrectionRequest({
      targetDate: '2026-05-01',
      missingPunchType: 'CLOCK_IN',
      expectedPunchAt: '2026-05-01T09:00:00',
      reason: '补卡',
    });
    await api.workflow.todos();
    await api.workflow.requests({ pageNo: 2, pageSize: 20, status: 'PENDING' });
    await api.workflow.approveTask('TASK-1', { comment: '同意' });
    await api.workflow.rejectTask('TASK-2', { comment: '不同意' });
    await api.workflow.withdrawRequest('2053928814116044802', { comment: '撤回' });
    await api.workflow.requestDetail('2053928814116044802');
    await api.workflow.requestActions('2053928814116044802');
    await api.workflow.createAttachmentDownloadTicket('2053928814116044802', '2053928981082898444', { downloadType: 'SINGLE' });

    expect(calls).toEqual([
      ['post', '/api/employee/workflow/leave-requests', {
        leaveTypeId: 1,
        startAt: '2026-05-01T09:00:00',
        endAt: '2026-05-01T18:00:00',
        durationMinutes: 480,
        reason: '年假',
      }],
      ['post', '/api/employee/workflow/clock-correction-requests', {
        targetDate: '2026-05-01',
        missingPunchType: 'CLOCK_IN',
        expectedPunchAt: '2026-05-01T09:00:00',
        reason: '补卡',
      }],
      ['get', '/api/employee/workflow/todos', undefined],
      ['get', '/api/employee/workflow/requests', { query: { pageNo: 2, pageSize: 20, status: 'PENDING' } }],
      ['post', '/api/employee/workflow/tasks/TASK-1/approve', { comment: '同意' }],
      ['post', '/api/employee/workflow/tasks/TASK-2/reject', { comment: '不同意' }],
      ['post', '/api/employee/workflow/requests/2053928814116044802/withdraw', { comment: '撤回' }],
      ['get', '/api/employee/workflow/requests/2053928814116044802', undefined],
      ['get', '/api/employee/workflow/requests/2053928814116044802/actions', undefined],
      ['post', '/api/employee/workflow/requests/2053928814116044802/attachments/2053928981082898444/download-tickets', { downloadType: 'SINGLE' }],
    ]);
    expect(api.workflow.downloadAttachmentUrl('WF-1')).toBe('/api/employee/workflow/attachments/download/WF-1');
  });

  it('maps attendance, leave and file endpoints without mock-only paths', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.attendance.exceptions({ startDate: '2026-05-01', endDate: '2026-05-09' });
    await api.attendance.dailyResults({ startDate: '2026-05-01', endDate: '2026-05-09' });
    await api.attendance.teamDailyResults({ userId: 42, startDate: '2026-05-01', endDate: '2026-05-09' });
    await api.attendance.punch({ punchType: 'ON_DUTY', sourceType: 'PC' });
    await api.leave.accounts();
    await api.files.list({ pageNo: 1, pageSize: 20 });
    await api.files.recycleBin({ pageNo: 1, pageSize: 20 });
    await api.files.quota();
    await api.files.createUploadTicket({
      ownerType: 'PERSONAL_FILE',
      ownerId: null,
      folderId: 'folder-1',
      fileName: '个人文件.pdf',
      mimeType: 'application/pdf',
      fileSize: 1024,
      contentSha256: 'a'.repeat(64),
    });
    await api.files.confirmUpload({
      uploadTicketId: 'ticket-1',
      objectKey: 'uploads/a.pdf',
      contentSha256: 'a'.repeat(64),
      fileSize: 1024,
      ownerType: 'PERSONAL_FILE',
      ownerId: null,
      folderId: 'folder-1',
      displayName: '个人文件.pdf',
    });
    await api.files.browser({ folderId: 'folder-1', keyword: '个人', fileType: 'document' });
    await api.files.folders();
    await api.files.createFolder({ parentFolderId: 'folder-1', folderName: '报销' });
    await api.files.move('2053928981082898444', { targetFolderId: 'folder-1' });
    await api.files.copy('2053928981082898444', { targetFolderId: 'folder-1', displayName: '个人文件副本.pdf' });
    await api.files.createDownloadTicket('2053928981082898444', { downloadType: 'PERSONAL_FILE', reason: '查看材料' });
    await api.files.trash('2053928981082898444', { reason: '删除' });
    await api.files.restore('2053928981082898444', { reason: '恢复' });
    await api.files.purge('2053928981082898444');

    expect(calls).toEqual([
      ['get', '/api/employee/attendance/exceptions', { query: { startDate: '2026-05-01', endDate: '2026-05-09' } }],
      ['get', '/api/employee/attendance/daily-results', { query: { startDate: '2026-05-01', endDate: '2026-05-09' } }],
      ['get', '/api/employee/attendance/team/daily-results', { query: { userId: 42, startDate: '2026-05-01', endDate: '2026-05-09' } }],
      ['post', '/api/employee/attendance/punch', { punchType: 'ON_DUTY', sourceType: 'PC' }],
      ['get', '/api/employee/leave/accounts', undefined],
      ['get', '/api/employee/files', { query: { pageNo: 1, pageSize: 20 } }],
      ['get', '/api/employee/files/recycle-bin', { query: { pageNo: 1, pageSize: 20 } }],
      ['get', '/api/employee/files/quota', undefined],
      ['post', '/api/employee/files/upload-tickets', {
        ownerType: 'PERSONAL_FILE',
        ownerId: null,
        folderId: 'folder-1',
        fileName: '个人文件.pdf',
        mimeType: 'application/pdf',
        fileSize: 1024,
        contentSha256: 'a'.repeat(64),
      }],
      ['post', '/api/employee/files/confirm-upload', {
        uploadTicketId: 'ticket-1',
        objectKey: 'uploads/a.pdf',
        contentSha256: 'a'.repeat(64),
        fileSize: 1024,
        ownerType: 'PERSONAL_FILE',
        ownerId: null,
        folderId: 'folder-1',
        displayName: '个人文件.pdf',
      }],
      ['get', '/api/employee/files/browser', { query: { folderId: 'folder-1', keyword: '个人', fileType: 'document' } }],
      ['get', '/api/employee/files/folders', undefined],
      ['post', '/api/employee/files/folders', { parentFolderId: 'folder-1', folderName: '报销' }],
      ['post', '/api/employee/files/2053928981082898444/move', { targetFolderId: 'folder-1' }],
      ['post', '/api/employee/files/2053928981082898444/copy', { targetFolderId: 'folder-1', displayName: '个人文件副本.pdf' }],
      ['post', '/api/employee/files/2053928981082898444/download-tickets', { downloadType: 'PERSONAL_FILE', reason: '查看材料' }],
      ['post', '/api/employee/files/2053928981082898444/trash', { reason: '删除' }],
      ['post', '/api/employee/files/2053928981082898444/restore', { reason: '恢复' }],
      ['delete', '/api/employee/files/2053928981082898444', undefined],
    ]);
    expect(api.files.downloadUrl('DL-1')).toBe('/api/employee/files/download/DL-1');
  });

  it('maps IM conversations and messages to backend IM controller', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.im.conversations({ beforeLastMessageAt: '2026-05-12T09:30:00', beforeConversationId: 7001, size: 20 });
    await api.im.conversationDetail(7001);
    await api.im.messages(7001, { beforeSeq: 20, size: 10 });
    await api.im.startPrivateConversation({ targetUserId: 2002 });
    await api.im.createGroupConversation({ title: '项目推进群', memberUserIds: [2002, 2004] });
    await api.im.sendMessage(7001, {
      messageType: 'TEXT',
      text: '我下午两点同步。',
      clientMessageId: 'client-msg-001',
    });
    await api.im.updateReadPosition(7001, { lastReadSeq: 3 });

    expect(calls).toEqual([
      ['get', '/api/employee/im/conversations', { query: { beforeLastMessageAt: '2026-05-12T09:30:00', beforeConversationId: 7001, size: 20 } }],
      ['get', '/api/employee/im/conversations/7001', undefined],
      ['get', '/api/employee/im/conversations/7001/messages', { query: { beforeSeq: 20, size: 10 } }],
      ['post', '/api/employee/im/private-conversations', { targetUserId: 2002 }],
      ['post', '/api/employee/im/group-conversations', { title: '项目推进群', memberUserIds: [2002, 2004] }],
      ['post', '/api/employee/im/conversations/7001/messages', {
        messageType: 'TEXT',
        text: '我下午两点同步。',
        clientMessageId: 'client-msg-001',
      }],
      ['post', '/api/employee/im/conversations/7001/read-position', { lastReadSeq: 3 }],
    ]);
  });

  it('maps new IM and Agent endpoints that are implemented by backend controllers', async () => {
    const { client, calls } = createSpyClient();
    const api = createEmployeeApi(client);

    await api.me.profile();
    await api.me.updateProfile({ mobile: '13900002202' });
    await api.me.manager();
    await api.me.subordinates();
    await api.im.groupMembers(7001);
    await api.im.groupProfile(7001);
    await api.im.updateGroupProfile(7001, { title: '新群名', noticeText: '公告' });
    await api.im.addGroupMembers(7001, { memberUserIds: [2002] });
    await api.im.removeGroupMembers(7001, { memberUserIds: [2002] });
    await api.im.leaveGroup(7001);
    await api.im.dismissGroup(7001);
    await api.im.groupFiles(7001);
    await api.im.createGroupFileDownloadTicket(7001, '2053928981082898444', { downloadType: 'SINGLE', reason: '预览' });
    await api.im.trashGroupFile(7001, '2053928981082898444', { reason: '删除' });
    await api.im.updatePreference(7001, { pin: true });
    await api.im.startAgentConversation({ agentId: 9001 });
    await api.im.editMessage(7001, 7101, { text: '更新后的消息' });
    await api.im.recallMessage(7001, 7101);
    await api.im.translateMessage(7001, 7101);
    await api.im.forwardMessage(7001, 7101, { targetConversationId: 7002 });
    await api.im.favoriteMessage(7001, 7101);
    await api.im.remindMessage(7001, 7101, { note: '提醒' });
    await api.im.deleteMessage(7001, 7101);
    await api.im.createMessageAttachmentDownloadTicket('2053928814116044802', '2053928814116044811', '2053928981082898444', { downloadType: 'SINGLE' });
    await api.agents.list({ keyword: 'HR' });
    await api.agents.createTask(9001, { triggerType: 'MANUAL', inputPayloadJson: '{}' });
    await api.agentTasks.confirm('2053928981082898433', { approved: true, comment: '确认' });
    await api.agentTasks.cancel('2053928981082898433', { reason: '取消' });

    expect(calls).toEqual([
      ['get', '/api/employee/me/profile', undefined],
      ['put', '/api/employee/me/profile', { mobile: '13900002202' }],
      ['get', '/api/employee/me/manager', undefined],
      ['get', '/api/employee/me/subordinates', undefined],
      ['get', '/api/employee/im/group-conversations/7001/members', undefined],
      ['get', '/api/employee/im/group-conversations/7001/profile', undefined],
      ['post', '/api/employee/im/group-conversations/7001/profile', { title: '新群名', noticeText: '公告' }],
      ['post', '/api/employee/im/group-conversations/7001/members', { memberUserIds: [2002] }],
      ['post', '/api/employee/im/group-conversations/7001/members/remove', { memberUserIds: [2002] }],
      ['post', '/api/employee/im/group-conversations/7001/leave', undefined],
      ['post', '/api/employee/im/group-conversations/7001/dismiss', undefined],
      ['get', '/api/employee/im/group-conversations/7001/files', undefined],
      ['post', '/api/employee/im/group-conversations/7001/files/2053928981082898444/download-tickets', { downloadType: 'SINGLE', reason: '预览' }],
      ['post', '/api/employee/im/group-conversations/7001/files/2053928981082898444/trash', { reason: '删除' }],
      ['post', '/api/employee/im/conversations/7001/preference', { pin: true }],
      ['post', '/api/employee/im/agent-conversations', { agentId: 9001 }],
      ['post', '/api/employee/im/conversations/7001/messages/7101/edit', { text: '更新后的消息' }],
      ['post', '/api/employee/im/conversations/7001/messages/7101/recall', undefined],
      ['post', '/api/employee/im/conversations/7001/messages/7101/translate', undefined],
      ['post', '/api/employee/im/conversations/7001/messages/7101/forward', { targetConversationId: 7002 }],
      ['post', '/api/employee/im/conversations/7001/messages/7101/favorite', undefined],
      ['post', '/api/employee/im/conversations/7001/messages/7101/reminder', { note: '提醒' }],
      ['post', '/api/employee/im/conversations/7001/messages/7101/delete', undefined],
      ['post', '/api/employee/im/conversations/2053928814116044802/messages/2053928814116044811/attachments/2053928981082898444/download-tickets', { downloadType: 'SINGLE' }],
      ['get', '/api/employee/agents', { query: { keyword: 'HR' } }],
      ['post', '/api/employee/agents/9001/tasks', { triggerType: 'MANUAL', inputPayloadJson: '{}' }],
      ['post', '/api/employee/agent-tasks/2053928981082898433/confirm', { approved: true, comment: '确认' }],
      ['post', '/api/employee/agent-tasks/2053928981082898433/cancel', { reason: '取消' }],
    ]);
    expect(api.im.downloadAttachmentUrl('IM-1')).toBe('/api/employee/im/attachments/download/IM-1');
  });
});
