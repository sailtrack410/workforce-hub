import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { imApi } from '@/api';
import { listCachedMessages } from '@/services/desktopMessages';
import type { CurrentUserResponse, ImMessageResponse } from '@/api/types';
import { useImStore } from './im';

const { session } = vi.hoisted(() => ({ session: { user: null as CurrentUserResponse | null } }));
vi.mock('@/api', () => ({ imApi: { messages: vi.fn(), conversations: vi.fn() } }));
vi.mock('@/stores/session', () => ({ useSessionStore: () => session }));
vi.mock('@/services/sync', () => ({ durableRequest: vi.fn(), pendingOperations: vi.fn(async () => []) }));
vi.mock('@/services/desktopMessages', () => ({ listCachedMessages: vi.fn(), cacheMessages: vi.fn(async () => undefined) }));

const message = (seqNo: number, extra = {}): ImMessageResponse => ({
  messageId: String(seqNo), conversationId: '9', seqNo, senderType: 'USER', messageType: 'TEXT', status: 'NORMAL', ...extra,
});
const page = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, index) => message(from + index));
let desktop: { cacheContext: ReturnType<typeof vi.fn>; readDraft: ReturnType<typeof vi.fn>; replaceMessages: ReturnType<typeof vi.fn>; saveDraft: ReturnType<typeof vi.fn>; clearMessages: ReturnType<typeof vi.fn> };

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(imApi.messages).mockReset();
  vi.mocked(imApi.conversations).mockReset();
  setActivePinia(createPinia());
  session.user = { userId: '9007199254740993', loginName: 'fixture', displayName: 'fixture' };
  desktop = { cacheContext: vi.fn(async () => 'scope'), readDraft: vi.fn(async () => null), replaceMessages: vi.fn(async () => undefined), saveDraft: vi.fn(async () => undefined), clearMessages: vi.fn(async () => undefined) };
  vi.stubGlobal('window', { workforceDesktop: desktop });
  vi.mocked(listCachedMessages).mockResolvedValue([]);
});
afterEach(() => vi.unstubAllGlobals());

it('刷新最新页保留更早历史，并同步查询范围内的删除和撤回', async () => {
  const im = useImStore();
  im.messagesByConversation['9'] = page(1, 250);
  im.syncedMessageSeq['9'] = 250;
  vi.mocked(imApi.messages).mockResolvedValue(page(151, 251).filter(item => item.seqNo !== 200)
    .map(item => item.seqNo === 201 ? { ...item, status: 'RECALLED' } : item));
  await im.loadMessages('9');
  expect(im.messagesByConversation['9'].map(item => item.seqNo)).toEqual(page(1, 251).filter(item => item.seqNo !== 200).map(item => item.seqNo));
  expect(im.messagesByConversation['9'].find(item => item.seqNo === 201)?.status).toBe('RECALLED');
  expect(desktop.replaceMessages).toHaveBeenCalledWith('scope', '9', expect.any(Array), { fromSeq: 151, toSeq: 251 });
});

it('重连跨越多页补齐消息，实时到达的新消息不会跳过补拉区间', async () => {
  const im = useImStore();
  im.messagesByConversation['9'] = page(1, 50);
  im.syncedMessageSeq['9'] = 50;
  im.upsertMessage(message(351));
  vi.mocked(imApi.messages).mockResolvedValueOnce(page(252, 351)).mockResolvedValueOnce(page(152, 251))
    .mockResolvedValueOnce(page(52, 151)).mockResolvedValueOnce(page(1, 51));
  await im.loadMessages('9');
  expect(im.messagesByConversation['9'].map(item => item.seqNo)).toEqual(page(1, 351).map(item => item.seqNo));
  expect(vi.mocked(imApi.messages).mock.calls.map(call => call[1])).toEqual([
    { size: 100 }, { beforeSeq: 252, size: 100 }, { beforeSeq: 152, size: 100 }, { beforeSeq: 52, size: 100 },
  ]);
  expect(desktop.saveDraft).toHaveBeenCalledWith('scope', 'im-synced-seq:9', 351);
});

it('重启使用持久补拉游标，不能将缓存中的最新实时消息当作完整历史', async () => {
  const im = useImStore();
  vi.mocked(listCachedMessages).mockResolvedValue([message(351)]);
  desktop.readDraft.mockResolvedValue(50);
  vi.mocked(imApi.messages).mockResolvedValueOnce(page(252, 351)).mockResolvedValueOnce(page(152, 251))
    .mockResolvedValueOnce(page(52, 151)).mockResolvedValueOnce(page(1, 51));
  await im.loadMessages('9');
  expect(im.messagesByConversation['9']).toHaveLength(351);
  expect(im.syncedMessageSeq['9']).toBe(351);
});

