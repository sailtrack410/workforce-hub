import { describe, expect, it } from 'vitest';
import { renderMarkdownText } from './markdown';

describe('markdown utils', () => {
  it('renders common markdown blocks as safe html', () => {
    expect(renderMarkdownText('# 标题\n\n- **重点**\n- `字段`\n\n> 提示')).toBe(
      '<h3>标题</h3><ul><li><strong>重点</strong></li><li><code>字段</code></li></ul><blockquote>提示</blockquote>'
    );
  });

  it('escapes html before rendering markdown', () => {
    expect(renderMarkdownText('**<script>alert(1)</script>**')).toBe(
      '<p><strong>&lt;script&gt;alert(1)&lt;/script&gt;</strong></p>'
    );
  });
});
