// 找到从 start 开始的配平大括号终点（跳过字符串与转义），找不到返回 -1
function findJsonBlockEnd(value: string, start: number): number {
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < value.length; i += 1) {
    const ch = value[i];
    if (escaped) {
      escaped = false;
      continue;
    }
    if (ch === '\\') {
      escaped = true;
      continue;
    }
    if (ch === '"') {
      inString = !inString;
      continue;
    }
    if (inString) continue;
    if (ch === '{') depth += 1;
    if (ch === '}') {
      depth -= 1;
      if (depth === 0) return i;
    }
  }
  return -1;
}

function isJsonObject(text: string): boolean {
  try {
    const parsed = JSON.parse(text);
    return typeof parsed === 'object' && parsed !== null;
  } catch {
    return false;
  }
}

// 把正文里夹带的长 JSON 块（如工具返回原文）折叠为 :::json-fold 标记块
function extractJsonBlocks(value: string): string {
  let result = '';
  let i = 0;
  while (i < value.length) {
    if (value[i] === '{') {
      const end = findJsonBlockEnd(value, i);
      if (end > i) {
        const candidate = value.slice(i, end + 1);
        // 足够长且是合法 JSON 对象才折叠，短的花括号片段当普通文本
        if (candidate.length >= 60 && isJsonObject(candidate)) {
          result += `\n:::json-fold\n${candidate}\n:::\n`;
          i = end + 1;
          continue;
        }
      }
    }
    result += value[i];
    i += 1;
  }
  return result;
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function renderInlineMarkdown(value: string) {
  return escapeHtml(value)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+|mailto:[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer">$1</a>');
}

// 轻量 Markdown 转 HTML：支持标题、列表、代码块、引用、加粗/斜体/链接
export function renderMarkdownText(value?: string | null) {
  if (!value) return '';
  const lines = extractJsonBlocks(value).replace(/\r\n/g, '\n').split('\n');
  const html: string[] = [];
  let paragraph: string[] = [];
  let listType: 'ul' | 'ol' | null = null;
  let inCode = false;
  let codeLines: string[] = [];
  let inJsonFold = false;
  let jsonLines: string[] = [];

  function flushParagraph() {
    if (!paragraph.length) return;
    html.push(`<p>${paragraph.map(renderInlineMarkdown).join('<br>')}</p>`);
    paragraph = [];
  }

  function flushList() {
    if (!listType) return;
    html.push(`</${listType}>`);
    listType = null;
  }

  function openList(type: 'ul' | 'ol') {
    if (listType === type) return;
    flushList();
    html.push(`<${type}>`);
    listType = type;
  }

  function flushJsonFold() {
    let pretty = jsonLines.join('\n');
    try {
      pretty = JSON.stringify(JSON.parse(pretty), null, 2);
    } catch {
      // 保留原文
    }
    html.push(`<details class="json-fold"><summary>查看原始数据</summary><pre><code>${escapeHtml(pretty)}</code></pre></details>`);
    jsonLines = [];
    inJsonFold = false;
  }

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === ':::json-fold') {
      flushParagraph();
      flushList();
      inJsonFold = true;
      jsonLines = [];
      continue;
    }
    if (inJsonFold) {
      if (trimmed === ':::') {
        flushJsonFold();
      } else {
        jsonLines.push(line);
      }
      continue;
    }
    if (trimmed.startsWith('```')) {
      if (inCode) {
        html.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`);
        codeLines = [];
        inCode = false;
      } else {
        flushParagraph();
        flushList();
        inCode = true;
      }
      continue;
    }
    if (inCode) {
      codeLines.push(line);
      continue;
    }
    if (!trimmed) {
      flushParagraph();
      flushList();
      continue;
    }
    const heading = trimmed.match(/^(#{1,4})\s+(.+)$/);
    if (heading) {
      flushParagraph();
      flushList();
      const level = Math.min(heading[1].length + 2, 6);
      html.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`);
      continue;
    }
    const unordered = trimmed.match(/^[-*]\s+(.+)$/);
    if (unordered) {
      flushParagraph();
      openList('ul');
      html.push(`<li>${renderInlineMarkdown(unordered[1])}</li>`);
      continue;
    }
    const ordered = trimmed.match(/^\d+\.\s+(.+)$/);
    if (ordered) {
      flushParagraph();
      openList('ol');
      html.push(`<li>${renderInlineMarkdown(ordered[1])}</li>`);
      continue;
    }
    const quote = trimmed.match(/^>\s+(.+)$/);
    if (quote) {
      flushParagraph();
      flushList();
      html.push(`<blockquote>${renderInlineMarkdown(quote[1])}</blockquote>`);
      continue;
    }
    flushList();
    paragraph.push(line);
  }

  if (inCode) {
    html.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`);
  }
  if (inJsonFold) {
    flushJsonFold();
  }
  flushParagraph();
  flushList();
  return html.join('');
}
