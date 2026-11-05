import { useMemo, useState } from 'react';
import { renderMarkdown } from '../markdown';

type Props = {
  value: string;
  onChange: (value: string) => void;
  rows?: number;
  placeholder?: string;
};

/**
 * Markdown 编辑器：桌面端左右分栏实时预览；窄屏（≤720px）切换「编辑 / 预览」两个页签，
 * 避免手机上左右两栏把内容压成一条。
 */
export function MarkdownEditor({ value, onChange, rows = 12, placeholder }: Props) {
  const [pane, setPane] = useState<'write' | 'preview'>('write');
  const html = useMemo(() => renderMarkdown(value), [value]);

  return (
    <div className="md-editor">
      <div className="md-tabs" role="tablist">
        <button
          type="button"
          className={pane === 'write' ? 'active' : ''}
          onClick={() => setPane('write')}
        >
          编辑
        </button>
        <button
          type="button"
          className={pane === 'preview' ? 'active' : ''}
          onClick={() => setPane('preview')}
        >
          预览
        </button>
      </div>
      <div className="md-panes">
        <textarea
          className={pane === 'write' ? 'pane' : 'pane md-hidden'}
          rows={rows}
          value={value}
          placeholder={placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
        <div
          className={pane === 'preview' ? 'pane preview' : 'pane preview md-hidden'}
          // 渲染前已整体转义 HTML，这里放入的是本模块受控生成的标签
          dangerouslySetInnerHTML={{
            __html: html || '<p class="muted">（这里实时显示预览）</p>',
          }}
        />
      </div>
    </div>
  );
}
