import { Link } from 'react-router-dom';
import type { ToastItem } from '../useNotificationStream';

/** 右上角实时提醒：新通知到达时弹出，6 秒后自动消失，也可点 × 关掉。 */
export function Toasts({ items, onDismiss }: { items: ToastItem[]; onDismiss: (id: number) => void }) {
  if (items.length === 0) {
    return null;
  }
  return (
    <div className="toasts" aria-live="polite">
      {items.map((t) => (
        <div className="toast" key={t.id}>
          <Link to={t.link} onClick={() => onDismiss(t.id)}>{t.title}</Link>
          <button type="button" className="link" aria-label="关闭" onClick={() => onDismiss(t.id)}>×</button>
        </div>
      ))}
    </div>
  );
}
