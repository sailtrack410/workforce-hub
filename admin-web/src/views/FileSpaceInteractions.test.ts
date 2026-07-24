import { describe, expect, it } from 'vitest';
import shellSource from './AdminShell.vue?raw';
import fileSpaceSource from './FileCleanupView.vue?raw';

describe('admin file space interaction contracts', () => {
  it('keeps sidebar scrolling without a visible scrollbar', () => {
    expect(shellSource).toContain("label: '文件空间'");
    expect(shellSource).toContain('scrollbar-width: none');
    expect(shellSource).toContain('.admin-sidebar::-webkit-scrollbar');
  });

  it('uses a guided cleanup flow', () => {
    expect(fileSpaceSource).toContain('<AdminDialog');
    expect(fileSpaceSource).toContain('<AdminConfirmDialog');
    expect(fileSpaceSource).toContain('预览影响，不删除文件');
    expect(fileSpaceSource).toContain('.mode-option input { width: 18px');
    expect(fileSpaceSource).toContain('按此条件正式清理');
    expect(fileSpaceSource).toContain('class="danger-button"');
  });

  it('surfaces storage health and pending work', () => {
    expect(fileSpaceSource).toContain('title="空间状态"');
    expect(fileSpaceSource).toContain('title="文件与待处理对象"');
    expect(fileSpaceSource).toContain('pendingCleanupObjectCount');
    expect(fileSpaceSource).not.toContain('window.confirm');
  });

  it('shows the enterprise file catalog by default', () => {
    expect(fileSpaceSource).toContain("activeTab = ref<'catalog' | 'storage'>('catalog')");
    expect(fileSpaceSource).toContain('title="企业文件"');
    expect(fileSpaceSource).toContain('<AdminTable');
    expect(fileSpaceSource).toContain('<AdminPagination');
    expect(fileSpaceSource).toContain('adminApi.files.list');
    expect(fileSpaceSource).toContain('没有找到匹配文件');
    expect(fileSpaceSource.match(/v-if="activeTab === 'catalog'"/g)).toHaveLength(1);
  });
});
