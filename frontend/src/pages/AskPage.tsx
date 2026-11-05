import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { BountyDialog, type BountyValue } from '../components/BountyDialog';
import { MarkdownEditor } from '../components/MarkdownEditor';

export function AskPage() {
  const { me } = useAuth();
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [tags, setTags] = useState('');
  const [courseId, setCourseId] = useState<string>('');
  const [bounty, setBounty] = useState<BountyValue | null>(null);
  const [bountyOpen, setBountyOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [published, setPublished] = useState<{ id: number; warning?: string } | null>(null);
  const [error, setError] = useState('');

  const submit = async () => {
    if (submitting) return;
    setSubmitting(true);
    setError('');
    try {
      const { id } = await api.ask({
        courseId: courseId ? Number(courseId) : undefined,
        title,
        body,
        tags: tags.split(/[,，\s]+/).filter(Boolean),
      });
      if (bounty) {
        try {
          await api.bounty(id, bounty.points, bounty.days);
        } catch (e) {
          // 问题已经创建成功，悬赏失败不回滚，也不重复提交，只提示用户去详情页重试
          setPublished({ id, warning: `问题已发布，但悬赏未设置成功：${(e as Error).message}` });
          return;
        }
      }
      setPublished({ id });
      navigate(`/questions/${id}`);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  if (published) {
    return (
      <div className="form-page">
        <h1>问题已发布</h1>
        {published.warning && <p className="error">{published.warning}</p>}
        <div className="actions">
          <Link className="primary" to={`/questions/${published.id}`}>去看我的问题</Link>
          <Link to="/">返回列表</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="form-page">
      <h1>提出一个问题</h1>
      <label>标题（10–150 字，尽量具体）
        <input value={title} onChange={(e) => setTitle(e.target.value)} />
      </label>

      <label>正文（Markdown，至少 20 字：说明你尝试过什么、卡在哪里）</label>
      <MarkdownEditor
        value={body}
        onChange={setBody}
        rows={12}
        placeholder={'支持 Markdown：\n**加粗**、`行内代码`、```代码块```、- 列表、[链接](https://example.com)'}
      />

      <label>标签（1–5 个，用空格或逗号分隔）
        <input value={tags} onChange={(e) => setTags(e.target.value)} placeholder="数据结构 红黑树" />
      </label>
      <label>所属课程（可选）
        <select value={courseId} onChange={(e) => setCourseId(e.target.value)}>
          <option value="">不关联课程</option>
          {me?.courses.map((c) => <option key={c.id} value={c.id}>{c.code} {c.name}</option>)}
        </select>
      </label>

      <div className="bounty-row">
        {bounty
          ? <span>已设置悬赏 <b>{bounty.points}</b> 分 · {bounty.days} 天
              <button type="button" className="link" onClick={() => setBounty(null)}>取消悬赏</button>
            </span>
          : <button type="button" onClick={() => setBountyOpen(true)}>+ 添加悬赏（吸引更多回答）</button>}
      </div>

      {error && <p className="error">{error}</p>}
      <button className="primary" disabled={submitting} onClick={submit}>
        {submitting ? '发布中…' : '发布问题'}
      </button>

      {bountyOpen && (
        <BountyDialog
          initial={bounty ?? undefined}
          onCancel={() => setBountyOpen(false)}
          onConfirm={(value) => { setBounty(value); setBountyOpen(false); }}
        />
      )}
    </div>
  );
}
