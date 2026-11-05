/**
 * 极简 Markdown 渲染器。
 * 设计要点：**先整体转义 HTML，再做受控替换**，因此输出中不含用户输入的原始标签，
 * 不需要额外引入 DOMPurify 也不会出现 XSS（链接只允许 http/https）。
 * 支持：标题、粗体、斜体、行内代码、围栏代码块、无序/有序列表、引用、链接、换行。
 */

const ESCAPES: { [ch: string]: string } = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

export function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (ch) => ESCAPES[ch] ?? ch);
}

function inline(text: string): string {
  return text
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*\n]+)\*/g, '<em>$1</em>')
    .replace(
      /\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)/g,
      '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>',
    );
}

export function renderMarkdown(source: string): string {
  if (source == null || source.trim() === '') {
    return '';
  }

  const lines = escapeHtml(source.replace(/\r\n?/g, '\n')).split('\n');
  const html: string[] = [];

  let inCode = false;
  let codeLang = '';
  let codeLines: string[] = [];
  let items: string[] = [];
  let quoteLines: string[] = [];
  let paraLines: string[] = [];

  const flushPara = () => {
    if (paraLines.length > 0) {
      html.push(`<p>${inline(paraLines.join('<br/>'))}</p>`);
      paraLines = [];
    }
  };
  const flushList = () => {
    if (items.length > 0) {
      html.push(`<ul>${items.join('')}</ul>`);
      items = [];
    }
  };
  const flushQuote = () => {
    if (quoteLines.length > 0) {
      html.push(`<blockquote>${inline(quoteLines.join('<br/>'))}</blockquote>`);
      quoteLines = [];
    }
  };
  const flushAll = () => {
    flushPara();
    flushList();
    flushQuote();
  };

  for (const raw of lines) {
    const line = raw.trimEnd();

    // 围栏代码块：``` 开头，语言标识只保留字母数字与连字符
    if (line.startsWith('```')) {
      if (inCode) {
        const cls = codeLang ? ` class="lang-${codeLang}"` : '';
        html.push(`<pre><code${cls}>${codeLines.join('\n')}</code></pre>`);
        inCode = false;
        codeLang = '';
        codeLines = [];
      } else {
        flushAll();
        inCode = true;
        codeLang = line.slice(3).trim().toLowerCase().replace(/[^a-z0-9-]/g, '');
      }
      continue;
    }
    if (inCode) {
      codeLines.push(raw);
      continue;
    }

    if (line.trim() === '') {
      flushAll();
      continue;
    }

    const heading = /^(#{1,6})\s+(.*)$/.exec(line);
    if (heading) {
      flushAll();
      const level = heading[1].length;
      html.push(`<h${level}>${inline(heading[2])}</h${level}>`);
      continue;
    }

    // 引用：> 已被转义成 &gt;
    const quoted = /^&gt;\s?(.*)$/.exec(line);
    if (quoted) {
      flushPara();
      flushList();
      quoteLines.push(quoted[1]);
      continue;
    }

    const bullet = /^(?:[-*]|\d+\.)\s+(.*)$/.exec(line);
    if (bullet) {
      flushPara();
      flushQuote();
      items.push(`<li>${inline(bullet[1])}</li>`);
      continue;
    }

    paraLines.push(line);
  }

  if (inCode) {
    const cls = codeLang ? ` class="lang-${codeLang}"` : '';
    html.push(`<pre><code${cls}>${codeLines.join('\n')}</code></pre>`);
  }
  flushAll();

  return html.join('\n');
}
