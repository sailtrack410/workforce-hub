import { describe, expect, it } from 'vitest';
import { h } from 'vue';
import { renderToString } from 'vue/server-renderer';
import AppSkeleton from './AppSkeleton.vue';

function countOccurrences(html: string, needle: string) {
  return html.split(needle).length - 1;
}

describe('AppSkeleton', () => {
  it('默认渲染单条文本占位', async () => {
    const html = await renderToString(h(AppSkeleton));
    expect(countOccurrences(html, 'sk-line')).toBe(1);
  });

  it('list 变体渲染指定数量的头像行', async () => {
    const html = await renderToString(h(AppSkeleton, { variant: 'list', count: 4 }));
    expect(countOccurrences(html, 'class="sk-row"')).toBe(4);
    expect(countOccurrences(html, 'sk-avatar')).toBe(4);
  });

  it('grid 变体渲染指定数量的卡片', async () => {
    const html = await renderToString(h(AppSkeleton, { variant: 'grid', count: 6 }));
    expect(countOccurrences(html, 'class="sk-card"')).toBe(6);
  });

  it('chat 变体渲染指定数量的气泡', async () => {
    const html = await renderToString(h(AppSkeleton, { variant: 'chat', count: 3 }));
    expect(countOccurrences(html, 'class="sk-bubble-row"')).toBe(3);
  });

  it('count 非法时至少渲染一项', async () => {
    const html = await renderToString(h(AppSkeleton, { variant: 'list', count: 0 }));
    expect(countOccurrences(html, 'class="sk-row"')).toBe(1);
  });
});