it('历史分页按最早服务端序号向前读取，空页才确认结束', async () => {
  const im = useImStore();
  im.messagesByConversation['9'] = page(201, 300);
  vi.mocked(imApi.messages).mockResolvedValueOnce(page(101, 200)).mockResolvedValueOnce(page(1, 100)).mockResolvedValueOnce([]);
  await im.loadOlderMessages('9'); await im.loadOlderMessages('9'); await im.loadOlderMessages('9');
  expect(im.messagesByConversation['9']).toHaveLength(300);
  expect(vi.mocked(imApi.messages).mock.calls.map(call => call[1]?.beforeSeq)).toEqual([201, 101, 1]);
  expect(im.hasMoreMessages['9']).toBe(false);
});

it('离线历史读取同一游标的 SQLite 页面，不将缓存结束误判为服务端结束', async () => {
  const im = useImStore();
  im.messagesByConversation['9'] = page(201, 300);
  vi.mocked(imApi.messages).mockRejectedValue(new TypeError('offline'));
  vi.mocked(listCachedMessages).mockResolvedValue(page(101, 200));
  await im.loadOlderMessages('9');
  expect(listCachedMessages).toHaveBeenCalledWith(session.user!.userId, '9', { beforeSeq: 201, size: 100 }, 'scope');
  expect(im.messagesByConversation['9']).toHaveLength(200);
  expect(im.hasMoreMessages['9']).not.toBe(false);
  expect(desktop.replaceMessages).not.toHaveBeenCalled();
});

it('补拉中途失败保留原消息与补拉游标，重试能够重新取得完整区间', async () => {
  const im = useImStore();
  im.messagesByConversation['9'] = [message(1)]; im.syncedMessageSeq['9'] = 1;
  vi.mocked(imApi.messages).mockResolvedValueOnce(page(202, 301)).mockRejectedValueOnce(new TypeError('offline'));
  await im.loadMessages('9');
  expect(im.messagesByConversation['9']).toEqual([message(1)]);
  expect(im.syncedMessageSeq['9']).toBe(1);
  expect(im.messageErrors['9']).toEqual({ message: 'offline', history: false });
  expect(desktop.replaceMessages).not.toHaveBeenCalled(); expect(desktop.saveDraft).not.toHaveBeenCalled();
  vi.mocked(imApi.messages).mockResolvedValueOnce(page(202, 301)).mockResolvedValueOnce(page(102, 201))
    .mockResolvedValueOnce(page(2, 101)).mockResolvedValueOnce([message(1)]);
  await im.loadMessages('9');
  expect(im.messagesByConversation['9']).toHaveLength(301);
  expect(im.messageErrors['9']).toBeUndefined();
});

it('切换账号后丢弃旧会话请求的迟到响应', async () => {
  const im = useImStore(); let finish!: (messages: ImMessageResponse[]) => void;
  vi.mocked(imApi.messages).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const loading = im.loadMessages('9');
  await vi.waitFor(() => expect(imApi.messages).toHaveBeenCalled());
  session.user = { ...session.user!, userId: 'other-account' }; im.$reset();
  finish([message(1)]); await loading;
  expect(im.messagesByConversation).toEqual({}); expect(desktop.replaceMessages).not.toHaveBeenCalled();
});

it('清空记录会使尚未完成的历史请求失效', async () => {
  const im = useImStore(); let finish!: (messages: ImMessageResponse[]) => void;
  vi.mocked(imApi.messages).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  const loading = im.loadMessages('9');
  await vi.waitFor(() => expect(imApi.messages).toHaveBeenCalled());
  await im.resetMessageHistory('9'); finish([message(1)]); await loading;
  expect(im.messagesByConversation['9']).toEqual([]);
  expect(desktop.replaceMessages).not.toHaveBeenCalled();
  expect(desktop.saveDraft).toHaveBeenCalledWith('scope', 'im-synced-seq:9', null);
});

it('重连优先补齐当前会话，然后补齐已打开的其他有效会话', async () => {
  const im = useImStore(); im.activeConversationId = '9'; im.messagesByConversation['10'] = [message(1, { conversationId: '10' })];
  im.messagesByConversation['removed'] = [];
  vi.mocked(imApi.conversations).mockResolvedValue([{ conversationId: '9' }, { conversationId: '10' }] as never);
  const load = vi.spyOn(im, 'loadMessages').mockResolvedValue(undefined);
  await im.syncMessagesAfterReconnect();
  expect(load.mock.calls).toEqual([['9'], ['10']]);
});
