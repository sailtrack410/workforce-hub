import { afterEach, expect, it, vi } from 'vitest';
import { fileApi, imApi } from './index';

afterEach(() => vi.unstubAllGlobals());
function mockFetch(payload: string) {
  vi.stubGlobal('window', { location: { protocol: 'http:' } });
  vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=fixture' });
  const fetcher = vi.fn<typeof fetch>(async () => new Response(payload));
  vi.stubGlobal('fetch', fetcher);
  return fetcher;
}

it('消息分页使用后端 size 和 beforeSeq 契约', async () => {
  const fetcher = mockFetch('[]');
  await imApi.messages('9007199254740993', { beforeSeq: 201, size: 100 });
  expect(fetcher.mock.calls[0][0]).toBe('/api/employee/im/conversations/9007199254740993/messages?beforeSeq=201&size=100');
});

it('消息在线分页失败不使用普通 GET 快照冒充实时响应', async () => {
  mockFetch('[]');
  const cacheGet = vi.fn(async () => ({ value: [], updatedAt: 1 }));
  vi.stubGlobal('window', { location: { protocol: 'http:' }, workforceDesktop: { cacheContext: async () => 'scope', cacheGet } });
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
  await expect(imApi.messages('9', { size: 100 })).rejects.toThrow('offline');
  expect(cacheGet).not.toHaveBeenCalled();
});

it('文件夹和父文件夹的裸数字响应保留完整精度，并原样发送目录查询与新建请求', async () => {
  const fetcher = mockFetch('{"folderId":9007199254740993,"parentFolderId":9007199254740995}');
  const folder = await fileApi.createFolder({ parentFolderId: '9007199254740995', folderName: '文件夹' });
  expect(folder.folderId).toBe('9007199254740993'); expect(folder.parentFolderId).toBe('9007199254740995');
  await fileApi.browser({ folderId: folder.folderId });
  expect(fetcher.mock.calls[1][0]).toBe('/api/employee/files/browser?folderId=9007199254740993');
  expect(JSON.parse(fetcher.mock.calls[0][1]!.body as string)).toEqual({ parentFolderId: '9007199254740995', folderName: '文件夹' });
});

it('文件浏览的当前目录、面包屑和文件归属目录均保留雪花 ID', async () => {
  mockFetch('{"currentFolderId":9007199254740993,"breadcrumbs":[{"folderId":9007199254740993,"parentFolderId":9007199254740995,"folderName":"目录"}],"folders":[],"files":[{"folderId":9007199254740993}]}');
  const browser = await fileApi.browser();
  expect(browser.currentFolderId).toBe('9007199254740993');
  expect(browser.breadcrumbs[0].folderId).toBe('9007199254740993');
  expect(browser.breadcrumbs[0].parentFolderId).toBe('9007199254740995');
  expect(browser.files[0].folderId).toBe('9007199254740993');
});
